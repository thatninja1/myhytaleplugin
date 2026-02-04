package com.ninja.tags.store;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TagStore {
    void initialize();

    void upsertPlayer(UUID uuid, String username);

    Optional<UUID> resolveUuidByUsername(String username);

    String getUsername(UUID uuid);

    Set<String> getOwnedTags(UUID uuid);

    boolean addOwnedTag(UUID uuid, String tagId);

    boolean removeOwnedTag(UUID uuid, String tagId);

    Optional<String> getEquippedTag(UUID uuid);

    void setEquippedTag(UUID uuid, String tagId);
}
