package com.ninja.tags.commands;

import com.ninja.tags.NinjaTagsPlugin;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.ui.TagsUiController;
import com.hytale.server.command.CommandSender;
import com.hytale.server.player.Player;

import java.sql.SQLException;

public class TagsCommand {
    private final NinjaTagsPlugin plugin;
    private final TagDao tagDao;
    private final TagsUiController uiController;

    public TagsCommand(NinjaTagsPlugin plugin, TagDao tagDao, TagsUiController uiController) {
        this.plugin = plugin;
        this.tagDao = tagDao;
        this.uiController = uiController;
    }

    public void execute(CommandSender sender) {
        if (!plugin.hasPermission(sender, "tags.open")) {
            plugin.sendMessage(sender, "You do not have permission to use this command.");
            return;
        }
        if (!(sender instanceof Player player)) {
            plugin.sendMessage(sender, "Only players can use this command.");
            return;
        }

        try {
            plugin.touchPlayerRecord(player, tagDao);
            uiController.open(player);
        } catch (SQLException ex) {
            plugin.sendMessage(sender, "Unable to open tags right now.");
        }
    }
}
