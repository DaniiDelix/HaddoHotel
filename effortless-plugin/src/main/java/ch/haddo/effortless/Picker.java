package ch.haddo.effortless;

import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Finds the next point of a shape from where the player is looking. Like Effortless Building, the point is
 * constrained to a line or plane through the previous point, so it can be picked in mid-air.
 */
final class Picker {

    private static final Vector[] AXES = {new Vector(1, 0, 0), new Vector(0, 1, 0), new Vector(0, 0, 1)};

    private Picker() {
    }

    /** @return the next point, or null if the player is not aiming at a valid spot */
    static Pos next(Player player, BuildMode mode, List<Pos> points, double reach) {
        if (points.isEmpty()) {
            return null;
        }
        Vector eye = player.getEyeLocation().toVector();
        Vector dir = player.getEyeLocation().getDirection().normalize();
        Pos a = points.getFirst();
        if (points.size() >= 2) {
            // third point of cube / cylinder: height above the second point
            Pos b = points.get(1);
            return onLine(eye, dir, centre(b), AXES[1], reach, b);
        }
        return switch (mode) {
            case LINE -> bestLine(eye, dir, a, reach);
            case WALL -> wall(eye, dir, a, reach);
            case FLOOR, CUBE, CIRCLE, CYLINDER, SPHERE -> floor(eye, dir, a, reach);
            case DIAGONAL_LINE -> free(player, reach);
            case NORMAL -> null;
        };
    }

    private static Vector centre(Pos p) {
        return new Vector(p.x() + 0.5, p.y() + 0.5, p.z() + 0.5);
    }

    private static Pos floor(Vector eye, Vector dir, Pos a, double reach) {
        if (Math.abs(dir.getY()) < 1e-6) {
            return null;
        }
        double t = (a.y() + 0.5 - eye.getY()) / dir.getY();
        if (t <= 0 || t > reach) {
            return null;
        }
        Vector hit = eye.clone().add(dir.clone().multiply(t));
        return new Pos(hit.getBlockX(), a.y(), hit.getBlockZ());
    }

    private static Pos wall(Vector eye, Vector dir, Pos a, double reach) {
        Pos best = null;
        double bestFacing = -1;
        // vertical plane through A facing east/west, and the one facing north/south
        if (Math.abs(dir.getX()) > 1e-6) {
            double t = (a.x() + 0.5 - eye.getX()) / dir.getX();
            if (t > 0 && t <= reach) {
                Vector hit = eye.clone().add(dir.clone().multiply(t));
                best = new Pos(a.x(), hit.getBlockY(), hit.getBlockZ());
                bestFacing = Math.abs(dir.getX());
            }
        }
        if (Math.abs(dir.getZ()) > 1e-6) {
            double t = (a.z() + 0.5 - eye.getZ()) / dir.getZ();
            if (t > 0 && t <= reach && Math.abs(dir.getZ()) > bestFacing) {
                Vector hit = eye.clone().add(dir.clone().multiply(t));
                best = new Pos(hit.getBlockX(), hit.getBlockY(), a.z());
            }
        }
        return best;
    }

    private static Pos bestLine(Vector eye, Vector dir, Pos a, double reach) {
        Pos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vector axis : AXES) {
            double[] st = closest(eye, dir, centre(a), axis);
            if (st == null || st[1] <= 0 || Math.abs(st[0]) > reach) {
                continue;
            }
            if (st[2] < bestDistance) {
                bestDistance = st[2];
                long s = Math.round(st[0]);
                best = a.add((int) (axis.getX() * s), (int) (axis.getY() * s), (int) (axis.getZ() * s));
            }
        }
        return best;
    }

    private static Pos onLine(Vector eye, Vector dir, Vector origin, Vector axis, double reach, Pos from) {
        double[] st = closest(eye, dir, origin, axis);
        if (st == null || st[1] <= 0 || Math.abs(st[0]) > reach) {
            return null;
        }
        long s = Math.round(st[0]);
        return from.add((int) (axis.getX() * s), (int) (axis.getY() * s), (int) (axis.getZ() * s));
    }

    /**
     * Closest points between the view ray (eye + t*dir) and the line (origin + s*axis).
     * @return {s, t, distance} or null if they are parallel
     */
    private static double[] closest(Vector eye, Vector dir, Vector origin, Vector axis) {
        Vector w0 = origin.clone().subtract(eye);
        double b = axis.dot(dir);
        double d = axis.dot(w0);
        double e = dir.dot(w0);
        double denom = 1 - b * b;
        if (denom < 1e-6) {
            return null;
        }
        double s = (b * e - d) / denom;
        double t = (e - b * d) / denom;
        Vector onLine = origin.clone().add(axis.clone().multiply(s));
        Vector onRay = eye.clone().add(dir.clone().multiply(t));
        return new double[]{s, t, onLine.distance(onRay)};
    }

    /** Diagonal lines end where a block would be placed against the surface you look at. */
    private static Pos free(Player player, double reach) {
        RayTraceResult hit = player.rayTraceBlocks(reach, FluidCollisionMode.NEVER);
        if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
            return null;
        }
        if (hit.getHitBlock().isReplaceable()) {
            return Pos.of(hit.getHitBlock());
        }
        return Pos.of(hit.getHitBlock().getRelative(hit.getHitBlockFace()));
    }
}
