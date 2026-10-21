package ch.haddo.wands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class WandCommand implements CommandExecutor, TabCompleter {

    private final WandsPlugin plugin;

    WandCommand(WandsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "aiuto" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "annulla" -> {
                if (sender instanceof Player p && !plugin.listener().undo(p)) {
                    plugin.send(p, "niente-da-annullare");
                }
            }
            case "dai" -> {
                if (!sender.hasPermission("wands.admin")) {
                    plugin.send(sender, "niente-permesso");
                } else {
                    give(sender, args);
                }
            }
            case "ricarica" -> {
                if (!sender.hasPermission("wands.admin")) {
                    plugin.send(sender, "niente-permesso");
                } else {
                    plugin.reload();
                    plugin.send(sender, "<green>Configurazione ricaricata.");
                }
            }
            default -> {
                StringBuilder text = new StringBuilder("<gold>Comandi:</gold>\n<white>/" + label
                        + " annulla</white> <gray>- annulla l'ultima piazzata");
                if (sender.hasPermission("wands.admin")) {
                    text.append("\n<white>/").append(label).append(" dai <giocatore> <tipo></white> <gray>- tipi: ")
                            .append(String.join(", ", plugin.items().tiers().keySet()))
                            .append("\n<white>/").append(label).append(" ricarica</white>");
                }
                plugin.send(sender, text.toString());
            }
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.send(sender, "<red>Uso: /bacchetta dai <giocatore> <tipo>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.send(sender, "<red>Giocatore non trovato: <white>" + args[1]);
            return;
        }
        WandTier tier = plugin.items().tiers().get(args[2].toLowerCase(Locale.ROOT));
        if (tier == null) {
            plugin.send(sender, "<red>Tipo sconosciuto. Tipi: <white>" + String.join(", ", plugin.items().tiers().keySet()));
            return;
        }
        target.getInventory().addItem(plugin.items().create(tier)).values()
                .forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
        plugin.send(sender, "<green>Data " + tier.name() + "<green> a " + target.getName() + ".");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.add("annulla");
            if (sender.hasPermission("wands.admin")) {
                options.add("dai");
                options.add("ricarica");
            }
        } else if (sender.hasPermission("wands.admin") && args[0].equalsIgnoreCase("dai")) {
            if (args.length == 2) {
                Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
            } else if (args.length == 3) {
                options.addAll(plugin.items().tiers().keySet());
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        options.removeIf(o -> !o.toLowerCase(Locale.ROOT).startsWith(prefix));
        return options;
    }
}
