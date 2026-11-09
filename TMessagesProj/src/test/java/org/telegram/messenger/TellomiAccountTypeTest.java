package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The system contacts account must stay the installed package, or an overlay drops it. */
public class TellomiAccountTypeTest {

    @Test
    public void authenticatorMatchesTheInstalledPackage() throws Exception {
        String auth = read("src/main/res/xml/auth.xml");
        String sync = read("src/main/res/xml/sync_contacts.xml");
        String menu = read("src/main/res/xml/auth_menu.xml");
        String kinds = read("src/main/res/xml/contacts.xml");
        String code = read("src/main/java/org/telegram/messenger/ContactsController.java");
        String vars = read("src/main/java/org/telegram/messenger/BuildVars.java");

        assertTrue(vars.contains("ACCOUNT_TYPE = \"app.tellomi.chat\""));
        assertTrue(auth.contains("android:accountType=\"app.tellomi.chat\""));
        assertTrue(sync.contains("android:accountType=\"app.tellomi.chat\""));
        assertTrue(menu.contains("android:targetPackage=\"app.tellomi.chat\""));
        assertTrue(kinds.contains("vnd.app.tellomi.chat.profile"));
        assertTrue(kinds.contains("vnd.app.tellomi.chat.call"));
        assertTrue(kinds.contains("vnd.app.tellomi.chat.call.video"));
        assertTrue(code.contains("getAccountsByType(BuildVars.ACCOUNT_TYPE)"));
        assertTrue(code.contains("new Account(\"\" + getUserConfig().getClientUserId(), BuildVars.ACCOUNT_TYPE)"));
        assertFalse(auth.contains("android:accountType=\"org.telegram.messenger\""));
        assertFalse(sync.contains("android:accountType=\"org.telegram.messenger\""));
        assertFalse(code.contains("getAccountsByType(\"org.telegram.messenger\")"));
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get(relative);
        if (!Files.isRegularFile(path)) {
            path = Paths.get("TMessagesProj").resolve(relative);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
