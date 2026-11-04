package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiUsernameTest {

    @Test
    public void fourCharactersPass() {
        assertFalse(TellomiUsername.isTooShort("abcd"));
        assertFalse(TellomiUsername.isTooShort("ab_d"));
    }

    @Test
    public void threeCharactersFail() {
        assertTrue(TellomiUsername.isTooShort("abc"));
        assertTrue(TellomiUsername.isTooShort("ab"));
        assertTrue(TellomiUsername.isTooShort(""));
        assertTrue(TellomiUsername.isTooShort(null));
    }
}
