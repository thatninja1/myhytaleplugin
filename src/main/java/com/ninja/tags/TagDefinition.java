package com.ninja.tags;

public record TagDefinition(String id, String displayName, String hexColor, String text) {
    public String formattedSuffix() {
        return "&" + hexColor + text;
    }
}
