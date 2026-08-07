package dev.bukkit.game.dungeon.proceduralDungeon;

import dev.core.game.dungeon.proceduralDungeon.util.Direction3D;
import dev.core.game.dungeon.proceduralDungeon.util.Vector3Int;
import dev.core.game.dungeon.proceduralDungeon.util.dungeonBlocks.*;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;

import java.util.*;
import java.util.stream.Collectors;

public class DungeonMaterialsManagerBukkit extends DungeonMaterialsManager<Material> {

    private final World world;

    private Map<Material, Integer> materialUsedMap = new HashMap<>();
    private Map<Material, Integer> floorMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> stairMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> wallMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> ceilingMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> floorDecorationMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> wallDecorationMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> ceilingDecorationMaterialUsedMap = new HashMap<>();
    private Map<Material, Integer> cornerDecorationMaterialUsedMap = new HashMap<>();

    public DungeonMaterialsManagerBukkit(long seed, World world) {
        super(seed);
        this.world = world;
        System.out.println("DungeonMaterialsManagerBukkit seed: " + seed);
    }

    private final float waterLoggedSlabsStairsProbability = 0.5F;

    @Override
    public List<DungeonMaterial<Material>> getDungeonFloorBlockMaterials() {
        return List.of(
                DungeonMaterial.of(0.8F, DungeonMaterial.of(0.75F, Material.STONE_BRICKS), DungeonMaterial.of(0.25F, Material.STONE)),
                DungeonMaterial.of(0.17F, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.CRACKED_STONE_BRICKS,  Material.MOSSY_STONE_BRICKS, Material.TUFF_BRICKS, Material.ANDESITE),
                DungeonMaterial.of(0.01F, Material.STONE_BRICK_SLAB, Material.STONE_SLAB, Material.COBBLESTONE_SLAB, Material.MOSSY_COBBLESTONE_SLAB, Material.MOSSY_STONE_BRICK_SLAB, Material.TUFF_BRICK_SLAB, Material.ANDESITE_SLAB),
                DungeonMaterial.of(0.02F, Material.STONE_BRICK_STAIRS, Material.STONE_STAIRS, Material.COBBLESTONE_STAIRS, Material.MOSSY_COBBLESTONE_STAIRS, Material.MOSSY_STONE_BRICK_STAIRS, Material.TUFF_BRICK_STAIRS, Material.ANDESITE_STAIRS)
                );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonStairBlockMaterials() {
        return List.of(
                DungeonMaterial.of(0.8F, DungeonMaterial.of(0.75F, Material.STONE_BRICK_STAIRS), DungeonMaterial.of(0.25F, Material.STONE_STAIRS)),
                DungeonMaterial.of(0.2F, Material.COBBLESTONE_STAIRS, Material.MOSSY_COBBLESTONE_STAIRS, Material.MOSSY_STONE_BRICK_STAIRS, Material.TUFF_BRICK_STAIRS, Material.ANDESITE_STAIRS)
        );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonWallBlockMaterials() {
        return List.of(
                DungeonMaterial.of(0.8F, Material.STONE_BRICKS, Material.STONE, Material.COBBLESTONE),
                DungeonMaterial.of(0.15F, Material.MOSSY_COBBLESTONE, Material.MOSSY_STONE_BRICKS, Material.TUFF_BRICKS, Material.ANDESITE, Material.CRACKED_STONE_BRICKS),
                DungeonMaterial.of(0.03F, getDungeonLightBlockMaterials().toArray(DungeonMaterial[]::new))
                // maybe also Material.GRAVEL
        );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonCeilingBlockMaterials() {
        return List.of(
                DungeonMaterial.of(0.7F, Material.STONE_BRICKS, Material.STONE),
                DungeonMaterial.of(0.28F, Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.MOSSY_STONE_BRICKS, Material.TUFF_BRICKS, Material.ANDESITE, Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS),
                DungeonMaterial.of(0.02F, getDungeonLightBlockMaterials().toArray(DungeonMaterial[]::new))
        );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonLightBlockMaterials() {
        return List.of(
                DungeonMaterial.of(1, Material.GLOWSTONE, Material.SEA_LANTERN, Material.SHROOMLIGHT, Material.OCHRE_FROGLIGHT, Material.VERDANT_FROGLIGHT)
                // maybe also : Material.REDSTONE_LAMP, Material.WAXED_WEATHERED_COPPER_BULB, Material.WAXED_EXPOSED_COPPER_BULB
        );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonFloorDecorationBlockMaterials() {
        return List.of(
                DungeonDecorationMaterial.of(DungeonDecorationType.SPREADING, 1F, Material.MOSS_CARPET, Material.PALE_MOSS_CARPET, Material.LEAF_LITTER),
                DungeonDecorationMaterial.of(0.75F, Material.DEAD_BUSH, Material.SHORT_DRY_GRASS, Material.SHORT_GRASS, Material.BUSH, Material.FIREFLY_BUSH, Material.DEAD_BUBBLE_CORAL, Material.DEAD_BRAIN_CORAL),
                DungeonDecorationMaterial.of(0.25F, Material.COBWEB)
        );
    }

    // the placeProbability between different DungeonDecorationTypes is ignored when choosing a DungeonDecorationMaterial, therefore when there aren't multiple DungeonDecorationMaterials of the same type it can be choosen arbitrarily

    @Override
    public List<DungeonMaterial<Material>> getDungeonWallDecorationBlockMaterials() {
        return List.of(
                DungeonDecorationMaterial.of(1F, Material.TORCH, Material.REDSTONE_TORCH),
                DungeonDecorationMaterial.of(DungeonDecorationType.SPREADING, 1F, DungeonMaterial.of(0.4F, Material.VINE), DungeonMaterial.of(0.3F, Material.GLOW_LICHEN), DungeonMaterial.of(0.3F, Material.IRON_BARS))
//                DungeonDecorationMaterial.of(DungeonDecorationType.SPREADING, 0.2F, Material.IRON_BARS) // not sure
        );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonCeilingDecorationBlockMaterials() {
        return List.of(
                DungeonDecorationMaterial.of(DungeonDecorationType.HANGING, 0.3F, Material.PALE_HANGING_MOSS),
                DungeonDecorationMaterial.of(DungeonDecorationType.HANGING, 0.3F, Material.CAVE_VINES),
                DungeonDecorationMaterial.of(DungeonDecorationType.HANGING,0.15F, Material.COBWEB),
                DungeonDecorationMaterial.of(DungeonDecorationType.HANGING,0.25F, Material.CHAIN) // + lantern
                // maybe also Material.HANGING_ROOTS
        );
    }

    @Override
    public List<DungeonMaterial<Material>> getDungeonRoomCornerDecorationBlockMaterials() {
        return List.of(
                DungeonDecorationMaterial.of(DungeonDecorationType.BULK,0.5F, Material.ACACIA_LEAVES, Material.OAK_LEAVES, Material.AZALEA_LEAVES, Material.PALE_OAK_LEAVES),
                DungeonDecorationMaterial.of(DungeonDecorationType.BULK, 0.2F, Material.MOSS_BLOCK),
                DungeonDecorationMaterial.of(DungeonDecorationType.BULK, 0.3F, Material.COBWEB)
        );
    }

//    int spongesPlaced = 0;

    @Override
    public void placeBlock(DungeonBlock dungeonBlock) {
        Material material = getMaterial(dungeonBlock);
        placeBlock(dungeonBlock, material);
    }

    public void placeBlocksWithSameMaterial(LinkedHashSet<? extends DungeonBlock> dungeonBlocks) {
        Material material = getMaterial(dungeonBlocks.getFirst());
        for (var dungeonBlock : dungeonBlocks) {
            placeBlock(dungeonBlock, material);
        }
    }

    private void placeBlock(DungeonBlock dungeonBlock, Material material) {
        materialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);

        if (dungeonBlock instanceof DungeonFloorBlock) floorMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
        if (dungeonBlock instanceof DungeonStairBlock) stairMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
        if (dungeonBlock instanceof DungeonWallBlock) wallMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
        if (dungeonBlock instanceof DungeonCeilingBlock) ceilingMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);

        Vector3Int pos = dungeonBlock.getPos();
        Block block = world.getBlockAt(pos.x, pos.y, pos.z);

//        if (material == Material.GRAVEL && world.getBlockAt(pos.x, pos.y - 1, pos.z).getType() == Material.AIR) {
//            world.getBlockAt(pos.x, pos.y - 1, pos.z).setType(Material.SPONGE);
//            spongesPlaced++;
//        }

        block.setType(material);
        if (block.getBlockData() instanceof Stairs stair) {
            if (dungeonBlock instanceof DungeonStairBlock stairBlock) {
                stair.setFacing(BlockFace.valueOf(stairBlock.getFacingDirectionAsString()));
            } else if (dungeonBlock instanceof DungeonFloorBlock) {
                stair.setFacing(BlockFace.valueOf(Direction3D.getRandom2DCardinalDirection(random).name()));
                boolean waterlogged = random.nextFloat() < waterLoggedSlabsStairsProbability;
                stair.setWaterlogged(waterlogged);
                if (waterlogged) {
                    placeBlocksAroundBlock(block);
                }
            }
            block.setBlockData(stair);
        }
        if (block.getBlockData() instanceof Slab slab) {
            if (dungeonBlock instanceof DungeonFloorBlock) {
                boolean waterlogged = random.nextFloat() < waterLoggedSlabsStairsProbability;
                slab.setWaterlogged(waterlogged);
                if (waterlogged) {
                    placeBlocksAroundBlock(block);
                }
            }
            block.setBlockData(slab);
        }

        if (dungeonBlock instanceof DungeonDecorationBlock decorationBlock) {
            switch (decorationBlock.getPlacementType()) {
                case FLOOR -> {
                    floorDecorationMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
                    if (block.getBlockData() instanceof Waterlogged waterlogged) {
                        waterlogged.setWaterlogged(false);
                        block.setBlockData(waterlogged);
                    }
                }
                case WALL -> {
                    wallDecorationMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
                    if (block.getBlockData() instanceof MultipleFacing multipleFacing) {
                        multipleFacing.setFace(BlockFace.valueOf(decorationBlock.getFacingDirectionAsString()).getOppositeFace(), true);
                        block.setBlockData(multipleFacing);
                    }
                }
                case CEILING -> {
                    ceilingDecorationMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
                }
                case CORNER -> {
                    cornerDecorationMaterialUsedMap.compute(material, (mat, used) -> used == null ? 1 : used + 1);
                }
            }
        }
    }

    private void placeBlocksAroundBlock(Block block) {
//        if (block.getBlockData() instanceof Waterlogged waterlogged && !waterlogged.isWaterlogged()) return;
//        System.out.println("placeBlocksAroundBlock: " + block + " -> " + ((Waterlogged) block.getBlockData()).isWaterlogged());
        List<BlockFace> faces = List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
        for (var face : faces) {
            Block b = block.getRelative(face);
            if (b.getType() == Material.AIR) {
                b.setType(Material.TARGET);
            }
        }
    }

    public void printStatistic() {
        System.out.println();
        float totalCount = materialUsedMap.values().stream().mapToInt(i -> i).sum();
        System.out.println("\t\t\t\t\tMaterial Placement Statistics:");
        System.out.println("\t\t\t\tTotal Statistics: " + (int) totalCount + " placed in total");
        int longestStringLength = materialUsedMap.keySet().stream().mapToInt(mat -> mat.toString().length()).max().orElse(10);
        for (var material : materialUsedMap.keySet().stream().sorted(Comparator.comparing(mat -> materialUsedMap.get(mat)).reversed()).collect(Collectors.toCollection(LinkedHashSet::new))) {
            int used = materialUsedMap.get(material);
            int extraSpaces = longestStringLength - material.toString().length();
            System.out.println(material + ":" + " ".repeat(Math.max(0, extraSpaces)) + "\t\t" + used + " -> " + String.format("%.2f", ((used/totalCount) * 100)) + "%");
        }
        System.out.println();

        printMaterialList(longestStringLength, getDungeonFloorBlockMaterials(), floorMaterialUsedMap, "Dungeon Floor Statistics");
        printMaterialList(longestStringLength, getDungeonStairBlockMaterials(), stairMaterialUsedMap, "Dungeon Stair Statistics");
        printMaterialList(longestStringLength, getDungeonWallBlockMaterials(), wallMaterialUsedMap, "Dungeon Wall Statistics");
        printMaterialList(longestStringLength, getDungeonCeilingBlockMaterials(), ceilingMaterialUsedMap, "Dungeon Ceiling Statistics");
        printMaterialList(longestStringLength, getDungeonFloorDecorationBlockMaterials(), floorDecorationMaterialUsedMap, "Dungeon Floor Decoration Statistics");
        printMaterialList(longestStringLength, getDungeonWallDecorationBlockMaterials(), wallDecorationMaterialUsedMap, "Dungeon Wall Decoration Statistics");
        printMaterialList(longestStringLength, getDungeonCeilingDecorationBlockMaterials(), ceilingDecorationMaterialUsedMap, "Dungeon Ceiling Decoration Statistics");
        printMaterialList(longestStringLength, getDungeonRoomCornerDecorationBlockMaterials(), cornerDecorationMaterialUsedMap, "Dungeon Corner Decoration Statistics");

//        System.out.println("spongesPlaced: " + spongesPlaced);
    }

    private void printMaterialList(int longestStringLength, List<DungeonMaterial<Material>> dungeonMaterials, Map<Material, Integer> materialUsedMap, String title) {
        float totalCount = dungeonMaterials.stream().flatMap(d -> d.getMaterials().stream()).mapToInt(mat -> materialUsedMap.getOrDefault(mat, 0)).sum();
        System.out.println("\t\t" + title + ": " + (int) totalCount + " placed in total");
        for (var i = 0; i < dungeonMaterials.size(); i++) {
            DungeonMaterial<Material> material = dungeonMaterials.get(i);
            int count = material.getMaterials().stream().mapToInt(mat -> materialUsedMap.getOrDefault(mat, 0)).sum();
            System.out.println((i+1) + ")\tCount: " + count + " -> " + String.format("%.2f", ((count/totalCount) * 100)) + "%" + "\twith average of: " + String.format("%.2f", ((count/totalCount) * 100)/material.getMaterials().size()) + "%" + " per Material");
            for (var mat : material.getMaterials().stream().sorted(Comparator.comparing(mat -> materialUsedMap.getOrDefault(mat, 0)).reversed()).toList()) {
                int used = materialUsedMap.getOrDefault(mat, 0);
                int extraSpaces = longestStringLength - mat.toString().length();
                int extraSpacesForUsed = String.valueOf(((int) totalCount)).length() - String.valueOf(used).length();
                System.out.println("\t\t\t" + mat + ":" + " ".repeat(Math.max(0, extraSpaces)) + "\t" + used + " ".repeat(Math.max(0, extraSpacesForUsed)) + " -> " + String.format("%.2f", ((used/totalCount) * 100)) + "%");
            }
        }
        System.out.println();
    }
}
