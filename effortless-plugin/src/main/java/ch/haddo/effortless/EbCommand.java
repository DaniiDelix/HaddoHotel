package ch.haddo.effortless;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class EbCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("menu", "annulla", "ripeti", "centro", "ricarica");

    private final EffortlessPlugin plugin;

    EbCommand(EffortlessPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("ricarica")) {
            if (!sender.hasPermission("effortless.admin")) {
                plugin.send(sender, "niente-permesso");
                return true;
            }
            plugin.reloadConfig();
            plugin.send(sender, "<green>Configurazione ricaricata.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.send(sender, "<red>Solo in gioco.");
            return true;
        }
        if (!player.hasPermission("effortless.use")) {
            plugin.send(player, "niente-permesso");
            return true;
        }
        switch (sub) {
            case "annulla" -> plugin.undo(player);
            case "ripeti" -> plugin.redo(player);
            case "centro" -> plugin.setCentre(player);
            default -> player.openInventory(new BuildMenu(plugin, player).getInventory());
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String sub : SUBS) {
                if (sub.startsWith(prefix) && (!sub.equals("ricarica") || sender.hasPermission("effortless.admin"))) {
                    options.add(sub);
                }
            }
        }
        return options;
    }
}
