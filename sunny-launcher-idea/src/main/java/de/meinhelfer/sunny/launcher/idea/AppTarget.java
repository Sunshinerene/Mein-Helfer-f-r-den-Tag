package de.meinhelfer.sunny.launcher.idea;

record AppTarget(String packageName, String activityName) {
    static AppTarget fromPreference(String value) {
        int separator = value.indexOf('/');
        if (separator <= 0 || separator == value.length() - 1
                || separator != value.lastIndexOf('/')) {
            return null;
        }
        return new AppTarget(value.substring(0, separator), value.substring(separator + 1));
    }

    String preferenceKey() {
        return packageName + "/" + activityName;
    }
}
