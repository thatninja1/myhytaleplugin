# NinjaTags

NinjaTags is a Hytale plugin that provides player tags with LuckPerms suffix integration.

## Features

- `/tags` opens an in-game custom UI page showing tags on the left and equip/de-equip buttons on the right.
- Tag equip/de-equip is handled directly by UI button clicks (no chat subcommands required).
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


## UI assets

- The tags menu layout is defined in `src/main/resources/Common/UI/Custom/ninjatags/TagsMenu.ui`.
- `manifest.json` has `"IncludesAssetPack": true` so the UI asset is packaged and loadable by Hytale.

- Asset pack metadata is provided in `src/main/resources/Common/manifest.json` for connect-time asset registration.

- A minimal fallback HUD document is included at `src/main/resources/Common/UI/HUD/MultipleHUD.ui` to satisfy clients/mods expecting `HUD/MultipleHUD.ui`.
