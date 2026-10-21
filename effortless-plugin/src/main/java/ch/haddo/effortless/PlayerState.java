package ch.haddo.effortless;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Everything the plugin remembers about one player. */
final class PlayerState {

    /** One undoable action: what was there before and what was placed. */
    record Action(World world, List<BlockState> previous, Map<Pos, BlockData> placed) {
    }

    BuildMode mode = BuildMode.NORMAL;
    Fill fill = Fill.FULL;
    final Modifiers modifiers = new Modifiers();
    final List<Pos> points = new ArrayList<>();
    World pointsWorld;

    final Map<Pos, BlockDisplay> preview = new HashMap<>();
    BlockData previewData;

    final Deque<Action> undo = new ArrayDeque<>();
    final Deque<Action> redo = new ArrayDeque<>();

    long lastClick;

    void clearPoints() {
        points.clear();
        pointsWorld = null;
    }

    void clearPreview() {
        preview.values().forEach(d -> {
            if (d.isValid()) {
                d.remove();
            }
        });
        preview.clear();
        previewData = null;
    }

    static Block block(World world, Pos pos) {
        return world.getBlockAt(pos.x(), pos.y(), pos.z());
    }
}
