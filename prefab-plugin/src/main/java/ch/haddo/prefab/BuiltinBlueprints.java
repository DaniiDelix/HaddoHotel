package ch.haddo.prefab;

/**
 * The structures that ship with the plugin. Coordinates: x = west->east, y = up, z = front (north) -> back (south).
 */
final class BuiltinBlueprints {

    private BuiltinBlueprints() {
    }

    /** Small starter house: 7x7, gable roof, bed, chest, furnace, crafting table. */
    static Blueprint house() {
        Blueprint b = new Blueprint(7, 8, 7);
        // floor
        b.fill(0, 0, 0, 6, 0, 6, "cobblestone");
        b.fill(1, 0, 1, 5, 0, 5, "oak_planks");
        // walls with log corners
        b.walls(0, 1, 0, 6, 3, 6, "oak_planks");
        for (int y = 1; y <= 3; y++) {
            b.set(0, y, 0, "oak_log[axis=y]").set(6, y, 0, "oak_log[axis=y]")
                    .set(0, y, 6, "oak_log[axis=y]").set(6, y, 6, "oak_log[axis=y]");
        }
        // top beam
        b.walls(0, 4, 0, 6, 4, 6, "spruce_planks");
        // windows
        b.set(1, 2, 0, "glass_pane").set(5, 2, 0, "glass_pane")
                .set(0, 2, 3, "glass_pane").set(6, 2, 3, "glass_pane").set(3, 2, 6, "glass_pane");
        // door
        b.set(3, 1, 0, "oak_door[facing=north,half=lower,hinge=left]")
                .set(3, 2, 0, "oak_door[facing=north,half=upper,hinge=left]");
        // gable roof, ridge running front to back
        for (int z = 0; z <= 6; z++) {
            b.set(0, 5, z, "spruce_stairs[facing=east]").set(6, 5, z, "spruce_stairs[facing=west]");
            b.set(1, 6, z, "spruce_stairs[facing=east]").set(5, 6, z, "spruce_stairs[facing=west]");
            b.set(2, 7, z, "spruce_stairs[facing=east]").set(4, 7, z, "spruce_stairs[facing=west]");
            b.set(3, 7, z, "spruce_planks");
        }
        for (int z : new int[]{0, 6}) {
            b.fill(1, 5, z, 5, 5, z, "spruce_planks");
            b.fill(2, 6, z, 4, 6, z, "spruce_planks");
        }
        // furniture
        b.set(1, 1, 4, "red_bed[facing=south,part=foot]").set(1, 1, 5, "red_bed[facing=south,part=head]");
        b.set(5, 1, 5, "crafting_table");
        b.set(5, 1, 4, "furnace[facing=west]");
        b.set(5, 1, 3, "chest[facing=west]");
        b.set(2, 3, 1, "wall_torch[facing=south]").set(4, 3, 1, "wall_torch[facing=south]");
        b.set(3, 3, 5, "wall_torch[facing=north]");
        return b.connect();
    }

    /** Wheat farm: 9x9 farmland around a water source, fenced, with a gate. */
    static Blueprint farm() {
        Blueprint b = new Blueprint(11, 3, 11);
        b.walls(0, 0, 0, 10, 0, 10, "oak_log[axis=y]");
        b.fill(1, 0, 1, 9, 0, 9, "farmland[moisture=7]");
        b.fill(1, 1, 1, 9, 1, 9, "wheat[age=0]");
        b.set(5, 0, 5, "water");
        b.set(5, 1, 5, "air");
        b.walls(0, 1, 0, 10, 1, 10, "oak_fence");
        b.set(5, 1, 0, "oak_fence_gate[facing=north]");
        b.set(0, 2, 0, "torch").set(10, 2, 0, "torch").set(0, 2, 10, "torch").set(10, 2, 10, "torch");
        return b.connect();
    }

    /** Storage room: stone bricks, rows of barrels, hanging lantern. */
    static Blueprint storage() {
        Blueprint b = new Blueprint(7, 5, 7);
        b.fill(0, 0, 0, 6, 0, 6, "stone_bricks");
        b.walls(0, 1, 0, 6, 3, 6, "stone_bricks");
        b.fill(0, 4, 0, 6, 4, 6, "spruce_planks");
        for (int y = 1; y <= 3; y++) {
            b.set(0, y, 0, "polished_andesite").set(6, y, 0, "polished_andesite")
                    .set(0, y, 6, "polished_andesite").set(6, y, 6, "polished_andesite");
        }
        b.set(3, 1, 0, "spruce_door[facing=north,half=lower,hinge=left]")
                .set(3, 2, 0, "spruce_door[facing=north,half=upper,hinge=left]");
        for (int y = 1; y <= 2; y++) {
            for (int z = 2; z <= 5; z++) {
                b.set(1, y, z, "barrel[facing=east]").set(5, y, z, "barrel[facing=west]");
            }
            for (int x = 2; x <= 4; x++) {
                b.set(x, y, 5, "barrel[facing=north]");
            }
        }
        b.set(3, 3, 3, "lantern[hanging=true]");
        b.set(2, 3, 1, "wall_torch[facing=south]").set(4, 3, 1, "wall_torch[facing=south]");
        return b.connect();
    }

    /** Watch tower: 5x5, 11 blocks high, ladder inside, fenced platform on top. */
    static Blueprint tower() {
        Blueprint b = new Blueprint(5, 14, 5);
        b.fill(0, 0, 0, 4, 0, 4, "stone_bricks");
        b.walls(0, 1, 0, 4, 10, 4, "stone_bricks");
        for (int y = 1; y <= 10; y++) {
            b.set(0, y, 0, "mossy_stone_bricks").set(4, y, 0, "mossy_stone_bricks")
                    .set(0, y, 4, "mossy_stone_bricks").set(4, y, 4, "mossy_stone_bricks");
        }
        b.fill(0, 11, 0, 4, 11, 4, "stone_bricks");
        b.set(2, 1, 0, "spruce_door[facing=north,half=lower,hinge=left]")
                .set(2, 2, 0, "spruce_door[facing=north,half=upper,hinge=left]");
        for (int y = 1; y <= 11; y++) {
            b.set(2, y, 3, "ladder[facing=north]");
        }
        for (int y : new int[]{5, 8}) {
            b.set(0, y, 2, "glass_pane").set(4, y, 2, "glass_pane").set(2, y, 0, "glass_pane");
        }
        b.set(1, 4, 1, "wall_torch[facing=south]").set(3, 7, 1, "wall_torch[facing=south]");
        b.walls(0, 12, 0, 4, 12, 4, "spruce_fence");
        b.set(0, 13, 0, "torch").set(4, 13, 0, "torch").set(0, 13, 4, "torch").set(4, 13, 4, "torch");
        return b.connect();
    }

    /** Animal pen: 9x9 fence with gate, hay and a water cauldron, lanterns on the corners. */
    static Blueprint pen() {
        Blueprint b = new Blueprint(9, 2, 9);
        b.walls(0, 0, 0, 8, 0, 8, "oak_fence");
        b.set(4, 0, 0, "oak_fence_gate[facing=north]");
        b.set(7, 0, 7, "hay_block[axis=y]").set(6, 0, 7, "water_cauldron[level=3]").set(7, 0, 6, "hay_block[axis=y]");
        b.set(0, 1, 0, "lantern[hanging=false]").set(8, 1, 0, "lantern[hanging=false]")
                .set(0, 1, 8, "lantern[hanging=false]").set(8, 1, 8, "lantern[hanging=false]");
        return b.connect();
    }
}
