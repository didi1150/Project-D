package dev.bukkit.game.boss.pillar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import dev.bukkit.entity.BukkitEntityMetadata;
import dev.bukkit.event.BukkitEventBus;
import dev.bukkit.utils.bossfight.ResourceCrateSpawner;
import dev.core.event.EventAction;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.utils.AdventureText;

public final class PillarManager {

    private final World world;
    private final Location bossBase;
    private Location bossLocation;
    private final PillarConfig config;
    private final List<Location> bases;
    private final List<Location> chainLinks;
    private final List<Location> crateLocations;
    private final boolean debugManager;
    private final Map<Integer, Pillar> pillars = new LinkedHashMap<>();
    private final Map<UUID, Integer> aggroAssignments = new HashMap<>();
    private final List<ResourceCrateSpawner> crates = new ArrayList<>();
    private final Random random = new Random();

    private EventAction<EntityDamageByEntityEvent> damageAction;
    private EventAction<EntityDamageEvent> genericDamageAction;
    private EventAction<PlayerInteractEvent> interactAction;
    private EventAction<PlayerInteractEntityEvent> interactEntityAction;
    private boolean started;
    private int tickCounter;

    public PillarManager(World world, Location bossBase, PillarConfig config, List<Location> bases,
            List<Location> chainLinks, List<Location> crateLocations, boolean debugManager) {
        this.world = world;
        this.bossBase = bossBase != null ? bossBase.clone() : null;
        this.bossLocation = this.bossBase != null ? this.bossBase.clone() : null;
        this.config = config;
        this.bases = bases != null ? new ArrayList<>(bases) : List.of();
        this.chainLinks = chainLinks != null ? new ArrayList<>(chainLinks) : List.of();
        this.crateLocations = crateLocations != null ? new ArrayList<>(crateLocations) : List.of();
        this.debugManager = debugManager;
    }

    public boolean start() {
        if (started) {
            return true;
        }
        if (bases.isEmpty() || world == null) {
            return false;
        }

        for (int i = 0; i < bases.size(); i++) {
            Location base = bases.get(i).clone();
            base.setWorld(world);
            Pillar pillar = new Pillar(this, world, i + 1, base, config);
            pillar.build();
            pillars.put(i + 1, pillar);
        }

        for (Location location : crateLocations) {
            ResourceCrateSpawner crate = new ResourceCrateSpawner(Material.QUARTZ, 5, 5,
                    new Vector3Int(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
            crate.spawnDisplay(world);
            crates.add(crate);
        }

        damageAction = new EventAction<>(this::onDamage, EntityDamageByEntityEvent.class,
                EventAction.HIGHEST_PRIORITY);
        genericDamageAction = new EventAction<>(this::onGenericDamage, EntityDamageEvent.class,
                EventAction.HIGHEST_PRIORITY);
        interactAction = new EventAction<>(this::onInteractBlock, PlayerInteractEvent.class,
                EventAction.HIGHEST_PRIORITY);
        interactEntityAction = new EventAction<>(this::onInteractEntity, PlayerInteractEntityEvent.class,
                EventAction.HIGHEST_PRIORITY);
        BukkitEventBus.getInstance().subscribe(damageAction);
        BukkitEventBus.getInstance().subscribe(genericDamageAction);
        BukkitEventBus.getInstance().subscribe(interactAction);
        BukkitEventBus.getInstance().subscribe(interactEntityAction);

        started = true;
        DebugPillarService.getInstance().register(this);
        return true;
    }

    public void tick() {
        if (!started) {
            return;
        }
        tickCounter++;
        for (Pillar pillar : new ArrayList<>(pillars.values())) {
            pillar.tick();
        }
        if (tickCounter % config.aggroIntervalTicks() == 0) {
            updateAggro();
        }
    }

    public double calculateBossHeightIncrease(double deltaSeconds, double baseRate, double maxRate,
            double fullHeight) {
        double[] fractions = new double[pillars.size()];
        double total = 0.0;
        double max = 0.0;
        int i = 0;
        for (Pillar pillar : pillars.values()) {
            total += pillar.getHp();
            max += pillar.getMaxHp();
            fractions[i++] = pillar.getHealthFraction();
        }
        double aggregate = max <= 0 ? 0.0 : PillarMath.aggregateFraction(total, max);
        double weakest = PillarMath.weakestFraction(fractions);
        return PillarMath.heightIncrease(aggregate, weakest, config.holdThreshold(), baseRate, maxRate,
                deltaSeconds, fullHeight);
    }

    public double getAggregateHealthFraction() {
        double total = 0.0;
        double max = 0.0;
        for (Pillar pillar : pillars.values()) {
            total += pillar.getHp();
            max += pillar.getMaxHp();
        }
        return max <= 0 ? 0.0 : PillarMath.aggregateFraction(total, max);
    }

    public double getWeakestHealthFraction() {
        double[] fractions = new double[pillars.size()];
        int i = 0;
        for (Pillar pillar : pillars.values()) {
            fractions[i++] = pillar.getHealthFraction();
        }
        return PillarMath.weakestFraction(fractions);
    }

    public boolean isHoldingBoss() {
        return !pillars.isEmpty() && getWeakestHealthFraction() >= config.holdThreshold();
    }

    public int getPillarCount() {
        return pillars.size();
    }

    public List<Integer> getPillarIndices() {
        return List.copyOf(pillars.keySet());
    }

    public List<String> describePillars() {
        List<String> lines = new ArrayList<>();
        for (Pillar pillar : pillars.values()) {
            lines.add(pillar.describe());
        }
        return lines;
    }

    public String describeStatus() {
        int percent = (int) Math.round(getAggregateHealthFraction() * 100.0);
        int weakest = (int) Math.round(getWeakestHealthFraction() * 100.0);
        return "§a[Pillars] §eAggregate " + percent + "% §7| §eWeakest " + weakest + "% §7| "
                + (isHoldingBoss() ? "§aHOLDING" : "§cRISING");
    }

    public boolean damage(int index, double amount) {
        Pillar pillar = pillars.get(index);
        return pillar != null && pillar.damage(amount);
    }

    public void damageAll(double amount) {
        for (Pillar pillar : pillars.values()) {
            pillar.damage(amount);
        }
    }

    public boolean setHp(int index, double hp) {
        Pillar pillar = pillars.get(index);
        if (pillar == null || !pillar.isAlive()) {
            return false;
        }
        pillar.setHp(hp);
        return true;
    }

    public void setAllHp(double hp) {
        for (Pillar pillar : pillars.values()) {
            pillar.setHp(hp);
        }
    }

    public void setAllHealthFraction(double fraction) {
        for (Pillar pillar : pillars.values()) {
            pillar.setHealthFraction(fraction);
        }
    }

    public void resetAll() {
        for (Pillar pillar : pillars.values()) {
            pillar.reset();
        }
    }

    public boolean revive(int index) {
        Pillar pillar = pillars.get(index);
        if (pillar == null) {
            return false;
        }
        pillar.revive();
        return true;
    }

    public void reviveAll() {
        for (Pillar pillar : pillars.values()) {
            pillar.revive();
        }
    }

    public void shutdown(boolean restoreTerrain) {
        if (!started && pillars.isEmpty()) {
            return;
        }
        started = false;
        unsubscribe();
        clearAggroTargets();
        for (Pillar pillar : pillars.values()) {
            pillar.shutdown(restoreTerrain);
        }
        pillars.clear();
        for (ResourceCrateSpawner crate : crates) {
            crate.destroy();
        }
        crates.clear();
        aggroAssignments.clear();
        DebugPillarService.getInstance().unregister(this);
    }

    public boolean isDebugManager() {
        return debugManager;
    }

    private void unsubscribe() {
        if (damageAction != null) {
            BukkitEventBus.getInstance().unsubscribe(damageAction);
            damageAction = null;
        }
        if (genericDamageAction != null) {
            BukkitEventBus.getInstance().unsubscribe(genericDamageAction);
            genericDamageAction = null;
        }
        if (interactAction != null) {
            BukkitEventBus.getInstance().unsubscribe(interactAction);
            interactAction = null;
        }
        if (interactEntityAction != null) {
            BukkitEventBus.getInstance().unsubscribe(interactEntityAction);
            interactEntityAction = null;
        }
    }

    private void onDamage(EntityDamageByEntityEvent event) {
        OptionalInt index = PillarResourceKeys.getPillarIndex(event.getEntity());
        if (index.isEmpty()) {
            return;
        }
        Pillar pillar = pillars.get(index.getAsInt());
        if (pillar == null || !pillar.isTarget(event.getEntity())) {
            return;
        }
        event.setCancelled(true);

        LivingEntity attacker = resolveAttacker(event.getDamager());
        if (attacker == null) {
            return;
        }
        if (attacker instanceof Player player) {
            player.sendActionBar(AdventureText.legacy("§eUse resources to repair this pillar."));
            return;
        }
        if (isPillarCore(attacker)
                || (!BukkitEntityMetadata.has(attacker, "BOSS_MINION")
                        && !BukkitEntityMetadata.has(attacker, "BOSS")
                        && !BukkitEntityMetadata.has(attacker, "DUNGEON"))) {
            return;
        }

        double damage = Math.max(event.getFinalDamage(), event.getDamage());
        if (damage <= 0.0) {
            return;
        }
        resetCore(pillar);
        Location hit = event.getEntity().getLocation();
        if (pillar.damage(damage * config.mobDamageScale())) {
            world.spawnParticle(Particle.BLOCK_CRUMBLE, hit, 5, 0.4, 0.4, 0.4, 0.02,
                    Material.QUARTZ_BLOCK.createBlockData());
            world.playSound(hit, Sound.BLOCK_STONE_HIT, 1.2f, 0.8f);
        }
    }

    private void onGenericDamage(EntityDamageEvent event) {
        if (findPillarEntity(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    private LivingEntity resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity shooter) {
            return shooter;
        }
        return damager instanceof LivingEntity living ? living : null;
    }

    private void resetCore(Pillar pillar) {
        if (pillar.getCore() != null && pillar.getCore().isValid()) {
            pillar.getCore().setNoDamageTicks(0);
            pillar.getCore().setLastDamage(0);
            pillar.getCore().setHealth(pillar.getCore().getMaxHealth());
        }
    }

    private void onInteractBlock(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        Pillar pillar = findPillarAt(event.getClickedBlock().getLocation());
        if (pillar == null) {
            return;
        }
        event.setCancelled(true);
        tryRepair(pillar, event.getPlayer());
    }

    private void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Pillar pillar = findPillarEntity(event.getRightClicked());
        if (pillar == null) {
            return;
        }
        event.setCancelled(true);
        tryRepair(pillar, event.getPlayer());
    }

    private void tryRepair(Pillar pillar, Player player) {
        if (pillar.getState() != Pillar.State.ALIVE) {
            player.sendActionBar(AdventureText.legacy("§cThis pillar cannot be repaired."));
            return;
        }
        if (pillar.getHp() >= pillar.getMaxHp()) {
            player.sendActionBar(AdventureText.legacy("§eThis pillar is already at full integrity."));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (!PillarResourceKeys.isRepairResource(item)) {
            player.sendActionBar(AdventureText.legacy("§eHold a crate resource to repair this pillar."));
            return;
        }

        if (pillar.repair(config.repairHpPerResource())) {
            if (player.getGameMode() != GameMode.CREATIVE) {
                int remaining = item.getAmount() - 1;
                if (remaining <= 0) {
                    player.getInventory().setItemInMainHand(null);
                } else {
                    item.setAmount(remaining);
                }
            }
            playRepairFeedback(pillar, player);
            player.sendActionBar(AdventureText.legacy("§aPillar " + pillar.getIndex() + " repaired §e+"
                    + format(config.repairHpPerResource()) + " §7("
                    + Math.round(pillar.getHealthFraction() * 100.0) + "%)"));
        }
    }

    private void playRepairFeedback(Pillar pillar, Player player) {
        double height = config.height();
        Location center = pillar.getBase().add(0.5, height / 2.0, 0.5);
        Location top = pillar.getBase().add(0.5, height + 0.5, 0.5);

        world.spawnParticle(Particle.HAPPY_VILLAGER, center, 45, 1.1, height / 2.0, 1.1, 0.03);
        world.spawnParticle(Particle.END_ROD, center, 35, 0.9, height / 2.0, 0.9, 0.04);
        world.spawnParticle(Particle.CRIT, center, 25, 1.0, height / 2.0, 1.0, 0.12);
        world.spawnParticle(Particle.HEART, top, 10, 0.7, 0.3, 0.7, 0.02);

        world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 1.6f);
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.1f, 1.8f);
        world.playSound(center, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
    }

    private Pillar findPillarAt(Location location) {
        for (Pillar pillar : pillars.values()) {
            if (pillar.containsBlock(location)) {
                return pillar;
            }
        }
        return null;
    }

    private Pillar findPillarEntity(Entity entity) {
        for (Pillar pillar : pillars.values()) {
            if (pillar.isTarget(entity)) {
                return pillar;
            }
        }
        return null;
    }

    private boolean isPillarCore(Entity entity) {
        return findPillarEntity(entity) != null;
    }

    private void updateAggro() {
        if (config.aggroPercent() <= 0.0) {
            return;
        }
        Set<UUID> present = new HashSet<>();
        for (Mob mob : world.getEntitiesByClass(Mob.class)) {
            if (!BukkitEntityMetadata.has(mob, "BOSS_MINION") || !mob.isValid() || mob.isDead()) {
                continue;
            }
            present.add(mob.getUniqueId());
            Integer assigned = aggroAssignments.get(mob.getUniqueId());
            if (assigned != null) {
                Pillar pillar = pillars.get(assigned);
                if (pillar == null || !pillar.isTargetValid()) {
                    aggroAssignments.remove(mob.getUniqueId());
                    if (isPillarCore(mob.getTarget())) {
                        mob.setTarget(null);
                    }
                } else {
                    mob.setTarget(pillar.getCore());
                }
                continue;
            }
            if (random.nextDouble() * 100.0 >= config.aggroPercent()) {
                continue;
            }
            Pillar nearest = nearestPillar(mob);
            if (nearest != null) {
                aggroAssignments.put(mob.getUniqueId(), nearest.getIndex());
                mob.setTarget(nearest.getCore());
            }
        }
        aggroAssignments.keySet().removeIf(uuid -> !present.contains(uuid));
    }

    private Pillar nearestPillar(Mob mob) {
        Pillar nearest = null;
        double best = Double.MAX_VALUE;
        Location location = mob.getLocation();
        for (Pillar pillar : pillars.values()) {
            if (!pillar.isTargetValid()) {
                continue;
            }
            double distance = location.distanceSquared(pillar.getBase());
            if (distance < best) {
                best = distance;
                nearest = pillar;
            }
        }
        return nearest;
    }

    private void clearAggroTargets() {
        for (UUID uuid : aggroAssignments.keySet()) {
            Entity entity = world == null ? null : world.getEntity(uuid);
            if (entity instanceof Mob mob && isPillarCore(mob.getTarget())) {
                mob.setTarget(null);
            }
        }
    }

    LivingEntity findNearestPillarCore(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return null;
        }
        Pillar nearest = null;
        double best = Double.MAX_VALUE;
        for (Pillar pillar : pillars.values()) {
            if (!pillar.isTargetValid()) {
                continue;
            }
            double distance = location.distanceSquared(pillar.getBase());
            if (distance < best) {
                best = distance;
                nearest = pillar;
            }
        }
        return nearest != null ? nearest.getCore() : null;
    }

    double distanceSquared(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return Double.MAX_VALUE;
        }
        double best = Double.MAX_VALUE;
        if (bossBase != null) {
            best = location.distanceSquared(bossBase);
        }
        for (Pillar pillar : pillars.values()) {
            best = Math.min(best, location.distanceSquared(pillar.getBase()));
        }
        return best;
    }

    Location getChainLink(int index) {
        int listIndex = index - 1;
        if (listIndex < 0 || listIndex >= chainLinks.size()) {
            return null;
        }
        Location location = chainLinks.get(listIndex);
        if (location == null) {
            return null;
        }
        Location clone = location.clone();
        clone.setWorld(world);
        return clone;
    }

    Location getBossBase() {
        return bossBase;
    }

    Location getBossLocation() {
        return bossLocation != null ? bossLocation.clone() : null;
    }

    public void updateBossLocation(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return;
        }
        bossLocation = location.clone();
        for (Pillar pillar : pillars.values()) {
            pillar.updateChainBossLocation(bossLocation);
        }
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
