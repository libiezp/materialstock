package com.materialstock.gui;

import com.materialstock.StockConfig;
import com.materialstock.craft.CraftCalculator;
import com.materialstock.data.MaterialLine;
import com.materialstock.data.RecipeEntry;
import com.materialstock.util.QuantityFormat;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.GuiTextFieldInteger;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 合成计算器（改版）：
 * 顶部为 搜索框 + 搜索 + 清空选择 + 收藏框 + 加入收藏；
 * 支持多选成品（点击候选或选择收藏即添加一个成品，可继续添加多个一起计算）；
 * 成品确定后自动从游戏内置配方推导各成品的直接原料并汇总；
 * 材料行保留勾选功能（勾选才计入合计）、数量可修改，数量按"几盒+几组+几个"格式化。
 */
public class GuiStockCalculator extends GuiBase {
    private static final int TAB_Y = 22;
    private static final int TAB_H = 16;
    private static final int CAND_H = 12;
    private static final int PROD_H = 18;
    private static final int ROW_H = 18;
    /** 三区动态布局：候选/成品/材料 平均分配可用高度；余数先给材料再给成品 */
    private int candY = 60, candRows = 3, candH = 36;
    private int prodY = 96, prodRows = 3, prodH = 54;
    private int matY = 150, matRows = 6, matH = 108;

    /** 由材料清单页/差异页点击物品时预填（自动推导配方） */
    public static String pendingItemId = "";
    /** 详情页"计算配方"跳转：自动填入的材料ID与数量 */
    public static String pendingMaterialId = "";
    public static int pendingMaterialCount = 1;
    /** 详情页"对比差异"批量跳转：缺失且有配方的物品 */
    public static java.util.List<String> pendingBatchIds = new java.util.ArrayList<>();
    public static java.util.List<Integer> pendingBatchCounts = new java.util.ArrayList<>();
    /** 由材料区"加入计算器"传入的物品 ID（作为材料行） */
    public static String pendingAddMaterialId = "";

    /** 会话级保留：退出界面后再次打开时恢复的选择（开启"退出保留"时使用） */
    private static final List<ProductEntry> sessionProducts = new ArrayList<>();
    private static final Map<String, Boolean> sessionRowSelected = new LinkedHashMap<>();
    /** 候选列表滚动偏移（搜索框下候选区，滚轮翻页） */
    private int candidateScroll = 0;
    /** 所有按钮的矩形区域（x,y,w,h）：滚轮悬停在按钮上时不触发翻页，只有点击才响应 */
    private final List<int[]> buttonBounds = new ArrayList<>();
    /** 收藏按钮/下拉列表的动态 x 与宽度（随屏幕宽度自适应，GUI 缩放大时不溢出屏幕） */
    private int favX = 304;
    private int favW = 150;
    /** 收藏文本框（只读，同搜索框样式，点击展开下拉） */
    private GuiTextFieldGeneric favField = null;
    /** 成品标题文字（drawContents 中绘制，保证收藏列表浮顶能盖住） */
    private String prodTitleText = "";
    /** 收藏文本框当前显示文本（选中收藏名；空则显示提示） */
    private String favLabel = "";

    /** 一个成品：物品 + 目标数量 + 是否参与计算 + 选中的配方序号（多配方切换用） */
    private static final class ProductEntry {
        String itemId;
        int targetCount;
        boolean selected = true;
        int recipeIndex = 0;
        /** 是否由"对比差异"批量带入（已够自动删除；原有成品不删） */
        boolean fromCompare = false;

        ProductEntry(String itemId, int targetCount) {
            this.itemId = itemId;
            this.targetCount = targetCount;
        }
    }

    private static final class ProductRow {
        ProductEntry entry;
        GuiTextFieldInteger countField;
        ButtonGeneric delBtn;
        String recipeTag = "";
        int y;
    }

    private static final class TextFieldRow {
        MaterialLine line;
        String itemId;
        int depth;
        GuiTextFieldGeneric qtyField;
        ButtonGeneric delBtn;
        ButtonGeneric subBtn;
        int y;
    }

    /** 本次打开计算器是否已静默重扫过备货区（防回调里 initGui 死循环） */
    private boolean stockRescanned = false;
    /** 收藏下拉展开状态（自绘列表，展开后浮在所有按钮之上） */
    private boolean favOpen = false;
    /** 当前选中的收藏索引（用于"删除收藏"） */
    private int favSelected = -1;
    /** 收藏列表滚动偏移（每次显示 5 项，滚轮翻页） */
    private int favScroll = 0;

    private GuiTextFieldGeneric searchField;
    private RecipeEntry current;
    private final List<ProductEntry> products = new ArrayList<>();
    private final List<ProductRow> productRows = new ArrayList<>();
    private final List<TextFieldRow> rows = new ArrayList<>();
    private final List<String> candidates = new ArrayList<>();
    /** 材料行勾选状态：itemId → 是否参与计算（默认勾选） */
    private final Map<String, Boolean> rowSelected = new LinkedHashMap<>();
    private int scrollOffset = 0;
    /** 成品区翻页偏移（固定显示 3 行，多余先隐藏，滚轮在成品区时翻页） */
    private int productScroll = 0;
    private String status = "";
    /** 顶部四个页签按钮（用于绘制鼠标悬停提示） */
    private final java.util.List<ButtonGeneric> tabButtons = new java.util.ArrayList<>();
    /** 用于检测成品数量变化（自动重算材料总量） */
    private String lastSig = "";

    public GuiStockCalculator() {
        this.setTitle("材料备货助手 - 合成计算器");
        this.current = new RecipeEntry("", 1);

        // 退出保留：优先内存会话；重启游戏后首次打开从持久化配置恢复
        if (StockConfig.get().keepCalcOnExit && sessionProducts.isEmpty() && !StockConfig.get().calcProducts.isEmpty()) {
            for (RecipeEntry re : StockConfig.get().calcProducts) {
                ProductEntry cp = new ProductEntry(re.itemId, re.targetCount);
                cp.selected = re.selected;
                cp.recipeIndex = re.recipeIndex;
                cp.fromCompare = re.fromCompare;
                sessionProducts.add(cp);
            }
            sessionRowSelected.clear();
        }

        // 退出保留：从会话恢复上次的选择（开启时）
        if (StockConfig.get().keepCalcOnExit && !sessionProducts.isEmpty()) {
            for (ProductEntry p : sessionProducts) {
                this.products.add(new ProductEntry(p.itemId, p.targetCount));
                ProductEntry copy = this.products.get(this.products.size() - 1);
                copy.selected = p.selected;
                copy.recipeIndex = p.recipeIndex;
                copy.fromCompare = p.fromCompare;
            }
            this.rowSelected.putAll(sessionRowSelected);
        }

        if (!pendingMaterialId.isEmpty()) {
            // 详情页"计算配方"：原有成品保留但取消勾选；新材料默认勾选参与计算（已有则覆盖数量）
            for (ProductEntry pe : this.products) {
                pe.selected = false;
            }
            boolean exists = false;
            for (ProductEntry pe : this.products) {
                if (pe.itemId.equals(pendingMaterialId)) {
                    pe.targetCount = Math.max(1, pendingMaterialCount);
                    pe.selected = true;
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                this.products.add(new ProductEntry(pendingMaterialId, Math.max(1, pendingMaterialCount)));
            }
            pendingMaterialId = "";
            pendingMaterialCount = 1;
        }
        if (!pendingBatchIds.isEmpty()) {
            // 对比差异批量列入：原有成品保留但取消勾选；缺失且有配方的材料作为成品加入（已有则覆盖数量，不重复）
            for (ProductEntry pe : this.products) {
                pe.selected = false;
            }
            for (int i = 0; i < pendingBatchIds.size(); i++) {
                String pid = pendingBatchIds.get(i);
                int cnt = i < pendingBatchCounts.size() ? pendingBatchCounts.get(i) : 1;
                boolean exists = false;
                for (ProductEntry pe : this.products) {
                    if (pe.itemId.equals(pid)) {
                        pe.targetCount = Math.max(1, cnt);
                        pe.selected = true;
                        // 已有项保留原 fromCompare 标记：用户手动加的配方(false)不被自动删除
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    ProductEntry ne = new ProductEntry(pid, Math.max(1, cnt));
                    ne.fromCompare = true;
                    this.products.add(ne);
                }
            }
            pendingBatchIds.clear();
            pendingBatchCounts.clear();
            // 对比差异刚跳转：静默重扫一次备货区，扫完删已够的（只这一次）
            com.materialstock.scan.StockAreaScanner.scanAll(() -> {
                java.util.Map<net.minecraft.world.item.Item, Integer> avail =
                    com.materialstock.scan.StockAreaScanner.summarizeAvailable();
                boolean removed = this.products.removeIf(pe -> {
                    if (!pe.fromCompare) return false;
                    // 走 parseVariant：药水 id 带 #效果，直接 tryParse 会解析失败
                    CraftCalculator.Variant v = CraftCalculator.parseVariant(pe.itemId);
                    if (v == null) return false;
                    net.minecraft.world.item.Item it = v.item;
                    if (it == net.minecraft.world.item.Items.AIR) return false;
                    int have = avail.getOrDefault(it, 0) + backpackCount(it);
                    return have >= pe.targetCount;
                });
                if (removed) {
                    this.initGui();
                }
            });
        }
        // 对比差异带入的成品：背包+备货区已有量达到目标数量则自动删除（原有成品保留）
        if (!this.products.isEmpty()) {
            java.util.Map<net.minecraft.world.item.Item, Integer> avail0 =
                com.materialstock.scan.StockAreaScanner.summarizeAvailable();
            this.products.removeIf(pe -> {
                if (!pe.fromCompare) return false;
                CraftCalculator.Variant v = CraftCalculator.parseVariant(pe.itemId);
                if (v == null) return false;
                net.minecraft.world.item.Item it = v.item;
                if (it == net.minecraft.world.item.Items.AIR) return false;
                int have = avail0.getOrDefault(it, 0) + backpackCount(it);
                return have >= pe.targetCount;
            });
        }
        if (!pendingItemId.isEmpty()) {
            this.products.add(new ProductEntry(pendingItemId, 1));
            pendingItemId = "";
        } else if (!GuiItemSearch.pickedItemId.isEmpty()) {
            this.products.add(new ProductEntry(GuiItemSearch.pickedItemId, 1));
            GuiItemSearch.pickedItemId = "";
        }
        if (!this.products.isEmpty()) {
            this.recomputeMaterials();
            this.status = "已添加成品，配方已直接展示（可继续搜索添加多个成品）";
        }
        if (!pendingAddMaterialId.isEmpty()) {
            String add = pendingAddMaterialId;
            pendingAddMaterialId = "";
            boolean exists = false;
            for (MaterialLine m : this.current.materials) {
                if (m.itemId.equals(add)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                this.current.materials.add(new MaterialLine(add, 1));
                this.status = "已从材料区加入：" + resolveDisplayName(add);
            }
        }
        this.lastSig = this.signature();
    }

    @Override
    public void initGui() {
        super.initGui();
        // 保留搜索框已输入的文字（initGui 会重建控件，滚轮翻页/按钮点击后不丢搜索内容）
        String keepSearch = (this.searchField != null) ? this.searchField.getTextWrapper() : "";
        this.clearWidgets();
        this.tabButtons.clear();   // initGui 会被反复调用，不清会无限累加
        this.productRows.clear();
        this.rows.clear();
        this.candidates.clear();
        this.buttonBounds.clear();
        int mid = this.getScreenWidth() / 2;

        // ---- 顶部 tab（宽度随屏幕自适应：GUI 缩放大、窗口窄时压缩，避免溢出屏幕）----
        int tabW = Math.max(64, Math.min(100, (this.getScreenWidth() - 44) / 4));
        this.addTab(mid - 2 * tabW - 12, TAB_Y, tabW, "材料清单", new GuiStockMain());
        this.addTab(mid - tabW - 4, TAB_Y, tabW, "合成计算器", new GuiStockCalculator());
        this.addTab(mid + 4, TAB_Y, tabW, "备货区", new GuiStockArea());
        this.addTab(mid + tabW + 12, TAB_Y, tabW, "材料区", new GuiMaterialArea());

        // ---- 第一行：搜索框 + 清空选择 + 收藏框 + 删除收藏（已去除“搜索”按钮与收藏下拉箭头）----
        int sw = this.getScreenWidth();
        int searchW = Math.max(70, Math.min(150, sw - 282));
        this.searchField = new GuiTextFieldGeneric(10, 42, searchW, 16, Minecraft.getInstance().font);
        this.searchField.setTextWrapper(keepSearch);
        this.searchField.setSuggestion("搜索（支持拼音，中文，iD）");
        this.addTextField(this.searchField, null);
        int cx = 10 + searchW + 4;

        // 清空选择：紧贴搜索框
        ButtonGeneric clearBtn = new ButtonGeneric(cx, 44, 54, 16, "清空选择");
        clearBtn.setActionListener((b, mb) -> {
            // 锁定的成品保留，只清未锁定的
            java.util.Set<String> locked = com.materialstock.StockConfig.get().lockedProducts;
            this.products.removeIf(p -> !locked.contains(p.itemId));
            this.rowSelected.clear();
            sessionRowSelected.clear();
            // 持久化也只清未锁定的
            com.materialstock.StockConfig.get().calcProducts.removeIf(p -> !locked.contains(p.itemId));
            com.materialstock.StockConfig.save();
            // 重新计算锁定的成品
            this.current = new RecipeEntry("", 1);
            this.recomputeMaterials();
            this.lastSig = this.signature();
            this.status = "已清空未锁定成品（锁定的已保留并重新计算）";
            this.initGui();
        });
        this.addButton(clearBtn);
        cx += 58;

        // 收藏文本框：点击展开下拉（无箭头）；删除收藏紧贴其后
        this.favX = cx;
        this.favW = Math.max(96, Math.min(150, sw - cx - 178));
        List<String> favs = StockConfig.get().favorites;
        String favText = this.favLabel.isEmpty()
            ? (favs.isEmpty() ? "收藏（空）" : "收藏：" + favs.size() + " 项")
            : this.favLabel;
        this.favField = new GuiTextFieldGeneric(this.favX, 44, this.favW, 16, Minecraft.getInstance().font);
        this.favField.setEditable(false);
        this.favField.setMaxLength(64);
        this.favField.setTextWrapper(favText);
        this.addTextField(this.favField, null);

        ButtonGeneric delFavBtn = new ButtonGeneric(cx + this.favW + 4, 44, 50, 16, "删除收藏");
        delFavBtn.setActionListener((b, mb) -> this.deleteFavorite());
        this.addButton(delFavBtn);

        // 删除收藏后同一行依次：保留搜索 + 退出保留（大小一致）
        ButtonGeneric keepSearchBtn = new ButtonGeneric(cx + this.favW + 58, 44, 56, 16,
            StockConfig.get().keepSearchOnPick ? "保留搜索:开" : "保留搜索:关");
        keepSearchBtn.setActionListener((b, mb) -> {
            StockConfig.get().keepSearchOnPick = !StockConfig.get().keepSearchOnPick;
            StockConfig.save();
            this.status = StockConfig.get().keepSearchOnPick
                ? "已开启保留搜索：点击物品添加后搜索框文字保留"
                : "已关闭保留搜索：点击物品添加后自动清空搜索框";
            this.initGui();
        });
        this.addButton(keepSearchBtn);

        ButtonGeneric keepBtn = new ButtonGeneric(cx + this.favW + 118, 44, 56, 16,
            StockConfig.get().keepCalcOnExit ? "退出保留:开" : "退出保留:关");
        keepBtn.setActionListener((b, mb) -> {
            StockConfig.get().keepCalcOnExit = !StockConfig.get().keepCalcOnExit;
            StockConfig.save();
            this.status = StockConfig.get().keepCalcOnExit
                ? "已开启退出保留：关闭界面后再次打开会恢复当前选择"
                : "已关闭退出保留：下次打开计算器将清空选择";
            if (!StockConfig.get().keepCalcOnExit) {
                sessionProducts.clear();
                sessionRowSelected.clear();
            }
            this.initGui();
        });
        this.addButton(keepBtn);

        // ---- 三区布局：候选/成品/材料平均分配（仅删除底部状态与合计两行）----
        this.computeLayout();
        String prodTitle = "成品（可多选：点击候选或选择收藏即添加一个，每个可改数量）";
        if (this.products.size() > this.prodRows) {
            prodTitle += " [已添加 " + this.products.size() + " 个，显示 " + (this.productScroll + 1) + "-"
                + Math.min(this.productScroll + this.prodRows, this.products.size()) + "，滚轮翻页]";
        }
        this.prodTitleText = prodTitle;   // 在 drawContents 中绘制（在收藏列表之前），避免被 label 覆盖
        this.rebuildProductRows();
        this.rebuildRows();

        // 同步会话快照（退出保留用）
        this.syncSession();
    }

    /** 保存当前选择到会话（退出界面后再打开可恢复） */
    private void syncSession() {
        sessionProducts.clear();
        sessionRowSelected.clear();
        if (!StockConfig.get().keepCalcOnExit) return;
        for (ProductEntry p : this.products) {
            ProductEntry copy = new ProductEntry(p.itemId, p.targetCount);
            copy.selected = p.selected;
            copy.recipeIndex = p.recipeIndex;
            copy.fromCompare = p.fromCompare;
            sessionProducts.add(copy);
        }
        sessionRowSelected.putAll(this.rowSelected);
    }

    /** 关闭界面时把当前成品选择持久化到配置（退游后仍保存） */
    @Override
    public void closeGui(boolean showParent) {
        this.persistSession();
        super.closeGui(showParent);
    }

    /** 用当前 GUI 最新状态写盘 */
    private void persistSession() {
        this.syncSession();
        persistSessionStatic();
    }

    /** 从会话快照写盘（退出游戏兜底调用；drawContents 每帧保持会话快照最新） */
    public static void persistSessionStatic() {
        StockConfig cfg = StockConfig.get();
        cfg.calcProducts.clear();
        if (cfg.keepCalcOnExit) {
            for (ProductEntry p : sessionProducts) {
                RecipeEntry re = new RecipeEntry(p.itemId, p.targetCount);
                re.selected = p.selected;
                re.recipeIndex = p.recipeIndex;
                re.fromCompare = p.fromCompare;
                cfg.calcProducts.add(re);
            }
        }
        StockConfig.save();
    }

    private void addTab(int x, int y, int width, String text, GuiBase target) {
        ButtonGeneric b = new ButtonGeneric(x, y, width, TAB_H, text);
        b.setActionListener((button, mouseButton) -> GuiBase.openGui(target));
        GuiTabs.applyHover(b, text);
        this.tabButtons.add(b);
        this.addButton(b);
    }

    /** 页签悬停提示：最后绘制，避免被其它控件（含收藏下拉）盖住 */
    private void renderTabTooltips(net.minecraft.client.gui.GuiGraphics ctx, int mouseX, int mouseY) {
        for (ButtonGeneric b : this.tabButtons) {
            GuiTabs.renderTooltip(b, mouseX, mouseY, ctx);
        }
    }

    /** 注册按钮并记录其区域（滚轮悬停在按钮上不翻页，只响应点击） */
    private void addButton(ButtonGeneric b) {
        this.buttonBounds.add(new int[]{b.getX(), b.getY(), b.getWidth(), b.getHeight()});
        this.addWidget(b);
    }

    private void rebuildProductRows() {
        this.productRows.clear();
        if (this.productScroll < 0) this.productScroll = 0;
        int maxScroll = Math.max(0, this.products.size() - this.prodRows);
        if (this.productScroll > maxScroll) this.productScroll = maxScroll;

        int y = this.prodY + 18;
        for (int i = this.productScroll; i < this.products.size() && i < this.productScroll + this.prodRows; i++) {
            ProductEntry p = this.products.get(i);
            if (p.itemId == null || p.itemId.isEmpty()) continue;
            CraftCalculator.Variant pv = CraftCalculator.parseVariant(p.itemId);
            if (pv == null) continue;
            ProductRow r = new ProductRow();
            r.entry = p;
            r.y = y;
            r.recipeTag = CraftCalculator.recipeTagFor(pv.item, pv.variant, p.recipeIndex);
            // 数量框始终创建；收藏下拉是浮层（最后绘制盖住下方），点击由 favOpen 分支拦截不会误触
            r.countField = new GuiTextFieldInteger(400, y - 2, 56, 16, Minecraft.getInstance().font);
            r.countField.setTextWrapper(String.valueOf(p.targetCount));
            this.addTextField(r.countField, null);

            // 配方切换按钮：有多个配方时显示"配方 i/N·类型"，点击循环切换；单配方也显示类型（合成/熔炼/切石/酿造）
            int recipeCount = CraftCalculator.recipeCountFor(pv.item, pv.variant);
            String tag = r.recipeTag;
            String label = (recipeCount > 1 ? "配方 " + (p.recipeIndex + 1) + "/" + recipeCount + "·" : "")
                + (tag.isEmpty() ? "合成" : tag);
            ButtonGeneric recipeBtn = new ButtonGeneric(244, y - 2, 108, 16, label);
            recipeBtn.setActionListener((b, mb) -> {
                int cnt = CraftCalculator.recipeCountFor(pv.item, pv.variant);
                if (cnt > 1) {
                    p.recipeIndex = (p.recipeIndex + 1) % cnt;
                    this.recomputeMaterials();
                    this.lastSig = this.signature();
                    String t = CraftCalculator.recipeTagFor(pv.item, pv.variant, p.recipeIndex);
                    this.status = "已切换配方：" + resolveDisplayName(p.itemId)
                        + " → " + (t.isEmpty() ? "合成" : t) + "（配方 " + (p.recipeIndex + 1) + "/" + cnt + "）";
                    this.initGui();
                }
            });
            String lockLabel = com.materialstock.StockConfig.get().lockedProducts.contains(p.itemId) ? "[解锁]" : "[锁定]";
            ButtonGeneric lockBtn = new ButtonGeneric(462, y - 2, 54, 16, lockLabel);
            lockBtn.setActionListener((b, mb) -> {
                java.util.Set<String> locked = com.materialstock.StockConfig.get().lockedProducts;
                if (locked.contains(p.itemId)) locked.remove(p.itemId);
                else locked.add(p.itemId);
                com.materialstock.StockConfig.save();
                this.status = locked.contains(p.itemId) ? "已锁定（清空选择时保留）" : "已解锁";
                this.initGui();
            });
            this.addButton(lockBtn);
            ButtonGeneric fav = new ButtonGeneric(520, y - 2, 50, 16, "收藏");
            fav.setActionListener((b, mb) -> this.addFavorite(p.itemId));
            ButtonGeneric del = new ButtonGeneric(574, y - 2, 50, 16, "删除");
            del.setActionListener((b, mb) -> {
                this.products.remove(r.entry);
                this.rowSelected.clear();
                this.recomputeMaterials();
                this.lastSig = this.signature();
                this.status = "已删除成品";
                this.initGui();
            });
            this.addButton(recipeBtn);
            this.addButton(fav);
            this.addButton(del);
            r.delBtn = del;
            this.productRows.add(r);
            y += PROD_H;
        }
    }

    /** 材料表行：顶层 = 全部成品下的总需求（数量可改）；子行 = 子合成直接原料（只读，随倍数变化）。
     *  翻页滚动：固定显示 visibleRows 行，滚一格第一行消失、下一行出现。 */
    private void rebuildRows() {
        this.rows.clear();
        List<TextFieldRow> flat = new ArrayList<>();
        this.collectRowsData(this.current.materials, 0, flat);

        int visible = this.matRows;
        if (this.scrollOffset < 0) this.scrollOffset = 0;
        int maxScroll = Math.max(0, flat.size() - visible);
        if (this.scrollOffset > maxScroll) this.scrollOffset = maxScroll;

        for (int i = this.scrollOffset; i < flat.size() && i < this.scrollOffset + visible; i++) {
            TextFieldRow r = flat.get(i);
            r.y = this.matY + 18 + (i - this.scrollOffset) * ROW_H;
            if (r.depth == 0) {
                r.qtyField = new GuiTextFieldGeneric(260, r.y - 2, 44, 16, Minecraft.getInstance().font);
                r.qtyField.setTextWrapper(QuantityFormat.formatInput(r.line.perUnit));
                this.addTextField(r.qtyField, null);
                ButtonGeneric del = new ButtonGeneric(528, r.y - 2, 50, 16, "清除");
                del.setActionListener((b, mb) -> {
                    this.current.materials.removeIf(m -> m.itemId.equals(r.itemId));
                    this.rowSelected.remove(r.itemId);
                    this.initGui();
                });
                this.addButton(del);
                r.delBtn = del;
                // 追踪按钮（清除按钮右边）
                boolean tracked = StockConfig.get().trackedItems.contains(r.itemId);
                ButtonGeneric trackBtn = new ButtonGeneric(584, r.y - 2, 64, 16, tracked ? "取消追踪" : "追踪");
                trackBtn.setActionListener((b, mb) -> {
                    if (StockConfig.get().trackedItems.contains(r.itemId)) {
                        StockConfig.get().trackedItems.remove(r.itemId);
                    } else {
                        StockConfig.get().trackedItems.add(r.itemId);
                    }
                    StockConfig.save();
                    this.initGui();
                });
                this.addButton(trackBtn);
            }
            if (r.line.craftable) {
                ButtonGeneric sub = new ButtonGeneric(462, r.y - 2, 62, 16, r.line.expanded ? "收起" : "子合成");
                sub.setActionListener((b, mb) -> {
                    r.line.expanded = !r.line.expanded;
                    this.status = r.line.expanded ? ("已展开子合成：" + resolveDisplayName(r.line.itemId) + "（只显示直接原料，可继续逐层展开）")
                        : ("已收起子合成：" + resolveDisplayName(r.line.itemId));
                    this.initGui();
                });
                this.addButton(sub);
                r.subBtn = sub;
            }
            this.rows.add(r);
        }
    }

    /** 三区动态布局：候选/成品/材料 平均分配可用高度；余数先给材料再给成品（无法整除时） */
    private void computeLayout() {
        int top = 60;
        int bottom = this.getScreenHeight() - 36;  // 底部留一行给状态提示（黄字）
        int avail = Math.max(90, bottom - top);
        this.candY = top;
        this.candRows = 4;                          // 候选区固定 4 行
        this.candH = this.candRows * CAND_H;
        this.prodY = this.candY + this.candH + 4;   // 与候选区留 4px 间隙
        this.prodRows = 4;                          // 成品 4 行
        this.prodH = 18 + this.prodRows * PROD_H;
        this.matY = this.prodY + this.prodH;
        this.matRows = Math.max(1, (avail - this.candH - this.prodH - 18 - 4) / ROW_H);  // 剩下的全给材料，预留 4px 间隙不压状态行
        this.matH = 18 + this.matRows * ROW_H;
    }

    /** 扁平化后的材料总行数（含展开的子合成行） */
    private int countFlatRows() {
        List<TextFieldRow> flat = new ArrayList<>();
        this.collectRowsData(this.current.materials, 0, flat);
        return flat.size();
    }

    /** 材料标题文字。绘制与点击都用它算 [展开] 的横坐标，避免两处各写一遍算法而不一致 */
    private String matTitleText() {
        return "材料（滚轮翻页：显示 " + this.rows.size() + " 行 / 共 " + this.countFlatRows()
            + " 行，第 " + (this.scrollOffset + 1) + " 行起）";
    }

    /** 标题文字宽度 */
    private int matTitleWidth() {
        return Minecraft.getInstance().font.width(matTitleText());
    }

    /**
     * 打开材料明细全屏界面（[展开]）。
     * 传顶层材料（勾选且数量>0）的 规范id + 总数（perUnit × 倍数），
     * 顺序与界面显示一致；离开后看到的是打开瞬间的快照，不会随重算跳动。
     */
    private void openMaterialDetail() {
        int mult = Math.max(1, StockConfig.get().multiplier);
        List<String> ids = new ArrayList<>();
        List<Double> totals = new ArrayList<>();
        for (MaterialLine m : this.current.materials) {
            if (m == null || m.itemId == null || m.itemId.isEmpty()) continue;
            if (!Boolean.TRUE.equals(this.rowSelected.get(m.itemId))) continue;
            if (m.perUnit <= 0) continue;
            ids.add(m.itemId);
            totals.add(m.perUnit * mult);
        }
        Minecraft.getInstance().setScreen(new GuiStockCalculatorDetail(ids, totals, this));
    }

    /** 树形扁平化（只建行数据）：顶层行 + 展开的子合成行（一步步展开，不跨级） */
    private void collectRowsData(List<MaterialLine> lines, int depth, List<TextFieldRow> out) {
        for (MaterialLine line : lines) {
            if (line.itemId == null || line.itemId.isEmpty()) continue;
            TextFieldRow r = new TextFieldRow();
            r.line = line;
            r.itemId = line.itemId;
            r.depth = depth;
            out.add(r);
            if (line.expanded) {
                this.collectRowsData(line.children, depth + 1, out);
            }
        }
    }

    /** 成品/勾选/数量签名，用于检测变化自动重算 */
    private String signature() {
        StringBuilder sb = new StringBuilder();
        for (ProductEntry p : this.products) {
            sb.append(p.itemId).append(':').append(p.targetCount).append(p.selected ? 'S' : 's').append(';');
        }
        return sb.toString();
    }

    /** 玩家背包+物品栏里该物品的数量 */
    private static int backpackCount(net.minecraft.world.item.Item item) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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

    /** 重算材料总量：对每个勾选的成品推导直接原料并按其数量加权汇总；构建子合成树并保留展开状态 */
    private void recomputeMaterials() {
        Map<String, MaterialLine> merged = new LinkedHashMap<>();
        for (ProductEntry p : this.products) {
            if (p.itemId == null || p.itemId.isEmpty() || !p.selected || p.targetCount <= 0) continue;
            // 必须传规范 id（含药水效果），否则隐身的药水会被当成普通药水、找不到酿造配方
            List<MaterialLine> lines = CraftCalculator.deriveBaseMaterials(p.itemId, p.recipeIndex);
            for (MaterialLine l : lines) {
                if (l.itemId == null) continue;
                MaterialLine m = merged.computeIfAbsent(l.itemId, k -> new MaterialLine(l.itemId, 0));
                m.perUnit += l.perUnit * p.targetCount;
            }
        }

        // 保留展开状态（按 itemId 路径），再构建新的子合成树
        Map<String, Boolean> expandedSave = new LinkedHashMap<>();
        this.saveExpanded(this.current.materials, "", expandedSave);

        List<MaterialLine> newMaterials = new ArrayList<>(merged.values());
        for (MaterialLine m : newMaterials) {
            // 同样要带变体，否则子合成树也无法展开药水
            MaterialLine tree = CraftCalculator.buildSubTree(m.itemId, m.perUnit);
            m.craftable = tree.craftable;
            m.children = tree.children;
        }
        this.restoreExpanded(newMaterials, "", expandedSave);

        this.rowSelected.keySet().retainAll(merged.keySet());
        for (String k : merged.keySet()) {
            if (!this.rowSelected.containsKey(k)) {
                this.rowSelected.put(k, true);
            }
        }
        this.current.materials = newMaterials;
        if (!this.products.isEmpty()) {
            this.current.itemId = this.products.get(0).itemId;
        }
    }

    private void saveExpanded(List<MaterialLine> lines, String path, Map<String, Boolean> out) {
        for (MaterialLine l : lines) {
            String key = path + "/" + l.itemId;
            if (l.expanded) out.put(key, true);
            this.saveExpanded(l.children, key, out);
        }
    }

    private void restoreExpanded(List<MaterialLine> lines, String path, Map<String, Boolean> save) {
        for (MaterialLine l : lines) {
            String key = path + "/" + l.itemId;
            l.expanded = Boolean.TRUE.equals(save.get(key));
            this.restoreExpanded(l.children, key, save);
        }
    }

    /** 收藏指定成品（直接传入物品 ID；原"搜索框收藏"改为成品行内"收藏"按钮） */
    private void addFavorite(String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            this.status = "收藏失败：该成品无效";
            return;
        }
        if (!StockConfig.get().favorites.contains(itemId)) {
            StockConfig.get().favorites.add(itemId);
            StockConfig.save();
        }
        this.status = "已加入收藏：" + resolveDisplayName(itemId);
        this.initGui(); // 刷新下拉列表，新收藏立即可见
    }

    /** 按中文名/模糊匹配解析物品（支持"侦测器"这类中文输入） */
    private static Item resolveByName(String name) {
        String q = name.toLowerCase();
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR) continue;
            String n = new ItemStack(it).getHoverName().getString();
            String id = BuiltInRegistries.ITEM.getKey(it).toString();
            if (n.equals(name) || n.toLowerCase().contains(q) || id.toLowerCase().contains(q)
                || com.materialstock.util.PinyinUtil.toFull(n).contains(q)
                || com.materialstock.util.PinyinUtil.toInitials(n).contains(q)) {
                return it;
            }
        }
        return null;
    }

    /** 删除收藏：先展开收藏列表点击选中一项，再点"删除收藏"删除 */
    private void deleteFavorite() {
        List<String> favs = StockConfig.get().favorites;
        if (this.favSelected < 0 || this.favSelected >= favs.size()) {
            this.status = "删除收藏：请先展开收藏列表并点击选中要删除的收藏";
            return;
        }
        String removed = favs.remove(this.favSelected);
        this.favSelected = -1;
        this.favLabel = "";
        StockConfig.save();
        this.status = "已删除收藏：" + resolveDisplayName(removed);
        this.initGui();
    }

    /** 添加一个成品（已存在则不重复）并重算材料 */
    private void addProduct(String id) {
        for (ProductEntry p : this.products) {
            if (p.itemId.equals(id)) {
                return;
            }
        }
        this.products.add(new ProductEntry(id, 1));
        this.recomputeMaterials();
        this.lastSig = this.signature();
    }

    /** 从文本框同步成品数量与顶层材料数量（子行只读，由父级推导；收藏展开期间字段未注册则跳过） */
    private void syncFromFields() {
        for (ProductRow pr : this.productRows) {
            if (pr.countField == null) continue;
            try {
                pr.entry.targetCount = Math.max(0, Integer.parseInt(pr.countField.getTextWrapper().trim()));
            } catch (Exception ignored) {
            }
        }
        for (TextFieldRow r : this.rows) {
            if (r.qtyField == null || r.line == null) continue;
            r.line.perUnit = Math.max(0, QuantityFormat.parseInput(r.qtyField.getTextWrapper()));
        }
    }

    private static Item parseItem(String id) {
        if (id == null || id.isEmpty()) return null;
        // 药水是 "minecraft:potion#invisibility" 这种变体 id：直接 tryParse 会把 #后面当成路径而查不到物品，
        // 必须走 parseVariant 剥掉变体后缀。
        CraftCalculator.Variant v = CraftCalculator.parseVariant(id);
        return v == null ? null : v.item;
    }

    /** 候选渲染用的 ItemStack：药水带上效果，否则图标/名不对 */
    private static ItemStack candidateStack(String id) {
        ItemStack st = com.materialstock.craft.BrewingRecipes.variantStack(id);
        if (!st.isEmpty()) return st;
        Item item = parseItem(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static String resolveDisplayName(String id) {
        CraftCalculator.Variant v = CraftCalculator.parseVariant(id);
        if (v == null) return "?" + id;
        if (v.variant != null) {
            // 药水：用语言键取"隐身药水/喷溅型隐身药水/滞留型隐身药水/药箭"
            return com.materialstock.craft.BrewingRecipes.displayName(v.item, v.variant);
        }
        return new ItemStack(v.item).getHoverName().getString();
    }

    /** 候选列表最多收集的条数（界面固定显示 3 条，滚轮翻页查看更多） */
    private static final int CAND_MAX = 30;

    /** 候选是否命中搜索（中文名/ID/拼音）。id 为规范 id，药水会带 "#效果" */
    private static boolean matchCandidateId(String id, String q) {
        String name = resolveDisplayName(id);
        return name.toLowerCase().contains(q) || id.toLowerCase().contains(q)
            || com.materialstock.util.PinyinUtil.toFull(name).contains(q)
            || com.materialstock.util.PinyinUtil.toInitials(name).contains(q);
    }

    /** 拼音优先：第一个字的拼音（全拼或首字母）以 q 开头 */
    private static boolean isFirstPinyinMatchId(String id, String q) {
        String name = resolveDisplayName(id);
        if (name.isEmpty()) return false;
        String firstChar = name.substring(0, 1);
        String firstP = com.materialstock.util.PinyinUtil.toFull(firstChar);
        String firstI = com.materialstock.util.PinyinUtil.toInitials(firstChar);
        return (!firstP.isEmpty() && firstP.startsWith(q))
            || (!firstI.isEmpty() && firstI.startsWith(q));
    }

    /**
     * 收集可添加为成品的候选规范 id。
     * 药水必须展开成变体（水瓶/粗制的药水/隐身药水…）——它们全是同一个 minecraft:potion，
     * 只遍历物品注册表的话所有药水都叫"药水"，搜"隐身"根本找不到。
     */
    private static void collectCandidateIds(List<String> out) {
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            out.add(id);
            if (item == Items.POTION || item == Items.SPLASH_POTION || item == Items.LINGERING_POTION) {
                for (String v : com.materialstock.craft.BrewingRecipes.allVariantIds()) {
                    out.add(id + com.materialstock.craft.BrewingRecipes.VARIANT_SEP + v);
                }
            }
        }
    }

    /** 成品搜索框内容变化时实时搜索候选：优先收集“第一个字拼音”匹配的物品，其余排后 */
    private void updateCandidates() {
        this.candidates.clear();
        String q = this.searchField.getTextWrapper().trim().toLowerCase();
        if (q.isEmpty()) return;
        List<String> all = new ArrayList<>();
        collectCandidateIds(all);
        List<String> first = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String cid : all) {
            if (!matchCandidateId(cid, q)) continue;
            if (isFirstPinyinMatchId(cid, q)) {
                first.add(cid);
                if (first.size() >= CAND_MAX) break;
            }
        }
        for (String cid : all) {
            if (!matchCandidateId(cid, q) || isFirstPinyinMatchId(cid, q)) continue;
            rest.add(cid);
            if (first.size() + rest.size() >= CAND_MAX) break;
        }
        this.candidates.addAll(first);
        this.candidates.addAll(rest);
        if (this.candidates.size() > CAND_MAX) {
            this.candidates.clear();
            this.candidates.addAll(first.size() >= CAND_MAX ? first.subList(0, CAND_MAX) : first);
            if (this.candidates.size() < CAND_MAX) {
                this.candidates.addAll(rest.subList(0, Math.min(rest.size(), CAND_MAX - this.candidates.size())));
            }
        }
        if (this.candidateScroll > Math.max(0, this.candidates.size() - this.candRows)) {
            this.candidateScroll = Math.max(0, this.candidates.size() - this.candRows);
        }
    }

    @Override
    protected void drawContents(GuiGraphics drawContext, int mouseX, int mouseY, float partialTicks) {
        super.drawContents(drawContext, mouseX, mouseY, partialTicks);

        // 搜索框提示文字：输入内容后自动消失
        if (this.searchField != null) {
            this.searchField.setSuggestion(this.searchField.getTextWrapper().isEmpty() ? "搜索（支持拼音，中文，iD）" : "");
        }

        // 实时同步成品数量到数据（输入即时生效；数量 0 = 不参与计算；收藏展开期间数量框未注册则跳过）
        for (ProductRow pr : this.productRows) {
            if (pr.countField == null) continue;
            try {
                pr.entry.targetCount = Math.max(0, Integer.parseInt(pr.countField.getTextWrapper().trim()));
            } catch (Exception ignored) {
            }
        }

        // 成品数量变化 → 自动重算材料总量
        String sig = this.signature();
        if (!sig.equals(this.lastSig)) {
            this.lastSig = sig;
            this.recomputeMaterials();
            // 更新现有材料行数量框文本（行结构不变时）
            if (this.rows.size() == this.current.materials.size()) {
                for (int i = 0; i < this.rows.size(); i++) {
                    if (this.rows.get(i).qtyField != null) {
                        this.rows.get(i).qtyField.setTextWrapper(String.valueOf(this.current.materials.get(i).perUnit));
                    }
                }
            } else {
                this.syncFromFields();
                this.initGui();
                return;
            }
        }

        // 实时候选（输入即搜，搜索列表在顶部下方直接展示；固定 3 行，滚轮翻页）
        this.updateCandidates();
        if (!this.candidates.isEmpty()) {
            int start = this.candidateScroll;
            int end = Math.min(start + this.candRows, this.candidates.size());
            int y = this.candY;
            for (int i = start; i < end; i++) {
                String cid = this.candidates.get(i);
                ItemStack cst = candidateStack(cid);
                drawContext.renderItem(cst, 10, y);
                this.drawString(drawContext, resolveDisplayName(cid) + "  （点击添加为成品）", 30, y + 2, 0xFFFFFFFF);
                y += CAND_H;
            }
        }

        // 成品行：勾选 + 图标 + 中文名（对齐最左，与材料行一致；熔炼/切石配方在后面标注）
        for (ProductRow pr : this.productRows) {
            // 用 candidateStack 才能让药水图标/悬浮名带效果（parseItem 只给到 minecraft:potion 本体）
            ItemStack pst = candidateStack(pr.entry.itemId);
            if (pst.isEmpty()) continue;
            boolean sel = pr.entry.selected;
            this.drawString(drawContext, sel ? "☑" : "☐", 8, pr.y + 2, sel ? 0xFF55FF55 : 0xFF888888);
            drawContext.renderItem(pst, 24, pr.y);
            String name = resolveDisplayName(pr.entry.itemId);
            String tag = pr.recipeTag;
            if (!tag.isEmpty()) {
                name += "（" + tag + "）";
            }
            this.drawString(drawContext, name, 44, pr.y + 2, tag.isEmpty() ? 0xFFFFFFFF : 0xFFFFAA55);
            this.drawString(drawContext, "数量:", 360, pr.y + 2, 0xFFAAAAAA);
        }

        // 材料行（树形）：顶层可勾选/可改数量（支持分数，如 1/9）；子合成为只读，随主合成倍数变化
        for (TextFieldRow r : this.rows) {
            MaterialLine line = r.line;
            if (line == null || line.itemId == null) continue;
            boolean isTop = r.depth == 0;
            boolean sel = isTop && Boolean.TRUE.equals(this.rowSelected.get(line.itemId));
            if (isTop) {
                this.drawString(drawContext, sel ? "☑" : "☐", 8, r.y, sel ? 0xFF55FF55 : 0xFF888888);
            }
            int iconX = 24 + r.depth * 16;
            // candidateStack 让药水原料的图标带效果（水瓶/粗制的药水/隐身药水 图标不同）
            ItemStack matStack = candidateStack(line.itemId);
            if (!matStack.isEmpty()) {
                drawContext.renderItem(matStack, iconX, r.y);
            }
            String name = resolveDisplayName(line.itemId);
            this.drawString(drawContext, name, iconX + 20, r.y + 2, 0xFFFFFFFF);

            double qty = line.perUnit;
            if (isTop && r.qtyField != null) {
                qty = Math.max(0, QuantityFormat.parseInput(r.qtyField.getTextWrapper()));
                line.perUnit = qty;
            }
            if (isTop) {
                if (sel) {
                    int stack = matStack.isEmpty() ? 64 : matStack.getMaxStackSize();
                    this.drawString(drawContext, "合计 " + QuantityFormat.format(qty, stack), 312, r.y + 2, 0xFFFFAA55);
                } else {
                    this.drawString(drawContext, "（未勾选）", 312, r.y + 2, 0xFF777777);
                }
            } else {
                // 子行只读：数量已按父级需求与配方产率换算（可为分数），随倍数自动变化
                int stack = matStack.isEmpty() ? 64 : matStack.getMaxStackSize();
                this.drawString(drawContext, "需 " + QuantityFormat.format(qty, stack), 312, r.y + 2, 0xFF88DDFF);
            }
        }

        // 成品标题（在收藏列表之前绘制，收藏列表展开时会被其不透明背景盖住）
        if (!this.prodTitleText.isEmpty()) {
            this.drawString(drawContext, this.prodTitleText, 10, this.prodY, 0xFFFFFF55);
        }

        // 材料区标题 + 翻页提示（固定显示 N 行，滚轮翻页）
        String matTitle = matTitleText();
        this.drawString(drawContext, matTitle, 10, this.matY, 0xFFFFFF55);
        // 标题后 [展开]：打开材料明细全屏界面（与材料区的 [展开] 功能一致：搜索 + 追踪）
        this.drawString(drawContext, "[展开]", matTitleWidth() + 6, this.matY, 0xFFFFFF66);

        // 底部状态提示（黄字，最下面；合计行保持删除）
        this.drawString(drawContext, this.status, 10, this.getScreenHeight() - 26, 0xFFFFFF00);

        // 收藏下拉展开列表：最后绘制，浮在所有按钮之上
        this.drawFavoriteList(drawContext, mouseX, mouseY);

        // 统一翻页提示（候选 + 收藏），显示在删除收藏按钮正下方
        String candPage = "";
        if (!this.candidates.isEmpty()) {
            int cs = this.candidateScroll;
            int ce = Math.min(cs + this.candRows, this.candidates.size());
            candPage = "候选 " + (cs + 1) + "-" + ce + "/" + this.candidates.size() + "，滚轮翻页";
        }
        String favPage = "";
        List<String> favs0 = StockConfig.get().favorites;
        if (this.favOpen && !favs0.isEmpty()) {
            int fs = this.favScroll;
            int fshown = Math.min(this.candRows, favs0.size() - fs);
            favPage = "收藏 " + (fs + 1) + "-" + (fs + fshown) + "/" + favs0.size() + "，滚轮翻页";
        }
        String pageText = candPage.isEmpty() ? favPage
            : (favPage.isEmpty() ? candPage : candPage + " ｜ " + favPage);
        if (!pageText.isEmpty()) {
            this.drawString(drawContext, pageText, this.favX + this.favW + 4, 66, 0xFF88DDFF);
        }

        // 每帧同步会话快照（退出保留 / 退游持久化始终基于最新选择）
        this.syncSession();

        // 页签悬停提示：最后绘制，浮在收藏下拉与其它控件之上
        this.renderTabTooltips(drawContext, mouseX, mouseY);
    }

    /** 收藏下拉展开列表：绘制在所有控件之上（不透明背景浮顶）。点击选中项添加为成品；点外部收起。 */
    private void drawFavoriteList(GuiGraphics drawContext, int mouseX, int mouseY) {
        List<String> favs = StockConfig.get().favorites;
        if (!this.favOpen || favs.isEmpty()) return;
        int x = this.favX;
        int w = this.favW;
        int itemH = 16;
        int y0 = 62;
        int perPage = this.candRows;   // 与候选区展示行数一致
        if (this.favScroll < 0) this.favScroll = 0;
        if (this.favScroll > Math.max(0, favs.size() - perPage)) this.favScroll = Math.max(0, favs.size() - perPage);
        int start = this.favScroll;
        int shown = Math.min(perPage, favs.size() - start);
        int listH = shown * itemH;
        // 背景：完全不透明，盖住下方所有控件（含成品数量框等文字）
        drawContext.fill(x, y0 - 1, x + w, y0 + listH, 0xFF202020);
        drawContext.fill(x, y0, x + w, y0 + listH - 1, 0xFF3A3A3A);
        for (int i = 0; i < shown; i++) {
            int yy = y0 + i * itemH;
            int fi = start + i;
            ItemStack favStack = candidateStack(favs.get(fi));
            boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= yy && mouseY < yy + itemH;
            if (hover) {
                drawContext.fill(x, yy, x + w, yy + itemH - 1, 0xFF555555);
            }
            if (!favStack.isEmpty()) {
                drawContext.renderItem(favStack, x + 2, yy + 1);
            }
            String name = resolveDisplayName(favs.get(fi));
            this.drawString(drawContext, name, x + 22, yy + 3, hover ? 0xFFFFFF55 : 0xFFFFFFFF);
            if (fi == this.favSelected) {
                this.drawString(drawContext, "◀", x + w - 12, yy + 3, 0xFF55FFFF);
            }
        }
    }

    @Override
    public boolean onMouseClicked(int mouseX, int mouseY, int mouseButton) {
        // 非展开状态：点收藏文本框展开下拉
        if (!this.favOpen && mouseButton == 0 && mouseX >= this.favX && mouseX <= this.favX + this.favW
                && mouseY >= 44 && mouseY <= 60) {
            this.favOpen = true;
            this.initGui();
            return true;
        }
        // 收藏下拉展开时优先处理：列表项=选中并添加成品；点收藏按钮本身=收起/展开；点其他区域=收起且不触发被覆盖的按钮
        if (this.favOpen && mouseButton == 0) {
            List<String> favs = StockConfig.get().favorites;
            int perPage = this.candRows;   // 与候选区展示行数一致
            if (this.favScroll < 0) this.favScroll = 0;
            if (this.favScroll > Math.max(0, favs.size() - perPage)) this.favScroll = Math.max(0, favs.size() - perPage);
            if (!favs.isEmpty() && mouseX >= this.favX && mouseX <= this.favX + this.favW && mouseY >= 62) {
                int idx = this.favScroll + (mouseY - 62) / 16;
                int shown = Math.min(perPage, favs.size() - this.favScroll);
                if (idx >= this.favScroll && idx < this.favScroll + shown && mouseY < 62 + shown * 16) {
                    this.favSelected = idx;
                    this.favLabel = resolveDisplayName(favs.get(idx));
                    this.favOpen = false;
                    this.addProduct(favs.get(idx));
                    this.status = "已添加收藏成品：" + resolveDisplayName(favs.get(idx)) + "（配方已直接展示，可继续多选）";
                    if (!StockConfig.get().keepSearchOnPick && this.searchField != null) {
                        this.searchField.setTextWrapper("");
                        this.status += " ｜ 已清空搜索框（点“保留搜索:开”可保留）";
                    }
                    this.initGui();
                    return true;
                }
            }
            // 点收藏区（y 44-60）：直接收起（已无箭头按钮）
            if (mouseX >= this.favX && mouseX <= this.favX + this.favW && mouseY >= 44 && mouseY <= 60) {
                this.favOpen = false;
                this.initGui();
                return true;
            }
            // 点其他区域：收起下拉，并吞掉本次点击（避免误触下方被覆盖的按钮）
            this.favOpen = false;
            this.initGui();
            return true;
        }
        if (super.onMouseClicked(mouseX, mouseY, mouseButton)) {
            return true;
        }
        if (mouseButton != 0) return false;

        // 材料标题后的 [展开]：打开材料明细全屏界面
        int expX = 10 + this.matTitleWidth() + 6;
        int expW = Minecraft.getInstance().font.width("[展开]");
        if (mouseX >= expX && mouseX <= expX + expW && mouseY >= this.matY && mouseY <= this.matY + 12) {
            this.syncFromFields();
            this.openMaterialDetail();
            return true;
        }

        // 实时候选点击 → 添加为成品（只点物品图标+名字区才触发，空白处不触发）
        if (!this.candidates.isEmpty() && mouseY >= this.candY && mouseY < this.candY + this.candH) {
            int idx = (mouseY - this.candY) / CAND_H + this.candidateScroll;
            if (idx >= 0 && idx < this.candidates.size()) {
                String id = this.candidates.get(idx);
                String name = resolveDisplayName(id);
                int textW = 30 + net.minecraft.client.Minecraft.getInstance().font.width(name + "  （点击添加为成品）");
                if (mouseX >= 10 && mouseX <= textW) {
                this.addProduct(id);
                this.status = "已添加成品：" + resolveDisplayName(id) + "（配方已直接展示，可继续搜索多选）";
                if (!StockConfig.get().keepSearchOnPick) {
                    this.searchField.setTextWrapper("");
                    this.status += " ｜ 已清空搜索框（点“保留搜索:开”可保留）";
                }
                this.initGui();
                return true;
                }
            }
        }

        // 成品行勾选切换（x=8 处 ☑/☐，与材料行一致）
        if (!this.productRows.isEmpty() && mouseX >= 4 && mouseX <= 26) {
            int pIdx = (mouseY - this.prodY - 18) / PROD_H;
            if (pIdx >= 0 && pIdx < this.productRows.size()) {
                ProductRow pr = this.productRows.get(pIdx);
                pr.entry.selected = !pr.entry.selected;
                this.status = (pr.entry.selected ? "已勾选成品（参与计算）：" : "已取消勾选成品（不参与计算）：")
                    + resolveDisplayName(pr.entry.itemId);
                this.recomputeMaterials();
                this.lastSig = this.signature();
                this.initGui();
                return true;
            }
        }

        // 材料行勾选切换（仅顶层行；rows 已裁剪为可见行，索引不需要再加滚动偏移）
        int rowIdx = (mouseY - this.matY - 18) / ROW_H;
        if (rowIdx >= 0 && rowIdx < this.rows.size() && mouseX >= 4 && mouseX <= 26) {
            TextFieldRow r = this.rows.get(rowIdx);
            if (r.depth == 0 && r.line != null) {
                boolean now = !Boolean.TRUE.equals(this.rowSelected.get(r.itemId));
                this.rowSelected.put(r.itemId, now);
                this.status = (now ? "已勾选" : "已取消勾选") + "：" + resolveDisplayName(r.itemId);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onMouseScrolled(int mouseX, int mouseY, double horizontalAmount, double verticalAmount) {
        // 收藏下拉展开时：在展开列表区域滚动 → 翻收藏页；不触发下方按钮/成品翻页
        if (this.favOpen && mouseX >= this.favX && mouseX <= this.favX + this.favW && mouseY >= 62) {
            List<String> favs = StockConfig.get().favorites;
            int shown = Math.min(this.candRows, favs.size() - this.favScroll);   // 与候选区展示行数一致
            if (mouseY < 62 + shown * 16) {
                this.favScroll -= (int) Math.signum(verticalAmount);
                if (this.favScroll < 0) this.favScroll = 0;
                if (this.favScroll > Math.max(0, favs.size() - this.candRows)) this.favScroll = Math.max(0, favs.size() - this.candRows);
                return true;
            }
        }
        // 鼠标悬停在按钮上时直接消费滚轮事件：malilib 的按钮会把滚轮当作点击触发动作，
        // 这里先拦截，保证所有按钮只有真正点击才生效
        for (int[] bb : this.buttonBounds) {
            if (mouseX >= bb[0] && mouseX <= bb[0] + bb[2] && mouseY >= bb[1] && mouseY <= bb[1] + bb[3]) {
                return true;
            }
        }
        if (super.onMouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        // 滚轮：鼠标在候选区 → 候选翻页；在成品区 → 成品翻页；在材料区 → 材料固定行数翻页
        if (!this.candidates.isEmpty() && this.candidates.size() > this.candRows
                && mouseX >= 10 && mouseX <= this.getScreenWidth() - 20
                && mouseY >= this.candY && mouseY < this.candY + this.candH) {
            this.candidateScroll -= (int) Math.signum(verticalAmount);
            if (this.candidateScroll < 0) this.candidateScroll = 0;
            if (this.candidateScroll > this.candidates.size() - this.candRows) this.candidateScroll = this.candidates.size() - this.candRows;
            this.initGui();
            return true;
        }
        int prodBottom = this.prodY + this.prodH;
        if (mouseX >= 10 && mouseX <= this.getScreenWidth() - 20 && mouseY >= this.prodY && mouseY <= prodBottom
                && this.products.size() > this.prodRows) {
            this.productScroll -= (int) Math.signum(verticalAmount);
            this.syncFromFields();
            this.initGui();
            return true;
        }
        if (mouseX >= 10 && mouseX <= this.getScreenWidth() - 20 && mouseY >= this.matY && mouseY <= this.matY + this.matH) {
            this.scrollOffset -= (int) Math.signum(verticalAmount);
            this.syncFromFields();
            this.initGui();
            return true;
        }
        return false;
    }
}
