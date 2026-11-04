package org.telegram.ui.tellomi.feed;

import android.content.Context;
import android.widget.ImageView;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.ReportBottomSheet;
import org.telegram.ui.TellomiPack;

import java.util.Collections;
import java.util.List;

/**
 * Discover report. The reason list is Telegram's report sheet. The submit
 * goes to {@code POST /v1/feed/reports}, not {@code messages.report}.
 */
public final class TellomiDiscoverReport {

    public static final String NATIVE_PICKER = "org.telegram.ui.ReportBottomSheet";
    public static final String ACTION_REPORT = "report";
    public static final String[] OPTION_IDS = {
        "spam", "violence", "pornography", "child", "drugs", "personal", "other"
    };

    private TellomiDiscoverReport() {
    }

    public static boolean officialOption(String optionId) {
        if (optionId == null) {
            return false;
        }
        for (int i = 0; i < OPTION_IDS.length; i++) {
            if (OPTION_IDS[i].equals(optionId)) {
                return true;
            }
        }
        return false;
    }

    /** Logged-out menus are empty, so the client does not open a picker or send. */
    public static List<String> menuActions(boolean loggedIn) {
        if (!loggedIn) {
            return Collections.emptyList();
        }
        return Collections.singletonList(ACTION_REPORT);
    }

    public static String optionKey(String optionId) {
        if (!officialOption(optionId)) {
            return null;
        }
        if ("spam".equals(optionId)) {
            return "Tellomi.Discover.Report.Spam";
        }
        if ("violence".equals(optionId)) {
            return "Tellomi.Discover.Report.Violence";
        }
        if ("pornography".equals(optionId)) {
            return "Tellomi.Discover.Report.Pornography";
        }
        if ("child".equals(optionId)) {
            return "Tellomi.Discover.Report.Child";
        }
        if ("drugs".equals(optionId)) {
            return "Tellomi.Discover.Report.Drugs";
        }
        if ("personal".equals(optionId)) {
            return "Tellomi.Discover.Report.Personal";
        }
        return "Tellomi.Discover.Report.Other";
    }

    public static void showMenu(BaseFragment fragment, ImageView anchor, String postId, String commentId) {
        if (fragment == null || anchor == null || menuActions(loggedIn(fragment)).isEmpty()) {
            return;
        }
        String label = TellomiPack.text("Tellomi.Discover.Report", language(fragment), TellomiPack.device());
        ItemOptions.makeOptions(fragment, anchor).add(R.drawable.msg_report, label, new Runnable() {
            @Override
            public void run() {
                open(fragment, postId, commentId);
            }
        }).show();
    }

    public static void open(BaseFragment fragment, String postId, String commentId) {
        if (fragment == null || menuActions(loggedIn(fragment)).isEmpty()) {
            return;
        }
        Context context = fragment.getContext();
        if (context == null || TellomiDiscover.postId(postId) == null) {
            return;
        }
        String language = language(fragment);
        TellomiPack.Source source = TellomiPack.device();
        String[] labels = new String[OPTION_IDS.length];
        for (int i = 0; i < OPTION_IDS.length; i++) {
            labels[i] = TellomiPack.text(optionKey(OPTION_IDS[i]), language, source);
        }
        String title = TellomiPack.text("Tellomi.Discover.Report", language, source);
        final String comment = commentId;
        ReportBottomSheet.openLocal(context, fragment.getResourceProvider(), title, OPTION_IDS, labels, new ReportBottomSheet.LocalCallback() {
            @Override
            public void onPicked(String optionId, String detail) {
                submit(fragment, postId, comment, optionId, detail);
            }
        });
    }

    static void submit(BaseFragment fragment, String postId, String commentId, String optionId, String detail) {
        if (fragment == null || !loggedIn(fragment)) {
            return;
        }
        int account = fragment.getCurrentAccount();
        TellomiDiscover.Configuration configuration = org.telegram.messenger.TellomiDiscoverFlags.configuration(account);
        TellomiDiscover.Loader loader = TellomiDiscoverClient.loader(account);
        String language = language(fragment);
        loader.run(true, configuration, TellomiDiscover.Operation.report(postId, commentId, optionId, detail), new TellomiDiscover.Completion() {
            @Override
            public void onResult(TellomiDiscover.Outcome outcome) {
                String key = outcome != null && outcome.ok() ? "Tellomi.Discover.ReportSent" : "Tellomi.Discover.ReportFailed";
                String text = TellomiPack.text(key, language, TellomiPack.device());
                int type = outcome != null && outcome.ok() ? Bulletin.TYPE_SUCCESS : Bulletin.TYPE_ERROR;
                try {
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.showBulletin, type, text);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    private static boolean loggedIn(BaseFragment fragment) {
        try {
            return UserConfig.getInstance(fragment.getCurrentAccount()).isClientActivated();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String language(BaseFragment fragment) {
        try {
            org.telegram.messenger.LocaleController.LocaleInfo info = org.telegram.messenger.LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null && !info.shortName.isEmpty()) {
                return info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return "en";
    }
}
