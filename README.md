# PestSwap

A Fabric mod for Minecraft 1.21.11 that automatically swaps between pest gear and farming gear based on Hypixel SkyBlock pest cooldown timers.

## Features

- **Automatic gear cycling** — Detects pest cooldown from the tab list and swaps gear at the right time
- **Wardrobe integration** — Swaps wardrobe slots for armor sets (pest vs farming)
- **Equipment swapping** — Automatically swaps SkyBlock equipment (necklace, cloak, belt, gloves) between pest and farming sets
- **Rod swap mode** — Alternative swap method using a swap rod
- **Configurable thresholds** — Set pest count thresholds, cooldown timing, GUI delays
- **Reconnect-safe** — Automatically resets swap state on server reconnect (handles rest disconnects, lobby warps, kicks)
- **Script integration** — Stops and restarts farming scripts around gear swaps

## Usage

| Key | Action |
|-----|--------|
| `K` | Toggle PestSwap on/off |
| `O` | Open config screen |

## Config Options

- **Gear Swap Mode** — None, Wardrobe, or Rod
- **Auto-Equipment** — Toggle automatic equipment swapping
- **Wardrobe Slots** — Farming slot and pest slot (1-9)
- **Pest Swap Timing** — Seconds left on cooldown to trigger swap
- **Pest Threshold** — Number of pests alive to swap back to farming gear
- **GUI Click Delay** — Delay between GUI interactions (ms)
- **Equipment Swap Delay** — Delay between equipment swaps (ms)
- **Restart Script Command** — Command to restart farming after swap

## Building

```
gradlew build
```

Output JAR is in `build/libs/`.
