/*
 * This is the source code of Telegram for Android v. 7.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 *
 * Copyright Nikolai Kudashov, 2013-2020.
 *
 * Tellomi flavor only. Email sessions have no phone on the user object.
 * The address used at sign-in is remembered here so account chrome, the
 * switcher, and "already logged in" can see it. See UPSTREAM_PATCHES.md.
 */

package org.telegram.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.INavigationLayout;

import java.util.ArrayList;
import java.util.List;

public final class TellomiAccount {

    private static final String PREFS = "tellomi_account";

    private TellomiAccount() {
    }

    public static boolean looksLikeEmail(String value) {
        if (value == null) {
            return false;
        }
        int at = value.indexOf('@');
        return at > 0 && at == value.lastIndexOf('@') && at < value.length() - 1;
    }

    /** A phone, not an email stuffed into {@code user.phone}. */
    public static String phoneOf(TLRPC.User user) {
        if (user == null || TextUtils.isEmpty(user.phone) || looksLikeEmail(user.phone)) {
            return null;
        }
        String digits = PhoneFormat.stripExceptNumbers(user.phone);
        return digits.length() >= 6 ? digits : null;
    }

    public static void rememberEmail(int account, String email) {
        if (account < 0 || account >= UserConfig.MAX_ACCOUNT_COUNT) {
            return;
        }
        SharedPreferences prefs = prefs();
        if (prefs == null) {
            return;
        }
        String normalized = TellomiEmailLogin.normalize(email);
        SharedPreferences.Editor editor = prefs.edit();
        if (!TellomiEmailLogin.isValid(normalized)) {
            editor.remove(key(account));
        } else {
            editor.putString(key(account), normalized);
        }
        editor.apply();
    }

    public static void clearEmail(int account) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            prefs.edit().remove(key(account)).apply();
        }
    }

    /** Saved sign-in address, or an email the server put in {@code user.phone}. */
    public static String emailOf(int account, TLRPC.User user) {
        SharedPreferences prefs = prefs();
        if (prefs != null) {
            String saved = prefs.getString(key(account), null);
            if (TellomiEmailLogin.isValid(saved)) {
                return saved;
            }
        }
        if (user != null && looksLikeEmail(user.phone)) {
            return TellomiEmailLogin.normalize(user.phone);
        }
        return null;
    }

    /**
     * Switcher / avatar label. Phone when there is one, otherwise {@code a***@x.com}.
     * Falls back to the display name when neither is known.
     */
    public static String switcherLabel(int account, TLRPC.User user) {
        String phone = phoneOf(user);
        if (phone != null) {
            return PhoneFormat.getInstance().format("+" + phone);
        }
        String email = emailOf(account, user);
        if (email != null) {
            return maskEmail(email);
        }
        return user == null ? "" : UserObject.getUserName(user);
    }

    /** {@code alice@example.com} → {@code a***@e.com}. */
    public static String maskEmail(String email) {
        if (!looksLikeEmail(email)) {
            return email == null ? "" : email;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String head = local.substring(0, 1);
        int dot = domain.lastIndexOf('.');
        String domainHead = domain.substring(0, 1);
        String tail = dot > 0 ? domain.substring(dot) : "";
        return head + "***@" + domainHead + tail;
    }

    /** Another activated slot with this user id, or -1. */
    public static int accountForUser(long userId, int exceptAccount) {
        if (userId == 0) {
            return -1;
        }
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a == exceptAccount) {
                continue;
            }
            UserConfig config = UserConfig.getInstance(a);
            if (config.isClientActivated() && config.getClientUserId() == userId) {
                return a;
            }
        }
        return -1;
    }

    /** True when {@code typed} is the phone or email of an account already on this device. */
    public static int accountMatching(String typed, int exceptAccount) {
        if (typed == null) {
            return -1;
        }
        String raw = typed.trim();
        if (raw.length() == 0) {
            return -1;
        }
        boolean email = looksLikeEmail(raw);
        String digits = email ? "" : PhoneFormat.stripExceptNumbers(raw);
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (a == exceptAccount) {
                continue;
            }
            UserConfig config = UserConfig.getInstance(a);
            if (!config.isClientActivated()) {
                continue;
            }
            TLRPC.User user = config.getCurrentUser();
            if (email) {
                String known = emailOf(a, user);
                if (known != null && known.equalsIgnoreCase(raw)) {
                    return a;
                }
            } else if (digits.length() > 0 && user != null) {
                String phone = phoneOf(user);
                if (phone != null && (phone.equals(digits) || phone.endsWith(digits) || digits.endsWith(phone))) {
                    return a;
                }
            }
        }
        return -1;
    }

    /**
     * Add Account must open the login flow for a free slot, never the welcome intro.
     * A leftover intro under the main UI (first account only) is removed first so it cannot become the visible page.
     * @return false when the account limit is already reached
     */
    public static boolean openAddAccount(BaseFragment host) {
        if (host == null) {
            return false;
        }
        INavigationLayout layout = host.getParentLayout();
        if (layout != null) {
            List<BaseFragment> stack = new ArrayList<>(layout.getFragmentStack());
            for (int i = stack.size() - 1; i >= 0; i--) {
                BaseFragment fragment = stack.get(i);
                if (fragment instanceof IntroActivity) {
                    layout.removeFragmentFromStack(fragment);
                }
            }
        }
        int slot = freeSlot();
        if (slot < 0) {
            return false;
        }
        host.presentFragment(new LoginActivity(slot));
        return true;
    }

    /** Lowest free slot that the non-premium limit still allows, or -1. */
    public static int freeSlot() {
        int freeAccounts = 0;
        Integer available = null;
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!UserConfig.getInstance(a).isClientActivated()) {
                freeAccounts++;
                if (available == null) {
                    available = a;
                }
            }
        }
        if (!UserConfig.hasPremiumOnAccounts()) {
            freeAccounts -= (UserConfig.MAX_ACCOUNT_COUNT - UserConfig.MAX_ACCOUNT_DEFAULT_COUNT);
        }
        if (freeAccounts > 0 && available != null) {
            return available;
        }
        return -1;
    }

    private static SharedPreferences prefs() {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return null;
        }
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int account) {
        return "email_" + account;
    }
}
