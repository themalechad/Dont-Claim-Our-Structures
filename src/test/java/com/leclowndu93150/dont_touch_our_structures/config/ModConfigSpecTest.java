package com.leclowndu93150.dont_touch_our_structures.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards compatibility with config files written by upstream 2.0.0: every key path, type, default and range must stay the same.
 */
class ModConfigSpecTest {

    /** Upstream 2.0.0 keys and defaults. Never edit an entry here without adding a migration. */
    private static final Map<String, Object> UPSTREAM_DEFAULTS = new LinkedHashMap<>();

    static {
        UPSTREAM_DEFAULTS.put("general.affect_singleplayer", false);
        UPSTREAM_DEFAULTS.put("general.bypass_with_op", true);
        UPSTREAM_DEFAULTS.put("general.op_bypass_level", 2);
        UPSTREAM_DEFAULTS.put("general.enable_debug_logging", false);
        UPSTREAM_DEFAULTS.put("structure_types.protect_surface", true);
        UPSTREAM_DEFAULTS.put("structure_types.protect_underground", false);
        UPSTREAM_DEFAULTS.put("structure_types.terrain_adaptations_to_protect", List.of("BURY", "BEARD_THIN", "BEARD_BOX"));
        UPSTREAM_DEFAULTS.put("structure_types.generation_steps_to_protect", List.of("SURFACE_STRUCTURES"));
        UPSTREAM_DEFAULTS.put("structures.filter_mode", ModConfig.FilterMode.BLACKLIST);
        UPSTREAM_DEFAULTS.put("structures.whitelist", List.of());
        UPSTREAM_DEFAULTS.put("structures.blacklist", List.of());
        UPSTREAM_DEFAULTS.put("dimensions.filter_mode", ModConfig.FilterMode.DISABLED);
        UPSTREAM_DEFAULTS.put("dimensions.whitelist", List.of("minecraft:overworld"));
        UPSTREAM_DEFAULTS.put("dimensions.blacklist", List.of());
        UPSTREAM_DEFAULTS.put("biomes.filter_mode", ModConfig.FilterMode.DISABLED);
        UPSTREAM_DEFAULTS.put("biomes.whitelist", List.of());
        UPSTREAM_DEFAULTS.put("biomes.blacklist", List.of());
        UPSTREAM_DEFAULTS.put("padding.enable", false);
        UPSTREAM_DEFAULTS.put("padding.default_chunks", 1);
        UPSTREAM_DEFAULTS.put("padding.custom_structure_padding", List.of());
        UPSTREAM_DEFAULTS.put("messages.custom_denial_message", "");
        UPSTREAM_DEFAULTS.put("messages.include_structure_name", true);
        UPSTREAM_DEFAULTS.put("messages.include_structure_type", true);
        UPSTREAM_DEFAULTS.put("messages.include_coordinates", false);
        UPSTREAM_DEFAULTS.put("performance.enable_cache", true);
        UPSTREAM_DEFAULTS.put("performance.cache_ttl_seconds", 300);
        UPSTREAM_DEFAULTS.put("performance.cache_max_entries", 1000);
        UPSTREAM_DEFAULTS.put("compat.ftb_chunks_enabled", true);
        UPSTREAM_DEFAULTS.put("compat.opac_enabled", true);
        UPSTREAM_DEFAULTS.put("compat.opac_use_mixin", true);
    }

    private static final Map<String, int[]> UPSTREAM_RANGES = Map.of(
            "general.op_bypass_level", new int[]{1, 4},
            "padding.default_chunks", new int[]{0, 16},
            "performance.cache_ttl_seconds", new int[]{10, 3600},
            "performance.cache_max_entries", new int[]{100, 10000});

    @Test
    void keyPathsAreExactlyTheUpstreamOnes() {
        assertEquals(new TreeSet<>(UPSTREAM_DEFAULTS.keySet()), new TreeSet<>(valueSpecs().keySet()));
    }

    @Test
    void defaultsAreUnchanged() {
        valueSpecs().forEach((path, spec) ->
                assertEquals(UPSTREAM_DEFAULTS.get(path), spec.getDefault(), "default of " + path));
    }

    @Test
    void rangesAreUnchanged() {
        UPSTREAM_RANGES.forEach((path, range) -> {
            ModConfigSpec.Range<Integer> actual = valueSpecs().get(path).getRange();
            assertNotNull(actual, "range of " + path);
            assertEquals(range[0], actual.getMin(), "min of " + path);
            assertEquals(range[1], actual.getMax(), "max of " + path);
        });
    }

    @Test
    void onlyOpacUseMixinNeedsRestart() {
        valueSpecs().forEach((path, spec) -> {
            ModConfigSpec.RestartType expected = path.equals("compat.opac_use_mixin")
                    ? ModConfigSpec.RestartType.WORLD : ModConfigSpec.RestartType.NONE;
            assertEquals(expected, spec.restartType(), "restart type of " + path);
        });
    }

    @Test
    void typicalServerFileNeedsNoValueCorrection() {
        CommentedConfig file = CommentedConfig.inMemory();
        UPSTREAM_DEFAULTS.forEach(file::set);
        file.set("structures.filter_mode", "WHITELIST");
        file.set("structures.whitelist", List.of("minecraft:village_plains", "village_desert", "somemod:tower"));
        file.set("dimensions.whitelist", List.of("minecraft:overworld", "minecraft:the_nether"));
        file.set("padding.enable", true);
        file.set("padding.default_chunks", 16);
        file.set("padding.custom_structure_padding", List.of("minecraft:village_plains=2", "minecraft:monument=0", "a:b=+3"));
        file.set("structure_types.terrain_adaptations_to_protect", List.of("NONE", "ENCAPSULATE"));
        file.set("messages.custom_denial_message", "No claims near {structure_name}!");

        List<String> corrected = new ArrayList<>();
        ModConfig.SPEC.correct(file, (action, path, from, to) -> corrected.add(String.join(".", path)), null);
        assertEquals(List.of(), corrected);
    }

    @Test
    void garbageListEntriesAreRejected() {
        assertNull(ModConfig.parseId(""));
        assertNull(ModConfig.parseId("minecraft:"));
        assertNull(ModConfig.parseId("Bad Id"));
        assertNotNull(ModConfig.parseId("minecraft:village_plains"));
        assertNull(ModConfig.parsePaddingEntry("minecraft:village=17"));
        assertNull(ModConfig.parsePaddingEntry("minecraft:village=-1"));
        assertNull(ModConfig.parsePaddingEntry("minecraft:village=two"));
        assertNull(ModConfig.parsePaddingEntry("minecraft:village"));
        assertNull(ModConfig.parsePaddingEntry("minecraft:village=1=2"));
        assertNull(ModConfig.parsePaddingEntry("=3"));
        assertEquals(Map.entry(ModConfig.parseId("minecraft:village"), 2), ModConfig.parsePaddingEntry("minecraft:village=2"));
    }

    @Test
    void everyOptionAndSectionHasATranslationAndTooltip() throws Exception {
        JsonObject lang;
        try (var in = getClass().getResourceAsStream("/assets/dont_touch_our_structures/lang/en_us.json")) {
            assertNotNull(in, "en_us.json on the test classpath");
            lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
        String prefix = "dont_touch_our_structures.configuration.";
        Set<String> sections = new TreeSet<>();
        valueSpecs().forEach((path, spec) -> {
            assertEquals(prefix + path, spec.getTranslationKey(), "translation key of " + path);
            sections.add(path.substring(0, path.indexOf('.')));
        });
        for (String section : sections) {
            assertEquals(prefix + section, ModConfig.SPEC.getLevelTranslationKey(List.of(section)), "section key " + section);
            assertNotNull(ModConfig.SPEC.getLevelComment(List.of(section)), "section comment " + section);
        }
        List<String> keys = new ArrayList<>(valueSpecs().keySet());
        keys.addAll(sections);
        for (String key : keys) {
            assertTrue(lang.has(prefix + key), "lang label for " + key);
            assertTrue(lang.has(prefix + key + ".tooltip"), "lang tooltip for " + key);
        }
    }

    private static Map<String, ModConfigSpec.ValueSpec> valueSpecs() {
        Map<String, ModConfigSpec.ValueSpec> out = new TreeMap<>();
        collect(ModConfig.SPEC.getSpec(), "", out);
        return out;
    }

    private static void collect(UnmodifiableConfig config, String prefix, Map<String, ModConfigSpec.ValueSpec> out) {
        config.valueMap().forEach((key, value) -> {
            if (value instanceof UnmodifiableConfig sub) {
                collect(sub, prefix + key + ".", out);
            } else {
                out.put(prefix + key, (ModConfigSpec.ValueSpec) value);
            }
        });
    }
}
