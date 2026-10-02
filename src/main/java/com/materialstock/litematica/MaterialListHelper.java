package com.materialstock.litematica;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.materials.MaterialListEntry;
import fi.dy.masa.litematica.materials.MaterialListSchematic;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.util.FileType;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.Item;

/**
 * 投影文件材料清单：选择投影文件 → 生成按物品聚合的材料统计。
 * 文件列表与材料解析结果都会缓存，避免每次打开界面重复扫描与解析。
 */
public class MaterialListHelper {

    /** 支持的文件扩展名 */
    private static final String[] SUPPORTED_EXT = {".litematic", ".schem", ".schematic", ".nbt"};

    /** 会话内缓存的投影文件列表 */
    private static List<String> cachedFileList = null;
    /** 会话内缓存的材料清单：文件名 → Map<Item, 数量> */
    private static final Map<String, Map<Item, Integer>> cachedMaterials = new LinkedHashMap<>();

    /** 列出投影目录下的所有投影文件（相对路径，含扩展名）；有缓存时直接返回缓存 */
    public static List<String> listSchematicFiles() {
        if (cachedFileList != null) {
            return new ArrayList<>(cachedFileList);
        }
        List<String> names = new ArrayList<>();
        File base = DataManager.getSchematicsBaseDirectory();
        if (base == null || !base.isDirectory()) return names;
        collectFiles(base, "", names);
        cachedFileList = names;
        return names;
    }

    /** 强制重新扫描投影目录 */
    public static void refreshFileList() {
        cachedFileList = null;
    }

    /** 已缓存材料清单的投影项目名 */
    public static java.util.Set<String> cachedProjectNames() {
        return new java.util.LinkedHashSet<>(cachedMaterials.keySet());
    }

    /** 是否已缓存该投影 */
    public static boolean isCached(String fileName) {
        return cachedMaterials.containsKey(fileName);
    }

    /** 获取缓存的材料清单（返回副本，避免外部修改缓存） */
    public static Map<Item, Integer> getCachedMaterials(String fileName) {
        Map<Item, Integer> hit = cachedMaterials.get(fileName);
        return hit != null ? new LinkedHashMap<>(hit) : null;
    }

    /** 清除全部材料缓存（同时清磁盘缓存） */
    public static void clearMaterialsCache() {
        cachedMaterials.clear();
        com.materialstock.StockConfig.get().materialCache.clear();
        com.materialstock.StockConfig.save();
    }

    /** 清除单个投影的材料缓存（同时清磁盘缓存） */
    public static void clearMaterialCache(String fileName) {
        if (fileName != null) {
            cachedMaterials.remove(fileName);
            com.materialstock.StockConfig.get().materialCache.remove(fileName);
            com.materialstock.StockConfig.save();
        }
    }

    /** 启动时从配置恢复磁盘缓存（退游戏后保存的投影材料清单） */
    public static void restoreDiskCache(java.util.Map<String, java.util.Map<String, Integer>> cache) {
        if (cache == null) return;
        for (java.util.Map.Entry<String, java.util.Map<String, Integer>> e : cache.entrySet()) {
            Map<Item, Integer> m = new LinkedHashMap<>();
            for (java.util.Map.Entry<String, Integer> en : e.getValue().entrySet()) {
                try {
                    Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(en.getKey()));
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        mergeItem(m, item, en.getValue());
                    }
                } catch (Exception ignored) {
                }
            }
            if (!m.isEmpty()) {
                cachedMaterials.put(e.getKey(), m);
            }
        }
    }

    private static void collectFiles(File dir, String prefix, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectFiles(f, prefix + f.getName() + "/", out);
            } else if (f.isFile()) {
                for (String ext : SUPPORTED_EXT) {
                    if (f.getName().endsWith(ext)) {
                        out.add(prefix + f.getName());
                        break;
                    }
                }
            }
        }
    }

    /**
     * 加载投影文件并生成材料清单（总需求量）。
     * 命中缓存时直接返回缓存结果；失败返回 null。
     */
    public static Map<Item, Integer> loadSchematicMaterials(String fileName) {
        Map<Item, Integer> hit = cachedMaterials.get(fileName);
        if (hit != null) {
            return new LinkedHashMap<>(hit);
        }
        // 磁盘缓存（退游戏后保存）
        java.util.Map<String, Integer> disk = com.materialstock.StockConfig.get().materialCache.get(fileName);
        if (disk != null && !disk.isEmpty()) {
            Map<Item, Integer> m = new LinkedHashMap<>();
            for (java.util.Map.Entry<String, Integer> en : disk.entrySet()) {
                try {
                    Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(en.getKey()));
                    if (item != null && item != net.minecraft.world.item.Items.AIR) {
                        mergeItem(m, item, en.getValue());
                    }
                } catch (Exception ignored) {
                }
            }
            if (!m.isEmpty()) {
                cachedMaterials.put(fileName, m);
                return new LinkedHashMap<>(m);
            }
        }

        File base = DataManager.getSchematicsBaseDirectory();
        if (base == null || fileName == null || fileName.isEmpty()) return null;
        FileType type = FileType.fromName(fileName);
        if (type == FileType.INVALID || type == FileType.UNKNOWN || type == FileType.JSON) return null;

        LitematicaSchematic schematic = LitematicaSchematic.createFromFile(base, fileName, type);
        if (schematic == null) return null;

        try {
            MaterialListSchematic materialList = new MaterialListSchematic(schematic, true);
            Map<Item, Integer> counts = new LinkedHashMap<>();
            for (MaterialListEntry entry : materialList.getMaterialsAll()) {
                Item item = entry.getStack().getItem();
                int count = entry.getCountTotal();
                mergeItem(counts, item, count);
            }
            cachedMaterials.put(fileName, counts);
            // 写入磁盘缓存（退游戏后仍保留，不用每次重新解析）
            java.util.Map<String, Integer> diskOut = new LinkedHashMap<>();
            for (Map.Entry<Item, Integer> en : counts.entrySet()) {
                diskOut.put(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(en.getKey()).toString(), en.getValue());
            }
            com.materialstock.StockConfig.get().materialCache.put(fileName, diskOut);
            com.materialstock.StockConfig.save();
            // 必须返回副本：命中缓存的分支返回的是副本，这里若直接返回内部实例，
            // 调用方（HudRenderer.hudMaterials = counts 后 clear()）会把缓存本体清空，
            // 导致该投影本次游戏内永久"已缓存但 0 材料"，只能重启恢复。
            return new LinkedHashMap<>(counts);
        } catch (Exception e) {
            return null;
        }
    }

    /** 物品显示名 */
    /** 数量格式化：>=1728 显示 N盒+M个（潜影盒）；>=64 显示 N×64+M个；<64 显示 N个 */
    public static String formatQty(int n) {
        if (n < 0) n = 0;
        if (n >= 1728) {
            return (n / 1728) + "盒+" + (n % 1728) + "个";
        }
        if (n >= 64) {
            return (n / 64) + "×64+" + (n % 64) + "个";
        }
        return n + "个";
    }

    public static String getItemDisplayName(Item item) {
        return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
    }

    /**
     * 墙面变体（"墙上的XX"）→ 对应正常物品；非墙面变体返回 null。
     * wall_torch → torch，oak_wall_sign → oak_sign，skeleton_wall_skull → skeleton_skull 等。
     */
    private static Item normalizeWallVariant(Item item) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).getPath();
        String norm;
        if (id.startsWith("wall_")) {
            norm = id.substring("wall_".length());
        } else if (id.contains("_wall_")) {
            norm = id.replace("_wall_", "_");
        } else {
            return null;
        }
        try {
            Item n = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(norm));
            return (n != null && n != net.minecraft.world.item.Items.AIR) ? n : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 归并物品数量：墙面变体的数量计入对应正常物品，墙面变体本身不进入清单 */
    private static void mergeItem(Map<Item, Integer> counts, Item item, int count) {
        Item norm = normalizeWallVariant(item);
        counts.merge(norm != null ? norm : item, count, Integer::sum);
    }
}
