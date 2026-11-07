package org.telegram.ui.tellomi.feed;

import android.graphics.BitmapFactory;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Turns a phone-fetched Open Graph result into the native {@code webPage}
 * the composer and the bubble already draw, then fills
 * {@code inputMediaWebPage#c21b8849} flags.3–6.
 */
public final class TellomiChatLinkPage {

    private static final AtomicInteger LOCAL_IDS = new AtomicInteger(1);
    private static final IdentityHashMap<TLRPC.WebPage, String> FILES = new IdentityHashMap<>();
    private static final IdentityHashMap<TLRPC.WebPage, String> IMAGES = new IdentityHashMap<>();

    private TellomiChatLinkPage() {
    }

    public static boolean clientFetch(String raw) {
        return TellomiChatLink.clientFetch(raw, BuildVars.LINK_HOST);
    }

    /** First link in the message, when it is an external page the phone should fetch. */
    public static String firstExternal(String text, ArrayList<TLRPC.MessageEntity> entities) {
        String raw = firstLink(text, entities);
        if (!clientFetch(raw)) {
            return null;
        }
        return TellomiDiscoverLink.httpUrl(raw);
    }

    /**
     * Fetch on the phone network and deliver the native page on the UI thread.
     * A missing card is null. The image, if any, is attached to the same object later.
     */
    public static void load(String url, String displayedText, Utilities.Callback<TLRPC.WebPage> done) {
        if (!clientFetch(url)) {
            if (done != null) {
                AndroidUtilities.runOnUIThread(() -> done.run(null));
            }
            return;
        }
        TellomiDiscoverLinkFetch.load(url, preview -> {
            TLRPC.WebPage page = pageFrom(preview, url, displayedText);
            if (done != null) {
                done.run(page);
            }
            if (page == null || preview == null || preview.image == null) {
                return;
            }
            final String image = preview.image;
            synchronized (IMAGES) {
                IMAGES.put(page, image);
            }
            Thread thread = new Thread(() -> {
                byte[] data = TellomiDiscoverLinkFetch.imageBytes(image);
                if (data == null || data.length == 0) {
                    return;
                }
                AndroidUtilities.runOnUIThread(() -> attachPhoto(page, data));
            }, "tellomi-chat-link-image");
            thread.setDaemon(true);
            thread.start();
        });
    }

    /** Copy title, description, and site name. A server {@code telegram_*} card is left as a URL. */
    public static void fill(TLRPC.TL_inputMediaWebPage input, TLRPC.WebPage page) {
        if (input == null || !clientCard(page)) {
            return;
        }
        String host = TellomiDiscoverLink.host(page.url);
        input.title = TellomiChatLink.clampRunes(page.title, TellomiChatLink.TITLE_RUNES);
        if (input.title == null) {
            input.title = host;
        }
        input.description = TellomiChatLink.clampRunes(page.description, TellomiChatLink.DESCRIPTION_RUNES);
        input.site_name = TellomiChatLink.clampRunes(page.site_name, TellomiChatLink.SITE_RUNES);
        if (input.site_name == null) {
            input.site_name = host;
        }
        if (page.photo instanceof TLRPC.TL_photo) {
            TLRPC.TL_photo photo = (TLRPC.TL_photo) page.photo;
            if (photo.access_hash != 0) {
                input.photo = inputPhoto(photo);
            }
        }
    }

    /**
     * Upload a locally fetched image, then run {@code send}.
     * Returns false when there is nothing to upload and {@code send} should run now.
     */
    public static boolean preparePhoto(int account, TLRPC.InputPeer peer, TLRPC.WebPage page, TLRPC.TL_inputMediaWebPage input, Runnable send) {
        if (input == null || send == null || !clientCard(page)) {
            return false;
        }
        if (input.photo != null) {
            return false;
        }
        String path;
        synchronized (FILES) {
            path = FILES.get(page);
        }
        if (path != null) {
            upload(account, peer, path, page, input, send);
            return true;
        }
        String image;
        synchronized (IMAGES) {
            image = IMAGES.get(page);
        }
        if (image == null) {
            return false;
        }
        Thread thread = new Thread(() -> {
            byte[] data = TellomiDiscoverLinkFetch.imageBytes(image);
            AndroidUtilities.runOnUIThread(() -> {
                String saved = data == null ? null : attachPhoto(page, data);
                if (saved == null) {
                    send.run();
                } else {
                    upload(account, peer, saved, page, input, send);
                }
            });
        }, "tellomi-chat-link-upload");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    public static boolean clientCard(TLRPC.WebPage page) {
        if (page == null || page.url == null) {
            return false;
        }
        if (page.type != null && page.type.startsWith("telegram_")) {
            return false;
        }
        return clientFetch(page.url);
    }

    private static TLRPC.WebPage pageFrom(TellomiDiscoverLink.Preview preview, String requested, String displayedText) {
        String url = preview == null ? TellomiDiscoverLink.httpUrl(requested) : preview.url;
        if (!clientFetch(url)) {
            return null;
        }
        TLRPC.TL_webPage page = new TLRPC.TL_webPage();
        page.id = -Math.abs(Utilities.random.nextLong());
        if (page.id == 0) {
            page.id = -1;
        }
        page.url = url;
        page.display_url = displayUrl(url);
        page.displayedText = displayedText;
        page.date = (int) (System.currentTimeMillis() / 1000L);
        page.type = "article";
        String host = TellomiDiscoverLink.host(url);
        page.title = TellomiChatLink.clampRunes(preview == null ? null : preview.title, TellomiChatLink.TITLE_RUNES);
        if (page.title == null) {
            page.title = host;
        }
        page.description = TellomiChatLink.clampRunes(preview == null ? null : preview.description, TellomiChatLink.DESCRIPTION_RUNES);
        page.site_name = TellomiChatLink.clampRunes(preview == null ? null : preview.siteName, TellomiChatLink.SITE_RUNES);
        if (page.site_name == null) {
            page.site_name = host;
        }
        return page;
    }

    private static String attachPhoto(TLRPC.WebPage page, byte[] data) {
        if (page == null || data == null || data.length == 0) {
            return null;
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }
        File dir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
        if (dir == null) {
            return null;
        }
        int localId = -LOCAL_IDS.incrementAndGet();
        TLRPC.TL_fileLocationToBeDeprecated location = new TLRPC.TL_fileLocationToBeDeprecated();
        location.volume_id = Integer.MIN_VALUE;
        location.local_id = localId;
        location.dc_id = Integer.MIN_VALUE;
        File file = new File(dir, location.volume_id + "_" + location.local_id + ".jpg");
        try {
            FileOutputStream out = new FileOutputStream(file);
            try {
                out.write(data);
            } finally {
                out.close();
            }
        } catch (Exception ignored) {
            return null;
        }
        TLRPC.TL_photoSize size = new TLRPC.TL_photoSize();
        size.type = "m";
        size.w = bounds.outWidth;
        size.h = bounds.outHeight;
        size.size = data.length;
        size.location = location;
        TLRPC.TL_photo photo = new TLRPC.TL_photo();
        photo.id = page.id;
        photo.date = page.date;
        photo.file_reference = new byte[0];
        photo.sizes.add(size);
        page.photo = photo;
        page.type = "photo";
        String path = file.getAbsolutePath();
        synchronized (FILES) {
            FILES.put(page, path);
        }
        return path;
    }

    private static void upload(int account, TLRPC.InputPeer peer, String path, TLRPC.WebPage page, TLRPC.TL_inputMediaWebPage input, Runnable send) {
        NotificationCenter center = NotificationCenter.getInstance(account);
        NotificationCenter.NotificationCenterDelegate[] observer = new NotificationCenter.NotificationCenterDelegate[1];
        Runnable unlisten = () -> {
            center.removeObserver(observer[0], NotificationCenter.fileUploaded);
            center.removeObserver(observer[0], NotificationCenter.fileUploadFailed);
        };
        observer[0] = (id, acc, args) -> {
            if (!(args[0] instanceof String) || !path.equals(args[0])) {
                return;
            }
            unlisten.run();
            if (id != NotificationCenter.fileUploaded || !(args[1] instanceof TLRPC.InputFile)) {
                send.run();
                return;
            }
            TLRPC.TL_messages_uploadMedia req = new TLRPC.TL_messages_uploadMedia();
            req.peer = peer;
            TLRPC.TL_inputMediaUploadedPhoto uploaded = new TLRPC.TL_inputMediaUploadedPhoto();
            uploaded.file = (TLRPC.InputFile) args[1];
            req.media = uploaded;
            ConnectionsManager.getInstance(account).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                if (res instanceof TLRPC.TL_messageMediaPhoto) {
                    TLRPC.Photo uploadedPhoto = ((TLRPC.TL_messageMediaPhoto) res).photo;
                    if (uploadedPhoto instanceof TLRPC.TL_photo) {
                        input.photo = inputPhoto((TLRPC.TL_photo) uploadedPhoto);
                        page.photo = uploadedPhoto;
                        page.type = "photo";
                    }
                }
                send.run();
            }));
        };
        center.addObserver(observer[0], NotificationCenter.fileUploaded);
        center.addObserver(observer[0], NotificationCenter.fileUploadFailed);
        FileLoader.getInstance(account).uploadFile(path, false, true, ConnectionsManager.FileTypePhoto);
    }

    private static TLRPC.TL_inputPhoto inputPhoto(TLRPC.TL_photo photo) {
        TLRPC.TL_inputPhoto inputPhoto = new TLRPC.TL_inputPhoto();
        inputPhoto.id = photo.id;
        inputPhoto.access_hash = photo.access_hash;
        inputPhoto.file_reference = photo.file_reference == null ? new byte[0] : photo.file_reference;
        return inputPhoto;
    }

    private static String firstLink(String text, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities != null && text != null) {
            for (int i = 0; i < entities.size(); i++) {
                TLRPC.MessageEntity entity = entities.get(i);
                if (entity instanceof TLRPC.TL_messageEntityTextUrl && entity.url != null && !entity.url.isEmpty()) {
                    return entity.url;
                }
                if (entity instanceof TLRPC.TL_messageEntityUrl) {
                    int start = entity.offset;
                    int end = entity.offset + entity.length;
                    if (start >= 0 && end <= text.length() && end > start) {
                        return text.substring(start, end);
                    }
                }
            }
        }
        return TellomiDiscoverLink.firstUrl(text);
    }

    private static String displayUrl(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host == null) {
                return url;
            }
            String path = uri.getRawPath();
            if (path == null || "/".equals(path)) {
                path = "";
            }
            String query = uri.getRawQuery();
            return host + path + (query == null || query.isEmpty() ? "" : "?" + query);
        } catch (Exception ignored) {
            return url;
        }
    }
}
