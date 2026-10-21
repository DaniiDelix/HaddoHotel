package ch.haddo.prefab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/** Creates and recognises prefab items and registers their crafting recipes. */
public final class PrefabItems {

    private final PrefabPlugin plugin;
    private final NamespacedKey idKey;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    PrefabItems(PrefabPlugin plugin) {
        this.plugin = plugin;
        this.idKey = new NamespacedKey(plugin, "id");
    }

    public ItemStack create(PrefabType type, int amount) {
        ItemStack item = new ItemStack(Material.PAPER, amount);
        ItemMeta meta = item.getItemMeta();
        meta.itemName(MiniMessage.miniMessage().deserialize(type.name()));
        meta.setItemModel(type.icon());
        meta.setEnchantmentGlintOverride(true);
        meta.setMaxStackSize(16);
        Blueprint bp = type.blueprint();
        meta.lore(List.of(
                plain("<gray>Struttura pronta: " + bp.width() + "×" + bp.depth() + ", alta " + bp.height()),
                plain("<white>Clic destro<gray> sul terreno: anteprima"),
                plain("<white>Clic destro<gray> di nuovo: costruisci"),
                plain("<white>Shift + clic destro<gray>: annulla anteprima")));
        meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, type.id());
        item.setItemMeta(meta);
        return item;
    }

    private static Component plain(String mini) {
        return MiniMessage.miniMessage().deserialize(mini).decoration(TextDecoration.ITALIC, false);
    }

    /** Returns the prefab id stored on the item, or null if it is not a prefab item. */
    public String read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }

    void registerRecipes(PrefabRegistry registry) {
        unregisterRecipes();
        if (!plugin.getConfig().getBoolean("ricette.attive", true)) {
            return;
        }
        recipe(registry, "casetta", new String[]{"LGL", "CDC", "LBL"},
                'L', new RecipeChoice.MaterialChoice(Tag.LOGS), 'G', new RecipeChoice.MaterialChoice(Material.GLASS),
                'C', new RecipeChoice.MaterialChoice(Material.COBBLESTONE), 'D', new RecipeChoice.MaterialChoice(Tag.WOODEN_DOORS),
                'B', new RecipeChoice.MaterialChoice(Tag.BEDS));
        recipe(registry, "fattoria", new String[]{"FSF", "SWS", "FHF"},
                'F', new RecipeChoice.MaterialChoice(Tag.WOODEN_FENCES), 'S', new RecipeChoice.MaterialChoice(Material.WHEAT_SEEDS),
                'W', new RecipeChoice.MaterialChoice(Material.WATER_BUCKET), 'H', new RecipeChoice.MaterialChoice(Tag.ITEMS_HOES));
        recipe(registry, "magazzino", new String[]{"XCX", "CLC", "XCX"},
                'X', new RecipeChoice.MaterialChoice(Material.STONE_BRICKS), 'C', new RecipeChoice.MaterialChoice(Material.BARREL),
                'L', new RecipeChoice.MaterialChoice(Material.LANTERN));
        recipe(registry, "torre", new String[]{"SFS", "SLS", "SDS"},
                'S', new RecipeChoice.MaterialChoice(Material.STONE_BRICKS), 'F', new RecipeChoice.MaterialChoice(Tag.WOODEN_FENCES),
                'L', new RecipeChoice.MaterialChoice(Material.LADDER), 'D', new RecipeChoice.MaterialChoice(Tag.WOODEN_DOORS));
        recipe(registry, "recinto", new String[]{"FFF", "FHF", "FGF"},
                'F', new RecipeChoice.MaterialChoice(Tag.WOODEN_FENCES), 'H', new RecipeChoice.MaterialChoice(Material.HAY_BLOCK),
                'G', new RecipeChoice.MaterialChoice(Tag.FENCE_GATES));
        Bukkit.getOnlinePlayers().forEach(p -> p.discoverRecipes(recipeKeys));
    }

    private void recipe(PrefabRegistry registry, String id, String[] shape, Object... ingredients) {
        PrefabType type = registry.get(id);
        if (type == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, "ricetta_" + id);
        ShapedRecipe recipe = new ShapedRecipe(key, create(type, 1));
        recipe.shape(shape);
        for (int i = 0; i < ingredients.length; i += 2) {
            recipe.setIngredient((Character) ingredients[i], (RecipeChoice) ingredients[i + 1]);
        }
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
