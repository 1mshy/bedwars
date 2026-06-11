package com.imshy.bedwars.runtime;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link ChatNameLinker}: the pure formatting-code-carry and
 * boundary-matching helpers, and the component rewrite itself (vanilla 1.8.9
 * chat components are plain data classes — no Minecraft bootstrap needed).
 */
public class ChatNameLinkerTest {

    // ── activeFormattingCodes ───────────────────────────────────────────────

    @Test
    public void noCodesYieldsEmptyCarry() {
        assertEquals("", ChatNameLinker.activeFormattingCodes("PlayerA hello", 8));
    }

    @Test
    public void colorCodeIsCarried() {
        assertEquals("§7", ChatNameLinker.activeFormattingCodes("§7PlayerA", 5));
    }

    @Test
    public void latestColorWins() {
        assertEquals("§e", ChatNameLinker.activeFormattingCodes("§7abc §edef", 8));
    }

    @Test
    public void colorResetsEarlierStyles() {
        // Vanilla 1.8 semantics: a color code clears any active style codes.
        assertEquals("§7", ChatNameLinker.activeFormattingCodes("§l§7abc", 6));
    }

    @Test
    public void stylesAfterColorAccumulate() {
        assertEquals("§7§l", ChatNameLinker.activeFormattingCodes("§7§labc", 5));
        assertEquals("§7§l§o", ChatNameLinker.activeFormattingCodes("§7§l§oabc", 7));
    }

    @Test
    public void resetCodeClearsEverything() {
        assertEquals("", ChatNameLinker.activeFormattingCodes("§7§labc§rdef", 10));
    }

    @Test
    public void uppercaseCodesAreNormalized() {
        assertEquals("§a", ChatNameLinker.activeFormattingCodes("§Aabc", 4));
    }

    @Test
    public void indexZeroCarriesNothing() {
        assertEquals("", ChatNameLinker.activeFormattingCodes("§7abc", 0));
    }

    // ── indexOfNameToken ────────────────────────────────────────────────────

    @Test
    public void findsTokenAtStart() {
        assertEquals(0, ChatNameLinker.indexOfNameToken("PlayerA joined", "PlayerA", 0));
    }

    @Test
    public void rejectsEmbeddedPrefix() {
        assertEquals(-1, ChatNameLinker.indexOfNameToken("xPlayerA joined", "PlayerA", 0));
    }

    @Test
    public void rejectsEmbeddedSuffix() {
        // "Player1" must not link inside "Player12".
        assertEquals(-1, ChatNameLinker.indexOfNameToken("Player12 says hi", "Player1", 0));
    }

    @Test
    public void underscoreIsAWordCharacter() {
        assertEquals(-1, ChatNameLinker.indexOfNameToken("_PlayerA", "PlayerA", 0));
        assertEquals(-1, ChatNameLinker.indexOfNameToken("PlayerA_", "PlayerA", 0));
    }

    @Test
    public void formattingCodeBeforeNameIsABoundary() {
        // "§7PlayerA" — '7' is a word char but belongs to the code pair.
        assertEquals(2, ChatNameLinker.indexOfNameToken("§7PlayerA fell", "PlayerA", 0));
        assertEquals(5, ChatNameLinker.indexOfNameToken("by §cKillerGuy.", "KillerGuy", 0));
    }

    @Test
    public void punctuationIsABoundary() {
        assertEquals(0, ChatNameLinker.indexOfNameToken("PlayerA: hello", "PlayerA", 0));
        assertEquals(1, ChatNameLinker.indexOfNameToken("(PlayerA)", "PlayerA", 0));
    }

    @Test
    public void fromIndexSkipsEarlierOccurrence() {
        assertEquals(12, ChatNameLinker.indexOfNameToken("PlayerA and PlayerA", "PlayerA", 1));
    }

    @Test
    public void matchIsCaseSensitive() {
        assertEquals(-1, ChatNameLinker.indexOfNameToken("playera said hi", "PlayerA", 0));
    }

    @Test
    public void nullAndEmptyInputsYieldMinusOne() {
        assertEquals(-1, ChatNameLinker.indexOfNameToken(null, "PlayerA", 0));
        assertEquals(-1, ChatNameLinker.indexOfNameToken("abc", null, 0));
        assertEquals(-1, ChatNameLinker.indexOfNameToken("abc", "", 0));
    }

    // ── makeNamesClickable ──────────────────────────────────────────────────

    @Test
    public void attachesClickAndHoverToNameSegment() {
        IChatComponent root = new ChatComponentText("§7PlayerA §ejoined the game");

        IChatComponent rewritten = ChatNameLinker.makeNamesClickable(root, names("PlayerA"));

        assertNotNull(rewritten);
        List<IChatComponent> clickable = clickableLeaves(rewritten);
        assertEquals(1, clickable.size());

        ClickEvent click = clickable.get(0).getChatStyle().getChatClickEvent();
        assertEquals(ClickEvent.Action.RUN_COMMAND, click.getAction());
        assertEquals("/bw lookup PlayerA", click.getValue());

        HoverEvent hover = clickable.get(0).getChatStyle().getChatHoverEvent();
        assertNotNull(hover);
        assertEquals(HoverEvent.Action.SHOW_TEXT, hover.getAction());
        assertTrue(hover.getValue().getUnformattedText().contains("PlayerA"));
    }

    @Test
    public void visibleTextIsPreserved() {
        IChatComponent root = new ChatComponentText("§7PlayerA §ewas slain by §cKillerGuy§e. §b§lFINAL KILL!");

        IChatComponent rewritten = ChatNameLinker.makeNamesClickable(root, names("PlayerA", "KillerGuy"));

        assertNotNull(rewritten);
        assertEquals(stripCodes(root.getUnformattedText()), stripCodes(rewritten.getUnformattedText()));
        assertEquals(2, clickableLeaves(rewritten).size());
    }

    @Test
    public void activeColorIsCarriedIntoNameAndPostSegments() {
        IChatComponent root = new ChatComponentText("§ePlayerA says hi");

        IChatComponent rewritten = ChatNameLinker.makeNamesClickable(root, names("PlayerA"));

        assertNotNull(rewritten);
        IChatComponent nameSegment = clickableLeaves(rewritten).get(0);
        assertEquals("§ePlayerA", ((ChatComponentText) nameSegment).getChatComponentText_TextValue());

        // The segment after the name re-establishes the active color too.
        List<IChatComponent> siblings = rewritten.getSiblings();
        IChatComponent post = siblings.get(siblings.size() - 1);
        assertEquals("§e says hi", ((ChatComponentText) post).getChatComponentText_TextValue());
    }

    @Test
    public void messageWithExistingClickEventIsLeftUntouched() {
        IChatComponent root = new ChatComponentText("Click here to join PlayerA's party!");
        ChatComponentText sibling = new ChatComponentText("[ACCEPT]");
        ChatStyle style = new ChatStyle();
        style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/party accept PlayerA"));
        sibling.setChatStyle(style);
        root.appendSibling(sibling);

        assertNull(ChatNameLinker.makeNamesClickable(root, names("PlayerA")));
    }

    @Test
    public void noMatchYieldsNull() {
        IChatComponent root = new ChatComponentText("nothing to see here");

        assertNull(ChatNameLinker.makeNamesClickable(root, names("PlayerA")));
    }

    @Test
    public void emptyCandidatesYieldNull() {
        IChatComponent root = new ChatComponentText("PlayerA joined");

        assertNull(ChatNameLinker.makeNamesClickable(root, Collections.<String>emptySet()));
        assertNull(ChatNameLinker.makeNamesClickable(root, null));
    }

    @Test
    public void nonTextRootIsLeftAlone() {
        IChatComponent root = new ChatComponentTranslation("chat.type.text", "PlayerA", "hi");

        assertNull(ChatNameLinker.makeNamesClickable(root, names("PlayerA")));
    }

    @Test
    public void linkCountIsCappedPerMessage() {
        IChatComponent root = new ChatComponentText("Aa Aa Aa Aa Aa Aa Aa");

        IChatComponent rewritten = ChatNameLinker.makeNamesClickable(root, names("Aa"));

        assertNotNull(rewritten);
        assertEquals(ChatNameLinker.MAX_LINKS_PER_MESSAGE, clickableLeaves(rewritten).size());
    }

    @Test
    public void namesAcrossSiblingNodesAreLinked() {
        IChatComponent root = new ChatComponentText("");
        root.appendSibling(new ChatComponentText("§7PlayerA "));
        root.appendSibling(new ChatComponentText("§ehit PlayerB"));

        IChatComponent rewritten = ChatNameLinker.makeNamesClickable(root, names("PlayerA", "PlayerB"));

        assertNotNull(rewritten);
        assertEquals(2, clickableLeaves(rewritten).size());
        assertEquals(stripCodes(root.getUnformattedText()), stripCodes(rewritten.getUnformattedText()));
    }

    @Test
    public void siblingStyleSurvivesTheSplit() {
        IChatComponent root = new ChatComponentText("");
        ChatComponentText sibling = new ChatComponentText("PlayerA joined");
        sibling.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.RED));
        root.appendSibling(sibling);

        IChatComponent rewritten = ChatNameLinker.makeNamesClickable(root, names("PlayerA"));

        assertNotNull(rewritten);
        IChatComponent nameSegment = clickableLeaves(rewritten).get(0);
        assertEquals(EnumChatFormatting.RED, nameSegment.getChatStyle().getColor());
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static Set<String> names(String... values) {
        return new HashSet<String>(Arrays.asList(values));
    }

    private static String stripCodes(String text) {
        return text.replaceAll("§.", "");
    }

    private static List<IChatComponent> clickableLeaves(IChatComponent root) {
        List<IChatComponent> out = new ArrayList<IChatComponent>();
        collectClickable(root, out);
        return out;
    }

    private static void collectClickable(IChatComponent node, List<IChatComponent> out) {
        if (node.getChatStyle() != null && node.getChatStyle().getChatClickEvent() != null) {
            out.add(node);
        }
        for (Object sibling : node.getSiblings()) {
            collectClickable((IChatComponent) sibling, out);
        }
    }
}
