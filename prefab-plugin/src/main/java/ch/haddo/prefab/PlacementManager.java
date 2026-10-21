package ch.haddo.prefab;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
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
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Preview, confirmation, building and undo. */
final class PlacementManager implements Listener {

    private record Preview(Placement placement, List<BlockDisplay> displays, long expiresAt) {
    }

    private record Undo(String prefabId, List<BlockState> previous, List<Placement.Change> placed, long expiresAt) {
    }

    private final PrefabPlugin plugin;
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, Undo> undos = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();

    PlacementManager(PrefabPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::expirePreviews, 20L, 20L);
    }

    // ---------------------------------------------------------------- events

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        String id = plugin.items().read(event.getItem());
        if (id == null) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // right click with a prefab item never opens chests, places blocks or uses the item
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        Long last = lastClick.put(player.getUniqueId(), now);
        if (last != null && now - last < 250) {
            return;
        }
        if (!player.hasPermission("prefab.use")) {
            plugin.send(player, "niente-permesso");
            return;
        }
        PrefabType type = plugin.registry().get(id);
        if (type == null) {
            plugin.send(player, plugin.msg("sconosciuto").replace("{id}", id));
            return;
        }
        if (player.isSneaking()) {
            if (cancelPreview(player)) {
                plugin.send(player, "anteprima-annullata");
            }
            return;
        }
        int range = plugin.getConfig().getInt("costruzione.distanza-mira", 12);
        Block target = player.getTargetBlockExact(range, FluidCollisionMode.NEVER);
        while (target != null && !target.getType().isAir() && target.isReplaceable()
                && target.getY() > target.getWorld().getMinHeight()) {
            target = target.getRelative(BlockFace.DOWN); // aim through grass and flowers
        }
        if (target == null || target.getType().isAir()) {
            plugin.send(player, plugin.msg("nessun-terreno").replace("{distanza}", String.valueOf(range)));
            return;
        }
        Placement placement = Placement.compute(plugin, type, target, player.getFacing());
        Preview current = previews.get(player.getUniqueId());
        if (current != null && current.placement().sameSpot(placement)) {
            confirm(player, placement);
        } else {
            showPreview(player, placement);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if (plugin.items().read(item) != null) {
            event.setCancelled(true); // e.g. do not feed animals with it
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() != null && plugin.items().read(event.getRecipe().getResult()) != null) {
            return;
        }
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (plugin.items().read(item) != null) {
                event.getInventory().setResult(null); // prefab items are not paper
                return;
            }
        }
    }

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        if (cancelPreview(event.getPlayer())) {
            plugin.send(event.getPlayer(), "anteprima-annullata");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        event.getPlayer().discoverRecipes(plugin.items().recipeKeys());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelPreview(event.getPlayer());
        lastClick.remove(event.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- preview

    private void showPreview(Player player, Placement placement) {
        cancelPreview(player);
        boolean ok = placement.problem == null;
        List<Placement.Change> shown = placement.preview;
        int max = plugin.getConfig().getInt("anteprima.max-blocchi", 1500);
        if (shown.size() > max) {
            shown = shell(placement, max);
        }
        List<BlockDisplay> displays = new ArrayList<>(shown.size());
        Color glow = ok ? Color.LIME : Color.RED;
        for (Placement.Change change : shown) {
            BlockData data = change.data();
            BlockDisplay display = change.block().getWorld().spawn(change.block().getLocation(), BlockDisplay.class, d -> {
                d.setVisibleByDefault(false);
                d.setPersistent(false);
                d.setBlock(data);
                d.setGlowing(true);
                d.setGlowColorOverride(glow);
                d.setBrightness(new Display.Brightness(15, 15));
            });
            player.showEntity(plugin, display);
            displays.add(display);
        }
        long seconds = plugin.getConfig().getLong("anteprima.secondi", 30);
        previews.put(player.getUniqueId(), new Preview(placement, displays, System.currentTimeMillis() + seconds * 1000));
        if (ok) {
            plugin.send(player, plugin.msg("anteprima").replace("{nome}", placement.type.name()));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.6f);
        } else {
            plugin.send(player, plugin.msg("anteprima-bloccata").replace("{motivo}", placement.problem));
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
        }
    }

    /** Keeps only the outer layer of a big structure so the preview stays light. */
    private static List<Placement.Change> shell(Placement placement, int max) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Placement.Change c : placement.preview) {
            Block b = c.block();
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minY = Math.min(minY, b.getY());
            maxY = Math.max(maxY, b.getY());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }
        List<Placement.Change> outer = new ArrayList<>();
        for (Placement.Change c : placement.preview) {
            Block b = c.block();
            if (b.getX() == minX || b.getX() == maxX || b.getY() == minY || b.getY() == maxY || b.getZ() == minZ || b.getZ() == maxZ) {
                outer.add(c);
                if (outer.size() >= max) {
                    break;
                }
            }
        }
        return outer;
    }

    boolean cancelPreview(Player player) {
        Preview preview = previews.remove(player.getUniqueId());
        if (preview == null) {
            return false;
        }
        preview.displays().forEach(d -> {
            if (d.isValid()) {
                d.remove();
            }
        });
        return true;
    }

    private void expirePreviews() {
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(previews.keySet())) {
            if (previews.get(id).expiresAt() < now) {
                Player player = Bukkit.getPlayer(id);
                if (player != null) {
                    cancelPreview(player);
                    plugin.send(player, "anteprima-scaduta");
                } else {
                    previews.remove(id).displays().forEach(d -> d.remove());
                }
            }
        }
        undos.values().removeIf(u -> u.expiresAt() < now);
    }

    void shutdown() {
        for (Preview preview : previews.values()) {
            preview.displays().forEach(d -> d.remove());
        }
        previews.clear();
    }

    // ---------------------------------------------------------------- building

    private void confirm(Player player, Placement placement) {
        if (placement.problem != null) {
            showPreview(player, placement);
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!placement.type.id().equals(plugin.items().read(hand))) {
            return;
        }
        if (!mayBuild(player, placement, hand)) {
            plugin.send(player, "zona-protetta");
            return;
        }
        cancelPreview(player);
        if (player.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
            player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        }

        List<BlockState> previous = new ArrayList<>(placement.changes.size());
        for (Placement.Change change : placement.changes) {
            previous.add(change.block().getState());
        }
        long minutes = plugin.getConfig().getLong("annulla.minuti", 10);
        undos.put(player.getUniqueId(), new Undo(placement.type.id(), previous, placement.changes,
                System.currentTimeMillis() + minutes * 60_000));

        List<Placement.Change> ordered = new ArrayList<>(placement.changes);
        ordered.sort(Comparator.comparingInt(c -> c.block().getY())); // bottom-up, foundation first
        int perTick = plugin.getConfig().getInt("costruzione.blocchi-per-tick", 0);
        if (perTick <= 0) {
            ordered.forEach(c -> c.block().setBlockData(c.data(), false));
            finished(player, placement, minutes);
        } else {
            new BukkitRunnable() {
                int index = 0;

                @Override
                public void run() {
                    for (int i = 0; i < perTick && index < ordered.size(); i++, index++) {
                        Placement.Change c = ordered.get(index);
                        c.block().setBlockData(c.data(), false);
                    }
                    if (index >= ordered.size()) {
                        cancel();
                        finished(player, placement, minutes);
                    }
                }
            }.runTaskTimer(plugin, 1L, 1L);
        }
    }

    private void finished(Player player, Placement placement, long minutes) {
        if (player.isOnline()) {
            plugin.send(player, plugin.msg("costruita").replace("{nome}", placement.type.name())
                    .replace("{minuti}", String.valueOf(minutes)));
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.4f);
        }
    }

    /**
     * Asks protection plugins (WorldGuard, GriefPrevention, ...) by firing a place event for the corners and the centre.
     * Only a few blocks are checked so logging plugins are not flooded with test events.
     */
    private boolean mayBuild(Player player, Placement placement, ItemStack hand) {
        if (player.hasPermission("prefab.admin") && player.getGameMode() == GameMode.CREATIVE) {
            return true;
        }
        List<Placement.Change> all = placement.changes;
        if (all.isEmpty()) {
            return true;
        }
        Block first = all.getFirst().block(), last = all.getLast().block();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        for (Placement.Change c : all) {
            Block b = c.block();
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minY = Math.min(minY, b.getY());
            maxY = Math.max(maxY, b.getY());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }
        var world = first.getWorld();
        List<Block> samples = new ArrayList<>(List.of(first, last,
                world.getBlockAt((minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2)));
        for (int x : new int[]{minX, maxX}) {
            for (int y : new int[]{minY, maxY}) {
                for (int z : new int[]{minZ, maxZ}) {
                    samples.add(world.getBlockAt(x, y, z));
                }
            }
        }
        for (Block block : samples) {
            BlockPlaceEvent check = new BlockPlaceEvent(block, block.getState(), placement.ground, hand, player, true,
                    EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(check);
            if (check.isCancelled() || !check.canBuild()) {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- undo

    boolean undo(Player player) {
        Undo undo = undos.remove(player.getUniqueId());
        if (undo == null || undo.expiresAt() < System.currentTimeMillis()) {
            return false;
        }
        // keep whatever the player stored in the new chests and barrels
        for (Placement.Change change : undo.placed()) {
            if (change.block().getState() instanceof Container container) {
                for (ItemStack item : container.getInventory().getContents()) {
                    if (item != null && !item.getType().isAir()) {
                        change.block().getWorld().dropItemNaturally(change.block().getLocation().add(0.5, 0.5, 0.5), item);
                    }
                }
                container.getInventory().clear();
            }
        }
        List<BlockState> states = new ArrayList<>(undo.previous());
        states.sort(Comparator.comparingInt(BlockState::getY).reversed()); // top-down
        for (BlockState state : states) {
            state.update(true, false);
        }
        PrefabType type = plugin.registry().get(undo.prefabId());
        if (type != null && player.getGameMode() != GameMode.CREATIVE) {
            player.getInventory().addItem(plugin.items().create(type, 1)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        return true;
    }
}
