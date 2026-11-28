package de.meinhelfer.sunny.launcher.idea;

import org.junit.Test;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class PaletteTest {
    @Test
    public void bothColorPalettesHaveDistinctLightAndDarkVariants() {
        for (LauncherPreferences.Colors colors : LauncherPreferences.Colors.values()) {
            var light = LauncherStyle.Palette.forDesign(colors, false);
            var dark = LauncherStyle.Palette.forDesign(colors, true);
            assertTrue(luminance(light.background()) > luminance(dark.background()));
            assertNotEquals(light.accent(), dark.accent());
        }
        assertNotEquals(LauncherStyle.Palette.forDesign(LauncherPreferences.Colors.SUNRISE, false),
                LauncherStyle.Palette.forDesign(LauncherPreferences.Colors.TWILIGHT, false));
    }

    @Test
    public void bodyTextAndControlsMeetFourPointFiveToOneContrastInAllPalettes() {
        for (LauncherPreferences.Colors colors : LauncherPreferences.Colors.values()) {
            for (boolean dark : new boolean[]{false, true}) {
                var palette = LauncherStyle.Palette.forDesign(colors, dark);
                checkContrast(palette.text(), palette.background());
                checkContrast(palette.text(), palette.surface());
                checkContrast(palette.muted(), palette.background());
                checkContrast(palette.muted(), palette.surface());
                checkContrast(palette.muted(), palette.heroStart());
                checkContrast(palette.muted(), palette.heroEnd());
                checkContrast(palette.text(), palette.heroStart());
                checkContrast(palette.text(), palette.heroEnd());
                checkContrast(palette.accent(), palette.background());
                checkContrast(palette.accent(), palette.surface());
                checkContrast(palette.accent(), palette.soft());
                checkContrast(palette.onAccent(), palette.accent());
            }
        }
    }

    private static void checkContrast(int ink, int background) {
        double a = luminance(ink);
        double b = luminance(background);
        double ratio = (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
        assertTrue(String.format("#%06x / #%06x = %.2f", ink & 0xffffff, background & 0xffffff, ratio),
                ratio >= 4.5);
    }

    private static double luminance(int rgb) {
        return 0.2126 * linear(rgb >> 16 & 255)
                + 0.7152 * linear(rgb >> 8 & 255) + 0.0722 * linear(rgb & 255);
    }

    private static double linear(int channel) {
        double normalized = channel / 255.0;
        return normalized <= 0.04045 ? normalized / 12.92 : Math.pow((normalized + 0.055) / 1.055, 2.4);
    }
}
