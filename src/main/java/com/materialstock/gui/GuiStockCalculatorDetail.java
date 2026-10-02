package com.materialstock.gui;

import com.materialstock.StockConfig;
import com.materialstock.craft.CraftCalculator;
import com.materialstock.util.QuantityFormat;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 合成计算器「材料」明细全屏界面（点材料标题后的 [展开] 打开）。
 *
 * 与材料区的 GuiMaterialAreaDetail 交互一致：搜索框过滤、行尾 [追踪]/[取消追踪]、右键返回。
 * 数据由计算器传入（材料 id → 总数），这样离开计算器后仍看到打开瞬间的快照，
 * 不会因为计算器重算而跳动。
 */
public class GuiStockCalculatorDetail extends GuiBase {
    private static final int ROW_H = 16;
    private static final int X0 = 20;

    private final List<String> ids = new ArrayList<>();
    private final List<Double> counts = new ArrayList<>();
    private int scroll = 0;
    private GuiTextFieldGeneric searchField;
    private final net.minecraft.client.gui.screens.Screen parent;

    /**
     * @param materialIds  材料规范 id（药水会带 "#效果"，用 CraftCalculator.parseVariant 还原）
     * @param totals       与 ids 一一对应的总数（已乘倍数）
     * @param parent       返回目标（通常是计算器界面）
     */
    public GuiStockCalculatorDetail(List<String> materialIds, List<Double> totals,
                                    net.minecraft.client.gui.screens.Screen parent) {
        this.parent = parent;
        if (materialIds != null) {
            for (int i = 0; i < materialIds.size(); i++) {
                Double t = (totals != null && i < totals.size()) ? totals.get(i) : null;
                if (t == null || t <= 0) continue;
                this.ids.add(materialIds.get(i));
                this.counts.add(t);
            }
        }
    }

    @Override
    public void initGui() {
        super.initGui();
        this.searchField = new GuiTextFieldGeneric(X0, 8, 200, 16, Minecraft.getInstance().font);
        this.searchField.setFocused(true);
        this.addTextField(this.searchField, null);
    }

    /** 把规范 id 还原成物品；药水走 parseVariant，否则 "#效果" 会让 tryParse 解析失败 */
    private static Item itemOf(String id) {
        CraftCalculator.Variant v = CraftCalculator.parseVariant(id);
        return v == null ? null : v.item;
    }

    private static String nameOf(String id) {
        CraftCalculator.Variant v = CraftCalculator.parseVariant(id);
        if (v == null) return "?" + id;
        if (v.variant != null) {
            return com.materialstock.craft.BrewingRecipes.displayName(v.item, v.variant);
        }
        return new ItemStack(v.item).getHoverName().getString();
    }

    /** 命中的行下标（搜索过滤后） */
    private List<Integer> visibleRows() {
        String kw = this.searchField != null ? this.searchField.getValue().toLowerCase() : "";
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < this.ids.size(); i++) {
            if (kw.isEmpty() || nameOf(this.ids.get(i)).toLowerCase().contains(kw)
                    || this.ids.get(i).toLowerCase().contains(kw)) {
                out.add(i);
            }
        }
        return out;
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        this.drawString(drawContext, "计算器材料明细（Esc / 右键 返回）", X0 + 210, 12, 0xFFFFFF55);

        List<Integer> list = this.visibleRows();
        int visible = Math.max(1, (this.height - 50) / ROW_H);
        int maxScroll = Math.max(0, list.size() - visible);
        if (this.scroll > maxScroll) this.scroll = maxScroll;
        if (this.scroll < 0) this.scroll = 0;

        int y = 32;
        for (int k = this.scroll; k < list.size() && k < this.scroll + visible; k++) {
            int i = list.get(k);
            String id = this.ids.get(i);
            double qty = this.counts.get(i);
            Item item = itemOf(id);

            if (item != null) {
                ItemStack st = com.materialstock.craft.BrewingRecipes.variantStack(id);
                if (st.isEmpty()) st = new ItemStack(item);
                drawContext.renderItem(st, X0, y);
            }
            String name = nameOf(id);
            int stackSize = item != null ? Math.max(1, new ItemStack(item).getMaxStackSize()) : 64;
            String rowText = name + "  × " + QuantityFormat.format(qty, stackSize);
            this.drawString(drawContext, rowText, X0 + 20, y + 4, 0xFFFFFFFF);

            String trackBtn = StockConfig.get().trackedItems.contains(id) ? "[取消追踪]" : "[追踪]";
            this.drawString(drawContext, trackBtn,
                X0 + 20 + this.font.width(rowText) + 6, y + 4, 0xFFFFFF66);
            y += ROW_H;
        }

        if (list.isEmpty()) {
            this.drawString(drawContext, this.ids.isEmpty()
                    ? "当前没有材料。先在计算器里添加成品。"
                    : "没有匹配的材料。", X0, 32, 0xFF888888);
        }
        this.drawString(drawContext, "共 " + list.size() + " 种，显示 " + (list.isEmpty() ? 0 : this.scroll + 1)
            + "-" + Math.min(this.scroll + visible, list.size()), X0, this.height - 18, 0xFF88DDFF);
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        this.scroll -= (int) verticalAmount;
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            if (this.parent != null) Minecraft.getInstance().setScreen(this.parent);
            else this.onClose();
            return true;
        }
        if (button == 0) {
            List<Integer> list = this.visibleRows();
            int visible = Math.max(1, (this.height - 50) / ROW_H);
            int y = 32;
            for (int k = this.scroll; k < list.size() && k < this.scroll + visible; k++) {
                if (mouseY >= y && mouseY < y + ROW_H) {
                    int i = list.get(k);
                    String id = this.ids.get(i);
                    double qty = this.counts.get(i);
                    Item item = itemOf(id);
                    int stackSize = item != null ? Math.max(1, new ItemStack(item).getMaxStackSize()) : 64;
                    String rowText = nameOf(id) + "  × " + QuantityFormat.format(qty, stackSize);
                    int trackX = X0 + 20 + this.font.width(rowText) + 6;
                    if (mouseX >= trackX && mouseX <= trackX + 64) {
                        // 用规范 id 存取，药水才能按效果分别追踪
                        if (StockConfig.get().trackedItems.contains(id)) {
                            StockConfig.get().trackedItems.remove(id);
                        } else {
                            StockConfig.get().trackedItems.add(id);
                        }
                        StockConfig.save();
                    }
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
