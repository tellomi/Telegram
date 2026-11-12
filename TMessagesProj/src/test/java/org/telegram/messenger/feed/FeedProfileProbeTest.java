package org.telegram.messenger.feed;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FeedProfileProbeTest {

    @Test
    public void confirmedEmptyDoesNotBlockTheNextOpen() {
        FeedProfileProbe.Peer peer = new FeedProfileProbe.Peer();
        assertTrue(FeedProfileProbe.beginOpen(peer, 0L));
        FeedProfileProbe.succeed(peer);
        assertFalse(peer.inFlight);
        assertEquals(0, peer.failures);
        assertTrue(FeedProfileProbe.beginOpen(peer, 50L));
        FeedProfileProbe.succeed(peer);
        assertTrue(FeedProfileProbe.beginOpen(peer, 100L));
    }

    @Test
    public void tabRebuildDoesNotFetchAndFailureBacksOffOneFlight() {
        assertFalse(FeedProfileProbe.beginFromTabs());
        FeedProfileProbe.Peer peer = new FeedProfileProbe.Peer();
        FeedProfileProbe.bind(peer, new Object());
        assertTrue(FeedProfileProbe.beginOpen(peer, 1_000L));
        assertFalse(FeedProfileProbe.beginOpen(peer, 1_001L));
        long delay = FeedProfileProbe.fail(peer, 1_000L);
        assertFalse(peer.inFlight);
        assertEquals(FeedProfileProbe.BACKOFF_FIRST_MS, delay);
        assertFalse(FeedProfileProbe.beginOpen(peer, 1_000L + delay - 1L));
        assertTrue(FeedProfileProbe.beginOpen(peer, 1_000L + delay));
        long second = FeedProfileProbe.fail(peer, 5_000L);
        assertEquals(4_000L, second);
        assertFalse(FeedProfileProbe.beginRetry(peer, 5_000L));
        assertTrue(FeedProfileProbe.beginRetry(peer, 5_000L + second));
        FeedProfileProbe.succeed(peer);
        assertFalse(FeedProfileProbe.beginRetry(peer, 60_000L));
        FeedProfileProbe.Peer armed = new FeedProfileProbe.Peer();
        FeedProfileProbe.bind(armed, new Object());
        assertTrue(FeedProfileProbe.beginOpen(armed, 0L));
        assertTrue(FeedProfileProbe.fail(armed, 0L) > 0L);
        assertEquals(0L, FeedProfileProbe.fail(armed, 10L));
        assertEquals(FeedProfileProbe.BACKOFF_CAP_MS, FeedProfileProbe.backoffMs(8));
    }

    @Test
    public void leavingUnregistersOnlyThatLayoutAndADetachedCallbackIsDropped() {
        FeedProfileProbe.Peer peer = new FeedProfileProbe.Peer();
        Object layout = new Object();
        Object replacement = new Object();
        FeedProfileProbe.bind(peer, layout);
        assertTrue(FeedProfileProbe.dispatch(peer, true));
        assertFalse(FeedProfileProbe.acceptCallback(false, true));
        assertFalse(FeedProfileProbe.dispatch(peer, false));
        assertFalse(FeedProfileProbe.unbind(peer, replacement));
        assertTrue(peer.listener == layout);
        FeedProfileProbe.bind(peer, replacement);
        assertFalse(FeedProfileProbe.unbind(peer, layout));
        assertTrue(peer.listener == replacement);
        assertTrue(FeedProfileProbe.unbind(peer, replacement));
        assertFalse(FeedProfileProbe.dispatch(peer, true));
        assertFalse(FeedProfileProbe.acceptCallback(true, false));
        FeedProfileProbe.Peer gone = new FeedProfileProbe.Peer();
        Object left = new Object();
        FeedProfileProbe.bind(gone, left);
        assertTrue(FeedProfileProbe.beginOpen(gone, 0L));
        assertTrue(FeedProfileProbe.unbind(gone, left));
        assertEquals(0L, FeedProfileProbe.fail(gone, 10L));
        assertFalse(gone.retryArmed);
    }

    @Test
    public void deletingTheLoadedPageKeepsPostsWhenAnotherPageRemains() {
        assertFalse(FeedProfileProbe.confirmNoPostsAfterDelete(0, true));
        assertTrue(FeedProfileProbe.confirmNoPostsAfterDelete(0, false));
        assertFalse(FeedProfileProbe.confirmNoPostsAfterDelete(2, false));
        assertFalse(FeedProfileProbe.confirmNoPostsAfterDelete(2, true));
    }

    @Test
    public void openAndResumeProbeSeparatelyFromTabRebuilds() throws Exception {
        String layout = read("src/main/java/org/telegram/ui/Components/SharedMediaLayout.java");
        String controller = read("src/main/java/org/telegram/messenger/feed/FeedController.java");
        String update = method(layout, "public void updateTabs(boolean animated)", "private void startStopVisibleGifs");
        String resume = method(layout, "public void onResume()", "public void onPause()");
        String destroy = method(layout, "public void onDestroy()", "private void checkCurrentTabValid");
        assertFalse(update.contains("ensureMomentsTab"));
        assertTrue(layout.contains("updateTabs(false);\n        ensureMomentsTab();"));
        assertTrue(resume.contains("ensureMomentsTab()"));
        assertTrue(destroy.contains("clearProfilePostsListener"));
        assertTrue(layout.contains("FeedProfileProbe.acceptCallback"));
        assertTrue(layout.contains("momentsTabsApplying"));
        assertFalse(controller.contains("profileProbeStarted"));
        assertTrue(controller.contains("FeedProfileProbe.beginOpen"));
        assertTrue(controller.contains("FeedProfileProbe.fail"));
        assertTrue(controller.contains("FeedProfileProbe.confirmNoPostsAfterDelete"));
        assertTrue(controller.contains("FeedProfileProbe.unbind"));
        int ensure = controller.indexOf("void ensureProfilePosts");
        int load = controller.indexOf("void loadProfilePosts");
        String ensureBody = controller.substring(ensure, load);
        assertTrue(ensureBody.contains("beginOpen"));
        assertFalse(ensureBody.contains("getJson"));
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start + startToken.length());
        assertTrue(start > 0 && end > start);
        return source.substring(start, end);
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
}
