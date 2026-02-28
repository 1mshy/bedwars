package com.imshy.bedwars.runtime;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TrackedEnemy {
    public int diamondCount;
    public int emeraldCount;
    public int armorProtectionLevel;
    public final List<ItemStack> observedHotbarItems = new ArrayList<ItemStack>();
    private final Set<String> seenItemKeys = new HashSet<String>();

    public void addHotbarItem(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return;
        }
        String key = Item.itemRegistry.getNameForObject(stack.getItem()).toString();
        if (seenItemKeys.add(key)) {
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
