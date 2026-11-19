package app.voicekb.tinyasr;

import android.view.inputmethod.InputConnection;

/**
 * Writes recognized text into the editor: final words are committed, the words that may still change are kept as
 * composing text and replaced by each new partial.
 */
final class TextWriter {
    /** True while our composing text (the partial) is in the editor. */
    private boolean composing;
    /** The space put before the current composing text, if any. */
    private String lead = "";

    boolean isComposing() {
        return composing;
    }

    /** The editor changed under us (new field, new recording): nothing of ours is composing any more. */
    void reset() {
        composing = false;
    }

    /** Final words, after everything written before. */
    void commit(InputConnection ic, String text) {
        ic.beginBatchEdit();
        String l = composing ? lead : leadFor(ic, text);
        ic.commitText(l + text, 1);
        composing = false;
        ic.endBatchEdit();
    }

    /** The words not final yet; replaces the previous partial ("" removes it). */
    void partial(InputConnection ic, String text) {
        ic.beginBatchEdit();
        if (text.isEmpty()) {
            if (composing) ic.commitText("", 1);
            composing = false;
        } else {
            if (!composing) lead = leadFor(ic, text);
            ic.setComposingText(lead + text, 1);
            composing = true;
        }
        ic.endBatchEdit();
    }

    /** Leaves the partial in the editor as plain text. */
    void settle(InputConnection ic) {
        if (composing && ic != null) ic.finishComposingText();
        composing = false;
    }

    /** A space before new words, unless the text before the cursor already separates them. */
    static String leadFor(InputConnection ic, String text) {
        CharSequence before = ic.getTextBeforeCursor(1, 0);
        if (before == null || before.length() == 0) return "";
        char c = before.charAt(0);
        if (Character.isWhitespace(c) || "([{«„“\"'/-".indexOf(c) >= 0) return "";
        if (!text.isEmpty() && ".,!?:;)".indexOf(text.charAt(0)) >= 0) return "";
        return " ";
    }
}
