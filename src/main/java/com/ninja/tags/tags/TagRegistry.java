package com.ninja.tags.tags;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TagRegistry {
    private final Path dataFolder;
    private final Gson gson = new Gson();
    private Map<String, TagDefinition> tagsById = new LinkedHashMap<>();

    public TagRegistry(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() throws IOException {
        Path tagsPath = dataFolder.resolve("tags.json");
        if (Files.notExists(tagsPath)) {
            copyDefaultTags(tagsPath);
        }

        try (InputStream inputStream = Files.newInputStream(tagsPath)) {
            List<TagDefinition> tags = gson.fromJson(new java.io.InputStreamReader(inputStream),
                new TypeToken<List<TagDefinition>>() {}.getType());
            Map<String, TagDefinition> updated = new LinkedHashMap<>();
            if (tags != null) {
                for (TagDefinition tag : tags) {
                    if (tag.getId() == null) {
                        continue;
                    }
                    updated.put(tag.normalizedId(), tag);
                }
            }
            tagsById = updated;
        }
    }

    private void copyDefaultTags(Path tagsPath) throws IOException {
        Files.createDirectories(tagsPath.getParent());
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream("tags.json")) {
            if (resource == null) {
                throw new IOException("Missing default tags.json resource");
            }
            Files.copy(resource, tagsPath);
        }
    }

    public TagDefinition getTag(String id) {
        if (id == null) {
            return null;
        }
        return tagsById.get(id.toLowerCase());
    }

    public List<TagDefinition> getAllTags() {
        return Collections.unmodifiableList(new ArrayList<>(tagsById.values()));
    }
}
