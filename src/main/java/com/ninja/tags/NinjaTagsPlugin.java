package com.ninja.tags;

import com.ninja.tags.commands.TagsAdminCommand;
import com.ninja.tags.commands.TagsCommand;
import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.db.SqliteDb;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.tags.TagRegistry;
import com.ninja.tags.ui.TagsPage;
import com.ninja.tags.ui.TagsUiController;
import com.hytale.server.command.CommandSender;
import com.hytale.server.player.Player;
import com.hytale.server.plugins.JavaPlugin;
import com.hytale.server.events.player.PlayerJoinEvent;
import net.luckperms.api.LuckPerms;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NinjaTagsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private TagRegistry tagRegistry;
    private TagDao tagDao;
    private Optional<LuckPermsService> luckPermsService = Optional.empty();
    private TagsPage tagsPage;
    private TagsUiController uiController;

    public void setup() {
        try {
            Path dataFolder = getDataFolder().toPath();
            configManager = new ConfigManager(dataFolder);
            configManager.load();

            tagRegistry = new TagRegistry(dataFolder);
            tagRegistry.load();

            SqliteDb db = new SqliteDb(dataFolder);
            tagDao = new TagDao(db);
            tagDao.initialize();

            luckPermsService = LuckPermsService.from(resolveLuckPerms(),
                configManager.getSuffixPriority(),
                configManager.getClearSuffixPriorityGte());

            tagsPage = new TagsPage(configManager, tagRegistry, tagDao);
            uiController = new TagsUiController(tagRegistry, tagDao, tagsPage, configManager, luckPermsService);

            registerCommands();
            registerListeners();
        } catch (IOException | SQLException ex) {
            getLogger().error("Failed to initialize NinjaTags", ex);
        }
    }

    public void reload() throws SQLException {
        try {
            configManager.load();
            tagRegistry.load();
        } catch (IOException ex) {
            getLogger().error("Failed to reload NinjaTags", ex);
        }
    }

    private Optional<LuckPerms> resolveLuckPerms() {
        try {
            return Optional.ofNullable(getServer().getServicesManager().load(LuckPerms.class));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void registerCommands() {
        TagsCommand tagsCommand = new TagsCommand(this, tagDao, uiController);
        TagsAdminCommand adminCommand = new TagsAdminCommand(this, tagDao, tagRegistry, luckPermsService);

        getServer().getCommandManager().register("tags", (sender, args) -> tagsCommand.execute(sender));
        List<String> aliases = configManager.getAliases();
        for (String alias : aliases) {
            getServer().getCommandManager().register(alias, (sender, args) -> tagsCommand.execute(sender));
        }
        getServer().getCommandManager().register("tagsadmin", (sender, args) -> adminCommand.execute(sender, args));
    }

    private void registerListeners() {
        getServer().getEventManager().registerListener(PlayerJoinEvent.class, event -> {
            Player player = event.getPlayer();
            try {
                touchPlayerRecord(player, tagDao);
                reconcileEquippedTag(player.getUniqueId());
            } catch (SQLException ex) {
                getLogger().warn("Failed to update player record", ex);
            }
        });
    }

    private void reconcileEquippedTag(UUID uuid) throws SQLException {
        Optional<String> equipped = tagDao.getEquippedTag(uuid);
        if (equipped.isEmpty()) {
            return;
        }
        if (tagRegistry.getTag(equipped.get()) == null) {
            tagDao.clearEquippedTag(uuid);
            luckPermsService.ifPresent(service -> service.clearSuffixes(uuid));
        }
    }

    public void touchPlayerRecord(Player player, TagDao tagDao) throws SQLException {
        tagDao.upsertPlayer(player.getUniqueId(), player.getName());
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(message);
    }

    public Player findOnlinePlayer(UUID uuid) {
        return getServer().getPlayerManager().getPlayer(uuid);
    }
}
