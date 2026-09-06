# Zero Point Systems — Block & Item Reference

*A tech mod with computers, radios, production machines, and control systems.*
Built for NeoForge 1.21.1 (modId `zps`). This document catalogs every functional
block, then functional items, then resource/decorative blocks and crafting materials.

---

## 1. Functional Blocks

### 1.1 Power / Cable Network

The cable power network is a block-graph system: components connect face-to-face only
when they share the same cable standard and agree on how many parallel **channels** the
connection carries. Each block spans a fixed number of independent channels (a plain
cable carries 1, dense cables and most panels carry 4, the Octo-Controller 8, the
Dodeca-Controller 12). Signals are routed per-channel by a flood-fill that discovers the
**terminals** reachable on each channel. Terminals (transformers, converters, controllers,
panels, levers) are the endpoints that actually source or sink either a redstone value
(0–15) or Forge Energy (FE); plain cables and separators are pure conductors.

#### Cable (`cable`)
- Basic single-channel conductor carrying one signal (redstone or FE routing); auto-connects to any adjacent network component on all six faces.
- Does nothing on its own — it only links terminals together; its model sprouts an arm toward each connected neighbor.
- Can be upgraded in-world by right-clicking with Cable Insulation, a Catwalk, or Space Grating, which locks its current connections and prevents new ones.

#### Dense Cables (`dense_cables`)
- Bundles 4 independent channels through a single block, routing four separate signals in the footprint of one cable.
- Connects on all six faces to other dense cables, dense cable separators, and 4-channel terminals (switch panels, controllers).
- Accepts the same insulation/catwalk/grating surface upgrades as a normal cable.

#### Dense Cable Separator (`dense_cable_separator`)
- Transition block that splits a Dense Cable's 4 channels out to four individual cables (or merges four cables back into a dense bundle).
- Has a facing side that mates with the dense cable; the four perpendicular sides each map to one of the four channels.
- Rotate with Shift + Right-Click (or a Create wrench) to cycle which perpendicular cable maps to which dense channel.

#### Step-Up Transformer (`stepup_transformer`)
- Energy **source** for the network: each tick it pulls FE out of the block it faces into an internal buffer (up to 4096 FE/t).
- Distributes buffered FE across the network's main channel to all connected Step-Down Transformers, splitting energy proportionally among them.
- A network terminal; connects to cable on all sides except its energy-input (facing) side.
- ⚠️ If a Redstone Converter shares the same network while the transformer holds energy, the converter is overpowered and (per config) explodes — never mix FE and redstone into a converter.

#### Step-Down Transformer (`stepdown_transformer`)
- Energy **sink** counterpart: receives FE routed over the network and pushes it into the FE-accepting block it faces.
- A network terminal; connects to cable on all sides except its energy-output (facing) side.
- Tracks recent throughput (FE/t) for the HUD readout, clearing to zero when transfer stops.

#### Redstone Converter (`redstone_converter`)
- Bridges vanilla redstone and the cable network: reads the redstone signal on its facing side (also settable via the `set_redstone` script command) and transmits that strength over the network.
- Re-emits redstone signals received from the network as real redstone power out its back face.
- A network terminal that connects to cable like a transformer (all sides except facing).
- ⚠️ Cannot tolerate FE — if a Step-Up Transformer feeds energy onto its network it is overvolted and destroyed (configurable).

#### Octo-Controller (`octo_controller`)
- Rideable seat with 8 independent redstone outputs; right-click to mount, then WASD/arrow keys drive the eight channels.
- Sources signals onto an 8-channel network split into two 4-channel groups — one exits the back face, the other the bottom face (each via Dense Cables).
- Lets a single seat command up to eight separate circuits at once.

#### Dodeca-Controller (`dodeca_controller`)
- Rideable seat with 12 independent redstone outputs — the larger sibling of the Octo-Controller.
- Distributes its 12 channels as three 4-channel groups exiting the back, left, and right faces (each mates with Dense Cables).
- Right-click to ride; player input sets the twelve output strengths flowing to downstream terminals.

#### Switch Panel (`switch_panel`)
- A wall/floor/ceiling-mountable panel of 4 toggle switches ("4 levers in 1"), each driving one output channel.
- Clicking a quadrant of the front face flips that switch — full redstone (15) when on, 0 when off.
- A 4-channel terminal that connects out its back (mounting) face via Dense Cables.

#### Graduated Lever (`graduated_lever`)
- Wall/floor/ceiling panel for analog redstone: outputs an adjustable 0–15 strength on a single channel.
- Click the upper half of the front face to step up, the lower half to step down (click pitch rises with strength).
- A single-channel terminal that connects out its back face via plain Cable.

#### Cable Insulation (`cable_insulation`)
- Applied to bare cables (right-click a cable with it) to sheath them: the cable locks to its current connections and forms no new ones.
- Renders as a full solid block wrapping the wire; breaking the insulation pops it back as an item and returns the cable to bare.
- Companion surface treatments — Catwalk (walkable top) and Space Grating (see-through) — similarly restrict cable connections.

### 1.2 Data / Light Pipe Network

The light-pipe network is a text-based signal system. **Data Cables** carry short text
strings between connected terminals: a single sender broadcasts to every receiver, and if
two senders share one cable the data scrambles into garbled characters. Atop this, the
**Serial Bus** and **Script Terminal** form a scripting system — the terminal holds a list
of commands that, when powered by redstone, dispatch one line at a time down the cable to
each Serial Bus, which runs the line as a `zps` script against the machine directly in
front of it (e.g. `set_redstone`, `set_page`, robotic-arm `take_items`/`use`,
`set_frequency`). Scripts can branch on getters that read the target block. **Radios**
carry the same text wirelessly over frequencies 1–64, where higher frequencies reach
farther but demand a taller Radio Antenna. With Create installed, Ponder collects these
blocks under the **Cable Components** and **Data Cable Components** tags, and a block's
Ponder page lists the script commands and getters that block accepts.

#### Data Cable (`data_cable`)
- Carries text/data between light-pipe blocks; data fed to one input is sent to all connected outputs.
- A minimal setup is one sender and one receiver joined by cable.
- If more than one active sender shares the network, the merged output is randomized/garbled rather than cleanly combined.

#### Serial Bus (`serial_bus`)
- Receives text over a Data Cable and executes it as a `zps` script command, targeting the block directly in front of its interface face.
- A leading `/` is stripped and the target's coordinates are auto-injected, so scripts act on "the block in front of me."
- If it faces a Create `display_link` instead, it forwards its text to that link, which offers two sources — *Raw Input Data* (the live text on the cable) and *Stored Display Text* (a value written by the `set_display_text` script command and held until changed).
- The physical bridge between a Script Terminal's commands and the machine being controlled.

#### Script Terminal (`script_terminal`)
- Opens a GUI (right-click) for a multi-line script, plus a loop toggle and a per-command delay (2/4/8/16 ticks).
- On a redstone signal it dispatches commands in sequence — one per delay interval — each running once for every connected Serial Bus, evaluated against the block in front of that bus.
- Supports control flow and getters (e.g. `if block == minecraft:piston[facing=up] set_redstone 15`), `wait <n>` pauses, and looping while power is held.
- Resolves relative/local coordinates (`~ ~ ~`, `^ ^ ^`), and when an Address Pad book is inserted, resolves `@name` labels to stored coordinates.
- A leading block of `#def name = expression` lines declares reusable aliases (including `value_of(...)` getter expressions) that later commands expand, with in-editor suggestions and diagnostics for bad or conflicting declarations.
- Accepts an Address Pad item (Shift+right-click to remove); the pad drops when the terminal is broken.
- Failing commands are ignored; the `ScriptCommandFailureBehavior` server config switches between silent failure (default) and a concise log line naming the command and reason.

#### Data Lectern (`data_lectern`)
- Holds a written or writable book and outputs the text of its current page onto the Data Cable network as a sender.
- Emits a redstone signal scaled to the current page position (like a vanilla lectern), with a GUI for turning pages.
- Readable/writable by scripts: `get_page`/`read_page` getters and `set_page`/`write_page` executors target it (and vanilla lecterns).
- Right-click opens the page GUI; Shift+right-click takes the book back out into your inventory.

#### Data Transcriber (`data_transcriber`)
- When powered, copies the text arriving on its Data Cable input onto the current page of a book in the (Data) Lectern directly below it.
- Requires a writable book; if the incoming data changes while still powered, it advances to the next page before writing (adding pages up to 100).
- Plays a cartographer work sound on each successful write.

#### Text Display (`text_display`)
- Receives text from a Data Cable and renders it on the block face for players to read.
- Emits a redstone signal proportional to how full the display is (text length vs. capacity).
- Adjacent Text Displays auto-merge into larger multi-block screens (2×1, 1×2, or 2×2) to show more text.
- Text is laid out proportionally with word-aware line breaks that stay aligned across the blocks of a merged screen, and holds up to 3,600 characters on a full 2×2.

#### Data Comparator (`data_comparator`)
- Compares the text from two Data Cable inputs and outputs redstone based on the selected mode; Shift+right-click cycles the mode.
- **Equals**: powered when the inputs match exactly. **Contains**: powered when the first input contains the second. **Greater Than**: powered when both parse as numbers and the first is larger.
- Inputs are trimmed before comparison, so surrounding whitespace is ignored.

#### Data Combinator (`data_combinator`)
- Merges two Data Cable inputs (Input A left, Input B right) into a single output on the back face; Shift+right-click cycles the mode.
- **Append**: passes a single input through, or appends Input B after Input A when both are present.
- **Replace**: passes Input A through, substituting the first `%s` in it with Input B when both are present.

#### Radio Transmitter (`radio_transmitter`)
- Takes the text on its Data Cable and broadcasts it wirelessly on its current frequency.
- Tune the frequency (1–64) with Shift+right-click or a Create wrench; higher frequencies reach farther but require a taller Radio Antenna stacked on top.
- Transmission only activates when the antenna is tall enough for the chosen frequency; otherwise it goes silent.

#### Radio Receiver (`radio_receiver`)
- Picks up transmissions on its tuned frequency and outputs the received text onto its Data Cable as a sender (polls ~every 8 ticks).
- Tune the frequency (1–64) with Shift+right-click or a Create wrench; higher frequencies need a taller Radio Antenna.
- Overlapping transmissions mix their characters weighted by signal strength, so weak or distant signals come through partially garbled.

#### Radio Antenna (`radio_antenna`)
- Stacks vertically into a pillar atop a Radio Transmitter or Receiver; the stack height sets the maximum usable frequency and range.
- Required height scales with frequency (`freq/20 + 1.5` blocks), so high frequencies demand tall towers.
- The topmost antenna renders a wider cap; placing/removing antennas updates the radio's signal strength automatically.

#### Loudspeaker (`loudspeaker`)
- A light-pipe terminal that speaks/announces the text data arriving over its Data Cable.
- Driven from the data network, so scripts and radios can route messages to it for audio output.

### 1.3 Production Machines

#### Rolling Mill (`rolling_mill`)
- Consumes FE to process `zps:rolling` recipes: rolls metal ingots into rods and metal nuggets into wire (copper/aluminum/gold nuggets → wire; space metal ingot → rod).
- Recipes set their own cost and duration (wires ~120 ticks, space metal rod ~200 ticks, all at 32 FE/tick); receives up to 512 FE/tick into an 8,192 FE buffer and holds progress when power runs out.
- One input + one output slot with a GUI; hoppers/pipes may insert only into the input and extract only from the output, and a working blockstate drives the spinning-roller animation.

#### Assembler (`assembler`)
- Automated crafter built around a 5×5 ghost "pattern" grid that defines a recipe; resolves the mod's own `zps:shaped_5x5` recipes, vanilla shaped/shapeless recipes, and Create mechanical crafting when Create is installed.
- Runs only while receiving redstone, drawing 16 FE/tick over a 20-tick cycle; pulls matching items from its 12-slot input buffer and deposits the result in its single output slot.
- Pattern cells can be stamped manually in the GUI or set programmatically via the `set_recipe` script command, which enables tag-aware ingredient matching (any item in a tag).
- Receives up to 512 FE/tick into an 8,192 FE buffer; automation inserts into the input buffer and extracts from the output only.
- JEI lists it as the workstation for 5×5 and vanilla crafting recipes, and its "+" transfer button stamps a shown recipe straight into the pattern grid (refusing, with a message, any layout the grid cannot hold).

#### Impact Piston (`impact_piston`)
- Heavy stamping machine that acts on the block directly **beneath** it: while powered it winches a central rod upward over 20 ticks at 64 FE/tick, then drops it in 3 ticks, and the landing applies a `zps:impact` recipe to the struck block.
- Losing redstone power freezes the rod where it is (a resumed stroke still costs a full raise) and lets it settle back down limply; a stroke that reaches the top is committed and always lands, whatever the signal does meanwhile.
- Energy only — no inventory: 8,192 FE buffer receiving up to 512 FE/tick, exposed on every face. Right-click opens a plain energy readout, and JEI shows every impact recipe under an "Impact Piston" tab.
- Built-in recipes crack stone/deepslate/nether/polished-blackstone bricks and deepslate tiles, shatter any `#c:glass_blocks` into nothing, and pound cobblestone → gravel, sandstone → sand, red sandstone → red sand. The three crushing recipes have a 1-in-20 chance of leaving a *suspicious* block instead, pre-buried with 1–2 random nuggets drawn from a per-material tag (`zps:resources_in_cobblestone` / `_sandstone` / `_red_sandstone`).
- Paired with a cobblestone generator and a Sieve, this makes some raw resources renewable: the piston pounds cobblestone into gravel — occasionally suspicious gravel — which falls through the Sieve below and gives up its buried copper, aluminum, and iron nuggets. Nine of each craft back into an ingot, so a single automated loop yields **copper, aluminum, and iron** indefinitely. The sandstone recipes do the same for iron and lithium (from sand) and gold and copper (from red sand), given a renewable sandstone supply.

#### Sieve (`sieve`)
- A five-slot pan (hopper-style inventory) whose frame collides but whose mesh does not: players and mobs stand on top, while everything else drops through — slowed as it passes.
- Falling blocks that fall through are **sifted** — a falling suspicious sand/gravel surrenders its buried loot into the sieve's slots and continues falling as plain sand or gravel; anything the sieve cannot hold spills on the ground.
- Storage only for now (no processing recipes); exposes its inventory to hoppers and pipes and drops its contents when broken.
- Crafted on a 5×5 grid — either in the Assembler via `zps:shaped_5x5`, or on a Create mechanical crafter when Create is installed.

#### Robotic Arm (`robotic_arm`)
- A scriptable arm that moves its hand to any block within 4 blocks, taking 15 ticks per move and consuming 8 FE/tick while moving (8,192 FE capacity, receive-only).
- Controlled by script commands from an adjacent Serial Bus: `take_items`, `put_items`, `use`, `shift_use`, and `drop_items`; accepts a new instruction about every 16 ticks.
- Carries a single held stack and acts through a fake player, so `use`/`shift_use` can right-click blocks, place blocks/items, and fill or empty buckets with fluids at the target.
- GUI settings control the per-action item count (1–64) and a range-preview toggle; transfers visually open chests/barrels/shulkers during the move.
- Range and interaction are Valkyrien Skies-aware, working across ships/sublevels via world-space projection.

#### Incomplete Robotic Arm (`incomplete_robotic_arm_0` / `_1` / `_2`)
- The three placement/build stages of the Robotic Arm: right-click each with a Robotic Arm Segment to advance (0 → 1 → 2 → finished `robotic_arm`), consuming one segment per stage.
- Share the finished arm's collision shape and play a repair sound/particles per segment; completing the arm grants the `robotic_arm_completed` advancement.
- Purely inert until completed — no work, energy, or item storage.

### 1.4 Power Generation & Storage

#### Coal Burner (`coal_burner`)
- Burns coal-type fuels in a single fuel slot to generate 32 FE/tick; accepts anything in the `coals` tag plus Blocks of Coal (Coal/Charcoal ~80s each, a Block of Coal ~800s).
- Buffers up to 8,192 FE and pushes up to 256 FE/tick out of any of its 6 faces into adjacent energy-accepting blocks; never accepts incoming FE.
- Has a GUI (fuel slot + energy/burn readout) and a lit blockstate while burning; contents drop when broken.

#### Power Cell (`power_cell`)
- Bulk FE battery storing up to 2,097,152 FE (~2.1M) per cell, transferring up to 16,384 FE/tick in and out of any face.
- Multiblock: cells placed in a square column (1×1 to 3×3 wide, up to 32 tall) merge into one battery in the same way as Create's fluid tank. Only capacity scales with the cell count (transfer stays at 16,384 FE/tick and item charging at 1,024 FE/tick); the whole structure shares one charge slot and GUI, reachable from any cell, and any face accepts/provides energy for the pool. Placing a cell on the top or bottom of a structure fills the whole new layer from the stack (sneak to place a single cell).
- Charges an inserted energy-capable item at 1,024 FE/tick via its charge slot and GUI, regardless of structure size.
- Emits a comparator signal proportional to fill and shows the fill visually: the end plates and inset walls are drawn only on the structure's outer faces, and one divider ring rises through the whole structure.
- The multiblock plumbing lives in `g_mungus.zps.multiblock` (`MultiblockPart`, `ConnectivityHandler`, `MultiblockBlockEntity`, `MultiblockBlockItem`) and is reusable for other blocks.

#### Creative Power Cell (`creative_power_cell`)
- Infinite FE source: pushes unlimited energy into every adjacent energy-accepting block each tick and never draws power itself.
- Charges an inserted energy item at 4,096 FE/tick through its charge slot; its GUI reports infinite stored energy.
- A creative/testing supply — it cannot be filled and has no finite buffer.

---

## 2. Functional Items

#### Power Drill (`power_drill`)
- Powered iron-tier mining tool storing up to 128,000 FE and consuming 96 FE per block; mines and drops correctly only while charged, otherwise degrading to base mining speed.
- Effective on pickaxe- and shovel-mineable blocks, with a large Efficiency-V–like bonus on pickaxe-tagged blocks.
- Not damageable or enchantable; recharged via FE, shows a cyan energy bar, and flashes "LOW POWER" when empty.

#### Chainsaw (`chainsaw`)
- Powered cutting tool sharing the same energy system: 128,000 FE capacity, 96 FE per block, cyan energy bar, and "LOW POWER" warning.
- Effective on axe- and hoe-mineable blocks, with the bonus mining-speed boost on axe-tagged blocks (wood/leaves).
- Not damageable or enchantable; charged through FE rather than durability.

#### Address Pad (`address_pad`)
- Saves named block positions ("Addresses"): right-click a block to store it (max 15 entries; names limited to letters/digits/`.`/`_`).
- Right-click the air to open a GUI for renaming/removing saved Addresses.
- Right-click a Script Terminal to mount the pad, making its Addresses available as `@name` labels in scripts; suppresses normal block interaction while held.

#### Robotic Arm Segment (`robotic_arm_segment`)
- Construction component: right-click an Incomplete Robotic Arm to attach it, advancing the build until a complete Robotic Arm is formed.
- Doubles as an improvised melee weapon (+4 attack damage, −2.8 attack speed, +2 knockback).

#### Energy Storage Module (`energy_storage_module`)
- Non-functional crafting component (a plain item, no active behavior) used as a recipe ingredient representing a battery/energy sub-assembly (e.g. in the power drill, chainsaw, and power cell).

---

## 3. Resource & Storage Blocks

Ore and raw/refined material blocks. Most are ordinary storage/ore blocks, with a couple
of functional quirks noted.

- **Bauxite (`bauxite`)** — aluminum-bearing stone (andesite-like, netherrack sound); mined and smelted/processed into aluminum.
- **Lithium Ore (`lithium_ore`) / Deepslate Lithium Ore (`deepslate_lithium_ore`)** — ores that drop raw lithium and 3–7 XP when mined without Silk Touch.
- **Aluminum Block (`aluminum_block`)** — decorative/storage block of refined aluminum (copper-block-like).
- **Lithium Block (`lithium_block`) / Raw Lithium Block (`raw_lithium_block`)** — ⚠️ reactive: **explode when placed in or next to water**, discouraging naive storage near liquids.
- **Aluminum Plating (`aluminum_plating`)** — a metal building block that also auto-generates matching slab/stairs/wall variants.
- **Suspicious Red Sand (`suspicious_red_sand`)** — vanilla suspicious sand in red: brushes out into red sand and holds one buried item. Produced by the Impact Piston's red sandstone recipe.
- **Vanilla suspicious sand & gravel** are replaced with the same reinforced version — unlike vanilla they survive falling *and* being pushed by pistons with their buried payload intact, and now drop plain sand/gravel when broken (or when they fall somewhere they cannot land) rather than nothing.

---

## 4. Decorative / Non-Functional Blocks

A large family of purely cosmetic/structural building blocks (no logic beyond shape, light,
or collision). Grouped here as one set:

- **Space Plating (`space_plating`, `riveted_space_plating`, + all 16 dye colors, `chrome_space_plating`, `agate_space_plating`)** — the mod's core metal building blocks; each variant also generates matching slab, stairs, and wall pieces. Riveted plating is tougher (higher blast resistance).
- **Structural framing (`space_truss`, `space_scaffold`, `space_mesh_block`, `space_grating_block`)** — trusses, scaffolds, and see-through mesh/grating for industrial builds; the grating is non-occluding and hides seams against adjacent grating/grated cables.
- **Catwalk (`catwalk`) & Catwalk Stairs (`catwalk_stairs`)** — walkable metal platforms with thin top-layer collision (stand on top, pass underneath); stairs orient to the player and can double as cable-connection blockers.
- **Industrial Lights (`small_industrial_light`, `industrial_light`, `large_industrial_light`)** — mountable lamps in three sizes that emit light level 15 when lit (`LIT` blockstate) with distinct collision footprints.
- **Caution Blocks (`caution_block`, `radiation_caution_block`, `void_caution_block`)** — hazard-striped decorative marker blocks for signage and industrial theming.

---

## 5. Crafting Materials (Non-Functional Items)

Plain items that exist solely as recipe ingredients, with no active behavior:

- **Space-metal parts:** space_metal_ingot, space_metal_plate, space_metal_mesh, space_metal_rod, space_metal_pipe, space_metal_screw, space_metal_bolt, space_metal_spring
- **Metals & raw drops:** aluminum_ingot, aluminum_nugget, copper_nugget, raw_lithium, lithium_ingot, lithium_nugget
- **Electronic components:** capacitor, transistor, modulator, copper_magnetron, gold_magnetron
- **Wires & spools:** copper_wire, gold_wire, aluminum_wire, verdite_wire, empty_spool, copper_spool, gold_spool, verdite_spool
- **Sub-assembly:** energy_storage_module

All are registered as plain items used only as crafting inputs.