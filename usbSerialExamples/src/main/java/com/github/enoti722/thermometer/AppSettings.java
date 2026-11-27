package com.github.enoti722.thermometer;

/**
 * Настройки для конкретного приложения
 */
public class AppSettings {
    public static final String SYSTEM_PACKAGE_NAME = "__system_home__";
    public static final String SYSTEM_APP_NAME = "Система";
    /** Допустимый диапазон позиции оверлея по горизонтали в процентах (ползунок и сохранённое значение). */
    public static final int OVERLAY_POSITION_X_MIN_PERCENT = -5;
    public static final int OVERLAY_POSITION_X_MAX_PERCENT = 105;
    /** Допустимый диапазон позиции оверлея по вертикали в процентах. */
    public static final int OVERLAY_POSITION_Y_MIN_PERCENT = -5;
    public static final int OVERLAY_POSITION_Y_MAX_PERCENT = 105;
    /** SeekBar: progress от 0 до *_SEEK_MAX включительно → см. {@link #percentXFromSeekProgress}/{@link #percentYFromSeekProgress}. */
    public static final int OVERLAY_POSITION_X_SEEK_MAX =
            OVERLAY_POSITION_X_MAX_PERCENT - OVERLAY_POSITION_X_MIN_PERCENT;
    public static final int OVERLAY_POSITION_Y_SEEK_MAX =
            OVERLAY_POSITION_Y_MAX_PERCENT - OVERLAY_POSITION_Y_MIN_PERCENT;
    public static final int OVERLAY_ALPHA_MIN = 0;
    public static final int OVERLAY_ALPHA_MAX = 255;
    public static final int DEFAULT_OVERLAY_ALPHA_TOP = 0xD9;
    public static final int DEFAULT_OVERLAY_ALPHA_BOTTOM = 0xF0;
    /**
     * Масштаб отображаемых данных оверлея для приложения: 1.0 — размер как сейчас.
     * Итоговый размер получается умножением на общий масштаб оверлея из настроек системы.
     */
    public static final float OVERLAY_DATA_SIZE_SCALE_MIN = 0.25f;
    public static final float OVERLAY_DATA_SIZE_SCALE_MAX = 4.0f;
    public static final float DEFAULT_OVERLAY_DATA_SIZE_SCALE = 1.0f;
    /** Шаг ползунка масштаба данных; progress 5 → x0.25, 20 → x1.0, 80 → x4.0. */
    public static final float OVERLAY_DATA_SIZE_SEEK_STEP = 0.05f;
    public static final int OVERLAY_DATA_SIZE_SEEK_MAX =
            Math.round(OVERLAY_DATA_SIZE_SCALE_MAX / OVERLAY_DATA_SIZE_SEEK_STEP);
    /**
     * Смещение содержимого (значок и тексты) внутри плашки: проценты от размера самого содержимого.
     * -50% по горизонтали — сдвиг влево на половину ширины содержимого; 0 — по центру (как раньше).
     */
    public static final int OVERLAY_CONTENT_OFFSET_MIN_PERCENT = -100;
    public static final int OVERLAY_CONTENT_OFFSET_MAX_PERCENT = 100;
    public static final int OVERLAY_CONTENT_OFFSET_SEEK_STEP = 5;
    public static final int OVERLAY_CONTENT_OFFSET_SEEK_MAX =
            (OVERLAY_CONTENT_OFFSET_MAX_PERCENT - OVERLAY_CONTENT_OFFSET_MIN_PERCENT)
                    / OVERLAY_CONTENT_OFFSET_SEEK_STEP;
    /** Размер плашки (фона) оверлея: проценты от текущего размера (100% — как сейчас). */
    public static final int OVERLAY_PLATE_SIZE_MIN_PERCENT = 50;
    public static final int OVERLAY_PLATE_SIZE_MAX_PERCENT = 300;
    public static final int DEFAULT_OVERLAY_PLATE_SIZE_PERCENT = 100;
    public static final int OVERLAY_PLATE_SIZE_SEEK_STEP = 5;
    public static final int OVERLAY_PLATE_SIZE_SEEK_MAX =
            (OVERLAY_PLATE_SIZE_MAX_PERCENT - OVERLAY_PLATE_SIZE_MIN_PERCENT)
                    / OVERLAY_PLATE_SIZE_SEEK_STEP;

    private String packageName;
    private String appName;
    private boolean overlayEnabled;
    private boolean notificationsEnabled;
    private boolean overlaySpeedVisible;
    private int overlayAlphaTop;
    private int overlayAlphaBottom;
    private int overlayPositionXPercent;
    private int overlayPositionYPercent;
    /** Данные оверлея вертикально в несколько строк: значок, температура, скорость. */
    private boolean overlayVertical;
    /** Масштаб отображаемых данных оверлея (x0.25 … x4.0). */
    private float overlayDataSizeScale;
    /** Смещение содержимого (значок и тексты) внутри плашки по горизонтали, % от ширины содержимого. */
    private int overlayContentOffsetXPercent;
    /** Смещение содержимого внутри плашки по вертикали, % от высоты содержимого. */
    private int overlayContentOffsetYPercent;
    /** Ширина плашки (фона) оверлея, % от текущей. */
    private int overlayPlateWidthPercent;
    /** Высота плашки (фона) оверлея, % от текущей. */
    private int overlayPlateHeightPercent;
    private boolean isSelected; // для режима редактирования

    public AppSettings(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
        this.overlayEnabled = true; // по умолчанию включено
        this.notificationsEnabled = true; // по умолчанию включено
        this.overlaySpeedVisible = false; // по умолчанию выключено
        this.overlayAlphaTop = DEFAULT_OVERLAY_ALPHA_TOP;
        this.overlayAlphaBottom = DEFAULT_OVERLAY_ALPHA_BOTTOM;
        this.overlayPositionXPercent = clampOverlayPositionXPercent(50);
        this.overlayPositionYPercent = clampOverlayPositionYPercent(5);
        this.overlayVertical = false; // по умолчанию данные в одну строку
        this.overlayDataSizeScale = DEFAULT_OVERLAY_DATA_SIZE_SCALE;
        this.overlayContentOffsetXPercent = 0; // содержимое по центру плашки
        this.overlayContentOffsetYPercent = 0;
        this.overlayPlateWidthPercent = DEFAULT_OVERLAY_PLATE_SIZE_PERCENT;
        this.overlayPlateHeightPercent = DEFAULT_OVERLAY_PLATE_SIZE_PERCENT;
        this.isSelected = false;
    }

    public AppSettings(String packageName, String appName, boolean overlayEnabled, boolean notificationsEnabled,
                      int overlayPositionXPercent, int overlayPositionYPercent) {
        this(packageName, appName, overlayEnabled, notificationsEnabled, false,
                DEFAULT_OVERLAY_ALPHA_TOP, DEFAULT_OVERLAY_ALPHA_BOTTOM, overlayPositionXPercent, overlayPositionYPercent);
    }

    public AppSettings(String packageName, String appName, boolean overlayEnabled, boolean notificationsEnabled,
                      boolean overlaySpeedVisible, int overlayPositionXPercent, int overlayPositionYPercent) {
        this(packageName, appName, overlayEnabled, notificationsEnabled, overlaySpeedVisible,
                DEFAULT_OVERLAY_ALPHA_TOP, DEFAULT_OVERLAY_ALPHA_BOTTOM, overlayPositionXPercent, overlayPositionYPercent);
    }

    public AppSettings(String packageName, String appName, boolean overlayEnabled, boolean notificationsEnabled,
                      boolean overlaySpeedVisible, int overlayAlphaTop, int overlayAlphaBottom,
                      int overlayPositionXPercent, int overlayPositionYPercent) {
        this(packageName, appName, overlayEnabled, notificationsEnabled, overlaySpeedVisible,
                overlayAlphaTop, overlayAlphaBottom, overlayPositionXPercent, overlayPositionYPercent,
                false, DEFAULT_OVERLAY_DATA_SIZE_SCALE);
    }

    public AppSettings(String packageName, String appName, boolean overlayEnabled, boolean notificationsEnabled,
                      boolean overlaySpeedVisible, int overlayAlphaTop, int overlayAlphaBottom,
                      int overlayPositionXPercent, int overlayPositionYPercent,
                      boolean overlayVertical, float overlayDataSizeScale) {
        this.packageName = packageName;
        this.appName = appName;
        this.overlayEnabled = overlayEnabled;
        this.notificationsEnabled = notificationsEnabled;
        this.overlaySpeedVisible = overlaySpeedVisible;
        this.overlayAlphaTop = clampOverlayAlpha(overlayAlphaTop);
        this.overlayAlphaBottom = clampOverlayAlpha(overlayAlphaBottom);
        this.overlayPositionXPercent = clampOverlayPositionXPercent(overlayPositionXPercent);
        this.overlayPositionYPercent = clampOverlayPositionYPercent(overlayPositionYPercent);
        this.overlayVertical = overlayVertical;
        this.overlayDataSizeScale = clampOverlayDataSizeScale(overlayDataSizeScale);
        this.overlayContentOffsetXPercent = 0;
        this.overlayContentOffsetYPercent = 0;
        this.overlayPlateWidthPercent = DEFAULT_OVERLAY_PLATE_SIZE_PERCENT;
        this.overlayPlateHeightPercent = DEFAULT_OVERLAY_PLATE_SIZE_PERCENT;
        this.isSelected = false;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public boolean isOverlayEnabled() {
        return overlayEnabled;
    }

    public void setOverlayEnabled(boolean overlayEnabled) {
        this.overlayEnabled = overlayEnabled;
    }

    public boolean isNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean isOverlaySpeedVisible() {
        return overlaySpeedVisible;
    }

    public void setOverlaySpeedVisible(boolean overlaySpeedVisible) {
        this.overlaySpeedVisible = overlaySpeedVisible;
    }

    public int getOverlayAlphaTop() {
        return overlayAlphaTop;
    }

    public void setOverlayAlphaTop(int overlayAlphaTop) {
        this.overlayAlphaTop = clampOverlayAlpha(overlayAlphaTop);
    }

    public int getOverlayAlphaBottom() {
        return overlayAlphaBottom;
    }

    public void setOverlayAlphaBottom(int overlayAlphaBottom) {
        this.overlayAlphaBottom = clampOverlayAlpha(overlayAlphaBottom);
    }

    public int getOverlayPositionXPercent() {
        return overlayPositionXPercent;
    }

    public int getOverlayPositionYPercent() {
        return overlayPositionYPercent;
    }

    public boolean isOverlayVertical() {
        return overlayVertical;
    }

    public void setOverlayVertical(boolean overlayVertical) {
        this.overlayVertical = overlayVertical;
    }

    public float getOverlayDataSizeScale() {
        return overlayDataSizeScale;
    }

    public void setOverlayDataSizeScale(float overlayDataSizeScale) {
        this.overlayDataSizeScale = clampOverlayDataSizeScale(overlayDataSizeScale);
    }

    public int getOverlayContentOffsetXPercent() {
        return overlayContentOffsetXPercent;
    }

    public void setOverlayContentOffsetXPercent(int overlayContentOffsetXPercent) {
        this.overlayContentOffsetXPercent = clampOverlayContentOffsetPercent(overlayContentOffsetXPercent);
    }

    public int getOverlayContentOffsetYPercent() {
        return overlayContentOffsetYPercent;
    }

    public void setOverlayContentOffsetYPercent(int overlayContentOffsetYPercent) {
        this.overlayContentOffsetYPercent = clampOverlayContentOffsetPercent(overlayContentOffsetYPercent);
    }

    public int getOverlayPlateWidthPercent() {
        return overlayPlateWidthPercent;
    }

    public void setOverlayPlateWidthPercent(int overlayPlateWidthPercent) {
        this.overlayPlateWidthPercent = clampOverlayPlateSizePercent(overlayPlateWidthPercent);
    }

    public int getOverlayPlateHeightPercent() {
        return overlayPlateHeightPercent;
    }

    public void setOverlayPlateHeightPercent(int overlayPlateHeightPercent) {
        this.overlayPlateHeightPercent = clampOverlayPlateSizePercent(overlayPlateHeightPercent);
    }

    public static int clampOverlayPositionXPercent(int value) {
        return Math.max(OVERLAY_POSITION_X_MIN_PERCENT,
                Math.min(OVERLAY_POSITION_X_MAX_PERCENT, value));
    }

    public static int clampOverlayPositionYPercent(int value) {
        return Math.max(OVERLAY_POSITION_Y_MIN_PERCENT,
                Math.min(OVERLAY_POSITION_Y_MAX_PERCENT, value));
    }

    public static int clampOverlayAlpha(int value) {
        return Math.max(OVERLAY_ALPHA_MIN,
                Math.min(OVERLAY_ALPHA_MAX, value));
    }

    public static float clampOverlayDataSizeScale(float value) {
        return Math.max(OVERLAY_DATA_SIZE_SCALE_MIN,
                Math.min(OVERLAY_DATA_SIZE_SCALE_MAX, value));
    }

    public static int clampOverlayContentOffsetPercent(int value) {
        return Math.max(OVERLAY_CONTENT_OFFSET_MIN_PERCENT,
                Math.min(OVERLAY_CONTENT_OFFSET_MAX_PERCENT, value));
    }

    public static int clampOverlayPlateSizePercent(int value) {
        return Math.max(OVERLAY_PLATE_SIZE_MIN_PERCENT,
                Math.min(OVERLAY_PLATE_SIZE_MAX_PERCENT, value));
    }

    /** Значение процентов по горизонтали из позиции SeekBar (0 … OVERLAY_POSITION_X_SEEK_MAX). */
    public static int percentXFromSeekProgress(int seekProgress) {
        int percent = seekProgress + OVERLAY_POSITION_X_MIN_PERCENT;
        return clampOverlayPositionXPercent(percent);
    }

    /** Позиция SeekBar для сохранённого процента по горизонтали. */
    public static int seekXProgressFromPercent(int percent) {
        return clampOverlayPositionXPercent(percent) - OVERLAY_POSITION_X_MIN_PERCENT;
    }

    /** Значение процентов по вертикали из позиции SeekBar (0 … OVERLAY_POSITION_Y_SEEK_MAX). */
    public static int percentYFromSeekProgress(int seekProgress) {
        int percent = seekProgress + OVERLAY_POSITION_Y_MIN_PERCENT;
        return clampOverlayPositionYPercent(percent);
    }

    /** Позиция SeekBar для сохранённого процента по вертикали. */
    public static int seekYProgressFromPercent(int percent) {
        return clampOverlayPositionYPercent(percent) - OVERLAY_POSITION_Y_MIN_PERCENT;
    }

    /** Значение масштаба данных из позиции ползунка (0 … OVERLAY_DATA_SIZE_SEEK_MAX). */
    public static float dataSizeScaleFromSeekProgress(int seekProgress) {
        return clampOverlayDataSizeScale(seekProgress * OVERLAY_DATA_SIZE_SEEK_STEP);
    }

    /** Позиция ползунка для сохранённого масштаба данных. */
    public static int dataSizeSeekProgressFromScale(float scale) {
        float clamped = clampOverlayDataSizeScale(scale);
        return Math.round(clamped / OVERLAY_DATA_SIZE_SEEK_STEP);
    }

    /** Значение смещения содержимого из позиции ползунка (0 … OVERLAY_CONTENT_OFFSET_SEEK_MAX). */
    public static int overlayContentOffsetFromSeekProgress(int seekProgress) {
        int percent = (seekProgress - OVERLAY_CONTENT_OFFSET_SEEK_MAX / 2) * OVERLAY_CONTENT_OFFSET_SEEK_STEP;
        return clampOverlayContentOffsetPercent(percent);
    }

    /** Позиция ползунка для сохранённого смещения содержимого. */
    public static int overlayContentOffsetSeekProgressFromPercent(int percent) {
        return clampOverlayContentOffsetPercent(percent) / OVERLAY_CONTENT_OFFSET_SEEK_STEP
                + OVERLAY_CONTENT_OFFSET_SEEK_MAX / 2;
    }

    /** Значение размера плашки из позиции ползунка (0 … OVERLAY_PLATE_SIZE_SEEK_MAX). */
    public static int overlayPlateSizeFromSeekProgress(int seekProgress) {
        return clampOverlayPlateSizePercent(
                OVERLAY_PLATE_SIZE_MIN_PERCENT + seekProgress * OVERLAY_PLATE_SIZE_SEEK_STEP);
    }

    /** Позиция ползунка для сохранённого размера плашки. */
    public static int overlayPlateSizeSeekProgressFromPercent(int percent) {
        return (clampOverlayPlateSizePercent(percent) - OVERLAY_PLATE_SIZE_MIN_PERCENT)
                / OVERLAY_PLATE_SIZE_SEEK_STEP;
    }

    public void setOverlayPositionXPercent(int overlayPositionXPercent) {
        this.overlayPositionXPercent = clampOverlayPositionXPercent(overlayPositionXPercent);
    }

    public void setOverlayPositionYPercent(int overlayPositionYPercent) {
        this.overlayPositionYPercent = clampOverlayPositionYPercent(overlayPositionYPercent);
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
