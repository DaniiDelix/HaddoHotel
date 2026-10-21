package ch.haddo.wands;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Works out which empty blocks a wand click would fill. */
final class Selection {

    private static final BlockFace[] AXES = {BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private Selection() {
    }

    /** Whether a block can be copied at all: it must be an item and a single block (no doors, beds, tall plants). */
    static boolean copyable(Block block) {
        Material type = block.getType();
        BlockData data = block.getBlockData();
        if (type.isAir() || !type.isItem() || block.isReplaceable() || block.isLiquid()) {
            return false;
        }
        if (data instanceof Door || data instanceof Bed) {
            return false;
        }
        return !(data instanceof Bisected) || data instanceof Stairs || data instanceof TrapDoor;
    }

    /**
     * @param clicked the block the player right-clicked; new blocks are copies of it
     * @param face    the clicked side; new blocks go on this side of the supporting blocks
     * @param look    the player's view direction, used by LINE mode
     * @param max     how many blocks at most
     */
    static List<Block> compute(Block clicked, BlockFace face, WandMode mode, Vector look, int max) {
        List<Block> result = new ArrayList<>();
        if (max <= 0 || !face.isCartesian() || !copyable(clicked)) {
            return result;
        }
        Material type = clicked.getType();
        List<BlockFace> directions = new ArrayList<>();
        for (BlockFace axis : AXES) {
            if (axis != face && axis != face.getOppositeFace()) {
                directions.add(axis);
            }
        }
        if (mode == WandMode.LINE) {
            BlockFace best = directions.getFirst();
            double bestDot = -2;
            for (BlockFace d : directions) {
                double dot = Math.abs(look.dot(d.getDirection()));
                if (dot > bestDot) {
                    bestDot = dot;
                    best = d;
                }
            }
            directions = List.of(best, best.getOppositeFace());
        }

        Deque<Block> queue = new ArrayDeque<>();
        Set<Block> visited = new HashSet<>();
        queue.add(clicked);
        visited.add(clicked);
        int scanLimit = Math.max(64, max * 8);
        while (!queue.isEmpty() && result.size() < max && visited.size() < scanLimit) {
            Block support = queue.poll();
            Block target = support.getRelative(face);
            if (!free(target)) {
                continue; // blocked: do not spread past this point
            }
            result.add(target);
            if (mode == WandMode.SINGLE) {
                break;
            }
            for (BlockFace d : directions) {
                Block next = support.getRelative(d);
                if (visited.add(next) && supports(next, type, mode)) {
                    queue.add(next);
                }
            }
        }
        return result;
    }

    private static boolean supports(Block block, Material type, WandMode mode) {
        if (mode == WandMode.SURFACE_SAME) {
            return block.getType() == type;
        }
        return !block.getType().isAir() && !block.isReplaceable() && !block.isLiquid();
    }

    private static boolean free(Block block) {
        if (block.getY() < block.getWorld().getMinHeight() || block.getY() >= block.getWorld().getMaxHeight()) {
            return false;
        }
        if (!block.getType().isAir() && !block.isReplaceable()) {
            return false;
        }
        BoundingBox box = BoundingBox.of(block);
        for (Entity entity : block.getWorld().getNearbyEntities(box)) {
            if (entity instanceof LivingEntity) {
                return false;
            }
        }
        return true;
    }
}
