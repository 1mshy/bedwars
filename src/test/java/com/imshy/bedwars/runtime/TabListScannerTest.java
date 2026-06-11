package com.imshy.bedwars.runtime;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests for TabListScanner.parseStarCount — the pre-fetch star hint parsed from
 * the tab display-name bracket token ("[123✫] Name" in pre-game lobbies). The
 * same slot holds the team tag in-game, so non-star tokens must return -1.
 */
public class TabListScannerTest {

    // ==================== STAR PREFIXES ====================

    @Test
    public void parsesPlainStarPrefix() {
        assertEquals(123, TabListScanner.parseStarCount("123✫")); // 123✫
    }

    @Test
    public void parsesSingleDigitStar() {
        assertEquals(1, TabListScanner.parseStarCount("1✫"));
    }

    @Test
    public void parsesPrestigeGlyphs() {
        assertEquals(857, TabListScanner.parseStarCount("857✪")); // ✪
        assertEquals(1062, TabListScanner.parseStarCount("1062⚝")); // ⚝
    }

    @Test
    public void trimsSurroundingWhitespace() {
        assertEquals(45, TabListScanner.parseStarCount(" 45✫ "));
    }

    @Test
    public void allowsSpaceBetweenDigitsAndGlyph() {
        assertEquals(45, TabListScanner.parseStarCount("45 ✫"));
    }

    // ==================== NON-STAR TOKENS ====================

    @Test
    public void rejectsInGameTeamTags() {
        assertEquals(-1, TabListScanner.parseStarCount("R"));
        assertEquals(-1, TabListScanner.parseStarCount("RED"));
        assertEquals(-1, TabListScanner.parseStarCount("?"));
    }

    @Test
    public void rejectsPureDigitTokens() {
        // No trailing glyph — could be anything; do not treat as a star count.
        assertEquals(-1, TabListScanner.parseStarCount("123"));
    }

    @Test
    public void rejectsDigitsFollowedByLetters() {
        assertEquals(-1, TabListScanner.parseStarCount("12a"));
        assertEquals(-1, TabListScanner.parseStarCount("1st"));
    }

    @Test
    public void rejectsGlyphWithoutDigits() {
        assertEquals(-1, TabListScanner.parseStarCount("✫"));
    }

    @Test
    public void rejectsEmptyAndNull() {
        assertEquals(-1, TabListScanner.parseStarCount(""));
        assertEquals(-1, TabListScanner.parseStarCount("   "));
        assertEquals(-1, TabListScanner.parseStarCount(null));
    }

    @Test
    public void rejectsOverflowingDigitRuns() {
        assertEquals(-1, TabListScanner.parseStarCount("99999999999999✫"));
    }
}
