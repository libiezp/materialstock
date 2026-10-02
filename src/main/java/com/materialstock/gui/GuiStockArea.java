package com.materialstock.gui;

import com.materialstock.StockConfig;
import com.materialstock.data.StockArea;
import com.materialstock.scan.ContainerCache;
import com.materialstock.scan.SelectionManager;
import com.materialstock.scan.StockAreaScanner;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;

/**
 * 备货区页：框选两点保存备货区；扫描后物品放入容器即可在材料清单中显示数量。
 */
public class GuiStockArea extends GuiBase {
    private static final int TAB_Y = 22;
    private static final int TAB_H = 16;
    private static final int LIST_Y0 = 100;
    private static final int LIST_H = 18;
    /** 备货区列表固定显示行数（不占用下方物品明细区域，避免重叠） */
    private static final int MAX_LIST_ROWS = 3;
    /** 物品明细标题行：位于备货区列表（含滚动提示行）之下，避免与列表/提示重叠 */
    private static final int DETAIL_TITLE = LIST_Y0 + MAX_LIST_ROWS * LIST_H + 18;
    /** 物品明细首行：标题之下再让出一行 */
    private static final int DETAIL_ROW0 = DETAIL_TITLE + 16;

    private GuiTextFieldGeneric nameField;
    private String status = "";
    private int itemScroll = 0;
    /** 顶部四个页签按钮（用于绘制鼠标悬停提示） */
    private final java.util.List<ButtonGeneric> tabButtons = new java.util.ArrayList<>();
    /** 备货区列表滚动偏移（超过 3 行时滚轮翻页） */
    private int areaScroll = 0;
    /** 正在改名的备货区原名；null 表示不在改名模式 */
    private String renameTarget = null;
    private Integer editCoordIdx = null;
    private boolean editSelCoord = false;
    private GuiTextFieldGeneric[] selCoordFields = new GuiTextFieldGeneric[6];
    private GuiTextFieldGeneric[] coordFields = new GuiTextFieldGeneric[6];
    /** 行内改名输入框（仅在改名模式下创建） */
    private GuiTextFieldGeneric renameField = null;

    /** 按钮滚轮拦截：malilib 会把按钮上的滚轮当点击，这里先拦截 */
    private final ButtonScrollGuard scrollGuard = new ButtonScrollGuard();

    public GuiStockArea() {
        this.setTitle("材料备货助手 - 备货区");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearWidgets();
        this.tabButtons.clear();   // initGui 会被反复调用，不清会无限累加
        this.scrollGuard.clear();
        StockConfig config = StockConfig.get();
        int mid = this.getScreenWidth() / 2;

        // ---- 顶部 tab（宽度随屏幕自适应，GUI 缩放大时不溢出屏幕）----
        int tabW = Math.max(64, Math.min(100, (this.getScreenWidth() - 44) / 4));
        this.addTab(mid - 2 * tabW - 12, TAB_Y, tabW, "材料清单", new GuiStockMain());
        this.addTab(mid - tabW - 4, TAB_Y, tabW, "合成计算器", new GuiStockCalculator());
        this.addTab(mid + 4, TAB_Y, tabW, "备货区", new GuiStockArea());
        this.addTab(mid + tabW + 12, TAB_Y, tabW, "材料区", new GuiMaterialArea());

        SelectionManager selM = SelectionManager.getInstance();
        int sw = this.getScreenWidth();

        // ---- 控制行1：框选提示 ----
        // 文本含键名，改到 drawContents 每帧实时绘制（addLabel 的文本是创建时的快照，改键后不会更新）

        // ---- 控制行2：保存/清除选区/扫描/清除全部（四按钮同一排，宽度随屏幕自适应）----
        this.addLabel(10, 64, 56, 16, 0xFFAAAAAA, "区域名称:");
        int nameW = Math.max(80, Math.min(140, sw - 412));
        this.nameField = new GuiTextFieldGeneric(66, 62, nameW, 16, Minecraft.getInstance().font);
        // 顶部输入框只负责新建名称，改名输入走行内输入框，不再抢焦点
        this.nameField.setTextWrapper("备货区" + (config.stockAreas.size() + 1));
        this.addTextField(this.nameField, null);
        int cx = 66 + nameW + 4;

        ButtonGeneric saveBtn = new ButtonGeneric(cx, 62, 80, 16, "保存为备货区");
        saveBtn.setActionListener((b, mb) -> this.saveSelection());
        this.addBtn(saveBtn);
        cx += 84;

        // 清除选区：紧跟“保存为备货区”之后（格式与其他按钮一致）
        ButtonGeneric clearSel = new ButtonGeneric(cx, 62, 60, 16, "清除选区");
        clearSel.setActionListener((b, mb) -> {
            SelectionManager.getInstance().clear();
            this.status = "已清除选区";
            this.initGui();
        });
        this.addBtn(clearSel);
        cx += 64;

        ButtonGeneric scanBtn = new ButtonGeneric(cx, 62, 80, 16, "扫描备货区");
        scanBtn.setActionListener((b, mb) -> {
            this.status = "正在扫描备货区…";
            StockAreaScanner.scanAll(() -> {
                this.status = "备货区扫描完成：缓存 " + ContainerCache.getInstance().size() + " 个容器，物品已并入材料清单统计";
                this.initGui();
            });
        });
        this.addBtn(scanBtn);
        cx += 84;

        ButtonGeneric clearAll = new ButtonGeneric(cx, 62, 80, 16, "清除全部");
        clearAll.setActionListener((b, mb) -> {
            config.stockAreas.removeIf(a -> !config.lockedStockAreas.contains(a.name));
            ContainerCache.getInstance().clear();
            config.containerCache = new java.util.LinkedHashMap<>();
            com.materialstock.scan.StockAreaScanner.materialContainerContents.clear();
            StockConfig.save();
            this.status = "已清除全部备货区与缓存";
            this.initGui();
        });
        this.addBtn(clearAll);

        // ---- 选区状态 ----
        // 选区状态在 drawContents 中显示

        // ---- 备货区列表（固定显示 3 行，超过时滚轮翻页；避免与下方物品明细重叠）----
        int areaCount = config.stockAreas.size();
        int maxAreaScroll = Math.max(0, areaCount - MAX_LIST_ROWS);
        if (this.areaScroll > maxAreaScroll) this.areaScroll = maxAreaScroll;
        if (this.areaScroll < 0) this.areaScroll = 0;
        int y = LIST_Y0;
        for (int i = this.areaScroll; i < areaCount && i < this.areaScroll + MAX_LIST_ROWS; i++) {
            StockArea area = config.stockAreas.get(i);
            int idx = i;   // lambda 只能捕获 effectively final，循环变量 i 不行；此处声明需早于所有 lambda
            String text = (i + 1) + ". " + area.name + "  (" + fmt(area.getMin()) + ")~(" + fmt(area.getMax()) + ")";
            boolean isRenameTarget = this.renameTarget != null && area.name.equals(this.renameTarget);
            boolean isEditCoord = this.editCoordIdx != null && this.editCoordIdx == i;
            int textRight = (isRenameTarget || isEditCoord) ? this.getScreenWidth() - 280 : this.getScreenWidth() - 170;
            if (isEditCoord) {
                // 编辑坐标模式：显示 6 个输入框
                StockArea a = config.stockAreas.get(idx);
                int[] vals = {a.x1, a.y1, a.z1, a.x2, a.y2, a.z2};
                for (int j = 0; j < 6; j++) {
                    coordFields[j] = new GuiTextFieldGeneric(10 + j * 62, y - 2, 56, 16, Minecraft.getInstance().font);
                    coordFields[j].setTextWrapper(String.valueOf(vals[j]));
                    this.addTextField(coordFields[j], null);
                }
                ButtonGeneric cancelCoord = new ButtonGeneric(10 + 6 * 62 + 48, y - 2, 40, 16, "取消");
                cancelCoord.setActionListener((b, mb) -> {
                    this.editCoordIdx = null;
                    this.initGui();
                });
                this.addBtn(cancelCoord);
                // 确定（放在原"坐标(改)"按钮位置，与上方 取消 对齐）
                // 坐标必须归一化：逆序输入（如 x1 > x2）会让 StockArea.contains 恒为 false，扫描不到任何物品
                ButtonGeneric confirmCoord = new ButtonGeneric(this.getScreenWidth() - 216, y - 2, 60, 16, "确定");
                confirmCoord.setActionListener((b, mb) -> {
                    try {
                        StockArea area2 = config.stockAreas.get(idx);
                        int nx1 = Integer.parseInt(coordFields[0].getValue().trim());
                        int ny1 = Integer.parseInt(coordFields[1].getValue().trim());
                        int nz1 = Integer.parseInt(coordFields[2].getValue().trim());
                        int nx2 = Integer.parseInt(coordFields[3].getValue().trim());
                        int ny2 = Integer.parseInt(coordFields[4].getValue().trim());
                        int nz2 = Integer.parseInt(coordFields[5].getValue().trim());
                        area2.x1 = Math.min(nx1, nx2);
                        area2.x2 = Math.max(nx1, nx2);
                        area2.y1 = Math.min(ny1, ny2);
                        area2.y2 = Math.max(ny1, ny2);
                        area2.z1 = Math.min(nz1, nz2);
                        area2.z2 = Math.max(nz1, nz2);
                        StockConfig.save();
                        this.status = "坐标已更新";
                    } catch (Exception ex) {
                        this.status = "坐标格式错误";
                    }
                    this.editCoordIdx = null;
                    this.initGui();
                });
                this.addBtn(confirmCoord);
            } else {
                this.addLabel(10, y, textRight - 10, LIST_H, 0xFF88FF88, text);
                // 点击坐标文字进入编辑
                // （简单处理：在文字区域加个透明按钮）
            }
            if (isRenameTarget) {
                // 行内输入框：填入当前名字并聚焦
                this.renameField = new GuiTextFieldGeneric(this.getScreenWidth() - 276, y - 2, 56, 16, Minecraft.getInstance().font);
                this.renameField.setTextWrapper(this.renameTarget);
                this.renameField.setFocused(true);
                this.addTextField(this.renameField, null);
                // 取消
                ButtonGeneric cancelBtn = new ButtonGeneric(this.getScreenWidth() - 216, y - 2, 60, 16, "取消");
                cancelBtn.setActionListener((b, mb) -> {
                    this.renameTarget = null;
                    this.renameField = null;
                    this.status = "已取消改名";
                    this.initGui();
                });
                this.addBtn(cancelBtn);
                // 确定（原“改名”按钮位置）
                ButtonGeneric confirmBtn = new ButtonGeneric(this.getScreenWidth() - 152, y - 2, 60, 16, "确定");
                confirmBtn.setActionListener((b, mb) -> this.applyRename());
                this.addBtn(confirmBtn);
            } else {
                if (!isEditCoord) {
                String lockLabel = config.lockedStockAreas.contains(area.name) ? "[解锁]" : "[锁定]";
                ButtonGeneric lockBtn = new ButtonGeneric(this.getScreenWidth() - 278, y - 2, 56, 16, lockLabel);
                lockBtn.setActionListener((b, mb) -> {
                    if (config.lockedStockAreas.contains(area.name)) config.lockedStockAreas.remove(area.name);
                    else config.lockedStockAreas.add(area.name);
                    com.materialstock.StockConfig.save();
                    this.status = config.lockedStockAreas.contains(area.name) ? "已锁定（清除全部时保留）" : "已解锁";
                    this.initGui();
                });
                this.addBtn(lockBtn);
                }
                if (this.editCoordIdx == null) {
                ButtonGeneric coordBtn = new ButtonGeneric(this.getScreenWidth() - 216, y - 2, 60, 16, "坐标(改)");
                coordBtn.setActionListener((b, mb) -> {
                    if (idx >= 0 && idx < config.stockAreas.size()) {
                        this.editCoordIdx = idx;
                        this.status = "编辑坐标：修改 x1/y1/z1/x2/y2/z2 后点确定";
                        this.initGui();
                    }
                });
                this.addBtn(coordBtn);
                }
                ButtonGeneric renameBtn = new ButtonGeneric(this.getScreenWidth() - 152, y - 2, 60, 16, "改名");
                renameBtn.setActionListener((b, mb) -> {
                    if (idx >= 0 && idx < config.stockAreas.size()) {
                        this.renameTarget = config.stockAreas.get(idx).name;
                        this.status = "正在改名：" + this.renameTarget + "，输入新名字后点“确定”";
                        this.initGui();
                    }
                });
                this.addBtn(renameBtn);
            }
            // 删除按钮：间距与控制行“扫描/清除”一致（4px）
            ButtonGeneric del = new ButtonGeneric(this.getScreenWidth() - 88, y - 2, 60, 16, "删除");
            del.setActionListener((b, mb) -> {
                if (idx >= 0 && idx < config.stockAreas.size()) {
                    com.materialstock.data.StockArea removed = config.stockAreas.get(idx);
                    config.stockAreas.remove(idx);
                    // 只删该选区覆盖的容器缓存（明确用该选区自己的维度，避免误删别的维度）
                    com.materialstock.scan.ContainerCache.getInstance()
                            .removeInArea(removed.dimension, removed);
                    if (config.stockAreas.isEmpty()) {
                        ContainerCache.getInstance().clear();
                        config.containerCache = new java.util.LinkedHashMap<>();
                    }
                    com.materialstock.scan.StockAreaScanner.persistContainerCache();
                    StockConfig.save();
                    this.status = "已删除备货区及其容器缓存";
                    this.initGui();
                }
            });
            this.addBtn(del);
            y += LIST_H;
        }
        if (areaCount == 0) {
            // 空状态提示改由 drawContents 每帧实时绘制（含键名，避免改键后显示旧键位）
        } else if (areaCount > MAX_LIST_ROWS) {
            this.addLabel(10, LIST_Y0 + MAX_LIST_ROWS * LIST_H, 400, LIST_H, 0xFF888888,
                "显示 " + (this.areaScroll + 1) + "-" + Math.min(this.areaScroll + MAX_LIST_ROWS, areaCount)
                    + "/共 " + areaCount + " 个备货区，滚轮翻页");
        }
    }

    private void addTab(int x, int y, int width, String text, GuiBase target) {
        ButtonGeneric b = new ButtonGeneric(x, y, width, TAB_H, text);
        b.setActionListener((button, mouseButton) -> GuiBase.openGui(target));
        GuiTabs.applyHover(b, text);
        this.tabButtons.add(b);
        this.addBtn(b);
    }

    /** 页签悬停提示：最后绘制，避免被其它控件盖住 */
    private void renderTabTooltips(net.minecraft.client.gui.GuiGraphics ctx, int mouseX, int mouseY) {
        for (ButtonGeneric b : this.tabButtons) {
            GuiTabs.renderTooltip(b, mouseX, mouseY, ctx);
        }
    }

    /** 注册按钮并记录区域（按钮上滚轮被拦截，只有点击才触发） */
    private void addBtn(ButtonGeneric b) {
        this.scrollGuard.add(b.getX(), b.getY(), b.getWidth(), b.getHeight());
        this.addWidget(b);
    }

    private void saveSelection() {
        SelectionManager sel = SelectionManager.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            this.status = "请先进入世界";
            return;
        }
        if (!sel.hasBoth()) {
            this.status = "需要两个角点：对准方块按 " + com.materialstock.KeyBindings.selectKeyName() + " 依次选点1、点2";
            return;
        }
        String dim = mc.level.dimension().location().toString();
        String name = this.nameField.getTextWrapper().trim();
        if (name.isEmpty()) name = "备货区" + (StockConfig.get().stockAreas.size() + 1);
        StockConfig.get().stockAreas.add(new StockArea(name, dim, sel.getPos1(), sel.getPos2()));
        StockConfig.save();
        sel.clear();
        this.status = "已保存备货区：" + name + "（物品放入容器后点“扫描备货区”即可更新数量）";
        this.initGui();
    }

    /** 确认改名：把 renameTarget 对应的备货区改为行内输入框中的新名字 */
    private void applyRename() {
        StockConfig config = StockConfig.get();
        String newName = this.renameField != null ? this.renameField.getTextWrapper().trim() : "";
        if (newName.isEmpty()) {
            this.status = "名称不能为空";
            return;
        }
        for (StockArea a : config.stockAreas) {
            if (a.name.equals(this.renameTarget)) {
                a.name = newName;
                StockConfig.save();
                this.status = "已改名：" + this.renameTarget + " → " + newName;
                this.renameTarget = null;
                this.renameField = null;
                this.initGui();
                return;
            }
        }
        this.renameTarget = null;
        this.renameField = null;
        this.status = "未找到要改名的备货区";
        this.initGui();
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        SelectionManager sel = SelectionManager.getInstance();

        int bottom = this.getScreenHeight() - 40;

        // 空状态提示：每帧实时读取键名（原先由 initGui 里的 addLabel 创建，是改键前的快照）
        if (StockConfig.get().stockAreas.isEmpty()) {
            this.drawString(drawContext,
                "暂无备货区。按 " + com.materialstock.KeyBindings.selectKeyName() + " 框选两点后点“保存为备货区”。",
                10, 100, 0xFF888888);
        }

        // 框选提示（y=44）：每帧实时读取键名，改键后立即显示新键位
        this.drawString(drawContext,
            "框选：对准方块一角按 " + com.materialstock.KeyBindings.selectKeyName() + " 选点1，再对准对角按 "
                + com.materialstock.KeyBindings.selectKeyName() + " 选点2（预览见世界内绿框）",
            10, 44, 0xFFAAAAAA);

        // 选区状态（显示在“扫描备货区”按钮正下方，避免与备货区列表重叠）
        String selText;
        if (!sel.hasPos1()) {
            selText = "当前选区：未开始（对准方块按 " + com.materialstock.KeyBindings.selectKeyName() + " 选点）";
        } else if (!sel.hasBoth()) {
            selText = "当前选区：点1 = " + fmt(sel.getPos1()) + "，再对准对角按 " + com.materialstock.KeyBindings.selectKeyName() + " 选点2";
        } else {
            selText = "当前选区：点1 = " + fmt(sel.getPos1()) + "，点2 = " + fmt(sel.getPos2());
        }
        this.drawString(drawContext, selText, 10, 82, 0xFFFFAA55);

        // 缓存与已有材料汇总
        Map<Item, Integer> available = StockAreaScanner.summarizeAvailable();
        int items = 0;
        long total = 0;
        for (Map.Entry<Item, Integer> e : available.entrySet()) {
            items++;
            total += e.getValue();
        }
        this.drawString(drawContext, String.format("备货区缓存：%d 个容器 · 共 %d 种物品 / %d 个（材料清单页会显示“已有”数量）",
            ContainerCache.getInstance().size(), items, total), 10, bottom, 0xFF55FFFF);

        // ---- 物品明细：有什么物品、各多少个（固定区域、滚动翻页、不与列表/滚动提示/下方提示重叠）----
        // 明细区下探到底部提示上方：多显示一行物品（原“当前维度备货区数量”已移除）
        int detailBottom = Math.max(bottom - 18, DETAIL_ROW0 + 14);
        if (available.isEmpty()) {
            this.drawString(drawContext, "物品明细：暂无缓存（请先点“扫描备货区”）", 10, DETAIL_ROW0, 0xFF888888);
        } else {
            String titleText = "物品明细：共 " + available.size() + " 种（滚轮翻页）";
            this.drawString(drawContext, titleText, 10, DETAIL_TITLE, 0xFFFFFF55);
            List<Map.Entry<Item, Integer>> entryList = new ArrayList<>(available.entrySet());
            int visible = Math.max(1, (detailBottom - DETAIL_ROW0) / 14);
            int maxScroll = Math.max(0, entryList.size() - visible);
            if (this.itemScroll > maxScroll) this.itemScroll = maxScroll;
            if (this.itemScroll < 0) this.itemScroll = 0;
            int y = DETAIL_ROW0;
            for (int i = this.itemScroll; i < entryList.size() && i < this.itemScroll + visible; i++) {
                if (y + 14 > detailBottom) break;
                Map.Entry<Item, Integer> e = entryList.get(i);
                drawContext.renderItem(new net.minecraft.world.item.ItemStack(e.getKey()), 10, y);
                String name = new net.minecraft.world.item.ItemStack(e.getKey()).getHoverName().getString();
                String fmtQ = com.materialstock.util.QuantityFormat.format(e.getValue(), e.getKey());
                this.drawString(drawContext, name + "  × " + e.getValue() + "（" + fmtQ + "）", 30, y + 3, 0xFFFFFFFF);
                y += 14;
            }
            if (entryList.size() > visible) {
                this.drawString(drawContext, "显示 " + (this.itemScroll + 1) + "-" + Math.min(this.itemScroll + visible, entryList.size())
                    + "/" + entryList.size() + "，滚轮翻页", 10, detailBottom + 2, 0xFF88DDFF);
            }
        }

        this.drawString(drawContext, this.status, 10, bottom + 14, 0xFFFFFF00);

        // 页签悬停提示：最后绘制，浮在状态行之上
        this.renderTabTooltips(drawContext, mouseX, mouseY);
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        // 按钮上滚轮直接拦截（malilib 按钮会把滚轮当点击触发动作）
        if (this.scrollGuard.isOver(mouseX, mouseY)) {
            return true;
        }
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        // 备货区列表翻页（鼠标在列表区域时）
        int areaCount = StockConfig.get().stockAreas.size();
        if (areaCount > MAX_LIST_ROWS && mouseX >= 10 && mouseX <= this.getScreenWidth() - 20
            && mouseY >= LIST_Y0 && mouseY <= LIST_Y0 + MAX_LIST_ROWS * LIST_H) {
            this.areaScroll -= (int) (verticalAmount * 1);
            int maxAreaScroll = areaCount - MAX_LIST_ROWS;
            if (this.areaScroll > maxAreaScroll) this.areaScroll = maxAreaScroll;
            if (this.areaScroll < 0) this.areaScroll = 0;
            this.initGui();
            return true;
        }
        // 物品明细翻页
        if (mouseX >= 10 && mouseX <= this.getScreenWidth() - 20 && mouseY >= DETAIL_ROW0 && mouseY <= this.getScreenHeight() - 90) {
            this.itemScroll -= (int) (verticalAmount * 3);
            return true;
        }
        return false;
    }
}
