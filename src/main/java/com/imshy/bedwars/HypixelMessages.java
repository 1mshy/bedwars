package com.imshy.bedwars;

/**
 * Hypixel in-game chat message strings used for game state detection.
 * Centralised here so changes to Hypixel's message format only need a
 * single update rather than hunting through BedwarsRuntime.
 */
public final class HypixelMessages {

    private HypixelMessages() {}

    // ── Game lifecycle ───────────────────────────────────────────────────────
    public static final String GAME_START       = "Protect your bed and destroy the enemy beds.";

    // ── Win conditions (any one of these → WIN) ─────────────────────────────
    public static final String WIN_VICTORY      = "VICTORY!";
    /** Partial match: also check that the message contains the local player name */
    public static final String WIN_1ST_KILLER   = "1st Killer";
    public static final String WIN_YOU_WON      = "You won!";
    /** Partial match: also check that the message contains the local player name */
    public static final String WIN_WINNER       = "Winner";

    // ── Loss conditions (any one of these → LOSS) ───────────────────────────
    public static final String LOSS_GAME_OVER   = "GAME OVER!";
    public static final String LOSS_ELIMINATED  = "You have been eliminated!";
    /** startsWith check */
    public static final String LOSS_1ST_KILLER  = "1st Killer - ";

    // ── Player leaving ───────────────────────────────────────────────────────
    public static final String PLAYER_LEFT      = "You left.";
    public static final String PLAYER_SENDING   = "Sending you to";

    // ── Party ────────────────────────────────────────────────────────────────
    public static final String NOT_IN_PARTY     = "You are not currently in a party";

    // ── Autoplay ─────────────────────────────────────────────────────────────
    public static final String AUTOPLAY_RATE_LIMIT = "Please don't spam the command!";

    // ── Final kills ──────────────────────────────────────────────────────────
    /** Suffix present on every Hypixel Bedwars final-kill death message. */
    public static final String FINAL_KILL_SUFFIX = "FINAL KILL!";
    /**
     * Matches Hypixel final-kill messages after formatting codes are stripped.
     *
     * <p>Examples:
     * <pre>
     * PlayerA was killed by PlayerB. FINAL KILL!
     * PlayerA fell into the void while trying to escape PlayerB. FINAL KILL!
     * PlayerA hit the ground too hard. FINAL KILL!
     * </pre>
     *
     * <p>Group 1 is always the victim. Group 2 may be the killer (optional — some
     * environmental deaths don't name a killer).
     */
    public static final java.util.regex.Pattern FINAL_KILL_PATTERN = java.util.regex.Pattern.compile(
            "^([A-Za-z0-9_]{1,16}) .*?(?:by ([A-Za-z0-9_]{1,16})|escape ([A-Za-z0-9_]{1,16}))?\\.?\\s*FINAL KILL!");
}
