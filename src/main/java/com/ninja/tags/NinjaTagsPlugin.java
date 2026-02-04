package com.ninja.tags;

import com.ninja.tags.commands.TagsAdminCommand;
import com.ninja.tags.commands.TagsCommand;
import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.store.JsonTagStore;
import com.ninja.tags.store.TagStore;
import com.ninja.tags.tags.TagDefinition;
import com.ninja.tags.tags.TagRegistry;
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
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class NinjaTagsPlugin extends JavaPlugin {
    private ConfigManager configManager;
    private TagRegistry tagRegistry;
    private TagStore tagStore;
    private Optional<LuckPermsService> luckPermsService = Optional.empty();
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

            tagStore = new JsonTagStore(dataFolder);
            tagStore.initialize();

            luckPermsService = LuckPermsService.from(resolveLuckPerms(),
                configManager.getSuffixPriority(),
                configManager.getClearSuffixPriorityGte());

            registerCommands();
            registerListeners();
        } catch (IOException ex) {
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

    public void reload() {
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
        TagsCommand tagsCommand = new TagsCommand(this, tagStore, tagRegistry, configManager, luckPermsService);
        TagsAdminCommand adminCommand = new TagsAdminCommand(this, tagStore, tagRegistry, luckPermsService);

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
            touchPlayerRecord(player);
            reconcileEquippedTag(getPlayerUuid(player), player);
            onlinePlayers.put(getPlayerUuid(player), player);
        });

        getEventRegistry().register(PlayerDisconnectEvent.class, event -> {
            UUID uuid = event.getPlayerRef().getUuid();
            if (uuid != null) {
                onlinePlayers.remove(uuid);
            }
        });
    }

    private void reconcileEquippedTag(UUID uuid, Player player) {
        Optional<String> equipped = tagStore.getEquippedTag(uuid);
        if (equipped.isEmpty()) {
            return;
        }
        String equippedId = equipped.get();
        if (tagRegistry.getTag(equippedId) == null || !tagStore.getOwnedTags(uuid).contains(equippedId)) {
            tagStore.setEquippedTag(uuid, null);
            luckPermsService.ifPresent(service -> service.clearSuffixes(uuid));
            return;
        }
        if (player != null && luckPermsService.isPresent()) {
            TagDefinition tag = tagRegistry.getTag(equippedId);
            if (tag != null) {
                luckPermsService.get().setSuffix(uuid, " " + tag.getDisplay());
            }
        }
    }

    public void touchPlayerRecord(Player player) {
        tagStore.upsertPlayer(getPlayerUuid(player), player.getDisplayName());
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

    public TagStore getTagStore() {
        return tagStore;
    }

    private UUID getPlayerUuid(Player player) {
        return ((CommandSender) player).getUuid();
    }
}
