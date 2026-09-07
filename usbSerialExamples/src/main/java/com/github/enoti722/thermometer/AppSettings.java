package com.github.enoti722.thermometer;

/**
 * Настройки для конкретного приложения
 */
public class AppSettings {
    public static final String SYSTEM_PACKAGE_NAME = "__system_home__";
    public static final String SYSTEM_APP_NAME = "Система";
    /** Допустимый диапазон позиции оверлея в процентах (ползунок и сохранённое значение). */
    public static final int OVERLAY_POSITION_MIN_PERCENT = -5;
    public static final int OVERLAY_POSITION_MAX_PERCENT = 105;
    /** SeekBar: progress от 0 до OVERLAY_POSITION_SEEK_MAX включительно → см. {@link #percentFromSeekProgress}. */
    public static final int OVERLAY_POSITION_SEEK_MAX =
            OVERLAY_POSITION_MAX_PERCENT - OVERLAY_POSITION_MIN_PERCENT;
    public static final int OVERLAY_ALPHA_MIN = 0;
    public static final int OVERLAY_ALPHA_MAX = 255;
    public static final int DEFAULT_OVERLAY_ALPHA_TOP = 0xD9;
    public static final int DEFAULT_OVERLAY_ALPHA_BOTTOM = 0xF0;

    private String packageName;
    private String appName;
    private boolean overlayEnabled;
    private boolean notificationsEnabled;
    private boolean overlaySpeedVisible;
    private int overlayAlphaTop;
    private int overlayAlphaBottom;
    private int overlayPositionXPercent;
    private int overlayPositionYPercent;
    private boolean isSelected; // для режима редактирования

    public AppSettings(String packageName, String appName) {
        this.packageName = packageName;
        this.appName = appName;
        this.overlayEnabled = true; // по умолчанию включено
        this.notificationsEnabled = true; // по умолчанию включено
        this.overlaySpeedVisible = false; // по умолчанию выключено
        this.overlayAlphaTop = DEFAULT_OVERLAY_ALPHA_TOP;
        this.overlayAlphaBottom = DEFAULT_OVERLAY_ALPHA_BOTTOM;
        this.overlayPositionXPercent = clampOverlayPositionPercent(50);
        this.overlayPositionYPercent = clampOverlayPositionPercent(5);
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
        this.packageName = packageName;
        this.appName = appName;
        this.overlayEnabled = overlayEnabled;
        this.notificationsEnabled = notificationsEnabled;
        this.overlaySpeedVisible = overlaySpeedVisible;
        this.overlayAlphaTop = clampOverlayAlpha(overlayAlphaTop);
        this.overlayAlphaBottom = clampOverlayAlpha(overlayAlphaBottom);
        this.overlayPositionXPercent = clampOverlayPositionPercent(overlayPositionXPercent);
        this.overlayPositionYPercent = clampOverlayPositionPercent(overlayPositionYPercent);
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

    public static int clampOverlayPositionPercent(int value) {
        return Math.max(OVERLAY_POSITION_MIN_PERCENT,
                Math.min(OVERLAY_POSITION_MAX_PERCENT, value));
    }

    public static int clampOverlayAlpha(int value) {
        return Math.max(OVERLAY_ALPHA_MIN,
                Math.min(OVERLAY_ALPHA_MAX, value));
    }

    /** Значение процентов из позиции SeekBar (0 … OVERLAY_POSITION_SEEK_MAX). */
    public static int percentFromSeekProgress(int seekProgress) {
        int percent = seekProgress + OVERLAY_POSITION_MIN_PERCENT;
        return clampOverlayPositionPercent(percent);
    }

    /** Позиция SeekBar для сохранённого процента. */
    public static int seekProgressFromPercent(int percent) {
        return clampOverlayPositionPercent(percent) - OVERLAY_POSITION_MIN_PERCENT;
    }

    public void setOverlayPositionXPercent(int overlayPositionXPercent) {
        this.overlayPositionXPercent = clampOverlayPositionPercent(overlayPositionXPercent);
    }

    public void setOverlayPositionYPercent(int overlayPositionYPercent) {
        this.overlayPositionYPercent = clampOverlayPositionPercent(overlayPositionYPercent);
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
