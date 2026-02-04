package com.ninja.tags.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class JsonTagStore implements TagStore {
    private final Path dataFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Object lock = new Object();
    private DataFile data = new DataFile();

    public JsonTagStore(Path dataFolder) {
        this.dataFile = dataFolder.resolve("tags-data.json");
    }

    @Override
    public void initialize() {
        synchronized (lock) {
            try {
                Files.createDirectories(dataFile.getParent());
                if (Files.notExists(dataFile)) {
                    data = new DataFile();
                    saveLocked();
                    return;
                }
                try (Reader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
                    DataFile loaded = gson.fromJson(reader, DataFile.class);
                    data = loaded == null ? new DataFile() : loaded;
                    if (data.players == null) {
                        data.players = new LinkedHashMap<>();
                    }
                }
            } catch (IOException ex) {
                data = new DataFile();
            }
        }
    }

    @Override
    public void upsertPlayer(UUID uuid, String username) {
        synchronized (lock) {
            PlayerEntry entry = data.players.computeIfAbsent(uuid.toString(), key -> new PlayerEntry());
            entry.username = username;
            if (entry.owned == null) {
                entry.owned = new LinkedHashSet<>();
            }
            saveLocked();
        }
    }

    @Override
    public Optional<UUID> resolveUuidByUsername(String username) {
        if (username == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            for (Map.Entry<String, PlayerEntry> entry : data.players.entrySet()) {
                PlayerEntry playerEntry = entry.getValue();
                if (playerEntry != null && playerEntry.username != null
                    && playerEntry.username.equalsIgnoreCase(username)) {
                    return Optional.of(UUID.fromString(entry.getKey()));
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public String getUsername(UUID uuid) {
        synchronized (lock) {
            PlayerEntry entry = data.players.get(uuid.toString());
            return entry == null ? null : entry.username;
        }
    }

    @Override
    public Set<String> getOwnedTags(UUID uuid) {
        synchronized (lock) {
            PlayerEntry entry = data.players.get(uuid.toString());
            if (entry == null || entry.owned == null) {
                return Collections.emptySet();
            }
            return new LinkedHashSet<>(entry.owned);
        }
    }

    @Override
    public boolean addOwnedTag(UUID uuid, String tagId) {
        String normalized = normalizeTagId(tagId);
        if (normalized == null) {
            return false;
        }
        synchronized (lock) {
            PlayerEntry entry = data.players.get(uuid.toString());
            if (entry == null) {
                return false;
            }
            if (entry.owned == null) {
                entry.owned = new LinkedHashSet<>();
            }
            boolean added = entry.owned.add(normalized);
            if (added) {
                saveLocked();
            }
            return added;
        }
    }

    @Override
    public boolean removeOwnedTag(UUID uuid, String tagId) {
        String normalized = normalizeTagId(tagId);
        if (normalized == null) {
            return false;
        }
        synchronized (lock) {
            PlayerEntry entry = data.players.get(uuid.toString());
            if (entry == null || entry.owned == null) {
                return false;
            }
            boolean removed = entry.owned.remove(normalized);
            if (removed && normalized.equalsIgnoreCase(entry.equipped)) {
                entry.equipped = null;
            }
            if (removed) {
                saveLocked();
            }
            return removed;
        }
    }

    @Override
    public Optional<String> getEquippedTag(UUID uuid) {
        synchronized (lock) {
            PlayerEntry entry = data.players.get(uuid.toString());
            if (entry == null || entry.equipped == null || entry.equipped.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(entry.equipped.toLowerCase());
        }
    }

    @Override
    public void setEquippedTag(UUID uuid, String tagId) {
        synchronized (lock) {
            PlayerEntry entry = data.players.get(uuid.toString());
            if (entry == null) {
                return;
            }
            String normalized = normalizeTagId(tagId);
            if (normalized != null && entry.owned != null && !entry.owned.contains(normalized)) {
                return;
            }
            entry.equipped = normalized;
            saveLocked();
        }
    }

    private void saveLocked() {
        try {
            Path tempFile = dataFile.resolveSibling(dataFile.getFileName().toString() + ".tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
            try {
                Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException ex) {
                Files.move(tempFile, dataFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            // best-effort persistence
        }
    }

    private static String normalizeTagId(String tagId) {
        if (tagId == null) {
            return null;
        }
        String normalized = tagId.trim().toLowerCase();
        return normalized.isEmpty() ? null : normalized;
    }

    private static class DataFile {
        private Map<String, PlayerEntry> players = new LinkedHashMap<>();
    }

    private static class PlayerEntry {
        private String username;
        private Set<String> owned = new LinkedHashSet<>();
        private String equipped;
    }
}
