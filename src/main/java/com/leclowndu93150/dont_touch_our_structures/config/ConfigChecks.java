package com.leclowndu93150.dont_touch_our_structures.config;

import com.leclowndu93150.dont_touch_our_structures.DontClaimOurStructures;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Relations between options that NeoForge's per-value validation can't express.
 * Each problem logs one WARN; nothing is rewritten and the effective values are what the protection code already does.
 */
public final class ConfigChecks {
    private static final Logger LOGGER = LoggerFactory.getLogger(DontClaimOurStructures.MODID);
    private static final Set<String> MESSAGE_PLACEHOLDERS = Set.of("structure_name", "structure_type", "chunk_x", "chunk_z");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_]+)}");

    private ConfigChecks() {
    }

    /** Checks that only need the config itself. Runs on every load and reload. */
    static void checkFields(List<? extends String> rawCustomPadding, boolean opacUseMixinInFile) {
        checkNothingProtected();
        checkFilter("structures", ModConfig.structureFilterMode, ModConfig.structureWhitelist, ModConfig.structureBlacklist,
                "no structure is protected");
        checkFilter("dimensions", ModConfig.dimensionFilterMode, ModConfig.dimensionWhitelist, ModConfig.dimensionBlacklist,
                "protection is off in every dimension");
        checkFilter("biomes", ModConfig.biomeFilterMode, ModConfig.biomeWhitelist, ModConfig.biomeBlacklist,
                "no structure chunk is protected (padding still applies)");
        checkPadding(rawCustomPadding);
        checkUnusedLists();
        checkMessagePlaceholders();
        checkIntegrations();
        if (opacUseMixinInFile != ModConfig.opacUseMixin) {
            warn("compat.opac_use_mixin", "was changed to " + opacUseMixinInFile + " in the file",
                    "it needs a server restart; still using " + ModConfig.opacUseMixin + " until then");
        }
    }

    /** Checks ids against the server's registries. Runs at server start and on reload while a server is running. */
    public static void checkAgainstServer(MinecraftServer server) {
        Registry<Structure> structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        Set<ResourceLocation> dimensions = server.levelKeys().stream()
                .map(ResourceKey::location)
                .collect(Collectors.toSet());

        warnUnknown("structures.whitelist", ModConfig.structureWhitelist, structures::containsKey, "structure");
        warnUnknown("structures.blacklist", ModConfig.structureBlacklist, structures::containsKey, "structure");
        warnUnknown("padding.custom_structure_padding", ModConfig.customStructurePadding.keySet(), structures::containsKey, "structure");
        warnUnknown("biomes.whitelist", ModConfig.biomeWhitelist, biomes::containsKey, "biome");
        warnUnknown("biomes.blacklist", ModConfig.biomeBlacklist, biomes::containsKey, "biome");
        warnUnknown("dimensions.whitelist", ModConfig.dimensionWhitelist, dimensions::contains, "dimension");
        warnUnknown("dimensions.blacklist", ModConfig.dimensionBlacklist, dimensions::contains, "dimension");
    }

    private static void checkNothingProtected() {
        if (!ModConfig.protectSurfaceStructures && !ModConfig.protectUndergroundStructures) {
            warn("structure_types.protect_surface/protect_underground", "are both false",
                    "no structure (and no padding) is protected");
        }
    }

    private static void checkFilter(String section, ModConfig.FilterMode mode, Set<ResourceLocation> whitelist,
                                    Set<ResourceLocation> blacklist, String emptyWhitelistEffect) {
        if (mode == ModConfig.FilterMode.WHITELIST && whitelist.isEmpty()) {
            warn(section + ".whitelist", "is empty while " + section + ".filter_mode = WHITELIST", emptyWhitelistEffect);
        }
        Set<ResourceLocation> both = new TreeSet<>(whitelist);
        both.retainAll(blacklist);
        if (!both.isEmpty()) {
            warn(section, "has ids in both whitelist and blacklist " + both,
                    "only the list matching filter_mode (" + mode + ") is used");
        }
    }

    private static void checkPadding(List<? extends String> rawCustomPadding) {
        boolean anyCustomAboveZero = ModConfig.customStructurePadding.values().stream().anyMatch(chunks -> chunks > 0);
        if (ModConfig.enablePadding && ModConfig.defaultPaddingChunks == 0 && !anyCustomAboveZero) {
            warn("padding.default_chunks", "is 0 and no custom_structure_padding entry is above 0 while padding.enable = true",
                    "padding has no effect");
        }
        if (!ModConfig.enablePadding && !rawCustomPadding.isEmpty()) {
            warn("padding.custom_structure_padding", "has entries but padding.enable = false", "the entries are ignored");
        }
        Set<ResourceLocation> seen = new HashSet<>();
        Set<ResourceLocation> duplicates = new TreeSet<>();
        for (String entry : rawCustomPadding) {
            Map.Entry<ResourceLocation, Integer> parsed = ModConfig.parsePaddingEntry(entry);
            if (parsed != null && !seen.add(parsed.getKey())) {
                duplicates.add(parsed.getKey());
            }
        }
        if (!duplicates.isEmpty()) {
            warn("padding.custom_structure_padding", "lists these structures more than once " + duplicates,
                    "the last entry for each one is used");
        }
    }

    private static void checkUnusedLists() {
        if (!ModConfig.terrainAdaptationsToProtect.equals(Set.copyOf(ModConfig.DEFAULT_TERRAIN_ADAPTATIONS))) {
            warn("structure_types.terrain_adaptations_to_protect", "was changed from the default",
                    "it is not used in this version; use protect_surface/protect_underground instead");
        }
        if (!ModConfig.generationStepsToProtect.equals(Set.copyOf(ModConfig.DEFAULT_GENERATION_STEPS))) {
            warn("structure_types.generation_steps_to_protect", "was changed from the default",
                    "it is not used in this version and has no effect");
        }
    }

    private static void checkMessagePlaceholders() {
        Set<String> unknown = new TreeSet<>();
        Matcher matcher = PLACEHOLDER.matcher(ModConfig.customDenialMessage);
        while (matcher.find()) {
            if (!MESSAGE_PLACEHOLDERS.contains(matcher.group(1))) {
                unknown.add(matcher.group());
            }
        }
        if (!unknown.isEmpty()) {
            warn("messages.custom_denial_message", "has unknown placeholders " + unknown,
                    "they are sent as written; known ones are " + MESSAGE_PLACEHOLDERS);
        }
    }

    private static void checkIntegrations() {
        boolean opacActive = ModConfig.opacEnabled && ModConfig.isModLoaded("openpartiesandclaims");
        boolean ftbActive = ModConfig.ftbChunksEnabled && ModConfig.isModLoaded("ftbchunks");
        if (!opacActive && !ftbActive) {
            warn("compat", "has no active integration (Open Parties and Claims / FTB Chunks missing or disabled)",
                    "no claim is blocked");
        }
    }

    private static void warnUnknown(String key, Collection<ResourceLocation> ids, Predicate<ResourceLocation> known, String kind) {
        Set<ResourceLocation> unknown = ids.stream().filter(known.negate()).collect(Collectors.toCollection(TreeSet::new));
        if (!unknown.isEmpty()) {
            warn(key, "has unknown " + kind + " ids " + unknown, "they never match (typo or mod not installed?)");
        }
    }

    private static void warn(String key, String problem, String effect) {
        LOGGER.warn("[DTOS] Config {} {}: {}.", key, problem, effect);
    }
}
