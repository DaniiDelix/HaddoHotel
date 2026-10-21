package ch.haddo.prefab;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.structure.Structure;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

/** Built-in prefabs plus the ones saved in game, stored as vanilla .nbt structure files. */
public final class PrefabRegistry {

    public static final Pattern VALID_ID = Pattern.compile("[a-z0-9_]{1,32}");

    private final PrefabPlugin plugin;
    private final File folder;
    private final File indexFile;
    private final Map<String, PrefabType> types = new LinkedHashMap<>();

    PrefabRegistry(PrefabPlugin plugin) {
        this.plugin = plugin;
        this.folder = new File(plugin.getDataFolder(), "strutture");
        this.indexFile = new File(folder, "strutture.yml");
    }

    void load() {
        types.clear();
        register(new PrefabType("casetta", "<gold>Casetta", NamespacedKey.minecraft("bricks"), BuiltinBlueprints.house(), true));
        register(new PrefabType("fattoria", "<yellow>Fattoria", NamespacedKey.minecraft("wheat"), BuiltinBlueprints.farm(), true));
        register(new PrefabType("magazzino", "<aqua>Magazzino", NamespacedKey.minecraft("barrel"), BuiltinBlueprints.storage(), true));
        register(new PrefabType("torre", "<gray>Torre di guardia", NamespacedKey.minecraft("stone_bricks"), BuiltinBlueprints.tower(), true));
        register(new PrefabType("recinto", "<green>Recinto per animali", NamespacedKey.minecraft("lead"), BuiltinBlueprints.pen(), true));

        if (!indexFile.exists()) {
            return;
        }
        YamlConfiguration index = YamlConfiguration.loadConfiguration(indexFile);
        for (String id : index.getKeys(false)) {
            ConfigurationSection section = index.getConfigurationSection(id);
            if (section == null || types.containsKey(id)) {
                continue;
            }
            File file = new File(folder, id + ".nbt");
            try {
                Structure structure = Bukkit.getStructureManager().loadStructure(file);
                BlockFace facing = BlockFace.valueOf(section.getString("direzione", "SOUTH"));
                Blueprint blueprint = toBlueprint(structure).rotatedClockwise(-quarterTurnsFromSouth(facing));
                NamespacedKey icon = NamespacedKey.fromString(section.getString("icona", "minecraft:paper"));
                register(new PrefabType(id, section.getString("nome", id), icon == null ? NamespacedKey.minecraft("paper") : icon,
                        blueprint.connect(), false));
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Impossibile caricare la struttura " + id, e);
            }
        }
    }

    private void register(PrefabType type) {
        types.put(type.id(), type);
    }

    public PrefabType get(String id) {
        return id == null ? null : types.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<PrefabType> all() {
        return types.values();
    }

    /**
     * Saves the blocks between two corners as a new prefab. {@code facing} is the direction the admin was
     * looking at while saving: the side of the build turned towards them becomes the front.
     */
    void save(String id, String name, NamespacedKey icon, Location corner1, Location corner2, BlockFace facing) throws IOException {
        Structure structure = Bukkit.getStructureManager().createStructure();
        Location min = new Location(corner1.getWorld(), Math.min(corner1.getBlockX(), corner2.getBlockX()),
                Math.min(corner1.getBlockY(), corner2.getBlockY()), Math.min(corner1.getBlockZ(), corner2.getBlockZ()));
        Location max = new Location(corner1.getWorld(), Math.max(corner1.getBlockX(), corner2.getBlockX()),
                Math.max(corner1.getBlockY(), corner2.getBlockY()), Math.max(corner1.getBlockZ(), corner2.getBlockZ()));
        structure.fill(min, max.clone().add(1, 1, 1), false);
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IOException("Impossibile creare la cartella " + folder);
        }
        Bukkit.getStructureManager().saveStructure(new File(folder, id + ".nbt"), structure);

        YamlConfiguration index = indexFile.exists() ? YamlConfiguration.loadConfiguration(indexFile) : new YamlConfiguration();
        index.set(id + ".nome", name);
        index.set(id + ".icona", icon.toString());
        index.set(id + ".direzione", facing.name());
        index.save(indexFile);
        load();
    }

    boolean delete(String id) throws IOException {
        PrefabType type = get(id);
        if (type == null || type.builtin()) {
            return false;
        }
        YamlConfiguration index = YamlConfiguration.loadConfiguration(indexFile);
        index.set(type.id(), null);
        index.save(indexFile);
        File file = new File(folder, type.id() + ".nbt");
        if (file.exists() && !file.delete()) {
            throw new IOException("Impossibile eliminare " + file);
        }
        load();
        return true;
    }

    /** SOUTH = 0, WEST = 1, NORTH = 2, EAST = 3 clockwise quarter turns. */
    static int quarterTurnsFromSouth(BlockFace facing) {
        return switch (facing) {
            case WEST -> 1;
            case NORTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    private static Blueprint toBlueprint(Structure structure) {
        var size = structure.getSize();
        Blueprint blueprint = new Blueprint(size.getBlockX(), size.getBlockY(), size.getBlockZ());
        if (structure.getPaletteCount() == 0) {
            return blueprint;
        }
        for (BlockState state : structure.getPalettes().getFirst().getBlocks()) {
            BlockData data = state.getBlockData();
            if (data.getMaterial().isAir() || data.getMaterial().name().equals("STRUCTURE_VOID")) {
                continue;
            }
            blueprint.set(state.getX(), state.getY(), state.getZ(), data.clone());
        }
        return blueprint;
    }
}
