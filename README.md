# Mechanical Storage

Mechanical Storage is a Minecraft 1.21.1 NeoForge addon for Create 6.0.10.

The goal is a Create-style mechanical storage network where Create shafts/cogs act as the network path, and storage terminals/connectors only function when connected to a rotating, non-overstressed kinetic network.

## Current experimental scope

The `experiment/network-scan` branch currently includes:

- Mechanical Storage Connector
- Mechanical Storage Terminal
- Create kinetic-network membership with no artificial distance limit
- Loaded connectors only, with a 64-connector limit per network
- Combined item counts that keep different item components separate
- Search by name, `@mod`, or `#tag`
- Name/count sorting, a draggable proportional scrollbar, and small/medium/large/stretch grid heights
- Shift-click deposits and withdrawals
- Full-stack left-click withdrawals
- Ctrl-left-click and Ctrl-scroll single-item transfers
- Matching-stack-first network insertion
- Live disconnected and overstressed terminal states
- Persistent Create List Filter and Attribute Filter tabs on the terminal
- Functional Storage, Sophisticated Storage, and Sophisticated Backpacks in the development client for compatibility testing

## Planned behaviour

- Connector front face points at one adjacent inventory, pump-style.
- Terminal and connectors must be on the same rotating Create kinetic network.
- No rotation means offline.
- Overstressed means offline.
- Loaded chunks only.
- 64 connectors per network.
- No cross-dimensional storage.

## Planned expansion

- Connector priority and per-connector Create-filter support
- Functional Storage and Sophisticated Storage compatibility testing
- Mechanical crafting grid with a per-player crafting matrix
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
