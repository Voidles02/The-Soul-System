package com.KDI.souls.guide;

import com.KDI.souls.message.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class GuideCommand implements CommandExecutor {
    private final GuideService guide;
    private final MessageService messages;

    public GuideCommand(GuideService guide, MessageService messages) {
        this.guide = guide;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "player-only");
            return true;
        }
        if (!sender.hasPermission("souls.use") && !sender.hasPermission("souls.admin")) {
            messages.send(sender, "no-permission");
            return true;
        }
        guide.open(player);
        return true;
    }
}