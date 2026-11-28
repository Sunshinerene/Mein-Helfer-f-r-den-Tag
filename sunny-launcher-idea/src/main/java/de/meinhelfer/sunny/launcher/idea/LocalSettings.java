package de.meinhelfer.sunny.launcher.idea;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Collections;
import java.util.Set;

final class LocalSettings {
    private static final String FAVORITES = "favorites";
    private static final String APPEARANCE = "appearance";
    private static final String COLORS = "colors";

    private final SharedPreferences file;

    LocalSettings(Context context) {
        file = context.getSharedPreferences("sunny_launcher", Context.MODE_PRIVATE);
    }

    Set<AppTarget> favorites() {
        return LauncherPreferences.readFavorites(storedFavorites());
    }

    void setFavorite(AppTarget target, boolean selected) {
        file.edit().putStringSet(FAVORITES,
                LauncherPreferences.saveFavorite(storedFavorites(), target, selected)).apply();
    }

    LauncherPreferences.Appearance appearance() {
        return LauncherPreferences.Appearance.read(file.getString(APPEARANCE, null));
    }

    void setAppearance(LauncherPreferences.Appearance appearance) {
        file.edit().putString(APPEARANCE, appearance.name()).apply();
    }

    LauncherPreferences.Colors colors() {
        return LauncherPreferences.Colors.read(file.getString(COLORS, null));
    }

    void setColors(LauncherPreferences.Colors colors) {
        file.edit().putString(COLORS, colors.name()).apply();
    }

    private Set<String> storedFavorites() {
        return file.getStringSet(FAVORITES, Collections.emptySet());
    }
}
