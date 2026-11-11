package org.telegram.messenger.feed;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Every visibility row and action, for the viewer's own post and someone else's.
 * shareable is the only card bit. Invented can_* names are ignored.
 */
public class FeedActionPolicyTest {

    private static final FeedActionPolicy.Action[] REDISTRIBUTE = new FeedActionPolicy.Action[]{
            FeedActionPolicy.Action.REPOST,
            FeedActionPolicy.Action.QUOTE,
            FeedActionPolicy.Action.CHAT_SHARE,
            FeedActionPolicy.Action.COPY_LINK,
            FeedActionPolicy.Action.EXTERNAL_SHARE
    };

    /**
     * A published post or article with shareable omitted, so SQL shareable() is true.
     * Columns: repost, quote, chat-share, copy link, external share.
     */
    private static final Object[][] MIRROR = new Object[][]{
            {"public", true, true, true, true, true},
            {"everyone", true, true, true, true, true},
            {"contacts", false, false, false, true, true},
            {"friends", false, false, true, true, true},
            {"close_friends", false, false, false, true, true},
            {"include", false, false, false, true, true},
            {"selected", false, false, false, true, true},
            {"mutual_followers", false, false, true, true, true},
            {"exclude", false, false, false, true, true},
            {"excluded", false, false, false, true, true},
            {"only_me", false, false, false, true, true},
            {"private", false, false, false, true, true},
            {"followers", false, false, false, true, true},
            {"", false, false, false, true, true}
    };

    @Test
    public void everyVisibilityCellMatchesShareableAndRepostable() {
        for (int row = 0; row < MIRROR.length; row++) {
            String visibility = (String) MIRROR[row][0];
            for (int own = 0; own < 2; own++) {
                boolean viewerOwns = own == 1;
                FeedActionPolicy.Input card = published(visibility, viewerOwns);
                for (int column = 0; column < REDISTRIBUTE.length; column++) {
                    boolean want = (Boolean) MIRROR[row][column + 1];
                    FeedActionPolicy.Decision decision = FeedActionPolicy.decide(card, REDISTRIBUTE[column]);
                    assertEquals(label(visibility, viewerOwns, REDISTRIBUTE[column]), want, decision.allowed);
                    if (want) {
                        assertEquals("", decision.reasonKey);
                    } else if ((REDISTRIBUTE[column] == FeedActionPolicy.Action.REPOST || REDISTRIBUTE[column] == FeedActionPolicy.Action.QUOTE)
                            && FeedActionPolicy.mutualTier(visibility)) {
                        assertEquals(FeedActionPolicy.REASON_REPOST_MUTUAL, decision.reasonKey);
                    } else if (REDISTRIBUTE[column] == FeedActionPolicy.Action.REPOST || REDISTRIBUTE[column] == FeedActionPolicy.Action.QUOTE) {
                        assertEquals(FeedActionPolicy.REASON_REPOST, decision.reasonKey);
                    } else {
                        assertEquals(FeedActionPolicy.REASON_SHARE, decision.reasonKey);
                    }
                }
                assertEquals(viewerOwns, FeedActionPolicy.decide(card, FeedActionPolicy.Action.EDIT).allowed);
                assertEquals(viewerOwns, FeedActionPolicy.decide(card, FeedActionPolicy.Action.CHANGE_AUDIENCE).allowed);
            }
        }
    }

    @Test
    public void unavailableAndDeletedDenyEveryAction() {
        String[] states = new String[]{"unavailable", "deleted", "published"};
        boolean[] gone = new boolean[]{false, false, true};
        for (int i = 0; i < states.length; i++) {
            for (int row = 0; row < MIRROR.length; row++) {
                String visibility = (String) MIRROR[row][0];
                for (int own = 0; own < 2; own++) {
                    FeedActionPolicy.Input card = published(visibility, own == 1);
                    card.state = states[i];
                    card.unavailable = gone[i];
                    card.shareable = Boolean.TRUE;
                    for (FeedActionPolicy.Action action : FeedActionPolicy.Action.values()) {
                        FeedActionPolicy.Decision decision = FeedActionPolicy.decide(card, action);
                        assertFalse(label(visibility, own == 1, action) + " " + states[i], decision.allowed);
                        assertEquals(FeedActionPolicy.REASON_UNAVAILABLE, decision.reasonKey);
                    }
                }
            }
        }
    }

    @Test
    public void shareableFalseBlocksRedistributionAndKeepsAuthorEdit() {
        FeedActionPolicy.Input card = published("public", true);
        card.shareable = Boolean.FALSE;
        for (FeedActionPolicy.Action action : REDISTRIBUTE) {
            assertFalse(action.name(), FeedActionPolicy.decide(card, action).allowed);
        }
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.EDIT).allowed);
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.CHANGE_AUDIENCE).allowed);

        FeedActionPolicy.Input friends = published("friends", false);
        friends.shareable = Boolean.TRUE;
        assertFalse(FeedActionPolicy.decide(friends, FeedActionPolicy.Action.REPOST).allowed);
        assertTrue(FeedActionPolicy.decide(friends, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertTrue(FeedActionPolicy.decide(friends, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertTrue(FeedActionPolicy.decide(friends, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);

        FeedActionPolicy.Input mine = published("only_me", true);
        mine.shareable = Boolean.TRUE;
        assertFalse(FeedActionPolicy.decide(mine, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertTrue(FeedActionPolicy.decide(mine, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertTrue(FeedActionPolicy.decide(mine, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);
        assertTrue(FeedActionPolicy.decide(mine, FeedActionPolicy.Action.EDIT).allowed);
    }

    @Test
    public void omittedShareableMirrorsSqlAndIgnoresInventedFlags() throws Exception {
        FeedActionPolicy.Input story = published("public", false);
        story.kind = "story";
        assertFalse(FeedActionPolicy.shareable(story));
        assertFalse(FeedActionPolicy.decide(story, FeedActionPolicy.Action.COPY_LINK).allowed);

        FeedActionPolicy.Input comment = published("public", false);
        comment.kind = "comment";
        assertFalse(FeedActionPolicy.decide(comment, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);

        FeedActionPolicy.Input pending = published("public", true);
        pending.authorOnlyPending = true;
        assertFalse(FeedActionPolicy.decide(pending, FeedActionPolicy.Action.REPOST).allowed);
        assertFalse(FeedActionPolicy.decide(pending, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertTrue(FeedActionPolicy.decide(pending, FeedActionPolicy.Action.EDIT).allowed);

        JSONObject invented = new JSONObject("{\"can_repost\":true,\"can_quote\":true,\"can_chat_share\":true,\"can_copy_link\":true,\"can_external_share\":true,\"can_share\":true,\"actions\":{\"repost\":true}}");
        assertNull(FeedActionPolicy.readShareable(invented));
        FeedActionPolicy.Input contacts = published("contacts", false);
        assertFalse(FeedActionPolicy.decide(contacts, FeedActionPolicy.Action.REPOST).allowed);
        assertFalse(FeedActionPolicy.decide(contacts, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertTrue(FeedActionPolicy.decide(contacts, FeedActionPolicy.Action.COPY_LINK).allowed);

        assertEquals(Boolean.TRUE, FeedActionPolicy.readShareable(new JSONObject("{\"shareable\":true}")));
        assertEquals(Boolean.FALSE, FeedActionPolicy.readShareable(new JSONObject("{\"shareable\":0}")));
        assertEquals(Boolean.TRUE, FeedActionPolicy.readShareable(new JSONObject("{\"shareable\":\"1\"}")));
        assertNull(FeedActionPolicy.readShareable(new JSONObject("{\"viewer\":{\"shareable\":true}}")));
    }

    @Test
    public void explicitShareableBeatsAuthorOnlyPending() {
        FeedActionPolicy.Input card = published("contacts", false);
        card.authorOnlyPending = true;
        card.shareable = Boolean.TRUE;
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertFalse(FeedActionPolicy.decide(card, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertFalse(FeedActionPolicy.decide(card, FeedActionPolicy.Action.QUOTE).allowed);
    }

    @Test
    public void editWindowZeroIsUnlimitedAndPositiveHidesEdit() {
        FeedActionPolicy.Input card = published("only_me", true);
        card.createdAtMillis = 1_000_000L;
        card.nowMillis = card.createdAtMillis + 86_400_000L;
        card.editWindowSeconds = 0;
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.EDIT).allowed);

        card.editWindowSeconds = 900;
        card.nowMillis = card.createdAtMillis + 900_000L;
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.EDIT).allowed);
        card.nowMillis = card.createdAtMillis + 900_001L;
        assertFalse(FeedActionPolicy.decide(card, FeedActionPolicy.Action.EDIT).allowed);
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.CHANGE_AUDIENCE).allowed);
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.COPY_LINK).allowed);

        card.createdAtMillis = 0;
        assertTrue(FeedActionPolicy.decide(card, FeedActionPolicy.Action.EDIT).allowed);
    }

    @Test
    public void configEditWindowAndRepost404() throws Exception {
        int previous = FeedActionPolicy.editWindowSeconds();
        try {
            FeedActionPolicy.noteConfig(new JSONObject("{\"limits\":{\"edit_window_seconds\":900}}"));
            assertEquals(900, FeedActionPolicy.editWindowSeconds());
            FeedActionPolicy.noteConfig(new JSONObject("{\"media_base_url\":\"https://cdn.example\"}"));
            assertEquals(900, FeedActionPolicy.editWindowSeconds());
            FeedActionPolicy.noteConfig(new JSONObject("{\"edit_window_seconds\":0}"));
            assertEquals(0, FeedActionPolicy.editWindowSeconds());
        } finally {
            FeedActionPolicy.setEditWindowSecondsForTest(previous);
        }
        assertEquals(FeedActionPolicy.REASON_REPOST, FeedActionPolicy.reasonKeyForRepostError("not_found"));
        assertEquals(FeedActionPolicy.REASON_REPOST, FeedActionPolicy.reasonKeyForRepostError("http_404"));
        assertEquals(FeedActionPolicy.REASON_UNAVAILABLE, FeedActionPolicy.reasonKeyForError("not_found"));
        assertEquals(FeedActionPolicy.REASON_QUOTE_UNAVAILABLE, FeedActionPolicy.reasonKeyForError("quote_unavailable"));
        assertEquals(FeedActionPolicy.REASON_QUOTE_INVALID, FeedActionPolicy.reasonKeyForError("invalid_quote"));
        assertEquals("", FeedActionPolicy.reasonKeyForError("not_shareable"));
        assertEquals("", FeedActionPolicy.reasonKeyForRepostError("rate_limited"));
    }

    @Test
    public void cardParseReadsOnlyShareable() throws Exception {
        String models = read("messenger/feed/FeedModels.java");
        int early = models.indexOf("o.optString(\"text\", \"\").length() == 0");
        int flags = models.indexOf("shareable = FeedActionPolicy.readShareable");
        assertTrue(flags >= 0 && early > flags);
        assertFalse(models.contains("can_repost"));
        assertFalse(models.contains("can_chat_share"));
        String policy = read("messenger/feed/FeedActionPolicy.java");
        assertFalse(policy.contains("can_repost"));
        assertFalse(policy.contains("can_quote"));
        assertFalse(policy.contains("can_chat_share"));
        assertFalse(policy.contains("can_copy_link"));
        assertFalse(policy.contains("can_external_share"));
        assertFalse(policy.contains("not_shareable"));
        String menus = read("ui/Feed/FeedPostActions.java");
        assertTrue(menus.contains("reasonKeyForRepostError"));
        assertTrue(menus.contains("Moments_Edit"));
        assertTrue(menus.contains("Moments_ChangeAudience"));
        String api = read("messenger/feed/FeedApi.java");
        assertTrue(api.contains("FeedActionPolicy.noteConfig"));
    }

    private static FeedActionPolicy.Input published(String visibility, boolean own) {
        FeedActionPolicy.Input card = new FeedActionPolicy.Input();
        card.visibility = visibility;
        card.state = "published";
        card.kind = "post";
        card.own = own;
        return card;
    }

    private static String label(String visibility, boolean own, FeedActionPolicy.Action action) {
        return visibility + (own ? " own " : " ") + action.name();
    }

    private static String read(String relative) throws Exception {
        Path[] roots = new Path[]{
                Paths.get("src/main/java/org/telegram", relative),
                Paths.get("TMessagesProj/src/main/java/org/telegram", relative)
        };
        for (int i = 0; i < roots.length; i++) {
            if (Files.isRegularFile(roots[i])) {
                return new String(Files.readAllBytes(roots[i]), "UTF-8");
            }
        }
        throw new IllegalStateException("missing " + relative);
    }
}
