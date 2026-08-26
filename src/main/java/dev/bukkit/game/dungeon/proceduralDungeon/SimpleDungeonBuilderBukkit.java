package dev.bukkit.game.dungeon.proceduralDungeon;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.proceduralDungeon.util.DungeonRoom;
import dev.core.game.dungeon.proceduralDungeon.util.DungeonSpawnManager;
import dev.core.game.dungeon.proceduralDungeon.util.SpawnLocation;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import dev.bukkit.DMain;
import dev.bukkit.command.CommandManager;
import dev.bukkit.command.SubCommandBuilder;
import dev.bukkit.entity.boss.BukkitDisplayEntityRegistry;
import dev.core.game.dungeon.BoundingBox;
import dev.core.game.dungeon.proceduralDungeon.AbstractDungeonGenerator;
import dev.core.game.dungeon.proceduralDungeon.DungeonGenerationParameters;
import dev.core.game.dungeon.proceduralDungeon.RoomFirstDungeonGenerator;
import dev.core.game.dungeon.proceduralDungeon.RoomFirstDungeonGenerator3D;
import dev.core.game.dungeon.proceduralDungeon.SimpleRandomWalkDungeonGenerator.SimpleRandomWalkParameters;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonCeilingBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonDecorationBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonFloorBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonStairBlock;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.DungeonWallBlock;
import org.bukkit.util.Transformation;
import org.joml.Vector3f;

public class SimpleDungeonBuilderBukkit {

    private record Pair<F, S>(F first, S second) {
    }

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
//                        BoundingBox box = rooms.poll();
//                        System.out.println("Room with " + box.getFilledBoxPositions().size() + " blocks");
//                        for (var position : box.get2DFilledBoxPositions()) {
//                            setBlock(position.add(0, -1, 0), Material.WHITE_WOOL);
//                        }
//                        setBlock(box.getMinPoint().add(0, -1, 0), Material.GREEN_WOOL);
//                        setBlock(box.getMinPoint().add(box.getDimensions().getX(), -1, 0), Material.YELLOW_WOOL);
//                        setBlock(box.getMinPoint().add(0, -1, box.getDimensions().getZ()), Material.YELLOW_WOOL);
//                        setBlock(box.getMaxPoint().add(0, -1, 0), Material.RED_WOOL);
//                        setBlock(box.get2DCenter().add(0, -1, 0), Material.BLUE_WOOL);
                        rooms.clear();
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
                        setBlock(nextPos.add(0, 1, 0), wallMat);
                        setBlock(nextPos.add(0, 2, 0), wallMat);
                        setBlock(nextPos.add(0, 3, 0), wallMat);
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

        BoundingBox bounds = new BoundingBox(dungeonGenerator.getMaxBounds().getMinPoint().add(0, -1, 0),
                dungeonGenerator.getMaxBounds().getMaxPoint());
        Queue<Vector3Int> allSpace = new LinkedList<>(bounds.getFilledBoxPositions());

        new BukkitRunnable() {
            @Override
            public void run() {
                int blocksPerTick = 1000; // Adjust based on performance needs
                outer: for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!allSpace.isEmpty()) {
                        while (isBlockAir(Objects.requireNonNull(nextPos = allSpace.poll()))) {
                            if (allSpace.isEmpty())
                                continue outer;
                        }
                        setBlockToAir(nextPos);
                        if (!rooms.isEmpty()) {
                            BoundingBox box = rooms.poll();
                            box = new BoundingBox(box.getMinPoint(), box.getMaxPoint().add(0, 4, 0));
                            for (var position : box.getFilledBoxPositions()) {
                                setBlockToAir(position.add(0, -1, 0));
                            }
                        } else if (!floorPositions.isEmpty()) {
                            nextPos = floorPositions.poll();
                            setBlockToAir(nextPos);
                        } else if (!wallPositions.isEmpty()) {
                            nextPos = wallPositions.poll();
                            setBlockToAir(nextPos);
                            setBlockToAir(nextPos.add(0, 1, 0));
                            setBlockToAir(nextPos.add(0, 2, 0));
                            setBlockToAir(nextPos.add(0, 3, 0));
                        }
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

    public void buildDungeon(RoomFirstDungeonGenerator3D dungeonGenerator, Runnable onComplete, boolean debugMode) {
//        Queue<Vector3Int> floorPositions = new LinkedList<>(dungeonGenerator.getFloorPositions());
        Queue<DungeonFloorBlock> floorBlocks = new LinkedList<>(dungeonGenerator.getFloorBlocks());
//        Queue<Vector3Int> wallPositions = new LinkedList<>(dungeonGenerator.getWallPositions());
//        Queue<Vector3Int> corridorFloors = new LinkedList<>(dungeonGenerator.getCorridorFloor());
        Queue<BoundingBox> rooms = new LinkedList<>(dungeonGenerator.getRooms());
        Queue<BoundingBox> notConnectedRooms = new LinkedList<>(dungeonGenerator.getNotConnectedRooms());

        DungeonMaterialsManagerBukkit matManager = new DungeonMaterialsManagerBukkit(dungeonGenerator.getLastUsedSeed(),
                world);

        Queue<DungeonStairBlock> stairBlocks = new LinkedList<>(dungeonGenerator.getStairBlocks());
        Queue<DungeonFloorBlock> corridorBlocks = new LinkedList<>(dungeonGenerator.getCorridorBlocks());
        Queue<DungeonWallBlock> wallBlocks = new LinkedList<>(dungeonGenerator.getWallBlocks());
        Queue<DungeonCeilingBlock> ceilingBlocks = new LinkedList<>(dungeonGenerator.getCeilingBlocks());
        Queue<Set<DungeonDecorationBlock>> decorationBlocks = new LinkedList<>(dungeonGenerator.getDecorationBlocks());

        // --- Debug: compute overlap counts and filter-out placements that would block corridors/stairs ---
        var floorPosSet = floorBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));
        var corridorPosSet = corridorBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));
        var stairPosSet = stairBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));
        var wallPosSet = wallBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));
        var ceilingPosSet = ceilingBlocks.stream().map(DungeonBlock::getPos).collect(Collectors.toCollection(LinkedHashSet::new));

        int wallVsCorridor = (int) wallPosSet.stream().filter(corridorPosSet::contains).count();
        int wallVsStair = (int) wallPosSet.stream().filter(stairPosSet::contains).count();
        int ceilingVsStair = (int) ceilingPosSet.stream().filter(stairPosSet::contains).count();

        Bukkit.broadcastMessage("§e[DBG] overlaps before filter - wall vs corridor: " + wallVsCorridor + ", wall vs stair: " + wallVsStair + ", ceiling vs stair: " + ceilingVsStair);

        // Create final filtered queues (avoid reassigning original locals so anonymous class can capture them)
        final Queue<DungeonWallBlock> wallBlocksFiltered = new LinkedList<>(wallBlocks.stream().filter(w -> {
            var p = w.getPos();
            return !corridorPosSet.contains(p) && !stairPosSet.contains(p) && !floorPosSet.contains(p);
        }).collect(Collectors.toCollection(LinkedHashSet::new)));

        final Queue<DungeonCeilingBlock> ceilingBlocksFiltered = new LinkedList<>(ceilingBlocks.stream().filter(c -> {
            var p = c.getPos();
            return !corridorPosSet.contains(p) && !stairPosSet.contains(p) && !floorPosSet.contains(p);
        }).collect(Collectors.toCollection(LinkedHashSet::new)));

        // Filter decoration blocks group-wise: drop individual decoration blocks that would overlap
        var filteredDecorations = new LinkedList<Set<DungeonDecorationBlock>>();
        for (var set : decorationBlocks) {
            var filtered = set.stream().filter(d -> {
                var p = d.getPos();
                return !corridorPosSet.contains(p) && !stairPosSet.contains(p) && !floorPosSet.contains(p);
            }).collect(Collectors.toCollection(LinkedHashSet::new));
            if (!filtered.isEmpty()) filteredDecorations.add(filtered);
        }
        final Queue<Set<DungeonDecorationBlock>> decorationBlocksFiltered = new LinkedList<>(filteredDecorations);

        // report counts after filtering
        Bukkit.broadcastMessage("§e[DBG] after filter - walls: " + wallBlocksFiltered.size() + ", ceilings: " + ceilingBlocksFiltered.size() + ", decoGroups: " + decorationBlocksFiltered.size());

        int blocksToPlace = floorBlocks.size() + corridorBlocks.size() + stairBlocks.size() + wallBlocksFiltered.size()
                + ceilingBlocksFiltered.size() + decorationBlocksFiltered.size();
        Bukkit.broadcastMessage("§a" + blocksToPlace + " Blocks (" + rooms.size() + " Rooms) to place ...");

        Queue<Pair<Vector3Int, Material>> currentRoomBox = new LinkedList<>();

        if (debugMode) {
            for (DungeonRoom dungeonRoom : dungeonGenerator.getDungeonRooms()) {
                for (SpawnLocation spawnPosition : dungeonRoom.getSpawnPositions()) {
                    Point3D p = spawnPosition.getPosition();
                    Location loc = new Location(world, p.getX() + 0.5, p.getY() + 2.25, p.getZ() + 0.5);
                    if (spawnPosition.isMiniBossSpawn()) {
                        spawnBlockDisplay(world, Vector3Int.fromPoint3D(p), Vector3Int.fromPoint3D(p).add(0,1,0), Material.PINK_STAINED_GLASS);
                        BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, TextDisplay.class, d -> {
                            d.setText("MiniBoss");
                            d.setBillboard(Display.Billboard.CENTER);
                        });
                    } else if (spawnPosition.isEliteSpawn()) {
                        spawnBlockDisplay(world, Vector3Int.fromPoint3D(p), Vector3Int.fromPoint3D(p).add(0,1,0), Material.MAGENTA_STAINED_GLASS);
                        BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, TextDisplay.class, d -> {
                            d.setText("Elite");
                            d.setBillboard(Display.Billboard.CENTER);
                        });
                    } else {
                        spawnBlockDisplay(world, Vector3Int.fromPoint3D(p), Vector3Int.fromPoint3D(p).add(0,1,0), Material.PURPLE_STAINED_GLASS);
                    }
                }
            }
        }

        long time = System.currentTimeMillis();
        new BukkitRunnable() {
            int blocksPerTick = 200; // Adjust based on performance needs

            @Override
            public void run() {
                if (rooms.isEmpty() && currentRoomBox.isEmpty() && blocksPerTick == 200) {
                    blocksPerTick = 100;
                }
                if (floorBlocks.isEmpty() && blocksPerTick == 100) {
                    blocksPerTick = 20;
                }
                if (corridorBlocks.isEmpty() && blocksPerTick == 20) {
                    blocksPerTick = 500;
                }
                for (int i = 0; i < blocksPerTick; i++) {
                    DungeonBlock block = null;
                    if (!rooms.isEmpty() || !currentRoomBox.isEmpty()) {
                        if (!debugMode) {
                            rooms.clear();
                            currentRoomBox.clear();
                            break;
                        }
                        if (currentRoomBox.isEmpty()) {
                            BoundingBox box = rooms.poll();
//                            for (var position : box.getHollowBoxPositions()) {
//                                if (notConnectedRooms.contains(box))
//                                    currentRoomBox.add(new Pair<>(position, Material.PINK_STAINED_GLASS));
//                                if (dungeonGenerator.getBossRoom().equals(box))
//                                    currentRoomBox.add(new Pair<>(position, Material.RED_STAINED_GLASS));
//                                if (dungeonGenerator.getStartRoom().equals(box))
//                                    currentRoomBox.add(new Pair<>(position, Material.LIME_STAINED_GLASS));
//                            }
                            if (notConnectedRooms.contains(box))
                                spawnBlockDisplay(world, box.getMinPoint(), box.getMaxPoint(), Material.PINK_STAINED_GLASS);
                            if (dungeonGenerator.getBossRoom().equals(box))
                                spawnBlockDisplay(world, box.getMinPoint(), box.getMaxPoint(), Material.RED_STAINED_GLASS);
                            if (dungeonGenerator.getStartRoom().equals(box))
                                spawnBlockDisplay(world, box.getMinPoint(), box.getMaxPoint(), Material.LIME_STAINED_GLASS);

                            spawnBlockDisplay(world, box.getMinPoint().sub(0,1,0), new Vector3Int(box.maxX, box.minY, box.maxZ).sub(0,1,0), Material.WHITE_STAINED_GLASS);

//                            for (var position : box.expand(0).get2DFilledBoxPositions()) {
//                                currentRoomBox.add(new Pair<>(position.add(0, -1, 0), Material.WHITE_WOOL));
//                            }

                            currentRoomBox.add(new Pair<>(box.getMinPoint().add(0, 0, 0), Material.GREEN_WOOL));
                            currentRoomBox.add(new Pair<>(box.getMinPoint().add(box.getDimensions().getX(), -1, 0),
                                    Material.YELLOW_WOOL));
                            currentRoomBox.add(new Pair<>(box.getMinPoint().add(0, -1, box.getDimensions().getZ()),
                                    Material.YELLOW_WOOL));
                            currentRoomBox.add(new Pair<>(box.getMaxPoint().add(0, 0, 0), Material.RED_WOOL));
                            currentRoomBox.add(new Pair<>(box.get2DCenter().add(0, -1, 0), Material.BLUE_WOOL));
                            break;
                        }
                        var pair = currentRoomBox.poll();
                        //setBlock(pair.first, pair.second);
                        spawnBlockDisplay(world, pair.first, pair.second);
                        if (currentRoomBox.isEmpty())
                            break;
                        continue;
                    } else if (!floorBlocks.isEmpty()) {
                        block = floorBlocks.poll();
                        //if (world.getBlockAt(block.getPos().x, block.getPos().y - 1, block.getPos().z).getType() != Material.BLUE_WOOL)
                        if (debugMode)
                            setBlock(block.getPos().add(0, -1, 0), Material.STONE); // testing
                    }
//                    else if (!corridorFloors.isEmpty()) {
//                        Vector3Int nextPos = corridorFloors.poll();
//                        Material floorMat = Material.SMOOTH_STONE;
//                        setBlock(nextPos, floorMat);
//                    }
                    else if (!corridorBlocks.isEmpty()) {
                        block = corridorBlocks.poll();
                    } else if (!stairBlocks.isEmpty()) {
                        block = stairBlocks.poll();
                    } else if (!wallBlocksFiltered.isEmpty()) {
                        block = wallBlocksFiltered.poll();
//                        wallBlocks.clear(); // for testing
                    } else if (!ceilingBlocksFiltered.isEmpty()) {
                        block = ceilingBlocksFiltered.poll();
//                        ceilingBlocks.clear(); // for testing
                    } else if (!decorationBlocksFiltered.isEmpty()) {
                        LinkedHashSet<DungeonDecorationBlock> dungeonBlocks = new LinkedHashSet<>(decorationBlocksFiltered.poll());
                        matManager.placeBlocksWithSameMaterial(dungeonBlocks);
                    } else {
                        cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        long timePassed = System.currentTimeMillis() - time;
                        double sec = timePassed / 1000d;
                        Bukkit.broadcastMessage("§aDungeon construction completed! (in " + sec + " secs)");
                        if (debugMode)
                            matManager.printStatistic();
                        // teleport online players to a safe floor location within the start room
                        BoundingBox startRoom = dungeonGenerator.getStartRoom();
                        if (startRoom != null) {
                            Location spawnLoc = findSafeSpawnLocation(world, startRoom);
                            Vector3f roomCenter = startRoom.get2DCenter().toVector3f().add(0.5f, 1, 0.5f);
                            spawnLoc = new Location(world, roomCenter.x, roomCenter.y, roomCenter.z);
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                p.teleport(spawnLoc);
                            }
                            Bukkit.broadcastMessage("§aPlayers teleported to safe spot in start room: " + startRoom + " at " + spawnLoc.toVector());
                        }

                        // /d cdw dungeon1
                        //test command /d dungeonTest create 8 8 8 35 35 35 1 true 3 50 15 true 1786367046024

                        return;
                    }
                    if (block != null)
                        matManager.placeBlock(block);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void buildSpace(List<BoundingBox> boundingBoxes, Runnable onComplete) {
        Queue<BoundingBox> rooms = new LinkedList<>(boundingBoxes);

        BoundingBox biggestRoom = boundingBoxes.stream().max(Comparator.comparing(b -> b.getDimensions().getLength()))
                .orElse(new BoundingBox(0, 0, 0, 0, 0, 0));

        int blocksToPlace = rooms.size();
        Bukkit.broadcastMessage(
                "§a" + blocksToPlace + " Blocks (" + rooms.size() + " Rooms) to place ... Biggest Room = " + biggestRoom
                        + "(" + biggestRoom.getDimensions().getLength() + ")");
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
                            setBlock(position.add(0, yOffset, 0), mat);
                        }
                        setBlock(box.getMinPoint().add(0, yOffset, 0), Material.GREEN_WOOL);
                        setBlock(box.getMinPoint().add(box.getDimensions().getX(), yOffset, 0), Material.YELLOW_WOOL);
                        setBlock(box.getMinPoint().add(0, yOffset, box.getDimensions().getZ()), Material.YELLOW_WOOL);
                        setBlock(box.getMaxPoint().add(0, yOffset, 0), Material.RED_WOOL);
                        setBlock(box.get2DCenter().add(0, yOffset, 0), Material.BLUE_WOOL);
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

    public void resetSpace(BoundingBox space, Runnable onComplete, Set<Vector3Int>... extraBlocks) {
        Set<Vector3Int> allPositions = new LinkedHashSet<>();
        if (extraBlocks.length > 0)
            allPositions.addAll(extraBlocks[0]);
        allPositions.addAll(space.getFilledBoxPositions());
        for (int i = 1; i < extraBlocks.length; i++) {
            allPositions.addAll(extraBlocks[i]);
        }
        Queue<Vector3Int> allSpace = new LinkedList<>(allPositions);

        //for removing debug block displays
        org.bukkit.util.BoundingBox b = new org.bukkit.util.BoundingBox(space.minX, space.minY, space.minZ, space.maxX, space.maxY, space.maxZ).expand(1);
        var entities = world.getNearbyEntities(b, e -> e.getType() == EntityType.BLOCK_DISPLAY || e.getType() == EntityType.TEXT_DISPLAY);
        for (Entity entity : entities) {
            entity.remove();
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                int blocksPerTick = 1000; // Adjust based on performance needs
                outer: for (int i = 0; i < blocksPerTick; i++) {
                    Vector3Int nextPos;
                    if (!allSpace.isEmpty()) {
                        while (isBlockAir(Objects.requireNonNull(nextPos = allSpace.poll()))) {
                            if (allSpace.isEmpty())
                                continue outer;
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

    private void spawnBlockDisplay(World world, Vector3Int point, Material material) {
        Location loc = new Location(world, point.x, point.y, point.z);
        BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, BlockDisplay.class, d -> {
            d.setBlock(Bukkit.createBlockData(material));
            d.setBrightness(new Display.Brightness(15,15));
        });
    }

    private void spawnBlockDisplay(World world, Vector3Int minPoint, Vector3Int maxPoint, Material material) {
        Vector3f scaleVec = maxPoint.sub(minPoint).add(1,1,1).toVector3f().mul(1);
//        Vector3f middlePoint = minPoint.toVector3f().add(scaleVec.mul(0.25f));
        Vector3f spawnPoint = minPoint.toVector3f().sub(0.05f,0.05f,0.05f);
        Location loc = new Location(world, spawnPoint.x, spawnPoint.y, spawnPoint.z);
        BukkitDisplayEntityRegistry.getInstance().spawnDisplayEntity(loc, BlockDisplay.class, d -> {
            d.setBlock(Bukkit.createBlockData(material));
            d.setBrightness(new Display.Brightness(15,15));

            Transformation transformation = d.getTransformation();
            transformation.getScale().set(scaleVec.add(0.1f,0.1f,0.1f));
            d.setTransformation(transformation);
        });
    }

    private void setBlock(Vector3Int point, Material material) {
        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
        block.setType(material, false);
    }

    private void setBlockToAir(Vector3Int point) {
        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
        block.setType(Material.AIR, false);
    }

    private boolean isBlockAir(Vector3Int point) {
        Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
        return block.getType() == Material.AIR;
    }

    private static AbstractDungeonGenerator lastDungeonGenerator;
    private static BoundingBox lastGeneratedSpace;
    private static RoomFirstDungeonGenerator3D last3DDungeonGenerator;

    private static boolean checkPerm(org.bukkit.entity.Player p, String node) {
        if (p.hasPermission(node) || p.hasPermission("projectd.admin") || p.isOp()) return true;
        p.sendMessage("§cNo permission: " + node);
        return false;
    }

    public static void initDungeonTestCommand(CommandManager commandManager) {
        System.out.println("INIT Dungeon Test Commands");
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("createDungeonWorld").addAlias("cdw")
                .setDescription("Dungeon controls")
                .setPlayerCommandAction(1, (player, args) -> {
                    if (!checkPerm(player, "projectd.dungeon.createWorld")) return;
                    Plugin plugin = DMain.getInstance();

                    World world = Bukkit
                            .createWorld(new WorldCreator(args[0]).generator(new BukkitVoidWorldGenerator()));

                    Vector3Int startPoint = new Vector3Int(0, 64, 0); // Fixed Y level for dungeons

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        player.teleport(new Location(world, startPoint.getX() + 0.5, startPoint.getY() + 1,
                                startPoint.getZ() + 0.5));
                    }, 20L);
                }).setCommandArgumentsList(0, "worldName"));
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("createDungeon").addAlias("cd")
                .setDescription("Dungeon controls")
                .setPlayerCommandAction(2, (player, args) -> {
                    if (!checkPerm(player, "projectd.dungeon.create")) return;
                    Plugin plugin = DMain.getInstance();

                    Runnable generateDungeon = () -> {

                        player.sendMessage("§aGenerating dungeon ...");

                        Vector3Int startPoint = new Vector3Int(0, 64, 0); // Fixed Y level for dungeons

                        RoomFirstDungeonGenerator.RoomFirstParameters roomFirstParameters = switch (args[0]) {
                        case "medium" -> DungeonGenerationParameters.roomFirstParametersMedium;
                        case "big" -> DungeonGenerationParameters.roomFirstParametersBig;
                        default -> DungeonGenerationParameters.roomFirstParametersSmall;
                        };

                        long seed = Long.parseLong(args[1]);

                        // Generate dungeon in async task
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            player.sendMessage("§aStarting generation ...");
                            RoomFirstDungeonGenerator dungeonGenerator = new RoomFirstDungeonGenerator(
                                    roomFirstParameters);
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
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                        player.getWorld());
                                dungeonBuilder.buildDungeon(dungeonGenerator, () -> {
                                    player.sendMessage("§aDungeon building complete!");
                                });
                            });
                        });
                    };

                    if (lastDungeonGenerator != null) {
                        player.sendMessage("§aResetting previous dungeon ...");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                    player.getWorld());
                            dungeonBuilder.resetSpace(lastDungeonGenerator, generateDungeon);
                            lastDungeonGenerator = null;
                        });
                    } else {
                        generateDungeon.run();
                    }
                }).setCommandArgumentsList(0, List.of("small", "medium", "big"), "dungeonSize")
                .setCommandArgumentsList(1, "seed"));
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("createCustomDungeon").addAlias("ccd")
                .setDescription("Dungeon controls")
                .setPlayerCommandAction(9, (player, args) -> {
                    if (!checkPerm(player, "projectd.dungeon.createCustom")) return;
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

                        SimpleRandomWalkParameters randomWalkParameters = new SimpleRandomWalkParameters(iterations,
                                walkLength, startRandomlyEachIteration);

                        player.sendMessage("§aWith Parameters: minRoomWidth= " + minRoomWidth + " , minRoomLength= "
                                + minRoomLength + " , dungeonWidth= " + dungeonWidth + " , dungeonHeight= "
                                + dungeonHeight + " , offset= " + offset + " , randomWalkRooms= " + randomWalkRooms);

                        player.getWorld().getBlockAt(startPoint.getX(), startPoint.getY(), startPoint.getZ())
                                .setType(Material.GREEN_CONCRETE);
                        player.getWorld().getBlockAt(startPoint.getX() + dungeonWidth,
                                startPoint.getY() + dungeonHeight, startPoint.getZ()).setType(Material.RED_CONCRETE);

                        // Generate dungeon in async task
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            player.sendMessage("§aStarting generation ...");
                            AbstractDungeonGenerator dungeonGenerator = new RoomFirstDungeonGenerator(
                                    randomWalkParameters, minRoomWidth, minRoomLength, dungeonWidth, dungeonHeight,
                                    offset, randomWalkRooms);
                            dungeonGenerator.setStartPosition(startPoint);
                            dungeonGenerator.generateDungeon();

                            player.sendMessage("§aDungeon generation complete!");

                            lastDungeonGenerator = dungeonGenerator;

                            // Build dungeon on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§aBuilding ...");
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                        player.getWorld());
                                dungeonBuilder.buildDungeon(dungeonGenerator, () -> {
                                    player.sendMessage("§aDungeon building complete!");
                                });
                            });
                        });
                    };

                    if (lastDungeonGenerator != null) {
                        player.sendMessage("§aResetting previous dungeon ...");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                    player.getWorld());
                            dungeonBuilder.resetSpace(lastDungeonGenerator, generateDungeon);
                            lastDungeonGenerator = null;
                        });
                    } else {
                        generateDungeon.run();
                    }
                }).setCommandArgumentsList(0, "minRoomWidth").setCommandArgumentsList(1, "minRoomHeight")
                .setCommandArgumentsList(2, "dungeonWidth").setCommandArgumentsList(3, "dungeonHeight")
                .setCommandArgumentsList(4, "offset").setCommandArgumentsList(5, "randomWalkRooms")
                .setCommandArgumentsList(5, "iterations").setCommandArgumentsList(6, "walkLength")
                .setCommandArgumentsList(7, "startRandomlyEachIteration"));
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("resetDungeon").addAlias("rd")
                .setDescription("Dungeon controls")
                .setPlayerCommandAction(0, (player, args) -> {
                    if (!checkPerm(player, "projectd.dungeon.reset")) return;
                    if (lastDungeonGenerator == null) {
                        player.sendMessage("§cNo Dungeon to reset.");
                        return;
                    }

                    Plugin plugin = DMain.getInstance();

                    player.sendMessage("§aResetting dungeon ...");

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                player.getWorld());
                        dungeonBuilder.resetSpace(lastDungeonGenerator, () -> {
                        });
                        lastDungeonGenerator = null;
                    });
                }));
        commandManager.addSubCommand("project-d", SubCommandBuilder.startBuilding("teleportToDungeonWorld")
                .addAlias("tpDungeon").addAlias("tpd").setDescription("Dungeon controls").setPlayerCommandAction(1, (player, args) -> {
                    if (!checkPerm(player, "projectd.dungeon.teleport")) return;

                    World world = Bukkit.getWorld(args[0]);

                    if (world == null) {
                        player.sendMessage("§cFailed.");
                        return;
                    }

                    player.teleport(new Location(world, 0.5, 65, 0.5));

                }).setCommandArgumentsList(0, "WorldName"));
        commandManager.addSubCommand("project-d",
                SubCommandBuilder.startBuilding("dungeonTest").setDescription("Dungeon controls").setPlayerCommandAction(14, "create", (player, args) -> {
                    if (!checkPerm(player, "projectd.dungeon.test")) return;
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
                        SimpleRandomWalkParameters parameters = new SimpleRandomWalkParameters(iterations, walkLength,
                                startRandomlyEachIteration);

                        long seed = Long.parseLong(args[13]) == -1 ? System.currentTimeMillis()
                                : Long.parseLong(args[13]);

                        Vector3Int startPoint = new Vector3Int(0, 64, 0);
                        BoundingBox space = new BoundingBox(startPoint, startPoint.add(dungeonWidth, dungeonHeight, dungeonLength));

                        // Generate dungeon in async task
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                            player.sendMessage("§aStarting generation ...");
//                            List<BoundingBox> rooms = ProceduralGenerationAlgorithms.binarySpacePartitioning3D(space, minRoomWidth, minRoomHeight, minRoomLength, new Random(seed));

                            RoomFirstDungeonGenerator3D dungeonGenerator = new RoomFirstDungeonGenerator3D(startPoint,
                                    parameters, minRoomWidth, minRoomHeight, minRoomLength, dungeonWidth, dungeonHeight,
                                    dungeonLength, roomOffset, randomWalkRooms, corridorWidth);

                            dungeonGenerator.generateDungeon(seed);

                            lastGeneratedSpace = new BoundingBox(space.getMinPoint().add(0, -1, 0), space.getMaxPoint()); // -1 for testing
                            last3DDungeonGenerator = dungeonGenerator;

                            // Build dungeon on main thread
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                player.sendMessage("§aBuilding ...");
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                        player.getWorld());
                                dungeonBuilder.buildDungeon(dungeonGenerator, () -> {}, true);
//                                dungeonBuilder.buildSpace(rooms, () -> {
//                                });
                            });
                        });
                    };

                    if (lastGeneratedSpace != null) {
                        player.sendMessage("§aResetting previous space ...");
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                    player.getWorld());
                            Set<Vector3Int> decoBlocks = last3DDungeonGenerator.getDecorationBlocks().stream()
                                    .flatMap(Collection::stream).map(DungeonBlock::getPos).collect(Collectors.toSet());
                            dungeonBuilder.resetSpace(lastGeneratedSpace, buildSpace, decoBlocks,
                                    last3DDungeonGenerator.getCorridorFloor(),
                                    last3DDungeonGenerator.getWallPositions());
                            lastGeneratedSpace = null;
                            last3DDungeonGenerator = null;
                        });
                    } else {
                        buildSpace.run();
                    }
                }).setCommandArgumentsList(0, List.of("create", "reset"))
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
                            if (!checkPerm(player, "projectd.dungeon.test")) return;
                            if (lastGeneratedSpace == null) {
                                player.sendMessage("§cNo Space to reset.");
                                return;
                            }
                            Plugin plugin = DMain.getInstance();

                            player.sendMessage("§aResetting space ...");

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                SimpleDungeonBuilderBukkit dungeonBuilder = new SimpleDungeonBuilderBukkit(plugin,
                                        player.getWorld());
                                Set<Vector3Int> decoBlocks = last3DDungeonGenerator.getDecorationBlocks().stream()
                                        .flatMap(Collection::stream).map(DungeonBlock::getPos)
                                        .collect(Collectors.toSet());
                                dungeonBuilder.resetSpace(lastGeneratedSpace, () -> {
                                }, decoBlocks, last3DDungeonGenerator.getCorridorFloor(),
                                        last3DDungeonGenerator.getWallPositions());
                                lastGeneratedSpace = null;
                                last3DDungeonGenerator = null;
                            });
                        }));
    }

    /**
     * Finds a safe spawn location within the given BoundingBox, preferably on the
     * floor level. Iterates through potential spots near the center and checks for
     * air/solid blocks to ensure safety.
     */
    private Location findSafeSpawnLocation(World world, BoundingBox box) {
        // Start checking from a point slightly above the expected floor level (Y=1)
        double startX = box.getMinPoint().getX() + 2;
        double startZ = box.getMinPoint().getZ() + 2;
        double safeY = Math.max(64, box.getMinPoint().getY() - 1); // Ensure we are at least one
                                                                   // block above the lowest point

        // Simple iterative search: check a grid pattern near the center of the room
        int maxAttempts = 50;
        for (int i = 0; i < maxAttempts; i++) {
            double x = startX + (Math.random() * (box.getDimensions().getX() - box.getMinPoint().getX()));
            double z = startZ + (Math.random() * (box.getDimensions().getZ() - box.getMinPoint().getZ()));
            Location testLoc = new Location(world, x, safeY, z);

            // Check if the location is within the bounding box and on solid ground
            if (testLoc.getX() >= box.getMinPoint().getX() && testLoc.getX() <= box.getMaxPoint().getX()
                    && testLoc.getZ() >= box.getMinPoint().getZ() && testLoc.getZ() <= box.getMaxPoint().getZ()) {

                Block blockBelow = world.getBlockAt((int) Math.round(x), (int) Math.floor(safeY - 1),
                        (int) Math.round(z));
                // Check if the spot is clear (air) and has solid ground below it
                if (blockBelow.getType() == Material.AIR
                        && !isBlockAir(new Vector3Int((int) x, (int) safeY - 1, (int) z))) {
                    // Found a safe spot on the floor level
                    return testLoc;
                }
            }
        }

        // Fallback: return the center location if no safe spot is found after attempts
        Location fallback = new Location(world, box.get3DCenter().getX(), Math.max(64, box.getMinPoint().getY() + 1),
                box.get3DCenter().getZ());
        return fallback;
    }
}
