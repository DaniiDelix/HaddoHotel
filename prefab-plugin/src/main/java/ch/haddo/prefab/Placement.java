package ch.haddo.prefab;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Where and how a prefab would be built: the list of block changes plus the first problem found, if any. */
final class Placement {

    record Change(Block block, BlockData data, boolean structure) {
    }

    private static final BlockData AIR = Material.AIR.createBlockData();

    final PrefabType type;
    final Block ground;
    final BlockFace facing;
    final List<Change> changes = new ArrayList<>();
    final List<Change> preview = new ArrayList<>();
    String problem;

    private Placement(PrefabType type, Block ground, BlockFace facing) {
        this.type = type;
        this.ground = ground;
        this.facing = facing;
    }

    boolean sameSpot(Placement other) {
        return other != null && type.id().equals(other.type.id()) && facing == other.facing && ground.equals(other.ground);
    }

    /**
     * @param ground the block the player is looking at; the structure stands on top of it,
     *               with its front centred on it and extending away from the player
     */
    static Placement compute(PrefabPlugin plugin, PrefabType type, Block ground, BlockFace facing) {
        Placement placement = new Placement(type, ground, facing);
        Blueprint bp = type.blueprint();
        World world = ground.getWorld();
        int turns = PrefabRegistry.quarterTurnsFromSouth(facing);
        StructureRotation rotation = switch (turns) {
            case 1 -> StructureRotation.CLOCKWISE_90;
            case 2 -> StructureRotation.CLOCKWISE_180;
            case 3 -> StructureRotation.COUNTERCLOCKWISE_90;
            default -> StructureRotation.NONE;
        };
        int baseY = ground.getY() + 1;
        if (ground.getY() < world.getMinHeight() || baseY + bp.height() > world.getMaxHeight()) {
            placement.problem = plugin.msg("motivo-altezza");
            return placement;
        }

        boolean clearTerrain = plugin.getConfig().getBoolean("costruzione.rimuovi-terreno-naturale", true);
        int centre = (bp.width() - 1) / 2;
        Map<Blueprint.Pos, BlockData> blocks = bp.blocks();
        List<Change> clearing = new ArrayList<>();
        List<Change> building = new ArrayList<>();
        BoundingBox box = null;
        for (int y = 0; y < bp.height(); y++) {
            for (int x = 0; x < bp.width(); x++) {
                for (int z = 0; z < bp.depth(); z++) {
                    int[] offset = rotate(x - centre, z, turns);
                    Block block = world.getBlockAt(ground.getX() + offset[0], baseY + y, ground.getZ() + offset[1]);
                    BoundingBox cell = BoundingBox.of(block);
                    box = box == null ? cell : box.union(cell);
                    BlockData data = blocks.get(new Blueprint.Pos(x, y, z));
                    if (placement.problem == null && !canClear(block, clearTerrain)) {
                        placement.problem = plugin.msg("motivo-ostacolo").replace("{blocco}", pretty(block.getType()));
                    }
                    if (data != null) {
                        BlockData rotated = data.clone();
                        rotated.rotate(rotation);
                        Change change = new Change(block, rotated, true);
                        building.add(change);
                        placement.preview.add(change);
                    } else if (!block.getType().isAir()) {
                        clearing.add(new Change(block, AIR, false));
                    }
                }
            }
        }

        // foundation: fill holes under the footprint so the structure does not float
        BlockData foundation = foundationData(plugin);
        int maxDepth = plugin.getConfig().getInt("costruzione.profondita-max-fondamenta", 6);
        List<Change> base = new ArrayList<>();
        for (int x = 0; x < bp.width() && placement.problem == null; x++) {
            for (int z = 0; z < bp.depth(); z++) {
                int[] offset = rotate(x - centre, z, turns);
                Block below = world.getBlockAt(ground.getX() + offset[0], baseY - 1, ground.getZ() + offset[1]);
                int depth = 0;
                while (below.getY() >= world.getMinHeight() && (below.getType().isAir() || below.isReplaceable())) {
                    if (++depth > maxDepth) {
                        placement.problem = plugin.msg("motivo-fondamenta");
                        break;
                    }
                    base.add(new Change(below, foundation, false));
                    below = below.getRelative(BlockFace.DOWN);
                }
            }
        }

        if (placement.problem == null && box != null) {
            for (Player player : world.getNearbyPlayers(box.getCenter().toLocation(world),
                    box.getWidthX() / 2 + 1, box.getHeight() / 2 + 1, box.getWidthZ() / 2 + 1)) {
                if (player.getBoundingBox().overlaps(box)) {
                    placement.problem = plugin.msg("motivo-giocatore");
                    break;
                }
            }
        }

        placement.changes.addAll(base);
        placement.changes.addAll(clearing);
        placement.changes.addAll(building);
        return placement;
    }

    /** Rotates a local (x, z) offset clockwise in quarter turns: south -> west -> north -> east. */
    static int[] rotate(int x, int z, int turns) {
        for (int i = 0; i < turns; i++) {
            int t = x;
            x = -z;
            z = t;
        }
        return new int[]{x, z};
    }

    private static boolean canClear(Block block, boolean clearTerrain) {
        Material type = block.getType();
        if (type.isAir() || block.isReplaceable()) {
            return true;
        }
        if (Tag.FLOWERS.isTagged(type) || Tag.SAPLINGS.isTagged(type) || type == Material.SWEET_BERRY_BUSH) {
            return true;
        }
        return clearTerrain && (Tag.DIRT.isTagged(type) || Tag.SAND.isTagged(type) || Tag.BASE_STONE_OVERWORLD.isTagged(type)
                || Tag.LEAVES.isTagged(type) || type == Material.GRAVEL || type == Material.SNOW_BLOCK
                || type == Material.CLAY || type == Material.SANDSTONE);
    }

    private static BlockData foundationData(PrefabPlugin plugin) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("costruzione.materiale-fondamenta", "COBBLESTONE"));
        if (material == null || !material.isBlock() || material.isAir()) {
            material = Material.COBBLESTONE;
        }
        return material.createBlockData();
    }

    static String pretty(Material material) {
        return material.getKey().getKey().replace('_', ' ');
    }
}
