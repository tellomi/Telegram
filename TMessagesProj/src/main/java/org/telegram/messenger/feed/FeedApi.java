package org.telegram.messenger.feed;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.tl.TL_discover;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/**
 * HTTPS client for content-engine /v1/feed. Auth is discover.engineCredential (token, expires, region).
 * region is cn or global and is not a host. The host comes from help.getAppConfig.
 */
public class FeedApi {

    public interface Callback<T> {
        void run(T value, String error);
    }

    private final int account;
    private String token = "";
    private int tokenExpires;

    public FeedApi(int account) {
        this.account = account;
    }

    public void getPage(final String pathAndQuery, final Callback<FeedModels.Page> callback) {
        request("GET", pathAndQuery, null, new Callback<JSONObject>() {
            @Override
            public void run(JSONObject value, String error) {
                if (value == null) {
                    callback.run(null, error);
                    return;
                }
                callback.run(FeedModels.parsePage(value), null);
            }
        });
    }

    public void getJson(String pathAndQuery, Callback<JSONObject> callback) {
        request("GET", pathAndQuery, null, callback);
    }

    public void sendJson(String method, String path, JSONObject body, Callback<JSONObject> callback) {
        request(method, path, body, callback);
    }

    public void uploadFile(final File file, final String filename, final String mime, final Callback<String> callback) {
        if (!FeedConfig.enabled(account)) {
            callback.run(null, "disabled");
            return;
        }
        withToken(new Callback<String>() {
            @Override
            public void run(String tokenValue, String error) {
                if (tokenValue == null) {
                    callback.run(null, error);
                    return;
                }
                Utilities.stageQueue.postRunnable(() -> {
                    try {
                        String key = tusUpload(tokenValue, file, filename, mime);
                        AndroidUtilities.runOnUIThread(() -> callback.run(key, key == null ? "upload_failed" : null));
                    } catch (Exception e) {
                        FileLog.e(e);
                        AndroidUtilities.runOnUIThread(() -> callback.run(null, "upload_failed"));
                    }
                });
            }
        });
    }

    private void request(final String method, final String path, final JSONObject body, final Callback<JSONObject> callback) {
        if (!FeedConfig.enabled(account)) {
            callback.run(null, "disabled");
            return;
        }
        withToken(new Callback<String>() {
            @Override
            public void run(final String tokenValue, String error) {
                if (tokenValue == null) {
                    callback.run(null, error == null ? "auth" : error);
                    return;
                }
                Utilities.stageQueue.postRunnable(() -> {
                    HttpResult result = call(method, path, body, tokenValue);
                    AndroidUtilities.runOnUIThread(() -> {
                        if (result.code == 401) {
                            token = "";
                            tokenExpires = 0;
                        }
                        if (result.json == null) {
                            callback.run(null, result.error == null ? "network" : result.error);
                        } else if (result.json.has("error") && result.code >= 400) {
                            callback.run(result.json, result.json.optString("error", "error"));
                        } else {
                            callback.run(result.json, null);
                        }
                    });
                });
            }
        });
    }

    private void withToken(final Callback<String> callback) {
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        if (token != null && token.length() > 0 && tokenExpires > now + 30) {
            callback.run(token, null);
            return;
        }
        TL_discover.TL_discover_getEngineCredential req = new TL_discover.TL_discover_getEngineCredential();
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TL_discover.TL_discover_engineCredential) {
                TL_discover.TL_discover_engineCredential cred = (TL_discover.TL_discover_engineCredential) response;
                token = cred.token == null ? "" : cred.token;
                tokenExpires = cred.expires;
                if (token.length() == 0) {
                    callback.run(null, "auth");
                } else {
                    callback.run(token, null);
                }
            } else {
                callback.run(null, error == null ? "auth" : error.text);
            }
        }));
    }

    public void audienceToken(String kind, ArrayList<Long> userIds, final Callback<String> callback) {
        TL_discover.TL_discover_getAudienceToken req = new TL_discover.TL_discover_getAudienceToken();
        req.kind = kind;
        if (userIds != null) {
            req.user_ids.addAll(userIds);
        }
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TL_discover.TL_discover_audienceToken) {
                callback.run(((TL_discover.TL_discover_audienceToken) response).token, null);
            } else {
                callback.run(null, error == null ? "auth" : error.text);
            }
        }));
    }

    private HttpResult call(String method, String path, JSONObject body, String bearer) {
        HttpResult result = new HttpResult();
        HttpURLConnection connection = null;
        try {
            String base = FeedConfig.engineBase(account);
            URL url = new URL(base + (path.startsWith("/") ? path : "/" + path));
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + bearer);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = body.toString().getBytes("UTF-8");
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setFixedLengthStreamingMode(bytes.length);
                OutputStream out = connection.getOutputStream();
                out.write(bytes);
                out.close();
            }
            result.code = connection.getResponseCode();
            if (FeedContract.noBodySuccess(result.code)) {
                result.json = new JSONObject();
                return result;
            }
            InputStream in;
            try {
                in = result.code >= 400 ? connection.getErrorStream() : connection.getInputStream();
            } catch (Exception ignored) {
                if (result.code >= 200 && result.code < 300) {
                    result.json = new JSONObject();
                    return result;
                }
                throw ignored;
            }
            String text = readAll(in);
            if (text != null && text.length() > 0 && (text.charAt(0) == '{' || text.charAt(0) == '[')) {
                if (text.charAt(0) == '[') {
                    JSONObject wrap = new JSONObject();
                    wrap.put("items", new JSONArray(text));
                    result.json = wrap;
                } else {
                    result.json = new JSONObject(text);
                }
            } else if (result.code >= 200 && result.code < 300) {
                result.json = new JSONObject();
            }
            if (result.json != null && result.json.has("error") && result.code >= 400) {
                result.error = result.json.optString("error", "error");
            } else if (result.code == 404) {
                result.error = "not_found";
                if (result.json == null) {
                    result.json = new JSONObject();
                    result.json.put("error", "not_found");
                }
            } else if (result.code >= 400) {
                result.error = "http_" + result.code;
            }
        } catch (Exception e) {
            FileLog.e(e);
            result.error = "network";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return result;
    }

    private String tusUpload(String bearer, File file, String filename, String mime) throws Exception {
        String base = FeedConfig.engineBase(account);
        URL url = new URL(base + "/v1/feed/media/uploads");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Authorization", "Bearer " + bearer);
        connection.setRequestProperty("Tus-Resumable", "1.0.0");
        connection.setRequestProperty("Upload-Length", String.valueOf(file.length()));
        String meta = "filename " + base64(filename) + ",filetype " + base64(mime == null ? "application/octet-stream" : mime);
        connection.setRequestProperty("Upload-Metadata", meta);
        connection.setFixedLengthStreamingMode(0);
        connection.setDoOutput(true);
        connection.getOutputStream().close();
        int code = connection.getResponseCode();
        String location = connection.getHeaderField("Location");
        String body = readAll(code >= 400 ? connection.getErrorStream() : connection.getInputStream());
        connection.disconnect();
        if (code >= 400) {
            return null;
        }
        String key = null;
        if (body != null && body.startsWith("{")) {
            JSONObject json = new JSONObject(body);
            key = json.optString("key", json.optString("id", ""));
            if (key.length() == 0) {
                key = null;
            }
        }
        if (location == null || location.length() == 0) {
            return key;
        }
        if (!location.startsWith("http")) {
            location = base + (location.startsWith("/") ? location : "/" + location);
        }
        FileInputStream in = new FileInputStream(file);
        byte[] buf = new byte[256 * 1024];
        long offset = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            HttpURLConnection patch = (HttpURLConnection) new URL(location).openConnection();
            patch.setConnectTimeout(15000);
            patch.setReadTimeout(60000);
            patch.setRequestMethod("PATCH");
            patch.setRequestProperty("Authorization", "Bearer " + bearer);
            patch.setRequestProperty("Tus-Resumable", "1.0.0");
            patch.setRequestProperty("Upload-Offset", String.valueOf(offset));
            patch.setRequestProperty("Content-Type", "application/offset+octet-stream");
            patch.setFixedLengthStreamingMode(n);
            patch.setDoOutput(true);
            OutputStream out = patch.getOutputStream();
            out.write(buf, 0, n);
            out.close();
            int patchCode = patch.getResponseCode();
            String uploadKey = patch.getHeaderField("Upload-Key");
            if (uploadKey != null && uploadKey.length() > 0) {
                key = uploadKey;
            }
            patch.disconnect();
            if (patchCode >= 400) {
                in.close();
                return null;
            }
            offset += n;
        }
        in.close();
        if (key == null) {
            int slash = location.lastIndexOf('/');
            key = slash >= 0 ? location.substring(slash + 1) : location;
        }
        return key;
    }

    private static String base64(String value) {
        try {
            return android.util.Base64.encodeToString(value.getBytes("UTF-8"), android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    private static String readAll(InputStream in) {
        if (in == null) {
            return "";
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            return out.toString("UTF-8");
        } catch (Exception e) {
            FileLog.e(e);
            return "";
        }
    }

    public static String enc(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static class HttpResult {
        int code;
        JSONObject json;
        String error;
    }

}
