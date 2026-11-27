package com.leclowndu93150.dont_touch_our_structures.config;

import com.leclowndu93150.dont_touch_our_structures.config.ModConfig.FilterMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Surface/underground decisions of {@link StructureFilter}; no Minecraft bootstrap needed. */
class StructureFilterTest {

    private static final boolean SURFACE = true;
    private static final boolean UNDERGROUND = false;

    // Surface (terrain adaptation != NONE)
    private static final ResourceLocation VILLAGE = id("minecraft:village_plains");
    private static final ResourceLocation OUTPOST = id("minecraft:pillager_outpost");
    private static final ResourceLocation ANCIENT_CITY = id("minecraft:ancient_city");
    // Underground (terrain adaptation NONE)
    private static final ResourceLocation FORTRESS = id("minecraft:fortress");
    private static final ResourceLocation MANSION = id("minecraft:mansion");
    private static final ResourceLocation MONUMENT = id("minecraft:monument");
    private static final ResourceLocation FIRE_DRAGON_CAVE = id("iceandfire:fire_dragon_cave");
    private static final ResourceLocation MINESHAFT = id("minecraft:mineshaft");
    private static final ResourceLocation RUINED_PORTAL = id("minecraft:ruined_portal");

    private static final Set<ResourceLocation> MAJOR_UNDERGROUND = Set.of(FORTRESS, MANSION, MONUMENT, FIRE_DRAGON_CAVE);

    @Test
    void defaultsProtectEverySurfaceStructureAndNoUndergroundOne() {
        StructureFilter filter = StructureFilter.DEFAULTS;
        assertTrue(filter.isProtected(VILLAGE, SURFACE));
        assertTrue(filter.isProtected(ANCIENT_CITY, SURFACE));
        assertFalse(filter.isProtected(FORTRESS, UNDERGROUND));
        assertFalse(filter.isProtected(MINESHAFT, UNDERGROUND));
    }

    @Test
    void undergroundOffIgnoresTheUndergroundList() {
        StructureFilter filter = filter(false, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.WHITELIST, Set.of(FORTRESS));
        assertFalse(filter.isProtected(FORTRESS, UNDERGROUND), "listed but protect_underground = false");
        assertTrue(filter.isProtected(VILLAGE, SURFACE), "surface still protected under global BLACKLIST(0)");

        StructureFilter disabled = filter(false, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.DISABLED, Set.of());
        assertFalse(disabled.isProtected(MINESHAFT, UNDERGROUND));
    }

    @Test
    void surfaceOffUnprotectsSurfaceOnly() {
        StructureFilter filter = new StructureFilter(false, true, FilterMode.DISABLED, Set.of(), Set.of(),
                FilterMode.WHITELIST, Set.of(FORTRESS));
        assertFalse(filter.isProtected(VILLAGE, SURFACE));
        assertTrue(filter.isProtected(FORTRESS, UNDERGROUND));
    }

    @Test
    void undergroundListCannotUnprotectASurfaceStructure() {
        StructureFilter whitelist = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.WHITELIST, MAJOR_UNDERGROUND);
        assertTrue(whitelist.isProtected(VILLAGE, SURFACE), "absent from the underground whitelist");
        assertTrue(whitelist.isProtected(ANCIENT_CITY, SURFACE));

        StructureFilter blacklist = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.BLACKLIST, Set.of(VILLAGE, OUTPOST));
        assertTrue(blacklist.isProtected(VILLAGE, SURFACE), "on the underground blacklist");
        assertTrue(blacklist.isProtected(OUTPOST, SURFACE));
    }

    @Test
    void undergroundListCannotExtraProtectASurfaceStructure() {
        StructureFilter filter = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(VILLAGE), FilterMode.WHITELIST, Set.of(VILLAGE));
        assertFalse(filter.isProtected(VILLAGE, SURFACE), "global blacklist wins for surface");
    }

    @Test
    void globalListsCannotChangeAnUndergroundDecision() {
        StructureFilter globalWhitelist = filter(true, FilterMode.WHITELIST, Set.of(VILLAGE), Set.of(), FilterMode.WHITELIST, Set.of(FORTRESS));
        assertTrue(globalWhitelist.isProtected(FORTRESS, UNDERGROUND), "global WHITELIST of villages does not strip the fortress");
        assertTrue(globalWhitelist.isProtected(VILLAGE, SURFACE));
        assertFalse(globalWhitelist.isProtected(OUTPOST, SURFACE));

        StructureFilter globalBlacklist = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(FORTRESS), FilterMode.WHITELIST, Set.of(FORTRESS));
        assertTrue(globalBlacklist.isProtected(FORTRESS, UNDERGROUND), "global BLACKLIST of fortress does not unprotect it");

        StructureFilter globalWhitelistOfMineshaft = filter(true, FilterMode.WHITELIST, Set.of(MINESHAFT), Set.of(), FilterMode.WHITELIST, Set.of(FORTRESS));
        assertFalse(globalWhitelistOfMineshaft.isProtected(MINESHAFT, UNDERGROUND), "global whitelist does not add underground ids");
    }

    @Test
    void undergroundWhitelistProtectsOnlyListedMajorStructures() {
        StructureFilter filter = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.WHITELIST, MAJOR_UNDERGROUND);
        for (ResourceLocation major : MAJOR_UNDERGROUND) {
            assertTrue(filter.isProtected(major, UNDERGROUND), major.toString());
        }
        assertFalse(filter.isProtected(MINESHAFT, UNDERGROUND));
        assertFalse(filter.isProtected(RUINED_PORTAL, UNDERGROUND));
        assertTrue(filter.isProtected(VILLAGE, SURFACE));
        assertTrue(filter.isProtected(OUTPOST, SURFACE));
        assertTrue(filter.isProtected(ANCIENT_CITY, SURFACE));
    }

    @Test
    void emptyUndergroundWhitelistProtectsNoUndergroundStructure() {
        StructureFilter filter = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.WHITELIST, Set.of());
        assertFalse(filter.isProtected(FORTRESS, UNDERGROUND));
        assertFalse(filter.isProtected(MINESHAFT, UNDERGROUND));
        assertTrue(filter.isProtected(VILLAGE, SURFACE));
    }

    @Test
    void undergroundBlacklistProtectsAllButListed() {
        StructureFilter filter = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.BLACKLIST, Set.of(MINESHAFT));
        assertFalse(filter.isProtected(MINESHAFT, UNDERGROUND));
        assertTrue(filter.isProtected(FORTRESS, UNDERGROUND));
        assertTrue(filter.isProtected(RUINED_PORTAL, UNDERGROUND));

        StructureFilter empty = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.BLACKLIST, Set.of());
        assertTrue(empty.isProtected(MINESHAFT, UNDERGROUND), "empty blacklist protects all underground");
    }

    @Test
    void undergroundDisabledProtectsAllUnderground() {
        StructureFilter filter = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.DISABLED, Set.of(MINESHAFT));
        assertTrue(filter.isProtected(MINESHAFT, UNDERGROUND), "list ignored");
        assertTrue(filter.isProtected(RUINED_PORTAL, UNDERGROUND));
        assertTrue(filter.isProtected(FORTRESS, UNDERGROUND));
    }

    @Test
    void missingNamespaceResolvesAsMinecraft() {
        Set<ResourceLocation> parsed = ModConfig.toIds(List.of("fortress", "mansion", "iceandfire:fire_dragon_cave", "minecraft:", ""));
        assertEquals(Set.of(FORTRESS, MANSION, FIRE_DRAGON_CAVE), parsed);

        StructureFilter filter = filter(true, FilterMode.BLACKLIST, Set.of(), Set.of(), FilterMode.WHITELIST, parsed);
        assertTrue(filter.isProtected(FORTRESS, UNDERGROUND));
        assertFalse(filter.isProtected(MINESHAFT, UNDERGROUND));
    }

    private static StructureFilter filter(boolean protectUnderground, FilterMode surfaceMode, Set<ResourceLocation> surfaceWhitelist,
                                          Set<ResourceLocation> surfaceBlacklist, FilterMode undergroundMode,
                                          Set<ResourceLocation> undergroundStructures) {
        return new StructureFilter(true, protectUnderground, surfaceMode, surfaceWhitelist, surfaceBlacklist,
                undergroundMode, undergroundStructures);
    }

    private static ResourceLocation id(String text) {
        return ResourceLocation.parse(text);
    }
}
