package app.voicekb.tinyasr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TranscriptTest {
    /** Records what Transcript reports, one line per call. */
    private static final class Log implements Transcript.Sink {
        final List<String> calls = new ArrayList<>();

        @Override public void commit(String text) { calls.add("commit:" + text); }
        @Override public void partial(String text) { calls.add("partial:" + text); }
        @Override public void language(String lang) { calls.add("language:" + lang); }
    }

    private static Log run(String json) throws JSONException {
        Log log = new Log();
        Transcript.dispatch(json, log);
        return log;
    }

    @Test
    public void emptyInputDoesNothing() throws JSONException {
        Log log = new Log();
        assertFalse(Transcript.dispatch(null, log));
        assertFalse(Transcript.dispatch("[]", log));
        assertTrue(log.calls.isEmpty());
    }

    @Test
    public void commitJoinsWordsWithSpaces() throws JSONException {
        Log log = run("[{\"type\":\"commit\",\"words\":[{\"text\":\"Hello,\",\"start\":0.52,\"end\":0.9},"
                + "{\"text\":\"world.\",\"start\":0.9,\"end\":1.3}]}]");
        assertEquals(List.of("commit:Hello, world."), log.calls);
    }

    @Test
    public void partialIsOpenThenTail() throws JSONException {
        Log log = run("[{\"type\":\"partial\",\"open\":[{\"text\":\"Добрий\",\"start\":0,\"end\":1}],"
                + "\"tail\":[{\"text\":\"день\",\"start\":1,\"end\":2}]}]");
        assertEquals(List.of("partial:Добрий день"), log.calls);
    }

    @Test
    public void partialWithOnlyOneSide() throws JSONException {
        assertEquals(List.of("partial:one"),
                run("[{\"type\":\"partial\",\"open\":[{\"text\":\"one\",\"start\":0,\"end\":1}],\"tail\":[]}]").calls);
        assertEquals(List.of("partial:two"),
                run("[{\"type\":\"partial\",\"open\":[],\"tail\":[{\"text\":\"two\",\"start\":0,\"end\":1}]}]").calls);
    }

    @Test
    public void emptyPartialClearsButIsNotActivity() throws JSONException {
        Log log = new Log();
        assertFalse(Transcript.dispatch("[{\"type\":\"partial\",\"open\":[],\"tail\":[]}]", log));
        assertEquals(List.of("partial:"), log.calls);
    }

    @Test
    public void joinedWordsHaveNoSpace() throws JSONException {
        Log log = run("[{\"type\":\"commit\",\"words\":[{\"text\":\"ab\",\"start\":0,\"end\":1},"
                + "{\"text\":\"cd\",\"start\":1,\"end\":2,\"join\":true},{\"text\":\"ef\",\"start\":2,\"end\":3}]}]");
        assertEquals(List.of("commit:abcd ef"), log.calls);
    }

    @Test
    public void eventsKeepTheirOrderAndUnknownOnesAreSkipped() throws JSONException {
        Log log = run("[{\"type\":\"language\",\"lang\":\"uk\"},{\"type\":\"eou\",\"at\":3.1,\"p\":0.93},"
                + "{\"type\":\"commit\",\"words\":[{\"text\":\"Так.\",\"start\":0,\"end\":1}]},"
                + "{\"type\":\"partial\",\"open\":[],\"tail\":[{\"text\":\"а\",\"start\":1,\"end\":2}]}]");
        assertEquals(List.of("language:uk", "commit:Так.", "partial:а"), log.calls);
    }

    @Test
    public void escapedCharactersAreDecoded() throws JSONException {
        Log log = run("[{\"type\":\"commit\",\"words\":[{\"text\":\"say \\\"hi\\\"\",\"start\":0,\"end\":1}]}]");
        assertEquals(List.of("commit:say \"hi\""), log.calls);
    }
}
