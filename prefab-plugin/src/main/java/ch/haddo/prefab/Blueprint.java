package ch.haddo.prefab;

import org.bukkit.Bukkit;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.block.data.type.Fence;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.GlassPane;
import org.bukkit.block.structure.StructureRotation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A structure in local coordinates.
 * <p>
 * Axes follow world directions as seen by a player looking SOUTH at the build site:
 * x grows to the EAST, y grows UP, z grows to the SOUTH (away from the player).
 * The front of the structure (the side facing the player, where the door goes) is z = 0.
 * Block data is written with world facings for that orientation and gets rotated on placement.
 */
public final class Blueprint {

    public record Pos(int x, int y, int z) {
    }

    private static final Map<String, BlockData> PARSED = new HashMap<>();
    private static final BlockFace[] SIDES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    private final int width;
    private final int height;
    private final int depth;
    private final Map<Pos, BlockData> blocks = new LinkedHashMap<>();

    public Blueprint(int width, int height, int depth) {
        this.width = width;
        this.height = height;
        this.depth = depth;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int depth() {
        return depth;
    }

    public Map<Pos, BlockData> blocks() {
        return blocks;
    }

    static BlockData data(String spec) {
        return PARSED.computeIfAbsent(spec, Bukkit::createBlockData).clone();
    }

    public Blueprint set(int x, int y, int z, String spec) {
        return set(x, y, z, data(spec));
    }

    public Blueprint set(int x, int y, int z, BlockData data) {
        if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= depth) {
            throw new IllegalArgumentException("Block outside blueprint: " + x + "," + y + "," + z);
        }
        if (data.getMaterial().isAir()) {
            blocks.remove(new Pos(x, y, z));
        } else {
            blocks.put(new Pos(x, y, z), data);
        }
        return this;
    }

    /** Fills the whole cuboid. */
    public Blueprint fill(int x1, int y1, int z1, int x2, int y2, int z2, String spec) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, spec);
                }
            }
        }
        return this;
    }

    /** Fills only the vertical outer ring of the cuboid (walls without floor and ceiling). */
    public Blueprint walls(int x1, int y1, int z1, int x2, int y2, int z2, String spec) {
        int minX = Math.min(x1, x2), maxX = Math.max(x1, x2), minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);
        for (int x = minX; x <= maxX; x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (x == minX || x == maxX || z == minZ || z == maxZ) {
                        set(x, y, z, spec);
                    }
                }
            }
        }
        return this;
    }

    /** Lets fences and glass panes connect to their neighbours, like a player would have placed them. */
    public Blueprint connect() {
        for (Map.Entry<Pos, BlockData> entry : blocks.entrySet()) {
            if (!(entry.getValue() instanceof Fence) && !(entry.getValue() instanceof GlassPane)) {
                continue;
            }
            MultipleFacing data = (MultipleFacing) entry.getValue();
            Pos p = entry.getKey();
            for (BlockFace face : SIDES) {
                BlockData neighbour = blocks.get(new Pos(p.x() + face.getModX(), p.y(), p.z() + face.getModZ()));
                boolean join = neighbour != null && (neighbour instanceof Fence || neighbour instanceof GlassPane
                        || neighbour instanceof Gate || neighbour.getMaterial().isOccluding());
                if (data.getAllowedFaces().contains(face)) {
                    data.setFace(face, join);
                }
            }
        }
        return this;
    }

    /** Returns a copy rotated clockwise (seen from above) in steps of 90 degrees. */
    public Blueprint rotatedClockwise(int quarterTurns) {
        int turns = Math.floorMod(quarterTurns, 4);
        Blueprint result = this;
        for (int i = 0; i < turns; i++) {
            Blueprint next = new Blueprint(result.depth, result.height, result.width);
            for (Map.Entry<Pos, BlockData> entry : result.blocks.entrySet()) {
                Pos p = entry.getKey();
                BlockData data = entry.getValue().clone();
                data.rotate(StructureRotation.CLOCKWISE_90);
                // clockwise: north side becomes east side -> (x, z) -> (depth - 1 - z, x)
                next.blocks.put(new Pos(result.depth - 1 - p.z(), p.y(), p.x()), data);
            }
            result = next;
        }
        return turns == 0 ? copy() : result;
    }

    public Blueprint copy() {
        Blueprint copy = new Blueprint(width, height, depth);
        blocks.forEach((pos, data) -> copy.blocks.put(pos, data.clone()));
        return copy;
    }
}
