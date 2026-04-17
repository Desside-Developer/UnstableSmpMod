# UnstableSMP Mod

UnstableSMP is a Fabric server-side modification for Minecraft that extends player management and gameplay control systems. It introduces runtime skin manipulation, nickname management, and an immortality mechanic, designed for SMP environments requiring administrative control and dynamic player customization.

---

## Overview

The mod is structured around modular managers and server-integrated systems:

- Skin management with real-time texture replacement
- Persistent nickname system affecting all player display contexts
- Immortality system with configurable gameplay rules
- Command-driven administrative interface
- Event-based synchronization with server lifecycle

All features are designed for server-side execution without requiring client-side installation.

---

## Features

### Skin System

The skin system allows runtime modification of player textures.

Supported operations:

- Set skin from direct image URL (processed via external skin service)
- Copy skin from a Mojang account
- Reset skin to original Mojang-provided textures
- Immediate visual update across all connected clients

Implementation details:

- Uses Mojang session server for texture resolution
- Uses MineSkin API for signed texture generation
- Applies updates via packet-based player info refresh
- Forces client-side respawn synchronization for full rendering update

---

### Nickname System

Provides persistent player display name customization.

Applies to:

- Chat messages
- Tab list display
- In-game nametag

Key properties:

- Stored per player UUID
- Persisted across server restarts
- Automatically re-applied on player join
- Integrated into chat formatting pipeline

---

### Immortality System

Adds a server-controlled damage constraint layer for selected players.

Behavior:

- Prevents player health from dropping below 0.5 HP
- Can be toggled per player
- Supports forced enable/disable via command
- Integrates with damage event handling logic

Intended for controlled gameplay scenarios or administrative protection.

---

## Commands

### Skin Commands

/skin <player> url <imageUrl>
Applies a skin from a direct image URL.

/skin <player> player <mojangName>
Copies the skin of another Mojang account.

/skin <player> reset
Restores the player's original Mojang skin.

---

### Nick Commands

/nick <player> <nick>
Sets a custom display name for a player.

/nick <player> reset
Removes the custom nickname and restores the original username.

---

### Immortal Commands

/immortal <player>
Toggles immortality state.

/immortal <player> true
/immortal <player> false
Explicitly enables or disables immortality.

---

## Data Storage

The mod persists data locally within the server configuration directory:

- unstablesmpmod_skins.json – skin mappings (UUID → texture data)
- unstablesmpmod_nicks.json – nickname mappings (UUID → string)

Data format is JSON-based and loaded during server initialization.

---

## Event Integration

The mod integrates with Fabric lifecycle and gameplay events:

- Player join event:
  - Re-applies stored skins and nicknames

- Server stop event:
  - Clears runtime state and cached data

- Chat event pipeline:
  - Resolves nickname display values

- Death/damage events:
  - Enforces immortality constraints

---

## External Services

The following external services are used:

- Mojang Session Server (texture validation and profile resolution)
- Mojang API (UUID/name resolution)
- MineSkin API (skin signing and generation)
- Minetools API (profile fallback resolution)

All external requests are executed asynchronously.

---

## Implementation Notes

- Built on Fabric API (server-side entrypoint)
- Uses Brigadier for command registration
- Heavy use of reflection for compatibility across Minecraft versions
- GameProfile manipulation for runtime texture injection
- Packet-level updates for immediate client synchronization
- Asynchronous processing for all external HTTP operations

---

## Architecture

Core components:

- UnstableSMPMod – main entrypoint and initialization
- SkinManager – skin lifecycle and external API integration
- NickManager – display name management and persistence
- ImmortalManager – gameplay constraint system
- Command layer – Brigadier-based server commands
- Event listeners – Fabric lifecycle and gameplay hooks

---

## Runtime Behavior

- All modifications are server-authoritative
- Clients receive updates via network packets only
- No client modification is required
- State is rehydrated on player join events
- External API latency is isolated from main server thread

---

## Requirements

- Minecraft Fabric Server
- Java 17 or higher
- Fabric API

---

## Compatibility

The mod relies on internal Minecraft and Authlib structures. Some functionality uses reflection and may require adjustments between Minecraft versions.

---

## License MIT

This project is licensed under the MIT License. See the [LICENSE](./LICENSE.txt) file for details.
