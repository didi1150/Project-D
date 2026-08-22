package dev.bukkit.reload;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import dev.bukkit.DMain;
import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.display.LoreLabels;
import dev.bukkit.storage.BukkitConfigManager;
import dev.core.ability.Ability;
import dev.core.ability.AbilityCost;
import dev.core.ability.AbilityRegistry;
import dev.core.ability.storage.AbilityLoader;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.item.RPGItem;
import dev.core.item.RPGItemSet;
import dev.core.item.equipment.EquipmentSlot;
import dev.core.item.loader.RPGItemLoader;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.storage.config.ConfigProvider;

/**
 * Live reload for abilities.yml + items.yml (+lore.yml) without restart.
 * Keeps EffectFactories intact, swaps registry contents, and re-syncs online
 * players' EquipmentManagers so new stats/abilities/lore take effect.
 */
public final class ItemsAbilitiesReloadService {

    private ItemsAbilitiesReloadService() {}

    public static ReloadResult reload(BukkitConfigManager configManager) {
        long start = System.currentTimeMillis();

        // 1) Reload raw providers from disk
        ConfigProvider abilitiesProvider = configManager.reloadProvider("abilities.yml");
        ConfigProvider itemsProvider = configManager.reloadProvider("items.yml");
        ConfigProvider loreProvider = configManager.reloadProvider("lore.yml");

        // 2) Snapshot old counts for reporting
        int oldAbilities = AbilityRegistry.all().size();
        int oldItems = RPGItemRegistry.getInstance().allItems().size();
        int oldSets = RPGItemRegistry.getInstance().allItemSets().size();

        // 3) Prepare to reset ability costs so removal of a cost section correctly clears costs
        // AbilityLoader only sets cost if section present; removed costs would otherwise linger
        for (Ability a : new ArrayList<>(AbilityRegistry.all().values())) {
            try { a.setCost(AbilityCost.noCost()); } catch (Exception ignored) {}
        }

        // 4) Load abilities FIRST (items reference ability ids)
        Map<String, Ability> loadedAbilities;
        try {
            loadedAbilities = AbilityLoader.loadAll(abilitiesProvider);
        } catch (Exception e) {
            String msg = "Failed to load abilities.yml: " + e.getMessage();
            Bukkit.getLogger().warning(msg);
            return ReloadResult.failure(msg, e);
        }
        // updateAll mutates existing Ability instances (preserves effect factories)
        AbilityRegistry.updateAll(loadedAbilities);

        // 5) Load lore (optional) before items so BukkitLoreRenderer reflects new labels
        try {
            LoreLabels.load(loreProvider);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Lore reload warning: " + e.getMessage());
        }

        // 6) Load items + sets
        Map<String, RPGItem> loadedItems;
        Map<String, RPGItemSet> loadedSets;
        try {
            // RPGItemLoader.loadAll internally also calls loadSets to attach set refs,
            // but we also need the distinct sets map for registry
            loadedSets = RPGItemLoader.loadSets(itemsProvider);
            loadedItems = RPGItemLoader.loadAll(itemsProvider);
        } catch (Exception e) {
            String msg = "Failed to load items.yml: " + e.getMessage();
            Bukkit.getLogger().warning(msg);
            return ReloadResult.failure(msg, e);
        }

        // 7) Atomically replace registry (clear removed ids, add new)
        RPGItemRegistry registry = RPGItemRegistry.getInstance();
        registry.replaceAll(loadedItems, loadedSets);

        // 8) Re-wire online players: re-resolve equipped/inventory ids to new RPGItem instances
        int playersSynced = 0;
        int playersWarned = 0;
        List<String> warnings = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            try {
                var opt = EntityManager.getInstance().getEntity(player.getUniqueId());
                if (opt.isEmpty()) continue;
                RPGEntity rpg = opt.get();
                if (!(rpg instanceof BukkitPlayerEntity bpe)) continue;

                // snapshot current ids before clearing
                Map<EquipmentSlot, String> equippedIds = new HashMap<>();
                for (Map.Entry<EquipmentSlot, RPGItem> e : rpg.getEquipmentManager().getAllEquippedItems().entrySet()) {
                    equippedIds.put(e.getKey(), e.getValue().getId());
                }
                List<String> passiveIds = new ArrayList<>();
                for (RPGItem it : rpg.getEquipmentManager().getInventoryItems()) {
                    passiveIds.add(it.getId());
                }

                // Preserve vanilla ItemStacks' RPG ids before unequip clears mapping?
                // We unequip by slot, which will deregister providers and move to passive, but we
                // snapshot ids so we can re-lookup.

                // Clear equipment manager state: unequip all slots (deregisters active providers)
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    try { rpg.getEquipmentManager().unequipItem(slot); } catch (Exception ignored) {}
                }
                // Clear any leftover passive providers
                for (RPGItem leftover : new ArrayList<>(rpg.getEquipmentManager().getInventoryItems())) {
                    try { rpg.getEquipmentManager().removeFromInventory(leftover); } catch (Exception ignored) {}
                }

                // Re-equip from snapshot using new registry
                for (Map.Entry<EquipmentSlot, String> e : equippedIds.entrySet()) {
                    String id = e.getValue();
                    RPGItemRegistry.getInstance().getItem(id).ifPresentOrElse(newItem -> {
                        try { rpg.getEquipmentManager().equipItem(e.getKey(), newItem); } catch (Exception ex) {
                            warnings.add(player.getName() + ": failed to re-equip " + id + " -> " + ex.getMessage());
                        }
                    }, () -> {
                        warnings.add(player.getName() + ": equipped item '" + id + "' no longer exists, removed");
                    });
                }

                for (String pid : passiveIds) {
                    // Avoid double-adding if it was already re-added as equipped passive move
                    RPGItemRegistry.getInstance().getItem(pid).ifPresent(newItem -> {
                        // Don't add if already equipped
                        if (rpg.getEquipmentManager().isEquipped(newItem)) return;
                        if (!rpg.getEquipmentManager().isInInventory(newItem)) {
                            try { rpg.getEquipmentManager().addToInventory(newItem); } catch (Exception ignored) {}
                        }
                    });
                }

                // Refresh lore to preserve PDC uuids while updating names/stats/abilities
                try { BukkitInventorySync.refreshLore(rpg, player); } catch (Exception ex) {
                    warnings.add(player.getName() + ": lore refresh failed: " + ex.getMessage());
                }

                // Also sync diff to catch any inventory items that changed id
                try { BukkitInventorySync.syncInventoryDiff(rpg, player); } catch (Exception ignored) {}

                playersSynced++;
            } catch (Exception e) {
                playersWarned++;
                warnings.add(player.getName() + ": sync failed: " + e.getMessage());
                Bukkit.getLogger().warning("Items reload: failed to sync " + player.getName() + ": " + e.getMessage());
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        String summary = String.format("abilities %d->%d, items %d->%d (sets %d->%d), players %d (%d warnings) in %dms",
                oldAbilities, loadedAbilities.size(), oldItems, loadedItems.size(), oldSets, loadedSets.size(), playersSynced, playersWarned, elapsed);

        if (!warnings.isEmpty() && warnings.size() <= 10) {
            summary += " | " + String.join("; ", warnings);
        } else if (!warnings.isEmpty()) {
            summary += " | " + warnings.size() + " warnings (see console)";
            for (String w : warnings) Bukkit.getLogger().warning("Items reload warn: " + w);
        }

        return ReloadResult.success(summary, loadedAbilities.size(), loadedItems.size(), loadedSets.size(), playersSynced);
    }

    public static final class ReloadResult {
        public final boolean success;
        public final String message;
        public final Throwable error;
        public final int abilities;
        public final int items;
        public final int sets;
        public final int players;

        private ReloadResult(boolean success, String message, Throwable error, int abilities, int items, int sets, int players) {
            this.success = success;
            this.message = message;
            this.error = error;
            this.abilities = abilities;
            this.items = items;
            this.sets = sets;
            this.players = players;
        }
        public static ReloadResult success(String msg, int a, int i, int s, int p) {
            return new ReloadResult(true, msg, null, a, i, s, p);
        }
        public static ReloadResult failure(String msg, Throwable e) {
            return new ReloadResult(false, msg, e, 0,0,0,0);
        }
    }
}
