package org.telegram.ui.Components;

public enum IconBackgroundColors {
    // TELLOMI: neutral plates so a white glyph stays readable. Names stay put
    // so upstream call sites do not move. Hardcoded hues still pass through
    // TellomiIconColors.plate in SettingCell.Background.
    BLUE(0xFF3A3A3E, 0xFF2A2A2E),
    BLUE_ALT(0xFF454549, 0xFF323236),
    BLUE_DEEP(0xFF2E2E32, 0xFF1C1C1F),
    BLUE_LIGHT(0xFF525256, 0xFF3C3C40),

    ORANGE(0xFF404044, 0xFF2C2C30),
    ORANGE_DEEP(0xFF353538, 0xFF222226),

    GREEN(0xFF48484C, 0xFF343438),
    RED(0xFF2A2A2E, 0xFF18181B),
    CYAN(0xFF4E4E52, 0xFF38383C),
    PURPLE(0xFF3E3E42, 0xFF2C2C30),
    GRAY(0xFF6B6B70, 0xFF55555A);

    public final int top;
    public final int bottom;

    private IconBackgroundColors(int top, int bottom) {
        this.top = top;
        this.bottom = bottom;
    }
}
