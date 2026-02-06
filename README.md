# NinjaTags

NinjaTags is a Hytale plugin that provides player tags with LuckPerms suffix integration.

## Features

- `/tags` opens a text-based menu showing the player's available tags (left side = tag name, right side = equip/de-equip action command).
- `/tags equip <tagid>` equips a tag the player owns.
- `/tags deequip` removes the currently equipped tag.
- `/tagsadmin givetag <player> <tagid>` gives a tag to a player.
- `/tagsadmin removetag <player> <tagid>` removes a tag from a player.
- Player tag ownership is saved to JSON (`player-tags.json`) in the plugin data folder.
- Tag definitions are saved in `tags.json` (generated with defaults on first startup).
- Equipping a tag applies a LuckPerms suffix using the format `&` + `#RRGGBB` + tag text (example result: `&#808080Ninja`).

## Data files

### `tags.json`

```json
[
  {
    "id": "ninja",
    "displayName": "Ninja",
    "hexColor": "#808080",
    "text": "Ninja"
  }
]
```

### `player-tags.json`

```json
{
  "player-uuid": {
    "tags": ["ninja"],
    "equippedTag": "ninja"
  }
}
```

## Build

1. Place `HytaleServer.jar` in `libs/`.
2. Ensure Java 25 is installed and available to Gradle toolchains.
3. Run `gradle build`.
4. Find the plugin jar in `build/libs/` (and `dist/` if the copy task runs).
