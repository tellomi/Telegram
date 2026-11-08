package org.telegram.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The chat send control draws the iOS {@code chatInputPanelSendIconImage} arrow.
 * The circle is the button fill, not part of this drawable.
 */
public class TellomiSendGlyphTest {

    @Test
    public void sendGlyphIsTheIosArrowWithoutACircle() throws Exception {
        String arrow = read("TMessagesProj/tellomi/res/drawable/tellomi_chat_send_arrow.xml");
        String upstream = read("TMessagesProj/src/main/res/drawable/send_plane_24.xml");
        assertTrue(arrow.contains("M11,14.6666667 L16.4310816,9.40016333"));
        assertTrue(arrow.contains("M16.5,9.33333333 C17.0522847,9.33333333 17.5,9.78104858"));
        assertFalse(arrow.contains("M6.232"));
        assertTrue(pathData(upstream).contains("M6.232"));
        String enter = read("TMessagesProj/src/main/java/org/telegram/ui/Components/ChatActivityEnterView.java");
        assertTrue(enter.contains("R.drawable.tellomi_chat_send_arrow"));
    }

    @Test
    public void notificationAndContactIconReusesTheLauncherArt() throws Exception {
        String[] densities = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
        for (String density : densities) {
            byte[] launcher = Files.readAllBytes(find("TMessagesProj/tellomi/res/mipmap-" + density + "/ic_launcher.png").toPath());
            byte[] contact = Files.readAllBytes(find("TMessagesProj/tellomi/res/drawable-" + density + "/ic_launcher_dr.png").toPath());
            assertEquals(launcher.length, contact.length);
            for (int i = 0; i < launcher.length; i++) {
                if (launcher[i] != contact[i]) {
                    throw new AssertionError(density + " differs at " + i);
                }
            }
            byte[] plane = Files.readAllBytes(find("TMessagesProj/src/main/res/drawable-" + density + "/ic_launcher_dr.webp").toPath());
            assertFalse(plane.length == contact.length && plane[0] == contact[0] && plane[1] == contact[1] && plane[2] == contact[2]);
        }
    }

    private static String pathData(String xml) {
        int at = xml.indexOf("android:pathData=\"");
        int start = at + "android:pathData=\"".length();
        int end = xml.indexOf('"', start);
        return xml.substring(start, end);
    }

    private static Box bounds(String path) {
        Box box = new Box();
        int i = 0;
        while (i < path.length()) {
            char command = path.charAt(i);
            if (command == ' ' || command == ',') {
                i++;
                continue;
            }
            if (command == 'Z') {
                break;
            }
            if (command != 'M' && command != 'L') {
                throw new AssertionError("unexpected " + command);
            }
            i++;
            int[] cursor = new int[]{i};
            float x = readNumber(path, cursor);
            float y = readNumber(path, cursor);
            i = cursor[0];
            box.add(x, y);
        }
        return box;
    }

    private static float readNumber(String path, int[] at) {
        int i = at[0];
        while (i < path.length() && (path.charAt(i) == ' ' || path.charAt(i) == ',')) {
            i++;
        }
        int start = i;
        if (i < path.length() && path.charAt(i) == '-') {
            i++;
        }
        while (i < path.length() && (Character.isDigit(path.charAt(i)) || path.charAt(i) == '.')) {
            i++;
        }
        at[0] = i;
        return Float.parseFloat(path.substring(start, i));
    }

    private static final class Box {
        float minX = Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        float tipX;

        void add(float x, float y) {
            if (y < minY) {
                minY = y;
                tipX = x;
            }
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
    }

    private static String read(String relative) throws Exception {
        return new String(Files.readAllBytes(find(relative).toPath()), StandardCharsets.UTF_8);
    }

    private static File find(String relative) {
        File direct = new File(relative);
        if (direct.isFile()) {
            return direct;
        }
        File nested = new File("..", relative);
        if (nested.isFile()) {
            return nested;
        }
        throw new AssertionError("missing " + relative);
    }
}
