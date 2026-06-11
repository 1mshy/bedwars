package com.imshy.bedwars.gui;

import com.imshy.bedwars.ModConfig;
import com.imshy.bedwars.render.HudAnchorMath;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop HUD layout editor, opened via {@code /bw edithud}.
 *
 * <p>Each movable element is shown as a labeled proxy rectangle at its CURRENT
 * computed position (the real renderers early-out without game data, so fixed
 * representative sample sizes are used). Dragging live-updates the in-memory
 * ModConfig fields — the real HUD moves underneath if visible — and releasing
 * snaps the element to the nearest of nine anchors (element center vs screen
 * thirds) with offsets recomputed. The scroll wheel over the main stats panel
 * adjusts hudScale within its 0.5-2.0 clamp; R over an element resets it to
 * its legacy default position. Closing (ESC or Done) persists every property
 * in one config.save().
 */
public class GuiHudEditor extends GuiScreen {

    private static final int ELEMENT_HUD = 0;
    private static final int ELEMENT_KILLFEED = 1;
    private static final int ELEMENT_MATCH_SUMMARY = 2;
    private static final int ELEMENT_BRIEFING = 3;

    private static final int DONE_BUTTON_ID = 0;
    private static final double SCALE_STEP = 0.1;

    /** Per-element proxy state; rect is recomputed every frame in raw ScaledResolution space. */
    private static class ElementHandle {
        final int id;
        final String label;
        final int sampleWidth;
        final int sampleHeight;
        final boolean scalesWithHud;
        boolean custom;
        String anchorX;
        String anchorY;
        int offsetX;
        int offsetY;
        int x;
        int y;
        int width;
        int height;

        ElementHandle(int id, String label, int sampleWidth, int sampleHeight, boolean scalesWithHud) {
            this.id = id;
            this.label = label;
            this.sampleWidth = sampleWidth;
            this.sampleHeight = sampleHeight;
            this.scalesWithHud = scalesWithHud;
        }

        boolean contains(int mouseX, int mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    private final List<ElementHandle> elements = new ArrayList<ElementHandle>();
    private ElementHandle dragging;
    private int grabDX;
    private int grabDY;
    private boolean dirty;
    private int lastMouseX;
    private int lastMouseY;

    @Override
    public void initGui() {
        // Rebuilt on every (re-)init, including window resizes: live state is
        // pushed into the ModConfig static fields on each change, so reading
        // them back here is lossless.
        elements.clear();
        ElementHandle hud = new ElementHandle(ELEMENT_HUD, "Stats Panel", 130, 90, true);
        hud.custom = ModConfig.isHudCustomPosition();
        hud.anchorX = ModConfig.getHudAnchorX();
        hud.anchorY = ModConfig.getHudAnchorY();
        hud.offsetX = ModConfig.getHudAnchorOffsetX();
        hud.offsetY = ModConfig.getHudAnchorOffsetY();
        elements.add(hud);

        ElementHandle killfeed = new ElementHandle(ELEMENT_KILLFEED, "Killfeed", 120, 32, true);
        killfeed.custom = ModConfig.isKillfeedCustomPosition();
        killfeed.anchorX = ModConfig.getKillfeedAnchorX();
        killfeed.anchorY = ModConfig.getKillfeedAnchorY();
        killfeed.offsetX = ModConfig.getKillfeedAnchorOffsetX();
        killfeed.offsetY = ModConfig.getKillfeedAnchorOffsetY();
        elements.add(killfeed);

        ElementHandle summary = new ElementHandle(ELEMENT_MATCH_SUMMARY, "Match Summary", 170, 90, false);
        summary.custom = ModConfig.isMatchSummaryCustomPosition();
        summary.anchorX = ModConfig.getMatchSummaryAnchorX();
        summary.anchorY = ModConfig.getMatchSummaryAnchorY();
        summary.offsetX = ModConfig.getMatchSummaryAnchorOffsetX();
        summary.offsetY = ModConfig.getMatchSummaryAnchorOffsetY();
        elements.add(summary);

        ElementHandle briefing = new ElementHandle(ELEMENT_BRIEFING, "Pre-Game Briefing", 160, 76, false);
        briefing.custom = ModConfig.isPreGameBriefingCustomPosition();
        briefing.anchorX = ModConfig.getPreGameBriefingAnchorX();
        briefing.anchorY = ModConfig.getPreGameBriefingAnchorY();
        briefing.offsetX = ModConfig.getPreGameBriefingAnchorOffsetX();
        briefing.offsetY = ModConfig.getPreGameBriefingAnchorOffsetY();
        elements.add(briefing);

        buttonList.clear();
        buttonList.add(new GuiButton(DONE_BUTTON_ID, this.width / 2 - 40, this.height - 28, 80, 20, "Done"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        drawRect(0, 0, this.width, this.height, 0xB0101010);

        drawCenteredString(fontRendererObj, "HUD Editor", this.width / 2, 8, 0xFFFFFF);
        drawCenteredString(fontRendererObj,
                "Drag elements to move • Scroll over the stats panel to scale",
                this.width / 2, 20, 0xAAAAAA);
        drawCenteredString(fontRendererObj,
                "R over an element resets it • ESC or Done saves",
                this.width / 2, 30, 0xAAAAAA);

        ElementHandle hovered = null;
        for (ElementHandle h : elements) {
            updateRect(h);
            if (h.contains(mouseX, mouseY)) {
                hovered = h;
            }
        }

        for (ElementHandle h : elements) {
            boolean active = h == dragging || (dragging == null && h == hovered);
            int fill = active ? 0x804060A0 : 0x60303030;
            int border = active ? 0xFFFFFFFF : 0xFF909090;
            drawRect(h.x, h.y, h.x + h.width, h.y + h.height, fill);
            drawHollowRect(h.x, h.y, h.width, h.height, border);
            drawCenteredString(fontRendererObj, h.label,
                    h.x + h.width / 2, h.y + h.height / 2 - 8, 0xFFFFFF);
            drawCenteredString(fontRendererObj, describePosition(h),
                    h.x + h.width / 2, h.y + h.height / 2 + 2, 0xBBBBBB);
        }

        if (hovered != null && hovered.id == ELEMENT_HUD) {
            drawCenteredString(fontRendererObj,
                    String.format("Scale: %.1fx", ModConfig.getHudScale()),
                    this.width / 2, this.height - 40, 0xFFFF55);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) {
            return;
        }
        // Last hit wins: matches draw order, so the topmost proxy is grabbed.
        for (int i = elements.size() - 1; i >= 0; i--) {
            ElementHandle h = elements.get(i);
            if (h.contains(mouseX, mouseY)) {
                dragging = h;
                grabDX = mouseX - h.x;
                grabDY = mouseY - h.y;
                // Legacy->custom conversion is deferred to the first actual
                // movement: a click without a drag must not change anything.
                return;
            }
        }
    }

    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (dragging == null || clickedMouseButton != 0) {
            return;
        }
        ElementHandle h = dragging;
        if (!h.custom) {
            // First movement of this press: convert legacy placement into
            // anchor+offset in-place so the element does not jump.
            h.custom = true;
            h.anchorX = HudAnchorMath.nearestAnchorX(h.x, h.width, this.width);
            h.anchorY = HudAnchorMath.nearestAnchorY(h.y, h.height, this.height);
            h.offsetX = HudAnchorMath.offsetXFor(h.anchorX, h.x, h.width, this.width);
            h.offsetY = HudAnchorMath.offsetYFor(h.anchorY, h.y, h.height, this.height);
        }
        int newX = HudAnchorMath.clamp(mouseX - grabDX, 0, this.width - h.width);
        int newY = HudAnchorMath.clamp(mouseY - grabDY, 0, this.height - h.height);
        h.offsetX = HudAnchorMath.offsetXFor(h.anchorX, newX, h.width, this.width);
        h.offsetY = HudAnchorMath.offsetYFor(h.anchorY, newY, h.height, this.height);
        h.x = newX;
        h.y = newY;
        applyLive(h);
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (dragging == null || state != 0) {
            return;
        }
        ElementHandle h = dragging;
        dragging = null;
        if (!h.custom) {
            // Pure click, never dragged — nothing was converted, nothing to snap.
            return;
        }
        // Snap to the nearest anchor (element center vs screen thirds) and
        // recompute offsets so the element stays exactly where it was dropped.
        h.anchorX = HudAnchorMath.nearestAnchorX(h.x, h.width, this.width);
        h.anchorY = HudAnchorMath.nearestAnchorY(h.y, h.height, this.height);
        h.offsetX = HudAnchorMath.offsetXFor(h.anchorX, h.x, h.width, this.width);
        h.offsetY = HudAnchorMath.offsetYFor(h.anchorY, h.y, h.height, this.height);
        applyLive(h);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int dwheel = Mouse.getEventDWheel();
        if (dwheel == 0) {
            return;
        }
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        for (ElementHandle h : elements) {
            if (h.id == ELEMENT_HUD && h.contains(mouseX, mouseY)) {
                double step = dwheel > 0 ? SCALE_STEP : -SCALE_STEP;
                // Round to one decimal so repeated scrolling cannot drift.
                double newScale = Math.round((ModConfig.getHudScale() + step) * 10.0) / 10.0;
                ModConfig.applyHudScaleLive(newScale);
                dirty = true;
                return;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_R && dragging == null) {
            for (int i = elements.size() - 1; i >= 0; i--) {
                ElementHandle h = elements.get(i);
                if (h.contains(lastMouseX, lastMouseY)) {
                    resetToDefaults(h);
                    return;
                }
            }
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == DONE_BUTTON_ID) {
            this.mc.displayGuiScreen(null);
        }
    }

    @Override
    public void onGuiClosed() {
        if (dirty) {
            ModConfig.persistHudEditorLayout();
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    /** Recompute the proxy rect in raw ScaledResolution space. */
    private void updateRect(ElementHandle h) {
        double scale = ModConfig.getHudScale();
        if (h.scalesWithHud) {
            h.width = (int) Math.round(h.sampleWidth * scale);
            h.height = (int) Math.round(h.sampleHeight * scale);
        } else {
            h.width = h.sampleWidth;
            h.height = h.sampleHeight;
        }
        if (h.custom) {
            h.x = HudAnchorMath.computeX(h.anchorX, this.width, h.width, h.offsetX);
            h.y = HudAnchorMath.computeY(h.anchorY, this.height, h.height, h.offsetY);
        } else {
            int[] origin = legacyRawOrigin(h, scale);
            h.x = origin[0];
            h.y = origin[1];
        }
    }

    /**
     * Legacy (pre-editor) position of an element, converted into raw
     * ScaledResolution space. The scaled elements replicate their renderers'
     * math exactly: corner origin in scale-divided space, then multiplied back.
     */
    private int[] legacyRawOrigin(ElementHandle h, double scale) {
        switch (h.id) {
            case ELEMENT_HUD: {
                int sw = (int) (this.width / scale);
                int sh = (int) (this.height / scale);
                int[] o = HudAnchorMath.legacyCornerOrigin(ModConfig.getHudPosition(),
                        sw, sh, h.sampleWidth, h.sampleHeight, 4, 4);
                return new int[]{(int) Math.round(o[0] * scale), (int) Math.round(o[1] * scale)};
            }
            case ELEMENT_KILLFEED: {
                int sw = (int) (this.width / scale);
                int sh = (int) (this.height / scale);
                int[] o = HudAnchorMath.legacyCornerOrigin(ModConfig.getKillfeedAnchor(),
                        sw, sh, h.sampleWidth, h.sampleHeight,
                        ModConfig.getKillfeedOffsetX(), ModConfig.getKillfeedOffsetY());
                return new int[]{(int) Math.round(o[0] * scale), (int) Math.round(o[1] * scale)};
            }
            case ELEMENT_MATCH_SUMMARY:
                return HudAnchorMath.legacyLeftCardOrigin(this.height, h.height, 3, 4);
            default:
                return HudAnchorMath.legacyCardOrigin(this.width, this.height, h.width, h.height, 4);
        }
    }

    /** Push the handle's in-memory state into ModConfig so the live HUD follows. */
    private void applyLive(ElementHandle h) {
        switch (h.id) {
            case ELEMENT_HUD:
                ModConfig.applyHudLayoutLive(h.custom, h.anchorX, h.anchorY, h.offsetX, h.offsetY);
                break;
            case ELEMENT_KILLFEED:
                ModConfig.applyKillfeedLayoutLive(h.custom, h.anchorX, h.anchorY, h.offsetX, h.offsetY);
                break;
            case ELEMENT_MATCH_SUMMARY:
                ModConfig.applyMatchSummaryLayoutLive(h.custom, h.anchorX, h.anchorY, h.offsetX, h.offsetY);
                break;
            default:
                ModConfig.applyPreGameBriefingLayoutLive(h.custom, h.anchorX, h.anchorY, h.offsetX, h.offsetY);
                break;
        }
        dirty = true;
    }

    /**
     * R key: back to the legacy default placement. hudScale is deliberately left
     * untouched — it is a pre-existing user setting, not editor-owned layout
     * state; the scroll wheel remains the only way to change it here.
     */
    private void resetToDefaults(ElementHandle h) {
        h.custom = false;
        switch (h.id) {
            case ELEMENT_HUD:
                h.anchorX = HudAnchorMath.ANCHOR_LEFT;
                h.anchorY = HudAnchorMath.ANCHOR_TOP;
                h.offsetX = 4;
                h.offsetY = 4;
                break;
            case ELEMENT_KILLFEED:
                h.anchorX = HudAnchorMath.ANCHOR_RIGHT;
                h.anchorY = HudAnchorMath.ANCHOR_TOP;
                h.offsetX = -4;
                h.offsetY = 4;
                break;
            default:
                h.anchorX = HudAnchorMath.ANCHOR_CENTER;
                h.anchorY = HudAnchorMath.ANCHOR_CENTER;
                h.offsetX = 0;
                h.offsetY = 0;
                break;
        }
        applyLive(h);
    }

    private static String describePosition(ElementHandle h) {
        if (!h.custom) {
            return "default";
        }
        return h.anchorX + "/" + h.anchorY + " " + signed(h.offsetX) + "," + signed(h.offsetY);
    }

    private static String signed(int value) {
        return value >= 0 ? "+" + value : Integer.toString(value);
    }

    private void drawHollowRect(int x, int y, int w, int h, int color) {
        drawRect(x, y, x + w, y + 1, color);
        drawRect(x, y + h - 1, x + w, y + h, color);
        drawRect(x, y, x + 1, y + h, color);
        drawRect(x + w - 1, y, x + w, y + h, color);
    }
}
