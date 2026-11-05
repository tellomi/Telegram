package org.telegram.ui.tellomi.feed;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLObject;

/**
 * teamgram {@code discover.getEngineCredential} and {@code discover.getAudienceToken}.
 * Neither is sent unless the account is logged in. The engine JWT is not a login session.
 */
public final class TellomiDiscoverClient {

    private static final TellomiDiscoverHttps HTTPS = new TellomiDiscoverHttps();

    private TellomiDiscoverClient() {
    }

    public static TellomiDiscover.Loader loader(final int account) {
        return new TellomiDiscover.Loader(new TellomiDiscover.Clock() {
            @Override
            public long now() {
                int time = ConnectionsManager.getInstance(account).getCurrentTime();
                if (time > 0) {
                    return time;
                }
                return System.currentTimeMillis() / 1000L;
            }
        }, null, new TellomiDiscover.CredentialFetch() {
            @Override
            public void fetch(final TellomiDiscover.CredentialCallback callback) {
                fetchCredential(account, callback);
            }
        }, new TellomiDiscover.HttpTransport() {
            @Override
            public void perform(final TellomiDiscover.HttpCall call, final TellomiDiscover.HttpCallback callback) {
                HTTPS.perform(call, new TellomiDiscover.HttpCallback() {
                    @Override
                    public void onResult(final TellomiDiscover.HttpResult result) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                callback.onResult(result);
                            }
                        });
                    }
                });
            }
        });
    }

    public static void fetchCredential(int account, final TellomiDiscover.CredentialCallback callback) {
        if (!UserConfig.getInstance(account).isClientActivated()) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(null, TellomiDiscover.ClientError.SIGNED_OUT);
                }
            });
            return;
        }
        ConnectionsManager.getInstance(account).sendRequest(new GetEngineCredential(), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                TellomiDiscover.ClientError mapped = TellomiDiscover.clientError(error.text, error.code);
                // This RPC's USER_ID_INVALID means the signed-in account is gone.
                if (mapped == TellomiDiscover.ClientError.USER_INVALID) {
                    mapped = TellomiDiscover.ClientError.DISABLED;
                }
                callback.onResult(null, mapped);
                return;
            }
            if (!(response instanceof CredentialBox)) {
                callback.onResult(null, TellomiDiscover.ClientError.UNREADABLE);
                return;
            }
            callback.onResult(((CredentialBox) response).credential, null);
        }));
    }

    public static void fetchAudienceToken(int account, String kind, long[] userIds, final AudienceCallback callback) {
        if (!UserConfig.getInstance(account).isClientActivated()) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(null, TellomiDiscover.ClientError.SIGNED_OUT);
                }
            });
            return;
        }
        ConnectionsManager.getInstance(account).sendRequest(new GetAudienceToken(kind, userIds), (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                callback.onResult(null, TellomiDiscover.clientError(error.text, error.code));
                return;
            }
            if (!(response instanceof AudienceBox)) {
                callback.onResult(null, TellomiDiscover.ClientError.UNREADABLE);
                return;
            }
            callback.onResult(((AudienceBox) response).token, null);
        }));
    }

    public interface AudienceCallback {
        void onResult(TellomiDiscover.AudienceToken token, TellomiDiscover.ClientError error);
    }

    public interface UploadCallback {
        void onResult(java.util.List<String> keys, TellomiDiscover.ClientError error);
    }

    public interface ImageCallback {
        void onImage(int index, byte[] bytes);
    }

    /** Tus each file, then return card keys in the same order. An empty list does not hit the network. */
    public static void uploadImages(final int account, final String baseUrl, final java.util.List<TellomiDiscoverMedia.Bytes> files, final UploadCallback callback) {
        if (files == null || files.isEmpty()) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(java.util.Collections.<String>emptyList(), null);
                }
            });
            return;
        }
        fetchCredential(account, new TellomiDiscover.CredentialCallback() {
            @Override
            public void onResult(TellomiDiscover.Credential credential, TellomiDiscover.ClientError error) {
                if (error != null || credential == null || credential.token == null || credential.token.isEmpty()) {
                    callback.onResult(null, error == null ? TellomiDiscover.ClientError.MEDIA_UPLOAD : error);
                    return;
                }
                pump(TellomiDiscoverMedia.Upload.start(baseUrl, credential.token, files), callback);
            }
        });
    }

    private static void pump(final TellomiDiscoverMedia.Upload upload, final UploadCallback callback) {
        if (upload.finished()) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (upload.error != null) {
                        callback.onResult(null, upload.error);
                    } else {
                        callback.onResult(upload.keys(), null);
                    }
                }
            });
            return;
        }
        final TellomiDiscover.HttpCall call = upload.next();
        if (call == null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    callback.onResult(null, TellomiDiscover.ClientError.MEDIA_UPLOAD);
                }
            });
            return;
        }
        HTTPS.perform(call, new TellomiDiscover.HttpCallback() {
            @Override
            public void onResult(TellomiDiscover.HttpResult result) {
                upload.consume(result);
                pump(upload, callback);
            }
        });
    }

    /** One credential, then a GET per key. A miss calls back with a null body. */
    public static void loadImages(final int account, final String baseUrl, final java.util.List<TellomiDiscover.Image> images, final ImageCallback callback) {
        if (images == null || images.isEmpty() || callback == null) {
            return;
        }
        fetchCredential(account, new TellomiDiscover.CredentialCallback() {
            @Override
            public void onResult(TellomiDiscover.Credential credential, TellomiDiscover.ClientError error) {
                if (error != null || credential == null || credential.token == null) {
                    for (int i = 0; i < images.size(); i++) {
                        callback.onImage(i, null);
                    }
                    return;
                }
                for (int i = 0; i < images.size(); i++) {
                    final int index = i;
                    String url = TellomiDiscoverMedia.imageUrl(baseUrl, images.get(i).key);
                    if (url == null || baseUrl == null || !url.startsWith(baseUrl)) {
                        callback.onImage(index, null);
                        continue;
                    }
                    HTTPS.perform(new TellomiDiscover.HttpCall("GET", url, null, credential.token, true, null), new TellomiDiscover.HttpCallback() {
                        @Override
                        public void onResult(final TellomiDiscover.HttpResult result) {
                            final byte[] bytes = result != null && result.error == null && result.status == 200 ? result.body : null;
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    callback.onImage(index, bytes);
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    public static final class GetEngineCredential extends TLObject {
        @Override
        public void serializeToStream(OutputSerializedData stream) {
            writeBytes(stream, TellomiDiscover.Tl.encodeRequest());
        }

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TellomiDiscover.Tl.ENGINE_CREDENTIAL_ID) {
                return null;
            }
            String token = stream.readString(exception);
            int expires = stream.readInt32(exception);
            String region = stream.readString(exception);
            if (token == null || token.isEmpty() || (!"cn".equals(region) && !"global".equals(region))) {
                return null;
            }
            CredentialBox box = new CredentialBox();
            box.credential = new TellomiDiscover.Credential(token, expires & 0xffffffffL, region);
            return box;
        }
    }

    public static final class GetAudienceToken extends TLObject {
        private final String kind;
        private final long[] userIds;

        public GetAudienceToken(String kind, long[] userIds) {
            this.kind = kind;
            this.userIds = userIds == null ? new long[0] : userIds;
        }

        @Override
        public void serializeToStream(OutputSerializedData stream) {
            writeBytes(stream, TellomiDiscover.Tl.encodeAudienceRequest(kind, userIds));
        }

        @Override
        public TLObject deserializeResponse(InputSerializedData stream, int constructor, boolean exception) {
            if (constructor != TellomiDiscover.Tl.AUDIENCE_TOKEN_ID) {
                return null;
            }
            String token = stream.readString(exception);
            int expires = stream.readInt32(exception);
            if (token == null || token.isEmpty()) {
                return null;
            }
            AudienceBox box = new AudienceBox();
            box.token = new TellomiDiscover.AudienceToken(token, expires & 0xffffffffL);
            return box;
        }
    }

    public static final class CredentialBox extends TLObject {
        public TellomiDiscover.Credential credential;
    }

    public static final class AudienceBox extends TLObject {
        public TellomiDiscover.AudienceToken token;
    }

    private static void writeBytes(OutputSerializedData stream, byte[] bytes) {
        for (int i = 0; i < bytes.length; i++) {
            stream.writeByte(bytes[i]);
        }
    }
}
