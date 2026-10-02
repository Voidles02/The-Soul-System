package com.KDI.souls.gui;

import com.KDI.souls.guide.GuideService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class SoulStatsListener implements Listener {
    private final GuideService guide;

    public SoulStatsListener(GuideService guide) {
        this.guide = guide;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SoulStatsHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getRawSlot() == 22) {
            event.getWhoClicked().closeInventory();
        } else if (event.getRawSlot() == 16 && event.getWhoClicked() instanceof Player player) {
            guide.open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SoulStatsHolder) {
            event.setCancelled(true);
        }
    }
}