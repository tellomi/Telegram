package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Likes and public text comments on the discover engine. Not a private-chat send. */
public class TellomiDiscoverReactTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";
    private static final String PARENT = "22222222-2222-4222-8222-222222222222";
    private static final String OTHER = "33333333-3333-4333-8333-333333333333";

    @Test
    public void likeUsesAnEmptyBodyAndTheSurface() {
        TellomiDiscover.HttpCall like = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.like("post-9", true, null), "jwt");
        assertEquals("PUT", like.method);
        assertEquals("/v1/feed/posts/post-9/like", like.path());
        assertNull(like.body);
        assertTrue(like.authorizes);
        assertEquals("recommend", like.queryItems().get(0).value);
        TellomiDiscover.HttpCall unlike = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.like("post-9", false, "following"), "jwt");
        assertEquals("DELETE", unlike.method);
        assertEquals("following", unlike.queryItems().get(0).value);
        assertEquals("hot", TellomiDiscoverReact.surface("hot"));
        assertEquals("search", TellomiDiscoverReact.surface("search"));
        assertEquals("profile", TellomiDiscoverReact.surface("profile"));
        assertEquals("recommend", TellomiDiscoverReact.surface(TellomiDiscover.FeedKind.NEARBY));
        assertEquals("following", TellomiDiscoverReact.surface(TellomiDiscover.FeedKind.FOLLOWING));
        TellomiDiscover.Outcome done = TellomiDiscover.decodeHttp(TellomiDiscover.Operation.like("post-9", true, "recommend"), new TellomiDiscover.HttpResult(204, new byte[0]));
        assertEquals(TellomiDiscover.Decoded.Kind.DONE, done.value.kind);
        TellomiDiscover.Outcome again = TellomiDiscover.decodeHttp(TellomiDiscover.Operation.like("post-9", true, "recommend"), new TellomiDiscover.HttpResult(204, new byte[0]));
        assertTrue(again.ok());
        TellomiDiscover.Outcome missing = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.like("post-9", true, "recommend"),
            new TellomiDiscover.HttpResult(404, "{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.NOT_FOUND, missing.error);
        TellomiDiscover.Outcome hidden = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.like("post-9", false, "recommend"),
            new TellomiDiscover.HttpResult(404, "{\"error\":\"feed_hidden\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.HIDDEN, hidden.error);
        assertNull(TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.like("../x", true, "recommend"), "jwt"));
    }

    @Test
    public void cardReadsLikedAndDoesNotShowAHiddenZero() {
        String json = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"me\":{\"liked\":true},\"stats\":{\"likes\":4,\"likes_hidden\":true,\"comments\":2}}]}";
        TellomiDiscover.Card card = TellomiDiscoverParse.cards(json.getBytes(StandardCharsets.UTF_8)).get(0);
        assertTrue(card.liked);
        assertTrue(card.likesHidden);
        assertEquals(4, card.likeCount);
        assertEquals(2, card.commentCount);
        TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(card);
        assertTrue(post.likesHidden);
        String meta = TellomiDiscover.postSummary(post, TellomiDiscover.Copy.resolve("en"), true).meta;
        assertFalse(meta.contains("0"));
        assertFalse(meta.contains("like"));
        String shown = "{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"me\":{\"liked\":false},\"stats\":{\"likes\":3,\"comments\":1}}]}";
        TellomiDiscover.Post visible = TellomiDiscoverParse.displayPost(TellomiDiscoverParse.cards(shown.getBytes(StandardCharsets.UTF_8)).get(0));
        assertFalse(visible.liked);
        assertFalse(visible.likesHidden);
        assertTrue(TellomiDiscover.postSummary(visible, TellomiDiscover.Copy.resolve("en"), true).meta.contains("3"));
    }

    @Test
    public void rapidLikesStayOnOneTargetAndAFailureRestoresTheCount() {
        TellomiDiscoverReact.Like like = TellomiDiscoverReact.Like.start("post-9", false, 2, false, "recommend");
        TellomiDiscoverReact.Like.Request first = like.tap();
        assertTrue(first.on);
        assertEquals(3, like.view().count);
        assertNull(like.tap());
        assertNull(like.tap());
        assertTrue(like.view().liked);
        assertEquals(3, like.view().count);
        assertNull(like.complete(first.generation, first.version, true));
        assertTrue(like.view().liked);
        assertEquals(3, like.view().count);
        assertNull(like.complete(first.generation, first.version, true));

        TellomiDiscoverReact.Like.Request undo = like.tap();
        assertFalse(undo.on);
        TellomiDiscoverReact.Like.Request redo = like.complete(undo.generation, undo.version, false);
        assertNull(redo);
        assertTrue(like.view().liked);
        assertEquals(3, like.view().count);

        TellomiDiscoverReact.Like hidden = TellomiDiscoverReact.Like.start("post-9", false, 0, true, "profile");
        TellomiDiscoverReact.Like.Request hiddenTap = hidden.tap();
        assertTrue(hidden.view().hidden);
        assertEquals(0, hidden.view().count);
        hidden.complete(hiddenTap.generation, hiddenTap.version, true);
        assertTrue(hidden.view().liked);
        assertTrue(hidden.view().hidden);
        assertEquals(0, hidden.view().count);

        TellomiDiscoverReact.Like moved = TellomiDiscoverReact.Like.start("post-9", false, 1, false, "recommend");
        TellomiDiscoverReact.Like.Request stale = moved.tap();
        moved.bind("post-8", false, 5, false, "hot");
        assertNull(moved.complete(stale.generation, stale.version, true));
        assertFalse(moved.view().liked);
        assertEquals(5, moved.view().count);
    }

    @Test
    public void clicksDuringAnUncertainFailureSendTheLatestTargetOnce() throws Exception {
        TellomiDiscoverReact.Like like = TellomiDiscoverReact.Like.start("post-9", false, 2, false, "recommend");
        TellomiDiscoverReact.Like.Request first = like.tap();
        assertNull(like.tap());
        assertNull(like.tap());
        assertTrue(like.view().liked);
        TellomiDiscover.Outcome uncertain = TellomiDiscover.Outcome.failure(TellomiDiscover.ClientError.UNAVAILABLE);
        TellomiDiscoverReact.Like.Request follow = TellomiDiscoverReact.onLikeHttp(like, first, uncertain);
        assertTrue(follow.on);
        assertTrue(follow.automatic);
        assertEquals("PUT", TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.like(follow.postId, follow.on, follow.surface), "jwt").method);
        assertEquals(3, like.view().count);
        assertNull(like.failure());
        assertNull(TellomiDiscoverReact.onLikeHttp(like, follow, uncertain));
        assertFalse(like.view().liked);
        assertEquals(2, like.view().count);
        assertEquals(TellomiDiscover.ClientError.UNAVAILABLE, like.failure());
        assertEquals("Tellomi.Discover.Failed", TellomiDiscoverReact.messageKey(like.failure()));

        TellomiDiscoverReact.Like settled = TellomiDiscoverReact.Like.start("post-9", false, 2, false, "recommend");
        TellomiDiscoverReact.Like.Request again = settled.tap();
        settled.tap();
        settled.tap();
        TellomiDiscoverReact.Like.Request retry = TellomiDiscoverReact.onLikeHttp(settled, again, uncertain);
        TellomiDiscover.Outcome done = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.like("post-9", true, "recommend"),
            new TellomiDiscover.HttpResult(204, new byte[0])
        );
        assertNull(TellomiDiscoverReact.onLikeHttp(settled, retry, done));
        assertTrue(settled.view().liked);
        assertEquals(3, settled.view().count);
        assertNull(settled.failure());

        TellomiDiscoverReact.Like invisible = TellomiDiscoverReact.Like.start("post-9", false, 2, false, "recommend");
        TellomiDiscoverReact.Like.Request hiddenPut = invisible.tap();
        invisible.tap();
        invisible.tap();
        TellomiDiscover.Outcome missing = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.like("post-9", true, "recommend"),
            new TellomiDiscover.HttpResult(404, "{\"error\":\"not_found\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.NOT_FOUND, missing.error);
        assertTrue(TellomiDiscoverReact.permanentLikeFailure(missing.error));
        assertNull(TellomiDiscoverReact.onLikeHttp(invisible, hiddenPut, missing));
        assertFalse(invisible.view().liked);
        assertEquals(2, invisible.view().count);
        assertEquals(TellomiDiscover.ClientError.NOT_FOUND, invisible.failure());

        TellomiDiscoverReact.Like forbidden = TellomiDiscoverReact.Like.start("post-9", false, 2, false, "following");
        TellomiDiscoverReact.Like.Request deniedPut = forbidden.tap();
        forbidden.tap();
        forbidden.tap();
        TellomiDiscover.Outcome denied = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.like("post-9", true, "following"),
            new TellomiDiscover.HttpResult(403, "{\"error\":\"nope\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.UNAUTHORIZED, denied.error);
        assertNull(TellomiDiscoverReact.onLikeHttp(forbidden, deniedPut, denied));
        assertFalse(forbidden.view().liked);
        assertEquals(2, forbidden.view().count);

        TellomiDiscoverReact.Like page = TellomiDiscoverReact.Like.start("post-9", false, 2, false, "recommend");
        TellomiDiscoverReact.Like.Request left = page.tap();
        page.tap();
        page.tap();
        page.close();
        assertNull(TellomiDiscoverReact.onLikeHttp(page, left, done));
        assertFalse(page.view().liked);
        assertEquals(2, page.view().count);
        assertNull(page.failure());

        String screen = read("TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverScreen.java");
        String detail = read("TMessagesProj/tellomi/java/org/telegram/ui/tellomi/feed/TellomiDiscoverPostFragment.java");
        String call = "TellomiDiscover.Operation.like(request.postId, request.on, request.surface)";
        assertTrue(screen.contains(call));
        assertTrue(detail.contains(call));
        assertTrue(screen.contains("TellomiDiscoverReact.onLikeHttp(session, request, outcome)"));
        assertTrue(detail.contains("TellomiDiscoverReact.onLikeHttp(likeSession, request, outcome)"));
        assertTrue(screen.contains("like.close()"));
        assertTrue(detail.contains("likeSession.close()"));
        assertTrue(screen.contains("session.failure()"));
        assertTrue(detail.contains("likeSession.failure()"));
    }

    @Test
    public void commentPostIsTextOnlyAndRetriesTheSameNonce() {
        byte[] body = TellomiDiscoverReact.commentBody("  hello  ", null, NONCE);
        TellomiJson json = TellomiJson.parse(body);
        assertEquals(new HashSet<String>(Arrays.asList("text", "client_nonce")), json.objectValue.keySet());
        assertEquals("hello", json.objectValue.get("text").stringValue);
        assertFalse(json.objectValue.containsKey("media_keys"));
        assertFalse(json.objectValue.containsKey("operation_id"));
        byte[] reply = TellomiDiscoverReact.commentBody("reply", PARENT, NONCE);
        assertEquals(PARENT, TellomiJson.parse(reply).objectValue.get("parent_id").stringValue);
        assertNull(TellomiDiscoverReact.commentBody("", null, NONCE));
        assertNull(TellomiDiscoverReact.commentBody("hi", "not-a-uuid", NONCE));
        assertNull(TellomiDiscoverReact.commentBody("hi", null, "nonce"));
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 501; i++) {
            longText.append("字");
        }
        assertEquals(TellomiDiscover.ClientError.INVALID_TEXT, TellomiDiscoverReact.commentError(longText.toString(), null, NONCE));

        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.comment("post-9", "hello", null, NONCE), "jwt");
        assertEquals("POST", call.method);
        assertEquals("/v1/feed/posts/post-9/comments", call.path());
        assertEquals("application/json", call.contentType);
        String created = "{\"id\":\"" + OTHER + "\",\"state\":\"pending\",\"version\":1,\"held\":false}";
        TellomiDiscover.Outcome first = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.comment("post-9", "hello", null, NONCE),
            new TellomiDiscover.HttpResult(201, created.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.Decoded.Kind.COMMENT_CREATED, first.value.kind);
        assertEquals(OTHER, first.value.commentCreated.id);
        assertEquals("pending", first.value.commentCreated.state);
        assertEquals(1, first.value.commentCreated.version);
        assertEquals(Boolean.FALSE, first.value.commentCreated.held);
        String retry = "{\"id\":\"" + OTHER + "\",\"state\":\"pending\",\"version\":1}";
        TellomiDiscover.Outcome second = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.comment("post-9", "hello", null, NONCE),
            new TellomiDiscover.HttpResult(200, retry.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(OTHER, second.value.commentCreated.id);
        assertEquals(1, second.value.commentCreated.version);
        assertNull(second.value.commentCreated.held);

        TellomiDiscoverReact.Comments thread = new TellomiDiscoverReact.Comments();
        thread.open("post-9");
        TellomiDiscoverReact.Comments.Send send = thread.submit("hello", null, NONCE);
        assertTrue(send.ready());
        thread.failSend(send.generation, send.version);
        TellomiDiscoverReact.Comments.Send againSend = thread.submit("hello", null, "99999999-9999-4999-8999-999999999999");
        assertEquals(NONCE, againSend.nonce);
        assertTrue(thread.finishSend(againSend.generation, againSend.version, second.value.commentCreated));
        assertEquals(0, thread.publicCount());
        assertEquals(1, thread.pendingRows().size());
        assertEquals(OTHER, thread.pendingRows().get(0).id);
    }

    @Test
    public void commentPagesStayInOrderAndDropTheWrongPost() {
        String page = "{\"comments\":["
            + card("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "post-9", "one", null, null)
            + "," + card("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb", "post-8", "wrong", PARENT, null)
            + "," + "{\"id\":\"cccccccc-cccc-4ccc-8ccc-cccccccccccc\",\"state\":\"pending\",\"card\":{\"text\":\"wait\"}}"
            + "," + card("dddddddd-dddd-4ddd-8ddd-dddddddddddd", "post-9", "two", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
            + "],\"cursor\":\"next-page\"}";
        TellomiDiscover.Outcome outcome = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.comments("post-9", null),
            new TellomiDiscover.HttpResult(200, page.getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(2, outcome.value.commentPage.comments.size());
        assertEquals("one", outcome.value.commentPage.comments.get(0).text);
        assertEquals("two", outcome.value.commentPage.comments.get(1).text);
        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", outcome.value.commentPage.comments.get(1).parentId);
        assertEquals("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", outcome.value.commentPage.comments.get(1).rootId);
        assertEquals("next-page", outcome.value.commentPage.cursor);
        TellomiDiscover.HttpCall next = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.comments("post-9", "next-page"), "jwt");
        assertEquals("GET", next.method);
        assertEquals("/v1/feed/posts/post-9/comments", next.path());
        assertEquals("next-page", next.queryItems().get(0).value);
        assertNull(next.rawQuery() == null ? null : (next.rawQuery().contains("limit") ? "limit" : null));

        TellomiDiscoverReact.Comments thread = new TellomiDiscoverReact.Comments();
        TellomiDiscoverReact.Comments.Load first = thread.open("post-9");
        assertTrue(thread.accept(first.generation, first.version, outcome.value.commentPage));
        String duplicate = "{\"comments\":[" + card("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", "post-9", "one", null, null) + "," + card(OTHER, "post-9", "three", null, null) + "]}";
        TellomiDiscover.CommentPage second = TellomiDiscoverParse.commentPage(duplicate.getBytes(StandardCharsets.UTF_8), "post-9");
        TellomiDiscoverReact.Comments.Load more = thread.more();
        assertEquals("next-page", more.cursor);
        assertTrue(thread.accept(more.generation, more.version, second));
        assertEquals(Arrays.asList("one", "two", "three"), texts(thread.visible()));
        assertEquals(3, thread.publicCount());
        assertFalse(thread.hasMore());

        TellomiDiscoverReact.Comments.Load switched = thread.open("post-1");
        assertFalse(thread.accept(first.generation, first.version, outcome.value.commentPage));
        assertEquals(0, thread.publicCount());
        assertEquals("post-1", switched.postId);

        StringBuilder tooMany = new StringBuilder("{\"comments\":[");
        for (int i = 0; i < 51; i++) {
            if (i > 0) {
                tooMany.append(',');
            }
            tooMany.append(card(String.format("44444444-4444-4444-8444-%012d", i), "post-9", "x", null, null));
        }
        tooMany.append("]}");
        assertNull(TellomiDiscoverParse.commentPage(tooMany.toString().getBytes(StandardCharsets.UTF_8), "post-9"));
        TellomiDiscover.Outcome badCursor = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.comments("post-9", "nope"),
            new TellomiDiscover.HttpResult(400, "{\"error\":\"invalid_cursor\"}".getBytes(StandardCharsets.UTF_8))
        );
        assertEquals(TellomiDiscover.ClientError.BAD_CURSOR, badCursor.error);
        assertEquals(TellomiDiscover.ClientError.BAD_CURSOR, TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.comments("post-9", "nope"),
            new TellomiDiscover.HttpResult(400, "{\"error\":\"cursor\"}".getBytes(StandardCharsets.UTF_8))
        ).error);
    }

    @Test
    public void commentErrorsStayOnThePostAndDeleteDoesNotRemoveThePost() {
        assertEquals(TellomiDiscover.ClientError.PROFILE_REQUIRED, error(409, "profile_required"));
        assertEquals(TellomiDiscover.ClientError.INVALID_TEXT, error(400, "invalid_text"));
        assertEquals(TellomiDiscover.ClientError.INVALID_NONCE, error(400, "client_nonce"));
        assertEquals(TellomiDiscover.ClientError.INVALID_PARENT, error(400, "parent"));
        assertEquals(TellomiDiscover.ClientError.COMMENTS_CLOSED, error(403, "comments_closed"));
        assertEquals(TellomiDiscover.ClientError.REPLY_NOT_ALLOWED, error(403, "reply_not_allowed"));
        assertEquals(TellomiDiscover.ClientError.HARASSMENT_GUARD, error(403, "harassment_guard"));
        assertEquals(TellomiDiscover.ClientError.NOT_COMMENTABLE, error(403, "not_commentable"));
        assertEquals(TellomiDiscover.ClientError.NOT_FOUND, error(404, "not_found"));
        assertEquals("Tellomi.Discover.CommentsClosed", TellomiDiscoverReact.messageKey(TellomiDiscover.ClientError.COMMENTS_CLOSED));
        assertEquals("Tellomi.Discover.ProfileRequired", TellomiDiscoverReact.messageKey(TellomiDiscover.ClientError.PROFILE_REQUIRED));

        TellomiDiscover.HttpCall delete = TellomiDiscover.httpCall("https://feed.example", TellomiDiscover.Operation.deleteComment(OTHER), "jwt");
        assertEquals("DELETE", delete.method);
        assertEquals("/v1/feed/posts/" + OTHER, delete.path());
        assertNull(delete.body);
        assertFalse(delete.path().contains("/comments"));
        TellomiDiscover.Outcome removed = TellomiDiscover.decodeHttp(TellomiDiscover.Operation.deleteComment(OTHER), new TellomiDiscover.HttpResult(204, new byte[0]));
        assertTrue(removed.ok());

        String page = "{\"comments\":[" + card(OTHER, "post-9", "mine", null, null).replace("\"text\":\"mine\"", "\"text\":\"mine\"") + "]}";
        String owned = page.replace("\"card\":{\"text\":\"mine\"}", "\"card\":{\"text\":\"mine\"},\"me\":{\"author\":true}");
        TellomiDiscoverReact.Comments thread = new TellomiDiscoverReact.Comments();
        TellomiDiscoverReact.Comments.Load load = thread.open("post-9");
        thread.accept(load.generation, load.version, TellomiDiscoverParse.commentPage(owned.getBytes(StandardCharsets.UTF_8), "post-9"));
        assertTrue(thread.canDelete(OTHER));
        assertEquals(OTHER, thread.replyTo(thread.visible().get(0)));
        TellomiDiscoverReact.Comments.Delete request = thread.delete(OTHER);
        assertFalse(thread.finishDelete(request.generation, request.version, OTHER, false));
        assertEquals(1, thread.publicCount());
        TellomiDiscoverReact.Comments.Delete again = thread.delete(OTHER);
        assertTrue(thread.finishDelete(again.generation, again.version, OTHER, true));
        assertEquals(0, thread.publicCount());
        assertNull(thread.delete("post-9"));
    }

    @Test
    public void credentialExpiredRefreshesOnceAndAClosedCommentDoesNot() {
        Script expired = new Script(new TellomiDiscover.Credential("old", 9_000, "cn"));
        expired.results.add(new TellomiDiscover.HttpResult(401, "{\"error\":\"credential_expired\"}".getBytes(StandardCharsets.UTF_8)));
        expired.results.add(new TellomiDiscover.HttpResult(204, new byte[0]));
        TellomiDiscover.Outcome liked = expired.run(TellomiDiscover.Operation.like("post-9", true, "recommend"));
        assertTrue(liked.ok());
        assertEquals(1, expired.fetches);
        assertEquals(Arrays.asList("old", "new"), tokens(expired.calls));

        Script named = new Script(new TellomiDiscover.Credential("old", 9_000, "cn"));
        named.results.add(new TellomiDiscover.HttpResult(401, "{\"error\":\"other\"}".getBytes(StandardCharsets.UTF_8)));
        TellomiDiscover.Outcome denied = named.run(TellomiDiscover.Operation.like("post-9", true, "recommend"));
        assertEquals(TellomiDiscover.ClientError.UNAUTHORIZED, denied.error);
        assertEquals(0, named.fetches);

        Script closed = new Script(new TellomiDiscover.Credential("old", 9_000, "cn"));
        closed.results.add(new TellomiDiscover.HttpResult(403, "{\"error\":\"comments_closed\"}".getBytes(StandardCharsets.UTF_8)));
        TellomiDiscover.Outcome comments = closed.run(TellomiDiscover.Operation.comment("post-9", "hello", null, NONCE));
        assertEquals(TellomiDiscover.ClientError.COMMENTS_CLOSED, comments.error);
        assertEquals(0, closed.fetches);
        assertEquals(1, closed.calls.size());
    }

    private static String read(String relative) throws Exception {
        java.io.File direct = new java.io.File(relative);
        if (!direct.isFile()) {
            direct = new java.io.File("TMessagesProj", relative.startsWith("TMessagesProj/") ? relative.substring("TMessagesProj/".length()) : relative);
        }
        if (!direct.isFile()) {
            direct = new java.io.File("..", relative);
        }
        return new String(java.nio.file.Files.readAllBytes(direct.toPath()), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static TellomiDiscover.ClientError error(int status, String code) {
        TellomiDiscover.Outcome outcome = TellomiDiscover.decodeHttp(
            TellomiDiscover.Operation.comment("post-9", "hello", null, NONCE),
            new TellomiDiscover.HttpResult(status, ("{\"error\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8))
        );
        return outcome.error;
    }

    private static String card(String id, String postId, String text, String parentId, String rootId) {
        StringBuilder out = new StringBuilder();
        out.append("{\"id\":\"").append(id).append("\",\"state\":\"ok\",\"post_id\":\"").append(postId).append("\",\"card\":{\"text\":\"").append(text).append("\"}");
        if (parentId != null) {
            out.append(",\"parent_id\":\"").append(parentId).append("\"");
        }
        if (rootId != null) {
            out.append(",\"root_id\":\"").append(rootId).append("\"");
        }
        out.append("}");
        return out.toString();
    }

    private static List<String> texts(List<TellomiDiscover.Comment> comments) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < comments.size(); i++) {
            values.add(comments.get(i).text);
        }
        return values;
    }

    private static List<String> tokens(List<TellomiDiscover.HttpCall> calls) {
        List<String> values = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            values.add(calls.get(i).token);
        }
        return values;
    }

    private static final class Script {
        final List<TellomiDiscover.HttpResult> results = new ArrayList<>();
        final List<TellomiDiscover.HttpCall> calls = new ArrayList<>();
        int fetches;
        TellomiDiscover.Credential next = new TellomiDiscover.Credential("new", 9_000, "cn");
        final TellomiDiscover.Loader loader;

        Script(final TellomiDiscover.Credential cached) {
            final Script self = this;
            loader = new TellomiDiscover.Loader(new TellomiDiscover.Clock() {
                @Override
                public long now() {
                    return 1_000;
                }
            }, cached, new TellomiDiscover.CredentialFetch() {
                @Override
                public void fetch(TellomiDiscover.CredentialCallback callback) {
                    self.fetches++;
                    callback.onResult(self.next, null);
                }
            }, new TellomiDiscover.HttpTransport() {
                @Override
                public void perform(TellomiDiscover.HttpCall call, TellomiDiscover.HttpCallback callback) {
                    self.calls.add(call);
                    TellomiDiscover.HttpResult result = self.results.isEmpty() ? new TellomiDiscover.HttpResult(204, new byte[0]) : self.results.remove(0);
                    callback.onResult(result);
                }
            });
        }

        TellomiDiscover.Outcome run(TellomiDiscover.Operation operation) {
            final TellomiDiscover.Outcome[] out = new TellomiDiscover.Outcome[1];
            loader.run(true, new TellomiDiscover.Configuration(true, "https://feed.example"), operation, new TellomiDiscover.Completion() {
                @Override
                public void onResult(TellomiDiscover.Outcome outcome) {
                    out[0] = outcome;
                }
            });
            return out[0];
        }
    }
}
