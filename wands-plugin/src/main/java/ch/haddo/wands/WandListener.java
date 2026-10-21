package ch.haddo.wands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Placing, mode switching, particle preview and undo. */
final class WandListener implements Listener {

    private record Undo(Material material, List<BlockState> previous, List<Block> placed, boolean refund, long expiresAt) {
    }

    private final WandsPlugin plugin;
    private final Map<UUID, Undo> undos = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    WandListener(WandsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::preview, 5L, 4L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        WandTier tier = plugin.items().tier(event.getItem());
        if (tier == null) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.PHYSICAL) {
            return;
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < 150) {
            return;
        }
        if (!player.hasPermission("wands.use")) {
            plugin.send(player, "niente-permesso");
            return;
        }
        ItemStack wand = event.getItem();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            WandMode mode = plugin.items().mode(wand).next();
            plugin.items().setMode(wand, tier, mode);
            player.sendActionBar(MiniMessage.miniMessage().deserialize(plugin.msg("modalita").replace("{modalita}", mode.label)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.6f);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            place(player, wand, tier, event.getClickedBlock(), event.getBlockFace());
        }
    }

    private void place(Player player, ItemStack wand, WandTier tier, Block clicked, BlockFace face) {
        if (!Selection.copyable(clicked)) {
            plugin.send(player, "non-copiabile");
            return;
        }
        Material material = clicked.getType();
        boolean consume = tier.consumes() && player.getGameMode() != GameMode.CREATIVE;
        int limit = limit(player, wand, tier, material, consume);
        if (limit <= 0) {
            plugin.send(player, plugin.msg("niente-blocchi").replace("{blocco}", pretty(material)));
            return;
        }
        List<Block> targets = Selection.compute(clicked, face, plugin.items().mode(wand), player.getLocation().getDirection(), limit);
        if (targets.isEmpty()) {
            plugin.send(player, "niente-spazio");
            return;
        }

        BlockData data = clicked.getBlockData();
        ItemStack placedItem = new ItemStack(material);
        List<BlockState> previous = new ArrayList<>();
        List<Block> placed = new ArrayList<>();
        for (Block target : targets) {
            BlockState old = target.getState();
            target.setBlockData(data.clone(), true);
            // let protection and logging plugins (WorldGuard, CoreProtect, ...) see a normal placement
            BlockPlaceEvent check = new BlockPlaceEvent(target, old, clicked, placedItem, player, true, EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled() || !check.canBuild()) {
                old.update(true, false);
                continue;
            }
            previous.add(old);
            placed.add(target);
        }
        if (placed.isEmpty()) {
            plugin.send(player, "niente-spazio");
            return;
        }
        if (consume) {
            take(player.getInventory(), material, placed.size());
        }
        boolean broken = damage(player, wand, tier, placed.size());
        long minutes = plugin.getConfig().getLong("annulla.minuti", 5);
        undos.put(player.getUniqueId(), new Undo(material, previous, placed, consume, System.currentTimeMillis() + minutes * 60_000));
        player.playSound(clicked.getLocation(), data.getSoundGroup().getPlaceSound(), 1f, 0.9f);
        player.sendActionBar(MiniMessage.miniMessage().deserialize(plugin.msg("piazzati")
                .replace("{n}", String.valueOf(placed.size())).replace("{blocco}", pretty(material))));
        if (broken) {
            plugin.send(player, "rotta");
        }
    }

    /** How many blocks this click may place: wand size, blocks in the inventory and remaining durability. */
    private int limit(Player player, ItemStack wand, WandTier tier, Material material, boolean consume) {
        int limit = tier.maxBlocks();
        if (consume) {
            limit = Math.min(limit, count(player.getInventory(), material));
        }
        if (tier.durability() > 0 && player.getGameMode() != GameMode.CREATIVE && wand.getItemMeta() instanceof Damageable d) {
            limit = Math.min(limit, d.getMaxDamage() - d.getDamage());
        }
        return limit;
    }

    /** Wears the wand out by one point per block; returns true if it broke. */
    private boolean damage(Player player, ItemStack wand, WandTier tier, int amount) {
        if (tier.durability() <= 0 || player.getGameMode() == GameMode.CREATIVE || !(wand.getItemMeta() instanceof Damageable d)) {
            return false;
        }
        int damage = d.getDamage() + amount;
        if (damage >= d.getMaxDamage()) {
            player.getInventory().setItemInMainHand(null);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            return true;
        }
        d.setDamage(damage);
        wand.setItemMeta(d);
        return false;
    }

    private static int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (isPlain(item, material)) {
                total += item.getAmount();
            }
        }
        ItemStack off = inventory.getItemInOffHand();
        if (isPlain(off, material)) {
            total += off.getAmount();
        }
        return total;
    }

    private static void take(PlayerInventory inventory, Material material, int amount) {
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (isPlain(item, material)) {
                int used = Math.min(amount, item.getAmount());
                item.setAmount(item.getAmount() - used);
                amount -= used;
                contents[i] = item.getAmount() <= 0 ? null : item;
            }
        }
        inventory.setStorageContents(contents);
        ItemStack off = inventory.getItemInOffHand();
        if (amount > 0 && isPlain(off, material)) {
            off.setAmount(off.getAmount() - amount);
            inventory.setItemInOffHand(off.getAmount() <= 0 ? null : off);
        }
    }

    /** Only ordinary blocks are used up, never renamed or special items. */
    private static boolean isPlain(ItemStack item, Material material) {
        return item != null && item.getType() == material && !item.hasItemMeta();
    }

    private static String pretty(Material material) {
        return material.getKey().getKey().replace('_', ' ');
    }

    // ---------------------------------------------------------------- preview

    private void preview() {
        if (!plugin.getConfig().getBoolean("anteprima.attiva", true)) {
            return;
        }
        Color color = parseColor(plugin.getConfig().getString("anteprima.colore", "#7CFC00"));
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.9f);
        double reach = plugin.getConfig().getDouble("distanza", 6);
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack wand = player.getInventory().getItemInMainHand();
            WandTier tier = plugin.items().tier(wand);
            if (tier == null) {
                continue;
            }
            RayTraceResult hit = player.rayTraceBlocks(reach, FluidCollisionMode.NEVER);
            if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) {
                continue;
            }
            Block clicked = hit.getHitBlock();
            if (!Selection.copyable(clicked)) {
                continue;
            }
            boolean consume = tier.consumes() && player.getGameMode() != GameMode.CREATIVE;
            int limit = limit(player, wand, tier, clicked.getType(), consume);
            for (Block target : Selection.compute(clicked, hit.getHitBlockFace(), plugin.items().mode(wand),
                    player.getLocation().getDirection(), limit)) {
                player.spawnParticle(Particle.DUST, target.getLocation().add(0.5, 0.5, 0.5), 1, 0.15, 0.15, 0.15, 0, dust);
            }
        }
    }

    private static Color parseColor(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (RuntimeException e) {
            return Color.LIME;
        }
    }

    // ---------------------------------------------------------------- undo

    boolean undo(Player player) {
        Undo undo = undos.remove(player.getUniqueId());
        if (undo == null || undo.expiresAt() < System.currentTimeMillis()) {
            return false;
        }
        int restored = 0;
        for (int i = 0; i < undo.placed().size(); i++) {
            Block block = undo.placed().get(i);
            if (block.getType() != undo.material()) {
                continue; // changed since: leave it alone
            }
            undo.previous().get(i).update(true, false);
            restored++;
        }
        if (undo.refund() && restored > 0) {
            int left = restored;
            while (left > 0) {
                int stack = Math.min(left, undo.material().getMaxStackSize());
                player.getInventory().addItem(new ItemStack(undo.material(), stack)).values()
                        .forEach(rest -> player.getWorld().dropItemNaturally(player.getLocation(), rest));
                left -= stack;
            }
        }
        plugin.send(player, plugin.msg("annullato").replace("{n}", String.valueOf(restored)));
        return true;
    }

    // ---------------------------------------------------------------- guards

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (plugin.items().isWand(item)) {
                event.getInventory().setResult(null); // a wand is not a stick
                return;
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.items().isWand(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true); // left click switches mode, it never breaks blocks
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent event) {
        if (plugin.items().isWand(event.getFuel())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(plugin.items().recipeKeys());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
