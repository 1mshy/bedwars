package com.imshy.bedwars.render;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Pins the anchor + offset position model used by the HUD editor:
 * forward math for all nine anchor combinations, exact round-tripping
 * between positions and offsets, the center-vs-screen-thirds snap rule,
 * and the legacy origin replicas the editor uses for non-customized elements.
 */
public class HudAnchorMathTest {

    // Fixed geometry used across the forward-math tests.
    private static final int SCREEN_W = 480;
    private static final int SCREEN_H = 270;
    private static final int ELEM_W = 100;
    private static final int ELEM_H = 40;

    // ─── Forward math: all 9 anchor combinations ───

    @Test
    public void computeXForAllThreeAnchors() {
        assertEquals(7, HudAnchorMath.computeX(HudAnchorMath.ANCHOR_LEFT, SCREEN_W, ELEM_W, 7));
        assertEquals((SCREEN_W - ELEM_W) / 2 + 7,
                HudAnchorMath.computeX(HudAnchorMath.ANCHOR_CENTER, SCREEN_W, ELEM_W, 7));
        assertEquals(SCREEN_W - ELEM_W + 7,
                HudAnchorMath.computeX(HudAnchorMath.ANCHOR_RIGHT, SCREEN_W, ELEM_W, 7));
    }

    @Test
    public void computeYForAllThreeAnchors() {
        assertEquals(-7, HudAnchorMath.computeY(HudAnchorMath.ANCHOR_TOP, SCREEN_H, ELEM_H, -7));
        assertEquals((SCREEN_H - ELEM_H) / 2 - 7,
                HudAnchorMath.computeY(HudAnchorMath.ANCHOR_CENTER, SCREEN_H, ELEM_H, -7));
        assertEquals(SCREEN_H - ELEM_H - 7,
                HudAnchorMath.computeY(HudAnchorMath.ANCHOR_BOTTOM, SCREEN_H, ELEM_H, -7));
    }

    @Test
    public void allNineAnchorCombinationsProduceExpectedCorners() {
        String[] xAnchors = {HudAnchorMath.ANCHOR_LEFT, HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.ANCHOR_RIGHT};
        String[] yAnchors = {HudAnchorMath.ANCHOR_TOP, HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.ANCHOR_BOTTOM};
        int[] expectedX = {0, (SCREEN_W - ELEM_W) / 2, SCREEN_W - ELEM_W};
        int[] expectedY = {0, (SCREEN_H - ELEM_H) / 2, SCREEN_H - ELEM_H};
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals("x for " + xAnchors[i] + "/" + yAnchors[j], expectedX[i],
                        HudAnchorMath.computeX(xAnchors[i], SCREEN_W, ELEM_W, 0));
                assertEquals("y for " + xAnchors[i] + "/" + yAnchors[j], expectedY[j],
                        HudAnchorMath.computeY(yAnchors[j], SCREEN_H, ELEM_H, 0));
            }
        }
    }

    @Test
    public void unknownAnchorsFallBackToLeftAndTop() {
        assertEquals(9, HudAnchorMath.computeX("DIAGONAL", SCREEN_W, ELEM_W, 9));
        assertEquals(9, HudAnchorMath.computeY(null, SCREEN_H, ELEM_H, 9));
    }

    // ─── Round-tripping ───

    @Test
    public void offsetThenComputeRecoversPositionForAllAnchors() {
        // Odd screen and element sizes stress the int-division center case.
        int sw = 481;
        int w = 99;
        int[] positions = {0, 1, 37, (sw - w) / 2, sw - w - 1, sw - w};
        String[] anchors = {HudAnchorMath.ANCHOR_LEFT, HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.ANCHOR_RIGHT};
        for (String anchor : anchors) {
            for (int x : positions) {
                int offset = HudAnchorMath.offsetXFor(anchor, x, w, sw);
                assertEquals("anchor " + anchor + " x " + x, x,
                        HudAnchorMath.computeX(anchor, sw, w, offset));
            }
        }
    }

    @Test
    public void offsetThenComputeRecoversPositionVertically() {
        int sh = 271;
        int h = 41;
        int[] positions = {0, 13, (sh - h) / 2, sh - h};
        String[] anchors = {HudAnchorMath.ANCHOR_TOP, HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.ANCHOR_BOTTOM};
        for (String anchor : anchors) {
            for (int y : positions) {
                int offset = HudAnchorMath.offsetYFor(anchor, y, h, sh);
                assertEquals("anchor " + anchor + " y " + y, y,
                        HudAnchorMath.computeY(anchor, sh, h, offset));
            }
        }
    }

    @Test
    public void computeThenOffsetRecoversOffsetForAllNineCombinations() {
        String[] xAnchors = {HudAnchorMath.ANCHOR_LEFT, HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.ANCHOR_RIGHT};
        String[] yAnchors = {HudAnchorMath.ANCHOR_TOP, HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.ANCHOR_BOTTOM};
        int[] offsets = {-31, -4, 0, 4, 31};
        for (String ax : xAnchors) {
            for (String ay : yAnchors) {
                for (int off : offsets) {
                    int x = HudAnchorMath.computeX(ax, SCREEN_W, ELEM_W, off);
                    int y = HudAnchorMath.computeY(ay, SCREEN_H, ELEM_H, off);
                    assertEquals(ax + " offset", off, HudAnchorMath.offsetXFor(ax, x, ELEM_W, SCREEN_W));
                    assertEquals(ay + " offset", off, HudAnchorMath.offsetYFor(ay, y, ELEM_H, SCREEN_H));
                }
            }
        }
    }

    // ─── Snap rule: element center vs screen thirds ───

    @Test
    public void nearestAnchorXSnapsByElementCenterThirds() {
        // screen 300: thirds at 100 and 200; element width 100.
        assertEquals(HudAnchorMath.ANCHOR_LEFT, HudAnchorMath.nearestAnchorX(10, 100, 300));   // center 60
        assertEquals(HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.nearestAnchorX(100, 100, 300)); // center 150
        assertEquals(HudAnchorMath.ANCHOR_RIGHT, HudAnchorMath.nearestAnchorX(195, 100, 300));  // center 245
    }

    @Test
    public void nearestAnchorXBoundaryCentersSnapToCenter() {
        // Element centers sitting exactly on a third boundary snap to CENTER.
        assertEquals(HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.nearestAnchorX(50, 100, 300));  // center 100
        assertEquals(HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.nearestAnchorX(150, 100, 300)); // center 200
    }

    @Test
    public void nearestAnchorYSnapsByElementCenterThirds() {
        assertEquals(HudAnchorMath.ANCHOR_TOP, HudAnchorMath.nearestAnchorY(0, 40, 270));      // center 20
        assertEquals(HudAnchorMath.ANCHOR_CENTER, HudAnchorMath.nearestAnchorY(115, 40, 270)); // center 135
        assertEquals(HudAnchorMath.ANCHOR_BOTTOM, HudAnchorMath.nearestAnchorY(230, 40, 270)); // center 250
    }

    @Test
    public void snapThenRecomputeOffsetsKeepsElementInPlace() {
        // The editor's mouseReleased flow: drop position -> nearest anchors ->
        // offsets -> recomputed rect must equal the drop position exactly.
        int[][] drops = {{3, 5}, {190, 115}, {377, 229}, {0, 0}, {380, 230}};
        for (int[] drop : drops) {
            int x = drop[0];
            int y = drop[1];
            String ax = HudAnchorMath.nearestAnchorX(x, ELEM_W, SCREEN_W);
            String ay = HudAnchorMath.nearestAnchorY(y, ELEM_H, SCREEN_H);
            int offX = HudAnchorMath.offsetXFor(ax, x, ELEM_W, SCREEN_W);
            int offY = HudAnchorMath.offsetYFor(ay, y, ELEM_H, SCREEN_H);
            assertEquals(x, HudAnchorMath.computeX(ax, SCREEN_W, ELEM_W, offX));
            assertEquals(y, HudAnchorMath.computeY(ay, SCREEN_H, ELEM_H, offY));
        }
    }

    // ─── Legacy origin replicas ───

    @Test
    public void legacyCornerOriginMatchesRendererCornerMath() {
        int margin = 4;
        int[] tl = HudAnchorMath.legacyCornerOrigin("TOP_LEFT", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, margin, margin);
        assertEquals(margin, tl[0]);
        assertEquals(margin, tl[1]);

        int[] tr = HudAnchorMath.legacyCornerOrigin("TOP_RIGHT", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, margin, margin);
        assertEquals(SCREEN_W - ELEM_W - margin, tr[0]);
        assertEquals(margin, tr[1]);

        int[] bl = HudAnchorMath.legacyCornerOrigin("BOTTOM_LEFT", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, margin, margin);
        assertEquals(margin, bl[0]);
        assertEquals(SCREEN_H - ELEM_H - margin, bl[1]);

        int[] br = HudAnchorMath.legacyCornerOrigin("BOTTOM_RIGHT", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, margin, margin);
        assertEquals(SCREEN_W - ELEM_W - margin, br[0]);
        assertEquals(SCREEN_H - ELEM_H - margin, br[1]);
    }

    @Test
    public void legacyCornerOriginUnknownPositionFallsBackToTopLeft() {
        int[] o = HudAnchorMath.legacyCornerOrigin("SIDEWAYS", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, 6, 8);
        assertEquals(6, o[0]);
        assertEquals(8, o[1]);
    }

    @Test
    public void legacyCardOriginCentersHorizontallyAndDividesVertically() {
        int[] summary = HudAnchorMath.legacyCardOrigin(SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, 3);
        assertEquals((SCREEN_W - ELEM_W) / 2, summary[0]);
        assertEquals((SCREEN_H - ELEM_H) / 3, summary[1]);

        int[] briefing = HudAnchorMath.legacyCardOrigin(SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, 4);
        assertEquals((SCREEN_W - ELEM_W) / 2, briefing[0]);
        assertEquals((SCREEN_H - ELEM_H) / 4, briefing[1]);
    }

    @Test
    public void legacyCardOriginAppliesTwentyPixelFloor() {
        int[] o = HudAnchorMath.legacyCardOrigin(SCREEN_W, 100, ELEM_W, 90, 3);
        assertEquals(20, o[1]);
    }

    @Test
    public void leftCardOriginPinsToLeftMarginWithSameVerticalMath() {
        // Match-summary default: left edge instead of horizontally centered.
        int[] summary = HudAnchorMath.legacyLeftCardOrigin(SCREEN_H, ELEM_H, 3, 4);
        assertEquals(4, summary[0]);
        assertEquals((SCREEN_H - ELEM_H) / 3, summary[1]);

        // Same 20px floor as the centered variant.
        int[] floored = HudAnchorMath.legacyLeftCardOrigin(100, 90, 3, 4);
        assertEquals(20, floored[1]);
    }

    @Test
    public void customDefaultAnchorsReproduceLegacyCorners() {
        // hud defaults LEFT/TOP +4/+4 == hudPosition TOP_LEFT with margin 4.
        int[] legacyHud = HudAnchorMath.legacyCornerOrigin("TOP_LEFT", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, 4, 4);
        assertEquals(legacyHud[0], HudAnchorMath.computeX(HudAnchorMath.ANCHOR_LEFT, SCREEN_W, ELEM_W, 4));
        assertEquals(legacyHud[1], HudAnchorMath.computeY(HudAnchorMath.ANCHOR_TOP, SCREEN_H, ELEM_H, 4));

        // killfeed defaults RIGHT/TOP -4/+4 == killfeedAnchor TOP_RIGHT with margins 4/4.
        int[] legacyFeed = HudAnchorMath.legacyCornerOrigin("TOP_RIGHT", SCREEN_W, SCREEN_H, ELEM_W, ELEM_H, 4, 4);
        assertEquals(legacyFeed[0], HudAnchorMath.computeX(HudAnchorMath.ANCHOR_RIGHT, SCREEN_W, ELEM_W, -4));
        assertEquals(legacyFeed[1], HudAnchorMath.computeY(HudAnchorMath.ANCHOR_TOP, SCREEN_H, ELEM_H, 4));
    }

    // ─── Drag clamp ───

    @Test
    public void clampBoundsValueAndToleratesInvertedRange() {
        assertEquals(0, HudAnchorMath.clamp(-5, 0, 100));
        assertEquals(100, HudAnchorMath.clamp(250, 0, 100));
        assertEquals(42, HudAnchorMath.clamp(42, 0, 100));
        // Element wider than the screen: max < min collapses to min.
        assertEquals(0, HudAnchorMath.clamp(7, 0, -20));
    }
}
