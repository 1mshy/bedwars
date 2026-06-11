package com.imshy.bedwars.runtime;

import com.imshy.bedwars.runtime.KillFeedTracker.Entry;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link KillFeedTracker}: add/ordering, the 6-entry bound, the 12s
 * expiry window and opportunistic pruning, and clearing. The tracker is
 * MC-free (strings + longs, injected timestamps) so everything here is
 * deterministic.
 */
public class KillFeedTrackerTest {

    private KillFeedTracker tracker;

    @Before
    public void setUp() {
        tracker = new KillFeedTracker();
    }

    // ── add / field semantics ───────────────────────────────────────────────

    @Test
    public void addEntryStoresAllFields() {
        tracker.addEntry("Victim", "Killer", "§c", 1_000L);

        List<Entry> active = tracker.getActiveEntries(1_000L);
        assertEquals(1, active.size());
        Entry entry = active.get(0);
        assertEquals("Victim", entry.victimName);
        assertEquals("Killer", entry.killerName);
        assertEquals("§c", entry.victimTeamColorCode);
        assertEquals(1_000L, entry.timestampMs);
    }

    @Test
    public void nullKillerIsAllowed() {
        tracker.addEntry("Victim", null, "§a", 1_000L);

        List<Entry> active = tracker.getActiveEntries(1_000L);
        assertEquals(1, active.size());
        assertNull(active.get(0).killerName);
    }

    @Test
    public void nullTeamColorBecomesEmptyString() {
        tracker.addEntry("Victim", "Killer", null, 1_000L);

        assertEquals("", tracker.getActiveEntries(1_000L).get(0).victimTeamColorCode);
    }

    @Test
    public void nullVictimIsIgnored() {
        tracker.addEntry(null, "Killer", "§c", 1_000L);

        assertTrue(tracker.isEmpty());
    }

    @Test
    public void emptyVictimIsIgnored() {
        tracker.addEntry("", "Killer", "§c", 1_000L);

        assertTrue(tracker.isEmpty());
    }

    // ── ordering / bound ────────────────────────────────────────────────────

    @Test
    public void entriesAreReturnedNewestFirst() {
        tracker.addEntry("First", null, "", 1_000L);
        tracker.addEntry("Second", null, "", 2_000L);
        tracker.addEntry("Third", null, "", 3_000L);

        List<Entry> active = tracker.getActiveEntries(3_000L);
        assertEquals(3, active.size());
        assertEquals("Third", active.get(0).victimName);
        assertEquals("Second", active.get(1).victimName);
        assertEquals("First", active.get(2).victimName);
    }

    @Test
    public void boundedAtMaxEntriesDroppingOldest() {
        for (int i = 1; i <= KillFeedTracker.MAX_ENTRIES + 2; i++) {
            tracker.addEntry("Victim" + i, null, "", 1_000L + i);
        }

        List<Entry> active = tracker.getActiveEntries(2_000L);
        assertEquals(KillFeedTracker.MAX_ENTRIES, active.size());
        // Newest survives at the head; the two oldest were dropped.
        assertEquals("Victim8", active.get(0).victimName);
        assertEquals("Victim3", active.get(active.size() - 1).victimName);
    }

    // ── expiry ──────────────────────────────────────────────────────────────

    @Test
    public void entryIsActiveJustBeforeTtl() {
        tracker.addEntry("Victim", null, "", 0L);

        assertEquals(1, tracker.getActiveEntries(KillFeedTracker.ENTRY_TTL_MS - 1).size());
    }

    @Test
    public void entryExpiresExactlyAtTtl() {
        tracker.addEntry("Victim", null, "", 0L);

        assertTrue(tracker.getActiveEntries(KillFeedTracker.ENTRY_TTL_MS).isEmpty());
    }

    @Test
    public void expiredEntriesArePrunedFromTail() {
        tracker.addEntry("Old", null, "", 0L);
        tracker.addEntry("New", null, "", KillFeedTracker.ENTRY_TTL_MS);

        List<Entry> active = tracker.getActiveEntries(KillFeedTracker.ENTRY_TTL_MS);
        assertEquals(1, active.size());
        assertEquals("New", active.get(0).victimName);
        // The expired tail entry was physically removed, not just filtered.
        assertFalse(tracker.isEmpty());
        assertEquals(1, tracker.getActiveEntries(KillFeedTracker.ENTRY_TTL_MS).size());
    }

    @Test
    public void allEntriesExpiringEmptiesTracker() {
        tracker.addEntry("A", null, "", 0L);
        tracker.addEntry("B", null, "", 100L);

        assertTrue(tracker.getActiveEntries(KillFeedTracker.ENTRY_TTL_MS + 100L).isEmpty());
        assertTrue(tracker.isEmpty());
    }

    @Test
    public void isExpiredMatchesTtlBoundary() {
        tracker.addEntry("Victim", null, "", 5_000L);
        Entry entry = tracker.getActiveEntries(5_000L).get(0);

        assertFalse(entry.isExpired(5_000L + KillFeedTracker.ENTRY_TTL_MS - 1));
        assertTrue(entry.isExpired(5_000L + KillFeedTracker.ENTRY_TTL_MS));
    }

    // ── clear ───────────────────────────────────────────────────────────────

    @Test
    public void clearRemovesEverything() {
        tracker.addEntry("A", "B", "§c", 1_000L);
        tracker.addEntry("C", null, "§9", 2_000L);

        tracker.clear();

        assertTrue(tracker.isEmpty());
        assertTrue(tracker.getActiveEntries(2_000L).isEmpty());
    }

    @Test
    public void freshTrackerIsEmpty() {
        assertTrue(tracker.isEmpty());
        assertTrue(tracker.getActiveEntries(0L).isEmpty());
    }
}
