# Changelog

## 1.1.0

Everything is configured from inside the chest now, and the mod no longer
depends on owo-lib.

**If you are updating from 1.0.0, you can delete owo-lib.** Your chests keep
their owner and their password; nothing is lost.

### Added

- **Configuration inside the chest.** A lock button next to the chest screen
  opens a settings screen with everything in it. The crouch + right click
  shortcut is gone, so that gesture works like it does in vanilla again: you can
  place blocks and item frames against the chest.
- **Permissions.** Give a player access and they open the chest like the owner
  does, with no password. Pick them from the list of players online, or type a
  name. Adding someone who is offline is allowed after a confirmation; they get
  access the first time they open the chest.
- **Protection switch.** Turn it off and the chest opens like a vanilla one.
  Password and permissions stop applying while it is off. Breaking the chest and
  changing its settings stay reserved for the owner either way, and the block
  never stops being explosion-proof.
- **Configurable hoppers.** Two independent switches, for putting items in and
  for taking them out, both off by default. When on, the chest works with any mod
  that uses the Fabric Transfer API, such as Create, AE2 or Refined Storage.
- **The chest says who owns it.** The title reads "Alberto's Chest" instead of
  "Shielded Chest".
- **`/chestshield`** shows the owner and settings of the chest you are looking
  at, for the owner or an admin. Useful for moderating without breaking blocks.
- Carry On cannot pick up a shielded chest any more.

### Changed

- **No more owo-lib.** The mod has no dependencies other than Fabric API.
- The guest password is now simply set or not set, with a button to clear it.
- The server decides when to ask for a password instead of the client guessing,
  so a chest can no longer end up ignoring your clicks.

### Fixed

- Hoppers under a double chest only reached the half they were under.
- The two halves of a double chest were lit differently when a light source was
  next to one of them.
- A player with an English game read "This chest belongs to desconocido" when the
  owner was unknown.
