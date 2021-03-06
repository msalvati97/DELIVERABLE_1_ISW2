
package main;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.AbstractTreeIterator;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

@SuppressWarnings("unused")
public class RepositoryClass {
	private static final String PROJNAME="S2GRAPH";
	private Repository repo;
	private Git git;
	private String path;
	private static String ptrn1= "S2GRAPH-\\d{2,}";
	public RepositoryClass(String path) throws IOException {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        this.repo=builder.setGitDir(new File(path)).setMustExist(true).build();
        this.git=new Git(repo);
        this.setPath(path);
	}
	 public Repository getRepo() {
		return repo;
	}
	public void setRepo(Repository repo) {
		this.repo = repo;
	}
	public Git getGit() {
		return git;
	}
	public void setGit(Git git) {
		this.git = git;
	}
	public static void cloneRepository(String repositorylink,String directory) throws  GitAPIException {
	  		Git.cloneRepository()
	           .setURI(repositorylink)
	           .setDirectory(new File(directory))
	           .call();
	}
    //this class return commit info with the particular id 
	public JSONObject returnCommitsFromString(Git git,String s) throws IOException,  GitAPIException, JSONException{ 
   	        JSONObject jsonObject = new JSONObject();
	    	Iterable<RevCommit> log = git.log().call();
	 	    RevCommit previousCommit = null;
	   	    for (RevCommit commit : log) {
	   	        if (previousCommit != null) {
	   	            AbstractTreeIterator oldTreeIterator = getCanonicalTreeParser( previousCommit, git );
	   	            AbstractTreeIterator newTreeIterator = getCanonicalTreeParser( commit, git );
	   	            OutputStream outputStream = new ByteArrayOutputStream();
	   	            try( DiffFormatter formatter = new DiffFormatter(outputStream)) {
	   	              formatter.setRepository( git.getRepository() );
	   	              formatter.format( oldTreeIterator, newTreeIterator );
	   	            }
	   	            }
	   	        String logMessage = commit.getShortMessage();
 	   		    Long temp = Long.parseLong(commit.getCommitTime()+"") * 1000; 
 	            Date date = new Date(temp);
	   	        if (logMessage.startsWith(s))  {
	   		    JSONArray array = new JSONArray();
	   		    array.put("CommitShortMEssage:"+ logMessage);
	   		    array.put("CommitTime:"+ date);
                jsonObject.put(commit.getId().toString(), array);
	   	         }
	   	        previousCommit = commit;
	   	        }
	   	    git.close();
			return jsonObject;
	   	}
	private static AbstractTreeIterator getCanonicalTreeParser( ObjectId commitId ,Git git) throws IOException {
			try( RevWalk walk = new RevWalk( git.getRepository() ) ) {
		      RevCommit commit = walk.parseCommit( commitId );
		      ObjectId treeId = commit.getTree().getId();
		      try( ObjectReader reader = git.getRepository().newObjectReader() ) {
		        return new CanonicalTreeParser( null, reader, treeId );
		      }
		    }
		  }
	public static Date dateToUTC(Date date){
	    return new Date(date.getTime() - Calendar.getInstance().getTimeZone().getOffset(date.getTime()));
	}
	public static  JSONObject getcommitlogs(Git git,String pattern) throws IOException, GitAPIException, JSONException {
	    	    JSONObject jsonObject = new JSONObject();
    	        JSONArray arr = new JSONArray();
	    	    Iterable<RevCommit> log = git.log().call();
	            Pattern p = Pattern.compile(pattern);
	            RevCommit previousCommit = null;
	            List<DiffEntry> listDiffs= null;
	    	    for (RevCommit commit : log) {
	    	    	if (previousCommit==null) {
						listDiffs = listDiff(git.getRepository(), git, commit.getName()+"^",commit.getName());
	    	    	}
	    	        if (previousCommit != null) {
	    	            AbstractTreeIterator oldTreeIterator = getCanonicalTreeParser(previousCommit,git);
	    	            AbstractTreeIterator newTreeIterator = getCanonicalTreeParser(commit, git );
						listDiffs = listDiff(git.getRepository(), git, commit.getName()+"^",commit.getName());
	    	            OutputStream outputStream = new ByteArrayOutputStream();
	    	            try(DiffFormatter formatter = new DiffFormatter(outputStream)) {
	    	              formatter.setRepository( git.getRepository() );
	    	              formatter.format( oldTreeIterator, newTreeIterator );
	    	            }
	    	        }
		    	    JSONObject json = new JSONObject();
	    	        String logMessage = commit.getShortMessage();
	    	   		Long temp = Long.parseLong(commit.getCommitTime()+"") * 1000;
	    	        Date date = new Date(temp);
	    	        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
	    	        String dateStr = sdf.format(date);
	    	        json.put("CommitName",commit.getName());
	    	        json.put("CommitTime",dateStr);
	    	        json.put("CommitMessage",logMessage);
		 	        Matcher m = p.matcher(logMessage);
		 	        if (m.find()) {
		    	           json.put("Linked","Yes");
		    	           json.put("Ticket",m.group(0));
		 	               }		   	        
		   	        else {
		   	            json.put("Linked","No");
	    	            json.put("Ticket","No");
		   	        }
					if (listDiffs!=null) {
						json.put("NumberOfFilesTouched",listDiffs.size());
				 	    json.put("DiffFiles", getdiffinfo(listDiffs));
					}
					else {
						json.put("NumberOfFilesTouched",0);
				 	    json.put("DiffFiles", "no");
					}
					arr.put(json);
					jsonObject.put("CommitsLog", arr);
	    	        previousCommit = commit;
					}
	    	    git.close();
				return jsonObject;
	    	}
	private static ArrayList<String> getdiffinfo(List<DiffEntry> listDiffs) {
		ArrayList <String> diffinfo= new ArrayList<>();
		for (DiffEntry diff : listDiffs) {
		       diffinfo.add("Diff: " + diff.getChangeType() + ": " +
		               (diff.getOldPath().equals(diff.getNewPath()) ? diff.getNewPath() : diff.getOldPath() + " -> " + diff.getNewPath()));
		    }
		return diffinfo;
	}
    @SuppressWarnings("finally")
	private static List<DiffEntry> listDiff(Repository repository, Git git, String oldCommit, String newCommit) throws GitAPIException, IOException {
    		List<DiffEntry> listDiff=null;
			try {
				listDiff = git.diff()
				    .setOldTree(prepareTreeParser(repository, oldCommit))
				    .setNewTree(prepareTreeParser(repository, newCommit))
				    .call();
			} catch (Exception e) {
				return listDiff;
			} finally {
				return listDiff;
				}
    }
    private static AbstractTreeIterator prepareTreeParser(Repository repository, String objectId) throws IOException {
        //from the commit we can build the tree which allows us to construct the TreeParser
        //noinspection Duplicates
        try (RevWalk walk = new RevWalk(repository)) {
            RevCommit commit = walk.parseCommit(repository.resolve(objectId));
            RevTree tree = walk.parseTree(commit.getTree().getId());
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                treeParser.reset(reader, tree.getId());
            }
            walk.dispose();
            return treeParser;
        } }
	public static List<String> fetchGitBranches(Git git) throws GitAPIException {
              ArrayList <String> branches = new ArrayList<>();          
              List<Ref> call = git.branchList().setListMode(ListMode.ALL).call();
              for (Ref ref : call) {
            	   String[] name = ref.getName().split("/");
                   branches.add(name[name.length-1]);
        }
              return branches;
	    }
	public String getPath() {
				return path;
			}
	public void setPath(String path) {
				this.path = path;
			}
	
	public static void main(String[] args) throws IOException, GitAPIException, JSONException {

	 	        RepositoryClass r2 = new RepositoryClass("C:\\Users\\salva\\git\\incubator-s2graph\\.git");
				JSONObject json2 = getcommitlogs(r2.getGit(),ptrn1);
				try (FileWriter file = new FileWriter("CommitLog"+ PROJNAME+ ".json")) {
					file.write(json2.toString(1));
				}
			}
	    	}
	    
	    
	