package org.telegram.ui.tellomi.feed;

import android.content.Intent;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.ShareAlert;
import org.telegram.ui.TellomiPack;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Forward menu for a public post (D-14). A post that is not {@code shareable}
 * has no items. The engine drops a repost when the original is deleted.
 */
public final class TellomiDiscoverShare {

    public enum Action {
        REPOST,
        QUOTE,
        CHAT,
        EXTERNAL
    }

    public static final List<Action> ACTIONS = Collections.unmodifiableList(Arrays.asList(
        Action.REPOST, Action.QUOTE, Action.CHAT, Action.EXTERNAL
    ));

    private TellomiDiscoverShare() {
    }

    public static List<Action> actions(boolean shareable) {
        return shareable ? ACTIONS : Collections.<Action>emptyList();
    }

    /** {@code https://tell.cc/m#<id>}. A bad id does not become a link. */
    public static String publicUrl(String host, String postId) {
        String id = TellomiDiscover.postId(postId);
        if (id == null || host == null || host.isEmpty() || host.indexOf('/') >= 0 || host.indexOf(' ') >= 0) {
            return null;
        }
        return "https://" + host + "/m#" + id;
    }

    public static void show(BaseFragment fragment, String postId, boolean shareable, Runnable repost, Runnable quote) {
        if (fragment == null || fragment.getParentActivity() == null || actions(shareable).isEmpty()) {
            return;
        }
        final String url = publicUrl(BuildVars.LINK_HOST, postId);
        if (url == null) {
            return;
        }
        String language = language();
        String[] labels = new String[] {
            phrase(language, "Tellomi.Discover.Repost"),
            phrase(language, "Tellomi.Discover.Quote"),
            phrase(language, "Tellomi.Discover.ShareChat"),
            phrase(language, "Tellomi.Discover.ShareExternal")
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(phrase(language, "Tellomi.Discover.Forward"));
        builder.setItems(labels, (dialog, which) -> {
            if (which == 0) {
                if (repost != null) {
                    repost.run();
                }
            } else if (which == 1) {
                if (quote != null) {
                    quote.run();
                }
            } else if (which == 2) {
                fragment.showDialog(new ShareAlert(fragment.getParentActivity(), null, url, false, url, false));
            } else if (which == 3) {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/plain");
                intent.putExtra(Intent.EXTRA_TEXT, url);
                fragment.getParentActivity().startActivity(Intent.createChooser(intent, labels[3]));
            }
        });
        fragment.showDialog(builder.create());
    }

    static String phrase(String language, String key) {
        return TellomiPack.text(key, language, TellomiPack.device());
    }

    private static String language() {
        try {
            LocaleController.LocaleInfo info = LocaleController.getInstance().getCurrentLocaleInfo();
            if (info != null && info.shortName != null && !info.shortName.isEmpty()) {
                return info.shortName;
            }
        } catch (Throwable ignored) {
        }
        return "en";
    }
}
