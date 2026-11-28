package de.meinhelfer.sunny.launcher.idea;

import java.util.Set;

final class LaunchGate {
    enum Result { OPENED, UNAVAILABLE, BLOCKED }

    interface Apps {
        boolean isLaunchable(AppTarget target);
        Result start(AppTarget target);
    }

    private LaunchGate() {}

    static Result openFromTap(AppTarget target, boolean foreground, Set<AppTarget> visible, Apps apps) {
        if (!foreground || target == null || !visible.contains(target)) {
            return Result.UNAVAILABLE;
        }
        try {
            return apps.isLaunchable(target) ? apps.start(target) : Result.UNAVAILABLE;
        } catch (SecurityException denied) {
            return Result.BLOCKED;
        }
    }
}
