package com.ninja.tags.commands;

import com.ninja.tags.NinjaTagsPlugin;
import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.tags.TagRegistry;
import com.ninja.tags.ui.NinjaTagsTagsUI;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.sql.SQLException;
import java.util.Optional;

public class TagsCommand extends AbstractPlayerCommand {
    private final NinjaTagsPlugin plugin;
    private final TagDao tagDao;
    private final TagRegistry tagRegistry;
    private final ConfigManager configManager;
    private final Optional<LuckPermsService> luckPermsService;

    public TagsCommand(NinjaTagsPlugin plugin,
                       TagDao tagDao,
                       TagRegistry tagRegistry,
                       ConfigManager configManager,
                       Optional<LuckPermsService> luckPermsService) {
        super("tags", "Open the tags menu");
        this.plugin = plugin;
        this.tagDao = tagDao;
        this.tagRegistry = tagRegistry;
        this.configManager = configManager;
        this.luckPermsService = luckPermsService;
        requirePermission("tags.open");
    }

    @Override
    protected void execute(CommandContext context,
                           Store<EntityStore> store,
                           Ref<EntityStore> ref,
                           PlayerRef playerRef,
                           World world) {
        CommandSender sender = context.sender();
        if (!plugin.hasPermission(sender, "tags.open")) {
            plugin.sendMessage(sender, "You do not have permission to use this command.");
            return;
        }

        Player player = context.senderAs(Player.class);
        if (player == null) {
            plugin.sendMessage(sender, "Only players can use this command.");
            return;
        }

        try {
            plugin.touchPlayerRecord(player, tagDao);
        } catch (SQLException ex) {
            plugin.sendMessage(sender, "Unable to open tags right now.");
            return;
        }

        player.getPageManager().openCustomPage(ref, store,
            new NinjaTagsTagsUI(playerRef, tagRegistry, tagDao, configManager, luckPermsService));
    }
}
