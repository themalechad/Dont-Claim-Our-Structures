# Underground structure list (2.0.0-opac033-moddy3)

DCOS sorts every structure into one of two categories, using only its terrain adaptation:

- **surface**: `terrain_adaptation` is anything other than `NONE`. Examples: villages, pillager outposts, ancient cities, strongholds, trial chambers.
- **underground**: `terrain_adaptation` is `NONE`, or the JSON leaves it out (it then defaults to `NONE`). This includes many structures that sit above ground: mansions, monuments, end cities, fortresses, bastions, temples, shipwrecks and ruined portals.

Each category has its own filter, and neither filter can change the other category:

| Category | Master switch | Id filter |
|---|---|---|
| surface | `structure_types.protect_surface` (default `true`) | `structures.filter_mode` / `whitelist` / `blacklist` (default `BLACKLIST`, empty) |
| underground | `structure_types.protect_underground` (default `false`) | `structures.underground_structure_list_mode` / `underground_structures` (default `WHITELIST`, empty) |

The underground list modes:

- `WHITELIST`: only the ids in `underground_structures` are protected. An empty list protects no underground structure.
- `BLACKLIST`: every underground structure is protected except the listed ones. An empty list protects all of them.
- `DISABLED`: the list is ignored and every underground structure is protected.

If `protect_underground = false`, the underground keys are ignored and no underground structure is protected. This is the default and matches the behaviour before moddy3.

Ids must match exactly; wildcards are not supported. An id with no namespace means `minecraft` (so `fortress` is `minecraft:fortress`).

Existing claims are never removed. The filter only blocks new claims.

Changes take effect when you save the file or run `/dtos reload`. A restart is only needed if you change `compat.opac_use_mixin`.

## Boogaloo settings (Minecrafty / pack)

**Do not enable `protect_underground` on Boogaloo without this whitelist.** With the old empty blacklist it would protect all ~160 underground-classified pack structures, including mineshafts, ruined portals, shipwrecks, every WAN digsite, and the Alex's Caves canyons and trenches.

**Do not put surface structures on the underground list.** Ancient cities, strongholds, trial chambers, villages, outposts, ice and fire roosts, `pastel:city_below` and the others are already protected by `protect_surface`. Any surface id on this list is ignored, and a WARN is logged at server start.

Edit `config/dont_touch_our_structures-common.toml` and back it up first. Set these values; every other key stays as it is:

```toml
[structure_types]
	protect_surface = true
	protect_underground = true

[structures]
	filter_mode = "BLACKLIST"
	whitelist = []
	blacklist = []
	underground_structure_list_mode = "WHITELIST"
	underground_structures = [
		"minecraft:mansion",
		"minecraft:monument",
		"minecraft:end_city",
		"minecraft:fortress",
		"minecraft:bastion_remnant",
		"minecraft:desert_pyramid",
		"minecraft:jungle_pyramid",
		"alexscaves:licowitch_tower",
		"alexscaves:volcano",
		"alexscaves:abyssal_ruins",
		"antarchy:basilisk_lair",
		"antarchy:toreterror_arena",
		"antarchy:ruined_arena",
		"antarchy:temple_of_technoblade",
		"antarchy:fallen_kingdom_castle",
		"antarchy:sky_isle_fallen_kingdom_castle",
		"antarchy:sky_isle_magical_castle",
		"antarchy:underside_castle",
		"antarchy:ruined_prison",
		"ati_structures:herobrine_stronghold",
		"ati_structures:wither_cavern",
		"ati_structures:wither_keep",
		"ati_structures:underground_monument",
		"ati_structures:underwater_dungeon",
		"ati_structures:ancient_temple",
		"ati_structures:fortified_temple",
		"ati_structures:sinking_temple",
		"ati_structures:rotting_temple",
		"ati_structures:dark_keep",
		"ati_structures:deepslate_keep",
		"ati_structures:woodland_keep",
		"ati_structures:catalonian_castle",
		"ati_structures:castillo",
		"ati_structures:ruined_castle",
		"ati_structures:granite_fort",
		"ati_structures:old_fort",
		"ati_structures:marble_chateau",
		"ati_structures:manor",
		"ati_structures:illager_tower",
		"ati_structures:sculk_deposit",
		"bda:red_death_volcano",
		"boogaloo_core:soul_temple",
		"bookofdragons:roost_cave",
		"createcybernetics:lab",
		"epic:large_dungeon",
		"epic:large_ice_dungeon",
		"epic:large_sand_dungeon",
		"epic:medium_ice_dungeon",
		"epic:medium_plains_dungeon",
		"epic:medium_sand_dungeon",
		"iceandfire:fire_dragon_cave",
		"iceandfire:ice_dragon_cave",
		"iceandfire:lightning_dragon_cave",
		"infinity:isolation",
		"infinity:pyramid",
		"integrated_boogaloo:alien_hive",
		"opposing_force:motif_mega",
		"pastel:forsaken_cathedral",
		"pastel:preservation_ruins",
		"pastel:undergrowth_manor",
		"scguns:cog_chambers",
		"thematic:foundry",
		"weeping_angels:catacombs"
	]
```

The list has 63 ids. Then run `/dtos reload`; no restart is needed. The log should show:

`[DTOS] Config reapplied: ... surface=on, underground=on, structures=BLACKLIST(0), undergroundFilter=WHITELIST(63), ...`

If an id is misspelled or its mod is missing, a WARN names it: `Config structures.underground_structures has unknown structure ids [...]`.

## In-game test checklist

Use a non-op account, or set `general.bypass_with_op = false` for the test. Operators bypass protection by default.

1. Stand in a nether fortress chunk, or an ice/fire dragon cave chunk. `/dtos debug` lists the id with `Type: underground | Adaptation: NONE | ... | Filter: protected`, and `/dtos check` shows `PROTECTED_STRUCTURE`. Claiming it with OPAC or FTB Chunks is refused.
2. Stand in a mineshaft or ruined portal chunk. `/dtos debug` shows `underground | Adaptation: NONE | ... | Filter: not protected`, and `/dtos check` shows `ALLOWED` unless another protected structure or padding covers the chunk. The claim works.
3. Stand in a village, pillager outpost or ancient city chunk. `/dtos debug` shows `surface | Adaptation: BEARD_THIN/BEARD_BOX/... | Filter: protected`, and the claim is refused, as before.
4. Claims that existed before the change are still there.
