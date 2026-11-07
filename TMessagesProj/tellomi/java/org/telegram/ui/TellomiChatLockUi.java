package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.text.InputType;
import android.widget.EditText;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;

import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

/** Pin dialogs and the optional biometric unlock. The hash never leaves the device. */
public final class TellomiChatLockUi {

    private TellomiChatLockUi() {
    }

    public static void configure(BaseFragment fragment, int account, long dialogId) {
        Activity activity = fragment.getParentActivity();
        if (activity == null || dialogId == 0) {
            return;
        }
        if (!TellomiChatLockStore.locked(account, dialogId)) {
            askNewPin(activity, account, dialogId, null);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(LocaleController.getString(R.string.TellomiChatPassword));
        builder.setItems(new CharSequence[] {
                LocaleController.getString(R.string.TellomiChatPasswordChange),
                LocaleController.getString(R.string.TellomiChatPasswordRemove),
                LocaleController.getString(R.string.TellomiChatPasswordForgot)
        }, (dialog, which) -> {
            if (which == 0) {
                askPin(activity, LocaleController.getString(R.string.TellomiChatPasswordCurrent), pin -> {
                    TellomiChatLock.Record record = TellomiChatLockStore.load(account, dialogId);
                    if (!TellomiChatLock.matches(record, pin)) {
                        toast(activity, LocaleController.getString(R.string.TellomiChatPasswordWrong));
                        return;
                    }
                    askNewPin(activity, account, dialogId, null);
                });
            } else if (which == 1 || which == 2) {
                confirmAppPasscode(activity, () -> {
                    TellomiChatLockStore.clear(account, dialogId);
                    deleteKey(account, dialogId);
                });
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    public static void unlock(BaseFragment fragment, int account, long dialogId, Runnable onUnlocked) {
        Activity activity = fragment.getParentActivity();
        TellomiChatLock.Record record = TellomiChatLockStore.load(account, dialogId);
        if (activity == null || record == null) {
            if (onUnlocked != null) {
                onUnlocked.run();
            }
            return;
        }
        if (record.biometric) {
            offerBiometric(activity, account, dialogId, record, onUnlocked);
        }
        askPin(activity, LocaleController.getString(R.string.TellomiChatPasswordEnter), pin -> {
            if (!TellomiChatLock.matches(record, pin)) {
                toast(activity, LocaleController.getString(R.string.TellomiChatPasswordWrong));
                return;
            }
            if (onUnlocked != null) {
                onUnlocked.run();
            }
        });
    }

    private static void askNewPin(Activity activity, int account, long dialogId, String biometricCipher) {
        askPin(activity, LocaleController.getString(R.string.TellomiChatPasswordNew), first -> {
            if (!TellomiChatLock.acceptable(first)) {
                toast(activity, LocaleController.getString(R.string.TellomiChatPasswordRules));
                return;
            }
            askPin(activity, LocaleController.getString(R.string.TellomiChatPasswordConfirm), second -> {
                if (!first.equals(second)) {
                    toast(activity, LocaleController.getString(R.string.TellomiChatPasswordMismatch));
                    return;
                }
                boolean wantBio = biometricCipher == null && canAuthenticate(activity);
                if (!wantBio) {
                    save(activity, account, dialogId, first, "");
                    return;
                }
                AlertDialog.Builder builder = new AlertDialog.Builder(activity);
                builder.setTitle(LocaleController.getString(R.string.TellomiChatPasswordBiometric));
                builder.setMessage(LocaleController.getString(R.string.TellomiChatPasswordBiometricHint));
                builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) ->
                        encryptWithBiometric(activity, account, dialogId, first));
                builder.setNegativeButton(LocaleController.getString(R.string.TellomiChatPasswordSkip), (d, w) ->
                        save(activity, account, dialogId, first, ""));
                builder.show();
            });
        });
    }

    private static void save(Activity activity, int account, long dialogId, String pin, String cipher) {
        byte[] salt = new byte[16];
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        rnd.nextBytes(salt);
        TellomiChatLockStore.save(account, dialogId, TellomiChatLock.seal(pin, salt, cipher));
        toast(activity, LocaleController.getString(R.string.TellomiChatPasswordSaved));
    }

    private static void askPin(Activity activity, String title, Pin pin) {
        EditText field = new EditText(activity);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        field.setHint(LocaleController.getString(R.string.TellomiChatPasswordRules));
        int pad = AndroidUtilities.dp(20);
        field.setPadding(pad, pad, pad, pad);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(title);
        builder.setView(field);
        builder.setPositiveButton(LocaleController.getString(R.string.OK), (d, w) -> pin.accept(field.getText().toString().trim()));
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        builder.show();
    }

    private static void confirmAppPasscode(Activity activity, Runnable onAccepted) {
        if (SharedConfig.passcodeHash == null || SharedConfig.passcodeHash.length() == 0) {
            new AlertDialog.Builder(activity)
                    .setTitle(LocaleController.getString(R.string.TellomiChatPasswordForgot))
                    .setMessage(LocaleController.getString(R.string.TellomiChatPasswordNeedApp))
                    .setPositiveButton(LocaleController.getString(R.string.OK), null)
                    .show();
            return;
        }
        askPin(activity, LocaleController.getString(R.string.TellomiChatPasswordApp), pin -> {
            if (!TellomiChatLock.mayClear(true, SharedConfig.checkPasscode(pin))) {
                toast(activity, LocaleController.getString(R.string.TellomiChatPasswordWrong));
                return;
            }
            onAccepted.run();
            toast(activity, LocaleController.getString(R.string.TellomiChatPasswordCleared));
        });
    }

    private static boolean canAuthenticate(Context context) {
        try {
            int code = BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            return code == BiometricManager.BIOMETRIC_SUCCESS;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void encryptWithBiometric(Activity activity, int account, long dialogId, String pin) {
        try {
            SecretKey key = newKey(account, dialogId);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            authenticate(activity, new BiometricPrompt.CryptoObject(cipher), result -> {
                try {
                    Cipher used = result.getCryptoObject().getCipher();
                    byte[] ct = used.doFinal(pin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    String packed = TellomiChatLock.hex(used.getIV()) + ":" + TellomiChatLock.hex(ct);
                    save(activity, account, dialogId, pin, packed);
                } catch (Throwable t) {
                    FileLog.e(t);
                    save(activity, account, dialogId, pin, "");
                }
            }, () -> save(activity, account, dialogId, pin, ""));
        } catch (Throwable t) {
            FileLog.e(t);
            save(activity, account, dialogId, pin, "");
        }
    }

    private static void offerBiometric(Activity activity, int account, long dialogId, TellomiChatLock.Record record, Runnable onUnlocked) {
        try {
            String[] parts = record.biometricCipher.split(":", 2);
            if (parts.length != 2) {
                return;
            }
            byte[] iv = TellomiChatLock.unhex(parts[0]);
            byte[] ct = TellomiChatLock.unhex(parts[1]);
            SecretKey key = existingKey(account, dialogId);
            if (key == null) {
                return;
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            authenticate(activity, new BiometricPrompt.CryptoObject(cipher), result -> {
                try {
                    byte[] pinBytes = result.getCryptoObject().getCipher().doFinal(ct);
                    String pin = new String(pinBytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (TellomiChatLock.matches(record, pin) && onUnlocked != null) {
                        onUnlocked.run();
                    }
                } catch (Throwable t) {
                    FileLog.e(t);
                }
            }, null);
        } catch (Throwable t) {
            FileLog.e(t);
        }
    }

    private static void authenticate(Activity activity, BiometricPrompt.CryptoObject crypto, Success success, Runnable failed) {
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(LocaleController.getString(R.string.TellomiChatPasswordBiometric))
                .setNegativeButtonText(LocaleController.getString(R.string.Cancel))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        if (!(activity instanceof FragmentActivity)) {
            if (failed != null) {
                failed.run();
            }
            return;
        }
        BiometricPrompt prompt = new BiometricPrompt((FragmentActivity) activity, ContextCompat.getMainExecutor(activity), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                success.accept(result);
            }

            @Override
            public void onAuthenticationError(int errorCode, CharSequence errString) {
                if (failed != null) {
                    failed.run();
                }
            }
        });
        prompt.authenticate(info, crypto);
    }

    private static SecretKey newKey(int account, long dialogId) throws Exception {
        deleteKey(account, dialogId);
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias(account, dialogId), KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .build());
        return generator.generateKey();
    }

    private static SecretKey existingKey(int account, long dialogId) {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            return (SecretKey) store.getKey(alias(account, dialogId), null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void deleteKey(int account, long dialogId) {
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            store.deleteEntry(alias(account, dialogId));
        } catch (Throwable ignore) {
        }
    }

    private static String alias(int account, long dialogId) {
        return "tellomi_chat_lock_" + account + "_" + dialogId;
    }

    private static void toast(Activity activity, String text) {
        AndroidUtilities.runOnUIThread(() -> {
            if (!activity.isFinishing()) {
                new AlertDialog.Builder(activity).setMessage(text).setPositiveButton(LocaleController.getString(R.string.OK), null).show();
            }
        });
    }

    private interface Pin {
        void accept(String pin);
    }

    private interface Success {
        void accept(BiometricPrompt.AuthenticationResult result);
    }
}
