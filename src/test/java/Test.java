import jline.ConsoleReader;
import jline.SimpleCompletor;
import jline.CandidateListCompletionHandler;

import java.io.IOException;

/**
 * @author Kohsuke Kawaguchi
 */
public class Test {
    public static void main(String[] args) throws IOException {
        ConsoleReader r = new ConsoleReader();
        r.addCompletor(new SimpleCompletor(new String[]{"aaa","aab","bbb"}));
        CandidateListCompletionHandler ch = new CandidateListCompletionHandler();
        ch.setAlwaysIncludeNewline(false);
        r.setCompletionHandler(ch);

        String line;
        while((line=r.readLine(">"))!=null)
            System.out.println('"'+line+'"');
    }
}
