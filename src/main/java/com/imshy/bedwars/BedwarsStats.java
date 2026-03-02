package com.imshy.bedwars;

/**
 * Data class holding Bedwars statistics for a player
 */
public class BedwarsStats {

    public enum ThreatLevel {
        UNKNOWN, // Could not fetch stats
        LOW, // Beginner/casual player
        MEDIUM, // Average player
        HIGH, // Skilled player
        EXTREME // Very dangerous player
    }

    private final String playerName;
    private final String uuid;

    // Core stats
    private int stars = 0; // Bedwars level
    private int finalKills = 0;
    private int finalDeaths = 0;
    private int wins = 0;
    private int losses = 0;
    private int bedsBroken = 0;

    // Calculated ratios
    private double fkdr = 0.0; // Final Kill/Death Ratio
    private double wlr = 0.0; // Win/Loss Ratio

    private boolean loaded = false;
    private boolean error = false;
    private String errorMessage = null;

    public BedwarsStats(String playerName, String uuid) {
        this.playerName = playerName;
        this.uuid = uuid;
    }

    /**
     * Parse stats from Hypixel API JSON response
     */
    public void parseFromJson(String jsonResponse) {
        try {
            // Manual JSON parsing (no external libraries needed for Forge 1.8.9)
            if (jsonResponse == null || !jsonResponse.contains("\"success\":true")) {
                error = true;
                errorMessage = "API request failed";
                return;
            }

            // Check if player has Bedwars stats
            if (!jsonResponse.contains("\"Bedwars\"")) {
                // Player exists but hasn't played Bedwars
                loaded = true;
                return;
            }

            // Scope all stat extraction to the Bedwars section of the response.
            // The full API response contains stats for every game, quests, and
            // achievements, so keys like "final_deaths_bedwars" can appear outside
            // the Bedwars object (e.g. in quest descriptors) with wrong values.
            String bedwarsJson = extractJsonSection(jsonResponse, "Bedwars");
            if (bedwarsJson == null) {
                // Bedwars key present but section could not be extracted
                loaded = true;
                return;
            }

            // Extract Experience to calculate stars
            int exp = extractInt(bedwarsJson, "Experience");
            stars = calculateStars(exp);

            // Extract kill/death stats
            finalKills = extractInt(bedwarsJson, "final_kills_bedwars");
            finalDeaths = extractInt(bedwarsJson, "final_deaths_bedwars");

            // Extract win/loss stats
            wins = extractInt(bedwarsJson, "wins_bedwars");
            losses = extractInt(bedwarsJson, "losses_bedwars");

            // Extract beds broken
            bedsBroken = extractInt(bedwarsJson, "beds_broken_bedwars");

            // Calculate ratios. Fall back to 0.0 (not raw counts) when denominator
            // is zero — a genuine 0-death player is far too rare to justify showing
            // their kill count as FKDR.
            fkdr = finalDeaths > 0 ? (double) finalKills / finalDeaths : 0.0;
            wlr = losses > 0 ? (double) wins / losses : 0.0;

            loaded = true;

        } catch (Exception e) {
            error = true;
            errorMessage = e.getMessage();
        }
    }

    /**
     * Calculate Bedwars stars from experience
     * Based on Hypixel's leveling formula
     */
    private int calculateStars(int exp) {
        // Simplified star calculation
        // Each prestige is 487000 exp, each level within prestige varies
        if (exp < 0)
            return 0;

        int level = 0;
        int remaining = exp;

        // Experience thresholds per level (repeats every 100 levels with prestige
        // bonus)
        int[] levelThresholds = { 500, 1000, 2000, 3500, 5000 };

        while (remaining > 0) {
            int threshold = levelThresholds[Math.min(level % 100, 4)];
            if (level >= 100) {
                // Add prestige bonus
                threshold += (level / 100) * 500;
            }

            if (remaining >= threshold) {
                remaining -= threshold;
                level++;
            } else {
                break;
            }

            // Safety limit
            if (level > 5000)
                break;
        }

        return level;
    }

    /**
     * Extract a top-level JSON object section by key name.
     * Walks the string to find "key":{ ... } using brace counting, returning the
     * inner content (excluding the outer braces) so that extractInt searches are
     * properly scoped to that section.
     */
    private String extractJsonSection(String json, String sectionKey) {
        String searchKey = "\"" + sectionKey + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1)
            return null;

        int braceStart = json.indexOf('{', keyIndex + searchKey.length());
        if (braceStart == -1)
            return null;

        int depth = 1;
        int i = braceStart + 1;
        while (i < json.length() && depth > 0) {
            char c = json.charAt(i);
            if (c == '{')
                depth++;
            else if (c == '}')
                depth--;
            i++;
        }

        if (depth != 0)
            return null;

        return json.substring(braceStart, i); // includes outer { }
    }

    /**
     * Extract integer value from JSON by key
     */
    private int extractInt(String json, String key) {
        String searchKey = "\"" + key + "\":";
        int keyIndex = json.indexOf(searchKey);
        if (keyIndex == -1)
            return 0;

        int valueStart = keyIndex + searchKey.length();
        int valueEnd = valueStart;

        // Find end of number
        while (valueEnd < json.length()) {
            char c = json.charAt(valueEnd);
            if (c >= '0' && c <= '9') {
                valueEnd++;
            } else {
                break;
            }
        }

        if (valueEnd == valueStart)
            return 0;

        try {
            return Integer.parseInt(json.substring(valueStart, valueEnd));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Determine threat level based on stats
     */
    public ThreatLevel getThreatLevel() {
        if (error || !loaded)
            return ThreatLevel.UNKNOWN;

        // EXTREME: 500+ stars OR 6+ FKDR
        if (stars >= 500 || fkdr >= 6.0) {
            return ThreatLevel.EXTREME;
        }

        // HIGH: 300+ stars OR 4+ FKDR
        if (stars >= 300 || fkdr >= 4.0) {
            return ThreatLevel.HIGH;
        }

        // MEDIUM: 100+ stars OR 2+ FKDR
        if (stars >= 100 || fkdr >= 2.0) {
            return ThreatLevel.MEDIUM;
        }

        return ThreatLevel.LOW;
    }

    /**
     * Get color code for threat level
     */
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
            default:
                return "\u00A77"; // Gray
        }
    }

    /**
     * Format stats for display
     */
    public String getDisplayString() {
        if (error) {
            return "\u00A77[Error]";
        }
        if (!loaded) {
            return "\u00A77[Loading...]";
        }

        String color = getThreatColor();
        return String.format("%s[%d\u2B50] %.2f FKDR", color, stars, fkdr);
    }

    // Getters
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
