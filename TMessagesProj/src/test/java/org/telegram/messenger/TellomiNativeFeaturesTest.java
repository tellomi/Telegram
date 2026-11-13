package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Native feature audit 2026-11-14. Defaults are real constants, and the
 * upstream call sites that read them are checked from source.
 */
public class TellomiNativeFeaturesTest {

    @Test
    public void serverBackedFeaturesDefaultOn() {
        assertTrue(TellomiNativeFeatures.DIALOG_FILTERS_ENABLED);
        assertTrue(TellomiNativeFeatures.BACKGROUND_CONNECTION);
        assertTrue(TellomiNativeFeatures.KEEP_ALIVE_SERVICE);
        assertTrue(TellomiNativeFeatures.SAVE_GIFS_WITH_STICKERS);
    }

    @Test
    public void paymentsFragmentAndTonStayOff() {
        assertTrue(TellomiNativeFeatures.PREMIUM_PURCHASE_BLOCKED);
        assertTrue(TellomiNativeFeatures.BILLING_UNAVAILABLE);
        assertFalse(TellomiNativeFeatures.STARS_PAID_MESSAGES);
        assertFalse(TellomiNativeFeatures.STARS_GIFTS_ENABLED);
        assertFalse(TellomiNativeFeatures.GIFTS_IN_PROFILE);
        assertFalse(TellomiNativeFeatures.WALLET_AVAILABLE);
        assertFalse(TellomiNativeFeatures.fragmentLinksAllowed());
        assertTrue("disabled".equals(TellomiNativeFeatures.TRANSLATIONS));
        assertFalse(TellomiNativeFeatures.aiComposeEnabled(false));
        assertTrue(TellomiNativeFeatures.aiComposeEnabled(true));
    }

    @Test
    public void messagesControllerUsesTheAuditDefaults() throws Exception {
        String source = read("src/main/java/org/telegram/messenger/MessagesController.java");
        assertTrue(source.contains("getBoolean(\"filtersEnabled\", TellomiNativeFeatures.DIALOG_FILTERS_ENABLED)"));
        assertTrue(source.contains("getBoolean(\"backgroundConnection\", TellomiNativeFeatures.BACKGROUND_CONNECTION)"));
        assertTrue(source.contains("getBoolean(\"keepAliveService\", TellomiNativeFeatures.KEEP_ALIVE_SERVICE)"));
        assertTrue(source.contains("getBoolean(\"saveGifsWithStickers\", TellomiNativeFeatures.SAVE_GIFS_WITH_STICKERS)"));
        assertTrue(source.contains("getBoolean(\"premiumLocked\", TellomiNativeFeatures.PREMIUM_PURCHASE_BLOCKED)"));
        assertTrue(source.contains("getBoolean(\"starsPaidMessagesAvailable\", TellomiNativeFeatures.STARS_PAID_MESSAGES)"));
        assertTrue(source.contains("getBoolean(\"starsGiftsEnabled\", TellomiNativeFeatures.STARS_GIFTS_ENABLED)"));
        assertTrue(source.contains("getBoolean(\"enableGiftsInProfile\", TellomiNativeFeatures.GIFTS_IN_PROFILE)"));
        assertTrue(source.contains("getString(\"translationsManualEnabled\", TellomiNativeFeatures.TRANSLATIONS)"));
        assertTrue(source.contains("getString(\"translationsAutoEnabled\", TellomiNativeFeatures.TRANSLATIONS)"));
    }

    @Test
    public void connectionAndBillingFollowTheSameDefaults() throws Exception {
        String config = read("src/main/java/org/telegram/messenger/AppGlobalConfig.java");
        assertTrue(config.contains("ofBoolean(\"wallet_available\", TellomiNativeFeatures.WALLET_AVAILABLE)"));
        assertTrue(config.contains("ofBoolean(\"tellomi_ai_compose_enabled\", false)"));

        String vars = read("src/main/java/org/telegram/messenger/BuildVars.java");
        assertTrue(vars.contains("IS_BILLING_UNAVAILABLE = TellomiNativeFeatures.BILLING_UNAVAILABLE"));

        String connection = read("src/main/java/org/telegram/tgnet/ConnectionsManager.java");
        assertTrue(connection.contains("getBoolean(\"backgroundConnection\", TellomiNativeFeatures.BACKGROUND_CONNECTION)"));

        String loader = read("src/main/java/org/telegram/messenger/ApplicationLoader.java");
        assertTrue(loader.contains("getBoolean(\"keepAliveService\", TellomiNativeFeatures.KEEP_ALIVE_SERVICE)"));
    }

    @Test
    public void composerAiFollowsTheServerFlag() throws Exception {
        String[] files = {
                "src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java",
                "src/main/java/org/telegram/ui/Components/ChatAttachAlert.java",
                "src/main/java/org/telegram/ui/Components/CaptionPhotoViewer.java",
                "src/main/java/org/telegram/ui/iv/RichEditor.java",
                "src/main/java/org/telegram/ui/iv/RichEditorToolbar.java",
                "src/main/java/org/telegram/ui/iv/ChatAttachAlertRichLayout.java"
        };
        for (String file : files) {
            String source = read(file);
            assertTrue(file, source.contains("TellomiNativeFeatures.aiComposeEnabled("));
            assertTrue(file, source.contains("config.tellomiAiComposeEnabled.get()"));
        }
    }

    @Test
    public void fragmentEntryPointsCheckTheGate() throws Exception {
        String[] files = {
                "src/main/java/org/telegram/ui/ChangeUsernameActivity.java",
                "src/main/java/org/telegram/ui/ChannelCreateActivity.java",
                "src/main/java/org/telegram/ui/ChatEditTypeActivity.java",
                "src/main/java/org/telegram/ui/ChatActivity.java",
                "src/main/java/org/telegram/ui/ProfileActivity.java",
                "src/main/java/org/telegram/ui/Wallet/WalletActivity.java",
                "src/main/java/org/telegram/ui/Stars/StarGiftSheet.java"
        };
        for (String file : files) {
            String source = read(file);
            assertTrue(file, source.contains("TellomiNativeFeatures.fragmentLinksAllowed()"));
            assertTrue(file, source.contains("fragment.com"));
        }
    }

    private static String read(String relative) {
        try {
            return new String(Files.readAllBytes(repoRoot().resolve("TMessagesProj").resolve(relative)), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(relative, e);
        }
    }

    private static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.exists(dir.resolve("settings.gradle")) && Files.exists(dir.resolve("TMessagesProj"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("repo root not found from " + Paths.get("").toAbsolutePath());
    }
}
