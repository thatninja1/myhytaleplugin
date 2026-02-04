package com.ninja.tags.commands;

import com.ninja.tags.NinjaTagsPlugin;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.tags.TagDefinition;
import com.ninja.tags.tags.TagRegistry;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class TagsAdminCommand extends AbstractCommand {
    private final NinjaTagsPlugin plugin;
    private final TagDao tagDao;
    private final TagRegistry tagRegistry;
    private final Optional<LuckPermsService> luckPermsService;

    public TagsAdminCommand(NinjaTagsPlugin plugin,
                            TagDao tagDao,
                            TagRegistry tagRegistry,
                            Optional<LuckPermsService> luckPermsService) {
        super("tagsadmin", "Administer tags");
        this.plugin = plugin;
        this.tagDao = tagDao;
        this.tagRegistry = tagRegistry;
        this.luckPermsService = luckPermsService;
        requirePermission("tags.admin");
        setAllowsExtraArguments(true);
    }

    @Override
    protected CompletableFuture<Void> execute(CommandContext context) {
        CommandSender sender = context.sender();
        if (!plugin.hasPermission(sender, "tags.admin")) {
            plugin.sendMessage(sender, "You do not have permission to use this command.");
            return CompletableFuture.completedFuture(null);
        }

        String[] args = parseArgs(context);
        if (args.length == 0) {
            plugin.sendMessage(sender, "Usage: /tagsadmin <give|giveall|remove|reload>");
            return CompletableFuture.completedFuture(null);
        }

        String sub = args[0].toLowerCase();
        try {
            switch (sub) {
                case "give" -> handleGive(sender, args);
                case "giveall" -> handleGiveAll(sender, args);
                case "remove" -> handleRemove(sender, args);
                case "reload" -> handleReload(sender);
                default -> plugin.sendMessage(sender, "Unknown subcommand.");
            }
        } catch (SQLException ex) {
            plugin.sendMessage(sender, "Database error while processing command.");
        }

        return CompletableFuture.completedFuture(null);
    }

    private void handleGive(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 3) {
            plugin.sendMessage(sender, "Usage: /tagsadmin give <username> <tagId>");
            return;
        }
        String username = args[1];
        String tagId = args[2].toLowerCase();
        TagDefinition tag = tagRegistry.getTag(tagId);
        if (tag == null) {
            plugin.sendMessage(sender, "Unknown tag id.");
            return;
        }
        UUID uuid = resolveUuidOrFail(sender, username);
        if (uuid == null) {
            return;
        }
        tagDao.giveTag(uuid, tagId);
        plugin.sendMessage(sender, "Gave tag " + tagId + " to " + username + ".");
    }

    private void handleGiveAll(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 2) {
            plugin.sendMessage(sender, "Usage: /tagsadmin giveall <username>");
            return;
        }
        String username = args[1];
        UUID uuid = resolveUuidOrFail(sender, username);
        if (uuid == null) {
            return;
        }
        Set<String> tagIds = tagRegistry.getAllTags().stream()
            .map(TagDefinition::normalizedId)
            .collect(Collectors.toSet());
        tagDao.giveAllTags(uuid, tagIds);
        plugin.sendMessage(sender, "Gave all tags to " + username + ".");
    }

    private void handleRemove(CommandSender sender, String[] args) throws SQLException {
        if (args.length < 3) {
            plugin.sendMessage(sender, "Usage: /tagsadmin remove <username> <tagId>");
            return;
        }
        String username = args[1];
        String tagId = args[2].toLowerCase();
        UUID uuid = resolveUuidOrFail(sender, username);
        if (uuid == null) {
            return;
        }
        tagDao.removeTag(uuid, tagId);
        Optional<String> equipped = tagDao.getEquippedTag(uuid);
        if (equipped.isPresent() && equipped.get().equalsIgnoreCase(tagId)) {
            tagDao.clearEquippedTag(uuid);
            Player online = plugin.findOnlinePlayer(uuid);
            if (online != null && luckPermsService.isPresent()) {
                luckPermsService.get().clearSuffixes(uuid);
            }
        }
        plugin.sendMessage(sender, "Removed tag " + tagId + " from " + username + ".");
    }

    private void handleReload(CommandSender sender) throws SQLException {
        plugin.reload();
        plugin.sendMessage(sender, "NinjaTags reloaded.");
    }

    private UUID resolveUuidOrFail(CommandSender sender, String username) throws SQLException {
        Optional<UUID> uuid = tagDao.findUuidByUsername(username);
        if (uuid.isEmpty()) {
            plugin.sendMessage(sender, "Player must have joined before.");
            return null;
        }
        return uuid.get();
    }

    private String[] parseArgs(CommandContext context) {
        String input = context.getInputString();
        if (input == null || input.isBlank()) {
            return new String[0];
        }
        return input.trim().split("\\s+");
    }
}
