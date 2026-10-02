package com.materialstock.scan;

import fi.dy.masa.malilib.util.InventoryUtils;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 容器内容缓存：保存每个已扫描容器位置的物品列表与时间戳，
 * 并汇总备货区已有材料数量（自动展开潜影盒与束口袋）。
 */
public class ContainerCache {
    private static final ContainerCache INSTANCE = new ContainerCache();

    private static final class CacheEntry {
        final List<ItemStack> stacks;
        final long time;
        /** 该容器所在维度（如 "minecraft:overworld"）。主世界与下界同坐标必须区分，否则互相覆盖。 */
        final String dim;
        /**
         * 是否属于「材料区」。
         * 材料区的容器也放这里（为了持久化，好让追踪高亮重启后仍可用），
         * 但材料区必须与备货区【独立统计】——备货区的容器数/物品明细/已有数量都不能把它算进去。
         */
        final boolean isMaterial;

        CacheEntry(List<ItemStack> stacks, long time, String dim, boolean isMaterial) {
            this.stacks = stacks;
            this.time = time;
            this.dim = dim;
            this.isMaterial = isMaterial;
        }
    }

    /** 当前所在维度（用于未显式传维度的调用）；拿不到时返回 null 表示"不做维度过滤" */
    public static String currentDimension() {
        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                return mc.level.dimension().location().toString();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private final Map<BlockPos, CacheEntry> cache = new ConcurrentHashMap<>();

    public static ContainerCache getInstance() {
        return INSTANCE;
    }

    /** 兼容旧签名：使用当前维度（备货区写入） */
    public void put(BlockPos pos, List<ItemStack> stacks) {
        put(pos, stacks, currentDimension());
    }

    /** 备货区写入 */
    public void put(BlockPos pos, List<ItemStack> stacks, String dim) {
        this.cache.put(pos.immutable(),
            new CacheEntry(new ArrayList<>(stacks), System.currentTimeMillis(), dim, false));
    }

    /**
     * 材料区写入。
     * 若该位置已有备货区条目则不覆盖 —— 两个选区可能重叠，
     * 备货区的统计口径优先（否则一次材料区扫描会悄悄改掉备货区的"已有"数量）。
     */
    public void putMaterial(BlockPos pos, List<ItemStack> stacks, String dim) {
        BlockPos key = pos.immutable();
        CacheEntry old = this.cache.get(key);
        if (old != null && !old.isMaterial) return;
        this.cache.put(key,
            new CacheEntry(new ArrayList<>(stacks), System.currentTimeMillis(), dim, true));
    }

    /** 是否为材料区条目 */
    public boolean isMaterialEntry(BlockPos pos) {
        CacheEntry e = this.cache.get(pos.immutable());
        return e != null && e.isMaterial;
    }


    public List<ItemStack> getStacks(BlockPos pos) {
        CacheEntry e = this.cache.get(pos.immutable());
        return e != null ? e.stacks : null;
    }

    public boolean isExpired(BlockPos pos, long maxAgeMs) {
        CacheEntry e = this.cache.get(pos.immutable());
        return e == null || (System.currentTimeMillis() - e.time) > maxAgeMs;
    }

    public boolean contains(BlockPos pos) {
        return this.cache.containsKey(pos.immutable());
    }

    /** 兼容旧签名：当前维度的【备货区】容器数（不含材料区） */
    public int size() {
        return stockSize(currentDimension());
    }

    /** 兼容旧签名：当前维度的容器位置（活视图，删除元素即从缓存移除） */
    public Set<BlockPos> getPositions() {
        return getPositions(currentDimension());
    }

    /**
     * 指定维度的容器位置（活视图，支持 iterator().remove() 直接删缓存）。
     * dim 为 null 时返回全部维度的位置。
     */
    public Set<BlockPos> getPositions(String dim) {
        if (dim == null) {
            return this.cache.keySet();
        }
        Set<BlockPos> out = ConcurrentHashMap.newKeySet();
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (dim.equals(e.getValue().dim)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** 某位置所属维度；不存在返回 null */
    public String dimensionOf(BlockPos pos) {
        CacheEntry e = this.cache.get(pos);
        return e == null ? null : e.dim;
    }

    /**
     * 全部条目「位置 → 维度」。供需要按区域反查归属的调用方使用
     * （如按材料区范围重建"材料区容器位置集合"）。
     */
    public Map<BlockPos, String> getPositionsWithDim() {
        Map<BlockPos, String> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (e.getValue() != null) out.put(e.getKey(), e.getValue().dim);
        }
        return out;
    }

    /**
     * 移除指定维度且落在某区域内的【备货区】缓存条目（删备货区/扫描后清理陈旧数据用）。
     * 材料区条目不在这里删 —— 它们由 materialContainerKeys 与 materialContainerContents 管理。
     */
    public void removeInArea(String dim, com.materialstock.data.StockArea area) {
        if (area == null) return;
        java.util.Iterator<Map.Entry<BlockPos, CacheEntry>> it = this.cache.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, CacheEntry> e = it.next();
            if (e.getValue().isMaterial) continue;           // 材料区不归这里管
            if (dim != null && !dim.equals(e.getValue().dim)) continue;
            if (area.contains(e.getKey())) {
                it.remove();
            }
        }
    }

    /**
     * 清空【备货区】缓存（保留材料区条目）。
     * 材料区条目是追踪高亮的数据来源，被误清会导致追踪失效。
     */
    public void clear() {
        this.cache.entrySet().removeIf(e -> !e.getValue().isMaterial);
    }

    /** 找出装有指定物品的容器位置（用于追踪高亮）；只查当前维度 */
    public java.util.Set<BlockPos> findPositionsWithItem(net.minecraft.world.item.Item target) {
        String dim = currentDimension();
        java.util.Set<BlockPos> result = new java.util.HashSet<>();
        for (java.util.Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (dim != null && !dim.equals(e.getValue().dim)) continue;
            if (e.getValue() == null || e.getValue().stacks == null) continue;
            for (net.minecraft.world.item.ItemStack st : e.getValue().stacks) {
                if (st != null && !st.isEmpty() && st.is(target)) {
                    result.add(e.getKey());
                    break;
                }
            }
        }
        return result;
    }

    /** 找出缓存中包含任一所需物品的容器位置（用于高亮）；只查当前维度 */
    public List<BlockPos> matchContainers(Set<Item> required) {
        List<BlockPos> matched = new ArrayList<>();
        if (required == null || required.isEmpty()) return matched;
        String dim = currentDimension();
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (dim != null && !dim.equals(e.getValue().dim)) continue;
            for (ItemStack stack : e.getValue().stacks) {
                if (stack != null && !stack.isEmpty()) {
                    if (required.contains(stack.getItem())) {
                        matched.add(e.getKey());
                        break;
                    }
                    // 潜影盒内部含所需物品也视为命中
                    boolean hit = false;
                    try {
                        for (ItemStack inner : InventoryUtils.getStoredItems(stack)) {
                            if (inner != null && !inner.isEmpty() && required.contains(inner.getItem())) {
                                hit = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (hit) {
                        matched.add(e.getKey());
                        break;
                    }
                }
            }
        }
        return matched;
    }

    /**
     * 汇总所有缓存容器中的物品数量（展开潜影盒/束口袋）。
     * 已排除材料区条目：本方法服务于「备货区已有数量」，材料区必须独立统计。
     */
    public Map<Item, Integer> summarizeAll() {
        return summarizeStock(currentDimension());
    }

    /**
     * 只汇总【备货区】容器的物品数量（材料区条目不计入）。
     * dim 为 null 时汇总全部维度。
     */
    public Map<Item, Integer> summarizeStock(String dim) {
        Map<Item, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (e.getValue().isMaterial) continue;           // 材料区不进备货区统计
            if (dim != null && !dim.equals(e.getValue().dim)) continue;
            for (ItemStack stack : e.getValue().stacks) {
                if (stack == null || stack.isEmpty()) continue;
                countStack(stack, map);
            }
        }
        return map;
    }

    /** 只统计【备货区】容器数量（材料区条目不计入）；dim 为 null 时全部维度 */
    public int stockSize(String dim) {
        int n = 0;
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (e.getValue().isMaterial) continue;
            if (dim != null && !dim.equals(e.getValue().dim)) continue;
            n++;
        }
        return n;
    }

    /** 指定维度的汇总（含材料区）；保留给需要"全部容器"的调用方 */
    public Map<Item, Integer> summarizeAll(String dim) {
        Map<Item, Integer> map = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            if (dim != null && !dim.equals(e.getValue().dim)) continue;
            for (ItemStack stack : e.getValue().stacks) {
                if (stack == null || stack.isEmpty()) continue;
                countStack(stack, map);
            }
        }
        return map;
    }

    /** 汇总一批容器物品（展开潜影盒/束口袋；有内容的容器不统计本体，只统计空容器） */
    public static Map<Item, Integer> summarizeStacks(List<ItemStack> stacks) {
        Map<Item, Integer> map = new LinkedHashMap<>();
        if (stacks != null) {
            for (ItemStack stack : stacks) {
                if (stack == null || stack.isEmpty()) continue;
                countStack(stack, map);
            }
        }
        return map;
    }

    public static void countStack(ItemStack stack, Map<Item, Integer> map) {
        Item item = stack.getItem();
        if (item == net.minecraft.world.item.Items.AIR) return;
        // 潜影盒/束口袋：有内容时不统计容器本体（内部物品已并入明细），只统计空的容器
        List<ItemStack> stored = null;
        List<ItemStack> bundle = null;
        boolean hasContent = false;
        try {
            stored = InventoryUtils.getStoredItems(stack);
            if (stored != null) {
                for (ItemStack inner : stored) {
                    if (inner != null && !inner.isEmpty()) {
                        hasContent = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        try {
            bundle = InventoryUtils.getBundleItems(stack);
            if (bundle != null && !hasContent) {
                for (ItemStack inner : bundle) {
                    if (inner != null && !inner.isEmpty()) {
                        hasContent = true;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (hasContent) {
            if (stored != null) {
                for (ItemStack inner : stored) {
                    if (inner != null && !inner.isEmpty()) {
                        map.merge(inner.getItem(), inner.getCount(), Integer::sum);
                    }
                }
            }
            if (bundle != null) {
                for (ItemStack inner : bundle) {
                    if (inner != null && !inner.isEmpty()) {
                        map.merge(inner.getItem(), inner.getCount(), Integer::sum);
                    }
                }
            }
            return;
        }
        map.merge(item, stack.getCount(), Integer::sum);
    }

    /** 导出缓存为可序列化 Map（展开潜影盒/束口袋）："维度|x,y,z" → 物品ID → 数量 */
    public void persist(Map<String, Map<String, Integer>> out) {
        out.clear();
        for (Map.Entry<BlockPos, CacheEntry> e : this.cache.entrySet()) {
            BlockPos pos = e.getKey();
            Map<String, Integer> items = new LinkedHashMap<>();
            for (Map.Entry<Item, Integer> en : summarizeStacks(e.getValue().stacks).entrySet()) {
                items.put(BuiltInRegistries.ITEM.getKey(en.getKey()).toString(), en.getValue());
            }
            out.put(persistKey(e.getValue().dim, pos), items);
        }
    }

    /** 持久化 key：优先带维度（"dim|x,y,z"），维度缺失时退回旧的 "x,y,z" 以兼容旧配置 */
    private static String persistKey(String dim, BlockPos pos) {
        String xyz = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return dim == null ? xyz : dim + "|" + xyz;
    }

    /** 从持久化数据恢复缓存（重建普通 ItemStack，数量按 64 拆组；已展开物品直接计数） */
    public void restore(Map<String, Map<String, Integer>> in) {
        restore(in, currentDimension(), null);
    }

    public void restore(Map<String, Map<String, Integer>> in, String dimFallback) {
        restore(in, dimFallback, null);
    }

    /**
     * 恢复缓存。dimFallback 用于兼容旧格式（无维度前缀的 "x,y,z" 条目，视为当前维度）。
     * materialKeys 为「材料区容器」键集合（"维度|x,y,z"）：命中的条目标记为材料区，
     * 这样追踪高亮的 isMaterialContainer 过滤与备货区统计的排除都能在重启后继续成立。
     */
    public void restore(Map<String, Map<String, Integer>> in, String dimFallback,
                        java.util.Set<String> materialKeys) {
        this.cache.clear();
        if (in == null) return;
        for (Map.Entry<String, Map<String, Integer>> e : in.entrySet()) {
            String rawKey = e.getKey();
            String dim = dimFallback;
            int sep = rawKey.indexOf('|');
            if (sep > 0) {
                dim = rawKey.substring(0, sep);
                rawKey = rawKey.substring(sep + 1);
            }
            String[] p = rawKey.split(",");
            if (p.length != 3) continue;
            try {
                BlockPos pos = new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
                List<ItemStack> stacks = new ArrayList<>();
                for (Map.Entry<String, Integer> it : e.getValue().entrySet()) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(it.getKey()));
                    if (item == null || item == Items.AIR || it.getValue() <= 0) continue;
                    int remain = it.getValue();
                    while (remain > 0) {
                        int c = Math.min(64, remain);
                        stacks.add(new ItemStack(item, c));
                        remain -= c;
                    }
                }
                boolean isMat = materialKeys != null && materialKeys.contains(e.getKey());
                this.cache.put(pos.immutable(),
                    new CacheEntry(stacks, System.currentTimeMillis(), dim, isMat));
            } catch (Exception ignored) {
            }
        }
    }
}
