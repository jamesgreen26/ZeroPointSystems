# Valkyrien Skies Integration

When [Valkyrien Skies](https://modrinth.com/mod/valkyrien-skies) is loaded, commands for querying ships become available.

---

## Getter

| Getter | Output | Description |
|--------|--------|-------------|
| `ship` | Ship | Returns the ship that is managing the target block's position. If the target block is not on a ship, a sentinel value is returned and all mappers handle it safely. |

---

## Ship Mappers

Produced by: `ship`

| Mapper | Output | Description |
|--------|--------|-------------|
| `slug` | String | The ship's name identifier. Returns `""` if not on a ship. |
| `id` | Int | The ship's numeric ID. Returns `-1` if not on a ship. |
| `pos` | Vec Pos | The ship's center position in world space. Returns the target block's own position if not on a ship. |
| `world_vel` | Vec Dir | The ship's velocity vector in world space. Returns zero if not on a ship. |
| `local_vel` | Vec Dir | The ship's velocity in ship-local space. Returns zero if not on a ship. |
| `bounding_box` | Vec Box | The dimensions of the ship's bounding box in world-scaled blocks. Returns zero if not on a ship. |
| `dir <direction>` | Vec Dir | A cardinal direction (`north`, `south`, `east`, `west`, `up`, `down`) rotated to match the ship's current orientation. Returns the unrotated direction if not on a ship. |
| `mass` | Double | The ship's effective mass, adjusted for the ship-to-world scale. Returns `0` if not on a ship. |

---

## Examples

Check if the block is on a ship:

```
if ship id > -1 set_redstone 15 else set_redstone 0
```

Set redstone based on whether the ship is moving upward:

```
if ship world_vel y > 0.1 set_redstone 15 else set_redstone 0
```

Output a redstone signal proportional to ship speed (clamped to 15):

```
set_redstone value_of(ship world_vel length rounded_down)
```

Check which way the ship's front is pointing:

```
if ship dir north y < 0 set_redstone 15
```

Activate when the ship is large:

```
if ship bounding_box volume > 1000 set_redstone 15
```
