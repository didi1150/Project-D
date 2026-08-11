package dev.bukkit.game.states;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.Vector;

import dev.bukkit.entity.BukkitPlayerEntity;
import dev.bukkit.game.coords.PointToLocation;
import dev.bukkit.game.dungeon.DungeonVoting;
import dev.bukkit.item.BukkitInventorySync;
import dev.bukkit.item.display.BukkitTextColorAdapter;
import dev.bukkit.utils.BukkitMessageSender;
import dev.core.entity.EntityManager;
import dev.core.entity.rpgclass.RPGClassType;
import dev.core.event.EventAction;
import dev.core.event.EventBusInterface;
import dev.core.game.GameState;
import dev.core.game.GameStateResult;
import dev.core.game.ScheduledTask;
import dev.core.game.coords.Point3D;
import dev.core.game.coords.ViewPoint3D;
import dev.core.utils.MessageComponent;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

public class SelectClassState extends GameState {

    private static final long DURATION = 20 * 60L;
    public static String NAME = "SELECTCLASS";
    private Map<RPGClassType, Location> locations;
    private final double BLOCK_DETECTION_RADIUS = 1.5;
    private final int REQUIRED_ROLES_SELECTED = 5;

    private static class RoleState {
        private final RPGClassType classType;
        private Player owner = null;
        private boolean locked = false;
        private Set<Player> playersOnBlock = new HashSet<>();

        public RoleState(RPGClassType rpgClassType) {
            this.classType = rpgClassType;
        }

        public RPGClassType getClassType() {
            return classType;
        }

        public Player getOwner() {
            return owner;
        }

        public boolean isLocked() {
            return locked;
        }

        public Set<Player> getPlayersOnBlock() {
            return new HashSet<>(playersOnBlock);
        }

        public void setOwner(Player owner) {
            this.owner = owner;
        }

        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        public void addPlayer(Player player) {
            playersOnBlock.add(player);
        }

        public void removePlayer(Player player) {
            playersOnBlock.remove(player);
        }
    }

    private ScheduledTask updateTask;
    private ScheduledTask particleTask;
    private final Map<String, RoleState> roleStates = new HashMap<>(); // role name -> state
    private final Map<UUID, String> playerSelections = new HashMap<>(); // player -> role name
    private Location spawnLocation;

    private static final List<Point3D> CACHED_CRUMBLE_OFFSETS = new ArrayList<>();
    private static final int CRUMBLE_RADIUS = 9;
    private Point3D holeCenter;

    static {
        for (int x = -CRUMBLE_RADIUS; x <= CRUMBLE_RADIUS; x++) {
            for (int z = -CRUMBLE_RADIUS; z <= CRUMBLE_RADIUS; z++) {
                double distance = Math.sqrt(x * x + z * z);
                if (distance <= CRUMBLE_RADIUS) {
                    CACHED_CRUMBLE_OFFSETS.add(new Point3D(x, 0, z));
                }
            }
        }
    }

    private DungeonVoting dungeonVoting;

    public SelectClassState(Point3D holeCenter, ViewPoint3D spawnLocation, Map<RPGClassType, Point3D> locations,
            EventBusInterface eventBus) {
        super(NAME, DURATION, eventBus);
        this.holeCenter = holeCenter;
        this.locations = new ConcurrentHashMap<>();
        this.spawnLocation = PointToLocation.viewToLoc(spawnLocation);
        locations.entrySet().forEach(entry -> {
            this.locations.put(entry.getKey(), PointToLocation.blockToLoc(entry.getValue()));
        });
        // Initialize role states
        for (RPGClassType classType : locations.keySet()) {
            roleStates.put(classType.toString(), new RoleState(classType));
        }
        this.dungeonVoting = new DungeonVoting();
    }

    @Override
    protected void onStart() {
        fillHole();
        placeLocationBlocks();

        particleTask = scheduler.runTaskTimer(() -> {
            double time = System.currentTimeMillis() / 1000.0; // seconds
            double baseHeight = 2.5; // base height above the block
            double bobHeight = 1.25; // bobbing amplitude
            double bobSpeed = 2.0; // bobbing speed
            double radius = 0.6; // radius of the ring
            int particleCount = 16; // particles per ring

            for (Map.Entry<RPGClassType, Location> entry : locations.entrySet()) {
                if (roleStates.get(entry.getKey().toString()).isLocked()) {
                    continue;
                }
                RPGClassType type = entry.getKey();
                Location base = entry.getValue().clone().add(0.5, baseHeight, 0.5);
                DustOptions dust = getDustOptionsForClass(type);

                double yOffset = Math.sin(time * bobSpeed) * bobHeight;

                for (int i = 0; i < particleCount; i++) {
                    double angle = 2 * Math.PI * i / particleCount;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = base.clone().add(x, yOffset, z);

                    particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 1, 0, 0, 0, dust);
                }
            }
        }, 0L, 2L); // every 2 ticks (~0.1s)

        dungeonVoting.startVoting(scheduler);
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.teleport(spawnLocation);
            BukkitMessageSender.getInstance().sendLine(player, ChatColor.YELLOW.toString());
            BukkitMessageSender.getInstance().sendCenteredMessage(player,
                    MessageComponent.of("<yellow>=== CLASS & FLOOR SELECTION ===</yellow>"));
            BukkitMessageSender.getInstance().sendCenteredMessage(player,
                    MessageComponent.of("<gray>Stand on a colored block to select a classType!</gray>"));
            BukkitMessageSender.getInstance().sendCenteredMessage(player,
                    MessageComponent.of("<gray>Sneak to lock your choice!</gray>"));
        });
        updateTask = scheduler.runTaskTimer(() -> {
            Bukkit.getOnlinePlayers().forEach(player -> {
                updatePlayerUI(player);
            });
        }, 0, 1);
    }

    private void placeLocationBlocks() {
        World world = spawnLocation.getWorld();
        locations.entrySet().forEach(entry -> {
            Material material = Material.BEDROCK;
            switch (entry.getKey()) {
            case ARCHER:
                material = Material.RED_WOOL;
                world.getBlockAt(entry.getValue()).setType(material);
                break;
            case ASSASSIN:
                material = Material.GRAY_WOOL;
                world.getBlockAt(entry.getValue()).setType(material);
                break;
            case MAGE:
                material = Material.CYAN_WOOL;
                world.getBlockAt(entry.getValue()).setType(material);
                break;
            case NONE:
                break;
            case SUPPORT:
                material = Material.GREEN_WOOL;
                world.getBlockAt(entry.getValue()).setType(material);
                break;
            case TANK:
                material = Material.LIGHT_GRAY_WOOL;
                world.getBlockAt(entry.getValue()).setType(material);
                break;
            }
        });
    }

    private void fillHole() {
        World world = spawnLocation.getWorld();
        for (Point3D offset : CACHED_CRUMBLE_OFFSETS) {
            Block block = world.getBlockAt(holeCenter.getX() + offset.getX(), holeCenter.getY() + offset.getY(),
                    holeCenter.getZ() + offset.getZ());
            block.setType(Material.BLACK_WOOL);
        }

    }

    @Override
    protected void onStop() {
        checkAssignments();
        displayFinalSelections();

        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            BukkitPlayerEntity playerEntity = new BukkitPlayerEntity(player);
            EntityManager.getInstance().registerEntity(playerEntity);
            // Update reference
            playerEntity = (BukkitPlayerEntity) EntityManager.getInstance().getEntity(playerEntity.getUuid()).get();
            playerEntity.syncState();
            BukkitInventorySync.syncInventory(playerEntity, player);
//                for (RPGClassType rpgClassType : RPGClassType.values()) {
//                    PlayerClassProgression progression = playerEntity.getPlayerProgression()
//                            .getProgression(rpgClassType);
//                    PlayerClassProgression cachedProgression = classProgressionService
//                            .getProgression(player.getUniqueId(), rpgClassType);
//                    progression.setLevel(cachedProgression.getLevel());
//                    progression.setUsableItems(cachedProgression.getUsableItems());
//                    progression.setXp(cachedProgression.getXp());
//                }
            player.setLevel(0);
            player.setExp(0);
            String selection = playerSelections.get(player.getUniqueId());
            RPGClassType assignedClass = (selection != null && roleStates.containsKey(selection))
                    ? roleStates.get(selection).classType
                    : RPGClassType.NONE; // defensive: no finalized selection -> keep base stats
            playerEntity.getPlayerProgression().setActiveClass(assignedClass, playerEntity.getStatManager());
        }
        cancelUpdateTask();
    }

    private void checkAssignments() {
        Set<UUID> assigned = new HashSet<>();
        // Mark already locked players as assigned
        for (RoleState roleState : roleStates.values()) {
            if (roleState.isLocked() && roleState.getOwner() != null) {
                assigned.add(roleState.getOwner().getUniqueId());
            }
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (assigned.contains(player.getUniqueId())) {
                continue; // already locked
            }

            // Does this player have a selection?
            String selectedRole = playerSelections.get(player.getUniqueId());
            RoleState chosenRole = null;

            if (selectedRole != null) {
                RoleState state = roleStates.get(selectedRole);
                if (state != null && !state.isLocked()) {
                    chosenRole = state;
                }
            }

            // If no valid role chosen, assign the first available
            if (chosenRole == null) {
                chosenRole = roleStates.values().stream().filter(role -> !role.isLocked()).findAny().orElse(null);
            }

            // If still null (all roles locked), just give them any role (random)
            if (chosenRole == null) {
                List<RoleState> allRoles = new ArrayList<>(roleStates.values());
                chosenRole = allRoles.get((int) (Math.random() * allRoles.size()));
            }

            // Lock role for player
            if (chosenRole != null) {
                lockRole(player, chosenRole);
                player.sendMessage(ChatColor.YELLOW + "You did not pick a class in time. "
                        + "You have been assigned to: " + BukkitTextColorAdapter
                                .colored(chosenRole.getClassType().getColor(), chosenRole.getClassType().toString()));
            }
        }
    }

    @Override
    protected void onTickSecond(long secondsRemaining) {
        updateCountdownXP(secondsRemaining, DURATION / 20);
        if (secondsRemaining < 10) {
            dungeonVoting.stopVoting();
        }
    }

    @Override
    protected void registerSubscribers() {
        EventAction<PlayerMoveEvent> moveAction = new EventAction<>(this::handleMovement, PlayerMoveEvent.class);
        EventAction<PlayerQuitEvent> quitAction = new EventAction<>(this::handlePlayerQuit, PlayerQuitEvent.class);
        EventAction<PlayerToggleSneakEvent> lockAction = new EventAction<>(this::handleLock,
                PlayerToggleSneakEvent.class);
        EventAction<PlayerJoinEvent> joinAction = new EventAction<>(this::handleJoin, PlayerJoinEvent.class);

        EventAction<EntityDamageEvent> damageAction = new EventAction<EntityDamageEvent>(this::handleDamage,
                EntityDamageEvent.class);
        EventAction<BlockBreakEvent> blockBreakAction = new EventAction<BlockBreakEvent>(this::handleBlockBreak,
                BlockBreakEvent.class);
        EventAction<BlockPlaceEvent> blockPlaceAction = new EventAction<BlockPlaceEvent>(this::handleBlockPlace,
                BlockPlaceEvent.class);

        addSubscriber(blockPlaceAction);
        addSubscriber(blockBreakAction);
        addSubscriber(damageAction);

        addSubscriber(quitAction);
        addSubscriber(lockAction);
        addSubscriber(moveAction);
        addSubscriber(joinAction);
    }

    private void handleDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    private void handleBlockBreak(BlockBreakEvent event) {
        event.setCancelled(true);
    }

    private void handleBlockPlace(BlockPlaceEvent event) {
        event.setCancelled(true);
    }

    /**
     * Helper method to update all online players' XP to reflect countdown progress
     * 
     * @param secondsRemaining Current seconds remaining
     * @param totalSeconds     Total duration in seconds
     */
    protected final void updateCountdownXP(long secondsRemaining, long totalSeconds) {
        if (totalSeconds <= 0)
            return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // Level shows seconds remaining
            player.setLevel((int) secondsRemaining);

            // XP bar shows progress (remaining / total)
            float progress = (float) secondsRemaining / (float) totalSeconds;
            progress = Math.max(0.0f, Math.min(1.0f, progress));
            player.setExp(progress);
        }
    }

    private void handleJoin(PlayerJoinEvent event) {
        scheduler.runTaskLater(() -> {
            event.setJoinMessage(null);
            event.getPlayer().kickPlayer("Game is already running!");
        }, 1);
    }

    private void handleMovement(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();

        // Check if player is frozen (locked to a role)
        if (isPlayerFrozen(player)) {
            // Allow rotation but not movement
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                event.setCancelled(true);
                player.sendMessage("§cYou are locked to your role! You cannot move.");
                return;
            }
        }

        // Check if player is trying to enter a locked role block
        if (isPlayerEnteringLockedRole(player, from, to)) {
            event.setCancelled(true);
            player.sendMessage("§cThis role is locked! You cannot enter this area.");
            return;
        }

        // Update player's role selection based on position
        updatePlayerRoleSelection(player);
    }

    private void handleLock(PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();

        if (!event.isSneaking())
            return; // Only handle start sneaking

        String currentRole = playerSelections.get(player.getUniqueId());
        if (currentRole == null) {
            player.sendMessage("§cYou must be standing on a role block to lock your selection!");
            return;
        }

        RoleState roleState = roleStates.get(currentRole);
        if (roleState.isLocked()) {
            player.sendMessage("§cThis role is already locked!");
            return;
        }

        // Lock the role
        lockRole(player, roleState);
    }

    private void handlePlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Remove player from all role states
        for (RoleState roleState : roleStates.values()) {
            roleState.removePlayer(player);

            // If this player owned and locked a role, unlock it
            if (roleState.getOwner() == player && roleState.isLocked()) {
                unlockRole(roleState);
                Bukkit.broadcastMessage("§e"
                        + player.getName() + " left! Role " + BukkitTextColorAdapter
                                .colored(roleState.getClassType().getColor(), roleState.getClassType().toString())
                        + " is now available again.");
            }
        }

        playerSelections.remove(playerId);

        // Check if we still have enough players
        checkCompletionConditions();
    }

    private void updatePlayerRoleSelection(Player player) {
        if (isPlayerFrozen(player)) {
            return;
        }

        Location playerLoc = player.getLocation();
        String newRole = null;

        // Check which role block the player is closest to
        for (RoleState roleState : roleStates.values()) {
            Location roleLoc = locations.get(roleState.getClassType());

            if (isPlayerOnRoleBlock(playerLoc, roleLoc)) {
                // Skip locked roles that belong to others (they should be blocked by movement
                // handler)
                if (roleState.isLocked() && roleState.getOwner() != player) {
                    continue;
                }

                newRole = roleState.getClassType().toString();
                break;
            }
        }

        // Update player's selection
        String oldRole = playerSelections.get(player.getUniqueId());

        if (!Objects.equals(oldRole, newRole)) {
            // Remove from old role
            if (oldRole != null) {
                RoleState oldRoleState = roleStates.get(oldRole);
                oldRoleState.removePlayer(player);
            }

            // Add to new role
            if (newRole != null) {
                RoleState newRoleState = roleStates.get(newRole);
                newRoleState.addPlayer(player);
                playerSelections.put(player.getUniqueId(), newRole);

                player.sendMessage("§a» Selected: " + BukkitTextColorAdapter
                        .colored(newRoleState.getClassType().getColor(), newRoleState.getClassType().toString()));
                player.sendMessage("§eSneak to lock this role!");
            } else {
                playerSelections.remove(player.getUniqueId());
            }
        }
    }

    private boolean isPlayerOnRoleBlock(Location playerLoc, Location blockLoc) {
        return playerLoc.distance(blockLoc) <= BLOCK_DETECTION_RADIUS
                && Math.abs(playerLoc.getY() - blockLoc.getY()) <= 2.0; // Allow some Y tolerance
    }

    private boolean isPlayerEnteringLockedRole(Player player, Location from, Location to) {
        // Check if player is trying to move into a locked role area
        for (RoleState roleState : roleStates.values()) {
            if (!roleState.isLocked() || roleState.getOwner() == player) {
                continue; // Skip unlocked roles or roles owned by this player
            }

            Location roleLoc = locations.get(roleState.getClassType());

            // Check if player is moving FROM outside TO inside a locked role area
            boolean wasInside = isPlayerOnRoleBlock(from, roleLoc);
            boolean willBeInside = isPlayerOnRoleBlock(to, roleLoc);

            if (!wasInside && willBeInside) {
                return true; // Player is trying to enter a locked area
            }
        }

        return false;
    }

    private void kickOffPlayer(Player player, Location blockLoc) {
        Location playerLoc = player.getLocation();
        Vector direction = playerLoc.toVector().subtract(blockLoc.toVector());

        // If player is directly on the block, choose a random direction
        if (direction.length() < 0.1) {
            direction = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
        }

        direction = direction.normalize();
        direction.multiply(4.0); // Stronger knockback than push
        direction.setY(0.3); // Slight upward boost

        final Vector finalDirection = direction;

        Location newLoc = blockLoc.clone().add(finalDirection);
        newLoc.setY(blockLoc.getY() + 1); // Ensure they're above ground

        // Teleport first, then add velocity for effect
        player.teleport(newLoc);

        // Add velocity with a slight delay to ensure teleport completes
        scheduler.runTaskLater(() -> {
            if (player.isOnline()) {
                player.setVelocity(finalDirection.multiply(0.3));
            }
        }, 1L);
    }

    private void lockRole(Player player, RoleState roleState) {
        roleState.setLocked(true);
        roleState.setOwner(player);

        playerSelections.put(player.getUniqueId(), roleState.getClassType().toString());

        Location location = locations.get(roleState.getClassType());
        player.teleport(location.add(0.5, 1, 0.5));

        // First: Knock off all other players from this block
        Set<Player> playersToRemove = new HashSet<>();
        for (Player otherPlayer : roleState.getPlayersOnBlock()) {
            if (otherPlayer != player) {
                kickOffPlayer(otherPlayer, location);
                otherPlayer.sendMessage("§c"
                        + player.getName() + " locked " + BukkitTextColorAdapter
                                .colored(roleState.getClassType().getColor(), roleState.getClassType().toString())
                        + "§c! You were removed from the area.");
                playersToRemove.add(otherPlayer);
            }
        }

        // Remove kicked players from role state
        for (Player kickedPlayer : playersToRemove) {
            roleState.removePlayer(kickedPlayer);
            playerSelections.remove(kickedPlayer.getUniqueId());
        }

        // Announce lock
        Bukkit.broadcastMessage("§a" + player.getName() + " locked role: "
                + BukkitTextColorAdapter.colored(roleState.getClassType().getColor(),
                        roleState.getClassType().toString())
                + " §7(" + getLockedRoleCount() + "/" + REQUIRED_ROLES_SELECTED + ")");

        player.sendMessage("§a§l✓ Role locked! You cannot move until the selection phase ends.");
        player.sendMessage("§7Other players can no longer enter this area.");

        checkCompletionConditions();
    }

    private void unlockRole(RoleState roleState) {
        roleState.setLocked(false);
        roleState.setOwner(null);
    }

    private boolean isPlayerFrozen(Player player) {
        String roleName = playerSelections.get(player.getUniqueId());
        if (roleName == null) {
            return false;
        }

        RoleState roleState = roleStates.get(roleName);
        return roleState != null && roleState.isLocked() && roleState.getOwner() == player;
    }

    private void updatePlayerUI(Player player) {
        // Update action bar with current selection
        String currentRole = playerSelections.get(player.getUniqueId());

        if (currentRole != null) {
            RoleState roleState = roleStates.get(currentRole);
            String status = roleState.isLocked() && roleState.getOwner() == player ? "§a§lLOCKED" : "§eSneak to lock";
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent(BukkitTextColorAdapter.colored(roleState.getClassType().getColor(),
                            roleState.getClassType().toString()) + " §7| " + status));
        } else {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                    new TextComponent("§7Stand on a colored block to select a role"));
        }

        // Update experience bar to show progress
//        int lockedRoles = getLockedRoleCount();
//        player.setLevel(lockedRoles);
//        player.setExp((float) lockedRoles / (float) REQUIRED_ROLES_SELECTED);
    }

    private void checkCompletionConditions() {
        int lockedRoles = getLockedRoleCount();

        if (lockedRoles >= REQUIRED_ROLES_SELECTED) {
            complete(GameStateResult.COMPLETE);
        }
    }

    private int getLockedRoleCount() {
        return (int) roleStates.values().stream().filter(RoleState::isLocked).count();
    }

    private void displayFinalSelections() {
        Bukkit.broadcastMessage("§a§l--- Final Role Assignments ---");
        for (RoleState roleState : roleStates.values()) {
            if (roleState.isLocked()) {
                Bukkit.broadcastMessage(BukkitTextColorAdapter.colored(roleState.getClassType().getColor(),
                        roleState.getClassType().toString()) + " §7→ §f" + roleState.getOwner().getName());
            }
        }
        Bukkit.broadcastMessage("§a§l-------------------------------");
    }

    private void cancelUpdateTask() {
        if (updateTask != null) {
            updateTask.cancel();
            updateTask = null;
        }
    }

    private DustOptions getDustOptionsForClass(RPGClassType type) {
        switch (type) {
        case TANK:
            return new DustOptions(Color.fromRGB(128, 128, 128), 1f); // gray
        case ASSASSIN:
            return new DustOptions(Color.fromRGB(64, 64, 64), 1f); // dark gray
        case ARCHER:
            return new DustOptions(Color.fromRGB(255, 0, 0), 1f); // red
        case MAGE:
            return new DustOptions(Color.fromRGB(0, 0, 255), 1f); // blue
        case SUPPORT:
            return new DustOptions(Color.fromRGB(0, 255, 0), 1f); // green
        default:
            return new DustOptions(Color.WHITE, 1f); // fallback
        }
    }

}
