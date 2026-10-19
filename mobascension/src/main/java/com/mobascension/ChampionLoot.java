
package com.mobascension;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class ChampionLoot {
    private static final int APEX_BOOK_MIN_LEVEL = 11;
    private static final int APEX_BOOK_MAX_LEVEL = 20;
    private ChampionLoot() {}
    public static ItemStack rollUnobtainiumDrop(RandomSource random) {
        List<Item> pool = new ArrayList<>();
        List<? extends String> configured = AscensionConfig.UNOBTAINIUM_DROP_ITEMS.get();
        if (!configured.isEmpty()) {
            for (String raw : configured) {
                ResourceLocation id = ResourceLocation.tryParse(raw);
                if (id == null) continue;
                Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(Items.AIR);
                if (item != Items.AIR) pool.add(item);
            }
        } else {
            for (Map.Entry<ResourceKey<Item>, Item> entry : BuiltInRegistries.ITEM.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                if (!id.getNamespace().equals("allthemodium")) continue;
                String path = id.getPath();
                if (path.contains("template") || (path.contains("alloy") && path.endsWith("_ingot"))) {
                    pool.add(entry.getValue());
                }
            }
        }
        if (pool.isEmpty()) return null;
        return new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    public static List<ItemStack> roll(ServerLevel level, Rank rank, RandomSource random) {
        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);
        RegistryAccess access = level.registryAccess();
        List<ItemStack> out = new ArrayList<>();

        for (String line : cfg.guaranteedLoot.get()) {
            Entry e = Entry.parse(line);
            if (e == null) continue;

            ItemStack stack = e.create(access, random);
            if (!stack.isEmpty()) out.add(stack);
        }

        List<Entry> pool = new ArrayList<>();
        double total = 0;

        for (String line : cfg.rewardPool.get()) {
            Entry e = Entry.parse(line);
            if (e == null || e.value <= 0) continue;

            pool.add(e);
            total += e.value;
        }

        int min = cfg.rewardRollsMin.get();
        int max = Math.max(min, cfg.rewardRollsMax.get());
        int rolls = min + random.nextInt(max - min + 1);

        if (total > 0) {
            for (int i = 0; i < rolls; i++) {
                double pick = random.nextDouble() * total;
                Entry chosen = pool.get(pool.size() - 1);

                for (Entry e : pool) {
                    pick -= e.value;
                    if (pick < 0) {
                        chosen = e;
                        break;
                    }
                }

                ItemStack stack = chosen.create(access, random);
                if (!stack.isEmpty()) out.add(stack);
            }
        }

        out.addAll(randomBooks(level, rank, random));
        out.addAll(randomTools(level, rank, random));

        for (String line : cfg.bonusLoot.get()) {
            Entry e = Entry.parse(line);
            if (e == null) continue;

            if (random.nextDouble() < e.value) {
                ItemStack stack = e.create(access, random);
                if (!stack.isEmpty()) out.add(stack);
            }
        }

        return out;
    }

    public static List<ItemStack> randomBooks(
            ServerLevel level, Rank rank, RandomSource random) {

        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);

        int min = cfg.bookCountMin.get();
        int max = Math.max(min, cfg.bookCountMax.get());
        int count = min + random.nextInt(max - min + 1);

        if (rank == Rank.APEX) count = Math.max(count, 1);

        List<ItemStack> out = new ArrayList<>();
        if (count <= 0) return out;

        Registry<Enchantment> registry =
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        List<Holder.Reference<Enchantment>> pool = registry.holders()
                .filter(h -> !h.is(EnchantmentTags.CURSE))
                .filter(h -> !isBannedName(h))
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(pool, new java.util.Random(random.nextLong()));

        if (rank == Rank.APEX && !pool.isEmpty()) {
            List<Holder.Reference<Enchantment>> multi = pool.stream()
                    .filter(h -> h.value().getMaxLevel() >= 2)
                    .toList();

            List<Holder.Reference<Enchantment>> from =
                    multi.isEmpty() ? pool : multi;

            Holder.Reference<Enchantment> pick =
                    from.get(random.nextInt(from.size()));

            pool.remove(pick);

            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(
                    pick,
                    APEX_BOOK_MIN_LEVEL
                            + random.nextInt(
                                    APEX_BOOK_MAX_LEVEL - APEX_BOOK_MIN_LEVEL + 1));

            out.add(book);
        }

        double mult = cfg.bookLevelMult.get();
        double frac = cfg.bookMinFraction.get();
        int remaining = count - out.size();

        for (int i = 0; i < remaining && i < pool.size(); i++) {
            Holder<Enchantment> h = pool.get(i);

            int vanillaMax = h.value().getMaxLevel();
            int top = vanillaMax <= 1
                    ? 1
                    : Math.max(1, (int) Math.round(vanillaMax * mult));

            int low = Math.max(
                    1,
                    Math.min(top, (int) Math.ceil(top * frac)));

            int lvl = low + random.nextInt(top - low + 1);

            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            book.enchant(h, lvl);
            out.add(book);
        }

        return out;
    }

    public static boolean isBannedName(Holder<Enchantment> holder) {
        StringBuilder sb = new StringBuilder();

        holder.unwrapKey()
                .ifPresent(k -> sb.append(k.location()).append(' '));

        Enchantment e = holder.value();

        if (e.description().getContents() instanceof TranslatableContents tc) {
            sb.append(tc.getKey()).append(' ');
        }

        sb.append(e.description().getString());

        String all = sb.toString().toLowerCase(Locale.ROOT);

        return all.contains("error") || all.contains("illegal");
    }

    public static List<ItemStack> randomTools(
            ServerLevel level, Rank rank, RandomSource random) {

        AscensionConfig.RankSettings cfg = AscensionConfig.rank(rank);

        int min = cfg.toolDropsMin.get();
        int max = Math.max(min, cfg.toolDropsMax.get());
        int count = min + random.nextInt(max - min + 1);

        List<ItemStack> out = new ArrayList<>();
        if (count <= 0) return out;

        List<Item> items = new ArrayList<>();

        for (String line : cfg.toolDrops.get()) {
            ResourceLocation id = ResourceLocation.tryParse(line.trim());
            if (id == null) continue;

            Item item = BuiltInRegistries.ITEM
                    .getOptional(id)
                    .orElse(Items.AIR);

            if (item != Items.AIR) items.add(item);
        }

        if (items.isEmpty()) return out;

        Registry<Enchantment> registry =
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        List<Holder.Reference<Enchantment>> all = registry.holders()
                .filter(h -> !h.is(EnchantmentTags.CURSE))
                .collect(Collectors.toCollection(ArrayList::new));

        int enchMin = cfg.toolEnchantMin.get();
        int enchMax = Math.max(enchMin, cfg.toolEnchantMax.get());

        for (int i = 0; i < count; i++) {
            ItemStack stack =
                    new ItemStack(items.get(random.nextInt(items.size())));

            List<Holder.Reference<Enchantment>> shuffled =
                    new ArrayList<>(all);

            Collections.shuffle(
                    shuffled,
                    new java.util.Random(random.nextLong()));

            int want = enchMin + random.nextInt(enchMax - enchMin + 1);

            List<Holder<Enchantment>> applied = new ArrayList<>();

            for (Holder.Reference<Enchantment> h : shuffled) {
                if (applied.size() >= want) break;
                if (!h.value().canEnchant(stack)) continue;

                boolean compatible = true;

                for (Holder<Enchantment> other : applied) {
                    if (!Enchantment.areCompatible(h, other)) {
                        compatible = false;
                        break;
                    }
                }

                if (!compatible) continue;

                stack.enchant(
                        h,
                        toolLevel(h.value().getMaxLevel(), cfg, random));

                applied.add(h);
            }

            out.add(stack);
        }

        return out;
    }

    private static int toolLevel(
            int vanillaMax,
            AscensionConfig.RankSettings cfg,
            RandomSource random) {

        int top = vanillaMax <= 1
                ? 1
                : Math.max(vanillaMax, cfg.toolTopLevel.get());

        if (random.nextDouble() < cfg.toolMaxLevelChance.get()) {
            return top;
        }

        int low = Math.max(
                1,
                Math.min(
                        top,
                        (int) Math.ceil(top * cfg.toolMinFraction.get())));

        return low + random.nextInt(top - low + 1);
    }

    public static ItemStack apexBow(ServerLevel level, RandomSource random) {
        ItemStack bow = new ItemStack(Items.BOW);

        Registry<Enchantment> registry =
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        registry.getHolder(net.minecraft.world.item.enchantment.Enchantments.POWER)
                .ifPresent(h -> bow.enchant(h, 5 + random.nextInt(2)));

        registry.getHolder(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)
                .ifPresent(h -> bow.enchant(h, 3));

        registry.getHolder(net.minecraft.world.item.enchantment.Enchantments.MENDING)
                .ifPresent(h -> bow.enchant(h, 1));

        return bow;
    }

    private static final class Entry {
        final Item item;
        final double value;
        final int min;
        final int max;
        final Map<String, Integer> enchants;

        private Entry(
                Item item,
                double value,
                int min,
                int max,
                Map<String, Integer> enchants) {

            this.item = item;
            this.value = value;
            this.min = min;
            this.max = max;
            this.enchants = enchants;
        }

        static Entry parse(String line) {
            try {
                String[] p = line.split("\\|");
                if (p.length < 4) return null;

                ResourceLocation id =
                        ResourceLocation.tryParse(p[0].trim());

                if (id == null) return null;

                Item item = BuiltInRegistries.ITEM
                        .getOptional(id)
                        .orElse(Items.AIR);

                if (item == Items.AIR) return null;

                double value = Double.parseDouble(p[1].trim());
                int min = Integer.parseInt(p[2].trim());
                int max = Math.max(
                        min,
                        Integer.parseInt(p[3].trim()));

                Map<String, Integer> enchants = new LinkedHashMap<>();

                if (p.length >= 5 && !p[4].isBlank()) {
                    for (String part : p[4].split(",")) {
                        String[] kv = part.split("=");
                        if (kv.length != 2) continue;

                        enchants.put(
                                kv[0].trim(),
                                Integer.parseInt(kv[1].trim()));
                    }
                }

                return new Entry(item, value, min, max, enchants);

            } catch (NumberFormatException ex) {
                return null;
            }
        }

        ItemStack create(RegistryAccess access, RandomSource random) {
            int amount = min + random.nextInt(max - min + 1);
            if (amount <= 0) return ItemStack.EMPTY;

            ItemStack stack = new ItemStack(item, 1);
            stack.setCount(Math.min(amount, stack.getMaxStackSize()));

            if (!enchants.isEmpty()) {
                applyEnchants(stack, access);
            }

            return stack;
        }

        private void applyEnchants(ItemStack stack, RegistryAccess access) {
            Registry<Enchantment> registry =
                    access.registryOrThrow(Registries.ENCHANTMENT);

            boolean book = stack.is(Items.ENCHANTED_BOOK);
            List<Holder<Enchantment>> applied = new ArrayList<>();

            for (Map.Entry<String, Integer> en : enchants.entrySet()) {
                String name = en.getKey().contains(":")
                        ? en.getKey()
                        : "minecraft:" + en.getKey();

                ResourceLocation id = ResourceLocation.tryParse(name);
                if (id == null) continue;

                Optional<Holder.Reference<Enchantment>> ref =
                        registry.getHolder(
                                ResourceKey.create(Registries.ENCHANTMENT, id));

                if (ref.isEmpty()) continue;

                Holder<Enchantment> holder = ref.get();

                if (book && isBannedName(holder)) continue;
                if (!book && !holder.value().canEnchant(stack)) continue;

                boolean compatible = true;

                for (Holder<Enchantment> other : applied) {
                    if (!Enchantment.areCompatible(holder, other)) {
                        compatible = false;
                        break;
                    }
                }

                if (!compatible) continue;

                int lvl = Math.max(
                        1,
                        Math.min(en.getValue(), holder.value().getMaxLevel()));

                stack.enchant(holder, lvl);
                applied.add(holder);
            }
        }
    }
}
