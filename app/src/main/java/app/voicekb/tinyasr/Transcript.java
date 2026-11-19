package app.voicekb.tinyasr;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Turns the recognizer's JSON events into text for the editor. */
final class Transcript {
    interface Sink {
        /** Final words, to append after everything committed before. */
        void commit(String text);

        /** The words that may still change; replaces the previous partial ("" clears it). */
        void partial(String text);

        void language(String lang);
    }

    private Transcript() {}

    /** Returns true if the events carried any text activity. */
    static boolean dispatch(String json, Sink sink) throws JSONException {
        if (json == null || json.length() <= 2) return false;
        JSONArray events = new JSONArray(json);
        boolean active = false;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.getJSONObject(i);
            switch (e.optString("type")) {
                case "commit": {
                    String t = join(e.optJSONArray("words"));
                    if (!t.isEmpty()) {
                        sink.commit(t);
                        active = true;
                    }
                    break;
                }
                case "partial": {
                    String open = join(e.optJSONArray("open"));
                    String tail = join(e.optJSONArray("tail"));
                    String t = open.isEmpty() ? tail : tail.isEmpty() ? open : glue(open, e.optJSONArray("tail"), tail);
                    sink.partial(t);
                    if (!t.isEmpty()) active = true;
                    break;
                }
                case "language":
                    sink.language(e.optString("lang"));
                    break;
                default:
                    break;
            }
        }
        return active;
    }

    private static String glue(String open, JSONArray tailWords, String tail) {
        JSONObject first = tailWords.optJSONObject(0);
        boolean join = first != null && first.optBoolean("join", false);
        return join ? open + tail : open + " " + tail;
    }

    static String join(JSONArray words) {
        if (words == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < words.length(); i++) {
            JSONObject w = words.optJSONObject(i);
            if (w == null) continue;
            String t = w.optString("text");
            if (t.isEmpty()) continue;
            if (sb.length() > 0 && !w.optBoolean("join", false)) sb.append(' ');
            sb.append(t);
        }
        return sb.toString();
    }
}
