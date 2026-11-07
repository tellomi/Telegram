package org.telegram.messenger;

import java.util.Locale;

/**
 * Unsent voice turned into text with {@code messages.transcribeDraft}.
 * The bytes stay in that one request. Nothing is sent as a message.
 */
public final class TellomiDraftTranscribe {

    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int CONSTRUCTOR = 0x7e11da47;

    private TellomiDraftTranscribe() {
    }

    public static boolean supportedName(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase(Locale.US);
        return lower.endsWith(".ogg") || lower.endsWith(".opus") || lower.endsWith(".wav") || lower.endsWith(".mp4");
    }

    /** Null when the file can be sent. Otherwise the local error code. */
    public static String localError(boolean loggedIn, long size, String path) {
        if (!loggedIn) {
            return "USER_ID_INVALID";
        }
        if (size <= 0 || !supportedName(path)) {
            return "MEDIA_EMPTY";
        }
        if (size > MAX_BYTES) {
            return "AUDIO_TOO_LONG";
        }
        return null;
    }

    public static String message(String code, boolean chinese) {
        if (code == null) {
            code = "TRANSCRIPTION_FAILED";
        }
        if (code.contains("USER_ID_INVALID")) {
            return chinese ? "请先登录后再把录音转成文字" : "Sign in before turning a recording into text.";
        }
        if (code.contains("MEDIA_EMPTY")) {
            return chinese ? "这段录音是空的，无法转成文字" : "This recording is empty.";
        }
        if (code.contains("AUDIO_TOO_LONG")) {
            return chinese ? "录音超过 8MB，无法转成文字" : "The recording is larger than 8MB.";
        }
        if (code.contains("TRANSCRIPTION_UNAVAILABLE")) {
            return chinese ? "听写服务暂不可用，请稍后再试" : "Transcription is unavailable right now.";
        }
        return chinese ? "没有识别出文字" : "No words were recognized.";
    }
}
