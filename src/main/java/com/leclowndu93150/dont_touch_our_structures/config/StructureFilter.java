package com.leclowndu93150.dont_touch_our_structures.config;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Immutable snapshot of the options that decide whether a structure id is protected, built on every config apply.
 * <p>
 * Pure logic (no {@code Structure} or level needed) so it can be unit tested. The caller classifies the structure:
 * surface = terrain adaptation other than NONE, underground = NONE.
 * <ul>
 *     <li>Surface: {@code protect_surface}, then only the global {@code structures.filter_mode/whitelist/blacklist}.</li>
 *     <li>Underground: {@code protect_underground}, then only {@code underground_structure_list_mode/underground_structures}.</li>
 * </ul>
 * Neither filter can affect the other category.
 */
public record StructureFilter(
        boolean protectSurface,
        boolean protectUnderground,
        ModConfig.FilterMode surfaceMode,
        Set<ResourceLocation> surfaceWhitelist,
        Set<ResourceLocation> surfaceBlacklist,
        ModConfig.FilterMode undergroundMode,
        Set<ResourceLocation> undergroundStructures) {

    /** The shipped defaults: every surface structure protected, no underground structure protected. */
    public static final StructureFilter DEFAULTS = new StructureFilter(true, false,
            ModConfig.FilterMode.BLACKLIST, Set.of(), Set.of(),
            ModConfig.FilterMode.WHITELIST, Set.of());

    public StructureFilter {
        surfaceWhitelist = Set.copyOf(surfaceWhitelist);
        surfaceBlacklist = Set.copyOf(surfaceBlacklist);
        undergroundStructures = Set.copyOf(undergroundStructures);
    }

    public boolean isProtected(ResourceLocation structureId, boolean surface) {
        return surface ? isSurfaceProtected(structureId) : isUndergroundProtected(structureId);
    }

    private boolean isSurfaceProtected(ResourceLocation structureId) {
        return protectSurface && matches(surfaceMode, surfaceWhitelist, surfaceBlacklist, structureId);
    }

    private boolean isUndergroundProtected(ResourceLocation structureId) {
        // One list for both modes: it is the whitelist in WHITELIST mode and the blacklist in BLACKLIST mode.
        return protectUnderground && matches(undergroundMode, undergroundStructures, undergroundStructures, structureId);
    }

    private static boolean matches(ModConfig.FilterMode mode, Set<ResourceLocation> whitelist,
                                   Set<ResourceLocation> blacklist, ResourceLocation structureId) {
        return switch (mode) {
            case WHITELIST -> whitelist.contains(structureId);
            case BLACKLIST -> !blacklist.contains(structureId);
            case DISABLED -> true;
        };
    }
}
