# SurRealSell

A configurable inventory-based selling plugin for Paper/Purpur, made by **surrealsmpofficial**.

## Features
- `/sell` opens a chest-like sell inventory.
- Put items into the upper 45 slots.
- The green confirm button shows the current total and sells eligible items.
- Closing with Escape returns items that have not been sold.
- Items without a configured price are returned to the player.
- Uses Vault to pay into the server's existing economy.
- Editable item prices in `plugins/SurRealSell/config.yml`.
- `/sell reload` reloads prices (permission: `surrealsell.admin`).

## Requirements
- Paper or Purpur server with Java 21 for this build.
- Vault and an economy provider (for example EssentialsX Economy).
- Vault is a dependency; the plugin will disable itself if Vault/economy is missing.

## Build with GitHub Actions
Push this project to a GitHub repository. The workflow at `.github/workflows/build.yml` builds the JAR and uploads it as an artifact. Open **Actions**, select the successful run, and download the `SurRealSell` artifact ZIP.

## Commands
- `/sell` — open the selling inventory.
- `/sell reload` — reload config (admin only).

## Pricing
Prices are per individual item. For example, if `DIAMOND: 100` and a player sells 3 diamonds, they receive 300 in the economy currency. Prices may be decimals but cannot be negative.
