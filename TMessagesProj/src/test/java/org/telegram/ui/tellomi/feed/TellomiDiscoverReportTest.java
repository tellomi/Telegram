package org.telegram.ui.tellomi.feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiDiscoverReportTest {

    @Test
    public void optionsAreTheOfficialIdsAndTheMenuStaysClosedWhenSignedOut() {
        assertArrayEquals(new String[] {"spam", "violence", "pornography", "child", "drugs", "personal", "other"}, TellomiDiscoverReport.OPTION_IDS);
        assertEquals("org.telegram.ui.ReportBottomSheet", TellomiDiscoverReport.NATIVE_PICKER);
        assertEquals(Arrays.asList("report"), TellomiDiscoverReport.menuActions(true));
        assertTrue(TellomiDiscoverReport.menuActions(false).isEmpty());
        assertEquals("Tellomi.Discover.Report.Spam", TellomiDiscoverReport.optionKey("spam"));
        assertEquals("Tellomi.Discover.Report.Other", TellomiDiscoverReport.optionKey("other"));
        assertNull(TellomiDiscoverReport.optionKey("harm_others"));
    }

    @Test
    public void reportPostsToTheFeedEndpointWithDetail() {
        byte[] body = TellomiDiscover.reportBody("post-9", null, "spam", "");
        TellomiJson json = TellomiJson.parse(body);
        assertEquals("content", json.objectValue.get("target_type").stringValue);
        assertEquals("post-9", json.objectValue.get("target_id").stringValue);
        assertEquals("spam", json.objectValue.get("option_id").stringValue);
        assertEquals("", json.objectValue.get("detail").stringValue);
        assertNull(json.objectValue.get("comment_id"));
        assertNull(json.objectValue.get("grp"));
        assertFalse(new String(body, StandardCharsets.UTF_8).contains("messages.report"));

        TellomiJson comment = TellomiJson.parse(TellomiDiscover.reportBody("post-9", "comment-1", "other", "详情"));
        assertEquals("comment-1", comment.objectValue.get("comment_id").stringValue);
        assertEquals("other", comment.objectValue.get("option_id").stringValue);
        assertEquals("详情", comment.objectValue.get("detail").stringValue);

        assertNull(TellomiDiscover.reportBody("post-9", null, "other", "  "));
        assertNull(TellomiDiscover.reportBody("post-9", null, "harm_others", ""));
        assertNull(TellomiDiscover.reportBody("../x", null, "spam", ""));
        List<TellomiDiscover.Card> cards = TellomiDiscoverParse.cards("{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"comments\":[{\"id\":\"comment-1\",\"text\":\"hi\"},{\"id\":\"../bad\",\"text\":\"no\"}]}]}".getBytes(StandardCharsets.UTF_8));
        assertEquals(1, cards.get(0).comments.size());
        assertEquals("comment-1", cards.get(0).comments.get(0).id);
        assertEquals("hi", TellomiDiscoverParse.displayPost(cards.get(0)).comments.get(0).text);
    }

    @Test
    public void signedOutDoesNotSendAndASuccessIsOnlyTheFeedCall() {
        TellomiDiscover.Configuration configuration = new TellomiDiscover.Configuration(true, "https://feed.example");
        RecordingLoader signedOut = new RecordingLoader();
        TellomiDiscover.Outcome blocked = run(signedOut, false, configuration, TellomiDiscover.Operation.report("post-9", null, "violence", ""));
        assertEquals(TellomiDiscover.ClientError.SIGNED_OUT, blocked.error);
        assertTrue(signedOut.calls.isEmpty());

        RecordingLoader loader = new RecordingLoader();
        loader.statuses.add(201);
        TellomiDiscover.Outcome sent = run(loader, true, configuration, TellomiDiscover.Operation.report("post-9", "comment-1", "violence", "note"));
        assertTrue(sent.ok());
        assertEquals(TellomiDiscover.Decoded.Kind.REPORTED, sent.value.kind);
        assertEquals(1, loader.calls.size());
        assertEquals("POST", loader.calls.get(0).method);
        assertEquals("/v1/feed/reports", loader.calls.get(0).path());
        assertTrue(loader.calls.get(0).authorizes);
        TellomiJson posted = TellomiJson.parse(loader.calls.get(0).body);
        assertEquals("violence", posted.objectValue.get("option_id").stringValue);
        assertEquals("comment-1", posted.objectValue.get("comment_id").stringValue);
        assertEquals("note", posted.objectValue.get("detail").stringValue);

        RecordingLoader invalid = new RecordingLoader();
        TellomiDiscover.Outcome rejected = run(invalid, true, configuration, TellomiDiscover.Operation.report("post-9", null, "other", ""));
        assertEquals(TellomiDiscover.ClientError.NOT_PUBLISHABLE, rejected.error);
        assertTrue(invalid.calls.isEmpty());
    }

    private static TellomiDiscover.Outcome run(RecordingLoader loader, boolean loggedIn, TellomiDiscover.Configuration configuration, TellomiDiscover.Operation operation) {
        final TellomiDiscover.Outcome[] box = new TellomiDiscover.Outcome[1];
        loader.loader.run(loggedIn, configuration, operation, new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                box[0] = outcome;
            }
        });
        return box[0];
    }

    private static final class RecordingLoader {
        final List<Integer> statuses = new ArrayList<>();
        final List<TellomiDiscover.HttpCall> calls = new ArrayList<>();
        final TellomiDiscover.Loader loader;

        RecordingLoader() {
            loader = new TellomiDiscover.Loader(new TellomiDiscover.Clock() {
                @Override
                public long now() {
                    return 1_000;
                }
            }, new TellomiDiscover.Credential("jwt", 9_000, "cn"), new TellomiDiscover.CredentialFetch() {
                @Override
                public void fetch(TellomiDiscover.CredentialCallback callback) {
                    callback.onResult(new TellomiDiscover.Credential("jwt", 9_000, "cn"), null);
                }
            }, new TellomiDiscover.HttpTransport() {
                @Override
                public void perform(TellomiDiscover.HttpCall call, TellomiDiscover.HttpCallback callback) {
                    calls.add(call);
                    int status = statuses.isEmpty() ? 200 : statuses.remove(0);
                    callback.onResult(new TellomiDiscover.HttpResult(status, "{\"state\":\"received\"}".getBytes(StandardCharsets.UTF_8)));
                }
            });
        }
    }
}
