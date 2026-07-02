package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;
import com.imshy.bedwars.HypixelAPI;
import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Appends cached threat stats (stars + FKDR) to tab-list display names by
 * mutating {@link NetworkPlayerInfo#setDisplayName}. Strictly APPEND-only:
 * the server-supplied display name is kept as a prefix so the mod's own tab
 * parsers (first-bracket team token, first-§-code team colour) keep working.
 *
 * Hypixel resends UPDATE_DISPLAY_NAME packets that overwrite injected text,
 * so the suffix is re-applied on a timer from {@link BedwarsRuntime#onClientTick}.
 * Per-entry bookkeeping (keyed by tab UUID) makes the pass idempotent: an
 * entry whose current component is still ours is skipped unless the cached
 * stats object changed; anything else is treated as a fresh server base.
 *
 * Only already-cached stats are used — this class never fetches, so it cannot
 * eat into the 120 req/min API budget (cache warm-up is handled by the lobby
 * scan and render-fetch throttles).
 */
public class TabStatsInjector {

    /** Re-apply cadence in client ticks (~0.5s) — fast enough to win against server resends. */
    static final int INJECT_INTERVAL_TICKS = 10;

    /** Marker that opens every injected suffix; used to recognise and strip our own text. */
    static final String SUFFIX_MARKER = " §8| ";

    private final RuntimeState state;

    /** Per-entry injection bookkeeping, keyed by tab-entry UUID. */
    private final Map<UUID, InjectedEntry> injected = new HashMap<UUID, InjectedEntry>();

    /** Composed-suffix memo keyed by lowercase name; invalidated when the cached stats instance changes. */
    private final Map<String, CachedSuffix> suffixCache = new HashMap<String, CachedSuffix>();

    TabStatsInjector(RuntimeState state) {
        this.state = state;
    }

    /**
     * Timer-driven entry point. Injects while the feature is active and the
     * player is in a tracked lobby/match; restores touched entries the moment
     * any activation condition stops holding.
     */
    public void onClientTick(Minecraft mc) {
        if (state.clientTickCounter % INJECT_INTERVAL_TICKS != 0) {
            return;
        }

        if (mc == null || mc.getNetHandler() == null || mc.thePlayer == null || mc.theWorld == null) {
            // Connection/world gone — the NetworkPlayerInfo objects we touched
            // no longer exist, so there is nothing left to restore against.
            clearState();
            return;
        }

        boolean active = ModConfig.isModEnabled()
                && ModConfig.isTabStatsEnabled()
                && HypixelAPI.hasApiKey()
                && state.gamePhase != GamePhase.IDLE
                && !state.disconnectedFromGame;

        if (!active) {
            restoreAll(mc);
            return;
        }

        Collection<NetworkPlayerInfo> playerInfoMap = mc.getNetHandler().getPlayerInfoMap();
        if (playerInfoMap.isEmpty()) {
            return;
        }

        UUID localUuid = mc.thePlayer.getUniqueID();
        Set<UUID> presentUuids = new HashSet<UUID>();

        for (NetworkPlayerInfo info : playerInfoMap) {
            if (info == null || info.getGameProfile() == null) {
                continue;
            }

            UUID uuid = info.getGameProfile().getId();
            String name = info.getGameProfile().getName();
            if (uuid == null || name == null || name.isEmpty()) {
                continue;
            }
            presentUuids.add(uuid);

            if (uuid.equals(localUuid) || MatchThreatService.isWatchdogBotName(name)) {
                continue;
            }

            BedwarsStats stats = HypixelAPI.getCachedStats(name);
            if (stats == null || !stats.isLoaded()) {
                // Leave the entry alone — cache warm-up is the fetch paths' job.
                continue;
            }

            injectEntry(info, name, uuid, stats);
        }

        // Entries that left the tab list (server switch, quit) are gone for
        // good — their NetworkPlayerInfo objects were discarded with them.
        Iterator<Map.Entry<UUID, InjectedEntry>> it = injected.entrySet().iterator();
        while (it.hasNext()) {
            if (!presentUuids.contains(it.next().getKey())) {
                it.remove();
            }
        }
    }

    /**
     * Restore every touched entry to its pre-injection display name (or null
     * when the base was synthesized from team formatting), then forget all
     * bookkeeping. Entries the server already overwrote are left untouched.
     */
    public void restoreAll(Minecraft mc) {
        if (!injected.isEmpty() && mc != null && mc.getNetHandler() != null) {
            for (Map.Entry<UUID, InjectedEntry> mapEntry : injected.entrySet()) {
                NetworkPlayerInfo info = mc.getNetHandler().getPlayerInfo(mapEntry.getKey());
                if (info == null) {
                    continue;
                }
                InjectedEntry entry = mapEntry.getValue();
                if (entry.matchesInjected(info.getDisplayName())) {
                    info.setDisplayName(entry.baseSynthesized ? null : entry.originalComponent);
                }
            }
        }
        clearState();
    }

    private void clearState() {
        injected.clear();
        suffixCache.clear();
    }

    private void injectEntry(NetworkPlayerInfo info, String name, UUID uuid, BedwarsStats stats) {
        String suffix = suffixFor(name, stats);
        IChatComponent current = info.getDisplayName();
        InjectedEntry entry = injected.get(uuid);

        if (entry != null && entry.matchesInjected(current)) {
            // Our component is still installed. For synthesized bases, vanilla
            // would re-evaluate team formatting live every frame — the server
            // never resends a display name it never sent — so track scoreboard
            // team changes (game-start recolors, rejoins) ourselves.
            //
            // Only re-evaluate against a REAL team: formatPlayerName returns the
            // bare (uncoloured) name when the team is null, and getPlayerTeam()
            // blips null while Hypixel churns team packets early in a game. The
            // old `liveBase != null` guard was dead (formatPlayerName never
            // returns null for a non-empty name), so those blips clobbered the
            // cached colour and froze the name white for up to a tick interval —
            // the "name flashes white in tab" bug. Keep the last known colour
            // until a team read succeeds again.
            ScorePlayerTeam liveTeam = info.getPlayerTeam();
            if (entry.baseSynthesized && liveTeam != null) {
                String liveBase = ScorePlayerTeam.formatPlayerName(liveTeam, name);
                if (!liveBase.equals(entry.baseFormattedText)) {
                    entry.baseFormattedText = liveBase;
                    apply(info, entry, suffix);
                    return;
                }
            }
            // Rebuild only if the stats changed.
            if (suffix.equals(entry.suffix)) {
                return;
            }
            apply(info, entry, suffix);
            return;
        }

        // First touch, or the server resent the display name: the current
        // component (or its team-formatted synthesis) is the new base.
        ScorePlayerTeam freshTeam = info.getPlayerTeam();
        if (current == null && freshTeam == null) {
            // Nothing coloured to anchor to: the server sent no display name and
            // no scoreboard team has loaded yet. Synthesizing now would freeze
            // the bare white name in for a tick interval. Leave the slot to
            // vanilla (which renders the same bare name for this frame) and try
            // again next pass once a team exists — avoids a white flash on a
            // player's first appearance in tab.
            return;
        }
        if (entry == null) {
            entry = new InjectedEntry();
            injected.put(uuid, entry);
        }
        entry.originalComponent = current;
        entry.baseSynthesized = current == null;
        entry.baseFormattedText = current != null
                ? current.getFormattedText()
                : ScorePlayerTeam.formatPlayerName(freshTeam, name);
        apply(info, entry, suffix);
    }

    private void apply(NetworkPlayerInfo info, InjectedEntry entry, String suffix) {
        ChatComponentText composed = new ChatComponentText(entry.baseFormattedText + suffix);
        entry.suffix = suffix;
        entry.injectedComponent = composed;
        entry.injectedFormattedText = composed.getFormattedText();
        info.setDisplayName(composed);
    }

    private String suffixFor(String name, BedwarsStats stats) {
        String key = name.toLowerCase();
        CachedSuffix cached = suffixCache.get(key);
        if (cached != null && cached.stats == stats) {
            return cached.suffix;
        }
        String suffix = buildSuffix(stats);
        suffixCache.put(key, new CachedSuffix(stats, suffix));
        return suffix;
    }

    /**
     * Build the appended stat suffix for a loaded stats object:
     * " §8| <threatColor><stars>✫ §7<fkdr>". Nicked players get the
     * established "[NICK]" marker instead of meaningless zero stats.
     * Pure — covered by unit tests.
     */
    static String buildSuffix(BedwarsStats stats) {
        if (stats.getThreatLevel() == BedwarsStats.ThreatLevel.NICKED) {
            return SUFFIX_MARKER + stats.getThreatColor() + "[NICK]";
        }
        return SUFFIX_MARKER + stats.getThreatColor() + stats.getStars()
                + "✫ §7" + BedwarsStats.formatRatioShort(stats.getFkdr());
    }

    /**
     * Remove an injected stat suffix from a formatted tab name. Consumers that
     * scan the WHOLE display name (e.g. MatchThreatService's last-colour-code
     * team heuristic) must see the server's original text, otherwise the
     * suffix's trailing §7 would make every injected player look gray-teamed.
     * The " §8| " marker never occurs in Hypixel-supplied tab names.
     * Pure — covered by unit tests.
     */
    public static String stripInjectedSuffix(String formattedText) {
        if (formattedText == null) {
            return null;
        }
        int markerIndex = formattedText.indexOf(SUFFIX_MARKER);
        return markerIndex >= 0 ? formattedText.substring(0, markerIndex) : formattedText;
    }

    /** Bookkeeping for one touched tab entry. */
    private static class InjectedEntry {
        /** Component the server had set when we first (re-)injected; null if none. */
        IChatComponent originalComponent;
        /** True when the base was synthesized via team formatting — restore to null. */
        boolean baseSynthesized;
        /** Formatted text of the base the suffix is appended to. */
        String baseFormattedText;
        /** Stat suffix currently applied. */
        String suffix;
        /** The composed component we installed, plus its formatted text for equality checks. */
        IChatComponent injectedComponent;
        String injectedFormattedText;

        boolean matchesInjected(IChatComponent current) {
            if (current == null) {
                return false;
            }
            if (current == injectedComponent) {
                return true;
            }
            return injectedFormattedText != null
                    && injectedFormattedText.equals(current.getFormattedText());
        }
    }

    /** Suffix memo entry — valid only while the same stats instance is cached. */
    private static class CachedSuffix {
        final BedwarsStats stats;
        final String suffix;

        CachedSuffix(BedwarsStats stats, String suffix) {
            this.stats = stats;
            this.suffix = suffix;
        }
    }
}
