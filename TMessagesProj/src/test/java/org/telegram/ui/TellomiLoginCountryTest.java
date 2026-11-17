package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Fresh login shows +86 and does not insert a sample number.
 * The country picker stays.
 */
public class TellomiLoginCountryTest {

    private static final String US_SIM_LINE = "+1 555 123 4567";

    @Test
    public void freshLoginIsChinaEvenWhenTheSimIsUs() {
        assertTrue(TellomiLoginCountry.useChinaDefault(true));
        assertFalse(TellomiLoginCountry.useChinaDefault(false));
        assertEquals("CN", TellomiLoginCountry.ISO);
        assertEquals("86", TellomiLoginCountry.defaultCallingCode("US", "US"));
        assertEquals("86", TellomiLoginCountry.defaultCallingCode("us", null));
        assertEquals("86", TellomiLoginCountry.defaultCallingCode(null, "US"));
        assertEquals("86", TellomiLoginCountry.defaultCallingCode("CN", "CN"));
        assertEquals("86", TellomiLoginCountry.CALLING_CODE);
    }

    @Test
    public void freshLoginDoesNotInsertTheSimSampleNumber() {
        assertTrue(TellomiLoginCountry.skipLineNumberPrefill(true));
        assertFalse(TellomiLoginCountry.skipLineNumberPrefill(false));
        assertEquals("", TellomiLoginCountry.nationalNumberPrefill(true, US_SIM_LINE));
        assertEquals("", TellomiLoginCountry.nationalNumberPrefill(true, "15551234567"));
        assertEquals("", TellomiLoginCountry.nationalNumberPrefill(true, null));
        assertEquals("", TellomiLoginCountry.nationalNumberPrefill(true, ""));
        assertFalse(TellomiLoginCountry.nationalNumberPrefill(true, US_SIM_LINE).contains("555"));

        assertEquals("15551234567", TellomiLoginCountry.nationalNumberPrefill(false, US_SIM_LINE));
    }

    @Test
    public void phoneScreenSetsChinaAndStillLetsTheUserChangeCountry() throws Exception {
        String countries = read("src/main/assets/countries.txt");
        assertTrue(countries.contains("86;CN;China"));
        assertTrue(countries.contains("1;US;USA"));

        String login = read("src/main/java/org/telegram/ui/LoginActivity.java");
        assertTrue(login.contains("TellomiLoginCountry.useChinaDefault(activityMode == MODE_LOGIN)"));
        assertTrue(login.contains("codeField.setText(TellomiLoginCountry.defaultCallingCode(null, null))"));
        assertTrue(login.contains("selectCountry(country)"));
        assertTrue(login.contains("new CountrySelectActivity(true, countriesArray)"));
        assertTrue(login.contains("TL_help_getNearestDc"));
        assertFalse(login.contains("555 123 4567"));
        assertFalse(login.contains("5551234567"));

        String fill = method(login, "public void fillNumber");
        assertTrue(fill.contains("TellomiLoginCountry.skipLineNumberPrefill(activityMode == MODE_LOGIN)"));
        assertTrue(fill.indexOf("skipLineNumberPrefill") < fill.indexOf("getLine1Number"));
    }

    private static String method(String source, String name) {
        int start = source.indexOf(name);
        assertTrue(name + " is missing", start >= 0);
        int brace = source.indexOf('{', start);
        int depth = 0;
        for (int i = brace; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(start, i + 1);
                }
            }
        }
        throw new AssertionError(name + " has no closing brace");
    }

    private static String read(String relative) {
        try {
            return new String(Files.readAllBytes(repoRoot().resolve("TMessagesProj").resolve(relative)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(relative, e);
        }
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle")) && Files.exists(dir.resolve("TMessagesProj"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Paths.get("").toAbsolutePath());
    }
}
