package com.ninja.tags.commands;

import com.ninja.tags.NinjaTagsPlugin;
import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.store.TagStore;
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

import java.util.Optional;

public class TagsCommand extends AbstractPlayerCommand {
    private final NinjaTagsPlugin plugin;
    private final TagStore tagStore;
    private final TagRegistry tagRegistry;
    private final ConfigManager configManager;
    private final Optional<LuckPermsService> luckPermsService;

    public TagsCommand(NinjaTagsPlugin plugin,
                       TagStore tagStore,
                       TagRegistry tagRegistry,
                       ConfigManager configManager,
                       Optional<LuckPermsService> luckPermsService) {
        super("tags", "Open the tags menu");
        this.plugin = plugin;
        this.tagStore = tagStore;
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

        plugin.touchPlayerRecord(player);

        player.getPageManager().openCustomPage(ref, store,
            new NinjaTagsTagsUI(playerRef, tagRegistry, tagStore, configManager, luckPermsService));
    }
}
