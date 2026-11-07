package org.telegram.messenger;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** English-to-Chinese target codes and unsent-voice checks, without Android. */
public class TellomiTranslateDraftTest {

    @Test
    public void onlySimplifiedChineseCodesAreAccepted() {
        assertEquals("zh-hans", TellomiTranslate.acceptedToLang("zh"));
        assertEquals("zh-hans", TellomiTranslate.acceptedToLang("zh-CN"));
        assertEquals("zh-hans", TellomiTranslate.acceptedToLang("zh_hans"));
        assertNull(TellomiTranslate.acceptedToLang("en"));
        assertNull(TellomiTranslate.acceptedToLang("zh-tw"));
        assertNull(TellomiTranslate.acceptedToLang(null));
    }

    @Test
    public void otherTargetsAreSentAsSimplifiedChinese() {
        assertEquals("zh-hans", TellomiTranslate.rpcToLang("en"));
        assertEquals("zh-hans", TellomiTranslate.rpcToLang("fr"));
        assertEquals("zh-hans", TellomiTranslate.rpcToLang("zh-hans"));
    }

    @Test
    public void appConfigCountsOnlyTheEnabledWord() {
        assertTrue(TellomiTranslate.configEnabled("enabled"));
        assertFalse(TellomiTranslate.configEnabled("alternative"));
        assertFalse(TellomiTranslate.configEnabled("system"));
        assertFalse(TellomiTranslate.configEnabled("disabled"));
        assertFalse(TellomiTranslate.configEnabled(null));
    }

    @Test
    public void draftAudioIsRejectedBeforeTheRequest() {
        assertEquals("USER_ID_INVALID", TellomiDraftTranscribe.localError(false, 12, "a.ogg"));
        assertEquals("MEDIA_EMPTY", TellomiDraftTranscribe.localError(true, 0, "a.ogg"));
        assertEquals("MEDIA_EMPTY", TellomiDraftTranscribe.localError(true, 12, "a.mp3"));
        assertEquals("AUDIO_TOO_LONG", TellomiDraftTranscribe.localError(true, TellomiDraftTranscribe.MAX_BYTES + 1, "a.ogg"));
        assertNull(TellomiDraftTranscribe.localError(true, 32, "voice.ogg"));
        assertTrue(TellomiDraftTranscribe.supportedName("clip.MP4"));
        assertEquals(0x7e11da47, TellomiDraftTranscribe.CONSTRUCTOR);
    }

    @Test
    public void draftErrorsStayInChinese() {
        assertTrue(TellomiDraftTranscribe.message("AUDIO_TOO_LONG", true).contains("8MB"));
        assertTrue(TellomiDraftTranscribe.message("TRANSCRIPTION_UNAVAILABLE", true).contains("暂不可用"));
        assertTrue(TellomiDraftTranscribe.message("TRANSCRIPTION_FAILED", true).contains("没有识别出文字"));
        assertTrue(TellomiDraftTranscribe.message("USER_ID_INVALID", false).contains("Sign in"));
    }
}
