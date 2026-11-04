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
