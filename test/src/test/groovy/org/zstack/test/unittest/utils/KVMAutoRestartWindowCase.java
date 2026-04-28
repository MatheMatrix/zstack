package org.zstack.test.unittest.utils;

import org.junit.Test;
import org.zstack.kvm.KVMHostFactory;

import java.time.LocalTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KVMAutoRestartWindowCase {

    private boolean inWindow(String configValue, String now) {
        return KVMHostFactory.isNowInAutoRestartWindow(configValue, LocalTime.parse(now));
    }

    @Test
    public void emptyOrNullValueAlwaysAllowed() {
        assertTrue(inWindow("", "00:00"));
        assertTrue(inWindow("", "12:00"));
        assertTrue(inWindow("", "23:59"));
        assertTrue(inWindow(null, "12:00"));
        assertTrue(inWindow("   ", "12:00"));
    }

    @Test
    public void normalWindowMembership() {
        assertTrue(inWindow("02:00-04:00", "02:30"));
        assertTrue(inWindow("02:00-04:00", "03:59"));
        assertFalse(inWindow("02:00-04:00", "00:00"));
        assertFalse(inWindow("02:00-04:00", "14:00"));
    }

    @Test
    public void normalWindowBoundary() {
        assertTrue(inWindow("02:00-04:00", "02:00"));
        assertFalse(inWindow("02:00-04:00", "04:00"));
        assertTrue(inWindow("02:00-04:00", "03:59"));
    }

    @Test
    public void crossMidnightWindowMembership() {
        assertTrue(inWindow("22:00-02:00", "23:30"));
        assertTrue(inWindow("22:00-02:00", "22:00"));
        assertTrue(inWindow("22:00-02:00", "00:30"));
        assertTrue(inWindow("22:00-02:00", "01:59"));
        assertFalse(inWindow("22:00-02:00", "14:00"));
        assertFalse(inWindow("22:00-02:00", "02:00"));
        assertFalse(inWindow("22:00-02:00", "21:59"));
    }

    @Test
    public void wholeDayMinusOneMinute() {
        assertTrue(inWindow("00:00-23:59", "00:00"));
        assertTrue(inWindow("00:00-23:59", "12:00"));
        assertTrue(inWindow("00:00-23:59", "23:58"));
        assertFalse(inWindow("00:00-23:59", "23:59"));
    }
}
