package de.meinhelfer.sunny.launcher.idea;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private enum Screen {
        HOME(R.string.home), APPS(R.string.apps), DESIGN(R.string.design);

        final int label;

        Screen(int label) {
            this.label = label;
        }
    }

    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService catalogWorker = Executors.newSingleThreadExecutor();
    private final Map<AppTarget, AndroidApps.Entry> catalog = new LinkedHashMap<>();
    private final Runnable tick = this::refreshClock;
    private Screen screen = Screen.HOME;
    private LocalSettings settings;
    private AndroidApps androidApps;
    private LauncherStyle ui;
    private boolean foreground;
    private boolean catalogLoaded;
    private int catalogRequest;

    private LinearLayout root;
    private ScrollView scroll;
    private TextView clock;
    private TextView date;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        settings = new LocalSettings(this);
        androidApps = new AndroidApps(this);
        if (state != null) {
            screen = Screen.valueOf(state.getString("screen", Screen.HOME.name()));
        }
        renderScreen(0);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("screen", screen.name());
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        refreshClock();
        refreshCatalog();
    }

    @Override
    protected void onPause() {
        foreground = false;
        clockHandler.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        catalogWorker.shutdownNow();
        super.onDestroy();
    }

    private void refreshCatalog() {
        int request = ++catalogRequest;
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        catalogWorker.execute(() -> {
            var entries = Collections.<AndroidApps.Entry>emptyList();
            try {
                entries = androidApps.list(locale);
            } catch (SecurityException restrictedVisibility) {
                // The examples remain available when Android restricts the catalog.
            }
            var snapshot = entries;
            runOnUiThread(() -> {
                if (isDestroyed() || request != catalogRequest) return;
                catalog.clear();
                snapshot.forEach(entry -> catalog.put(entry.target(), entry));
                catalogLoaded = true;
                if (foreground && screen != Screen.DESIGN) renderPage(scroll.getScrollY());
            });
        });
    }

    private void refreshClock() {
        clockHandler.removeCallbacks(tick);
        if (clock == null) return;

        Date now = new Date();
        Locale locale = getResources().getConfiguration().getLocales().get(0);
        clock.setText(android.text.format.DateFormat.getTimeFormat(this).format(now));
        date.setText(android.icu.text.DateFormat.getInstanceForSkeleton("EEEEyMMMMd", locale).format(now));
        if (foreground) clockHandler.postDelayed(tick, 60_000 - now.getTime() % 60_000);
    }

    private void navigate(Screen destination) {
        if (screen == destination) return;
        screen = destination;
        renderScreen(0);
    }

    private void renderScreen(int scrollPosition) {
        boolean deviceDark = (getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        boolean dark = settings.appearance().isDark(deviceDark);
        ui = new LauncherStyle(this, LauncherStyle.Palette.forDesign(settings.colors(), dark));

        root = ui.stack();
        root.setBackgroundColor(ui.palette.background());
        root.addView(masthead(), new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        root.addView(scroll, new LinearLayout.LayoutParams(MATCH_PARENT, 0, 1));
        root.addView(navigation(), new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        configureSystemBars(getWindow(), dark);
        setContentView(root);
        root.requestApplyInsets();
        renderPage(scrollPosition);
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars(Window window, boolean dark) {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(dark ? 0 : mask, mask);
            }
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                var safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return insets;
            });
        } else {
            window.setStatusBarColor(ui.palette.background());
            window.setNavigationBarColor(ui.palette.background());
            window.getDecorView().setSystemUiVisibility(dark ? 0
                    : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }

    private LinearLayout masthead() {
        var header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ui.dp(18), ui.dp(7), ui.dp(18), ui.dp(8));
        var mark = new ImageView(this);
        mark.setImageResource(R.drawable.ic_sunny_launcher);
        mark.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        header.addView(mark, new LinearLayout.LayoutParams(ui.dp(40), ui.dp(40)));
        LinearLayout labels = ui.stack();
        TextView name = ui.heading(getText(R.string.app_name), 20);
        labels.addView(name);
        labels.addView(ui.text(getText(R.string.prototype), 13, ui.palette.accent()));
        var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        params.setMarginStart(ui.dp(10));
        header.addView(labels, params);
        return header;
    }

    private LinearLayout navigation() {
        var nav = new LinearLayout(this);
        nav.setBackgroundColor(ui.palette.background());
        nav.setPadding(ui.dp(12), ui.dp(5), ui.dp(12), ui.dp(6));
        for (Screen destination : Screen.values()) {
            boolean selected = screen == destination;
            Button tab = ui.button(getText(destination.label),
                    selected ? ui.palette.soft() : ui.palette.background(),
                    selected ? ui.palette.accent() : ui.palette.muted());
            tab.setSelected(selected);
            tab.setOnClickListener(view -> navigate(destination));
            var params = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
            params.setMarginStart(ui.dp(3));
            params.setMarginEnd(ui.dp(3));
            nav.addView(tab, params);
        }
        return nav;
    }

    private void renderPage(int scrollPosition) {
        scroll.removeAllViews();
        LinearLayout body = ui.stack();
        body.setPadding(ui.dp(16), ui.dp(7), ui.dp(16), ui.dp(16));
        scroll.addView(body, new ScrollView.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        clock = null;
        date = null;
        switch (screen) {
            case HOME -> showHome(body);
            case APPS -> showApps(body);
            case DESIGN -> showDesign(body);
        }
        refreshClock();
        ScrollView rendered = scroll;
        rendered.post(() -> rendered.scrollTo(0, scrollPosition));
    }

    private void showHome(LinearLayout body) {
        Configuration configuration = getResources().getConfiguration();
        int width = configuration.screenWidthDp;
        boolean split = width >= (configuration.fontScale > 1.3f ? 820 : 640);
        int portraitRoom = split ? (width - 50) / 2 : width - 32;
        LinearLayout hero = sunnyHero(Math.max(0, Math.min(272, portraitRoom - 38)));

        LinearLayout cards = ui.stack();
        cards.addView(favoritesCard());
        append(cards, assistantCard(), 12);
        if (split) {
            var columns = new LinearLayout(this);
            columns.addView(hero, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
            var secondColumn = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
            secondColumn.setMarginStart(ui.dp(14));
            columns.addView(cards, secondColumn);
            body.addView(columns);
        } else {
            body.addView(hero);
            append(body, cards, 12);
        }
    }

    private LinearLayout sunnyHero(int portraitSize) {
        LinearLayout hero = ui.stack();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(ui.dp(14), ui.dp(16), ui.dp(14), ui.dp(8));
        hero.setBackground(ui.hero());

        TextView title = ui.heading(getText(R.string.sunny), 21);
        title.setGravity(Gravity.CENTER);
        hero.addView(title, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        clock = ui.text("", 46, ui.palette.text());
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        hero.addView(clock, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
        date = ui.text("", 15, ui.palette.muted());
        date.setGravity(Gravity.CENTER);
        hero.addView(date, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        FrameLayout artwork = new FrameLayout(this);
        View halo = new View(this);
        halo.setBackground(ui.halo(portraitSize / 2));
        halo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        artwork.addView(halo, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        ImageView sunny = new ImageView(this);
        sunny.setImageResource(R.drawable.sunny_su08);
        sunny.setScaleType(ImageView.ScaleType.FIT_CENTER);
        sunny.setContentDescription(getText(R.string.sunny_portrait));
        artwork.addView(sunny, new FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
        var artworkSize = new LinearLayout.LayoutParams(ui.dp(portraitSize), ui.dp(portraitSize));
        artworkSize.topMargin = ui.dp(8);
        hero.addView(artwork, artworkSize);
        return hero;
    }

    private LinearLayout favoritesCard() {
        LinearLayout card = ui.card();
        card.addView(ui.heading(getText(R.string.favorites), 22));
        Set<AppTarget> favorites = settings.favorites();
        if (!catalogLoaded) {
            append(card, copy(R.string.apps_loading), 6);
        } else if (favorites.isEmpty()) {
            append(card, copy(R.string.favorites_intro), 6);
            Button browse = ui.button(getText(R.string.browse_apps), ui.palette.accent(),
                    ui.palette.onAccent());
            browse.setOnClickListener(view -> navigate(Screen.APPS));
            append(card, browse, 12);
        } else {
            for (AndroidApps.Entry entry : catalog.values()) {
                if (favorites.remove(entry.target())) append(card, appRow(entry, true), 8);
            }
            favorites.stream().sorted(Comparator.comparing(AppTarget::preferenceKey))
                    .forEach(target -> append(card, missingRow(target), 8));
        }
        return card;
    }

    private LinearLayout assistantCard() {
        LinearLayout card = ui.card();
        View contents = getLayoutInflater().inflate(R.layout.assistant_placeholder, card, false);
        ui.badge(contents.findViewById(R.id.assistant_status));
        ui.styleHeading(contents.findViewById(R.id.assistant_title), 21);
        ui.type(contents.findViewById(R.id.assistant_copy), 15, ui.palette.muted(), false);
        card.addView(contents);
        return card;
    }

    private void showApps(LinearLayout body) {
        body.addView(ui.heading(getText(R.string.apps), 30));
        append(body, copy(R.string.apps_intro), 5);
        if (!catalogLoaded) {
            append(body, copy(R.string.apps_loading), 16);
        } else if (catalog.isEmpty()) {
            LinearLayout examples = ui.card();
            examples.addView(ui.heading(getText(R.string.examples_title), 20));
            append(examples, copy(R.string.examples_intro), 6);
            append(examples, copy(R.string.example_camera), 12);
            append(examples, copy(R.string.example_gallery), 5);
            append(body, examples, 16);
        } else {
            Set<AppTarget> favorites = settings.favorites();
            for (AndroidApps.Entry entry : catalog.values()) {
                append(body, appRow(entry, favorites.contains(entry.target())), 8);
            }
        }
    }

    private LinearLayout appRow(AndroidApps.Entry app, boolean favorite) {
        LinearLayout row = appRowContainer();
        var launch = new LinearLayout(this);
        launch.setGravity(Gravity.CENTER_VERTICAL);
        launch.setMinimumHeight(ui.dp(52));
        launch.setPadding(ui.dp(9), ui.dp(5), ui.dp(8), ui.dp(5));
        launch.setBackground(ui.touch(ui.palette.surface(), 14));
        launch.setContentDescription(getString(R.string.open_app, app.label()));
        launch.setFocusable(true);
        launch.setOnClickListener(view -> openVisibleApp(app.target()));

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(app.icon());
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        launch.addView(icon, new LinearLayout.LayoutParams(ui.dp(36), ui.dp(36)));
        TextView label = ui.text(app.label(), 16, ui.palette.text());
        label.setMaxLines(3);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        var labelParams = new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1);
        labelParams.setMarginStart(ui.dp(9));
        launch.addView(label, labelParams);
        row.addView(launch, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        row.addView(favoriteButton(app.target(), app.label(), favorite));
        return row;
    }

    private LinearLayout missingRow(AppTarget target) {
        LinearLayout row = appRowContainer();
        LinearLayout details = ui.stack();
        details.setPadding(ui.dp(9), ui.dp(4), ui.dp(8), ui.dp(4));
        details.addView(ui.text(getText(R.string.unavailable), 15, ui.palette.text()));
        TextView identity = ui.text(target.packageName(), 13, ui.palette.muted());
        details.addView(identity);
        row.addView(details, new LinearLayout.LayoutParams(0, WRAP_CONTENT, 1));
        row.addView(favoriteButton(target, target.packageName(), true));
        return row;
    }

    private LinearLayout appRowContainer() {
        var row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3));
        row.setBackground(ui.shape(ui.palette.surface(), 17, ui.palette.outline()));
        return row;
    }

    private Button favoriteButton(AppTarget target, String label, boolean selected) {
        Button button = ui.button("", ui.palette.surface(), ui.palette.accent());
        button.setTextSize(23);
        updateFavoriteButton(button, label, selected);
        button.setOnClickListener(view -> {
            boolean nowSelected = !settings.favorites().contains(target);
            settings.setFavorite(target, nowSelected);
            Toast.makeText(this, getString(nowSelected ? R.string.favorite_added
                    : R.string.favorite_removed, label), Toast.LENGTH_SHORT).show();
            if (screen == Screen.HOME) {
                renderPage(scroll.getScrollY());
            } else {
                updateFavoriteButton(button, label, nowSelected);
            }
        });
        return button;
    }

    private void updateFavoriteButton(Button button, String label, boolean selected) {
        button.setText(selected ? "★" : "☆");
        button.setSelected(selected);
        button.setContentDescription(getString(
                selected ? R.string.remove_favorite : R.string.add_favorite, label));
    }

    private void openVisibleApp(AppTarget target) {
        LaunchGate.Result result = LaunchGate.openFromTap(target, foreground, catalog.keySet(), androidApps);
        if (result == LaunchGate.Result.OPENED) return;

        int message = result == LaunchGate.Result.UNAVAILABLE ? R.string.open_unavailable : R.string.open_blocked;
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        refreshCatalog();
    }

    private void showDesign(LinearLayout body) {
        body.addView(ui.heading(getText(R.string.design), 30));
        LinearLayout appearance = ui.card();
        appearance.addView(ui.heading(getText(R.string.appearance), 22));
        appearance.addView(choices(LauncherPreferences.Appearance.values(), settings.appearance(),
                new int[]{R.string.appearance_system, R.string.appearance_light, R.string.appearance_dark},
                settings::setAppearance));
        append(body, appearance, 12);

        LinearLayout colors = ui.card();
        colors.addView(ui.heading(getText(R.string.colors), 22));
        colors.addView(choices(LauncherPreferences.Colors.values(), settings.colors(),
                new int[]{R.string.palette_twilight, R.string.palette_sunrise}, settings::setColors));
        append(body, colors, 12);
    }

    private <T> RadioGroup choices(T[] options, T current, int[] labels, Consumer<T> onChange) {
        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < options.length; i++) {
            T option = options[i];
            RadioButton button = new RadioButton(this);
            button.setId(View.generateViewId());
            button.setText(labels[i]);
            button.setTag(option);
            button.setMinHeight(ui.dp(52));
            button.setPaddingRelative(ui.dp(4), ui.dp(4), ui.dp(8), ui.dp(4));
            button.setButtonTintList(ColorStateList.valueOf(ui.palette.accent()));
            button.setBackground(ui.touch(ui.palette.surface(), 12));
            ui.type(button, 16, ui.palette.text(), false);
            group.addView(button, new RadioGroup.LayoutParams(MATCH_PARENT, WRAP_CONTENT));
            if (option.equals(current)) group.check(button.getId());
            button.setOnClickListener(view -> {
                if (option.equals(current)) return;
                boolean wasFocused = view.isFocused();
                onChange.accept(option);
                renderScreen(scroll.getScrollY());
                if (wasFocused) root.findViewWithTag(option).requestFocus();
            });
        }
        return group;
    }

    private TextView copy(int message) {
        return ui.text(getText(message), 15, ui.palette.muted());
    }

    private void append(LinearLayout container, View child, int topMargin) {
        var params = new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT);
        params.topMargin = ui.dp(topMargin);
        container.addView(child, params);
    }
}
