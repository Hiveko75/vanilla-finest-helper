package io.github.hiveko.vanillafinest.helper.setup;

/**
 * Stands in for Minecraft's {@code MouseButtonEvent} (1.21.9 and newer) in the setup screen code copied from
 * 26.x: 1.21.1's mouse methods get x, y and the button separately (see mc1.21.1/build.gradle).
 */
record MouseButtonEvent(double x, double y, int button) {
}
