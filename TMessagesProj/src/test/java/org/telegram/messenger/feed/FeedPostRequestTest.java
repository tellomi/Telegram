package org.telegram.messenger.feed;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Strict stand-in for content-engine DisallowUnknownFields on POST /v1/feed/posts.
 * A field the engine does not declare is rejected. media and location are not declared.
 */
public class FeedPostRequestTest {

    private static final Set<String> POST_FIELDS = new HashSet<>(Arrays.asList(
            "kind", "text", "title", "visibility", "audience", "poll",
            "comments_closed", "like_count_hidden", "story_ttl_hours", "allow_screenshot",
            "quote_of", "topics", "reply_moderation", "client_nonce",
            "media_keys", "city", "allow_nearby", "place_id"
    ));

    private static final Set<String> COMMENT_FIELDS = new HashSet<>(Arrays.asList(
            "text", "parent_id", "media_keys", "sticker", "client_nonce"
    ));

    @Test
    public void textOnlyPublicPostHasNoMediaOrLocation() {
        FeedContract.PostRequest request = base("hello");
        FeedContract.PostBuild built = FeedContract.buildPost(request, null, null);
        assertAccepted(built);
        JsonObject body = json(built.body);
        assertEquals("post", body.get("kind").getAsString());
        assertEquals("hello", body.get("text").getAsString());
        assertEquals("public", body.get("visibility").getAsString());
        assertFalse(body.has("media_keys"));
        assertFalse(body.has("media"));
        assertFalse(body.has("location"));
        assertFalse(body.has("audience"));
        assertFalse(body.has("city"));
        assertFalse(body.has("allow_nearby"));
    }

    @Test
    public void multiImageUsesMediaKeyStrings() {
        FeedContract.PostRequest request = base("");
        request.mediaKeys.addAll(Arrays.asList("m/a", "m/b", "m/c"));
        request.mediaTypes.addAll(Arrays.asList("image", "image", "image"));
        FeedContract.PostBuild built = FeedContract.buildPost(request, null, null);
        assertAccepted(built);
        JsonArray keys = json(built.body).getAsJsonArray("media_keys");
        assertEquals(3, keys.size());
        assertEquals("m/a", keys.get(0).getAsString());
        assertEquals("m/c", keys.get(2).getAsString());
        assertTrue(keys.get(0).isJsonPrimitive());
        assertFalse(json(built.body).has("media"));
    }

    @Test
    public void videoKeyIsAStringAndExtraImagesStopAtTen() {
        FeedContract.PostRequest request = base("");
        for (int i = 0; i < 12; i++) {
            request.mediaKeys.add("m/img-" + i);
            request.mediaTypes.add("image");
        }
        request.mediaKeys.add("m/clip");
        request.mediaTypes.add("video");
        FeedContract.PostBuild built = FeedContract.buildPost(request, null, null);
        assertAccepted(built);
        JsonArray keys = json(built.body).getAsJsonArray("media_keys");
        assertEquals(11, keys.size());
        assertEquals("m/img-0", keys.get(0).getAsString());
        assertEquals("m/img-9", keys.get(9).getAsString());
        assertEquals("m/clip", keys.get(10).getAsString());
        assertFalse(json(built.body).has("media"));
    }

    @Test
    public void publicNearbyUsesRootCityAndPlace() {
        FeedContract.PostRequest request = base("nearby");
        request.city = "cn:chengdu";
        request.cityName = "Chengdu";
        request.allowNearby = true;
        request.placeId = "poi-1";
        request.placeName = "A park";
        FeedContract.PostBuild built = FeedContract.buildPost(request, "stale-token", null);
        assertAccepted(built);
        JsonObject body = json(built.body);
        assertEquals("public", body.get("visibility").getAsString());
        assertEquals("cn:chengdu", body.get("city").getAsString());
        assertTrue(body.get("allow_nearby").getAsBoolean());
        assertEquals("poi-1", body.get("place_id").getAsString());
        assertFalse(body.has("location"));
        assertFalse(body.has("city_name"));
        assertFalse(body.has("place"));
        assertFalse(body.has("audience"));
        assertFalse(built.body.contains("Chengdu"));
        assertFalse(built.body.contains("A park"));
        assertFalse(built.body.contains("stale-token"));
    }

    @Test
    public void contactsAndCloseFriendsSendNoAudienceObject() {
        FeedContract.PostRequest contacts = base("hi");
        contacts.visibility = "contacts";
        contacts.allowNearby = true;
        FeedContract.PostBuild contactsBuilt = FeedContract.buildPost(contacts, null, null);
        assertAccepted(contactsBuilt);
        assertEquals("contacts", json(contactsBuilt.body).get("visibility").getAsString());
        assertFalse(json(contactsBuilt.body).has("audience"));
        assertFalse(json(contactsBuilt.body).has("allow_nearby"));

        FeedContract.PostRequest close = base("hi");
        close.visibility = "close_friends";
        FeedContract.PostBuild closeBuilt = FeedContract.buildPost(close, null, null);
        assertAccepted(closeBuilt);
        assertEquals("close_friends", json(closeBuilt.body).get("visibility").getAsString());
        assertFalse(json(closeBuilt.body).has("audience"));

        FeedContract.PostRequest friends = base("hi");
        friends.visibility = "mutual_followers";
        FeedContract.PostBuild friendsBuilt = FeedContract.buildPost(friends, null, null);
        assertAccepted(friendsBuilt);
        assertEquals("friends", json(friendsBuilt.body).get("visibility").getAsString());
        assertEquals("friends", friendsBuilt.visibility);
    }

    @Test
    public void selectedAndExcludedUseAudienceToken() {
        FeedContract.PostRequest selected = base("only them");
        selected.visibility = "selected";
        selected.userIds.add(11L);
        selected.userIds.add(12L);
        FeedContract.PostBuild built = FeedContract.buildPost(selected, "token-include", null);
        assertAccepted(built);
        JsonObject body = json(built.body);
        assertEquals("include", body.get("visibility").getAsString());
        JsonObject audience = body.getAsJsonObject("audience");
        assertEquals("token-include", audience.get("audience_token").getAsString());
        assertEquals(1, audience.size());
        assertFalse(audience.has("kind"));
        assertFalse(body.has("user_ids"));

        FeedContract.PostRequest excluded = base("not them");
        excluded.visibility = "public";
        excluded.userIds.add(21L);
        FeedContract.PostBuild excludedBuilt = FeedContract.buildPost(excluded, "token-exclude", null);
        assertAccepted(excludedBuilt);
        assertEquals("exclude", json(excludedBuilt.body).get("visibility").getAsString());
        assertEquals("token-exclude", json(excludedBuilt.body).getAsJsonObject("audience").get("audience_token").getAsString());
        assertEquals(Arrays.asList(21L), excludedBuilt.userIds);
    }

    @Test
    public void quoteOfIsPreserved() {
        FeedContract.PostRequest request = base("adding to this");
        request.quoteOf = "quoted-post";
        FeedContract.PostBuild built = FeedContract.buildPost(request, null, null);
        assertAccepted(built);
        assertEquals("quoted-post", json(built.body).get("quote_of").getAsString());
        assertFalse(json(built.body).has("quote"));
    }

    @Test
    public void nonceStaysFixedAcrossRetriesAndMustBeAUuid() {
        FeedContract.PostRequest request = base("retry me");
        String nonce = request.clientNonce;
        assertTrue(FeedContract.isClientNonce(nonce));
        FeedContract.PostBuild first = FeedContract.buildPost(request, null, null);
        FeedContract.PostBuild second = FeedContract.buildPost(request, null, null);
        assertEquals(first.body, second.body);
        assertEquals(nonce, first.nonce);
        assertEquals(nonce, second.nonce);
        assertEquals(nonce, json(first.body).get("client_nonce").getAsString());

        request.clientNonce = "4815162342-" + System.currentTimeMillis();
        FeedContract.PostBuild rejected = FeedContract.buildPost(request, null, null);
        assertFalse(rejected.send);
        assertNull(rejected.body);
        assertEquals("invalid_client_nonce", rejected.error);
        assertFalse(FeedContract.isClientNonce(rejected.nonce) && rejected.send);
    }

    @Test
    public void tokenFailureKeepsTheSelectionAndDoesNotBecomePublic() {
        FeedContract.PostRequest request = base("private");
        request.visibility = "include";
        request.userIds.add(4L);
        request.userIds.add(5L);
        String nonce = request.clientNonce;
        FeedContract.PostBuild blocked = FeedContract.buildPost(request, null, "AUDIENCE_INVALID");
        assertFalse(blocked.send);
        assertNull(blocked.body);
        assertEquals("include", blocked.visibility);
        assertEquals(Arrays.asList(4L, 5L), blocked.userIds);
        assertEquals(nonce, blocked.nonce);
        assertEquals("include", request.visibility);
        assertEquals(2, request.userIds.size());

        FeedContract.PostBuild missing = FeedContract.buildPost(request, "", null);
        assertFalse(missing.send);
        assertEquals("include", missing.visibility);
        assertNull(missing.body);

        FeedContract.PostRequest contacts = base("contacts");
        contacts.visibility = "contacts";
        contacts.userIds.add(9L);
        FeedContract.PostBuild widened = FeedContract.buildPost(contacts, "token", null);
        assertFalse(widened.send);
        assertNull(widened.body);
        assertEquals("contacts", widened.visibility);
        assertEquals(Arrays.asList(9L), widened.userIds);
    }

    @Test
    public void draftKeepsMembersQuoteAndCityAndOldSelectedDraftStaysInclude() {
        FeedContract.PostRequest request = base("draft");
        request.visibility = "exclude";
        request.userIds.add(7L);
        request.userIds.add(8L);
        request.quoteOf = "q-1";
        request.city = "cn:chengdu";
        request.cityName = "Chengdu";
        request.allowNearby = true;
        request.placeId = "poi-9";
        ArrayList<String> paths = new ArrayList<>();
        paths.add("/tmp/a.jpg");
        ArrayList<String> types = new ArrayList<>();
        types.add("image");
        String raw = FeedContract.draftJson(request, paths, types);
        ArrayList<String> readPaths = new ArrayList<>();
        ArrayList<String> readTypes = new ArrayList<>();
        FeedContract.PostRequest restored = FeedContract.parseDraft(raw, readPaths, readTypes);
        assertEquals("exclude", restored.visibility);
        assertEquals(Arrays.asList(7L, 8L), restored.userIds);
        assertEquals("q-1", restored.quoteOf);
        assertEquals("cn:chengdu", restored.city);
        assertEquals("Chengdu", restored.cityName);
        assertEquals("poi-9", restored.placeId);
        assertEquals(Arrays.asList("/tmp/a.jpg"), readPaths);
        assertEquals(Arrays.asList("image"), readTypes);
        restored.clientNonce = request.clientNonce;
        restored.text = request.text;
        FeedContract.PostBuild built = FeedContract.buildPost(restored, "tok", null);
        assertAccepted(built);
        assertEquals("q-1", json(built.body).get("quote_of").getAsString());
        assertEquals("exclude", json(built.body).get("visibility").getAsString());

        FeedContract.PostRequest legacy = FeedContract.parseDraft("{\"audience\":\"selected\",\"text\":\"x\"}", null, null);
        assertEquals("include", legacy.visibility);
        assertTrue(legacy.userIds.isEmpty());
        legacy.clientNonce = FeedContract.newClientNonce();
        FeedContract.PostBuild legacyBuilt = FeedContract.buildPost(legacy, null, "AUDIENCE_INVALID");
        assertFalse(legacyBuilt.send);
        assertEquals("include", legacyBuilt.visibility);
        assertNull(legacyBuilt.body);
    }

    @Test
    public void mockRejectsUnknownMediaAndLocation() {
        String unknown = mockCreate("{\"kind\":\"post\",\"media\":[{\"key\":\"m/a\"}],\"location\":{\"city\":\"cn:chengdu\"}}");
        assertTrue(unknown.startsWith("unknown:"));
        assertEquals("unknown:attachment", mockComment("{\"text\":\"hi\",\"attachment\":{\"type\":\"image\"}}"));
    }

    @Test
    public void audienceUpdateCarriesTokenAndDropsContentFields() {
        ArrayList<Long> ids = new ArrayList<>();
        ids.add(3L);
        FeedContract.PostBuild built = FeedContract.buildAudience("selected", ids, "tok", null);
        assertTrue(built.send);
        assertNull(mockCreate(built.body));
        JsonObject body = json(built.body);
        assertEquals("include", body.get("visibility").getAsString());
        assertEquals("tok", body.getAsJsonObject("audience").get("audience_token").getAsString());
        assertFalse(body.has("kind"));
        assertFalse(body.has("text"));
        assertFalse(body.has("client_nonce"));

        FeedContract.PostBuild failed = FeedContract.buildAudience("exclude", ids, null, "USER_ID_INVALID");
        assertFalse(failed.send);
        assertNull(failed.body);
        assertEquals("exclude", failed.visibility);
        assertEquals(Arrays.asList(3L), failed.userIds);
    }

    @Test
    public void commentFailureKeepsTextReplyAttachmentAndNonce() {
        FeedContract.CommentDraft draft = new FeedContract.CommentDraft();
        draft.prepare("hello", "parent-1", "image", "m/pic");
        String nonce = draft.nonce;
        assertTrue(FeedContract.isClientNonce(nonce));
        draft.onFailure();
        assertEquals("hello", draft.text);
        assertEquals("parent-1", draft.parentId);
        assertEquals("image", draft.attachmentType);
        assertEquals("m/pic", draft.attachmentKey);
        assertEquals(nonce, draft.nonce);
        draft.prepare("hello!", "parent-1", "image", "m/pic");
        assertEquals(nonce, draft.nonce);
        assertEquals("hello!", draft.text);
        String first = draft.body();
        String second = draft.body();
        assertEquals(first, second);
        assertNull(mockComment(first));
        assertEquals(nonce, json(first).get("client_nonce").getAsString());
        assertEquals("m/pic", json(first).getAsJsonArray("media_keys").get(0).getAsString());
        draft.onSuccess();
        assertEquals("", draft.text);
        assertEquals("", draft.parentId);
        assertEquals("", draft.attachmentKey);
        assertEquals("", draft.nonce);
        draft.prepare("next", "", "", "");
        assertFalse(nonce.equals(draft.nonce));
        assertTrue(FeedContract.isClientNonce(draft.nonce));
    }

    @Test
    public void listTapsUseTheSheetAndDeepLinksUseDetail() {
        assertTrue(FeedContract.opensCommentSheet(FeedContract.ENTRY_BODY));
        assertTrue(FeedContract.opensCommentSheet(FeedContract.ENTRY_COMMENT));
        assertTrue(FeedContract.opensCommentSheet(FeedContract.ENTRY_TOPIC));
        assertTrue(FeedContract.opensCommentSheet(FeedContract.ENTRY_NOTIFICATION));
        assertFalse(FeedContract.opensStandaloneDetail(FeedContract.ENTRY_BODY));
        assertTrue(FeedContract.opensStandaloneDetail(FeedContract.ENTRY_DEEP_LINK));
        assertFalse(FeedContract.opensCommentSheet(FeedContract.ENTRY_DEEP_LINK));
    }

    @Test
    public void serverCityNamesWinAndTheSixCitiesAreOnlyAFallback() {
        String[][] fallback = new String[][]{
                {"cn:beijing", "Beijing"},
                {"cn:shanghai", "Shanghai"},
                {"cn:guangzhou", "Guangzhou"}
        };
        ArrayList<String> codes = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        codes.add("cn:tokyo");
        names.add(FeedContract.serverCityName("東京", "Tokyo", "Tokio"));
        codes.add("cn:beijing");
        names.add(FeedContract.serverCityName("北京", "Beijing", "Peking"));
        ArrayList<String[]> rows = FeedContract.resolveCities(codes, names, fallback, "shanghai");
        assertEquals(2, rows.size());
        assertEquals("cn:tokyo", rows.get(0)[0]);
        assertEquals("東京", rows.get(0)[1]);
        assertEquals("北京", rows.get(1)[1]);

        codes.clear();
        names.clear();
        codes.add("cn:beijing");
        names.add(FeedContract.serverCityName("", "Peking", ""));
        rows = FeedContract.resolveCities(codes, names, fallback, "");
        assertEquals(1, rows.size());
        assertEquals("Peking", rows.get(0)[1]);

        codes.clear();
        names.clear();
        codes.add("cn:beijing");
        names.add("");
        rows = FeedContract.resolveCities(codes, names, fallback, "");
        assertEquals("Beijing", rows.get(0)[1]);

        rows = FeedContract.resolveCities(new ArrayList<String>(), new ArrayList<String>(), fallback, "shang");
        assertEquals(1, rows.size());
        assertEquals("cn:shanghai", rows.get(0)[0]);
        assertEquals("Shanghai", rows.get(0)[1]);
        assertTrue(readCityActivity().contains("resolveCities"));
        assertTrue(readCityActivity().contains("fallbackCities"));
    }

    @Test
    public void newMomentsStringsExistInEveryBuiltInLanguage() throws Exception {
        String[] keys = new String[]{
                "Moments_Comment_SendFailed",
                "Moments_Audience_Kept",
                "Moments_Notif_Other",
                "Moments_City_Beijing",
                "Moments_City_Shanghai",
                "Moments_City_Guangzhou",
                "Moments_City_Shenzhen",
                "Moments_City_Chengdu",
                "Moments_City_Hangzhou"
        };
        String[] folders = new String[]{
                "values", "values-ar", "values-de", "values-es", "values-it", "values-ko",
                "values-nl", "values-pt-rBR", "values-ru", "values-uk", "values-zh-rCN", "values-zh-rTW"
        };
        String english = readRes("values/strings.xml");
        for (String folder : folders) {
            String xml = readRes(folder + "/strings.xml");
            for (String key : keys) {
                String value = stringValue(xml, key);
                assertTrue(folder + " missing " + key, value != null && value.trim().length() > 0);
                if (!"values".equals(folder) && !key.startsWith("Moments_City_")) {
                    assertFalse(folder + " " + key + " repeats English", value.equals(stringValue(english, key)));
                }
            }
        }
    }

    @Test
    public void routingAndCopySourcesMatchTheContract() throws Exception {
        String activity = read("ui/Feed/FeedActivity.java");
        int open = activity.indexOf("void onOpen");
        int helper = activity.indexOf("void openInFeed");
        assertTrue(open >= 0 && helper > open);
        String onOpen = activity.substring(open, helper);
        assertTrue(onOpen.contains("ENTRY_BODY"));
        assertFalse(onOpen.contains("presentFragment"));
        assertTrue(activity.contains("ENTRY_COMMENT"));
        assertTrue(activity.contains("FeedCommentSheet.open"));
        assertTrue(read("ui/Feed/FeedLinks.java").contains("FeedDetailActivity"));
        assertTrue(read("ui/Feed/FeedLinks.java").contains("ENTRY_DEEP_LINK"));
        assertFalse(read("ui/Feed/FeedDetailActivity.java").contains("void onResume"));
        String topic = read("ui/Feed/FeedTopicActivity.java");
        assertTrue(topic.contains("ENTRY_TOPIC"));
        assertTrue(topic.contains("FeedCommentSheet.open"));
        assertFalse(topic.contains("FeedDetailActivity"));
        String notifications = read("ui/Feed/FeedNotificationsActivity.java");
        assertTrue(notifications.contains("ENTRY_NOTIFICATION"));
        assertTrue(notifications.contains("Moments_Notif_Other"));
        assertFalse(notifications.contains("return kind"));
        String city = read("ui/Feed/FeedCityActivity.java");
        assertTrue(city.contains("Moments_City_Beijing"));
        assertTrue(city.contains("Moments_City_Hangzhou"));
        assertFalse(city.contains("北京"));
        assertFalse(city.contains("杭州"));
        String controller = read("messenger/feed/FeedController.java");
        assertTrue(controller.contains("FeedContract.buildPost"));
        assertTrue(controller.contains("FeedContract.newClientNonce"));
        assertFalse(controller.contains("put(\"media\""));
        assertFalse(controller.contains("put(\"location\""));
        assertFalse(controller.contains("random.nextLong"));
        String composer = read("ui/Feed/FeedComposerActivity.java");
        assertTrue(composer.contains("quoteOf"));
        assertTrue(composer.contains("audienceUserIds"));
        assertFalse(composer.contains("random.nextLong"));
        assertFalse(composer.contains("link_preview"));
        String sheet = read("ui/Feed/FeedCommentSheet.java");
        assertTrue(sheet.contains("commentDraft.onSuccess"));
        assertTrue(sheet.contains("commentDraft.onFailure"));
        assertTrue(sheet.contains("Moments_Comment_SendFailed"));
        int send = sheet.indexOf("private void send()");
        int clearPending = sheet.indexOf("private void clearPending()");
        assertTrue(send >= 0 && clearPending > send);
        String sendMethod = sheet.substring(send, clearPending);
        assertTrue(sendMethod.contains("commentDraft.nonce"));
        assertTrue(sendMethod.indexOf("onSuccess") < sendMethod.indexOf("setText(\"\")"));
        assertTrue(sendMethod.contains("onFailure"));
    }

    private static void assertAccepted(FeedContract.PostBuild built) {
        assertTrue(built.error, built.send);
        assertNull(mockCreate(built.body));
    }

    private static String mockCreate(String body) {
        JsonObject o = parseObject(body);
        if (o == null) {
            return "bad_request";
        }
        for (String key : o.keySet()) {
            if (!POST_FIELDS.contains(key)) {
                return "unknown:" + key;
            }
        }
        if (!o.has("kind") && !o.has("visibility")) {
            return "bad_request";
        }
        if (o.has("kind")) {
            String kind = o.get("kind").getAsString();
            if (!"post".equals(kind) && !"article".equals(kind) && !"story".equals(kind)) {
                return "invalid_kind";
            }
        }
        if (!o.has("visibility")) {
            return "invalid_visibility";
        }
        String visibility = o.get("visibility").getAsString();
        if (!Arrays.asList("public", "friends", "contacts", "close_friends", "include", "exclude", "only_me").contains(visibility)) {
            return "invalid_visibility";
        }
        if (o.has("client_nonce") && !FeedContract.isClientNonce(o.get("client_nonce").getAsString())) {
            return "invalid_client_nonce";
        }
        if (o.has("audience")) {
            if (!"include".equals(visibility) && !"exclude".equals(visibility)) {
                return "invalid_audience";
            }
            if (!o.get("audience").isJsonObject()) {
                return "invalid_audience";
            }
            JsonObject audience = o.getAsJsonObject("audience");
            if (audience.size() != 1 || !audience.has("audience_token") || audience.get("audience_token").getAsString().length() == 0) {
                return "invalid_audience";
            }
        } else if ("include".equals(visibility) || "exclude".equals(visibility)) {
            return "invalid_audience";
        }
        if (o.has("media_keys")) {
            if (!o.get("media_keys").isJsonArray()) {
                return "invalid_media";
            }
            JsonArray keys = o.getAsJsonArray("media_keys");
            for (JsonElement key : keys) {
                if (!key.isJsonPrimitive() || !key.getAsJsonPrimitive().isString() || key.getAsString().length() == 0) {
                    return "invalid_media";
                }
            }
        }
        if (o.has("allow_nearby") && (!o.get("allow_nearby").getAsBoolean() || !"public".equals(visibility))) {
            return "invalid_nearby";
        }
        if (o.has("city") && !o.get("city").isJsonPrimitive()) {
            return "invalid_city";
        }
        if (o.has("place_id") && !o.get("place_id").isJsonPrimitive()) {
            return "invalid_place";
        }
        if (o.has("quote_of") && !o.get("quote_of").isJsonPrimitive()) {
            return "invalid_quote";
        }
        return null;
    }

    private static String mockComment(String body) {
        JsonObject o = parseObject(body);
        if (o == null) {
            return "bad_request";
        }
        for (String key : o.keySet()) {
            if (!COMMENT_FIELDS.contains(key)) {
                return "unknown:" + key;
            }
        }
        if (o.has("client_nonce") && !FeedContract.isClientNonce(o.get("client_nonce").getAsString())) {
            return "invalid_client_nonce";
        }
        return null;
    }

    private static JsonObject json(String body) {
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static JsonObject parseObject(String body) {
        try {
            JsonElement element = JsonParser.parseString(body);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        } catch (Exception ignore) {
        }
        return null;
    }

    private static FeedContract.PostRequest base(String text) {
        FeedContract.PostRequest request = new FeedContract.PostRequest();
        request.kind = "post";
        request.text = text;
        request.visibility = "public";
        request.clientNonce = FeedContract.newClientNonce();
        return request;
    }

    private static String readCityActivity() {
        try {
            return read("ui/Feed/FeedCityActivity.java");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String readRes(String relative) throws Exception {
        Path[] roots = new Path[]{
                Paths.get("src/main/res", relative),
                Paths.get("TMessagesProj/src/main/res", relative)
        };
        for (int i = 0; i < roots.length; i++) {
            if (Files.isRegularFile(roots[i])) {
                return new String(Files.readAllBytes(roots[i]), "UTF-8");
            }
        }
        throw new IllegalStateException("missing " + relative);
    }

    private static String stringValue(String xml, String name) {
        String token = "name=\"" + name + "\">";
        int start = xml.indexOf(token);
        if (start < 0) {
            return null;
        }
        start += token.length();
        int end = xml.indexOf("</string>", start);
        if (end < 0) {
            return null;
        }
        return xml.substring(start, end);
    }

    private static String read(String relative) throws Exception {
        Path[] roots = new Path[]{
                Paths.get("src/main/java/org/telegram", relative),
                Paths.get("TMessagesProj/src/main/java/org/telegram", relative)
        };
        for (int i = 0; i < roots.length; i++) {
            if (Files.isRegularFile(roots[i])) {
                return new String(Files.readAllBytes(roots[i]), "UTF-8");
            }
        }
        throw new IllegalStateException("missing " + relative);
    }
}
