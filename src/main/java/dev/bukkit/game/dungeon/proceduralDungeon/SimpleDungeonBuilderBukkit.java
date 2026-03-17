package dev.bukkit.game.dungeon.proceduralDungeon;

import dev.bukkit.DMain;
import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.*;
import dev.core.game.dungeon.proceduralDungeon.SimpleRandomWalkDungeonGenerator.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SimpleDungeonBuilderBukkit {

    private Plugin plugin;
    private World world;

    public SimpleDungeonBuilderBukkit(Plugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;
    }

    public void buildDungeon(AbstractDungeonGenerator dungeonGenerator, Runnable onComplete) {
        Queue<Vector3Int> floorPositions = new LinkedList<>(dungeonGenerator.getFloorPositions());
        Queue<Vector3Int> wallPositions = new LinkedList<>(dungeonGenerator.getWallPositions());
        Queue<BoundingBox> tmpRooms = new LinkedList<>();
        if (dungeonGenerator instanceof RoomFirstDungeonGenerator roomFirstDungeonGenerator) {
            tmpRooms = new LinkedList<>(roomFirstDungeonGenerator.getRooms());
        }
        Queue<BoundingBox> rooms = tmpRooms;
        int blocksToPlace = floorPositions.size() + wallPositions.size();
        Bukkit.broadcastMessage("§a" + blocksToPlace + " Blocks (" + rooms.size() + " Rooms) to place ...");
        new BukkitRunnable() {
            int blocksPerTick = 1; // Adjust based on performance needs
            @Override
            public void run() {
                if (rooms.isEmpty() && blocksPerTick != 100) {
                    blocksPerTick = 100;
                }
                for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!rooms.isEmpty()) {
                        BoundingBox box = rooms.poll();
                        System.out.println("Room with " + box.getFilledBoxPositions().size() + " blocks");
                        for (var position : box.get2DFilledBoxPositions()) {
                            setBlock(position.add(0,-1,0), Material.WHITE_WOOL);
                        }
                        setBlock(box.getMinPoint().add(0,-1,0), Material.GREEN_WOOL);
                        setBlock(box.getMinPoint().add(box.getDimensions().getX(),-1,0), Material.YELLOW_WOOL);
                        setBlock(box.getMinPoint().add(0,-1,box.getDimensions().getZ()), Material.YELLOW_WOOL);
                        setBlock(box.getMaxPoint().add(0,-1,0), Material.RED_WOOL);
                        setBlock(box.get2DCenter().add(0,-1,0), Material.BLUE_WOOL);
                        try {
                            synchronized (this) {
                                this.wait(200);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (!floorPositions.isEmpty()) {
                        nextPos = floorPositions.poll();
                        Material floorMat = Material.STONE;
//                        floorMat = Material.BLACK_STAINED_GLASS;
                        setBlock(nextPos, floorMat);
                    } else if (!wallPositions.isEmpty()) {
                        nextPos = wallPositions.poll();
                        Material wallMat = Material.STONE_BRICKS;
//                        wallMat = Material.GLASS;
                        setBlock(nextPos, wallMat);
                        setBlock(nextPos.add(0,1,0), wallMat);
                        setBlock(nextPos.add(0,2,0), wallMat);
                        setBlock(nextPos.add(0,3,0), wallMat);
                    } else {
                        cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        Bukkit.broadcastMessage("§aDungeon construction complete!");
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void resetSpace(AbstractDungeonGenerator dungeonGenerator, Runnable onComplete) {
        Queue<Vector3Int> floorPositions = new LinkedList<>(dungeonGenerator.getFloorPositions());
        Queue<Vector3Int> wallPositions = new LinkedList<>(dungeonGenerator.getWallPositions());
        Queue<BoundingBox> tmpRooms = new LinkedList<>();
        if (dungeonGenerator instanceof RoomFirstDungeonGenerator roomFirstDungeonGenerator) {
            tmpRooms = new LinkedList<>(roomFirstDungeonGenerator.getRooms());
        }
        Queue<BoundingBox> rooms = tmpRooms;

        BoundingBox bounds = new BoundingBox(dungeonGenerator.getMaxBounds().getMinPoint().add(0,-1,0), dungeonGenerator.getMaxBounds().getMaxPoint());
        Queue<Vector3Int> allSpace = new LinkedList<>(bounds.getFilledBoxPositions());

        new BukkitRunnable() {
            @Override
            public void run() {
                int blocksPerTick = 1000; // Adjust based on performance needs
                outer: for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!allSpace.isEmpty()) {
                        while (isBlockAir(Objects.requireNonNull(nextPos = allSpace.poll()))){
                            if (allSpace.isEmpty()) continue outer;
                        }
                        setBlockToAir(nextPos);
//                    if (!rooms.isEmpty()) {
//                        BoundingBox box = rooms.poll();
//                        box = new BoundingBox(box.getMinPoint(), box.getMaxPoint().add(0,4,0));
//                        for (var position : box.getFilledBoxPositions()) {
//                            setBlockToAir(position.add(0,-1,0));
//                        }
//                    } else if (!floorPositions.isEmpty()) {
//                        nextPos = floorPositions.poll();
//                        setBlockToAir(nextPos);
//                    } else if (!wallPositions.isEmpty()) {
//                        nextPos = wallPositions.poll();
//                        setBlockToAir(nextPos);
//                        setBlockToAir(nextPos.add(0,1,0));
//                        setBlockToAir(nextPos.add(0,2,0));
//                        setBlockToAir(nextPos.add(0,3,0));
                    } else {
                        this.cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        Bukkit.broadcastMessage("§aDungeon reset complete!");
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void buildDungeon(RoomFirstDungeonGenerator3D dungeonGenerator, Runnable onComplete) {
        Queue<Vector3Int> floorPositions = new LinkedList<>(dungeonGenerator.getFloorPositions());
        Queue<Vector3Int> wallPositions = new LinkedList<>(dungeonGenerator.getWallPositions());
        Queue<Vector3Int> corridorFloors = new LinkedList<>(dungeonGenerator.getCorridorFloor());
        Queue<BoundingBox> rooms = new LinkedList<>(dungeonGenerator.getRooms());
        Queue<BoundingBox> notConnectedRooms = new LinkedList<>(dungeonGenerator.getNotConnectedRooms());
        int blocksToPlace = floorPositions.size() + wallPositions.size() + corridorFloors.size();
        Bukkit.broadcastMessage("§a" + blocksToPlace + " Blocks (" + rooms.size() + " Rooms) to place ...");
        long time = System.currentTimeMillis();
        new BukkitRunnable() {
            int blocksPerTick = 1; // Adjust based on performance needs
            @Override
            public void run() {
                if (rooms.isEmpty() && blocksPerTick == 1) {
                    blocksPerTick = 100;
                }
                if (floorPositions.isEmpty() && blocksPerTick == 100) {
                    blocksPerTick = 20;
                }
                if (corridorFloors.isEmpty() && blocksPerTick == 20) {
                    blocksPerTick = 500;
                }
                for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!rooms.isEmpty()) {
                        BoundingBox box = rooms.poll();
//                        System.out.println("Room with " + box.getFilledBoxPositions().size() + " blocks");
                        for (var position : box.getHollowBoxPositions()) {
                            if (notConnectedRooms.contains(box)) setBlock(position, Material.PINK_STAINED_GLASS);
                            if (dungeonGenerator.getBossRoom().equals(box)) setBlock(position, Material.RED_STAINED_GLASS);
                            if (dungeonGenerator.getStartRoom().equals(box)) setBlock(position, Material.LIME_STAINED_GLASS);
                        }
                        for (var position : box.expand(0).get2DFilledBoxPositions()) {
                            setBlock(position.add(0,-1,0), Material.WHITE_WOOL);
                        }
                        setBlock(box.getMinPoint().add(0,0,0), Material.GREEN_WOOL);
                        setBlock(box.getMinPoint().add(box.getDimensions().getX(),-1,0), Material.YELLOW_WOOL);
                        setBlock(box.getMinPoint().add(0,-1,box.getDimensions().getZ()), Material.YELLOW_WOOL);
                        setBlock(box.getMaxPoint().add(0,0,0), Material.RED_WOOL);
                        setBlock(box.get2DCenter().add(0,-1,0), Material.BLUE_WOOL);
//                        try {
//                            synchronized (this) {
//                                this.wait(100);
//                            }
//                        } catch (InterruptedException e) {
//                            throw new RuntimeException(e);
//                        }
                    } else if (!floorPositions.isEmpty()) {
                        nextPos = floorPositions.poll();
                        Material floorMat = Material.STONE;
                        setBlock(nextPos, floorMat);
                        if (world.getBlockAt(nextPos.x, nextPos.y - 1, nextPos.z).getType() != Material.BLUE_WOOL) setBlock(nextPos.add(0,-1,0), floorMat); // testing
                    } else if (!corridorFloors.isEmpty()) {
                        nextPos = corridorFloors.poll();
                        Material floorMat = Material.SMOOTH_STONE;
                        setBlock(nextPos, floorMat);
                    } else if (!wallPositions.isEmpty()) {
                        nextPos = wallPositions.poll();
                        Material wallMat = Material.STONE_BRICKS;
//                        wallPositions.clear(); // for testing
                        setBlock(nextPos, wallMat);
                    } else {
                        cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        long timePassed = System.currentTimeMillis() - time;
                        double sec = timePassed / 1000d;
                        Bukkit.broadcastMessage("§aDungeon construction completed! (in " + sec + " secs)");
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void buildSpace(List<BoundingBox> boundingBoxes, Runnable onComplete) {
        Queue<BoundingBox> rooms = new LinkedList<>(boundingBoxes);

        BoundingBox biggestRoom = boundingBoxes.stream().max(Comparator.comparing(b -> b.getDimensions().getLength())).orElse(new BoundingBox(0,0,0,0,0,0));

        int blocksToPlace = rooms.size();
        Bukkit.broadcastMessage("§a" + blocksToPlace + " Blocks (" + rooms.size() + " Rooms) to place ... Biggest Room = " + biggestRoom + "(" + biggestRoom.getDimensions().getLength() + ")");
        new BukkitRunnable() {
            int blocksPerTick = 1; // Adjust based on performance needs
            @Override
            public void run() {
                for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!rooms.isEmpty()) {
                        BoundingBox box = rooms.poll();
                        System.out.println("Room with " + box.getFilledBoxPositions().size() + " blocks");
                        int yOffset = 0;
                        Material mat = box.equals(biggestRoom) ? Material.LIGHT_BLUE_WOOL : Material.WHITE_WOOL;
                        for (var position : box.expand(0).getHollowBoxPositions()) {
                            setBlock(position.add(0,yOffset,0), mat);
                        }
                        setBlock(box.getMinPoint().add(0,yOffset,0), Material.GREEN_WOOL);
                        setBlock(box.getMinPoint().add(box.getDimensions().getX(),yOffset,0), Material.YELLOW_WOOL);
                        setBlock(box.getMinPoint().add(0,yOffset,box.getDimensions().getZ()), Material.YELLOW_WOOL);
                        setBlock(box.getMaxPoint().add(0,yOffset,0), Material.RED_WOOL);
                        setBlock(box.get2DCenter().add(0,yOffset,0), Material.BLUE_WOOL);
                        try {
                            synchronized (this) {
                                this.wait(200);
                            }
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        Bukkit.broadcastMessage("§aConstruction complete!");
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void resetSpace(BoundingBox space, Set<Vector3Int> extraBlocks, Runnable onComplete) {
        Queue<Vector3Int> allSpace = new LinkedList<>(space.getFilledBoxPositions());
        allSpace.addAll(extraBlocks);
        new BukkitRunnable() {
            @Override
            public void run() {
                int blocksPerTick = 1000; // Adjust based on performance needs
                outer: for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!allSpace.isEmpty()) {
                        while (isBlockAir(Objects.requireNonNull(nextPos = allSpace.poll()))){
                            if (allSpace.isEmpty()) continue outer;
                        }
                        setBlockToAir(nextPos);
                    } else {
                        this.cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        Bukkit.broadcastMessage("§aDungeon reset complete!");
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void setBlock(Vector3Int point, Material material) {
        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
        block.setType(material);
    }

    private void setBlockToAir(Vector3Int point) {
        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
        block.setType(Material.AIR);
    }

    private boolean isBlockAir(Vector3Int point) {
        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
        return block.getType() == Material.AIR;
    }



    private static AbstractDungeonGenerator lastDungeonGenerator;
    private static BoundingBox lastGeneratedSpace;
    private static RoomFirstDungeonGenerator3D last3DDungeonGenerator;

    public static void initDungeonTestCommand(CommandManager commandManager) {
        System.out.println("INIT Dungeon Test Commands");
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("createDungeonWorld")
                .addAlias("cdw")
                .setPlayerCommandAction(1, (player, args) -> {
                    Plugin plugin = DMain.getInstance();

                    World world = Bukkit.createWorld(new WorldCreator(args[0]).generator(new BukkitVoidWorldGenerator()));

                    Vector3Int startPoint = new Vector3Int(0, 64, 0); // Fixed Y level for dungeons

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(new Location(world, startPoint.getX() + 0.5, startPoint.getY() + 1, startPoint.getZ() + 0.5));
                    }, 20L);
                })
                .setCommandArgumentsList(0, "worldName"));
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("createDungeon")
                .addAlias("cd")
                .setPlayerCommandAction(2, (player, args) -> {
                    Plugin plugin = DMain.getInstance();

                    Runnable generateDungeon = () -> {

                        player.sendMessage("§aGenerating dungeon ...");

                        Vector3Int startPoint = new Vector3Int(0, 64, 0); // Fixed Y level for dungeons

                        RoomFirstDungeonGenerator.RoomFirstParameters roomFirstParameters =
                                switch (args[0]) {
                                    case "medium" -> DungeonGenerationParameters.roomFirstParametersMedium;
                                    case "big" -> DungeonGenerationParameters.roomFirstParametersBig;
                                    default -> DungeonGenerationParameters.roomFirstParametersSmall;
                                };

                        long seed = Long.parseLong(args[1]);

                        // Generate dungeon in async task
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            player.sendMessage("§aStarting generation ...");
                            RoomFirstDungeonGenerator dungeonGenerator = new RoomFirstDungeonGenerator(roomFirstParameters);
                            dungeonGenerator.setStartPosition(startPoint);
                            if (seed != -1) {
                                dungeonGenerator.generateDungeon(seed);
                            } else {
                                dungeonGenerator.generateDungeon();
                            }

                            player.sendMessage("§aDungeon generation complete!");

                            lastDungeonGenerator = dungeonGenerator;

                            // Build dungeon on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§aBuilding ...");
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                                dungeonBuilder.buildDungeon(dungeonGenerator, () -> {
                                    player.sendMessage("§aDungeon building complete!");
                                });
                            });
                        });
                    };

                    if (lastDungeonGenerator != null) {
                        player.sendMessage("§aResetting previous dungeon ...");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                            dungeonBuilder.resetSpace(lastDungeonGenerator, generateDungeon);
                            lastDungeonGenerator = null;
                        });
                    } else {
                        generateDungeon.run();
                    }
                })
                .setCommandArgumentsList(0, List.of("small", "medium", "big"), "dungeonSize")
                .setCommandArgumentsList(1, "seed")
        );
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("createCustomDungeon")
                .addAlias("ccd")
                .setPlayerCommandAction(9, (player, args) -> {
                    Plugin plugin = DMain.getInstance();

                    Runnable generateDungeon = () -> {
                        player.sendMessage("§aGenerating dungeon ...");

                        Vector3Int startPoint = new Vector3Int(0, 64, 0); // Fixed Y level for dungeons
                        int minRoomWidth = Integer.parseInt(args[0]);
                        int minRoomLength = Integer.parseInt(args[1]);
                        int dungeonWidth = Integer.parseInt(args[2]);
                        int dungeonHeight = Integer.parseInt(args[3]);
                        int offset = Integer.parseInt(args[4]);
                        boolean randomWalkRooms = Boolean.parseBoolean(args[5]);

                        int iterations = Integer.parseInt(args[6]);
                        int walkLength = Integer.parseInt(args[7]);
                        boolean startRandomlyEachIteration = Boolean.parseBoolean(args[8]);

                        SimpleRandomWalkParameters randomWalkParameters = new SimpleRandomWalkParameters(iterations, walkLength, startRandomlyEachIteration);

                        player.sendMessage("§aWith Parameters: minRoomWidth= " + minRoomWidth + " , minRoomLength= " + minRoomLength + " , dungeonWidth= " + dungeonWidth + " , dungeonHeight= " + dungeonHeight + " , offset= " + offset + " , randomWalkRooms= " + randomWalkRooms);

                        player.getWorld().getBlockAt(startPoint.getX(), startPoint.getY(), startPoint.getZ()).setType(Material.GREEN_CONCRETE);
                        player.getWorld().getBlockAt(startPoint.getX() + dungeonWidth, startPoint.getY() + dungeonHeight, startPoint.getZ()).setType(Material.RED_CONCRETE);

                        // Generate dungeon in async task
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            player.sendMessage("§aStarting generation ...");
                            AbstractDungeonGenerator dungeonGenerator = new RoomFirstDungeonGenerator(randomWalkParameters, minRoomWidth, minRoomLength, dungeonWidth, dungeonHeight, offset, randomWalkRooms);
                            dungeonGenerator.setStartPosition(startPoint);
                            dungeonGenerator.generateDungeon();

                            player.sendMessage("§aDungeon generation complete!");

                            lastDungeonGenerator = dungeonGenerator;

                            // Build dungeon on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§aBuilding ...");
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                                dungeonBuilder.buildDungeon(dungeonGenerator, () -> {
                                    player.sendMessage("§aDungeon building complete!");
                                });
                            });
                        });
                    };

                    if (lastDungeonGenerator != null) {
                        player.sendMessage("§aResetting previous dungeon ...");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                            dungeonBuilder.resetSpace(lastDungeonGenerator, generateDungeon);
                            lastDungeonGenerator = null;
                        });
                    } else {
                        generateDungeon.run();
                    }
                })
                .setCommandArgumentsList(0, "minRoomWidth")
                .setCommandArgumentsList(1, "minRoomHeight")
                .setCommandArgumentsList(2, "dungeonWidth")
                .setCommandArgumentsList(3, "dungeonHeight")
                .setCommandArgumentsList(4, "offset")
                .setCommandArgumentsList(5, "randomWalkRooms")
                .setCommandArgumentsList(5, "iterations")
                .setCommandArgumentsList(6, "walkLength")
                .setCommandArgumentsList(7, "startRandomlyEachIteration")
        );
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("resetDungeon")
                .addAlias("rd")
                .setPlayerCommandAction(0, (player, args) -> {
                    if (lastDungeonGenerator == null) {
                        player.sendMessage("§cNo Dungeon to reset.");
                        return;
                    }

                    Plugin plugin = DMain.getInstance();

                    player.sendMessage("§aResetting dungeon ...");

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                        dungeonBuilder.resetSpace(lastDungeonGenerator, () -> {});
                        lastDungeonGenerator = null;
                    });
                })
        );
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("teleportToDungeonWorld")
                .addAlias("tpDungeon").addAlias("tpd")
                .setPlayerCommandAction(1, (player, args) -> {

                    World world = Bukkit.getWorld(args[0]);

                    if (world == null) {
                        player.sendMessage("§cFailed.");
                        return;
                    }

                    player.teleport(new Location(world, 0.5, 65, 0.5));

                }).setCommandArgumentsList(0, "WorldName")
        );
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("dungeonTest")
                .setPlayerCommandAction(14, "create", (player, args) -> {
                    Plugin plugin = DMain.getInstance();

                    Runnable buildSpace = () -> {
                        int minRoomWidth = Integer.parseInt(args[1]);
                        int minRoomHeight = Integer.parseInt(args[2]);
                        int minRoomLength = Integer.parseInt(args[3]);
                        int dungeonWidth = Integer.parseInt(args[4]);
                        int dungeonHeight = Integer.parseInt(args[5]);
                        int dungeonLength = Integer.parseInt(args[6]);

                        int roomOffset = Integer.parseInt(args[7]);
                        boolean randomWalkRooms = Boolean.parseBoolean(args[8]);
                        int corridorWidth = Integer.parseInt(args[9]);

                        int iterations = Integer.parseInt(args[10]);
                        int walkLength = Integer.parseInt(args[11]);
                        boolean startRandomlyEachIteration = Boolean.parseBoolean(args[12]);
                        SimpleRandomWalkParameters parameters = new SimpleRandomWalkParameters(iterations, walkLength, startRandomlyEachIteration);

                        long seed = Long.parseLong(args[13]) == -1 ? System.currentTimeMillis() : Long.parseLong(args[13]);

                        Vector3Int startPoint = new Vector3Int(0, 64, 0);
                        BoundingBox space = new BoundingBox(startPoint, startPoint.add(dungeonWidth, dungeonHeight, dungeonLength));

                        // Generate dungeon in async task
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            player.sendMessage("§aStarting generation ...");
//                            List<BoundingBox> rooms = ProceduralGenerationAlgorithms.binarySpacePartitioning3D(space, minRoomWidth, minRoomHeight, minRoomLength, new Random(seed));

                            RoomFirstDungeonGenerator3D dungeonGenerator = new RoomFirstDungeonGenerator3D(startPoint, parameters, minRoomWidth, minRoomHeight, minRoomLength, dungeonWidth, dungeonHeight, dungeonLength, roomOffset, randomWalkRooms, corridorWidth);

                            dungeonGenerator.generateDungeon(seed);

                            lastGeneratedSpace = new BoundingBox(space.getMinPoint().add(0,-1,0), space.getMaxPoint()); // -1 for testing
                            last3DDungeonGenerator = dungeonGenerator;

                            // Build dungeon on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§aBuilding ...");
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                                dungeonBuilder.buildDungeon(dungeonGenerator, () -> {});
//                                dungeonBuilder.buildSpace(rooms, () -> {
//                                });
                            });
                        });
                    };

                    if (lastGeneratedSpace != null) {
                        player.sendMessage("§aResetting previous space ...");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                            Set<Vector3Int> extraBlocks = new LinkedHashSet<>(last3DDungeonGenerator.getCorridorFloor());
                            extraBlocks.addAll(last3DDungeonGenerator.getWallPositions());
                            dungeonBuilder.resetSpace(lastGeneratedSpace, extraBlocks, buildSpace);
                            lastGeneratedSpace = null;
                            last3DDungeonGenerator = null;
                        });
                    } else {
                        buildSpace.run();
                    }
                })
                .setCommandArgumentsList(0, List.of("create", "reset"))
                .setCommandArgumentsList(1, "create", "minRoomWidth")
                .setCommandArgumentsList(2, "create", "minRoomHeight")
                .setCommandArgumentsList(3, "create", "minRoomLength")
                .setCommandArgumentsList(4, "create", "dungeonWidth")
                .setCommandArgumentsList(5, "create", "dungeonHeight")
                .setCommandArgumentsList(6, "create", "dungeonLength")
                .setCommandArgumentsList(7, "create", "roomOffset")
                .setCommandArgumentsList(8, "create", "randomWalkRooms")
                .setCommandArgumentsList(9, "create", "corridorWidth")
                .setCommandArgumentsList(10, "create", "iterations")
                .setCommandArgumentsList(11, "create", "walkLength")
                .setCommandArgumentsList(12, "create", "startRandomlyEachIteration")
                .setCommandArgumentsList(13, "create", "seed")
                .setPlayerCommandAction(1, "reset", (player, args) -> {
                    if (lastGeneratedSpace == null) {
                        player.sendMessage("§cNo Space to reset.");
                        return;
                    }
                    Plugin plugin = DMain.getInstance();

                    player.sendMessage("§aResetting space ...");

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin, player.getWorld());
                        Set<Vector3Int> extraBlocks = new LinkedHashSet<>(last3DDungeonGenerator.getCorridorFloor());
                        extraBlocks.addAll(last3DDungeonGenerator.getWallPositions());
                        dungeonBuilder.resetSpace(lastGeneratedSpace, extraBlocks, () -> {});
                        lastGeneratedSpace = null;
                        last3DDungeonGenerator = null;
                    });
                })
        );
    }
}
