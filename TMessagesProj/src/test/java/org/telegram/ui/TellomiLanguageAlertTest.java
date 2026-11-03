package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiLanguageAlertTest {

    @Test
    public void simplifiedChineseMatchesTheSuggestion() {
        assertTrue(TellomiLanguageAlert.sameSuggestion("zh_hans", "zh-hans", "zh-hans"));
        assertTrue(TellomiLanguageAlert.sameSuggestion("zh_hans", "zh-hans", "zh"));
    }

    @Test
    public void traditionalDoesNotMatchSimplified() {
        assertFalse(TellomiLanguageAlert.sameSuggestion("zh_hant", "zh-hant", "zh-hans"));
    }

    @Test
    public void englishDoesNotMatchChinese() {
        assertFalse(TellomiLanguageAlert.sameSuggestion("en", "en", "zh-hans"));
    }

    @Test
    public void emptySuggestionDoesNotMatch() {
        assertFalse(TellomiLanguageAlert.sameSuggestion("zh_hans", "zh-hans", null));
        assertFalse(TellomiLanguageAlert.sameSuggestion("zh_hans", "zh-hans", ""));
    }
}
