package com.materialstock.gui;

import com.materialstock.StockConfig;
import com.materialstock.litematica.CategoryData;
import com.materialstock.litematica.CategoryHelper;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 分类管理页：
 * 展示已有大类/小类；搜索物品后点击分类将其放入（覆盖分类表默认归属，持久化）；
 * 支持新建/删除大类与小类。
 */
public class GuiCategoryManager extends GuiBase {
    private static final int ROW_H = 14;
    private static final int TREE_X = 10;
    private static final int TREE_W = 330;
    private static final int LIST_Y0 = 116;

    private GuiTextFieldGeneric searchField;
    private GuiTextFieldGeneric newCatField;
    private GuiTextFieldGeneric newSubField;

    private final List<Item> results = new ArrayList<>();
    private final List<Item> allItems = new ArrayList<>();
    private int scroll = 0;
    private int treeScroll = 0;
    private String lastQuery = null;

    // 分类树：行 = [type, cat, sub]（type 0 大类 / 1 小类）
    private final List<Object[]> treeRows = new ArrayList<>();
    private final List<String> catOrder = new ArrayList<>();
    private final Map<String, List<String>> subs = new LinkedHashMap<>();
    private final Set<String> expanded = new HashSet<>();

    private String selectedItemId = null;
    private String selectedCat = null;
    private String selectedSub = null;
    private String status = "点选物品后点击左侧分类将其放入；也可新建/删除大类与小类";

    public GuiCategoryManager() {
        this.setTitle("材料备货助手 - 分类管理");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearWidgets();
        this.addLabel(10, 22, this.getScreenWidth() - 20, 16, 0xFFAAAAAA,
            "搜索物品 → 点击左侧大类（散货）或小类放入；新建/删除大类与小类");

        this.searchField = new GuiTextFieldGeneric(10, 42, Math.max(120, Math.min(280, this.getScreenWidth() - 300)), 16, Minecraft.getInstance().font);
        this.addTextField(this.searchField, null);
        ButtonGeneric backBtn = new ButtonGeneric(this.getScreenWidth() - 90, 42, 80, 16, "← 返回");
        backBtn.setActionListener((b, mb) -> GuiBase.openGui(new GuiStockMain()));
        this.addWidget(backBtn);

        this.newCatField = new GuiTextFieldGeneric(10, 62, 150, 16, Minecraft.getInstance().font);
        this.addTextField(this.newCatField, null);
        ButtonGeneric newCatBtn = new ButtonGeneric(164, 62, 76, 16, "新建大类");
        newCatBtn.setActionListener((b, mb) -> this.createCategory());
        this.addWidget(newCatBtn);
        ButtonGeneric delCatBtn = new ButtonGeneric(244, 62, 76, 16, "删除大类");
        delCatBtn.setActionListener((b, mb) -> this.deleteCategory());
        this.addWidget(delCatBtn);

        this.newSubField = new GuiTextFieldGeneric(10, 82, 150, 16, Minecraft.getInstance().font);
        this.addTextField(this.newSubField, null);
        ButtonGeneric newSubBtn = new ButtonGeneric(164, 82, 76, 16, "新建小类");
        newSubBtn.setActionListener((b, mb) -> this.createSub());
        this.addWidget(newSubBtn);
        ButtonGeneric delSubBtn = new ButtonGeneric(244, 82, 76, 16, "删除小类");
        delSubBtn.setActionListener((b, mb) -> this.deleteSub());
        this.addWidget(delSubBtn);

        this.refreshTree();
        this.allItems.clear();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == net.minecraft.world.item.Items.AIR) continue;
            this.allItems.add(item);
        }
        this.allItems.sort(Comparator.comparing(it -> BuiltInRegistries.ITEM.getKey(it).getPath()));
    }

    private void refreshTree() {
        StockConfig cfg = StockConfig.get();
        this.catOrder.clear();
        for (CategoryHelper.Category c : CategoryHelper.CATEGORIES) {
            if (!cfg.removedCategories.contains(c.name)) this.catOrder.add(c.name);
        }
        this.catOrder.addAll(cfg.extraCategories);
        this.subs.clear();
        for (String cat : this.catOrder) {
            List<String> list = new ArrayList<>();
            for (String sub : CategoryData.subsOf(cat)) {
                if (!cfg.removedSubs.containsKey(cat) || !cfg.removedSubs.get(cat).contains(sub)) {
                    list.add(sub);
                }
            }
            List<String> extra = cfg.extraSubs.get(cat);
            if (extra != null) list.addAll(extra);
            this.subs.put(cat, list);
        }
        if (this.selectedCat != null && !this.catOrder.contains(this.selectedCat)) {
            this.selectedCat = null;
            this.selectedSub = null;
        }
        if (this.selectedSub != null && !this.subs.getOrDefault(this.selectedCat, List.of()).contains(this.selectedSub)) {
            this.selectedSub = null;
        }
        this.rebuildTreeRows();
    }

    private void rebuildTreeRows() {
        this.treeRows.clear();
        for (String cat : this.catOrder) {
            this.treeRows.add(new Object[]{0, cat, null});
            if (this.expanded.contains(cat)) {
                for (String sub : this.subs.getOrDefault(cat, List.of())) {
                    this.treeRows.add(new Object[]{1, cat, sub});
                }
            }
        }
    }

    private void doSearch() {
        this.results.clear();
        this.scroll = 0;
        String query = this.searchField == null ? "" : this.searchField.getTextWrapper().trim();
        this.lastQuery = query;
        String q = query.toLowerCase();
        for (Item item : this.allItems) {
            if (this.results.size() >= 120) break;
            if (q.isEmpty()) {
                this.results.add(item);
                continue;
            }
            String name = new ItemStack(item).getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (name.toLowerCase().contains(q) || id.toLowerCase().contains(q)
                || com.materialstock.util.PinyinUtil.toFull(name).contains(q)
                || com.materialstock.util.PinyinUtil.toInitials(name).contains(q)) {
                this.results.add(item);
            }
        }
    }

    // ---------- 操作 ----------

    private void createCategory() {
        String name = this.newCatField == null ? "" : this.newCatField.getTextWrapper().trim();
        if (name.isEmpty()) {
            this.status = "请输入要新建的大类名称";
            return;
        }
        if (this.catOrder.contains(name)) {
            this.status = "大类已存在：" + name;
            return;
        }
        StockConfig cfg = StockConfig.get();
        cfg.extraCategories.add(name);
        this.refreshTree();
        this.selectedCat = name;
        this.selectedSub = null;
        StockConfig.save();
        this.status = "已新建大类：" + name + "（选中物品后点击它即可放入）";
    }

    private void createSub() {
        if (this.selectedCat == null) {
            this.status = "请先在左侧点击一个大类，再新建小类";
            return;
        }
        String name = this.newSubField == null ? "" : this.newSubField.getTextWrapper().trim();
        if (name.isEmpty()) {
            this.status = "请输入要新建的小类名称";
            return;
        }
        if (this.subs.getOrDefault(this.selectedCat, List.of()).contains(name)) {
            this.status = "小类已存在：" + this.selectedCat + " / " + name;
            return;
        }
        StockConfig cfg = StockConfig.get();
        cfg.extraSubs.computeIfAbsent(this.selectedCat, k -> new ArrayList<>()).add(name);
        this.refreshTree();
        this.expanded.add(this.selectedCat);
        this.rebuildTreeRows();
        this.selectedSub = name;
        StockConfig.save();
        this.status = "已新建小类：" + this.selectedCat + " / " + name;
    }

    /** 兜底大类名：无归属物品都会被归到这里，因此不允许删除（否则会涌现树中不存在的分组） */
    private static final String FALLBACK_CATEGORY = "其他";

    private void deleteCategory() {
        if (this.selectedCat == null) {
            this.status = "请先在左侧点击要删除的大类（再点删除大类）";
            return;
        }
        // "其他"是兜底大类：删掉它之后无归属物品仍会被归入"其他"，
        // 于是材料表里会出现一个树中不存在的分组，用户无法浏览/批量处理。直接禁止删除。
        if (FALLBACK_CATEGORY.equals(this.selectedCat)) {
            this.status = "「其他」是兜底大类（未归类物品都归于此），不能删除";
            return;
        }
        StockConfig cfg = StockConfig.get();
        // 表内大类 -> 标记删除；新建大类 -> 直接移除
        String cat = this.selectedCat;
        if (CategoryData.CATEGORY_ORDER.contains(cat)) {
            if (!cfg.removedCategories.contains(cat)) cfg.removedCategories.add(cat);
        } else {
            cfg.extraCategories.remove(cat);
        }
        cfg.extraSubs.remove(cat);
        cfg.removedSubs.remove(cat);
        // 归属该大类的物品 -> 归"其他"
        cfg.categoryOverrides.entrySet().removeIf(e -> !e.getValue().isEmpty() && e.getValue().get(0).equals(cat));
        // 必须在置 null 之前清展开状态，否则等价于 remove(null)，展开状态会残留
        this.expanded.remove(cat);
        this.selectedCat = null;
        this.selectedSub = null;
        this.refreshTree();
        StockConfig.save();
        this.status = "已删除大类（其下物品归入其他）";
    }

    private void deleteSub() {
        if (this.selectedCat == null || this.selectedSub == null) {
            this.status = "请先在左侧点击要删除的小类（再点删除小类）";
            return;
        }
        StockConfig cfg = StockConfig.get();
        if (CategoryData.subsOf(this.selectedCat).contains(this.selectedSub)) {
            cfg.removedSubs.computeIfAbsent(this.selectedCat, k -> new ArrayList<>()).add(this.selectedSub);
        } else {
            List<String> extra = cfg.extraSubs.get(this.selectedCat);
            if (extra != null) extra.remove(this.selectedSub);
        }
        // 归属该小类的物品 -> 变该大类散货（小类清空）
        cfg.categoryOverrides.entrySet().removeIf(e -> e.getValue().size() > 1
            && e.getValue().get(1) != null && e.getValue().get(1).equals(this.selectedSub));
        this.selectedSub = null;
        this.refreshTree();
        StockConfig.save();
        this.status = "已删除小类（其下物品变为" + this.selectedCat + "散货）";
    }

    /** 物品当前小类（override 优先；被删除小类视为散货 null） */
    private String currentSub(Item item, String cat) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        StockConfig cfg = StockConfig.get();
        List<String> ov = cfg.categoryOverrides.get(path);
        if (ov != null) {
            String sub = ov.size() > 1 ? ov.get(1) : null;
            if (sub != null && !ov.isEmpty() && cfg.removedSubs.containsKey(ov.get(0))
                    && cfg.removedSubs.get(ov.get(0)).contains(sub)) {
                return null;
            }
            return sub;
        }
        String sub2 = CategoryData.sub(path);
        if (sub2 != null && cfg.removedSubs.containsKey(cat) && cfg.removedSubs.get(cat).contains(sub2)) {
            return null;
        }
        return sub2;
    }

    /** 物品当前归属描述：大类 / 小类 或 大类（散货） */
    private String currentOf(Item item) {
        String cat = CategoryHelper.getCategory(item);
        String sub = this.currentSub(item, cat);
        return sub != null ? cat + " / " + sub : cat + "（散货）";
    }

    private void assignItem(String itemId, String cat, String sub) {
        if (itemId == null || cat == null) return;
        StockConfig cfg = StockConfig.get();
        List<String> ov = new ArrayList<>();
        ov.add(cat);
        if (sub != null) ov.add(sub);
        cfg.categoryOverrides.put(itemId, ov);
        StockConfig.save();
        String nm = net.minecraft.world.item.ItemStack.EMPTY.getDisplayName().getString();
        Item it = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(itemId));
        if (it != null && it != net.minecraft.world.item.Items.AIR) {
            nm = new ItemStack(it).getHoverName().getString();
        }
        this.status = "已放入：" + nm + " → " + cat + (sub != null ? " / " + sub : "（散货）");
    }

    // ---------- 渲染 ----------

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        String query = this.searchField == null ? "" : this.searchField.getTextWrapper().trim();
        if (!query.equals(this.lastQuery)) this.doSearch();

        int bottom = this.getScreenHeight() - 20;
        int right = this.getScreenWidth() - 10;

        // ---- 分类树（左） ----
        this.drawString(drawContext, "分类（点击大类展开/收起并选中；点击小类选中）", TREE_X, LIST_Y0 - 12, 0xFF55FFFF);
        int visibleTree = Math.max(1, (bottom - LIST_Y0) / ROW_H);
        int maxTreeScroll = Math.max(0, this.treeRows.size() - visibleTree);
        if (this.treeScroll > maxTreeScroll) this.treeScroll = maxTreeScroll;
        if (this.treeScroll < 0) this.treeScroll = 0;
        int ty = LIST_Y0;
        for (int i = this.treeScroll; i < this.treeRows.size() && i < this.treeScroll + visibleTree + 1; i++) {
            if (ty > bottom) break;
            Object[] row = this.treeRows.get(i);
            boolean isCat = (Integer) row[0] == 0;
            String cat = (String) row[1];
            String sub = (String) row[2];
            String text;
            int color;
            if (isCat) {
                boolean open = this.expanded.contains(cat);
                text = (open ? "▼ " : "▶ ") + cat;
                color = this.selectedCat != null && this.selectedCat.equals(cat) && this.selectedSub == null
                    ? 0xFFFFFF55 : 0xFF88FF88;
            } else {
                text = "    " + sub;
                color = this.selectedCat != null && this.selectedCat.equals(cat)
                    && this.selectedSub != null && this.selectedSub.equals(sub)
                    ? 0xFFFFFF55 : 0xFFFFFFFF;
            }
            this.drawString(drawContext, text, TREE_X + (isCat ? 0 : 10), ty + 3, color);
            // 行内 [删] 按钮
            this.drawString(drawContext, "[删]", TREE_X + TREE_W - 34, ty + 3, 0xFFFF5555);
            ty += ROW_H;
        }

        // ---- 搜索结果（右） ----
        int listX = TREE_X + TREE_W + 16;
        int listW = right - listX;
        this.drawString(drawContext, "物品（搜索 中文/拼音/ID；点击选中，再点左侧分类放入；共 " + this.results.size() + " 个）", listX, LIST_Y0 - 12, 0xFF55FFFF);
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
            boolean sel = this.selectedItemId != null && this.selectedItemId.equals(id);
            if (sel) {
                drawContext.fill(listX - 2, y, right, y + ROW_H, 0x30FFFFFF);
            }
            drawContext.renderItem(new ItemStack(item), listX, y + 1);
            this.drawString(drawContext, name + "  §7" + id, listX + 18, y + 3, sel ? 0xFFFFFF55 : 0xFFFFFFFF);
            y += ROW_H;
        }

        // ---- 状态 ----
        this.drawString(drawContext, this.status, 10, bottom, 0xFF55FFFF);
    }

    // ---------- 交互 ----------

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) return false;
        int bottom = this.getScreenHeight() - 20;
        int right = this.getScreenWidth() - 10;

        // 分类树区
        if (mouseX >= TREE_X && mouseX <= TREE_X + TREE_W && mouseY >= LIST_Y0 && mouseY <= bottom) {
            int idx = this.treeScroll + (mouseY - LIST_Y0) / ROW_H;
            if (idx < 0 || idx >= this.treeRows.size()) return false;
            Object[] row = this.treeRows.get(idx);
            boolean isCat = (Integer) row[0] == 0;
            String cat = (String) row[1];
            String sub = (String) row[2];
            boolean onDel = mouseX >= TREE_X + TREE_W - 44 && mouseX <= TREE_X + TREE_W;
            if (onDel) {
                if (isCat) {
                    this.selectedCat = cat;
                    this.selectedSub = null;
                    this.deleteCategory();
                } else {
                    this.selectedCat = cat;
                    this.selectedSub = sub;
                    this.deleteSub();
                }
                return true;
            }
            if (isCat) {
                if (this.expanded.contains(cat)) this.expanded.remove(cat);
                else this.expanded.add(cat);
                this.rebuildTreeRows();
                this.selectedCat = cat;
                this.selectedSub = null;
                // 已选中物品 -> 放入大类散货
                if (this.selectedItemId != null) {
                    this.assignItem(this.selectedItemId, cat, null);
                }
            } else {
                this.selectedCat = cat;
                this.selectedSub = sub;
                if (this.selectedItemId != null) {
                    this.assignItem(this.selectedItemId, cat, sub);
                }
            }
            return true;
        }

        // 物品列表区
        if (mouseX >= TREE_X + TREE_W + 16 && mouseX <= right && mouseY >= LIST_Y0 && mouseY <= bottom) {
            int idx = this.scroll + (mouseY - LIST_Y0) / ROW_H;
            if (idx >= 0 && idx < this.results.size()) {
                Item item = this.results.get(idx);
                this.selectedItemId = BuiltInRegistries.ITEM.getKey(item).toString();
                this.status = "已选中：" + new ItemStack(item).getHoverName().getString()
                    + "（原在：" + this.currentOf(item) + "；点击左侧分类放入）";
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
        if (mouseX >= TREE_X && mouseX <= TREE_X + TREE_W) {
            this.treeScroll -= (int) (verticalAmount * 2);
        } else {
            this.scroll -= (int) (verticalAmount * 2);
        }
        return true;
    }
}
