package com.ninja.tags;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public void load() {
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

            try (Reader in = Files.newBufferedReader(tagDefinitionsFile)) {
                List<TagDefinition> loadedTags = gson.fromJson(in, TAGS_TYPE);
                tagsById.clear();
                if (loadedTags != null) {
                    for (TagDefinition tag : loadedTags) {
                        if (tag != null && tag.id() != null) {
                            tagsById.put(tag.id(), tag);
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.severe("Failed to load tag data", e);
        }
    }

    public void save() {
        try {
            Files.createDirectories(dataFolder);
            try (Writer out = Files.newBufferedWriter(playerDataFile)) {
                gson.toJson(players, PLAYER_DATA_TYPE, out);
            }
        } catch (IOException e) {
            logger.severe("Failed to save tag data", e);
        }
    }

    public List<String> getOwnedTags(UUID playerId) {
        PlayerTagData data = players.get(playerId.toString());
        if (data == null || data.tags == null) {
            return List.of();
        }
        return List.copyOf(data.tags);
    }

    public boolean playerHasTag(UUID playerId, String tagId) {
        return getOwnedTags(playerId).contains(tagId);
    }

    public void grantTag(UUID playerId, String tagId) {
        PlayerTagData data = getOrCreate(playerId);
        if (!data.tags.contains(tagId)) {
            data.tags.add(tagId);
        }
    }

    public void revokeTag(UUID playerId, String tagId) {
        PlayerTagData data = getOrCreate(playerId);
        data.tags.remove(tagId);
    }

    public String getEquippedTag(UUID playerId) {
        PlayerTagData data = players.get(playerId.toString());
        return data == null ? null : data.equippedTag;
    }

    public void setEquippedTag(UUID playerId, String tagId) {
        PlayerTagData data = getOrCreate(playerId);
        data.equippedTag = tagId;
    }

    public TagDefinition getTag(String tagId) {
        return tagsById.get(tagId);
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
