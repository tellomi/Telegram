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
    public void momentsTabReplacesNativeStoriesOnOtherUserProfiles() {
        assertTrue(FeedProfileTabs.showsMoments(true, false, false, false));
        assertTrue(FeedProfileTabs.hidesNativeStories(true, false));

        assertFalse(FeedProfileTabs.showsMoments(true, false, false, true));
        assertFalse(FeedProfileTabs.hidesNativeStories(false, false));
        assertFalse(FeedProfileTabs.showsMoments(true, false, true, false));
        assertFalse(FeedProfileTabs.showsMoments(true, true, false, false));
        assertFalse(FeedProfileTabs.showsMoments(false, false, false, false));
        assertFalse(FeedProfileTabs.hidesNativeStories(true, true));
    }

    @Test
    public void followingLineStaysBelowTheProfileInfoForEveryInset() {
        int[] statusBars = {0, 24, 47, 62};
        int[] cutouts = {0, 28, 44, 59};
        int[] navigationBars = {0, 24, 48};
        int[] topPanels = {0, 38, 76, 112};
        int[] infoHeights = {0, 48, 180, 420, 800};
        int contentTop = momentsContentTop();
        assertTrue(contentTop >= 0);
        for (int status : statusBars) {
            for (int cutout : cutouts) {
                for (int navigation : navigationBars) {
                    for (int panel : topPanels) {
                        for (int info : infoHeights) {
                            int infoBottom = status + cutout + info;
                            int sharedMediaTop = infoBottom;
                            assertFalse(FeedProfileTabs.followingCoversInfo(infoBottom, sharedMediaTop, contentTop));
                            assertFalse(FeedProfileTabs.followingCoversInfo(infoBottom, sharedMediaTop, contentTop + panel + navigation));
                        }
                    }
                }
            }
        }
        assertTrue(FeedProfileTabs.followingCoversInfo(240, 240, -88));
        assertTrue(FeedProfileTabs.followingCoversInfo(240, 240, -1));
    }

    @Test
    public void profileRowDoesNotReuseTheTabTitle() throws Exception {
        String page = read("src/main/java/org/telegram/ui/Feed/FeedProfilePage.java");
        String layout = read("src/main/java/org/telegram/ui/Components/SharedMediaLayout.java");
        assertTrue(page.contains("Moments_Profile_OpenPosts"));
        assertFalse(page.contains("Moments_Profile_Posts"));
        assertTrue(layout.contains("hideNativeStories"));
        assertTrue(layout.contains("hasTab(TAB_MOMENTS)"));
        assertTrue(layout.contains("isAnyStoryPageType"));
        assertTrue(layout.contains("Moments_Profile_Posts"));
        String english = read("src/main/res/values/strings.xml");
        String chinese = read("src/main/res/values-zh-rCN/strings.xml");
        assertFalse(stringValue(english, "Moments_Profile_OpenPosts").equals(stringValue(english, "Moments_Profile_Posts")));
        assertFalse(stringValue(chinese, "Moments_Profile_OpenPosts").equals(stringValue(chinese, "Moments_Profile_Posts")));
        assertTrue(stringValue(chinese, "Moments_Profile_Posts").equals("动态"));
        assertTrue(stringValue(chinese, "Moments_Profile_OpenPosts").equals("帖子"));
    }

    private static int momentsContentTop() {
        try {
            String layout = read("src/main/java/org/telegram/ui/Components/SharedMediaLayout.java");
            int at = layout.indexOf("momentsPage, LayoutHelper.createFrame");
            assertTrue(at > 0);
            int open = layout.indexOf("Gravity.FILL,", at);
            String args = layout.substring(open, layout.indexOf(")", open));
            String[] parts = args.split(",");
            return Integer.parseInt(parts[2].trim());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
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
