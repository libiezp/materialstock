package com.materialstock.scan;

import com.materialstock.MaterialStock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.game.ClientboundTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.world.item.ItemStack;

/**
 * 联机容器探测：通过方块实体 NBT 查询包读取远处容器内容。
 * 实现参考 litestock 的 ContainerProbe。
 */
public class ContainerProbe {
    private static final ContainerProbe INSTANCE = new ContainerProbe();
    private static final int BATCH_SIZE = 8;
    private static final int BATCH_TIMEOUT_TICKS = 40;
    private static final long PROBE_COOLDOWN_MS = 5000L;

    private final Set<BlockPos> probeQueue = ConcurrentHashMap.newKeySet();
    // results / lastProbeTime 会被【网络线程】写（onTagQueryResponse 经 mixin 在包处理器 HEAD 内联调用，
    // 早于 vanilla 的 PacketUtils.ensureRunningOnSameThread），同时被【客户端线程】读
    // （startProbe 读 lastProbeTime、getResults 读 results），因此必须用并发容器，否则可能读到脏数据甚至死循环。
    private final Map<BlockPos, List<ItemStack>> results = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> lastProbeTime = new ConcurrentHashMap<>();
    private final Map<Integer, BlockPos> pendingBatch = new ConcurrentHashMap<>();
    private final Map<Integer, Integer> pendingBatchAge = new ConcurrentHashMap<>();
    private int batchTicks = 0;
    private volatile boolean probing = false;
    private volatile Runnable onComplete = null;
    private int transactionIdCounter = 0;
    // 计数同样跨线程（网络线程 ++、客户端线程读日志），用 volatile 保证可见性
    private volatile int totalQueriesSent = 0;
    private volatile int totalResponsesReceived = 0;

    public static ContainerProbe getInstance() {
        return INSTANCE;
    }

    public void startProbe(List<BlockPos> positions, Runnable callback) {
        this.stop();
        this.onComplete = callback;
        this.probing = true;
        long now = System.currentTimeMillis();
        for (BlockPos pos : positions) {
            long last = this.lastProbeTime.getOrDefault(pos, 0L);
            if (now - last > PROBE_COOLDOWN_MS) {
                this.probeQueue.add(pos.immutable());
            }
        }
        MaterialStock.LOGGER.info("[MaterialStock] Probe start: {} positions", this.probeQueue.size());
        if (this.probeQueue.isEmpty()) {
            this.probing = false;
            if (this.onComplete != null) {
                this.onComplete.run();
            }
        }
    }

    public void onClientTick() {
        if (!this.probing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            this.stop();
            return;
        }

        if (!this.pendingBatch.isEmpty()) {
            this.batchTicks++;
            for (Map.Entry<Integer, Integer> e : new ArrayList<>(this.pendingBatchAge.entrySet())) {
                int txId = e.getKey();
                int age = e.getValue() + 1;
                this.pendingBatchAge.put(txId, age);
                if (age > BATCH_TIMEOUT_TICKS) {
                    this.pendingBatch.remove(txId);
                    this.pendingBatchAge.remove(txId);
                    MaterialStock.LOGGER.debug("[MaterialStock] Probe timeout txId={}", txId);
                }
            }
        }

        if (this.pendingBatch.isEmpty()) {
            if (this.probeQueue.isEmpty()) {
                this.probing = false;
                MaterialStock.LOGGER.info(
                    "[MaterialStock] Probe complete: {} results, sent={} responses={}",
                    this.results.size(), this.totalQueriesSent, this.totalResponsesReceived
                );
                if (this.onComplete != null) {
                    mc.execute(this.onComplete);
                }
            } else {
                this.sendNextBatch(mc);
            }
        }
    }

    private void sendNextBatch(Minecraft mc) {
        if (mc.player == null || mc.player.connection == null) return;
        BlockPos origin = mc.player.blockPosition();
        List<BlockPos> batch = new ArrayList<>();
        while (!this.probeQueue.isEmpty() && batch.size() < BATCH_SIZE) {
            BlockPos nearest = this.findNearest(origin);
            if (nearest == null) break;
            this.probeQueue.remove(nearest);
            batch.add(nearest);
        }
        if (!batch.isEmpty()) {
            this.batchTicks = 0;
            for (BlockPos pos : batch) {
                int txId = this.transactionIdCounter++;
                this.pendingBatch.put(txId, pos.immutable());
                this.pendingBatchAge.put(txId, 0);
                this.totalQueriesSent++;
                ServerboundBlockEntityTagQueryPacket packet = new ServerboundBlockEntityTagQueryPacket(txId, pos);
                mc.player.connection.send(packet);
            }
        }
    }

    private BlockPos findNearest(BlockPos origin) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : this.probeQueue) {
            double dist = pos.distSqr(origin);
            if (dist < nearestDist) {
                nearestDist = dist;
                nearest = pos;
            }
        }
        return nearest;
    }

    public void onTagQueryResponse(ClientboundTagQueryPacket packet) {
        if (!this.probing) return;
        int txId = packet.getTransactionId();
        BlockPos pos = this.pendingBatch.remove(txId);
        if (pos == null) return;
        this.pendingBatchAge.remove(txId);
        this.totalResponsesReceived++;
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            List<ItemStack> items = this.parseItemsFromNbt(tag);
            this.results.put(pos, items);
            this.lastProbeTime.put(pos, System.currentTimeMillis());
        } else {
            this.results.put(pos, new ArrayList<>());
        }
    }

    private List<ItemStack> parseItemsFromNbt(CompoundTag tag) {
        List<ItemStack> items = new ArrayList<>();
        if (tag != null && tag.contains("Items")) {
            ListTag itemsList = tag.getList("Items", 10);
            for (int i = 0; i < itemsList.size(); i++) {
                CompoundTag itemTag = itemsList.getCompound(i);
                if (itemTag.isEmpty()) continue;
                try {
                    Optional<ItemStack> parsed = ItemStack.OPTIONAL_CODEC.parse(NbtOps.INSTANCE, itemTag).result();
                    if (parsed.isPresent() && !parsed.get().isEmpty()) {
                        items.add(parsed.get());
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return items;
    }

    public Map<BlockPos, List<ItemStack>> getResults() {
        return this.results;
    }

    public boolean isProbing() {
        return this.probing;
    }

    public int getQueueSize() {
        return this.probeQueue.size() + this.pendingBatch.size();
    }

    public void stop() {
        this.probing = false;
        this.probeQueue.clear();
        this.pendingBatch.clear();
        this.pendingBatchAge.clear();
        this.batchTicks = 0;
        // 关键：不能直接丢弃回调。调用方（StockAreaScanner）把 scanning 复位和 UI 收尾都放在回调里，
        // 一旦丢弃，scanning 会永久停在 true，之后所有扫描静默失效，只能重启游戏。
        Runnable cb = this.onComplete;
        this.onComplete = null;
        if (cb != null) {
            cb.run();
        }
    }
}
