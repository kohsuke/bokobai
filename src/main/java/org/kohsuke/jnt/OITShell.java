package org.kohsuke.jnt;

import org.apache.lucene.queryParser.ParseException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Iterator;
import java.util.ArrayList;

/**
 * Text-based shell for accessing offline issue tracker capability.
 *
 * @author Kohsuke Kawaguchi
 */
public class OITShell {
    public static void main(String[] args) throws Exception {
        List<String> argsList = Arrays.asList(args).subList(1,args.length);

        if(args[0].equals("refresh")) {
            refresh(argsList);
        }

        if(args[0].equals("search")) {
            search(argsList);
        }

        if(args[0].equals("list")) {
            list(argsList);
        }
    }

    private static void refresh(List<String> argsList) throws ProcessingException, IOException {
        OfflineIssueTracker oit = new OfflineIssueTracker(JavaNet.connectAnonymously().getProject(argsList.get(0)));
        System.out.println("Fetching updates");
        oit.refresh();
        System.out.println("Updating search index");
        oit.buildSearchIndex();
    }

    private static void search(List<String> argsList) throws ProcessingException, IOException, ParseException {
        boolean all = false;
        if(argsList.get(0).equals("-all")) {
            all = true;
            argsList = argsList.subList(1,argsList.size());
        }
        String projectName = argsList.get(0);
        OfflineIssueTracker oit = new OfflineIssueTracker(JavaNet.connectAnonymously().getProject(projectName));
        for (String a : argsList.subList(1,argsList.size())) {
            List<JNIssue> hits = oit.search(a);
            if(!all) {
                List<JNIssue> newList = new ArrayList<JNIssue>();
                for (JNIssue hit : hits) {
                    if(hit.getResolution()==null)
                        newList.add(hit);
                }
                hits = newList;
            }
            print(hits);
        }
    }

    private static void print(List<JNIssue> hits) {
        int subComponentWidth = maxSubComponentLen(hits);
        for (JNIssue issue : hits) {
            int votes = issue.getVotes();
            System.out.printf("%s#%-4s %s %s %s\t%-" + subComponentWidth + "s %s%s%s\n",
                    color(issue),
                    issue.getId(),
                    issue.getType().name().toUpperCase().substring(0, 3),
                    issue.getPriority(),
                    issue.getStatus(),
                    issue.getSubComponent(),
                    votes == 0 ? "" : "(" + votes + " votes) ",
                    issue.getShortDescription(),
                    REVERT);
            System.out.printf("      https://%s.dev.java.net/issues/show_bug.cgi?id=%s\n\n",
                    issue.getProject().getName(),
                    issue.getId());
        }
        System.out.printf("%d hits\n",hits.size());
    }

    /**
     * List unresolved items.
     */
    private static void list(List<String> argsList) throws ProcessingException, IOException {
        OfflineIssueTracker oit = new OfflineIssueTracker(JavaNet.connectAnonymously().getProject(argsList.get(0)));
        List<JNIssue> all = oit.getAll();
        for (Iterator<JNIssue> itr = all.iterator(); itr.hasNext();) {
            JNIssue issue = itr.next();
            if(!issue.getStatus().needsWork)
                itr.remove();
        }
        print(all);
    }

    private static int maxSubComponentLen(List<JNIssue> hits) {
        int len = 0;
        for (JNIssue issue : hits)
            len = Math.max(len,issue.getSubComponent().length());
        return len;
    }

    /**
     * Reverts the color
     */
    private static final String REVERT = "\u001B[m";

    private static String color(JNIssue issue) {
        StringBuilder buf = new StringBuilder();
        buf.append("\u001B[");

        switch (issue.getType()) {
        case DEFECT:
            buf.append("31");   // red
            break;
        case PATCH:
            buf.append("32");   // green
            break;
        case ENHANCEMENT:
        case FEATURE:
        case TASK:
            buf.append("30");   // black
            break;
        }

        if(issue.getPriority()== Priority.P1)
            buf.append(";1");
        buf.append('m');
        return buf.toString();
    }
}
