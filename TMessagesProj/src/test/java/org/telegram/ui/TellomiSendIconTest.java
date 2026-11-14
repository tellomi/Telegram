package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TellomiSendIconTest {

    @Test
    public void composerUsesAnUpArrowAndScheduleKeepsTheClock() {
        assertEquals("tellomi_send_up", TellomiSendIcon.composerDrawableName(false));
        assertEquals("input_schedule", TellomiSendIcon.composerDrawableName(true));
    }

    @Test
    public void arrowPointsUp() throws Exception {
        String xml = read("src/main/res/drawable/tellomi_send_up.xml");
        assertTrue(xml.contains("M11,20L11,7.83"));
        assertTrue(xml.contains("L12,4"));
    }

    @Test
    public void composerButtonUsesTheHelper() throws Exception {
        String enter = read("src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java");
        int button = enter.indexOf("sendButton = new SendButton");
        assertTrue(button >= 0);
        String line = enter.substring(button, enter.indexOf(") {", button));
        assertTrue(line.contains("TellomiSendIcon.composerDrawable(isInScheduleMode())"));
        assertTrue(!line.contains("send_plane_24"));
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
