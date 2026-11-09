package org.telegram.ui.Feed;

import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.MediaController;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;
import org.telegram.ui.Components.EmojiView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedConfig;
import org.telegram.messenger.feed.FeedController;
import org.telegram.messenger.feed.FeedModels;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ShareAlert;

import java.io.File;
import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

public class FeedDetailActivity extends BaseFragment implements FeedPostCell.Delegate {

    public boolean focusReply;
    private final String postId;
    private FeedModels.Post post;
    private final ArrayList<FeedModels.Comment> comments = new ArrayList<>();
    private String sort = "hot";
    private RecyclerListView listView;
    private EditText reply;
    private TextView hint;
    private String replyTo;
    private TextView attachmentLabel;
    private String attachmentPath;
    private String attachmentKind;
    private JSONObject readyAttachment;
    private FeedVoiceCapture commentVoice;
    private FeedRecordWave recordWave;
    private EmojiView emojiView;

    public FeedDetailActivity(Bundle args) {
        super(args);
        postId = args.getString("post_id");
    }

    public static FeedDetailActivity forPost(String id) {
        Bundle args = new Bundle();
        args.putString("post_id", id);
        return new FeedDetailActivity(args);
    }

    private FeedController controller() {
        return FeedController.getInstance(currentAccount);
    }

    @Override
    public View createView(android.content.Context context) {
        actionBar.setTitle(getString(R.string.FeedDetailTitle));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });
        post = controller().find(postId);
        if (post == null) {
            post = FeedModels.Post.unavailable(postId);
        }
        FrameLayout root = new FrameLayout(context);
        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setAdapter(new Adapter());
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 0, 0, 120));

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.VERTICAL);
        bar.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        hint = new TextView(context);
        hint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        hint.setTextSize(12);
        hint.setPadding(dp(12), dp(4), dp(12), 0);
        bar.addView(hint);
        attachmentLabel = new TextView(context);
        attachmentLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        attachmentLabel.setPadding(dp(12), dp(4), dp(12), 0);
        attachmentLabel.setVisibility(View.GONE);
        attachmentLabel.setOnClickListener(v -> clearAttachment());
        bar.addView(attachmentLabel);
        recordWave = new FeedRecordWave(context);
        recordWave.setVisibility(View.GONE);
        bar.addView(recordWave, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 112));
        LinearLayout tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        addReplyTool(tools, getString(R.string.FeedToolPhoto), this::pickCommentPhoto);
        addReplyTool(tools, getString(R.string.FeedToolVoice), this::toggleCommentVoice);
        addReplyTool(tools, getString(R.string.FeedToolSticker), this::toggleStickers);
        bar.addView(tools);
        emojiView = new EmojiView(this, false, true, true, context, false, null, null, true, resourceProvider, false);
        emojiView.setVisibility(View.GONE);
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override
            public void onStickerSelected(View view, TLRPC.Document sticker, String query, Object parent, org.telegram.messenger.MessageObject.SendAnimationData sendAnimationData, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                setDocumentAttachment(sticker, "sticker");
            }

            @Override
            public void onGifSelected(View view, Object gif, String query, Object parent, boolean notify, int scheduleDate, int scheduleRepeatPeriod) {
                if (gif instanceof TLRPC.Document) {
                    setDocumentAttachment((TLRPC.Document) gif, "gif");
                }
            }
        });
        bar.addView(emojiView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 240));
        LinearLayout row = new LinearLayout(context);
        reply = new EditText(context);
        reply.setHint(getString(R.string.FeedReplyHint));
        reply.setBackground(null);
        reply.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        reply.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        row.addView(reply, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
        TextView send = new TextView(context);
        send.setText(getString(R.string.Send));
        send.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        send.setTypeface(AndroidUtilities.bold());
        send.setPadding(dp(12), dp(12), dp(12), dp(12));
        send.setOnClickListener(v -> sendComment());
        row.addView(send);
        bar.addView(row);
        root.addView(bar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM));
        fragmentView = root;
        load();
        if (focusReply) {
            AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(reply), 200);
        }
        return fragmentView;
    }

    private void load() {
        controller().api.getJson("/v1/feed/posts/" + postId, (json, error) -> {
            if (json == null || error != null) {
                post = FeedModels.Post.unavailable(postId);
            } else if (json.has("post")) {
                post = FeedModels.Post.parse(json.optJSONObject("post"));
            } else if (json.has("id")) {
                post = FeedModels.Post.parse(json);
            }
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
            applyPolicy();
        });
        controller().loadComments(postId, sort, "", (list, next) -> {
            comments.clear();
            if (list != null) {
                comments.addAll(list);
            }
            if (listView.getAdapter() != null) {
                listView.getAdapter().notifyDataSetChanged();
            }
        });
    }

    private void applyPolicy() {
        if (post == null) {
            return;
        }
        String policy = post.commentPolicy == null ? "" : post.commentPolicy;
        if ("closed".equals(policy)) {
            hint.setText(getString(R.string.FeedCommentsClosed));
            reply.setEnabled(false);
        } else if ("mutual_followers".equals(policy)) {
            hint.setText(getString(R.string.FeedCommentsMutual));
        } else if ("author_following".equals(policy)) {
            hint.setText(getString(R.string.FeedCommentsFollowing));
        } else if ("mentioned_only".equals(policy)) {
            hint.setText(getString(R.string.FeedCommentsMentioned));
        } else {
            hint.setText("");
            reply.setEnabled(true);
        }
    }

    private void addReplyTool(LinearLayout tools, String title, Runnable click) {
        TextView view = new TextView(getContext());
        view.setText(title);
        view.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        view.setPadding(dp(12), dp(8), dp(12), dp(8));
        view.setOnClickListener(v -> click.run());
        tools.addView(view);
    }

    private void clearAttachment() {
        attachmentPath = null;
        attachmentKind = null;
        readyAttachment = null;
        attachmentLabel.setVisibility(View.GONE);
    }

    private void setLocalAttachment(String path, String kind) {
        readyAttachment = null;
        attachmentPath = path;
        attachmentKind = kind;
        attachmentLabel.setText(kind + " · " + getString(R.string.FeedRemovePreview));
        attachmentLabel.setVisibility(View.VISIBLE);
        if (emojiView != null) {
            emojiView.setVisibility(View.GONE);
        }
    }

    private void setDocumentAttachment(TLRPC.Document document, String kind) {
        if (document == null) {
            return;
        }
        attachmentPath = null;
        attachmentKind = kind;
        readyAttachment = new JSONObject();
        try {
            readyAttachment.put("type", kind);
            readyAttachment.put("document_id", document.id);
            readyAttachment.put("access_hash", document.access_hash);
            if (document.file_reference != null) {
                readyAttachment.put("file_reference", android.util.Base64.encodeToString(document.file_reference, android.util.Base64.NO_WRAP));
            }
        } catch (Exception ignore) {
        }
        attachmentLabel.setText(kind + " · " + getString(R.string.FeedRemovePreview));
        attachmentLabel.setVisibility(View.VISIBLE);
        emojiView.setVisibility(View.GONE);
    }

    private void pickCommentPhoto() {
        if (getParentActivity() == null) {
            return;
        }
        ChatAttachAlert alert = new ChatAttachAlert(getParentActivity(), this, false, false, true, resourceProvider);
        alert.setMaxSelectedPhotos(1, false);
        alert.setDelegate(new ChatAttachAlert.ChatAttachViewDelegate() {
            @Override
            public void didPressedButton(int button, boolean arg, boolean notify, int scheduleDate, int scheduleRepeatPeriod, long effectId, boolean invertMedia, boolean forceDocument, long payStars) {
                ArrayList<Object> order = ChatAttachAlertPhotoLayout.selectedPhotosOrder;
                if (!order.isEmpty()) {
                    Object entry = ChatAttachAlertPhotoLayout.selectedPhotos.get(order.get(0));
                    if (entry instanceof MediaController.PhotoEntry && !((MediaController.PhotoEntry) entry).isVideo && ((MediaController.PhotoEntry) entry).path != null) {
                        setLocalAttachment(((MediaController.PhotoEntry) entry).path, "image");
                    }
                }
                ChatAttachAlertPhotoLayout.selectedPhotos.clear();
                ChatAttachAlertPhotoLayout.selectedPhotosOrder.clear();
                alert.dismiss();
            }
        });
        alert.show();
    }

    private void toggleCommentVoice() {
        if (commentVoice != null && commentVoice.isRecording()) {
            commentVoice.stop();
            recordWave.setVisibility(View.GONE);
            return;
        }
        FeedPlayback.stop();
        MediaController.getInstance().cleanupPlayer(true, true);
        commentVoice = new FeedVoiceCapture(currentAccount, new FeedVoiceCapture.Callback() {
            @Override
            public void onFile(File file) {
                if (file != null && file.exists()) {
                    setLocalAttachment(file.getAbsolutePath(), "audio");
                }
            }

            @Override
            public void onError() {
                recordWave.setVisibility(View.GONE);
            }

            @Override
            public void onAmplitude(double amplitude) {
                recordWave.setAmplitude(amplitude);
            }
        });
        commentVoice.start();
        recordWave.updateColors();
        recordWave.setVisibility(View.VISIBLE);
        attachmentLabel.setText(getString(R.string.FeedRecording));
        attachmentLabel.setVisibility(View.VISIBLE);
    }

    private void toggleStickers() {
        if (emojiView == null) {
            return;
        }
        emojiView.setVisibility(emojiView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void sendComment() {
        String text = reply.getText().toString().trim();
        if ((text.length() == 0 && attachmentPath == null && readyAttachment == null) || text.length() > FeedConfig.MAX_COMMENT) {
            return;
        }
        if (attachmentPath != null) {
            final String path = attachmentPath;
            final String kind = attachmentKind;
            controller().api.uploadFile(new File(path), new File(path).getName(), "audio".equals(kind) ? "audio/ogg" : "image/jpeg", (key, error) -> {
                if (key == null) {
                    return;
                }
                JSONObject attachment = new JSONObject();
                try {
                    attachment.put("type", kind);
                    attachment.put("key", key);
                } catch (Exception ignore) {
                }
                postComment(text, attachment);
            });
            return;
        }
        postComment(text, readyAttachment);
    }

    private void postComment(String text, JSONObject attachment) {
        controller().sendComment(postId, text, replyTo, attachment, error -> {
            reply.setText("");
            replyTo = null;
            clearAttachment();
            load();
        });
    }

    @Override
    public void onOpen(FeedModels.Post post) {
    }

    @Override
    public void onLike(FeedModels.Post post) {
        controller().toggleLike(post);
        if (listView.getAdapter() != null) {
            listView.getAdapter().notifyItemChanged(0);
        }
    }

    @Override
    public void onComment(FeedModels.Post post) {
        AndroidUtilities.showKeyboard(reply);
    }

    @Override
    public void onRepost(FeedModels.Post post) {
    }

    @Override
    public void onShare(FeedModels.Post post) {
        showDialog(ShareAlert.createShareAlert(getContext(), null, FeedUi.linkFor(post.id), false, FeedUi.linkFor(post.id), false));
    }

    @Override
    public void onSave(FeedModels.Post post) {
        controller().toggleSave(post);
    }

    @Override
    public void onMore(FeedModels.Post post, View anchor) {
    }

    @Override
    public void onFollow(FeedModels.Post post) {
        controller().follow(post.author.profileId, !post.following);
    }

    @Override
    public void onAuthor(FeedModels.Post post) {
        FeedUi.openProfile(this, post.author);
    }

    @Override
    public void onVote(FeedModels.Post post, int option) {
        if (post.poll == null || post.poll.closed()) {
            return;
        }
        JSONArray options = new JSONArray();
        options.put(post.poll.options.get(option).id);
        controller().vote(post.id, options, false, error -> load());
    }

    @Override
    public void onMedia(FeedModels.Post post, int index) {
        FeedMediaViewer.open(this, post, index);
    }

    private void commentMenu(FeedModels.Comment comment, View anchor) {
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        options.add(R.drawable.input_reply, getString(R.string.Reply), () -> {
            replyTo = comment.id;
            reply.setHint(getString(R.string.Reply) + " " + FeedUi.displayName(comment.author, currentAccount));
        });
        options.add(R.drawable.msg_copy, getString(R.string.Copy), () -> AndroidUtilities.addToClipboard(comment.text));
        options.add(R.drawable.msg_forward, getString(R.string.Forward), () -> showDialog(ShareAlert.createShareAlert(getContext(), null, comment.text + "\n" + FeedUi.linkFor(postId), false, FeedUi.linkFor(postId), false)));
        options.add(R.drawable.msg_report, getString(R.string.ReportChat), () -> FeedReportSheet.open(this, "comment", comment.id));
        if (FeedUi.isSelf(currentAccount, comment.author) || (post != null && FeedUi.isSelf(currentAccount, post.author))) {
            options.add(R.drawable.msg_delete, getString(R.string.Delete), () -> confirmDelete(comment));
        }
        if (FeedConfig.COMMENT_LIKES) {
            options.add(R.drawable.media_like, getString(R.string.FeedLike), () -> controller().api.sendJson(comment.liked ? "DELETE" : "PUT", "/v1/feed/comments/" + comment.id + "/like", null, (json, error) -> load()));
        }
        options.show();
    }

    private void confirmDelete(FeedModels.Comment comment) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setMessage(comment.replyCount > 0 ? getString(R.string.FeedDeleteThread) : getString(R.string.Delete));
        builder.setPositiveButton(getString(R.string.Delete), (d, w) -> controller().deleteComment(postId, comment.id, error -> load()));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private class Adapter extends RecyclerListView.SelectionAdapter {
        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return false;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == 0) {
                FeedPostCell cell = new FeedPostCell(parent.getContext(), currentAccount, true, false, FeedDetailActivity.this);
                cell.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(cell);
            }
            if (viewType == 1) {
                TextView sortView = new TextView(parent.getContext());
                sortView.setPadding(dp(16), dp(10), dp(16), dp(10));
                sortView.setTextColor(Theme.getColor(Theme.key_featuredStickers_addButton));
                sortView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                return new RecyclerListView.Holder(sortView);
            }
            TextView row = new TextView(parent.getContext());
            row.setPadding(dp(16), dp(10), dp(16), dp(10));
            row.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            row.setTextSize(org.telegram.messenger.SharedConfig.fontSize);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(row);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                ((FeedPostCell) holder.itemView).setPost(post);
                return;
            }
            if (position == 1) {
                TextView sortView = (TextView) holder.itemView;
                sortView.setText(("hot".equals(sort) ? getString(R.string.FeedSortHot) : getString(R.string.FeedSortNew)) + " · " + comments.size());
                sortView.setOnClickListener(v -> {
                    sort = "hot".equals(sort) ? "latest" : "hot";
                    load();
                });
                return;
            }
            FeedModels.Comment comment = flat().get(position - 2);
            TextView row = (TextView) holder.itemView;
            String prefix = comment.parentId != null && comment.parentId.length() > 0 ? getString(R.string.Reply) + " " + comment.replyToName + "\n" : "";
            row.setText(FeedUi.displayName(comment.author, currentAccount) + "  " + FeedUi.timeAgo(comment.createdAt) + "\n" + prefix + comment.text);
            row.setOnLongClickListener(v -> {
                commentMenu(comment, row);
                return true;
            });
        }

        private ArrayList<FeedModels.Comment> flat() {
            ArrayList<FeedModels.Comment> all = new ArrayList<>();
            for (int i = 0; i < comments.size(); i++) {
                all.add(comments.get(i));
                int shown = Math.min(FeedConfig.COMMENT_PREVIEW_REPLIES, comments.get(i).replies.size());
                for (int r = 0; r < shown; r++) {
                    all.add(comments.get(i).replies.get(r));
                }
            }
            return all;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return 0;
            }
            if (position == 1) {
                return 1;
            }
            return 2;
        }

        @Override
        public int getItemCount() {
            return 2 + flat().size();
        }
    }
}
