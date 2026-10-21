package ch.haddo.effortless;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Turns the clicked points into the set of block positions of a shape. */
final class Shapes {

    private Shapes() {
    }

    /**
     * @param points the clicked points, in click order (at least one)
     * @param limit  stop generating after this many blocks (the caller reports "too many")
     */
    static Set<Pos> generate(BuildMode mode, Fill fill, List<Pos> points, int limit) {
        Pos a = points.getFirst();
        Pos b = points.size() > 1 ? points.get(1) : a;
        Pos c = points.size() > 2 ? points.get(2) : b;
        return switch (mode) {
            case NORMAL -> Set.of(a);
            case LINE, DIAGONAL_LINE -> line(a, b);
            case WALL, FLOOR -> box(a, b, fill == Fill.FULL ? Fill.FULL : Fill.HOLLOW, limit);
            case CUBE -> box(a, new Pos(b.x(), c.y(), b.z()), fill, limit);
            case CIRCLE -> round(a, radius(a, b), a.y(), a.y(), fill, false, limit);
            case CYLINDER -> round(a, radius(a, b), a.y(), c.y(), fill, false, limit);
            case SPHERE -> round(a, radius(a, b), a.y(), a.y(), fill, true, limit);
        };
    }

    static double radius(Pos centre, Pos edge) {
        double dx = edge.x() - centre.x(), dz = edge.z() - centre.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    static Set<Pos> line(Pos a, Pos b) {
        Set<Pos> result = new LinkedHashSet<>();
        int dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        int steps = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        if (steps == 0) {
            result.add(a);
            return result;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            result.add(new Pos((int) Math.round(a.x() + dx * t), (int) Math.round(a.y() + dy * t), (int) Math.round(a.z() + dz * t)));
        }
        return result;
    }

    /** Cuboid between two corners. HOLLOW keeps the outer shell, EDGES the twelve edges. Works for flat walls and floors too. */
    static Set<Pos> box(Pos a, Pos b, Fill fill, int limit) {
        int minX = Math.min(a.x(), b.x()), maxX = Math.max(a.x(), b.x());
        int minY = Math.min(a.y(), b.y()), maxY = Math.max(a.y(), b.y());
        int minZ = Math.min(a.z(), b.z()), maxZ = Math.max(a.z(), b.z());
        Set<Pos> result = new LinkedHashSet<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int borders = 0;
                    if (minX != maxX && (x == minX || x == maxX)) borders++;
                    if (minY != maxY && (y == minY || y == maxY)) borders++;
                    if (minZ != maxZ && (z == minZ || z == maxZ)) borders++;
                    int flatAxes = (minX == maxX ? 1 : 0) + (minY == maxY ? 1 : 0) + (minZ == maxZ ? 1 : 0);
                    boolean keep = switch (fill) {
                        case FULL -> true;
                        case HOLLOW -> borders >= 1 || flatAxes == 3;
                        // a flat rectangle has no "edges" of a cube: use its border instead
                        case EDGES -> flatAxes >= 1 ? borders >= 1 || flatAxes == 3 : borders >= 2;
                    };
                    if (keep) {
                        result.add(new Pos(x, y, z));
                        if (result.size() > limit) {
                            return result;
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Circle (or a stack of circles = cylinder) around the centre, or a sphere when {@code sphere} is set. */
    static Set<Pos> round(Pos centre, double radius, int y1, int y2, Fill fill, boolean sphere, int limit) {
        int r = (int) Math.ceil(radius) + 1;
        double outer = (radius + 0.5) * (radius + 0.5);
        boolean hollow = fill != Fill.FULL;
        int minY = sphere ? -r : Math.min(y1, y2) - centre.y();
        int maxY = sphere ? r : Math.max(y1, y2) - centre.y();
        Set<Pos> result = new LinkedHashSet<>();
        for (int dy = minY; dy <= maxY; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (!inside(dx, dy, dz, outer, sphere)) {
                        continue;
                    }
                    if (hollow && inside(dx + 1, dy, dz, outer, sphere) && inside(dx - 1, dy, dz, outer, sphere)
                            && inside(dx, dy, dz + 1, outer, sphere) && inside(dx, dy, dz - 1, outer, sphere)
                            && (!sphere || (inside(dx, dy + 1, dz, outer, true) && inside(dx, dy - 1, dz, outer, true)))) {
                        continue; // fully surrounded: inner block
                    }
                    result.add(centre.add(dx, dy, dz));
                    if (result.size() > limit) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    private static boolean inside(int dx, int dy, int dz, double outer, boolean sphere) {
        return dx * dx + dz * dz + (sphere ? dy * dy : 0) <= outer;
    }
}
