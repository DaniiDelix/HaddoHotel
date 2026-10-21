package ch.haddo.wands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Wand tiers from the config, wand items and their recipes. */
final class WandItems {

    private final WandsPlugin plugin;
    private final NamespacedKey tierKey;
    private final NamespacedKey modeKey;
    private final Map<String, WandTier> tiers = new LinkedHashMap<>();
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    WandItems(WandsPlugin plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "tipo");
        this.modeKey = new NamespacedKey(plugin, "modalita");
    }

    void load() {
        tiers.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("bacchette");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection t = section.getConfigurationSection(id);
                if (t == null) {
                    continue;
                }
                NamespacedKey model = NamespacedKey.fromString(t.getString("modello", "minecraft:stick"));
                tiers.put(id.toLowerCase(Locale.ROOT), new WandTier(id.toLowerCase(Locale.ROOT), t.getString("nome", id),
                        Math.max(1, t.getInt("max-blocchi", 9)), Math.max(0, t.getInt("durata", 0)),
                        model == null ? NamespacedKey.minecraft("stick") : model, t.getBoolean("consuma", true)));
            }
        }
        registerRecipes();
    }

    Map<String, WandTier> tiers() {
        return tiers;
    }

    ItemStack create(WandTier tier) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(MiniMessage.miniMessage().deserialize(tier.name()));
        meta.setItemModel(tier.model());
        meta.setMaxStackSize(1);
        if (tier.durability() > 0 && meta instanceof Damageable damageable) {
            damageable.setMaxDamage(tier.durability());
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(tierKey, PersistentDataType.STRING, tier.id());
        pdc.set(modeKey, PersistentDataType.STRING, WandMode.SURFACE_SAME.name());
        item.setItemMeta(meta);
        updateLore(item, tier, WandMode.SURFACE_SAME);
        return item;
    }

    /** The wand tier of an item, or null if it is not a wand. */
    WandTier tier(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(tierKey, PersistentDataType.STRING);
        return id == null ? null : tiers.get(id);
    }

    boolean isWand(ItemStack item) {
        return item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(tierKey);
    }

    WandMode mode(ItemStack item) {
        return WandMode.parse(item.getItemMeta().getPersistentDataContainer().get(modeKey, PersistentDataType.STRING));
    }

    void setMode(ItemStack item, WandTier tier, WandMode mode) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        updateLore(item, tier, mode);
    }

    private void updateLore(ItemStack item, WandTier tier, WandMode mode) {
        ItemMeta meta = item.getItemMeta();
        meta.lore(List.of(
                line("<gray>Modalità: <white>" + mode.label),
                line("<gray>Fino a <white>" + tier.maxBlocks() + "</white> blocchi per clic"),
                line("<white>Clic destro<gray> su un blocco: piazza copie"),
                line("<white>Clic sinistro<gray>: cambia modalità")));
        item.setItemMeta(meta);
    }

    private static Component line(String mini) {
        return MiniMessage.miniMessage().deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    private void registerRecipes() {
        recipeKeys.forEach(Bukkit::removeRecipe);
        recipeKeys.clear();
        if (!plugin.getConfig().getBoolean("ricette.attive", true)) {
            return;
        }
        recipe("pietra", new RecipeChoice.MaterialChoice(Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.BLACKSTONE));
        recipe("ferro", new RecipeChoice.MaterialChoice(Material.IRON_INGOT));
        recipe("diamante", new RecipeChoice.MaterialChoice(Material.DIAMOND));
        recipe("netherite", new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT));
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipes(recipeKeys));
    }

    private void recipe(String tierId, RecipeChoice head) {
        WandTier tier = tiers.get(tierId);
        if (tier == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "bacchetta_" + tierId);
        ShapedRecipe recipe = new ShapedRecipe(key, create(tier));
        recipe.shape("  M", " S ", "S  ");
        recipe.setIngredient('M', head);
        recipe.setIngredient('S', new RecipeChoice.MaterialChoice(Material.STICK));
        Bukkit.addRecipe(recipe);
        recipeKeys.add(key);
    }

    void unregisterRecipes() {
        recipeKeys.forEach(Bukkit::removeRecipe);
        recipeKeys.clear();
    }

    List<NamespacedKey> recipeKeys() {
        return recipeKeys;
    }
}
