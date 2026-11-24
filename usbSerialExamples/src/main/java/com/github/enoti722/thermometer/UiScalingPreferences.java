package com.github.enoti722.thermometer;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Масштаб шрифтов для оверлея и overlay-окна событий (не влияет на шрифты основного приложения).
 */
public final class UiScalingPreferences {

    static final String PREFS_NAME = "ui_scaling_prefs";
    /** float, множитель: 1.0 = 100% */
    public static final String KEY_OVERLAY_FONT_SCALE = "overlay_font_scale";
    public static final String KEY_ALERT_FONT_SCALE = "alert_font_scale";
    /** int, % от размера содержимого: смещение содержимого (значок и тексты) внутри плашки оверлея. */
    public static final String KEY_OVERLAY_CONTENT_OFFSET_X = "overlay_content_offset_x";
    public static final String KEY_OVERLAY_CONTENT_OFFSET_Y = "overlay_content_offset_y";
    /** int, % от текущего размера: ширина/высота плашки (фона) оверлея. */
    public static final String KEY_OVERLAY_PLATE_WIDTH_PERCENT = "overlay_plate_width_percent";
    public static final String KEY_OVERLAY_PLATE_HEIGHT_PERCENT = "overlay_plate_height_percent";

    /** Базовые размеры (sp до масштабирования), совпадают с layout. */
    public static final float OVERLAY_PRIMARY_SP = 16f;

    public static final float ALERT_TITLE_SP = 16f;
    public static final float ALERT_TIMER_SP = 12f;
    public static final float ALERT_BODY_SP = 12f;
    /** Кнопка «Ок» в окнах с обязательным подтверждением. */
    public static final float ALERT_BUTTON_SP = 14f;

    public static final int SCALE_SEEK_MIN = 50;
    public static final int SCALE_SEEK_MAX = 200;

    private UiScalingPreferences() {}

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static float overlayFontScale(Context context) {
        return clampScale(prefs(context).getFloat(KEY_OVERLAY_FONT_SCALE, 1f));
    }

    public static float alertFontScale(Context context) {
        return clampScale(prefs(context).getFloat(KEY_ALERT_FONT_SCALE, 1f));
    }

    /** Общее смещение содержимого внутри плашки по горизонтали, % от ширины содержимого. */
    public static int overlayContentOffsetXPercent(Context context) {
        return AppSettings.clampOverlayContentOffsetPercent(
                prefs(context).getInt(KEY_OVERLAY_CONTENT_OFFSET_X, 0));
    }

    /** Общее смещение содержимого внутри плашки по вертикали, % от высоты содержимого. */
    public static int overlayContentOffsetYPercent(Context context) {
        return AppSettings.clampOverlayContentOffsetPercent(
                prefs(context).getInt(KEY_OVERLAY_CONTENT_OFFSET_Y, 0));
    }

    /** Общая ширина плашки (фона) оверлея, % от текущей. */
    public static int overlayPlateWidthPercent(Context context) {
        return AppSettings.clampOverlayPlateSizePercent(
                prefs(context).getInt(KEY_OVERLAY_PLATE_WIDTH_PERCENT, AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT));
    }

    /** Общая высота плашки (фона) оверлея, % от текущей. */
    public static int overlayPlateHeightPercent(Context context) {
        return AppSettings.clampOverlayPlateSizePercent(
                prefs(context).getInt(KEY_OVERLAY_PLATE_HEIGHT_PERCENT, AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT));
    }

    public static int overlaySeekProgress(Context context) {
        return scaleToSeekProgress(overlayFontScale(context));
    }

    public static int alertSeekProgress(Context context) {
        return scaleToSeekProgress(alertFontScale(context));
    }

    public static int scaleToSeekProgress(float scale) {
        int p = Math.round(clampScale(scale) * 100f);
        return Math.max(SCALE_SEEK_MIN, Math.min(SCALE_SEEK_MAX, p));
    }

    /** progress SeekBar из диапазона {@link #SCALE_SEEK_MIN} … {@link #SCALE_SEEK_MAX}. */
    public static float seekProgressToScale(int progress) {
        return clampScale(progress / 100f);
    }

    static float clampScale(float s) {
        return Math.max(SCALE_SEEK_MIN / 100f, Math.min(SCALE_SEEK_MAX / 100f, s));
    }
}
