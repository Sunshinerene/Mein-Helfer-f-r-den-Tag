package de.meinhelfer.sunny.launcher.idea;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class LauncherStyle {
    record Palette(int background, int surface, int outline, int text, int muted, int accent,
                   int onAccent, int soft, int heroStart, int heroEnd, int halo) {
        static Palette forDesign(LauncherPreferences.Colors colors, boolean dark) {
            if (colors == LauncherPreferences.Colors.SUNRISE) {
                return dark
                        ? fromHex(0x1C1315, 0x2A1D20, 0x64454D, 0xFFEFE8, 0xDCBFB6,
                        0xFFB9A4, 0x4B2019, 0x482B2D, 0x462833, 0x291921, 0x9C534D)
                        : fromHex(0xFFF6EE, 0xFFFCF8, 0xECD7C6, 0x392318, 0x664C3E,
                        0x943D2D, 0xFFFFFF, 0xFFE4D4, 0xFFEBD9, 0xFAD6C2, 0xF5AA9E);
            }
            return dark
                    ? fromHex(0x100D1A, 0x1E162B, 0x55456C, 0xF7F1FF, 0xC8B9DC,
                    0xD9BEFF, 0x24123C, 0x34244D, 0x302048, 0x1B1838, 0x7652BA)
                    : fromHex(0xF7F3FD, 0xFFFFFF, 0xDDD3EE, 0x24163B, 0x5B4B72,
                    0x5E3B91, 0xFFFFFF, 0xECE2FB, 0xF1E7FF, 0xE1D8FC, 0xBFA6F7);
        }

        private static Palette fromHex(int background, int surface, int outline, int text,
                                       int muted, int accent, int onAccent, int soft,
                                       int heroStart, int heroEnd, int halo) {
            return new Palette(opaque(background), opaque(surface), opaque(outline), opaque(text),
                    opaque(muted), opaque(accent), opaque(onAccent), opaque(soft), opaque(heroStart),
                    opaque(heroEnd), opaque(halo));
        }

        private static int opaque(int rgb) {
            return rgb | 0xFF000000;
        }
    }

    private final Context context;
    final Palette palette;

    LauncherStyle(Context context, Palette palette) {
        this.context = context;
        this.palette = palette;
    }

    TextView text(CharSequence label, int size, int color) {
        TextView view = new TextView(context);
        view.setText(label);
        type(view, size, color, false);
        return view;
    }

    TextView heading(CharSequence label, int size) {
        TextView view = new TextView(context);
        view.setText(label);
        styleHeading(view, size);
        return view;
    }

    void styleHeading(TextView view, int size) {
        type(view, size, palette.text(), true);
        if (Build.VERSION.SDK_INT >= 28) view.setAccessibilityHeading(true);
    }

    void type(TextView view, int size, int color, boolean emphasized) {
        view.setTextSize(size);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(emphasized ? "sans-serif-medium" : "sans-serif",
                Typeface.NORMAL));
    }

    void badge(TextView view) {
        type(view, 13, palette.accent(), true);
        view.setBackground(shape(palette.soft(), 16, Color.TRANSPARENT));
        view.setPadding(dp(10), dp(3), dp(10), dp(3));
    }

    Button button(CharSequence label, int fill, int ink) {
        Button view = new Button(context);
        view.setText(label);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setMinimumHeight(dp(48));
        view.setMinWidth(dp(48));
        view.setMinimumWidth(dp(48));
        view.setPadding(dp(10), dp(5), dp(10), dp(5));
        view.setGravity(Gravity.CENTER);
        type(view, 15, ink, true);
        view.setStateListAnimator(null);
        view.setBackground(touch(fill, 16));
        return view;
    }

    RippleDrawable touch(int fill, int radius) {
        var states = new StateListDrawable();
        states.addState(new int[]{android.R.attr.state_focused}, shape(fill, radius, palette.accent()));
        states.addState(new int[0], shape(fill, radius, Color.TRANSPARENT));
        int ripple = (palette.accent() & 0x00FFFFFF) | 0x33000000;
        return new RippleDrawable(ColorStateList.valueOf(ripple), states,
                shape(Color.WHITE, radius, Color.TRANSPARENT));
    }

    LinearLayout stack() {
        var view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout card() {
        LinearLayout view = stack();
        view.setBackground(shape(palette.surface(), 24, palette.outline()));
        view.setPadding(dp(18), dp(16), dp(18), dp(16));
        return view;
    }

    GradientDrawable hero() {
        var gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{palette.heroStart(), palette.heroEnd()});
        gradient.setCornerRadius(dp(30));
        gradient.setStroke(dp(1), palette.outline());
        return gradient;
    }

    GradientDrawable halo(int radius) {
        var halo = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{palette.halo(), palette.halo() & 0x00FFFFFF});
        halo.setShape(GradientDrawable.OVAL);
        halo.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        halo.setGradientRadius(dp(radius));
        return halo;
    }

    GradientDrawable shape(int fill, int radius, int stroke) {
        var shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) shape.setStroke(dp(1), stroke);
        return shape;
    }

    int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
