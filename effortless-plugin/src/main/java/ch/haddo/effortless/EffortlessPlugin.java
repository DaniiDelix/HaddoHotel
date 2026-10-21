package ch.haddo.effortless;

import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class EffortlessPlugin extends JavaPlugin {

    private final Map<UUID, PlayerState> states = new HashMap<>();
    private Builder builder;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        builder = new Builder(this);
        BuildListener listener = new BuildListener(this);
        getServer().getPluginManager().registerEvents(listener, this);
        EbCommand command = new EbCommand(this);
        PluginCommand eb = getCommand("eb");
        if (eb != null) {
            eb.setExecutor(command);
            eb.setTabCompleter(command);
        }
    }

    @Override
    public void onDisable() {
        states.values().forEach(PlayerState::clearPreview);
        states.clear();
    }

    PlayerState state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new PlayerState());
    }

    void forget(Player player) {
        PlayerState state = states.remove(player.getUniqueId());
        if (state != null) {
            state.clearPreview();
        }
    }

    Map<UUID, PlayerState> states() {
        return states;
    }

    Builder builder() {
        return builder;
    }

    void setCentre(Player player) {
        Pos centre = Pos.of(player.getLocation().getBlock());
        state(player).modifiers.centre = centre;
        send(player, msg("centro").replace("{x}", String.valueOf(centre.x())).replace("{y}", String.valueOf(centre.y()))
                .replace("{z}", String.valueOf(centre.z())));
    }

    void undo(Player player) {
        int n = builder.undo(player, state(player));
        send(player, n < 0 ? msg("niente-da-annullare") : msg("annullato").replace("{n}", String.valueOf(n)));
    }

    void redo(Player player) {
        int n = builder.redo(player, state(player));
        send(player, n < 0 ? msg("niente-da-ripetere") : msg("ripetuto").replace("{n}", String.valueOf(n)));
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
