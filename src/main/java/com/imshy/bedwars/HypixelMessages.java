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
}
