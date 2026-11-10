package org.telegram.ui.Feed;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedContract;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Douyin-style comment sheet. Nested replies, likes, and image attachments
 * render when the payload has them. A province line and a dislike icon render
 * only when those fields are present.
 */
public class FeedCommentSheet extends Dialog {

    private final BaseFragment fragment;
    private final String postId;
    private final HashSet<String> expanded = new HashSet<>();
    private final ArrayList<Row> rows = new ArrayList<>();
    private final ArrayList<FeedModels.Comment> roots = new ArrayList<>();
    private String replyTo = "";
    private String replyName = "";
    private String threadCursor = "";
    private String pendingKey = "";
    private String pendingKind = "";
    private int commentTotal;
    private boolean full;
    private boolean loadingMore;
    private final HashSet<String> loadingReplies = new HashSet<>();
    private TextView pending;
    private EmojiView emojiView;
    private TextView title;
    private EditText input;
    private LinearLayout panel;
    private RecyclerListView list;
    private CommentAdapter adapter;

    public static void open(BaseFragment fragment, String postId, boolean focusInput) {
        if (fragment == null || fragment.getParentActivity() == null || postId == null || postId.length() == 0) {
            return;
        }
        FeedCommentSheet sheet = new FeedCommentSheet(fragment, postId);
        fragment.showDialog(sheet);
        if (focusInput) {
            sheet.input.requestFocus();
            AndroidUtilities.showKeyboard(sheet.input);
        }
    }

    private FeedCommentSheet(BaseFragment fragment, String postId) {
        super(fragment.getParentActivity(), R.style.TransparentDialog);
        this.fragment = fragment;
        this.postId = postId;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(getContext());
        root.setOnClickListener(v -> dismiss());
        panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setOnClickListener(v -> { });
        GradientDrawable background = new GradientDrawable();
        background.setColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        background.setCornerRadii(new float[]{dp(16), dp(16), dp(16), dp(16), 0, 0, 0, 0});
        panel.setBackground(background);

        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), dp(8), dp(8), dp(4));
        ImageView expand = icon(R.drawable.msg_zoomin, FeedUi.s("Moments.FullText", R.string.FeedFullText));
        expand.setOnClickListener(v -> {
            full = !full;
            expand.setImageResource(full ? R.drawable.msg_zoomout : R.drawable.msg_zoomin);
            applyHeight();
        });
        title = new TextView(getContext());
        title.setGravity(Gravity.CENTER);
        title.setTextSize(15);
        title.setTypeface(AndroidUtilities.bold());
        title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        title.setText(countTitle(0));
        ImageView close = icon(R.drawable.msg_close, getString(R.string.Close));
        close.setOnClickListener(v -> dismiss());
        header.addView(expand, LayoutHelper.createLinear(40, 40));
        header.addView(title, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        header.addView(close, LayoutHelper.createLinear(40, 40));
        panel.addView(header, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        list = new RecyclerListView(getContext());
        list.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CommentAdapter();
        list.setAdapter(adapter);
        list.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || threadCursor.length() == 0 || loadingMore) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager != null && manager.findLastVisibleItemPosition() >= rows.size() - 3) {
                    loadMore();
                }
            }
        });
        panel.addView(list, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        LinearLayout bar = new LinearLayout(getContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(8), dp(8));
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        input = new EditText(getContext());
        input.setBackground(null);
        input.setTextSize(15);
        input.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        input.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        input.setHint(FeedUi.s("Moments.CommentHint", R.string.FeedCommentHint));
        input.setSingleLine(true);
        pending = new TextView(getContext());
        pending.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        pending.setTextSize(12);
        pending.setVisibility(View.GONE);
        pending.setOnClickListener(v -> clearPending());
        bar.addView(pending, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 0, 0, 8, 0));
        input.setImeOptions(EditorInfo.IME_ACTION_SEND);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                send();
                return true;
            }
            return false;
        });
        bar.addView(input, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        ImageView photo = icon(R.drawable.msg_gallery, FeedUi.s("Moments.Photo", R.string.FeedPhoto));
        photo.setOnClickListener(v -> pickPhoto());
        ImageView mention = icon(R.drawable.msg_mention, "@");
        mention.setOnClickListener(v -> input.getText().insert(input.getSelectionStart() < 0 ? input.length() : input.getSelectionStart(), "@"));
        ImageView emoji = icon(R.drawable.input_smile, FeedUi.s("Moments.Comment", R.string.FeedComment));
        emoji.setOnClickListener(v -> toggleEmoji());
        bar.addView(photo, LayoutHelper.createLinear(36, 36));
        bar.addView(mention, LayoutHelper.createLinear(36, 36));
        bar.addView(emoji, LayoutHelper.createLinear(36, 36));
        panel.addView(bar, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        root.addView(panel, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        setContentView(root);
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(0x66000000));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.BOTTOM);
        }
        applyHeight();
        load();
    }

    private void applyHeight() {
        int height = full ? AndroidUtilities.displaySize.y - AndroidUtilities.statusBarHeight : (int) (AndroidUtilities.displaySize.y * 0.72f);
        panel.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, height / AndroidUtilities.density, Gravity.BOTTOM));
    }

    private void load() {
        threadCursor = "";
        FeedController.getInstance(fragment.getCurrentAccount()).loadCommentThread(postId, "", page -> AndroidUtilities.runOnUIThread(() -> {
            roots.clear();
            if (page != null) {
                roots.addAll(page.comments);
                threadCursor = page.cursor;
                commentTotal = page.total;
            }
            rebuild();
        }));
    }

    private void loadMore() {
        if (loadingMore || threadCursor.length() == 0) {
            return;
        }
        loadingMore = true;
        String cursor = threadCursor;
        FeedController.getInstance(fragment.getCurrentAccount()).loadCommentThread(postId, cursor, page -> AndroidUtilities.runOnUIThread(() -> {
            loadingMore = false;
            if (page == null) {
                return;
            }
            for (int i = 0; i < page.comments.size(); i++) {
                FeedModels.Comment comment = page.comments.get(i);
                if (!contains(comment.id)) {
                    roots.add(comment);
                }
            }
            threadCursor = page.cursor;
            if (page.total > commentTotal) {
                commentTotal = page.total;
            }
            rebuild();
        }));
    }

    private boolean contains(String id) {
        for (int i = 0; i < roots.size(); i++) {
            if (id.equals(roots.get(i).id)) {
                return true;
            }
        }
        return false;
    }

    private void rebuild() {
        rows.clear();
        for (int i = 0; i < roots.size(); i++) {
            FeedModels.Comment comment = roots.get(i);
            rows.add(Row.comment(comment));
            int replies = Math.max(comment.replyCount, comment.replies.size());
            if (replies > 0) {
                boolean open = expanded.contains(comment.id);
                rows.add(Row.expand(comment, open));
                if (open) {
                    for (int r = 0; r < comment.replies.size(); r++) {
                        rows.add(Row.reply(comment.replies.get(r)));
                    }
                }
            }
        }
        title.setText(countTitle(commentTotal > 0 ? commentTotal : roots.size()));
        adapter.notifyDataSetChanged();
    }

    private void fetchReplies(FeedModels.Comment comment) {
        if (comment == null || comment.id.length() == 0 || loadingReplies.contains(comment.id)) {
            return;
        }
        loadingReplies.add(comment.id);
        FeedController.getInstance(fragment.getCurrentAccount()).loadReplies(comment.id, comment.repliesCursor, page -> AndroidUtilities.runOnUIThread(() -> {
            loadingReplies.remove(comment.id);
            if (page != null) {
                for (int i = 0; i < page.comments.size(); i++) {
                    FeedModels.Comment reply = page.comments.get(i);
                    if (!hasReply(comment, reply.id)) {
                        comment.replies.add(reply);
                    }
                }
                comment.repliesCursor = page.cursor;
                if (page.total > comment.replyCount) {
                    comment.replyCount = page.total;
                }
            }
            expanded.add(comment.id);
            rebuild();
        }));
    }

    private boolean hasReply(FeedModels.Comment parent, String id) {
        for (int i = 0; i < parent.replies.size(); i++) {
            if (id.equals(parent.replies.get(i).id)) {
                return true;
            }
        }
        return false;
    }

    private void removeComment(String id) {
        for (int i = 0; i < roots.size(); i++) {
            FeedModels.Comment comment = roots.get(i);
            if (id.equals(comment.id)) {
                roots.remove(i);
                if (commentTotal > 0) {
                    commentTotal--;
                }
                rebuild();
                return;
            }
            for (int r = 0; r < comment.replies.size(); r++) {
                if (id.equals(comment.replies.get(r).id)) {
                    comment.replies.remove(r);
                    if (comment.replyCount > 0) {
                        comment.replyCount--;
                    }
                    rebuild();
                    return;
                }
            }
        }
    }

    private void send() {
        String text = input.getText() == null ? "" : input.getText().toString().trim();
        if (text.length() == 0 && pendingKey.length() == 0) {
            return;
        }
        String parent = replyTo;
        org.json.JSONObject attachment = null;
        if (pendingKey.length() > 0) {
            attachment = new org.json.JSONObject();
            try {
                attachment.put("type", pendingKind.length() == 0 ? "image" : pendingKind);
                attachment.put("key", pendingKey);
            } catch (Exception ignore) {
            }
        }
        input.setText("");
        replyTo = "";
        replyName = "";
        clearPending();
        input.setHint(FeedUi.s("Moments.CommentHint", R.string.FeedCommentHint));
        FeedController.getInstance(fragment.getCurrentAccount()).sendComment(postId, text, parent, attachment, error -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                load();
            }
        }));
    }

    private void clearPending() {
        pendingKey = "";
        pendingKind = "";
        pending.setVisibility(View.GONE);
    }

    private void holdUpload(File file, String mime, String kind) {
        if (file == null || !file.exists()) {
            return;
        }
        FeedController.getInstance(fragment.getCurrentAccount()).api.uploadFile(file, file.getName(), mime, (key, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (key == null || key.length() == 0) {
                return;
            }
            pendingKey = key;
            pendingKind = kind;
            pending.setText(FeedUi.s("sticker".equals(kind) ? "Moments.Tool.Sticker" : "Moments.Photo", "sticker".equals(kind) ? R.string.FeedToolSticker : R.string.FeedPhoto));
            pending.setVisibility(View.VISIBLE);
        }));
    }

    private void pickPhoto() {
        if (fragment.getParentActivity() == null) {
            return;
        }
        ChatAttachAlert alert = new ChatAttachAlert(fragment.getParentActivity(), fragment, false, false, true, fragment.getResourceProvider());
        alert.setMaxSelectedPhotos(1, false);
        alert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                ArrayList<Object> order = ChatAttachAlertPhotoLayout.selectedPhotosOrder;
                if (!order.isEmpty()) {
                    Object entry = ChatAttachAlertPhotoLayout.selectedPhotos.get(order.get(0));
                    if (entry instanceof MediaController.PhotoEntry && !((MediaController.PhotoEntry) entry).isVideo && ((MediaController.PhotoEntry) entry).path != null) {
                        holdUpload(new File(((MediaController.PhotoEntry) entry).path), "image/jpeg", "image");
                    }
                }
                ChatAttachAlertPhotoLayout.selectedPhotos.clear();
                ChatAttachAlertPhotoLayout.selectedPhotosOrder.clear();
                alert.dismiss();
            }
        });
        alert.show();
    }

    private void toggleEmoji() {
        if (emojiView == null) {
            emojiView = new EmojiView(fragment, false, true, false, getContext(), false, null, null, true, fragment.getResourceProvider(), false);
            emojiView.setVisibility(View.GONE);
            emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
                @Override
                public void onStickerSelected(View view, TLRPC.Document sticker, String query, Object parent, org.telegram.messenger.MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                    emojiView.setVisibility(View.GONE);
                    useSticker(sticker);
                }
            });
            panel.addView(emojiView, panel.getChildCount() - 1, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 240));
        }
        emojiView.setVisibility(emojiView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void useSticker(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        int account = fragment.getCurrentAccount();
        File file = FileLoader.getInstance(account).getPathToAttach(document, true);
        if (file != null && file.exists()) {
            holdUpload(file, "image/webp", "sticker");
            return;
        }
        String name = FileLoader.getAttachFileName(document);
        NotificationCenter center = NotificationCenter.getInstance(account);
        NotificationCenter.NotificationCenterDelegate delegate = new NotificationCenter.NotificationCenterDelegate() {
            @Override
            public void didReceivedNotification(int id, int account, Object... args) {
                if (id == NotificationCenter.fileLoaded && args.length > 0 && name.equals(args[0])) {
                    center.removeObserver(this, NotificationCenter.fileLoaded);
                    File loaded = FileLoader.getInstance(account).getPathToAttach(document, true);
                    holdUpload(loaded, "image/webp", "sticker");
                }
            }
        };
        center.addObserver(delegate, NotificationCenter.fileLoaded);
        FileLoader.getInstance(account).loadFile(document, document, FileLoader.PRIORITY_HIGH, 1);
    }

    private String countTitle(int count) {
        return FeedUi.s("Moments.CommentCount", R.string.FeedCommentCount).replace("%d", String.valueOf(count));
    }

    private ImageView icon(int res, String description) {
        ImageView view = new ImageView(getContext());
        view.setImageResource(res);
        view.setColorFilter(Theme.getColor(Theme.key_dialogIcon));
        view.setScaleType(ImageView.ScaleType.CENTER);
        view.setContentDescription(description);
        return view;
    }

    private static int dp(float value) {
        return AndroidUtilities.dp(value);
    }

    private static String getString(int res) {
        return org.telegram.messenger.LocaleController.getString(res);
    }

    private static final class Row {
        static final int COMMENT = 0;
        static final int EXPAND = 1;
        static final int REPLY = 2;
        int kind;
        FeedModels.Comment comment;
        boolean open;

        static Row comment(FeedModels.Comment comment) {
            Row row = new Row();
            row.kind = COMMENT;
            row.comment = comment;
            return row;
        }

        static Row expand(FeedModels.Comment comment, boolean open) {
            Row row = new Row();
            row.kind = EXPAND;
            row.comment = comment;
            row.open = open;
            return row;
        }

        static Row reply(FeedModels.Comment comment) {
            Row row = new Row();
            row.kind = REPLY;
            row.comment = comment;
            return row;
        }
    }

    private class CommentAdapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).kind == Row.EXPAND ? 1 : 0;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            if (viewType == 1) {
                TextView view = new TextView(getContext());
                view.setTextSize(13);
                view.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                view.setPadding(dp(64), dp(4), dp(16), dp(8));
                return new RecyclerListView.Holder(view);
            }
            return new RecyclerListView.Holder(new CommentRow(getContext()));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            if (row.kind == Row.EXPAND) {
                TextView view = (TextView) holder.itemView;
                int count = Math.max(row.comment.replyCount, row.comment.replies.size());
                String label = FeedUi.s("Moments.ShowReplies", R.string.FeedShowReplies).replace("%d", String.valueOf(count));
                view.setText("—— " + label);
                view.setOnClickListener(v -> {
                    boolean open = expanded.contains(row.comment.id);
                    if (open && row.comment.repliesCursor.length() > 0) {
                        fetchReplies(row.comment);
                        return;
                    }
                    if (open) {
                        expanded.remove(row.comment.id);
                        rebuild();
                        return;
                    }
                    expanded.add(row.comment.id);
                    if (row.comment.replies.isEmpty()) {
                        fetchReplies(row.comment);
                    } else {
                        rebuild();
                    }
                });
                return;
            }
            CommentRow view = (CommentRow) holder.itemView;
            view.bind(row.comment, row.kind == Row.REPLY);
        }
    }

    private class CommentRow extends LinearLayout {
        private final BackupImageView avatar;
        private final TextView name;
        private final TextView author;
        private final TextView text;
        private final BackupImageView picture;
        private final TextView meta;
        private final ImageView like;
        private final TextView likeCount;
        private final ImageView dislike;

        CommentRow(android.content.Context context) {
            super(context);
            setOrientation(HORIZONTAL);
            setPadding(dp(16), dp(8), dp(8), dp(4));
            avatar = new BackupImageView(context);
            avatar.setRoundRadius(dp(16));
            addView(avatar, LayoutHelper.createLinear(32, 32, Gravity.TOP, 0, 2, 8, 0));
            LinearLayout column = new LinearLayout(context);
            column.setOrientation(VERTICAL);
            LinearLayout nameRow = new LinearLayout(context);
            nameRow.setGravity(Gravity.CENTER_VERTICAL);
            name = new TextView(context);
            name.setTextSize(13);
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            author = new TextView(context);
            author.setTextSize(11);
            author.setTextColor(0xFFFF2E38);
            author.setText(FeedUi.s("Moments.Author", R.string.FeedAuthor));
            author.setPadding(dp(4), 0, 0, 0);
            nameRow.addView(name, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            nameRow.addView(author, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            text = new TextView(context);
            text.setTextSize(15);
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            picture = new BackupImageView(context);
            picture.setRoundRadius(dp(8));
            meta = new TextView(context);
            meta.setTextSize(12);
            meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            column.addView(nameRow);
            column.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));
            column.addView(picture, LayoutHelper.createLinear(120, 120, 0, 6, 0, 0));
            column.addView(meta, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
            addView(column, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            LinearLayout side = new LinearLayout(context);
            side.setOrientation(VERTICAL);
            side.setGravity(Gravity.CENTER_HORIZONTAL);
            like = new ImageView(context);
            like.setScaleType(ImageView.ScaleType.CENTER);
            likeCount = new TextView(context);
            likeCount.setTextSize(12);
            likeCount.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            dislike = new ImageView(context);
            dislike.setImageResource(R.drawable.msg_cancel);
            dislike.setColorFilter(Theme.getColor(Theme.key_dialogIcon));
            dislike.setScaleType(ImageView.ScaleType.CENTER);
            side.addView(like, LayoutHelper.createLinear(32, 28));
            side.addView(likeCount, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            side.addView(dislike, LayoutHelper.createLinear(32, 28, 0, 4, 0, 0));
            addView(side, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        }

        void bind(FeedModels.Comment comment, boolean reply) {
            setPadding(dp(reply ? 48 : 16), dp(8), dp(8), dp(4));
            FeedUi.bindAvatar(avatar, comment.author, fragment.getCurrentAccount());
            name.setText(FeedUi.displayName(comment.author, fragment.getCurrentAccount()));
            author.setVisibility(comment.isAuthor ? VISIBLE : GONE);
            String body = comment.text == null ? "" : comment.text;
            if (reply && comment.replyToName != null && comment.replyToName.length() > 0) {
                body = FeedUi.s("Moments.ReplyTo", R.string.FeedReplyTo).replace("%s", comment.replyToName) + " " + body;
            }
            text.setText(body);
            text.setVisibility(body.length() == 0 ? GONE : VISIBLE);
            boolean image = comment.attachment != null && (comment.attachment.isImage() || "sticker".equals(comment.attachment.type));
            picture.setVisibility(image ? VISIBLE : GONE);
            if (image) {
                String url = comment.attachment.displayUrl(fragment.getCurrentAccount());
                picture.setImage(url, "200_200", new ColorDrawable(Theme.getColor(Theme.key_graySection)));
            }
            String when = FeedUi.timeAgo(comment.createdAt);
            StringBuilder metaText = new StringBuilder(when);
            if (comment.province != null && comment.province.length() > 0) {
                metaText.append(" · ").append(comment.province);
            }
            metaText.append(" · ").append(FeedUi.s("Moments.Reply", R.string.FeedReply));
            meta.setText(metaText);
            meta.setOnClickListener(v -> {
                replyTo = comment.id;
                replyName = FeedUi.displayName(comment.author, fragment.getCurrentAccount());
                input.setHint(FeedUi.s("Moments.ReplyTo", R.string.FeedReplyTo).replace("%s", replyName));
                input.requestFocus();
                AndroidUtilities.showKeyboard(input);
            });
            like.setImageResource(comment.liked ? R.drawable.media_like_active : R.drawable.media_like);
            like.setColorFilter(comment.liked ? 0xFFFF2E38 : Theme.getColor(Theme.key_dialogIcon));
            likeCount.setText(comment.likes > 0 ? String.valueOf(comment.likes) : "");
            View.OnClickListener toggle = v -> FeedController.getInstance(fragment.getCurrentAccount()).api.sendJson(comment.liked ? "DELETE" : "PUT", FeedContract.commentLikePath(comment.id), null, (json, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    comment.liked = !comment.liked;
                    comment.likes += comment.liked ? 1 : -1;
                    if (comment.likes < 0) {
                        comment.likes = 0;
                    }
                    bind(comment, reply);
                }
            }));
            like.setOnClickListener(toggle);
            likeCount.setOnClickListener(toggle);
            dislike.setOnClickListener(v -> FeedController.getInstance(fragment.getCurrentAccount()).api.sendJson("PUT", FeedContract.commentDislikePath(comment.id), new org.json.JSONObject(), (json, error) -> AndroidUtilities.runOnUIThread(() -> {
                if (error == null) {
                    removeComment(comment.id);
                }
            })));
        }
    }
}
