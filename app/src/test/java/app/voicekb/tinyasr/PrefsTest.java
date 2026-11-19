package app.voicekb.tinyasr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class PrefsTest {
    private final Prefs prefs = new Prefs(new FakePreferences());

    @Test
    public void defaultsOfferEveryLanguageAndAuto() {
        assertEquals(List.of("uk", "en", "ru", "uk,en,ru"), prefs.modes());
        assertEquals("uk", prefs.mode());
        assertFalse(prefs.autoStart());
    }

    @Test
    public void uncheckedLanguageLeavesTheKeyAndAuto() {
        assertTrue(prefs.setLangEnabled("ru", false));
        assertEquals(List.of("uk", "en", "uk,en"), prefs.modes());
    }

    @Test
    public void lastLanguageCannotBeUnchecked() {
        assertTrue(prefs.setLangEnabled("uk", false));
        assertTrue(prefs.setLangEnabled("ru", false));
        assertFalse(prefs.setLangEnabled("en", false));
        assertEquals(List.of("en"), prefs.modes());
        assertTrue(prefs.langEnabled("en"));
    }

    @Test
    public void autoNeedsItsCheckboxAndTwoLanguages() {
        prefs.setAutoMode(false);
        assertEquals(List.of("uk", "en", "ru"), prefs.modes());
        prefs.setAutoMode(true);
        prefs.setLangEnabled("en", false);
        prefs.setLangEnabled("ru", false);
        assertEquals(List.of("uk"), prefs.modes());
    }

    @Test
    public void keyCyclesThroughModesAndWraps() {
        prefs.setLangEnabled("ru", false);
        prefs.setMode(prefs.nextMode());
        assertEquals("en", prefs.mode());
        prefs.setMode(prefs.nextMode());
        assertEquals("uk,en", prefs.mode());
        prefs.setMode(prefs.nextMode());
        assertEquals("uk", prefs.mode());
    }

    @Test
    public void selectedModeFallsBackWhenItIsNoLongerOffered() {
        prefs.setMode("ru");
        assertEquals("ru", prefs.mode());
        prefs.setLangEnabled("ru", false);
        assertEquals("uk", prefs.mode());

        prefs.setMode("uk,en");
        prefs.setAutoMode(false);
        assertEquals("uk", prefs.mode());
    }

    @Test
    public void labels() {
        assertEquals("UK", Prefs.label("uk"));
        assertEquals("AUTO", Prefs.label("uk,en"));
        assertTrue(Prefs.isAuto("uk,en,ru"));
        assertFalse(Prefs.isAuto("ru"));
    }

    @Test
    public void autoStartsFromTheLastDetectedLanguageIfStillAllowed() {
        assertEquals("uk", prefs.startLang("uk,en,ru"));
        prefs.setLastLang("en");
        assertEquals("en", prefs.startLang("uk,en,ru"));
        assertEquals("uk", prefs.startLang("uk,ru"));
        prefs.setLastLang("xx"); // not one of ours: ignored
        assertEquals("en", prefs.startLang("uk,en"));
    }
}
