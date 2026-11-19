package app.voicekb.tinyasr;

import android.view.inputmethod.InputConnection;

import java.lang.reflect.Proxy;

/**
 * A text field for tests: the text, a cursor, and a composing region, driven through the InputConnection calls the
 * keyboard makes. Any other call fails the test.
 */
final class FakeEditor {
    final StringBuilder text = new StringBuilder();
    int cursor;
    /** Composing region [start, end), or -1 when there is none. */
    int compStart = -1, compEnd = -1;
    int batchDepth;

    FakeEditor(String initial) {
        text.append(initial);
        cursor = text.length();
    }

    /** The text with the composing region in [brackets] and the cursor as |. */
    String show() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            if (compStart >= 0 && i == compStart) sb.append('[');
            if (i == cursor) sb.append('|');
            if (compStart >= 0 && i == compEnd) sb.append(']');
            if (i < text.length()) sb.append(text.charAt(i));
        }
        return sb.toString();
    }

    private void replaceComposing(String s, boolean keepComposing) {
        int start = compStart >= 0 ? compStart : cursor;
        int end = compStart >= 0 ? compEnd : cursor;
        text.replace(start, end, s);
        cursor = start + s.length();
        if (keepComposing && !s.isEmpty()) {
            compStart = start;
            compEnd = cursor;
        } else {
            compStart = compEnd = -1;
        }
    }

    InputConnection connection() {
        return (InputConnection) Proxy.newProxyInstance(InputConnection.class.getClassLoader(),
                new Class<?>[] {InputConnection.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "beginBatchEdit":
                            batchDepth++;
                            return true;
                        case "endBatchEdit":
                            batchDepth--;
                            return batchDepth > 0;
                        case "commitText":
                            replaceComposing(args[0].toString(), false);
                            return true;
                        case "setComposingText":
                            replaceComposing(args[0].toString(), true);
                            return true;
                        case "finishComposingText":
                            compStart = compEnd = -1;
                            return true;
                        case "getTextBeforeCursor": {
                            int n = (Integer) args[0];
                            return text.substring(Math.max(0, cursor - n), cursor);
                        }
                        default:
                            throw new AssertionError("unexpected InputConnection call: " + method.getName());
                    }
                });
    }
}
