package com.imshy.bedwars.runtime;

import com.imshy.bedwars.runtime.SidebarEventClock.NextEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link SidebarEventClock#parseRow}: the "<event> in M:SS" rows
 * Hypixel prints on the sidebar, tier classification and deadline math.
 */
public class SidebarEventClockTest {

    private static final long T0 = 5_000_000L;

    @Test
    public void parsesDiamondTierRow() {
        NextEvent event = SidebarEventClock.parseRow("Diamond II in 3:45", T0);
        assertNotNull(event);
        assertEquals("Diamond II", event.label);
        assertTrue(event.isGeneratorTier);
        assertEquals(T0 + (3 * 60 + 45) * 1000L, event.deadlineMs);
        assertEquals(225, event.secondsRemaining(T0));
    }

    @Test
    public void parsesEmeraldTierRow() {
        NextEvent event = SidebarEventClock.parseRow("Emerald III in 0:12", T0);
        assertNotNull(event);
        assertTrue(event.isGeneratorTier);
        assertEquals(12, event.secondsRemaining(T0));
    }

    @Test
    public void parsesNonGeneratorEventRows() {
        NextEvent bedGone = SidebarEventClock.parseRow("Bed Gone In 5:00", T0);
        assertNotNull(bedGone);
        assertFalse(bedGone.isGeneratorTier);
        assertEquals("Bed Gone", bedGone.label);

        NextEvent suddenDeath = SidebarEventClock.parseRow("Sudden Death in 2:00", T0);
        assertNotNull(suddenDeath);
        assertFalse(suddenDeath.isGeneratorTier);
    }

    @Test
    public void toleratesSurroundingWhitespace() {
        assertNotNull(SidebarEventClock.parseRow("  Diamond II in 3:45  ", T0));
    }

    @Test
    public void rejectsNonEventRows() {
        assertNull(SidebarEventClock.parseRow(null, T0));
        assertNull(SidebarEventClock.parseRow("", T0));
        assertNull(SidebarEventClock.parseRow("Red: 3", T0));
        assertNull(SidebarEventClock.parseRow("Map: Lighthouse", T0));
        assertNull(SidebarEventClock.parseRow("Diamond II in 3:75", T0));
        assertNull(SidebarEventClock.parseRow("mc.hypixel.net", T0));
    }

    @Test
    public void secondsRemainingRoundsUpAndClampsAtZero() {
        NextEvent event = SidebarEventClock.parseRow("Diamond II in 0:05", T0);
        assertEquals(5, event.secondsRemaining(T0));
        assertEquals(5, event.secondsRemaining(T0 + 1));
        assertEquals(1, event.secondsRemaining(T0 + 4_500));
        assertEquals(0, event.secondsRemaining(T0 + 5_000));
        assertEquals(0, event.secondsRemaining(T0 + 60_000));
    }
}
