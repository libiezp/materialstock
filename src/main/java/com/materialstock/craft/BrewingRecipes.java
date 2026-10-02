package com.materialstock.craft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * 药水酿造配方（内置表）。
 *
 * 背景：原版<b>没有</b>酿造配方数据 —— 酿造台的配方是硬编码在 BrewingStandMenu 里的，
 * 所以 RecipeManager.getAllRecipesFor(BREWING) 拿不到任何东西，必须自己内置这张表。
 *
 * 药水物品本身全部是 minecraft:potion，靠 potion 数据组件区分效果，
 * 因此这里用变体标识 "minecraft:potion#invisibility" 来区分不同药水（见 variantId）。
 */
public final class BrewingRecipes {

    private BrewingRecipes() {
    }

    /** 变体分隔符："minecraft:potion#invisibility" */
    public static final char VARIANT_SEP = '#';

    /** 三种药水物品 */
    private static final Item[] POTION_ITEMS = {Items.POTION, Items.SPLASH_POTION, Items.LINGERING_POTION};
    private static final String[] POTION_PATHS = {"potion", "splash_potion", "lingering_potion"};

    /** 效果前缀（"long_" / "strong_"）→ 中文修饰词 */
    private static final Map<String, String> PREFIX_CN = new LinkedHashMap<>();
    static {
        PREFIX_CN.put("long", "延长版");
        PREFIX_CN.put("strong", "强化版");
    }

    /**
     * 酿造表中的全部有效药水 id（含延长/强化衍生版），用于识别"这是个药水变体"。
     * 由 buildRecipes() 填充。
     */
    private static final java.util.Set<String> POTION_IDS = new java.util.HashSet<>();
    private static final List<Entry> RECIPES = new ArrayList<>();

    /** 一条酿造配方：基础药水 + 1 个材料 → 产物药水（可产出多个，如滞留药水 1 瓶 → 药箭 8 支） */
    public static final class Entry {
        public final String basePotionId;
        public final Item ingredient;
        public final String resultPotionId;
        public final Item resultItemOverride;
        public final int resultCount;

        Entry(String basePotionId, Item ingredient, String resultPotionId, Item resultItemOverride, int resultCount) {
            this.basePotionId = basePotionId;
            this.ingredient = ingredient;
            this.resultPotionId = resultPotionId;
            this.resultItemOverride = resultItemOverride;
            this.resultCount = resultCount;
        }
    }

    static {
        // ================= 基础药水（水瓶 + 材料）=================
        recipe("water", Items.NETHER_WART, "awkward");
        // 以下加水瓶得到"平凡的药水/浓稠的药水"（无效果，但游戏里确实存在这些物品）
        recipe("water", Items.FERMENTED_SPIDER_EYE, "weakness");
        recipe("water", Items.GLISTERING_MELON_SLICE, "mundane");
        recipe("water", Items.SUGAR, "mundane");
        recipe("water", Items.RABBIT_FOOT, "mundane");
        recipe("water", Items.SPIDER_EYE, "mundane");
        recipe("water", Items.GHAST_TEAR, "mundane");
        recipe("water", Items.BLAZE_POWDER, "mundane");
        recipe("water", Items.MAGMA_CREAM, "mundane");
        recipe("water", Items.PUFFERFISH, "mundane");
        recipe("water", Items.GOLDEN_CARROT, "mundane");
        recipe("water", Items.REDSTONE, "mundane");
        recipe("water", Items.GLOWSTONE_DUST, "thick");
        recipe("water", Items.REDSTONE, "thick");

        // ================= 效果药水（粗制的药水 + 材料）=================
        // 配方依据 Minecraft Wiki "Brewing" / 各药水条目（1.21）
        effect("night_vision", Items.GOLDEN_CARROT);
        effect("invisibility", Items.FERMENTED_SPIDER_EYE);
        effect("leaping", Items.RABBIT_FOOT);
        effect("fire_resistance", Items.MAGMA_CREAM);
        effect("swiftness", Items.SUGAR);
        effect("slowness", Items.FERMENTED_SPIDER_EYE);
        effect("water_breathing", Items.PUFFERFISH);
        effect("healing", Items.GLISTERING_MELON_SLICE);
        effect("harming", Items.FERMENTED_SPIDER_EYE);
        effect("poison", Items.SPIDER_EYE);
        effect("regeneration", Items.GHAST_TEAR);
        effect("strength", Items.BLAZE_POWDER);
        effect("weakness", Items.FERMENTED_SPIDER_EYE);
        effect("slow_falling", Items.PHANTOM_MEMBRANE);
        effect("turtle_master", Items.TURTLE_HELMET);
        // 1.21 新增四件套
        effect("infested", Items.STONE);
        effect("oozing", Items.SLIME_BLOCK);
        effect("weaving", Items.COBWEB);
        effect("wind_charged", Items.BREEZE_ROD);
        // 注意：飘浮(levitation)与幸运(luck)在正式版无法酿造（仅创造/掉落），故不列入

        // ================= 延长版（红石）/ 强化版（萤石）=================
        for (String id : new String[]{"night_vision", "invisibility", "leaping", "fire_resistance", "swiftness",
                "slowness", "water_breathing", "poison", "regeneration", "strength", "weakness", "slow_falling",
                "turtle_master"}) {
            recipe(id, Items.REDSTONE, "long_" + id);
        }
        for (String id : new String[]{"leaping", "swiftness", "healing", "harming", "poison", "regeneration",
                "strength", "turtle_master"}) {
            recipe(id, Items.GLOWSTONE_DUST, "strong_" + id);
        }

        // ================= 喷溅药水（药水 + 火药）=================
        for (String id : allEffectIds()) {
            splash(id);
        }
        splash("water");
        splash("awkward");
        splash("mundane");
        splash("thick");

        // ================= 滞留药水（【喷溅药水】 + 龙息）=================
        // 关键：原版是在喷溅药水上加龙息，不是在普通药水上 —— 早先写错过，这里按 wiki 修正
        for (String id : allEffectIds()) {
            lingering(id);
        }
        lingering("water");
        lingering("awkward");
        lingering("mundane");
        lingering("thick");

        // ================= 药箭（滞留药水 + 箭 ×8）=================
        for (String id : allEffectIds()) {
            arrow(id);
        }
        arrow("water");
        arrow("awkward");
    }

    /** 全部基础效果 id（不含 long_/strong_ 衍生），按加入顺序 */
    private static List<String> allEffectIds() {
        List<String> ids = new ArrayList<>();
        for (Entry e : RECIPES) {
            if (!e.resultPotionId.startsWith("long_") && !e.resultPotionId.startsWith("strong_")) {
                if (!ids.contains(e.resultPotionId)) ids.add(e.resultPotionId);
            }
        }
        return ids;
    }

    /** 普通药水：普通药水(base) + 材料 → 普通药水(result) */
    private static void recipe(String base, Item ing, String result) {
        add(new Entry(base, ing, result, Items.POTION, 1));
    }

    /** 粗制的药水 + 材料 → 效果药水 */
    private static void effect(String id, Item ing) {
        add(new Entry("awkward", ing, id, Items.POTION, 1));
    }

    private static void splash(String id) {
        add(new Entry(id, Items.GUNPOWDER, id, Items.SPLASH_POTION, 1));
    }

    /** 滞留：喷溅药水 + 龙息 */
    private static void lingering(String id) {
        add(new Entry(id, Items.DRAGON_BREATH, id, Items.LINGERING_POTION, 1));
    }

    private static void arrow(String id) {
        add(new Entry(id, Items.ARROW, id, Items.TIPPED_ARROW, 8));
    }

    private static void add(Entry e) {
        RECIPES.add(e);
        POTION_IDS.add(e.basePotionId);
        POTION_IDS.add(e.resultPotionId);
    }

    /** 是否是已知的药水变体 id（如 "invisibility"、"long_invisibility"） */
    public static boolean isPotionVariant(String id) {
        return id != null && POTION_IDS.contains(id);
    }

    /** 拼出药水物品的变体标识：("minecraft:potion", "invisibility") -> "minecraft:potion#invisibility" */
    public static String variantId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item it = stack.getItem();
        String path = null;
        for (int i = 0; i < POTION_ITEMS.length; i++) {
            if (it == POTION_ITEMS[i]) { path = POTION_PATHS[i]; break; }
        }
        if (path == null) return null;
        PotionContents pc = stack.get(DataComponents.POTION_CONTENTS);
        if (pc == null || pc.potion().isEmpty()) return null;
        ResourceLocation rl = BuiltInRegistries.POTION.getKey(pc.potion().get().value());
        if (rl == null) return null;
        return "minecraft:" + path + VARIANT_SEP + rl.getPath();
    }

    /** 从变体标识解析出物品 + 药水 id；返回 null 表示不是药水变体 */
    public static PotionRef parse(String fullId) {
        if (fullId == null) return null;
        int sep = fullId.indexOf(VARIANT_SEP);
        if (sep <= 0 || sep >= fullId.length() - 1) return null;
        String itemPart = fullId.substring(0, sep);
        String potionId = fullId.substring(sep + 1);
        Item item = null;
        for (int i = 0; i < POTION_PATHS.length; i++) {
            if (itemPart.equals("minecraft:" + POTION_PATHS[i])) { item = POTION_ITEMS[i]; break; }
        }
        if (item == null) return null;
        return new PotionRef(item, potionId);
    }

    public static final class PotionRef {
        public final Item item;
        public final String potionId;

        PotionRef(Item item, String potionId) {
            this.item = item;
            this.potionId = potionId;
        }
    }

    /** 构造对应变体的 ItemStack（用于注册表/命名查询） */
    public static ItemStack makeStack(Item item, String potionId) {
        Potion p = BuiltInRegistries.POTION.get(ResourceLocation.tryParse("minecraft:" + potionId));
        ItemStack st = new ItemStack(item);
        if (p != null) {
            // PotionContents 要的是 Holder<Potion>，不能直接塞 Potion
            st.set(DataComponents.POTION_CONTENTS,
                new PotionContents(BuiltInRegistries.POTION.wrapAsHolder(p)));
        }
        return st;
    }

    /**
     * 药水变体的显示名。走语言文件（客户端已本地化）。
     * 普通/喷溅/滞留用 item.minecraft.<potion|splash_potion|lingering_potion>.effect.<id>；
     * 药箭的语言键不含效果，所以用「<效果名>箭」拼出来。
     * long_/strong_ 没有独立语言条目，用「延长版/强化版 + 基础名」拼出。
     */
    public static String displayName(Item item, String potionId) {
        String trimmed = potionId;
        String prefix = null;
        if (trimmed.startsWith("long_")) {
            prefix = PREFIX_CN.get("long");
            trimmed = trimmed.substring("long_".length());
        } else if (trimmed.startsWith("strong_")) {
            prefix = PREFIX_CN.get("strong");
            trimmed = trimmed.substring("strong_".length());
        }

        String name;
        if (item == Items.TIPPED_ARROW) {
            String effKey = "item.minecraft.potion.effect." + trimmed;
            String eff = net.minecraft.network.chat.Component.translatable(effKey).getString();
            // 没有对应语言键时会原样返回 key，此时退回 id 本身
            if (eff.equals(effKey)) eff = trimmed;
            name = eff + "箭";
        } else {
            String base = "item.minecraft.potion";
            if (item == Items.SPLASH_POTION) base = "item.minecraft.splash_potion";
            else if (item == Items.LINGERING_POTION) base = "item.minecraft.lingering_potion";
            String key = base + ".effect." + trimmed;
            name = net.minecraft.network.chat.Component.translatable(key).getString();
            // 没有对应语言键时会原样返回 key，此时退回基础药水名
            if (name.equals(key)) {
                name = new ItemStack(item).getHoverName().getString();
            }
        }
        return prefix == null ? name : (prefix + name);
    }

    /** 该药水变体是否可酿造出来（在酿造表里有配方） */
    public static List<Entry> entriesForResult(String resultPotionId, Item resultItem) {
        List<Entry> hit = new ArrayList<>();
        for (Entry e : RECIPES) {
            if (!e.resultPotionId.equals(resultPotionId)) continue;
            if (e.resultItemOverride != resultItem) continue;
            hit.add(e);
        }
        return hit;
    }

    /** 全部已知药水变体 id（用于搜索时展开候选），按添加顺序 */
    public static List<String> allVariantIds() {
        return new ArrayList<>(POTION_IDS);
    }

    /** 按变体 id 反查对应的 ItemStack（含效果），用于界面渲染图标 */
    public static ItemStack variantStack(String fullId) {
        PotionRef ref = parse(fullId);
        if (ref == null) return ItemStack.EMPTY;
        return makeStack(ref.item, ref.potionId);
    }
}
