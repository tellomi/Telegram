package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Documents the login ink pairs that were hard to see, without starting Android. */
public class TellomiContrastTest {

    @Test
    public void oldHintAndEmptyCheckboxFailOnTheLightField() {
        assertFalse(TellomiContrast.readable(0xFFC7C7CC, TellomiContrast.FIELD_DAY));
        assertFalse(TellomiContrast.readable(0xFFC8C8CC, TellomiContrast.PAGE_DAY));
        assertFalse(TellomiContrast.readable(0xFF8E8E93, TellomiContrast.FIELD_NIGHT));
    }

    @Test
    public void currentFieldInkMeetsBodyTextContrast() {
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_DAY, TellomiContrast.FIELD_DAY));
        assertTrue(TellomiContrast.readable(TellomiContrast.HINT_DAY, TellomiContrast.FIELD_DAY));
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_NIGHT, TellomiContrast.FIELD_NIGHT));
        assertTrue(TellomiContrast.readable(TellomiContrast.HINT_NIGHT, TellomiContrast.FIELD_NIGHT));
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_NIGHT, TellomiContrast.PAGE_NIGHT));
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_DAY, TellomiContrast.PAGE_DAY));
    }
}
