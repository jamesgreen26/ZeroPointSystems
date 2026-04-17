# Zero Point Systems 1.21.1 NeoForge Port Plan

## Goal

Port the current `1.20.1` Forge/LegacyForge project to `1.21.1` NeoForge while preserving existing mod behavior, except for Valkyrien Skies and Genesis compatibility, which should be intentionally removed for this port.

## Success Condition

All existing gametests in `src/main/java/g_mungus/zps/gametest` can be run on the `1.21.1` NeoForge port, and none fail.

## Explicit Non-Goals

- Do not keep Valkyrien Skies compatibility for the `1.21.1` port.
- Do not keep Genesis compatibility for the `1.21.1` port.
- Do not spend time building temporary shims for VS or Genesis unless those mods become available on the target version later.

## Progress Update

### Current Status

- The project has been moved off `net.neoforged.moddev.legacyforge` and onto `net.neoforged.moddev`.
- Build metadata now targets `1.21.1` NeoForge and uses `META-INF/neoforge.mods.toml`.
- Valkyrien Skies and Genesis compat code/docs have been removed from the port branch.
- The project now compiles cleanly on `1.21.1` NeoForge.
- The NeoForge game test server starts successfully and all required gametests pass.

### Completed So Far

- Updated Gradle/toolchain/build metadata:
  - `build.gradle`
  - `gradle.properties`
  - `settings.gradle`
  - `run_gametests.sh`
  - `src/main/resources/META-INF/neoforge.mods.toml`
- Removed the old Forge metadata file:
  - `src/main/resources/META-INF/mods.toml`
- Removed Valkyrien Skies and Genesis dependencies/repos from the build.
- Removed Valkyrien Skies and Genesis compat source:
  - `src/main/java/g_mungus/zps/compat/VSCompat.java`
  - `src/main/java/g_mungus/zps/compat/genesis/GenesisCompat.java`
  - `src/main/java/g_mungus/zps/compat/genesis/CelestialArgument.java`
- Rewrote the remaining compat entry point so it no longer exposes VS/Genesis paths:
  - `src/main/java/g_mungus/zps/compat/Compat.java`
  - `src/main/java/g_mungus/zps/mixin/ArgumentTypeInfosMixin.java`
- Removed VS/Genesis manual/docs/navigation/lang content:
  - `docs/genesis.md`
  - `docs/genesis.html`
  - `docs/valkyrien_skies.md`
  - `docs/valkyrien_skies.html`
  - `docs/getters.md`
  - `docs/mappers.md`
  - `docs/_data/docs_nav.yml`
  - `src/main/java/g_mungus/zps/manual/ModManuals.java`
  - `src/main/resources/assets/zps/lang/en_us.json`
  - `src/main/resources/assets/zps/doc/en_us/.obsidian/workspace.json`
- Added the bundled Create Registrate jar to the workspace so the port can compile against the Create 1.21.1 stack:
  - `libs/Registrate-MC1.21-1.3.0+62.jar`

### Partially Completed

- The Forge-to-NeoForge API migration has been completed across the ported codebase.
- Registry classes, event wiring, networking, capabilities, data-component usage, block interactions, and entity/block entity serialization have been updated for `1.21.1`.
- Create, Ponder, Catnip, Flywheel, Vanillin, JEI, and other retained integrations are wired to working `1.21.1` NeoForge artifacts.

### Verified State

- `./gradlew compileJava` has been run on the current port branch.
- `./gradlew runGameTestServer` has been run on the current port branch.
- The build gets through the NeoForge artifact/bootstrap stage and starts the game test server successfully.
- All `53` required gametests pass on the `1.21.1` NeoForge port.

## Workstreams

### 1. Move the build from LegacyForge to NeoForge

- Replace the current `net.neoforged.moddev.legacyforge` setup in `build.gradle` and the `legacyForge {}` block with the `1.21.1` NeoForge build configuration.
- Update `gradle.properties` for the target Minecraft version and the target NeoForge version.
- Update the direct dependency versions for Create and the other kept dependencies to their `1.21.1` NeoForge-compatible versions.
- Re-check the required Java/toolchain level for the target stack and update the Gradle Java configuration accordingly.
- Update mod metadata and version ranges in `src/main/resources/META-INF/mods.toml`, or migrate to the NeoForge metadata format if the loader requires it.
- Update the gametest run setup so the current `gameTestServer` run and `forge.enabledGameTestNamespaces` usage have a correct NeoForge equivalent.

Status: done.

### 2. Remove VS and Genesis cleanly

- Remove the VS and Genesis dependencies and any repositories that only exist for them from `build.gradle`.
- Remove or rewrite compat entry points in:
  - `src/main/java/g_mungus/zps/compat/Compat.java`
  - `src/main/java/g_mungus/zps/compat/VSCompat.java`
  - `src/main/java/g_mungus/zps/compat/genesis/GenesisCompat.java`
  - `src/main/java/g_mungus/zps/compat/genesis/CelestialArgument.java`
  - `src/main/java/g_mungus/zps/mixin/ArgumentTypeInfosMixin.java`
- Remove manual/docs/navigation/lang references that expose Genesis or Valkyrien Skies content:
  - `docs/genesis.md`
  - `docs/valkyrien_skies.md`
  - `docs/getters.md`
  - `docs/_data/docs_nav.yml`
  - `src/main/java/g_mungus/zps/manual/ModManuals.java`
  - related translation keys in `src/main/resources/assets/zps/lang/en_us.json`
- Treat VS/Genesis removal as deletion, not as a compile-time toggle. Dead compat paths should not remain in the shipped `1.21.1` port.

Status: done for the current port branch.

### 3. Migrate Forge APIs to NeoForge APIs

- Audit central entry points first: `ZPSMod`, registry classes, config, networking, command registration, and client setup.
- Replace Forge imports/usages with NeoForge equivalents across event bus wiring, deferred registers, config, networking, capabilities, fake players, client events, and gametest helpers.
- Fix mixins and command argument registration after the loader migration, because those areas are likely to break on package or bootstrap changes.

Status: done.

### 4. Introduce abstraction for changed `Block` interaction overrides

- Do not patch every concrete block independently when `Block` override signatures change in `1.21.1`.
- Add compatibility shims in shared base classes so the bulk of the codebase can keep its current behavior.
- In particular, add a shared interaction method to `CableComponentBlock`, for example an `InteractionResult use(...)` helper, and let `useWithoutItem`, `useItemOn`, and any other required entry points delegate to it as needed.
- Apply the same pattern to other repeated block families if more override splits appear.
- The intent is to isolate version-specific method churn in a small number of abstract classes instead of scattering it through every block implementation.

Status: done.

### 5. Update Create and retained integrations

- Keep Create compatibility and move it to the `1.21.1` NeoForge Create stack.
- Do not treat `ace.actually.radios:radios:1.2.0` as a porting task. The bundled `radios` jar is already on `1.21.1`, and its API has not changed.
- Update Create, Ponder, Flywheel, Registrate, JEI, Fusion, Spark, MixinExtras, MixinSquared, and any other retained direct dependencies to their target-version equivalents.
- Re-audit `src/main/java/g_mungus/zps/compat/create` after the dependency updates, because registry hooks, behaviors, and helper APIs are likely to shift between versions.
- Verify that Create-backed script executors, display-link integrations, and any client-side hooks still compile and behave correctly after the dependency bump.

Status: done.

### 6. Preserve and restore the gametest suite

- Port the gametest imports and annotations from the current Forge setup to the NeoForge setup.
- Keep the game test structure templates usable on the target version. For `1.21.1`, these resources now live under `src/main/resources/data/zps/structure/gametest`.
- Audit test registration and discovery so the full existing suite runs. The current explicit registration in `ZPSMod.registerGameTests(...)` only names a subset of the classes under `src/main/java/g_mungus/zps/gametest`, so this should be checked during the port.
- Standardize on `./run_gametests.sh` or an updated NeoForge equivalent as the verification entry point.

Status: done.

## Remaining Work

No remaining work is required to satisfy the port goal or success condition.

### Non-Blocking Follow-Up

- NeoForge still emits some non-fatal warnings during the game test run, including deprecated `@EventBusSubscriber` usage in a few client-only classes and missing `minecraft:placeable` tag references for `zps:logo` and `zps:quasar`.
- JourneyMap reports that the selected beta is outdated, but this does not block compile, startup, or the game test suite.

## Recommended Order

1. Get the NeoForge `1.21.1` build, metadata, and dependency graph compiling.
2. Remove VS and Genesis code, docs, and dependency references.
3. Port the core loader and API surface from Forge to NeoForge.
4. Add shared `Block` interaction abstractions to absorb method signature churn.
5. Update Create and the other retained integrations.
6. Bring the full gametest suite back to green and use it as the merge gate.

## Done When

- The mod builds and starts on `1.21.1` NeoForge.
- Create and the other retained dependencies are updated to working `1.21.1` NeoForge versions.
- Valkyrien Skies and Genesis compat code is removed from the port.
- Interaction override churn is handled through shared abstractions instead of repeated one-off block rewrites.
- All existing gametests can be run, and none fail.
