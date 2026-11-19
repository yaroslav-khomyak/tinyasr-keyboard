package app.voicekb.tinyasr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.inputmethod.InputConnection;

import org.junit.Test;

public class TextWriterTest {
    private final TextWriter writer = new TextWriter();

    @Test
    public void dictationIntoAnEmptyField() {
        FakeEditor e = new FakeEditor("");
        InputConnection ic = e.connection();
        writer.partial(ic, "Hello");
        assertEquals("[Hello|]", e.show());
        writer.partial(ic, "Hello this is");
        assertEquals("[Hello this is|]", e.show());
        writer.commit(ic, "Hello, this is a test.");
        assertEquals("Hello, this is a test.|", e.show());
        writer.partial(ic, "How are");
        assertEquals("Hello, this is a test.[ How are|]", e.show());
        writer.commit(ic, "How are you?");
        assertEquals("Hello, this is a test. How are you?|", e.show());
        assertFalse(writer.isComposing());
        assertEquals(0, e.batchDepth);
    }

    @Test
    public void spaceIsAddedAfterExistingText() {
        FakeEditor e = new FakeEditor("Dear John,");
        writer.commit(e.connection(), "thank you.");
        assertEquals("Dear John, thank you.|", e.show());
    }

    @Test
    public void noSecondSpaceAfterWhitespaceOrOpeningBracket() {
        FakeEditor e = new FakeEditor("First line\n");
        writer.commit(e.connection(), "Second.");
        assertEquals("First line\nSecond.|", e.show());

        FakeEditor f = new FakeEditor("note (");
        writer.partial(f.connection(), "see");
        assertEquals("note ([see|]", f.show());
    }

    @Test
    public void noSpaceBeforePunctuation() {
        FakeEditor e = new FakeEditor("Hello");
        writer.commit(e.connection(), ", world");
        assertEquals("Hello, world|", e.show());
    }

    @Test
    public void commitWithoutAPartialBeforeIt() {
        FakeEditor e = new FakeEditor("");
        InputConnection ic = e.connection();
        writer.commit(ic, "One.");
        writer.commit(ic, "Two.");
        assertEquals("One. Two.|", e.show());
    }

    @Test
    public void emptyPartialRemovesTheComposingText() {
        FakeEditor e = new FakeEditor("Kept.");
        InputConnection ic = e.connection();
        writer.partial(ic, "noise");
        assertEquals("Kept.[ noise|]", e.show());
        writer.partial(ic, "");
        assertEquals("Kept.|", e.show());
        assertFalse(writer.isComposing());
        writer.partial(ic, ""); // nothing composing: the editor is not touched
        assertEquals("Kept.|", e.show());
    }

    @Test
    public void settleKeepsThePartialAsPlainText() {
        FakeEditor e = new FakeEditor("");
        InputConnection ic = e.connection();
        writer.partial(ic, "half a sentence");
        assertTrue(writer.isComposing());
        writer.settle(ic);
        assertEquals("half a sentence|", e.show());
        assertFalse(writer.isComposing());
        // A new recording continues after it.
        writer.partial(ic, "and more");
        assertEquals("half a sentence[ and more|]", e.show());
    }

    @Test
    public void settleWithoutAConnectionOnlyForgets() {
        writer.partial(new FakeEditor("").connection(), "x");
        writer.settle(null);
        assertFalse(writer.isComposing());
    }

    @Test
    public void textInTheMiddleGetsALeadingSpaceOnly() {
        FakeEditor e = new FakeEditor("before after");
        e.cursor = 6; // after "before"
        writer.commit(e.connection(), "inserted");
        assertEquals("before inserted| after", e.show());
    }
}
