# Tactical Overhaul

A Starsector mod that enhances combat with a tactical view overlay, allowing you to command your fleet while staying close to the action.

## Features

- **Tactical View Toggle** - Press backtick (`) to enter/exit tactical mode
- **Enhanced Ship Display** - See all ships with color-coded indicators (blue for friendly, red for enemy)
- **Ship Status Visualization**
  - Facing direction indicators
  - Velocity vectors with arrows
  - Flux level arcs (green/yellow/red based on level)
  - Weapon range circles for capitals and cruisers
  - Hull size indicators (diamond shapes below ships)
- **Multi-Ship Selection** - Select multiple friendly ships with Shift+click
- **Direct Fleet Commands**
  - Right-click empty space to issue move orders
  - Right-click enemy ships to issue attack orders
  - Commands work on all selected ships simultaneously
- **Command Visualization**
  - Waypoint markers for move commands
  - Attack markers on targeted enemies
  - Dashed lines showing command paths
- **Camera Controls**
  - Scroll wheel to zoom in/out
  - Right-click drag to pan
  - Edge-of-screen panning
  - Arrow keys to pan
  - Home or C to re-center on player ship

## Controls

| Key/Action | Function |
|------------|----------|
| ` (backtick) | Toggle tactical view |
| Scroll wheel | Zoom in/out |
| Left-click | Select friendly ship |
| Shift + Left-click | Add/remove ship from selection |
| Right-click (empty space) | Issue move command |
| Right-click (enemy ship) | Issue attack command |
| Right-click + drag | Pan camera |
| Arrow keys | Pan camera |
| Home / C | Re-center on player ship |
| Escape | Deselect all ships |

## Installation

1. Download the latest release
2. Extract to your Starsector `mods` folder
3. Enable "Tactical Overhaul" in the Starsector launcher

## Requirements

- Starsector 0.98a-RC8

## Building from Source

Requires JDK 17. Update the paths in `build.bat` if needed, then run:

```batch
build.bat
```

## License

MIT License - Feel free to use, modify, and distribute.

## Credits

Created with assistance from Claude Code.
