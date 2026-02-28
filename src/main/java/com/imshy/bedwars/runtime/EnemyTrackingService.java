package com.imshy.bedwars.runtime;

import com.imshy.bedwars.ModConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class EnemyTrackingService {

    private static final long ARMOR_HELD_SCAN_INTERVAL = 500; // ms (every ~10 ticks)

    private final RuntimeState state;
    private final MatchThreatService matchThreatService;

    public EnemyTrackingService(RuntimeState state, MatchThreatService matchThreatService) {
        this.state = state;
        this.matchThreatService = matchThreatService;
    }

    public void scanItemPickups(Minecraft mc) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        Map<Integer, double[]> currentItems = new HashMap<Integer, double[]>();

        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (!(entity instanceof EntityItem)) {
                continue;
            }
            EntityItem entityItem = (EntityItem) entity;
            ItemStack stack = entityItem.getEntityItem();
            if (stack == null || stack.getItem() == null) {
                continue;
            }

            boolean isDiamond = stack.getItem() == Items.diamond;
            boolean isEmerald = stack.getItem() == Items.emerald;
            if (!isDiamond && !isEmerald) {
                continue;
            }

            currentItems.put(entity.getEntityId(), new double[]{
                    entity.posX, entity.posY, entity.posZ,
                    isDiamond ? 1.0 : 0.0,
                    stack.stackSize
            });
        }

        // Check which previously tracked items have disappeared (picked up)
        double pickupRange = ModConfig.getEnemyTrackingPickupRange();
        double pickupRangeSq = pickupRange * pickupRange;

        for (Map.Entry<Integer, double[]> prev : state.trackedResourceItems.entrySet()) {
            if (currentItems.containsKey(prev.getKey())) {
                continue;
            }

            // Item vanished — find nearest non-teammate player
            double[] data = prev.getValue();
            double itemX = data[0];
            double itemY = data[1];
            double itemZ = data[2];
            boolean isDiamond = data[3] == 1.0;
            int stackSize = (int) data[4];

            EntityPlayer nearest = findNearestEnemy(mc, itemX, itemY, itemZ, pickupRangeSq);
            if (nearest != null) {
                TrackedEnemy tracked = getOrCreateTracked(nearest.getName());
                if (isDiamond) {
                    tracked.diamondCount += stackSize;
                } else {
                    tracked.emeraldCount += stackSize;
                }
            }
        }

        state.trackedResourceItems.clear();
        state.trackedResourceItems.putAll(currentItems);
    }

    public void scanArmorAndHeldItems(Minecraft mc, long currentTime) {
        if (mc.theWorld == null || mc.thePlayer == null) {
            return;
        }

        if (currentTime - state.lastArmorHeldItemScan < ARMOR_HELD_SCAN_INTERVAL) {
            return;
        }
        state.lastArmorHeldItemScan = currentTime;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }
            if (matchThreatService.isTeammate(mc, mc.thePlayer, player)) {
                continue;
            }

            TrackedEnemy tracked = getOrCreateTracked(player.getName());

            // Scan armor for highest Protection enchantment level
            int maxProt = 0;
            for (int slot = 0; slot < 4; slot++) {
                ItemStack armor = player.getCurrentArmor(slot);
                if (armor != null) {
                    int protLevel = EnchantmentHelper.getEnchantmentLevel(Enchantment.protection.effectId, armor);
                    if (protLevel > maxProt) {
                        maxProt = protLevel;
                    }
                }
            }
            tracked.armorProtectionLevel = maxProt;

            // Track held item
            ItemStack held = player.getHeldItem();
            if (held != null) {
                tracked.addHotbarItem(held);
            }
        }
    }

    public void handleDeathMessage(String playerName) {
        TrackedEnemy tracked = state.trackedEnemies.get(playerName);
        if (tracked != null) {
            tracked.clear();
        }
    }

    public void clearAll() {
        state.trackedEnemies.clear();
        state.trackedResourceItems.clear();
        state.lastArmorHeldItemScan = 0;
    }

    public TrackedEnemy getTrackedEnemy(String playerName) {
        return state.trackedEnemies.get(playerName);
    }

    public Map<String, TrackedEnemy> getAllTrackedEnemies() {
        return state.trackedEnemies;
    }

    private TrackedEnemy getOrCreateTracked(String name) {
        TrackedEnemy tracked = state.trackedEnemies.get(name);
        if (tracked == null) {
            tracked = new TrackedEnemy();
            state.trackedEnemies.put(name, tracked);
        }
        return tracked;
    }

    private EntityPlayer findNearestEnemy(Minecraft mc, double x, double y, double z, double maxDistSq) {
        EntityPlayer nearest = null;
        double nearestDistSq = maxDistSq;

        for (EntityPlayer player : mc.theWorld.playerEntities) {
            if (player.getUniqueID().equals(mc.thePlayer.getUniqueID())) {
                continue;
            }
            if (matchThreatService.isTeammate(mc, mc.thePlayer, player)) {
                continue;
            }

            double dx = player.posX - x;
            double dy = player.posY - y;
            double dz = player.posZ - z;
            double distSq = dx * dx + dy * dy + dz * dz;

            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearest = player;
            }
        }

        return nearest;
    }
}
