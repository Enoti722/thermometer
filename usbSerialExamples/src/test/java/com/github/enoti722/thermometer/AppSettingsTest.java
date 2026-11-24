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
    public void horizontalPositionRangeIsMinus20To120() {
        assertEquals(-20, AppSettings.clampOverlayPositionXPercent(-100));
        assertEquals(-20, AppSettings.clampOverlayPositionXPercent(-20));
        assertEquals(50, AppSettings.clampOverlayPositionXPercent(50));
        assertEquals(120, AppSettings.clampOverlayPositionXPercent(120));
        assertEquals(120, AppSettings.clampOverlayPositionXPercent(500));
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
        assertEquals(-20, AppSettings.percentXFromSeekProgress(0));
        assertEquals(50, AppSettings.percentXFromSeekProgress(70));
        assertEquals(120, AppSettings.percentXFromSeekProgress(AppSettings.OVERLAY_POSITION_X_SEEK_MAX));
        assertEquals(0, AppSettings.seekXProgressFromPercent(-20));
        assertEquals(70, AppSettings.seekXProgressFromPercent(50));
        assertEquals(AppSettings.OVERLAY_POSITION_X_SEEK_MAX, AppSettings.seekXProgressFromPercent(120));
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
        // До расширения горизонтали сохранялись значения -5 … 105 — они остаются в новом диапазоне.
        assertEquals(-5, AppSettings.clampOverlayPositionXPercent(-5));
        assertEquals(105, AppSettings.clampOverlayPositionXPercent(105));
        assertEquals(15, AppSettings.seekXProgressFromPercent(-5));
        assertEquals(-5, AppSettings.percentXFromSeekProgress(15));
        assertEquals(125, AppSettings.seekXProgressFromPercent(105));
        assertEquals(105, AppSettings.percentXFromSeekProgress(125));
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
        assertEquals(-20, settings.getOverlayPositionXPercent());
        assertEquals(105, settings.getOverlayPositionYPercent());
    }

    @Test
    public void legacyConstructorKeepsLegacyDefaultsForNewOptions() {
        AppSettings settings = new AppSettings("com.example.app", "Example",
                true, true, false, 217, 240, 30, 10);
        assertFalse(settings.isOverlayVertical());
        assertEquals(AppSettings.DEFAULT_OVERLAY_DATA_SIZE_SCALE, settings.getOverlayDataSizeScale(), 0f);
    }
}
