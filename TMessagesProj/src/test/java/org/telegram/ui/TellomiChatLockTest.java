package org.telegram.ui;

import org.junit.Test;

import java.security.SecureRandom;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiChatLockTest {

    @Test
    public void pinIsFourToSixDigits() {
        assertFalse(TellomiChatLock.acceptable("123"));
        assertFalse(TellomiChatLock.acceptable("1234567"));
        assertFalse(TellomiChatLock.acceptable("12ab"));
        assertFalse(TellomiChatLock.acceptable(null));
        assertTrue(TellomiChatLock.acceptable("1234"));
        assertTrue(TellomiChatLock.acceptable("123456"));
    }

    @Test
    public void hashMatchesOnlyTheSamePinAndSalt() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        TellomiChatLock.Record record = TellomiChatLock.seal("1357", salt, null);
        assertTrue(TellomiChatLock.matches(record, "1357"));
        assertFalse(TellomiChatLock.matches(record, "1358"));
        assertFalse(record.biometric);

        byte[] other = salt.clone();
        other[0] ^= 0x5a;
        TellomiChatLock.Record again = TellomiChatLock.seal("1357", other, "cipher");
        assertNotEquals(record.hashHex, again.hashHex);
        assertTrue(again.biometric);
    }

    @Test
    public void previewStaysMaskedWhileTheLockExists() {
        assertEquals(TellomiChatLock.PREVIEW, TellomiChatLock.preview("hello", true));
        assertEquals("hello", TellomiChatLock.preview("hello", false));
    }

    @Test
    public void forgottenPinNeedsTheAppPasscode() {
        assertFalse(TellomiChatLock.mayClear(false, true));
        assertFalse(TellomiChatLock.mayClear(true, false));
        assertTrue(TellomiChatLock.mayClear(true, true));
    }

    @Test
    public void recordRoundTripDropsABiometricFlagWithoutCiphertext() {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        TellomiChatLock.Record record = TellomiChatLock.seal("2468", salt, "aa");
        TellomiChatLock.Record back = TellomiChatLock.parse(TellomiChatLock.serialize(record));
        assertTrue(TellomiChatLock.matches(back, "2468"));
        assertTrue(back.biometric);
        TellomiChatLock.Record plain = TellomiChatLock.parse(record.saltHex + "\n" + record.hashHex + "\n1\n");
        assertFalse(plain.biometric);
        assertNull(TellomiChatLock.parse(null));
        assertNull(TellomiChatLock.parse("short"));
    }
}
