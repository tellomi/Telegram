package org.telegram.ui.Feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
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
        assertTrue(layout.contains("FeedProfileTabs.insertIndex"));
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

    @Test
    public void postsTabIsAbsentWithoutPostsAndFollowsStories() {
        ArrayList<Integer> withStories = new ArrayList<>();
        withStories.add(FeedProfileTabs.STORIES_TAB);
        withStories.add(14);
        assertFalse(FeedProfileTabs.show(true, false));
        assertFalse(FeedProfileTabs.show(false, true));
        assertEquals(-1, FeedProfileTabs.insertIndex(withStories, false));
        assertEquals(1, FeedProfileTabs.insertIndex(withStories, FeedProfileTabs.show(true, true)));

        ArrayList<Integer> photosFirst = new ArrayList<>();
        photosFirst.add(0);
        photosFirst.add(FeedProfileTabs.STORIES_TAB);
        photosFirst.add(1);
        assertEquals(2, FeedProfileTabs.insertIndex(photosFirst, true));

        ArrayList<Integer> noStories = new ArrayList<>();
        noStories.add(0);
        noStories.add(1);
        assertEquals(0, FeedProfileTabs.insertIndex(noStories, true));
        assertEquals(8, FeedProfileTabs.STORIES_TAB);
        assertEquals(16, FeedProfileTabs.MOMENTS_TAB);
    }

    @Test
    public void bothProfileTabsDoNotShareThePostsTitle() throws Exception {
        String chinese = read("src/main/res/values-zh-rCN/strings.xml");
        String stories = stringValue(chinese, "Moments_Profile_Stories");
        String posts = stringValue(chinese, "Moments_Profile_Posts");
        assertEquals("快拍", stories);
        assertEquals("快拍", stringValue(chinese, "ProfileStories"));
        assertEquals("动态", posts);
        assertFalse(stories.equals(posts));

        String layout = read("src/main/java/org/telegram/ui/Components/SharedMediaLayout.java");
        int storiesTitle = layout.indexOf("Moments_Profile_Stories");
        int postsTitle = layout.indexOf("Moments_Profile_Posts");
        assertTrue(storiesTitle > 0 && postsTitle > storiesTitle);
        assertTrue(layout.contains("public static final int TAB_STORIES = 8"));
        assertTrue(layout.contains("public static final int TAB_MOMENTS = 16"));
        assertFalse(layout.contains("tabs.add(0, new Pair<>(TAB_MOMENTS"));
        assertTrue(layout.contains("FeedProfileTabs.show"));
        String storiesSlot = layout.substring(storiesTitle, postsTitle);
        assertFalse(storiesSlot.contains("Moments_Profile_Posts"));
    }

    @Test
    public void inTabPagingAndLikesDoNotDelayProfileOpen() throws Exception {
        String layout = read("src/main/java/org/telegram/ui/Components/SharedMediaLayout.java");
        String page = read("src/main/java/org/telegram/ui/Feed/FeedProfilePage.java");
        String controller = read("src/main/java/org/telegram/messenger/feed/FeedController.java");
        String profile = read("src/main/java/org/telegram/ui/ProfileActivity.java");
        assertTrue(page.contains("new FeedListView"));
        assertTrue(page.contains("toggleLike(post)"));
        assertTrue(page.contains("loadProfilePosts(profileId, false)"));
        assertTrue(page.contains("loadProfilePosts(profileId, true)"));
        assertTrue(page.contains("ENTRY_COMMENT"));
        assertTrue(page.contains("setContentPaddingTop"));
        assertTrue(layout.contains("getPagePaddingTop(TAB_MOMENTS)"));
        assertTrue(layout.contains("momentsPage.getListView()"));
        assertFalse(profile.contains("setContentPaddingTop"));
        assertFalse(page.contains("needDelayOpenAnimation"));
        assertFalse(page.contains("resumeDelayedFragmentAnimation"));

        int ensure = controller.indexOf("void ensureProfilePosts");
        int load = controller.indexOf("void loadProfilePosts");
        assertTrue(ensure > 0 && load > ensure);
        String ensureBody = controller.substring(ensure, load);
        assertTrue(ensureBody.contains("runOnUIThread"));
        assertFalse(ensureBody.contains("getJson"));

        int open = profile.indexOf("boolean needDelayOpenAnimation()");
        int media = profile.indexOf("void mediaCountUpdated()");
        assertTrue(open > 0 && media > open);
        String gate = profile.substring(open, media);
        assertFalse(gate.contains("Feed"));
        assertFalse(gate.contains("Moments"));
        assertFalse(gate.contains("TAB_MOMENTS"));
        assertTrue(gate.contains("playProfileAnimation"));
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
