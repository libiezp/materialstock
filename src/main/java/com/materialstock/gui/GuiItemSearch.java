package com.materialstock.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品搜索页：按中文名或注册 ID 搜索物品，点击后回到合成计算器并填入。
 */
public class GuiItemSearch extends GuiBase {
    private static final int LIST_Y0 = 66;
    private static final int ROW_H = 18;

    /** 点选后回填到计算器的物品 ID */
    public static String pickedItemId = "";

    private GuiTextFieldGeneric searchField;
    private final List<Item> results = new ArrayList<>();
    private final List<Item> allItems = new ArrayList<>();
    private int scroll = 0;
    private String lastQuery = null;

    public GuiItemSearch() {
        this.setTitle("材料备货助手 - 搜索物品");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearWidgets();

        this.addLabel(10, 22, this.getScreenWidth() - 20, 16, 0xFFAAAAAA,
            "输入物品中文名或ID（如：火把 / torch / minecraft:torch）后按回车，点击物品行选择");

        this.searchField = new GuiTextFieldGeneric(10, 42, Math.max(100, Math.min(260, this.getScreenWidth() - 20)), 16, Minecraft.getInstance().font);
        this.addTextField(this.searchField, null);

        this.allItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            this.allItems.add(item);
        }
        this.allItems.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath()));
    }

    private void doSearch() {
        this.results.clear();
        this.scroll = 0;
        String query = this.searchField == null ? "" : this.searchField.getTextWrapper().trim();
        this.lastQuery = query;
        for (Item item : this.allItems) {
            if (this.results.size() >= 120) break;
            if (item == net.minecraft.world.item.Items.AIR) continue;
            if (query.isEmpty()) {
                this.results.add(item);
                continue;
            }
            String name = new ItemStack(item).getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String q = query.toLowerCase();
            if (name.toLowerCase().contains(q) || id.toLowerCase().contains(q)) {
                this.results.add(item);
            }
        }
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        String query = this.searchField == null ? "" : this.searchField.getTextWrapper().trim();
        if (!query.equals(this.lastQuery)) {
            this.doSearch();
        }
        if (this.results.isEmpty() && this.allItems.isEmpty()) {
            this.allItems.clear();
            for (Item item : BuiltInRegistries.ITEM) {
                this.allItems.add(item);
            }
            this.allItems.sort(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).getPath()));
            this.doSearch();
        }

        int bottom = this.getScreenHeight() - 20;
        this.drawString(drawContext, "共 " + this.results.size() + " 个结果（点击选择；滚轮滚动）", 10, bottom, 0xFF55FFFF);

        int visible = Math.max(1, (bottom - LIST_Y0) / ROW_H);
        int maxScroll = Math.max(0, this.results.size() - visible);
        if (this.scroll > maxScroll) this.scroll = maxScroll;
        if (this.scroll < 0) this.scroll = 0;

        int y = LIST_Y0;
        for (int i = this.scroll; i < this.results.size() && i < this.scroll + visible + 1; i++) {
            if (y > bottom) break;
            Item item = this.results.get(i);
            String name = new ItemStack(item).getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            drawContext.renderItem(new ItemStack(item), 12, y + 1);
            this.drawString(drawContext, name + "  §7" + id, 30, y + 4, 0xFFFFFFFF);
            y += ROW_H;
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton == 0 && mouseX >= 10 && mouseY >= LIST_Y0 && mouseY <= this.getScreenHeight() - 20) {
            int idx = this.scroll + (mouseY - LIST_Y0) / ROW_H;
            if (idx >= 0 && idx < this.results.size()) {
                Item item = this.results.get(idx);
                pickedItemId = BuiltInRegistries.ITEM.getKey(item).toString();
                GuiBase.openGui(new GuiStockCalculator());
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        this.scroll -= (int) (verticalAmount * 3);
        return true;
    }
}
