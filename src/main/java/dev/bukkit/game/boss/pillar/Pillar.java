package dev.bukkit.game.boss.pillar;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Slime;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Vector;

import dev.bukkit.entity.BukkitDisplayEntityRegistry;
import dev.bukkit.utils.bossfight.ChainDisplay;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.utils.AdventureText;

final class Pillar {

    enum State {
        ALIVE, BREAKING, DESTROYED
    }

    private static final String[] ROMAN = { "I", "II", "III", "IV", "V", "VI", "VII", "VIII" };

    private final PillarManager manager;
    private final World world;
    private final int index;
    private final Location base;
    private final PillarConfig config;
    private final List<PillarShape.Offset> offsets;
    private final Map<Vector3Int, BlockData> originals = new LinkedHashMap<>();
    private final List<Vector3Int> removalOrder = new ArrayList<>();
    private final List<FallingBlock> fallingBlocks = new ArrayList<>();

    private double hp;
    private State state = State.ALIVE;
    private int paletteBucket = -1;
    private int breakTicksRemaining;

    private Slime core;
    private Interaction hitbox;
    private TextDisplay healthbar;
    private ChainDisplay chain;
    private Vector3Int chainBossPos;
    private float chainSag = -1.0f;

    Pillar(PillarManager manager, World world, int index, Location base, PillarConfig config) {
        this.manager = manager;
        this.world = world;
        this.index = index;
        this.base = base.clone();
        this.base.setWorld(world);
        this.config = config;
        this.offsets = PillarShape.offsets(config.height());
        this.hp = config.maxHp();
    }

    void build() {
        buildBlocks(true);
        spawnHitbox();
        spawnCore();
        spawnHealthbar();
        spawnChain();
    }

    private void buildBlocks(boolean snapshot) {
        if (snapshot || originals.isEmpty()) {
            originals.clear();
            for (PillarShape.Offset offset : offsets) {
                Vector3Int pos = blockPos(offset);
                Block block = world.getBlockAt(pos.x, pos.y, pos.z);
                originals.put(pos, block.getBlockData());
            }
        }
        removalOrder.clear();
        removalOrder.addAll(originals.keySet());
        removalOrder.sort(Comparator.comparingInt((Vector3Int pos) -> pos.y).reversed());
        paletteBucket = -1;
        refreshPalette();
    }

    private void refreshPalette() {
        if (state != State.ALIVE) {
            return;
        }
        int bucket = PillarPalette.bucket(healthFraction());
        if (bucket == paletteBucket) {
            return;
        }
        paletteBucket = bucket;
        for (Vector3Int pos : originals.keySet()) {
            Material material = PillarPalette.materialFor(healthFraction(), pos.x, pos.y, pos.z, index + 31);
            world.getBlockAt(pos.x, pos.y, pos.z).setBlockData(material.createBlockData(), false);
        }
    }

    private void spawnHitbox() {
        Location center = new Location(world, base.getX() + 0.5, base.getY() + config.height() / 2.0,
                base.getZ() + 0.5);
        hitbox = world.spawn(center, Interaction.class, interaction -> {
            interaction.setInteractionWidth(3.0f);
            interaction.setInteractionHeight(config.height());
            interaction.setResponsive(true);
            interaction.setPersistent(false);
            PillarResourceKeys.tagPillar(interaction, index);
        });
    }

    private void spawnCore() {
        core = world.spawn(coreLocation(), Slime.class, slime -> {
            slime.setSize(1);
            slime.setAI(false);
            slime.setInvisible(true);
            slime.setSilent(true);
            slime.setGravity(false);
            slime.setCollidable(false);
            slime.setPersistent(false);
            slime.setRemoveWhenFarAway(false);
            slime.setInvulnerable(false);
            slime.customName(AdventureText.legacy("§cPillar " + roman(index)));
            slime.setCustomNameVisible(false);
            PillarResourceKeys.tagPillar(slime, index);
        });
        var maxHealth = core.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(1024.0);
        }
        core.setHealth(core.getMaxHealth());
    }

    private Location coreLocation() {
        double centerX = base.getX() + 0.5;
        double centerZ = base.getZ() + 0.5;
        double dx = 0.0;
        double dz = 0.0;
        Location bossBase = manager.getBossBase();
        if (bossBase != null && world.equals(bossBase.getWorld())) {
            dx = Math.signum(bossBase.getX() - centerX);
            dz = Math.signum(bossBase.getZ() - centerZ);
        }
        if (dx == 0.0 && dz == 0.0) {
            dx = 1.0;
        }
        Location location = new Location(world, centerX + dx * 2.2, base.getY(), centerZ + dz * 2.2);
        for (int i = 0; i < 8 && !world.getBlockAt(location).getType().isAir(); i++) {
            location.add(0, 1, 0);
        }
        return location;
    }

    private void spawnHealthbar() {
        Location location = new Location(world, base.getX() + 0.5, base.getY() + config.height() + 1.0,
                base.getZ() + 0.5);
        healthbar = BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(location, TextDisplay.class,
                display -> {
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setSeeThrough(false);
                    display.setBackgroundColor(Color.fromARGB(120, 0, 0, 0));
                    display.setPersistent(false);
                    display.setInvulnerable(true);
                    display.setGravity(false);
                    display.text(AdventureText.legacy(healthText()));
                });
    }

    private void spawnChain() {
        Location link = manager.getChainLink(index);
        if (link == null) {
            return;
        }
        Location bossLocation = manager.getBossLocation();
        Vector3Int bossPos = new Vector3Int(base.getBlockX(), base.getBlockY() + config.height(), base.getBlockZ());
        if (bossLocation != null && world.equals(bossLocation.getWorld())) {
            bossPos = new Vector3Int(bossLocation.getBlockX(), bossLocation.getBlockY(), bossLocation.getBlockZ());
        }
        chainBossPos = bossPos;
        float sag = (float) PillarMath.chainSag(healthFraction(), config.tautSag(), config.slackSag());
        chain = new ChainDisplay(bossPos,
                new Vector3Int(link.getBlockX(), link.getBlockY(), link.getBlockZ()), 1.0f, sag);
        chain.spawnDisplay(world);
        chainSag = sag;
    }

    void updateChainBossLocation(Location bossLocation) {
        if (chain == null || bossLocation == null || !world.equals(bossLocation.getWorld())) {
            return;
        }
        Vector3Int bossPos = new Vector3Int(bossLocation.getBlockX(), bossLocation.getBlockY(),
                bossLocation.getBlockZ());
        if (bossPos.equals(chainBossPos)) {
            return;
        }
        chainBossPos = bossPos;
        chain.setFirstPos(world, bossPos);
    }

    boolean damage(double amount) {
        if (state != State.ALIVE || amount <= 0.0) {
            return false;
        }
        hp = Math.max(0.0, hp - amount);
        if (hp <= 0.0) {
            startBreaking();
        } else {
            refreshVisuals();
        }
        return true;
    }

    boolean repair(double amount) {
        if (state != State.ALIVE || amount <= 0.0 || hp >= config.maxHp()) {
            return false;
        }
        hp = Math.min(config.maxHp(), hp + amount);
        refreshVisuals();
        return true;
    }

    void setHp(double value) {
        if (state != State.ALIVE) {
            return;
        }
        hp = Math.max(0.0, Math.min(config.maxHp(), value));
        if (hp <= 0.0) {
            startBreaking();
        } else {
            refreshVisuals();
        }
    }

    void setHealthFraction(double fraction) {
        setHp(config.maxHp() * PillarMath.clamp01(fraction));
    }

    void reset() {
        if (state == State.ALIVE) {
            hp = config.maxHp();
            refreshVisuals();
        } else {
            revive();
        }
    }

    void revive() {
        removeTargetEntities();
        removeHealthbar();
        removeChain();
        removeFallingBlocks();
        state = State.ALIVE;
        hp = config.maxHp();
        breakTicksRemaining = 0;
        buildBlocks(false);
        spawnHitbox();
        spawnCore();
        spawnHealthbar();
        spawnChain();
        world.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 2.0f, 0.8f);
    }

    void tick() {
        if (state == State.BREAKING) {
            tickBreaking();
        }
    }

    private void tickBreaking() {
        breakTicksRemaining--;
        if (breakTicksRemaining % 5 == 0) {
            Location center = base.clone().add(0.5, Math.max(0.5, config.height() / 4.0), 0.5);
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, center, 4, 1.2, 0.5, 1.2, 0.01);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, center, 8, 1.4, 0.6, 1.4, 0.02,
                    Material.QUARTZ_BLOCK.createBlockData());
            world.playSound(base, Sound.BLOCK_GRAVEL_BREAK, 1.2f, 0.6f);
        }
        if (breakTicksRemaining <= 0) {
            finishBreaking();
        }
    }

    private void startCollapse() {
        int baseTopY = base.getBlockY() + Math.max(1, config.height() / 4);
        for (Vector3Int pos : removalOrder) {
            if (pos.y < baseTopY) {
                continue;
            }
            Block block = world.getBlockAt(pos.x, pos.y, pos.z);
            BlockData data = block.getBlockData();
            if (data.getMaterial().isAir()) {
                continue;
            }
            block.setType(Material.AIR, false);
            spawnFallingBlock(pos, data);
            Location center = new Location(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
            world.spawnParticle(Particle.BLOCK_CRUMBLE, center, 2, 0.25, 0.25, 0.25, 0.01, data);
        }
        world.playSound(base, Sound.BLOCK_DEEPSLATE_BREAK, 3.0f, 0.5f);
        world.playSound(base, Sound.ENTITY_WITHER_BREAK_BLOCK, 1.0f, 0.6f);
    }

    private void spawnFallingBlock(Vector3Int pos, BlockData data) {
        Location location = new Location(world, pos.x + 0.5, pos.y + 0.1, pos.z + 0.5);
        Vector velocity = new Vector((base.getX() + 0.5 - location.getX()) * 0.02, -0.05,
                (base.getZ() + 0.5 - location.getZ()) * 0.02);
        FallingBlock falling = world.spawn(location, FallingBlock.class, block -> {
            block.setBlockData(data);
            block.setDropItem(false);
            block.setCancelDrop(true);
            block.setHurtEntities(false);
            block.setPersistent(false);
            block.setGravity(true);
        });
        falling.setVelocity(velocity);
        fallingBlocks.add(falling);
    }

    private void startBreaking() {
        state = State.BREAKING;
        hp = 0.0;
        breakTicksRemaining = Math.max(30, config.breakTicks());
        removeTargetEntities();
        removeChain();
        updateHealthbarText();
        startCollapse();
    }

    private void finishBreaking() {
        removeFallingBlocks();
        for (Vector3Int pos : removalOrder) {
            Block block = world.getBlockAt(pos.x, pos.y, pos.z);
            BlockData data = block.getBlockData();
            block.setType(Material.AIR, false);
            if (!data.getMaterial().isAir()) {
                Location center = new Location(world, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5);
                world.spawnParticle(Particle.BLOCK_CRUMBLE, center, 4, 0.35, 0.35, 0.35, 0.02, data);
            }
        }
        state = State.DESTROYED;
        removeHealthbar();
        world.playSound(base, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2.0f, 0.5f);
        world.spawnParticle(Particle.EXPLOSION, base.clone().add(0.5, 1.0, 0.5), 2, 1.5, 1.0, 1.5, 0);
    }

    private void removeFallingBlocks() {
        for (FallingBlock falling : fallingBlocks) {
            if (falling != null && falling.isValid()) {
                falling.remove();
            }
        }
        fallingBlocks.clear();
    }

    private void refreshVisuals() {
        refreshPalette();
        updateHealthbarText();
        updateChainSag();
    }

    private void updateHealthbarText() {

        if (healthbar != null && healthbar.isValid()) {
            if (state == State.DESTROYED) {
                healthbar.remove();
                return;
            }
            healthbar.text(AdventureText.legacy(healthText()));
        }

    }

    private void updateChainSag() {
        if (chain == null) {
            return;
        }
        float sag = (float) PillarMath.chainSag(healthFraction(), config.tautSag(), config.slackSag());
        if (Math.abs(chainSag - sag) < 0.01f) {
            return;
        }
        chainSag = sag;
        chain.setSag(world, sag);
    }

    private String healthText() {
        if (state == State.BREAKING) {
            return "§c§lCOLLAPSING";
        }
        int percent = (int) Math.round(healthFraction() * 100.0);
        String color = percent >= 66 ? "§a" : percent >= 33 ? "§e" : "§c";
        int filled = (int) Math.round(healthFraction() * 10.0);
        String bar = "■".repeat(filled) + "□".repeat(10 - filled);
        return "§fPillar " + roman(index) + " " + color + "[" + bar + "] §e" + percent + "%";
    }

    private String roman(int value) {
        return value >= 1 && value <= ROMAN.length ? ROMAN[value - 1] : String.valueOf(value);
    }

    void shutdown(boolean restoreTerrain) {
        removeTargetEntities();
        removeHealthbar();
        removeChain();
        removeFallingBlocks();
        for (Map.Entry<Vector3Int, BlockData> entry : originals.entrySet()) {
            Vector3Int pos = entry.getKey();
            if (restoreTerrain) {
                world.getBlockAt(pos.x, pos.y, pos.z).setBlockData(entry.getValue(), false);
            } else {
                world.getBlockAt(pos.x, pos.y, pos.z).setType(Material.AIR, false);
            }
        }
        state = State.DESTROYED;
    }

    private void removeTargetEntities() {
        if (core != null) {
            core.remove();
            core = null;
        }
        if (hitbox != null) {
            hitbox.remove();
            hitbox = null;
        }
    }

    private void removeHealthbar() {
        if (healthbar != null) {
            healthbar.remove();
            healthbar = null;
        }
    }

    private void removeChain() {
        if (chain != null) {
            chain.clearDisplays(true, null);
            chain = null;
            chainBossPos = null;
            chainSag = -1.0f;
        }
    }

    private Vector3Int blockPos(PillarShape.Offset offset) {
        return new Vector3Int(base.getBlockX() + offset.x(), base.getBlockY() + offset.y(),
                base.getBlockZ() + offset.z());
    }

    boolean containsBlock(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().equals(world)) {
            return false;
        }
        if (state == State.DESTROYED) {
            return false;
        }
        return originals.containsKey(new Vector3Int(location.getBlockX(), location.getBlockY(), location.getBlockZ()));
    }

    boolean isTarget(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (core != null && core.getUniqueId().equals(entity.getUniqueId())) {
            return true;
        }
        return hitbox != null && hitbox.getUniqueId().equals(entity.getUniqueId());
    }

    double getHealthFraction() {
        return healthFraction();
    }

    private double healthFraction() {
        return PillarMath.aggregateFraction(hp, config.maxHp());
    }

    double getHp() {
        return hp;
    }

    double getMaxHp() {
        return config.maxHp();
    }

    int getIndex() {
        return index;
    }

    Location getBase() {
        return base.clone();
    }

    State getState() {
        return state;
    }

    boolean isAlive() {
        return state == State.ALIVE;
    }

    Slime getCore() {
        return core;
    }

    boolean isTargetValid() {
        return state == State.ALIVE && core != null && core.isValid();
    }

    String describe() {
        int percent = (int) Math.round(getHealthFraction() * 100.0);
        String stateText = switch (state) {
        case ALIVE -> "§aALIVE";
        case BREAKING -> "§eBREAKING";
        case DESTROYED -> "§cDESTROYED";
        };
        return "§ePillar " + index + " §7— §e" + format(getHp()) + "§7/§e" + format(getMaxHp()) + " §7(" + percent
                + "%) " + stateText;
    }

    private static String format(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }
}
