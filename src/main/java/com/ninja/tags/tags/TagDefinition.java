package com.ninja.tags.tags;

public class TagDefinition {
    private String id;
    private String display;
    private String description;
    private String permission;

    public String getId() {
        return id;
    }

    public String getDisplay() {
        return display;
    }

    public String getDescription() {
        return description;
    }

    public String getPermission() {
        return permission;
    }

    public String normalizedId() {
        return id == null ? "" : id.toLowerCase();
    }
}
