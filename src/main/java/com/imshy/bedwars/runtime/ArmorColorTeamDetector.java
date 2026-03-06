package com.imshy.bedwars.runtime;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Detects team membership by comparing the dye color of leather armor
 * (helmet and chestplate) that Hypixel Bedwars assigns to each team.
 *
 * <p>
 * In Bedwars every player spawns with a leather helmet and chestplate
 * dyed to their team color. Armor upgrades only affect pants and boots,
 * so the helmet and chestplate always remain as dyed leather — making
 * this the most reliable team identification signal available.
 */
public final class ArmorColorTeamDetector {

    /** Armor slot index for helmet (getCurrentArmor is 0=boots … 3=helmet). */
    private static final int SLOT_HELMET = 3;
    /** Armor slot index for chestplate. */
    private static final int SLOT_CHESTPLATE = 2;

    private ArmorColorTeamDetector() {
    }

    /**
     * Extracts the dye color from a player's leather helmet or chestplate.
     *
     * <p>
     * Checks the helmet first. If the helmet is missing or not leather,
     * falls back to the chestplate. Returns {@code null} if neither slot
     * contains dyed leather armor.
     *
     * @param player the player to inspect
     * @return the RGB dye color as an {@code Integer}, or {@code null}
     */
    public static Integer getTeamArmorColor(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        // Try helmet first, then chestplate
        Integer color = getLeatherDyeColor(player.getCurrentArmor(SLOT_HELMET));
        if (color != null) {
            return color;
        }
        return getLeatherDyeColor(player.getCurrentArmor(SLOT_CHESTPLATE));
    }

    /**
     * Returns {@code true} if both players are wearing leather armor dyed
     * to the same color, indicating they are on the same team.
     *
     * <p>
     * Returns {@code false} if either player's armor color cannot be
     * determined (e.g. no leather armor equipped).
     *
     * @param a first player
     * @param b second player
     * @return {@code true} when armor colors match
     */
    public static boolean hasSameTeamColor(EntityPlayer a, EntityPlayer b) {
        Integer colorA = getTeamArmorColor(a);
        Integer colorB = getTeamArmorColor(b);
        if (colorA == null || colorB == null) {
            return false;
        }
        return colorA.intValue() == colorB.intValue();
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Reads the {@code display.color} NBT tag from a leather armor piece.
     *
     * @param stack the armor item stack (may be {@code null})
     * @return the RGB color, or {@code null} if the item is not dyed leather armor
     */
    private static Integer getLeatherDyeColor(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        if (!(stack.getItem() instanceof ItemArmor)) {
            return null;
        }

        ItemArmor armor = (ItemArmor) stack.getItem();

        // Only leather armor carries dye colors
        if (armor.getArmorMaterial() != ItemArmor.ArmorMaterial.LEATHER) {
            return null;
        }

        // The color is stored in the NBT path: tag.display.color
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null || !tag.hasKey("display", 10)) { // 10 = compound
            return null;
        }

        NBTTagCompound display = tag.getCompoundTag("display");
        if (!display.hasKey("color", 3)) { // 3 = int
            return null;
        }

        return Integer.valueOf(display.getInteger("color"));
    }
}
