package ch.haddo.effortless;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Chest menu used instead of the mod's radial menu (opened with Shift + F or /eb). */
final class BuildMenu implements InventoryHolder {

    static final int FILL = 9, MIRROR_X = 11, MIRROR_Y = 12, MIRROR_Z = 13, RADIAL = 14, ARRAY_COUNT = 15, ARRAY_STEP = 16,
            CENTRE = 17, UNDO = 18, REDO = 19, MODIFIERS_OFF = 21, INFO = 22, CLOSE = 26;

    private final EffortlessPlugin plugin;
    private final Player player;
    private final Inventory inventory;

    BuildMenu(EffortlessPlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, MiniMessage.miniMessage().deserialize("<dark_green>Costruzione"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    void render() {
        PlayerState s = plugin.state(player);
        Modifiers m = s.modifiers;
        inventory.clear();
        for (BuildMode mode : BuildMode.values()) {
            inventory.setItem(mode.ordinal(), item(mode.icon, (s.mode == mode ? "<green>▶ " : "<white>") + mode.label,
                    s.mode == mode, "<gray>" + mode.help, mode.points > 1 ? "<gray>Punti da cliccare: <white>" + mode.points : ""));
        }
        inventory.setItem(FILL, item(Material.BRICKS, "<white>Riempimento: <yellow>" + s.fill.label, false,
                "<gray>Clic per cambiare (Pieno / Vuoto / Solo spigoli)"));
        inventory.setItem(MIRROR_X, toggle(Material.RED_CONCRETE, "Specchio X (est-ovest)", m.mirrorX));
        inventory.setItem(MIRROR_Y, toggle(Material.LIME_CONCRETE, "Specchio Y (alto-basso)", m.mirrorY));
        inventory.setItem(MIRROR_Z, toggle(Material.BLUE_CONCRETE, "Specchio Z (nord-sud)", m.mirrorZ));
        inventory.setItem(RADIAL, item(Material.COMPASS, "<white>Simmetria radiale: <yellow>" + (m.radial > 1 ? m.radial + " parti" : "spenta"),
                m.radial > 1, "<gray>Sinistro: aumenta, destro: diminuisce", "<gray>Ruota attorno al centro"));
        inventory.setItem(ARRAY_COUNT, item(Material.REPEATER, "<white>Ripeti: <yellow>" + (m.arrayCount > 0 ? "×" + m.arrayCount : "spento"),
                m.arrayCount > 0, "<gray>Sinistro: aumenta, destro: diminuisce",
                "<gray>Direzione: <white>" + direction(m.arrayDirection) + " <gray>(quella in cui guardi quando clicchi)"));
        inventory.setItem(ARRAY_STEP, item(Material.LADDER, "<white>Passo ripetizione: <yellow>" + m.arrayStep, false,
                "<gray>Distanza tra una copia e l'altra", "<gray>Sinistro: aumenta, destro: diminuisce"));
        Pos c = m.centre;
        inventory.setItem(CENTRE, item(Material.TARGET, "<white>Imposta centro qui", false,
                "<gray>Centro di specchio e simmetria radiale",
                c == null ? "<gray>Non impostato" : "<gray>Attuale: <white>" + c.x() + " " + c.y() + " " + c.z()));
        inventory.setItem(UNDO, item(Material.ARROW, "<white>Annulla", false, "<gray>Annulla l'ultima azione (" + s.undo.size() + ")"));
        inventory.setItem(REDO, item(Material.SPECTRAL_ARROW, "<white>Ripeti azione", false, "<gray>Rifà l'azione annullata (" + s.redo.size() + ")"));
        inventory.setItem(MODIFIERS_OFF, item(Material.BARRIER, "<white>Spegni tutti i modificatori", false));
        inventory.setItem(INFO, item(Material.BOOK, "<gold>Come si usa", false,
                "<gray>1. Scegli una forma e prendi un blocco in mano",
                "<gray>2. <white>Clic destro</white> su un blocco: primo punto",
                "<gray>3. Guarda dove finire e <white>clic destro</white> di nuovo",
                "<gray>4. <white>Clic sinistro</white> annulla la forma in corso",
                "<gray><white>Shift + F</white> apre questo menu"));
        inventory.setItem(CLOSE, item(Material.OAK_DOOR, "<white>Chiudi", false));
    }

    void click(int slot, ClickType type) {
        PlayerState s = plugin.state(player);
        Modifiers m = s.modifiers;
        boolean less = type.isRightClick();
        if (slot >= 0 && slot < BuildMode.values().length) {
            s.mode = BuildMode.values()[slot];
            s.clearPoints();
        } else {
            switch (slot) {
                case FILL -> s.fill = s.fill.next();
                case MIRROR_X -> m.mirrorX = !m.mirrorX;
                case MIRROR_Y -> m.mirrorY = !m.mirrorY;
                case MIRROR_Z -> m.mirrorZ = !m.mirrorZ;
                case RADIAL -> m.radial = cycle(Modifiers.RADIAL_STEPS, m.radial, less);
                case ARRAY_COUNT -> {
                    m.arrayCount = Math.max(0, Math.min(16, m.arrayCount + (less ? -1 : 1)));
                    m.arrayDirection = lookDirection(player);
                }
                case ARRAY_STEP -> m.arrayStep = Math.max(1, Math.min(32, m.arrayStep + (less ? -1 : 1)));
                case CENTRE -> plugin.setCentre(player);
                case UNDO -> plugin.undo(player);
                case REDO -> plugin.redo(player);
                case MODIFIERS_OFF -> {
                    m.mirrorX = m.mirrorY = m.mirrorZ = false;
                    m.radial = 0;
                    m.arrayCount = 0;
                }
                case CLOSE -> {
                    player.closeInventory();
                    return;
                }
                default -> {
                    return;
                }
            }
        }
        if ((m.mirrorX || m.mirrorY || m.mirrorZ || m.radial > 1) && m.centre == null) {
            plugin.setCentre(player);
        }
        render();
    }

    private static int cycle(int[] steps, int current, boolean back) {
        int index = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == current) {
                index = i;
            }
        }
        index = Math.floorMod(index + (back ? -1 : 1), steps.length);
        return steps[index];
    }

    static BlockFace lookDirection(Player player) {
        float pitch = player.getLocation().getPitch();
        if (pitch < -60) {
            return BlockFace.UP;
        }
        if (pitch > 60) {
            return BlockFace.DOWN;
        }
        return player.getFacing();
    }

    private static String direction(BlockFace face) {
        return switch (face) {
            case UP -> "su";
            case DOWN -> "giù";
            case NORTH -> "nord";
            case SOUTH -> "sud";
            case EAST -> "est";
            case WEST -> "ovest";
            default -> face.name().toLowerCase();
        };
    }

    private static ItemStack toggle(Material icon, String name, boolean on) {
        return item(icon, "<white>" + name + ": " + (on ? "<green>attivo" : "<red>spento"), on, "<gray>Clic per accendere/spegnere");
    }

    private static ItemStack item(Material material, String name, boolean glow, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(MiniMessage.miniMessage().deserialize(name));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            if (!line.isEmpty()) {
                lines.add(MiniMessage.miniMessage().deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
        }
        meta.lore(lines);
        meta.setEnchantmentGlintOverride(glow);
        item.setItemMeta(meta);
        return item;
    }
}
