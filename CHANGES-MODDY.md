# moddy2: 2.0.0-opac033-moddy2, config standard

Output jar: `build/libs/dont_touch_our_structures-1.21.1-NeoForge-2.0.0-opac033-moddy2.jar` (`mod_version` in `gradle.properties` is now `2.0.0-opac033-moddy2`).

**Compatibility: no key renames. Existing config files work unchanged.** Config type (COMMON), file name (`config/dont_touch_our_structures-common.toml`), and all 30 key paths, types, defaults and number ranges are the same as upstream 2.0.0, so no migration is needed. `ModConfigSpecTest` pins them.
On the first boot NeoForge sees the new comments and rewrites the file with them. It keeps a backup copy next to the file, and every value is preserved.
Protection behaviour and the OPAC mixin are unchanged.

## Config changes (`config/ModConfig.java`)
- Every option has a new comment, following CODING-GUIDE section 3a. Each one covers:
  - what the option does, and what changing it does
  - that existing claims are never removed, since only new claims are blocked
  - the meaning of 0/empty, and `Default:` for booleans, strings, lists and enums
  - units in `[in chunks]` / `[in seconds]` form
  - `Performance:` notes (padding scan size, cache off, debug logging)
  - `Requires …` links, a copy-paste example for every list and for the padding/message formats, and the meaning of each enum value
- The first section carries a file header: COMMON, shared by all worlds, no `serverconfig/` copy, live reload, and a pointer to `/dtos check` and `/dtos debug`.
- Every section (`general`, `structure_types`, `structures`, `dimensions`, `biomes`, `padding`, `messages`, `performance`, `compat`) now has a comment and a translation key. Sections use `push`/`pop`, which gives the same key paths as the old dotted names. Options are ordered from most to least important.
- `.translation("dont_touch_our_structures.configuration.<section>.<key>")` is set on every option and section. Labels and `.tooltip` entries are in `en_us.json`, and the unused example-mod template entries were removed from it.
- The comments now document facts about the existing behaviour:
  - "surface" means terrain adaptation ≠ NONE, so temples, mansions, monuments and end cities count as "underground" and are not protected by default
  - the biome filter does not apply to padding
  - the padding scan radius is the largest padding value
  - the OPAC fallback listener (`opac_use_mixin = false`) can remove existing claims on a whole-region change
- `structure_types.terrain_adaptations_to_protect` and `structure_types.generation_steps_to_protect` were never read by the protection code, upstream or here. They are kept so existing files stay valid, documented as "not used in this version", and a WARN is logged if they differ from the default. Wiring them in would change protection behaviour, so that is left for a separate decision.
- Validators:
  - ids now also reject `""` and an empty path (`"minecraft:"`)
  - the terrain adaptation and generation step validators are built from the vanilla enums, which adds the missing `ENCAPSULATE` (trial chambers). This only loosens them.
  - padding entries are parsed without exceptions; accepted inputs are the same as before
  - number ranges are unchanged (op level 1–4, padding 0–16, cache TTL 10–3600 s, cache size 100–10000)
- `compat.opac_use_mixin` is now `.worldRestart()`. It was only honoured at server start (listener registration), but the mixin read it live, so a live edit could leave OPAC unprotected until restart. Now the value is held until restart, and a WARN says so if the file changes.

## Reload handling
- The config is applied on `ModConfigEvent.Loading` and `Reloading` only (it used to run on every `ModConfigEvent`, including Unloading), filtered to this mod's spec.
- On a file reload while a server is running, the new values are applied on the server thread (`server.execute`), so a claim check never sees half-updated values.
- **Fix:** the per-chunk `StructureCache` is now invalidated on every config apply, through a generation counter that is checked lazily on the next get/put. Before, editing the file left old results cached for up to `cache_ttl_seconds`; only `/dtos reload` cleared them.
- One INFO line with a compact effective-config summary is logged on load, reload and `/dtos reload`, for example:
  `[DTOS] Config loaded: bypassOp=level 2, singleplayer=off, surface=on, underground=off, structures=BLACKLIST(0), dimensions=DISABLED, biomes=DISABLED, padding=off, message=auto, cache=300s/1000, opac=on (mixin), ftbchunks=on, debug=off`
- Live: everything except `compat.opac_use_mixin` (restart). `compat.opac_enabled` is live with the mixin; in listener mode it needs a restart, as documented.

## Cross-field WARN checks (`config/ConfigChecks.java`)
Each problem logs one WARN on load and reload. Nothing crashes and the file is never rewritten for these.
- `protect_surface` and `protect_underground` are both false, so nothing is protected
- `structures`/`dimensions`/`biomes` in WHITELIST mode with an empty whitelist, stating the effect for each
- the same id in both the whitelist and the blacklist of a section
- padding enabled, with `default_chunks = 0` and no custom entry above 0
- `custom_structure_padding` entries while padding is disabled
- duplicate structures in `custom_structure_padding` (the last one wins)
- `terrain_adaptations_to_protect` / `generation_steps_to_protect` changed from the default (they have no effect)
- unknown `{placeholders}` in `custom_denial_message`
- no active integration (OPAC/FTB Chunks both missing or disabled)
- `opac_use_mixin` changed in the file while running (it needs a restart)
- At server start, and on reload while running: unknown ids, checked against the server registries or level list, with one WARN per list, in:
  - `structures.whitelist` and `structures.blacklist`
  - `padding.custom_structure_padding`
  - `biomes.whitelist` and `biomes.blacklist`
  - `dimensions.whitelist` and `dimensions.blacklist`

  Padding values above 16 are already dropped by the NeoForge element validator, which logs its own correction.

## Build and tests
- `build.gradle` adds `mavenCentral()`, JUnit 5.10.2, and a test classpath that reuses main's (plain JUnit, no FML boot).
- `src/test/.../config/ModConfigSpecTest.java` (7 tests) checks:
  - exact key paths, defaults and ranges against upstream 2.0.0
  - that only `opac_use_mixin` is restart-only
  - that a typical existing server file needs no value correction
  - that garbage entries are rejected
  - that every option and section has a translation key, label and tooltip

## Not verified
The new jar has not been booted on a server here. Still to check: the first-boot comment rewrite and backup, the INFO summary line, and that the WARNs fire as expected.

---

# Don't Claim Our Structures 2.0.0 — OPAC 0.33.0 patch (moddy)

Base: branch `1.21.1`, commit `fa683b3` ("2.0 rewrite"), stock 2.0.0. Version, mod id and all other dependencies are unchanged.

Output jar: `build/libs/dont_touch_our_structures-1.21.1-NeoForge-2.0.0-opac033-moddy.jar`
(byte-identical copy of `build/libs/dont_touch_our_structures-1.21.1-NeoForge-2.0.0.jar`, SHA-1 `44bbda115a858fe7462bdbe646ce592c626f949f`).

Targets: NeoForge 1.21.1 (tested compile against 21.1.123; server 21.1.267), Open Parties and Claims NeoForge 1.21.1 **0.33.0**, Java 21. Server-side only.

## Changes

### 1. `build.gradle`: OPAC dependency
- `implementation "curse.maven:open-parties-and-claims-636608:6476205"` (OPAC 0.24.2)
  → `compileOnly "curse.maven:open-parties-and-claims-636608:9221991"` (OPAC NeoForge 1.21.1 0.33.0).
- The resolved jar's SHA-1 is `6075cf36ac8d70bd1fd870e0eb9ac84685388df7`, which matches the official 0.33.0 jar.
- FTB Chunks / FTB Library / FTB Teams / Architectury coordinates are unchanged.

### 2. `src/main/java/.../mixin/opac/ServerClaimsManagerMixin.java`: rewritten for the 0.33.0 API
- **Crash 1 (`getServer` visibility):** removed the private reflection helper `getServer()`. It was treated as an implicit overwrite of OPAC's new public `getServer()`. It is replaced by:
  `@Shadow public abstract MinecraftServer getServer();`
  The `java.lang.reflect.Field` import is gone.
- **Crash 2 (`ClaimsManager$Action` missing):** replaced `xaero.pac.common.claims.ClaimsManager.Action` with `xaero.pac.common.claims.action.api.ClaimingAction`. The action check is now `action != ClaimingAction.CLAIM` instead of `toString().equals("CLAIM")`, so UNCLAIM, FORCELOAD, ANCHOR and the other actions are never blocked.
- **`tryToClaimTyped` inject:** the descriptor and handler parameters now match 0.33.0:
  `(ResourceLocation, UUID, int, ResourceLocation fromDimension, int fromX, int fromZ, int x, int z, boolean force, boolean ignoreAnchors, ClaimResult.Type claimingPermissionResult) -> ClaimResult`.
  A protected chunk returns `new ClaimResult<>(null, ClaimResult.Type.ADDON_FORBIDS)`. Stock returned `ALREADY_CLAIMED`.
- **`tryClaimActionOverArea` inject:** now targets the long worker overload, with the full JVM descriptor:
  `(...ILnet/minecraft/resources/ResourceLocation;IIIIIILxaero/pac/common/claims/action/api/ClaimingAction;ZILjava/util/function/Predicate;Lxaero/pac/common/claims/result/api/ClaimResult$Type;Ljava/util/function/Consumer;)V`.
  The short overload and `tryToClaimArea` both funnel into this method. The method is `void`, so the handler takes a `CallbackInfo`. On a blocked area it:
  - sends the player the denial message, as before
  - calls `listener.accept(new AreaClaimResult(blockedResults, new HashSet<>(), dimension, left, top, right, bottom))`, using the new 7-argument constructor
  - calls `ci.cancel()`
- **Robustness:**
  - Both injectors keep `require = 0`, and `defaultRequire: 0` stays in the mixin json, so a descriptor mismatch on another OPAC version skips the injection instead of crashing boot.
  - Handler bodies catch `LinkageError` and fail open.
  - The dimension helper is now `@Unique dtos$dimensionToResourceKey`.
  - Kept from stock: the `ModConfig.opacEnabled` / `opacUseMixin` early return, the chunk loop over `left..right` × `top..bottom`, and the ops bypass inside `StructureProtectionManager.checkProtection`.
  - Note: `@Shadow getServer()` requires OPAC ≥ 0.27.5, where that method exists. That is fine for 0.33.0.
- All three target descriptors and the `AreaClaimResult` constructor were checked with `javap -s` against the 0.33.0 jar.

### 3. `src/main/resources/dont_touch_our_structures.mixins.json`: refmap warning
- Removed `"refmap": "dont_touch_our_structures.refmap.json"`. The build never generated that file, and the only mixin is `remap = false`, so no refmap is needed. This removes the "Reference map ... could not be read" warning.
- The mixin entry `opac.ServerClaimsManagerMixin` and the `[[mixins]]` block in `neoforge.mods.toml` are kept.

### Not changed
- `compat/OPaC/OPaCCompat.java`: the listener fallback compiles unchanged against 0.33.0.
- `compat/FTBChunks/FTBChunksCompat.java`, `gradle.properties` (version 2.0.0, neo_version 21.1.123), the Gradle wrapper (8.8), and all other sources.
- No client mixins were added.

### Build environment note
The machine's only JDK is Java 25, and Gradle 8.8 cannot run on it ("Unsupported class file major version 69"). Temurin JDK 21 was downloaded into `~/.gradle/jdks/` (Gradle's own auto-provision location), and the build was run with `JAVA_HOME` pointing at it. To rebuild:

```bash
JAVA_HOME="$HOME/.gradle/jdks/jdk-21.0.13.1+2" ./gradlew.bat build
```

## Recommended config (`dont_touch_our_structures` common/server config)

| Key | Value | Why |
|---|---|---|
| `compat.opac_enabled` | `true` | Turns on OPAC protection. |
| `compat.opac_use_mixin` | `true` | **Recommended.** Claims are denied before they are made, with no flicker. Log line: `OPaC mixin-based protection active`. |

Fallback: if the mixin path misbehaves on a future OPAC version, set `compat.opac_use_mixin = false` with `compat.opac_enabled = true`. The mixin still attaches harmlessly, and the claims-manager listener unclaims protected chunks after the fact (a brief flicker is expected). Log line: `OPaC fallback listener registered (mixin disabled)`.

## Not yet verified
The jar compiles against 0.33.0, but it has **not** been booted on a server here. Still to check on the live server:
- boot logs are clean (no mixin errors, no refmap warning)
- a non-op claim on a village chunk is denied, while wilderness chunks can still be claimed
- the fallback path works with `opac_use_mixin=false`
