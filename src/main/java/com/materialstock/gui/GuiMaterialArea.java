package com.materialstock.gui;

import com.materialstock.KeyBindings;
import com.materialstock.StockConfig;
import com.materialstock.data.StockArea;
import com.materialstock.scan.SelectionManager;
import com.materialstock.scan.StockAreaScanner;
import com.materialstock.util.QuantityFormat;
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
import net.minecraft.world.item.ItemStack;

/**
 * 材料区（独立于备货区）：
 * 用 K 键单独框选一个区域作为材料区；框选过程中世界内显示已选范围与正在框选范围。
 * 选中区域后默认不显示框，按下热键 [H]（或按钮）才显示材料区高亮。
 * 扫描后展示该材料区有多少材料，可与计算器联动。
 */
public class GuiMaterialArea extends GuiBase {
    private static final int TAB_Y = 22;
    private static final int TAB_H = 16;
    /** 列表起始行：与备货区完全一致 */
    private static final int LIST_Y0 = 100;
    private static final int LIST_H = 18;
    private static final int MAX_LIST_ROWS = 3;
    /** 物品明细标题行：位于材料区列表（含滚动提示行）之下，避免与列表/提示重叠 */
    private static final int DETAIL_TITLE = LIST_Y0 + MAX_LIST_ROWS * LIST_H + 18;
    /** 物品明细首行：标题之下再让出一行 */
    private static final int DETAIL_ROW0 = DETAIL_TITLE + 16;

    private GuiTextFieldGeneric nameField;
    private String status = "";
    /** 顶部四个页签按钮（用于绘制鼠标悬停提示） */
    private final java.util.List<ButtonGeneric> tabButtons = new java.util.ArrayList<>();
    private String selectedName = "";
    private String selectedId = "";
    private int itemScroll = 0;
    /** 材料区列表滚动偏移（超过 3 行时滚轮翻页） */
    private int areaScroll = 0;
    /** 正在改名的材料区原名；null 表示不在改名模式 */
    private String renameTarget = null;
    private Integer editCoordIdx = null;
    private GuiTextFieldGeneric[] coordFields = new GuiTextFieldGeneric[6];
    /** 行内改名输入框（仅在改名模式下创建） */
    private GuiTextFieldGeneric renameField = null;
    private Map<Item, Integer> material = new java.util.LinkedHashMap<>();
    private final List<Map.Entry<Item, Integer>> entryList = new ArrayList<>();

    /** 按钮滚轮拦截：malilib 会把按钮上的滚轮当点击，这里先拦截 */
    private final ButtonScrollGuard scrollGuard = new ButtonScrollGuard();

    /** 材料区高亮开关按钮：由 drawContents 每帧按实时热键文字宽度重定位 */
    private ButtonGeneric materialVisBtn = null;

    private static boolean autoScanned = false;

    public GuiMaterialArea() {
        this.setTitle("材料备货助手 - 材料区");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearWidgets();
        this.tabButtons.clear();   // initGui 会被反复调用，不清会无限累加
        this.scrollGuard.clear();
        StockConfig config = StockConfig.get();
        // 第一次打开材料区时自动扫描一次
        if (!autoScanned && !config.materialAreas.isEmpty()) {
            autoScanned = true;
            com.materialstock.scan.StockAreaScanner.scanMaterialAreas(() -> {
                net.minecraft.client.Minecraft.getInstance().execute(() -> this.initGui());
            });
        }
        int mid = this.getScreenWidth() / 2;

        // ---- 顶部 tab（宽度随屏幕自适应，GUI 缩放大时不溢出屏幕）----
        int tabW = Math.max(64, Math.min(100, (this.getScreenWidth() - 44) / 4));
        this.addTab(mid - 2 * tabW - 12, TAB_Y, tabW, "材料清单", new GuiStockMain());
        this.addTab(mid - tabW - 4, TAB_Y, tabW, "合成计算器", new GuiStockCalculator());
        this.addTab(mid + 4, TAB_Y, tabW, "备货区", new GuiStockArea());
        this.addTab(mid + tabW + 12, TAB_Y, tabW, "材料区", new GuiMaterialArea());

        // ---- 提示行说明 ----
        // 顶部框选提示（y=44）含键名，改到 drawContents 每帧实时绘制
        // （malilib 的 addLabel 把文本存进 WidgetLabel 的 final 列表，创建后不再更新，
        //   会导致玩家改键后界面仍显示旧键位）
        int sw = this.getScreenWidth();

        // 高亮按钮：初始位置在此计算，实际每帧由 drawContents 按实时文字宽度重定位
        String visText = config.materialAreaVis ? "材料区高亮：显示中（按 " + com.materialstock.KeyBindings.visKeyName() + " 隐藏）" : "材料区高亮：已隐藏（按 " + com.materialstock.KeyBindings.visKeyName() + " 或上方按钮显示）";
        int visW = Minecraft.getInstance().font.width(visText);
        ButtonGeneric visBtn = new ButtonGeneric(10 + visW + 6, 80, 40, 16, config.materialAreaVis ? "高亮:开" : "高亮:关");
        visBtn.setActionListener((b, mb) -> {
            config.materialAreaVis = !config.materialAreaVis;
            StockConfig.save();
            this.status = config.materialAreaVis ? "材料区高亮已显示（按 " + com.materialstock.KeyBindings.visKeyName() + " 可随时切换）" : "材料区高亮已隐藏（按 " + com.materialstock.KeyBindings.visKeyName() + " 可随时切换）";
            this.initGui();
        });
        this.addBtn(visBtn);
        this.materialVisBtn = visBtn;

        // ---- 控制行：保存 / 清除选区 / 扫描 / 清除全部（四按钮同一排，与备货区一致）----
        this.addLabel(10, 64, 50, 16, 0xFFAAAAAA, "区域名称:");
        int nameW = Math.max(56, Math.min(120, sw - 412));
        this.nameField = new GuiTextFieldGeneric(58, 62, nameW, 16, Minecraft.getInstance().font);
        // 顶部输入框只负责新建名称，改名输入走行内输入框，不再抢焦点
        this.nameField.setTextWrapper("材料区" + (config.materialAreas.size() + 1));
        this.addTextField(this.nameField, null);
        int cx = 58 + nameW + 4;

        ButtonGeneric saveBtn = new ButtonGeneric(cx, 62, 80, 16, "保存为材料区");
        saveBtn.setActionListener((b, mb) -> this.saveSelection());
        this.addBtn(saveBtn);
        cx += 84;

        ButtonGeneric clearSel = new ButtonGeneric(cx, 62, 60, 16, "清除选区");
        clearSel.setActionListener((b, mb) -> {
            SelectionManager.getInstance().clear();
            this.status = "已清除选区";
            this.initGui();
        });
        this.addBtn(clearSel);
        cx += 64;

        ButtonGeneric scanBtn = new ButtonGeneric(cx, 62, 80, 16, "扫描材料区");
        scanBtn.setActionListener((b, mb) -> {
            this.status = "正在扫描材料区…";
            StockAreaScanner.scanMaterialAreas(() -> {
                this.status = "材料区扫描完成（独立统计，不与备货区混合）";
                this.initGui();
            });
        });
        this.addBtn(scanBtn);
        cx += 84;

        ButtonGeneric clearAll = new ButtonGeneric(cx, 62, 80, 16, "清除全部");
        clearAll.setActionListener((b, mb) -> {
            // 先收集将被删除的区域（锁定的保留），以便只清它们的容器数据
            java.util.List<com.materialstock.data.StockArea> removed =
                    new java.util.ArrayList<>(config.materialAreas);
            removed.removeIf(a -> config.lockedMaterialAreas.contains(a.name));
            config.materialAreas.removeIf(a -> !config.lockedMaterialAreas.contains(a.name));
            this.selectedName = "";
            // 移除被删区域的容器明细（锁定的区域其容器保留）
            java.util.Iterator<net.minecraft.core.BlockPos> it =
                    com.materialstock.scan.StockAreaScanner.materialContainerPositions().iterator();
            while (it.hasNext()) {
                net.minecraft.core.BlockPos bp = it.next();
                for (com.materialstock.data.StockArea a : removed) {
                    if (a.contains(bp)) { it.remove(); break; }
                }
            }
            // 同步清理备货区容器缓存的持久化数据
            // 键格式为 "维度|x,y,z"（旧配置可能是 "x,y,z"），两者都要能解析
            if (config.containerCache != null) {
                config.containerCache.keySet().removeIf(k -> {
                    net.minecraft.core.BlockPos bp = parseCacheKey(k);
                    if (bp == null) return false;
                    for (com.materialstock.data.StockArea a : removed) {
                        if (a.contains(bp)) return true;
                    }
                    return false;
                });
            }
            // 重算汇总（从剩余容器重新统计，锁定的区域数据自然保留）
            com.materialstock.scan.StockAreaScanner.rebuildMaterialSummary();
            StockConfig.save();
            this.status = "已清除全部材料区与缓存";
            this.initGui();
        });
        this.addBtn(clearAll);

        // ---- 材料区列表（固定显示 3 行，超过时滚轮翻页）----
        int areaCount = config.materialAreas.size();
        int maxAreaScroll = Math.max(0, areaCount - MAX_LIST_ROWS);
        if (this.areaScroll > maxAreaScroll) this.areaScroll = maxAreaScroll;
        if (this.areaScroll < 0) this.areaScroll = 0;
        int y = LIST_Y0;
        // 坐标编辑行：必须画在【被编辑行当前可见位置】，否则会压住那一行的文字。
        // 注意 editCoordIdx 是列表索引，areaScroll 是滚动偏移，两者相减才是窗口内的行号。
        int coordRow = (this.editCoordIdx != null) ? this.editCoordIdx - this.areaScroll : -1;
        if (this.editCoordIdx != null && this.editCoordIdx < areaCount
            && coordRow >= 0 && coordRow < MAX_LIST_ROWS) {
            com.materialstock.data.StockArea sa = config.materialAreas.get(this.editCoordIdx);
            int[] vals = {sa.x1, sa.y1, sa.z1, sa.x2, sa.y2, sa.z2};
            int coordY = LIST_Y0 + coordRow * LIST_H - 2;
            for (int j = 0; j < 6; j++) {
                coordFields[j] = new GuiTextFieldGeneric(10 + j * 62, coordY, 56, 16, Minecraft.getInstance().font);
                coordFields[j].setTextWrapper(String.valueOf(vals[j]));
                this.addTextField(coordFields[j], null);
            }
        }

        for (int i = this.areaScroll; i < areaCount && i < this.areaScroll + MAX_LIST_ROWS; i++) {
            StockArea area = config.materialAreas.get(i);
            // 列表项格式与备货区完全一致（编号.名称 (坐标)~(坐标)，颜色统一绿色，不显示选中标记）
            String text = (i + 1) + ". " + area.name + "  (" + fmt(area.getMin()) + ")~(" + fmt(area.getMax()) + ")";
            // 改名模式下目标行右侧放 输入框+取消+确定，其他行保持 [改名][删除] 不变
            boolean isRenameTarget = this.renameTarget != null && area.name.equals(this.renameTarget);
            int textRight = isRenameTarget ? this.getScreenWidth() - 280 : this.getScreenWidth() - 170;
            if (this.editCoordIdx == null || this.editCoordIdx != i) {
                this.addLabel(10, y, textRight - 10, LIST_H, 0xFF88FF88, text);
            }
            int idx = i;
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
                if (this.editCoordIdx == null) {
                String lockLabel = config.lockedMaterialAreas.contains(area.name) ? "[解锁]" : "[锁定]";
                ButtonGeneric lockBtn = new ButtonGeneric(this.getScreenWidth() - 278, y - 2, 56, 16, lockLabel);
                lockBtn.setActionListener((b, mb) -> {
                    if (config.lockedMaterialAreas.contains(area.name)) config.lockedMaterialAreas.remove(area.name);
                    else config.lockedMaterialAreas.add(area.name);
                    com.materialstock.StockConfig.save();
                    this.status = config.lockedMaterialAreas.contains(area.name) ? "已锁定（清除全部时保留）" : "已解锁";
                    this.initGui();
                });
                this.addBtn(lockBtn);
                }
                if (this.editCoordIdx == null) {
                ButtonGeneric coordBtn = new ButtonGeneric(this.getScreenWidth() - 216, y - 2, 60, 16, "坐标(改)");
                coordBtn.setActionListener((b, mb) -> {
                    if (idx >= 0 && idx < config.materialAreas.size()) {
                        this.editCoordIdx = idx;
                        this.status = "编辑坐标：修改 x1/y1/z1/x2/y2/z2 后点确定";
                        this.initGui();
                    }
                });
                this.addBtn(coordBtn);
                }
                // 编辑态：本行放「确定」保存坐标
                // 不能用 else if 挂在上面那个 if 后面 —— 进入本分支时 editCoordIdx 必不为 null，
                // 那个条件恒假，会导致「确定」永远不渲染、坐标改了无法保存。
                if (this.editCoordIdx != null && this.editCoordIdx == idx) {
                ButtonGeneric okBtn = new ButtonGeneric(this.getScreenWidth() - 216, y - 2, 60, 16, "确定");
                okBtn.setActionListener((b, mb) -> {
                    // 编辑态下 editCoordIdx 必然等于本行 idx；坐标必须归一化，
                    // 否则逆序输入（如 x1 > x2）会让 StockArea.contains 恒为 false，扫描不到任何物品
                    try {
                        com.materialstock.data.StockArea area2 = config.materialAreas.get(idx);
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
                this.addBtn(okBtn);
                }
                ButtonGeneric renameBtn = new ButtonGeneric(this.getScreenWidth() - 152, y - 2, 60, 16, "改名");
                renameBtn.setActionListener((b, mb) -> {
                    if (idx >= 0 && idx < config.materialAreas.size()) {
                        this.renameTarget = config.materialAreas.get(idx).name;
                        this.status = "正在改名：" + this.renameTarget + "，输入新名字后点“确定”";
                        this.initGui();
                    }
                });
                this.addBtn(renameBtn);
            }
            // 删除按钮：间距与控制行“扫描/清除”一致（4px）
            ButtonGeneric del = new ButtonGeneric(this.getScreenWidth() - 88, y - 2, 60, 16, "删除");
            del.setActionListener((b, mb) -> {
                if (idx >= 0 && idx < config.materialAreas.size()) {
                    com.materialstock.data.StockArea removed = config.materialAreas.get(idx);
                    if (removed.name.equals(this.selectedName)) {
                        this.selectedName = "";
                    }
                    config.materialAreas.remove(idx);
                    // 该选区覆盖的容器：从材料区明细中移除（persistMaterialSummary 负责写盘）
                    java.util.Iterator<net.minecraft.core.BlockPos> it =
                            com.materialstock.scan.StockAreaScanner.materialContainerPositions().iterator();
                    while (it.hasNext()) {
                        if (removed.contains(it.next())) it.remove();
                    }
                    // 同步清理备货区容器缓存的持久化数据，否则退游戏重进后仍会残留
                    // 键格式为 "维度|x,y,z"（旧配置可能是 "x,y,z"）
                    if (config.containerCache != null) {
                        config.containerCache.keySet().removeIf(k -> {
                            net.minecraft.core.BlockPos bp = parseCacheKey(k);
                            return bp != null && removed.contains(bp);
                        });
                    }
                    // 同步清理"材料区容器位置"：否则被删区域内的箱子仍会被追踪高亮
                    if (config.materialContainerKeys != null) {
                        config.materialContainerKeys.removeIf(k -> {
                            net.minecraft.core.BlockPos bp = parseCacheKey(k);
                            return bp != null && removed.contains(bp);
                        });
                    }
                    // 重算汇总（不做逐项减法：潜影盒/束口袋是展开统计的，减法会对不上）
                    com.materialstock.scan.StockAreaScanner.rebuildMaterialSummary();
                    StockConfig.save();
                    this.status = "已删除材料区，对应物品已从汇总扣除";
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
                    + "/共 " + areaCount + " 个材料区，滚轮翻页");
        }

        // ---- 选中材料：行左侧实心三角指示（蓝色高亮已在 drawContents 绘制）----
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

    /**
     * 解析容器缓存的持久化键。
     * 新格式："维度|x,y,z"；旧格式（兼容）："x,y,z"。解析失败返回 null。
     */
    private static net.minecraft.core.BlockPos parseCacheKey(String key) {
        if (key == null) return null;
        String s = key;
        int sep = s.indexOf('|');
        if (sep > 0) s = s.substring(sep + 1);
        String[] p = s.split(",");
        if (p.length != 3) return null;
        try {
            return new net.minecraft.core.BlockPos(
                Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
        } catch (Exception ignored) {
            return null;
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
            this.status = "需要两个角点：对准方块按 " + com.materialstock.KeyBindings.selectKeyName() + " 依次选点1、点2（框选时世界内显示范围预览）";
            return;
        }
        String dim = mc.level.dimension().location().toString();
        String name = this.nameField.getTextWrapper().trim();
        if (name.isEmpty()) name = "材料区" + (StockConfig.get().materialAreas.size() + 1);
        StockConfig.get().materialAreas.add(new StockArea(name, dim, sel.getPos1(), sel.getPos2()));
        StockConfig.save();
        sel.clear();
        this.selectedName = name;
        this.status = "已保存材料区：" + name + "（默认不显示框，按 " + com.materialstock.KeyBindings.visKeyName() + " 显示）";
        this.initGui();
    }

    /** 确认改名：把 renameTarget 对应的材料区改为行内输入框中的新名字；若改的是当前选中区则同步选中 */
    private void applyRename() {
        StockConfig config = StockConfig.get();
        String newName = this.renameField != null ? this.renameField.getTextWrapper().trim() : "";
        if (newName.isEmpty()) {
            this.status = "名称不能为空";
            return;
        }
        for (StockArea a : config.materialAreas) {
            if (a.name.equals(this.renameTarget)) {
                if (this.selectedName.equals(this.renameTarget)) {
                    this.selectedName = newName;
                }
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
        this.status = "未找到要改名的材料区";
        this.initGui();
    }

    private static String fmt(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static Item parse(String id) {
        net.minecraft.resources.ResourceLocation rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        return rl != null ? net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl) : null;
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        SelectionManager sel = SelectionManager.getInstance();
        Minecraft mc = Minecraft.getInstance();

        int bottom = this.getScreenHeight() - 40;

        // 空状态提示：每帧实时读取键名（原先由 initGui 里的 addLabel 创建，是改键前的快照）
        if (StockConfig.get().materialAreas.isEmpty()) {
            this.drawString(drawContext,
                "暂无材料区。按 " + com.materialstock.KeyBindings.selectKeyName() + " 框选两点后点“保存为材料区”"
                    + "（选中后默认不显示框，按 " + com.materialstock.KeyBindings.visKeyName() + " 显示）。",
                10, 100, 0xFF888888);
        }

        // 顶部框选提示（y=44）：每帧实时读取键名，改键后立即显示新键位
        this.drawString(drawContext,
            "材料区独立框选：对准方块一角按 [" + com.materialstock.KeyBindings.selectKeyName() + "] 选点1，再对准对角按 ["
                + com.materialstock.KeyBindings.selectKeyName() + "] 选点2（框选时世界内显示范围），选好后点“保存为材料区”",
            10, 44, 0xFFAAAAAA);

        // 高亮状态 + 选区状态（与备货区“当前选区”同位置 y=82 合并在一行；高亮按钮也在此行）
        StockConfig config = StockConfig.get();
        String visText = config.materialAreaVis ? "材料区高亮：显示中（按 " + com.materialstock.KeyBindings.visKeyName() + " 隐藏）" : "材料区高亮：已隐藏（按 " + com.materialstock.KeyBindings.visKeyName() + " 或上方按钮显示）";
        this.drawString(drawContext, visText, 10, 82, config.materialAreaVis ? 0xFF88FF88 : 0xFF888888);
        int visW = Minecraft.getInstance().font.width(visText);
        // 按钮紧跟实时文字之后（键名长度变化时不能沿用 initGui 时的位置）
        if (this.materialVisBtn != null) {
            this.materialVisBtn.setX(10 + visW + 6);
        }
        String selText;
        if (!sel.hasPos1()) {
            selText = "当前选区：未开始（对准方块按 " + com.materialstock.KeyBindings.selectKeyName() + " 选点）";
        } else if (!sel.hasBoth()) {
            selText = "当前选区：点1 = " + fmt(sel.getPos1()) + "，再对准对角按 " + com.materialstock.KeyBindings.selectKeyName() + " 选点2";
        } else {
            selText = "当前选区：点1 = " + fmt(sel.getPos1()) + "，点2 = " + fmt(sel.getPos2());
        }
        // 选区状态放在高亮文字+按钮之后（按钮占 40 宽，右侧留 12 间距）
        this.drawString(drawContext, selText, 10 + visW + 46 + 12, 82, 0xFFFFAA55);

        // 材料区材料明细（独立统计）
        this.material = StockAreaScanner.summarizeMaterial();
        this.entryList.clear();
        this.entryList.addAll(this.material.entrySet());

        // 统计汇总（与备货区“备货区缓存”同位置 bottom，青色）
        long totalItems = 0;
        for (Map.Entry<Item, Integer> e : this.entryList) {
            totalItems += e.getValue();
        }
        this.drawString(drawContext, String.format("材料区缓存：%d 个容器 · 共 %d 种物品 / %d 个（独立统计，不与备货区混合）",
            StockAreaScanner.materialContainerCount(), this.entryList.size(), totalItems), 10, bottom, 0xFF55FFFF);

        String matTitle = String.format("材料区材料：共 %d 种（滚轮翻页）", this.entryList.size());
        this.drawString(drawContext, matTitle, 10, DETAIL_TITLE, 0xFFFFFF55);
        this.drawString(drawContext, "[展开]",
            10 + net.minecraft.client.Minecraft.getInstance().font.width(matTitle) + 6,
            DETAIL_TITLE, 0xFFFFFF66);

        if (this.entryList.isEmpty()) {
            this.drawString(drawContext, "材料区暂无统计。先保存材料区，再点“扫描材料区”。", 10, DETAIL_ROW0, 0xFF888888);
        } else {
            // 明细区下探到底部提示上方：多显示一行物品（与备货区一致，原“选中材料区详情”已移除）
            int visible = 6;   // 固定显示6行
            int detailBottom = DETAIL_ROW0 + visible * 14;
            int maxScroll = Math.max(0, this.entryList.size() - visible);
            if (this.itemScroll > maxScroll) this.itemScroll = maxScroll;
            if (this.itemScroll < 0) this.itemScroll = 0;
            int y = DETAIL_ROW0;
            for (int i = this.itemScroll; i < this.entryList.size() && i < this.itemScroll + visible; i++) {
                if (y + 14 > detailBottom) break;
                Map.Entry<Item, Integer> e = this.entryList.get(i);
                Item item = e.getKey();
                boolean itemSel = this.selectedId.equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString());
                drawContext.renderItem(new ItemStack(item), 10, y);
                String name = new ItemStack(item).getHoverName().getString();
                String fmtQ = QuantityFormat.format(e.getValue(), item);
                this.drawString(drawContext, name + "  × " + e.getValue() + "（" + fmtQ + "）", 30, y + 3, 0xFFFFFFFF);
                if (itemSel) {
                    // 文字后面[追踪]/[取消追踪]
                    String rowText = name + "  × " + e.getValue() + "（" + fmtQ + "）";
                    String trackBtn = StockConfig.get().trackedItems.contains(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString())
                        ? "[取消追踪]" : "[追踪]";
                    this.drawString(drawContext, trackBtn,
                        30 + net.minecraft.client.Minecraft.getInstance().font.width(rowText) + 6,
                        y + 3, 0xFFFFFF66);
                }
                y += 14;
            }
            if (this.entryList.size() > visible) {
                this.drawString(drawContext, "显示 " + (this.itemScroll + 1) + "-" + Math.min(this.itemScroll + visible, this.entryList.size())
                    + "/" + this.entryList.size() + "，滚轮翻页", 10, detailBottom + 2, 0xFF88DDFF);
            }
        }

        // 状态栏：与备货区一致，画在底部（黄色）
        this.drawString(drawContext, this.status, 10, bottom + 14, 0xFFFFFF00);

        // 页签悬停提示：最后绘制，浮在状态行之上
        this.renderTabTooltips(drawContext, mouseX, mouseY);
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        // 点标题后面的 [展开] 打开材料区明细全屏界面
        if (!this.entryList.isEmpty()) {
            String matTitle = String.format("材料区材料：共 %d 种（滚轮翻页）", this.entryList.size());
            int btnX = 10 + net.minecraft.client.Minecraft.getInstance().font.width(matTitle) + 6;
            int btnW = net.minecraft.client.Minecraft.getInstance().font.width("[展开]");
            if (mouseX >= btnX && mouseX <= btnX + btnW
                    && mouseY >= DETAIL_TITLE && mouseY <= DETAIL_TITLE + 12) {
                com.materialstock.gui.GuiMaterialAreaDetail detail = new com.materialstock.gui.GuiMaterialAreaDetail();
                detail.setParent(this);
                net.minecraft.client.Minecraft.getInstance().setScreen(detail);
                return true;
            }
        }
        if (mouseButton != 0) return false;

        // 材料区列表：只点行名字区才选中
        if (mouseY >= LIST_Y0 && mouseY < LIST_Y0 + Math.min(MAX_LIST_ROWS, StockConfig.get().materialAreas.size()) * LIST_H) {
            int idx = this.areaScroll + (mouseY - LIST_Y0) / LIST_H;
            List<StockArea> areas = StockConfig.get().materialAreas;
            if (idx >= 0 && idx < areas.size()) {
                int nameW = 10 + net.minecraft.client.Minecraft.getInstance().font.width(areas.get(idx).name) + 20;
                if (mouseX >= 10 && mouseX <= nameW) {
                    this.selectedName = areas.get(idx).name;
                    this.status = "已选中材料区：" + this.selectedName;
                    this.initGui();
                    return true;
                }
            }
        }

        // 材料明细：只点物品图标+名字区才选中
        if (mouseY >= DETAIL_ROW0 && mouseY <= this.getScreenHeight() - 70) {
            int idx = this.itemScroll + (mouseY - DETAIL_ROW0) / 14;
            if (idx >= 0 && idx < this.entryList.size()) {
                Map.Entry<Item, Integer> e = this.entryList.get(idx);
                Item item = e.getKey();
                String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();
                String name = item.getDescription().getString();
                String fmtQ = QuantityFormat.format(e.getValue(), item);
                String rowText = name + "  × " + e.getValue() + "（" + fmtQ + "）";
                int textW = 30 + net.minecraft.client.Minecraft.getInstance().font.width(rowText);
                // 已选中行：点[追踪]/[取消追踪]切换
                if (this.selectedId != null && this.selectedId.equals(id)) {
                    String trackBtn = StockConfig.get().trackedItems.contains(id) ? "[取消追踪]" : "[追踪]";
                    int btnW = net.minecraft.client.Minecraft.getInstance().font.width(trackBtn);
                    if (mouseX >= textW + 6 && mouseX <= textW + 6 + btnW) {
                        if (StockConfig.get().trackedItems.contains(id)) {
                            StockConfig.get().trackedItems.remove(id);
                        } else {
                            StockConfig.get().trackedItems.add(id);
                        }
                        StockConfig.save();
                        this.initGui();
                        return true;
                    }
                }
                if (mouseX >= 10 && mouseX <= textW) {
                    this.selectedId = id;
                    this.initGui();
                    return true;
                }
            }
        }
        return false;
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
        // 材料区列表翻页（鼠标在列表区域时）
        int areaCount = StockConfig.get().materialAreas.size();
        if (areaCount > MAX_LIST_ROWS && mouseX >= 10 && mouseX <= this.getScreenWidth() - 20
            && mouseY >= LIST_Y0 && mouseY <= LIST_Y0 + MAX_LIST_ROWS * LIST_H) {
            this.areaScroll -= (int) (verticalAmount * 1);
            int maxAreaScroll = areaCount - MAX_LIST_ROWS;
            if (this.areaScroll > maxAreaScroll) this.areaScroll = maxAreaScroll;
            if (this.areaScroll < 0) this.areaScroll = 0;
            this.initGui();
            return true;
        }
        // 材料明细翻页
        if (mouseX >= 10 && mouseX <= this.getScreenWidth() - 20 && mouseY >= DETAIL_ROW0 && mouseY <= this.getScreenHeight() - 90) {
            this.itemScroll -= (int) (verticalAmount * 3);
            return true;
        }
        return false;
    }
}
