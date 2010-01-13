package org.kohsuke.jnt.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryParser.CharStream;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.QueryParserTokenManager;
import org.apache.lucene.search.ConstantScoreRangeQuery;
import org.apache.lucene.search.Query;

import java.util.HashSet;
import java.util.Set;

/**
 * Runs {@link #getRangeQuery(String, String, String, boolean)} through
 * {@link NumberUtils} if it's a number field.
 *
 * @author Kohsuke Kawaguchi
 */
public class QueryParser2 extends QueryParser {
    private final Set<String> intFields = new HashSet<String>();

    public QueryParser2(String f, Analyzer a) {
        super(f, a);
    }

    public QueryParser2(CharStream stream) {
        super(stream);
    }

    public QueryParser2(QueryParserTokenManager tm) {
        super(tm);
    }

    public void addIntField(String n) {
        this.intFields.add(n);
    }


    protected Query getRangeQuery(String field, String part1, String part2, boolean inclusive) throws ParseException {
        if(intFields.contains(field)) {
            if(part1.equals("*"))   part1=null;
            else                    part1 = NumberUtils.int2sortableStr(part1);
            if(part2.equals("*"))   part2=null;
            else                    part2 = NumberUtils.int2sortableStr(part2);
            return new ConstantScoreRangeQuery(field,part1,part2,inclusive,inclusive);
        }
        return super.getRangeQuery(field, part1, part2, inclusive);
    }
}
