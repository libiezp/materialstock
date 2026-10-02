package com.materialstock.craft;

import com.materialstock.data.MaterialLine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.BlastingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.SmokingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * 合成计算器：输入要合成的物品与数量，按原版配方推导直接原料数量。
 * 推导只展开成品的一层直接原料（不做过度递归）：
 * 例如红石比较器 → 红石粉 + 下界石英 + 橡木台阶；
 * 配方中的"任意同类"原料（tag）统一用普通橡木系列表示，不会出现竹子。
 * 基础材料列表默认由用户维护（可为空），也可通过 {@link #deriveBaseMaterials} 自动推导后自行删改。
 */
public class CraftCalculator {

    public static List<MaterialLine> deriveBaseMaterials(Item target) {
        return deriveBaseMaterials(target, 0);
    }

    /** 变体版本入口：成品带效果（药水）时必须用它，否则区分不出不同药水 */
    public static List<MaterialLine> deriveBaseMaterials(String targetId) {
        Variant v = parseVariant(targetId);
        return deriveBaseMaterials(v == null ? null : v.item, v == null ? null : v.variant, 0);
    }

    public static List<MaterialLine> deriveBaseMaterials(String targetId, int recipeIndex) {
        Variant v = parseVariant(targetId);
        return deriveBaseMaterials(v == null ? null : v.item, v == null ? null : v.variant, recipeIndex);
    }

    /**
     * 推导合成 1 个单位成品所需的直接原料（使用第 recipeIndex 个配方，用于多配方切换）。
     * 数量可为分数：如 1 铁锭 = 1/9 铁块（铁块分解配方产出 9 个，取 1/9 次配方）。
     */
    public static List<MaterialLine> deriveBaseMaterials(Item target, int recipeIndex) {
        return deriveBaseMaterials(target, null, recipeIndex);
    }

    public static List<MaterialLine> deriveBaseMaterials(Item target, String variantId, int recipeIndex) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (target == null || target == Items.AIR) {
            return new ArrayList<>();
        }

        List<Recipe<?>> recipes = findAllRecipes(target, variantId);
        if (recipes.isEmpty()) {
            // 目标本身没有配方，视为基础材料
            Variant tv = new Variant(target, variantId);
            out.put(tv.id(), 1.0);
            return toLinesById(out);
        }
        // 同时夹下界：recipeIndex 会被持久化进 stock.json，旧配置或手改可能为负数，负数会直接抛 IndexOutOfBounds
        Recipe<?> recipe = recipes.get(Math.max(0, Math.min(recipeIndex, recipes.size() - 1)));

        HolderLookup.Provider registryAccess = registryAccess();
        ItemStack result = recipe.getResultItem(registryAccess);
        double outputPerCraft = Math.max(1, result.getCount());
        // 1 单位成品需要 1/outputPerCraft 次完整配方（可为分数，不再向上取整）
        double crafts = 1.0 / outputPerCraft;

        for (Ingredient ingredient : recipe.getIngredients()) {
            if (ingredient == null) continue;
            ItemStack rep = pickRepresentativeStack(ingredient, target);
            if (rep == null || rep.isEmpty()) continue;
            String cid = canonicalId(rep);
            if (cid == null) continue;
            int perCraft = Math.max(1, rep.getCount());
            out.merge(cid, crafts * perCraft, Double::sum);
        }
        return toLinesById(out);
    }

    /**
     * 原料代表物品：tag 类原料（多个选项）优先选橡木系列；
     * 找不到橡木时也绝不选竹子；任何竹木原料（含 mod 配方里的单物品竹板等）一律映射为橡木对应物。
     * 例外：合成产物本身是竹子类（竹板/竹台阶/竹块等）时，原料保留竹子系列，不映射橡木。
     */
    private static Item pickRepresentative(Ingredient ingredient, Item target) {
        ItemStack[] choices;
        try {
            choices = ingredient.getItems();
        } catch (Exception e) {
            return null;
        }
        if (choices == null || choices.length == 0) return null;

        java.util.List<ItemStack> valid = new java.util.ArrayList<>();
        for (ItemStack c : choices) {
            if (c != null && !c.isEmpty()) valid.add(c);
        }
        if (valid.isEmpty()) return null;

        // 0) 产物是竹子类：原料保留竹子系列（竹板/竹块/竹竿），不映射橡木
        if (target != null) {
            String tid = itemId(target);
            if (tid != null && tid.contains("bamboo")) {
                for (ItemStack c : valid) {
                    if (itemId(c.getItem()).contains("bamboo")) {
                        return c.getItem();
                    }
                }
                return valid.get(0).getItem();
            }
        }

        // 1) 优先橡木系列（oak_log / oak_planks / oak_slab …），且排除竹子变体
        for (ItemStack c : valid) {
            String id = itemId(c.getItem());
            if (id.contains("oak_") && !id.contains("bamboo_")) {
                return c.getItem();
            }
        }
        // 2) 无橡木可选时，绝不选竹子，取第一个非竹子选项
        for (ItemStack c : valid) {
            if (!itemId(c.getItem()).contains("bamboo_")) {
                return c.getItem();
            }
        }
        // 3) 兜底：只有竹木可选时，映射为橡木对应物（竹板→橡木木板、竹块→橡木原木…）
        return bambooToOak(valid.get(0).getItem());
    }

    /**
     * 原料代表「物品栈」：用于需要保留变体信息（药水效果）的场景。
     * 选谁复用 pickRepresentative 的规则（橡木优先、不选竹子），再取回对应的完整 ItemStack。
     */
    private static ItemStack pickRepresentativeStack(Ingredient ingredient, Item target) {
        Item chosen = pickRepresentative(ingredient, target);
        if (chosen == null || chosen == Items.AIR) return null;
        try {
            ItemStack[] choices = ingredient.getItems();
            if (choices != null) {
                for (ItemStack c : choices) {
                    if (c != null && !c.isEmpty() && c.getItem() == chosen) return c;
                }
            }
        } catch (Exception ignored) {
        }
        return new ItemStack(chosen);
    }

    /** 竹木物品 → 橡木对应物（用户口径：所有关于木头的合成统一用橡木，不出现竹子） */
    private static Item bambooToOak(Item item) {        if (item == null || item == Items.AIR) return item;
        String id = itemId(item);
        if (id == null || !id.contains("bamboo")) return item;

        String oakId;
        if (id.equals("minecraft:bamboo")) {
            oakId = "minecraft:oak_planks"; // 竹竿当木质原料用时 → 橡木木板（用户口径：木头合成统一橡木，不出现竹子）
        } else if (id.equals("minecraft:bamboo_sapling")) {
            return item; // 竹苗保留
        } else if (id.startsWith("minecraft:stripped_bamboo_block")) {
            oakId = "minecraft:stripped_oak_log";
        } else if (id.startsWith("minecraft:stripped_bamboo_wood")) {
            oakId = "minecraft:stripped_oak_wood";
        } else if (id.equals("minecraft:bamboo_block")) {
            oakId = "minecraft:oak_log";
        } else if (id.equals("minecraft:bamboo_wood")) {
            oakId = "minecraft:oak_wood";
        } else if (id.equals("minecraft:bamboo_raft")) {
            oakId = "minecraft:oak_boat";
        } else if (id.equals("minecraft:bamboo_chest_raft")) {
            oakId = "minecraft:oak_chest_boat";
        } else if (id.equals("minecraft:bamboo_mosaic")) {
            oakId = "minecraft:oak_planks";
        } else if (id.equals("minecraft:bamboo_mosaic_slab")) {
            oakId = "minecraft:oak_slab";
        } else if (id.equals("minecraft:bamboo_mosaic_stairs")) {
            oakId = "minecraft:oak_stairs";
        } else if (id.startsWith("minecraft:bamboo_")) {
            // 竹板/台阶/楼梯/栅栏/门/活板门/按钮/压力板/告示牌… → oak + 后缀
            oakId = "minecraft:oak" + id.substring("minecraft:bamboo".length());
        } else {
            return item;
        }
        Item oak = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .get(net.minecraft.resources.ResourceLocation.tryParse(oakId));
        return (oak != null && oak != Items.AIR) ? oak : item;
    }

    /** 子合成最大展开深度（防止配方环导致无限递归） */
    private static final int MAX_SUB_DEPTH = 4;

    /**
     * 构建子合成树：需要 qty 个 item 时，children 为其直接原料（仅合成配方，熔炼/切石不出现在子合成里）。
     * 每个子节点继续递归（一步步展开，不跨级：红石火把 → 木棍+红石，木棍 → 木板，木板 → 原木）。
     * 深度上限 4 层 + 祖先链环路截断，保证不会无限递归。
     */
    public static MaterialLine buildSubTree(Item item, double qty) {
        return buildSubTree(item, null, qty, new java.util.HashSet<>(), 0);
    }

    /** 变体版本入口：药水必须用它才能区分效果 */
    public static MaterialLine buildSubTree(String id, double qty) {
        Variant v = parseVariant(id);
        if (v == null) return new MaterialLine(id == null ? "" : id, qty);
        return buildSubTree(v.item, v.variant, qty, new java.util.HashSet<>(), 0);
    }

    private static MaterialLine buildSubTree(Item item, String variant, double qty, java.util.Set<String> path, int depth) {
        Variant self = (item == null) ? null : new Variant(item, variant);
        String selfId = self == null ? "" : self.id();
        MaterialLine node = new MaterialLine(selfId, qty);
        if (item == null || item == Items.AIR) return node;
        if (depth >= MAX_SUB_DEPTH) return node;
        if (path.contains(selfId)) return node;
        Recipe<?> craft = findCraftingRecipe(item, variant);
        if (craft == null) return node;
        node.craftable = true;

        HolderLookup.Provider ra = registryAccess();
        if (ra == null) return node;
        ItemStack result = craft.getResultItem(ra);
        double outputPerCraft = Math.max(1, result.getCount());
        // 需要 qty 个 item：完整配方次数可为分数（如 1 铁锭 = 1/9 铁块）
        double crafts = qty / outputPerCraft;

        java.util.Set<String> newPath = new java.util.HashSet<>(path);
        newPath.add(selfId);
        // 先合并同一原料在配方里占用的全部数量（如箱子配方的 8 个木板槽位 → 木板 x8），
        // 再据此构建子树。绝不能"先按单格递归、再合并数量" ——
        // 那样合并后 perUnit 变大了，但子节点仍按单格算，会导致内层数量偏小
        // （例：8 木板应需 2 原木，却显示 0.25 原木，用户按此备料会严重少备）。
        // 键用「规范 id」而非 Item：药水要按效果区分（水瓶 / 粗制的药水 / 隐身药水…）。
        java.util.Map<String, double[]> totals = new java.util.LinkedHashMap<>();
        for (Ingredient ingredient : craft.getIngredients()) {
            if (ingredient == null) continue;
            ItemStack rep = pickRepresentativeStack(ingredient, item);
            if (rep == null || rep.isEmpty()) continue;
            String cid = canonicalId(rep);
            if (cid == null) continue;
            int perCraft = Math.max(1, rep.getCount());
            double[] acc = totals.get(cid);
            if (acc == null) {
                // 只存数量；ItemStack 通过变体 id 反查，避免额外保存状态
                totals.put(cid, new double[]{crafts * perCraft});
            } else {
                acc[0] += crafts * perCraft;
            }
        }
        for (java.util.Map.Entry<String, double[]> en : totals.entrySet()) {
            Variant cv = parseVariant(en.getKey());
            if (cv == null) continue;
            node.children.add(buildSubTree(cv.item, cv.variant, en.getValue()[0], newPath, depth + 1));
        }
        return node;
    }

    /** 查找产出该物品的合成配方（仅 CRAFTING，子合成专用，排除熔炼/切石）。
     *  优先选含多选项原料（tag 风格）的原版配方，避免 mod 添加的竹板单物品配方抢先。 */
    private static Recipe<?> findCraftingRecipe(Item item) {
        return findCraftingRecipe(item, null);
    }

    /** 变体版本：合成配方优先；药水则回退到酿造配方（原版无 brewing 配方数据，来自内置表） */
    private static Recipe<?> findCraftingRecipe(Item item, String variantId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getRecipeManager() == null) return null;
        RecipeManager rm = mc.level.getRecipeManager();
        HolderLookup.Provider ra = registryAccess();
        if (ra == null) return null;
        Recipe<?> fallback = null;
        try {
            for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe r = holder.value();
                if (r == null || r.isSpecial() || !r.getResultItem(ra).is(item)) continue;
                if (fallback == null) fallback = r;
                if (hasTagLikeIngredient(r)) {
                    return r;
                }
            }
        } catch (Exception ignored) {
        }
        if (fallback != null) return fallback;
        // 药水没有合成配方，回退到酿造配方
        if (variantId != null) {
            List<Recipe<?>> brewing = brewingRecipesFor(item, variantId);
            if (!brewing.isEmpty()) return brewing.get(0);
        }
        return null;
    }

    /** 配方是否含"任意同类"（tag 展开为多个选项）原料，原版配方多为这种风格 */
    private static boolean hasTagLikeIngredient(CraftingRecipe r) {
        try {
            for (Ingredient ing : r.getIngredients()) {
                if (ing != null && ing.getItems() != null && ing.getItems().length > 1) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 主合成配方类型标注：熔炼/切石/酿造/合成（空=合成或基础材料），用于界面提示 */
    public static String recipeTagFor(Item item) {
        return recipeTagFor(item, null, 0);
    }

    /** 指定配方的类型标注（多配方切换时按当前选中的配方标注） */
    public static String recipeTagFor(Item item, int recipeIndex) {
        return recipeTagFor(item, null, recipeIndex);
    }

    /** 变体版本：药水会标注为"酿造" */
    public static String recipeTagFor(Item item, String variantId, int recipeIndex) {
        if (item == null || item == Items.AIR) return "";
        List<Recipe<?>> recipes = findAllRecipes(item, variantId);
        if (recipes.isEmpty()) return "";
        // 同样夹下界，避免负数下标越界
        Recipe<?> r = recipes.get(Math.max(0, Math.min(recipeIndex, recipes.size() - 1)));
        if (r instanceof PotionRecipe) return "酿造";
        if (r instanceof StonecutterRecipe) return "切石";
        if (r instanceof SmeltingRecipe || r instanceof BlastingRecipe
            || r instanceof SmokingRecipe || r instanceof CampfireCookingRecipe) {
            return "熔炼";
        }
        return "";
    }

    /** 产出该物品的可用配方总数（用于成品行显示"配方 i/N"） */
    public static int recipeCountFor(Item item) {
        return recipeCountFor(item, null);
    }

    /** 变体版本：药水要把酿造配方算进去 */
    public static int recipeCountFor(Item item, String variantId) {
        return findAllRecipes(item, variantId).size();
    }

    /**
     * 收集产出该物品的全部配方，顺序：合成（tag 风格优先）→ 熔炼/高炉/烟熏/营火（同原料去重）→ 切石。
     * 这样铁锭可以同时显示"铁粒合成/铁块分解/铁原矿熔炼"等所有方式，由用户切换。
     */
    public static List<Recipe<?>> findAllRecipes(Item item) {
        return findAllRecipes(item, null);
    }

    /** 变体版本：variantId 非空时会额外找出该药水的酿造配方 */
    public static List<Recipe<?>> findAllRecipes(Item item, String variantId) {
        List<Recipe<?>> out = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getRecipeManager() == null) return out;
        RecipeManager rm = mc.level.getRecipeManager();
        HolderLookup.Provider ra = registryAccess();
        if (ra == null) return out;

        List<Recipe<?>> craft = new ArrayList<>();
        try {
            for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe r = holder.value();
                if (r == null || r.isSpecial() || !r.getResultItem(ra).is(item)) continue;
                craft.add(r);
            }
        } catch (Exception ignored) {
        }
        craft.sort((a, b) -> Boolean.compare(
            hasTagLikeIngredient((CraftingRecipe) b), hasTagLikeIngredient((CraftingRecipe) a)));
        out.addAll(craft);

        // 熔炼类（熔炉/高炉/烟熏/营火）由 addCooking 内部一次取全；
        // 原代码这里连调 4 次，每次都把四个注册表重扫一遍，纯属复制粘贴冗余，会成倍拖慢界面刷新。
        addCooking(out, rm, ra, item);

        try {
            for (RecipeHolder<StonecutterRecipe> holder : rm.getAllRecipesFor(RecipeType.STONECUTTING)) {
                StonecutterRecipe r = holder.value();
                if (r != null && r.getResultItem(ra).is(item)) out.add(r);
            }
        } catch (Exception ignored) {
        }

        // 药水酿造：原版没有酿造配方数据（硬编码在 BrewingStandMenu），只能用内置表补充
        if (variantId != null) {
            out.addAll(brewingRecipesFor(item, variantId));
        }
        return out;
    }

    /** 由内置酿造表生成配方对象（基础药水 + 材料 → 目标药水） */
    private static List<Recipe<?>> brewingRecipesFor(Item targetItem, String targetVariant) {
        List<Recipe<?>> out = new ArrayList<>();
        for (com.materialstock.craft.BrewingRecipes.Entry e
                : com.materialstock.craft.BrewingRecipes.entriesForResult(targetVariant, targetItem)) {
            // 基础药水的形态由【材料】决定（对照原版酿造规则）：
            //   龙息 → 在喷溅药水上加   ⟹ base 是喷溅药水
            //   箭   → 用滞留药水合成   ⟹ base 是滞留药水
            //   其余（火药先转喷溅）    ⟹ 产物是喷溅时 base 取普通药水，否则与产物同形态
            Item baseItem;
            if (e.ingredient == Items.DRAGON_BREATH) baseItem = Items.SPLASH_POTION;
            else if (e.ingredient == Items.ARROW) baseItem = Items.LINGERING_POTION;
            else if (targetItem == Items.SPLASH_POTION) baseItem = Items.POTION;
            else baseItem = targetItem;

            ItemStack baseStack = com.materialstock.craft.BrewingRecipes.makeStack(baseItem, e.basePotionId);
            ItemStack addStack = new ItemStack(e.ingredient);
            ItemStack result = e.resultItemOverride == Items.TIPPED_ARROW
                ? new ItemStack(Items.TIPPED_ARROW, e.resultCount)
                : com.materialstock.craft.BrewingRecipes.makeStack(e.resultItemOverride, e.resultPotionId);
            if (result.isEmpty() || baseStack.isEmpty()) continue;
            out.add(new PotionRecipe(Ingredient.of(baseStack), Ingredient.of(addStack), result));
        }
        return out;
    }

    /** 熔炼类配方收集：熔炉/高炉/烟熏/营火各取产出匹配的配方，与已有熔炼配方原料相同则跳过 */
    private static void addCooking(List<Recipe<?>> out, RecipeManager rm, HolderLookup.Provider ra, Item item) {
        try {
            for (RecipeHolder<SmeltingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMELTING)) {
                addCookingIfNew(out, holder.value(), ra, item);
            }
        } catch (Exception ignored) {
        }
        try {
            for (RecipeHolder<BlastingRecipe> holder : rm.getAllRecipesFor(RecipeType.BLASTING)) {
                addCookingIfNew(out, holder.value(), ra, item);
            }
        } catch (Exception ignored) {
        }
        try {
            for (RecipeHolder<SmokingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMOKING)) {
                addCookingIfNew(out, holder.value(), ra, item);
            }
        } catch (Exception ignored) {
        }
        try {
            for (RecipeHolder<CampfireCookingRecipe> holder : rm.getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
                addCookingIfNew(out, holder.value(), ra, item);
            }
        } catch (Exception ignored) {
        }
    }

    private static void addCookingIfNew(List<Recipe<?>> out, Recipe<?> r, HolderLookup.Provider ra, Item item) {
        if (r == null || !r.getResultItem(ra).is(item)) return;
        Item key = firstIngredientItem(r);
        for (Recipe<?> prev : out) {
            if (prev instanceof AbstractCookingRecipe && firstIngredientItem(prev) == key) {
                return;
            }
        }
        out.add(r);
    }

    private static Item firstIngredientItem(Recipe<?> r) {
        try {
            for (Ingredient ing : r.getIngredients()) {
                if (ing == null) continue;
                ItemStack[] ch = ing.getItems();
                if (ch != null && ch.length > 0 && ch[0] != null && !ch[0].isEmpty()) {
                    return ch[0].getItem();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 按规范 id 组装材料行（药水会带上效果后缀，靠 parseVariant 还原显示） */
    private static List<MaterialLine> toLinesById(Map<String, Double> map) {
        List<MaterialLine> lines = new ArrayList<>();
        for (Map.Entry<String, Double> e : map.entrySet()) {
            if (e.getKey() != null && !e.getKey().isEmpty() && e.getValue() != null && e.getValue() > 0) {
                lines.add(new MaterialLine(e.getKey(), e.getValue()));
            }
        }
        return lines;
    }

    /** 由 物品->数量 转材料行（内部走规范 id） */
    private static List<MaterialLine> toLines(Map<Item, Double> map) {
        Map<String, Double> byId = new LinkedHashMap<>();
        for (Map.Entry<Item, Double> e : map.entrySet()) {
            if (e.getKey() == null || e.getKey() == Items.AIR) continue;
            byId.merge(itemId(e.getKey()), e.getValue(), Double::sum);
        }
        return toLinesById(byId);
    }

    /**
     * 按用户定义的基础材料表计算总需求：total = perUnit * targetCount。
     * 返回 LinkedHashMap<itemId, 数量>。
     */
    public static Map<String, Integer> calculate(Map<String, Integer> perUnit, int targetCount) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (targetCount <= 0) return result;
        for (Map.Entry<String, Integer> e : perUnit.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0) {
                result.put(e.getKey(), e.getValue() * targetCount);
            }
        }
        return result;
    }

    /** 查找产出该物品的第一个可用配方：优先合成（tag 风格配方优先），其次熔炼/切石 */
    private static Recipe<?> findRecipe(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.getRecipeManager() == null) return null;
        RecipeManager rm = mc.level.getRecipeManager();
        HolderLookup.Provider registryAccess = registryAccess();
        if (registryAccess == null) return null;

        Recipe<?> craftFallback = null;
        try {
            for (RecipeHolder<CraftingRecipe> holder : rm.getAllRecipesFor(RecipeType.CRAFTING)) {
                CraftingRecipe r = holder.value();
                if (r == null || r.isSpecial() || !r.getResultItem(registryAccess).is(item)) continue;
                if (craftFallback == null) craftFallback = r;
                if (hasTagLikeIngredient(r)) {
                    return r;
                }
            }
        } catch (Exception ignored) {
        }
        if (craftFallback != null) return craftFallback;

        try {
            for (RecipeHolder<SmeltingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMELTING)) {
                AbstractCookingRecipe r = holder.value();
                if (r != null && r.getResultItem(registryAccess).is(item)) return r;
            }
            for (RecipeHolder<BlastingRecipe> holder : rm.getAllRecipesFor(RecipeType.BLASTING)) {
                AbstractCookingRecipe r = holder.value();
                if (r != null && r.getResultItem(registryAccess).is(item)) return r;
            }
            for (RecipeHolder<SmokingRecipe> holder : rm.getAllRecipesFor(RecipeType.SMOKING)) {
                AbstractCookingRecipe r = holder.value();
                if (r != null && r.getResultItem(registryAccess).is(item)) return r;
            }
            for (RecipeHolder<CampfireCookingRecipe> holder : rm.getAllRecipesFor(RecipeType.CAMPFIRE_COOKING)) {
                AbstractCookingRecipe r = holder.value();
                if (r != null && r.getResultItem(registryAccess).is(item)) return r;
            }
        } catch (Exception ignored) {
        }

        try {
            for (RecipeHolder<StonecutterRecipe> holder : rm.getAllRecipesFor(RecipeType.STONECUTTING)) {
                StonecutterRecipe r = holder.value();
                if (r != null && r.getResultItem(registryAccess).is(item)) return r;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static HolderLookup.Provider registryAccess() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.registryAccess() : null;
    }

    public static String itemId(Item item) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
    }

    // ==================== 药水变体支持 ====================
    // 药水物品全是 minecraft:potion，靠数据组件区分效果，普通物品 id 无法区分。
    // 这里统一用「规范 id」：普通物品 "minecraft:iron_ingot"，药水 "minecraft:potion#invisibility"。

    /** 物品 + 可选变体（药水效果 id） */
    public static final class Variant {
        public final Item item;
        /** null = 普通物品 */
        public final String variant;

        Variant(Item item, String variant) {
            this.item = item;
            this.variant = variant;
        }

        /** 规范 id：变体存在时拼成 "物品id#变体" */
        public String id() {
            String base = itemId(this.item);
            return this.variant == null ? base : (base + com.materialstock.craft.BrewingRecipes.VARIANT_SEP + this.variant);
        }
    }

    /**
     * 解析规范 id 为 物品+变体。
     * 兼容旧数据：普通药水写法 "minecraft:potion" 会被补成水瓶（酿造链的起点），
     * 这样旧配置里的药水条目还能正常显示。
     */
    public static Variant parseVariant(String id) {
        if (id == null || id.isEmpty()) return null;
        com.materialstock.craft.BrewingRecipes.PotionRef ref =
            com.materialstock.craft.BrewingRecipes.parse(id);
        if (ref != null) {
            Item it = ref.item;
            return it == null || it == Items.AIR ? null : new Variant(it, ref.potionId);
        }
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) return null;
        Item it = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (it == Items.AIR) return null;
        // 旧格式的裸药水 id：补成水瓶变体
        if (it == Items.POTION || it == Items.SPLASH_POTION || it == Items.LINGERING_POTION) {
            return new Variant(it, "water");
        }
        return new Variant(it, null);
    }

    /** 从原料 ItemStack 取规范 id（药水会带上效果） */
    private static String canonicalId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        String v = com.materialstock.craft.BrewingRecipes.variantId(stack);
        return v != null ? v : itemId(stack.getItem());
    }
}
