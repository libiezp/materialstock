package com.materialstock.litematica;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * 材料分类（数据驱动）：大类与小类归属来自 CategoryData（由 _extract 的 v25 分类表生成）。
 * 投影材料中的物品按 ID 精确归类；表外物品（工具/武器/药水等非方块）归入"其他"。
 * 墙面变体（墙上的火把/告示牌等）已由 MaterialListHelper 在合并阶段归并到主体物品。
 */
public class CategoryHelper {
    public static final class Category {
        public final String name;
        public final List<?> tags;
        public final List<String> idPrefixes;

        public Category(String name, List<?> tags, List<String> idPrefixes) {
            this.name = name;
            this.tags = tags;
            this.idPrefixes = idPrefixes;
        }
    }

    /** 大类顺序（与 v25 分类表一致） */
    public static final List<Category> CATEGORIES = List.of(
        new Category("原木与木材", List.of(), List.of()),
        new Category("矿物与矿石", List.of(), List.of()),
        new Category("石材与石制品", List.of(), List.of()),
        new Category("金属与材料", List.of(), List.of()),
        new Category("染色方块", List.of(), List.of()),
        new Category("红石与元件", List.of(), List.of()),
        new Category("植物与食物", List.of(), List.of()),
        new Category("工作与交互方块", List.of(), List.of()),
        new Category("自然方块类", List.of(), List.of()),
        new Category("其他", List.of(), List.of())
    );

    /** 返回物品所属大类名（优先用户分类管理覆盖；表外物品/被删除大类 -> 其他） */
    public static String getCategory(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        com.materialstock.StockConfig cfg = com.materialstock.StockConfig.get();
        java.util.List<String> ov = cfg.categoryOverrides.get(id);
        if (ov != null && !ov.isEmpty()) {
            return ov.get(0);
        }
        String cat = CategoryData.category(id);
        if (cat != null && !cfg.removedCategories.contains(cat)) {
            return cat;
        }
        return "其他";
    }

    /** 返回物品所属小类名（override 优先；被删除小类视为散货返回 null） */
    public static String getSub(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).getPath();
        com.materialstock.StockConfig cfg = com.materialstock.StockConfig.get();
        java.util.List<String> ov = cfg.categoryOverrides.get(id);
        if (ov != null) {
            String sub = ov.size() > 1 ? ov.get(1) : null;
            if (sub != null && !ov.isEmpty() && cfg.removedSubs.containsKey(ov.get(0))
                    && cfg.removedSubs.get(ov.get(0)).contains(sub)) {
                return null;
            }
            return sub;
        }
        String sub2 = CategoryData.sub(id);
        if (sub2 != null) {
            String cat = getCategory(item);
            if (cfg.removedSubs.containsKey(cat) && cfg.removedSubs.get(cat).contains(sub2)) {
                return null;
            }
        }
        return sub2;
    }
}
