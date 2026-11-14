package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TellomiClearHistoryTest {

    @Test
    public void wholePrivateHistoryUsesTheClearWording() {
        assertEquals(TellomiClearHistory.ALSO_CLEAR, TellomiClearHistory.alsoForUserKey(true, true));
        assertEquals(TellomiClearHistory.ALSO_DELETE, TellomiClearHistory.alsoForUserKey(false, true));
        assertEquals(TellomiClearHistory.ALSO_DELETE, TellomiClearHistory.alsoForUserKey(true, false));
    }

    @Test
    public void daysDialogAppliesTheClearKey() throws Exception {
        String alerts = read("src/main/java/org/telegram/ui/Components/AlertsCreator.java");
        int method = alerts.indexOf("public static void createClearDaysDialogAlert");
        assertTrue(method >= 0);
        String body = alerts.substring(method, alerts.indexOf("public static void createCallDialogAlert", method));
        assertTrue(body.contains("TellomiClearHistory.alsoForUserKey(days == -1, user != null)"));
        assertTrue(body.contains("R.string.ClearHistoryOptionAlso"));
        assertTrue(read("src/main/res/values-zh/strings.xml").contains("同时为 %1$s 清空历史"));
        assertTrue(read("src/main/res/values-zh-rCN/strings.xml").contains("同时为 %1$s 清空历史"));
        assertTrue(read("src/main/res/values-zh-rTW/strings.xml").contains("同時為 %1$s 清空歷史"));
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
