package com.imshy.bedwars.runtime;

import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Rewrites incoming chat components so known player names become clickable
 * links that run {@code /bw lookup <name>} (client-registered, never reaches
 * Hypixel) with a hover tooltip.
 *
 * <p>Hypixel 1.8 sends legacy {@code §} formatting codes inline inside text
 * nodes, so the rewrite must operate on each node's RAW text (not the stripped
 * string used by the parsers) and re-establish the formatting codes that were
 * active before a split point, otherwise colors reset mid-line. That carry
 * logic lives in {@link #activeFormattingCodes(String, int)} and the
 * boundary-aware matcher in {@link #indexOfNameToken(String, String, int)} —
 * both pure statics so they are unit-testable.
 *
 * <p>Conservative by design: messages that already carry a click event
 * anywhere in their tree (party invites, etc.) are left untouched, non-text
 * components are passed through unchanged, and at most
 * {@link #MAX_LINKS_PER_MESSAGE} names are linked per message.
 */
public final class ChatNameLinker {

    /** Hard cap on linked name occurrences per message. */
    static final int MAX_LINKS_PER_MESSAGE = 5;

    private ChatNameLinker() {}

    /**
     * Returns a rewritten copy of {@code root} with candidate names made
     * clickable, or {@code null} when the message should be left untouched
     * (no matches, an existing click event, or a non-text root).
     */
    public static IChatComponent makeNamesClickable(IChatComponent root, Collection<String> candidateNames) {
        if (root == null || candidateNames == null || candidateNames.isEmpty()) {
            return null;
        }
        // Hypixel chat arrives as ChatComponentText; anything else (translation
        // components etc.) is out of scope — leave it alone.
        if (!(root instanceof ChatComponentText)) {
            return null;
        }
        // Messages that already carry click events (party invites, /msg
        // prompts, ...) must not be rewritten.
        if (hasClickEvent(root)) {
            return null;
        }

        // Accumulate the would-be children first WITHOUT touching the original
        // tree: appendSibling re-points each component's parent style, so on
        // the no-match path the originals must stay pristine for later handlers
        // that rely on 1.8.9 parent-style inheritance from the real root.
        List<IChatComponent> newChildren = new ArrayList<IChatComponent>();
        int[] budget = {MAX_LINKS_PER_MESSAGE};
        boolean changed = false;

        // The root's own text inherits the (copied) root style from newRoot,
        // so its segments need no explicit base style.
        String rootText = ((ChatComponentText) root).getChatComponentText_TextValue();
        List<IChatComponent> rootSegments = splitTextNode(rootText, null, candidateNames, budget);
        if (rootSegments != null) {
            newChildren.addAll(rootSegments);
            changed = true;
        } else if (rootText != null && !rootText.isEmpty()) {
            newChildren.add(new ChatComponentText(rootText));
        }

        for (IChatComponent sibling : root.getSiblings()) {
            List<IChatComponent> segments = null;
            // Only flat text nodes are split; nodes with their own subtree are
            // passed through so style inheritance inside them stays intact.
            if (budget[0] > 0 && sibling instanceof ChatComponentText && sibling.getSiblings().isEmpty()) {
                String text = ((ChatComponentText) sibling).getChatComponentText_TextValue();
                segments = splitTextNode(text, sibling.getChatStyle(), candidateNames, budget);
            }
            if (segments != null) {
                newChildren.addAll(segments);
                changed = true;
            } else {
                newChildren.add(sibling);
            }
        }

        if (!changed) {
            return null;
        }

        ChatComponentText newRoot = new ChatComponentText("");
        newRoot.setChatStyle(root.getChatStyle().createShallowCopy());
        for (IChatComponent child : newChildren) {
            newRoot.appendSibling(child);
        }
        return newRoot;
    }

    /** True when any node in the component tree carries a click event. */
    private static boolean hasClickEvent(IChatComponent node) {
        if (node.getChatStyle() != null && node.getChatStyle().getChatClickEvent() != null) {
            return true;
        }
        for (IChatComponent sibling : node.getSiblings()) {
            if (hasClickEvent(sibling)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Splits one text node's raw text into pre/name/post segments around
     * candidate-name matches. Returns {@code null} when nothing matched.
     * Segments after the first carry the formatting codes active at their
     * start so inline colors survive the split.
     */
    private static List<IChatComponent> splitTextNode(String raw, ChatStyle baseStyle,
                                                      Collection<String> candidateNames, int[] budget) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }

        List<IChatComponent> out = null;
        int pos = 0;
        while (budget[0] > 0) {
            int bestIndex = -1;
            String bestName = null;
            for (String name : candidateNames) {
                int idx = indexOfNameToken(raw, name, pos);
                if (idx < 0) {
                    continue;
                }
                // Earliest match wins; on a tie prefer the longer name so
                // "Player12" beats "Player1" at the same offset.
                if (bestIndex < 0 || idx < bestIndex
                        || (idx == bestIndex && name.length() > bestName.length())) {
                    bestIndex = idx;
                    bestName = name;
                }
            }
            if (bestIndex < 0) {
                break;
            }
            if (out == null) {
                out = new ArrayList<IChatComponent>();
            }
            if (bestIndex > pos) {
                out.add(buildSegment(raw, pos, bestIndex, baseStyle, null));
            }
            out.add(buildSegment(raw, bestIndex, bestIndex + bestName.length(), baseStyle, bestName));
            pos = bestIndex + bestName.length();
            budget[0]--;
        }

        if (out == null) {
            return null;
        }
        if (pos < raw.length()) {
            out.add(buildSegment(raw, pos, raw.length(), baseStyle, null));
        }
        return out;
    }

    /**
     * Builds one segment component for {@code raw[from, to)}. Segments that do
     * not start the node re-establish the formatting codes active at their
     * start. When {@code clickName} is non-null the segment gets the
     * RUN_COMMAND click event (value MUST start with '/' — 1.8.9 sends
     * non-slash values as raw chat to the server) and a hover tooltip.
     */
    private static IChatComponent buildSegment(String raw, int from, int to,
                                               ChatStyle baseStyle, String clickName) {
        String carried = from > 0 ? activeFormattingCodes(raw, from) : "";
        ChatComponentText segment = new ChatComponentText(carried + raw.substring(from, to));
        ChatStyle style = baseStyle != null ? baseStyle.createShallowCopy() : new ChatStyle();
        if (clickName != null) {
            style.setChatClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bw lookup " + clickName));
            style.setChatHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new ChatComponentText("§eClick: Bedwars stats for §f" + clickName)));
        }
        segment.setChatStyle(style);
        return segment;
    }

    // ── Pure helpers (unit-tested) ───────────────────────────────────────────

    /**
     * Returns the legacy formatting codes active just before {@code index} in
     * {@code raw}: the last color code (if any) followed by the style codes
     * applied after it. A color code resets styles (vanilla 1.8 semantics);
     * {@code §r} resets everything.
     */
    static String activeFormattingCodes(String raw, int index) {
        String color = null;
        StringBuilder styles = new StringBuilder();
        int limit = Math.min(index, raw.length());
        for (int i = 0; i < limit - 1; i++) {
            if (raw.charAt(i) != '§') {
                continue;
            }
            char code = Character.toLowerCase(raw.charAt(i + 1));
            if (code == 'r') {
                color = null;
                styles.setLength(0);
            } else if ((code >= '0' && code <= '9') || (code >= 'a' && code <= 'f')) {
                color = "§" + code;
                styles.setLength(0);
            } else if (code >= 'k' && code <= 'o') {
                styles.append('§').append(code);
            }
            i++; // skip the code character
        }
        return (color != null ? color : "") + styles;
    }

    /**
     * Finds {@code name} in {@code raw} at or after {@code fromIndex},
     * requiring word boundaries on both sides. The before-boundary check is
     * formatting-aware: it skips backwards over {@code §x} pairs so
     * "§7PlayerA" matches even though '7' is a word character.
     * Returns the match index or -1.
     */
    static int indexOfNameToken(String raw, String name, int fromIndex) {
        if (raw == null || name == null || name.isEmpty()) {
            return -1;
        }
        int idx = raw.indexOf(name, fromIndex);
        while (idx >= 0) {
            if (isBoundaryBefore(raw, idx) && isBoundaryAfter(raw, idx + name.length())) {
                return idx;
            }
            idx = raw.indexOf(name, idx + 1);
        }
        return -1;
    }

    private static boolean isBoundaryBefore(String raw, int start) {
        int i = start;
        // Skip backwards over consecutive formatting-code pairs.
        while (i >= 2 && raw.charAt(i - 2) == '§') {
            i -= 2;
        }
        // A position directly after '§' is the code character of a formatting
        // pair (e.g. the 'a' of "§aWildPig") and can never start a real name.
        return i == 0 || (raw.charAt(i - 1) != '§' && !isWordChar(raw.charAt(i - 1)));
    }

    private static boolean isBoundaryAfter(String raw, int end) {
        return end >= raw.length() || !isWordChar(raw.charAt(end));
    }

    private static boolean isWordChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_';
    }
}
