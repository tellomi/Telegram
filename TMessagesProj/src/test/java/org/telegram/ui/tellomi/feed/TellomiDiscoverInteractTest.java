package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Polls, the four forward actions, and discover notification switches (M3 / D-10, D-14, D-18). */
public class TellomiDiscoverInteractTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";

    @Test
    public void forwardMenuIsTheFourPublicActions() {
        assertTrue(TellomiDiscoverShare.actions(false).isEmpty());
        assertEquals(TellomiDiscoverShare.ACTIONS, TellomiDiscoverShare.actions(true));
        assertEquals("https://tell.cc/m#post-9", TellomiDiscoverShare.publicUrl("tell.cc", "post-9"));
        assertNull(TellomiDiscoverShare.publicUrl("tell.cc", "../no"));
        assertNull(TellomiDiscoverShare.publicUrl("", "post-9"));
    }

    @Test
    public void repostUsesTheEnginePut() {
        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.repost("post-9"), "jwt");
        assertEquals("PUT", call.method);
        assertEquals("/v1/feed/posts/post-9/repost", call.path());
        assertNull(call.body);
        assertTrue(call.authorizes);
        assertNull(TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.repost("../x"), "jwt"));
        TellomiDiscover.Outcome done = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.repost("post-9"),
            new TellomiDiscover.HttpResult(204, new byte[0])
        );
        assertEquals(TellomiDiscover.Decoded.Kind.DONE, done.value.kind);
    }

    @Test
    public void quoteAndPollStayOffAPlainPost() {
        byte[] plain = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null);
        TellomiJson object = TellomiJson.parse(plain);
        assertEquals(new HashSet<String>(Arrays.asList("kind", "text", "visibility", "client_nonce")), object.objectValue.keySet());
        byte[] quote = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, "post-1", null);
        TellomiJson quoted = TellomiJson.parse(quote);
        assertEquals("post-1", quoted.objectValue.get("quote_of").stringValue);
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, "../no", null));
        byte[] poll = TellomiDiscover.publishBody("去哪", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, Arrays.asList("成都", "重庆"));
        TellomiJson polled = TellomiJson.parse(poll);
        assertEquals("成都", polled.objectValue.get("poll").objectValue.get("options").arrayValue.get(0).stringValue);
        assertEquals("重庆", polled.objectValue.get("poll").objectValue.get("options").arrayValue.get(1).stringValue);
        assertNull(polled.objectValue.get("quote_of"));
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, "post-9", Arrays.asList("甲", "乙")));
        assertNull(TellomiDiscover.cleanPoll(Collections.singletonList("only")));
        assertEquals(Arrays.asList("a", "b", "c", "d", "e"), TellomiDiscover.cleanPoll(Arrays.asList("a", " ", "b", "c", "d", "e")));
        assertNull(TellomiDiscover.cleanPoll(Arrays.asList("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11")));
        StringBuilder longOption = new StringBuilder();
        for (int i = 0; i < 41; i++) {
            longOption.append("字");
        }
        assertNull(TellomiDiscover.cleanPoll(Arrays.asList("甲", longOption.toString())));
        assertEquals(40, TellomiDiscover.POLL_OPTION_TEXT_LIMIT);
        assertEquals(10, TellomiDiscover.POLL_OPTION_MAX);
    }

    @Test
    public void cardKeepsThePollAndShareableFlag() {
        String json = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"shareable\":true,\"card\":{\"text\":\"去哪\",\"poll\":{\"options\":[\"成都\",\"重庆\"]}},\"poll\":{\"counts\":[2,0],\"voted\":[0]},\"stats\":{\"reposts\":4}}]}";
        TellomiDiscover.Card card = TellomiDiscoverParse.cards(json.getBytes(StandardCharsets.UTF_8)).get(0);
        assertTrue(card.shareable);
        assertEquals(2, card.poll.options.size());
        assertEquals("成都", card.poll.options.get(0).text);
        assertEquals(2, card.poll.options.get(0).votes);
        assertEquals(0, card.poll.options.get(1).votes);
        assertEquals(Collections.singletonList(0), card.poll.voted);
        TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(card);
        assertTrue(post.shareable);
        TellomiDiscover.PostSummary summary = TellomiDiscover.postSummary(post, TellomiDiscover.Copy.resolve("zh"), true);
        assertTrue(summary.body.contains("成都 2"));
        assertTrue(summary.body.contains("重庆"));
        String open = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"card\":{\"poll\":{\"options\":[\"甲\",\"乙\"]}},\"poll\":{\"counts\":[2,0],\"voted\":[]}}]}";
        assertTrue(TellomiDiscoverParse.cards(open.getBytes(StandardCharsets.UTF_8)).get(0).poll.voted.isEmpty());
        String legacy = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"card\":{\"poll\":{\"options\":[{\"text\":\"甲\",\"votes\":2,\"voted\":true},{\"label\":\"重庆\",\"vote_count\":1,\"voted_by_me\":false}]}}}]}";
        assertNull(TellomiDiscoverParse.cards(legacy.getBytes(StandardCharsets.UTF_8)).get(0).poll);
        TellomiDiscover.Poll updated = TellomiDiscover.applyVote(post.poll, new TellomiDiscover.VoteTally(Arrays.asList(3, 1), Collections.singletonList(0)));
        assertEquals(3, updated.options.get(0).votes);
        assertEquals(1, updated.options.get(1).votes);
        assertEquals(Collections.singletonList(0), updated.voted);
        TellomiDiscover.HttpCall vote = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.vote("post-9", 1), "jwt");
        assertEquals("PUT", vote.method);
        assertEquals("/v1/feed/posts/post-9/vote", vote.path());
        TellomiJson voteJson = TellomiJson.parse(vote.body);
        assertNull(voteJson.objectValue.get("option"));
        assertEquals(1, (int) voteJson.objectValue.get("options").arrayValue.get(0).numberValue);
        assertNull(TellomiDiscover.voteBody(-1));
        TellomiDiscover.Outcome empty = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.vote("post-9", 0),
            new TellomiDiscover.HttpResult(200, "{}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.UNREADABLE, empty.error);
        TellomiDiscover.Outcome blank = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.vote("post-9", 0),
            new TellomiDiscover.HttpResult(200, new byte[0])
        );
        assertEquals(TellomiDiscover.ClientError.UNREADABLE, blank.error);
        TellomiDiscover.Outcome voted = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.vote("post-9", 0),
            new TellomiDiscover.HttpResult(200, "{\"counts\":[3,1],\"voted\":[0]}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.Decoded.Kind.VOTED, voted.value.kind);
        assertEquals(Arrays.asList(3, 1), voted.value.vote.counts);
        assertEquals(Collections.singletonList(0), voted.value.vote.voted);
    }

    @Test
    public void deletedQuoteCopyStaysOnTheCard() {
        String json = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"shareable\":false,\"card\":{\"text\":\"引用\"},\"quoted\":{\"id\":\"post-1\",\"state\":\"deleted\"}}]}";
        TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(TellomiDiscoverParse.cards(json.getBytes(StandardCharsets.UTF_8)).get(0));
        assertFalse(post.shareable);
        assertTrue(TellomiDiscoverShare.actions(post.shareable).isEmpty());
        assertTrue(TellomiDiscover.postSummary(post, TellomiDiscover.Copy.resolve("zh"), false).body.contains("原动态已被作者删除"));
    }

    @Test
    public void notificationPageAndPushSwitch() {
        String json = "{\"items\":[{\"id\":\"ntf-01\",\"kind\":\"like\",\"actor_name\":\"林\",\"post_id\":\"post-9\",\"text\":\"\"},{\"event_id\":\"ntf-02\",\"kind\":\"comment\",\"actor\":{\"display_name\":\"安\"},\"post_id\":\"post-8\",\"body\":\"好看\"}],\"cursor\":\"next\"}";
        TellomiDiscover.NoticePage page = TellomiDiscoverParse.notices(json.getBytes(StandardCharsets.UTF_8));
        assertEquals(2, page.items.size());
        assertEquals("like", page.items.get(0).kind);
        assertEquals("林", page.items.get(0).actorName);
        assertEquals("post-9", page.items.get(0).postId);
        assertEquals("安", page.items.get(1).actorName);
        assertEquals("好看", page.items.get(1).text);
        assertEquals("next", page.cursor);
        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.notifications("next"), "jwt");
        assertEquals("GET", call.method);
        assertEquals("/v1/feed/me/notifications", call.path());
        assertEquals("next", call.queryItems().get(0).value);
        TellomiDiscover.Outcome decoded = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.notifications(null),
            new TellomiDiscover.HttpResult(200, json.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.Decoded.Kind.NOTICES, decoded.value.kind);
        assertTrue(TellomiDiscoverNotify.pushesEnabled(-1, 0, 1_000));
        assertTrue(TellomiDiscoverNotify.pushesEnabled(0, 0, 1_000));
        assertFalse(TellomiDiscoverNotify.pushesEnabled(2, 0, 1_000));
        assertFalse(TellomiDiscoverNotify.pushesEnabled(3, 2_000, 1_000));
        assertTrue(TellomiDiscoverNotify.pushesEnabled(3, 1_000, 1_000));
        assertEquals(777005L, TellomiDiscoverNotify.OFFICIAL_USER_ID);
        assertEquals("faxian", TellomiDiscoverNotify.USERNAME);
    }

    @Test
    public void discoverPushOpensThePostAndStaysQuietWhenMuted() {
        TellomiDiscoverPush.Event event = TellomiDiscoverPush.from("DISCOVER_COMMENT_TEXT", "1", "comment", "post-9", "林", "好看");
        assertEquals("comment", event.kind);
        assertEquals("post-9", event.postId);
        assertEquals("liked your post", TellomiDiscoverPush.banner("like", "secret", false, "liked your post"));
        assertEquals("评论了你的帖子：好看", TellomiDiscoverPush.banner("comment", "好看", true, "评论了你的帖子"));
        assertEquals("评论了你的帖子", TellomiDiscoverPush.banner("comment", "好看", false, "评论了你的帖子"));
        assertNull(TellomiDiscoverPush.from("MESSAGE_TEXT", null, null, "post-9", "林", ""));
        assertNull(TellomiDiscoverPush.from("DISCOVER_LIKE", "1", "like", "../no", "林", ""));
        TellomiDiscoverPush.Event follow = TellomiDiscoverPush.from("DISCOVER_FOLLOW", null, null, null, "林", "");
        assertEquals("follow", follow.kind);
        assertNull(follow.postId);
    }
}
