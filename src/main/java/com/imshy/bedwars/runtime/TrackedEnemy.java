package com.imshy.bedwars.runtime;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class TrackedEnemy {
    public int diamondCount;
    public int emeraldCount;
    public int armorProtectionLevel;
    /**
     * Highest armor material tier observed on this enemy so far.
     * 0 = none, 1 = leather, 2 = chain, 3 = iron, 4 = diamond.
     */
    public int armorTier;
    public final List<ItemStack> observedHotbarItems = new ArrayList<ItemStack>();
    private final Set<String> seenItemKeys = new HashSet<String>();

    // Last-seen tracking for the off-screen "last seen" arrow HUD overlay.
    // {@code lastSeenTime} is 0 when never observed.
    public double lastSeenX;
    public double lastSeenY;
    public double lastSeenZ;
    public long lastSeenTime;

    private enum ToolCategory { SWORD, PICKAXE, AXE }

    private static class ToolInfo {
        final ToolCategory category;
        final int tier;
        ToolInfo(ToolCategory category, int tier) {
            this.category = category;
            this.tier = tier;
        }
    }

    private static ToolInfo classifyTool(Item item) {
        if (item == null) return null;
        if (item == Items.wooden_sword)  return new ToolInfo(ToolCategory.SWORD, 0);
        if (item == Items.stone_sword)   return new ToolInfo(ToolCategory.SWORD, 1);
        if (item == Items.iron_sword)    return new ToolInfo(ToolCategory.SWORD, 2);
        if (item == Items.diamond_sword) return new ToolInfo(ToolCategory.SWORD, 3);
        if (item == Items.golden_sword)  return new ToolInfo(ToolCategory.SWORD, 0);

        if (item == Items.wooden_axe)  return new ToolInfo(ToolCategory.AXE, 0);
        if (item == Items.stone_axe)   return new ToolInfo(ToolCategory.AXE, 1);
        if (item == Items.iron_axe)    return new ToolInfo(ToolCategory.AXE, 2);
        if (item == Items.diamond_axe) return new ToolInfo(ToolCategory.AXE, 3);
        if (item == Items.golden_axe)  return new ToolInfo(ToolCategory.AXE, 0);

        if (item == Items.wooden_pickaxe)  return new ToolInfo(ToolCategory.PICKAXE, 0);
        if (item == Items.stone_pickaxe)   return new ToolInfo(ToolCategory.PICKAXE, 0);
        if (item == Items.iron_pickaxe)    return new ToolInfo(ToolCategory.PICKAXE, 1);
        if (item == Items.golden_pickaxe)  return new ToolInfo(ToolCategory.PICKAXE, 2);
        if (item == Items.diamond_pickaxe) return new ToolInfo(ToolCategory.PICKAXE, 3);

        return null;
    }

    public void addHotbarItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        String key = Item.itemRegistry.getNameForObject(stack.getItem()).toString();
        ToolInfo info = classifyTool(stack.getItem());

        if (info == null) {
            if (seenItemKeys.add(key)) {
                observedHotbarItems.add(stack.copy());
            }
            return;
        }

        int existingIndex = -1;
        int existingTier = -1;
        String existingKey = null;
        for (int i = 0; i < observedHotbarItems.size(); i++) {
            ItemStack existing = observedHotbarItems.get(i);
            ToolInfo existingInfo = classifyTool(existing.getItem());
            if (existingInfo != null && existingInfo.category == info.category) {
                existingIndex = i;
                existingTier = existingInfo.tier;
                existingKey = Item.itemRegistry.getNameForObject(existing.getItem()).toString();
                break;
            }
        }

        if (existingIndex < 0) {
            if (seenItemKeys.add(key)) {
                observedHotbarItems.add(stack.copy());
            }
            return;
        }

        if (info.tier > existingTier) {
            observedHotbarItems.remove(existingIndex);
            seenItemKeys.remove(existingKey);
            seenItemKeys.add(key);
            observedHotbarItems.add(stack.copy());
        }
    }

    public void clear() {
        diamondCount = 0;
        emeraldCount = 0;
        armorProtectionLevel = 0;
        armorTier = 0;
        observedHotbarItems.clear();
        seenItemKeys.clear();
        lastSeenTime = 0;
    }

    public boolean hasAnyData() {
        return diamondCount > 0 || emeraldCount > 0 || armorProtectionLevel > 0
                || armorTier > 0 || !observedHotbarItems.isEmpty();
    }

    public void recordSighting(double x, double y, double z, long now) {
        this.lastSeenX = x;
        this.lastSeenY = y;
        this.lastSeenZ = z;
        this.lastSeenTime = now;
    }

    /**
     * Classify an armor item into a tier from 0 (none) to 4 (diamond).
     */
    public static int classifyArmorTier(Item item) {
        if (item == null) return 0;
        if (item == Items.leather_helmet      || item == Items.leather_chestplate
         || item == Items.leather_leggings    || item == Items.leather_boots) return 1;
        if (item == Items.chainmail_helmet    || item == Items.chainmail_chestplate
         || item == Items.chainmail_leggings  || item == Items.chainmail_boots) return 2;
        if (item == Items.iron_helmet         || item == Items.iron_chestplate
         || item == Items.iron_leggings       || item == Items.iron_boots) return 3;
        if (item == Items.diamond_helmet      || item == Items.diamond_chestplate
         || item == Items.diamond_leggings    || item == Items.diamond_boots) return 4;
        return 0;
    }

    public static String armorTierLabel(int tier) {
        switch (tier) {
            case 1:  return "LEAT";
            case 2:  return "CHAIN";
            case 3:  return "IRON";
            case 4:  return "DIA";
            default: return "NONE";
        }
    }

    /**
     * Build a compact one-line loadout summary like
     * {@code "IRON \u00b7 DIA-SWORD \u00b7 BOW \u00b7 SHEARS"} from whatever has been
     * observed so far. Returns an empty string when nothing has been tracked.
     */
    public String formatLoadoutCompact() {
        List<String> parts = new ArrayList<String>();

        if (armorTier > 0) {
            parts.add(armorTierLabel(armorTier));
        }

        int bestSwordTier = -1;
        boolean hasBow = false;
        boolean hasShears = false;
        boolean hasFireball = false;
        boolean hasPearl = false;
        boolean hasTnt = false;
        boolean hasGap = false;
        for (ItemStack stack : observedHotbarItems) {
            if (stack == null || stack.getItem() == null) continue;
            Item it = stack.getItem();
            ToolInfo info = classifyTool(it);
            if (info != null && info.category == ToolCategory.SWORD) {
                if (info.tier > bestSwordTier) {
                    bestSwordTier = info.tier;
                }
            }
            if (it == Items.bow) hasBow = true;
            else if (it == Items.shears) hasShears = true;
            else if (it == Items.fire_charge) hasFireball = true;
            else if (it == Items.ender_pearl) hasPearl = true;
            else if (it == Items.golden_apple) hasGap = true;
            else if (it == Item.getItemFromBlock(Blocks.tnt)) hasTnt = true;
        }

        if (bestSwordTier >= 0) {
            parts.add(swordLabel(bestSwordTier) + "-SWORD");
        }
        if (hasBow) parts.add("BOW");
        if (hasShears) parts.add("SHEARS");
        if (hasPearl) parts.add("PEARL");
        if (hasFireball) parts.add("FB");
        if (hasTnt) parts.add("TNT");
        if (hasGap) parts.add("GAP");

        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" \u00b7 ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static String swordLabel(int tier) {
        switch (tier) {
            case 0:  return "WOOD";
            case 1:  return "STONE";
            case 2:  return "IRON";
            case 3:  return "DIA";
            default: return "?";
        }
    }
}
