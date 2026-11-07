package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
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

    @Test
    public void hintOnTheFilledFieldIsLiftedUntilBodyTextPasses() {
        int light = TellomiContrast.ensure(0xFFA8A8A8, 0xFF111111, 0xFFD8D8D8, TellomiContrast.MIN_TEXT);
        assertTrue(TellomiContrast.readable(light, 0xFFD8D8D8));
        int dark = TellomiContrast.ensure(0xFF788898, 0xFFFFFFFF, 0xFF485868, TellomiContrast.MIN_TEXT);
        assertTrue(TellomiContrast.readable(dark, 0xFF485868));
    }

    @Test
    public void checkboxRingIsLiftedToTheUiBoundary() {
        int stroke = TellomiContrast.ensure(0xFF546679, 0xFFFFFFFF, 0xFF1D2733, TellomiContrast.MIN_UI);
        assertTrue(TellomiContrast.ratio(stroke, 0xFF1D2733) >= TellomiContrast.MIN_UI);
    }

    @Test
    public void inkThatAlreadyClearsIsUnchanged() {
        assertEquals(0xFF737373, TellomiContrast.ensure(0xFF737373, 0xFF111111, 0xFFFFFFFF, TellomiContrast.MIN_UI));
    }
}
