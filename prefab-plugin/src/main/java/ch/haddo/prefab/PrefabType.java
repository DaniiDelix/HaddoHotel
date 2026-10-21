package ch.haddo.prefab;

import org.bukkit.NamespacedKey;

/**
 * A placeable structure.
 *
 * @param id        unique id used in commands and stored on the item
 * @param name      display name (MiniMessage)
 * @param icon      vanilla item model shown for the prefab item, e.g. minecraft:bricks
 * @param blueprint the blocks to place
 * @param builtin   true for structures shipped with the plugin
 */
public record PrefabType(String id, String name, NamespacedKey icon, Blueprint blueprint, boolean builtin) {
}
