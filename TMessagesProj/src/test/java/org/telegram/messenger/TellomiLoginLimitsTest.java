package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiLoginLimitsTest {

    @Test
    public void callingCodeWhitelistIsChinaOnly() {
        assertTrue(TellomiCallingCodes.allowed("86"));
        assertTrue(TellomiCallingCodes.allowed("+86"));
        assertFalse(TellomiCallingCodes.allowed("1"));
        assertFalse(TellomiCallingCodes.allowed("44"));
        assertFalse(TellomiCallingCodes.allowed("852"));
        assertTrue(TellomiCallingCodes.prefixOfAllowed(""));
        assertTrue(TellomiCallingCodes.prefixOfAllowed("8"));
        assertTrue(TellomiCallingCodes.prefixOfAllowed("86"));
        assertFalse(TellomiCallingCodes.prefixOfAllowed("1"));
        assertFalse(TellomiCallingCodes.foreignPrefix("1", false, true));
        assertTrue(TellomiCallingCodes.foreignPrefix("1", true, true));
        assertTrue(TellomiCallingCodes.foreignPrefix("44", false, true));
        assertFalse(TellomiCallingCodes.foreignPrefix("86", false, true));
        assertTrue(TellomiCallingCodes.rejectedText(true).contains("+86"));
        assertTrue(TellomiCallingCodes.rejectedText(false).contains("+86"));
    }

    @Test
    public void codeFloodTextNamesTheDailyCapAndTheWait() {
        assertTrue(TellomiLoginErrors.isCodeFlood("PHONE_CODE_FLOOD"));
        assertTrue(TellomiLoginErrors.isDailyCodeFlood("FLOOD PHONE_NUMBER_FLOOD"));
        assertFalse(TellomiLoginErrors.isDailyCodeFlood("FLOOD_WAIT_30"));
        String daily = TellomiLoginErrors.codeFloodText("PHONE_CODE_FLOOD", true);
        assertTrue(daily.contains("10"));
        assertTrue(daily.contains("明天"));
        assertEquals(30, TellomiLoginErrors.floodWaitSeconds("FLOOD_WAIT_30").intValue());
        assertEquals(120, TellomiLoginErrors.floodWaitSeconds("FLOOD_WAIT_120").intValue());
        String shortWait = TellomiLoginErrors.codeFloodText("FLOOD_WAIT_60", true);
        assertTrue(shortWait.contains("60"));
        assertFalse(shortWait.contains("今天"));
        assertFalse(shortWait.contains("10"));
        String numberFlood = TellomiLoginErrors.codeFloodText("PHONE_NUMBER_FLOOD", true);
        assertTrue(numberFlood.contains("今天"));
        assertFalse(numberFlood.contains("60 秒"));
        assertTrue(TellomiLoginErrors.codeFloodText("FLOOD_WAIT_45", true).contains("45"));
        assertTrue(TellomiLoginErrors.codeFloodText("FLOOD_WAIT_120", true).contains("2"));
        assertTrue(TellomiLoginErrors.codeFloodText("FLOOD_WAIT_7200", true).contains("2"));
        assertTrue(TellomiLoginErrors.codeFloodText("FLOOD_WAIT_45", false).contains("45"));
        assertNull(TellomiLoginErrors.codeFloodText("PHONE_NUMBER_INVALID", true));
        assertNull(TellomiLoginErrors.floodWaitSeconds("PHONE_CODE_FLOOD"));
    }
}
