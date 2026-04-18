package com.imshy.bedwars;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Data class holding Bedwars statistics for a player.
 *
 * Parses the Hypixel API response with Gson, extracting both career counters
 * and Hypixel's rolling monthly/weekly buckets. The rolling buckets are
 * summed across the {@code _a}/{@code _b} ping-pong slots that Hypixel uses
 * internally to keep the time window fresh.
 */
public class BedwarsStats {

    public enum ThreatLevel {
        UNKNOWN, // Could not fetch stats
        LOW, // Beginner/casual player
        MEDIUM, // Average player
        HIGH, // Skilled player
        EXTREME, // Very dangerous player
        NICKED // Player is using a Hypixel nick (alias) — real identity hidden
    }

    /** Recent-form summary used by HUD/overlay code. */
    public enum RecentWindow {
        NONE,
        WEEKLY,
        MONTHLY
    }

    private final String playerName;
    private final String uuid;

    // Core (career) stats
    private int stars = 0;
    private int finalKills = 0;
    private int finalDeaths = 0;
    private int wins = 0;
    private int losses = 0;
    private int bedsBroken = 0;
    private double fkdr = 0.0;
    private double wlr = 0.0;

    // Rolling monthly counters (Hypixel _monthly_a + _monthly_b)
    private int monthlyFinalKills = 0;
    private int monthlyFinalDeaths = 0;
    private int monthlyWins = 0;
    private int monthlyLosses = 0;
    private double monthlyFkdr = 0.0;
    private double monthlyWlr = 0.0;

    // Rolling weekly counters (Hypixel _weekly_a + _weekly_b)
    private int weeklyFinalKills = 0;
    private int weeklyFinalDeaths = 0;
    private int weeklyWins = 0;
    private int weeklyLosses = 0;
    private double weeklyFkdr = 0.0;
    private double weeklyWlr = 0.0;

    private boolean loaded = false;
    private boolean error = false;
    private String errorMessage = null;
    private boolean nicked = false;

    /**
     * Minimum recent final-kill volume required to consider a windowed FKDR
     * representative. Below this, a player has not played enough games for the
     * recent ratio to be meaningful and we fall back to the next window.
     */
    private static final int MIN_RECENT_FK_FOR_RELIABLE_FKDR = 25;

    public BedwarsStats(String playerName, String uuid) {
        this.playerName = playerName;
        this.uuid = uuid;
    }

    /**
     * Build a stats object representing a nicked (alias) player for whom no real
     * identity could be resolved — e.g. Mojang returned HTTP 404 for the name.
     */
    public static BedwarsStats createNicked(String playerName) {
        BedwarsStats stats = new BedwarsStats(playerName, "");
        stats.loaded = true;
        stats.nicked = true;
        return stats;
    }

    /**
     * Parse stats from Hypixel API JSON response using Gson. The response is
     * expected to look like:
     *   { "success": true, "player": { "stats": { "Bedwars": { ... } } } }
     */
    public void parseFromJson(String jsonResponse) {
        try {
            if (jsonResponse == null) {
                error = true;
                errorMessage = "Empty API response";
                return;
            }

            JsonElement parsed = new JsonParser().parse(jsonResponse);
            if (parsed == null || !parsed.isJsonObject()) {
                error = true;
                errorMessage = "Malformed API response";
                return;
            }

            JsonObject root = parsed.getAsJsonObject();
            if (!getBoolean(root, "success")) {
                error = true;
                errorMessage = "API request failed";
                return;
            }

            // "player":null indicates an account that has never logged in to
            // Hypixel — a strong nick signal.
            JsonElement playerEl = root.get("player");
            if (playerEl == null || playerEl.isJsonNull()) {
                loaded = true;
                nicked = true;
                return;
            }

            JsonObject player = playerEl.getAsJsonObject();
            JsonObject stats = getObject(player, "stats");
            if (stats == null) {
                loaded = true;
                nicked = true;
                return;
            }

            JsonObject bw = getObject(stats, "Bedwars");
            if (bw == null) {
                // Account exists but no Bedwars play history — almost always
                // a nick when seen as an opponent in-game.
                loaded = true;
                nicked = true;
                return;
            }

            this.stars = calculateStars(getInt(bw, "Experience"));

            this.finalKills = getInt(bw, "final_kills_bedwars");
            this.finalDeaths = getInt(bw, "final_deaths_bedwars");
            this.wins = getInt(bw, "wins_bedwars");
            this.losses = getInt(bw, "losses_bedwars");
            this.bedsBroken = getInt(bw, "beds_broken_bedwars");
            this.fkdr = computeRatio(finalKills, finalDeaths);
            this.wlr = computeRatio(wins, losses);

            // Rolling monthly + weekly windows. Hypixel maintains two ping-pong
            // buckets (_a/_b) per stat that together cover the full window.
            this.monthlyFinalKills = sumBuckets(bw, "final_kills_bedwars_monthly");
            this.monthlyFinalDeaths = sumBuckets(bw, "final_deaths_bedwars_monthly");
            this.monthlyWins = sumBuckets(bw, "wins_bedwars_monthly");
            this.monthlyLosses = sumBuckets(bw, "losses_bedwars_monthly");
            this.monthlyFkdr = computeRatio(monthlyFinalKills, monthlyFinalDeaths);
            this.monthlyWlr = computeRatio(monthlyWins, monthlyLosses);

            this.weeklyFinalKills = sumBuckets(bw, "final_kills_bedwars_weekly");
            this.weeklyFinalDeaths = sumBuckets(bw, "final_deaths_bedwars_weekly");
            this.weeklyWins = sumBuckets(bw, "wins_bedwars_weekly");
            this.weeklyLosses = sumBuckets(bw, "losses_bedwars_weekly");
            this.weeklyFkdr = computeRatio(weeklyFinalKills, weeklyFinalDeaths);
            this.weeklyWlr = computeRatio(weeklyWins, weeklyLosses);

            // All zero after a successful parse => Bedwars section exists but
            // is empty — matches a nicked account.
            if (stars == 0 && finalKills == 0 && finalDeaths == 0
                    && wins == 0 && losses == 0 && bedsBroken == 0) {
                nicked = true;
            }

            loaded = true;

        } catch (Exception e) {
            error = true;
            errorMessage = e.getMessage();
        }
    }

    private static boolean getBoolean(JsonObject obj, String key) {
        if (obj == null) return false;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return false;
        try {
            return el.getAsBoolean();
        } catch (Exception e) {
            return false;
        }
    }

    private static int getInt(JsonObject obj, String key) {
        if (obj == null) return 0;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return 0;
        try {
            return el.getAsInt();
        } catch (Exception e) {
            return 0;
        }
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null) return null;
        JsonElement el = obj.get(key);
        if (el == null || el.isJsonNull() || !el.isJsonObject()) return null;
        return el.getAsJsonObject();
    }

    /**
     * Sum the {@code _a} and {@code _b} buckets that Hypixel uses to maintain
     * a rolling window for monthly/weekly counters. Either bucket may be
     * missing if the player has not played in that period.
     */
    private static int sumBuckets(JsonObject bw, String prefix) {
        return getInt(bw, prefix + "_a") + getInt(bw, prefix + "_b");
    }

    private static double computeRatio(int num, int den) {
        if (den > 0) {
            return (double) num / den;
        }
        return num > 0 ? Double.MAX_VALUE : 0.0;
    }

    /**
     * Calculate Bedwars stars from experience.
     * Based on Hypixel's leveling formula.
     */
    private int calculateStars(int exp) {
        if (exp < 0) {
            return 0;
        }

        int level = 0;
        int remaining = exp;

        // Experience thresholds per level (repeats every 100 levels with prestige bonus)
        int[] levelThresholds = { 500, 1000, 2000, 3500, 5000 };

        while (remaining > 0) {
            int threshold = levelThresholds[Math.min(level % 100, 4)];
            if (level >= 100) {
                threshold += (level / 100) * 500;
            }

            if (remaining >= threshold) {
                remaining -= threshold;
                level++;
            } else {
                break;
            }

            if (level > 5000) {
                break;
            }
        }

        return level;
    }

    /**
     * Determine threat level based on stats.
     */
    public ThreatLevel getThreatLevel() {
        if (error || !loaded)
            return ThreatLevel.UNKNOWN;

        if (nicked && ModConfig.isNickDetectionEnabled()) {
            return ThreatLevel.NICKED;
        }

        if (stars >= 500 || fkdr >= 6.0) {
            return ThreatLevel.EXTREME;
        }
        if (stars >= 300 || fkdr >= 4.0) {
            return ThreatLevel.HIGH;
        }
        if (stars >= 100 || fkdr >= 2.0) {
            return ThreatLevel.MEDIUM;
        }
        return ThreatLevel.LOW;
    }

    public String getThreatColor() {
        switch (getThreatLevel()) {
            case EXTREME:
                return "\u00A74"; // Dark Red
            case HIGH:
                return "\u00A7c"; // Red
            case MEDIUM:
                return "\u00A7e"; // Yellow
            case LOW:
                return "\u00A7a"; // Green
            case NICKED:
                return "\u00A7d"; // Light Purple
            default:
                return "\u00A77"; // Gray
        }
    }

    public static String formatRatio(double ratio) {
        if (ratio == Double.MAX_VALUE) {
            return "\u221e";
        }
        return String.format("%.2f", ratio);
    }

    public static String formatRatioShort(double ratio) {
        if (ratio == Double.MAX_VALUE) {
            return "\u221e";
        }
        return String.format("%.1f", ratio);
    }

    public String getDisplayString() {
        if (error) {
            return "\u00A77[Error]";
        }
        if (!loaded) {
            return "\u00A77[Loading...]";
        }
        if (getThreatLevel() == ThreatLevel.NICKED) {
            return "\u00A7d[NICK]";
        }

        String color = getThreatColor();
        return color + "[" + stars + "\u2B50] " + formatRatio(fkdr) + " FKDR";
    }

    /**
     * Pick the most representative recent FKDR. Prefers the monthly window if
     * the player has played enough finals there; otherwise tries weekly;
     * otherwise falls back to career.
     */
    public double getRecentFkdr() {
        RecentWindow window = getRecentWindow();
        switch (window) {
            case MONTHLY:
                return monthlyFkdr;
            case WEEKLY:
                return weeklyFkdr;
            default:
                return fkdr;
        }
    }

    public RecentWindow getRecentWindow() {
        if (monthlyFinalKills + monthlyFinalDeaths >= MIN_RECENT_FK_FOR_RELIABLE_FKDR) {
            return RecentWindow.MONTHLY;
        }
        if (weeklyFinalKills + weeklyFinalDeaths >= MIN_RECENT_FK_FOR_RELIABLE_FKDR) {
            return RecentWindow.WEEKLY;
        }
        return RecentWindow.NONE;
    }

    /**
     * Difference between recent FKDR and career FKDR. Returns 0 when no
     * recent window has enough samples or career FKDR is unset.
     * A positive value means the player is currently outperforming their
     * lifetime average ("hot"); negative means they're slumping.
     */
    public double getRecentFkdrDelta() {
        if (getRecentWindow() == RecentWindow.NONE) {
            return 0.0;
        }
        double recent = getRecentFkdr();
        if (recent == Double.MAX_VALUE || fkdr == Double.MAX_VALUE) {
            return 0.0;
        }
        return recent - fkdr;
    }

    /** Compact "MO"/"WK" tag for the active recent window. */
    public String getRecentWindowLabel() {
        switch (getRecentWindow()) {
            case MONTHLY:
                return "MO";
            case WEEKLY:
                return "WK";
            default:
                return "";
        }
    }

    public boolean isNicked() {
        return nicked;
    }

    public String getPlayerName() {
        return playerName;
    }

    public String getUuid() {
        return uuid;
    }

    public int getStars() {
        return stars;
    }

    public int getFinalKills() {
        return finalKills;
    }

    public int getFinalDeaths() {
        return finalDeaths;
    }

    public double getFkdr() {
        return fkdr;
    }

    public int getWins() {
        return wins;
    }

    public int getLosses() {
        return losses;
    }

    public double getWlr() {
        return wlr;
    }

    public int getBedsBroken() {
        return bedsBroken;
    }

    public int getMonthlyFinalKills() {
        return monthlyFinalKills;
    }

    public int getMonthlyFinalDeaths() {
        return monthlyFinalDeaths;
    }

    public double getMonthlyFkdr() {
        return monthlyFkdr;
    }

    public int getMonthlyWins() {
        return monthlyWins;
    }

    public int getMonthlyLosses() {
        return monthlyLosses;
    }

    public double getMonthlyWlr() {
        return monthlyWlr;
    }

    public int getWeeklyFinalKills() {
        return weeklyFinalKills;
    }

    public int getWeeklyFinalDeaths() {
        return weeklyFinalDeaths;
    }

    public double getWeeklyFkdr() {
        return weeklyFkdr;
    }

    public int getWeeklyWins() {
        return weeklyWins;
    }

    public int getWeeklyLosses() {
        return weeklyLosses;
    }

    public double getWeeklyWlr() {
        return weeklyWlr;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public boolean hasError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
