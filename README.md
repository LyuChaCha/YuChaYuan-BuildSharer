# YuChaYuan BuildSharer

Turns your current Wynncraft character into one [WynnBuilder](https://wynnbuilder.github.io/builder/) link: equipped gear, crafted gear, powders, skill points, ability tree, level, aspects, and optionally tomes.

Run `/buildsharer` (or bind **Share build to WynnBuilder** in Controls). The mod opens your character menu in the background for a few seconds, then posts the link in chat and copies it to your clipboard. Keep your inventory closed while it reads.

## What it reads

| Part | How |
|---|---|
| Gear | Armour, accessories and the weapon in your hand (or hotbar). Ascended items map to WynnBuilder's Masterwork versions. |
| Crafted gear | Rebuilt as a real WynnBuilder crafter recipe. The game does not store ingredients, so the mod searches recipes, ingredients, material tiers and ingredient positions until skill requirements match exactly and every stat and the durability fall inside their ranges. If nothing matches exactly, it uses the closest recipe and tells you which item. |
| Powders | Element and tier of every slot, read from the item's second tooltip page. Nothing to configure. |
| Skill points | Points you assigned, from the character menu. |
| Ability tree | Every unlocked node. |
| Aspects | The aspects Wynntils has seen you equip. |
| Tomes | Optional: `/buildsharer tomes on` or `/buildsharer tomes off` (off by default). |

## How it differs from WynnBuild

[WynnBuild](https://github.com/pozgerab/WynnBuild) does the same job. The differences:

| | WynnBuild | BuildSharer |
|---|---|---|
| Loaders | Fabric | Fabric and NeoForge |
| Crafted gear | Custom item with copied stats | Recipe reverse-engineered for WynnBuilder's crafter |
| Powder tier | One default tier you set | Read from each item |
| Language | English | English by default; follows [WynnChaYuan](https://github.com/LyuChaCha/WynnChaYuan)'s language when it is installed (8 languages) |

## Requirements

- Minecraft 1.21.11 with Fabric (and Fabric API) or NeoForge
- [Wynntils](https://modrinth.com/mod/wynntils)

## Languages

Messages are in English unless [WynnChaYuan](https://github.com/LyuChaCha/WynnChaYuan) is installed, in which case they follow the language you pick in WynnChaYuan (or your Minecraft language if you did not pick one). Available: English, 繁體中文, 简体中文, 日本語, 한국어, Русский, Español, Türkçe.

## Building

Put the Wynntils Fabric and NeoForge jars in `libs/`, then run `gradle build`. WynnBuilder's data is downloaded from wynnbuilder.github.io the first time you share a build and cached in `config/yuchayuan_buildsharer`.

## 中文說明

`/buildsharer` 會把目前角色的裝備、crafted、粉末、技能點、技能樹、等級、Aspect（可選擇是否包含書）轉成一條 WynnBuilder 網址。Crafted 會反推出 crafter 配方，粉末階級自動讀取，支援 Fabric 與 NeoForge。訊息預設英文；裝了 WynnChaYuan 會跟著它的語言切換。

## License

AGPL-3.0-or-later
