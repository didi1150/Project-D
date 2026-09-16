package dev.bukkit.utils.setup;

import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.bukkit.game.coords.LocToPoint;
import dev.bukkit.utils.BukkitMessageSender;
import dev.bukkit.utils.InterActionType;
import dev.bukkit.utils.ItemAbilityLore;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.settings.GameSettings;
import dev.core.utils.MessageComponent;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class BossSetupHelper extends SetupHelper {

    private static final String DISPLAY_NAME_CHAIN_TEST = "Chain Test Tool";

    private final ChainDisplay chainTestDisplay = new ChainDisplay("Chain Test", 16, Material.CHAIN, Color.WHITE);

    public BossSetupHelper() {
        super("GameSetupMode");
    }

    private ItemStack getChainTestTool() {
        return SetupUtils.createSimpleItem(Material.CHAIN, DISPLAY_NAME_CHAIN_TEST,
                List.of(new ItemAbilityLore(InterActionType.LEFT_CLICK_BLOCK, "Select 1. Position", ""),
                        new ItemAbilityLore(InterActionType.LEFT_CLICK_AIR, "Clear 1. Pos Selection", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_BLOCK, "Select 2. Position", ""),
                        new ItemAbilityLore(InterActionType.RIGHT_CLICK_AIR, "Clear 2. Pos Selection", ""),
                        new ItemAbilityLore(InterActionType.SNEAK_LEFT_CLICK, "Change the sag of the chain", "Current sag: " + 0f),
                        new ItemAbilityLore(InterActionType.SNEAK_RIGHT_CLICK, "Change the scale of the chain", "Current scale: " + 16f)
                        ));
    }

    @Override
    public void giveSetupItemsToPlayer(BukkitMessageSender ms, Player player) {
        List<ItemStack> items = new ArrayList<>(List.of(getChainTestTool()));
        for (ItemStack item : items) {
            player.getInventory().addItem(item);
        }
        ms.sendMessage(player, MessageComponent.of(ChatColor.GREEN + "You received: " + items.stream().map(i -> i.getItemMeta().getDisplayName()).collect(Collectors.joining(", "))));
    }

    @Override
    public void removeSetupItemsFromPlayer(Player player) {
        List<ItemStack> items = getAllSetupItems();
        for (ItemStack item : items) {
            player.getInventory().remove(item);
        }
    }

    private List<ItemStack> getAllSetupItems() {
        List<ItemStack> items = new ArrayList<>(List.of(getChainTestTool()));
        return items;
    }

    @Override
    public boolean removePlayerFromMode(Player player) {
        if (super.removePlayerFromMode(player)) {
            if (playersInMode.isEmpty()) {
                clearDisplays();
            }
            return true;
        }
        return false;
    }

    @Override
    public void cleanUp(Server server) {
        super.cleanUp(server);
        clearDisplays();
    }

    private void clearDisplays() {
        chainTestDisplay.clearDisplays(true, null);
    }

    @Override
    protected void cleanUp(Server server, UUID uuid, @Nullable Player player) {
        if (player != null)
            removePlayerFromMode(player);
    }

    @Override
    public void registerCommand(CommandManager cm, BukkitMessageSender ms) {
        cm.addSubCommand("project-d", SubCommandBuilder.startBuilding("bossSetup")
                        .setPlayerCommandAction(1, "toggleSetupMode", ((player, args) -> {
                            if (isPlayerInMode(player, ms, false)) {
                                removePlayerFromMode(player);
                            } else {
                                setPlayerInMode(ms, player);
                            }
                        }))
                        .setCommandArgumentsList(0, Arrays.asList("toggleSetupMode"))
        );
    }

    @Override
    public void registerEvents(EventBusInterface eventBus, BukkitMessageSender ms) {
        GameSettings gameSettings = GameSettings.getCurrentSettings();
        eventBus.subscribe(new EventAction<>(event -> {
            Player player = event.getPlayer();
            if (event.getHand() == EquipmentSlot.HAND && isPlayerInMode(player, ms, false)) {
                event.setCancelled(true);

                //any left click
                if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
                    //sneak left click
                    if (player.isSneaking()) {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            int oldSag = (int) (chainTestDisplay.sag * 100);
                            int newSag = oldSag;
                            if (oldSag < 10) {
                                newSag += 2;
                            } else if (oldSag < 20) {
                                newSag += 5;
                            } else if (oldSag < 100) {
                                newSag += 10;
                            } else {
                                newSag = 0;
                            }
                            SetupUtils.setValueInItemLore(event.getItem(), newSag + "%", 9);
                            ms.sendMessage(player, MessageComponent.of("<green>Set sag to <yellow>%s</yellow></green>", newSag + "%"));
                            chainTestDisplay.setSag(player.getWorld(), newSag / 100f);
                            return;
                        }
                    }

                }
                //any right click
                if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                    //sneak right click
                    if (player.isSneaking()) {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            float oldScale = chainTestDisplay.scale;
                            float newScale = oldScale;
                            if (oldScale < 6) {
                                newScale += 1;
                            } else if (oldScale < 16) {
                                newScale += 2;
                            } else if (oldScale < 40) {
                                newScale += 4;
                            } else {
                                newScale = 1;
                            }
                            SetupUtils.setValueInItemLore(event.getItem(), String.valueOf(newScale), 11);
                            ms.sendMessage(player, MessageComponent.of("<green>Set scale to <yellow>%s</yellow></green>", newScale));
                            chainTestDisplay.setScale(player.getWorld(), newScale);
                            return;
                        }
                    }
                }
                switch (event.getAction()) {
                    case LEFT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            ms.sendMessage(player, MessageComponent.of("<green>Set firstPos to: <yellow>%s</yellow></green>", pos));
                            chainTestDisplay.setFirstPos(player.getWorld(), pos);
                            return;
                        }
                    }
                    case RIGHT_CLICK_BLOCK -> {
                        Location loc = event.getClickedBlock().getLocation();
                        Point3D point3D = LocToPoint.locToBlock(loc);
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            Vector3Int pos = Vector3Int.fromPoint3D(point3D);
                            ms.sendMessage(player, MessageComponent.of("<green>Set secondPos to: <yellow>%s</yellow></green>", pos));
                            chainTestDisplay.setSecondPos(player.getWorld(), pos);
                            return;
                        }
                    }
                    case LEFT_CLICK_AIR -> {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            if (chainTestDisplay.firstPos != null) {
                                ms.sendMessage(player, MessageComponent.of("<yellow>Cleared firstPos</yellow>"));
                                chainTestDisplay.setFirstPos(player.getWorld(), null);
                            }
                            return;
                        }
                    }
                    case RIGHT_CLICK_AIR -> {
                        if (SetupUtils.hasItemInMainHand(player, DISPLAY_NAME_CHAIN_TEST)) {
                            if (chainTestDisplay.secondPos != null) {
                                ms.sendMessage(player, MessageComponent.of("<yellow>Cleared secondPos</yellow>"));
                                chainTestDisplay.setSecondPos(player.getWorld(), null);
                            }
                            return;
                        }
                    }
                }
                event.setCancelled(false);
            }
        }, PlayerInteractEvent.class));
        eventBus.subscribe(new EventAction<>(event -> {
            event.getPlayer().sendMessage("Right clicked: " + event.getRightClicked().getType());
        }, PlayerInteractEntityEvent.class));
    }

    private class ChainDisplay {
        private final String name;
        private final Material material;
        private final Color glowColor;

        private List<ChainPartDisplay> parts;
        private TextDisplay nameDisplay;
        private Vector3Int firstPos;
        private Vector3Int secondPos;
        private float scale;
        private float sag;

        public ChainDisplay(String name, float scale, Material material, Color glowColor) {
            this.name = name;
            this.scale = scale;
            this.material = material;
            this.glowColor = glowColor;
            parts = new ArrayList<>();
        }

        public void setFirstPos(World world, Vector3Int firstPos) {
            this.firstPos = firstPos;
            updateDisplays(world);
        }

        public void setSecondPos(World world, Vector3Int secondPos) {
            this.secondPos = secondPos;
            updateDisplays(world);
        }

        public void setScale(World world, float scale) {
            this.scale = scale;
            updateDisplays(world);
        }

        public void setSag(World world, float sag) {
            this.sag = sag;
            updateDisplays(world);
        }

        public void updateDisplays(World world) {
            if (firstPos == null || secondPos == null){
                clearDisplays(false, null);
                return;
            }
            if (parts.isEmpty()) {
                createChainParts(world, firstPos, secondPos, sag);
//                nameDisplay = SetupUtils.spawnTextDisplayInBlockCenter(world, pos, name);
            } else {
                clearDisplays(true, null);
                createChainParts(world, firstPos, secondPos ,sag);
//                recalculatePartPositions(world, firstPos, secondPos);
//                SetupUtils.repositionTextDisplay(world, pos, nameDisplay);
            }
        }

        private void createChainParts(World world, Vector3Int firstPos, Vector3Int secondPos, float sag) {
            Vector3Int dir = secondPos.sub(firstPos);
            Vector3f start = firstPos.toVector3f();
            Vector3f end = secondPos.toVector3f();
            float ropeLength = start.distance(end) * (1 + sag);
            int segmentCount = (int) (ropeLength / scale) + (ropeLength % scale == 0 ? 0 : 1);
            Vector3f[] points = generateRope(start, end, ropeLength, segmentCount);
            for (int i = 0; i < points.length - 1; i++) {
                Vector3f a = points[i];
                Vector3f b = points[i + 1];
                ChainPartDisplay part = new ChainPartDisplay(material, glowColor);
                parts.add(part);
                part.updateDisplays(world, a, b, scale);
//                System.out.println("created chain from: " + a + " to " + b);

//                Vector3f size = new Vector3f(b).sub(a).add(1, 1, 1);
//                Vector3f c = new Vector3f(a).add(new Vector3f(size).mul(0.5f));
//                Interaction interaction = (Interaction) world.spawnEntity(new Location(world, c.x, a.y, c.z), EntityType.INTERACTION);
//                interaction.setInteractionHeight(size.y);
//                interaction.setInteractionWidth(Math.max(size.x, size.z));
            }
            
//            System.out.println("dir: " + dir);
//            System.out.println("ropeLength: " + ropeLength);
//            System.out.println("segmentCount: " + segmentCount);
//            System.out.println("created " + parts.size() + " chains");
        }
        
        public static Vector3f[] generateRope(Vector3f start, Vector3f end, float ropeLength, int segmentCount) {

            float directDistance = start.distance(end);

            ropeLength = Math.max(ropeLength, directDistance);

            if (ropeLength <= directDistance + 0.0001f) {
                Vector3f[] points = new Vector3f[segmentCount + 1];
                for (int i = 0; i <= segmentCount; i++) {
                    float t = (float) i / segmentCount;

                    points[i] = new Vector3f(start).lerp(end, t);
                }
                return points;
            }

            Vector3f direction = new Vector3f(end).sub(start).normalize();

            Vector3f sagDirection = new Vector3f(0, -1, 0)
                    .sub(new Vector3f(direction).mul(direction.dot(0, -1, 0)));

            if (sagDirection.lengthSquared() < 0.000001f) {
                sagDirection.set(1, 0, 0);
            } else {
                sagDirection.normalize();
            }

            float controlSag = findSag(start, end, sagDirection, ropeLength);

            Vector3f midpoint = new Vector3f(start).add(end).mul(0.5f);

            Vector3f p1 = new Vector3f(midpoint).fma(controlSag, sagDirection);

            Function<Float, Vector3f> getBezierPoint = t -> {
                float u = 1.0f - t;
                return new Vector3f(start).mul(u * u).fma(2.0f * u * t, p1).fma(t * t, end);
            };

            int sampleResolution = 200;
            float[] sampleTs = new float[sampleResolution + 1];
            float[] accumulatedLengths = new float[sampleResolution + 1];
            sampleTs[0] = 0.0f;
            accumulatedLengths[0] = 0.0f;

            Vector3f previousPoint = new Vector3f(start);

            for (int i = 1; i <= sampleResolution; i++) {
                float t = (float) i / sampleResolution;
                Vector3f currentPoint = getBezierPoint.apply(t);
                accumulatedLengths[i] = accumulatedLengths[i - 1] + previousPoint.distance(currentPoint);
                sampleTs[i] = t;
                previousPoint.set(currentPoint);
            }
            float totalLength = accumulatedLengths[sampleResolution];

            Vector3f[] points = new Vector3f[segmentCount + 1];
            points[0] = new Vector3f(start);
            points[segmentCount] = new Vector3f(end);

            float segmentLength = totalLength / segmentCount;

            for (int i = 1; i < segmentCount; i++) {
                float targetDistance = i * segmentLength;

                int low = 0;
                int high = sampleResolution;
                while (low < high) {
                    int mid = (low + high) >>> 1;
                    if (accumulatedLengths[mid] < targetDistance) {
                        low = mid + 1;
                    } else {
                        high = mid;
                    }
                }

                int index = low;
                float lengthBefore = accumulatedLengths[index - 1];
                float lengthAfter = accumulatedLengths[index];
                float interpolation = (targetDistance - lengthBefore) / (lengthAfter - lengthBefore);
                float t = sampleTs[index - 1] + (sampleTs[index] - sampleTs[index - 1]) * interpolation;
                points[i] = getBezierPoint.apply(t);
            }

            return points;
        }


        private static float findSag(Vector3f p0, Vector3f p2, Vector3f sagDirection, float targetLength) {

            Vector3f midpoint = new Vector3f(p0).add(p2).mul(0.5f);

            Function<Float, Float> calculateLength = controlSag -> {
                Vector3f p1 = new Vector3f(midpoint).fma(controlSag, sagDirection);
                int samples = 100;
                float length = 0.0f;
                Vector3f previous = new Vector3f(p0);

                for (int i = 1; i <= samples; i++) {
                    float t = (float) i / samples;
                    float u = 1.0f - t;
                    Vector3f current = new Vector3f(p0).mul(u * u).fma(2.0f * u * t, p1).fma(t * t, p2);
                    length += previous.distance(current);
                    previous.set(current);
                }

                return length;
            };

            float low = 0.0f;
            float high = p0.distance(p2);

            while (calculateLength.apply(high) < targetLength) {
                high *= 2.0f;
            }

            for (int i = 0; i < 20; i++) {
                float mid = (low + high) * 0.5f;
                float length = calculateLength.apply(mid);
                if (length < targetLength) {
                    low = mid;
                } else {
                    high = mid;
                }
            }

            return (low + high) * 0.5f;
        }

        private void recalculatePartPositions(World world, Vector3Int firstPos, Vector3Int secondPos) {

        }

        public void clearDisplays(boolean force, Player ignoredPlayer) {
            if (!parts.isEmpty()) {
//                if (!force) {
//                    if (isItemBeingUsedByOtherPlayers(ignoredPlayer, itemName)) return;
//                }
                for (ChainPartDisplay part : parts) {
                    part.clearDisplays(false, ignoredPlayer);
                }
                parts.clear();
//                nameDisplay.remove();
//                nameDisplay = null;
            }
        }
    }

    private class ChainPartDisplay {
        private final Material material;
        private final Color glowColor;

        private BlockDisplay display;

        public ChainPartDisplay(Material material, Color glowColor) {
            this.material = material;
            this.glowColor = glowColor;
        }

        public void updateDisplays(World world, Vector3f firstPos, Vector3f secondPos, float scale) {
            if (display == null) {
                display = spawnChainDisplay(world, firstPos, secondPos, scale, material, glowColor);
            } else {
//                SetupUtils.repositionBlockDisplay(world, pos, display);
            }
        }

        public static BlockDisplay spawnChainDisplay(World world, Vector3f firstPos, Vector3f secondPos, float scale, Material material, Color glowColor) {
            Vector3f dir = new Vector3f(secondPos).sub(firstPos);
            scale = dir.length() + 0.1f;
            Vector3f offset = new Vector3f(scale / 2);
            Vector3f spawnPoint = new Vector3f(firstPos).add(new Vector3f(dir).mul(0.5f)).add(new Vector3f(0.5f));
            Location loc = new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z);
            BlockDisplay blockDisplay = (BlockDisplay) world.spawnEntity(loc, EntityType.BLOCK_DISPLAY);
            blockDisplay.setBlock(Bukkit.createBlockData(material));
            blockDisplay.setBrightness(new Display.Brightness(15,15));
//            blockDisplay.setGlowing(true);
            blockDisplay.setGlowColorOverride(glowColor);

            Transformation transformation = blockDisplay.getTransformation();
            transformation.getTranslation().set(new Vector3f(scale * 0.5f).mul(-1));
//            System.out.println("scale: " + scale + " -> " + dir.length() + " - " + dir);
            transformation.getScale().set(new Vector3f(scale));
            blockDisplay.setTransformation(transformation);
            float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pitch = (float) Math.toDegrees(Math.atan2(-dir.y, Math.sqrt(dir.x * dir.x + dir.z * dir.z)));
            if (dir.y < 0) {
                pitch -= 90;
            }
            else {
                pitch += 90;
            }
            blockDisplay.setRotation(yaw, pitch);
            return blockDisplay;
        }

        public void clearDisplays(boolean force, Player ignoredPlayer) {
            if (display != null) {
//                if (!force) {
//                    if (isItemBeingUsedByOtherPlayers(ignoredPlayer, itemName)) return;
//                }
                display.remove();
                display = null;
            }
        }
    }

}
