package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Saved files and the contacts group must keep the names the installed package already wrote. */
public class TellomiInstalledDataPathTest {

    @Test
    public void savedMediaAndContactLabelsUseTheInstalledName() throws Exception {
        String vars = read("src/main/java/org/telegram/messenger/BuildVars.java");
        String images = read("src/main/java/org/telegram/messenger/ImageLoader.java");
        String shared = read("src/main/java/org/telegram/messenger/SharedConfig.java");
        String migration = read("src/main/java/org/telegram/messenger/FilesMigrationService.java");
        String utilities = read("src/main/java/org/telegram/messenger/AndroidUtilities.java");
        String picker = read("src/main/java/org/telegram/ui/Components/ChatAttachAlertDocumentLayout.java");
        String contacts = read("src/main/java/org/telegram/messenger/ContactsController.java");
        String messages = read("src/main/java/org/telegram/messenger/MessagesController.java");

        assertTrue(vars.contains("APP_NAME = \"Tellomi\""));
        assertTrue(vars.contains("STORAGE_DIR_NAME = APP_NAME"));
        assertTrue(vars.contains("STORAGE_IMAGES_DIR = APP_NAME + \" Images\""));
        assertTrue(images.contains("BuildVars.STORAGE_DIR_NAME"));
        assertTrue(images.contains("BuildVars.STORAGE_IMAGES_DIR"));
        assertTrue(images.contains("BuildVars.STORAGE_VIDEO_DIR"));
        assertTrue(images.contains("BuildVars.STORAGE_AUDIO_DIR"));
        assertTrue(images.contains("BuildVars.STORAGE_DOCUMENTS_DIR"));
        assertTrue(images.contains("BuildVars.STORAGE_FILES_DIR"));
        assertTrue(images.contains("BuildVars.STORAGE_STORIES_DIR"));
        assertTrue(shared.contains("BuildVars.STORAGE_DIR_NAME"));
        assertTrue(migration.contains("BuildVars.STORAGE_DIR_NAME"));
        assertTrue(utilities.contains("BuildVars.STORAGE_DIR_NAME"));
        assertTrue(picker.contains("BuildVars.STORAGE_DIR_NAME"));
        assertTrue(picker.contains("fs.title = BuildVars.APP_NAME"));
        assertTrue(contacts.contains("BuildVars.APP_NAME + \" Profile\""));
        assertTrue(contacts.contains("BuildVars.APP_NAME + \" Voice Call\""));
        assertTrue(contacts.contains("BuildVars.APP_NAME + \" Video Call\""));
        assertTrue(contacts.contains("BuildVars.APP_NAME + \"ConnectionService\""));
        int support = messages.indexOf("public void addSupportUser()");
        assertTrue(support >= 0);
        String seed = messages.substring(support, messages.indexOf("public TLRPC.InputUser getInputUser", support));
        assertTrue(seed.contains("user.id = 777000"));
        assertTrue(seed.contains("user.first_name = BuildVars.APP_NAME"));
        assertFalse(seed.contains("user.first_name = \"Telegram\""));

        for (String source : new String[] {images, shared, migration, utilities, picker, contacts}) {
            assertFalse(source.contains("\"Telegram Images\""));
            assertFalse(source.contains("\"Telegram Video\""));
            assertFalse(source.contains("\"Telegram Audio\""));
            assertFalse(source.contains("\"Telegram Documents\""));
            assertFalse(source.contains("\"Telegram Files\""));
            assertFalse(source.contains("\"Telegram Stories\""));
            assertFalse(source.contains("\"TelegramConnectionService\""));
            assertFalse(source.contains("\"Telegram Profile\""));
        }
    }

    private static String read(String relative) throws Exception {
        Path path = Paths.get("TMessagesProj").resolve(relative);
        if (!Files.exists(path)) {
            path = Paths.get(relative);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
