package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiMessageTranslateTest {

    @Test
    public void englishCodesOnly() {
        assertTrue(TellomiMessageTranslate.english("en"));
        assertTrue(TellomiMessageTranslate.english("EN-US"));
        assertTrue(TellomiMessageTranslate.english("en_gb"));
        assertFalse(TellomiMessageTranslate.english("zh"));
        assertFalse(TellomiMessageTranslate.english("zh-hans"));
        assertFalse(TellomiMessageTranslate.english("und"));
        assertFalse(TellomiMessageTranslate.english(null));
    }

    @Test
    public void sideControlStaysOffTheBubbleUnlessTheMessageIsIncomingEnglish() {
        assertTrue(TellomiMessageTranslate.showSide(true, false, true, false, true, true, false));
        assertFalse(TellomiMessageTranslate.showSide(false, false, true, false, true, true, false));
        assertFalse(TellomiMessageTranslate.showSide(true, true, true, false, true, true, false));
        assertFalse(TellomiMessageTranslate.showSide(true, false, false, false, true, true, false));
        assertFalse(TellomiMessageTranslate.showSide(true, false, true, true, true, true, false));
        assertFalse(TellomiMessageTranslate.showSide(true, false, true, false, false, true, false));
        assertFalse(TellomiMessageTranslate.showSide(true, false, true, false, true, false, false));
        assertTrue(TellomiMessageTranslate.showSide(true, false, true, false, true, true, true));
    }
}
