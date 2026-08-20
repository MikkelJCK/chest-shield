# Reinforced Chests

Chests locked to their owner, with an optional password. Griefing-proof.

A Fabric mod for Minecraft that adds a chest only its owner can open, break or
automate. Designed for survival servers where you want private storage without
installing a protection plugin.

## Features

- **Automatic ownership.** Whoever places the chest owns it. No commands, no setup.
- **Optional password.** The owner can set a password so specific people can get
  in. Passwords are hashed with PBKDF2 and never leave the server, so not even an
  administrator can read them from the world files.
- **Explosion proof.** TNT, creepers and beds do nothing to it.
- **Sealed against automation.** Hoppers, pipes and item transport systems from
  other mods cannot see inside. The block entity deliberately does not expose an
  inventory.
- **Unbreakable by others.** Only the owner, or an admin holding the Master Key,
  can break it.
- **Double chests.** Two chests join into a 54 slot chest only when they belong to
  the same owner, so nobody can extend yours to reach your items.
- **Master Key.** An admin item, obtainable only through commands, that opens and
  breaks any chest. Requires operator level 2 *and* the key held in hand.

## Usage

| Action | How |
|---|---|
| Craft | A vanilla chest surrounded by 8 iron ingots |
| Open | Right click, as the owner |
| Set or change the password | Crouch + right click, as the owner |
| Enter a password | Right click, as anyone else |
| Get the Master Key | `/give @s reinforced_chests:master_key` |

Access granted by password lasts for that one opening. It has to be typed again
next time.

## Requirements

- Minecraft 26.2
- Fabric Loader 0.19.3 or newer
- [Fabric API](https://modrinth.com/mod/fabric-api)
- [owo-lib](https://modrinth.com/mod/owo-lib) 0.13.1 or newer

Required on both the client and the server.

## Branches

One branch per Minecraft version. `26.1.2` and `26.2` are both maintained.

## License

MIT. See [LICENSE](LICENSE).
