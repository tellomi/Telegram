package org.telegram.messenger;

import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Before a GIF, document, or unconverted video upload, ask
 * {@code messages.getDocumentByHash}. A hit swaps the request to
 * {@code inputMediaDocument}. Anything else runs {@code upload}.
 */
public final class TellomiDocumentByHashSend {

    private static final WeakHashMap<SendMessagesHelper.DelayedMessage, HashSet<Integer>> TRIED = new WeakHashMap<>();

    private TellomiDocumentByHashSend() {
    }

    /**
     * @return true when the caller must not start the upload; this method
     *         will run {@code upload}, {@code onHit}, or {@code onDrop}.
     */
    public static boolean lookupBeforeUpload(SendMessagesHelper helper, SendMessagesHelper.DelayedMessage message,
                                              int index, String path, Runnable upload, Runnable onHit, Runnable onDrop) {
        try {
            if (helper == null || message == null || upload == null || !TellomiDocumentByHash.isLocalFilePath(path)) {
                return false;
            }
            if (alreadyTried(message, index)) {
                return false;
            }
            File file = new File(path);
            if (!file.isFile() || file.length() <= 0) {
                return false;
            }
            TLRPC.InputMedia media = currentMedia(message, index);
            if (!(media instanceof TLRPC.TL_inputMediaUploadedDocument) || media.file != null) {
                return false;
            }
            if (message.sendEncryptedRequest != null || message.isLivePhoto || media.live_photo) {
                return false;
            }
            if (message.performCoverUpload || message.coverPhotoSize != null || media.video_cover != null) {
                return false;
            }
            if (media.stickers != null && !media.stickers.isEmpty()) {
                return false;
            }
            MessageObject obj = messageObject(message, index);
            VideoEditedInfo edited = editedInfo(message, obj, index);
            boolean needsConvert = edited != null && edited.needConvert();
            AttributeFlags flags = readFlags(media, obj);
            String mime = TellomiDocumentByHash.mimeFor(mimeOf(media, obj), path);
            int kind = TellomiDocumentByHash.kind(mime, flags.animated, flags.video, flags.voice, needsConvert,
                    false, false, false, false, false);
            if (kind == TellomiDocumentByHash.NONE || mime == null) {
                return false;
            }
            markTried(message, index);
            final String mimeFinal = mime;
            final AtomicBoolean once = new AtomicBoolean();
            final int[] requestId = new int[1];
            final WeakReference<SendMessagesHelper> helperRef = new WeakReference<>(helper);
            final Runnable timeout = () -> {
                if (!once.compareAndSet(false, true)) {
                    return;
                }
                SendMessagesHelper current = helperRef.get();
                if (current != null && requestId[0] != 0) {
                    current.getConnectionsManager().cancelRequest(requestId[0], false);
                }
                log("fail-open timeout " + TellomiDocumentByHash.kindName(kind));
                continueUpload(obj, upload, onDrop);
            };
            try {
                AndroidUtilities.runOnUIThread(timeout, TellomiDocumentByHash.FAIL_OPEN_MS);
                Utilities.globalQueue.postRunnable(() -> {
                long[] sizeOut = new long[1];
                byte[] hash = TellomiDocumentByHash.sha256(file, sizeOut);
                long size = sizeOut[0];
                onUi(() -> {
                    if (once.get()) {
                        return;
                    }
                    if (hash == null) {
                        if (once.compareAndSet(false, true)) {
                            AndroidUtilities.cancelRunOnUIThread(timeout);
                            log("fail-open hash " + TellomiDocumentByHash.kindName(kind));
                            continueUpload(obj, upload, onDrop);
                        }
                        return;
                    }
                    SendMessagesHelper current = helperRef.get();
                    if (current == null) {
                        if (once.compareAndSet(false, true)) {
                            AndroidUtilities.cancelRunOnUIThread(timeout);
                            continueUpload(obj, upload, onDrop);
                        }
                        return;
                    }
                    Get request = new Get();
                    request.sha256 = hash;
                    request.size = size;
                    request.mimeType = mimeFinal;
                    try {
                        if (once.get()) {
                            return;
                        }
                        requestId[0] = current.getConnectionsManager().sendRequest(request, (response, error) -> onUi(() -> {
                            if (!once.compareAndSet(false, true)) {
                                return;
                            }
                            AndroidUtilities.cancelRunOnUIThread(timeout);
                            if (!pending(obj)) {
                                if (onDrop != null) {
                                    onDrop.run();
                                }
                                return;
                            }
                            TLRPC.Document found = response instanceof TLRPC.Document ? (TLRPC.Document) response : null;
                            boolean empty = found == null || found instanceof TLRPC.TL_documentEmpty;
                            if (error != null || !TellomiDocumentByHash.reusable(empty, found == null ? 0 : found.id,
                                    found == null ? 0 : found.access_hash, found == null ? 0 : found.dc_id)) {
                                log("fail-open " + TellomiDocumentByHash.kindName(kind)
                                        + (error != null ? " " + error.text : " miss"));
                                upload.run();
                                return;
                            }
                            try {
                                applyHit(message, index, found, media);
                            } catch (Exception applyError) {
                                FileLog.e(applyError);
                                upload.run();
                                return;
                            }
                            log("hit " + TellomiDocumentByHash.kindName(kind) + " id=" + found.id);
                            if (onHit != null) {
                                onHit.run();
                            }
                        }), ConnectionsManager.RequestFlagFailOnServerErrors | ConnectionsManager.RequestFlagDoNotWaitFloodWait);
                        if (once.get() && requestId[0] != 0) {
                            current.getConnectionsManager().cancelRequest(requestId[0], false);
                        }
                    } catch (Exception rpcError) {
                        FileLog.e(rpcError);
                        if (once.compareAndSet(false, true)) {
                            AndroidUtilities.cancelRunOnUIThread(timeout);
                            continueUpload(obj, upload, onDrop);
                        }
                    }
                });
            });
            } catch (Exception scheduleError) {
                FileLog.e(scheduleError);
                AndroidUtilities.cancelRunOnUIThread(timeout);
                return false;
            }
            return true;
        } catch (Exception e) {
            FileLog.e(e);
            return false;
        }
    }

    private static void continueUpload(MessageObject obj, Runnable upload, Runnable onDrop) {
        if (pending(obj)) {
            upload.run();
        } else if (onDrop != null) {
            onDrop.run();
        }
    }

    private static void onUi(Runnable runnable) {
        if (ApplicationLoader.applicationHandler != null) {
            AndroidUtilities.runOnUIThread(runnable);
        } else {
            runnable.run();
        }
    }

    private static void applyHit(SendMessagesHelper.DelayedMessage message, int index, TLRPC.Document found, TLRPC.InputMedia previous) {
        TLRPC.TL_inputMediaDocument next = new TLRPC.TL_inputMediaDocument();
        next.id = new TLRPC.TL_inputDocument();
        next.id.id = found.id;
        next.id.access_hash = found.access_hash;
        next.id.file_reference = found.file_reference != null ? found.file_reference : new byte[0];
        if (previous != null) {
            next.spoiler = previous.spoiler;
            if (previous.ttl_seconds != 0) {
                next.ttl_seconds = previous.ttl_seconds;
                next.flags |= TLObject.FLAG_0;
            }
        }
        setMedia(message, index, next);
    }

    private static TLRPC.InputMedia currentMedia(SendMessagesHelper.DelayedMessage message, int index) {
        TLObject request = message.sendRequest;
        if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            TLRPC.TL_messages_sendMultiMedia multi = (TLRPC.TL_messages_sendMultiMedia) request;
            if (index < 0 || index >= multi.multi_media.size() || multi.multi_media.get(index) == null) {
                return null;
            }
            return multi.multi_media.get(index).media;
        }
        if (request instanceof TLRPC.TL_messages_sendMedia) {
            return unwrap(((TLRPC.TL_messages_sendMedia) request).media, index);
        }
        if (request instanceof org.telegram.tgnet.tl.TL_ephemeral.TL_sendMessage) {
            return unwrap(((org.telegram.tgnet.tl.TL_ephemeral.TL_sendMessage) request).media, index);
        }
        if (request instanceof TLRPC.TL_messages_editMessage) {
            return unwrap(((TLRPC.TL_messages_editMessage) request).media, index);
        }
        if (request instanceof TLRPC.TL_messages_addPollAnswer) {
            TLRPC.TL_messages_addPollAnswer poll = (TLRPC.TL_messages_addPollAnswer) request;
            if (poll.answer == null) {
                return null;
            }
            return unwrap(poll.answer.input_media, index);
        }
        return null;
    }

    private static TLRPC.InputMedia unwrap(TLRPC.InputMedia media, int index) {
        if (media instanceof TLRPC.TL_inputMediaPaidMedia) {
            ArrayList<TLRPC.InputMedia> list = ((TLRPC.TL_inputMediaPaidMedia) media).extended_media;
            int slot = index < 0 ? 0 : index;
            if (list == null || slot < 0 || slot >= list.size()) {
                return null;
            }
            return list.get(slot);
        }
        if (media instanceof TLRPC.TL_inputMediaPoll) {
            return null;
        }
        return media;
    }

    private static void setMedia(SendMessagesHelper.DelayedMessage message, int index, TLRPC.InputMedia next) {
        TLObject request = message.sendRequest;
        if (request instanceof TLRPC.TL_messages_sendMultiMedia) {
            TLRPC.TL_messages_sendMultiMedia multi = (TLRPC.TL_messages_sendMultiMedia) request;
            if (index >= 0 && index < multi.multi_media.size() && multi.multi_media.get(index) != null) {
                multi.multi_media.get(index).media = next;
            }
            return;
        }
        if (request instanceof TLRPC.TL_messages_sendMedia) {
            TLRPC.TL_messages_sendMedia send = (TLRPC.TL_messages_sendMedia) request;
            send.media = replaceSlot(send.media, index, next);
            return;
        }
        if (request instanceof org.telegram.tgnet.tl.TL_ephemeral.TL_sendMessage) {
            org.telegram.tgnet.tl.TL_ephemeral.TL_sendMessage send = (org.telegram.tgnet.tl.TL_ephemeral.TL_sendMessage) request;
            send.media = replaceSlot(send.media, index, next);
            return;
        }
        if (request instanceof TLRPC.TL_messages_editMessage) {
            TLRPC.TL_messages_editMessage edit = (TLRPC.TL_messages_editMessage) request;
            edit.media = replaceSlot(edit.media, index, next);
            return;
        }
        if (request instanceof TLRPC.TL_messages_addPollAnswer) {
            TLRPC.TL_messages_addPollAnswer poll = (TLRPC.TL_messages_addPollAnswer) request;
            if (poll.answer != null) {
                poll.answer.input_media = replaceSlot(poll.answer.input_media, index, next);
            }
        }
    }

    private static TLRPC.InputMedia replaceSlot(TLRPC.InputMedia media, int index, TLRPC.InputMedia next) {
        if (media instanceof TLRPC.TL_inputMediaPaidMedia) {
            ArrayList<TLRPC.InputMedia> list = ((TLRPC.TL_inputMediaPaidMedia) media).extended_media;
            int slot = index < 0 ? 0 : index;
            if (list != null && slot >= 0 && slot < list.size()) {
                list.set(slot, next);
            }
            return media;
        }
        if (media instanceof TLRPC.TL_inputMediaPoll) {
            return media;
        }
        return next;
    }

    private static MessageObject messageObject(SendMessagesHelper.DelayedMessage message, int index) {
        if (message.type == 4 && message.messageObjects != null && index >= 0 && index < message.messageObjects.size()) {
            return message.messageObjects.get(index);
        }
        return message.obj;
    }

    private static VideoEditedInfo editedInfo(SendMessagesHelper.DelayedMessage message, MessageObject obj, int index) {
        VideoEditedInfo info = obj != null ? obj.videoEditedInfo : null;
        if (message.type == 4 && message.videoEditedInfos != null && index >= 0 && index < message.videoEditedInfos.size()
                && message.videoEditedInfos.get(index) != null) {
            info = message.videoEditedInfos.get(index);
        }
        if (info == null) {
            info = message.videoEditedInfo;
        }
        return info;
    }

    private static String mimeOf(TLRPC.InputMedia media, MessageObject obj) {
        if (media != null && media.mime_type != null && !media.mime_type.isEmpty()) {
            return media.mime_type;
        }
        TLRPC.Document document = obj != null ? obj.getDocument() : null;
        if (document == null && obj != null) {
            document = obj.documentToPollAddOption;
        }
        return document != null ? document.mime_type : null;
    }

    private static AttributeFlags readFlags(TLRPC.InputMedia media, MessageObject obj) {
        AttributeFlags flags = new AttributeFlags();
        ArrayList<TLRPC.DocumentAttribute> attributes = media != null ? media.attributes : null;
        if ((attributes == null || attributes.isEmpty()) && obj != null) {
            TLRPC.Document document = obj.getDocument();
            if (document == null) {
                document = obj.documentToPollAddOption;
            }
            if (document != null) {
                attributes = document.attributes;
            }
        }
        if (attributes == null) {
            return flags;
        }
        for (int i = 0; i < attributes.size(); i++) {
            TLRPC.DocumentAttribute attribute = attributes.get(i);
            if (attribute instanceof TLRPC.TL_documentAttributeAnimated) {
                flags.animated = true;
            } else if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                flags.video = true;
            } else if (attribute instanceof TLRPC.TL_documentAttributeAudio && attribute.voice) {
                flags.voice = true;
            }
        }
        return flags;
    }

    private static boolean pending(MessageObject obj) {
        if (obj == null || obj.messageOwner == null) {
            return true;
        }
        int state = obj.messageOwner.send_state;
        return state == MessageObject.MESSAGE_SEND_STATE_SENDING || state == MessageObject.MESSAGE_SEND_STATE_EDITING;
    }

    private static boolean alreadyTried(SendMessagesHelper.DelayedMessage message, int index) {
        HashSet<Integer> indexes = TRIED.get(message);
        return indexes != null && indexes.contains(index);
    }

    private static void markTried(SendMessagesHelper.DelayedMessage message, int index) {
        HashSet<Integer> indexes = TRIED.get(message);
        if (indexes == null) {
            indexes = new HashSet<>();
            TRIED.put(message, indexes);
        }
        indexes.add(index);
    }

    private static void log(String message) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("getDocumentByHash " + message);
        }
    }

    private static final class AttributeFlags {
        boolean animated;
        boolean video;
        boolean voice;
    }

    /** {@code messages.getDocumentByHash#b1f2061f sha256:bytes size:long mime_type:string}. */
    public static final class Get extends TLObject {
        public byte[] sha256;
        public long size;
        public String mimeType;

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            return TLRPC.Document.TLdeserialize(stream, constructor, exception);
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            stream.writeInt32(TellomiDocumentByHash.CONSTRUCTOR);
            stream.writeByteArray(sha256);
            stream.writeInt64(size);
            stream.writeString(mimeType);
        }
    }
}
