package com.ninja.tags.commands;

import com.ninja.tags.NinjaTagsPlugin;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.ui.TagsUiController;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;

public class TagsCommand extends AbstractCommand {
    private final NinjaTagsPlugin plugin;
    private final TagDao tagDao;
    private final TagsUiController uiController;

    public TagsCommand(NinjaTagsPlugin plugin, TagDao tagDao, TagsUiController uiController) {
        super("tags", "Open the tags menu");
        this.plugin = plugin;
        this.tagDao = tagDao;
        this.uiController = uiController;
        requirePermission("tags.open");
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        CommandSender sender = context.sender();
        if (!plugin.hasPermission(sender, "tags.open")) {
            plugin.sendMessage(sender, "You do not have permission to use this command.");
            return CompletableFuture.completedFuture(null);
        }
        if (!context.isPlayer()) {
            plugin.sendMessage(sender, "Only players can use this command.");
            return CompletableFuture.completedFuture(null);
        }

        Player player = context.senderAs(Player.class);
        if (player == null) {
            plugin.sendMessage(sender, "Only players can use this command.");
            return CompletableFuture.completedFuture(null);
        }

        try {
            plugin.touchPlayerRecord(player, tagDao);
            uiController.open(player);
        } catch (SQLException ex) {
            plugin.sendMessage(sender, "Unable to open tags right now.");
        }
        return CompletableFuture.completedFuture(null);
    }
}
