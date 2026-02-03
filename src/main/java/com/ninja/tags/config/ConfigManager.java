package com.ninja.tags.config;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ConfigManager {
    private final Path dataFolder;
    private Map<String, Object> config;

    public ConfigManager(Path dataFolder) {
        this.dataFolder = dataFolder;
    }

    public void load() throws IOException {
        Path configPath = dataFolder.resolve("config.yml");
        if (Files.notExists(configPath)) {
            copyDefaultConfig(configPath);
        }

        Yaml yaml = new Yaml();
        try (InputStream inputStream = Files.newInputStream(configPath)) {
            Object data = yaml.load(inputStream);
            if (data instanceof Map) {
                //noinspection unchecked
                config = (Map<String, Object>) data;
            } else {
                config = new LinkedHashMap<>();
            }
        }

        applyDefaults();
        save();
    }

    private void copyDefaultConfig(Path configPath) throws IOException {
        Files.createDirectories(configPath.getParent());
        try (InputStream resource = getClass().getClassLoader().getResourceAsStream("config.yml")) {
            if (resource == null) {
                throw new IOException("Missing default config.yml resource");
            }
            Files.copy(resource, configPath);
        }
    }

    private void applyDefaults() {
        ensureMap("commands").putIfAbsent("aliases", List.of("tag"));
        ensureMap("ui").putIfAbsent("title", "tags");
        ensureMap("ui").putIfAbsent("pageSize", 10);
        ensureMap("buy").putIfAbsent("message", "/buy to buy more tags");
        ensureMap("luckperms").putIfAbsent("suffixPriority", 100);
        ensureMap("luckperms").putIfAbsent("clearSuffixPriorityGte", 100);
        ensureMap("ownership").putIfAbsent("usePermissionField", false);
    }

    private Map<String, Object> ensureMap(String key) {
        Object value = config.get(key);
        if (value instanceof Map) {
            //noinspection unchecked
            return (Map<String, Object>) value;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        config.put(key, created);
        return created;
    }

    public void save() throws IOException {
        Path configPath = dataFolder.resolve("config.yml");
        Yaml yaml = new Yaml();
        try (var writer = Files.newBufferedWriter(configPath)) {
            yaml.dump(config, writer);
        }
    }

    public List<String> getAliases() {
        Object value = ensureMap("commands").get("aliases");
        if (value instanceof List) {
            //noinspection unchecked
            return (List<String>) value;
        }
        return Collections.singletonList("tag");
    }

    public String getUiTitle() {
        Object value = ensureMap("ui").get("title");
        return value == null ? "tags" : value.toString();
    }

    public int getUiPageSize() {
        Object value = ensureMap("ui").get("pageSize");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 10;
    }

    public String getBuyMessage() {
        Object value = ensureMap("buy").get("message");
        return value == null ? "/buy to buy more tags" : value.toString();
    }

    public int getSuffixPriority() {
        Object value = ensureMap("luckperms").get("suffixPriority");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 100;
    }

    public int getClearSuffixPriorityGte() {
        Object value = ensureMap("luckperms").get("clearSuffixPriorityGte");
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 100;
    }

    public boolean usePermissionField() {
        Object value = ensureMap("ownership").get("usePermissionField");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return false;
    }
}
