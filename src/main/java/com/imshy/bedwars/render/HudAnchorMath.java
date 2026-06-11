package com.imshy.bedwars.render;

/**
 * Pure integer math for the anchor + offset HUD position model used by the
 * drag-and-drop HUD editor ({@code /bw edithud}).
 *
 * <p>Model: an element is pinned to one of nine screen anchors (LEFT/CENTER/RIGHT
 * x TOP/CENTER/BOTTOM) plus a signed pixel offset measured from the anchor point
 * on the screen to the SAME corner/edge point of the element. All coordinates
 * are in unscaled {@code ScaledResolution} pixels; callers that draw under a
 * {@code GlStateManager.scale} transform convert on their side.
 *
 * <ul>
 *   <li>LEFT / TOP: offset = element left/top edge.</li>
 *   <li>CENTER: offset = element center relative to screen center
 *       (computed as {@code x - (screen - element) / 2} so forward and inverse
 *       round-trip exactly under int division).</li>
 *   <li>RIGHT / BOTTOM: offset = element right/bottom edge relative to the
 *       screen right/bottom edge (a 4px margin is offset -4).</li>
 * </ul>
 *
 * <p>Deliberately free of Minecraft types so it is unit-testable.
 */
public final class HudAnchorMath {

    public static final String ANCHOR_LEFT = "LEFT";
    public static final String ANCHOR_CENTER = "CENTER";
    public static final String ANCHOR_RIGHT = "RIGHT";
    public static final String ANCHOR_TOP = "TOP";
    public static final String ANCHOR_BOTTOM = "BOTTOM";

    private HudAnchorMath() {
    }

    /** Anchor + offset -> element left edge. Unknown anchors behave as LEFT. */
    public static int computeX(String anchorX, int screenWidth, int elementWidth, int offsetX) {
        if (ANCHOR_CENTER.equals(anchorX)) {
            return (screenWidth - elementWidth) / 2 + offsetX;
        }
        if (ANCHOR_RIGHT.equals(anchorX)) {
            return screenWidth - elementWidth + offsetX;
        }
        return offsetX;
    }

    /** Anchor + offset -> element top edge. Unknown anchors behave as TOP. */
    public static int computeY(String anchorY, int screenHeight, int elementHeight, int offsetY) {
        if (ANCHOR_CENTER.equals(anchorY)) {
            return (screenHeight - elementHeight) / 2 + offsetY;
        }
        if (ANCHOR_BOTTOM.equals(anchorY)) {
            return screenHeight - elementHeight + offsetY;
        }
        return offsetY;
    }

    /** Inverse of {@link #computeX}: offset that places the element at {@code elementX}. */
    public static int offsetXFor(String anchorX, int elementX, int elementWidth, int screenWidth) {
        if (ANCHOR_CENTER.equals(anchorX)) {
            return elementX - (screenWidth - elementWidth) / 2;
        }
        if (ANCHOR_RIGHT.equals(anchorX)) {
            return elementX - (screenWidth - elementWidth);
        }
        return elementX;
    }

    /** Inverse of {@link #computeY}: offset that places the element at {@code elementY}. */
    public static int offsetYFor(String anchorY, int elementY, int elementHeight, int screenHeight) {
        if (ANCHOR_CENTER.equals(anchorY)) {
            return elementY - (screenHeight - elementHeight) / 2;
        }
        if (ANCHOR_BOTTOM.equals(anchorY)) {
            return elementY - (screenHeight - elementHeight);
        }
        return elementY;
    }

    /**
     * Snap rule: which horizontal anchor is nearest, judged by the element
     * CENTER against screen thirds. Exact int math (doubled center, tripled
     * thirds); a center sitting exactly on a third boundary snaps to CENTER.
     */
    public static String nearestAnchorX(int elementX, int elementWidth, int screenWidth) {
        int doubledCenter = elementX * 2 + elementWidth;
        if (doubledCenter * 3 < screenWidth * 2) {
            return ANCHOR_LEFT;
        }
        if (doubledCenter * 3 > screenWidth * 4) {
            return ANCHOR_RIGHT;
        }
        return ANCHOR_CENTER;
    }

    /** Vertical twin of {@link #nearestAnchorX}. */
    public static String nearestAnchorY(int elementY, int elementHeight, int screenHeight) {
        int doubledCenter = elementY * 2 + elementHeight;
        if (doubledCenter * 3 < screenHeight * 2) {
            return ANCHOR_TOP;
        }
        if (doubledCenter * 3 > screenHeight * 4) {
            return ANCHOR_BOTTOM;
        }
        return ANCHOR_CENTER;
    }

    /**
     * Legacy 4-corner origin, replicating {@code BedwarsHudRenderer.computeOrigin}
     * and {@code KillFeedRenderer.computeOrigin}: margins are measured INTO the
     * screen from the anchored corner; unknown positions fall back to TOP_LEFT.
     * Used by the editor to place proxies for elements still in legacy mode.
     */
    public static int[] legacyCornerOrigin(String position, int screenWidth, int screenHeight,
                                           int width, int height, int marginX, int marginY) {
        int x;
        int y;
        if ("TOP_RIGHT".equals(position)) {
            x = screenWidth - width - marginX;
            y = marginY;
        } else if ("BOTTOM_LEFT".equals(position)) {
            x = marginX;
            y = screenHeight - height - marginY;
        } else if ("BOTTOM_RIGHT".equals(position)) {
            x = screenWidth - width - marginX;
            y = screenHeight - height - marginY;
        } else {
            x = marginX;
            y = marginY;
        }
        return new int[]{x, y};
    }

    /**
     * Legacy centered-card origin, replicating MatchSummaryRenderer (yDivisor 3)
     * and PreGameBriefingRenderer (yDivisor 4): horizontally centered, upper
     * portion of the screen with a 20px floor.
     */
    public static int[] legacyCardOrigin(int screenWidth, int screenHeight,
                                         int width, int height, int yDivisor) {
        int x = (screenWidth - width) / 2;
        int y = Math.max(20, (screenHeight - height) / yDivisor);
        return new int[]{x, y};
    }

    /** Clamp helper shared by drag logic. */
    public static int clamp(int value, int min, int max) {
        if (max < min) {
            return min;
        }
        return value < min ? min : (value > max ? max : value);
    }
}
