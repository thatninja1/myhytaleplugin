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
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NinjaTagsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private TagRegistry tagRegistry;
    private TagDao tagDao;
    private Optional<LuckPermsService> luckPermsService = Optional.empty();
    private TagsPage tagsPage;
    private TagsUiController uiController;
    private final Map<UUID, Player> onlinePlayers = new ConcurrentHashMap<>();

    public NinjaTagsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        try {
            Path dataFolder = getDataDirectory();
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
            getLogger().at(Level.SEVERE).withCause(ex).log("Failed to initialize NinjaTags");
        }
    }

    @Override
    protected void start() {
    }

    @Override
    protected void shutdown() {
        onlinePlayers.clear();
    }

    public void reload() throws SQLException {
        try {
            configManager.load();
            tagRegistry.load();
        } catch (IOException ex) {
            getLogger().at(Level.SEVERE).withCause(ex).log("Failed to reload NinjaTags");
        }
    }

    private Optional<LuckPerms> resolveLuckPerms() {
        try {
            return Optional.ofNullable(LuckPermsProvider.get());
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void registerCommands() {
        TagsCommand tagsCommand = new TagsCommand(this, tagDao, uiController);
        TagsAdminCommand adminCommand = new TagsAdminCommand(this, tagDao, tagRegistry, luckPermsService);

        List<String> aliases = configManager.getAliases();
        if (!aliases.isEmpty()) {
            tagsCommand.addAliases(aliases.toArray(new String[0]));
        }

        tagsCommand.setOwner(this);
        adminCommand.setOwner(this);

        getCommandRegistry().registerCommand(tagsCommand);
        getCommandRegistry().registerCommand(adminCommand);
    }

    private void registerListeners() {
        getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
            Player player = event.getPlayer();
            try {
                touchPlayerRecord(player, tagDao);
                reconcileEquippedTag(getPlayerUuid(player));
            } catch (SQLException ex) {
                getLogger().at(Level.WARNING).withCause(ex).log("Failed to update player record");
            }
            onlinePlayers.put(getPlayerUuid(player), player);
        });

        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef().getUuid();
            if (uuid != null) {
                onlinePlayers.remove(uuid);
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
        tagDao.upsertPlayer(getPlayerUuid(player), player.getDisplayName());
    }

    public boolean hasPermission(CommandSender sender, String permission) {
        return sender.hasPermission(permission);
    }

    public void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(Message.raw(message));
    }

    public Player findOnlinePlayer(UUID uuid) {
        return onlinePlayers.get(uuid);
    }

    private UUID getPlayerUuid(Player player) {
        return ((CommandSender) player).getUuid();
    }
}
