package org.telegram.messenger.feed;

import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * One assertion per visibility row and action, including the viewer's own post.
 * Capability flags override the mirror. Author edit and audience follow the
 * engine routes and do not follow the shareable bit.
 */
public class FeedActionPolicyTest {

    private static final FeedActionPolicy.Action[] REDISTRIBUTE = new FeedActionPolicy.Action[]{
            FeedActionPolicy.Action.REPOST,
            FeedActionPolicy.Action.QUOTE,
            FeedActionPolicy.Action.CHAT_SHARE,
            FeedActionPolicy.Action.COPY_LINK,
            FeedActionPolicy.Action.EXTERNAL_SHARE
    };

    /** visibility, then repost, quote, chat-share, copy link, external share. */
    private static final Object[][] MIRROR = new Object[][]{
            {"public", true, true, true, true, true},
            {"everyone", true, true, true, true, true},
            {"contacts", false, false, false, false, false},
            {"friends", false, false, true, false, false},
            {"close_friends", false, false, false, false, false},
            {"include", false, false, false, false, false},
            {"selected", false, false, false, false, false},
            {"mutual_followers", false, false, true, false, false},
            {"exclude", false, false, false, false, false},
            {"excluded", false, false, false, false, false},
            {"only_me", false, false, false, false, false},
            {"private", false, false, false, false, false},
            {"followers", false, false, false, false, false},
            {"", false, false, false, false, false}
    };

    @Test
    public void everyVisibilityCellMatchesTheMirror() {
        for (int row = 0; row < MIRROR.length; row++) {
            String visibility = (String) MIRROR[row][0];
            for (int own = 0; own < 2; own++) {
                boolean viewerOwns = own == 1;
                for (int column = 0; column < REDISTRIBUTE.length; column++) {
                    boolean want = (Boolean) MIRROR[row][column + 1];
                    FeedActionPolicy.Decision decision = FeedActionPolicy.decide(visibility, "published", false, viewerOwns, REDISTRIBUTE[column], FeedActionPolicy.Flags.NONE);
                    assertEquals(cell(visibility, viewerOwns, REDISTRIBUTE[column]), want, decision.allowed);
                    if (want) {
                        assertEquals("", decision.reasonKey);
                    } else if (FeedActionPolicy.mutualTier(visibility) && (REDISTRIBUTE[column] == FeedActionPolicy.Action.REPOST || REDISTRIBUTE[column] == FeedActionPolicy.Action.QUOTE)) {
                        assertEquals(FeedActionPolicy.REASON_REPOST_MUTUAL, decision.reasonKey);
                    } else if (REDISTRIBUTE[column] == FeedActionPolicy.Action.REPOST || REDISTRIBUTE[column] == FeedActionPolicy.Action.QUOTE) {
                        assertEquals(FeedActionPolicy.REASON_REPOST, decision.reasonKey);
                    } else {
                        assertEquals(FeedActionPolicy.REASON_SHARE, decision.reasonKey);
                    }
                }
                FeedActionPolicy.Decision edit = FeedActionPolicy.decide(visibility, "published", false, viewerOwns, FeedActionPolicy.Action.EDIT, FeedActionPolicy.Flags.NONE);
                FeedActionPolicy.Decision audience = FeedActionPolicy.decide(visibility, "published", false, viewerOwns, FeedActionPolicy.Action.CHANGE_AUDIENCE, FeedActionPolicy.Flags.NONE);
                assertEquals(viewerOwns, edit.allowed);
                assertEquals(viewerOwns, audience.allowed);
            }
        }
    }

    @Test
    public void unavailableAndDeletedDenyEveryAction() {
        String[] states = new String[]{"unavailable", "deleted", "published"};
        boolean[] flags = new boolean[]{false, false, true};
        for (int i = 0; i < states.length; i++) {
            for (int row = 0; row < MIRROR.length; row++) {
                String visibility = (String) MIRROR[row][0];
                for (int own = 0; own < 2; own++) {
                    for (FeedActionPolicy.Action action : FeedActionPolicy.Action.values()) {
                        FeedActionPolicy.Decision decision = FeedActionPolicy.decide(visibility, states[i], flags[i], own == 1, action, FeedActionPolicy.Flags.NONE);
                        assertFalse(cell(visibility, own == 1, action) + " " + states[i], decision.allowed);
                        assertEquals(FeedActionPolicy.REASON_UNAVAILABLE, decision.reasonKey);
                    }
                }
            }
        }
    }

    @Test
    public void specificFlagsOverrideTheMirror() throws Exception {
        JSONObject contacts = new JSONObject("{\"audience\":{\"kind\":\"contacts\"},\"viewer\":{\"can_repost\":true,\"can_quote\":false},\"actions\":{\"chat_share\":true}}");
        FeedActionPolicy.Flags flags = FeedActionPolicy.readFlags(contacts);
        assertTrue(flags.present);
        assertTrue(of("contacts", false, flags, FeedActionPolicy.Action.REPOST).allowed);
        assertFalse(of("contacts", false, flags, FeedActionPolicy.Action.QUOTE).allowed);
        assertTrue(of("contacts", false, flags, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertFalse(of("contacts", false, flags, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertFalse(of("contacts", false, flags, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);

        JSONObject blockedPublic = new JSONObject("{\"audience\":{\"kind\":\"public\"},\"can_quote\":false,\"can_external_share\":false}");
        FeedActionPolicy.Flags pub = FeedActionPolicy.readFlags(blockedPublic);
        assertTrue(of("public", false, pub, FeedActionPolicy.Action.REPOST).allowed);
        assertFalse(of("public", false, pub, FeedActionPolicy.Action.QUOTE).allowed);
        assertEquals(FeedActionPolicy.REASON_REPOST, of("public", false, pub, FeedActionPolicy.Action.QUOTE).reasonKey);
        assertTrue(of("public", false, pub, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertTrue(of("public", false, pub, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertFalse(of("public", false, pub, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);
    }

    @Test
    public void shareableFalseStillAllowsMutualChatShare() throws Exception {
        FeedActionPolicy.Flags mutual = FeedActionPolicy.readFlags(new JSONObject("{\"audience\":{\"kind\":\"mutual_followers\"},\"shareable\":false}"));
        assertFalse(of("mutual_followers", false, mutual, FeedActionPolicy.Action.REPOST).allowed);
        assertFalse(of("mutual_followers", false, mutual, FeedActionPolicy.Action.QUOTE).allowed);
        assertTrue(of("mutual_followers", false, mutual, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertFalse(of("mutual_followers", false, mutual, FeedActionPolicy.Action.COPY_LINK).allowed);
        assertFalse(of("mutual_followers", false, mutual, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);

        FeedActionPolicy.Flags friends = FeedActionPolicy.readFlags(new JSONObject("{\"viewer\":{\"shareable\":false,\"can_chat_share\":false}}"));
        assertFalse(of("friends", true, friends, FeedActionPolicy.Action.CHAT_SHARE).allowed);
        assertTrue(of("friends", true, friends, FeedActionPolicy.Action.EDIT).allowed);
        assertTrue(of("friends", true, friends, FeedActionPolicy.Action.CHANGE_AUDIENCE).allowed);

        FeedActionPolicy.Flags open = FeedActionPolicy.readFlags(new JSONObject("{\"can_share\":true}"));
        for (FeedActionPolicy.Action action : REDISTRIBUTE) {
            assertTrue(action.name(), of("public", false, open, action).allowed);
        }
        FeedActionPolicy.Flags openedMutual = FeedActionPolicy.readFlags(new JSONObject("{\"shareable\":true}"));
        assertTrue(of("friends", false, openedMutual, FeedActionPolicy.Action.REPOST).allowed);
        assertTrue(of("only_me", false, openedMutual, FeedActionPolicy.Action.EXTERNAL_SHARE).allowed);
        FeedActionPolicy.Flags numeric = FeedActionPolicy.readFlags(new JSONObject("{\"can_repost\":0,\"can_quote\":1}"));
        assertFalse(of("public", false, numeric, FeedActionPolicy.Action.REPOST).allowed);
        assertTrue(of("public", false, numeric, FeedActionPolicy.Action.QUOTE).allowed);
    }

    @Test
    public void explicitFlagBeatsATombstone() throws Exception {
        FeedActionPolicy.Flags deleted = FeedActionPolicy.readFlags(new JSONObject("{\"state\":\"deleted\",\"can_copy_link\":true}"));
        assertTrue(of("public", false, deleted, FeedActionPolicy.Action.COPY_LINK, "deleted", false).allowed);
        assertFalse(of("public", true, deleted, FeedActionPolicy.Action.REPOST, "deleted", false).allowed);
        assertEquals(FeedActionPolicy.REASON_UNAVAILABLE, of("public", true, deleted, FeedActionPolicy.Action.REPOST, "deleted", false).reasonKey);
        assertFalse(of("public", true, deleted, FeedActionPolicy.Action.EDIT, "deleted", false).allowed);
    }

    @Test
    public void cardParseKeepsFlagsOnAnUnavailablePost() throws Exception {
        String models = read("messenger/feed/FeedModels.java");
        int early = models.indexOf("o.optString(\"text\", \"\").length() == 0");
        int flags = models.indexOf("actionFlags = FeedActionPolicy.readFlags");
        assertTrue(flags >= 0 && early > flags);
        String activity = read("ui/Feed/FeedActivity.java");
        String detail = read("ui/Feed/FeedDetailActivity.java");
        assertTrue(activity.contains("FeedPostActions.showMoreMenu"));
        assertTrue(activity.contains("FeedPostActions.showShareMenu"));
        assertTrue(detail.contains("FeedPostActions.showMoreMenu"));
        assertTrue(detail.contains("FeedPostActions.showRepostMenu"));
        String menus = read("ui/Feed/FeedPostActions.java");
        assertTrue(menus.contains("Moments_Edit"));
        assertTrue(menus.contains("Moments_ChangeAudience"));
        assertTrue(menus.contains("Moments_ExternalShare"));
        assertFalse(menus.contains("\"Only public"));
    }

    @Test
    public void quoteAndRepostErrorCodesUseMomentsKeys() {
        assertEquals(FeedActionPolicy.REASON_QUOTE_UNAVAILABLE, FeedActionPolicy.reasonKeyForError("quote_unavailable"));
        assertEquals(FeedActionPolicy.REASON_QUOTE_INVALID, FeedActionPolicy.reasonKeyForError("invalid_quote"));
        assertEquals(FeedActionPolicy.REASON_REPOST, FeedActionPolicy.reasonKeyForError("not_shareable"));
        assertEquals(FeedActionPolicy.REASON_UNAVAILABLE, FeedActionPolicy.reasonKeyForError("not_found"));
        assertEquals("", FeedActionPolicy.reasonKeyForError("rate_limited"));
        assertEquals("", FeedActionPolicy.reasonKeyForError(null));
    }

    @Test
    public void missingFlagsStayOnTheMirror() throws Exception {
        FeedActionPolicy.Flags flags = FeedActionPolicy.readFlags(new JSONObject("{\"id\":\"p\",\"audience\":{\"kind\":\"close_friends\"},\"viewer\":{\"can_comment\":true}}"));
        assertFalse(flags.present);
        FeedActionPolicy.Decision chat = of("close_friends", false, flags, FeedActionPolicy.Action.CHAT_SHARE);
        assertFalse(chat.allowed);
        assertEquals(FeedActionPolicy.REASON_SHARE, chat.reasonKey);
    }

    private static FeedActionPolicy.Decision of(String visibility, boolean own, FeedActionPolicy.Flags flags, FeedActionPolicy.Action action) {
        return of(visibility, own, flags, action, "published", false);
    }

    private static FeedActionPolicy.Decision of(String visibility, boolean own, FeedActionPolicy.Flags flags, FeedActionPolicy.Action action, String state, boolean unavailable) {
        return FeedActionPolicy.decide(visibility, state, unavailable, own, action, flags);
    }

    private static String cell(String visibility, boolean own, FeedActionPolicy.Action action) {
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
