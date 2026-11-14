package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiLoginCountriesTest {

    @Test
    public void releasePickerHidesHiddenCountries() {
        assertFalse(TellomiLoginCountries.visibleInLoginPicker(true, false));
        assertTrue(TellomiLoginCountries.visibleInLoginPicker(false, false));
    }

    @Test
    public void debugPickerKeepsHiddenCountries() {
        assertTrue(TellomiLoginCountries.visibleInLoginPicker(true, true));
        assertTrue(TellomiLoginCountries.visibleInLoginPicker(false, true));
    }

    @Test
    public void loginListUsesTheHelper() throws Exception {
        String login = read("src/main/java/org/telegram/ui/LoginActivity.java");
        int load = login.indexOf("private void loadCountries()");
        assertTrue(load >= 0);
        String body = login.substring(load, login.indexOf("public void updateColors()", load));
        assertTrue(body.contains("TellomiLoginCountries.visibleInLoginPicker(c.hidden, BuildVars.DEBUG_VERSION)"));
        assertTrue(body.contains("continue;"));
    }

    private static String read(String relative) throws Exception {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            Path candidate = dir.resolve("TMessagesProj").resolve(relative);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("missing " + relative);
    }
}
