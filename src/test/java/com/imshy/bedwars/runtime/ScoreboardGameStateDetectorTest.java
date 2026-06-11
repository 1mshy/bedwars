package com.imshy.bedwars.runtime;

import com.imshy.bedwars.runtime.ScoreboardGameStateDetector.TeamStatus;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for the MC-decoupled surface of {@link ScoreboardGameStateDetector}:
 * the null-Minecraft guards and {@link TeamStatus} value semantics. The core
 * scoreboard parsing requires a live Minecraft instance and is verified
 * in-game instead.
 */
public class ScoreboardGameStateDetectorTest {

    @Test
    public void parseTeamStatusesReturnsEmptyForNullMinecraft() {
        assertTrue(ScoreboardGameStateDetector.parseTeamStatuses(null).isEmpty());
    }

    @Test
    public void isMatchInProgressFalseForNullMinecraft() {
        assertFalse(ScoreboardGameStateDetector.isMatchInProgress(null));
    }

    @Test
    public void getOwnTeamColorCodeNullForNullMinecraft() {
        assertNull(ScoreboardGameStateDetector.getOwnTeamColorCode(null));
    }

    @Test
    public void teamStatusBedAlive() {
        TeamStatus status = new TeamStatus("Red", 'B', 0, false, "§c");
        assertEquals("Red", status.teamName);
        assertEquals('B', status.statusType);
        assertEquals(0, status.bedGoneCount);
        assertFalse(status.isOwnTeam);
        assertEquals("§c", status.colorCode);
        assertEquals("Red:B", status.toString());
    }

    @Test
    public void teamStatusBedGoneShowsAliveCountAndOwnTeam() {
        TeamStatus status = new TeamStatus("Blue", 'D', 3, true, "§9");
        assertEquals('D', status.statusType);
        assertEquals(3, status.bedGoneCount);
        assertTrue(status.isOwnTeam);
        assertEquals("Blue:D3(YOU)", status.toString());
    }

    @Test
    public void teamStatusEliminated() {
        TeamStatus status = new TeamStatus("Green", 'E', 0, false, "§a");
        assertEquals('E', status.statusType);
        assertEquals("Green:E", status.toString());
    }
}
