package com.materialstock.gui;

import com.materialstock.KeyBindings;
import com.materialstock.StockConfig;
import com.materialstock.litematica.CategoryHelper;
import com.materialstock.litematica.CategoryData;
import com.materialstock.litematica.MaterialListHelper;
import com.materialstock.render.HudRenderer;
import com.materialstock.scan.ContainerCache;
import com.materialstock.scan.StockAreaScanner;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

/**
 * 材料清单：
 * 列表页 = 默认不展示投影，输入搜索词后才显示匹配项目；点击"加载/进入查看"进入详情页。
 * 详情页 = 按原版标签分类的材料清单，结合备货区已有数量显示"需要/已有/缺少"；
 * 支持"对比备货区"：判断能否全部合成并同界面展示缺少项。
 */
public class GuiStockMain extends GuiBase {
    private static final int TAB_Y = 22;
    private static final int TAB_H = 16;
    private static final int CONTROL_Y = 44;
    private static final int STATUS_Y = 66;
    private static final int CONTENT_TOP = 98;
    private static final int LINE_H = 12;

    private enum Mode { LIST, DETAIL }

    private Mode mode = Mode.LIST;

    /** 顶部四个页签按钮（用于绘制鼠标悬停提示） */
    private final List<ButtonGeneric> tabButtons = new ArrayList<>();

    // ---- 列表页 ----
    private GuiTextFieldGeneric searchField;
    private final List<String> filteredFiles = new ArrayList<>();
    private int fileScroll = 0;
    private String lastQuery = "";

    // ---- 详情页 ----
    private final List<String> schematicFiles = new ArrayList<>();
    private String currentSchematic = "";
    private GuiTextFieldInteger multiplierField;
    private GuiTextFieldGeneric detailSearch;
    private String lastDetailQuery = "";


    private Map<Item, Integer> materialCounts = new LinkedHashMap<>();
    private Map<String, List<Item>> categorized = new LinkedHashMap<>();
    private Map<Item, Integer> availableCounts = new LinkedHashMap<>();
    private final Set<String> collapsed = new HashSet<>();
    private int scrollOffset = 0;
    private boolean draggingDetailScroll = false;   // 正在拖拽详情页滚动条滑块
    private String status = "";
    private boolean needsRefreshAvailable = false;
    /** 详情页当前选中的材料（行尾显示 忽略/恢复忽略 按钮） */
    private String selectedItemId = null;

    // ---- 对比备货区结果 ----
    private boolean showDiff = false;
    private String contrastStatus = "";
    private final List<ContrastMissing> contrastMissing = new ArrayList<>();

    /** 按钮滚轮拦截：malilib 会把按钮上的滚轮当点击，这里先拦截 */
    private final ButtonScrollGuard scrollGuard = new ButtonScrollGuard();

    /** 「打开按键设置」按钮：由 drawHint 每帧定位（紧跟实时热键文字之后） */
    private ButtonGeneric keySettingsBtn = null;

    private static final class ContrastMissing {
        final Item item;
        final int missing;

        ContrastMissing(Item item, int missing) {
            this.item = item;
            this.missing = missing;
        }
    }

    public GuiStockMain() {
        this.setTitle("材料备货助手 - 材料清单");
    }

    @Override
    public void initGui() {
        super.initGui();
        this.clearWidgets();
        this.tabButtons.clear();   // initGui 会被反复调用，不清会无限累加
        this.scrollGuard.clear();
        StockConfig config = StockConfig.get();

        // ---- 顶部 tab（宽度随屏幕自适应，GUI 缩放大时不溢出屏幕）----
        int tabW = Math.max(64, Math.min(100, (this.getScreenWidth() - 44) / 4));
        int mid = this.getScreenWidth() / 2;
        addTab(mid - 2 * tabW - 12, TAB_Y, tabW, "材料清单", new GuiStockMain());
        addTab(mid - tabW - 4, TAB_Y, tabW, "合成计算器", new GuiStockCalculator());
        addTab(mid + 4, TAB_Y, tabW, "备货区", new GuiStockArea());
        addTab(mid + tabW + 12, TAB_Y, tabW, "材料区", new GuiMaterialArea());

        if (this.mode == Mode.LIST) {
            this.initListPage(config);
        } else {
            this.collapsed.clear();
            this.collapsed.addAll(config.collapsedCats);
            this.initDetailPage(config);
        }
    }

    // ==================== 列表页 ====================

    private void initListPage(StockConfig config) {
        // ---- 搜索 + 控制（宽度随屏幕自适应，GUI 缩放大时不溢出屏幕）----
        int sw = this.getScreenWidth();
        this.addLabel(10, CONTROL_Y, 70, 16, 0xFFAAAAAA, "搜索投影:");
        int searchW = Math.max(60, Math.min(200, sw - 330));
        this.searchField = new GuiTextFieldGeneric(78, CONTROL_Y - 2, searchW, 16, net.minecraft.client.Minecraft.getInstance().font);
        this.searchField.setTextWrapper("");
        this.addTextField(this.searchField, null);
        int cx = 78 + searchW + 4;

        ButtonGeneric refreshBtn = new ButtonGeneric(cx, CONTROL_Y, 56, 16, "刷新列表");
        refreshBtn.setActionListener((b, mb) -> {
            MaterialListHelper.refreshFileList();
            this.refreshFilteredFiles();
            this.status = "已重新扫描投影目录";
        });
        this.addBtn(refreshBtn);
        cx += 60;

        ButtonGeneric clearCacheBtn = new ButtonGeneric(cx, CONTROL_Y, 90, 16, "清除缓存");
        clearCacheBtn.setActionListener((b, mb) -> {
            MaterialListHelper.clearMaterialsCache();
            com.materialstock.render.HudRenderer.hudMaterials.clear();
            com.materialstock.render.HudRenderer.hudEnabled = false;
            this.status = "已清除全部材料缓存（信息浮窗已关闭）";
        });
        this.addBtn(clearCacheBtn);
        cx += 94;

        ButtonGeneric catBtn = new ButtonGeneric(cx, CONTROL_Y, 90, 16, "分类管理");
        catBtn.setActionListener((b, mb) -> GuiBase.openGui(new GuiCategoryManager()));
        this.addBtn(catBtn);

        this.refreshFilteredFiles();

        // 底部「打开按键设置」按钮：文字提示由 drawHint 每帧实时绘制（改键后立即显示新键）
        this.keySettingsBtn = new ButtonGeneric(this.getScreenWidth() - 120, this.getScreenHeight() - 22, 106, 16, "打开按键设置");
        this.keySettingsBtn.setActionListener((b, mb) -> {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            mc.setScreen(new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(null, mc.options));
        });
        this.addBtn(this.keySettingsBtn);
    }

    /** 按搜索词过滤投影列表；支持 中文/拼音（全拼、首字母），首字拼音匹配优先；默认不展示（搜索词为空时列表为空） */
    private void refreshFilteredFiles() {
        this.filteredFiles.clear();
        String q = this.searchField == null ? "" : this.searchField.getTextWrapper().trim().toLowerCase();
        if (q.isEmpty()) return;
        List<String> files = MaterialListHelper.listSchematicFiles();
        List<String> first = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String f : files) {
            if (!matchFile(f, q)) continue;
            if (isFirstPinyinMatch(f, q)) first.add(f);
            else rest.add(f);
        }
        this.filteredFiles.addAll(first);
        this.filteredFiles.addAll(rest);
        if (this.filteredFiles.isEmpty() && !files.isEmpty()) {
            this.status = "没有匹配“" + this.searchField.getTextWrapper().trim() + "”的投影，换一个关键字试试";
        }
    }

    /** 文件名是否命中搜索（中文名/拼音全拼/首字母） */
    private static boolean matchFile(String fileName, String q) {
        String lower = fileName.toLowerCase();
        return lower.contains(q)
            || com.materialstock.util.PinyinUtil.toFull(fileName).contains(q)
            || com.materialstock.util.PinyinUtil.toInitials(fileName).contains(q);
    }

    /** 拼音优先：第一个字的拼音（全拼或首字母）以 q 开头 */
    private static boolean isFirstPinyinMatch(String fileName, String q) {
        if (fileName.isEmpty()) return false;
        String firstChar = fileName.substring(0, 1);
        String firstP = com.materialstock.util.PinyinUtil.toFull(firstChar);
        String firstI = com.materialstock.util.PinyinUtil.toInitials(firstChar);
        return (!firstP.isEmpty() && firstP.startsWith(q))
            || (!firstI.isEmpty() && firstI.startsWith(q));
    }

    /** 打开投影 → 进入详情页 */
    private void openProject(String fileName) {
        this.status = "正在加载：" + fileName + "…";
        Map<Item, Integer> counts = MaterialListHelper.loadSchematicMaterials(fileName);
        if (counts == null || counts.isEmpty()) {
            this.status = "加载失败：无法解析该投影文件（" + fileName + "）";
            return;
        }
        this.materialCounts = counts;
        this.rebuildCategorized();
        this.scrollOffset = 0;
        this.showDiff = false;
        this.schematicFiles.clear();
        this.schematicFiles.addAll(MaterialListHelper.listSchematicFiles());
        if (!this.schematicFiles.contains(fileName)) {
            this.schematicFiles.add(0, fileName);
        }
        StockConfig.get().lastSchematic = fileName;
        this.currentSchematic = fileName;
        StockConfig.save();
        this.mode = Mode.DETAIL;
        this.status = "已加载：" + fileName + "（" + counts.size() + " 种物品）";
        this.initGui();
    }

    private void drawHint(GuiGraphics drawContext) {
        int bottom = this.getScreenHeight() - 22;
        // 每帧实时读取当前绑定：玩家在游戏设置里改键后，这里立即显示新键位
        String yKey = KeyBindings.OPEN_GUI.getTranslatedKeyMessage().getString();
        String kKey = KeyBindings.SELECT_POINT.getTranslatedKeyMessage().getString();
        String hKey = KeyBindings.TOGGLE_MATERIAL_VIS.getTranslatedKeyMessage().getString();
        this.drawString(drawContext, "快捷键：[" + yKey + "] 主界面 · [" + kKey + "] 框选角点 · [" + hKey + "] 材料区高亮开关",
            12, bottom, 0xFF55FFFF);
        if (this.keySettingsBtn != null) {
            this.keySettingsBtn.setX(Math.max(136, this.getScreenWidth() - 120));
        }
    }

    // ==================== 详情页 ====================

    private void initDetailPage(StockConfig config) {
        int sw = this.getScreenWidth();

        // ---- 返回 ----
        ButtonGeneric backBtn = new ButtonGeneric(10, CONTROL_Y, 56, 16, "← 返回");
        backBtn.setActionListener((b, mb) -> {
            this.mode = Mode.LIST;
            this.initGui();
        });
        this.addBtn(backBtn);

        // ---- 行1：返回 + 物品搜索框 + 数量倍数 ----
        int dropW = Math.max(120, Math.min(280, sw - 320));
        this.detailSearch = new GuiTextFieldGeneric(74, CONTROL_Y - 2, dropW, 16, net.minecraft.client.Minecraft.getInstance().font);
        this.detailSearch.setTextWrapper("");
        this.addTextField(this.detailSearch, null);
        // 空间足够时在搜索框右侧显示提示（小屏自动省略）
        if (74 + dropW + 4 + 150 < sw - 130) {
            this.addLabel(74 + dropW + 4, CONTROL_Y, 150, 16, 0xFF888888, "搜物品或状态(忽略/缺失/完成)");
        }

        this.addLabel(sw - 124, CONTROL_Y, 52, 16, 0xFFAAAAAA, "数量倍数:");
        this.multiplierField = new GuiTextFieldInteger(sw - 70, CONTROL_Y - 2, 40, 16, net.minecraft.client.Minecraft.getInstance().font);
        this.multiplierField.setTextWrapper(String.valueOf(config.multiplier));
        this.addTextField(this.multiplierField, null);

        // ---- 行2：信息显示 + 对比差异 + 排列方式 + 恢复全部忽略 ----
        int row2 = CONTROL_Y + 20;

        ButtonGeneric loadBtn = new ButtonGeneric(10, row2, 56, 16, "信息显示");
        loadBtn.setActionListener((button, mouseButton) -> this.toggleHud());
        this.addBtn(loadBtn);

        ButtonGeneric scanBtn = new ButtonGeneric(74, row2, 64, 16, "对比差异");
        scanBtn.setActionListener((button, mouseButton) -> {
            this.status = "正在扫描备货区并对比差异…";
            StockAreaScanner.scanAll(() -> {
                this.refreshAvailable();
                int mult = this.currentMultiplier();
                java.util.List<String> batchIds = new ArrayList<>();
                java.util.List<Integer> batchCounts = new ArrayList<>();
                int noRecipe = 0;
                // 按详情页当前排列顺序：类别模式按 categorized 大类顺序；数量模式按数量排
                List<Item> ordered = new ArrayList<>();
                if (StockConfig.get().sortMode > 0) {
                    // 数量模式：全部缺失物品按需求量排序
                    List<Item> all = new ArrayList<>();
                    for (List<Item> li : this.categorized.values()) all.addAll(li);
                    all.sort(java.util.Comparator.comparingInt(
                        (Item it) -> this.materialCounts.getOrDefault(it, 0) * mult));
                    if (StockConfig.get().sortMode == 2) {
                        java.util.Collections.reverse(all);   // 降序
                    }
                    ordered.addAll(all);
                } else {
                    // 类别模式：按 categorized 大类顺序
                    for (List<Item> li : this.categorized.values()) ordered.addAll(li);
                }
                for (Item it : ordered) {
                    int need = this.materialCounts.getOrDefault(it, 0) * mult;
                    int have = this.availableCounts.getOrDefault(it, 0);
                    int missing = need - have;
                    if (missing <= 0) continue;
                    if (com.materialstock.craft.CraftCalculator.findAllRecipes(it).isEmpty()) {
                        noRecipe++;
                        continue;
                    }
                    batchIds.add(BuiltInRegistries.ITEM.getKey(it).toString());
                    batchCounts.add(missing);
                }
                if (batchIds.isEmpty()) {
                    this.status = "对比完成：" + (noRecipe > 0
                        ? "无缺失或缺失物品均无配方（" + noRecipe + " 种无配方无法合成）"
                        : "备货区材料充足，无需合成");
                    return;
                }
                GuiStockCalculator.pendingBatchIds = batchIds;
                GuiStockCalculator.pendingBatchCounts = batchCounts;
                GuiBase.openGui(new GuiStockCalculator());
            });
        });
        this.addBtn(scanBtn);

        ButtonGeneric diffBtn = new ButtonGeneric(146, row2, 70, 16, this.sortModeLabel());
        diffBtn.setActionListener((button, mouseButton) -> {
            StockConfig cfg2 = StockConfig.get();
            cfg2.sortMode = (cfg2.sortMode + 1) % 3;
            StockConfig.save();
            this.initGui();
        });
        this.addBtn(diffBtn);

        ButtonGeneric restoreBtn = new ButtonGeneric(224, row2, 96, 16, "恢复全部忽略");
        restoreBtn.setActionListener((b, mb) -> this.restoreAllIgnored());
        this.addBtn(restoreBtn);

        // 每次点开详情页都扫描一次备货区（刷新已有数量）
        if (config.autoScanOnOpen && !ContainerCache.getInstance().getPositions().isEmpty()) {
            this.scanAreas();
        } else {
            this.refreshAvailable();
        }
    }

    /** 排列方式按钮文字 */
    private String sortModeLabel() {
        int m = StockConfig.get().sortMode;
        return m == 0 ? "排列：类别" : (m == 1 ? "排列：升序" : "排列：倒序");
    }

    /** 信息显示：开启/关闭右下角信息浮窗（HUD） */
    private void toggleHud() {
        String sel = this.currentSchematic;
        if (sel != null && !sel.isEmpty()) {
            Map<Item, Integer> counts = MaterialListHelper.loadSchematicMaterials(sel);
            if (counts != null && !counts.isEmpty()) {
                HudRenderer.hudMaterials = counts;
            }
        }
        HudRenderer.hudEnabled = !HudRenderer.hudEnabled;
        StockConfig cfg = StockConfig.get();
        cfg.hudEnabled = HudRenderer.hudEnabled;
        cfg.hudSchematic = sel != null && !sel.isEmpty() ? sel : cfg.hudSchematic;
        StockConfig.save();
        this.status = HudRenderer.hudEnabled
            ? "信息浮窗已开启（右下角；已忽略 / 已收集的材料不显示）"
            : "信息浮窗已关闭";
    }

    /** 大类/小类展开切换（写回配置，退游戏后保留） */
    private void toggleCollapse(String key) {
        if (this.collapsed.contains(key)) {
            this.collapsed.remove(key);
        } else {
            this.collapsed.add(key);
        }
        StockConfig.get().collapsedCats = new ArrayList<>(this.collapsed);
        StockConfig.save();
    }

    private boolean isIgnored(Item item) {
        return StockConfig.get().ignoredMaterials.contains(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private boolean isTracked(Item item) {
        return StockConfig.get().trackedItems.contains(BuiltInRegistries.ITEM.getKey(item).toString());
    }
    /** 追踪/取消追踪：追踪物品在信息浮窗最顶端显示 */
    private void toggleTrack(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        if (StockConfig.get().trackedItems.contains(id)) {
            StockConfig.get().trackedItems.remove(id);
            this.status = "已取消追踪：" + MaterialListHelper.getItemDisplayName(item);
        } else {
            StockConfig.get().trackedItems.add(id);
            this.status = "已追踪：" + MaterialListHelper.getItemDisplayName(item) + "（信息浮窗置顶）";
        }
        StockConfig.save();
    }

    /** 忽略/恢复忽略：信息浮窗不显示该材料（详情页仍显示） */
    private void toggleIgnore(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        StockConfig cfg = StockConfig.get();
        if (cfg.ignoredMaterials.contains(id)) {
            cfg.ignoredMaterials.remove(id);
            this.status = "已恢复忽略：" + MaterialListHelper.getItemDisplayName(item) + "（重新进入信息浮窗）";
        } else {
            cfg.ignoredMaterials.add(id);
            this.status = "已忽略：" + MaterialListHelper.getItemDisplayName(item) + "（信息浮窗不再显示，详情页仍显示）";
        }
        StockConfig.save();
        this.selectedItemId = null;
    }

    private void restoreAllIgnored() {
        StockConfig.get().ignoredMaterials.clear();
        StockConfig.save();
        this.status = "已恢复全部忽略的材料";
    }

    private void addTab(int x, int y, int width, String text, GuiBase target) {
        ButtonGeneric b = new ButtonGeneric(x, y, width, TAB_H, text);
        b.setActionListener((button, mouseButton) -> GuiBase.openGui(target));
        GuiTabs.applyHover(b, text);
        this.tabButtons.add(b);
        this.addBtn(b);
    }

    /** 页签悬停提示：最后绘制，避免被其它控件（含收藏下拉）盖住 */
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

    private void loadMaterialList() {
        String sel = this.currentSchematic;
        if (sel == null || sel.isEmpty()) {
            this.status = "请先加载一个投影文件";
            return;
        }
        this.openProject(sel);
    }

    private void rebuildCategorized() {
        Map<String, List<Item>> map = new LinkedHashMap<>();
        com.materialstock.StockConfig cfg = com.materialstock.StockConfig.get();
        List<String> cats = new ArrayList<>();
        for (CategoryHelper.Category c : CategoryHelper.CATEGORIES) {
            if (!cfg.removedCategories.contains(c.name)) cats.add(c.name);
        }
        cats.addAll(cfg.extraCategories);
        for (String cat : cats) {
            List<Item> items = new ArrayList<>();
            for (Item item : this.materialCounts.keySet()) {
                if (CategoryHelper.getCategory(item).equals(cat)) {
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                map.put(cat, items);
            }
        }
        this.categorized = map;
    }

    private void scanAreas() {
        this.status = "正在扫描备货区…";
        StockAreaScanner.scanAll(() -> {
            this.refreshAvailable();
            this.status = "备货区扫描完成：缓存 " + ContainerCache.getInstance().size() + " 个容器";
        });
    }

    private void buildContrast() {
        this.contrastMissing.clear();
        int multiplier = this.currentMultiplier();
        int totalMissing = 0;
        boolean enough = true;
        for (Map.Entry<Item, Integer> e : this.materialCounts.entrySet()) {
            int need = e.getValue() * multiplier;
            int have = this.availableCounts.getOrDefault(e.getKey(), 0);
            int missing = Math.max(0, need - have);
            if (missing > 0) {
                enough = false;
                totalMissing += missing;
                this.contrastMissing.add(new ContrastMissing(e.getKey(), missing));
            }
        }
        if (enough) {
            this.contrastStatus = "备货区材料充足，可以按当前需求全部合成！";
        } else {
            this.contrastStatus = "材料不足，不能全部合成：缺少 " + this.contrastMissing.size()
                + " 种物品，合计缺 " + totalMissing + " 个";
        }
    }

    private int currentMultiplier() {
        if (this.multiplierField == null) return 1;
        try {
            return Math.max(1, Integer.parseInt(this.multiplierField.getTextWrapper().trim()));
        } catch (Exception e) {
            return 1;
        }
    }

    private void refreshAvailable() {
        this.availableCounts = StockAreaScanner.summarizeAvailable();
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);
        if (this.mode == Mode.LIST) {
            this.drawListPage(drawContext);
        } else {
            this.drawDetailPage(drawContext);
        }
        // 页签悬停提示：最后绘制（列表页与详情页共用），浮在其它控件之上
        this.renderTabTooltips(drawContext, mouseX, mouseY);
    }

    private void drawListPage(GuiGraphics drawContext) {
        this.drawString(drawContext, this.status, 10, STATUS_Y, 0xFFFFFF00);
        // 底部快捷键提示：每帧实时读取当前绑定，改键后立即显示新键位
        this.drawHint(drawContext);

        // 搜索实时过滤（无需重建界面）
        if (this.searchField != null) {
            String q = this.searchField.getTextWrapper().trim().toLowerCase();
            if (!q.equals(this.lastQuery)) {
                this.lastQuery = q;
                this.refreshFilteredFiles();
            }
        }

        int listH = 20;
        int bottom = this.getScreenHeight() - 40;
        int right = this.getScreenWidth() - 10;
        StockConfig cfg = StockConfig.get();
        boolean searching = !this.filteredFiles.isEmpty();

        // ---- 已加载投影区（多行并存：选新的不替换旧的）----
        java.util.List<String> cachedNames = new java.util.ArrayList<>(MaterialListHelper.cachedProjectNames());
        int cachedRows = Math.min(searching ? 2 : 3, cachedNames.size());
        int listY;
        if (cachedNames.isEmpty()) {
            listY = 88;
        } else {
            this.drawString(drawContext, "已加载投影（点击进入查看，选新的不会替换旧的）:", 12, 84, 0xFFAAAAAA);
            int y = 96;
            for (int i = 0; i < cachedRows; i++) {
                String name = cachedNames.get(i);
                boolean current = name.equals(cfg.lastSchematic);
                this.drawString(drawContext, (current ? "◀ " : "") + name, 12, y + 5, current ? 0xFF55FFFF : 0xFFCCCCCC);
                this.drawString(drawContext, "[再次加载]", right - 210, y + 5, 0xFF55AAFF);
                this.drawString(drawContext, "[清除缓存]", right - 112, y + 5, 0xFFFF5555);
                y += listH;
            }
            listY = y + 8;
        }

        if (!searching) {
            // 未搜索：提示在已加载区下面
            if (this.lastQuery.isEmpty()) {
                this.drawString(drawContext, "默认不展示投影：在上方输入关键字搜索，匹配的投影会显示在下面。", 14, listY, 0xFF888888);
            } else {
                this.drawString(drawContext, "没有匹配的投影。请确认投影文件在 litematica 的投影目录中，或点“刷新列表”。", 14, listY, 0xFF888888);
            }
            return;
        }

        // 已搜索：搜索匹配结果列表
        int maxVisible = Math.max(1, (bottom - 20 - listY) / listH);
        int maxScroll = Math.max(0, this.filteredFiles.size() - maxVisible);
        if (this.fileScroll > maxScroll) this.fileScroll = maxScroll;
        if (this.fileScroll < 0) this.fileScroll = 0;

        for (int i = this.fileScroll; i < this.filteredFiles.size() && i < this.fileScroll + maxVisible; i++) {
            String name = this.filteredFiles.get(i);
            int y = listY + (i - this.fileScroll) * listH;
            boolean cached = MaterialListHelper.isCached(name);
            this.drawString(drawContext, name, 12, y + 5, cached ? 0xFF88FF88 : 0xFFCCCCCC);
            this.drawString(drawContext, cached ? "[已缓存]" : "[未加载]", right - 205, y + 5, cached ? 0xFF88FF88 : 0xFF777777);
            if (cached) {
                this.drawString(drawContext, "[清除缓存]", right - 140, y + 5, 0xFFFF5555);
            }
            this.drawString(drawContext, cached ? "[进入查看]" : "[加载]", right - 88, y + 5, 0xFF55AAFF);
        }

        // 滚动条
        if (maxScroll > 0) {
            int listHTotal = bottom - 20 - listY;
            int barH = Math.max(12, listHTotal * listHTotal / Math.max(1, this.filteredFiles.size() * listH));
            int barY = listY + (listHTotal - barH) * this.fileScroll / maxScroll;
            drawContext.fill(this.getScreenWidth() - 4, barY, this.getScreenWidth() - 2, barY + barH, 0xAAFFFFFF);
        }
    }

    private void drawDetailPage(GuiGraphics drawContext) {
        int multiplier = this.currentMultiplier();
        if (StockConfig.get().multiplier != multiplier) {
            StockConfig.get().multiplier = multiplier;
            StockConfig.save();   // 倍数变化立即落盘，退游戏也保留
        }
        int contentBottom = this.getScreenHeight() - 30;

        // 状态行（第二行按钮下方）
        this.drawString(drawContext, this.status, 10, STATUS_Y + 18, 0xFFFFFF00);

        // 合计
        int totalNeed = 0;
        int totalHave = 0;
        int totalMissing = 0;
        for (Map.Entry<Item, Integer> e : this.materialCounts.entrySet()) {
            int need = e.getValue() * multiplier;
            int have = this.availableCounts.getOrDefault(e.getKey(), 0);
            totalNeed += need;
            totalHave += Math.min(need, have);
            totalMissing += Math.max(0, need - have);
        }
        this.drawString(drawContext, String.format("合计：需要 %d · 备货区已有 %d · 缺少 %d", totalNeed, totalHave, totalMissing), 10, contentBottom + 6, 0xFF55FFFF);

        if (this.materialCounts.isEmpty()) {
            this.drawString(drawContext, "未加载投影。请在列表页搜索并选择一个投影文件。", 14, CONTENT_TOP + 10, 0xFF888888);
            return;
        }

        if (this.showDiff) {
            this.drawContrastPage(drawContext, contentBottom, multiplier);
            return;
        }

        // 分类列表
        List<Row> rows = this.buildRows();
        int rowsHeight = rows.size() * LINE_H;
        int maxScroll = Math.max(0, rowsHeight - (contentBottom - CONTENT_TOP));
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
        if (this.scrollOffset < 0) this.scrollOffset = 0;

        int y = CONTENT_TOP - this.scrollOffset;
        int contentRight = this.getScreenWidth() - 10;
        for (Row r : rows) {
            if (y + LINE_H < CONTENT_TOP) {
                y += LINE_H;
                continue;
            }
            if (y > contentBottom) break;
            this.drawString(drawContext, r.text, r.indent, y, r.color);
            // 选中材料行：紧跟材料文字后面显示 忽略/恢复忽略 选项
            if (!r.header && r.item != null && this.selectedItemId != null
                    && this.selectedItemId.equals(BuiltInRegistries.ITEM.getKey(r.item).toString())) {
                boolean ign = this.isIgnored(r.item);
                int bx = r.indent + net.minecraft.client.Minecraft.getInstance().font.width(r.text) + 4;
                String ignBtn = ign ? "[恢复忽略]" : "[忽略]";
                this.drawString(drawContext, ignBtn, bx, y, ign ? 0xFF55FFFF : 0xFFFFAA55);
                int calcX = bx + net.minecraft.client.Minecraft.getInstance().font.width(ignBtn) + 4;
                this.drawString(drawContext, "[计算配方]", calcX, y, 0xFF55FFFF);
                boolean trk = this.isTracked(r.item);
                String trkBtn = trk ? "[取消追踪]" : "[追踪]";
                this.drawString(drawContext, trkBtn, calcX + net.minecraft.client.Minecraft.getInstance().font.width("[计算配方]") + 4, y, 0xFFFFFF66);
            }
            if (maxScroll > 0) {
                int barH = Math.max(12, (contentBottom - CONTENT_TOP) * (contentBottom - CONTENT_TOP) / rowsHeight);
                int barY = CONTENT_TOP + (contentBottom - CONTENT_TOP - barH) * this.scrollOffset / maxScroll;
                drawContext.fill(contentRight, barY, contentRight + 2, barY + barH, 0xAAFFFFFF);
            }
            y += LINE_H;
        }
    }

    /** 差异对比视图：显示能否全部合成 + 缺少项清单（同界面展示） */
    private void drawContrastPage(GuiGraphics drawContext, int contentBottom, int multiplier) {
        boolean enough = this.contrastMissing.isEmpty();
        int bannerColor = enough ? 0xFF88FF88 : 0xFFFF5555;
        this.drawString(drawContext, this.contrastStatus, 14, CONTENT_TOP + 4, bannerColor);

        if (this.availableCounts.isEmpty()) {
            this.drawString(drawContext, "备货区暂无缓存，请先点“扫描备货区”或“对比差异”后再查看。", 14, CONTENT_TOP + 20, 0xFFFFAA55);
            return;
        }

        if (enough) {
            this.drawString(drawContext, "全部材料均满足需求，无需补充。", 14, CONTENT_TOP + 20, 0xFF88FF88);
            return;
        }

        int listTop = CONTENT_TOP + 22;
        int rowsHeight = this.contrastMissing.size() * LINE_H;
        int maxScroll = Math.max(0, rowsHeight - (contentBottom - listTop));
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;
        if (this.scrollOffset < 0) this.scrollOffset = 0;

        int y = listTop - this.scrollOffset;
        int contentRight = this.getScreenWidth() - 10;
        for (ContrastMissing cm : this.contrastMissing) {
            if (y + LINE_H < listTop) {
                y += LINE_H;
                continue;
            }
            if (y > contentBottom) break;
            int need = this.materialCounts.getOrDefault(cm.item, 0) * multiplier;
            int have = this.availableCounts.getOrDefault(cm.item, 0);
            String name = MaterialListHelper.getItemDisplayName(cm.item);
            this.drawString(drawContext,
                String.format("缺少 %s  需要 %d · 已有 %d · 缺 %d", name, need, have, cm.missing),
                24, y, 0xFFFF5555);
            if (maxScroll > 0) {
                int barH = Math.max(12, (contentBottom - listTop) * (contentBottom - listTop) / rowsHeight);
                int barY = listTop + (contentBottom - listTop - barH) * this.scrollOffset / maxScroll;
                drawContext.fill(contentRight, barY, contentRight + 2, barY + barH, 0xAAFFFFFF);
            }
            y += LINE_H;
        }
    }

    /** 木种优先级（创造模式顺序：橡木→云杉→白桦→丛林→金合欢→深色橡木→红树→樱花→竹→苍白橡木→绯红→诡异） */
    private static final List<String> WOODS = List.of(
        "oak", "spruce", "birch", "jungle", "acacia", "dark_oak",
        "mangrove", "cherry", "bamboo", "pale_oak", "crimson", "warped");
    private static final String[] WOOD_NAMES = {
        "橡木", "云杉木", "白桦木", "丛林木", "金合欢木", "深色橡木",
        "红树木", "樱花木", "竹子", "苍白橡木", "绯红木", "诡异木"};

    /** 物品属于哪个木种（按 ID 前缀；去皮木材 stripped_* 也算对应木种）；不属于返回 -1 */
    private static int woodIndex(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        if (path.startsWith("stripped_")) {
            path = path.substring("stripped_".length());
        }
        // 下界疣块：随绯红木类（crimson，w10）
        if (path.equals("nether_wart_block")) {
            return 10;
        }
        for (int i = 0; i < WOODS.size(); i++) {
            String w = WOODS.get(i);
            if (path.equals(w) || path.startsWith(w + "_")) {
                return i;
            }
        }
        return -1;
    }

    private static String woodName(int idx) {
        return WOOD_NAMES[Math.min(idx, WOOD_NAMES.length - 1)] + "类";
    }

    /** 常见材料前缀 → 中文小类名（未列出的显示前缀原文） */
    private static final java.util.Map<String, String> PREFIX_NAMES = new java.util.HashMap<>();
    static {
        PREFIX_NAMES.put("stone", "石头");
        PREFIX_NAMES.put("cobblestone", "圆石");
        PREFIX_NAMES.put("deepslate", "深板岩");
        PREFIX_NAMES.put("granite", "花岗岩");
        PREFIX_NAMES.put("diorite", "闪长岩");
        PREFIX_NAMES.put("andesite", "安山岩");
        PREFIX_NAMES.put("tuff", "凝灰岩");
        PREFIX_NAMES.put("brick", "红砖");
        PREFIX_NAMES.put("quartz", "石英");
        PREFIX_NAMES.put("sandstone", "砂岩");
        PREFIX_NAMES.put("prismarine", "海晶石");
        PREFIX_NAMES.put("purpur", "紫珀");
        PREFIX_NAMES.put("blackstone", "黑石");
        PREFIX_NAMES.put("basalt", "玄武岩");
        PREFIX_NAMES.put("end_stone", "末地石");
        PREFIX_NAMES.put("terracotta", "陶瓦");
        PREFIX_NAMES.put("concrete", "混凝土");
        PREFIX_NAMES.put("glass", "玻璃");
        PREFIX_NAMES.put("wool", "羊毛");
        PREFIX_NAMES.put("iron", "铁");
        PREFIX_NAMES.put("gold", "金");
        PREFIX_NAMES.put("copper", "铜");
        PREFIX_NAMES.put("diamond", "钻石");
        PREFIX_NAMES.put("emerald", "绿宝石");
        PREFIX_NAMES.put("redstone", "红石");
        PREFIX_NAMES.put("obsidian", "黑曜石");
        PREFIX_NAMES.put("snow", "雪");
        PREFIX_NAMES.put("ice", "冰");
        PREFIX_NAMES.put("mud", "泥巴");
        PREFIX_NAMES.put("calcite", "方解石");
        PREFIX_NAMES.put("amethyst", "紫水晶");
        PREFIX_NAMES.put("netherite", "下界合金");
        PREFIX_NAMES.put("nether", "下界");
        PREFIX_NAMES.put("sculk", "幽匿");
        PREFIX_NAMES.put("slime", "黏液");
        PREFIX_NAMES.put("honey", "蜂蜜");
        PREFIX_NAMES.put("sea", "海");
        PREFIX_NAMES.put("glow", "荧光");
        PREFIX_NAMES.put("moss", "苔藓");
        PREFIX_NAMES.put("dripstone", "钟乳石");
        PREFIX_NAMES.put("sand", "沙子");
        PREFIX_NAMES.put("gravel", "沙砾");
        PREFIX_NAMES.put("clay", "黏土");
        PREFIX_NAMES.put("soul", "灵魂");
        PREFIX_NAMES.put("magma", "岩浆");
        PREFIX_NAMES.put("lantern", "灯笼");
        PREFIX_NAMES.put("candle", "蜡烛");
        PREFIX_NAMES.put("chain", "锁链");
        PREFIX_NAMES.put("sponge", "海绵");
        PREFIX_NAMES.put("vine", "藤蔓");
        PREFIX_NAMES.put("torch", "火把");
        PREFIX_NAMES.put("lever", "拉杆");
        PREFIX_NAMES.put("piston", "活塞");
        PREFIX_NAMES.put("rail", "铁轨");
        PREFIX_NAMES.put("dropper", "投掷器");
        PREFIX_NAMES.put("dispenser", "发射器");
        PREFIX_NAMES.put("tnt", "TNT");
        PREFIX_NAMES.put("bell", "钟");
        PREFIX_NAMES.put("beacon", "信标");
        PREFIX_NAMES.put("anvil", "铁砧");
        PREFIX_NAMES.put("bookshelf", "书架");
        PREFIX_NAMES.put("enchanting", "附魔");
        PREFIX_NAMES.put("bone", "骨头");
        PREFIX_NAMES.put("flint", "燧石");
        PREFIX_NAMES.put("hay", "干草");
        PREFIX_NAMES.put("sandstone", "砂岩");
        PREFIX_NAMES.put("dye", "染料");
        PREFIX_NAMES.put("tulip", "郁金香");
        PREFIX_NAMES.put("mushroom", "蘑菇");
        PREFIX_NAMES.put("sugar", "甘蔗");
        PREFIX_NAMES.put("cactus", "仙人掌");
        PREFIX_NAMES.put("wheat", "小麦");
        PREFIX_NAMES.put("ladder", "梯子");
        PREFIX_NAMES.put("scaffolding", "脚手架");
        PREFIX_NAMES.put("cobweb", "蜘蛛网");
        PREFIX_NAMES.put("lever", "拉杆");
        PREFIX_NAMES.put("string", "线");
        PREFIX_NAMES.put("feather", "羽毛");
        PREFIX_NAMES.put("leather", "皮革");
        PREFIX_NAMES.put("gunpowder", "火药");
        PREFIX_NAMES.put("arrow", "箭");
        PREFIX_NAMES.put("bucket", "桶");
        PREFIX_NAMES.put("potato", "土豆");
        PREFIX_NAMES.put("carrot", "胡萝卜");
        PREFIX_NAMES.put("beetroot", "甜菜根");
        PREFIX_NAMES.put("pumpkin", "南瓜");
        PREFIX_NAMES.put("melon", "西瓜");
        PREFIX_NAMES.put("cookie", "曲奇");
        PREFIX_NAMES.put("cake", "蛋糕");
        PREFIX_NAMES.put("potted", "盆栽");
        PREFIX_NAMES.put("crying", "下界");
        PREFIX_NAMES.put("bread", "面包");
    }

    /** 主前缀：循环剥离 装饰词 + 染色前缀 后取 id 第一个词；无 "_" 返回 null */
    private static final String[] DECO_WORDS = {
        "mossy_", "polished_", "cut_", "chiseled_", "smooth_", "cracked_", "weathered_",
        "exposed_", "oxidized_", "infested_", "coarse_", "stained_", "waxed_", "gilded_", "dark_", "cobbled_",
        "red_", "orange_", "white_", "black_", "blue_", "brown_", "cyan_", "gray_",
        "green_", "light_blue_", "light_gray_", "lime_", "magenta_", "pink_", "purple_", "yellow_"
    };

    private static String mainPrefix(String path) {
        // 语义特例：哭泣的黑曜石 → 下界类（nether 组）
        if (path.equals("crying_obsidian")) {
            return "nether";
        }
        boolean changed;
        do {
            changed = false;
            for (String d : DECO_WORDS) {
                if (path.startsWith(d)) {
                    path = path.substring(d.length());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        int u = path.indexOf('_');
        if (u < 0) {
            // 裸物品（如 brick/glass/quartz）：等于已知前缀时归入对应小类
            return PREFIX_NAMES.containsKey(path) ? path : null;
        }
        return path.substring(0, u);
    }

    /** 分组键：小类名（复用 CategoryHelper.getSub，与信息浮窗一致） */
    private static String groupKey(Item item) {
        return CategoryHelper.getSub(item);
    }

    /** 小类标题名：直接使用表内小类名（橡木类 / 圆石类 / …） */
    private static String subName(String key) {
        return key;
    }

    /** lue/nue 与 lve/nve 等价（忽略=hulve，用户常按输入法打成 hulue） */
    private static String normalizePinyin(String s) {
        return s.replace("lue", "lve").replace("nue", "nve");
    }

    /**
     * 搜索词是否命中某状态词：支持 中文 / 全拼 / 首字母，且均为【前缀】匹配
     * （用户从左往右打字，前缀才有意义）。
     *
     * 必须用前缀而非子串包含：状态词里"未收集"含有"收集"，
     * 若用 contains，输入"收集"会先命中"未收集"，筛出【未收集】——结果与意图完全相反。
     * 例：命中"忽略"的有 忽略 / hl / hulve / hulue / hu；
     *     "收集"只命中【完成】，"未收集"只命中【缺失】。
     */
    private static boolean matchesStateWord(String ql, String... words) {
        if (ql == null || ql.isEmpty()) return false;
        String q = normalizePinyin(ql);
        for (String w : words) {
            if (w == null || w.isEmpty()) continue;
            if (w.startsWith(ql)) return true;                                             // 中文前缀
            if (normalizePinyin(com.materialstock.util.PinyinUtil.toFull(w)).startsWith(q)) return true;     // 全拼前缀
            if (normalizePinyin(com.materialstock.util.PinyinUtil.toInitials(w)).startsWith(q)) return true; // 首字母前缀
        }
        return false;
    }

    /** 详情页搜索过滤：状态词（忽略/缺失/完成）或 名称/ID/拼音 匹配 */
    private boolean matchesDetailFilter(Item item, int multiplier) {
        String q = this.detailSearch == null ? "" : this.detailSearch.getTextWrapper().trim();
        int need = this.materialCounts.getOrDefault(item, 0) * multiplier;
        int have = this.availableCounts.getOrDefault(item, 0);
        boolean collected = have >= need;
        boolean ignored = this.isIgnored(item);
        if (!q.isEmpty()) {
            String ql = q.toLowerCase();
            // 状态词优先于物品名匹配；状态词都命中不了时才回退到 物品名/ID/拼音。
            // 顺序：忽略 → 缺失 → 完成。因 matchesStateWord 用前缀匹配，
            // "收集"不会再误命中"未收集"（旧版 contains 写法有此问题，会筛出相反结果）。
            if (matchesStateWord(ql, "忽略")) {
                if (!ignored) return false;
            } else if (matchesStateWord(ql, "缺失", "未收集", "缺少", "缺")) {
                if (collected) return false;
            } else if (matchesStateWord(ql, "完成", "收集")) {
                if (!collected) return false;
            } else {
                String name = MaterialListHelper.getItemDisplayName(item);
                String id = BuiltInRegistries.ITEM.getKey(item).toString();
                if (!name.toLowerCase().contains(ql)
                    && !id.toLowerCase().contains(ql)
                    && !com.materialstock.util.PinyinUtil.toFull(name).contains(ql)
                    && !com.materialstock.util.PinyinUtil.toInitials(name).contains(ql)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Row makeRow(Item item, int multiplier, int indent) {
        int need = this.materialCounts.get(item) * multiplier;
        int have = this.availableCounts.getOrDefault(item, 0);
        boolean collected = have >= need;
        boolean ignored = this.isIgnored(item);
        String name = MaterialListHelper.getItemDisplayName(item);
        String text = (collected ? "☑ " : (ignored ? "⊘ " : ""))
            + name + " × " + MaterialListHelper.formatQty(need)
            + "   已有 " + MaterialListHelper.formatQty(have)
            + " / 缺 " + MaterialListHelper.formatQty(Math.max(0, need - have));
        int color;
        if (ignored) {
            color = 0xFFAAAAAA;    // 忽略的材料：灰色
        } else if (collected) {
            color = 0xFF88FF88;    // 已收集：绿色
        } else {
            color = 0xFFFFAA55;    // 缺少：橙色
        }
        return new Row(false, null, item, text, color, collected, indent);
    }

    private List<Row> buildRows() {
        List<Row> rows = new ArrayList<>();
        int multiplier = this.currentMultiplier();

        // 数量排序模式：摊平全部物品，按所需数量升/降序；已收集(备齐)的统一沉底
        if (StockConfig.get().sortMode > 0) {
            List<Item> all = new ArrayList<>();
            for (List<Item> li : this.categorized.values()) {
                all.addAll(li);
            }
            all.removeIf(it -> !this.matchesDetailFilter(it, multiplier));
            List<Item> pend = new ArrayList<>();
            List<Item> got = new ArrayList<>();
            for (Item it : all) {
                int need0 = this.materialCounts.getOrDefault(it, 0) * multiplier;
                int have0 = this.availableCounts.getOrDefault(it, 0);
                (have0 >= need0 ? got : pend).add(it);
            }
            java.util.Comparator<Item> cmp = java.util.Comparator
                .comparingInt((Item it) -> this.materialCounts.getOrDefault(it, 0) * multiplier);
            if (StockConfig.get().sortMode == 2) {
                cmp = cmp.reversed();
            }
            pend.sort(cmp.thenComparing(it -> BuiltInRegistries.ITEM.getKey(it).toString()));
            got.sort(java.util.Comparator.comparing(it -> BuiltInRegistries.ITEM.getKey(it).toString()));
            for (Item item : pend) {
                rows.add(this.makeRow(item, multiplier, 0));
            }
            for (Item item : got) {
                rows.add(this.makeRow(item, multiplier, 0));
            }
            return rows;
        }

        List<Row> allDone = new ArrayList<>();   // 已收集行：跨大类统一沉到整个列表最底部
        String sq = this.detailSearch == null ? "" : this.detailSearch.getTextWrapper().trim();
        boolean searching = !sq.isEmpty();   // 搜索中：空大类隐藏、大小类全部强制展开
        for (Map.Entry<String, List<Item>> e : this.categorized.entrySet()) {
            String cat = e.getKey();
            List<Item> items = new ArrayList<>(e.getValue());
            // 详情页搜索过滤（物品名/ID/拼音 + 状态词：已隐藏/已收集/未收集）
            items.removeIf(it -> !this.matchesDetailFilter(it, multiplier));
            if (searching && items.isEmpty()) continue;   // 无匹配物品的大类整个隐藏
            // 大类内排序：木种按创造顺序，其余按物品ID（近似创造排序）
            items.sort(java.util.Comparator
                .comparingInt((Item it) -> woodIndex(it) < 0 ? 999 : woodIndex(it))
                .thenComparing(it -> BuiltInRegistries.ITEM.getKey(it).toString()));
            long catTotal = 0;
            int collected = 0;
            int ignored = 0;
            for (Item item : items) {
                int need = this.materialCounts.get(item) * multiplier;
                int have = this.availableCounts.getOrDefault(item, 0);
                catTotal += need;
                if (have >= need) collected++;
                if (this.isIgnored(item)) ignored++;
            }
            boolean open = searching || !this.collapsed.contains(cat);   // 搜索时大类强制展开
            String catTitle = (open ? "▼ " : "▶ ") + cat + "（" + items.size() + " 种，共 " + catTotal + " 个 · 已收集 " + collected + " 种 · 已忽略 " + ignored + " 种）";
            int catColor = 0xFFFFFF55;
            if (collected == items.size() || ignored == items.size()) {
                catTitle = "✓ " + catTitle;
                catColor = 0xFF88FF88;
            }
            rows.add(new Row(true, cat, null, catTitle, catColor, false, 10));
            if (!open) {
                // 折叠大类：已收集(未忽略)的物品仍抽到全局末尾沉底显示
                for (Item it : items) {
                    int needF = this.materialCounts.getOrDefault(it, 0) * multiplier;
                    int haveF = this.availableCounts.getOrDefault(it, 0);
                    if (haveF >= needF && !this.isIgnored(it)) {
                        allDone.add(this.makeRow(it, multiplier, 0));
                    }
                }
                continue;
            }

            // 按主前缀分组（木种 w0..w11 创造顺序；其他 p<prefix> 字母序；无前缀独立）
            Map<String, List<Item>> groups = new LinkedHashMap<>();
            List<Item> singles = new ArrayList<>();
            for (Item item : items) {
                String key = groupKey(item);
                if (key == null) {
                    singles.add(item);
                } else {
                    groups.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
                }
            }
            List<Row> done = new ArrayList<>();      // 已收集行（排大类最底）
            List<Row> subTitleRows = new ArrayList<>();  // 小类标题 + 紧跟其后的展开材料（在前）
            List<Row> looseRows = new ArrayList<>();     // 零散物品（<4 种组直接铺开 + 无前缀，在后）
            java.util.List<java.util.Map.Entry<String, List<Item>>> glist =
                new ArrayList<>(groups.entrySet());
            glist.sort(java.util.Comparator
                .comparingInt((java.util.Map.Entry<String, List<Item>> ge) -> CategoryData.subOrder(cat, ge.getKey()))
                .thenComparing(ge -> ge.getKey()));
            for (java.util.Map.Entry<String, List<Item>> g : glist) {
                String key = g.getKey();
                List<Item> gl = g.getValue();
                // 表上有小类的物品已按小类分组 -> 一律显示小类标题（小类默认折叠）
                String subKey = cat + ":" + key;
                boolean subOpen = searching || StockConfig.get().collapsedCats.contains(subKey);   // 搜索时小类强制展开
                // 小类标题只算未收集的种数（已收集的沉到全局底部）
                int pendCnt = 0;
                for (Item item : gl) {
                    int needS = this.materialCounts.getOrDefault(item, 0) * multiplier;
                    int haveS = this.availableCounts.getOrDefault(item, 0);
                    if (!(haveS >= needS) && !this.isIgnored(item)) pendCnt++;
                }
                subTitleRows.add(new Row(true, subKey, null,
                    (subOpen ? "▼ " : "▶ ") + subName(key) + "（" + pendCnt + " 种）", 0xFFFFFF88, false, 24));
                if (subOpen) {
                    for (Item item : gl) {
                        Row sr = this.makeRow(item, multiplier, 38);
                        if (sr.collected) done.add(sr);       // 已收集排大类最底
                        else subTitleRows.add(sr);            // 未收集紧跟小类标题下方
                    }
                } else {
                    // 小类折叠：已收集的物品仍抽到全局底部沉底显示
                    for (Item item : gl) {
                        Row sr = this.makeRow(item, multiplier, 38);
                        if (sr.collected) done.add(sr);
                    }
                }
            }
            // 零散区：无前缀材料行
            for (Item item : singles) {
                Row sr = this.makeRow(item, multiplier, 24);
                if (sr.collected) done.add(sr);
                else looseRows.add(sr);
            }
            rows.addAll(subTitleRows);   // 先小类
            rows.addAll(looseRows);      // 后零散
            allDone.addAll(done);        // 已收集：先收集，最后统一沉底
        }
        rows.addAll(allDone);            // 整个列表最底部：所有已收集
        return rows;
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) return false;

        if (this.mode == Mode.LIST) {
            int right = this.getScreenWidth() - 10;
            boolean searching = !this.filteredFiles.isEmpty();
            java.util.List<String> cachedNames = new java.util.ArrayList<>(MaterialListHelper.cachedProjectNames());
            int cachedRows = Math.min(searching ? 2 : 3, cachedNames.size());

            // 已加载投影区（多行并存）：点击行进入 / 再次加载 / 清除缓存
            if (!cachedNames.isEmpty() && mouseY >= 96 && mouseY <= 96 + cachedRows * 20) {
                int idx = (mouseY - 96) / 20;
                if (idx >= 0 && idx < cachedRows) {
                    String name = cachedNames.get(idx);
                    if (mouseX >= right - 210 && mouseX <= right - 120) {
                        this.openProject(name);
                        return true;
                    }
                    if (mouseX >= right - 112 && mouseX <= right - 12) {
                        MaterialListHelper.clearMaterialCache(name);
                        this.status = "已清除缓存：" + name;
                        com.materialstock.render.HudRenderer.hudEnabled = false;
                        com.materialstock.render.HudRenderer.hudMaterials.clear();
                        return true;
                    }
                    if (mouseX >= 12 && mouseX <= right - 220) {
                        this.openProject(name);
                        return true;
                    }
                }
                return false;
            }

            // 搜索匹配列表区：点击行加载 / 点击清除缓存
            int listY = cachedNames.isEmpty() ? 88 : (96 + cachedRows * 20 + 8);
            if (searching && mouseY >= listY && mouseY <= this.getScreenHeight() - 60) {
                int listH = 20;
                int idx = (mouseY - listY) / listH + this.fileScroll;
                if (idx >= 0 && idx < this.filteredFiles.size()) {
                    String name = this.filteredFiles.get(idx);
                    if (mouseX >= right - 140 && mouseX <= right - 94 && MaterialListHelper.isCached(name)) {
                        MaterialListHelper.clearMaterialCache(name);
                        this.status = "已清除缓存：" + name;
                        com.materialstock.render.HudRenderer.hudEnabled = false;
                        com.materialstock.render.HudRenderer.hudMaterials.clear();
                        return true;
                    }
                    if (mouseX >= 12 && mouseX <= right - 20) {
                        this.openProject(name);
                        return true;
                    }
                }
            }
            return false;
        }

        // 详情页
        if (this.showDiff) {
            int listTop = CONTENT_TOP + 22;
            if (mouseX >= 10 && mouseX <= this.getScreenWidth() - 20 && mouseY >= listTop && mouseY <= this.getScreenHeight() - 30) {
                int idx = (mouseY - listTop + this.scrollOffset) / LINE_H;
                if (idx >= 0 && idx < this.contrastMissing.size()) {
                    ContrastMissing cm = this.contrastMissing.get(idx);
                    GuiStockCalculator.pendingItemId =
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(cm.item).toString();
                    GuiBase.openGui(new GuiStockCalculator());
                    return true;
                }
            }
            return false;
        }
        // 点中滚动条滑块：开始拖拽（详情页）
        if (mouseButton == 0 && !this.showDiff) {
            int contentRight0 = this.getScreenWidth() - 10;
            int contentBottom0 = this.getScreenHeight() - 30;
            int viewH0 = contentBottom0 - CONTENT_TOP;
            List<Row> rowsD0 = this.buildRows();
            int rowsH0 = rowsD0.size() * LINE_H;
            int maxSc0 = Math.max(0, rowsH0 - viewH0);
            if (maxSc0 > 0 && mouseX >= contentRight0 - 3 && mouseX <= contentRight0 + 5) {
                int barH0 = Math.max(12, viewH0 * viewH0 / rowsH0);
                int barY0 = CONTENT_TOP + (viewH0 - barH0) * this.scrollOffset / maxSc0;
                if (mouseY >= barY0 - 2 && mouseY <= barY0 + barH0 + 2) {
                    this.draggingDetailScroll = true;
                    double frac0 = (double)(mouseY - CONTENT_TOP - barH0 / 2) / (viewH0 - barH0);
                    this.scrollOffset = (int)(Math.max(0, Math.min(1.0, frac0)) * maxSc0);
                    return true;
                }
            }
        }
        if (mouseY >= CONTENT_TOP && mouseY <= this.getScreenHeight() - 30) {
            int idx = (mouseY - CONTENT_TOP + this.scrollOffset) / LINE_H;
            List<Row> rows = this.buildRows();
            if (idx >= 0 && idx < rows.size()) {
                Row r = rows.get(idx);
                int right = this.getScreenWidth() - 10;
                net.minecraft.client.gui.Font fnt = net.minecraft.client.Minecraft.getInstance().font;
                if (r.header) {
                    // 只有点击标题文字区才折叠/展开
                    int hw = fnt.width(r.text);
                    if (mouseX >= 10 && mouseX <= 10 + hw + 8) {
                        this.toggleCollapse(r.catName);   // 大类与小类统一（小类 catName 含 ":"）
                        return true;
                    }
                    return false;
                }
                if (!r.header && r.item != null) {
                    String id = BuiltInRegistries.ITEM.getKey(r.item).toString();
                    int textEnd = r.indent + fnt.width(r.text) + 8;   // 材料文字区域右界
                    if (this.selectedItemId != null && this.selectedItemId.equals(id)) {
                        // 选中行的选项按钮：紧跟材料文字后面（忽略 / 恢复忽略）
                        boolean ign = this.isIgnored(r.item);
                        String btn = ign ? "[恢复忽略]" : "[忽略]";
                        int bx = r.indent + fnt.width(r.text) + 4;
                        int bw = fnt.width(btn);
                        if (mouseX >= bx && mouseX <= bx + bw) {
                            this.toggleIgnore(r.item);
                            return true;
                        }
                        // [计算配方]：跳转计算器，自动填入该材料需要的数量
                        String calcBtn = "[计算配方]";
                        int cx = bx + bw + 4;
                        int cw = net.minecraft.client.Minecraft.getInstance().font.width(calcBtn);
                        if (mouseX >= cx && mouseX <= cx + cw) {
                            int need = this.materialCounts.getOrDefault(r.item, 0) * this.currentMultiplier();
                            GuiStockCalculator.pendingMaterialId = id;
                            GuiStockCalculator.pendingMaterialCount = Math.max(1, need);
                            GuiBase.openGui(new GuiStockCalculator());
                            return true;
                        }
                        // [追踪]/[取消追踪]：信息浮窗置顶
                        String trackBtn = this.isTracked(r.item) ? "[取消追踪]" : "[追踪]";
                        int tx = cx + cw + 4;
                        int tw = net.minecraft.client.Minecraft.getInstance().font.width(trackBtn);
                        if (mouseX >= tx && mouseX <= tx + tw) {
                            this.toggleTrack(r.item);
                            return true;
                        }
                        // 点材料文字区：取消选中；空白处不触发
                        if (mouseX >= r.indent && mouseX <= textEnd) {
                            this.selectedItemId = null;
                            return true;
                        }
                        return false;
                    }
                    // 未选中：点材料文字区选中；空白处不触发
                    if (mouseX >= r.indent && mouseX <= textEnd) {
                        this.selectedItemId = id;
                        return true;
                    }
                    return false;
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
        if (this.mode == Mode.LIST) {
            this.fileScroll -= (int) (verticalAmount * 2);
            boolean searching = !this.filteredFiles.isEmpty();
            java.util.List<String> cachedNames = new java.util.ArrayList<>(MaterialListHelper.cachedProjectNames());
            int cachedRows = Math.min(searching ? 2 : 3, cachedNames.size());
            int listY = cachedNames.isEmpty() ? 88 : (96 + cachedRows * 20 + 8);
            int maxVisible = Math.max(1, (this.getScreenHeight() - 60 - listY) / 20);
            int maxScroll = Math.max(0, this.filteredFiles.size() - maxVisible);
            if (this.fileScroll > maxScroll) this.fileScroll = maxScroll;
            if (this.fileScroll < 0) this.fileScroll = 0;
            return true;
        }
        if (mouseX >= 10 && mouseX <= this.getScreenWidth() - 20 && mouseY >= CONTENT_TOP && mouseY <= this.getScreenHeight() - 30) {
            this.scrollOffset -= (int) (verticalAmount * 14);
            return true;
        }
        return false;
    }

    private static final class Row {
        final boolean header;
        final String catName;
        final Item item;
        final String text;
        final int color;
        final boolean collected;
        final int indent;

        Row(boolean header, String catName, Item item, String text, int color, boolean collected, int indent) {
            this.header = header;
            this.catName = catName;
            this.item = item;
            this.text = text;
            this.color = color;
            this.collected = collected;
            this.indent = indent;
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.draggingDetailScroll && button == 0) {
            int contentBottom = this.getScreenHeight() - 30;
            int viewH = contentBottom - CONTENT_TOP;
            List<Row> rowsD = this.buildRows();
            int rowsH = rowsD.size() * LINE_H;
            int maxSc = Math.max(0, rowsH - viewH);
            if (maxSc > 0) {
                int barH = Math.max(12, viewH * viewH / rowsH);
                double frac = (mouseY - CONTENT_TOP - barH / 2) / (viewH - barH);
                this.scrollOffset = (int)(Math.max(0, Math.min(1.0, frac)) * maxSc);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.draggingDetailScroll && button == 0) {
            this.draggingDetailScroll = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
}
