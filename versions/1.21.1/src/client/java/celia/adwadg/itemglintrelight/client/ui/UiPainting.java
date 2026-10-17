package celia.adwadg.itemglintrelight.client.ui;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Pixel access for {@link NativeImage} with ARGB semantics.
 *
 * <p>1.21.1 {@code NativeImage#setPixelRGBA}/{@code getPixelRGBA} store and return the raw ABGR
 * word (no channel swizzle); 1.21.11 split that into {@code setPixel(argb)}/ {@code setPixelABGR}.
 * The 1.21.11 code called {@code setPixel}, so every 1.21.1 call site has to convert ARGB &lt;-&gt;
 * ABGR explicitly or red and blue end up swapped.
 */
public final class UiPainting {
    private UiPainting() {
    }

    public static void setPixel(NativeImage image, int x, int y, int argb) {
        image.setPixelRGBA(x, y, toAbgr(argb));
    }

    public static int getPixel(NativeImage image, int x, int y) {
        return fromAbgr(image.getPixelRGBA(x, y));
    }

    public static int toAbgr(int argb) {
        return argb & 0xFF00FF00 | (argb & 0x00FF0000) >>> 16 | (argb & 0x000000FF) << 16;
    }

    public static int fromAbgr(int abgr) {
        return toAbgr(abgr);
    }
}
