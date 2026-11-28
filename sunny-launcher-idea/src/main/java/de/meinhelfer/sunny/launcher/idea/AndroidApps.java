package de.meinhelfer.sunny.launcher.idea;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class AndroidApps implements LaunchGate.Apps {
    record Entry(AppTarget target, String label, Drawable icon) {}

    private final Activity activity;
    private final PackageManager packages;

    AndroidApps(Activity activity) {
        this.activity = activity;
        packages = activity.getPackageManager();
    }

    List<Entry> list(Locale locale) {
        var unique = new LinkedHashMap<AppTarget, Entry>();
        for (ResolveInfo match : query(launcherIntent())) {
            if (isOrdinary(match.activityInfo)) {
                AppTarget target = targetOf(match.activityInfo);
                if (!unique.containsKey(target)) {
                    unique.put(target, new Entry(target, match.loadLabel(packages).toString(),
                            match.loadIcon(packages)));
                }
            }
        }
        var entries = new ArrayList<>(unique.values());
        Collator collator = Collator.getInstance(locale);
        entries.sort(Comparator.comparing(Entry::label, collator)
                .thenComparing(entry -> entry.target().preferenceKey()));
        return entries;
    }

    @Override
    public boolean isLaunchable(AppTarget target) {
        Intent scoped = launcherIntent().setPackage(target.packageName());
        for (ResolveInfo match : query(scoped)) {
            if (isOrdinary(match.activityInfo) && target.equals(targetOf(match.activityInfo))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public LaunchGate.Result start(AppTarget target) {
        Intent intent = launcherIntent().setComponent(new ComponentName(
                target.packageName(), target.activityName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            activity.startActivity(intent);
            return LaunchGate.Result.OPENED;
        } catch (ActivityNotFoundException disappeared) {
            return LaunchGate.Result.UNAVAILABLE;
        }
    }

    private List<ResolveInfo> query(Intent intent) {
        return packages.queryIntentActivities(intent, 0);
    }

    private boolean isOrdinary(ActivityInfo info) {
        return info != null && info.applicationInfo != null && info.exported && info.enabled
                && info.applicationInfo.enabled && info.permission == null
                && (info.applicationInfo.flags & ApplicationInfo.FLAG_SUSPENDED) == 0
                && !activity.getPackageName().equals(info.packageName);
    }

    private static AppTarget targetOf(ActivityInfo info) {
        return new AppTarget(info.packageName, info.name);
    }

    private static Intent launcherIntent() {
        return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    }
}
