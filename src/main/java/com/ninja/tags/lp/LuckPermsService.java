package com.ninja.tags.lp;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.SuffixNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.util.Tristate;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class LuckPermsService {
    private final LuckPerms luckPerms;
    private final int suffixPriority;
    private final int clearSuffixPriorityGte;

    public LuckPermsService(LuckPerms luckPerms, int suffixPriority, int clearSuffixPriorityGte) {
        this.luckPerms = luckPerms;
        this.suffixPriority = suffixPriority;
        this.clearSuffixPriorityGte = clearSuffixPriorityGte;
    }

    public static Optional<LuckPermsService> from(Optional<LuckPerms> luckPerms,
                                                  int suffixPriority,
                                                  int clearSuffixPriorityGte) {
        return luckPerms.map(api -> new LuckPermsService(api, suffixPriority, clearSuffixPriorityGte));
    }

    public boolean clearSuffixes(UUID uuid) {
        return modifyUser(uuid, user -> {
            user.data().clear(node -> node instanceof SuffixNode suffixNode
                && suffixNode.getPriority() >= clearSuffixPriorityGte);
            return true;
        });
    }

    public boolean setSuffix(UUID uuid, String suffixValue) {
        return modifyUser(uuid, user -> {
            user.data().clear(node -> node instanceof SuffixNode suffixNode
                && suffixNode.getPriority() >= clearSuffixPriorityGte);
            Node node = SuffixNode.builder(suffixValue, suffixPriority).build();
            user.data().add(node);
            return true;
        });
    }

    private boolean modifyUser(UUID uuid, java.util.function.Function<User, Boolean> action) {
        try {
            CompletableFuture<User> future = luckPerms.getUserManager().loadUser(uuid);
            User user = future.join();
            if (user == null) {
                return false;
            }
            action.apply(user);
            luckPerms.getUserManager().saveUser(user);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    public Optional<Tristate> hasSuffix(UUID uuid) {
        try {
            User user = luckPerms.getUserManager().getUser(uuid);
            if (user == null) {
                return Optional.empty();
            }
            return Optional.of(user.getCachedData().getMetaData(QueryOptions.defaultContextualOptions()).getSuffix() == null
                ? Tristate.FALSE
                : Tristate.TRUE);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
