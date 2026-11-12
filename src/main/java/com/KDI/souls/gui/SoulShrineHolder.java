package com.KDI.souls.gui;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class SoulShrineHolder implements InventoryHolder {
    private final Location shrine;
    private Inventory inventory;

    public SoulShrineHolder(Location shrine) {
        this.shrine = shrine.clone();
    }

    public Location getShrine() {
        return shrine.clone();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}