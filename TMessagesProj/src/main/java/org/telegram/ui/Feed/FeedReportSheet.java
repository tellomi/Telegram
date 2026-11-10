package org.telegram.ui.Feed;

import org.telegram.messenger.LocaleController;
import android.text.InputType;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.feed.FeedController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Same reason strings as the native report dialog. The option tree from
 * messages.report would also file a chat report, so the sheet stops at the
 * reason list and sends the code to the feed API.
 */
public final class FeedReportSheet {

    private FeedReportSheet() {
    }

    public static void open(BaseFragment fragment, String targetType, String targetId) {
        if (fragment.getParentActivity() == null) {
            return;
        }
        String[] keys = new String[]{
                "Moments_Report_Spam",
                "Moments_Report_Violence",
                "Moments_Report_Child",
                "Moments_Report_Drugs",
                "Moments_Report_Personal",
                "Moments_Report_Pornography",
                "Moments_Report_Fake",
                "Moments_Report_Other"
        };
        int[] reasons = new int[]{
                R.string.Moments_Report_Spam,
                R.string.Moments_Report_Violence,
                R.string.Moments_Report_Child,
                R.string.Moments_Report_Drugs,
                R.string.Moments_Report_Personal,
                R.string.Moments_Report_Pornography,
                R.string.Moments_Report_Fake,
                R.string.Moments_Report_Other
        };
        String[] codes = new String[]{"spam", "violence", "child_abuse", "illegal_drugs", "personal_details", "pornography", "fake", "other"};
        CharSequence[] labels = new CharSequence[reasons.length];
        for (int i = 0; i < reasons.length; i++) {
            labels[i] = LocaleController.getString(keys[i], reasons[i]);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString("Moments_Report", R.string.Moments_Report));
        builder.setItems(labels, (dialog, which) -> askDetail(fragment, targetType, targetId, codes[which]));
        fragment.showDialog(builder.create());
    }

    private static void askDetail(BaseFragment fragment, String targetType, String targetId, String reason) {
        EditText field = new EditText(fragment.getParentActivity());
        field.setHint(LocaleController.getString("Moments_Report_Detail", R.string.Moments_Report_Detail));
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity(), fragment.getResourceProvider());
        builder.setTitle(LocaleController.getString("Moments_Report", R.string.Moments_Report));
        builder.setView(field);
        builder.setPositiveButton(LocaleController.getString("Moments_Report", R.string.Moments_Report), (dialog, which) -> {
            String detail = field.getText() == null ? "" : field.getText().toString().trim();
            FeedController.getInstance(fragment.getCurrentAccount()).report(targetType, targetId, reason, detail, error -> {
                if (error != null) {
                    BulletinFactory.of(fragment).createErrorBulletin(error).show();
                } else {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.msg_antispam, LocaleController.getString("Moments_Report_Sent", R.string.Moments_Report_Sent)).show();
                }
            });
        });
        builder.setNegativeButton(LocaleController.getString("Moments_Cancel", R.string.Moments_Cancel), null);
        fragment.showDialog(builder.create());
        AndroidUtilities.runOnUIThread(() -> AndroidUtilities.showKeyboard(field), 200);
    }
}
