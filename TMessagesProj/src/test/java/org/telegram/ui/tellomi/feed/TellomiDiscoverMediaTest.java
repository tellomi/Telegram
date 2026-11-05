package org.telegram.ui.tellomi.feed;

import org.junit.Test;
import org.telegram.ui.TellomiPack;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Multi-image pick, tus upload, and the post media field (D-10). */
public class TellomiDiscoverMediaTest {

    private static final String NONCE = "11111111-1111-4111-8111-111111111111";

    @Test
    public void sniffAcceptsJpegPngAndWebpOnly() {
        assertEquals("image/jpeg", TellomiDiscoverMedia.mime(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}));
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};
        assertEquals("image/png", TellomiDiscoverMedia.mime(png));
        byte[] webp = new byte[12];
        webp[0] = 'R';
        webp[1] = 'I';
        webp[2] = 'F';
        webp[3] = 'F';
        webp[8] = 'W';
        webp[9] = 'E';
        webp[10] = 'B';
        webp[11] = 'P';
        assertEquals("image/webp", TellomiDiscoverMedia.mime(webp));
        assertNull(TellomiDiscoverMedia.mime(new byte[] {'G', 'I', 'F', '8'}));
        assertEquals(TellomiDiscover.ClientError.MEDIA_TYPE, TellomiDiscoverMedia.inspect(new byte[0]));
        assertEquals(TellomiDiscover.ClientError.MEDIA_TYPE, TellomiDiscoverMedia.inspect(new byte[] {'G', 'I', 'F', '8', '9', 'a'}));
        assertNull(TellomiDiscoverMedia.inspect(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));
    }

    @Test
    public void twentyMegabytesIsTheLimit() {
        byte[] ok = new byte[TellomiDiscoverMedia.MAX_BYTES];
        ok[0] = (byte) 0xFF;
        ok[1] = (byte) 0xD8;
        ok[2] = (byte) 0xFF;
        assertNull(TellomiDiscoverMedia.inspect(ok));
        byte[] over = new byte[TellomiDiscoverMedia.MAX_BYTES + 1];
        over[0] = (byte) 0xFF;
        over[1] = (byte) 0xD8;
        over[2] = (byte) 0xFF;
        assertEquals(TellomiDiscover.ClientError.MEDIA_TOO_LARGE, TellomiDiscoverMedia.inspect(over));
        assertEquals(10, TellomiDiscoverMedia.MAX_IMAGES);
    }

    @Test
    public void publishBodyUsesTheCardMediaShape() {
        byte[] plain = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null);
        TellomiJson without = TellomiJson.parse(plain);
        assertNull(without.objectValue.get("media"));
        byte[] posted = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, null, Arrays.asList("m/a", "m/b"));
        TellomiJson json = TellomiJson.parse(posted);
        assertNull(json.objectValue.get("media"));
        TellomiJson keys = json.objectValue.get("media_keys");
        assertEquals(2, keys.arrayValue.size());
        assertEquals("m/a", keys.arrayValue.get(0).stringValue);
        assertEquals("m/b", keys.arrayValue.get(1).stringValue);
        byte[] photosOnly = TellomiDiscover.publishBody("  ", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, null, Collections.singletonList("m/a"));
        TellomiJson only = TellomiJson.parse(photosOnly);
        assertEquals("", only.objectValue.get("text").stringValue);
        assertEquals("m/a", only.objectValue.get("media_keys").arrayValue.get(0).stringValue);
        assertNull(TellomiDiscover.publishBody("  ", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null));
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, null, Arrays.asList("..")));
        assertNull(TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, null, Collections.nCopies(11, "m/a")));
        TellomiDiscover.HttpCall call = TellomiDiscover.httpCall(
            "https://feed.example",
            TellomiDiscover.Operation.publish("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, null, Collections.singletonList("m/a")),
            "jwt"
        );
        assertEquals("/v1/feed/posts", call.path());
        assertEquals("m/a", TellomiJson.parse(call.body).objectValue.get("media_keys").arrayValue.get(0).stringValue);
        TellomiDiscoverLink.Preview preview = new TellomiDiscoverLink.Preview("https://example.com/a", "Hello", null, null);
        byte[] both = TellomiDiscover.publishBody("hi", TellomiDiscover.Visibility.EVERYONE, null, NONCE, 500, null, null, null, preview, Collections.singletonList("m/a"));
        TellomiJson joined = TellomiJson.parse(both);
        assertEquals("https://example.com/a", joined.objectValue.get("link_preview").objectValue.get("url").stringValue);
        assertEquals("m/a", joined.objectValue.get("media_keys").arrayValue.get(0).stringValue);
        assertNull(joined.objectValue.get("media"));
        assertNull(joined.objectValue.get("topics"));
    }

    @Test
    public void cardImagesAreMoreThanACount() {
        byte[] json = ("{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"card\":{\"text\":\"好看\",\"media\":[{\"type\":\"image\",\"key\":\"m/a\"},{\"type\":\"image\",\"key\":\"m/b\"},{\"type\":\"video\",\"key\":\"v/c\"},{\"type\":\"image\"}]}}]}")
            .getBytes(StandardCharsets.UTF_8);
        TellomiDiscover.Card card = TellomiDiscoverParse.cards(json).get(0);
        assertEquals(3, card.imageCount);
        assertEquals(2, card.images.size());
        assertEquals("m/a", card.images.get(0).key);
        assertEquals("m/b", card.images.get(1).key);
        assertNull(card.images.get(0).url);
        byte[] withUrl = ("{\"cards\":[{\"id\":\"post-9\",\"state\":\"ok\",\"card\":{\"text\":\"\",\"media\":[{\"type\":\"image\",\"key\":\"m/a\",\"url\":\"https://r2.example/a.jpg?X-Amz-Signature=abc\"},{\"type\":\"image\",\"key\":\"m/b\"}]}}]}")
            .getBytes(StandardCharsets.UTF_8);
        TellomiDiscover.Card remote = TellomiDiscoverParse.cards(withUrl).get(0);
        assertEquals("https://r2.example/a.jpg?X-Amz-Signature=abc", remote.images.get(0).url);
        assertNull(remote.images.get(1).url);
        TellomiDiscover.Post post = TellomiDiscoverParse.displayPost(card);
        assertEquals(2, post.images.size());
        assertEquals("https://r2.example/a.jpg?X-Amz-Signature=abc", TellomiDiscoverMedia.displayUrl(remote.images.get(0).url, "https://cdn.example/media", "m/a"));
        assertEquals("https://feed.example/media/m/a", TellomiDiscoverMedia.imageUrl("https://feed.example/media", "m/a"));
        assertEquals("https://cdn.example/files/m/b", TellomiDiscoverMedia.displayUrl(null, "https://cdn.example/files/", "m/b"));
        assertNull(TellomiDiscoverMedia.imageUrl("https://feed.example/media", "../secret"));
        assertTrue(!TellomiDiscoverMedia.imageUrl("https://feed.example/media", "m/a").contains("/v1/feed/media"));
        byte[] config = "{\"enabled\":true,\"region\":\"cn\",\"tabs\":[\"recommend\"],\"media_base_url\":\"https://cdn.example/media\"}".getBytes(StandardCharsets.UTF_8);
        assertEquals("https://cdn.example/media", TellomiDiscoverParse.engineConfig(config).mediaBaseUrl);
        assertNull(TellomiDiscoverParse.engineConfig("{\"enabled\":true,\"region\":\"cn\",\"tabs\":[\"recommend\"]}".getBytes(StandardCharsets.UTF_8)).mediaBaseUrl);
    }

    @Test
    public void tusCreateThenPatchReturnsTheKey() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9, 8};
        TellomiDiscoverMedia.Upload upload = TellomiDiscoverMedia.Upload.start(
            "https://feed.example",
            "jwt",
            Collections.singletonList(new TellomiDiscoverMedia.Bytes("照片.jpg", jpeg))
        );
        TellomiDiscover.HttpCall create = upload.next();
        assertEquals("POST", create.method);
        assertEquals("/v1/feed/media/uploads", create.path());
        assertEquals("1.0.0", create.header("Tus-Resumable"));
        assertEquals("5", create.header("Upload-Length"));
        assertTrue(create.authorizes);
        assertEquals("jwt", create.token);
        assertNull(create.body);
        String metadata = create.header("Upload-Metadata");
        assertTrue(metadata.startsWith("filename "));
        assertTrue(metadata.contains(",filetype "));
        String filename = new String(Base64.getDecoder().decode(metadata.split(" ")[1].split(",")[0]), StandardCharsets.UTF_8);
        String filetype = new String(Base64.getDecoder().decode(metadata.substring(metadata.lastIndexOf(' ') + 1)), StandardCharsets.UTF_8);
        assertEquals("照片.jpg", filename);
        assertEquals("image/jpeg", filetype);
        Map<String, String> created = new HashMap<>();
        created.put("location", "/v1/feed/media/uploads/up-1");
        created.put("upload-offset", "0");
        upload.consume(new TellomiDiscover.HttpResult(201, new byte[0], created));
        TellomiDiscover.HttpCall patch = upload.next();
        assertEquals("PATCH", patch.method);
        assertEquals("https://feed.example/v1/feed/media/uploads/up-1", patch.url);
        assertEquals("0", patch.header("Upload-Offset"));
        assertEquals("application/offset+octet-stream", patch.contentType);
        assertEquals(5, patch.body.length);
        Map<String, String> done = new HashMap<>();
        done.put("upload-offset", "5");
        upload.consume(new TellomiDiscover.HttpResult(204, "{\"key\":\"m/a\"}".getBytes(StandardCharsets.UTF_8), done));
        assertTrue(upload.finished());
        assertEquals(Collections.singletonList("m/a"), upload.keys());
        assertNull(TellomiDiscoverMedia.resolve("https://feed.example/v1/feed/media/uploads", "https://evil.example/v1/feed/media/uploads/up-1"));
    }

    @Test
    public void locationIdIsUsedWhenTheBodyHasNoKey() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        TellomiDiscoverMedia.Upload upload = TellomiDiscoverMedia.Upload.start(
            "https://feed.example",
            "jwt",
            Collections.singletonList(new TellomiDiscoverMedia.Bytes("a.jpg", jpeg))
        );
        upload.next();
        Map<String, String> created = new HashMap<>();
        created.put("location", "https://feed.example/v1/feed/media/uploads/abc-def");
        created.put("upload-offset", "4");
        upload.consume(new TellomiDiscover.HttpResult(201, new byte[0], created));
        assertEquals(Collections.singletonList("abc-def"), upload.keys());
        assertEquals("med/01", TellomiDiscoverMedia.uploadedKey("{\"id\":\"med/01\"}".getBytes(StandardCharsets.UTF_8), "https://feed.example/v1/feed/media/uploads/other"));
    }

    @Test
    public void failuresStayVisible() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1};
        TellomiDiscoverMedia.Upload network = TellomiDiscoverMedia.Upload.start("https://feed.example", "jwt", Collections.singletonList(new TellomiDiscoverMedia.Bytes("a.jpg", jpeg)));
        network.next();
        network.consume(new TellomiDiscover.HttpResult(TellomiDiscover.ClientError.UNAVAILABLE));
        assertEquals(TellomiDiscover.ClientError.MEDIA_NETWORK, network.error);
        TellomiDiscoverMedia.Upload rejected = TellomiDiscoverMedia.Upload.start("https://feed.example", "jwt", Collections.singletonList(new TellomiDiscoverMedia.Bytes("a.jpg", jpeg)));
        rejected.next();
        rejected.consume(new TellomiDiscover.HttpResult(413, new byte[0]));
        assertEquals(TellomiDiscover.ClientError.MEDIA_TOO_LARGE, rejected.error);
        List<TellomiDiscoverMedia.Bytes> eleven = Collections.nCopies(11, new TellomiDiscoverMedia.Bytes("a.jpg", jpeg));
        assertEquals(TellomiDiscover.ClientError.MEDIA_LIMIT, TellomiDiscoverMedia.Upload.start("https://feed.example", "jwt", eleven).error);
        TellomiDiscover.Copy copy = TellomiDiscover.Copy.resolve("zh");
        assertEquals("一次最多 10 张图片。", copy.failureText(TellomiDiscover.ClientError.MEDIA_LIMIT));
        assertEquals("这张图片超过 20MB。", copy.failureText(TellomiDiscover.ClientError.MEDIA_TOO_LARGE));
        assertEquals("只支持 JPEG、PNG 或 WebP。", copy.failureText(TellomiDiscover.ClientError.MEDIA_TYPE));
        assertEquals("图片上传失败。", copy.failureText(TellomiDiscover.ClientError.MEDIA_UPLOAD));
        assertEquals("网络不好，图片没有传上去。", copy.failureText(TellomiDiscover.ClientError.MEDIA_NETWORK));
        assertEquals("一次最多 10 張圖片。", TellomiDiscover.Copy.resolve("zh-TW").failureText(TellomiDiscover.ClientError.MEDIA_LIMIT));
        assertEquals("圖片上傳失敗。", TellomiPack.bundled("Tellomi.Discover.PhotoUploadFailed", "zh-Hant"));
        assertEquals("图片上传失败。", TellomiPack.bundled("Tellomi.Discover.PhotoUploadFailed", "zh-Hans"));
        assertEquals("发现", TellomiPack.bundled("Tellomi.Discover.Feed", "zh-Hant"));
    }
}
