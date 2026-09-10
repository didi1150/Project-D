package dev.bukkit.game.dungeon;

import dev.bukkit.game.states.ClearState;
import dev.bukkit.item.BukkitItemStackAdapter;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.event.impl.RPGEntityDeathEvent;
import dev.core.event.impl.TickEvent;
import dev.core.game.GameStateController;
import dev.core.game.GameStateResult;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.item.RPGItem;
import dev.core.item.loader.RPGItemRegistry;
import dev.core.utils.MessageComponent;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.EndPortalFrame;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.*;

public class DungeonPortalManager {

    private static final DungeonPortalManager INSTANCE = new DungeonPortalManager();
    public static DungeonPortalManager getInstance() {
        return INSTANCE;
    }

    private GameStateController gameStateController;

    private Vector3Int portalRoomCenter;
    private List<Location> portalFrameLocations;

    private AnimationState state = AnimationState.IDLE;
    private float ticks;
    private float beaconRotationAngle;
    private World world;
    private Queue<Location> beaconLocationsLeft;
    private List<BlockDisplay[]> beaconBeamDisplays;
    private Pair<BlockDisplay, Transformation>[] lastSummonedBeaconBeamDisplay;
    private List<ItemDisplay> eyeDisplays;
    private Item droppedEyeItem;

    private DungeonPortalManager() {
        portalFrameLocations = new ArrayList<>();
        beaconBeamDisplays = new ArrayList<>();
    }

    public void setPortalRoomCenter(Vector3Int portalRoomCenter) {
        this.portalRoomCenter = portalRoomCenter;
        portalFrameLocations.clear();
    }

    public void setGameStateController(GameStateController gameStateController) {
        this.gameStateController = gameStateController;
    }

    public void registerEvents(EventBusInterface eventBus) {
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            ItemStack item = event.getItem();
            World world = player.getWorld();
            if (isInClearState()) {
                if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName() && item.getItemMeta().getDisplayName().contains("Eye of Clarity")) {
                    if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock().getType() == Material.END_PORTAL_FRAME) {
                        event.setCancelled(true);
                        Block block = event.getClickedBlock();
                        EndPortalFrame endPortalFrame = ((EndPortalFrame) block.getBlockData());
                        if (endPortalFrame.hasEye()) {
                            player.sendMessage(ChatColor.RED + "Eye already placed!");
                        } else {
                            player.sendMessage(ChatColor.GREEN + "You placed an " + item.getItemMeta().getDisplayName());
                            endPortalFrame.setEye(true);
                            block.setBlockData(endPortalFrame);
                            item.setAmount(item.getAmount() - 1);
                            portalFrameLocations.add(block.getLocation());
                            world.playSound(block.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 1);
//                            Location loc = block.getLocation().clone();
//                            world.getBlockAt(loc.subtract(0,1,0)).setType(Material.LIGHT_BLUE_STAINED_GLASS);
//                            world.getBlockAt(loc.subtract(0,1,0)).setType(Material.BEACON);
//                            loc.subtract(0,1,0);
//                            for (int x = loc.getBlockX() - 1; x <= loc.getBlockX() + 1; x++) {
//                                for (int z = loc.getBlockZ() - 1; z <= loc.getBlockZ() + 1; z++) {
//                                    world.getBlockAt(x, loc.getBlockY(), z).setType(Material.IRON_BLOCK);
//                                }
//                            }

                            if (portalFrameLocations.size() == 8) {
                                startAnimation(player.getWorld());
                            }
                        }
                    }
                }
            }
        }, PlayerInteractEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            if (isInClearState()) {
                if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
                    if (portalRoomCenter != null && event.getPlayer().getLocation().toVector().toVector3f().distance(portalRoomCenter.toVector3f()) < 2) {
                        enterPortal(event.getPlayer());
                    }
                }
            }
        }, PlayerPortalEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            if (isInClearState()) {
                Entity victim = Bukkit.getEntity(event.getTarget().getUuid());
                if (victim != null && victim.getCustomName() != null && victim.getCustomName().contains("Skull King")) {
                    RPGItem rpgItem = RPGItemRegistry.getInstance().getItem("EYE_OF_CLARITY").get();
                    ItemStack item = BukkitItemStackAdapter.toItemStack(rpgItem, event.getKiller());
                    Location loc = victim.getLocation();
                    loc.getWorld().dropItem(loc, item,i -> {
                        i.setGlowing(true);
                        i.setUnlimitedLifetime(true);
                    });
                }
            }
        }, RPGEntityDeathEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            if (isInClearState()) {
                ticks += event.getTickDelta();
                switch (state) {
                    case IDLE -> {
                    }
                    case BEACONS_POWERING -> {
                        handleBeacons();
                    }
                    case EYES_MOVING -> {
                        handleEyes();
                    }
                    case EYE_DROPPED -> {
                        if (ticks >= 15) {
                            state = AnimationState.IDLE;
                            ticks = 0;
                            Location loc = new Location(world, portalRoomCenter.x, portalRoomCenter.y, portalRoomCenter.z);
                            world.strikeLightningEffect(loc);
                            droppedEyeItem.remove();
                            world.playSound(loc, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 10, 1);
                            world.playSound(loc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2, 1);
                            placePortal();
                            portalFrameLocations.clear();
                            for (BlockDisplay[] beam : beaconBeamDisplays) {
                                beam[0].remove();
                                beam[1].remove();
                            }
                        }
                    }
                }
                if (state != AnimationState.IDLE && !beaconBeamDisplays.isEmpty()) {
                    if (lastSummonedBeaconBeamDisplay != null && ticks >= 2) {
                        lastSummonedBeaconBeamDisplay[0].first.setTransformation(lastSummonedBeaconBeamDisplay[0].second);
                        lastSummonedBeaconBeamDisplay[1].first.setTransformation(lastSummonedBeaconBeamDisplay[1].second);
                        lastSummonedBeaconBeamDisplay = null;
                    }
//                    float angle = (float) Math.toRadians(allTicks * 2);
                    float angleMultiplier = switch (state) {
                        case IDLE -> 0.0F;
                        case BEACONS_POWERING -> 1.0f;
                        case EYES_MOVING -> ticks <= 25 ? lerp(1, 5, ticks / 25)
                                : lerp(5,15, (ticks - 25) / 100);
                        case EYE_DROPPED -> 15;
                    };
                    beaconRotationAngle += event.getTickDelta() * angleMultiplier;

                    for (BlockDisplay[] beam : beaconBeamDisplays) {
                        beam[0].setRotation(beaconRotationAngle * 2, 0);
                        beam[1].setRotation( -beaconRotationAngle, 0);
                    }
                }
            }
        }, TickEvent.class));
    }

    private static float lerp(float a, float b, float percent) {
        percent = Math.clamp(percent, 0, 1);
        return (1.0F - percent) * a + percent * b;
    }

    private boolean isInClearState() {
        return gameStateController.getCurrentState() instanceof ClearState;
    }

    private void startAnimation(World world) {
        this.world = world;
        state = AnimationState.BEACONS_POWERING;
        ticks = 0;
        beaconRotationAngle = 0;

        sortPortalFrameLocations();

        beaconLocationsLeft = new LinkedList<>(portalFrameLocations);
        beaconBeamDisplays.clear();
        lastSummonedBeaconBeamDisplay = null;
    }

    private void sortPortalFrameLocations() {
        List<Location> list = new ArrayList<>(portalFrameLocations);
        portalFrameLocations.clear();
        Location start = list.stream().max(Comparator.comparingDouble(Location::getBlockX)).get();
        portalFrameLocations.add(start);
        list.remove(start);
        while (!list.isEmpty()) {
            Location finalStart = start;
            var closestList = list.stream().sorted(Comparator.comparingDouble(loc -> loc.distance(finalStart))).toList();
            Location first = closestList.getFirst();
            if (closestList.size() > 1 && first.distance(start) == closestList.get(1).distance(start)) {
                Location second = closestList.get(1);
                if (second.getBlockZ() > first.getBlockZ()) {
                    first = second;
                }
            }
            portalFrameLocations.add(first);
            list.remove(first);
            start = first;
        }
    }

    private void handleBeacons() {
        if (beaconLocationsLeft.isEmpty() && ticks >= 20) {
            summonEyes();
            return;
        }

        if (ticks >= 20) {
            ticks = 0;
            Location loc = beaconLocationsLeft.poll().clone();
            for (int y = loc.add(0,1,0).getBlockY(); y < 256; y++) {
                Block block = world.getBlockAt(loc.getBlockX(), y,  loc.getBlockZ());
                if (block.getType().isSolid() && block.getType().isOccluding()) {
                    Vector3Int firstPos = new Vector3Int(loc.getBlockX(), loc.getBlockY() - 1, loc.getBlockZ());
                    spawnBeaconBeamDisplay(firstPos.sub(0,3,0), y - firstPos.y + 5, Material.SEA_LANTERN, Material.LIGHT_BLUE_STAINED_GLASS);
                    world.playSound(loc, Sound.BLOCK_BEACON_ACTIVATE, 5, 1);
                    break;
                }
//                if (!block.isEmpty()) {
//                    block.setType(Material.GRAY_STAINED_GLASS);
//                }
            }
        }
    }

    private void spawnBeaconBeamDisplay(Vector3Int firstPos, int y, Material beamMat, Material bloomMat) {
        Pair<BlockDisplay, Transformation> beam = spawnBeamDisplay(world, firstPos, firstPos.add(0,y,0), beamMat, -0.4f);
        Pair<BlockDisplay, Transformation> bloom = spawnBeamDisplay(world, firstPos, firstPos.add(0,y,0), bloomMat, -0.3f);
        beaconBeamDisplays.add(new BlockDisplay[]{beam.first, bloom.first});
        lastSummonedBeaconBeamDisplay = new Pair[]{beam, bloom};
    }

    private static Pair<BlockDisplay, Transformation> spawnBeamDisplay(World world, Vector3Int firstPos, Vector3Int secondPos, Material material, float offset) {
        Vector3f spawnPoint = firstPos.toVector3f().sub(new Vector3f(offset));
        Location loc = new Location(world, spawnPoint.x + 0.5 + offset, spawnPoint.y + 0.5 + offset , spawnPoint.z + 0.5 + offset);
        BlockDisplay blockDisplay = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
        blockDisplay.setBlock(Bukkit.createBlockData(material));
        blockDisplay.setBrightness(new Display.Brightness(15,15));
//        blockDisplay.setGlowing(true);
//        blockDisplay.setGlowColorOverride(Color.AQUA);

        blockDisplay.setInterpolationDuration(10);
        blockDisplay.setInterpolationDelay(0);

        Transformation transformation = blockDisplay.getTransformation();
        Vector3f scaleVec = new Vector3f(1);
        transformation.getScale().set(scaleVec.add(new Vector3f(offset * 2)));
        transformation.getTranslation().set(new Vector3f(-0.5F - offset, -0.5F - offset, -0.5F - offset));
        blockDisplay.setTransformation(transformation);

        scaleVec = secondPos.sub(firstPos).add(1,1,1).toVector3f();
        transformation.getScale().set(scaleVec.add(new Vector3f(offset * 2)));

        return new Pair<>(blockDisplay, transformation);
    }

    private void summonEyes() {
        state = AnimationState.EYES_MOVING;
        ticks = 0;
        eyeDisplays = new ArrayList<>();
        RPGItem rpgItem = RPGItemRegistry.getInstance().getItem("EYE_OF_CLARITY").get();
        ItemStack item = BukkitItemStackAdapter.toItemStack(rpgItem, null);
        for (Location portalFrameLocation : portalFrameLocations) {
            ItemDisplay display = (ItemDisplay) world.spawnEntity(portalFrameLocation.clone().add(0.5,0.8,0.5), EntityType.ITEM_DISPLAY);
            display.setItemStack(item);
            display.setBillboard(Display.Billboard.CENTER);
            display.setTeleportDuration(1);
            eyeDisplays.add(display);
            Block block = world.getBlockAt(portalFrameLocation);
            EndPortalFrame endPortalFrame = ((EndPortalFrame) block.getBlockData());
            endPortalFrame.setEye(false);
            block.setBlockData(endPortalFrame);
            world.playSound(portalFrameLocation, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.2f, 1);
        }
    }

    private void handleEyes() {
        float t = ticks;
        if (t <= 25) {
            for (ItemDisplay display : eyeDisplays) {
                Location loc = display.getLocation().add(0,0.1,0);
                display.teleport(loc);
            }
        } else {
            t -= 25;
            int ticksToComplete = 100;
            float angle = 4 * t * t / (ticksToComplete);
            float radiusPercentage = (ticksToComplete - t) / (ticksToComplete - 20);
            if (t <= 20) {
                radiusPercentage = 1;
            }
            int i = 0;
            for (ItemDisplay display : eyeDisplays) {
                double ang = Math.toRadians(angle + i * 45);
                double radius = radiusPercentage * portalFrameLocations.get(i).toVector().toVector3f().distance(portalRoomCenter.toVector3f());
                Location center = new Location(world, portalRoomCenter.getX() + 0.5, display.getLocation().getY(), portalRoomCenter.getZ() + 0.5);
                Vector dir = new Vector(Math.cos(ang) * radius, 0, Math.sin(ang) * radius);
                Location loc = center.clone().add(dir);
                display.teleport(loc);
                i++;
            }
            if (t >= ticksToComplete) {
                state = AnimationState.EYE_DROPPED;
                ticks = 0;
                for (ItemDisplay display : eyeDisplays) {
                    display.remove();
                }
                Location loc = new Location(world, portalRoomCenter.x + 0.5, portalRoomCenter.y + 4, portalRoomCenter.z + 0.5);
                RPGItem rpgItem = RPGItemRegistry.getInstance().getItem("EYE_OF_CLARITY").get();
                ItemStack item = BukkitItemStackAdapter.toItemStack(rpgItem, null);
                droppedEyeItem = world.dropItemNaturally(loc, item);
                world.playSound(loc, Sound.ENTITY_ENDER_EYE_DEATH, 1, 1);
            }
        }
    }

    private void placePortal() {
        if (portalRoomCenter == null) {
            BukkitMessageSender.getInstance().sendMessage(MessageComponent.of(ChatColor.RED + "portalRoomCenter not set -> can't open the portal to boss"));
            return;
        }
        for (int x = portalRoomCenter.x - 1; x <= portalRoomCenter.x + 1; x++) {
            for (int z = portalRoomCenter.z - 1; z <= portalRoomCenter.z + 1; z++) {
                Block block = world.getBlockAt(x, portalRoomCenter.y, z);
                block.setType(Material.END_PORTAL, false);
            }
        }
    }

    private void enterPortal(Player player) {
        System.out.println(player.getDisplayName() + " entered the boss portal");
        world.playSound(player, Sound.BLOCK_PORTAL_TRAVEL, 2, 1);
        gameStateController.getCurrentState().complete(GameStateResult.COMPLETE);
    }


    private enum AnimationState {
        IDLE, BEACONS_POWERING, EYES_MOVING, EYE_DROPPED
    }

    private record Pair<K, V>(K first, V second){}
}
