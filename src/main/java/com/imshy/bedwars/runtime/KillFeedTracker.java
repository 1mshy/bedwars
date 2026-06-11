package com.imshy.bedwars.runtime;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded history of recent kill/death events for the killfeed HUD element.
 *
 * <p>Deliberately Minecraft-free (strings + longs, injected timestamps) so it
 * is unit-testable: callers resolve team colors and pass timestamps in.
 * Entries are kept newest-first, capped at {@link #MAX_ENTRIES}, and expire
 * {@link #ENTRY_TTL_MS} after their timestamp.
 */
public class KillFeedTracker {

    /** Maximum entries retained (oldest dropped first). */
    public static final int MAX_ENTRIES = 6;
    /** Entries older than this are expired and pruned. */
    public static final long ENTRY_TTL_MS = 12_000L;

    /** One killfeed line: victim, optional killer, victim team color, time. */
    public static final class Entry {
        public final String victimName;
        /** Nullable — environmental/unattributed deaths have no killer. */
        public final String killerName;
        /** Victim team color code (e.g. "§c"), empty when unresolved. */
        public final String victimTeamColorCode;
        public final long timestampMs;

        Entry(String victimName, String killerName, String victimTeamColorCode, long timestampMs) {
            this.victimName = victimName;
            this.killerName = killerName;
            this.victimTeamColorCode = victimTeamColorCode;
            this.timestampMs = timestampMs;
        }

        public boolean isExpired(long nowMs) {
            return nowMs - timestampMs >= ENTRY_TTL_MS;
        }
    }

    // Newest entries at the head; timestamps are expected to be monotonic.
    private final Deque<Entry> entries = new ArrayDeque<Entry>();

    /** Records an event. Null/empty victims are ignored; killer may be null. */
    public void addEntry(String victimName, String killerName, String victimTeamColorCode, long nowMs) {
        if (victimName == null || victimName.isEmpty()) {
            return;
        }
        entries.addFirst(new Entry(victimName, killerName,
                victimTeamColorCode == null ? "" : victimTeamColorCode, nowMs));
        while (entries.size() > MAX_ENTRIES) {
            entries.removeLast();
        }
    }

    /**
     * Returns the unexpired entries newest-first, opportunistically pruning
     * expired ones from the tail (oldest end) of the deque.
     */
    public List<Entry> getActiveEntries(long nowMs) {
        while (!entries.isEmpty() && entries.peekLast().isExpired(nowMs)) {
            entries.removeLast();
        }
        List<Entry> out = new ArrayList<Entry>(entries.size());
        for (Entry entry : entries) {
            if (!entry.isExpired(nowMs)) {
                out.add(entry);
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public void clear() {
        entries.clear();
    }
}
