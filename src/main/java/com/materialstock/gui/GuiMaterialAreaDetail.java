package com.materialstock.gui;

import com.materialstock.StockConfig;
import com.materialstock.scan.StockAreaScanner;
import com.materialstock.util.QuantityFormat;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class GuiMaterialAreaDetail extends GuiBase {
    private static final int ROW_H = 16;
    private static final int X0 = 20;
    private int scroll = 0;
    private GuiTextFieldGeneric searchField;
    private net.minecraft.client.gui.screens.Screen parent;

    @Override
    public void initGui() {
        super.initGui();
        this.searchField = new GuiTextFieldGeneric(X0, 8, 200, 16, Minecraft.getInstance().font);
        this.searchField.setFocused(true);
        this.addTextField(this.searchField, null);
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        this.drawString(drawContext, "材料区物品明细（Esc / 右键 返回）", X0 + 210, 12, 0xFFFFFF55);

        Map<Item, Integer> all = StockAreaScanner.summarizeMaterial();
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(all.entrySet());

        String kw = this.searchField != null ? this.searchField.getValue().toLowerCase() : "";
        if (!kw.isEmpty()) {
            List<Map.Entry<Item, Integer>> filtered = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : list) {
                String name = new ItemStack(e.getKey()).getHoverName().getString().toLowerCase();
                if (name.contains(kw)) filtered.add(e);
            }
            list = filtered;
        }

        int visible = (this.height - 50) / ROW_H;
        int maxScroll = Math.max(0, list.size() - visible);
        if (this.scroll > maxScroll) this.scroll = maxScroll;
        if (this.scroll < 0) this.scroll = 0;

        int y = 32;
        for (int i = this.scroll; i < list.size() && i < this.scroll + visible; i++) {
            Map.Entry<Item, Integer> e = list.get(i);
            drawContext.renderItem(new ItemStack(e.getKey()), X0, y);
            String name = new ItemStack(e.getKey()).getHoverName().getString();
            String fmtQ = QuantityFormat.format(e.getValue(), e.getKey());
            String rowText = name + "  × " + e.getValue() + "（" + fmtQ + "）";
            this.drawString(drawContext, rowText, X0 + 20, y + 4, 0xFFFFFFFF);
            String id = BuiltInRegistries.ITEM.getKey(e.getKey()).toString();
            String trackBtn = StockConfig.get().trackedItems.contains(id) ? "[取消追踪]" : "[追踪]";
            this.drawString(drawContext, trackBtn,
                X0 + 20 + this.font.width(rowText) + 6, y + 4, 0xFFFFFF66);
            y += ROW_H;
        }

        this.drawString(drawContext, "共 " + list.size() + " 种，显示 " + (this.scroll + 1) + "-" + Math.min(this.scroll + visible, list.size()),
            X0, this.height - 18, 0xFF88DDFF);
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        this.scroll -= (int) (verticalAmount * 1);
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            if (this.parent != null) net.minecraft.client.Minecraft.getInstance().setScreen(this.parent);
            else this.onClose();
            return true;
        }
        // 只点[追踪]文字区域才触发
        if (button == 0) {
            Map<Item, Integer> all = StockAreaScanner.summarizeMaterial();
            List<Map.Entry<Item, Integer>> list = new ArrayList<>(all.entrySet());
            String kw = this.searchField != null ? this.searchField.getValue().toLowerCase() : "";
            if (!kw.isEmpty()) {
                List<Map.Entry<Item, Integer>> filtered = new ArrayList<>();
                for (Map.Entry<Item, Integer> e : list) {
                    String name = new ItemStack(e.getKey()).getHoverName().getString().toLowerCase();
                    if (name.contains(kw)) filtered.add(e);
                }
                list = filtered;
            }
            int visible = (this.height - 50) / ROW_H;
            int y = 32;
            for (int i = this.scroll; i < list.size() && i < this.scroll + visible; i++) {
                if (mouseY >= y && mouseY < y + ROW_H) {
                    String name = new ItemStack(list.get(i).getKey()).getHoverName().getString();
                    String fmtQ = com.materialstock.util.QuantityFormat.format(list.get(i).getValue(), list.get(i).getKey());
                    String rowText = name + "  × " + list.get(i).getValue() + "（" + fmtQ + "）";
                    int trackX = X0 + 20 + this.font.width(rowText) + 6;
                    if (mouseX >= trackX && mouseX <= trackX + 60) {
                        String id = BuiltInRegistries.ITEM.getKey(list.get(i).getKey()).toString();
                        if (StockConfig.get().trackedItems.contains(id)) {
                            StockConfig.get().trackedItems.remove(id);
                        } else {
                            StockConfig.get().trackedItems.add(id);
                        }
                        com.materialstock.StockConfig.save();
                    }
                    return true;
                }
                y += ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
