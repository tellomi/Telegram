package org.telegram.ui.Feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedProfileTabsTest {

    @Test
    public void existingCopyDoesNotCallStoriesKuaiPai() throws Exception {
        String chinese = read("src/main/res/values-zh-rCN/strings.xml");
        assertTrue(stringValue(chinese, "Moments_Title").equals("动态"));
        assertTrue(stringValue(chinese, "Moments_Story").equals("故事"));
        assertFalse(stringValue(chinese, "MainTabsMenuSettings").contains("快拍"));
        assertFalse(stringValue(chinese, "MainTabsHideLabels").contains("快拍"));
        assertFalse(stringValue(chinese, "MainTabsButtons").contains("快拍"));
        assertFalse(stringValue(chinese, "Moments_Title").contains("快拍"));
        assertFalse(stringValue(chinese, "Moments_Story").contains("快拍"));
    }

    @Test
    public void profileStoriesTabSaysKuaiPaiAndStaysDistinctFromMoments() throws Exception {
        String english = read("src/main/res/values/strings.xml");
        String chinese = read("src/main/res/values-zh-rCN/strings.xml");
        assertTrue(stringValue(english, "ProfileStories").equals("Stories"));
        assertTrue(stringValue(chinese, "ProfileStories").equals("快拍"));
        assertTrue(stringValue(english, "Moments_Profile_Stories").equals("Stories"));
        assertTrue(stringValue(chinese, "Moments_Profile_Stories").equals("快拍"));
        assertTrue(stringValue(english, "Moments_Profile_Posts").equals("Posts"));
        assertTrue(stringValue(chinese, "Moments_Profile_Posts").equals("动态"));
        assertFalse(stringValue(chinese, "ProfileStories").equals(stringValue(chinese, "Moments_Profile_Posts")));
        assertFalse(stringValue(english, "ProfileStories").equals(stringValue(english, "Moments_Profile_Posts")));
        assertTrue(stringValue(english, "ProfileArchivedStories").equals("Archived Posts"));
        assertTrue(stringValue(english, "Moments_Profile_OpenPosts").equals("All posts"));
        assertTrue(stringValue(chinese, "Moments_Profile_OpenPosts").equals("帖子"));
        assertFalse(stringValue(chinese, "Moments_Profile_OpenPosts").equals("动态"));
        assertFalse(stringValue(chinese, "Moments_Profile_OpenPosts").equals("快拍"));
    }

    @Test
    public void storiesTabStaysAndOnlyItsTitleIsReplaced() throws Exception {
        String layout = read("src/main/java/org/telegram/ui/Components/SharedMediaLayout.java");
        String page = read("src/main/java/org/telegram/ui/Feed/FeedProfilePage.java");
        assertFalse(layout.contains("hideNativeStories"));
        assertFalse(layout.contains("FeedProfileTabs"));
        int storiesTitle = layout.indexOf("tabs.get(i).first == TAB_STORIES");
        int momentsInsert = layout.indexOf("TAB_MOMENTS, LocaleController.getString(\"Moments_Profile_Posts\"");
        assertTrue(storiesTitle > 0 && momentsInsert > storiesTitle);
        String block = layout.substring(storiesTitle, momentsInsert);
        assertTrue(block.contains("Moments_Profile_Stories"));
        assertFalse(block.contains("tabs.remove"));
        assertTrue(layout.substring(Math.max(0, storiesTitle - 220), storiesTitle).contains("!hasBotPreviews"));
        assertTrue(page.contains("Moments_Profile_OpenPosts"));
        assertFalse(page.contains("Moments_Profile_Posts"));
    }

    private static String read(String relative) throws Exception {
        Path[] roots = new Path[]{
                Paths.get("TMessagesProj", relative),
                Paths.get(relative)
        };
        for (Path root : roots) {
            if (Files.isRegularFile(root)) {
                return new String(Files.readAllBytes(root), StandardCharsets.UTF_8);
            }
        }
        throw new IllegalStateException("missing " + relative);
    }

    private static String stringValue(String xml, String name) {
        String token = "name=\"" + name + "\">";
        int start = xml.indexOf(token);
        if (start < 0) {
            return "";
        }
        start += token.length();
        return xml.substring(start, xml.indexOf("</string>", start));
    }
}
