package org.telegram.ui;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The chat send control draws {@code R.drawable.send_plane_24}.
 * This checks that flavor file's geometry. It does not inflate a View.
 */
public class TellomiSendGlyphTest {

    @Test
    public void sendGlyphIsAnUpArrowNotThePaperPlane() throws Exception {
        String flavor = read("TMessagesProj/tellomi/res/drawable/send_plane_24.xml");
        String upstream = read("TMessagesProj/src/main/res/drawable/send_plane_24.xml");
        String flavorPath = pathData(flavor);
        String upstreamPath = pathData(upstream);
        assertFalse(flavorPath.contains("M6.232"));
        assertTrue(upstreamPath.contains("M6.232"));

        Box box = bounds(flavorPath);
        assertTrue(box.maxY - box.minY > box.maxX - box.minX);
        float tipX = box.tipX;
        float midX = (box.minX + box.maxX) / 2f;
        assertTrue(Math.abs(tipX - midX) < 1.5f);
        assertEquals(4f, box.minY, 0.01f);
        assertEquals(20f, box.maxY, 0.01f);
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
