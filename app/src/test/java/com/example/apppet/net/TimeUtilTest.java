package com.example.apppet.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeUtilTest {
    @Test
    public void isoToLocalUsesDeviceTimezone() {
        String expected = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(Instant.parse("2026-10-22T08:30:12Z").atZone(ZoneId.systemDefault()));
        assertEquals(expected, TimeUtil.isoToLocal("2026-10-22T08:30:12.123456+00:00"));
        assertEquals(expected, TimeUtil.isoToLocal("2026-10-22T08:30:12+00:00"));
    }

    @Test
    public void badInputGivesEmptyOrNull() {
        assertEquals("", TimeUtil.isoToLocal("rác"));
        assertEquals("", TimeUtil.isoToLocal(null));
        assertNull(TimeUtil.minusSeconds("rác", 2));
    }

    @Test
    public void cursorHelpersCompareInstantsNotStrings() {
        assertEquals("2026-10-22T08:30:10.500Z", TimeUtil.minusSeconds("2026-10-22T08:30:12.5+00:00", 2));
        assertEquals("2026-10-22T08:30:12.123456+00:00",
                TimeUtil.later("2026-10-22T08:30:12.12+00:00", "2026-10-22T08:30:12.123456+00:00"));
        assertEquals("2026-10-22T08:30:12+00:00", TimeUtil.later(null, "2026-10-22T08:30:12+00:00"));
    }
}
