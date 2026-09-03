# Mechanical Storage

Mechanical Storage is a Minecraft 1.21.1 NeoForge addon for Create 6.0.10.

The goal is a Create-style mechanical storage network where Create shafts/cogs act as the network path, and storage terminals/connectors only function when connected to a rotating, non-overstressed kinetic network.

## Features

- Connector and Cogwheel Connector for storage already containing an item type
- Copper-trimmed Overflow Connector and Cogwheel Overflow Connector for fallback storage
- Mechanical Storage Terminal
- Mechanical Crafting Terminal with a private per-player 3x3 crafting grid
- Monitor bridge that exposes Mechanical Storage stock to Create logistics without allowing extraction
- Request-gated Dispatch bridge that lets an attached Create Packager withdraw only Create logistics orders
- Create kinetic-network membership with no artificial distance limit
- Loaded connectors only, with a 64-connector limit per network
- Component-aware item grouping that keeps differently tagged items separate
- Shared network summaries for large, component-rich inventories
- Search by name, `@mod`, or `#tag`
- Inclusive Create filter tabs
- Name/count sorting, a draggable proportional scrollbar, and responsive grid heights
- Shift-click deposits and withdrawals
- Full-stack left-click withdrawals
- Ctrl-left-click and Ctrl-scroll single-item transfers
- Matching-storage-first network insertion with Overflow Connector fallback
- Live disconnected and overstressed terminal states
- In-game Create Ponder guide for all six Mechanical Storage blocks
- JEI recipe transfer support for the Crafting Terminal
- Per-block common-config switches for operation, recipes, JEI and creative-tab visibility

## Network rules

- Connector front face points at one adjacent inventory, pump-style.
- Terminal and connectors must be on the same rotating Create kinetic network.
- No rotation means offline.
- Overstressed means offline.
- Loaded chunks only.
- 64 connectors per network.
- Standard Connectors accept only item types already present in their inventory.
- Overflow Connectors accept new item types and leftovers after matching storage is tried.
- No cross-dimensional storage.
- A Monitor may sit anywhere on the powered Mechanical Storage network; tune it to the same Create logistics network as the requesting Factory Gauge.
- A Dispatch must face an adjacent Packager. The Packager faces the same direction, draws from the Dispatch behind it and outputs packages ahead.
- Monitor and Dispatch accept a shaft only on their rear face and may be rotated to any of the six directions.

## Block configuration

Every block is enabled by default. Modpack creators can disable blocks individually in
`config/mechanical_storage-common.toml` under `[blocks]`. Disabled blocks stay registered
for save compatibility, but stop operating and are omitted from the mod creative tab,
JEI, recipes and the recipe book. Restart or reload datapacks after changing recipe-facing
settings.

## Roadmap

- Per-connector Create-filter support
- Fluid grid and fluid-capable connectors
- RPM-ranged wireless satellite
- Backtank-powered handheld item and crafting grids

## Development

Requires Java 21.

Useful commands:

```bash
./gradlew build
./gradlew runClient
./gradlew runServer
```

On Windows PowerShell:

```powershell
.\gradlew build
.\gradlew runClient
```
