package dev.bukkit.reward;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.storage.progression.ClassProgressionService;
import dev.core.entity.EntityManager;
import dev.core.entity.RPGEntity;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.reward.CaseRollMath;
import dev.core.reward.RewardConfig;
import dev.core.reward.RewardDefinition;
import dev.core.reward.RewardPool;
import dev.core.utils.AdventureText;

/**
 * Runs the reward case flow: eligibility, weighted target selection, the reel
 * animation, claiming/fallback rewards and per-player floating chests.
 */
public final class RewardCaseService {

    public static final String INVENTORY_TITLE = "Reward Case";
    public static final int INVENTORY_SIZE = 27;
    public static final int REEL_START = 9;
    public static final int REEL_SLOTS = 9;
    public static final int CENTER_SLOT = 13;
    public static final int CENTER_INDEX = CENTER_SLOT - REEL_START;

    private static final Material[] RAINBOW_PANES = {
            Material.RED_STAINED_GLASS_PANE,
            Material.ORANGE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE,
            Material.LIME_STAINED_GLASS_PANE,
            Material.GREEN_STAINED_GLASS_PANE,
            Material.CYAN_STAINED_GLASS_PANE,
            Material.LIGHT_BLUE_STAINED_GLASS_PANE,
            Material.BLUE_STAINED_GLASS_PANE,
            Material.PURPLE_STAINED_GLASS_PANE,
            Material.MAGENTA_STAINED_GLASS_PANE
    };

    private static final RewardCaseService INSTANCE = new RewardCaseService();

    private final Random random = new Random();
    private final Map<UUID, ActiveCase> activeCases = new HashMap<>();
    private final Map<UUID, RewardChest> chestsByOwner = new HashMap<>();
    private final Map<UUID, RewardChest> chestsByInteraction = new HashMap<>();

    private Plugin plugin;
    private RewardConfig config;
    private ClassProgressionService progressionService;

    private RewardCaseService() {
    }

    public static RewardCaseService getInstance() {
        return INSTANCE;
    }

    public void init(Plugin plugin, RewardConfig config, ClassProgressionService progressionService) {
        this.plugin = plugin;
        this.progressionService = progressionService;
        this.config = config;
        validateConfiguredRewards();
    }

    public void reload(RewardConfig config) {
        this.config = config;
        validateConfiguredRewards();
    }

    public RewardConfig getConfig() {
        return config;
    }

    public ClassProgressionService getProgressionService() {
        return progressionService;
    }

    public boolean isCaseInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof CaseInventoryHolder;
    }

    /**
     * The rewards this player can currently roll: every XP dye plus reward-only
     * RPG items they have not unlocked yet.
     */
    public List<RewardDefinition> eligibleRewards(Player player) {
        if (config == null || player == null) {
            return List.of();
        }
        List<RewardDefinition> eligible = new ArrayList<>();
        for (RewardDefinition reward : config.getRewards()) {
            if (reward.isXpDye()) {
                eligible.add(reward);
                continue;
            }
            Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(reward.itemId());
            if (item.isEmpty() || !item.get().isRewardOnly()) {
                continue;
            }
            if (progressionService != null
                    && progressionService.isItemUnlocked(player.getUniqueId(), reward.itemId())) {
                continue;
            }
            eligible.add(reward);
        }
        return eligible;
    }

    public boolean canOpen(Player player) {
        return !eligibleRewards(player).isEmpty();
    }

    /**
     * Opens a case directly. Used by the debug command and available for the
     * future reward state.
     */
    public boolean openCase(Player player) {
        if (config == null) {
            player.sendMessage("§cReward config is not loaded.");
            return false;
        }
        if (activeCases.containsKey(player.getUniqueId())) {
            player.sendMessage("§eYou already have a reward case open.");
            return false;
        }

        List<RewardDefinition> pool = eligibleRewards(player);
        if (pool.isEmpty()) {
            player.sendMessage("§cThere are no rewards available for you right now.");
            return false;
        }

        RewardDefinition target = RewardPool.roll(pool, random).orElse(null);
        if (target == null) {
            player.sendMessage("§cCould not roll a reward.");
            return false;
        }

        int totalTicks = config.randomRollTicks(random);
        int maxOffset = CaseRollMath.maxOffsetForTopSpeed(totalTicks, config.getRollTopSpeed());
        int stripLength = maxOffset + REEL_SLOTS;
        List<RewardDefinition> strip = new ArrayList<>(stripLength);
        for (int i = 0; i < stripLength; i++) {
            strip.add(RewardPool.roll(pool, random).orElse(pool.get(0)));
        }
        strip.set(maxOffset + CENTER_INDEX, target);

        List<ItemStack> icons = new ArrayList<>(stripLength);
        for (RewardDefinition reward : strip) {
            icons.add(createIcon(player, reward));
        }

        CaseInventoryHolder holder = new CaseInventoryHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE,
                AdventureText.legacy(INVENTORY_TITLE));
        holder.setInventory(inventory);
        applyBackground(inventory);

        ActiveCase activeCase = new ActiveCase(player.getUniqueId(), inventory, holder, strip, icons, pool,
                target, totalTicks, maxOffset);
        renderFrame(activeCase, 0);
        player.openInventory(inventory);
        activeCases.put(player.getUniqueId(), activeCase);
        activeCase.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(activeCase), 1L, 1L);
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.1f);
        return true;
    }

    public RewardChest spawnChest(Player owner, Location location) {
        if (plugin == null || location == null || location.getWorld() == null) {
            return null;
        }
        RewardChest existing = chestsByOwner.remove(owner.getUniqueId());
        if (existing != null) {
            chestsByInteraction.remove(existing.getInteractionId());
            existing.remove();
        }
        RewardChest chest = RewardChest.spawn(plugin, owner, location);
        chestsByOwner.put(owner.getUniqueId(), chest);
        chestsByInteraction.put(chest.getInteractionId(), chest);
        return chest;
    }

    public RewardChest spawnChestInFront(Player player) {
        Location location = player.getLocation().clone().add(0, 1, 0);
        var direction = player.getLocation().getDirection();
        direction.setY(0);
        if (direction.lengthSquared() > 0.001) {
            location.add(direction.normalize().multiply(2.0));
        }
        return spawnChest(player, location);
    }

    /**
     * Handles a right-click on a reward chest interaction entity.
     *
     * @return true when the interaction belonged to a reward chest and should be
     *         cancelled
     */
    public boolean handleChestInteract(Player player, Interaction interaction) {
        RewardChest chest = chestsByInteraction.get(interaction.getUniqueId());
        if (chest == null) {
            return false;
        }
        if (!chest.getOwnerId().equals(player.getUniqueId())) {
            player.sendMessage("§cThis reward cache belongs to another player.");
            return true;
        }
        if (activeCases.containsKey(player.getUniqueId())) {
            player.sendMessage("§eYou already have a reward case open.");
            return true;
        }
        if (eligibleRewards(player).isEmpty()) {
            player.sendMessage("§cThere are no rewards available for you right now.");
            return true;
        }
        if (!openCase(player)) {
            return true;
        }
        removeChest(chest);
        return true;
    }

    public void handleClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CaseInventoryHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    public void handleDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof CaseInventoryHolder)) {
            return;
        }
        event.setCancelled(true);
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof CaseInventoryHolder holder)) {
            return;
        }
        ActiveCase activeCase = activeCases.get(holder.getPlayerId());
        if (activeCase == null || activeCase.inventory != event.getInventory()) {
            return;
        }
        activeCases.remove(holder.getPlayerId());
        cancelTask(activeCase);
        if (activeCase.claimed) {
            return;
        }
        Player player = Bukkit.getPlayer(holder.getPlayerId());
        if (player == null) {
            return;
        }
        awardFallback(activeCase, player, true);
    }

    public void handleQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        ActiveCase activeCase = activeCases.remove(player.getUniqueId());
        if (activeCase != null) {
            cancelTask(activeCase);
            if (!activeCase.claimed) {
                awardFallback(activeCase, player, false);
            }
        }
        RewardChest chest = chestsByOwner.remove(player.getUniqueId());
        if (chest != null) {
            chestsByInteraction.remove(chest.getInteractionId());
            chest.remove();
        }
    }

    public void shutdown() {
        for (ActiveCase activeCase : new ArrayList<>(activeCases.values())) {
            cancelTask(activeCase);
            Player player = Bukkit.getPlayer(activeCase.playerId);
            if (player != null) {
                player.closeInventory();
            }
        }
        activeCases.clear();
        for (RewardChest chest : new ArrayList<>(chestsByInteraction.values())) {
            chest.remove();
        }
        chestsByInteraction.clear();
        chestsByOwner.clear();
    }

    private void tick(ActiveCase activeCase) {
        Player player = Bukkit.getPlayer(activeCase.playerId);
        if (player == null) {
            activeCases.remove(activeCase.playerId);
            cancelTask(activeCase);
            return;
        }

        activeCase.elapsedTicks++;
        if (activeCase.elapsedTicks % 2 == 0) {
            renderRainbowBorder(activeCase.inventory, activeCase.elapsedTicks / 2);
        }
        activeCase.ticksSinceStep++;
        int offset = CaseRollMath.offsetAtTick(activeCase.elapsedTicks, activeCase.totalTicks,
                activeCase.maxOffset);
        if (offset != activeCase.lastOffset) {
            renderFrame(activeCase, offset);
            playStepClick(player, activeCase);
            activeCase.ticksSinceStep = 0;
        }

        if (activeCase.elapsedTicks >= activeCase.totalTicks) {
            renderFrame(activeCase, activeCase.maxOffset);
            cancelTask(activeCase);
            claim(activeCase, player, activeCase.target);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.6f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
            player.sendMessage("§6The case reveals §f" + activeCase.target.displayName() + "§6!");
        }
    }

    /**
     * Plays one click per visible reel step. Because the step interval grows as
     * the Bezier tail slows down, the clicks space themselves out; the rising
     * pitch makes the final crawl feel more suspenseful without hard-coding a
     * separate sound schedule.
     */
    private void playStepClick(Player player, ActiveCase activeCase) {
        int gap = Math.min(activeCase.ticksSinceStep, 12);
        float pitch = 1.15f + gap * 0.045f;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.2f, pitch);
    }

    private void claim(ActiveCase activeCase, Player player, RewardDefinition reward) {
        if (activeCase.claimed) {
            return;
        }
        activeCase.claimed = true;
        grant(player, reward);
    }

    private void awardFallback(ActiveCase activeCase, Player player, boolean notify) {
        if (activeCase.claimed) {
            return;
        }
        RewardDefinition display = activeCase.currentCenter;
        String excluded = display == null ? activeCase.target.id() : display.id();
        RewardDefinition fallback = RewardPool.rollExcluding(activeCase.eligiblePool, excluded, random).orElse(null);
        activeCase.claimed = true;
        if (fallback == null) {
            if (notify) {
                player.sendMessage("§cThe case closed early, but no alternative reward was available.");
            }
            return;
        }
        grant(player, fallback);
        if (notify) {
            player.sendMessage("§eThe case closed early. You received §f" + fallback.displayName() + "§e.");
        }
    }

    private void grant(Player player, RewardDefinition reward) {
        if (reward.isXpDye()) {
            grantClassXp(player, reward);
            return;
        }
        Optional<RPGItem> itemOpt = RPGItemRegistry.getInstance().getItem(reward.itemId());
        if (itemOpt.isEmpty()) {
            player.sendMessage("§cReward item '" + reward.itemId() + "' is not registered.");
            return;
        }
        RPGItem item = itemOpt.get();
        boolean newlyUnlocked = progressionService != null
                && progressionService.unlockItem(player.getUniqueId(), item.getId());
        if (reward.giveItem()) {
            RPGEntity holder = EntityManager.getInstance().getEntity(player.getUniqueId()).orElse(null);
            ItemStack stack = BukkitItemStackAdapter.toItemStack(item, holder);
            RewardItems.giveOrDrop(player, stack);
            if (holder != null) {
                BukkitInventorySync.syncInventoryDiff(holder, player);
            }
        }
        if (newlyUnlocked) {
            player.sendMessage("§dUnlocked §f" + item.getName() + "§d permanently for the item shop.");
        } else {
            player.sendMessage("§eYou already had §f" + item.getName() + "§e unlocked.");
        }
    }

    private void grantClassXp(Player player, RewardDefinition reward) {
        int range = reward.xpMax() - reward.xpMin();
        int xp = reward.xpMin() + (range <= 0 ? 0 : random.nextInt(range + 1));

        RPGClassType activeClass = progressionService == null ? RPGClassType.NONE
                : progressionService.getActiveClass(player.getUniqueId());
        if (activeClass == null) {
            activeClass = RPGClassType.NONE;
        }

        player.sendMessage("§b+" + xp + " " + activeClass.getDisplayName() + " XP");
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.4f);
        if (progressionService != null) {
            progressionService.addExperience(player.getUniqueId(), activeClass, xp);
        }
    }

    private ItemStack createIcon(Player player, RewardDefinition reward) {
        if (reward.isXpDye()) {
            return RewardItems.createDyeIcon(reward, random);
        }
        Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(reward.itemId());
        if (item.isEmpty()) {
            ItemStack fallback = new ItemStack(Material.BARRIER);
            ItemMeta meta = fallback.getItemMeta();
            if (meta != null) {
                meta.displayName(AdventureText.withoutItalic(AdventureText.legacy("§c" + reward.displayName())));
                fallback.setItemMeta(meta);
            }
            return fallback;
        }
        RPGEntity holder = EntityManager.getInstance().getEntity(player.getUniqueId()).orElse(null);
        return BukkitItemStackAdapter.toItemStack(item.get(), holder);
    }

    private void renderFrame(ActiveCase activeCase, int offset) {
        int clamped = Math.max(0, Math.min(activeCase.maxOffset, offset));
        for (int i = 0; i < REEL_SLOTS; i++) {
            ItemStack icon = activeCase.icons.get(clamped + i);
            activeCase.inventory.setItem(REEL_START + i, icon == null ? null : icon.clone());
        }
        activeCase.currentCenter = activeCase.strip.get(clamped + CENTER_INDEX);
        activeCase.lastOffset = clamped;
    }

    private void applyBackground(Inventory inventory) {
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            inventory.setItem(slot, black.clone());
        }
        renderRainbowBorder(inventory, 0);
        int topRowStart = REEL_START - REEL_SLOTS;
        int bottomRowStart = REEL_START + REEL_SLOTS;
        inventory.setItem(topRowStart + CENTER_INDEX, pane(Material.WHITE_STAINED_GLASS_PANE));
        inventory.setItem(bottomRowStart + CENTER_INDEX, pane(Material.WHITE_STAINED_GLASS_PANE));
    }

    private void renderRainbowBorder(Inventory inventory, int phase) {
        int topRowStart = REEL_START - REEL_SLOTS;
        int bottomRowStart = REEL_START + REEL_SLOTS;
        for (int slot = 0; slot < REEL_SLOTS; slot++) {
            if (slot == CENTER_INDEX) {
                continue;
            }
            Material material = RAINBOW_PANES[Math.floorMod(phase + slot, RAINBOW_PANES.length)];
            inventory.setItem(topRowStart + slot, pane(material));
            inventory.setItem(bottomRowStart + slot, pane(material));
        }
    }

    private static ItemStack pane(Material material) {
        ItemStack pane = new ItemStack(material);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.displayName(AdventureText.withoutItalic(AdventureText.legacy(" ")));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            pane.setItemMeta(meta);
        }
        return pane;
    }

    private void removeChest(RewardChest chest) {
        if (chest == null) {
            return;
        }
        chestsByOwner.remove(chest.getOwnerId());
        chestsByInteraction.remove(chest.getInteractionId());
        chest.remove();
    }

    private void cancelTask(ActiveCase activeCase) {
        if (activeCase.task != null && !activeCase.task.isCancelled()) {
            activeCase.task.cancel();
        }
    }

    private void validateConfiguredRewards() {
        if (config == null) {
            return;
        }
        for (RewardDefinition reward : config.getRewards()) {
            if (!reward.isItemUnlock()) {
                continue;
            }
            Optional<RPGItem> item = RPGItemRegistry.getInstance().getItem(reward.itemId());
            if (item.isEmpty()) {
                System.out.println("[Rewards] Reward '" + reward.id() + "' references unknown item '"
                        + reward.itemId() + "'.");
            } else if (!item.get().isRewardOnly()) {
                System.out.println("[Rewards] Reward '" + reward.id() + "' references item '" + reward.itemId()
                        + "' which is not marked reward-only; it will not enter the pool.");
            }
        }
    }

    private static final class ActiveCase {
        private final UUID playerId;
        private final Inventory inventory;
        @SuppressWarnings("unused")
        private final CaseInventoryHolder holder;
        private final List<RewardDefinition> strip;
        private final List<ItemStack> icons;
        private final List<RewardDefinition> eligiblePool;
        private final RewardDefinition target;
        private final int totalTicks;
        private final int maxOffset;

        private int elapsedTicks;
        private int ticksSinceStep;
        private int lastOffset = -1;
        private RewardDefinition currentCenter;
        private BukkitTask task;
        private boolean claimed;

        private ActiveCase(UUID playerId, Inventory inventory, CaseInventoryHolder holder,
                List<RewardDefinition> strip, List<ItemStack> icons,
                List<RewardDefinition> eligiblePool, RewardDefinition target, int totalTicks, int maxOffset) {
            this.playerId = playerId;
            this.inventory = inventory;
            this.holder = holder;
            this.strip = strip;
            this.icons = icons;
            this.eligiblePool = eligiblePool;
            this.target = target;
            this.totalTicks = totalTicks;
            this.maxOffset = maxOffset;
        }
    }
}
