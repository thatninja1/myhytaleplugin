package com.ninja.tags;

import com.hypixel.hytale.logger.HytaleLogger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class NinjaTagsConfig {
    private static final String DEFAULT_UI_TITLE = "Ninja Tags";

    private final Path dataFolder;
    private final Path configFile;
    private final HytaleLogger logger;
    private final Yaml yaml = new Yaml();

    private volatile String uiTitle = DEFAULT_UI_TITLE;

    public NinjaTagsConfig(Path dataFolder, HytaleLogger logger) {
        this.dataFolder = dataFolder;
        this.configFile = dataFolder.resolve("config.yml");
        this.logger = logger;
    }

    public synchronized void load() {
        try {
            Files.createDirectories(dataFolder);
            ensureDefaultConfigFile();
            uiTitle = readUiTitleFromConfig();
            logger.atInfo().log("Loaded NinjaTags config from %s (ui.title=%s)", configFile, uiTitle);
        } catch (Exception e) {
            uiTitle = DEFAULT_UI_TITLE;
            logger.atSevere().withCause(e).log("Failed to load config.yml from %s. Using default ui.title=%s", configFile, uiTitle);
        }
    }

    public synchronized boolean reload() {
        try {
            Files.createDirectories(dataFolder);
            ensureDefaultConfigFile();
            uiTitle = readUiTitleFromConfig();
            logger.atInfo().log("Reloaded NinjaTags config from %s (ui.title=%s)", configFile, uiTitle);
            return true;
        } catch (Exception e) {
            logger.atSevere().withCause(e).log("Failed to reload config.yml from %s. Keeping previous ui.title=%s", configFile, uiTitle);
            return false;
        }
    }

    public String getUiTitle() {
        return uiTitle;
    }

    private void ensureDefaultConfigFile() throws IOException {
        if (Files.exists(configFile)) {
            return;
        }

        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> uiSection = new LinkedHashMap<>();
        uiSection.put("title", DEFAULT_UI_TITLE);
        root.put("ui", uiSection);

        try (Writer out = Files.newBufferedWriter(configFile)) {
            yaml.dump(root, out);
        }
    }

    private String readUiTitleFromConfig() throws IOException {
        try (Reader in = Files.newBufferedReader(configFile)) {
            Object parsed = yaml.load(in);
            if (!(parsed instanceof Map<?, ?> root)) {
                return DEFAULT_UI_TITLE;
            }

            Object uiObj = root.get("ui");
            if (!(uiObj instanceof Map<?, ?> ui)) {
                return DEFAULT_UI_TITLE;
            }

            Object titleObj = ui.get("title");
            if (!(titleObj instanceof String title) || title.isBlank()) {
                return DEFAULT_UI_TITLE;
            }

            return title;
        }
    }
}
