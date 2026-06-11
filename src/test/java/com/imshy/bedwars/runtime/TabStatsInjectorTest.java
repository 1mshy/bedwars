package com.imshy.bedwars.runtime;

import com.imshy.bedwars.BedwarsStats;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for the pure parts of {@link TabStatsInjector}: the appended stat
 * suffix ({@link TabStatsInjector#buildSuffix}) and the suffix stripper used by
 * whole-name parsers ({@link TabStatsInjector#stripInjectedSuffix}).
 *
 * Threat colours assume the default ModConfig thresholds (100/300/500 stars,
 * 2.0/4.0/6.0 FKDR), same as BedwarsStatsTest.
 */
public class TabStatsInjectorTest {

    private Locale originalLocale;

    @Before
    public void pinLocale() {
        // formatRatioShort uses String.format with the JVM default locale; pin
        // it so decimal-point assertions don't break on decimal-comma locales.
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    // ── helpers (same fixture recipe as BedwarsStatsTest) ────────────────────

    private static BedwarsStats parse(String json) {
        BedwarsStats stats = new BedwarsStats("TestPlayer", "uuid-1234");
        stats.parseFromJson(json);
        return stats;
    }

    private static String bedwarsJson(String bedwarsBody) {
        return "{\"success\":true,\"player\":{\"stats\":{\"Bedwars\":{" + bedwarsBody + "}}}}";
    }

    /**
     * Exact XP needed to reach a star count, mirroring Hypixel's per-prestige
     * cost table (500/1000/2000/3500 for the first four levels of every
     * prestige, flat 5000 after).
     */
    private static int xpForStars(int stars) {
        int[] costs = { 500, 1000, 2000, 3500, 5000 };
        int xp = 0;
        for (int level = 0; level < stars; level++) {
            xp += costs[Math.min(level % 100, 4)];
        }
        return xp;
    }

    private static BedwarsStats statsWith(int stars, int finalKills, int finalDeaths) {
        return parse(bedwarsJson("\"Experience\":" + xpForStars(stars)
                + ",\"final_kills_bedwars\":" + finalKills
                + ",\"final_deaths_bedwars\":" + finalDeaths));
    }

    // ── buildSuffix ──────────────────────────────────────────────────────────

    @Test
    public void lowThreatSuffixIsGreenStarsGrayFkdr() {
        // 10✫, 15/10 = 1.5 FKDR → LOW (green)
        assertEquals(" §8| §a10✫ §71.5", TabStatsInjector.buildSuffix(statsWith(10, 15, 10)));
    }

    @Test
    public void mediumThreatSuffixIsYellow() {
        // 100✫ crosses the medium star threshold
        assertEquals(" §8| §e100✫ §71.5", TabStatsInjector.buildSuffix(statsWith(100, 15, 10)));
    }

    @Test
    public void highThreatSuffixIsRed() {
        // 300✫ crosses the high star threshold
        assertEquals(" §8| §c300✫ §71.5", TabStatsInjector.buildSuffix(statsWith(300, 15, 10)));
    }

    @Test
    public void extremeThreatSuffixIsDarkRed() {
        // 500✫ crosses the extreme star threshold
        assertEquals(" §8| §4500✫ §71.5", TabStatsInjector.buildSuffix(statsWith(500, 15, 10)));
    }

    @Test
    public void infiniteFkdrRendersInfinitySign() {
        // 5 final kills, 0 deaths → Double.MAX_VALUE sentinel → "∞" (and EXTREME)
        assertEquals(" §8| §40✫ §7∞", TabStatsInjector.buildSuffix(statsWith(0, 5, 0)));
    }

    @Test
    public void nickedStatsGetNickMarkerInsteadOfZeroStats() {
        assertEquals(" §8| §d[NICK]", TabStatsInjector.buildSuffix(BedwarsStats.createNicked("SomeNick")));
    }

    @Test
    public void suffixStartsWithMarkerAndNeverPrepends() {
        // Append-only contract: every suffix opens with the marker, so the
        // base's first §-code and first bracket stay first in the composite.
        assertTrue(TabStatsInjector.buildSuffix(statsWith(10, 15, 10))
                .startsWith(TabStatsInjector.SUFFIX_MARKER));
        assertTrue(TabStatsInjector.buildSuffix(BedwarsStats.createNicked("SomeNick"))
                .startsWith(TabStatsInjector.SUFFIX_MARKER));
    }

    // ── stripInjectedSuffix ──────────────────────────────────────────────────

    @Test
    public void stripRoundTripsAnInjectedName() {
        String base = "§c[R] §cPlayerOne§r";
        String composed = base + TabStatsInjector.buildSuffix(statsWith(300, 15, 10));
        assertEquals(base, TabStatsInjector.stripInjectedSuffix(composed));
    }

    @Test
    public void stripLeavesUninjectedNamesUntouched() {
        String serverName = "§7[42✫] §aPlayerTwo§r";
        assertSame(serverName, TabStatsInjector.stripInjectedSuffix(serverName));
    }

    @Test
    public void stripCutsFromTheFirstMarker() {
        // Pathological double marker: everything from the first one goes.
        String composed = "Name §8| §a1✫ §70.5 §8| junk";
        assertEquals("Name", TabStatsInjector.stripInjectedSuffix(composed));
    }

    @Test
    public void stripHandlesNullAndEmpty() {
        assertNull(TabStatsInjector.stripInjectedSuffix(null));
        assertEquals("", TabStatsInjector.stripInjectedSuffix(""));
    }

    @Test
    public void stripPreservesLastColourCodeSemantics() {
        // The MatchThreatService team heuristic takes the LAST colour code of
        // the formatted name; stripping must restore the server's own last
        // code (here §c) instead of the suffix's trailing §7.
        String base = "§c[R] §cPlayerOne";
        String composed = base + TabStatsInjector.buildSuffix(statsWith(10, 15, 10));
        String stripped = TabStatsInjector.stripInjectedSuffix(composed);
        int lastCode = stripped.lastIndexOf('§');
        assertEquals('c', stripped.charAt(lastCode + 1));
    }
}
