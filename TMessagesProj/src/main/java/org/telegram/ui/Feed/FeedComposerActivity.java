package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedController;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PollCreateActivity;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedComposerActivity extends BaseFragment {

    private static final int REQ_AUDIO = 41;

    private final String quoteId;
    private final String editId;
    private final String draftId;

    private EditText field;
    private TextView audienceView;
    private TextView counter;
    private TextView attachments;
    private FeedRecordWave recordWave;
    private TextView publish;
    private StoryPrivacyBottomSheet.StoryPrivacy privacy;
    private boolean allowNearby;
    private String cityCode = "";
    private String cityName = "";
    private String placeName = "";
    private boolean original;
    private final ArrayList<String> paths = new ArrayList<>();
    private final ArrayList<String> types = new ArrayList<>();
    private JSONObject poll;
    private FeedVoiceCapture voice;
    private boolean dirty;
    private TextView previewView;
    private TLRPC.WebPage foundPage;
    private boolean removedLink;
    private int previewRequest;
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern MENTION_PATTERN = Pattern.compile("(?<![\\w@])@([A-Za-z][A-Za-z0-9_]{3,31})");

    public FeedComposerActivity(Bundle args) {
        super(args);
        quoteId = args == null ? null : args.getString("quote");
        editId = args == null ? null : args.getString("edit");
        draftId = args == null ? null : args.getString("draft");
    }

    public static FeedComposerActivity quote(String id) {
        Bundle args = new Bundle();
        args.putString("quote", id);
        return new FeedComposerActivity(args);
    }

    public static FeedComposerActivity edit(String id) {
        Bundle args = new Bundle();
        args.putString("edit", id);
        return new FeedComposerActivity(args);
    }

    private FeedController controller() {
        return FeedController.getInstance(currentAccount);
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(LocaleController.getString("Moments_Composer_Title", R.string.Moments_Composer_Title));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    tryLeave();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        publish = new TextView(context);
        publish.setText(LocaleController.getString("Moments_Publish", R.string.Moments_Publish));
        publish.setTextColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
        publish.setTypeface(AndroidUtilities.bold());
        publish.setPadding(dp(12), dp(8), dp(12), dp(8));
        publish.setOnClickListener(v -> publish());
        menu.addItem(1, R.drawable.ic_ab_done).setOnClickListener(v -> publish());

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        column.setPadding(dp(16), dp(12), dp(16), dp(12));

        audienceView = new TextView(context);
        audienceView.setText(LocaleController.getString("Moments_Audience_Choose", R.string.Moments_Audience_Choose));
        audienceView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        audienceView.setTextSize(15);
        audienceView.setOnClickListener(v -> FeedAudiencePicker.open(this, privacy, picked -> {
            privacy = picked;
            dirty = true;
            audienceView.setText(FeedAudiencePicker.label(picked));
            refreshPublish();
        }));
        column.addView(audienceView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        field = new EditText(context);
        field.setHint(LocaleController.getString("Moments_Composer_Hint", R.string.Moments_Composer_Hint));
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        field.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        field.setBackground(null);
        field.setMinLines(6);
        field.setGravity(Gravity.TOP);
        field.setTextSize(org.telegram.messenger.SharedConfig.fontSize);
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                dirty = true;
                int left = FeedConfig.MAX_TEXT - s.length();
                counter.setVisibility(s.length() >= FeedConfig.TEXT_COUNTER_FROM ? View.VISIBLE : View.GONE);
                counter.setText(String.valueOf(left));
                refreshPublish();
                schedulePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > FeedConfig.MAX_TEXT) {
                    s.delete(FeedConfig.MAX_TEXT, s.length());
                }
            }
        });
        column.addView(field, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        counter = new TextView(context);
        counter.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        counter.setVisibility(View.GONE);
        column.addView(counter, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        previewView = new TextView(context);
        previewView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        previewView.setBackground(Theme.createRoundRectDrawable(dp(8), Theme.getColor(Theme.key_graySection)));
        previewView.setPadding(dp(10), dp(8), dp(10), dp(8));
        previewView.setVisibility(View.GONE);
        previewView.setOnClickListener(v -> {
            removedLink = true;
            foundPage = null;
            previewView.setVisibility(View.GONE);
        });
        column.addView(previewView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        attachments = new TextView(context);
        attachments.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        attachments.setTextSize(14);
        column.addView(attachments, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));
        recordWave = new FeedRecordWave(context);
        recordWave.setVisibility(View.GONE);
        column.addView(recordWave, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 112));

        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        addTool(tools, LocaleController.getString("Moments_Tool_Photo", R.string.Moments_Tool_Photo), () -> pickMedia(false));
        addTool(tools, LocaleController.getString("Moments_Tool_Video", R.string.Moments_Tool_Video), () -> pickMedia(true));
        addTool(tools, LocaleController.getString("Moments_Voice", R.string.Moments_Voice), this::toggleVoice);
        addTool(tools, LocaleController.getString("Moments_Tool_Poll", R.string.Moments_Tool_Poll), this::openPoll);
        addTool(tools, LocaleController.getString("Moments_Tool_Place", R.string.Moments_Tool_Place), () -> presentFragment(new FeedCityActivity(false)));
        column.addView(tools, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView nearby = new TextView(context);
        nearby.setText(LocaleController.getString("Moments_AllowNearby", R.string.Moments_AllowNearby));
        nearby.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        nearby.setOnClickListener(v -> {
            if (privacy == null || privacy.type != StoryPrivacyBottomSheet.TYPE_EVERYONE) {
                return;
            }
            allowNearby = !allowNearby;
            nearby.setText(LocaleController.getString("Moments_AllowNearby", R.string.Moments_AllowNearby) + (allowNearby ? " ✓" : ""));
            dirty = true;
        });
        column.addView(nearby, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView originalView = new TextView(context);
        originalView.setText(LocaleController.getString("Moments_SendOriginal", R.string.Moments_SendOriginal));
        originalView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        originalView.setOnClickListener(v -> {
            original = !original;
            originalView.setText(LocaleController.getString("Moments_SendOriginal", R.string.Moments_SendOriginal) + (original ? " ✓" : ""));
        });
        column.addView(originalView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        FrameLayout root = new FrameLayout(context);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        if (quoteId != null) {
            field.setHint(LocaleController.getString("Moments_QuoteHint", R.string.Moments_QuoteHint));
        }
        refreshPublish();
        return fragmentView;
    }

    private void addTool(LinearLayout tools, String title, Runnable click) {
        TextView view = new TextView(getContext());
        view.setText(title);
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        view.setPadding(dp(8), dp(10), dp(8), dp(10));
        view.setOnClickListener(v -> click.run());
        tools.addView(view);
    }

    private void refreshPublish() {
        boolean hasBody = field != null && (field.getText().length() > 0 || !paths.isEmpty() || poll != null);
        boolean hasAudience = privacy != null && privacy.type != 0;
        if (publish != null) {
            publish.setAlpha(hasBody && hasAudience ? 1f : 0.4f);
        }
        if (attachments != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < types.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(types.get(i));
            }
            if (poll != null) {
                builder.append(builder.length() == 0 ? "" : ", ").append(LocaleController.getString("Moments_Tool_Poll", R.string.Moments_Tool_Poll));
            }
            attachments.setText(builder.toString());
        }
        if (previewView != null && (has("image") || has("video") || poll != null)) {
            previewView.setVisibility(View.GONE);
        }
    }

    private boolean has(String type) {
        return types.contains(type);
    }

    private void pickMedia(boolean video) {
        if (poll != null) {
            return;
        }
        if (video && has("image")) {
            return;
        }
        if (!video && has("video")) {
            return;
        }
        ChatAttachAlert alert = new ChatAttachAlert(getParentActivity(), this, false, false, true, resourceProvider);
        int limit = video ? FeedConfig.MAX_VIDEOS - count("video") : FeedContract.imageSlotsLeft(count("image"));
        if (limit <= 0) {
            return;
        }
        alert.setMaxSelectedPhotos(limit, true);
        alert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                takeSelected(video);
                alert.dismiss();
            }
        });
        alert.show();
    }

    private int count(String type) {
        int n = 0;
        for (int i = 0; i < types.size(); i++) {
            if (type.equals(types.get(i))) {
                n++;
            }
        }
        return n;
    }

    private void takeSelected(boolean wantVideo) {
        ArrayList<Object> order = ChatAttachAlertPhotoLayout.selectedPhotosOrder;
        for (int i = 0; i < order.size(); i++) {
            Object entry = ChatAttachAlertPhotoLayout.selectedPhotos.get(order.get(i));
            if (!(entry instanceof MediaController.PhotoEntry)) {
                continue;
            }
            MediaController.PhotoEntry photo = (MediaController.PhotoEntry) entry;
            if (photo.isVideo != wantVideo) {
                continue;
            }
            if (photo.isVideo && photo.duration > FeedConfig.MAX_VIDEO_SEC && !trimmed(photo)) {
                new AlertDialog.Builder(getParentActivity()).setMessage(LocaleController.getString("Moments_VideoTooLong", R.string.Moments_VideoTooLong)).setPositiveButton(getString(R.string.OK), null).show();
                continue;
            }
            if (photo.path == null) {
                continue;
            }
            if (!photo.isVideo && count("image") >= FeedContract.MAX_IMAGES) {
                continue;
            }
            paths.add(photo.path);
            types.add(photo.isVideo ? "video" : "image");
            dirty = true;
        }
        ChatAttachAlertPhotoLayout.selectedPhotos.clear();
        ChatAttachAlertPhotoLayout.selectedPhotosOrder.clear();
        refreshPublish();
    }

    private boolean trimmed(MediaController.PhotoEntry photo) {
        VideoEditedInfo info = photo.editedInfo;
        if (info == null || info.endTime <= info.startTime) {
            return false;
        }
        long seconds = (info.endTime - info.startTime) / 1000000L;
        return seconds > 0 && seconds <= FeedConfig.MAX_VIDEO_SEC;
    }

    private void toggleVoice() {
        if (voice != null && voice.isRecording()) {
            voice.stop();
            recordWave.setVisibility(View.GONE);
            return;
        }
        if (has("video") || poll != null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getParentActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        FeedPlayback.stop();
        MediaController.getInstance().cleanupPlayer(true, true);
        voice = new FeedVoiceCapture(currentAccount, new FeedVoiceCapture.Callback() {
            @Override
            public void onFile(File file) {
                if (file != null && file.exists()) {
                    paths.add(file.getAbsolutePath());
                    types.add("audio");
                    dirty = true;
                    refreshPublish();
                }
            }

            @Override
            public void onError() {
                attachments.setText("");
                recordWave.setVisibility(View.GONE);
            }

            @Override
            public void onAmplitude(double amplitude) {
                recordWave.setAmplitude(amplitude);
            }
        });
        voice.start();
        recordWave.updateColors();
        recordWave.setVisibility(View.VISIBLE);
        attachments.setText(LocaleController.getString("Moments_Recording", R.string.Moments_Recording));
    }

    private void openPoll() {
        if (!paths.isEmpty()) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong("user_id", getUserConfig().getClientUserId());
        ChatActivity chat = new ChatActivity(args);
        PollCreateActivity pollActivity = new PollCreateActivity(chat, false, Boolean.FALSE);
        pollActivity.setDelegate((media, correctAnswers, notify, scheduleDate) -> {
            try {
                if (!(media instanceof org.telegram.tgnet.TLRPC.TL_messageMediaPoll)) {
                    return;
                }
                org.telegram.tgnet.TLRPC.TL_messageMediaPoll pollMedia = (org.telegram.tgnet.TLRPC.TL_messageMediaPoll) media;
                JSONObject object = new JSONObject();
                object.put("question", pollMedia.poll.question.text);
                object.put("multiple", pollMedia.poll.multiple_choice);
                object.put("anonymous", !pollMedia.poll.public_voters);
                if (pollMedia.poll.close_date != 0) {
                    object.put("closes_at", pollMedia.poll.close_date * 1000L);
                }
                JSONArray options = new JSONArray();
                for (int i = 0; i < pollMedia.poll.answers.size(); i++) {
                    options.put(new JSONObject().put("text", pollMedia.poll.answers.get(i).text.text));
                }
                object.put("options", options);
                poll = object;
                dirty = true;
                refreshPublish();
            } catch (Exception ignore) {
            }
        });
        presentFragment(pollActivity);
    }

    private void publish() {
        if (field.getText().length() == 0 && paths.isEmpty() && poll == null) {
            return;
        }
        if (privacy == null || privacy.type == 0) {
            audienceView.performClick();
            return;
        }
        ArrayList<String> blocked = mentionsWithoutAccess();
        if (!blocked.isEmpty()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("Moments_MentionTitle", R.string.Moments_MentionTitle));
            builder.setMessage(LocaleController.getString("Moments_MentionWarning", R.string.Moments_MentionWarning) + "\n" + TextUtils.join(", ", blocked));
            builder.setPositiveButton(LocaleController.getString("Moments_Publish", R.string.Moments_Publish), (d, w) -> publishNow());
            builder.setNegativeButton(getString(R.string.Cancel), null);
            showDialog(builder.create());
            return;
        }
        publishNow();
    }

    private void publishNow() {
        if (editId != null) {
            JSONObject body = new JSONObject();
            try {
                body.put("text", field.getText().toString());
            } catch (Exception ignore) {
            }
            controller().api.sendJson("PATCH", "/v1/feed/posts/" + editId, body, (json, error) -> finishFragment());
            return;
        }
        FeedController.UploadTask task = new FeedController.UploadTask();
        task.text = field.getText().toString();
        task.audienceKind = FeedAudiencePicker.kind(privacy.type);
        task.allowNearby = allowNearby;
        task.cityCode = cityCode.length() > 0 ? cityCode : controller().cityCode();
        task.cityName = cityName.length() > 0 ? cityName : controller().cityName();
        task.placeName = placeName;
        task.original = original;
        prepareImages();
        task.paths.addAll(paths);
        task.types.addAll(types);
        try {
            JSONObject extra = new JSONObject();
            if (poll != null) {
                extra.put("poll", poll);
            }
            if (foundPage != null && !has("image") && !has("video") && poll == null) {
                JSONObject link = new JSONObject();
                link.put("url", foundPage.url);
                link.put("title", foundPage.title);
                link.put("description", foundPage.description);
                extra.put("link_preview", link);
            }
            task.extraJson = extra.toString();
        } catch (Exception ignore) {
        }
        task.nonce = Utilities.random.nextLong() + "-" + System.currentTimeMillis();
        if (poll != null) {
            task.text = task.text;
        }
        controller().enqueue(task);
        if (draftId != null) {
            controller().storage.deleteDraft(draftId);
        }
        dirty = false;
        finishFragment();
    }

    private void tryLeave() {
        if (!dirty) {
            finishFragment();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString("Moments_Draft_Title", R.string.Moments_Draft_Title));
        builder.setMessage(LocaleController.getString("Moments_Draft_Message", R.string.Moments_Draft_Message));
        builder.setPositiveButton(LocaleController.getString("Moments_Draft_Save", R.string.Moments_Draft_Save), (d, w) -> {
            saveDraft();
            finishFragment();
        });
        builder.setNegativeButton(LocaleController.getString("Moments_Draft_Discard", R.string.Moments_Draft_Discard), (d, w) -> {
            dirty = false;
            finishFragment();
        });
        builder.setNeutralButton(LocaleController.getString("Moments_Draft_Continue", R.string.Moments_Draft_Continue), null);
        showDialog(builder.create());
    }

    private void saveDraft() {
        try {
            JSONObject o = new JSONObject();
            o.put("text", field.getText().toString());
            o.put("paths", new JSONArray(paths));
            o.put("types", new JSONArray(types));
            if (privacy != null) {
                o.put("audience", FeedAudiencePicker.kind(privacy.type));
            }
            String id = draftId != null ? draftId : String.valueOf(System.currentTimeMillis());
            controller().storage.saveDraft(id, System.currentTimeMillis(), o.toString());
        } catch (Exception ignore) {
        }
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (invoked) {
            tryLeave();
            return false;
        }
        return false;
    }

    public void setCity(String code, String name) {
        cityCode = code == null ? "" : code;
        cityName = name == null ? "" : name;
        dirty = true;
    }

    private void schedulePreview() {
        if (previewView == null) {
            return;
        }
        if (removedLink || has("image") || has("video") || poll != null) {
            previewView.setVisibility(View.GONE);
            return;
        }
        Matcher matcher = URL_PATTERN.matcher(field.getText());
        if (!matcher.find()) {
            foundPage = null;
            previewView.setVisibility(View.GONE);
            return;
        }
        final String url = matcher.group();
        final int request = ++previewRequest;
        TL_account.getWebPagePreview req = new TL_account.getWebPagePreview();
        req.message = url;
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (request != previewRequest || removedLink) {
                return;
            }
            TLRPC.WebPage page = null;
            if (response instanceof TL_account.webPagePreview) {
                TLRPC.MessageMedia media = ((TL_account.webPagePreview) response).media;
                if (media != null && media.webpage instanceof TLRPC.TL_webPage) {
                    page = media.webpage;
                }
            }
            foundPage = page;
            if (page == null) {
                previewView.setVisibility(View.GONE);
                return;
            }
            String title = page.title != null && page.title.length() > 0 ? page.title : page.site_name;
            previewView.setText((title == null ? url : title) + "\n" + LocaleController.getString("Moments_Remove", R.string.Moments_Remove));
            previewView.setVisibility(View.VISIBLE);
        }));
    }

    private ArrayList<String> mentionsWithoutAccess() {
        ArrayList<String> names = new ArrayList<>();
        if (privacy == null) {
            return names;
        }
        if (privacy.type == StoryPrivacyBottomSheet.TYPE_EVERYONE) {
            Matcher everyone = MENTION_PATTERN.matcher(field.getText());
            while (everyone.find() && names.size() < FeedConfig.MAX_MENTIONS) {
                String username = everyone.group(1);
                TLRPC.User user = findUser(username);
                if (user != null && privacy.selectedUserIds.contains(user.id)) {
                    names.add("@" + username);
                }
            }
            return names;
        }
        Matcher matcher = MENTION_PATTERN.matcher(field.getText());
        while (matcher.find() && names.size() < FeedConfig.MAX_MENTIONS) {
            String username = matcher.group(1);
            TLRPC.User user = findUser(username);
            if (user != null && canSee(user)) {
                continue;
            }
            names.add("@" + username);
        }
        return names;
    }

    private TLRPC.User findUser(String username) {
        ArrayList<TLRPC.TL_contact> contacts = getContactsController().contacts;
        for (int i = 0; i < contacts.size(); i++) {
            TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(contacts.get(i).user_id);
            if (user != null && username.equalsIgnoreCase(user.username)) {
                return user;
            }
        }
        return null;
    }

    private boolean canSee(TLRPC.User user) {
        if (privacy.type == StoryPrivacyBottomSheet.TYPE_CONTACTS) {
            return getContactsController().contactsDict.get(user.id) != null && !privacy.selectedUserIds.contains(user.id);
        }
        if (privacy.type == StoryPrivacyBottomSheet.TYPE_CLOSE_FRIENDS) {
            return user.close_friend;
        }
        if (privacy.type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS) {
            return privacy.selectedUserIds.contains(user.id);
        }
        return false;
    }

    private void prepareImages() {
        for (int i = 0; i < paths.size(); i++) {
            if (!"image".equals(types.get(i))) {
                continue;
            }
            File source = new File(paths.get(i));
            File prepared = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "feed-photo-" + System.currentTimeMillis() + "-" + i + ".jpg");
            try {
                if (!original) {
                    android.graphics.Bitmap bitmap = ImageLoader.loadBitmap(source.getAbsolutePath(), null, 1280, 1280, false);
                    if (bitmap != null) {
                        FileOutputStream out = new FileOutputStream(prepared);
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out);
                        out.close();
                        bitmap.recycle();
                        paths.set(i, prepared.getAbsolutePath());
                        continue;
                    }
                }
                copyFile(source, prepared);
                stripGps(prepared);
                paths.set(i, prepared.getAbsolutePath());
            } catch (Exception ignore) {
                stripGps(source);
            }
        }
    }

    private static void copyFile(File from, File to) throws Exception {
        FileInputStream in = new FileInputStream(from);
        FileOutputStream out = new FileOutputStream(to);
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        in.close();
        out.close();
    }

    private static void stripGps(File file) {
        try {
            androidx.exifinterface.media.ExifInterface exif = new androidx.exifinterface.media.ExifInterface(file.getAbsolutePath());
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LATITUDE_REF, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_LONGITUDE_REF, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_ALTITUDE_REF, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_TIMESTAMP, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_DATESTAMP, null);
            exif.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_GPS_PROCESSING_METHOD, null);
            exif.saveAttributes();
        } catch (Exception ignore) {
        }
    }
}
