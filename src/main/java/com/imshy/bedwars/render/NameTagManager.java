package com.imshy.bedwars.render;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Singleton that manages the vertical stacking order of per-player world-space nametag layers.
 *
 * <p>Layer order (bottom → top, closest to player head first):
 * <ol>
 *   <li>Vanilla nametag – rendered by Minecraft itself, no offset needed here</li>
 *   <li>{@link NameTagLayer#THREAT_LEVEL} – [HIGH] 500⭐ 3.2 FKDR</li>
 *   <li>{@link NameTagLayer#INVISIBLE} – 👁 INVISIBLE indicator</li>
 *   <li>{@link NameTagLayer#HOTBAR_INTEL} – diamonds, armor prot, hotbar items</li>
 * </ol>
 *
 * <p>All rendering methods in {@link BedwarsOverlayRenderer} delegate their Y-offset
 * calculation here so that every layer is guaranteed to have its own clear row.
 */
public final class NameTagManager {

    /** Gap from the top of the entity's bounding box to the vanilla nametag. */
    private static final float BASE_OFFSET = 0.5F;

    /**
     * Extra clearance above the vanilla nametag baseline before our first custom layer.
     * Keeps THREAT_LEVEL from rendering on top of the player's own username tag.
     */
    private static final float VANILLA_CLEARANCE = 0.35F;

    /**
     * Height (in world units) allocated to each label row.
     * At the default render scale (0.025F), a Minecraft font glyph is ~0.22F tall,
     * so 0.30F provides a comfortable gap between rows.
     */
    private static final float LINE_HEIGHT = 0.30F;

    // -------------------------------------------------------------------------
    // Singleton
    // -------------------------------------------------------------------------

    private static final NameTagManager INSTANCE = new NameTagManager();

    private NameTagManager() {}

    public static NameTagManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Layer enum
    // -------------------------------------------------------------------------

    /**
     * Ordered layers for per-player nametag rows.
     * The {@code slot} value determines vertical position: slot 0 is closest to the head.
     */
    public enum NameTagLayer {
        /** Bedwars threat level, star count, and FKDR. Slot 0 – closest to the vanilla name. */
        THREAT_LEVEL(0),
        /** 👁 INVISIBLE warning indicator. Slot 1 – between threat and hotbar intel. */
        INVISIBLE(1),
        /** Diamond/emerald counts, armor protection level, and observed hotbar items. Slot 2 – topmost. */
        HOTBAR_INTEL(2);

        public final int slot;

        NameTagLayer(int slot) {
            this.slot = slot;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the Y world-offset (relative to the entity's feet) at which a label
     * for the given layer should be translated before billboarding.
     *
     * <p>Formula: {@code player.height + BASE_OFFSET + VANILLA_CLEARANCE + (layer.slot * LINE_HEIGHT)}
     *
     * @param player the target entity (used for {@code player.height})
     * @param layer  which nametag row to place the label in
     * @return the Y offset to pass to {@code GlStateManager.translate}
     */
    public float computeHeightOffset(EntityPlayer player, NameTagLayer layer) {
        return player.height + BASE_OFFSET + VANILLA_CLEARANCE + (layer.slot * LINE_HEIGHT);
    }
}
