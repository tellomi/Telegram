package org.telegram.messenger;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shippable DC seed dials the relay. Download, upload, and CDN have no
 * second hardcoded origin address; media falls back to this same list.
 */
public class TellomiProductionDcTest {

    private static final String RELAY = "8.137.198.240";
    private static final String ORIGIN = "8.156.40.105";
    private static final String EXPANDED =
            "1:" + RELAY + ":10443,1:" + RELAY + ":443,1:api.tellomi.cn:10443,1:api.tellomi.cn:443,"
                    + "2:" + RELAY + ":10443,2:" + RELAY + ":443,2:api.tellomi.cn:10443,2:api.tellomi.cn:443";
    private static final String PIN_SHA256 = "3c310e3d81ae1f551ee133de9f4181b02b95154f0d340cfdf4b5b590f2c79776";

    @Test
    public void shippableSeedDialsTheRelayOnBothPortsThenTheHostname() throws Exception {
        String gradle = read("TMessagesProj/build.gradle");
        String workflow = read(".github/workflows/android-apk.yml");

        assertTrue(gradle.contains("def TELLOMI_PRODUCTION_HOST = '" + RELAY + "'"));
        assertFalse(gradle.contains("def TELLOMI_PRODUCTION_HOST = '" + ORIGIN + "'"));
        assertTrue(gradle.contains("def TELLOMI_PRODUCTION_PORT = '10443'"));
        assertTrue(gradle.contains("def TELLOMI_FALLBACK_PORT = '443'"));
        assertTrue(gradle.contains("def TELLOMI_FALLBACK_HOST = 'api.tellomi.cn'"));
        assertTrue(gradle.contains(
                "1:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_PRODUCTION_PORT},1:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_FALLBACK_PORT},1:${TELLOMI_FALLBACK_HOST}:${TELLOMI_PRODUCTION_PORT},1:${TELLOMI_FALLBACK_HOST}:${TELLOMI_FALLBACK_PORT},2:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_PRODUCTION_PORT},2:${TELLOMI_PRODUCTION_HOST}:${TELLOMI_FALLBACK_PORT},2:${TELLOMI_FALLBACK_HOST}:${TELLOMI_PRODUCTION_PORT},2:${TELLOMI_FALLBACK_HOST}:${TELLOMI_FALLBACK_PORT}"));
        assertTrue(gradle.contains("def tellomiDirectOrigin = '" + ORIGIN + "'"));
        assertTrue(gradle.contains("addresses.contains(tellomiDirectOrigin)"));
        assertTrue(workflow.contains(EXPANDED));
        assertFalse(workflow.contains(ORIGIN));
        assertFalse(gradle.contains("8.137.39.82"));
    }

    @Test
    public void rsaPinIsTheUnchangedOriginKey() throws Exception {
        String gradle = read("TMessagesProj/build.gradle");
        byte[] pem = Files.readAllBytes(find("TMessagesProj/config/tellomi-server-public.pem"));
        String text = new String(pem, StandardCharsets.UTF_8);

        assertTrue(gradle.contains("TMessagesProj/config/tellomi-server-public.pem"));
        assertTrue(text.contains("4381149949997195609"));
        assertTrue(text.contains("-----BEGIN RSA PUBLIC KEY-----"));
        assertEquals(PIN_SHA256, sha256(pem));
    }

    @Test
    public void downloadUploadAndCdnDoNotHardcodeTheOrigin() throws Exception {
        String[] relatives = new String[] {
                "TMessagesProj/jni/tgnet/ConnectionsManager.cpp",
                "TMessagesProj/jni/tgnet/Connection.cpp",
                "TMessagesProj/jni/tgnet/Datacenter.cpp",
                "TMessagesProj/jni/tgnet/TellomiDcApply.cpp",
                "TMessagesProj/jni/tgnet/TellomiNetConfig.cpp",
                "TMessagesProj/src/main/java/org/telegram/messenger/FileLoadOperation.java",
                "TMessagesProj/src/main/java/org/telegram/ui/Components/BlockingUpdateView.java",
                "TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java"
        };
        for (int i = 0; i < relatives.length; i++) {
            assertFalse(relatives[i], read(relatives[i]).contains(ORIGIN));
        }
        String nativeManager = read("TMessagesProj/jni/tgnet/ConnectionsManager.cpp");
        String javaManager = read("TMessagesProj/src/main/java/org/telegram/tgnet/ConnectionsManager.java");
        String connection = read("TMessagesProj/jni/tgnet/Connection.cpp");
        assertTrue(nativeManager.contains("datacenter->addAddressAndPort(dc.host, (uint32_t) dc.port, 0, \"\")"));
        assertTrue(javaManager.contains("if (BuildVars.TELLOMI_CUSTOM_BACKEND)"));
        assertTrue(javaManager.contains("skip Telegram DNS datacenter bootstrap"));
        assertTrue(connection.contains("tcpAddress = currentDatacenter->getCurrentAddress(currentAddressFlags | ipv6);"));
        assertTrue(connection.contains("currentAddressFlags = isStatic;"));
        assertTrue(connection.contains("return (type & ConnectionTypeGenericMedia) != 0 || (type & ConnectionTypeDownload) != 0;"));
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (int i = 0; i < digest.length; i++) {
            hex.append(String.format("%02x", digest[i]));
        }
        return hex.toString();
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(find(relative)), StandardCharsets.UTF_8);
    }

    private static Path find(String relative) {
        Path[] candidates = new Path[] {
                Paths.get(relative),
                Paths.get("..").resolve(relative),
                Paths.get("../..").resolve(relative)
        };
        for (int i = 0; i < candidates.length; i++) {
            if (Files.isRegularFile(candidates[i])) {
                return candidates[i];
            }
        }
        throw new IllegalStateException("missing " + relative);
    }
}
