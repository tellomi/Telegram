package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Message-bubble accessibility time. The zh-Hans langpack values are the ones
 * {@code langpack.getLangPack} serves: AccDescrSentDate “已发送于 %s”, TodayAt “于”.
 */
public class TellomiAccDescrTest {

    private static final String TIME = "12:34";

    @Test
    public void simplifiedSentDateKeepsOneYu() {
        String old = String.format("已发送于 %s", "于 " + TIME);
        assertTrue(old.contains("于 于"));

        String spoken = TellomiAccDescr.sentOrReceivedAt("已发送于 %s", "于", TIME);
        assertEquals("已发送于 12:34", spoken);
        assertFalse(spoken.contains("于于"));
        assertFalse(spoken.contains("于 于"));
    }

    @Test
    public void simplifiedSentDateWithoutSpaceBeforePlaceholderDoesNotDouble() {
        String spoken = TellomiAccDescr.sentOrReceivedAt("已发送于%s", "于", TIME);
        assertEquals("已发送于 12:34", spoken);
        assertFalse(spoken.contains("于于"));
    }

    @Test
    public void simplifiedReceivedDateStillSaysYu() {
        assertEquals("已收到 于 12:34", TellomiAccDescr.sentOrReceivedAt("已收到 %s", "于", TIME));
    }

    @Test
    public void traditionalSentAndReceived() {
        assertEquals("傳送於 12:34", TellomiAccDescr.sentOrReceivedAt("傳送於 %s", "於", TIME));
        assertEquals("收到了 於 12:34", TellomiAccDescr.sentOrReceivedAt("收到了 %s", "於", TIME));
        assertEquals("傳送於 12:34", TellomiAccDescr.sentOrReceivedAt("傳送於%s", "於", TIME));
        assertFalse(TellomiAccDescr.sentOrReceivedAt("傳送於 %s", "於", TIME).contains("於 於"));
        assertFalse(TellomiAccDescr.sentOrReceivedAt("傳送於%s", "於", TIME).contains("於於"));
    }

    @Test
    public void englishStillIncludesAt() {
        assertEquals("Sent at 12:34", TellomiAccDescr.sentOrReceivedAt("Sent %s", "at", TIME));
        assertEquals("Received at 12:34", TellomiAccDescr.sentOrReceivedAt("Received %s", "at", TIME));
        assertEquals("Sent at 12:34", TellomiAccDescr.sentOrReceivedAt("Sent at %s", "at", TIME));
    }

    @Test
    public void shortLatinPrepositionIsNotStrippedFromTheWordBeforeIt() {
        assertEquals("Chat at 12:34", TellomiAccDescr.sentOrReceivedAt("Chat %s", "at", TIME));
        assertEquals("Para a 12:34", TellomiAccDescr.sentOrReceivedAt("Para %s", "a", TIME));
    }

    @Test
    public void bubbleAccessibilityUsesTheHelper() throws Exception {
        String cell = read("src/main/java/org/telegram/ui/Cells/ChatMessageCell.java");
        int method = cell.indexOf("public void onInitializeAccessibilityNodeInfo");
        assertTrue(method >= 0);
        String body = cell.substring(method).replaceAll("\\s+", " ");
        assertTrue(body.contains("TellomiAccDescr.sentOrReceivedAt( getString(\"AccDescrSentDate\""));
        assertTrue(body.contains("TellomiAccDescr.sentOrReceivedAt( getString(\"AccDescrReceivedDate\""));
        assertFalse(body.contains("TodayAt\", R.string.TodayAt) + \" \" + currentTimeString"));
        assertTrue(body.contains("formatString(\"AccDescrScheduledDate\""));
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
