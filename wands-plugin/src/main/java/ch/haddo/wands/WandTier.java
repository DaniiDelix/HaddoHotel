package ch.haddo.wands;

import org.bukkit.NamespacedKey;

/**
 * @param maxBlocks  blocks placed with one click at most
 * @param durability total blocks before the wand breaks, 0 = unbreakable
 * @param consumes   false for the creative wand, which needs no blocks
 */
record WandTier(String id, String name, int maxBlocks, int durability, NamespacedKey model, boolean consumes) {
}
