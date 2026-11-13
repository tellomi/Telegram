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
        assertTrue(readUser.contains("ProfileOffline.awaitStoredUser(latch)"));
        assertFalse(readUser.contains("latch.await()"));
        assertTrue(readUser.contains("putUser(loaded[0], true)"));
        assertTrue(profile.contains("needsStoredUser(user != null, user != null && user.min)"));
    }

    @Test
    public void feedStubCannotReplaceAStoredOrCurrentUser() throws Exception {
        String feed = read("src/main/java/org/telegram/ui/Feed/FeedUi.java");
        assertFalse(feed.contains("putUser(stub, false)"));
        String resolver = method(feed, "static TLRPC.User cachedAuthorUser", "private static final class DiskUser");
        assertTrue(resolver.contains("needsStoredUser"));
        assertTrue(resolver.contains("readDiskUser"));
        assertTrue(resolver.contains("authorSeed"));
        assertTrue(resolver.contains("putUser(stored, true)"));
        assertTrue(resolver.contains("putUser(self, true)"));
        int stubMin = resolver.indexOf("stub.min = org.telegram.ui.ProfileOffline.authorStubIsMin()");
        int stubPut = resolver.indexOf("putUser(stub, org.telegram.ui.ProfileOffline.authorStubFromCache())");
        assertTrue(stubMin > 0 && stubPut > stubMin);
        assertTrue(resolver.contains("userId == selfId"));
        String seed = method(feed, "static void seedCachedUser", "static TLRPC.User cachedAuthorUser");
        assertTrue(seed.contains("cachedAuthorUser"));
        assertTrue(seed.contains("!user.min && user.id != selfId"));
        String avatar = method(feed, "private static TLRPC.User telegramUser", "static String avatarHttp");
        assertTrue(avatar.contains("cachedAuthorUser"));
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
