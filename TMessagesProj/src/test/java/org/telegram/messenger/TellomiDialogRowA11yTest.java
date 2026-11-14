package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TellomiDialogRowA11yTest {

    @Test
    public void rowTextKeepsNameMessageTimeAndUnread() {
        String text = TellomiDialogRowA11y.rowText("Ada", "see you", "18:40", 3);
        assertTrue(text.contains("Ada"));
        assertTrue(text.contains("see you"));
        assertTrue(text.contains("18:40"));
        assertTrue(text.contains("3"));
        assertFalse(TellomiDialogRowA11y.rowText("Ada", "see you", "18:40", 0).contains("\n0"));
    }

    @Test
    public void missingPiecesAreSkipped() {
        assertTrue(TellomiDialogRowA11y.rowText("Ada", "  ", null, 0).equals("Ada"));
        assertTrue(TellomiDialogRowA11y.containsCount("3 new messages", 3));
        assertFalse(TellomiDialogRowA11y.containsCount("new messages", 3));
    }

    @Test
    public void dialogCellPublishesTheDescriptionOnTheNode() throws Exception {
        String cell = read("src/main/java/org/telegram/ui/Cells/DialogCell.java");
        int node = cell.indexOf("public void onInitializeAccessibilityNodeInfo");
        assertTrue(node >= 0);
        String nodeBody = cell.substring(node, cell.indexOf("public void onPopulateAccessibilityEvent", node));
        assertTrue(nodeBody.contains("accessibilityDescription()"));
        assertTrue(nodeBody.contains("info.setContentDescription(description)"));
        assertTrue(nodeBody.contains("info.setText(description)"));
        assertTrue(cell.contains("setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES)"));
        assertTrue(cell.contains("TellomiDialogRowA11y.containsCount"));
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
