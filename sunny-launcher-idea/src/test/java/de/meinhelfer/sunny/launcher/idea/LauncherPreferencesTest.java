package de.meinhelfer.sunny.launcher.idea;

import org.junit.Test;

import java.util.Set;

import static de.meinhelfer.sunny.launcher.idea.LauncherPreferences.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LauncherPreferencesTest {
    private static final AppTarget CAMERA = new AppTarget("fixture.camera", "fixture.camera.Launch");
    private static final AppTarget SECOND_CAMERA =
            new AppTarget("fixture.camera", "fixture.camera.OtherLaunch");
    private static final AppTarget GALLERY = new AppTarget("fixture.gallery", "fixture.gallery.Launch");

    @Test
    public void favoriteRoundTripPersistsOnlyTheComponentIdentifier() {
        Set<String> saved = saveFavorite(Set.of(), CAMERA, true);
        assertEquals(Set.of("fixture.camera/fixture.camera.Launch"), saved);
        assertEquals(Set.of(CAMERA), readFavorites(saved));
    }

    @Test
    public void addIsIdempotentAndPreservesExistingFavorites() {
        Set<String> original = Set.of(GALLERY.preferenceKey());
        Set<String> once = saveFavorite(original, CAMERA, true);
        assertEquals(Set.of(CAMERA, GALLERY), readFavorites(once));
        assertEquals(once, saveFavorite(once, CAMERA, true));
        assertEquals(Set.of(GALLERY.preferenceKey()), original);
    }

    @Test
    public void removingOneActivityPreservesOtherActivitiesInSamePackage() {
        Set<String> original = Set.of(CAMERA.preferenceKey(), SECOND_CAMERA.preferenceKey());
        Set<String> saved = saveFavorite(original, CAMERA, false);
        assertEquals(Set.of(SECOND_CAMERA), readFavorites(saved));
    }

    @Test
    public void previouslySelectedMissingAppRemainsRemovable() {
        Set<String> stored = Set.of(GALLERY.preferenceKey());
        assertTrue(readFavorites(stored).contains(GALLERY));
        assertTrue(readFavorites(saveFavorite(stored, GALLERY, false)).isEmpty());
    }

    @Test
    public void malformedStoredIdsAreIgnored() {
        assertEquals(Set.of(CAMERA), readFavorites(Set.of(
                CAMERA.preferenceKey(), "", "packageOnly", "name/", "/activity", "pkg/class/extra")));
    }

    @Test
    public void missingOrUnknownAppearanceDefaultsToDeviceMode() {
        assertEquals(Appearance.SYSTEM, Appearance.read(null));
        assertEquals(Appearance.SYSTEM, Appearance.read("unsupported"));
        assertTrue(Appearance.SYSTEM.isDark(true));
        assertFalse(Appearance.SYSTEM.isDark(false));
    }

    @Test
    public void explicitAppearanceSurvivesRoundTripAndOverridesDeviceSetting() {
        assertFalse(Appearance.read(Appearance.LIGHT.name()).isDark(true));
        assertTrue(Appearance.read(Appearance.DARK.name()).isDark(false));
    }

    @Test
    public void paletteRoundTripAndFallbackAreDeterministic() {
        assertEquals(Colors.SUNRISE, Colors.read(Colors.SUNRISE.name()));
        assertEquals(Colors.TWILIGHT, Colors.read(Colors.TWILIGHT.name()));
        assertEquals(Colors.TWILIGHT, Colors.read(null));
        assertEquals(Colors.TWILIGHT, Colors.read("unsupported"));
    }
}
