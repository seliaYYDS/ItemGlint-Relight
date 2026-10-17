package celia.adwadg.itemglintrelight.client.ui;

public final class UiMath {
    private UiMath() {
    }

    public static float approach(float current, float target, float deltaSeconds, float speed) {
        return current + (target - current) * (1.0F - (float) Math.exp(-speed * deltaSeconds));
    }

    public static int mix(int from, int to, float amount) {
        float clamped = Math.max(0.0F, Math.min(1.0F, amount));
        int alpha = mixChannel(from >>> 24, to >>> 24, clamped);
        int red = mixChannel(from >>> 16 & 0xFF, to >>> 16 & 0xFF, clamped);
        int green = mixChannel(from >>> 8 & 0xFF, to >>> 8 & 0xFF, clamped);
        int blue = mixChannel(from & 0xFF, to & 0xFF, clamped);
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static boolean contains(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private static int mixChannel(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }
}
