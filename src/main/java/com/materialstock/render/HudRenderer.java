package com.materialstock.render;

import com.materialstock.StockConfig;
import com.materialstock.litematica.MaterialListHelper;
import com.materialstock.scan.StockAreaScanner;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 右下角信息浮窗（HUD）：显示当前投影中"未收集 且 未忽略"的材料与数量。
 * 打开方式：详情页点"信息显示"按钮；忽略的材料 / 已收集的材料不显示。
 */
public final class HudRenderer {
    /** 浮窗是否开启（详情页"信息显示"按钮切换） */
    public static boolean hudEnabled = false;
    /** 浮窗自动重扫备货区的上次时间（毫秒） */
    private static long lastAutoScanMs = 0L;
    /** 当前投影的材料统计（详情页加载投影时更新） */
    public static Map<Item, Integer> hudMaterials = new LinkedHashMap<>();

    private HudRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (hudEnabled) {
                render(drawContext);
            }
        });
    }

    /** 背包 + 物品栏里该物品的数量 */
    private static int backpackCount(Item item) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        int n = 0;
        net.minecraft.world.entity.player.Inventory inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && st.getItem() == item) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** 浮窗数量格式：>=1728 显示 "N (x.xx 潜影盒)"；>=64 显示 "N (x × 64 + y)"（余数0省略）；<64 只显示 N */
    private static String fmtHudQty(int n) {
        if (n < 0) n = 0;
        if (n >= 1728) {
            return n + " (" + String.format("%.2f", n / 1728.0) + " 潜影盒)";
        }
        if (n >= 64) {
            int q = n / 64;
            int r = n % 64;
            return n + " (" + q + " × 64" + (r > 0 ? " + " + r : "") + ")";
        }
        return String.valueOf(n);
    }

    /** 小类序号（大类内按小类顺序；散货排小类之后） */
    private static int subIndex(Item item) {
        String cat = com.materialstock.litematica.CategoryHelper.getCategory(item);
        String sub = com.materialstock.litematica.CategoryHelper.getSub(item);
        if (sub == null) return 999;
        return com.materialstock.litematica.CategoryData.subOrder(cat, sub);
    }

    /** 大类序号（用于浮窗从上到下排序；"其他"兜底排最后） */
    private static int catIndex(Item item) {
        String cat = com.materialstock.litematica.CategoryHelper.getCategory(item);
        java.util.List<com.materialstock.litematica.CategoryHelper.Category> cats =
            com.materialstock.litematica.CategoryHelper.CATEGORIES;
        for (int i = 0; i < cats.size(); i++) {
            if (cats.get(i).name.equals(cat)) return i;
        }
        return 99;
    }

    private static void render(GuiGraphics ctx) {
        if (hudMaterials.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        int multiplier = Math.max(1, StockConfig.get().multiplier);
        Map<Item, Integer> avail = StockAreaScanner.summarizeAvailable();
        // 排列方式：0=按大类→小类→ID；1/2=按所需数量升/降序（跟随材料清单的排列方式）
        List<Item> ordered = new ArrayList<>(hudMaterials.keySet());
        int sortMode = StockConfig.get().sortMode;
        if (sortMode > 0) {
            java.util.Comparator<Item> cmp = java.util.Comparator
                .comparingInt((Item it) -> {
                    Integer c = hudMaterials.get(it);
                    return (c == null ? 0 : c) * multiplier;
                });
            if (sortMode == 2) {
                cmp = cmp.reversed();
            }
            ordered.sort(cmp.thenComparing(it -> BuiltInRegistries.ITEM.getKey(it).toString()));
        } else {
            ordered.sort(java.util.Comparator
                .comparingInt((Item it) -> catIndex(it))
                .thenComparingInt((Item it) -> subIndex(it))
                .thenComparing(it -> BuiltInRegistries.ITEM.getKey(it).toString()));
        }
        // 收集需要展示的材料（最多 8 种；忽略的不显示；追踪物品置顶且即使够了也显示）
        List<Item> shown = new ArrayList<>();
        List<Integer> needOf = new ArrayList<>();
        List<Integer> haveOf = new ArrayList<>();
        int skipped = 0;
        // 第一轮：追踪物品置顶（不受"够了不显示"限制）
        for (Item item : ordered) {
            if (shown.size() >= 8) break;
            Integer cnt = hudMaterials.get(item);
            if (cnt == null) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!StockConfig.get().trackedItems.contains(id)) continue;
            if (StockConfig.get().ignoredMaterials.contains(id)) continue;
            int need = cnt * multiplier;
            int have = avail.getOrDefault(item, 0) + backpackCount(item);
            shown.add(item);
            needOf.add(need);
            haveOf.add(have);
        }
        // 第二轮：其余按原顺序补到 8 个
        for (Item item : ordered) {
            Integer cnt = hudMaterials.get(item);
            if (cnt == null) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (StockConfig.get().ignoredMaterials.contains(id)) continue;
            int need = cnt * multiplier;
            int have = avail.getOrDefault(item, 0) + backpackCount(item);
            if (have >= need) continue;
            if (shown.contains(item)) continue;
            if (shown.size() >= 8) { skipped++; continue; }
            shown.add(item);
            needOf.add(need);
            haveOf.add(have);
        }
        int rows = shown.size();
        int lineH = 16;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int w = 185;
        int h = 14 + Math.max(1, rows) * lineH + (skipped > 0 ? 10 : 0);
        int x = sw - w - 6;
        int y = sh - h - 6;
        ctx.fill(x - 4, y - 4, x + w + 4, y + h + 4, 0xC0000000);
        ctx.drawString(mc.font, "材料列表", x, y, 0xFF55FFFF);
        int yy = y + 13;
        if (rows == 0) {
            ctx.drawString(mc.font, "材料已备齐，无需补充", x, yy, 0xFFFFFFFF);
            return;
        }
        for (int i = 0; i < rows; i++) {
            Item item = shown.get(i);
            int need = needOf.get(i);
            String name = MaterialListHelper.getItemDisplayName(item);
            String qty = fmtHudQty(need);
            ctx.renderItem(new ItemStack(item), x, yy);
            ctx.drawString(mc.font, name, x + 18, yy + 3, 0xFFFFFFFF);
            ctx.drawString(mc.font, qty, x + w - mc.font.width(qty), yy + 3, 0xFFFFAA00);
            yy += lineH;
        }
        if (skipped > 0) {
            ctx.drawString(mc.font, "（还有 " + skipped + " 种未显示）", x, yy, 0xFFAAAAAA);
        }
    }
}
