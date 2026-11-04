package org.telegram.ui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TellomiSignUpAvatarTest {

    @Test
    public void signUpSucceedsBeforeTheAvatarUpload() {
        TellomiSignUpAvatar.Outcome outcome = TellomiSignUpAvatar.afterSignUp(true);
        assertTrue(outcome.accountCreated);
        assertTrue(outcome.uploadAvatar);
        assertFalse(outcome.canRetry);
        assertNull(outcome.errorCode);

        TellomiSignUpAvatar.Outcome noPhoto = TellomiSignUpAvatar.afterSignUp(false);
        assertTrue(noPhoto.accountCreated);
        assertFalse(noPhoto.uploadAvatar);
    }

    @Test
    public void uploadFailureKeepsTheAccountAndShowsTheServerCode() {
        TellomiSignUpAvatar.Outcome failed = TellomiSignUpAvatar.afterUpload(500, "INTERNAL_SERVER_ERROR");
        assertTrue(failed.accountCreated);
        assertFalse(failed.uploadAvatar);
        assertTrue(failed.canRetry);
        assertEquals("Tellomi.SignUp.AvatarFailed", failed.titleKey);
        assertEquals("500 INTERNAL_SERVER_ERROR", failed.errorCode);
        assertEquals("头像上传失败（%s）", TellomiPack.bundled(failed.titleKey, "zh-Hans"));
        assertEquals("Couldn't upload the profile photo (%s)", TellomiPack.bundled(failed.titleKey, "en"));
        assertEquals("头像上传失败（500 INTERNAL_SERVER_ERROR）", TellomiSignUpAvatar.message(TellomiPack.bundled(failed.titleKey, "zh"), failed.errorCode));
        assertEquals("Couldn't upload the profile photo (500 INTERNAL_SERVER_ERROR)", TellomiSignUpAvatar.message("Couldn't upload the profile photo (%@)", failed.errorCode));

        assertEquals("500", TellomiSignUpAvatar.afterUpload(500, "  ").errorCode);
        assertEquals("400 PHOTO_INVALID", TellomiSignUpAvatar.afterUpload(400, "PHOTO_INVALID").errorCode);
        assertNull(TellomiSignUpAvatar.afterUpload(0, null).errorCode);
        assertTrue(TellomiSignUpAvatar.afterUpload(0, null).accountCreated);
        assertTrue(TellomiSignUpAvatar.afterUploadSuccess().accountCreated);
        assertFalse(TellomiSignUpAvatar.afterUploadSuccess().canRetry);
    }
}
