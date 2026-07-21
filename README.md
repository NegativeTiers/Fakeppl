# MineTopFakePlayers (Paper + Velocity)

This project has **two plugins**:

1. `MineTopFakePlayers-Paper` — creates movable visual NPC mannequins on a Paper 1.21.10 backend.
2. `MineTopFakePlayers-Velocity` — changes the player count and hover sample shown in the multiplayer server list.

## Important behavior

- The Velocity count is a server-list display only. It does not create authenticated connections.
- The Paper NPCs are armor-stand-based player mannequins. They can be teleported and moved, but they are not real accounts and do not appear in TAB.
- Java 21 is used for Minecraft/Paper 1.21.10.

## Paper commands

- `/fp create <name>`
- `/fp tp <name>`
- `/fp move <name> <world> <x> <y> <z> [yaw] [pitch]`
- `/fp remove <name>`
- `/fp list`
- `/fp reload`

Permission: `minetop.fakeplayers.admin`

## Velocity commands

- `/fpv set <number>`
- `/fpv mode add` — real players + fake count
- `/fpv mode fixed` — exact configured count
- `/fpv reload`

Permission: `minetop.fakeplayers.admin`

## Build

Run:

```bash
gradle clean build
```

Or upload the project to GitHub and run the included **Build MineTopFakePlayers** Actions workflow. Jars will be available as an Actions artifact.

## Install

- Put the Paper jar in the backend server's `plugins/` folder.
- Put the Velocity jar in the proxy's `plugins/` folder.
- Restart both services.
- Edit each generated config and run `/fp reload` or `/fpv reload`.

## GitHub web upload note

A visible copy of the workflow is included as `GITHUB-WORKFLOW-build.yml`. If your browser does not upload the `.github` folder, open **Actions → set up a workflow yourself**, paste that file's contents, and save it as `.github/workflows/build.yml`.
