package com.KDI.souls.guide;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class GuideListener implements Listener {
    private final GuideService guide;

    public GuideListener(GuideService guide) {
        this.guide = guide;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        guide.autoOpen(event.getPlayer());
    }
}