package com.materialstock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.materialstock.data.RecipeEntry;
import com.materialstock.data.StockArea;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 模组配置：Gson 序列化到 config/materialstock/stock.json。
 * 保存备货区、计算器配方、渲染设置等。
 */
public class StockConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getConfigDir().resolve("materialstock");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("stock.json");

    public List<StockArea> stockAreas = new ArrayList<>();
    /** 材料区：独立的框选区域（与备货区分离），用于统计备料 */
    public List<StockArea> materialAreas = new ArrayList<>();
    /** 材料区高亮是否可见（默认隐藏，按热键切换） */
    public boolean materialAreaVis = false;
    public List<RecipeEntry> recipes = new ArrayList<>();
    /** 收藏的常用成品物品 ID（计算器便捷计算） */
    public List<String> favorites = new ArrayList<>();

    /** 容器缓存过期时间（秒） */
    public int cacheExpirySeconds = 300;
    /** 是否高亮包含所需材料的容器 */
    public boolean highlightEnabled = true;
    /** 高亮线宽 */
    public float lineWidth = 4.0F;
    /** 高亮颜色 ARGB */
    public int highlightColor = 0xFFFFFF00;
    /** 材料清单数量倍数 */
    public int multiplier = 1;
    /** 材料清单排列方式：0=按类别 1=数量升序 2=数量倒序 */
    public int sortMode = 0;
    /** 上次选择的投影文件名 */
    public String lastSchematic = "";
    /** 打开主界面时是否自动扫描备货区 */
    public boolean autoScanOnOpen = true;
    /** 退出合成计算器界面后是否保留当前选择（成品/数量/勾选） */
    public boolean keepCalcOnExit = true;
    /** 点击候选物品添加为成品后，是否保留搜索框输入的文字（默认清除） */
    public boolean keepSearchOnPick = false;
    /** 计算器会话持久化：退出游戏后保留的成品列表（keepCalcOnExit 开启时保存） */
    public List<RecipeEntry> calcProducts = new ArrayList<>();
    /** 锁定的备货区/材料区名字：清除全部时不会被清除 */
    public java.util.Set<String> lockedStockAreas = new java.util.HashSet<>();
    public java.util.Set<String> lockedMaterialAreas = new java.util.HashSet<>();
    /** 锁定的成品 id 列表：清空选择时不会被清除 */
    public java.util.Set<String> lockedProducts = new java.util.HashSet<>();
    /** 容器缓存持久化：扫描结果（容器坐标 "x,y,z" → 物品ID → 数量），退出游戏后保留备货区物品明细 */
    public java.util.Map<String, java.util.Map<String, Integer>> containerCache = new java.util.LinkedHashMap<>();
    /** 材料区汇总持久化：物品ID → 数量（退出游戏后保留材料区物品明细） */
    public java.util.Map<String, Integer> materialSummary = new java.util.LinkedHashMap<>();
    /**
     * 材料区容器位置持久化：["维度|x,y,z", ...]。
     * 追踪高亮要靠它区分"材料区的箱子"与"备货区的箱子"（两者的物品明细同在 containerCache 里）；
     * 同时让重启后无需重扫材料区就能追踪。
     */
    public List<String> materialContainerKeys = new ArrayList<>();
    /** 详情页大类/小类展开状态（退游戏后保留：展开就展开，关闭就关闭） */
    public List<String> collapsedCats = new ArrayList<>();
    /** 信息浮窗忽略的材料（物品ID）：浮窗不显示，但详情页仍显示 */
    public List<String> ignoredMaterials = new ArrayList<>();
    /** 追踪的材料（物品ID）：信息浮窗最顶端优先显示 */
    public List<String> trackedItems = new ArrayList<>();
    /** 信息浮窗是否开启（退游戏后保留） */
    public boolean hudEnabled = false;
    /** 信息浮窗数据来源的投影文件名（退游戏后保留，启动时自动加载） */
    public String hudSchematic = "";
    /** 投影材料清单磁盘缓存：投影文件名 → 物品ID→数量（退游戏后保留，不用每次重新解析） */
    public java.util.Map<String, java.util.Map<String, Integer>> materialCache = new java.util.LinkedHashMap<>();
    /** 分类管理：物品ID → [大类, 小类]（小类可空=散货）；用户自定义归属，覆盖分类表默认 */
    public java.util.Map<String, java.util.List<String>> categoryOverrides = new java.util.LinkedHashMap<>();
    /** 分类管理：用户新建的大类名（追加到分类表大类之后） */
    public List<String> extraCategories = new ArrayList<>();
    /** 分类管理：用户新建的小类（大类 → 小类名列表） */
    public java.util.Map<String, List<String>> extraSubs = new java.util.LinkedHashMap<>();
    /** 分类管理：用户删除的大类名（其下物品归入"其他"） */
    public List<String> removedCategories = new ArrayList<>();
    /** 分类管理：用户删除的小类（大类 → 小类名列表；其下物品变为该大类散货） */
    public java.util.Map<String, List<String>> removedSubs = new java.util.LinkedHashMap<>();

    /** 按世界名单独存储的选区数据（key=世界文件夹名） */
    public java.util.Map<String, WorldData> perWorld = new java.util.LinkedHashMap<>();

    /** 每个世界独立的数据（选区、容器缓存、材料汇总） */
    public static class WorldData {
        public java.util.List<com.materialstock.data.StockArea> stockAreas = new java.util.ArrayList<>();
        public java.util.List<com.materialstock.data.StockArea> materialAreas = new java.util.ArrayList<>();
        public java.util.Map<String, java.util.Map<String, Integer>> containerCache = new java.util.LinkedHashMap<>();
        public java.util.Map<String, Integer> materialSummary = new java.util.LinkedHashMap<>();
        /** 材料区容器位置（见 StockConfig.materialContainerKeys） */
        public List<String> materialContainerKeys = new ArrayList<>();
    }

    private static StockConfig instance;

    public static StockConfig get() {
        if (instance == null) {
            instance = new StockConfig();
        }
        return instance;
    }

    public static void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (Files.exists(CONFIG_FILE)) {
                String json = Files.readString(CONFIG_FILE);
                instance = GSON.fromJson(json, StockConfig.class);
                if (instance == null) {
                    instance = new StockConfig();
                }
                instance.sanitize();
            } else {
                instance = new StockConfig();
            }
        } catch (Exception e) {
            MaterialStock.LOGGER.error("[MaterialStock] Failed to load config", e);
            instance = new StockConfig();
        }
    }

    public static void save() {
        try {
            // 先同步当前世界数据到 perWorld
            if (WorldManager.getCurrentWorldKey() != null) {
                WorldManager.saveCurrentWorld();
            }
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(get()));
        } catch (Exception e) {
            MaterialStock.LOGGER.error("[MaterialStock] Failed to save config", e);
        }
    }

    private void sanitize() {
        if (this.stockAreas == null) this.stockAreas = new ArrayList<>();
        if (this.materialAreas == null) this.materialAreas = new ArrayList<>();
        if (this.recipes == null) this.recipes = new ArrayList<>();
        if (this.favorites == null) this.favorites = new ArrayList<>();
        if (this.calcProducts == null) this.calcProducts = new ArrayList<>();
        if (this.lockedProducts == null) this.lockedProducts = new java.util.HashSet<>();
        if (this.containerCache == null) this.containerCache = new java.util.LinkedHashMap<>();
        if (this.materialSummary == null) this.materialSummary = new java.util.LinkedHashMap<>();
        if (this.materialContainerKeys == null) this.materialContainerKeys = new ArrayList<>();
        if (this.trackedItems == null) this.trackedItems = new ArrayList<>();
        if (this.collapsedCats == null) this.collapsedCats = new ArrayList<>();
        if (this.ignoredMaterials == null) this.ignoredMaterials = new ArrayList<>();
        if (this.hudSchematic == null) this.hudSchematic = "";
        if (this.materialCache == null) this.materialCache = new java.util.LinkedHashMap<>();
        if (this.categoryOverrides == null) this.categoryOverrides = new java.util.LinkedHashMap<>();
        if (this.extraCategories == null) this.extraCategories = new ArrayList<>();
        if (this.extraSubs == null) this.extraSubs = new java.util.LinkedHashMap<>();
        if (this.removedCategories == null) this.removedCategories = new ArrayList<>();
        if (this.removedSubs == null) this.removedSubs = new java.util.LinkedHashMap<>();
        if (this.perWorld == null) this.perWorld = new java.util.LinkedHashMap<>();
        // 旧 stock.json 的 perWorld 条目没有 materialContainerKeys 字段，反序列化后为 null
        for (WorldData wd : this.perWorld.values()) {
            if (wd == null) continue;
            if (wd.stockAreas == null) wd.stockAreas = new java.util.ArrayList<>();
            if (wd.materialAreas == null) wd.materialAreas = new java.util.ArrayList<>();
            if (wd.containerCache == null) wd.containerCache = new java.util.LinkedHashMap<>();
            if (wd.materialSummary == null) wd.materialSummary = new java.util.LinkedHashMap<>();
            if (wd.materialContainerKeys == null) wd.materialContainerKeys = new ArrayList<>();
        }
        if (this.multiplier < 1) this.multiplier = 1;
        if (this.sortMode < 0 || this.sortMode > 2) this.sortMode = 0;
        if (this.cacheExpirySeconds < 1) this.cacheExpirySeconds = 1;
    }

    private static String loadEmbeddedResource(String path) {
        try (InputStream is = StockConfig.class.getResourceAsStream(path)) {
            if (is == null) return null;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append('\n');
                }
                return sb.toString();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
