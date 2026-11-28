package de.meinhelfer.sunny.launcher.idea;

import java.util.HashSet;
import java.util.Set;

final class LauncherPreferences {
    enum Appearance {
        SYSTEM, LIGHT, DARK;

        static Appearance read(String value) {
            if (LIGHT.name().equals(value)) return LIGHT;
            if (DARK.name().equals(value)) return DARK;
            return SYSTEM;
        }

        boolean isDark(boolean deviceDark) {
            return this == DARK || (this == SYSTEM && deviceDark);
        }
    }

    enum Colors {
        TWILIGHT, SUNRISE;

        static Colors read(String value) {
            return SUNRISE.name().equals(value) ? SUNRISE : TWILIGHT;
        }
    }

    private LauncherPreferences() {}

    static Set<AppTarget> readFavorites(Set<String> stored) {
        Set<AppTarget> favorites = new HashSet<>();
        for (String value : stored) {
            AppTarget target = AppTarget.fromPreference(value);
            if (target != null) favorites.add(target);
        }
        return favorites;
    }

    static Set<String> saveFavorite(Set<String> stored, AppTarget target, boolean selected) {
        Set<String> updated = new HashSet<>(stored);
        if (selected) {
            updated.add(target.preferenceKey());
        } else {
            updated.remove(target.preferenceKey());
        }
        return updated;
    }
}
