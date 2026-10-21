package ch.haddo.effortless;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Gate;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.data.type.TrapDoor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Places shapes for real: free-space checks, protection checks, inventory use and undo history. */
final class Builder {

    private final EffortlessPlugin plugin;
    /** True while we fire our own place events, so the listener does not mirror them again. */
    boolean busy;

    Builder(EffortlessPlugin plugin) {
        this.plugin = plugin;
    }

    /** Whether the item in hand can be used to build shapes. */
    static boolean buildable(ItemStack item) {
        if (item == null || !item.getType().isBlock() || !item.getType().isItem() || item.getType().isAir()) {
            return false;
        }
        BlockData data = item.getType().createBlockData();
        if (data instanceof Door || data instanceof Bed) {
            return false;
        }
        return !(data instanceof Bisected) || data instanceof Stairs || data instanceof TrapDoor;
    }

    /** Block data for the held block, turned towards the player the way vanilla mostly does it. */
    static BlockData dataFor(Player player, Material material) {
        BlockData data = material.createBlockData();
        if (data instanceof Directional directional) {
            BlockFace facing = player.getFacing();
            BlockFace wanted = data instanceof Stairs || data instanceof Gate ? facing : facing.getOppositeFace();
            if (directional.getFaces().contains(wanted)) {
                directional.setFacing(wanted);
            }
        }
        return data;
    }

    static boolean free(Block block) {
        if (block.getY() < block.getWorld().getMinHeight() || block.getY() >= block.getWorld().getMaxHeight()) {
            return false;
        }
        if (!block.getType().isAir() && !block.isReplaceable()) {
            return false;
        }
        for (Entity entity : block.getWorld().getNearbyEntities(BoundingBox.of(block))) {
            if (entity instanceof LivingEntity) {
                return false;
            }
        }
        return true;
    }

    /**
     * Places the given blocks for the player. In survival every block uses one item of {@code material} from the inventory.
     * @param record    add the action to the undo history
     * @param clearRedo forget undone actions (a new action, not a redo)
     * @return how many blocks were placed
     */
    int place(Player player, PlayerState state, World world, Map<Pos, BlockData> blocks, Material material, boolean record,
              boolean clearRedo) {
        boolean consume = player.getGameMode() != GameMode.CREATIVE;
        int available = consume ? count(player.getInventory(), material) : Integer.MAX_VALUE;
        List<BlockState> previous = new ArrayList<>();
        Map<Pos, BlockData> placed = new LinkedHashMap<>();
        ItemStack item = new ItemStack(material);
        busy = true;
        try {
            for (Map.Entry<Pos, BlockData> entry : blocks.entrySet()) {
                if (placed.size() >= available) {
                    break;
                }
                Block block = PlayerState.block(world, entry.getKey());
                if (!free(block)) {
                    continue;
                }
                BlockState old = block.getState();
                block.setBlockData(entry.getValue(), true);
                BlockPlaceEvent check = new BlockPlaceEvent(block, old, block.getRelative(BlockFace.DOWN), item, player, true,
                        EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(check);
                if (check.isCancelled() || !check.canBuild()) {
                    old.update(true, false);
                    continue;
                }
                previous.add(old);
                placed.put(entry.getKey(), entry.getValue());
            }
        } finally {
            busy = false;
        }
        if (consume && !placed.isEmpty()) {
            take(player.getInventory(), material, placed.size());
        }
        if (record && !placed.isEmpty()) {
            state.undo.push(new PlayerState.Action(world, previous, placed));
            while (state.undo.size() > plugin.getConfig().getInt("cronologia", 20)) {
                state.undo.removeLast();
            }
            if (clearRedo) {
                state.redo.clear();
            }
        }
        return placed.size();
    }

    /** Puts back what was there before the last action and refunds the blocks. */
    int undo(Player player, PlayerState state) {
        PlayerState.Action action = state.undo.poll();
        if (action == null) {
            return -1;
        }
        int restored = 0;
        Map<Material, Integer> refund = new LinkedHashMap<>();
        for (BlockState old : action.previous()) {
            Block block = old.getBlock();
            BlockData placed = action.placed().get(Pos.of(block));
            if (placed == null || block.getType() != placed.getMaterial()) {
                continue; // changed since: leave it alone
            }
            old.update(true, false);
            restored++;
            refund.merge(placed.getMaterial(), 1, Integer::sum);
        }
        if (player.getGameMode() != GameMode.CREATIVE) {
            refund.forEach((material, amount) -> give(player, material, amount));
        }
        state.redo.push(action);
        return restored;
    }

    /** Places the last undone action again. */
    int redo(Player player, PlayerState state) {
        PlayerState.Action action = state.redo.poll();
        if (action == null) {
            return -1;
        }
        Material material = action.placed().values().iterator().next().getMaterial();
        return place(player, state, action.world(), action.placed(), material, true, false);
    }

    private static void give(Player player, Material material, int amount) {
        while (amount > 0) {
            int stack = Math.min(amount, material.getMaxStackSize());
            player.getInventory().addItem(new ItemStack(material, stack)).values()
                    .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
            amount -= stack;
        }
    }

    static int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (plain(item, material)) {
                total += item.getAmount();
            }
        }
        ItemStack off = inventory.getItemInOffHand();
        return plain(off, material) ? total + off.getAmount() : total;
    }

    private static void take(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            if (plain(contents[i], material)) {
                int used = Math.min(amount, contents[i].getAmount());
                contents[i].setAmount(contents[i].getAmount() - used);
                amount -= used;
                if (contents[i].getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        inventory.setStorageContents(contents);
        ItemStack off = inventory.getItemInOffHand();
        if (amount > 0 && plain(off, material)) {
            off.setAmount(off.getAmount() - amount);
            inventory.setItemInOffHand(off.getAmount() <= 0 ? null : off);
        }
    }

    /** Only plain blocks are used, never renamed or otherwise special items. */
    private static boolean plain(ItemStack item, Material material) {
        return item != null && item.getType() == material && !item.hasItemMeta();
    }
}
