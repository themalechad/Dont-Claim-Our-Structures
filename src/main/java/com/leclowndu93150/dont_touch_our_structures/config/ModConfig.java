package com.leclowndu93150.dont_touch_our_structures.config;

import com.leclowndu93150.dont_touch_our_structures.DontClaimOurStructures;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * COMMON config, file {@code config/dont_touch_our_structures-common.toml}.
 * <p>
 * Every key path, type and default is unchanged from upstream 2.0.0 so existing files keep working.
 * Values are copied into the static fields below on load/reload (on the server thread when a server is running);
 * hot paths read those fields, and derived caches watch {@link #generation()}.
 */
@EventBusSubscriber(modid = DontClaimOurStructures.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ModConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(DontClaimOurStructures.MODID);
    private static final String KEY = DontClaimOurStructures.MODID + ".configuration.";

    public static final int PADDING_MIN = 0;
    public static final int PADDING_MAX = 16;

    static final List<String> DEFAULT_TERRAIN_ADAPTATIONS = List.of("BURY", "BEARD_THIN", "BEARD_BOX");
    static final List<String> DEFAULT_GENERATION_STEPS = List.of("SURFACE_STRUCTURES");
    private static final Set<String> TERRAIN_ADAPTATION_NAMES = enumNames(TerrainAdjustment.values());
    private static final Set<String> GENERATION_STEP_NAMES = enumNames(GenerationStep.Decoration.values());

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---------------------------------------------------------------- general
    static {
        BUILDER.comment(
                        "Don't Claim Our Structures: blocks land claims (Open Parties and Claims, FTB Chunks) on chunks that contain generated structures.",
                        "This file is config/dont_touch_our_structures-common.toml (COMMON type): one file for the whole game or server, shared by every world.",
                        "There is no per-world copy in serverconfig/, so modpack defaults and server edits both go here.",
                        "Edits are applied live when the file is saved (or with /dtos reload), except options marked \"Needs a server restart.\"",
                        "This mod only blocks NEW claims. Changing any option never removes claims that already exist",
                        "(the one exception is the OPAC fallback listener, see compat.opac_use_mixin).",
                        "Use /dtos check and /dtos debug in game to see why a chunk is or isn't protected.")
                .translation(KEY + "general")
                .push("general");
    }

    private static final ModConfigSpec.BooleanValue BYPASS_WITH_OP = BUILDER
            .comment("Lets operators claim protected chunks anyway.",
                    "Turning it off makes protection apply to operators too; existing claims are not affected.",
                    "Only works when the claiming player is online and known; claims made from the console or for other ids are never bypassed.",
                    "Default: true")
            .translation(KEY + "general.bypass_with_op")
            .define("bypass_with_op", true);

    private static final ModConfigSpec.IntValue OP_BYPASS_LEVEL = BUILDER
            .comment("Operator permission level a player needs to bypass protection.",
                    "Lowering it lets more players bypass; raising it restricts the bypass to higher-level operators.",
                    "Requires bypass_with_op = true (ignored otherwise).")
            .translation(KEY + "general.op_bypass_level")
            .defineInRange("op_bypass_level", 2, 1, 4);

    private static final ModConfigSpec.BooleanValue AFFECT_SINGLEPLAYER = BUILDER
            .comment("Applies protection in single-player and LAN worlds too.",
                    "false = integrated (single-player/LAN) worlds can claim anything; dedicated servers are always protected.",
                    "Default: false")
            .translation(KEY + "general.affect_singleplayer")
            .define("affect_singleplayer", false);

    private static final ModConfigSpec.BooleanValue ENABLE_DEBUG_LOGGING = BUILDER
            .comment("Logs every protection check, cache hit and blocked claim at INFO level, for troubleshooting.",
                    "Performance: very chatty on busy servers (area claims log one line per chunk); leave off normally.",
                    "Default: false")
            .translation(KEY + "general.enable_debug_logging")
            .define("enable_debug_logging", false);

    // ---------------------------------------------------------------- structure_types
    static {
        BUILDER.pop()
                .comment("Which kinds of structures are protected at all. A structure counts as \"surface\" when its terrain adaptation is anything",
                        "other than NONE (vanilla: villages, pillager outposts, strongholds, ancient cities, trail ruins, trial chambers),",
                        "and as \"underground\" when it is NONE (vanilla: mineshafts, temples, mansions, monuments, fortresses, end cities, shipwrecks, ruined portals...).")
                .translation(KEY + "structure_types")
                .push("structure_types");
    }

    private static final ModConfigSpec.BooleanValue PROTECT_SURFACE_STRUCTURES = BUILDER
            .comment("Protects surface structures (terrain adaptation other than NONE).",
                    "Turning it off allows new claims on them; turning it on does not remove claims that already cover one.",
                    "Default: true")
            .translation(KEY + "structure_types.protect_surface")
            .define("protect_surface", true);

    private static final ModConfigSpec.BooleanValue PROTECT_UNDERGROUND_STRUCTURES = BUILDER
            .comment("Protects underground structures (terrain adaptation NONE), which includes many above-ground vanilla ones such as temples and mansions.",
                    "Turning it on blocks claims over much larger areas (mineshafts and fortresses span many chunks); existing claims are kept.",
                    "Default: false")
            .translation(KEY + "structure_types.protect_underground")
            .define("protect_underground", false);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> TERRAIN_ADAPTATIONS_TO_PROTECT = BUILDER
            .comment("Not used in this version: surface vs underground is decided only by protect_surface/protect_underground.",
                    "Kept so existing config files stay valid; changing it has no effect (a WARN is logged if it differs from the default).",
                    "Values: NONE = no adaptation, BURY = buried in terrain, BEARD_THIN = terrain raised/lowered lightly under it,",
                    "BEARD_BOX = terrain boxed around it, ENCAPSULATE = enclosed in terrain.",
                    "Example: [\"BURY\", \"BEARD_THIN\", \"BEARD_BOX\"]",
                    "Default: [\"BURY\", \"BEARD_THIN\", \"BEARD_BOX\"]")
            .translation(KEY + "structure_types.terrain_adaptations_to_protect")
            .defineListAllowEmpty("terrain_adaptations_to_protect", DEFAULT_TERRAIN_ADAPTATIONS, ModConfig::isTerrainAdaptation);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> GENERATION_STEPS_TO_PROTECT = BUILDER
            .comment("Not used in this version: the generation step of a structure does not affect protection.",
                    "Kept so existing config files stay valid; changing it has no effect (a WARN is logged if it differs from the default).",
                    "Values (world generation steps, in order): RAW_GENERATION, LAKES, LOCAL_MODIFICATIONS, UNDERGROUND_STRUCTURES,",
                    "SURFACE_STRUCTURES, STRONGHOLDS, UNDERGROUND_ORES, UNDERGROUND_DECORATION, FLUID_SPRINGS, VEGETAL_DECORATION, TOP_LAYER_MODIFICATION.",
                    "Example: [\"SURFACE_STRUCTURES\", \"STRONGHOLDS\"]",
                    "Default: [\"SURFACE_STRUCTURES\"]")
            .translation(KEY + "structure_types.generation_steps_to_protect")
            .defineListAllowEmpty("generation_steps_to_protect", DEFAULT_GENERATION_STEPS, ModConfig::isGenerationStep);

    // ---------------------------------------------------------------- structures
    static {
        BUILDER.pop()
                .comment("Filter by structure id, applied after structure_types. Ids are exact (no wildcards); a missing namespace means minecraft.",
                        "Use /dtos debug to see the ids of the structures in a chunk.")
                .translation(KEY + "structures")
                .push("structures");
    }

    private static final ModConfigSpec.EnumValue<FilterMode> STRUCTURE_FILTER_MODE = BUILDER
            .comment("How structures.whitelist and structures.blacklist are used.",
                    "WHITELIST = only structures in the whitelist are protected (an empty whitelist protects nothing).",
                    "BLACKLIST = every structure is protected except those in the blacklist.",
                    "DISABLED = both lists are ignored; every structure allowed by structure_types is protected.",
                    "Changing the mode only affects new claims.",
                    "Default: BLACKLIST")
            .translation(KEY + "structures.filter_mode")
            .defineEnum("filter_mode", FilterMode.BLACKLIST);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_WHITELIST = BUILDER
            .comment("Structures to protect. Requires filter_mode = WHITELIST (ignored otherwise).",
                    "Removing an entry allows new claims on that structure; it does not unclaim anything.",
                    "Example: [\"minecraft:village_plains\", \"minecraft:pillager_outpost\"]",
                    "Default: []")
            .translation(KEY + "structures.whitelist")
            .defineListAllowEmpty("whitelist", List.of(), ModConfig::isResourceLocation);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> STRUCTURE_BLACKLIST = BUILDER
            .comment("Structures that are never protected. Requires filter_mode = BLACKLIST (ignored otherwise).",
                    "Adding an entry allows new claims on that structure; removing one does not unclaim anything.",
                    "Example: [\"minecraft:trail_ruins\", \"minecraft:pillager_outpost\"]",
                    "Default: []")
            .translation(KEY + "structures.blacklist")
            .defineListAllowEmpty("blacklist", List.of(), ModConfig::isResourceLocation);

    // ---------------------------------------------------------------- dimensions
    static {
        BUILDER.pop()
                .comment("Filter by dimension. In a dimension that is filtered out, every chunk can be claimed (no structure checks at all).")
                .translation(KEY + "dimensions")
                .push("dimensions");
    }

    private static final ModConfigSpec.EnumValue<FilterMode> DIMENSION_FILTER_MODE = BUILDER
            .comment("How dimensions.whitelist and dimensions.blacklist are used.",
                    "WHITELIST = protection only works in dimensions in the whitelist (an empty whitelist turns protection off everywhere).",
                    "BLACKLIST = protection works everywhere except in dimensions in the blacklist.",
                    "DISABLED = both lists are ignored; protection works in every dimension.",
                    "Default: DISABLED")
            .translation(KEY + "dimensions.filter_mode")
            .defineEnum("filter_mode", FilterMode.DISABLED);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_WHITELIST = BUILDER
            .comment("Dimensions where protection is active. Requires filter_mode = WHITELIST (ignored otherwise).",
                    "Example: [\"minecraft:overworld\", \"minecraft:the_nether\"]",
                    "Default: [\"minecraft:overworld\"]")
            .translation(KEY + "dimensions.whitelist")
            .defineListAllowEmpty("whitelist", List.of("minecraft:overworld"), ModConfig::isResourceLocation);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSION_BLACKLIST = BUILDER
            .comment("Dimensions where protection is off. Requires filter_mode = BLACKLIST (ignored otherwise).",
                    "Example: [\"minecraft:the_end\"]",
                    "Default: []")
            .translation(KEY + "dimensions.blacklist")
            .defineListAllowEmpty("blacklist", List.of(), ModConfig::isResourceLocation);

    // ---------------------------------------------------------------- biomes
    static {
        BUILDER.pop()
                .comment("Filter by biome. The biome is sampled once per claimed chunk, at its centre at Y=64.",
                        "It applies to structures inside the claimed chunk only, not to padding.")
                .translation(KEY + "biomes")
                .push("biomes");
    }

    private static final ModConfigSpec.EnumValue<FilterMode> BIOME_FILTER_MODE = BUILDER
            .comment("How biomes.whitelist and biomes.blacklist are used.",
                    "WHITELIST = structures are only protected in biomes in the whitelist (an empty whitelist protects no structure chunk).",
                    "BLACKLIST = structures are protected in every biome except those in the blacklist.",
                    "DISABLED = both lists are ignored.",
                    "Default: DISABLED")
            .translation(KEY + "biomes.filter_mode")
            .defineEnum("filter_mode", FilterMode.DISABLED);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_WHITELIST = BUILDER
            .comment("Biomes where structures are protected. Requires filter_mode = WHITELIST (ignored otherwise).",
                    "Example: [\"minecraft:plains\", \"minecraft:desert\"]",
                    "Default: []")
            .translation(KEY + "biomes.whitelist")
            .defineListAllowEmpty("whitelist", List.of(), ModConfig::isResourceLocation);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> BIOME_BLACKLIST = BUILDER
            .comment("Biomes where structures are not protected. Requires filter_mode = BLACKLIST (ignored otherwise).",
                    "Example: [\"minecraft:deep_dark\"]",
                    "Default: []")
            .translation(KEY + "biomes.blacklist")
            .defineListAllowEmpty("blacklist", List.of(), ModConfig::isResourceLocation);

    // ---------------------------------------------------------------- padding
    static {
        BUILDER.pop()
                .comment("A buffer zone of chunks around protected structures where claiming is blocked too.",
                        "Distance is counted in chunks in a square (a padding of 1 blocks the 8 chunks around a structure chunk).",
                        "Lowering or turning off padding never unclaims anything; raising it only blocks new claims.")
                .translation(KEY + "padding")
                .push("padding");
    }

    private static final ModConfigSpec.BooleanValue ENABLE_PADDING = BUILDER
            .comment("Turns the padding zone on.",
                    "Performance: each uncached check then looks at every chunk within the largest padding value (see default_chunks).",
                    "Default: false")
            .translation(KEY + "padding.enable")
            .define("enable", false);

    private static final ModConfigSpec.IntValue DEFAULT_PADDING_CHUNKS = BUILDER
            .comment("Padding around every protected structure that has no entry in custom_structure_padding.",
                    "[in chunks]",
                    "0 = no padding except for structures listed in custom_structure_padding.",
                    "Performance: a check scans (2 x padding + 1)^2 chunks (1 = 9, 4 = 81, 16 = 1089) and may load chunks to read their structures;",
                    "area claims multiply this per chunk. Keep it small. Requires enable = true.")
            .translation(KEY + "padding.default_chunks")
            .defineInRange("default_chunks", 1, PADDING_MIN, PADDING_MAX);

    private static final ModConfigSpec.ConfigValue<List<? extends String>> CUSTOM_STRUCTURE_PADDING = BUILDER
            .comment("Per-structure padding that replaces default_chunks for that structure (lower or higher, 0 allowed).",
                    "Format: \"namespace:structure_id=chunks\" with chunks from " + PADDING_MIN + " to " + PADDING_MAX + "; invalid entries are dropped on load.",
                    "Performance: the largest value in this list becomes the scan radius for every check. Requires enable = true.",
                    "Example: [\"minecraft:village_plains=2\", \"minecraft:pillager_outpost=3\"]",
                    "Default: []")
            .translation(KEY + "padding.custom_structure_padding")
            .defineListAllowEmpty("custom_structure_padding", List.of(), ModConfig::isCustomPaddingEntry);

    // ---------------------------------------------------------------- messages
    static {
        BUILDER.pop()
                .comment("The message a player gets when a claim is blocked. Messages are plain text built on the server (English).")
                .translation(KEY + "messages")
                .push("messages");
    }

    private static final ModConfigSpec.ConfigValue<String> CUSTOM_DENIAL_MESSAGE = BUILDER
            .comment("Fixed message to send instead of the auto-generated one. Empty = auto-generate it (the messages.include_* options apply).",
                    "Placeholders: {structure_name}, {structure_type} (surface/underground), {chunk_x}, {chunk_z} (the chunk being claimed).",
                    "Example: \"You can't claim near {structure_name} (chunk {chunk_x}, {chunk_z}).\"",
                    "Default: \"\" (empty)")
            .translation(KEY + "messages.custom_denial_message")
            .define("custom_denial_message", "");

    private static final ModConfigSpec.BooleanValue INCLUDE_STRUCTURE_NAME = BUILDER
            .comment("Adds the structure name to the auto-generated message. Requires custom_denial_message to be empty.",
                    "Default: true")
            .translation(KEY + "messages.include_structure_name")
            .define("include_structure_name", true);

    private static final ModConfigSpec.BooleanValue INCLUDE_STRUCTURE_TYPE = BUILDER
            .comment("Adds \"surface\" or \"underground\" to the auto-generated message (structure chunks only, not padding).",
                    "Requires custom_denial_message to be empty.",
                    "Default: true")
            .translation(KEY + "messages.include_structure_type")
            .define("include_structure_type", true);

    private static final ModConfigSpec.BooleanValue INCLUDE_COORDINATES = BUILDER
            .comment("Adds the claimed chunk's coordinates to the auto-generated message. Requires custom_denial_message to be empty.",
                    "Default: false")
            .translation(KEY + "messages.include_coordinates")
            .define("include_coordinates", false);

    // ---------------------------------------------------------------- performance
    static {
        BUILDER.pop()
                .comment("Cache of per-chunk results (structure and padding checks). The op bypass, single-player and dimension checks are never cached.",
                        "The cache is cleared whenever this file is reloaded, and with /dtos cache clear.")
                .translation(KEY + "performance")
                .push("performance");
    }

    private static final ModConfigSpec.BooleanValue ENABLE_CACHE = BUILDER
            .comment("Remembers the result for each checked chunk so repeated claim attempts are cheap.",
                    "Performance: turning it off re-scans structures (and the padding area) on every claim attempt.",
                    "Default: true")
            .translation(KEY + "performance.enable_cache")
            .define("enable_cache", true);

    private static final ModConfigSpec.IntValue CACHE_TTL_SECONDS = BUILDER
            .comment("How long a cached result is reused before the chunk is checked again.",
                    "[in seconds]",
                    "Structures don't move, so longer is safe; lower it only if structures are added to existing chunks by other mods.",
                    "Requires enable_cache = true.")
            .translation(KEY + "performance.cache_ttl_seconds")
            .defineInRange("cache_ttl_seconds", 300, 10, 3600);

    private static final ModConfigSpec.IntValue CACHE_MAX_ENTRIES = BUILDER
            .comment("Maximum number of cached chunk results; the least recently used one is dropped first.",
                    "Raising it uses a little more memory (well under 1 KB per entry). Requires enable_cache = true.")
            .translation(KEY + "performance.cache_max_entries")
            .defineInRange("cache_max_entries", 1000, 100, 10000);

    // ---------------------------------------------------------------- compat
    static {
        BUILDER.pop()
                .comment("Claim mod integrations. Each one does nothing if its mod is not installed.")
                .translation(KEY + "compat")
                .push("compat");
    }

    private static final ModConfigSpec.BooleanValue OPAC_ENABLED = BUILDER
            .comment("Blocks Open Parties and Claims claims on protected chunks. Does nothing without Open Parties and Claims.",
                    "Turning it off allows all OPAC claims again; existing claims are not affected.",
                    "Applies live with the mixin (default); with opac_use_mixin = false a change needs a server restart.",
                    "Default: true")
            .translation(KEY + "compat.opac_enabled")
            .define("opac_enabled", true);

    private static final ModConfigSpec.BooleanValue OPAC_USE_MIXIN = BUILDER
            .comment("How OPAC claims are blocked.",
                    "true = the claim is refused before it happens (recommended, no flicker).",
                    "false = fallback listener: the claim happens and is removed right after; this listener can also remove EXISTING",
                    "claims on protected chunks when OPAC reports a whole-region change. Use only if the mixin breaks on a new OPAC version.",
                    "Requires opac_enabled = true.",
                    "Needs a server restart (in single-player: a game restart).",
                    "Default: true")
            .translation(KEY + "compat.opac_use_mixin")
            .worldRestart()
            .define("opac_use_mixin", true);

    private static final ModConfigSpec.BooleanValue FTB_CHUNKS_ENABLED = BUILDER
            .comment("Blocks FTB Chunks claims on protected chunks. Does nothing without FTB Chunks.",
                    "Turning it off allows all FTB Chunks claims again; existing claims are not affected.",
                    "Default: true")
            .translation(KEY + "compat.ftb_chunks_enabled")
            .define("ftb_chunks_enabled", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    public enum FilterMode {
        WHITELIST,
        BLACKLIST,
        DISABLED
    }

    // Effective values. Written only by apply(), read by the protection code.
    public static volatile boolean affectSingleplayer = false;
    public static volatile boolean bypassWithOp = true;
    public static volatile int opBypassLevel = 2;
    public static volatile boolean enableDebugLogging = false;

    public static volatile boolean protectSurfaceStructures = true;
    public static volatile boolean protectUndergroundStructures = false;
    public static volatile Set<String> terrainAdaptationsToProtect = Set.copyOf(DEFAULT_TERRAIN_ADAPTATIONS);
    public static volatile Set<String> generationStepsToProtect = Set.copyOf(DEFAULT_GENERATION_STEPS);

    public static volatile FilterMode structureFilterMode = FilterMode.BLACKLIST;
    public static volatile Set<ResourceLocation> structureWhitelist = Set.of();
    public static volatile Set<ResourceLocation> structureBlacklist = Set.of();

    public static volatile FilterMode dimensionFilterMode = FilterMode.DISABLED;
    public static volatile Set<ResourceLocation> dimensionWhitelist = Set.of();
    public static volatile Set<ResourceLocation> dimensionBlacklist = Set.of();

    public static volatile FilterMode biomeFilterMode = FilterMode.DISABLED;
    public static volatile Set<ResourceLocation> biomeWhitelist = Set.of();
    public static volatile Set<ResourceLocation> biomeBlacklist = Set.of();

    public static volatile boolean enablePadding = false;
    public static volatile int defaultPaddingChunks = 1;
    public static volatile Map<ResourceLocation, Integer> customStructurePadding = Map.of();

    public static volatile String customDenialMessage = "";
    public static volatile boolean includeStructureName = true;
    public static volatile boolean includeStructureType = true;
    public static volatile boolean includeCoordinates = false;

    public static volatile boolean enableCache = true;
    public static volatile int cacheTtlSeconds = 300;
    public static volatile int cacheMaxEntries = 1000;

    public static volatile boolean ftbChunksEnabled = true;
    public static volatile boolean opacEnabled = true;
    /** Read once per game (worldRestart): later file edits are reported but not applied. */
    public static volatile boolean opacUseMixin = true;

    private static volatile int generation = 0;

    /** Increases every time the effective config is (re)applied; derived caches compare it to drop stale data. */
    public static int generation() {
        return generation;
    }

    // ---------------------------------------------------------------- validators

    private static boolean isResourceLocation(final Object obj) {
        return obj instanceof String str && parseId(str) != null;
    }

    private static boolean isTerrainAdaptation(final Object obj) {
        return obj instanceof String str && TERRAIN_ADAPTATION_NAMES.contains(str);
    }

    private static boolean isGenerationStep(final Object obj) {
        return obj instanceof String str && GENERATION_STEP_NAMES.contains(str);
    }

    private static boolean isCustomPaddingEntry(final Object obj) {
        return obj instanceof String str && parsePaddingEntry(str) != null;
    }

    /** Parses an id, rejecting malformed ones and ids with an empty path (e.g. "" or "minecraft:"). */
    @Nullable
    static ResourceLocation parseId(String text) {
        ResourceLocation id = ResourceLocation.tryParse(text);
        return id == null || id.getPath().isEmpty() ? null : id;
    }

    /** Parses "namespace:path=chunks"; null if malformed or out of range. */
    @Nullable
    static Map.Entry<ResourceLocation, Integer> parsePaddingEntry(String entry) {
        String[] parts = entry.split("=");
        if (parts.length != 2) {
            return null;
        }
        ResourceLocation id = parseId(parts[0]);
        Integer chunks = parseIntOrNull(parts[1]);
        if (id == null || chunks == null || chunks < PADDING_MIN || chunks > PADDING_MAX) {
            return null;
        }
        return Map.entry(id, chunks);
    }

    @Nullable
    private static Integer parseIntOrNull(String text) {
        int start = !text.isEmpty() && (text.charAt(0) == '+' || text.charAt(0) == '-') ? 1 : 0;
        int digits = text.length() - start;
        if (digits < 1 || digits > 9) {
            return null;
        }
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        return Integer.parseInt(text);
    }

    // ---------------------------------------------------------------- load / reload

    @SubscribeEvent
    static void onConfigLoading(final ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() == SPEC) {
            apply("loaded");
        }
    }

    @SubscribeEvent
    static void onConfigReloading(final ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            apply("reloaded");
            return;
        }
        // The file watcher runs on its own thread; apply on the server thread so a check never sees half a config.
        server.execute(() -> {
            apply("reloaded");
            ConfigChecks.checkAgainstServer(server);
        });
    }

    /** Re-applies the current values (used by /dtos reload). */
    public static void loadConfig() {
        apply("reapplied");
    }

    private static synchronized void apply(String reason) {
        affectSingleplayer = AFFECT_SINGLEPLAYER.get();
        bypassWithOp = BYPASS_WITH_OP.get();
        opBypassLevel = OP_BYPASS_LEVEL.get();
        enableDebugLogging = ENABLE_DEBUG_LOGGING.get();

        protectSurfaceStructures = PROTECT_SURFACE_STRUCTURES.get();
        protectUndergroundStructures = PROTECT_UNDERGROUND_STRUCTURES.get();
        terrainAdaptationsToProtect = Set.copyOf(TERRAIN_ADAPTATIONS_TO_PROTECT.get());
        generationStepsToProtect = Set.copyOf(GENERATION_STEPS_TO_PROTECT.get());

        structureFilterMode = STRUCTURE_FILTER_MODE.get();
        structureWhitelist = toIds(STRUCTURE_WHITELIST.get());
        structureBlacklist = toIds(STRUCTURE_BLACKLIST.get());

        dimensionFilterMode = DIMENSION_FILTER_MODE.get();
        dimensionWhitelist = toIds(DIMENSION_WHITELIST.get());
        dimensionBlacklist = toIds(DIMENSION_BLACKLIST.get());

        biomeFilterMode = BIOME_FILTER_MODE.get();
        biomeWhitelist = toIds(BIOME_WHITELIST.get());
        biomeBlacklist = toIds(BIOME_BLACKLIST.get());

        enablePadding = ENABLE_PADDING.get();
        defaultPaddingChunks = DEFAULT_PADDING_CHUNKS.get();
        customStructurePadding = toPaddingMap(CUSTOM_STRUCTURE_PADDING.get());

        customDenialMessage = CUSTOM_DENIAL_MESSAGE.get();
        includeStructureName = INCLUDE_STRUCTURE_NAME.get();
        includeStructureType = INCLUDE_STRUCTURE_TYPE.get();
        includeCoordinates = INCLUDE_COORDINATES.get();

        enableCache = ENABLE_CACHE.get();
        cacheTtlSeconds = CACHE_TTL_SECONDS.get();
        cacheMaxEntries = CACHE_MAX_ENTRIES.get();

        ftbChunksEnabled = FTB_CHUNKS_ENABLED.get();
        opacEnabled = OPAC_ENABLED.get();
        opacUseMixin = OPAC_USE_MIXIN.get(); // worldRestart: NeoForge keeps the first value until restart

        generation++;

        ConfigChecks.checkFields(CUSTOM_STRUCTURE_PADDING.get(), OPAC_USE_MIXIN.getRaw());
        LOGGER.info("[DTOS] Config {}: {}", reason, summary());
    }

    private static Set<ResourceLocation> toIds(List<? extends String> entries) {
        return entries.stream()
                .map(ModConfig::parseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Later entries for the same structure win, as before. */
    private static Map<ResourceLocation, Integer> toPaddingMap(List<? extends String> entries) {
        Map<ResourceLocation, Integer> map = new HashMap<>();
        for (String entry : entries) {
            Map.Entry<ResourceLocation, Integer> parsed = parsePaddingEntry(entry);
            if (parsed != null) {
                map.put(parsed.getKey(), parsed.getValue());
            }
        }
        return Map.copyOf(map);
    }

    static boolean isModLoaded(String modId) {
        ModList list = ModList.get();
        return list != null && list.isLoaded(modId);
    }

    private static String summary() {
        return "bypassOp=" + (bypassWithOp ? "level " + opBypassLevel : "off")
                + ", singleplayer=" + onOff(affectSingleplayer)
                + ", surface=" + onOff(protectSurfaceStructures)
                + ", underground=" + onOff(protectUndergroundStructures)
                + ", structures=" + filter(structureFilterMode, structureWhitelist, structureBlacklist)
                + ", dimensions=" + filter(dimensionFilterMode, dimensionWhitelist, dimensionBlacklist)
                + ", biomes=" + filter(biomeFilterMode, biomeWhitelist, biomeBlacklist)
                + ", padding=" + (enablePadding ? defaultPaddingChunks + " chunks (+" + customStructurePadding.size() + " custom)" : "off")
                + ", message=" + (customDenialMessage.isEmpty() ? "auto" : "custom")
                + ", cache=" + (enableCache ? cacheTtlSeconds + "s/" + cacheMaxEntries : "off")
                + ", opac=" + integration(opacEnabled, "openpartiesandclaims") + (opacUseMixin ? " (mixin)" : " (listener)")
                + ", ftbchunks=" + integration(ftbChunksEnabled, "ftbchunks")
                + ", debug=" + onOff(enableDebugLogging);
    }

    private static String filter(FilterMode mode, Set<ResourceLocation> whitelist, Set<ResourceLocation> blacklist) {
        return switch (mode) {
            case WHITELIST -> "WHITELIST(" + whitelist.size() + ")";
            case BLACKLIST -> "BLACKLIST(" + blacklist.size() + ")";
            case DISABLED -> "DISABLED";
        };
    }

    private static String integration(boolean enabled, String modId) {
        if (!enabled) {
            return "off";
        }
        return isModLoaded(modId) ? "on" : "on (not installed)";
    }

    private static String onOff(boolean value) {
        return value ? "on" : "off";
    }

    private static Set<String> enumNames(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }
}
