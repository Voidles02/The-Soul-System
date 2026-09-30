package com.KDI.souls.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

public final class SoulAltarHolder implements InventoryHolder {
    private final Location altar;
    private Inventory inventory;
    private String previewType;
    private double previewAmount;
    private long previewCost;
    private ItemStack previewItem;

    public SoulAltarHolder(Location altar) {
        this.altar = altar.clone();
    }

    public Location getAltar() {
        return altar.clone();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public String getPreviewType() {
        return previewType;
    }

    public double getPreviewAmount() {
        return previewAmount;
    }

    public long getPreviewCost() {
        return previewCost;
    }

    public ItemStack getPreviewItem() {
        return previewItem == null ? null : previewItem.clone();
    }

    public void setPreview(ItemStack item, String type, double amount, long cost) {
        this.previewItem = item.clone();
        this.previewType = type;
        this.previewAmount = amount;
        this.previewCost = cost;
    }

    public void clearPreview() {
        this.previewType = null;
        this.previewAmount = 0;
        this.previewCost = 0;
        this.previewItem = null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}