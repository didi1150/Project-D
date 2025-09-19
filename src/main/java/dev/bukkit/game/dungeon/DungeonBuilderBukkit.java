package dev.bukkit.game.dungeon;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.RedstoneWallTorch;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import dev.core.game.coords.Point3D;
import dev.core.game.dungeon.DecorationElement;
import dev.core.game.dungeon.DecorationType;
import dev.core.game.dungeon.Direction;
import dev.core.game.dungeon.Dungeon;
import dev.core.game.dungeon.DungeonRoom;
import dev.core.game.dungeon.DungeonTunnel;
import dev.core.game.dungeon.EndPortalRoom;

public class DungeonBuilderBukkit {

    private final Plugin plugin;
    private final World world;
    private final Random random;

    // Material palettes for variety
    private final List<Material> floorMaterials;
    private final List<Material> wallMaterials;
    private final List<Material> roofMaterials;
    private final List<Material> glowingWallMaterials;
    private final List<Material> portalPlatformMaterials;

    // Decoration material mappings
    private final Map<String, Material> decorationMaterials;

    // Lighting configuration
    private final boolean useGlowingBlocks;
    private final boolean useWallTorches;
    private final int lightingDensity; // 1-10, higher = more lights

    // Texture variation percentages
    private final double wallVariationChance;
    private final double floorVariationChance;
    private final double roofVariationChance;
    private final double glowingBlockChance;
    private final double portalPlatformVariationChance;

    public DungeonBuilderBukkit(Plugin plugin, World world) {
        this(plugin, world, createDefaultFloorMaterials(), createDefaultWallMaterials(), createDefaultRoofMaterials(),
                createDefaultGlowingMaterials(), createDefaultPortalPlatformMaterials(), true, true, 5);
    }

    public DungeonBuilderBukkit(Plugin plugin, World world, List<Material> floorMaterials, List<Material> wallMaterials,
            List<Material> roofMaterials, List<Material> glowingWallMaterials, List<Material> portalPlatformMaterials,
            boolean useGlowingBlocks, boolean useWallTorches, int lightingDensity) {
        this.plugin = plugin;
        this.world = world;
        this.random = new Random();
        this.floorMaterials = new ArrayList<>(floorMaterials);
        this.wallMaterials = new ArrayList<>(wallMaterials);
        this.roofMaterials = new ArrayList<>(roofMaterials);
        this.glowingWallMaterials = new ArrayList<>(glowingWallMaterials);
        this.portalPlatformMaterials = new ArrayList<>(portalPlatformMaterials);
        this.useGlowingBlocks = useGlowingBlocks;
        this.useWallTorches = useWallTorches;
        this.lightingDensity = Math.max(1, Math.min(10, lightingDensity));

        // Initialize decoration material mappings
        this.decorationMaterials = createDecorationMaterialMap();

        // Variation chances (how often to use non-primary materials)
        this.wallVariationChance = 0.25; // 25% chance for variety
        this.floorVariationChance = 0.15; // 15% chance for variety
        this.roofVariationChance = 0.20; // 20% chance for variety
        this.glowingBlockChance = 0.08; // 8% chance for glowing blocks
        this.portalPlatformVariationChance = 0.25; // 25% chance for variety
    }

    private static List<Material> createDefaultFloorMaterials() {
        return Arrays.asList(Material.STONE_BRICKS, // Primary
                Material.CRACKED_STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.COBBLESTONE,
                Material.MOSSY_COBBLESTONE);
    }

    private static List<Material> createDefaultPortalPlatformMaterials() {
        return Arrays.asList(Material.POLISHED_BLACKSTONE_BRICKS, // Primary
                Material.REDSTONE_BLOCK, Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.CRYING_OBSIDIAN,
                Material.BLACKSTONE_SLAB, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
    }

    private static List<Material> createDefaultWallMaterials() {
        return Arrays.asList(Material.COBBLESTONE, // Primary
                Material.MOSSY_COBBLESTONE, Material.STONE, Material.ANDESITE, Material.STONE_BRICKS,
                Material.CRACKED_STONE_BRICKS);
    }

    private static List<Material> createDefaultRoofMaterials() {
        return Arrays.asList(Material.STONE_BRICKS, // Primary
                Material.COBBLESTONE, Material.STONE, Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS);
    }

    private static List<Material> createDefaultGlowingMaterials() {
        return Arrays.asList(Material.GLOWSTONE, Material.SEA_LANTERN, Material.SHROOMLIGHT);
    }

    private Map<String, Material> createDecorationMaterialMap() {
        Map<String, Material> materials = new HashMap<>();

        // Vegetation
        materials.put("vine", Material.VINE);
        materials.put("oak_leaves", Material.OAK_LEAVES);
        materials.put("grass", Material.SHORT_GRASS);
        materials.put("fern", Material.FERN);
        materials.put("dead_bush", Material.DEAD_BUSH);
        materials.put("brown_mushroom", Material.BROWN_MUSHROOM);
        materials.put("red_mushroom", Material.RED_MUSHROOM);

        // Water features
        materials.put("water", Material.WATER);

        // Stone structures
        materials.put("stone_bricks", Material.STONE_BRICKS);
        materials.put("cobblestone", Material.COBBLESTONE);
        materials.put("chiseled_stone_bricks", Material.CHISELED_STONE_BRICKS);
        materials.put("mossy_cobblestone", Material.MOSSY_COBBLESTONE);

        // Decorative items
        materials.put("candle", Material.CANDLE);
        materials.put("barrel", Material.BARREL);
        materials.put("bookshelf", Material.BOOKSHELF);
        materials.put("oak_planks", Material.OAK_PLANKS);
        materials.put("cobweb", Material.COBWEB);

        // Prison and structural elements
        materials.put("iron_bars", Material.IRON_BARS);
        materials.put("chain", Material.CHAIN);

        // Glowing elements
        materials.put("glow_lichen", Material.GLOW_LICHEN);

        return materials;
    }

    public void resetDungeon(Dungeon dungeon, Runnable onComplete) {
        new BukkitRunnable() {
            private int blockIndex = 0;
            private final java.util.List<Point3D> allBlocks = new java.util.ArrayList<>();
            private int stage = 0; // 0 = collecting blocks, 1 = resetting blocks, 2 = complete

            {
                // Collect all blocks that need to be reset to stone
                allBlocks.addAll(dungeon.getAllFloorBlocks());
                allBlocks.addAll(dungeon.getAllWallBlocks());
                allBlocks.addAll(dungeon.getAllRoofBlocks());

                // Also reset air blocks to stone (fill in the entire dungeon)
                allBlocks.addAll(dungeon.getAllAirBlocks());

                // Reset decoration areas to stone as well
                for (DecorationElement decoration : dungeon.getAllDecorations()) {
                    allBlocks.addAll(decoration.getBlockTypes().keySet());
                }
            }

            @Override
            public void run() {
                int blocksPerTick = 2000; // Higher since we're just setting blocks to stone

                for (int i = 0; i < blocksPerTick; i++) {
                    if (stage == 0) { // Block resetting stage
                        if (blockIndex < allBlocks.size()) {
                            setBlockToStone(allBlocks.get(blockIndex));
                            blockIndex++;

                            // Progress updates
                            if (blockIndex % 5000 == 0 || blockIndex == allBlocks.size()) {
                                int percentage = (int) ((blockIndex / (double) allBlocks.size()) * 100);
                                Bukkit.broadcastMessage("§cResetting dungeon... " + percentage + "% complete");
                            }
                        } else {
                            stage = 1;
                        }
                    } else if (stage == 1) {
                        cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        Bukkit.broadcastMessage("§aDungeon reset complete! All blocks converted to stone.");
                        return;
                    }
                }
            }

            private void setBlockToStone(Point3D point) {
                Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
                block.setType(Material.STONE);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void buildDungeon(Dungeon dungeon, Runnable onComplete) {
        new BukkitRunnable() {
            private int blockIndex = 0;
            private final java.util.List<Point3D> floorBlocks = new java.util.ArrayList<>(dungeon.getAllFloorBlocks());
            private final java.util.List<Point3D> wallBlocks = new java.util.ArrayList<>(dungeon.getAllWallBlocks());
            private final java.util.List<Point3D> roofBlocks = new java.util.ArrayList<>(dungeon.getAllRoofBlocks());
            private final java.util.List<Point3D> airBlocks = new java.util.ArrayList<>(dungeon.getAllAirBlocks());
            private final java.util.List<DecorationElement> decorations = new java.util.ArrayList<>(
                    dungeon.getAllDecorations());
            private int decorationIndex = 0;
            private int stage = 0; // 0 = floor, 1 = walls, 2 = roof, 3 = air, 4 = decorations, 5 = lighting, 6 =
                                   // complete

            @Override
            public void run() {
                int blocksPerTick = 1000; // Adjust based on performance needs

                for (int i = 0; i < blocksPerTick; i++) {
                    if (stage == 0) { // Floor stage
                        if (blockIndex < floorBlocks.size()) {
                            Material floorMaterial = selectMaterial(floorMaterials, floorVariationChance);
                            setBlock(floorBlocks.get(blockIndex), floorMaterial);
                            blockIndex++;
                        } else {
                            stage = 1;
                            blockIndex = 0;
                            Bukkit.broadcastMessage("§a20% done - Floor complete");
                        }
                    } else if (stage == 1) { // Wall stage
                        if (blockIndex < wallBlocks.size()) {
                            Point3D wallPoint = wallBlocks.get(blockIndex);
                            Material wallMaterial = selectWallMaterial(wallPoint, dungeon);
                            setBlock(wallPoint, wallMaterial);
                            blockIndex++;
                        } else {
                            stage = 2;
                            blockIndex = 0;
                            Bukkit.broadcastMessage("§a40% done - Walls complete");
                        }
                    } else if (stage == 2) { // Roof stage
                        if (blockIndex < roofBlocks.size()) {
                            Material roofMaterial = selectMaterial(roofMaterials, roofVariationChance);
                            setBlock(roofBlocks.get(blockIndex), roofMaterial);
                            blockIndex++;
                        } else {
                            stage = 3;
                            blockIndex = 0;
                            Bukkit.broadcastMessage("§a60% done - Roof complete");
                        }
                    } else if (stage == 3) { // Air stage
                        if (blockIndex < airBlocks.size()) {
                            setBlock(airBlocks.get(blockIndex), Material.AIR);
                            blockIndex++;
                        } else {
                            stage = 4;
                            blockIndex = 0;
                            decorationIndex = 0;
                            Bukkit.broadcastMessage("§a75% done - Adding decorations...");
                        }
                    } else if (stage == 4) { // Decorations stage
                        if (decorationIndex < decorations.size()) {
                            // Place multiple blocks per decoration per tick for efficiency
                            int decorationsPerTick = Math.min(5, decorations.size() - decorationIndex);

                            for (int d = 0; d < decorationsPerTick; d++) {
                                placeDecoration(decorations.get(decorationIndex + d));
                            }

                            decorationIndex += decorationsPerTick;
                        } else {
                            stage = 5;
                            Bukkit.broadcastMessage("§a90% done - Adding lighting...");
                        }
                    } else if (stage == 5) { // Lighting stage
                        addLighting(dungeon);
                        stage = 6;
                    }

                    if (stage == 6) {
                        cancel();
                        if (onComplete != null) {
                            onComplete.run();
                        }
                        Bukkit.broadcastMessage("§aDungeon construction complete!");
                        return;
                    }
                }
            }

            private void setBlock(Point3D point, Material material) {
                Block block = world.getBlockAt(point.getX(), point.getY(), point.getZ());
                block.setType(material);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void placeDecoration(DecorationElement decoration) {
        DecorationType type = decoration.getType();
        Map<Point3D, String> blockTypes = decoration.getBlockTypes();

        for (Map.Entry<Point3D, String> entry : blockTypes.entrySet()) {
            Point3D position = entry.getKey();
            String blockTypeName = entry.getValue();

            Material material = decorationMaterials.get(blockTypeName);
            if (material == null) {
                // Fallback to stone if material not found
                material = Material.STONE;
                plugin.getLogger().warning("Unknown decoration block type: " + blockTypeName);
            }

            Block block = world.getBlockAt(position.getX(), position.getY(), position.getZ());

            // Handle special placement cases
            switch (type) {
            case WALL_VEGETATION:
                placeWallVegetation(block, material, position);
                break;
            case WALL_VINES:
                placeWallVines(block, material, position);
                break;
            case GLOWING_LICHEN:
                placeGlowingLichen(block, material, position);
                break;
            case WATER_FEATURE:
                placeWaterFeature(block, material, blockTypeName, position);
                break;
            case FOUNTAIN:
                placeFountain(block, material, blockTypeName, position, decoration);
                break;
            case PRISON_CELL:
                placePrisonCell(block, material, blockTypeName, position, decoration);
                break;
            case ALTAR:
                placeAltarBlock(block, material, blockTypeName, decoration);
                break;
            case BOOKSHELF_AREA:
                placeBookshelfBlock(block, material, blockTypeName, position, decoration);
                break;
            default:
                block.setType(material);
                break;
            }
        }
    }

    private void placeWallVegetation(Block block, Material material, Point3D position) {
        if (material == Material.VINE) {
            placeVineOnWall(block, position);
        } else {
            block.setType(material);
        }
    }

    private void placeWallVines(Block block, Material material, Point3D position) {
        if (material == Material.VINE) {
            placeVineOnWall(block, position);
        } else {
            block.setType(material);
        }
    }

    private void placeVineOnWall(Block block, Point3D position) {
        // Find adjacent wall for vine placement
        BlockFace attachedFace = null;

        for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType().isSolid()) {
                attachedFace = face;
                break;
            }
        }

        if (attachedFace != null) {
            block.setType(Material.VINE);

            if (block.getBlockData() instanceof MultipleFacing) {
                MultipleFacing vine = (MultipleFacing) block.getBlockData();
                vine.setFace(attachedFace, true);
                block.setBlockData(vine);
            }
        } else {
            // Fallback to leaves if no wall found
            block.setType(Material.OAK_LEAVES);
        }
    }

    private void placeGlowingLichen(Block block, Material material, Point3D position) {
        if (material == Material.GLOW_LICHEN) {
            // Find adjacent wall for lichen placement
            BlockFace attachedFace = null;

            for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST,
                    BlockFace.UP, BlockFace.DOWN)) {
                Block adjacent = block.getRelative(face);
                if (adjacent.getType().isSolid()) {
                    attachedFace = face;
                    break;
                }
            }

            if (attachedFace != null) {
                block.setType(Material.GLOW_LICHEN);

                // Set lichen direction data for 1.21
                if (block.getBlockData() instanceof MultipleFacing) {
                    MultipleFacing lichen = (MultipleFacing) block.getBlockData();
                    lichen.setFace(attachedFace, true);
                    block.setBlockData(lichen);
                }
            } else {
                // Fallback to glowstone if no wall found
                block.setType(Material.GLOWSTONE);
            }
        } else {
            block.setType(material);
        }
    }

    private void placeWaterFeature(Block block, Material material, String blockTypeName, Point3D position) {
        block.setType(material);

        // Handle waterlogged blocks if needed
        if (material == Material.WATER && block.getBlockData() instanceof Waterlogged) {
            Waterlogged waterlogged = (Waterlogged) block.getBlockData();
            waterlogged.setWaterlogged(true);
            block.setBlockData(waterlogged);
        }
    }

    private void placeFountain(Block block, Material material, String blockTypeName, Point3D position,
            DecorationElement decoration) {
        block.setType(material);

        // Handle special fountain water placement
        if (material == Material.WATER) {
            // Ensure water flows properly
            if (block.getBlockData() instanceof Waterlogged) {
                Waterlogged waterlogged = (Waterlogged) block.getBlockData();
                waterlogged.setWaterlogged(true);
                block.setBlockData(waterlogged);
            }
        }

        // Handle fountain decorative elements
        if (material == Material.CANDLE) {
            // Ensure candle is placed on solid block
            Block below = block.getRelative(BlockFace.DOWN);
            if (!below.getType().isSolid()) {
                return; // Don't place candle if no solid block below
            }
        }
    }

    private void placePrisonCell(Block block, Material material, String blockTypeName, Point3D position,
            DecorationElement decoration) {
        if (material == Material.IRON_BARS) {
            block.setType(Material.IRON_BARS);

            // Connect iron bars to adjacent bars or walls
            if (block.getBlockData() instanceof MultipleFacing) {
                MultipleFacing bars = (MultipleFacing) block.getBlockData();

                // Check all horizontal directions for connections
                for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
                    Block adjacent = block.getRelative(face);
                    if (adjacent.getType() == Material.IRON_BARS || adjacent.getType().isSolid()) {
                        bars.setFace(face, true);
                    }
                }

                block.setBlockData(bars);
            }
        } else if (material == Material.CHAIN) {
            block.setType(Material.CHAIN);

            // Set chain orientation (vertical by default)
            if (block.getBlockData() instanceof Directional) {
                Directional chain = (Directional) block.getBlockData();
                chain.setFacing(BlockFace.UP); // Chains hang vertically
                block.setBlockData(chain);
            }
        } else {
            block.setType(material);
        }
    }

    private void placeAltarBlock(Block block, Material material, String blockTypeName, DecorationElement decoration) {
        block.setType(material);

        // Handle candle placement on altar
        if (material == Material.CANDLE) {
            // Make sure candle is placed on solid block
            Block below = block.getRelative(BlockFace.DOWN);
            if (!below.getType().isSolid()) {
                // Don't place candle if no solid block below
                return;
            }
        }
    }

    private void placeBookshelfBlock(Block block, Material material, String blockTypeName, Point3D position,
            DecorationElement decoration) {
        block.setType(material);

        // Handle directional blocks like bookshelves facing towards the center
        if (block.getBlockData() instanceof Directional && material == Material.BOOKSHELF) {
            Directional directional = (Directional) block.getBlockData();
            Point3D center = decoration.getCenterPosition();

            // Calculate direction to face towards center
            int deltaX = center.getX() - position.getX();
            int deltaZ = center.getZ() - position.getZ();

            BlockFace facing = BlockFace.NORTH; // Default
            if (Math.abs(deltaX) > Math.abs(deltaZ)) {
                facing = deltaX > 0 ? BlockFace.EAST : BlockFace.WEST;
            } else {
                facing = deltaZ > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
            }

            if (directional.getFaces().contains(facing)) {
                directional.setFacing(facing);
                block.setBlockData(directional);
            }
        }
    }

    private Material selectMaterial(List<Material> materials, double variationChance) {
        if (materials.isEmpty())
            return Material.STONE;

        if (random.nextDouble() < variationChance && materials.size() > 1) {
            // Select a random non-primary material
            return materials.get(1 + random.nextInt(materials.size() - 1));
        } else {
            // Use primary material
            return materials.get(0);
        }
    }

    private Material selectWallMaterial(Point3D wallPoint, Dungeon dungeon) {
        // Check if this should be a glowing block for lighting
        if (useGlowingBlocks && shouldPlaceGlowingBlock(wallPoint, dungeon)) {
            return selectMaterial(glowingWallMaterials, 0.3); // 30% variation in glowing blocks
        }

        return selectMaterial(wallMaterials, wallVariationChance);
    }

    private boolean shouldPlaceGlowingBlock(Point3D wallPoint, Dungeon dungeon) {
        // Don't place glowing blocks too frequently
        if (random.nextDouble() > glowingBlockChance) {
            return false;
        }

        // Check if there's already a light source nearby
        int lightCheckRadius = 8;
        for (int x = wallPoint.getX() - lightCheckRadius; x <= wallPoint.getX() + lightCheckRadius; x++) {
            for (int z = wallPoint.getZ() - lightCheckRadius; z <= wallPoint.getZ() + lightCheckRadius; z++) {
                for (int y = wallPoint.getY() - 2; y <= wallPoint.getY() + 2; y++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (isLightSource(block.getType())) {
                        double distance = Math.sqrt(Math.pow(x - wallPoint.getX(), 2)
                                + Math.pow(z - wallPoint.getZ(), 2) + Math.pow(y - wallPoint.getY(), 2));
                        if (distance < lightCheckRadius) {
                            return false; // Too close to existing light
                        }
                    }
                }
            }
        }

        return true;
    }

    private boolean isLightSource(Material material) {
        return material == Material.TORCH || material == Material.WALL_TORCH || material == Material.GLOWSTONE
                || material == Material.SEA_LANTERN || material == Material.SHROOMLIGHT || material == Material.END_ROD
                || material == Material.REDSTONE_LAMP || material == Material.CANDLE
                || material == Material.GLOW_LICHEN;
    }

    private void addLighting(Dungeon dungeon) {
        // Add center torches in rooms
        for (DungeonRoom room : dungeon.getRooms()) {
            addRoomCenterLighting(room);
            if (room instanceof EndPortalRoom endPortalRoom) {
                buildEndPortal(endPortalRoom);
            }
            if (useWallTorches) {
                addWallTorches(room);
            }
        }

        // Add tunnel lighting
        for (DungeonTunnel tunnel : dungeon.getTunnels()) {
            addTunnelLighting(tunnel);
        }
    }

    private void addRoomCenterLighting(DungeonRoom room) {
        Point3D center = room.getCenter();

        // Try to place torch in center, or nearby if center is occupied
        for (int radius = 0; radius <= 2; radius++) {
            for (int xOffset = -radius; xOffset <= radius; xOffset++) {
                for (int zOffset = -radius; zOffset <= radius; zOffset++) {
                    if (Math.abs(xOffset) == radius || Math.abs(zOffset) == radius || radius == 0) {
                        Point3D torchPos = new Point3D(center.getX() + xOffset, center.getY() + 1,
                                center.getZ() + zOffset);
                        Block torchBlock = world.getBlockAt(torchPos.getX(), torchPos.getY(), torchPos.getZ());
                        Block groundBlock = world.getBlockAt(torchPos.getX(), torchPos.getY() - 1, torchPos.getZ());

                        if (torchBlock.getType() == Material.AIR && groundBlock.getType() != Material.AIR) {
                            torchBlock.setType(Material.TORCH);
                            return; // Only place one center torch
                        }
                    }
                }
            }
        }
    }

    private void buildEndPortal(EndPortalRoom room) {
        // 1. Place the portal frame
        for (Point3D framePos : room.getPortalStructure()) {
            Block frameBlock = world.getBlockAt(framePos.getX(), framePos.getY(), framePos.getZ());
            frameBlock.setType(Material.OBSIDIAN);
        }

        // 2. Place the ceremonial platform
        for (Point3D platformPos : room.getCeremonyArea()) {
            Block platformBlock = world.getBlockAt(platformPos.getX(), platformPos.getY(), platformPos.getZ());
            platformBlock.setType(selectMaterial(portalPlatformMaterials, portalPlatformVariationChance));
        }

        // 3. Light up the portal
        lightPortal(room.getPortalCenter());
    }

    private void lightPortal(Point3D portalCenter) {
        int portalWidth = 4;
        int portalHeight = 5;

        for (int x = -portalWidth / 2 + 1; x <= portalWidth / 2 - 1; x++) {
            for (int y = 1; y < portalHeight; y++) {
                Point3D portalPos = new Point3D(portalCenter.getX() + x, portalCenter.getY() + y, portalCenter.getZ());
                Block portalBlock = world.getBlockAt(portalPos.getX(), portalPos.getY(), portalPos.getZ());
                portalBlock.setType(Material.NETHER_PORTAL);
            }
        }
    }

    private void addWallTorches(DungeonRoom room) {
        Point3D center = room.getCenter();
        int roomSize = room.getSize();
        int torchSpacing = Math.max(4, roomSize / 3); // Adjust spacing based on room size

        // Place torches around room perimeter
        for (Direction dir : Direction.values()) {
            // Place multiple torches along each wall for larger rooms
            int torchCount = Math.max(1, roomSize / torchSpacing);

            for (int i = 0; i < torchCount; i++) {
                int offset = (i - torchCount / 2) * torchSpacing;
                Point3D basePos = dir.apply(center, roomSize / 3);

                Point3D torchWallPos;
                if (dir == Direction.NORTH || dir == Direction.SOUTH) {
                    torchWallPos = new Point3D(basePos.getX() + offset, basePos.getY(), basePos.getZ());
                } else {
                    torchWallPos = new Point3D(basePos.getX(), basePos.getY(), basePos.getZ() + offset);
                }

                placeLightingAtWall(torchWallPos, dir, 2); // Try up to 2 blocks high
            }
        }
    }

    private void addTunnelLighting(DungeonTunnel tunnel) {
        List<Point3D> centerPath = tunnel.getCenterPath();
        int lightSpacing = 6 + (10 - lightingDensity); // Higher density = smaller spacing

        for (int i = 0; i < centerPath.size(); i += lightSpacing) {
            Point3D pathPoint = centerPath.get(i);

            // Try to place torch on ceiling first, then walls
            Point3D ceilingPos = new Point3D(pathPoint.getX(), pathPoint.getY() + tunnel.getHeight() - 1,
                    pathPoint.getZ());
            Block ceilingBlock = world.getBlockAt(ceilingPos.getX(), ceilingPos.getY(), ceilingPos.getZ());
            Block airBelow = world.getBlockAt(ceilingPos.getX(), ceilingPos.getY() - 1, ceilingPos.getZ());

            if (ceilingBlock.getType() != Material.AIR && airBelow.getType() == Material.AIR) {
                // Place torch hanging from ceiling
                airBelow.setType(Material.TORCH);
            } else {
                // Try to place wall torch
                for (Direction dir : Direction.values()) {
                    Point3D wallCheckPos = dir.apply(pathPoint, tunnel.getWidth() / 2 + 1);
                    if (placeLightingAtWall(wallCheckPos, dir, 1)) {
                        break; // Successfully placed wall torch
                    }
                }
            }
        }
    }

    private boolean placeLightingAtWall(Point3D wallPos, Direction wallDirection, int maxHeight) {
        for (int y = 0; y < maxHeight; y++) {
            Point3D torchPos = new Point3D(wallPos.getX(), wallPos.getY() + y, wallPos.getZ());
            Point3D wallBehind = wallDirection.apply(torchPos, 1);

            Block airBlock = world.getBlockAt(torchPos.getX(), torchPos.getY(), torchPos.getZ());
            Block wallBlock = world.getBlockAt(wallBehind.getX(), wallBehind.getY(), wallBehind.getZ());

            if (airBlock.getType() == Material.AIR && wallBlock.getType() != Material.AIR
                    && !isLightSource(wallBlock.getType())) {
                try {
                    airBlock.setType(Material.REDSTONE_WALL_TORCH);
                    RedstoneWallTorch wallTorch = (RedstoneWallTorch) airBlock.getBlockData();
                    wallTorch.setFacing(getBukkitFacing(wallDirection.opposite()));
                    airBlock.setBlockData(wallTorch, true);
                    return true;
                } catch (Exception e) {
                    // Fallback to regular torch if wall torch fails
                    airBlock.setType(Material.TORCH);
                    return true;
                }
            }
        }
        return false;
    }

    private BlockFace getBukkitFacing(Direction direction) {
        switch (direction) {
        case NORTH:
            return BlockFace.NORTH;
        case SOUTH:
            return BlockFace.SOUTH;
        case EAST:
            return BlockFace.EAST;
        case WEST:
            return BlockFace.WEST;
        default:
            return BlockFace.NORTH;
        }
    }

    // Builder pattern for easy configuration
    public static class Builder {
        private Plugin plugin;
        private World world;
        private List<Material> floorMaterials = createDefaultFloorMaterials();
        private List<Material> wallMaterials = createDefaultWallMaterials();
        private List<Material> roofMaterials = createDefaultRoofMaterials();
        private List<Material> glowingWallMaterials = createDefaultGlowingMaterials();
        private List<Material> portalPlatformMaterials = createDefaultPortalPlatformMaterials();
        private boolean useGlowingBlocks = true;
        private boolean useWallTorches = true;
        private int lightingDensity = 5;

        public Builder(Plugin plugin, World world) {
            this.plugin = plugin;
            this.world = world;
        }

        public Builder floorMaterials(Material... materials) {
            this.floorMaterials = Arrays.asList(materials);
            return this;
        }

        public Builder wallMaterials(Material... materials) {
            this.wallMaterials = Arrays.asList(materials);
            return this;
        }

        public Builder roofMaterials(Material... materials) {
            this.roofMaterials = Arrays.asList(materials);
            return this;
        }

        public Builder glowingMaterials(Material... materials) {
            this.glowingWallMaterials = Arrays.asList(materials);
            return this;
        }

        public Builder portalPlatformMaterials(Material... materials) {
            this.portalPlatformMaterials = Arrays.asList(materials);
            return this;
        }

        public Builder useGlowingBlocks(boolean useGlowingBlocks) {
            this.useGlowingBlocks = useGlowingBlocks;
            return this;
        }

        public Builder useWallTorches(boolean useWallTorches) {
            this.useWallTorches = useWallTorches;
            return this;
        }

        public Builder lightingDensity(int density) {
            this.lightingDensity = Math.max(1, Math.min(10, density));
            return this;
        }

        public DungeonBuilderBukkit build() {
            return new DungeonBuilderBukkit(plugin, world, floorMaterials, wallMaterials, roofMaterials,
                    glowingWallMaterials, portalPlatformMaterials, useGlowingBlocks, useWallTorches, lightingDensity);
        }
    }
}