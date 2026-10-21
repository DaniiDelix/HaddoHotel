package ch.haddo.prefab;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

final class PrefabCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("lista", "stop", "annulla");
    private static final List<String> ADMIN_SUBS = List.of("dai", "pos1", "pos2", "salva", "elimina", "ricarica");

    private final PrefabPlugin plugin;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    PrefabCommand(PrefabPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "aiuto" : args[0].toLowerCase(Locale.ROOT);
        if (ADMIN_SUBS.contains(sub) && !sender.hasPermission("prefab.admin")) {
            plugin.send(sender, "niente-permesso");
            return true;
        }
        switch (sub) {
            case "lista" -> list(sender);
            case "stop" -> {
                if (sender instanceof Player p && plugin.placements().cancelPreview(p)) {
                    plugin.send(p, "anteprima-annullata");
                }
            }
            case "annulla" -> {
                if (sender instanceof Player p) {
                    plugin.send(p, plugin.placements().undo(p) ? "annullata" : "niente-da-annullare");
                }
            }
            case "dai" -> give(sender, args);
            case "pos1", "pos2" -> position(sender, sub.equals("pos1") ? 0 : 1);
            case "salva" -> save(sender, args);
            case "elimina" -> delete(sender, args);
            case "ricarica" -> {
                plugin.reload();
                plugin.send(sender, "<green>Configurazione e strutture ricaricate (" + plugin.registry().all().size() + ").");
            }
            default -> help(sender, label);
        }
        return true;
    }

    private void help(CommandSender sender, String label) {
        StringBuilder text = new StringBuilder("<gold>Comandi:</gold>"
                + "\n<white>/" + label + " lista</white> <gray>- strutture disponibili"
                + "\n<white>/" + label + " stop</white> <gray>- chiude l'anteprima"
                + "\n<white>/" + label + " annulla</white> <gray>- annulla l'ultima costruzione");
        if (sender.hasPermission("prefab.admin")) {
            text.append("\n<white>/").append(label).append(" dai <giocatore> <id> [quantità]</white>")
                    .append("\n<white>/").append(label).append(" pos1</white> <gray>/</gray> <white>pos2</white> <gray>- angoli (blocco che guardi)")
                    .append("\n<white>/").append(label).append(" salva <id> [nome]</white> <gray>- guarda la facciata mentre salvi")
                    .append("\n<white>/").append(label).append(" elimina <id></white>")
                    .append("\n<white>/").append(label).append(" ricarica</white>");
        }
        plugin.send(sender, text.toString());
    }

    private void list(CommandSender sender) {
        StringBuilder text = new StringBuilder("<gold>Strutture:</gold>");
        for (PrefabType type : plugin.registry().all()) {
            Blueprint bp = type.blueprint();
            text.append("\n<white>").append(type.id()).append("</white> <gray>- ").append(type.name())
                    .append(" <dark_gray>(").append(bp.width()).append("×").append(bp.depth()).append("×").append(bp.height())
                    .append(type.builtin() ? "" : ", personalizzata").append(")");
        }
        plugin.send(sender, text.toString());
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.send(sender, "<red>Uso: /prefab dai <giocatore> <id> [quantità]");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(sender, "<red>Giocatore non trovato: <white>" + args[1]);
            return;
        }
        PrefabType type = plugin.registry().get(args[2]);
        if (type == null) {
            plugin.send(sender, plugin.msg("sconosciuto").replace("{id}", args[2]));
            return;
        }
        int amount = 1;
        if (args.length >= 4) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                plugin.send(sender, "<red>Quantità non valida: <white>" + args[3]);
                return;
            }
        }
        target.getInventory().addItem(plugin.items().create(type, amount)).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.send(sender, "<green>Dato " + amount + "× " + type.name() + "<green> a " + target.getName() + ".");
    }

    private void position(CommandSender sender, int index) {
        if (!(sender instanceof Player player)) {
            return;
        }
        Block block = player.getTargetBlockExact(20, FluidCollisionMode.NEVER);
        Location location = block != null ? block.getLocation() : player.getLocation().getBlock().getLocation();
        selections.computeIfAbsent(player.getUniqueId(), k -> new Location[2])[index] = location;
        plugin.send(player, "<green>Angolo " + (index + 1) + " impostato: <white>"
                + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
    }

    private void save(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return;
        }
        if (args.length < 2 || !PrefabRegistry.VALID_ID.matcher(args[1].toLowerCase(Locale.ROOT)).matches()) {
            plugin.send(player, "<red>Uso: /prefab salva <id> [nome] <gray>(id: lettere minuscole, numeri e _)");
            return;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        PrefabType existing = plugin.registry().get(id);
        if (existing != null && existing.builtin()) {
            plugin.send(player, "<red>Questo id è già usato da una struttura inclusa.");
            return;
        }
        Location[] sel = selections.get(player.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null || sel[0].getWorld() != sel[1].getWorld()) {
            plugin.send(player, "<red>Prima imposta gli angoli con <white>/prefab pos1</white> e <white>/prefab pos2</white>.");
            return;
        }
        int sx = Math.abs(sel[0].getBlockX() - sel[1].getBlockX()) + 1;
        int sy = Math.abs(sel[0].getBlockY() - sel[1].getBlockY()) + 1;
        int sz = Math.abs(sel[0].getBlockZ() - sel[1].getBlockZ()) + 1;
        if (sx > 48 || sy > 48 || sz > 48) {
            plugin.send(player, "<red>Struttura troppo grande (massimo 48×48×48).");
            return;
        }
        String name = args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "<white>" + id;
        Block below = sel[0].getBlockY() <= sel[1].getBlockY() ? sel[0].getBlock() : sel[1].getBlock();
        NamespacedKey icon = below.getType().isItem() ? below.getType().getKey() : NamespacedKey.minecraft("paper");
        try {
            plugin.registry().save(id, name, icon, sel[0], sel[1], player.getFacing());
            plugin.items().registerRecipes(plugin.registry());
            plugin.send(player, "<green>Struttura <white>" + id + "</white> salvata (" + sx + "×" + sz + "×" + sy
                    + "). Usa <white>/prefab dai " + player.getName() + " " + id + "</white>.");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Salvataggio fallito", e);
            plugin.send(player, "<red>Salvataggio fallito: " + e.getMessage());
        }
    }

    private void delete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.send(sender, "<red>Uso: /prefab elimina <id>");
            return;
        }
        try {
            if (plugin.registry().delete(args[1])) {
                plugin.send(sender, "<green>Struttura eliminata.");
            } else {
                plugin.send(sender, "<red>Si possono eliminare solo strutture personalizzate esistenti.");
            }
        } catch (IOException e) {
            plugin.send(sender, "<red>Errore: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(PLAYER_SUBS);
            if (sender.hasPermission("prefab.admin")) {
                options.addAll(ADMIN_SUBS);
            }
        } else if (sender.hasPermission("prefab.admin")) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("dai") && args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            } else if ((sub.equals("dai") && args.length == 3) || (sub.equals("elimina") && args.length == 2)) {
                plugin.registry().all().stream()
                        .filter(t -> sub.equals("dai") || !t.builtin())
                        .forEach(t -> options.add(t.id()));
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
