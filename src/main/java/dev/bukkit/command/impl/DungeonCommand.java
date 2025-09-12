package dev.bukkit.command.impl;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import dev.bukkit.DMain;
import dev.bukkit.game.dungeon.BukkitVoidWorldGenerator;
import dev.bukkit.game.dungeon.DungeonBuilderBukkit;
import dev.core.game.dungeon.DecorationElement;
import dev.core.game.dungeon.Dungeon;
import dev.core.game.dungeon.DungeonGenerator;
import dev.core.game.dungeon.DungeonRoom;
import dev.core.game.dungeon.DungeonStatistics;
import dev.core.game.dungeon.Point3D;
import dev.core.game.dungeon.SpawnLocation;

public class DungeonCommand implements CommandExecutor {

    private Plugin plugin;
    private int roomCount;

    public DungeonCommand() {
        this.plugin = DMain.getInstance();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can generate dungeons!");
            return true;
        }

        Player player = (Player) sender;
        World world = Bukkit.createWorld(new WorldCreator(args[1]).generator(new BukkitVoidWorldGenerator()));

        roomCount = 10;
        if (args.length > 0) {
            try {
                roomCount = Integer.parseInt(args[0]);
                roomCount = Math.max(1, roomCount); // Limit between 1-50
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid room count! Using default: 10");
            }
        }

        player.sendMessage("§aGenerating dungeon with " + roomCount + " rooms...");

        // Generate dungeon in async task
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            DungeonGenerator generator = new DungeonGenerator(System.currentTimeMillis());
            Point3D startPoint = new Point3D(0, 64, // Fixed Y level for dungeons
                    0);

            Dungeon dungeon = generator.generateDungeon(roomCount, startPoint);

            // Access spawn information
            for (DungeonRoom room : dungeon.getRooms()) {
                List<SpawnLocation> roomSpawns = room.getSpawnLocations();
                List<DecorationElement> roomDecorations = room.getDecorations();

                System.out.println("Room " + room.getId() + ": " + roomSpawns.size() + " spawns, "
                        + roomDecorations.size() + " decorations");
            }

            // Get dungeon-wide statistics
            DungeonStatistics stats = dungeon.getStatistics();
            player.sendMessage("§6" + stats.toString());

            // Build dungeon on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                DungeonBuilderBukkit builder = new DungeonBuilderBukkit(plugin, world);

                // Build dungeon after clearing (delay by 5 seconds)
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    builder.buildDungeon(dungeon, () -> {
                        player.sendMessage(
                                "§aDungeon generation complete! Generated " + dungeon.getRooms().size() + " rooms.");

                        // Teleport player to start room
                        DungeonRoom startRoom = dungeon.getStartRoom();
                        if (startRoom != null) {
                            Point3D center = startRoom.getCenter();
                            player.teleport(new org.bukkit.Location(world, center.getX() + 0.5, center.getY() + 1,
                                    center.getZ() + 0.5));
                        }
                    });
                }, 100L); // 5 second delay
            });
        });

        return true;
    }
}
