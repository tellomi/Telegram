package org.telegram.ui.Feed;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
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
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.VideoEditedInfo;
import org.telegram.messenger.feed.FeedConfig;
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
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.io.File;
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
    private TextView publish;
    private StoryPrivacyBottomSheet.StoryPrivacy privacy;
    private boolean allowNearby;
    private String cityCode = "";
    private String cityName = "";
    private String placeName = "";
    private boolean original;
    private boolean removedLink;
    private final ArrayList<String> paths = new ArrayList<>();
    private final ArrayList<String> types = new ArrayList<>();
    private JSONObject poll;
    private MediaRecorder recorder;
    private File voiceFile;
    private boolean dirty;

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
        actionBar.setTitle(getString(R.string.FeedComposerTitle));
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
        publish.setText(getString(R.string.FeedPublish));
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
        audienceView.setText(getString(R.string.FeedAudiencePleaseChoose));
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
        field.setHint(getString(R.string.FeedComposerHint));
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

        attachments = new TextView(context);
        attachments.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        attachments.setTextSize(14);
        column.addView(attachments, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 8));

        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        addTool(tools, getString(R.string.FeedToolPhoto), () -> pickMedia(false));
        addTool(tools, getString(R.string.FeedToolVideo), () -> pickMedia(true));
        addTool(tools, getString(R.string.FeedToolVoice), this::toggleVoice);
        addTool(tools, getString(R.string.FeedToolPoll), this::openPoll);
        addTool(tools, getString(R.string.FeedToolPlace), () -> presentFragment(new FeedCityActivity(false)));
        column.addView(tools, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView nearby = new TextView(context);
        nearby.setText(getString(R.string.FeedAllowNearby));
        nearby.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        nearby.setOnClickListener(v -> {
            if (privacy == null || privacy.type != StoryPrivacyBottomSheet.TYPE_EVERYONE) {
                return;
            }
            allowNearby = !allowNearby;
            nearby.setText(getString(R.string.FeedAllowNearby) + (allowNearby ? " ✓" : ""));
            dirty = true;
        });
        column.addView(nearby, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));

        TextView originalView = new TextView(context);
        originalView.setText(getString(R.string.FeedSendOriginal));
        originalView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        originalView.setOnClickListener(v -> {
            original = !original;
            originalView.setText(getString(R.string.FeedSendOriginal) + (original ? " ✓" : ""));
        });
        column.addView(originalView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 8, 0, 0));

        FrameLayout root = new FrameLayout(context);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        fragmentView = root;
        if (quoteId != null) {
            field.setHint(getString(R.string.FeedQuoteHint));
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
                builder.append(builder.length() == 0 ? "" : ", ").append(getString(R.string.FeedToolPoll));
            }
            attachments.setText(builder.toString());
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
        int limit = video ? FeedConfig.MAX_VIDEOS - count("video") : FeedConfig.MAX_IMAGES - count("image");
        alert.setMaxSelectedPhotos(Math.max(1, limit), true);
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
                new AlertDialog.Builder(getParentActivity()).setMessage(getString(R.string.FeedVideoTooLong)).setPositiveButton(getString(R.string.OK), null).show();
                continue;
            }
            if (photo.path == null) {
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
        if (recorder != null) {
            stopVoice();
            return;
        }
        if (has("video") || poll != null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(getParentActivity(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            getParentActivity().requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
            return;
        }
        try {
            File dir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
            voiceFile = new File(dir, "feed-voice-" + System.currentTimeMillis() + ".m4a");
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setMaxDuration(FeedConfig.MAX_VOICE_SEC * 1000);
            recorder.setOutputFile(voiceFile.getAbsolutePath());
            recorder.setOnInfoListener((r, what, extra) -> {
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    AndroidUtilities.runOnUIThread(this::stopVoice);
                }
            });
            recorder.prepare();
            recorder.start();
            attachments.setText(getString(R.string.FeedRecording));
        } catch (Exception e) {
            recorder = null;
        }
    }

    private void stopVoice() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
            }
        } catch (Exception ignore) {
        }
        recorder = null;
        if (voiceFile != null && voiceFile.exists()) {
            paths.add(voiceFile.getAbsolutePath());
            types.add("audio");
            dirty = true;
        }
        refreshPublish();
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
        task.paths.addAll(paths);
        task.types.addAll(types);
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
        builder.setTitle(getString(R.string.FeedDraftTitle));
        builder.setMessage(getString(R.string.FeedDraftMessage));
        builder.setPositiveButton(getString(R.string.FeedDraftSave), (d, w) -> {
            saveDraft();
            finishFragment();
        });
        builder.setNegativeButton(getString(R.string.FeedDraftDiscard), (d, w) -> {
            dirty = false;
            finishFragment();
        });
        builder.setNeutralButton(getString(R.string.FeedDraftKeep), null);
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
}
