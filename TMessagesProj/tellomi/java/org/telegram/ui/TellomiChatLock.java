package org.telegram.ui;

import java.security.GeneralSecurityException;
import java.security.spec.KeySpec;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * A per-chat pin. The stored form is a salt and a PBKDF2 hash. The pin
 * itself is not kept. Clearing the lock requires the app passcode to have
 * been accepted; this class does not talk to the server.
 */
public final class TellomiChatLock {

    public static final String PREVIEW = "已锁定";
    public static final int MIN_DIGITS = 4;
    public static final int MAX_DIGITS = 6;
    private static final int ITERATIONS = 12000;

    private TellomiChatLock() {
    }

    public static boolean acceptable(String pin) {
        if (pin == null || pin.length() < MIN_DIGITS || pin.length() > MAX_DIGITS) {
            return false;
        }
        for (int i = 0; i < pin.length(); i++) {
            char c = pin.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    public static Record seal(String pin, byte[] salt, String biometricCipher) {
        if (!acceptable(pin) || salt == null || salt.length < 16) {
            throw new IllegalArgumentException("pin");
        }
        String cipher = biometricCipher == null ? "" : biometricCipher;
        return new Record(hex(salt), hex(hash(pin, salt)), !cipher.isEmpty(), cipher);
    }

    public static boolean matches(Record record, String pin) {
        if (record == null || !acceptable(pin)) {
            return false;
        }
        byte[] salt = unhex(record.saltHex);
        byte[] expect = unhex(record.hashHex);
        byte[] got = hash(pin, salt);
        if (got.length != expect.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < got.length; i++) {
            diff |= got[i] ^ expect[i];
        }
        return diff == 0;
    }

    /** List, search, notification, and share previews stay masked while a lock exists. */
    public static String preview(String original, boolean locked) {
        return locked ? PREVIEW : original;
    }

    /**
     * Forgotten pin: the lock can be removed only after the existing app
     * passcode has been checked. No app passcode means it cannot be cleared.
     */
    public static boolean mayClear(boolean appPasscodeConfigured, boolean appPasscodeAccepted) {
        return appPasscodeConfigured && appPasscodeAccepted;
    }

    public static String serialize(Record record) {
        return record.saltHex + "\n" + record.hashHex + "\n" + (record.biometric ? "1" : "0") + "\n" + record.biometricCipher;
    }

    public static Record parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\n", -1);
        if (parts.length < 3 || parts[0].length() < 32 || parts[1].length() < 32) {
            return null;
        }
        String cipher = parts.length >= 4 ? parts[3] : "";
        boolean biometric = "1".equals(parts[2]) && !cipher.isEmpty();
        return new Record(parts[0], parts[1], biometric, cipher);
    }

    private static byte[] hash(String pin, byte[] salt) {
        try {
            KeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            out.append(String.format(Locale.US, "%02x", b & 0xff));
        }
        return out.toString();
    }

    static byte[] unhex(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static final class Record {
        public final String saltHex;
        public final String hashHex;
        public final boolean biometric;
        public final String biometricCipher;

        Record(String saltHex, String hashHex, boolean biometric, String biometricCipher) {
            this.saltHex = saltHex;
            this.hashHex = hashHex;
            this.biometric = biometric;
            this.biometricCipher = biometricCipher == null ? "" : biometricCipher;
        }
    }
}
