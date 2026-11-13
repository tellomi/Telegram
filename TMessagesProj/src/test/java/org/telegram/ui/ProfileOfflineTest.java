package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileOfflineTest {

    private static final long AUTHOR = 42L;
    private static final long SELF = 7L;

    @Test
    public void fullMemoryUserWinsOverTheUsersTable() {
        assertEquals(ProfileOffline.AuthorSeed.USE_MEMORY, ProfileOffline.authorSeed(true, false, true, true, AUTHOR, SELF));
        assertFalse(ProfileOffline.needsStoredUser(true, false));
    }

    @Test
    public void minMemoryUserYieldsToTheStoredRow() {
        assertTrue(ProfileOffline.needsStoredUser(false, false));
        assertTrue(ProfileOffline.needsStoredUser(true, true));
        assertEquals(ProfileOffline.AuthorSeed.USE_STORED, ProfileOffline.authorSeed(true, true, true, true, AUTHOR, SELF));
        assertEquals(ProfileOffline.AuthorSeed.USE_STORED, ProfileOffline.authorSeed(false, false, true, true, AUTHOR, SELF));
    }

    @Test
    public void stubIsLastAndNeverForTheLoggedInUserOrATimedOutRead() {
        assertEquals(ProfileOffline.AuthorSeed.INSERT_MIN_STUB, ProfileOffline.authorSeed(false, false, false, true, AUTHOR, SELF));
        assertEquals(ProfileOffline.AuthorSeed.LEAVE_ABSENT, ProfileOffline.authorSeed(false, false, false, true, SELF, SELF));
        assertEquals(ProfileOffline.AuthorSeed.LEAVE_ABSENT, ProfileOffline.authorSeed(false, false, false, false, AUTHOR, SELF));
        assertEquals(ProfileOffline.AuthorSeed.USE_MEMORY, ProfileOffline.authorSeed(true, true, false, false, AUTHOR, SELF));
        assertEquals(ProfileOffline.AuthorSeed.LEAVE_ABSENT, ProfileOffline.authorSeed(false, false, true, true, 0, SELF));
        assertTrue(ProfileOffline.authorStubIsMin());
        assertTrue(ProfileOffline.authorStubFromCache());
    }

    @Test
    public void storedUserWaitIsBoundedAndDoesNotHang() throws Exception {
        assertTrue(ProfileOffline.STORED_USER_WAIT_MS >= 200L);
        assertTrue(ProfileOffline.STORED_USER_WAIT_MS <= 2000L);
        assertFalse(ProfileOffline.awaitStoredUser(null, 1_000L));

        CountDownLatch open = new CountDownLatch(0);
        long started = System.nanoTime();
        assertTrue(ProfileOffline.awaitStoredUser(open, 1_000L));
        assertTrue(System.nanoTime() - started < 200_000_000L);

        CountDownLatch blocked = new CountDownLatch(1);
        started = System.nanoTime();
        assertFalse(ProfileOffline.awaitStoredUser(blocked, 40L));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertTrue(elapsedMs < 400L);

        CountDownLatch interrupted = new CountDownLatch(1);
        Thread.currentThread().interrupt();
        try {
            assertFalse(ProfileOffline.awaitStoredUser(interrupted, 5_000L));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    public void profileBackDelegatesSheetDismissToTheBaseFragment() throws Exception {
        String profile = read("src/main/java/org/telegram/ui/ProfileActivity.java");
        String back = method(profile, "public boolean onBackPressed(boolean invoked)", "private TLRPC.User readStoredUser");
        assertTrue(back.contains("super.onBackPressed(invoked)"));
        assertFalse(back.contains("closeSheet("));
        int sheetGate = back.indexOf("if (!super.onBackPressed(invoked))");
        int fragmentCloses = back.lastIndexOf("return true");
        assertTrue(sheetGate >= 0 && fragmentCloses > sheetGate);

        String base = read("src/main/java/org/telegram/ui/ActionBar/BaseFragment.java");
        String baseBack = method(base, "public boolean onBackPressed(boolean invoked)", "public boolean closeSheet()");
        int shown = baseBack.indexOf("hasShownSheet()");
        int invoked = baseBack.indexOf("if (invoked) closeSheet()");
        assertTrue(shown >= 0 && invoked > shown);
        assertTrue(baseBack.contains("return false"));

        String readUser = method(profile, "private TLRPC.User readStoredUser", "public boolean isSettings()");
        assertTrue(readUser.contains("FeedUi.ensureStoredUser"));
        assertFalse(readUser.contains("await"));
        assertFalse(readUser.contains("getStorageQueue"));
        int checked = profile.indexOf("author_row_checked");
        int readCall = profile.indexOf("readStoredUser(userId)", checked);
        assertTrue(checked >= 0 && readCall > checked);
        assertTrue(profile.contains("!authorRowChecked && ProfileOffline.needsStoredUser(user != null, user != null && user.min)"));
    }

    @Test
    public void bindDoesNotBlockOnStorage() throws Exception {
        ProfileOffline.StoredReadGate gate = new ProfileOffline.StoredReadGate();
        String key = ProfileOffline.storedKey(0, AUTHOR);
        assertEquals(ProfileOffline.StoredRead.START, gate.begin(key, false));
        long started = System.nanoTime();
        assertEquals(ProfileOffline.StoredRead.SKIP, gate.begin(key, false));
        assertTrue((System.nanoTime() - started) < 50_000_000L);

        String feed = read("src/main/java/org/telegram/ui/Feed/FeedUi.java");
        String bind = method(feed, "public static void bindAvatar", "private static TLRPC.User telegramUser");
        String avatar = method(feed, "private static TLRPC.User telegramUser", "static String avatarHttp");
        String schedule = method(feed, "private static void scheduleStoredAuthor", "private static void postStoredRead");
        assertFalse(containsStorageWait(bind));
        assertFalse(containsStorageWait(avatar));
        assertFalse(containsStorageWait(schedule));
        assertTrue(avatar.contains("memoryAuthor"));
        assertTrue(avatar.contains("scheduleStoredAuthor"));
        assertTrue(schedule.contains("begin(key, false)"));
        assertFalse(schedule.contains("await("));
        assertFalse(bind.contains("ensureStoredUser"));
        assertFalse(avatar.contains("ensureStoredUser"));
    }

    @Test
    public void repeatedBindsOfAMissingAuthorReadStorageOnce() {
        ProfileOffline.StoredReadGate gate = new ProfileOffline.StoredReadGate();
        String key = ProfileOffline.storedKey(1, 99L);
        int reads = 0;
        for (int i = 0; i < 6; i++) {
            if (gate.begin(key, false) == ProfileOffline.StoredRead.START) {
                reads++;
            }
        }
        assertEquals(1, reads);
        assertEquals(ProfileOffline.StoredRead.WAIT, gate.begin(key, true));
        gate.complete(key);
        for (int i = 0; i < 6; i++) {
            assertEquals(ProfileOffline.StoredRead.SKIP, gate.begin(key, false));
            assertEquals(ProfileOffline.StoredRead.SKIP, gate.begin(key, true));
        }
        assertEquals(1, reads);
        assertTrue(gate.isDone(key));
    }

    @Test
    public void profileOpenJoinsTheInFlightRead() throws Exception {
        ProfileOffline.StoredReadGate gate = new ProfileOffline.StoredReadGate();
        String key = ProfileOffline.storedKey(0, AUTHOR);
        assertEquals(ProfileOffline.StoredRead.START, gate.begin(key, false));
        Thread worker = new Thread(() -> gate.complete(key));
        worker.start();
        assertTrue(gate.await(key, 1_000L));
        worker.join();
        assertEquals(ProfileOffline.StoredRead.SKIP, gate.begin(key, true));

        String feed = read("src/main/java/org/telegram/ui/Feed/FeedUi.java");
        String open = method(feed, "public static void openProfile", "static void seedCachedUser");
        assertTrue(open.contains("author_row_checked"));
        String ensure = method(feed, "public static TLRPC.User ensureStoredUser", "private static void scheduleStoredAuthor");
        assertTrue(ensure.contains("begin(key, true)"));
        assertTrue(ensure.contains("storedUsers().await(key, org.telegram.ui.ProfileOffline.STORED_USER_WAIT_MS)"));
        assertEquals(1, count(feed, "getStorageQueue().postRunnable"));
    }

    @Test
    public void feedStubCannotReplaceAStoredOrCurrentUser() throws Exception {
        String feed = read("src/main/java/org/telegram/ui/Feed/FeedUi.java");
        assertFalse(feed.contains("putUser(stub, false)"));
        String resolver = method(feed, "static TLRPC.User resolveAuthorForProfile", "private static TLRPC.User memoryAuthor");
        assertTrue(resolver.contains("needsStoredUser"));
        assertTrue(resolver.contains("ensureStoredUser"));
        assertTrue(resolver.contains("authorSeed"));
        assertTrue(resolver.contains("putUser(stored, true)"));
        int stubMin = resolver.indexOf("stub.min = org.telegram.ui.ProfileOffline.authorStubIsMin()");
        int stubPut = resolver.indexOf("putUser(stub, org.telegram.ui.ProfileOffline.authorStubFromCache())");
        assertTrue(stubMin > 0 && stubPut > stubMin);
        assertTrue(resolver.contains("userId, selfId"));
        String memory = method(feed, "private static TLRPC.User memoryAuthor", "public static TLRPC.User ensureStoredUser");
        assertTrue(memory.contains("putUser(self, true)"));
        assertFalse(containsStorageWait(memory));
        String seed = method(feed, "static void seedCachedUser", "static TLRPC.User resolveAuthorForProfile");
        assertTrue(seed.contains("resolveAuthorForProfile"));
        assertTrue(seed.contains("!user.min && user.id != selfId"));
        String avatar = method(feed, "private static TLRPC.User telegramUser", "static String avatarHttp");
        assertFalse(avatar.contains("new TLRPC.TL_user"));

        String controller = read("src/main/java/org/telegram/messenger/MessagesController.java");
        String put = method(controller, "public boolean putUser(TLRPC.User user, boolean fromCache, boolean force)", "public void reloadUser");
        int saveSelf = put.indexOf("getUserConfig().setCurrentUser(user)");
        int liveWrite = put.lastIndexOf("if (!fromCache)", saveSelf);
        int insertMissing = put.indexOf("} else if (oldUser == null)", saveSelf);
        int replaceMin = put.indexOf("} else if (oldUser.min)", insertMissing);
        int putReturns = put.indexOf("return false;", replaceMin);
        assertTrue(liveWrite >= 0 && saveSelf > liveWrite && insertMissing > saveSelf && replaceMin > insertMissing && putReturns > replaceMin);
        assertFalse(put.substring(insertMissing, putReturns).contains("setCurrentUser"));
    }

    private static boolean containsStorageWait(String source) {
        return source.contains("await(")
                || source.contains("awaitStoredUser")
                || source.contains("CountDownLatch")
                || source.contains("getStorageQueue");
    }

    private static int count(String source, String token) {
        int found = 0;
        int from = 0;
        while (true) {
            int at = source.indexOf(token, from);
            if (at < 0) {
                return found;
            }
            found++;
            from = at + token.length();
        }
    }

    private static String method(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = start < 0 ? -1 : source.indexOf(endToken, start + startToken.length());
        assertTrue(start >= 0 && end > start);
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
