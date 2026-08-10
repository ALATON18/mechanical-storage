# Mechanical Storage

Mechanical Storage is a Minecraft 1.21.1 NeoForge addon for Create 6.0.10.

The goal is a Create-style mechanical storage network where Create shafts/cogs act as the network path, and storage terminals/connectors only function when connected to a rotating, non-overstressed kinetic network.

## Features

- Mechanical Storage Connector
- Encased Cogwheel Connector
- Mechanical Storage Terminal
- Mechanical Crafting Terminal with a private per-player 3x3 crafting grid
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
- Matching-stack-first network insertion
- Live disconnected and overstressed terminal states
- JEI recipe transfer support for the Crafting Terminal

## Network rules

- Connector front face points at one adjacent inventory, pump-style.
- Terminal and connectors must be on the same rotating Create kinetic network.
- No rotation means offline.
- Overstressed means offline.
- Loaded chunks only.
- 64 connectors per network.
- No cross-dimensional storage.

## Roadmap

- Connector priority and per-connector Create-filter support
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
