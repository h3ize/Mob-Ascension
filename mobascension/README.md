# Mob Ascension (NeoForge 1.21.1)

Mob Ascension turns ordinary hostile mobs into **champions**. Every time a monster spawns, there is a chance it ascends into one of four ranks: **Veteran, Elite, Mythic or Apex**. Each rank is tougher, deadlier and more rewarding than the last, with rank-coloured glow and particles, rolled stat levels, random abilities, real gear and loot that scales with the fight.

Apex champions are the true threat: they hunt you down, hurl wither skulls, and drop a haul worth the trouble.

## Ranks (4)

| Rank    | Colour | Stat level (random) | Spawn odds | Abilities | Health bonus | Damage bonus |
| ------- | ------ | ------------------- | ---------- | --------- | ------------ | ------------ |
| Veteran | Green  | 3 - 5               | ~5.5%      | 1         | +30-50%      | +21-35%      |
| Elite   | Aqua   | 5 - 8               | ~1.5%      | 2         | +80-128%     | +45-72%      |
| Mythic  | Purple | 7 - 11              | ~0.5%      | 3         | +210-330%    | +98-154%     |
| Apex    | Gold   | 10 - 14             | ~0.17%     | 4         | +400-560%    | +180-252%    |

About 92% of spawns stay normal mobs. The Warden, Wither, Creeper and Vex can never ascend, and the Ender Dragon only ascends through the special dragon rule below. Mobs from spawners, trial spawners, breeding, buckets and conversions are excluded by default.

### How champions work
- **Random power:** every champion rolls its own stat level inside its rank's range, so no two are exactly alike
- **Stats:** extra health, armor, toughness, movement speed, attack damage, knockback resistance, safe fall distance and body size; immune to fire and lava
- **Ranged damage:** the damage bonus also applies to arrows, tridents and fireballs
- **Effects:** permanent Infested or Oozing, with higher ranks more likely to roll them (5% / 10% / 20% / 35% each)
- **Presence:** rank-coloured particle aura, rank in the mob's name (e.g. "Veteran Zombie"), and a coloured glow outline on Apex when they spawn
- **Announcements:** Apex spawns are announced to nearby players; Mythic and Apex deaths set off fireworks
- **Gear:** champions are armed and armoured by rank (leather and chainmail up to netherite), with random vanilla armor trims and enchantments capped per rank

## Abilities (8)

Champions roll random abilities, and higher ranks roll more. Each one has its own minimum rank, power and cooldown in the config.

| Ability     | Min rank | Effect                                                    |
| ----------- | -------- | --------------------------------------------------------- |
| Vampiric    | Veteran  | Heals for a share of the damage it deals                  |
| Thorned     | Veteran  | Reflects part of the melee damage you deal back at you    |
| Frostbite   | Veteran  | Slows and freezes on hit                                  |
| Venomous    | Veteran  | Poisons on hit                                            |
| Berserker   | Elite    | Enrages at 50% HP: faster and hits harder                 |
| Splitting   | Mythic   | Spawns two minions on death                               |
| Stormcaller | Apex     | Calls lightning down on you                               |

## Ascended Ender Dragon

A dragon **respawned with End crystals** ascends into an Apex champion (the very first, natural dragon stays vanilla; set `dragon.onlyWhenRespawnedWithCrystals = false` to change that). All numbers below are configurable in the `[dragon]` section.

- **Health:** 2000 (vanilla 200). **No armor**, no gear and no size change
- **Damage:** x2 on everything it deals (body, breath, fireballs, skulls); x3 total while enraged
- **Abilities:** every enabled ability except Splitting and Berserker (Vampiric, Thorned, Frostbite, Venomous, Stormcaller)
- **Wither skull volleys:** 2 skulls every 6 seconds at the nearest player in range
- **Lightning storms:** every 10 seconds a bolt strikes every player in range
- **Brood:** 4 Endermen are summoned at 75%, 50% and 25% health
- **Enrage:** below 50% health it deals +50% damage and volleys and storms come twice as often
- **Rewards:** killing it pays out the normal Apex kill rewards

## Apex champions

- **Ranged attacks:** only Apex shoot, firing **wither skulls** at their target
- **Skeleton bows:** Apex skeletons (skeleton, stray, bogged) carry a Power 10 bow. It never drops; the Apex loot table handles drops
- **They hunt you:** an Apex automatically targets the nearest survival player within 30 blocks, no provocation needed
- **Passive mobs (optional):** with `includePassiveMobs` on, passive mobs can ascend too, and an Apex passive mob hunts players with wither skulls. Off by default

## Rewards

Rewards grow with rank. A champion killed by a player gives:

| Rank    | Guaranteed loot                                                                 | Reward rolls | Random enchanted books | Kill XP levels |
| ------- | ------------------------------------------------------------------------------- | ------------ | ---------------------- | -------------- |
| Veteran | 1-2 experience bottles                                                          | 1            | 1                      | 1-3            |
| Elite   | 1-2 experience bottles, 0-1 diamond                                             | 1            | 1                      | 1-3            |
| Mythic  | 2-3 experience bottles, 1-2 diamonds, 1 golden apple                            | 1-2          | 1-2                    | 3-6            |
| Apex    | 4-8 diamonds, 1 netherite ingot, a Mending book, enchanted golden apple, golden apples, emeralds, lapis, gold, ender pearls, experience bottles | 2-4 | 2-3 | 10-16 |

- **Gear:** the armor and weapons the champion was wearing (1 piece, 1-2 for Apex), with randomly worn durability. Apex weapon drops can be a Power V-VI bow with Unbreaking III and Mending
- **Random enchanted books:** a different random positive enchantment on each book. Level scales with rank: up to about 1/3 of vanilla max for Veteran, up to the vanilla max for Mythic, and up to 1.5x vanilla max for Apex
- **Apex enchanted tools:** 1-2 random netherite tools or weapons (plus mace, trident, bow, crossbow) with 3-5 compatible enchantments, rolling up to level VI
- **Bonus loot:** chance-based extras, from iron for Veteran up to a **beacon, nether star, totem of undying, netherite scrap and diamond blocks** from Apex, plus a rare elytra
- **Experience:** normal XP orbs are scaled by rank (0.3x / 0.3x / 0.4x / 1.0x) on top of the kill levels
- **Apex Slayer:** killing an Apex earns the advancement and 150 XP

## Advancement

| Advancement | How to unlock         |
| ----------- | --------------------- |
| Apex Slayer | Defeat an Apex champion |

## Commands

`/mobascension summon <rank> [entity]` (operators only) spawns a champion of the chosen rank for testing.

## Configuration

Everything is configurable in `config/mobascension-common.toml` or in-game under **Mods > Mob Ascension > Config**:
- Spawn weights per rank and the normal-mob weight
- Stat level range, health, armor, toughness, speed, damage, knockback, size and XP per rank
- Whitelist, blacklist, vanilla-only mode and excluded spawn types
- Abilities: enabled, minimum rank, power and cooldown
- Gear lists, enchant caps, armor trims and Apex ranged chance and cooldown
- Loot: guaranteed loot, reward pools, bonus loot, random books and tools
- Apex aggro radius, particles, glow colours, name visibility and announcement radius

## Changes in 2.1

- Removed the Blinker (teleport) ability. Ascended mobs, Endermen included, can no longer teleport.
- Apex always drops at least one enchanted book with a random multi-level enchantment at level 11 up to 20.
- Books never include enchantments with "error" or "illegal" in their id or name.
- Apex no longer fires large fireballs (wither skulls only).
- If Allthemodium is installed, an Apex has a low chance (`apexAllthemodiumGearChance`, default 5%) to spawn in a full set of Allthemodium, Vibranium or Unobtainium gear plus a sword.
