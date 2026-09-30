package com.KDI.souls.recipe;

import com.KDI.souls.config.SoulsConfig;
import com.cryptomorin.xseries.XMaterial;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulRecipeService {
    private final JavaPlugin plugin;
    private final SoulsConfig config;
    private final NamespacedKey altarKey;
    private final NamespacedKey shrineKey;
    private final NamespacedKey itemTypeKey;
    private boolean altarRegistered;
    private boolean shrineRegistered;

    public SoulRecipeService(JavaPlugin plugin, SoulsConfig config) {
        this.plugin = plugin;
        this.config = config;
        this.altarKey = new NamespacedKey(plugin, "soul_altar_recipe");
        this.shrineKey = new NamespacedKey(plugin, "soul_shrine_recipe");
        this.itemTypeKey = new NamespacedKey(plugin, "soul_structure");
    }

    public void registerRecipes() {
        if (altarRegistered) {
            plugin.getServer().removeRecipe(altarKey);
            altarRegistered = false;
        }
        if (shrineRegistered) {
            plugin.getServer().removeRecipe(shrineKey);
            shrineRegistered = false;
        }
        if (config.featureEnabled("altar")) {
            altarRegistered = plugin.getServer().addRecipe(createAltarRecipe());
        }
        if (config.featureEnabled("shrines")) {
            shrineRegistered = plugin.getServer().addRecipe(createShrineRecipe());
        }
    }

    public ItemStack createAltarItem() {
        return createStructure("ENCHANTING_TABLE", "&6&lSoul Altar", "altar");
    }

    public ItemStack createShrineItem() {
        return createStructure("BEACON", "&5&lSoul Shrine", "shrine");
    }

    private ShapedRecipe createAltarRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(altarKey, createAltarItem());
        recipe.shape("GNG", "DCD", "OOO");
        recipe.setIngredient('G', material("GOLD_INGOT"));
        recipe.setIngredient('N', material("NETHER_STAR"));
        recipe.setIngredient('D', material("DIAMOND_BLOCK"));
        recipe.setIngredient('C', material("CRYING_OBSIDIAN"));
        recipe.setIngredient('O', material("OBSIDIAN"));
        return recipe;
    }

    private ShapedRecipe createShrineRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(shrineKey, createShrineItem());
        recipe.shape("ASA", "GNG", "OOO");
        recipe.setIngredient('A', material("AMETHYST_SHARD"));
        recipe.setIngredient('S', material("SOUL_SAND"));
        recipe.setIngredient('G', material("GOLD_INGOT"));
        recipe.setIngredient('N', material("NETHER_STAR"));
        recipe.setIngredient('O', material("OBSIDIAN"));
        return recipe;
    }

    private ItemStack createStructure(String materialName, String name, String type) {
        ItemStack item = XMaterial.matchXMaterial(materialName).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            throw new IllegalStateException("Missing material for Soul structure: " + materialName);
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(com.KDI.souls.message.MessageService.color(name));
        meta.getPersistentDataContainer().set(itemTypeKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }

    private Material material(String name) {
        return XMaterial.matchXMaterial(name)
                .map(XMaterial::parseMaterial)
                .orElseThrow(() -> new IllegalStateException("Missing recipe material: " + name));
    }
}