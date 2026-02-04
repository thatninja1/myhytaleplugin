package com.ninja.tags.ui;

import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.db.TagDao;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.tags.TagDefinition;
import com.ninja.tags.tags.TagRegistry;
import com.ninja.tags.ui.TagsPage.TagsPageState;
import com.hypixel.hytale.server.core.entity.entities.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TagsUiController {
    private final TagRegistry tagRegistry;
    private final TagDao tagDao;
    private final TagsPage tagsPage;
    private final ConfigManager configManager;
    private final Optional<LuckPermsService> luckPermsService;

    public TagsUiController(TagRegistry tagRegistry,
                            TagDao tagDao,
                            TagsPage tagsPage,
                            ConfigManager configManager,
                            Optional<LuckPermsService> luckPermsService) {
        this.tagRegistry = tagRegistry;
        this.tagDao = tagDao;
        this.tagsPage = tagsPage;
        this.configManager = configManager;
        this.luckPermsService = luckPermsService;
    }

    public void open(Player player) {
        // Placeholder for Hytale InteractiveCustomUIPage integration.
        // The API may differ, but the intent is to wire element IDs to the handlers below.
        // This method should:
        // 1) Load the TagsPage.ui asset.
        // 2) Set title_label, buy_hint, page_indicator, and button states.
        // 3) Bind APPLY, CLEAR, Prev, Next, and Close buttons.
        // 4) Populate tags_list with tag rows (EQUIP / DE-EQUIP).
    }

    public TagsPageView buildView(UUID playerId) throws SQLException {
        TagsPageState state = tagsPage.getState(playerId);
        List<TagDefinition> filtered = tagsPage.getVisibleTags(playerId, state.getSearchQuery());
        int pageSize = tagsPage.getPageSize();
        int totalPages = Math.max(1, (int) Math.ceil(filtered.size() / (double) pageSize));
        int pageIndex = Math.min(state.getPageIndex(), totalPages - 1);
        state.setPageIndex(pageIndex);

        int start = pageIndex * pageSize;
        int end = Math.min(filtered.size(), start + pageSize);
        List<TagDefinition> pageTags = new ArrayList<>();
        if (start < end) {
            pageTags.addAll(filtered.subList(start, end));
        }

        Optional<String> equipped = tagDao.getEquippedTag(playerId);
        equipped = equipped.filter(tagId -> tagRegistry.getTag(tagId) != null);
        if (equipped.isEmpty()) {
            tagDao.clearEquippedTag(playerId);
        }

        return new TagsPageView(pageTags, pageIndex + 1, totalPages, equipped.orElse(null));
    }

    public boolean equipTag(UUID playerId, TagDefinition tag) throws SQLException {
        if (luckPermsService.isEmpty()) {
            return false;
        }
        boolean updated = luckPermsService.get().setSuffix(playerId, " " + tag.getDisplay());
        if (!updated) {
            return false;
        }
        tagDao.setEquippedTag(playerId, tag.normalizedId());
        return true;
    }

    public boolean deEquip(UUID playerId) throws SQLException {
        if (luckPermsService.isEmpty()) {
            return false;
        }
        boolean updated = luckPermsService.get().clearSuffixes(playerId);
        if (!updated) {
            return false;
        }
        tagDao.clearEquippedTag(playerId);
        return true;
    }

    public String getTitle() {
        return tagsPage.getTitle();
    }

    public String getBuyHint() {
        return tagsPage.getBuyHint();
    }

    public int getPageSize() {
        return configManager.getUiPageSize();
    }

    public static class TagsPageView {
        private final List<TagDefinition> tags;
        private final int page;
        private final int totalPages;
        private final String equippedTagId;

        public TagsPageView(List<TagDefinition> tags, int page, int totalPages, String equippedTagId) {
            this.tags = tags;
            this.page = page;
            this.totalPages = totalPages;
            this.equippedTagId = equippedTagId;
        }

        public List<TagDefinition> getTags() {
            return tags;
        }

        public int getPage() {
            return page;
        }

        public int getTotalPages() {
            return totalPages;
        }

        public String getEquippedTagId() {
            return equippedTagId;
        }
    }
}
