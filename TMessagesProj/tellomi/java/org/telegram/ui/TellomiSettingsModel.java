package org.telegram.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings groups for M1, shared with the iOS layout of the same name.
 * No Android types: the screen in {@link TellomiSettingsLayout} only draws this.
 */
public final class TellomiSettingsModel {

    public static final String HELP_CONFIG_KEY = "tellomi_help_url";
    public static final String DEFAULT_HELP_URL = "https://tellomi.cn/help";

    public static final int ROW_ACCOUNT = 50;
    public static final int ROW_GENERAL = 51;
    public static final int ROW_SUPPORT = 52;

    /** Telegram Features / Tips. */
    public static final int TIPS_ID = 23;
    /** Apple Watch, if a row is ever given this id. */
    public static final int WATCH_ID = 28;
    public static final int FAQ_ID = 18;

    public static final int KIND_ROW = 1;
    public static final int KIND_HEADER = 2;
    public static final int KIND_SHADOW = 3;
    public static final int KIND_CUSTOM = 4;
    public static final int KIND_OTHER = 5;

    public static final int[] ACCOUNT_IDS = {1, 3, 8};
    public static final int[] GENERAL_IDS = {5, 2, 6, 7, 9, 10, 40, 25, 11, 12, 13, 15, 16};
    public static final int[] SUPPORT_IDS = {18, 17, 19, 27, 41};

    private static final int[] TAIL_IDS = {20, 21, 22, 24, 26};

    private TellomiSettingsModel() {
    }

    public interface RowOpener {
        void open(int rowId);
    }

    public static final class Copy {
        public final String account;
        public final String general;
        public final String support;
        public final String help;
        public final String phone;
        public final String email;
        public final String username;

        Copy(String account, String general, String support, String help, String phone, String email, String username) {
            this.account = account;
            this.general = general;
            this.support = support;
            this.help = help;
            this.phone = phone;
            this.email = email;
            this.username = username;
        }

        public static Copy resolve(String languageCode) {
            return resolve(languageCode, null);
        }

        public static Copy resolve(String languageCode, TellomiPack.Source source) {
            return new Copy(
                TellomiPack.text("Tellomi.Settings.Group.Account", languageCode, source),
                TellomiPack.text("Tellomi.Settings.Group.General", languageCode, source),
                TellomiPack.text("Tellomi.Settings.Group.Support", languageCode, source),
                TellomiPack.text("Tellomi.Settings.Help", languageCode, source),
                TellomiPack.text("Tellomi.Settings.Card.Phone", languageCode, source),
                TellomiPack.text("Tellomi.Settings.Card.Email", languageCode, source),
                TellomiPack.text("Tellomi.Settings.Card.Username", languageCode, source)
            );
        }
    }

    public static final class Card {
        public final String title;
        public final String phoneLine;
        public final String emailLine;
        public final String usernameLine;
        public final String subtitle;

        Card(String title, String phoneLine, String emailLine, String usernameLine, String subtitle) {
            this.title = title;
            this.phoneLine = phoneLine;
            this.emailLine = emailLine;
            this.usernameLine = usernameLine;
            this.subtitle = subtitle;
        }
    }

    public static final class Entry {
        public final int id;
        public final int kind;
        public final String text;
        public final int sourceIndex;

        public Entry(int id, int kind, String text, int sourceIndex) {
            this.id = id;
            this.kind = kind;
            this.text = text;
            this.sourceIndex = sourceIndex;
        }

        public Entry withText(String value) {
            return new Entry(id, kind, value, sourceIndex);
        }
    }

    public static boolean validKey(String key) {
        if (key == null || key.indexOf('-') >= 0) {
            return false;
        }
        return key.matches("^Tellomi\\.Settings\\.[A-Za-z0-9._]+$");
    }

    public static int groupOf(int id) {
        if (contains(ACCOUNT_IDS, id)) {
            return ROW_ACCOUNT;
        }
        if (contains(GENERAL_IDS, id)) {
            return ROW_GENERAL;
        }
        if (contains(SUPPORT_IDS, id)) {
            return ROW_SUPPORT;
        }
        return 0;
    }

    public static boolean isGroup(int id) {
        return id == ROW_ACCOUNT || id == ROW_GENERAL || id == ROW_SUPPORT;
    }

    public static boolean excludedFromHome(int id, String text) {
        if (id == TIPS_ID || id == WATCH_ID || groupOf(id) != 0) {
            return true;
        }
        return text != null && "Apple Watch".equals(text.trim());
    }

    public static String destination(int id) {
        switch (id) {
            case 1:
                return "profile";
            case 3:
                return "privacy";
            case 8:
                return "devices";
            case 5:
                return "notifications";
            case 2:
                return "chat";
            case 6:
                return "data";
            case 7:
                return "folders";
            case 9:
                return "power";
            case 10:
                return "language";
            case 40:
                return "navigation";
            case 25:
                return "wallet";
            case 11:
                return "premium";
            case 12:
                return "stars";
            case 13:
                return "ton";
            case 15:
                return "business";
            case 16:
                return "gifts";
            case FAQ_ID:
                return "help";
            case 17:
                return "ask";
            case 19:
                return "policy";
            case 27:
                return "licenses";
            case 41:
                return "updates";
            case ROW_ACCOUNT:
                return "page.account";
            case ROW_GENERAL:
                return "page.general";
            case ROW_SUPPORT:
                return "page.support";
            default:
                return "";
        }
    }

    public static String normalizeHelpUrl(String raw) {
        if (raw == null) {
            return DEFAULT_HELP_URL;
        }
        String value = raw.trim();
        if (value.length() == 0 || value.indexOf(' ') >= 0 || value.indexOf('\n') >= 0) {
            return DEFAULT_HELP_URL;
        }
        String lower = value.toLowerCase(Locale.US);
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) {
            return DEFAULT_HELP_URL;
        }
        return value;
    }

    /** Phone and email are both shown when the account has both. */
    public static Card card(String displayName, String phoneDisplay, String email, String username, Copy copy) {
        Copy labels = copy == null ? Copy.resolve("en") : copy;
        String phoneLine = line(labels.phone, phoneDisplay);
        String emailLine = line(labels.email, email);
        String at = null;
        if (!isEmpty(username)) {
            String trimmed = username.trim();
            at = trimmed.startsWith("@") ? trimmed : "@" + trimmed;
        }
        String usernameLine = line(labels.username, at);
        StringBuilder subtitle = new StringBuilder();
        appendLine(subtitle, phoneLine);
        appendLine(subtitle, emailLine);
        appendLine(subtitle, usernameLine);
        return new Card(displayName == null ? "" : displayName, phoneLine, emailLine, usernameLine, subtitle.toString());
    }

    public static List<Entry> arrangeEntries(List<Entry> source, Copy copy) {
        Copy labels = copy == null ? Copy.resolve("en") : copy;
        List<Entry> prefix = new ArrayList<>();
        List<Entry> tail = new ArrayList<>();
        boolean seenChild = false;
        boolean tailStarted = false;
        if (source != null) {
            for (int i = 0; i < source.size(); i++) {
                Entry entry = source.get(i);
                if (entry == null) {
                    continue;
                }
                if (entry.kind == KIND_ROW && excludedFromHome(entry.id, entry.text)) {
                    seenChild = true;
                    continue;
                }
                if (entry.kind == KIND_CUSTOM) {
                    if (!seenChild) {
                        prefix.add(entry);
                    } else {
                        tail.add(entry);
                    }
                    continue;
                }
                if (!seenChild) {
                    prefix.add(entry);
                    continue;
                }
                if (entry.kind == KIND_HEADER) {
                    Entry next = nextRow(source, i + 1);
                    if (next != null && next.kind == KIND_ROW && isTailRow(next.id)) {
                        tailStarted = true;
                        tail.add(entry);
                        continue;
                    }
                    if (!tailStarted) {
                        continue;
                    }
                }
                if (entry.kind == KIND_SHADOW && !tailStarted) {
                    continue;
                }
                if (entry.kind == KIND_ROW && isTailRow(entry.id)) {
                    tailStarted = true;
                }
                tail.add(entry);
            }
        }
        List<Entry> out = new ArrayList<>(prefix);
        out.add(new Entry(ROW_ACCOUNT, KIND_ROW, labels.account, -1));
        out.add(new Entry(ROW_GENERAL, KIND_ROW, labels.general, -1));
        out.add(new Entry(ROW_SUPPORT, KIND_ROW, labels.support, -1));
        if (!tail.isEmpty() && tail.get(0).kind != KIND_SHADOW && tail.get(0).kind != KIND_CUSTOM) {
            out.add(new Entry(0, KIND_SHADOW, null, -1));
        }
        out.addAll(tail);
        return collapseShadows(out);
    }

    public static List<Entry> pageEntries(int groupId, List<Entry> source, Copy copy) {
        Copy labels = copy == null ? Copy.resolve("en") : copy;
        int[] order = idsFor(groupId);
        Map<Integer, Entry> rows = new HashMap<>();
        if (source != null && order != null) {
            for (int i = 0; i < source.size(); i++) {
                Entry entry = source.get(i);
                if (entry == null || entry.kind != KIND_ROW || groupOf(entry.id) != groupId) {
                    continue;
                }
                if (rows.containsKey(entry.id)) {
                    continue;
                }
                rows.put(entry.id, entry.id == FAQ_ID ? entry.withText(labels.help) : entry);
            }
        }
        List<Entry> out = new ArrayList<>();
        if (order == null) {
            return out;
        }
        for (int id : order) {
            Entry entry = rows.get(id);
            if (entry != null) {
                out.add(entry);
            }
        }
        return out;
    }

    public static String pageTitle(int groupId, Copy copy) {
        Copy labels = copy == null ? Copy.resolve("en") : copy;
        if (groupId == ROW_ACCOUNT) {
            return labels.account;
        }
        if (groupId == ROW_GENERAL) {
            return labels.general;
        }
        if (groupId == ROW_SUPPORT) {
            return labels.support;
        }
        return "";
    }

    public static void dispatch(RowOpener opener, int rowId) {
        if (opener == null || isGroup(rowId) || rowId == TIPS_ID || rowId == WATCH_ID) {
            return;
        }
        opener.open(rowId);
    }

    private static Entry nextRow(List<Entry> source, int start) {
        for (int i = start; i < source.size(); i++) {
            Entry entry = source.get(i);
            if (entry == null || entry.kind == KIND_SHADOW) {
                continue;
            }
            if (entry.kind == KIND_ROW || entry.kind == KIND_HEADER || entry.kind == KIND_CUSTOM) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isTailRow(int id) {
        return contains(TAIL_IDS, id);
    }

    private static int[] idsFor(int groupId) {
        if (groupId == ROW_ACCOUNT) {
            return ACCOUNT_IDS;
        }
        if (groupId == ROW_GENERAL) {
            return GENERAL_IDS;
        }
        if (groupId == ROW_SUPPORT) {
            return SUPPORT_IDS;
        }
        return null;
    }

    private static boolean contains(int[] ids, int id) {
        for (int value : ids) {
            if (value == id) {
                return true;
            }
        }
        return false;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static String line(String label, String value) {
        if (isEmpty(value)) {
            return null;
        }
        String shown = value.trim();
        if (isEmpty(label)) {
            return shown;
        }
        return label + " " + shown;
    }

    private static void appendLine(StringBuilder out, String line) {
        if (line == null || line.length() == 0) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(line);
    }

    private static List<Entry> collapseShadows(List<Entry> items) {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            Entry entry = items.get(i);
            if (entry.kind == KIND_SHADOW && !out.isEmpty() && out.get(out.size() - 1).kind == KIND_SHADOW) {
                continue;
            }
            out.add(entry);
        }
        return out;
    }
}
