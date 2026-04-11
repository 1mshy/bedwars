package com.imshy.bedwars.runtime;

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
    public final List<ItemStack> observedHotbarItems = new ArrayList<ItemStack>();
    private final Set<String> seenItemKeys = new HashSet<String>();

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
        observedHotbarItems.clear();
        seenItemKeys.clear();
    }

    public boolean hasAnyData() {
        return diamondCount > 0 || emeraldCount > 0 || armorProtectionLevel > 0 || !observedHotbarItems.isEmpty();
    }
}
