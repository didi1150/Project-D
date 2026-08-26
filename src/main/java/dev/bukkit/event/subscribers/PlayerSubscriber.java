package dev.bukkit.event.subscribers;

import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.item.BowArrowManager;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.ability.AbilityAction;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.event.EventAction;
import dev.core.event.EventSubscriber;
import dev.core.event.Subscribe;
import dev.core.utils.MessageComponent;
import dev.core.utils.MessageText;

@EventSubscriber
public class PlayerSubscriber {

    final double STILL = -0.0784000015258789;
    private final Plugin plugin;

    public PlayerSubscriber(Plugin plugin) {
        this.plugin = plugin;

        ProtocolLibrary.getProtocolManager()
                .addPacketListener(new PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.CAMERA) {
                    @Override
                    public void onPacketSending(PacketEvent event) {
                        Player player = event.getPlayer();
                        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
                        if (((optional.isPresent() && !optional.get().isAlive())
                                || EntityManager.getInstance().isSpectator(player.getUniqueId()))
                                && player.getSpectatorTarget() == null) {
                            BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
                            playerEntity.syncState();
                        }
                    }
                });
    }

    @Subscribe(priority = EventAction.LOWEST_PRIORITY)
    public void onJoin(PlayerJoinEvent event) {
//        BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(event.getPlayer());
//        EntityManager.getInstance().registerEntity(playerEntity);
//        playerEntity = (BukkitPlayerEntity) EntityManager.getInstance().getEntity(playerEntity.getUuid()).get();
//        playerEntity.syncState();
//        BukkitInventorySync.syncInventory(playerEntity, event.getPlayer());
//        for (RPGClassType rpgClassType : RPGClassType.values()) {
//            PlayerClassProgression progression = playerEntity.getPlayerProgression().getProgression(rpgClassType);
//            PlayerClassProgression cachedProgression = classProgressionService
//                    .getProgression(event.getPlayer().getUniqueId(), rpgClassType);
//            progression.setLevel(cachedProgression.getLevel());
//            progression.setUsableItems(cachedProgression.getUsableItems());
//            progression.setXp(cachedProgression.getXp());
//        }
//        playerEntity.getPlayerProgression().setActiveClass(
//                classProgressionService.getActiveClass(playerEntity.getUuid()), playerEntity.getStatManager());
        BukkitMessageSender.getInstance().sendLine(event.getPlayer(), ChatColor.AQUA.toString());
        BukkitMessageSender.getInstance().sendCenteredMessage(event.getPlayer(),
                MessageComponent.of(MessageText.INFO_PLAYER_JOINED, event.getPlayer().getName()));
        BukkitMessageSender.getInstance().sendLine(event.getPlayer(), ChatColor.AQUA.toString());
        event.setJoinMessage("");
        event.getPlayer().setFoodLevel(20);
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("&a&m                                                                             "), false);
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("<red>Okay, so, this message is longer than 1 line of text. The color code should pass and it should center</red>"));
//			BukkitMessageSender.getInstance().sendDebugMessage(event.getPlayer(), MessageComponent.of("If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default. Show the changes recorded in the stash entry as a diff between the stashed contents and the commit back when the stash entry was first created. By default, the command shows the diffstat, but it will accept any format known to git diff (e.g., git stash show -p stash@{1} to view the second most recent entry in patch form). If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default."));
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("&a&m                                                                             "));
//			BukkitMessageSender.getInstance().sendCenteredMessage(event.getPlayer(), MessageComponent.of("<red>Okay, so, this message is longer than 1 line of text. The color code should pass and it should center</red>"));
//			BukkitMessageSender.getInstance().sendCenteredMessage(MessageComponent.of("Okay, so, this message is longer than 1 line of text. The color code should pass and it should center"));
//			BukkitMessageSender.getInstance().sendLine(event.getPlayer(), "<bold><blue> </blue></bold>");
//			BukkitMessageSender.getInstance().sendMessage(event.getPlayer(), MessageComponent.of("&a&m                                                                              "), false);
//			BukkitMessageSender.getInstance().sendLine(event.getPlayer(), ChatColor.AQUA.toString());
//			BukkitMessageSender.getInstance().sendCenteredDebugMessage(event.getPlayer(), MessageComponent.of( "If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default. Show the changes recorded in the stash entry as a diff between the stashed contents and the commit back when the stash entry was first created. By default, the command shows the diffstat, but it will accept any format known to git diff (e.g., git stash show -p stash@{1} to view the second most recent entry in patch form). If no <diff-option> is provided, the default behavior will be given by the stash.showStat, and stash.showPatch config variables. You can also use stash.showIncludeUntracked to set whether --include-untracked is enabled by default."));
    }

    @Subscribe
    public void onSpawnWorld(PlayerSpawnLocationEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        optional.ifPresent(rpgEntity -> {
            BukkitInventorySync.syncInventory(rpgEntity, event.getPlayer());
        });
        refreshLore(event.getPlayer());
    }

    @Subscribe
    public void onSwap(PlayerItemHeldEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        optional.ifPresent(rpgEntity -> {
            BukkitInventorySync.updateMainHand(rpgEntity, event.getPlayer(), event.getNewSlot());
        });
        refreshLore(event.getPlayer());
    }

    @Subscribe
    public void onBowSlotSwap(PlayerItemHeldEvent event) {
        BowArrowManager.onSlotSwap(event.getPlayer(), event.getNewSlot());
    }

    @Subscribe
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        optional.ifPresent(rpgEntity -> {
            BukkitInventorySync.updateMainAndOffHand(rpgEntity, event.getPlayer(), event.getOffHandItem(),
                    event.getMainHandItem());
        });
        refreshLore(event.getPlayer());
    }

    @Subscribe
    public void onBowSwapHands(PlayerSwapHandItemsEvent event) {
        if (event.getPlayer() instanceof Player player) {
            // PlayerSwapHandItemsEvent fires before the swap is applied.
            Bukkit.getScheduler().runTask(plugin, () -> BowArrowManager.onSwapHands(player));
        }
    }

    @Subscribe
    public void onInteract(PlayerInteractEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        if (optional.isPresent()) {
            if (optional.get().isAlive()) {

                if (event.getAction().toString().contains("RIGHT_CLICK")) {
                    if (event.getPlayer().isSneaking()) {
                        optional.get().triggerAbility(AbilityAction.SHIFT_RIGHT_CLICK);
//                            optional.get().triggerAbility(AbilityAction.RIGHT_CLICK);
                    } else {
                        optional.get().triggerAbility(AbilityAction.RIGHT_CLICK);
                    }

                    // Right-click quick-equip (e.g. armor held in the hand) is
                    // applied by the server's item-use handling AFTER
                    // PlayerInteractEvent fires, so a deferred diff picks up the
                    // new armor slot.
                    if (holdsRpgItem(event.getPlayer())) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            BukkitInventorySync.syncInventoryDiff(optional.get(), event.getPlayer());
                        });
                        refreshLore(event.getPlayer());
                    }
                }

                if (event.getAction().toString().contains("LEFT_CLICK")) {
                    if (event.getPlayer().isSneaking()) {
                        optional.get().triggerAbility(AbilityAction.SHIFT_LEFT_CLICK);
                        optional.get().triggerAbility(AbilityAction.LEFT_CLICK);
                    } else {
                        optional.get().triggerAbility(AbilityAction.LEFT_CLICK);
                    }
                }
            }
        }
    }

    @Subscribe(priority = EventAction.HIGHEST_PRIORITY)
    public void onInteractUncancel(PlayerInteractEvent event) {
        event.setCancelled(false);
    }

    @Subscribe
    public void onInteractSpectator(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        if ((optional.isPresent() && !optional.get().isAlive())
                || EntityManager.getInstance().isSpectator(player.getUniqueId())) {
            if (player.equals(event.getRightClicked())) {
                return;
            }
            player.setGameMode(GameMode.SPECTATOR);
            player.setSpectatorTarget(event.getRightClicked());
        }
    }

    @Subscribe
    public void onJump(PlayerMoveEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        if (optional.isPresent()) {
            Player player = event.getPlayer();
            boolean isJumping = player.getVelocity().getY() > STILL;
            if (isJumping) {
                optional.get().triggerAbility(AbilityAction.JUMP);
            }
        }
    }

    @Subscribe
    public void onShift(PlayerToggleSneakEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        if (optional.isPresent()) {
            BukkitPlayerEntity playerEntity = (BukkitPlayerEntity) optional.get();
            if (playerEntity.isAlive()) {
                if (event.isSneaking()) {
                    playerEntity.triggerAbility(AbilityAction.SHIFT);
                }
            }
        }
    }

    @Subscribe
    public void onRespawn(PlayerRespawnEvent event) {
        Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId());
        if (optional.isPresent()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                BukkitInventorySync.syncInventory(optional.get(), event.getPlayer());
            });
        }
        refreshLore(event.getPlayer());
    }

    @Subscribe
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
            if (optional.isPresent()) {
                // Deferred: InventoryClickEvent fires BEFORE the click is applied
                // to the inventory, so a synchronous diff would read the stale
                // pre-click state and miss shift-click / drag equips.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BukkitInventorySync.syncInventoryDiff(optional.get(), player);
                });
            }
            refreshLore(player);
        }
    }

    @Subscribe
    public void onBowInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && ((event.getClickedInventory() == player.getInventory()
                && BowArrowManager.isPlantedArrowSlot(player, event.getSlot()))
                || BowArrowManager.isPlantedArrows(player, event.getCursor()))) {
            event.setCancelled(true);
        }
    }

    @Subscribe
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            Optional<RPGEntity> optional = EntityManager.getInstance().getEntity(player.getUniqueId());
            if (optional.isPresent()) {
                // See onInventoryClick: drags also fire before the
                // inventory is mutated.
                Bukkit.getScheduler().runTask(plugin, () -> {
                    BukkitInventorySync.syncInventoryDiff(optional.get(), player);
                });
            }
            refreshLore(player);
        }
    }

    @Subscribe
    public void onBowInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            for (int slot : event.getInventorySlots()) {
                if (BowArrowManager.isPlantedArrowSlot(player, slot)) {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }

    @Subscribe
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            EntityManager.getInstance().getEntity(player.getUniqueId())
                    .ifPresent(rpg -> BukkitInventorySync.syncInventoryDiff(rpg, player));
            refreshLore(player);
        }
    }

    @Subscribe
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(rpg -> {
                    BukkitInventorySync.syncInventoryDiff(rpg, player);
                });
            });
            refreshLore(player);
        }
    }

    @Subscribe
    public void onBowPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack stack = event.getItem().getItemStack();
            if (BowArrowManager.isArrowMaterial(stack.getType()) && !BowArrowManager.isBounceArrow(stack)) {
                event.setCancelled(true);
            }
        }
    }

    @Subscribe
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(rpg -> {
                    BukkitInventorySync.syncInventoryDiff(rpg, player);
                });
            });
            refreshLore(player);
        }
    }

    @Subscribe
    public void onBowDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (player != null && BowArrowManager.isPlantedArrows(player, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @Subscribe
    public void onBowShoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player player && event.getProjectile() instanceof Projectile projectile) {
            // Vanilla consumes the arrow after the event; defer the refresh
            // so the stack is topped back up once the shot completes.
            Bukkit.getScheduler().runTask(plugin, () -> BowArrowManager.onShoot(player, projectile));
        }
    }

    @Subscribe
    public void onBowQuit(PlayerQuitEvent event) {
        BowArrowManager.onCleanup(event.getPlayer());
    }

    /**
     * Releases every ability binding held by the disconnecting player's entity
     * (behavior deactivation + registry untrack + listener unsubscribe), so
     * stale entries don't accumulate under discarded RPGEntity instances across
     * rejoins. Idempotent with the per-behavior quit cleanups.
     */
    @Subscribe
    public void onAbilityQuit(PlayerQuitEvent event) {
        EntityManager.getInstance().getEntity(event.getPlayer().getUniqueId())
                .ifPresent(entity -> entity.getEquipmentManager().cleanupBindings());
    }

    @Subscribe
    public void onBowDeath(PlayerDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            BowArrowManager.onCleanup(player);
        }
    }

    @Subscribe
    public void onBowPickupArrow(PlayerPickupArrowEvent event) {
        if (event.getItem() != null && BowArrowManager.isArrowMaterial(event.getItem().getItemStack().getType())
                && !BowArrowManager.isBounceArrow(event.getArrow())) {
            event.setCancelled(true);
        }
    }

    /**
     * True if the player holds an RPG item in either hand. Right-clicking with such
     * an item can quick-equip it (armor), which the server applies after
     * PlayerInteractEvent; a deferred inventory diff keeps the equipment manager in
     * sync.
     */
    private static boolean holdsRpgItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return BukkitItemStackAdapter.getRpgItemId(mainHand) != null
                || BukkitItemStackAdapter.getRpgItemId(offHand) != null;
    }

    /**
     * Re-renders the player's item lore holder-aware on the next tick, so the
     * inventory has settled after the triggering event. Keeps holder-dependent lore
     * (e.g. the Mage Set's mana discount on ability costs) accurate when the
     * player's equipment changes.
     */
    private void refreshLore(Player player) {
        EntityManager.getInstance().getEntity(player.getUniqueId()).ifPresent(
                rpg -> Bukkit.getScheduler().runTask(plugin, () -> BukkitInventorySync.refreshLore(rpg, player)));
    }
}
