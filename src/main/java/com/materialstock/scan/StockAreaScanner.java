package com.materialstock.scan;

import com.materialstock.StockConfig;
import com.materialstock.data.StockArea;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 备货区扫描：把框选区域内的所有容器内容读入缓存，
 * 之后材料清单中即可显示"已有"数量。物品放入容器后重新扫描即可更新。
 */
public class StockAreaScanner {
    private static boolean scanning = false;
    /** 材料区（独立区域）的汇总结果，与备货区缓存完全隔离 */
    public static volatile Map<Item, Integer> materialSummary = new java.util.LinkedHashMap<>();
    /** 材料区每容器位置->内容（用于追踪高亮材料区箱子）。
     *  用 ConcurrentHashMap：渲染线程遍历复制快照时，扫描线程可能正在 clear/put，普通 HashMap 会抛 ConcurrentModificationException。 */
    public static java.util.Map<BlockPos, java.util.List<net.minecraft.world.item.ItemStack>> materialContainerContents = new java.util.concurrent.ConcurrentHashMap<>();
    /** 材料区最近一次扫描的容器数量 */
    private static volatile int materialContainerCount = 0;

    /**
     * 材料区容器位置（持久化的"哪些箱子属于材料区"）。
     * 用途有两个：
     *  1) 追踪高亮只认材料区的箱子 —— ContainerCache 里同时装着备货区的箱子，必须靠本集合区分；
     *  2) 重启后无需重扫就能恢复追踪所需的位置数据（contents 由 ContainerCache 提供）。
     * 键为 "维度|x,y,z"，与 ContainerCache 的持久化键格式一致。
     */
    private static final java.util.Set<String> materialContainerKeys =
        java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** 材料区容器是否已被记录（用于渲染线程 O(1) 过滤） */
    public static boolean isMaterialContainer(BlockPos pos, String dim) {
        String xyz = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        return dim == null ? materialContainerKeys.contains(xyz) : materialContainerKeys.contains(dim + "|" + xyz);
    }

    /** 登记一个材料区容器（扫描时调用） */
    private static void addMaterialContainer(BlockPos pos, String dim) {
        String xyz = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        materialContainerKeys.add(dim == null ? xyz : dim + "|" + xyz);
    }

    /** 清空材料区容器记录（切世界 / 材料区被删光） */
    public static void clearMaterialContainers() {
        materialContainerKeys.clear();
    }

    public static boolean isScanning() {
        return scanning;
    }

    /** 材料区汇总：只统计材料区扫描的结果（不混入备货区） */
    public static Map<Item, Integer> summarizeMaterial() {
        return new java.util.LinkedHashMap<>(materialSummary);
    }

    /** 材料区最近一次扫描的容器数 */
    public static int materialContainerCount() {
        return materialContainerCount;
    }

    /** 材料区已扫描容器的位置集合（活视图：删除元素即从缓存移除；与 ContainerCache.getPositions 用法一致） */
    public static java.util.Set<BlockPos> materialContainerPositions() {
        return materialContainerContents.keySet();
    }

    /** 复位材料区容器计数（切世界 / 材料区被删光时调用，避免界面显示上一次的旧数字） */
    public static void resetMaterialContainerCount() {
        materialContainerCount = 0;
    }

    /**
     * 依据当前 materialContainerContents 重建材料区汇总。
     * 删除材料区后调用：不做逐项减法，直接重算，保证与扫描时的统计口径一致
     * （潜影盒/束口袋是展开统计的，逐项减法会对不上）。
     */
    public static void rebuildMaterialSummary() {
        List<ItemStack> all = new ArrayList<>();
        for (List<ItemStack> stacks : materialContainerContents.values()) {
            if (stacks != null) all.addAll(stacks);
        }
        materialSummary = ContainerCache.summarizeStacks(all);
        materialContainerCount = materialContainerContents.size();
        persistMaterialSummary();
    }

    /**
     * 汇总当前缓存中的已有材料（备货区物品数量）。
     * 只算备货区容器 —— 材料区是独立的一份库存，不能并入「已有」。
     */
    public static Map<Item, Integer> summarizeAvailable() {
        return ContainerCache.getInstance().summarizeStock(ContainerCache.currentDimension());
    }

    /** 扫描当前维度所有备货区 */
    public static void scanAll(Runnable onDone) {
        scanAreas(StockConfig.get().stockAreas, "备货区", onDone);
    }

    /** 扫描指定区域列表（材料区等独立区域），label 用于提示文案 */
    public static void scanAreas(List<StockArea> areas, String label, Runnable onDone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || scanning) {
            if (onDone != null) onDone.run();
            return;
        }
        String dim = mc.level.dimension().location().toString();
        List<StockArea> filtered = new ArrayList<>();
        for (StockArea a : areas) {
            if (a != null && a.isSameDimension(dim)) filtered.add(a);
        }
        if (filtered.isEmpty()) {
            if (onDone != null) onDone.run();
            return;
        }

        scanning = true;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
            if (serverLevel == null) {
                scanning = false;
                if (onDone != null) onDone.run();
                return;
            }
            mc.getSingleplayerServer().execute(() -> {
              try {
                int count = 0;
                java.util.Set<net.minecraft.core.BlockPos> processed = new java.util.HashSet<>();
                for (StockArea area : filtered) {
                    for (BlockEntity be : collectBlockEntities(serverLevel, area)) {
                        if (be instanceof Container container) {
                            if (processed.contains(be.getBlockPos())) continue;
                            List<ItemStack> stacks = new ArrayList<>();
                            try {
                                for (int i = 0; i < container.getContainerSize(); i++) {
                                    stacks.add(container.getItem(i));
                                }
                            } catch (Exception ignored) {
                            }
                            // 大箱子：读取另一半并合并（ChestBlockEntity.getItem 只给 27 格）
                            BlockPos other = getOtherChestHalf(be);
                            if (other != null) {
                                if (serverLevel.getBlockEntity(other) instanceof Container oc) {
                                    try {
                                        for (int i = 0; i < oc.getContainerSize(); i++) {
                                            stacks.add(oc.getItem(i));
                                        }
                                    } catch (Exception ignored) {}
                                }
                                processed.add(other);
                            }
                            ContainerCache.getInstance().put(canonicalPos(be.getBlockPos(), other), stacks, dim);
                            count++;
                        }
                    }
                }
                // 清理"选区内的陈旧条目"：缓存只增不删会让被挖掉/移出选区的箱子永久残留并写进 stock.json，
                // 导致"已有"数量虚高。这里按本次扫描的区域剔除，然后再由下面的 put 重新填充。
                // 按维度过滤：主世界与下界同坐标的容器不能互相删。
                java.util.Iterator<BlockPos> stale = ContainerCache.getInstance().getPositions(dim).iterator();
                while (stale.hasNext()) {
                    BlockPos p = stale.next();
                    for (StockArea a : filtered) {
                        if (a.contains(p)) { stale.remove(); break; }
                    }
                }
                final int scanned = count;
                mc.execute(() -> {
                    scanning = false;
                    if (onDone != null) onDone.run();
                    persistContainerCache();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("[材料备货助手] " + label + "扫描完成").withStyle(net.minecraft.ChatFormatting.GREEN),
                            true
                        );
                    }
                });
              } finally {
                // 兜底：异常时也必须复位，否则 scanning 永久为 true，之后所有扫描静默失效（只能重启游戏）
                scanning = false;
              }
            });
        } else {
            // 联机：收集区域内容器位置，然后通过 NBT 查询探测
            List<BlockPos> positions = new ArrayList<>();
            for (StockArea area : filtered) {
                for (BlockEntity be : collectBlockEntities(mc.level, area)) {
                    if (be instanceof Container) {
                        positions.add(be.getBlockPos());
                    }
                }
            }
            if (positions.isEmpty()) {
                scanning = false;
                if (onDone != null) onDone.run();
                return;
            }
            ContainerProbe.getInstance().startProbe(positions, () -> {
                ContainerCache cache = ContainerCache.getInstance();
                Map<BlockPos, List<ItemStack>> results = ContainerProbe.getInstance().getResults();
                // 大箱子：两半都要探测（各只有自己 27 格的 NBT），再合并到同一个键，
                // 否则同一对大箱子会被登记成两条各 27 格的记录。
                Map<BlockPos, List<ItemStack>> merged = new java.util.LinkedHashMap<>();
                for (BlockPos pos : positions) {
                    List<ItemStack> items = results.get(pos);
                    if (items == null) continue;
                    BlockPos other = isDoubleChestType(mc.level, pos)
                        ? pos.relative(ChestBlock.getConnectedDirection(mc.level.getBlockState(pos)))
                        : null;
                    BlockPos key = canonicalPos(pos, other);
                    merged.computeIfAbsent(key, k -> new ArrayList<>()).addAll(items);
                }
                // 先清理选区内的陈旧条目（缓存只增不删会让被挖掉/移出选区的箱子永久残留并写进 stock.json）
                // 按维度过滤：主世界与下界同坐标的容器不能互相删
                java.util.Iterator<BlockPos> stale = cache.getPositions(dim).iterator();
                while (stale.hasNext()) {
                    BlockPos p = stale.next();
                    for (StockArea a : filtered) {
                        if (a.contains(p)) { stale.remove(); break; }
                    }
                }
                // 再写入本次探测结果（与单机路径同样先清后填）
                for (Map.Entry<BlockPos, List<ItemStack>> e : merged.entrySet()) {
                    cache.put(e.getKey(), e.getValue(), dim);
                }
                scanning = false;
                if (onDone != null) onDone.run();
                persistContainerCache();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("[材料备货助手] " + label + "扫描完成").withStyle(net.minecraft.ChatFormatting.GREEN),
                        false
                    );
                }
            });
        }
    }

    /** 扫描材料区（独立区域）：结果只汇总到 materialSummary，不进备货区缓存 */
    public static void scanMaterialAreas(Runnable onDone) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || scanning) {
            if (onDone != null) onDone.run();
            return;
        }
        String dim = mc.level.dimension().location().toString();
        List<StockArea> areas = new ArrayList<>();
        for (StockArea a : StockConfig.get().materialAreas) {
            if (a != null && a.isSameDimension(dim)) areas.add(a);
        }
        if (areas.isEmpty()) {
            materialSummary = new java.util.LinkedHashMap<>();
            if (onDone != null) onDone.run();
            return;
        }

        scanning = true;
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            ServerLevel serverLevel = mc.getSingleplayerServer().getLevel(mc.level.dimension());
            if (serverLevel == null) {
                scanning = false;
                if (onDone != null) onDone.run();
                return;
            }
            mc.getSingleplayerServer().execute(() -> {
              try {
                List<ItemStack> all = new ArrayList<>();
                int count = 0;
                materialContainerContents.clear();
                java.util.Set<BlockPos> processed = new java.util.HashSet<>();
                for (StockArea area : areas) {
                    for (BlockEntity be : collectBlockEntities(serverLevel, area)) {
                        if (be instanceof Container container) {
                            if (processed.contains(be.getBlockPos())) continue;
                            try {
                                List<ItemStack> stacks = new ArrayList<>();
                                for (int i = 0; i < container.getContainerSize(); i++) {
                                    ItemStack st = container.getItem(i);
                                    all.add(st);
                                    stacks.add(st);
                                }
                                // 大箱子：必须合并另一半，否则只统计到 27/54
                                BlockPos other = getOtherChestHalf(be);
                                if (other != null) {
                                    if (serverLevel.getBlockEntity(other) instanceof Container oc) {
                                        try {
                                            for (int i = 0; i < oc.getContainerSize(); i++) {
                                                ItemStack st = oc.getItem(i);
                                                all.add(st);
                                                stacks.add(st);
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                    processed.add(other);
                                }
                                BlockPos key = canonicalPos(be.getBlockPos(), other);
                                materialContainerContents.put(key, stacks);
                                // 同时写入 ContainerCache（持久化）并登记为"材料区容器"，
                                // 这样追踪高亮在重启后仍有位置数据，且只认材料区的箱子。
                                // 用 putMaterial：备货区统计必须排除这些条目，两个区域保持独立。
                                ContainerCache.getInstance().putMaterial(key, stacks, dim);
                                addMaterialContainer(key, dim);
                            } catch (Exception ignored) {
                            }
                            count++;
                        }
                    }
                }
                final Map<Item, Integer> summary = ContainerCache.summarizeStacks(all);
                final int scanned = count;
                mc.execute(() -> {
                    scanning = false;
                    materialSummary = summary;
                    materialContainerCount = scanned;
                    if (onDone != null) onDone.run();
                    persistMaterialSummary();
                    if (mc.player != null) {
                        mc.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("[材料备货助手] 材料区扫描完成").withStyle(net.minecraft.ChatFormatting.GREEN),
                            true
                        );
                    }
                });
              } finally {
                // 兜底：异常时也必须复位，否则 scanning 永久为 true，之后所有扫描静默失效（只能重启游戏）
                scanning = false;
              }
            });
        } else {
            // 联机：只探测位置并查询（独立结果）
            List<BlockPos> positions = new ArrayList<>();
            for (StockArea area : areas) {
                for (BlockEntity be : collectBlockEntities(mc.level, area)) {
                    if (be instanceof Container) {
                        positions.add(be.getBlockPos());
                    }
                }
            }
            if (positions.isEmpty()) {
                scanning = false;
                materialSummary = new java.util.LinkedHashMap<>();
                if (onDone != null) onDone.run();
                return;
            }
            ContainerProbe.getInstance().startProbe(positions, () -> {
                Map<BlockPos, List<ItemStack>> results = ContainerProbe.getInstance().getResults();
                List<ItemStack> all = new ArrayList<>();
                materialContainerContents.clear();
                // 大箱子：两半各只有 27 格，合并到同一个键后再统计
                Map<BlockPos, List<ItemStack>> merged = new java.util.LinkedHashMap<>();
                for (BlockPos pos : positions) {
                    List<ItemStack> items = results.get(pos);
                    if (items == null) continue;
                    BlockPos other = isDoubleChestType(mc.level, pos)
                        ? pos.relative(ChestBlock.getConnectedDirection(mc.level.getBlockState(pos)))
                        : null;
                    BlockPos key = canonicalPos(pos, other);
                    merged.computeIfAbsent(key, k -> new ArrayList<>()).addAll(items);
                }
                for (Map.Entry<BlockPos, List<ItemStack>> e : merged.entrySet()) {
                    all.addAll(e.getValue());
                    List<ItemStack> stacks = new ArrayList<>(e.getValue());
                    materialContainerContents.put(e.getKey(), stacks);
                    // 与单人路径一致：写入 ContainerCache（持久化）+ 登记材料区容器，供追踪使用
                    ContainerCache.getInstance().putMaterial(e.getKey(), stacks, dim);
                    addMaterialContainer(e.getKey(), dim);
                }
                final Map<Item, Integer> summary = ContainerCache.summarizeStacks(all);
                scanning = false;
                materialSummary = summary;
                materialContainerCount = merged.size();
                if (onDone != null) onDone.run();
                persistMaterialSummary();
                if (mc.player != null) {
                    mc.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("[材料备货助手] 材料区扫描完成").withStyle(net.minecraft.ChatFormatting.GREEN),
                        false
                    );
                }
            });
        }
    }

    /** 备货区扫描结果写盘（退出游戏后仍保留物品明细） */

    /**
     * 是否为"大箱子"的一半。
     * 依据 ChestBlock.TYPE（SINGLE / LEFT / RIGHT），陷阱箱同样适用（TrappedChestBlock 继承 ChestBlock）。
     * 注意：不能只看相邻是否为 ChestBlockEntity —— ChestBlock.getConnectedDirection 对 SINGLE 箱子
     * 也会返回一个方向，必须先排除 SINGLE，否则会把旁边另一个独立箱子错认成配对。
     */
    private static boolean isDoubleChestType(Level level, BlockPos pos) {
        try {
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof ChestBlock)) return false;
            ChestType type = state.getValue(ChestBlock.TYPE);
            return type != null && type != ChestType.SINGLE;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 大箱子：找到另一半位置；非大箱子（单箱 / 木桶 / 潜影盒等）返回 null。
     * ChestBlockEntity.getItem() 只返回自己那 27 格，合并后的 54 格在 CompoundContainer 里，
     * 因此两半必须各自读取再合并，否则只会统计到大箱子的一半。
     */
    private static BlockPos getOtherChestHalf(BlockEntity be) {
        try {
            if (!(be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity)) return null;
            BlockPos pos = be.getBlockPos();
            Level level = be.getLevel();
            if (level == null) return null;
            if (!isDoubleChestType(level, pos)) return null;
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(level.getBlockState(pos)));
            return level.getBlockEntity(other) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity ? other : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** 同一对大箱子的两半归到同一个键，避免半块数据分散成两条 */
    private static BlockPos canonicalPos(BlockPos a, BlockPos b) {
        if (b == null) return a.immutable();
        if (a.getX() != b.getX()) return (a.getX() < b.getX() ? a : b).immutable();
        if (a.getZ() != b.getZ()) return (a.getZ() < b.getZ() ? a : b).immutable();
        return (a.getY() <= b.getY() ? a : b).immutable();
    }

    public static void persistContainerCache() {
        StockConfig cfg = StockConfig.get();
        Map<String, Map<String, Integer>> out = new java.util.LinkedHashMap<>();
        ContainerCache.getInstance().persist(out);
        cfg.containerCache = out;
        StockConfig.save();
    }

    /** 材料区汇总写盘 */
    public static void persistMaterialSummary() {
        StockConfig cfg = StockConfig.get();
        Map<String, Integer> ms = new java.util.LinkedHashMap<>();
        for (Map.Entry<Item, Integer> en : materialSummary.entrySet()) {
            ms.put(BuiltInRegistries.ITEM.getKey(en.getKey()).toString(), en.getValue());
        }
        cfg.materialSummary = ms;
        // 连同"哪些箱子属于材料区"一起写盘，否则重启后追踪没有位置可用（contents 在 ContainerCache 里）
        cfg.materialContainerKeys = new java.util.ArrayList<>(materialContainerKeys);
        StockConfig.save();
    }

    /**
     * 重启游戏后从配置恢复材料区汇总。
     * 同时重建材料区容器位置集合，并把材料区区域内的 ContainerCache 条目补进去 ——
     * 这样追踪高亮不必先扫一次材料区（ContainerCache 由 ContainerCache.restore 先行恢复）。
     * 注意调用顺序：本方法必须在 ContainerCache.restore 之后执行。
     */
    public static void restoreMaterial(Map<String, Integer> in) {
        materialSummary = new java.util.LinkedHashMap<>();
        materialContainerKeys.clear();
        StockConfig cfg = StockConfig.get();
        if (cfg.materialContainerKeys != null) {
            for (String k : cfg.materialContainerKeys) {
                if (k != null && !k.isEmpty()) materialContainerKeys.add(k);
            }
        }
        if (in != null) {
            for (Map.Entry<String, Integer> e : in.entrySet()) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(e.getKey()));
                if (item != null && item != net.minecraft.world.item.Items.AIR && e.getValue() > 0) {
                    materialSummary.put(item, e.getValue());
                }
            }
        }
        // 兼容：旧的 stock.json 没有 materialContainerKeys，或区域被移动过 ——
        // 按当前材料区范围反推一次，保证位置集合与选区一致
        rebuildMaterialContainerKeysFromAreas(cfg.materialAreas);
        // 让 ContainerCache 里属于材料区的条目标上 isMaterial 标记（备货区统计据此排除它们）。
        // 用当前已恢复的 containerCache 重放一次，避免改 restore 的入参签名而牵连调用方。
        ContainerCache.getInstance().restore(cfg.containerCache, ContainerCache.currentDimension(),
            materialContainerKeys);
    }

    /** 按材料区范围重算"材料区容器位置集合"（依据 ContainerCache 中已恢复的条目） */
    private static void rebuildMaterialContainerKeysFromAreas(List<StockArea> areas) {
        if (areas == null || areas.isEmpty()) return;
        for (Map.Entry<BlockPos, String> e : ContainerCache.getInstance().getPositionsWithDim().entrySet()) {
            BlockPos pos = e.getKey();
            String dim = e.getValue();
            if (dim == null) continue;
            for (StockArea a : areas) {
                if (a.isSameDimension(dim) && a.contains(pos)) {
                    addMaterialContainer(pos, dim);
                    break;
                }
            }
        }
    }

    /** 遍历区域内已加载区块，收集所有方块实体（不区分是否为容器） */
    private static List<BlockEntity> collectBlockEntities(Level level, StockArea area) {
        List<BlockEntity> list = new ArrayList<>();
        int minCX = area.x1 >> 4;
        int maxCX = area.x2 >> 4;
        int minCZ = area.z1 >> 4;
        int maxCZ = area.z2 >> 4;
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (level.hasChunk(cx, cz)) {
                    LevelChunk chunk = level.getChunk(cx, cz);
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (area.contains(be.getBlockPos())) {
                            list.add(be);
                        }
                    }
                }
            }
        }
        return list;
    }
}
