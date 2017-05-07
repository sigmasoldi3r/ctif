package pl.asie.ctif.platform;

import java.awt.*;

public abstract class Platform {
    public final int platformId;
    private final int charWidth, charHeight, width, height, customColorCount;
    private Color[] palette = null;

    protected Platform(int platformId, int charWidth, int charHeight, int width, int height,
                       int customColorCount) {
        this.platformId = platformId;
        this.charWidth = charWidth;
        this.charHeight = charHeight;
        this.width = width;
        this.height = height;
        this.customColorCount = customColorCount;
    }

    abstract Color[] generatePalette();

    public int getCharWidth() {
        return charWidth;
    }

    public int getCharHeight() {
        return charHeight;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getChars() {
        return getWidth() * getHeight();
    }

    public int getCustomColorCount() {
        return customColorCount;
    }

    public Color[] getPalette() {
        if (palette == null) {
            palette = generatePalette();
        }
        return palette.clone();
    }

    public float getDefaultAspectRatio() {
        return (float) getWidth() / getHeight();
    }

    public final int getWidthPx() {
        return getWidth() * getCharWidth();
    }

    public final int getHeightPx() {
        return getHeight() * getCharHeight();
    }

    public final int getCharsPx() {
        return getChars() * getCharWidth() * getCharHeight();
    }
}
