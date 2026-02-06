package com.ninja.tags;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NinjaTagsPlugin extends JavaPlugin {
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
        getLogger().atInfo().log("NinjaTags loaded.");
    }

    @Override
    protected void shutdown() {
        this.tagRepository.save();
    }

    private class TagsCommand extends AbstractCommand {
        private TagsCommand() {
            super("tags", "Opens the tags menu");
            setAllowsExtraArguments(true);
        }

        @Override
        protected CompletableFuture<Void> execute(CommandContext ctx) {
            if (!(ctx.sender() instanceof Player player)) {
                ctx.sendMessage(Message.raw("Only players can use /tags."));
                return CompletableFuture.completedFuture(null);
            }

            List<String> args = parseInput(ctx.getInputString());
            if (args.size() >= 3 && args.get(1).equalsIgnoreCase("equip")) {
                equipTag(player, args.get(2), ctx.sender());
                return CompletableFuture.completedFuture(null);
            }

            if (args.size() >= 2 && args.get(1).equalsIgnoreCase("deequip")) {
                deEquip(player, ctx.sender());
                return CompletableFuture.completedFuture(null);
            }

            sendTagsMenu(player);
            return CompletableFuture.completedFuture(null);
        }

        @SuppressWarnings("removal")
        private void sendTagsMenu(Player player) {
            UUID playerId = player.getUuid();
            String equipped = tagRepository.getEquippedTag(playerId);
            List<String> ownedTags = tagRepository.getOwnedTags(playerId);

            player.sendMessage(Message.raw("=== Ninja Tags ===").color("#F2C94C").bold(true));
            if (ownedTags.isEmpty()) {
                player.sendMessage(Message.raw("You don't own any tags yet."));
                return;
            }

            player.sendMessage(Message.raw("Tag (left) | Action (right)").color("#808080"));
            for (String tagId : ownedTags) {
                TagDefinition tag = tagRepository.getTag(tagId);
                if (tag == null) {
                    continue;
                }
                String status = tagId.equals(equipped) ? "De-equip" : "Equip";
                String command = tagId.equals(equipped) ? "/tags deequip" : "/tags equip " + tagId;

                Message line = Message.raw("• " + tag.displayName() + " " + tag.formattedSuffix())
                        .color("#FFFFFF")
                        .insert(Message.raw(" | " + status + " via: " + command).color("#6FCF97"));
                player.sendMessage(line);
            }
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
    private void equipTag(Player player, String tagId, CommandSender feedbackTarget) {
        UUID playerId = player.getUuid();
        if (!tagRepository.playerHasTag(playerId, tagId)) {
            feedbackTarget.sendMessage(Message.raw("You do not own tag id: " + tagId));
            return;
        }

        TagDefinition tag = tagRepository.getTag(tagId);
        if (tag == null) {
            feedbackTarget.sendMessage(Message.raw("Unknown tag id: " + tagId));
            return;
        }

        boolean ok = luckPermsTagService.applyManagedSuffix(playerId, tag.formattedSuffix(), feedbackTarget);
        if (!ok) {
            return;
        }

        tagRepository.setEquippedTag(playerId, tagId);
        tagRepository.save();
        feedbackTarget.sendMessage(Message.raw("Equipped tag: " + tag.displayName() + " " + tag.formattedSuffix()));
    }

    @SuppressWarnings("removal")
    private void deEquip(Player player, CommandSender feedbackTarget) {
        UUID playerId = player.getUuid();
        if (tagRepository.getEquippedTag(playerId) == null) {
            feedbackTarget.sendMessage(Message.raw("You do not have a tag equipped."));
            return;
        }

        luckPermsTagService.clearManagedSuffix(playerId, feedbackTarget);
        tagRepository.setEquippedTag(playerId, null);
        tagRepository.save();
        feedbackTarget.sendMessage(Message.raw("Tag de-equipped."));
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
