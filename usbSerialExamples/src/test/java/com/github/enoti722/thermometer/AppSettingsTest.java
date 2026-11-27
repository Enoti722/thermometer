package com.github.enoti722.thermometer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Диапазоны позиции оверлея по осям и масштаб отображаемых данных (настройки конкретного приложения).
 */
public class AppSettingsTest {

    @Test
    public void horizontalPositionRangeIsMinus5To105() {
        assertEquals(-5, AppSettings.clampOverlayPositionXPercent(-100));
        assertEquals(-5, AppSettings.clampOverlayPositionXPercent(-5));
        assertEquals(50, AppSettings.clampOverlayPositionXPercent(50));
        assertEquals(105, AppSettings.clampOverlayPositionXPercent(105));
        assertEquals(105, AppSettings.clampOverlayPositionXPercent(500));
    }

    @Test
    public void verticalPositionRangeIsMinus5To105() {
        assertEquals(-5, AppSettings.clampOverlayPositionYPercent(-100));
        assertEquals(-5, AppSettings.clampOverlayPositionYPercent(-5));
        assertEquals(5, AppSettings.clampOverlayPositionYPercent(5));
        assertEquals(105, AppSettings.clampOverlayPositionYPercent(105));
        assertEquals(105, AppSettings.clampOverlayPositionYPercent(500));
    }

    @Test
    public void horizontalSeekCoversWholeRange() {
        assertEquals(-5, AppSettings.percentXFromSeekProgress(0));
        assertEquals(50, AppSettings.percentXFromSeekProgress(55));
        assertEquals(105, AppSettings.percentXFromSeekProgress(AppSettings.OVERLAY_POSITION_X_SEEK_MAX));
        assertEquals(0, AppSettings.seekXProgressFromPercent(-5));
        assertEquals(55, AppSettings.seekXProgressFromPercent(50));
        assertEquals(AppSettings.OVERLAY_POSITION_X_SEEK_MAX, AppSettings.seekXProgressFromPercent(105));
    }

    @Test
    public void verticalSeekCoversWholeRange() {
        assertEquals(-5, AppSettings.percentYFromSeekProgress(0));
        assertEquals(5, AppSettings.percentYFromSeekProgress(10));
        assertEquals(105, AppSettings.percentYFromSeekProgress(AppSettings.OVERLAY_POSITION_Y_SEEK_MAX));
        assertEquals(0, AppSettings.seekYProgressFromPercent(-5));
        assertEquals(10, AppSettings.seekYProgressFromPercent(5));
        assertEquals(AppSettings.OVERLAY_POSITION_Y_SEEK_MAX, AppSettings.seekYProgressFromPercent(105));
    }

    @Test
    public void savedValuesFromOldRangeStayValid() {
        // Горизонтальная позиция ограничена диапазоном -5 … 105%.
        assertEquals(-5, AppSettings.clampOverlayPositionXPercent(-5));
        assertEquals(105, AppSettings.clampOverlayPositionXPercent(105));
        assertEquals(0, AppSettings.seekXProgressFromPercent(-5));
        assertEquals(-5, AppSettings.percentXFromSeekProgress(0));
        assertEquals(110, AppSettings.seekXProgressFromPercent(105));
        assertEquals(105, AppSettings.percentXFromSeekProgress(110));
    }

    @Test
    public void dataSizeScaleClampsToQuarterAndQuadruple() {
        assertEquals(0.25f, AppSettings.clampOverlayDataSizeScale(0.01f), 0f);
        assertEquals(0.25f, AppSettings.clampOverlayDataSizeScale(0.25f), 0f);
        assertEquals(1.0f, AppSettings.clampOverlayDataSizeScale(1.0f), 0f);
        assertEquals(4.0f, AppSettings.clampOverlayDataSizeScale(4.0f), 0f);
        assertEquals(4.0f, AppSettings.clampOverlayDataSizeScale(10f), 0f);
    }

    @Test
    public void dataSizeSeekMapsStepToScale() {
        assertEquals(0.25f, AppSettings.dataSizeScaleFromSeekProgress(5), 0f);
        assertEquals(1.0f, AppSettings.dataSizeScaleFromSeekProgress(20), 0f);
        assertEquals(4.0f, AppSettings.dataSizeScaleFromSeekProgress(AppSettings.OVERLAY_DATA_SIZE_SEEK_MAX), 0f);
        assertEquals(5, AppSettings.dataSizeSeekProgressFromScale(0.25f));
        assertEquals(20, AppSettings.dataSizeSeekProgressFromScale(1.0f));
        assertEquals(AppSettings.OVERLAY_DATA_SIZE_SEEK_MAX, AppSettings.dataSizeSeekProgressFromScale(4.0f));
    }

    @Test
    public void dataSizeSeekRoundTripsThroughSavedScale() {
        for (int progress = 5; progress <= AppSettings.OVERLAY_DATA_SIZE_SEEK_MAX; progress++) {
            float scale = AppSettings.dataSizeScaleFromSeekProgress(progress);
            assertEquals(progress, AppSettings.dataSizeSeekProgressFromScale(scale));
        }
    }

    @Test
    public void newAppUsesLegacyLayoutAndCurrentSize() {
        AppSettings settings = new AppSettings("com.example.app", "Example");
        assertFalse(settings.isOverlayVertical());
        assertEquals(AppSettings.DEFAULT_OVERLAY_DATA_SIZE_SCALE, settings.getOverlayDataSizeScale(), 0f);
        assertEquals(50, settings.getOverlayPositionXPercent());
        assertEquals(5, settings.getOverlayPositionYPercent());
    }

    @Test
    public void fullConstructorStoresVerticalAndScale() {
        AppSettings settings = new AppSettings("com.example.app", "Example",
                true, true, false, 217, 240, -20, 120, true, 2.5f);
        assertTrue(settings.isOverlayVertical());
        assertEquals(2.5f, settings.getOverlayDataSizeScale(), 0f);
        assertEquals(-5, settings.getOverlayPositionXPercent());
        assertEquals(105, settings.getOverlayPositionYPercent());
    }

    @Test
    public void legacyConstructorKeepsLegacyDefaultsForNewOptions() {
        AppSettings settings = new AppSettings("com.example.app", "Example",
                true, true, false, 217, 240, 30, 10);
        assertFalse(settings.isOverlayVertical());
        assertEquals(AppSettings.DEFAULT_OVERLAY_DATA_SIZE_SCALE, settings.getOverlayDataSizeScale(), 0f);
    }

    @Test
    public void contentOffsetRangeAndSeekMapping() {
        assertEquals(-100, AppSettings.clampOverlayContentOffsetPercent(-500));
        assertEquals(-100, AppSettings.clampOverlayContentOffsetPercent(-100));
        assertEquals(50, AppSettings.clampOverlayContentOffsetPercent(50));
        assertEquals(100, AppSettings.clampOverlayContentOffsetPercent(100));
        assertEquals(100, AppSettings.clampOverlayContentOffsetPercent(500));

        // Ползунок: 0 → -100%, середина → 0%, конец → +100%
        assertEquals(-100, AppSettings.overlayContentOffsetFromSeekProgress(0));
        assertEquals(0, AppSettings.overlayContentOffsetFromSeekProgress(
                AppSettings.OVERLAY_CONTENT_OFFSET_SEEK_MAX / 2));
        assertEquals(100, AppSettings.overlayContentOffsetFromSeekProgress(
                AppSettings.OVERLAY_CONTENT_OFFSET_SEEK_MAX));

        assertEquals(0, AppSettings.overlayContentOffsetSeekProgressFromPercent(-100));
        assertEquals(AppSettings.OVERLAY_CONTENT_OFFSET_SEEK_MAX / 2,
                AppSettings.overlayContentOffsetSeekProgressFromPercent(0));
        assertEquals(AppSettings.OVERLAY_CONTENT_OFFSET_SEEK_MAX,
                AppSettings.overlayContentOffsetSeekProgressFromPercent(100));
    }

    @Test
    public void plateSizeRangeAndSeekMapping() {
        assertEquals(50, AppSettings.clampOverlayPlateSizePercent(10));
        assertEquals(50, AppSettings.clampOverlayPlateSizePercent(50));
        assertEquals(100, AppSettings.clampOverlayPlateSizePercent(100));
        assertEquals(300, AppSettings.clampOverlayPlateSizePercent(300));
        assertEquals(300, AppSettings.clampOverlayPlateSizePercent(1000));

        // Ползунок: 0 → 50%, 10 → 100%, конец → 300%
        assertEquals(50, AppSettings.overlayPlateSizeFromSeekProgress(0));
        assertEquals(100, AppSettings.overlayPlateSizeFromSeekProgress(10));
        assertEquals(300, AppSettings.overlayPlateSizeFromSeekProgress(
                AppSettings.OVERLAY_PLATE_SIZE_SEEK_MAX));

        assertEquals(0, AppSettings.overlayPlateSizeSeekProgressFromPercent(50));
        assertEquals(10, AppSettings.overlayPlateSizeSeekProgressFromPercent(100));
        assertEquals(AppSettings.OVERLAY_PLATE_SIZE_SEEK_MAX,
                AppSettings.overlayPlateSizeSeekProgressFromPercent(300));
    }

    @Test
    public void newAppDefaultsToCenteredContentAndFullPlate() {
        AppSettings settings = new AppSettings("com.example.app", "Example");
        assertEquals(0, settings.getOverlayContentOffsetXPercent());
        assertEquals(0, settings.getOverlayContentOffsetYPercent());
        assertEquals(AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT, settings.getOverlayPlateWidthPercent());
        assertEquals(AppSettings.DEFAULT_OVERLAY_PLATE_SIZE_PERCENT, settings.getOverlayPlateHeightPercent());
    }

    @Test
    public void contentOffsetAndPlateSettersClamp() {
        AppSettings settings = new AppSettings("com.example.app", "Example");
        settings.setOverlayContentOffsetXPercent(-500);
        settings.setOverlayContentOffsetYPercent(999);
        settings.setOverlayPlateWidthPercent(10);
        settings.setOverlayPlateHeightPercent(9999);
        assertEquals(-100, settings.getOverlayContentOffsetXPercent());
        assertEquals(100, settings.getOverlayContentOffsetYPercent());
        assertEquals(50, settings.getOverlayPlateWidthPercent());
        assertEquals(300, settings.getOverlayPlateHeightPercent());
    }
}
