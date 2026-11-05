package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.TellomiDiscoverFlags;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Composer. Visibility is the Stories privacy sheet, not a second model. */
public class TellomiDiscoverComposeFragment extends BaseFragment {

    private static final int MENU_PUBLISH = 1;
    private static final int REQUEST_IMAGES = 42;
    private static final int REQUEST_VIDEO = 41;

    interface Publisher {
        void publish(String text, TellomiDiscover.Visibility visibility, long[] userIds, String nonce, String quoteOf, java.util.List<String> pollOptions, TellomiDiscoverLink.Preview linkPreview, java.util.List<String> mediaKeys, TellomiDiscover.Completion completion);

        void published();
    }

    private final int textLimit;
    private final Publisher publisher;
    private final String quoteOf;
    private final String nonce = UUID.randomUUID().toString();
    private final java.util.ArrayList<EditText> pollFields = new java.util.ArrayList<>();
    private boolean pollOn;
    private StoryPrivacyBottomSheet.StoryPrivacy privacy = new StoryPrivacyBottomSheet.StoryPrivacy();
    private EditText textView;
    private LinearLayout suggestBox;
    private TellomiDiscoverMention.Query activeQuery;
    private TextView visibilityButton;
    private TextView hintView;
    private TextView videoButton;
    private TextView videoRemove;
    private TextView videoHint;
    private TextView videoStatus;
    private Uri videoUri;
    private long videoDurationMs;
    private boolean videoBusy;
    private int videoGeneration;
    private TellomiDiscoverVideo.Decision videoDecision = TellomiDiscoverVideo.Decision.NONE;
    private TellomiDiscoverLinkCard linkCard;
    private final Handler linkHandler = new Handler(Looper.getMainLooper());
    private Runnable linkTask;
    private int linkGeneration;
    private String linkUrl;
    private TellomiDiscoverLink.Preview linkPreview;
    private boolean linkSettled;
    private boolean holding;
    private final java.util.ArrayList<TellomiDiscoverMedia.Bytes> photos = new java.util.ArrayList<>();
    private LinearLayout photoList;
    private TextView photoButton;
    private TextView photoStatus;
    private TellomiDiscover.ClientError photoError;
    private boolean photoBusy;
    private int photoGeneration;
    private boolean sending;
    private boolean editing;
    private TellomiDiscover.ClientError sendError;

    private TellomiDiscoverComposeFragment(Bundle args, int textLimit, Publisher publisher, String quoteOf) {
        super(args);
        this.textLimit = textLimit > 0 ? textLimit : TellomiDiscover.POST_TEXT_LIMIT;
        this.publisher = publisher;
        this.quoteOf = quoteOf;
    }

    static TellomiDiscoverComposeFragment create(int textLimit, Publisher publisher) {
        return create(textLimit, publisher, null);
    }

    static TellomiDiscoverComposeFragment create(int textLimit, Publisher publisher, String quoteOf) {
        Bundle args = new Bundle();
        args.putInt("limit", textLimit);
        return new TellomiDiscoverComposeFragment(args, textLimit, publisher, quoteOf);
    }

    @Override
    public void onFragmentDestroy() {
        linkGeneration++;
        holding = false;
        if (linkTask != null) {
            linkHandler.removeCallbacks(linkTask);
        }
        super.onFragmentDestroy();
    }

    @Override
    public View createView(Context context) {
        TellomiDiscover.Copy copy = copy();
        actionBar.setTitle(copy.compose);
        actionBar.setBackButtonImage(org.telegram.messenger.R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_PUBLISH) {
                    send();
                }
            }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite, resourceProvider));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16), dp(12), dp(16), dp(12));
        textView = new EditText(context) {
            @Override
            protected void onSelectionChanged(int selStart, int selEnd) {
                super.onSelectionChanged(selStart, selEnd);
                if (!editing && suggestBox != null) {
                    showSuggestions();
                }
            }
        };
        textView.setBackground(null);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textView.setMinHeight(dp(160));
        textView.setGravity(Gravity.TOP);
        textView.setHint(phrase(copy, "Tellomi.Discover.MentionHint"));
        textView.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        textView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshLink();
                if (!editing) {
                    refreshCompose();
                }
            }
        });
        column.addView(textView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));
        linkCard = TellomiDiscoverLinkCard.create(context, resourceProvider);
        linkCard.hide();
        column.addView(linkCard);
        TextView mention = new TextView(context);
        mention.setText(phrase(copy, "Tellomi.Discover.Mention"));
        mention.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        mention.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        mention.setPadding(0, dp(8), 0, dp(4));
        mention.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                insertAt();
            }
        });
        column.addView(mention, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        suggestBox = new LinearLayout(context);
        suggestBox.setOrientation(LinearLayout.VERTICAL);
        suggestBox.setVisibility(View.GONE);
        column.addView(suggestBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        if (quoteOf != null) {
            TextView quote = new TextView(context);
            quote.setText(phrase(copy, "Tellomi.Discover.Quote"));
            quote.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            quote.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            column.addView(quote, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        } else {
            TextView pollToggle = new TextView(context);
            pollToggle.setText(phrase(copy, "Tellomi.Discover.Poll"));
            pollToggle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            pollToggle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
            pollToggle.setPadding(0, dp(8), 0, dp(4));
            LinearLayout pollBox = new LinearLayout(context);
            pollBox.setOrientation(LinearLayout.VERTICAL);
            pollBox.setVisibility(View.GONE);
            pollToggle.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pollOn = !pollOn;
                    pollBox.setVisibility(pollOn ? View.VISIBLE : View.GONE);
                    if (pollOn && pollFields.isEmpty()) {
                        addPollField(context, pollBox);
                        addPollField(context, pollBox);
                    }
                    apply();
                }
            });
            column.addView(pollToggle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            TextView addOption = new TextView(context);
            addOption.setText(phrase(copy, "Tellomi.Discover.PollAdd"));
            addOption.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
            addOption.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
            addOption.setPadding(0, dp(8), 0, dp(4));
            addOption.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (pollFields.size() < TellomiDiscover.POLL_OPTION_MAX) {
                        addPollField(context, pollBox);
                        apply();
                    }
                }
            });
            pollBox.addView(addOption, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            column.addView(pollBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
        LinearLayout videoRow = new LinearLayout(context);
        videoRow.setOrientation(LinearLayout.HORIZONTAL);
        videoRow.setGravity(Gravity.CENTER_VERTICAL);
        videoButton = new TextView(context);
        videoButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        videoButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        videoButton.setPadding(0, dp(12), dp(16), dp(4));
        videoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickVideo();
            }
        });
        videoRemove = new TextView(context);
        videoRemove.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        videoRemove.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
        videoRemove.setPadding(0, dp(12), 0, dp(4));
        videoRemove.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearVideo();
            }
        });
        videoRow.addView(videoButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        videoRow.addView(videoRemove, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        column.addView(videoRow, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        videoHint = new TextView(context);
        videoHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        videoHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        column.addView(videoHint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        videoStatus = new TextView(context);
        videoStatus.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        videoStatus.setPadding(0, dp(4), 0, 0);
        column.addView(videoStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        photoButton = new TextView(context);
        photoButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        photoButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        photoButton.setPadding(0, dp(12), 0, dp(4));
        photoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickPhotos();
            }
        });
        column.addView(photoButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView photoHint = new TextView(context);
        photoHint.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        photoHint.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        photoHint.setText(phrase(copy, "Tellomi.Discover.PhotoHint"));
        column.addView(photoHint, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        photoList = new LinearLayout(context);
        photoList.setOrientation(LinearLayout.VERTICAL);
        column.addView(photoList, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        photoStatus = new TextView(context);
        photoStatus.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        photoStatus.setPadding(0, dp(4), 0, 0);
        column.addView(photoStatus, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        visibilityButton = new TextView(context);
        visibilityButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        visibilityButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider));
        visibilityButton.setPadding(0, dp(12), 0, dp(4));
        visibilityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                presentPrivacy();
            }
        });
        column.addView(visibilityButton, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        hintView = new TextView(context);
        hintView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        hintView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        column.addView(hintView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;
        refreshCompose();
        return fragmentView;
    }

    private void presentPrivacy() {
        if (getParentActivity() == null) {
            return;
        }
        StoryPrivacyBottomSheet sheet = new StoryPrivacyBottomSheet(getParentActivity(), 86400, resourceProvider);
        sheet.setValue(privacy);
        sheet.isEdit(false);
        sheet.whenSelectedRules(new StoryPrivacyBottomSheet.DoneCallback() {
            @Override
            public void done(StoryPrivacyBottomSheet.StoryPrivacy selected, boolean allowComments, boolean allowScreenshots, boolean keepInProfile, boolean isRtmpStream, org.telegram.tgnet.TLRPC.InputPeer peer, int pricePerComment, Runnable loaded, Runnable cancelled) {
                privacy = selected;
                apply();
                if (loaded != null) {
                    loaded.run();
                }
            }
        }, true);
        sheet.show();
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGES) {
            takePhotos(resultCode, data);
            return;
        }
        if (requestCode != REQUEST_VIDEO) {
            return;
        }
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        final int generation = ++videoGeneration;
        final Context context = getParentActivity();
        videoBusy = true;
        videoDecision = TellomiDiscoverVideo.Decision.NONE;
        apply();
        if (context == null) {
            videoBusy = false;
            videoUri = null;
            videoDurationMs = 0L;
            videoDecision = TellomiDiscoverVideo.Decision.UNKNOWN;
            apply();
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final long duration = TellomiDiscoverVideoDuration.millis(context, uri);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fragmentView == null || generation != videoGeneration) {
                            return;
                        }
                        videoBusy = false;
                        TellomiDiscoverVideo.Decision decision = TellomiDiscoverVideo.select(duration);
                        if (decision == TellomiDiscoverVideo.Decision.ACCEPT) {
                            videoUri = uri;
                            videoDurationMs = duration;
                            videoDecision = decision;
                        } else {
                            videoUri = null;
                            videoDurationMs = 0L;
                            videoDecision = decision;
                        }
                        apply();
                    }
                });
            }
        });
    }

    private void pickPhotos() {
        if (getParentActivity() == null || photoBusy || sending) {
            return;
        }
        if (photos.size() >= TellomiDiscoverMedia.MAX_IMAGES) {
            photoError = TellomiDiscover.ClientError.MEDIA_LIMIT;
            apply();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        try {
            startActivityForResult(intent, REQUEST_IMAGES);
        } catch (Throwable ignored) {
            photoError = TellomiDiscover.ClientError.MEDIA_TYPE;
            apply();
        }
    }

    private void takePhotos(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        final java.util.ArrayList<Uri> uris = new java.util.ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        if (uris.isEmpty()) {
            return;
        }
        final int generation = ++photoGeneration;
        final android.content.ContentResolver resolver = getParentActivity() == null ? null : getParentActivity().getContentResolver();
        photoBusy = true;
        photoError = null;
        apply();
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final java.util.ArrayList<TellomiDiscoverMedia.Bytes> accepted = new java.util.ArrayList<>();
                TellomiDiscover.ClientError problem = null;
                int room = TellomiDiscoverMedia.MAX_IMAGES - photos.size();
                if (uris.size() > room) {
                    problem = TellomiDiscover.ClientError.MEDIA_LIMIT;
                }
                int take = Math.min(room, uris.size());
                for (int i = 0; i < take; i++) {
                    TellomiDiscoverMediaFiles.Opened opened = TellomiDiscoverMediaFiles.read(resolver, uris.get(i));
                    if (opened.error != null) {
                        if (problem == null) {
                            problem = opened.error;
                        }
                        continue;
                    }
                    accepted.add(new TellomiDiscoverMedia.Bytes(opened.name, opened.bytes));
                }
                final TellomiDiscover.ClientError shown = problem;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fragmentView == null || generation != photoGeneration) {
                            return;
                        }
                        photoBusy = false;
                        photos.addAll(accepted);
                        if (shown != null) {
                            photoError = shown;
                        }
                        apply();
                    }
                });
            }
        });
    }

    private void removePhoto(int index) {
        if (photoBusy || sending || index < 0 || index >= photos.size()) {
            return;
        }
        photos.remove(index);
        photoError = null;
        apply();
    }

    private void pickVideo() {
        if (getParentActivity() == null || videoBusy || sending) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        try {
            startActivityForResult(intent, REQUEST_VIDEO);
        } catch (Throwable ignored) {
            videoUri = null;
            videoDurationMs = 0L;
            videoDecision = TellomiDiscoverVideo.Decision.UNKNOWN;
            apply();
        }
    }

    private void clearVideo() {
        videoGeneration++;
        videoUri = null;
        videoDurationMs = 0L;
        videoBusy = false;
        videoDecision = TellomiDiscoverVideo.Decision.NONE;
        apply();
    }

    /** Re-reads the length, then refuses the file. A text post is not sent instead. */
    private void blockAttachedVideo() {
        if (videoUri == null || videoBusy || sending) {
            return;
        }
        final Uri uri = videoUri;
        final long stored = videoDurationMs;
        final int generation = ++videoGeneration;
        final Context context = getParentActivity();
        videoBusy = true;
        apply();
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                long fresh = context == null ? 0L : TellomiDiscoverVideoDuration.millis(context, uri);
                final long duration = fresh > 0L ? fresh : stored;
                final TellomiDiscoverVideo.Decision decision = TellomiDiscoverVideo.beforeUpload(duration);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (fragmentView == null || generation != videoGeneration) {
                            return;
                        }
                        videoBusy = false;
                        if (decision == TellomiDiscoverVideo.Decision.TOO_LONG || decision == TellomiDiscoverVideo.Decision.UNKNOWN) {
                            videoUri = null;
                            videoDurationMs = 0L;
                            videoDecision = decision;
                        } else {
                            if (fresh > 0L) {
                                videoDurationMs = fresh;
                            }
                            videoDecision = TellomiDiscoverVideo.Decision.NOT_UPLOADED;
                        }
                        apply();
                    }
                });
            }
        });
    }

    private void send() {
        TellomiDiscover.AudienceChoice choice = choice();
        final String text = textView.getText() == null ? "" : textView.getText().toString();
        if (sending || holding || videoBusy || photoBusy || choice == null || !choice.publishable || !TellomiDiscoverMention.invalid(text).isEmpty()) {
            return;
        }
        if (videoUri != null) {
            blockAttachedVideo();
            return;
        }
        final String url = TellomiDiscoverLink.firstUrl(text);
        if (url != null && TellomiDiscoverLink.fetchable(url) && (!url.equals(linkUrl) || !linkSettled)) {
            TellomiDiscoverLink.Preview cached = TellomiDiscoverLinkFetch.peek(url);
            if (cached != null) {
                linkUrl = url;
                linkPreview = cached;
                linkSettled = true;
            } else {
                holding = true;
                apply();
                requestLink(url, true);
                return;
            }
        }
        holding = false;
        sending = true;
        sendError = null;
        apply();
        TellomiDiscoverLink.Preview preview = url != null && url.equals(linkUrl) && linkSettled ? linkPreview : null;
        if (!photos.isEmpty()) {
            uploadThenPublish(text, choice, preview);
            return;
        }
        publisher.publish(text, choice.visibility, choice.userIds, nonce, quoteOf, pollOn ? pollValues() : null, preview, null, new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                sending = false;
                if (outcome.ok()) {
                    publisher.published();
                    finishFragment();
                    return;
                }
                sendError = outcome.error;
                apply();
            }
        });
    }

    private void uploadThenPublish(final String body, final TellomiDiscover.AudienceChoice choice, final TellomiDiscoverLink.Preview preview) {
        TellomiDiscover.Configuration configuration = TellomiDiscoverFlags.configuration(currentAccount);
        final int generation = photoGeneration;
        java.util.ArrayList<TellomiDiscoverMedia.Bytes> files = new java.util.ArrayList<>(photos);
        TellomiDiscoverClient.uploadImages(currentAccount, configuration == null ? null : configuration.baseUrl, files, new TellomiDiscoverClient.UploadCallback() {
            @Override
            public void onResult(java.util.List<String> keys, TellomiDiscover.ClientError error) {
                if (fragmentView == null || generation != photoGeneration) {
                    return;
                }
                if (error != null || keys == null) {
                    sending = false;
                    sendError = error == null ? TellomiDiscover.ClientError.MEDIA_UPLOAD : error;
                    apply();
                    return;
                }
                publisher.publish(body, choice.visibility, choice.userIds, nonce, quoteOf, pollOn ? pollValues() : null, preview, keys, new TellomiDiscover.Completion() {
                    @Override
                    public void onResult(TellomiDiscover.Outcome outcome) {
                        sending = false;
                        if (outcome.ok()) {
                            publisher.published();
                            finishFragment();
                            return;
                        }
                        sendError = outcome.error;
                        apply();
                    }
                });
            }
        });
    }

    private void apply() {
        if (actionBar == null || textView == null) {
            return;
        }
        TellomiDiscover.Copy copy = copy();
        TellomiDiscover.AudienceChoice choice = choice();
        String title = choice == null ? copy.everyone : copy.visibilityTitle(choice.visibility);
        visibilityButton.setText(copy.visibility + " · " + title);
        String errorText = sendError == null ? null : copy.failureText(sendError);
        String reason = choice == null ? null : copy.reasonText(choice.reason);
        if (videoButton != null) {
            videoButton.setText(phrase(copy, videoUri == null ? "Tellomi.Discover.VideoAdd" : "Tellomi.Discover.VideoReplace"));
            videoButton.setEnabled(!videoBusy && !sending);
            videoRemove.setText(phrase(copy, "Tellomi.Discover.VideoRemove"));
            videoRemove.setVisibility(videoUri == null ? View.GONE : View.VISIBLE);
            videoRemove.setEnabled(!videoBusy && !sending);
            videoHint.setText(phrase(copy, "Tellomi.Discover.VideoHint"));
            String status = videoStatusText(copy);
            if (status == null || status.isEmpty()) {
                videoStatus.setVisibility(View.GONE);
            } else {
                videoStatus.setVisibility(View.VISIBLE);
                videoStatus.setText(status);
                boolean error = videoDecision == TellomiDiscoverVideo.Decision.TOO_LONG || videoDecision == TellomiDiscoverVideo.Decision.UNKNOWN;
                videoStatus.setTextColor(Theme.getColor(error ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            }
        }
        if (photoButton != null && photoList != null && photoStatus != null) {
            photoButton.setText(phrase(copy, "Tellomi.Discover.PhotoAdd"));
            photoButton.setEnabled(!photoBusy && !sending && photos.size() < TellomiDiscoverMedia.MAX_IMAGES);
            photoList.removeAllViews();
            for (int i = 0; i < photos.size(); i++) {
                final int index = i;
                LinearLayout line = new LinearLayout(photoList.getContext());
                line.setOrientation(LinearLayout.HORIZONTAL);
                line.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = new TextView(photoList.getContext());
                String label = photos.get(i).name;
                name.setText(label == null || label.isEmpty() ? phrase(copy, "Tellomi.Discover.PhotoAdd") : label);
                name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
                line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                TextView remove = new TextView(photoList.getContext());
                remove.setText(phrase(copy, "Tellomi.Discover.PhotoRemove"));
                remove.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15);
                remove.setTextColor(Theme.getColor(Theme.key_text_RedRegular, resourceProvider));
                remove.setPadding(dp(8), dp(6), 0, dp(6));
                remove.setEnabled(!photoBusy && !sending);
                remove.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removePhoto(index);
                    }
                });
                line.addView(remove);
                photoList.addView(line, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
            }
            String photoText = null;
            if (photoBusy || sending && !photos.isEmpty()) {
                photoText = phrase(copy, "Tellomi.Discover.PhotoUploading");
            } else if (photoError != null) {
                photoText = copy.failureText(photoError);
            } else if (!photos.isEmpty()) {
                try {
                    photoText = String.format(Locale.US, phrase(copy, "Tellomi.Discover.PhotoCount"), photos.size());
                } catch (RuntimeException ignored) {
                    photoText = Integer.toString(photos.size());
                }
            }
            if (photoText == null || photoText.isEmpty()) {
                photoStatus.setVisibility(View.GONE);
            } else {
                photoStatus.setVisibility(View.VISIBLE);
                photoStatus.setText(photoText);
                boolean photoFailure = photoError != null;
                photoStatus.setTextColor(Theme.getColor(photoFailure ? Theme.key_text_RedRegular : Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            }
        }
        String text = textView.getText() == null ? "" : textView.getText().toString();
        java.util.List<String> invalid = TellomiDiscoverMention.invalid(text);
        String mentionText = invalid.isEmpty() ? null : mentionLine(copy, invalid.get(0));
        if (errorText != null) {
            hintView.setText(errorText);
        } else if (reason != null) {
            hintView.setText(reason);
        } else {
            hintView.setText(mentionText == null ? "" : mentionText);
        }
        String trimmed = TellomiDiscover.trim(text);
        boolean pollOk = !pollOn || TellomiDiscover.cleanPoll(pollValues()) != null;
        boolean withinLimit = text.codePointCount(0, text.length()) <= textLimit;
        boolean canSend = !sending && !holding && !videoBusy && !photoBusy && choice != null && choice.publishable && pollOk && invalid.isEmpty() && withinLimit && (!trimmed.isEmpty() || !photos.isEmpty()) && TellomiDiscover.clientNonce(nonce);
        actionBar.createMenu().clearItems();
        actionBar.createMenu().addItem(MENU_PUBLISH, copy.publish).setEnabled(canSend);
    }

    private TellomiDiscover.AudienceChoice choice() {
        return fromStory(privacy, UserConfig.getInstance(currentAccount).getClientUserId());
    }

    static TellomiDiscover.AudienceChoice fromStory(StoryPrivacyBottomSheet.StoryPrivacy privacy, long selfUserId) {
        if (privacy == null) {
            return TellomiDiscover.audience(TellomiDiscover.StoryBase.EVERYONE, new long[0], selfUserId);
        }
        ArrayList<Long> raw = new ArrayList<>();
        if (privacy.selectedUserIds != null) {
            raw.addAll(privacy.selectedUserIds);
        }
        if (privacy.selectedUserIdsByGroup != null) {
            for (ArrayList<Long> members : privacy.selectedUserIdsByGroup.values()) {
                if (members != null) {
                    raw.addAll(members);
                }
            }
        }
        boolean dropped = false;
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (int i = 0; i < raw.size(); i++) {
            Long id = raw.get(i);
            if (id == null || id <= 0) {
                dropped = true;
                continue;
            }
            unique.add(id);
        }
        long[] ids = new long[unique.size()];
        int index = 0;
        for (Long id : unique) {
            ids[index++] = id;
        }
        int type = privacy.type;
        if (dropped && ids.length == 0 && !raw.isEmpty()) {
            TellomiDiscover.Visibility visibility = type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS
                ? TellomiDiscover.Visibility.INCLUDE
                : TellomiDiscover.Visibility.EXCLUDE;
            return new TellomiDiscover.AudienceChoice(visibility, false, "audience_invalid", new long[0]);
        }
        TellomiDiscover.StoryBase base;
        if (type == StoryPrivacyBottomSheet.TYPE_CLOSE_FRIENDS) {
            base = TellomiDiscover.StoryBase.CLOSE_FRIENDS;
        } else if (type == StoryPrivacyBottomSheet.TYPE_CONTACTS) {
            base = TellomiDiscover.StoryBase.CONTACTS;
        } else if (type == StoryPrivacyBottomSheet.TYPE_SELECTED_CONTACTS) {
            base = TellomiDiscover.StoryBase.NOBODY;
        } else {
            base = TellomiDiscover.StoryBase.EVERYONE;
        }
        return TellomiDiscover.audience(base, ids, selfUserId);
    }

    private void refreshLink() {
        if (linkCard == null || textView == null) {
            return;
        }
        String url = TellomiDiscoverLink.firstUrl(textView.getText() == null ? "" : textView.getText().toString());
        if (url == null || !TellomiDiscoverLink.fetchable(url)) {
            linkGeneration++;
            linkUrl = null;
            linkPreview = null;
            linkSettled = false;
            holding = false;
            if (linkTask != null) {
                linkHandler.removeCallbacks(linkTask);
            }
            linkCard.hide();
            return;
        }
        if (url.equals(linkUrl)) {
            if (linkSettled && linkPreview != null) {
                linkCard.show(linkPreview, false);
            }
            return;
        }
        TellomiDiscoverLink.Preview cached = TellomiDiscoverLinkFetch.peek(url);
        if (cached != null) {
            linkUrl = url;
            linkPreview = cached;
            linkSettled = true;
            linkCard.show(cached, false);
            return;
        }
        linkUrl = url;
        linkPreview = null;
        linkSettled = false;
        linkCard.showLoading();
        requestLink(url, false);
    }

    private void requestLink(final String url, final boolean immediate) {
        final int generation = ++linkGeneration;
        if (linkTask != null) {
            linkHandler.removeCallbacks(linkTask);
        }
        linkTask = new Runnable() {
            @Override
            public void run() {
                TellomiDiscoverLinkFetch.load(url, new TellomiDiscoverLinkFetch.Callback() {
                    @Override
                    public void onResult(TellomiDiscoverLink.Preview preview) {
                        if (generation != linkGeneration || linkCard == null || !url.equals(linkUrl)) {
                            return;
                        }
                        linkSettled = true;
                        linkPreview = preview;
                        if (preview == null) {
                            linkCard.hide();
                        } else {
                            linkCard.show(preview, false);
                        }
                        if (holding) {
                            holding = false;
                            send();
                        }
                    }
                });
            }
        };
        linkHandler.postDelayed(linkTask, immediate ? 0 : 450);
    }

    private void addPollField(Context context, LinearLayout pollBox) {
        EditText field = new EditText(context);
        field.setSingleLine(true);
        field.setBackground(null);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                apply();
            }
        });
        pollFields.add(field);
        pollBox.addView(field, pollBox.getChildCount() - 1, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));
    }

    private java.util.List<String> pollValues() {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (int i = 0; i < pollFields.size(); i++) {
            CharSequence text = pollFields.get(i).getText();
            values.add(text == null ? "" : text.toString());
        }
        return values;
    }

    private String videoStatusText(TellomiDiscover.Copy copy) {
        if (videoBusy) {
            return phrase(copy, "Tellomi.Discover.VideoReading");
        }
        if (videoDecision == TellomiDiscoverVideo.Decision.TOO_LONG) {
            return phrase(copy, "Tellomi.Discover.VideoTooLong");
        }
        if (videoDecision == TellomiDiscoverVideo.Decision.UNKNOWN) {
            return phrase(copy, "Tellomi.Discover.VideoUnknown");
        }
        if (videoUri != null) {
            String selected;
            try {
                selected = String.format(Locale.US, phrase(copy, "Tellomi.Discover.VideoSelected"), TellomiDiscoverVideo.clock(videoDurationMs));
            } catch (RuntimeException ignored) {
                selected = TellomiDiscoverVideo.clock(videoDurationMs);
            }
            return selected + "\n" + phrase(copy, "Tellomi.Discover.VideoNotUploaded");
        }
        return null;
    }

    private void refreshCompose() {
        if (textView == null) {
            return;
        }
        paintMentions();
        showSuggestions();
        apply();
    }

    private void paintMentions() {
        if (!(textView.getText() instanceof Spannable)) {
            return;
        }
        Spannable text = (Spannable) textView.getText();
        MentionMark[] marks = text.getSpans(0, text.length(), MentionMark.class);
        for (int i = 0; i < marks.length; i++) {
            text.removeSpan(marks[i]);
        }
        int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4, resourceProvider);
        List<TellomiDiscoverMention.Mention> mentions = TellomiDiscoverMention.parse(text.toString());
        for (int i = 0; i < mentions.size(); i++) {
            TellomiDiscoverMention.Mention mention = mentions.get(i);
            if (mention.start >= 0 && mention.end <= text.length() && mention.start < mention.end) {
                text.setSpan(new MentionMark(color), mention.start, mention.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    private void showSuggestions() {
        if (suggestBox == null || textView == null) {
            return;
        }
        String text = textView.getText() == null ? "" : textView.getText().toString();
        int caret = textView.getSelectionEnd();
        TellomiDiscoverMention.Query query = TellomiDiscoverMention.queryAt(text, caret < 0 ? text.length() : caret);
        if (sameQuery(activeQuery, query) && suggestBox.getChildCount() > 0) {
            return;
        }
        activeQuery = query;
        suggestBox.removeAllViews();
        if (query == null) {
            suggestBox.setVisibility(View.GONE);
            return;
        }
        List<TellomiDiscoverMention.Person> people = TellomiDiscoverMention.suggestions(knownPeople(), query.prefix, TellomiDiscoverMention.SUGGESTION_LIMIT);
        Context context = suggestBox.getContext();
        TellomiDiscover.Copy copy = copy();
        if (people.isEmpty()) {
            suggestBox.addView(suggestionLabel(context, phrase(copy, "Tellomi.Discover.MentionEmpty"), false));
        } else {
            for (int i = 0; i < people.size(); i++) {
                final TellomiDiscoverMention.Person person = people.get(i);
                String label = person.name.isEmpty() ? "@" + person.username : "@" + person.username + "  " + person.name;
                TextView row = suggestionLabel(context, label, true);
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        applyInsertion(person.username);
                    }
                });
                suggestBox.addView(row);
            }
        }
        suggestBox.setVisibility(View.VISIBLE);
    }

    private static boolean sameQuery(TellomiDiscoverMention.Query left, TellomiDiscoverMention.Query right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.at == right.at && left.end == right.end && left.prefix.equals(right.prefix);
    }

    private TextView suggestionLabel(Context context, String text, boolean tappable) {
        TextView row = new TextView(context);
        row.setText(text);
        row.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        row.setTextColor(Theme.getColor(tappable ? Theme.key_windowBackgroundWhiteBlackText : Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
        row.setPadding(0, dp(8), 0, dp(8));
        row.setFocusable(false);
        return row;
    }

    private void applyInsertion(String username) {
        if (textView == null || activeQuery == null) {
            return;
        }
        String text = textView.getText() == null ? "" : textView.getText().toString();
        TellomiDiscoverMention.Insertion insertion = TellomiDiscoverMention.insert(text, activeQuery, username);
        if (insertion == null) {
            return;
        }
        editing = true;
        textView.setText(insertion.text);
        int length = textView.getText() == null ? 0 : textView.getText().length();
        textView.setSelection(Math.min(Math.max(insertion.caret, 0), length));
        editing = false;
        refreshCompose();
    }

    private void insertAt() {
        if (textView == null || textView.getText() == null) {
            return;
        }
        Editable editable = textView.getText();
        int start = textView.getSelectionStart();
        int end = textView.getSelectionEnd();
        if (start < 0) {
            start = editable.length();
        }
        if (end < 0) {
            end = start;
        }
        if (end < start) {
            int swap = start;
            start = end;
            end = swap;
        }
        if (start == end && TellomiDiscoverMention.queryAt(editable.toString(), end) != null) {
            textView.requestFocus();
            showSuggestions();
            return;
        }
        editing = true;
        editable.replace(start, end, "@");
        textView.setSelection(Math.min(start + 1, editable.length()));
        editing = false;
        refreshCompose();
    }

    private List<TellomiDiscoverMention.Person> knownPeople() {
        ArrayList<TellomiDiscoverMention.Person> people = new ArrayList<>();
        try {
            long self = UserConfig.getInstance(currentAccount).getClientUserId();
            Map<Long, TLRPC.User> users = MessagesController.getInstance(currentAccount).getUsers();
            if (users == null) {
                return people;
            }
            for (TLRPC.User user : users.values()) {
                try {
                    if (user == null || user.id == self || UserObject.isDeleted(user)) {
                        continue;
                    }
                    String username = UserObject.getPublicUsername(user);
                    if (!TellomiDiscoverMention.validUsername(username)) {
                        continue;
                    }
                    people.add(new TellomiDiscoverMention.Person(username, UserObject.getUserName(user)));
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return people;
    }

    private String mentionLine(TellomiDiscover.Copy copy, String username) {
        String template = phrase(copy, "Tellomi.Discover.MentionInvalid");
        String formatted;
        try {
            formatted = String.format(Locale.US, template, username);
        } catch (RuntimeException ignored) {
            formatted = template;
        }
        return formatted.startsWith("@") ? formatted : "@" + formatted;
    }

    private static final class MentionMark extends ForegroundColorSpan {
        MentionMark(int color) {
            super(color);
        }
    }

    private String phrase(TellomiDiscover.Copy copy, String key) {
        return org.telegram.ui.TellomiPack.text(key, copy.languageCode, copy.source);
    }

    private TellomiDiscover.Copy copy() {
        String language = "en";
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null) {
                language = info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return TellomiDiscover.Copy.resolve(language, org.telegram.ui.TellomiPack.device());
    }
}
