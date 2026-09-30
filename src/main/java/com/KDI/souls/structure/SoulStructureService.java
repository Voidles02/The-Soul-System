package com.KDI.souls.structure;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SoulStructureService {
    private final JavaPlugin plugin;
    private final org.bukkit.NamespacedKey structureKey;

    public SoulStructureService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.structureKey = new org.bukkit.NamespacedKey(plugin, "soul_structure");
    }

    public String itemType(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().get(structureKey, PersistentDataType.STRING);
    }

    public String blockType(Block block) {
        if (block == null || !(block.getState() instanceof TileState state)) {
            return null;
        }
        return state.getPersistentDataContainer().get(structureKey, PersistentDataType.STRING);
    }

    public boolean tag(Block block, String type) {
        if (!matchesBlock(block, type) || !(block.getState() instanceof TileState state)) {
            return false;
        }
        if (type.equals(blockType(block))) {
            return true;
        }
        state.getPersistentDataContainer().set(structureKey, PersistentDataType.STRING, type);
        return state.update(true, false);
    }

    public boolean matchesBlock(Block block, String type) {
        if (block == null || type == null) {
            return false;
        }
        String material = XMaterial.matchXMaterial(block.getType()).name();
        return ("altar".equals(type) && material.equals("ENCHANTING_TABLE"))
                || ("shrine".equals(type) && material.equals("BEACON"));
    }

    public boolean isStructureBlock(Block block, String type) {
        return matchesBlock(block, type) && type.equals(blockType(block));
    }

    public boolean registerPlacedStructure(Block block, String type) {
        if (!matchesBlock(block, type)) {
            return false;
        }
        return isStructureBlock(block, type) || tag(block, type);
    }

    public ItemStack createItem(String type) {
        String material = type.equals("altar") ? "ENCHANTING_TABLE" : "BEACON";
        String name = type.equals("altar") ? "&6&lSoul Altar" : "&5&lSoul Shrine";
        ItemStack item = XMaterial.matchXMaterial(material).map(XMaterial::parseItem).orElse(null);
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(com.KDI.souls.message.MessageService.color(name));
        meta.getPersistentDataContainer().set(structureKey, PersistentDataType.STRING, type);
        item.setItemMeta(meta);
        return item;
    }
}