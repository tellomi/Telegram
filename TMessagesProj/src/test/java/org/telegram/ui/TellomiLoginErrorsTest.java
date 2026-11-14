package org.telegram.ui;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiLoginErrorsTest {

    private static final String FLOOD = "Too many attempts, please try again later";
    private static final String GENERIC = "An error occurred";

    @Test
    public void smsDailyLimitUsesTheFloodCopy() {
        assertEquals(FLOOD, TellomiLoginErrors.sendCodeMessage(400, "SMS_CODE_CREATE_FAILED", FLOOD, GENERIC));
        assertEquals(FLOOD, TellomiLoginErrors.sendCodeMessage(420, "FLOOD_WAIT_86400", FLOOD, GENERIC));
        assertEquals(FLOOD, TellomiLoginErrors.sendCodeMessage(400, "SMS_DAILY_LIMIT", FLOOD, GENERIC));
        assertEquals(FLOOD, TellomiLoginErrors.sendCodeMessage(406, "PHONE_NUMBER_FLOOD", FLOOD, GENERIC));
    }

    @Test
    public void otherSendFailuresStayVisible() {
        assertEquals(GENERIC + "\nSEND_CODE_UNAVAILABLE", TellomiLoginErrors.sendCodeMessage(400, "SEND_CODE_UNAVAILABLE", FLOOD, GENERIC));
        assertEquals(GENERIC + "\nPHONE_MIGRATE_2", TellomiLoginErrors.sendCodeMessage(303, "PHONE_MIGRATE_2", FLOOD, GENERIC));
    }

    @Test
    public void droppedConnectionStaysQuiet() {
        assertNull(TellomiLoginErrors.sendCodeMessage(-1000, "CONNECTION_NOT_INITED", FLOOD, GENERIC));
        assertNull(TellomiLoginErrors.sendCodeMessage(400, null, FLOOD, GENERIC));
    }

    @Test
    public void sendCodeHandlerShowsTheHelperInsteadOfProcessError() throws Exception {
        String login = read("src/main/java/org/telegram/ui/LoginActivity.java");
        int send = login.indexOf("TLRPC.TL_auth_sendCode sendCode = new TLRPC.TL_auth_sendCode();");
        assertTrue(send >= 0);
        int end = login.indexOf("private boolean numberFilled", send);
        String body = login.substring(send, end);
        assertTrue(body.contains("TellomiLoginErrors.sendCodeMessage"));
        assertTrue(body.contains("needShowAlert"));
        int errorBranch = body.indexOf("} else {");
        assertTrue(errorBranch >= 0);
        String errors = body.substring(errorBranch);
        assertTrue(!errors.contains("AlertsCreator.processError"));
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
