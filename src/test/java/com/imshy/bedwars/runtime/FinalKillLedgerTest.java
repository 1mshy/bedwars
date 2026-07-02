package com.imshy.bedwars.runtime;

import com.imshy.bedwars.runtime.FinalKillLedger.TeamTally;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link FinalKillLedger}: per-team accumulation, the UNKNOWN
 * fallback bucket, totals, clearing, insertion order and the 30s streak
 * window (driven deterministically via the public {@code lastKillTime} field).
 */
public class FinalKillLedgerTest {

    private FinalKillLedger ledger;

    @Before
    public void setUp() {
        ledger = new FinalKillLedger();
    }

    @Test
    public void recordFinalKillCreatesTally() {
        ledger.recordFinalKill("Alice", "Red", "§c");

        TeamTally tally = ledger.getTallies().get("Red");
        assertNotNull(tally);
        assertEquals("Red", tally.teamName);
        assertEquals("§c", tally.teamColor);
        assertEquals(1, tally.finalKills);
        assertEquals("Alice", tally.lastVictim);
        assertTrue(tally.lastKillTime > 0);
    }

    @Test
    public void repeatedKillsAccumulateOnSameTeam() {
        ledger.recordFinalKill("Alice", "Red", "§c");
        ledger.recordFinalKill("Bob", "Red", "§c");
        ledger.recordFinalKill("Carol", "Red", "§c");

        assertEquals(1, ledger.getTallies().size());
        TeamTally tally = ledger.getTallies().get("Red");
        assertEquals(3, tally.finalKills);
        assertEquals("Carol", tally.lastVictim);
    }

    @Test
    public void nullTeamFallsBackToUnknownBucket() {
        ledger.recordFinalKill("Mystery", null, null);

        TeamTally tally = ledger.getTallies().get("UNKNOWN");
        assertNotNull(tally);
        assertEquals("UNKNOWN", tally.teamName);
        assertEquals("§7", tally.teamColor); // gray fallback color
        assertEquals(1, tally.finalKills);
        assertEquals("Mystery", tally.lastVictim);
    }

    @Test
    public void totalsSumAcrossTeams() {
        assertEquals(0, ledger.getTotalFinalKills());

        ledger.recordFinalKill("Alice", "Red", "§c");
        ledger.recordFinalKill("Bob", "Blue", "§9");
        ledger.recordFinalKill("Carol", "Blue", "§9");
        ledger.recordFinalKill("Mystery", null, null);

        assertEquals(4, ledger.getTotalFinalKills());
    }

    @Test
    public void clearEmptiesLedger() {
        ledger.recordFinalKill("Alice", "Red", "§c");
        ledger.recordFinalKill("Bob", "Blue", "§9");

        ledger.clear();

        assertTrue(ledger.getTallies().isEmpty());
        assertEquals(0, ledger.getTotalFinalKills());
    }

    @Test
    public void talliesPreserveInsertionOrder() {
        ledger.recordFinalKill("Alice", "Red", "§c");
        ledger.recordFinalKill("Bob", "Blue", "§9");
        ledger.recordFinalKill("Carol", "Green", "§a");

        Iterator<String> keys = ledger.getTallies().keySet().iterator();
        assertEquals("Red", keys.next());
        assertEquals("Blue", keys.next());
        assertEquals("Green", keys.next());
    }

    @Test
    public void isOnStreakWindowBoundary() {
        TeamTally tally = new TeamTally("Red", "§c");

        // No kill recorded yet — never on streak, even at now == 0
        assertFalse(tally.isOnStreak(0L));

        tally.lastKillTime = 100_000L;
        assertTrue(tally.isOnStreak(100_000L));  // same instant
        assertTrue(tally.isOnStreak(130_000L));  // exactly 30s — inclusive
        assertFalse(tally.isOnStreak(130_001L)); // just past the window
    }

    @Test
    public void recordFinalKillStampsWallClock() {
        long before = System.currentTimeMillis();
        ledger.recordFinalKill("Alice", "Red", "§c");

        TeamTally tally = ledger.getTallies().get("Red");
        assertTrue(tally.lastKillTime >= before);
        assertTrue(tally.isOnStreak(System.currentTimeMillis()));
    }

    @Test
    public void killerTallyAccumulatesPerKiller() {
        ledger.recordFinalKill("Alice", "Red", "§c", "Sniper");
        ledger.recordFinalKill("Bob", "Blue", "§9", "Sniper");
        ledger.recordFinalKill("Carol", "Red", "§c", "Other");

        assertEquals(2, ledger.getKillerFinals("Sniper"));
        assertEquals(1, ledger.getKillerFinals("Other"));
        assertEquals(0, ledger.getKillerFinals("Nobody"));
        assertEquals(0, ledger.getKillerFinals(null));
    }

    @Test
    public void killerLookupIsCaseInsensitive() {
        ledger.recordFinalKill("Alice", "Red", "§c", "Sniper");
        assertEquals(1, ledger.getKillerFinals("sNiPeR"));
    }

    @Test
    public void nullKillerOnlyUpdatesTeamTally() {
        ledger.recordFinalKill("Alice", "Red", "§c", null);
        assertEquals(1, ledger.getTotalFinalKills());
        assertEquals(null, ledger.getTopKiller());
    }

    @Test
    public void topKillerIsHighestTally() {
        ledger.recordFinalKill("A", "Red", "§c", "Low");
        ledger.recordFinalKill("B", "Blue", "§9", "High");
        ledger.recordFinalKill("C", "Green", "§a", "High");

        assertEquals("High", ledger.getTopKiller().killerName);
        assertEquals(2, ledger.getTopKiller().finalKills);
    }

    @Test
    public void clearDropsKillerTallies() {
        ledger.recordFinalKill("Alice", "Red", "§c", "Sniper");
        ledger.clear();
        assertEquals(0, ledger.getKillerFinals("Sniper"));
        assertEquals(null, ledger.getTopKiller());
    }
}
