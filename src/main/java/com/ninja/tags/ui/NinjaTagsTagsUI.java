package com.ninja.tags.ui;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.codec.codecs.simple.StringCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.ninja.tags.config.ConfigManager;
import com.ninja.tags.lp.LuckPermsService;
import com.ninja.tags.store.TagStore;
import com.ninja.tags.tags.TagDefinition;
import com.ninja.tags.tags.TagRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NinjaTagsTagsUI extends InteractiveCustomUIPage<NinjaTagsTagsUI.UIEventData> {
    private static final String LAYOUT = "ninjatags/Tags.ui";
    private static final int PAGE_SIZE = 10;
    private static final Pattern HEX_COLOR = Pattern.compile("&#([0-9A-Fa-f]{6})");

    private static final BuilderCodec<UIEventData> EVENT_CODEC = BuilderCodec.builder(UIEventData.class, UIEventData::new)
        .addField(new KeyedCodec<>("action", new StringCodec(), false), UIEventData::setAction, UIEventData::getAction)
        .addField(new KeyedCodec<>("tagId", new StringCodec(), false), UIEventData::setTagId, UIEventData::getTagId)
        .addField(new KeyedCodec<>("filter", new StringCodec(), false), UIEventData::setFilter, UIEventData::getFilter)
        .addField(new KeyedCodec<>("value", new StringCodec(), false), UIEventData::setValue, UIEventData::getValue)
        .build();

    private final TagRegistry tagRegistry;
    private final TagStore tagStore;
    private final ConfigManager configManager;
    private final Optional<LuckPermsService> luckPermsService;
    private String filterText = "";
    private int pageIndex;

    public NinjaTagsTagsUI(PlayerRef playerRef,
                           TagRegistry tagRegistry,
                           TagStore tagStore,
                           ConfigManager configManager,
                           Optional<LuckPermsService> luckPermsService) {
        super(playerRef, CustomPageLifetime.CanDismiss, EVENT_CODEC);
        this.tagRegistry = tagRegistry;
        this.tagStore = tagStore;
        this.configManager = configManager;
        this.luckPermsService = luckPermsService;
    }

    @Override
    public void build(Ref<EntityStore> ref,
                      UICommandBuilder commands,
                      UIEventBuilder events,
                      Store<EntityStore> store) {
        commands.append(LAYOUT);
        bindStaticEvents(events);
        rebuildPage(ref, store, commands, events);
    }

    @Override
    public void handleDataEvent(Ref<EntityStore> ref, Store<EntityStore> store, UIEventData data) {
        if (data == null || data.getAction() == null) {
            return;
        }
        String action = data.getAction();
        switch (action) {
            case "close" -> close();
            case "prev_page" -> {
                pageIndex = Math.max(0, pageIndex - 1);
                sendRebuild(ref, store);
            }
            case "next_page" -> {
                pageIndex++;
                sendRebuild(ref, store);
            }
            case "apply_filter" -> {
                String filter = firstNonBlank(data.getFilter(), data.getValue());
                filterText = filter == null ? "" : filter;
                pageIndex = 0;
                sendRebuild(ref, store);
            }
            case "filter_changed" -> {
                String filter = firstNonBlank(data.getValue(), data.getFilter());
                if (filter != null) {
                    filterText = filter;
                }
            }
            case "clear_filter" -> {
                filterText = "";
                pageIndex = 0;
                sendRebuildWithClear(ref, store);
            }
            case "tag_click" -> {
                handleTagClick(data.getTagId());
                sendRebuild(ref, store);
            }
            default -> {
            }
        }
    }

    private void bindStaticEvents(UIEventBuilder events) {
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyFilterButton",
            EventData.of("action", "apply_filter").append("filter", "$TagSearchBox.value"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#ClearFilterButton",
            EventData.of("action", "clear_filter"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#PrevPageButton",
            EventData.of("action", "prev_page"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#NextPageButton",
            EventData.of("action", "next_page"));
        events.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton",
            EventData.of("action", "close"));
        events.addEventBinding(CustomUIEventBindingType.ValueChanged, "#TagSearchBox",
            EventData.of("action", "filter_changed"));
    }

    private void sendRebuild(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        bindStaticEvents(events);
        rebuildPage(ref, store, commands, events);
        sendUpdate(commands, events, false);
    }

    private void sendRebuildWithClear(Ref<EntityStore> ref, Store<EntityStore> store) {
        UICommandBuilder commands = new UICommandBuilder();
        UIEventBuilder events = new UIEventBuilder();
        bindStaticEvents(events);
        commands.set("#TagSearchBox.text", "");
        rebuildPage(ref, store, commands, events);
        sendUpdate(commands, events, false);
    }

    private void rebuildPage(Ref<EntityStore> ref,
                             Store<EntityStore> store,
                             UICommandBuilder commands,
                             UIEventBuilder events) {
        UUID playerId = playerRef.getUuid();
        List<TagDefinition> ownedTags = getFilteredOwnedTags(playerId);

        int totalPages = Math.max(1, (int) Math.ceil(ownedTags.size() / (double) PAGE_SIZE));
        if (pageIndex >= totalPages) {
            pageIndex = totalPages - 1;
        }
        if (pageIndex < 0) {
            pageIndex = 0;
        }

        int start = pageIndex * PAGE_SIZE;
        int end = Math.min(ownedTags.size(), start + PAGE_SIZE);
        List<TagDefinition> pageTags = new ArrayList<>();
        if (start < end) {
            pageTags.addAll(ownedTags.subList(start, end));
        }

        Optional<String> equipped = tagStore.getEquippedTag(playerId);
        String equippedId = equipped.orElse(null);

        commands.set("#TitleLabel.text", configManager.getUiTitle());
        commands.set("#BuyHintLabel.text", configManager.getBuyMessage());
        commands.set("#PageLabel.text", "Page " + (pageIndex + 1) + "/" + totalPages);
        commands.set("#PrevPageButton.enabled", pageIndex > 0);
        commands.set("#NextPageButton.enabled", pageIndex + 1 < totalPages);

        for (int i = 0; i < PAGE_SIZE; i++) {
            String nameId = "#TagRow" + i + "Name";
            String descriptionId = "#TagRow" + i + "Description";
            String buttonId = "#TagRow" + i + "Button";
            if (i < pageTags.size()) {
                TagDefinition tag = pageTags.get(i);
                String display = tag.getDisplay() == null ? "" : tag.getDisplay();
                String plainName = stripColorCodes(display);
                String color = extractHexColor(display);
                commands.set(nameId + ".text", plainName);
                commands.set(nameId + ".color", color);
                commands.set(descriptionId + ".text", tag.getDescription() == null ? "" : tag.getDescription());

                boolean isEquipped = equippedId != null && equippedId.equalsIgnoreCase(tag.normalizedId());
                commands.set(buttonId + ".text", isEquipped ? "De-Equip" : "Equip");
                commands.set(buttonId + ".enabled", true);

                events.addEventBinding(CustomUIEventBindingType.Activating, buttonId,
                    EventData.of("action", "tag_click").append("tagId", tag.normalizedId()));
            } else {
                commands.set(nameId + ".text", "");
                commands.set(descriptionId + ".text", "");
                commands.set(buttonId + ".text", "");
                commands.set(buttonId + ".enabled", false);
            }
        }
    }

    private List<TagDefinition> getFilteredOwnedTags(UUID playerId) {
        Set<String> ownedIds = tagStore.getOwnedTags(playerId);
        if (ownedIds.isEmpty()) {
            return List.of();
        }
        List<TagDefinition> owned = new ArrayList<>();
        String search = filterText == null ? "" : filterText.trim().toLowerCase(Locale.ROOT);
        for (TagDefinition tag : tagRegistry.getAllTags()) {
            String normalized = tag.normalizedId();
            if (!ownedIds.contains(normalized)) {
                continue;
            }
            if (!search.isEmpty()) {
                String id = tag.getId() == null ? "" : tag.getId().toLowerCase(Locale.ROOT);
                String display = stripColorCodes(tag.getDisplay()).toLowerCase(Locale.ROOT);
                if (!id.contains(search) && !display.contains(search)) {
                    continue;
                }
            }
            owned.add(tag);
        }
        return owned;
    }

    private void handleTagClick(String tagId) {
        if (tagId == null || tagId.isBlank()) {
            return;
        }
        TagDefinition tag = tagRegistry.getTag(tagId);
        if (tag == null) {
            return;
        }
        UUID playerId = playerRef.getUuid();
        Optional<String> equipped = tagStore.getEquippedTag(playerId);
        if (equipped.isPresent() && equipped.get().equalsIgnoreCase(tag.normalizedId())) {
            deEquip(playerId);
            return;
        }
        equipTag(playerId, tag);
    }

    private void equipTag(UUID playerId, TagDefinition tag) {
        if (luckPermsService.isEmpty()) {
            return;
        }
        boolean updated = luckPermsService.get().setSuffix(playerId, " " + tag.getDisplay());
        if (!updated) {
            return;
        }
        tagStore.setEquippedTag(playerId, tag.normalizedId());
    }

    private void deEquip(UUID playerId) {
        if (luckPermsService.isEmpty()) {
            return;
        }
        boolean updated = luckPermsService.get().clearSuffixes(playerId);
        if (!updated) {
            return;
        }
        tagStore.setEquippedTag(playerId, null);
    }

    private static String extractHexColor(String display) {
        if (display == null) {
            return "#FFFFFF";
        }
        Matcher matcher = HEX_COLOR.matcher(display);
        if (matcher.find()) {
            return "#" + matcher.group(1);
        }
        return "#FFFFFF";
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private static String stripColorCodes(String input) {
        if (input == null) {
            return "";
        }
        String withoutHex = input.replaceAll("&#[0-9A-Fa-f]{6}", "");
        return withoutHex.replaceAll("&[0-9A-FK-ORa-fk-or]", "");
    }

    public static class UIEventData {
        private String action;
        private String tagId;
        private String filter;
        private String value;

        public String getAction() {
            return action;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getTagId() {
            return tagId;
        }

        public void setTagId(String tagId) {
            this.tagId = tagId;
        }

        public String getFilter() {
            return filter;
        }

        public void setFilter(String filter) {
            this.filter = filter;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
