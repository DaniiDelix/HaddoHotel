package ch.haddo.effortless;

import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mirror, radial symmetry and array, applied on top of every placement. */
final class Modifiers {

    static final int[] RADIAL_STEPS = {0, 2, 3, 4, 6, 8, 12};

    boolean mirrorX;
    boolean mirrorY;
    boolean mirrorZ;
    int radial; // number of slices, 0 = off
    int arrayCount; // extra copies, 0 = off
    int arrayStep = 1;
    BlockFace arrayDirection = BlockFace.NORTH;
    Pos centre;

    boolean active() {
        return arrayCount > 0 || (centre != null && (mirrorX || mirrorY || mirrorZ || radial > 1));
    }

    /** Copies {@code data} to every position of {@code shape} and of its array / mirror / radial copies. */
    Map<Pos, BlockData> apply(Set<Pos> shape, BlockData data) {
        Map<Pos, BlockData> result = new LinkedHashMap<>();
        for (Pos p : shape) {
            result.put(p, data);
        }
        if (arrayCount > 0) {
            Map<Pos, BlockData> copies = new LinkedHashMap<>();
            for (Map.Entry<Pos, BlockData> e : result.entrySet()) {
                for (int i = 1; i <= arrayCount; i++) {
                    int d = arrayStep * i;
                    copies.putIfAbsent(e.getKey().add(arrayDirection.getModX() * d, arrayDirection.getModY() * d,
                            arrayDirection.getModZ() * d), e.getValue());
                }
            }
            copies.forEach(result::putIfAbsent);
        }
        if (centre != null) {
            if (mirrorX) {
                mirror(result, p -> new Pos(2 * centre.x() - p.x(), p.y(), p.z()), Mirror.FRONT_BACK, false);
            }
            if (mirrorZ) {
                mirror(result, p -> new Pos(p.x(), p.y(), 2 * centre.z() - p.z()), Mirror.LEFT_RIGHT, false);
            }
            if (mirrorY) {
                mirror(result, p -> new Pos(p.x(), 2 * centre.y() - p.y(), p.z()), null, true);
            }
            if (radial > 1) {
                List<Map.Entry<Pos, BlockData>> base = new ArrayList<>(result.entrySet());
                for (int k = 1; k < radial; k++) {
                    double angle = 2 * Math.PI * k / radial;
                    double cos = Math.cos(angle), sin = Math.sin(angle);
                    // a rotation by whole quarter turns can also turn stairs, logs, ...
                    boolean quarter = (k * 4) % radial == 0;
                    int quarters = quarter ? (k * 4 / radial) % 4 : 0;
                    for (Map.Entry<Pos, BlockData> e : base) {
                        Pos p = e.getKey();
                        int dx = p.x() - centre.x(), dz = p.z() - centre.z();
                        Pos q = new Pos(centre.x() + (int) Math.round(dx * cos - dz * sin), p.y(),
                                centre.z() + (int) Math.round(dx * sin + dz * cos));
                        BlockData rotated = e.getValue();
                        if (quarters != 0) {
                            rotated = rotated.clone();
                            rotated.rotate(switch (quarters) {
                                case 1 -> StructureRotation.CLOCKWISE_90;
                                case 2 -> StructureRotation.CLOCKWISE_180;
                                default -> StructureRotation.COUNTERCLOCKWISE_90;
                            });
                        }
                        result.putIfAbsent(q, rotated);
                    }
                }
            }
        }
        return result;
    }

    private static void mirror(Map<Pos, BlockData> result, java.util.function.UnaryOperator<Pos> flip, Mirror mirror, boolean vertical) {
        List<Map.Entry<Pos, BlockData>> base = new ArrayList<>(result.entrySet());
        for (Map.Entry<Pos, BlockData> e : base) {
            BlockData data = e.getValue().clone();
            if (mirror != null) {
                data.mirror(mirror);
            }
            if (vertical) {
                if (data instanceof Bisected bisected) {
                    bisected.setHalf(bisected.getHalf() == Bisected.Half.TOP ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
                } else if (data instanceof Slab slab && slab.getType() != Slab.Type.DOUBLE) {
                    slab.setType(slab.getType() == Slab.Type.TOP ? Slab.Type.BOTTOM : Slab.Type.TOP);
                }
            }
            result.putIfAbsent(flip.apply(e.getKey()), data);
        }
    }

    String describe() {
        List<String> parts = new ArrayList<>();
        if (centre != null && (mirrorX || mirrorY || mirrorZ)) {
            parts.add("specchio " + (mirrorX ? "X" : "") + (mirrorY ? "Y" : "") + (mirrorZ ? "Z" : ""));
        }
        if (centre != null && radial > 1) {
            parts.add("radiale " + radial);
        }
        if (arrayCount > 0) {
            parts.add("ripeti ×" + arrayCount);
        }
        return String.join(", ", parts);
    }
}
