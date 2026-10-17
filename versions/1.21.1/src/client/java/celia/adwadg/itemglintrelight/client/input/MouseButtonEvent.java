package celia.adwadg.itemglintrelight.client.input;

/**
 * 1.21.1 counterpart of {@code net.minecraft.client.input.MouseButtonEvent}. Only the accessors the
 * ported screen actually uses are provided.
 */
public record MouseButtonEvent(double x, double y, int button) {
}
