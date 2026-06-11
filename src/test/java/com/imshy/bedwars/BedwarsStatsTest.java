package com.imshy.bedwars;

import com.imshy.bedwars.BedwarsStats.RecentWindow;
import com.imshy.bedwars.BedwarsStats.ThreatLevel;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for {@link BedwarsStats}: JSON parse branches, star calculation,
 * the {@code Double.MAX_VALUE} infinity sentinel, recent-window selection and
 * threat-level boundaries at the default ModConfig thresholds
 * (100/300/500 stars, 2.0/4.0/6.0 FKDR).
 */
public class BedwarsStatsTest {

    private Locale originalLocale;

    @Before
    public void pinLocale() {
        // formatRatio/formatRatioShort use String.format with the JVM default
        // locale; pin it so decimal-point assertions don't break on machines
        // whose locale uses a decimal comma (e.g. it_IT).
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.US);
    }

    @After
    public void restoreLocale() {
        Locale.setDefault(originalLocale);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static BedwarsStats parse(String json) {
        BedwarsStats stats = new BedwarsStats("TestPlayer", "uuid-1234");
        stats.parseFromJson(json);
        return stats;
    }

    /** Wraps a Bedwars stats body in the full Hypixel response envelope. */
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

    /** Stats with an exact star count and career final kill/death counters. */
    private static BedwarsStats statsWith(int stars, int finalKills, int finalDeaths) {
        return parse(bedwarsJson("\"Experience\":" + xpForStars(stars)
                + ",\"final_kills_bedwars\":" + finalKills
                + ",\"final_deaths_bedwars\":" + finalDeaths));
    }

    /** Stats with career + rolling monthly/weekly final kill counters. */
    private static BedwarsStats recentStats(int careerKills, int careerDeaths,
            int monthlyKills, int monthlyDeaths, int weeklyKills, int weeklyDeaths) {
        return parse(bedwarsJson("\"Experience\":500"
                + ",\"final_kills_bedwars\":" + careerKills
                + ",\"final_deaths_bedwars\":" + careerDeaths
                + ",\"final_kills_bedwars_monthly_a\":" + monthlyKills
                + ",\"final_deaths_bedwars_monthly_a\":" + monthlyDeaths
                + ",\"final_kills_bedwars_weekly_a\":" + weeklyKills
                + ",\"final_deaths_bedwars_weekly_a\":" + weeklyDeaths));
    }

    // ── parseFromJson error branches ─────────────────────────────────────────

    @Test
    public void nullResponseSetsEmptyError() {
        BedwarsStats stats = parse(null);
        assertTrue(stats.hasError());
        assertEquals("Empty API response", stats.getErrorMessage());
        assertFalse(stats.isLoaded());
    }

    @Test
    public void nonObjectResponseSetsMalformedError() {
        BedwarsStats array = parse("[]");
        assertTrue(array.hasError());
        assertEquals("Malformed API response", array.getErrorMessage());

        BedwarsStats primitive = parse("42");
        assertTrue(primitive.hasError());
        assertEquals("Malformed API response", primitive.getErrorMessage());
    }

    @Test
    public void truncatedJsonSetsError() {
        // Gson throws on truncated input; parseFromJson swallows it into the
        // error flag rather than propagating.
        BedwarsStats stats = parse("{\"success\":");
        assertTrue(stats.hasError());
        assertFalse(stats.isLoaded());
    }

    @Test
    public void successFalseSetsApiError() {
        BedwarsStats stats = parse("{\"success\":false,\"cause\":\"Invalid API key\"}");
        assertTrue(stats.hasError());
        assertEquals("API request failed", stats.getErrorMessage());
    }

    @Test
    public void missingSuccessFieldSetsApiError() {
        BedwarsStats stats = parse("{}");
        assertTrue(stats.hasError());
        assertEquals("API request failed", stats.getErrorMessage());
    }

    @Test
    public void nonObjectPlayerSetsError() {
        // "player":5 survives the null check but explodes on getAsJsonObject();
        // the catch-all turns that into the error flag.
        BedwarsStats stats = parse("{\"success\":true,\"player\":5}");
        assertTrue(stats.hasError());
        assertFalse(stats.isLoaded());
    }

    // ── parseFromJson nick branches ──────────────────────────────────────────

    @Test
    public void nullPlayerMarksNicked() {
        BedwarsStats stats = parse("{\"success\":true,\"player\":null}");
        assertTrue(stats.isLoaded());
        assertTrue(stats.isNicked());
        assertFalse(stats.hasError());
    }

    @Test
    public void missingStatsMarksNicked() {
        BedwarsStats stats = parse("{\"success\":true,\"player\":{}}");
        assertTrue(stats.isLoaded());
        assertTrue(stats.isNicked());
    }

    @Test
    public void missingBedwarsSectionMarksNicked() {
        BedwarsStats stats = parse("{\"success\":true,\"player\":{\"stats\":{}}}");
        assertTrue(stats.isLoaded());
        assertTrue(stats.isNicked());
    }

    @Test
    public void allZeroStatsMarkNicked() {
        BedwarsStats stats = parse(bedwarsJson(""));
        assertTrue(stats.isLoaded());
        assertTrue(stats.isNicked());
        assertFalse(stats.hasError());
    }

    @Test
    public void createNickedFactorySetsFlags() {
        BedwarsStats stats = BedwarsStats.createNicked("SomeNick");
        assertTrue(stats.isLoaded());
        assertTrue(stats.isNicked());
        assertEquals("SomeNick", stats.getPlayerName());
        assertEquals("", stats.getUuid());
    }

    // ── parseFromJson success path ───────────────────────────────────────────

    @Test
    public void fullResponseParsesAllFields() {
        BedwarsStats stats = parse(bedwarsJson("\"Experience\":487000"
                + ",\"final_kills_bedwars\":300,\"final_deaths_bedwars\":100"
                + ",\"wins_bedwars\":80,\"losses_bedwars\":40"
                + ",\"beds_broken_bedwars\":150"
                + ",\"final_kills_bedwars_monthly_a\":20,\"final_kills_bedwars_monthly_b\":10"
                + ",\"final_deaths_bedwars_monthly_a\":10"
                + ",\"final_kills_bedwars_weekly_a\":5"
                + ",\"final_deaths_bedwars_weekly_a\":2"));

        assertTrue(stats.isLoaded());
        assertFalse(stats.isNicked());
        assertFalse(stats.hasError());
        assertNull(stats.getErrorMessage());

        assertEquals(100, stats.getStars()); // exactly one full prestige
        assertEquals(300, stats.getFinalKills());
        assertEquals(100, stats.getFinalDeaths());
        assertEquals(80, stats.getWins());
        assertEquals(40, stats.getLosses());
        assertEquals(150, stats.getBedsBroken());
        assertEquals(3.0, stats.getFkdr(), 1e-9);
        assertEquals(2.0, stats.getWlr(), 1e-9);

        // _a/_b ping-pong buckets are summed
        assertEquals(30, stats.getMonthlyFinalKills());
        assertEquals(10, stats.getMonthlyFinalDeaths());
        assertEquals(3.0, stats.getMonthlyFkdr(), 1e-9);
        assertEquals(5, stats.getWeeklyFinalKills());
        assertEquals(2, stats.getWeeklyFinalDeaths());
        assertEquals(2.5, stats.getWeeklyFkdr(), 1e-9);
    }

    // ── star calculation ─────────────────────────────────────────────────────

    @Test
    public void starsFollowPerLevelCostTable() {
        // 500/1000/2000/3500 for the first four levels, flat 5000 after
        assertEquals(0, statsWith(0, 1, 0).getStars());
        assertEquals(0, parse(bedwarsJson("\"Experience\":499,\"final_kills_bedwars\":1")).getStars());
        assertEquals(1, parse(bedwarsJson("\"Experience\":500")).getStars());
        assertEquals(1, parse(bedwarsJson("\"Experience\":1499")).getStars());
        assertEquals(2, parse(bedwarsJson("\"Experience\":1500")).getStars());
        assertEquals(2, parse(bedwarsJson("\"Experience\":3499")).getStars());
        assertEquals(3, parse(bedwarsJson("\"Experience\":3500")).getStars());
        assertEquals(3, parse(bedwarsJson("\"Experience\":6999")).getStars());
        assertEquals(4, parse(bedwarsJson("\"Experience\":7000")).getStars());
        assertEquals(4, parse(bedwarsJson("\"Experience\":11999")).getStars());
        assertEquals(5, parse(bedwarsJson("\"Experience\":12000")).getStars());
        assertEquals(5, parse(bedwarsJson("\"Experience\":16999")).getStars());
        assertEquals(6, parse(bedwarsJson("\"Experience\":17000")).getStars());
    }

    @Test
    public void fullPrestigeCosts487000Xp() {
        // 500+1000+2000+3500+96*5000 = 487,000 — and the cost table resets each prestige
        assertEquals(99, parse(bedwarsJson("\"Experience\":486999")).getStars());
        assertEquals(100, parse(bedwarsJson("\"Experience\":487000")).getStars());
        assertEquals(101, parse(bedwarsJson("\"Experience\":487500")).getStars());
        assertEquals(200, parse(bedwarsJson("\"Experience\":974000")).getStars());
    }

    @Test
    public void starsRoundTripAtVariousLevels() {
        int[] levels = { 1, 4, 5, 99, 100, 101, 250, 1000 };
        for (int level : levels) {
            assertEquals("stars for xp of level " + level,
                    level, parse(bedwarsJson("\"Experience\":" + xpForStars(level))).getStars());
            assertEquals("stars one XP short of level " + level,
                    level - 1, parse(bedwarsJson("\"Experience\":" + (xpForStars(level) - 1)
                            + ",\"final_kills_bedwars\":1")).getStars());
        }
    }

    @Test
    public void negativeExperienceYieldsZeroStars() {
        assertEquals(0, parse(bedwarsJson("\"Experience\":-100,\"final_kills_bedwars\":1")).getStars());
    }

    @Test
    public void starCapBreaksAt5001() {
        // The cap check runs after the increment, so the loop exits at 5001.
        assertEquals(5001, parse(bedwarsJson("\"Experience\":" + Integer.MAX_VALUE)).getStars());
    }

    // ── computeRatio sentinel & formatting ──────────────────────────────────

    @Test
    public void zeroDeathsWithKillsYieldsMaxValueSentinel() {
        BedwarsStats stats = statsWith(1, 10, 0);
        assertEquals(Double.MAX_VALUE, stats.getFkdr(), 0.0);
        assertEquals("∞", BedwarsStats.formatRatio(stats.getFkdr()));
    }

    @Test
    public void zeroKillsZeroDeathsYieldsZeroRatio() {
        BedwarsStats stats = statsWith(1, 0, 0);
        assertEquals(0.0, stats.getFkdr(), 0.0);
    }

    @Test
    public void formatRatioRendersTwoDecimalsAndInfinity() {
        assertEquals("1.50", BedwarsStats.formatRatio(1.5));
        assertEquals("0.00", BedwarsStats.formatRatio(0.0));
        assertEquals("3.33", BedwarsStats.formatRatio(10.0 / 3.0));
        assertEquals("∞", BedwarsStats.formatRatio(Double.MAX_VALUE));
    }

    @Test
    public void formatRatioShortRendersOneDecimalAndInfinity() {
        assertEquals("1.5", BedwarsStats.formatRatioShort(1.5));
        assertEquals("2.3", BedwarsStats.formatRatioShort(2.34));
        assertEquals("∞", BedwarsStats.formatRatioShort(Double.MAX_VALUE));
    }

    // ── recent window selection (25-sample cutoff) ───────────────────────────

    @Test
    public void recentWindowPrefersMonthlyAt25Samples() {
        BedwarsStats stats = recentStats(100, 100, 15, 10, 0, 0); // monthly sum exactly 25
        assertEquals(RecentWindow.MONTHLY, stats.getRecentWindow());
        assertEquals("MO", stats.getRecentWindowLabel());
        assertEquals(1.5, stats.getRecentFkdr(), 1e-9);
    }

    @Test
    public void recentWindowFallsBackToWeekly() {
        BedwarsStats stats = recentStats(100, 100, 14, 10, 15, 10); // monthly 24, weekly 25
        assertEquals(RecentWindow.WEEKLY, stats.getRecentWindow());
        assertEquals("WK", stats.getRecentWindowLabel());
        assertEquals(1.5, stats.getRecentFkdr(), 1e-9);
    }

    @Test
    public void recentWindowNoneBelowCutoff() {
        BedwarsStats stats = recentStats(100, 50, 14, 10, 14, 10); // both sums 24
        assertEquals(RecentWindow.NONE, stats.getRecentWindow());
        assertEquals("", stats.getRecentWindowLabel());
        // falls back to career FKDR
        assertEquals(2.0, stats.getRecentFkdr(), 1e-9);
    }

    @Test
    public void recentWindowSumsPingPongBuckets() {
        BedwarsStats stats = parse(bedwarsJson("\"Experience\":500"
                + ",\"final_kills_bedwars\":100,\"final_deaths_bedwars\":100"
                + ",\"final_kills_bedwars_monthly_a\":13"
                + ",\"final_kills_bedwars_monthly_b\":12"));
        assertEquals(25, stats.getMonthlyFinalKills());
        assertEquals(RecentWindow.MONTHLY, stats.getRecentWindow());
    }

    // ── recent FKDR delta ────────────────────────────────────────────────────

    @Test
    public void recentFkdrDeltaZeroWhenNoWindow() {
        BedwarsStats stats = recentStats(100, 50, 0, 0, 0, 0);
        assertEquals(0.0, stats.getRecentFkdrDelta(), 0.0);
    }

    @Test
    public void recentFkdrDeltaIsRecentMinusCareer() {
        BedwarsStats stats = recentStats(200, 100, 30, 10, 0, 0); // career 2.0, monthly 3.0
        assertEquals(1.0, stats.getRecentFkdrDelta(), 1e-9);
    }

    @Test
    public void recentFkdrDeltaZeroWhenRecentInfinite() {
        BedwarsStats stats = recentStats(200, 100, 25, 0, 0, 0); // monthly = MAX_VALUE sentinel
        assertEquals(RecentWindow.MONTHLY, stats.getRecentWindow());
        assertEquals(0.0, stats.getRecentFkdrDelta(), 0.0);
    }

    @Test
    public void recentFkdrDeltaZeroWhenCareerInfinite() {
        BedwarsStats stats = recentStats(10, 0, 20, 5, 0, 0); // career = MAX_VALUE sentinel
        assertEquals(0.0, stats.getRecentFkdrDelta(), 0.0);
    }

    // ── threat level at default thresholds ───────────────────────────────────

    @Test
    public void threatUnknownWhenNotLoaded() {
        BedwarsStats stats = new BedwarsStats("TestPlayer", "uuid-1234");
        assertEquals(ThreatLevel.UNKNOWN, stats.getThreatLevel());
    }

    @Test
    public void threatUnknownOnError() {
        assertEquals(ThreatLevel.UNKNOWN, parse(null).getThreatLevel());
    }

    @Test
    public void nickedPlayersReportNickedThreat() {
        // default nickDetectionEnabled = true
        assertEquals(ThreatLevel.NICKED, BedwarsStats.createNicked("SomeNick").getThreatLevel());
        assertEquals(ThreatLevel.NICKED,
                parse("{\"success\":true,\"player\":null}").getThreatLevel());
    }

    @Test
    public void starThresholdBoundaries() {
        // FKDR pinned at 1.0 (below the 2.0 MEDIUM boundary)
        assertEquals(ThreatLevel.LOW, statsWith(99, 10, 10).getThreatLevel());
        assertEquals(ThreatLevel.MEDIUM, statsWith(100, 10, 10).getThreatLevel());
        assertEquals(ThreatLevel.MEDIUM, statsWith(299, 10, 10).getThreatLevel());
        assertEquals(ThreatLevel.HIGH, statsWith(300, 10, 10).getThreatLevel());
        assertEquals(ThreatLevel.HIGH, statsWith(499, 10, 10).getThreatLevel());
        assertEquals(ThreatLevel.EXTREME, statsWith(500, 10, 10).getThreatLevel());
    }

    @Test
    public void fkdrThresholdBoundaries() {
        // stars pinned at 1 (below the 100-star MEDIUM boundary)
        assertEquals(ThreatLevel.LOW, statsWith(1, 199, 100).getThreatLevel());      // 1.99
        assertEquals(ThreatLevel.MEDIUM, statsWith(1, 20, 10).getThreatLevel());     // 2.0
        assertEquals(ThreatLevel.MEDIUM, statsWith(1, 399, 100).getThreatLevel());   // 3.99
        assertEquals(ThreatLevel.HIGH, statsWith(1, 40, 10).getThreatLevel());       // 4.0
        assertEquals(ThreatLevel.HIGH, statsWith(1, 599, 100).getThreatLevel());     // 5.99
        assertEquals(ThreatLevel.EXTREME, statsWith(1, 60, 10).getThreatLevel());    // 6.0
    }

    @Test
    public void infiniteFkdrIsExtreme() {
        assertEquals(ThreatLevel.EXTREME, statsWith(1, 5, 0).getThreatLevel());
    }

    @Test
    public void threatColorsMatchLevels() {
        assertEquals("§4", statsWith(500, 10, 10).getThreatColor()); // EXTREME
        assertEquals("§c", statsWith(300, 10, 10).getThreatColor()); // HIGH
        assertEquals("§e", statsWith(100, 10, 10).getThreatColor()); // MEDIUM
        assertEquals("§a", statsWith(1, 10, 10).getThreatColor());   // LOW
        assertEquals("§d", BedwarsStats.createNicked("X").getThreatColor()); // NICKED
        assertEquals("§7", new BedwarsStats("X", "").getThreatColor());      // UNKNOWN
    }

    @Test
    public void displayStringStates() {
        assertEquals("§7[Error]", parse(null).getDisplayString());
        assertEquals("§7[Loading...]", new BedwarsStats("X", "").getDisplayString());
        assertEquals("§d[NICK]", BedwarsStats.createNicked("X").getDisplayString());
        // 100 stars / 3.00 FKDR → MEDIUM (yellow)
        BedwarsStats stats = statsWith(100, 30, 10);
        assertEquals("§e[100⭐] 3.00 FKDR", stats.getDisplayString());
    }
}
