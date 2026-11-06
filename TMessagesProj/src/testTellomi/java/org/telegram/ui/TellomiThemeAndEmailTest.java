package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiThemeAndEmailTest {

    @Test
    public void loginInkStaysReadableInBothModes() {
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_NIGHT, TellomiContrast.FIELD_NIGHT));
        assertTrue(TellomiContrast.readable(TellomiContrast.HINT_NIGHT, TellomiContrast.FIELD_NIGHT));
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_NIGHT, TellomiContrast.PAGE_NIGHT));
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_DAY, TellomiContrast.FIELD_DAY));
        assertTrue(TellomiContrast.readable(TellomiContrast.HINT_DAY, TellomiContrast.FIELD_DAY));
        assertTrue(TellomiContrast.readable(TellomiContrast.DIGIT_DAY, TellomiContrast.PAGE_DAY));
        assertTrue(TellomiContrast.readable(TellomiContrast.iconInk(true), 0xFF1C1C1E));
        assertFalse(TellomiContrast.readable(0xFF111111, 0xFF1C1C1E));
        assertTrue((TellomiContrast.SELECTION_NIGHT >>> 24) < 0xFF);
        assertEquals(TellomiContrast.DIGIT_NIGHT, TellomiContrast.digit(true));
        assertEquals(TellomiContrast.HINT_DAY, TellomiContrast.hint(false));
    }

    @Test
    public void signInEmailIsNotRecoveryAndNotTheAttachFlow() {
        assertFalse(TellomiEmailRoles.countsAsRecovery("ada@tellomi.cn"));
        assertFalse(TellomiEmailRoles.countsAsRecovery(""));
        assertFalse(TellomiEmailRoles.countsAsRecovery(null));
        assertTrue(TellomiEmailRoles.isSignInAddress("Ada@tellomi.cn"));
        assertFalse(TellomiEmailRoles.isSignInAddress("8613800138000"));
        assertEquals("ada@tellomi.cn", TellomiEmailRoles.rememberCandidate(" ada@tellomi.cn ", "other@tellomi.cn", "8613800138000"));
        assertEquals("other@tellomi.cn", TellomiEmailRoles.rememberCandidate("", " other@tellomi.cn ", "8613800138000"));
        assertEquals("ada@tellomi.cn", TellomiEmailRoles.rememberCandidate(null, "", "ada@tellomi.cn"));
        assertEquals("", TellomiEmailRoles.rememberCandidate("", "", "8613800138000"));
    }

    @Test
    public void accountRowDoesNotRewriteSignInEmailAsLoginEmail() throws Exception {
        String login = new String(Files.readAllBytes(find("TMessagesProj/src/main/java/org/telegram/ui/LoginActivity.java").toPath()), StandardCharsets.UTF_8);
        String account = new String(Files.readAllBytes(find("TMessagesProj/src/main/java/org/telegram/ui/UserInfoActivity.java").toPath()), StandardCharsets.UTF_8);
        assertTrue(login.contains("TellomiEmailRoles.rememberCandidate"));
        assertTrue(login.contains("TellomiAttachLoginEmailTitle"));
        assertTrue(account.contains("TellomiEmailRoles.isSignInAddress"));
        assertTrue(account.contains("TellomiSignInEmailNotRecovery"));
        assertEquals(1, count(login, "TellomiAccount.rememberEmail("));
        assertFalse(login.contains("TellomiAccount.rememberEmail(currentAccount, savedEmail)"));
        assertTrue(login.contains("TellomiLoginChrome.applyInput(tellomiCodeField)"));
    }

    private static int count(String text, String needle) {
        int n = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return n;
            }
            n++;
            from = at + needle.length();
        }
    }

    private static java.io.File find(String relative) {
        java.io.File direct = new java.io.File(relative);
        if (direct.isFile()) {
            return direct;
        }
        java.io.File nested = new java.io.File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        String stripped = relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative;
        java.io.File fromModule = new java.io.File("..", stripped);
        if (fromModule.isFile()) {
            return fromModule;
        }
        throw new AssertionError("missing " + relative);
    }
}
