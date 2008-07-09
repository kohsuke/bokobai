package org.kohsuke.jnt;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.document.Document;
import org.kohsuke.jnt.lucene.QueryParser2;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.AbstractList;
import java.util.Collection;
import java.util.logging.Logger;

/**
 * @author Kohsuke Kawaguchi
 */
public class OfflineIssueTracker {
    public final JNProject project;

    /**
     * Directory to persist data.
     */
    private final File home;

    private Set<Integer> issueList;
    private final Map<Integer,JNIssue> cache = new Hashtable<Integer,JNIssue>();

    public OfflineIssueTracker(JNProject project) {
        this.project = project;
        home = new File(new File(System.getProperty("user.home")),".java.net.offline-issue-tracker/"+project.getName());
        home.mkdirs();
        issueList = reloadIndex();
    }

    /**
     * Lists up legal issue IDs for this project from the disk.
     */
    private Set<Integer> reloadIndex() {
        Set<Integer> issueList = new HashSet<Integer>();
        for(File xml : listXmlFiles()) {
            String n = xml.getName();
            n = n.substring(0,n.length()-4);
            try {
                issueList.add(Integer.parseInt(n));
            } catch (NumberFormatException e) {
                // ignore
            }
        }
        return issueList;
    }

    /**
     * List up all the issue XML files.
     */
    private File[] listXmlFiles() {
        return home.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".xml");
            }
        });
    }

    /**
     * Gets the issue by the issue number, from the cache.
     */
    public JNIssue get(int id) throws ProcessingException {
        synchronized (cache) {
            JNIssue n = cache.get(id);
            if(n==null) {
                File f = getCacheFile(id);
                if(f.exists()) {
                    try {
                        FileInputStream in = new FileInputStream(f);
                        try {
                            n = project.getIssueTracker().load(id,in);
                            cache.put(id,n);
                        } finally {
                            in.close();
                        }
                    } catch (IOException e) {
                        throw new ProcessingException(e);
                    }
                }
            }
            return n;
        }
    }

    /**
     * Gets all the cached issues.
     */
    public List<JNIssue> getAll() throws ProcessingException {
        List<JNIssue> r = new ArrayList<JNIssue>();
        for (Integer id : issueList)
            r.add(get(id));
        return r;
    }

    /**
     * Connects to java.net and updates the local cache.
     *
     * @return
     *      List of {@link JNIssue}s that were updated.
     */
    public Collection<JNIssue> refresh() throws ProcessingException, IOException {
        Map<Integer,JNIssue> issues;

        File timestamp = new File(home, ".last-updated");
        long now = System.currentTimeMillis();
        if(timestamp.exists()) {
            // fetch new files
            LOGGER.fine("Fetching issues updated since "+new Date(now));
            issues = project.getIssueTracker().getUpdatedIssues(new Date(timestamp.lastModified()));
        } else {
            // fetch all
            LOGGER.fine("Fetching all issues");
            issues = project.getIssueTracker().getAll();
            new FileOutputStream(timestamp).close();
        }

        // update the timestamp accordingly.
        // to be on the safe side, use the timestamp before we query the server.
        timestamp.setLastModified(now);

        // since we've parsed them, let's keep them in the cache.
        cache.putAll(issues);

        // persist new XML files
        for (JNIssue issue : issues.values()) {
            // TODO: write should be atomic
            OutputStream out = new BufferedOutputStream(new FileOutputStream(
                    getCacheFile(issue.getId())));
            try {
                issue.save(out);
            } finally {
                out.close();
            }
        }

        issueList = reloadIndex();

        return issues.values();
    }

    /**
     * Encapsulates the search logic.
     */
    private class Searcher {
        private final IndexSearcher searcher;
        private final QueryParser2 parser;

        private Searcher() throws IOException {
            IndexReader reader = openSearchIndex();

            searcher = new IndexSearcher(reader);
            Analyzer analyzer = IssueDocument.ISSUE_ANALYZER;
            parser = new QueryParser2("contents", analyzer);
            parser.addIntField("votes");
            parser.addIntField("id");
            parser.addIntField("priority");
        }

        Hits search(String queryString) throws ParseException, IOException {
            Query query = parser.parse(queryString);
            return searcher.search(query,new Sort("id"));
        }
    }

    private volatile Searcher searcher = null;

    /**
     * Queries the search index by using the standard Lucene query syntax.
     *
     * See http://lucene.apache.org/java/docs/queryparsersyntax.html for
     * the details.
     */
    public List<JNIssue> search(String queryString) throws ParseException, IOException {
        Searcher s = searcher;
        if(s==null)
            s = searcher = new Searcher();
        final Hits hits = s.search(queryString);
        return new AbstractList<JNIssue>() {
            public JNIssue get(int index) {
                try {
                    Document d = hits.doc(index);
                    String id = d.get("id");
                    return OfflineIssueTracker.this.get(Integer.parseInt(id));
                } catch (IOException e) {// UGLY
                    throw new RuntimeException(e);
                } catch (ProcessingException e) {
                    throw new RuntimeException(e);
                }
            }

            public int size() {
                return hits.length();
            }
        };
    }

    private File getCacheFile(int id) {
        return new File(home,String.format("%05d.xml", id));
    }

    /**
     * Builds lucene search index.
     */
    public void buildSearchIndex() throws IOException, ProcessingException {
        long start = System.currentTimeMillis();

        File indexDir = new File(home, "lucene-index");
        IndexWriter writer = new IndexWriter(indexDir, IssueDocument.ISSUE_ANALYZER, !indexDir.exists());
        for (JNIssue issue : getAll())
            writer.updateDocument(new Term("id",""+issue.getId()),IssueDocument.create(issue));
        writer.optimize();
        writer.close();
        searcher = null; 

        LOGGER.fine(String.format("Took %dms to index",System.currentTimeMillis()-start));
    }

    /**
     * Opens the lucene search index.
     */
    public IndexReader openSearchIndex() throws IOException {
        return IndexReader.open(new File(home,"lucene-index"));
    }

    private static final Logger LOGGER = Logger.getLogger(OfflineIssueTracker.class.getName());
}
