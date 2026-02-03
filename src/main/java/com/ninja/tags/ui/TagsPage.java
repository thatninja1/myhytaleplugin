package com.ninja.tags.ui;

import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.tags.TagDefinition;
import com.ninja.tags.tags.TagRegistry;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class TagsPage {
    private final ConfigManager configManager;
    private final TagRegistry tagRegistry;
    private final TagDao tagDao;
    private final ConcurrentMap<UUID, TagsPageState> stateByPlayer = new ConcurrentHashMap<>();

    public TagsPage(ConfigManager configManager, TagRegistry tagRegistry, TagDao tagDao) {
        this.configManager = configManager;
        this.tagRegistry = tagRegistry;
        this.tagDao = tagDao;
    }

    public TagsPageState getState(UUID playerId) {
        return stateByPlayer.computeIfAbsent(playerId, id -> new TagsPageState());
    }

    public void clearState(UUID playerId) {
        stateByPlayer.remove(playerId);
    }

    public List<TagDefinition> getVisibleTags(UUID playerId, String query) throws SQLException {
        List<TagDefinition> owned = getOwnedVisibleTags(playerId);
        if (query == null || query.isBlank()) {
            return owned;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        List<TagDefinition> filtered = new ArrayList<>();
        for (TagDefinition tag : owned) {
            if (tag.getId().toLowerCase(Locale.ROOT).contains(lower)) {
                filtered.add(tag);
                continue;
            }
            String plainDisplay = stripColorCodes(tag.getDisplay()).toLowerCase(Locale.ROOT);
            if (plainDisplay.contains(lower)) {
                filtered.add(tag);
            }
        }
        return filtered;
    }

    private List<TagDefinition> getOwnedVisibleTags(UUID playerId) throws SQLException {
        var ownedIds = tagDao.getOwnedTags(playerId);
        if (ownedIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<TagDefinition> owned = new ArrayList<>();
        for (TagDefinition tag : tagRegistry.getAllTags()) {
            if (ownedIds.contains(tag.normalizedId())) {
                owned.add(tag);
            }
        }
        return owned;
    }

    public int getPageSize() {
        return configManager.getUiPageSize();
    }

    public String getTitle() {
        return configManager.getUiTitle();
    }

    public String getBuyHint() {
        return configManager.getBuyMessage();
    }

    public static String stripColorCodes(String input) {
        if (input == null) {
            return "";
        }
        String withoutHex = input.replaceAll("&#[0-9A-Fa-f]{6}", "");
        return withoutHex.replaceAll("&[0-9A-FK-ORa-fk-or]", "");
    }

    public static class TagsPageState {
        private String searchQuery = "";
        private int pageIndex = 0;

        public String getSearchQuery() {
            return searchQuery;
        }

        public void setSearchQuery(String searchQuery) {
            this.searchQuery = searchQuery == null ? "" : searchQuery;
        }

        public int getPageIndex() {
            return pageIndex;
        }

        public void setPageIndex(int pageIndex) {
            this.pageIndex = Math.max(0, pageIndex);
        }
    }
}
