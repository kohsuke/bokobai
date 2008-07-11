package org.kohsuke.jnt;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.KeywordTokenizer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.queryParser.QueryParser;
import org.kohsuke.jnt.JNIssue.Description;
import org.kohsuke.jnt.lucene.NumberUtils;
import org.kohsuke.jnt.lucene.QueryParser2;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Date;

/**
 * Converts {@link JNIssue} into the {@link Document}.
 * 
 * @author Kohsuke Kawaguchi
 */
public class IssueDocument {
    public static Document create(JNIssue issue) {
        final Document doc = new Document();
        class Builder {
            void addHeader(String name, int value) {
                addHeader(name,value,Index.TOKENIZED);
            }
            void addHeader(String name, Enum value) {
                addHeader(name,value,Index.UN_TOKENIZED);
            }
            void addHeader(String name, String value) {
                addHeader(name,value,Index.UN_TOKENIZED);
            }
            void addHeader(String name, Date value) {
                addHeader(name,
                    DateTools.timeToString(value.getTime(), DateTools.Resolution.DAY),
                    Index.UN_TOKENIZED);
            }
            void addHeader(String name, Object value, Index index) {
                if(value==null) return; // nothing to index
                // QueryString runs everything in lower case, so do it here, too.
                doc.add(new Field(name,value.toString().toLowerCase(), Store.YES, index));
            }
        }
        Builder b = new Builder();
        b.addHeader("type",issue.getType());
        b.addHeader("reporter",issue.getReporter().getName());
        b.addHeader("resolution",issue.getResolution());
        b.addHeader("version",issue.getVersion().getName());
        b.addHeader("status",issue.getStatus());
        b.addHeader("created",issue.getCreationDate().getTime());
        b.addHeader("lastModified",issue.getLastModified().getTime());
        b.addHeader("summary",issue.getShortDescription(),Index.TOKENIZED);
        b.addHeader("component",issue.getComponent());
        b.addHeader("subComponent",issue.getSubComponent());
        b.addHeader("assignedTo",issue.getAssignedTo());
        // do the tokenization, which will run the text through NumberTokenizer below
        b.addHeader("id",issue.getId());
        b.addHeader("votes",issue.getVotes());
        b.addHeader("priority",issue.getPriority());

        StringBuilder buf = new StringBuilder();
        buf.append(issue.getShortDescription()).append("\n");
        for (Description d : issue.getDescriptions()) {
            if(buf.length()>0)  buf.append("\n\n\n");
            buf.append(d.getText());
        }
        doc.add(new Field("contents",new StringReader(buf.toString())));

        return doc;
    }

    /**
     * Lucene only handles string types for fields, so we need some hack to make
     * integer fields like "votes" and "ID" work, especially wrt range query
     * (like "votes>5").
     * <p>
     * This needs to be done in 2 places. First, this analyzer has a proper
     * "tokenization" code, which encodes numbers into strings in such a way
     * that preserves lexicological order. (see {@link NumberUtils}) This works
     * for index creation and the term query in {@link QueryParser}.
     *
     * <p>
     * The other place is in {@link QueryParser2}, which knows what fields
     * are int fields and perform suitable encoding.
     */
    public static final Analyzer ISSUE_ANALYZER;

    static {
        class NumberTokenizer extends KeywordTokenizer {
            NumberTokenizer(Reader input) {
                super(input);
            }

            public Token next(Token result) throws IOException {
                Token r = super.next(result);
                if(r!=null)
                    r.setTermText(NumberUtils.int2sortableStr(r.termText()));
                return r;
            }
        }

        class NumberAnalyzer extends Analyzer {
            public TokenStream tokenStream(String fieldName, Reader reader) {
              return new NumberTokenizer(reader);
            }
        }

        PerFieldAnalyzerWrapper a = new PerFieldAnalyzerWrapper(new StandardAnalyzer());
        a.addAnalyzer("votes",new NumberAnalyzer());
        a.addAnalyzer("id",new NumberAnalyzer());
        a.addAnalyzer("priority",new NumberAnalyzer());
        ISSUE_ANALYZER = a;
    }
}
