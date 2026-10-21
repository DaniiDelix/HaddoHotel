package ch.haddo.effortless;

import org.bukkit.World;
import org.bukkit.block.Block;

/** Block coordinates without a world. */
record Pos(int x, int y, int z) {

    static Pos of(Block block) {
        return new Pos(block.getX(), block.getY(), block.getZ());
    }

    Pos add(int dx, int dy, int dz) {
        return new Pos(x + dx, y + dy, z + dz);
    }

    Block block(World world) {
        return world.getBlockAt(x, y, z);
    }
}
