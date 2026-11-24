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
