# YuChaYuan BuildSharer

把目前的 Wynncraft 角色變成一條 [WynnBuilder](https://wynnbuilder.github.io/builder/) 網址：技能點、技能樹、身上裝備、crafted 裝備、等級、Aspect，可選擇是否包含書（Tome）。

Turns your current Wynncraft character into a single WynnBuilder link: skill points, ability tree, equipped gear, crafted gear, level, aspects, and optionally tomes.

## 使用 / Usage

- `/buildsharer`：讀取角色並產生網址（會短暫打開指南針選單，期間請不要開背包）。
- `/buildsharer tomes on|off`：網址要不要包含書。
- 按鍵設定裡也可以綁「分享配裝到 WynnBuilder」。

Crafted 裝備會轉成 WynnBuilder crafter 的配方：遊戲物品沒有記錄材料，模組依類型、等級、需求點數、詞條與耐久反推最吻合的材料組合。

## 需求 / Requirements

- Minecraft 1.21.11，Fabric（需要 Fabric API）或 NeoForge
- [Wynntils](https://modrinth.com/mod/wynntils)

## 建置 / Building

把 Wynntils 的 fabric 與 neoforge jar 放進 `libs/`，再執行 `gradle build`。WynnBuilder 的資料在執行時從 wynnbuilder.github.io 下載並快取在 `config/yuchayuan_buildsharer`。

## 授權 / License

AGPL-3.0-or-later
