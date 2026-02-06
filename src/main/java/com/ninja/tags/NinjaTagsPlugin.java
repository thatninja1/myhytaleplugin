package com.ninja.tags;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NinjaTagsPlugin extends JavaPlugin {
    private static final int MAX_VISIBLE_TAG_ROWS = 12;

    private TagRepository tagRepository;
    private LuckPermsTagService luckPermsTagService;

    public NinjaTagsPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        this.tagRepository = new TagRepository(getDataDirectory(), getLogger());
        this.tagRepository.load();
        this.luckPermsTagService = new LuckPermsTagService(getLogger());

        getCommandRegistry().registerCommand(new TagsCommand());
        getCommandRegistry().registerCommand(new TagsAdminCommand());

        boolean hasUiDoc = getClass().getClassLoader().getResource("Common/UI/Custom/ninjatags/TagsMenu.ui") != null;
        boolean hasUiDocAlt = getClass().getClassLoader().getResource("TagsMenu.ui") != null;
        boolean hasUiDocShort = getClass().getClassLoader().getResource("ninjatags/TagsMenu.ui") != null;
        boolean hasAssetManifest = getClass().getClassLoader().getResource("Common/manifest.json") != null;
        getLogger().atInfo().log("NinjaTags loaded. UI resources: Common/UI/Custom/ninjatags/TagsMenu.ui=%s, ninjatags/TagsMenu.ui=%s, TagsMenu.ui=%s", hasUiDoc, hasUiDocShort, hasUiDocAlt);
        getLogger().atInfo().log("NinjaTags asset-pack manifest present: Common/manifest.json=%s", hasAssetManifest);
        getLogger().atInfo().log("NinjaTags known CustomUI docs: [ninjatags/TagsMenu.ui]");
    }

    @Override
    protected void shutdown() {
        this.tagRepository.save();
    }

    private class TagsCommand extends AbstractCommand {
        private TagsCommand() {
            super("tags", "Opens the tags menu UI");
            setAllowsExtraArguments(false);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (!ctx.isPlayer()) {
                ctx.sendMessage(Message.raw("Only players can use /tags."));
                return CompletableFuture.completedFuture(null);
            }

            Player player = ctx.senderAs(Player.class);
            getLogger().atInfo().log("/tags invoked by sender=%s", ctx.sender());
            if (player == null || player.getWorld() == null) {
                ctx.sendMessage(Message.raw("Could not open tags UI: player world not available."));
                return CompletableFuture.completedFuture(null);
            }

            player.getWorld().execute(() -> openTagsPage(player));
            return CompletableFuture.completedFuture(null);
        }
    }

    private class TagsAdminCommand extends AbstractCommand {
        private TagsAdminCommand() {
            super("tagsadmin", "Admin controls for NinjaTags");
            setAllowsExtraArguments(true);
            requirePermission("ninjatags.admin");
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            List<String> args = parseInput(ctx.getInputString());
            if (args.size() < 4) {
                ctx.sendMessage(Message.raw("Usage: /tagsadmin <givetag|removetag> <player> <tagid>"));
                return CompletableFuture.completedFuture(null);
            }

            String sub = args.get(1).toLowerCase(Locale.ROOT);
            String username = args.get(2);
            String tagId = args.get(3);

            PlayerRef target = Universe.get().getPlayerByUsername(username, NameMatching.EXACT);
            if (target == null) {
                ctx.sendMessage(Message.raw("Player must be online and match exactly: " + username));
                return CompletableFuture.completedFuture(null);
            }

            if (tagRepository.getTag(tagId) == null) {
                ctx.sendMessage(Message.raw("Unknown tag id: " + tagId));
                return CompletableFuture.completedFuture(null);
            }

            if (sub.equals("givetag")) {
                tagRepository.grantTag(target.getUuid(), tagId);
                ctx.sendMessage(Message.raw("Granted tag " + tagId + " to " + target.getUsername()));
                target.sendMessage(Message.raw("You received tag: " + tagId));
            } else if (sub.equals("removetag")) {
                tagRepository.revokeTag(target.getUuid(), tagId);
                if (tagId.equals(tagRepository.getEquippedTag(target.getUuid()))) {
                    tagRepository.setEquippedTag(target.getUuid(), null);
                    luckPermsTagService.clearManagedSuffix(target.getUuid(), ctx.sender());
                }
                ctx.sendMessage(Message.raw("Removed tag " + tagId + " from " + target.getUsername()));
                target.sendMessage(Message.raw("An admin removed tag: " + tagId));
            } else {
                ctx.sendMessage(Message.raw("Unknown subcommand. Use givetag or removetag."));
            }

            tagRepository.save();
            return CompletableFuture.completedFuture(null);
        }
    }

    @SuppressWarnings("removal")
    private void openTagsPage(Player player) {
        if (player == null) {
            getLogger().atWarning().log("/tags open failed: player was null");
            return;
        }

        if (player.getWorld() == null) {
            getLogger().atWarning().log("/tags open failed: world was null for player=%s", player);
            return;
        }

        Ref<EntityStore> ref = player.getReference();
        Store<EntityStore> store = player.getWorld().getEntityStore().getStore();
        PlayerRef playerRef = player.getPlayerRef();
        if (ref == null || store == null || playerRef == null) {
            getLogger().atWarning().log("/tags open failed: ref=%s store=%s playerRef=%s", ref != null, store != null, playerRef != null);
            return;
        }

        getLogger().atInfo().log("Opening TagsMenuPage for %s (%s)", playerRef.getUsername(), playerRef.getUuid());
        player.getPageManager().openCustomPage(ref, store, new TagsMenuPage(playerRef));
    }

    private boolean equipTag(UUID playerId, String tagId, CommandSender feedbackTarget) {
        if (!tagRepository.playerHasTag(playerId, tagId)) {
            feedbackTarget.sendMessage(Message.raw("You do not own tag id: " + tagId));
            return false;
        }

        TagDefinition tag = tagRepository.getTag(tagId);
        if (tag == null) {
            feedbackTarget.sendMessage(Message.raw("Unknown tag id: " + tagId));
            return false;
        }

        boolean ok = luckPermsTagService.applyManagedSuffix(playerId, tag.formattedSuffix(), feedbackTarget);
        if (!ok) {
            return false;
        }

        tagRepository.setEquippedTag(playerId, tagId);
        tagRepository.save();
        feedbackTarget.sendMessage(Message.raw("Equipped tag: " + tag.displayName() + " " + tag.formattedSuffix()));
        return true;
    }

    private boolean deEquip(UUID playerId, CommandSender feedbackTarget) {
        if (tagRepository.getEquippedTag(playerId) == null) {
            feedbackTarget.sendMessage(Message.raw("You do not have a tag equipped."));
            return false;
        }

        luckPermsTagService.clearManagedSuffix(playerId, feedbackTarget);
        tagRepository.setEquippedTag(playerId, null);
        tagRepository.save();
        feedbackTarget.sendMessage(Message.raw("Tag de-equipped."));
        return true;
    }

    private class TagsMenuPage extends InteractiveCustomUIPage<TagsMenuPage.Data> {
        private static final BuilderCodec<Data> DATA_CODEC = BuilderCodec.builder(Data.class, Data::new)
                .append(new KeyedCodec<>("@Action", Codec.STRING), (data, value) -> data.action = value, data -> data.action)
                .add()
                .build();

        private TagsMenuPage(PlayerRef playerRef) {
            super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, DATA_CODEC);
        }

        @Override
        public void build(Ref<EntityStore> ref, UICommandBuilder uiCommandBuilder, UIEventBuilder uiEventBuilder, Store<EntityStore> store) {
            UUID playerId = playerRef.getUuid();
            String equippedId = tagRepository.getEquippedTag(playerId);
            List<String> ownedTags = tagRepository.getOwnedTags(playerId);

            getLogger().atInfo().log("Building TagsMenuPage for %s (%s). ownedTags=%s equipped=%s", playerRef.getUsername(), playerId, ownedTags.size(), equippedId);
            getLogger().atInfo().log("Appending CustomUI document: ninjatags/TagsMenu.ui");
            uiCommandBuilder.append("ninjatags/TagsMenu.ui");
            uiCommandBuilder.set("#Title.TextSpans", Message.raw("Ninja Tags"));

            for (int i = 0; i < MAX_VISIBLE_TAG_ROWS; i++) {
                String labelPath = "#TagLabel" + i;
                String buttonPath = "#TagButton" + i;
                uiCommandBuilder.set(labelPath + ".Visible", false);
                uiCommandBuilder.set(buttonPath + ".Visible", false);
            }

            for (int i = 0; i < ownedTags.size() && i < MAX_VISIBLE_TAG_ROWS; i++) {
                String tagId = ownedTags.get(i);
                TagDefinition tag = tagRepository.getTag(tagId);
                if (tag == null) {
                    continue;
                }

                boolean equipped = tagId.equals(equippedId);
                String labelPath = "#TagLabel" + i;
                String buttonPath = "#TagButton" + i;
                String action = equipped ? "deequip" : "equip:" + tagId;
                String buttonText = equipped ? "De-equip" : "Equip";

                uiCommandBuilder.set(labelPath + ".Visible", true);
                uiCommandBuilder.set(buttonPath + ".Visible", true);
                uiCommandBuilder.set(labelPath + ".TextSpans", Message.raw(tag.displayName() + " " + tag.formattedSuffix()));
                uiCommandBuilder.set(buttonPath + ".TextSpans", Message.raw(buttonText));

                uiEventBuilder.addEventBinding(
                        CustomUIEventBindingType.Activating,
                        buttonPath,
                        EventData.of("@Action", action),
                        false
                );
            }

            uiCommandBuilder.set("#EmptyLabel.Visible", ownedTags.isEmpty());
        }

        @Override
        public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, Data data) {
            if (data == null || data.action == null || data.action.isBlank()) {
                getLogger().atWarning().log("TagsMenuPage event had empty action for %s", playerRef.getUuid());
                sendUpdate();
                return;
            }

            Player player = store.getComponent(ref, Player.getComponentType());
            if (player == null) {
                return;
            }

            UUID playerId = playerRef.getUuid();
            getLogger().atInfo().log("TagsMenuPage action for %s: %s", playerId, data.action);
            if (data.action.equals("deequip")) {
                deEquip(playerId, player);
            } else if (data.action.startsWith("equip:")) {
                String tagId = data.action.substring("equip:".length());
                equipTag(playerId, tagId, player);
            }

            sendUpdate();
        }

        private static class Data {
            private String action;
        }
    }

    private static List<String> parseInput(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        String[] split = trimmed.split("\\s+");
        List<String> out = new ArrayList<>(split.length);
        for (String arg : split) {
            if (!arg.isBlank()) {
                out.add(arg);
            }
        }
        return out;
    }
}
