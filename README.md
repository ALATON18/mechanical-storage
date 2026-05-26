# Mechanical Storage

Mechanical Storage is a Minecraft 1.21.1 NeoForge addon for Create 6.0.10.

The goal is a Create-style mechanical storage network where Create shafts/cogs act as the network path, and storage terminals/connectors only function when connected to a rotating, non-overstressed kinetic network.

## Current v0.1 scope

This first pass is project setup and visible block registration:

- Mechanical Storage Connector
- Mechanical Storage Terminal
- Mechanical Storage creative tab
- Placeholder block/item models
- Basic recipes using Create materials
- Create declared as a required dependency

The actual storage network, kinetic-network detection, terminal GUI, and inventory movement logic are planned next.

## Planned behaviour

- Connector front face points at one adjacent inventory, pump-style.
- Terminal and connectors must be on the same rotating Create kinetic network.
- No rotation means offline.
- Overstressed means offline.
- Loaded chunks only.
- 64 connectors per network.
- No cross-dimensional storage.

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
