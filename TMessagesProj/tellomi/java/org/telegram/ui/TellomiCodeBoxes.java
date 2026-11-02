package org.telegram.ui;

/**
 * Sizes the login code boxes so every digit stays inside the field width.
 * Values are pixels. A 6-box row at the preferred 52dp size overflows a narrow phone.
 */
public final class TellomiCodeBoxes {

    private TellomiCodeBoxes() {
    }

    /**
     * @return {@code [boxPx, gapPx]} that fit {@code count} boxes into {@code availablePx}
     */
    public static int[] fit(int availablePx, int count, int preferredBox, int minBox, int preferredGap, int minGap) {
        if (count <= 0) {
            return new int[] {preferredBox, preferredGap};
        }
        if (count == 1) {
            int box = availablePx > 0 ? Math.min(preferredBox, Math.max(1, availablePx)) : preferredBox;
            return new int[] {box, 0};
        }
        int gap = Math.max(0, preferredGap);
        int box = preferredBox;
        if (availablePx > 0) {
            int gaps = gap * (count - 1);
            int room = availablePx - gaps;
            box = room > 0 ? Math.min(preferredBox, room / count) : minBox;
            if (box < minBox) {
                gap = Math.max(0, minGap);
                room = availablePx - gap * (count - 1);
                box = room > 0 ? room / count : 1;
                if (box < 1) {
                    box = 1;
                }
                int used = box * count + gap * (count - 1);
                if (used > availablePx) {
                    gap = 0;
                    box = Math.max(1, availablePx / count);
                }
            }
        }
        return new int[] {box, gap};
    }
}
