package org.telegram.ui;

import org.junit.Test;
import org.telegram.messenger.feed.FeedContract;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiGroupFeedTest {

    @Test
    public void addedUserIsListedEvenWhenTheContactFlagIsMissing() {
        assertTrue(TellomiGroupContacts.includeAddedUser(7L, 7L, false, false));
        assertFalse(TellomiGroupContacts.includeAddedUser(7L, 7L, true, true));
        assertFalse(TellomiGroupContacts.includeAddedUser(7L, 8L, false, false));
        assertTrue(TellomiGroupContacts.includeAddedUser(7L, 8L, true, false));
    }

    @Test
    public void groupScreenReloadsAfterContactsLoad() throws Exception {
        String group = read("src/main/java/org/telegram/ui/GroupCreateActivity.java");
        assertTrue(group.contains("adapter.reloadContacts()"));
        assertTrue(group.contains("private void fillContacts()"));
        String contacts = read("src/main/java/org/telegram/messenger/ContactsController.java");
        assertTrue(contacts.contains("TellomiGroupContacts.includeAddedUser"));
        assertTrue(contacts.contains("u.contact = true"));
    }

    @Test
    public void ownProfileUsesMePosts() {
        assertEquals("/v1/feed/profiles/42/posts", FeedContract.profilePostsPath("42", ""));
        assertEquals("/v1/feed/profiles/42/posts", FeedContract.profilePostsPath("42", "", "7"));
        assertEquals("/v1/feed/me/posts", FeedContract.profilePostsPath("7", "", "7"));
        assertEquals("/v1/feed/me/posts?cursor=c2", FeedContract.profilePostsPath("7", "c2", "7"));
        assertNull(FeedContract.profilePostsPath("", "", "7"));
        assertTrue(FeedContract.isOwnProfile("7", "7"));
        assertFalse(FeedContract.isOwnProfile("8", "7"));
    }

    @Test
    public void missingAudienceOpensThePickerAndEveryoneSendsAPublicPost() {
        assertEquals(FeedContract.PUBLISH_IDLE, FeedContract.publishAction(false, ""));
        assertEquals(FeedContract.PUBLISH_CHOOSE_AUDIENCE, FeedContract.publishAction(true, ""));
        assertEquals(FeedContract.PUBLISH_CHOOSE_AUDIENCE, FeedContract.publishAction(true, null));
        assertEquals("public", FeedContract.visibilityOf("public", false));
        assertEquals(FeedContract.PUBLISH_SEND, FeedContract.publishAction(true, "public"));

        FeedContract.PostRequest request = new FeedContract.PostRequest();
        request.text = "hello";
        request.visibility = "public";
        request.clientNonce = FeedContract.newClientNonce();
        FeedContract.PostBuild built = FeedContract.buildPost(request, null, null);
        assertTrue(built.send);
        assertEquals("public", built.visibility);
        assertFalse(built.body.contains("sendStory"));
    }

    @Test
    public void feedConfirmShakesUntilAVisibilityIsChosen() throws Exception {
        String sheet = read("src/main/java/org/telegram/ui/Stories/recorder/StoryPrivacyBottomSheet.java");
        assertTrue(sheet.contains("if (feedMode && selectedType == 0)"));
        assertTrue(sheet.contains("AndroidUtilities.shakeViewSpring(button, 3.5f)"));
        String composer = read("src/main/java/org/telegram/ui/Feed/FeedComposerActivity.java");
        assertTrue(composer.contains("FeedContract.publishAction"));
        assertTrue(composer.contains("openAudience()"));
        assertTrue(!composer.contains("stories.sendStory"));
        String picker = read("src/main/java/org/telegram/ui/Feed/FeedAudiencePicker.java");
        assertTrue(picker.contains("TYPE_EVERYONE"));
        assertTrue(picker.contains("return \"public\""));
        String controller = read("src/main/java/org/telegram/messenger/feed/FeedController.java");
        assertTrue(controller.contains("profilePostsPath(profileId, refresh ? \"\" : cursor(stream), selfId)"));
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
