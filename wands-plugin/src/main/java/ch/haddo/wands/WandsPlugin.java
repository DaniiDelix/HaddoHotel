package ch.haddo.wands;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WandsPlugin extends JavaPlugin {

    private WandItems items;
    private WandListener listener;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        items = new WandItems(this);
        items.load();
        listener = new WandListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        WandCommand command = new WandCommand(this);
        PluginCommand wand = getCommand("bacchetta");
        if (wand != null) {
            wand.setExecutor(command);
            wand.setTabCompleter(command);
        }
        getLogger().info(items.tiers().size() + " tipi di bacchetta caricati.");
    }

    @Override
    public void onDisable() {
        if (items != null) {
            items.unregisterRecipes();
        }
    }

    void reload() {
        reloadConfig();
        items.load();
    }

    WandItems items() {
        return items;
    }

    WandListener listener() {
        return listener;
    }

    String msg(String key) {
        return getConfig().getString("messaggi." + key, key);
    }

    /** Sends a config message (by key) or an already resolved MiniMessage string, with the prefix. */
    void send(CommandSender to, String keyOrText) {
        String text = getConfig().isString("messaggi." + keyOrText) ? msg(keyOrText) : keyOrText;
        to.sendRichMessage(msg("prefisso") + text);
    }
}
