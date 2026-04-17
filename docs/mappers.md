# Mappers

Mappers always follow a getter or another mapper, and transform the previous value into a new value. 
They sometimes take an argument, which can be a literal or a `value_of()` expression.

The available mappers depend on the type of the preceding value.

---

## BlockPos

Produced by: `pos`, `string as_block_pos`, `vec_pos rounded_down`

| Mapper | Output | Description |
|--------|--------|-------------|
| `x` | Int | X coordinate |
| `y` | Int | Y coordinate |
| `z` | Int | Z coordinate |
| `center` | Vec Pos | The centered floating-point position (`x + 0.5`, `y + 0.5`, `z + 0.5`) |
| `== <coords>` | Boolean | True if equal to the given position |
| `as_string` | String | Formats as `"x y z"` |

Examples:

```
if pos x > 0 set_redstone 15
set_redstone value_of(pos y & 15)
```

---

## BlockState

Produced by: `block`

| Mapper | Output | Description |
|--------|--------|-------------|
| `== <block predicate>` | Boolean | True if the block state matches the predicate (supports block properties) |

Examples:

```
if block == minecraft:stone set_redstone 15
if block == minecraft:piston[facing=up] set_redstone 15
```

---

## Vec Pos

A floating-point position. Produced by: `pos center`

| Mapper | Output | Description |
|--------|--------|-------------|
| `x` | Double | X component |
| `y` | Double | Y component |
| `z` | Double | Z component |
| `distance_to <coords>` | Double | Distance to the given position |
| `direction_to <coords>` | Vec Dir | Normalized direction vector toward the given position |
| `rounded_down` | BlockPos | Rounds each component down to get a block position |

## Vec Dir

A direction vector. Produced by: `vec_pos direction_to`, `vec_dir normalize`, `vec_dir cross`

| Mapper | Output | Description |
|--------|--------|-------------|
| `x` | Double | X component |
| `y` | Double | Y component |
| `z` | Double | Z component |
| `length` | Double | Magnitude of the vector |
| `normalize` | Vec Dir | Unit vector in the same direction |
| `cross <direction>` | Vec Dir | Cross product with another direction |
| `dot <direction>` | Double | Dot product with another direction |

---

## Int

Produced by: `pos x/y/z`, `redstone`, `get_page`, `double rounded_down/rounded_up`, `dimension index`, `string as_int`

| Mapper | Output | Description |
|--------|--------|-------------|
| `== <int>` | Boolean | Equality |
| `> <int>` | Boolean | Greater than |
| `< <int>` | Boolean | Less than |
| `+ <int>` | Int | Addition |
| `- <int>` | Int | Subtraction |
| `% <int>` | Int | Modulo |
| `& <int>` | Int | Bitwise AND |
| `\| <int>` | Int | Bitwise OR |
| `<< <int>` | Int | Left shift |
| `>> <int>` | Int | Right shift |
| `* <double>` | Double | Multiplication |
| `/ <double>` | Double | Division |
| `as_string` | String | Converts to string |

Examples:

```
if redstone > 7 set_redstone 15
set_redstone value_of(get_page - 1)
set_redstone value_of(pos x & 15)
```

---

## Double

Produced by: `pos x/y/z` (Vec Pos), `vec_dir length/dot`, `int * / `

| Mapper | Output | Description |
|--------|--------|-------------|
| `== <double>` | Boolean | Equality |
| `> <double>` | Boolean | Greater than |
| `< <double>` | Boolean | Less than |
| `+ <double>` | Double | Addition |
| `- <double>` | Double | Subtraction |
| `* <double>` | Double | Multiplication |
| `/ <double>` | Double | Division |
| `rounded_down` | Int | Floor |
| `rounded_up` | Int | Ceiling |
| `as_string` | String | Converts to string |

---

## String

Produced by: `read_page`, `pos as_string`, `dimension as_string`, `int as_string`, `double as_string`

| Mapper | Output | Description |
|--------|--------|-------------|
| `== <string>` | Boolean | Equality |
| `+ <string>` | String | Concatenation |
| `<+ <string>` | String | Prepends the given string |
| `lines` | Int | Splits by `\n` and returns the number of lines |
| `get_line <int>` | String | Splits by `\n` and returns the line at that index, or `""` if out of bounds |
| `as_int` | Int | Parse as integer |
| `as_double` | Double | Parse as double |
| `as_block_pos` | BlockPos | Parse as `"x y z"` format |
| `as_dimension` | Dimension | Treat as a dimension key |

Examples:

```
if read_page == "open" set_redstone 15
write_page value_of(pos as_string)
write_page value_of(pos as_string <+ "Pos: ")
set_redstone value_of(read_page lines)
```

---

## Dimension

Produced by: `dimension`, `string as_dimension`

| Mapper | Output | Description |
|--------|--------|-------------|
| `== <dimension>` | Boolean | True if equal to the given dimension |
| `index` | Int | A stable integer index for this dimension (consistent per server session) |
| `as_string` | String | The dimension's resource location (e.g. `minecraft:overworld`) |

Examples:

```
if dimension == minecraft:the_nether set_redstone 15
set_redstone value_of(dimension index & 15)
```
