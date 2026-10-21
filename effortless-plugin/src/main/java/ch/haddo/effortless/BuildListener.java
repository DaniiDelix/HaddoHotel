package ch.haddo.effortless;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Clicks, the Shift + F menu, mirroring of normal placements and the ghost preview. */
final class BuildListener implements Listener {

    private final EffortlessPlugin plugin;

    BuildListener(EffortlessPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPreview, 5L, 2L);
    }

    // ---------------------------------------------------------------- shape clicks

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        PlayerState state = plugin.states().get(player.getUniqueId());
        if (state == null || state.mode == BuildMode.NORMAL || event.getHand() != EquipmentSlot.HAND
                || !Builder.buildable(event.getItem()) || !player.hasPermission("effortless.use")) {
            return;
        }
        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            if (!state.points.isEmpty()) {
                event.setCancelled(true);
                state.clearPoints();
                plugin.send(player, "forma-annullata");
            }
            return;
        }
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block clicked = event.getClickedBlock();
        if (state.points.isEmpty() && clicked != null && clicked.getType().isInteractable() && !player.isSneaking()) {
            return; // let chests, doors, ... open normally
        }
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        long now = System.currentTimeMillis();
        if (now - state.lastClick < 150) {
            return;
        }
        state.lastClick = now;

        double reach = plugin.getConfig().getDouble("distanza", 64);
        if (state.points.isEmpty()) {
            if (clicked == null || event.getBlockFace() == null) {
                plugin.send(player, "punto-iniziale");
                return;
            }
            Block first = clicked.isReplaceable() ? clicked : clicked.getRelative(event.getBlockFace());
            state.points.add(Pos.of(first));
            state.pointsWorld = first.getWorld();
        } else {
            Pos next = Picker.next(player, state.mode, state.points, reach);
            if (next == null || state.pointsWorld != player.getWorld()) {
                plugin.send(player, plugin.msg("punto-non-valido").replace("{distanza}", String.valueOf((int) reach)));
                return;
            }
            state.points.add(next);
        }
        if (state.points.size() >= state.mode.points) {
            finish(player, state, event.getItem());
        } else {
            plugin.send(player, plugin.msg("punto").replace("{n}", String.valueOf(state.points.size()))
                    .replace("{tot}", String.valueOf(state.mode.points)));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.4f, 1.4f);
        }
    }

    private void finish(Player player, PlayerState state, ItemStack hand) {
        int max = plugin.getConfig().getInt("max-blocchi", 4096);
        Map<Pos, BlockData> blocks = compute(player, state, state.points, hand, max);
        World world = state.pointsWorld;
        state.clearPoints();
        state.clearPreview();
        if (blocks == null) {
            return;
        }
        int placed = plugin.builder().place(player, state, world, blocks, hand.getType(), true, true);
        if (placed == 0) {
            plugin.send(player, Builder.count(player.getInventory(), hand.getType()) == 0
                    && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                    ? plugin.msg("niente-blocchi").replace("{blocco}", pretty(hand)).replace("{n}", "0")
                    : plugin.msg("niente-spazio"));
            return;
        }
        boolean survival = player.getGameMode() != org.bukkit.GameMode.CREATIVE;
        if (placed < blocks.size() && survival && Builder.count(player.getInventory(), hand.getType()) == 0) {
            plugin.send(player, plugin.msg("niente-blocchi").replace("{blocco}", pretty(hand)).replace("{n}", String.valueOf(placed)));
        } else {
            plugin.send(player, plugin.msg("piazzati").replace("{n}", String.valueOf(placed)));
        }
        player.playSound(player.getLocation(), hand.getType().createBlockData().getSoundGroup().getPlaceSound(), 1f, 0.9f);
    }

    /** Shape + modifiers, or null (with a message) if it is too big. */
    private Map<Pos, BlockData> compute(Player player, PlayerState state, List<Pos> points, ItemStack hand, int max) {
        Set<Pos> shape = shape(state, points, max);
        if (shape == null) {
            plugin.send(player, plugin.msg("troppi-blocchi").replace("{n}", ">" + max).replace("{max}", String.valueOf(max)));
            return null;
        }
        Map<Pos, BlockData> blocks = state.modifiers.apply(shape, Builder.dataFor(player, hand.getType()));
        if (blocks.size() > max) {
            plugin.send(player, plugin.msg("troppi-blocchi").replace("{n}", String.valueOf(blocks.size())).replace("{max}", String.valueOf(max)));
            return null;
        }
        return blocks;
    }

    private Set<Pos> shape(PlayerState state, List<Pos> points, int max) {
        int maxRadius = plugin.getConfig().getInt("raggio-max", 48);
        if (points.size() >= 2 && (state.mode == BuildMode.CIRCLE || state.mode == BuildMode.CYLINDER || state.mode == BuildMode.SPHERE)
                && Shapes.radius(points.get(0), points.get(1)) > maxRadius) {
            return null;
        }
        Set<Pos> shape = Shapes.generate(state.mode, state.fill, points, max);
        return shape.size() > max ? null : shape;
    }

    private static String pretty(ItemStack item) {
        return item.getType().getKey().getKey().replace('_', ' ');
    }

    // ---------------------------------------------------------------- normal placement with modifiers

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.builder().busy) {
            return;
        }
        Player player = event.getPlayer();
        PlayerState state = plugin.states().get(player.getUniqueId());
        if (state == null || state.mode != BuildMode.NORMAL || !state.modifiers.active() || !player.hasPermission("effortless.use")) {
            return;
        }
        Block placed = event.getBlockPlaced();
        Pos origin = Pos.of(placed);
        Map<Pos, BlockData> copies = new LinkedHashMap<>(state.modifiers.apply(Set.of(origin), placed.getBlockData()));
        copies.remove(origin);
        if (copies.isEmpty() || copies.size() > plugin.getConfig().getInt("max-blocchi", 4096)) {
            return;
        }
        // run after the vanilla placement has used up its item
        Bukkit.getScheduler().runTask(plugin, () ->
                plugin.builder().place(player, state, placed.getWorld(), copies, placed.getType(), true, true));
    }

    // ---------------------------------------------------------------- menu

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (player.isSneaking() && player.hasPermission("effortless.use")) {
            event.setCancelled(true);
            player.openInventory(new BuildMenu(plugin, player).getInventory());
        }
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BuildMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == event.getView().getTopInventory()) {
            menu.click(event.getSlot(), event.getClick());
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BuildMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent event) {
        PlayerState state = plugin.states().get(event.getPlayer().getUniqueId());
        if (state != null) {
            state.clearPoints();
            state.clearPreview();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.forget(event.getPlayer());
    }

    // ---------------------------------------------------------------- preview

    private void tickPreview() {
        int previewMax = plugin.getConfig().getInt("anteprima.max-blocchi", 1500);
        int max = plugin.getConfig().getInt("max-blocchi", 4096);
        double reach = plugin.getConfig().getDouble("distanza", 64);
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerState state = plugin.states().get(player.getUniqueId());
            if (state == null) {
                continue;
            }
            ItemStack hand = player.getInventory().getItemInMainHand();
            boolean shapeMode = state.mode != BuildMode.NORMAL;
            if (!Builder.buildable(hand) || (!shapeMode && !state.modifiers.active())) {
                state.clearPreview();
                continue;
            }
            List<Pos> points = new ArrayList<>(state.points);
            if (points.isEmpty()) {
                RayTraceResult hit = player.rayTraceBlocks(6, FluidCollisionMode.NEVER);
                if (hit != null && hit.getHitBlock() != null && hit.getHitBlockFace() != null) {
                    Block b = hit.getHitBlock();
                    points.add(Pos.of(b.isReplaceable() ? b : b.getRelative(hit.getHitBlockFace())));
                }
            } else if (shapeMode) {
                Pos next = Picker.next(player, state.mode, points, reach);
                if (next != null) {
                    points.add(next);
                }
            }
            Map<Pos, BlockData> blocks = Map.of();
            if (!points.isEmpty()) {
                BuildMode mode = shapeMode ? state.mode : BuildMode.NORMAL;
                Set<Pos> shape = mode == BuildMode.NORMAL ? Set.of(points.getFirst()) : shape(state, points, max);
                if (shape != null) {
                    blocks = state.modifiers.apply(shape, Builder.dataFor(player, hand.getType()));
                }
            }
            show(player, state, blocks, previewMax);
            if (shapeMode) {
                String extra = state.modifiers.describe();
                player.sendActionBar(MiniMessage.miniMessage().deserialize("<green>" + state.mode.label
                        + (state.mode.points > 1 ? " <gray>• punto <white>" + Math.min(state.points.size() + 1, state.mode.points)
                        + "/" + state.mode.points : "")
                        + " <gray>• <white>" + blocks.size() + "</white> blocchi"
                        + (extra.isEmpty() ? "" : " <gray>• " + extra)));
            }
            Modifiers m = state.modifiers;
            if (m.centre != null && (m.mirrorX || m.mirrorY || m.mirrorZ || m.radial > 1)) {
                player.spawnParticle(Particle.END_ROD, m.centre.x() + 0.5, m.centre.y() + 0.5, m.centre.z() + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

    private void show(Player player, PlayerState state, Map<Pos, BlockData> blocks, int previewMax) {
        World world = player.getWorld();
        Map<Pos, BlockData> wanted = new LinkedHashMap<>();
        for (Map.Entry<Pos, BlockData> e : blocks.entrySet()) {
            if (wanted.size() >= previewMax) {
                break;
            }
            Block block = PlayerState.block(world, e.getKey());
            if (block.getType().isAir() || block.isReplaceable()) {
                wanted.put(e.getKey(), e.getValue());
            }
        }
        BlockData sample = wanted.isEmpty() ? null : wanted.values().iterator().next();
        if (state.previewData != null && sample != null && !state.previewData.getMaterial().equals(sample.getMaterial())) {
            state.clearPreview();
        }
        Set<Pos> keep = new HashSet<>(wanted.keySet());
        for (Iterator<Map.Entry<Pos, BlockDisplay>> it = state.preview.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Pos, BlockDisplay> e = it.next();
            BlockData now = wanted.get(e.getKey());
            if (!keep.contains(e.getKey()) || !e.getValue().isValid() || !e.getValue().getBlock().equals(now)) {
                e.getValue().remove();
                it.remove();
            }
        }
        for (Map.Entry<Pos, BlockData> e : wanted.entrySet()) {
            if (state.preview.containsKey(e.getKey())) {
                continue;
            }
            BlockData data = e.getValue();
            BlockDisplay display = world.spawn(PlayerState.block(world, e.getKey()).getLocation(), BlockDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBlock(data);
                d.setGlowing(true);
                d.setGlowColorOverride(Color.WHITE);
                d.setBrightness(new Display.Brightness(15, 15));
            });
            player.showEntity(plugin, display);
            state.preview.put(e.getKey(), display);
        }
        state.previewData = sample;
    }
}
