# Getters

Getters retrieve a value from the world or context. They take no arguments and are always the first element in a condition or `value_of()` expression.

Some getters are only available for specific block types.

---

## All Blocks

Available on: any target block

| Getter | Output | Description |
|--------|--------|-------------|
| `pos` | BlockPos | The position of the target block. |
| `block` | BlockState | The block state at the target position. |
| `dimension` | Dimension | The dimension the target block is in, as a dimension key such as `minecraft:overworld`. |
| `redstone` | Int | The strongest redstone signal received by the target block from any neighbor. |

Examples:

```
if pos == 0 64 0 set_redstone 15
set_redstone value_of(pos x & 15)
```

```
if block == minecraft:piston[facing=up] set_redstone 15
```

```
if dimension == minecraft:the_nether set_redstone 15
```

```
if redstone > 7 set_redstone 15 else set_redstone 0
set_redstone value_of(redstone + 1)
```

---

## Data Lectern / Lectern

The following getters require the target block to be a `Data Lectern` or a vanilla `Lectern` with a book.

| Getter | Output | Description |
|--------|--------|-------------|
| `get_page` | Int | The current page number of the book, 1-based. Returns `0` if there is no book. |
| `read_page` | String | The text content of the current page. Returns an empty string if there is no book or the page is blank. |

Examples:

```
if get_page > 3 set_redstone 15
set_page value_of(get_page + 1)
```

```
if read_page == "open" set_redstone 15
write_page value_of(read_page + " (edited)")
```

---

## Additional Getters

- [Valkyrien Skies](valkyrien_skies.md) — `ship`
- [Genesis](genesis.md) — `celestial`
