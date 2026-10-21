package ch.haddo.prefab;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class PrefabPlugin extends JavaPlugin {

    private PrefabRegistry registry;
    private PrefabItems items;
    private PlacementManager placements;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        registry = new PrefabRegistry(this);
        items = new PrefabItems(this);
        placements = new PlacementManager(this);
        reload();

        getServer().getPluginManager().registerEvents(placements, this);
        PrefabCommand command = new PrefabCommand(this);
        PluginCommand prefab = getCommand("prefab");
        if (prefab != null) {
            prefab.setExecutor(command);
            prefab.setTabCompleter(command);
        }
        getLogger().info(registry.all().size() + " strutture caricate.");
    }

    @Override
    public void onDisable() {
        if (placements != null) {
            placements.shutdown();
        }
        if (items != null) {
            items.unregisterRecipes();
        }
    }

    void reload() {
        reloadConfig();
        registry.load();
        items.registerRecipes(registry);
    }

    PrefabRegistry registry() {
        return registry;
    }

    PrefabItems items() {
        return items;
    }

    PlacementManager placements() {
        return placements;
    }

    /** Message text from the config, or the key itself if missing. */
    String msg(String key) {
        return getConfig().getString("messaggi." + key, key);
    }

    /** Sends a config message (by key) or an already resolved MiniMessage string, with the prefix. */
    void send(CommandSender to, String keyOrText) {
        String text = getConfig().isString("messaggi." + keyOrText) ? msg(keyOrText) : keyOrText;
        to.sendRichMessage(msg("prefisso") + text);
    }
}
