package com.ninja.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.logger.HytaleLogger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class TagRepository {
    private static final Type PLAYER_DATA_TYPE = new TypeToken<Map<String, PlayerTagData>>() {
    }.getType();
    private static final Type TAGS_TYPE = new TypeToken<List<TagDefinition>>() {
    }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path dataFolder;
    private final Path playerDataFile;
    private final Path tagDefinitionsFile;
    private final HytaleLogger logger;

    private final Map<String, PlayerTagData> players = new LinkedHashMap<>();
    private final Map<String, TagDefinition> tagsById = new LinkedHashMap<>();

    public TagRepository(Path dataFolder, HytaleLogger logger) {
        this.dataFolder = dataFolder;
        this.playerDataFile = dataFolder.resolve("player-tags.json");
        this.tagDefinitionsFile = dataFolder.resolve("tags.json");
        this.logger = logger;
    }

    public synchronized void load() {
        try {
            Files.createDirectories(dataFolder);
            ensureDefaultTagsFile();

            if (Files.exists(playerDataFile)) {
                try (Reader in = Files.newBufferedReader(playerDataFile)) {
                    Map<String, PlayerTagData> loaded = gson.fromJson(in, PLAYER_DATA_TYPE);
                    if (loaded != null) {
                        players.clear();
                        players.putAll(loaded);
                    }
                }
            }

            Map<String, TagDefinition> loadedTagsById = parseTagDefinitionsFile();
            tagsById.clear();
            tagsById.putAll(loadedTagsById);
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Failed to load tag data from %s", tagDefinitionsFile);
        }
    }

    public synchronized int reloadTags() {
        try {
            Files.createDirectories(dataFolder);
            ensureDefaultTagsFile();

            Map<String, TagDefinition> loadedTagsById = parseTagDefinitionsFile();
            tagsById.clear();
            tagsById.putAll(loadedTagsById);

            logger.atInfo().log("Reloaded tags from %s (%s tags).", tagDefinitionsFile, tagsById.size());
            return tagsById.size();
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Failed to reload tags from %s. Keeping previous in-memory tags (%s).", tagDefinitionsFile, tagsById.size());
            return -1;
        }
    }

    public synchronized Path getTagDefinitionsFile() {
        return tagDefinitionsFile;
    }

    public synchronized int getTagCount() {
        return tagsById.size();
    }

    public synchronized void save() {
        try {
            Files.createDirectories(dataFolder);
            try (Writer out = Files.newBufferedWriter(playerDataFile)) {
                gson.toJson(players, PLAYER_DATA_TYPE, out);
            }
        } catch (IOException e) {
            logger.atSevere().withCause(e).log("Failed to save tag data");
        }
    }

    public synchronized List<String> getOwnedTags(UUID playerId) {
        PlayerTagData data = players.get(playerId.toString());
        if (data == null || data.tags == null) {
            return List.of();
        }
        return List.copyOf(data.tags);
    }

    public synchronized boolean playerHasTag(UUID playerId, String tagId) {
        return getOwnedTags(playerId).contains(tagId);
    }

    public synchronized void grantTag(UUID playerId, String tagId) {
        PlayerTagData data = getOrCreate(playerId);
        if (!data.tags.contains(tagId)) {
            data.tags.add(tagId);
        }
    }

    public synchronized void revokeTag(UUID playerId, String tagId) {
        PlayerTagData data = getOrCreate(playerId);
        data.tags.remove(tagId);
    }

    public synchronized String getEquippedTag(UUID playerId) {
        PlayerTagData data = players.get(playerId.toString());
        return data == null ? null : data.equippedTag;
    }

    public synchronized void setEquippedTag(UUID playerId, String tagId) {
        PlayerTagData data = getOrCreate(playerId);
        data.equippedTag = tagId;
    }

    public synchronized TagDefinition getTag(String tagId) {
        return tagsById.get(tagId);
    }

    private synchronized Map<String, TagDefinition> parseTagDefinitionsFile() throws IOException {
        try (Reader in = Files.newBufferedReader(tagDefinitionsFile)) {
            List<TagDefinition> loadedTags = gson.fromJson(in, TAGS_TYPE);
            if (loadedTags == null) {
                throw new IllegalArgumentException("tags.json parsed as null");
            }

            Map<String, TagDefinition> loadedTagsById = new LinkedHashMap<>();
            for (TagDefinition tag : loadedTags) {
                validateTag(tag);
                loadedTagsById.put(tag.id(), tag);
            }
            return loadedTagsById;
        } catch (JsonParseException e) {
            throw new IllegalArgumentException("Invalid JSON in tags file: " + e.getMessage(), e);
        }
    }

    private static void validateTag(TagDefinition tag) {
        if (tag == null) {
            throw new IllegalArgumentException("Tag entry cannot be null");
        }
        if (tag.id() == null || tag.id().isBlank()) {
            throw new IllegalArgumentException("Tag id cannot be empty");
        }
        if (tag.displayName() == null || tag.displayName().isBlank()) {
            throw new IllegalArgumentException("Tag displayName cannot be empty (id=" + tag.id() + ")");
        }
        if (tag.hexColor() == null || !tag.hexColor().matches("#[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException("Tag hexColor must be #RRGGBB (id=" + tag.id() + ")");
        }
        if (tag.text() == null || tag.text().isBlank()) {
            throw new IllegalArgumentException("Tag text cannot be empty (id=" + tag.id() + ")");
        }
    }

    private void ensureDefaultTagsFile() throws IOException {
        if (Files.exists(tagDefinitionsFile)) {
            return;
        }

        List<TagDefinition> defaults = List.of(
                new TagDefinition("ninja", "Ninja", "#808080", "Ninja"),
                new TagDefinition("vip", "VIP", "#F2C94C", "VIP"),
                new TagDefinition("legend", "Legend", "#9B51E0", "Legend")
        );

        try (Writer out = Files.newBufferedWriter(tagDefinitionsFile)) {
            gson.toJson(defaults, TAGS_TYPE, out);
        }
    }

    private PlayerTagData getOrCreate(UUID playerId) {
        return players.computeIfAbsent(playerId.toString(), ignored -> new PlayerTagData());
    }

    private static final class PlayerTagData {
        private List<String> tags = new ArrayList<>();
        private String equippedTag;

        @Override
        public int hashCode() {
            return Objects.hash(tags, equippedTag);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof PlayerTagData other)) {
                return false;
            }
            return Objects.equals(this.tags, other.tags) && Objects.equals(this.equippedTag, other.equippedTag);
        }
    }
}
