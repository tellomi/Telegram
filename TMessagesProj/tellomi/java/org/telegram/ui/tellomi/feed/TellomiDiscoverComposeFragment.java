package org.telegram.ui.tellomi.feed;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Stories.recorder.StoryPrivacyBottomSheet;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Composer. Visibility is the Stories privacy sheet, not a second model. */
public class TellomiDiscoverComposeFragment extends BaseFragment {

    private static final int MENU_PUBLISH = 1;

    interface Publisher {
        void publish(String text, TellomiDiscover.Visibility visibility, long[] userIds, String nonce, String quoteOf, java.util.List<String> pollOptions, TellomiDiscover.Completion completion);

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
    private TextView visibilityButton;
    private TextView hintView;
    private boolean sending;
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
        textView = new EditText(context);
        textView.setBackground(null);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourceProvider));
        textView.setMinHeight(dp(160));
        textView.setGravity(Gravity.TOP);
        textView.addTextChangedListener(new TextWatcher() {
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
        column.addView(textView, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(160)));
        if (quoteOf != null) {
            TextView quote = new TextView(context);
            quote.setText(phrase(copy, "Tellomi.Discover.Quote"));
            quote.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            quote.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourceProvider));
            column.addView(quote, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
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
        root.addView(column, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;
        apply();
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

    private void send() {
        TellomiDiscover.AudienceChoice choice = choice();
        if (sending || choice == null || !choice.publishable) {
            return;
        }
        sending = true;
        sendError = null;
        apply();
        publisher.publish(textView.getText() == null ? "" : textView.getText().toString(), choice.visibility, choice.userIds, nonce, quoteOf, pollOn ? pollValues() : null, new TellomiDiscover.Completion() {
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
        hintView.setText(errorText != null ? errorText : (reason == null ? "" : reason));
        String text = textView.getText() == null ? "" : textView.getText().toString();
        String trimmed = TellomiDiscover.trim(text);
        boolean pollOk = !pollOn || TellomiDiscover.cleanPoll(pollValues()) != null;
        boolean canSend = !sending && choice != null && choice.publishable && pollOk && !trimmed.isEmpty() && text.codePointCount(0, text.length()) <= textLimit && TellomiDiscover.clientNonce(nonce);
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
