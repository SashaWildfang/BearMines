# BearMines

Resettable mines for the **Bear Nation** prison server. Select a region, set its block mix, and it refills on a timer.

![Paper](https://img.shields.io/badge/Paper-26.1.2-blue) ![Java](https://img.shields.io/badge/Java-25-orange) ![Build](https://img.shields.io/badge/build-Gradle-02303A)

## Features

- Create mines from a WorldEdit selection
- Weighted block composition per mine (`set` / `unset` blocks and percentages)
- Timed automatic resets, plus manual resets on demand
- Per-mine flags for reset timing and behaviour
- DecentHolograms integration for mine displays
- Fine-grained permissions for each admin action

## Commands

All commands go through `/bearmines` (alias `/bm`).

| Subcommand | Description | Permission |
|---|---|---|
| `create` | Create a mine from your selection | `bn.bearmines.cmd.create` |
| `list` | List all mines | `bn.bearmines.cmd.list` |
| `info <mine>` | Show a mine's block composition | `bn.bearmines.cmd.info` |
| `set` / `unset` | Edit a mine's block distribution | `bn.bearmines.cmd.composition` |
| `reset <mine>` | Reset a mine now | `bn.bearmines.cmd.reset` |
| `flag` | Change reset intervals and flags | `bn.bearmines.cmd.flag` |
| `erase <mine>` | Delete a mine | `bn.bearmines.cmd.erase` |

`bn.bearmines.admin` grants everything. All permissions default to op.

## Requirements

- Paper 26.1.2+
- Java 25
- [WorldEdit](https://enginehub.org/worldedit) (optional, for region selection)
- [DecentHolograms](https://www.spigotmc.org/resources/decentholograms.96927/) (optional)

## Building

```bash
./gradlew build
```

The plugin jar goes to `build/libs/`.

## Project layout

```
src/main/java/com/sasha/bearMines/
├── BearMines.java       # Plugin entry point
├── MineManager.java     # Mine registry and persistence
├── commands/            # /bearmines and tab completion
├── listeners/           # Mine block events
├── models/              # Mine model
└── tasks/               # Timed reset task
```

---

Part of the Bear Nation plugin suite. For a fuller mines system with storage backends and an API, see [PrisonMinesX](https://github.com/SashaWildfang/PrisonMinesX).
