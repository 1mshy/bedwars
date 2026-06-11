package com.imshy.bedwars;

import org.junit.Test;

import java.util.regex.Matcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link HypixelMessages}: the FINAL_KILL_PATTERN capture behavior
 * and the chat constants BedwarsRuntime matches against, pinned to realistic
 * Hypixel lines so silent Hypixel format drift shows up as a test failure.
 *
 * <p>Note the pattern's actual (not documented) capture layout: the victim is
 * always group 1, but the killer lands in group 2 for "by X" phrasings and in
 * group 3 for "escape X" phrasings.
 */
public class HypixelMessagesTest {

    private static Matcher match(String line) {
        Matcher m = HypixelMessages.FINAL_KILL_PATTERN.matcher(line);
        assertTrue("expected FINAL_KILL_PATTERN to match: " + line, m.find());
        return m;
    }

    private static void assertNoMatch(String line) {
        assertFalse("expected FINAL_KILL_PATTERN NOT to match: " + line,
                HypixelMessages.FINAL_KILL_PATTERN.matcher(line).find());
    }

    // ── FINAL_KILL_PATTERN ───────────────────────────────────────────────────

    @Test
    public void byPhrasingCapturesKillerInGroup2() {
        Matcher m = match("PlayerA was killed by PlayerB. FINAL KILL!");
        assertEquals("PlayerA", m.group(1));
        assertEquals("PlayerB", m.group(2));
        assertNull(m.group(3));

        m = match("PlayerA was shot by PlayerB. FINAL KILL!");
        assertEquals("PlayerA", m.group(1));
        assertEquals("PlayerB", m.group(2));

        m = match("PlayerA was thrown into the void by PlayerB. FINAL KILL!");
        assertEquals("PlayerA", m.group(1));
        assertEquals("PlayerB", m.group(2));
    }

    @Test
    public void escapePhrasingCapturesKillerInGroup3() {
        Matcher m = match("PlayerA fell into the void while trying to escape PlayerB. FINAL KILL!");
        assertEquals("PlayerA", m.group(1));
        assertNull(m.group(2));
        assertEquals("PlayerB", m.group(3));
    }

    @Test
    public void environmentalDeathHasNoKiller() {
        Matcher m = match("PlayerA hit the ground too hard. FINAL KILL!");
        assertEquals("PlayerA", m.group(1));
        assertNull(m.group(2));
        assertNull(m.group(3));
    }

    @Test
    public void possessiveKillerIsNotCaptured() {
        // "by PlayerB's fireball" — the killer name is followed by an
        // apostrophe, so the "by X" alternative cannot complete and the
        // message matches with no killer group at all.
        Matcher m = match("PlayerA was killed by PlayerB's fireball. FINAL KILL!");
        assertEquals("PlayerA", m.group(1));
        assertNull(m.group(2));
        assertNull(m.group(3));
    }

    @Test
    public void capturesUnderscoreAndDigitNames() {
        Matcher m = match("xX_Pro_Xx9 was slain by Some_Guy42. FINAL KILL!");
        assertEquals("xX_Pro_Xx9", m.group(1));
        assertEquals("Some_Guy42", m.group(2));
    }

    @Test
    public void patternRequiresFinalKillSuffix() {
        assertNoMatch("PlayerA was killed by PlayerB.");
        assertNoMatch("PlayerA hit the ground too hard.");
    }

    @Test
    public void patternIsAnchoredToLineStart() {
        // Leading residue (e.g. unstripped prefix) must not produce a match.
        assertNoMatch(" PlayerA was killed by PlayerB. FINAL KILL!");
        assertNoMatch("» PlayerA was killed by PlayerB. FINAL KILL!");
    }

    @Test
    public void patternAllowsTrailingText() {
        // No trailing anchor — decoration after the suffix still matches.
        Matcher m = match("PlayerA was killed by PlayerB. FINAL KILL! +5 coins");
        assertEquals("PlayerA", m.group(1));
        assertEquals("PlayerB", m.group(2));
    }

    @Test
    public void matchedLinesCarryFinalKillSuffix() {
        String line = "PlayerA was killed by PlayerB. FINAL KILL!";
        assertTrue(line.contains(HypixelMessages.FINAL_KILL_SUFFIX));
        assertEquals("FINAL KILL!", HypixelMessages.FINAL_KILL_SUFFIX);
    }

    // ── chat constants vs realistic Hypixel lines ────────────────────────────

    @Test
    public void gameLifecycleConstantsMatchHypixelLines() {
        assertTrue("Protect your bed and destroy the enemy beds."
                .contains(HypixelMessages.GAME_START));
        assertEquals("You left.", HypixelMessages.PLAYER_LEFT);
        assertTrue("Sending you to mini121A!".startsWith(HypixelMessages.PLAYER_SENDING));
    }

    @Test
    public void winConstantsMatchHypixelLines() {
        assertTrue("VICTORY!".contains(HypixelMessages.WIN_VICTORY));
        assertTrue("You won!".contains(HypixelMessages.WIN_YOU_WON));
        // Partial-match constants: runtime additionally checks for the local
        // player name in the same line.
        assertTrue("1st Killer - PlayerA - 7 Kills".contains(HypixelMessages.WIN_1ST_KILLER));
        assertTrue("Winner: Red Team".contains(HypixelMessages.WIN_WINNER));
    }

    @Test
    public void lossConstantsMatchHypixelLines() {
        assertTrue("GAME OVER!".contains(HypixelMessages.LOSS_GAME_OVER));
        assertEquals("You have been eliminated!", HypixelMessages.LOSS_ELIMINATED);
        // startsWith check per the constant's javadoc
        assertTrue("1st Killer - PlayerA - 7 Kills".startsWith(HypixelMessages.LOSS_1ST_KILLER));
    }

    @Test
    public void partyAndAutoplayConstantsMatchHypixelLines() {
        assertTrue("You are not currently in a party."
                .startsWith(HypixelMessages.NOT_IN_PARTY));
        assertEquals("Please don't spam the command!", HypixelMessages.AUTOPLAY_RATE_LIMIT);
    }
}
