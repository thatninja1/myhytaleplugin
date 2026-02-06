package com.ninja.tags;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandSender;
import com.hypixel.hytale.logger.HytaleLogger;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.node.types.MetaNode;

import java.util.UUID;

public class LuckPermsTagService {
    private static final String META_KEY = "ninjatags_suffix";
    private static final int TAG_PRIORITY = 100;

    private final HytaleLogger logger;

    public LuckPermsTagService(HytaleLogger logger) {
        this.logger = logger;
    }

    public boolean applyManagedSuffix(UUID playerId, String suffix, CommandSender feedbackTarget) {
        LuckPerms luckPerms = getLuckPerms(feedbackTarget);
        if (luckPerms == null) {
            return false;
        }

        luckPerms.getUserManager().modifyUser(playerId, user -> {
            clearManagedNodes(user);
            SuffixNode suffixNode = SuffixNode.builder(suffix, TAG_PRIORITY).build();
            MetaNode marker = MetaNode.builder(META_KEY, suffix).build();
            user.data().add(suffixNode);
            user.data().add(marker);
        }).join();

        return true;
    }

    public boolean clearManagedSuffix(UUID playerId, CommandSender feedbackTarget) {
        LuckPerms luckPerms = getLuckPerms(feedbackTarget);
        if (luckPerms == null) {
            return false;
        }

        luckPerms.getUserManager().modifyUser(playerId, this::clearManagedNodes).join();
        return true;
    }

    private void clearManagedNodes(User user) {
        for (Node node : user.data().toCollection()) {
            if (NodeType.META.matches(node)) {
                MetaNode meta = NodeType.META.cast(node);
                if (meta.getMetaKey().equalsIgnoreCase(META_KEY)) {
                    user.data().remove(node);
                }
            }
            if (NodeType.PREFIX.matches(node)) {
                continue;
            }
            if (NodeType.SUFFIX.matches(node)) {
                SuffixNode chatMeta = NodeType.SUFFIX.cast(node);
                if (chatMeta.getPriority() == TAG_PRIORITY) {
                    user.data().remove(node);
                }
            }
        }
    }

    private LuckPerms getLuckPerms(CommandSender feedbackTarget) {
        try {
            return LuckPermsProvider.get();
        } catch (IllegalStateException ex) {
            logger.atWarning().log("LuckPerms was not available while processing tags.");
            feedbackTarget.sendMessage(Message.raw("LuckPerms is not loaded; cannot change suffix."));
            return null;
        }
    }
}
