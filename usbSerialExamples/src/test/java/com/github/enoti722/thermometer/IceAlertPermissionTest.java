package com.github.enoti722.thermometer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IceAlertPermissionTest {

    private static final float THRESHOLD = 5f;
    private static final float REARM_DELTA = 3f;

    private final IceAlertPermission permission = new IceAlertPermission(THRESHOLD, REARM_DELTA);

    @Test
    public void warnsOncePerGrantedPermission() {
        permission.grant();
        assertTrue(permission.allowsWarning(4.9f));

        permission.consume();
        assertFalse(permission.allowsWarning(4.9f));
        assertFalse(permission.allowsWarning(-10f));
    }

    @Test
    public void rearmNeedsThreeDegreesAboveThreshold() {
        permission.grant();
        permission.consume();

        assertFalse(permission.rearmIfWarmEnough(THRESHOLD));
        assertFalse(permission.rearmIfWarmEnough(THRESHOLD + REARM_DELTA));
        assertFalse(permission.allowsWarning(THRESHOLD - 1f));

        assertTrue(permission.rearmIfWarmEnough(THRESHOLD + REARM_DELTA + 0.1f));
        assertTrue(permission.allowsWarning(THRESHOLD - 1f));
    }

    @Test
    public void rearmIsNoOpWhilePermissionStillGranted() {
        permission.grant();
        assertFalse(permission.rearmIfWarmEnough(20f));
        assertTrue(permission.isGranted());
    }

    @Test
    public void reconnectGrantsPermissionAgain() {
        permission.grant();
        permission.consume();
        permission.grant();

        assertTrue(permission.allowsWarning(0f));
    }

    @Test
    public void warmDisplayedTemperatureDoesNotAllowWarning() {
        permission.grant();
        assertFalse(permission.allowsWarning(THRESHOLD));
        assertFalse(permission.allowsWarning(THRESHOLD + 1f));
    }
}
