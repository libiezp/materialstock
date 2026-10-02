package com.materialstock;

import com.materialstock.gui.GuiStockMain;
import com.materialstock.render.WorldRenderer;
import com.materialstock.gui.GuiStockCalculator;
import com.materialstock.render.HudRenderer;
import com.materialstock.scan.ContainerCache;
import com.materialstock.scan.ContainerProbe;
import com.materialstock.scan.StockAreaScanner;
import fi.dy.masa.malilib.event.RenderEventHandler;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 材料备货助手：投影材料清单 + 合成计算器 + 备货区统计。
 */
public class MaterialStock implements ClientModInitializer {
    public static final String MOD_ID = "materialstock";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        KeyBindings.init(); // 必须在 GameOptions 初始化前注册快捷键
        StockConfig.load();
        // 重启游戏后恢复扫描结果（备货区物品明细 / 材料区物品明细）
        ContainerCache.getInstance().restore(StockConfig.get().containerCache);
        StockAreaScanner.restoreMaterial(StockConfig.get().materialSummary);
        com.materialstock.litematica.MaterialListHelper.restoreDiskCache(StockConfig.get().materialCache);
        RenderEventHandler.getInstance().registerWorldLastRenderer(WorldRenderer.getInstance());
        com.materialstock.WorldManager.register();
        HudRenderer.register();
        // 恢复信息浮窗（退游戏后保留开关与来源投影）
        if (StockConfig.get().hudEnabled) {
            try {
                String hs = StockConfig.get().hudSchematic;
                if (hs != null && !hs.isEmpty()) {
                    java.util.Map<net.minecraft.world.item.Item, Integer> counts =
                        com.materialstock.litematica.MaterialListHelper.loadSchematicMaterials(hs);
                    if (counts != null && !counts.isEmpty()) {
                        HudRenderer.hudMaterials = counts;
                    }
                }
            } catch (Throwable t) {
                LOGGER.warn("恢复信息浮窗失败（litematica 尚未就绪）：{}", t.toString());
            }
            HudRenderer.hudEnabled = true;
        }

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ContainerProbe.getInstance().onClientTick();
            KeyBindings.onTick(client);
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            GuiStockCalculator.persistSessionStatic();  // 计算器选择落盘
            Map<String, Map<String, Integer>> cc = new java.util.LinkedHashMap<>();
            ContainerCache.getInstance().persist(cc);
            StockConfig.get().containerCache = cc;
            StockConfig.save();                          // 扫描明细落盘
        });

        // 原版容器界面：开箱子时提示所属选区
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) return;
            if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr && client.level != null && client.player != null) {
                net.minecraft.core.BlockPos bp = bhr.getBlockPos();
                String dim = client.level.dimension().location().toString();
                String stockName = null;
                for (com.materialstock.data.StockArea area : StockConfig.get().stockAreas) {
                    if (area.isSameDimension(dim) && area.contains(bp)) { stockName = area.name; break; }
                }
                String matName = null;
                for (com.materialstock.data.StockArea area : StockConfig.get().materialAreas) {
                    if (area.isSameDimension(dim) && area.contains(bp)) { matName = area.name; break; }
                }
                if (stockName != null) {
                    client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§b此箱子处于备货区：" + stockName), true);
                } else if (matName != null) {
                    client.player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§6此箱子处于材料区：" + matName), true);
                }
            }
        });

        // 原版容器界面：仅当打开的箱子位于备货区选区内时，才加"扫描备货区"按钮
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen)) return;
            boolean inStockArea = false;
            if (client.hitResult instanceof net.minecraft.world.phys.BlockHitResult bhr && client.level != null) {
                net.minecraft.core.BlockPos bp = bhr.getBlockPos();
                String dim = client.level.dimension().location().toString();
                for (com.materialstock.data.StockArea area : StockConfig.get().stockAreas) {
                    if (area.isSameDimension(dim) && area.contains(bp)) { inStockArea = true; break; }
                }
            }
            if (!inStockArea) return;
            net.minecraft.client.gui.components.Button btn = net.minecraft.client.gui.components.Button.builder(
                net.minecraft.network.chat.Component.literal("扫描备货区"), b -> {
                    StockAreaScanner.scanAll(() -> {
                        client.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§a[材料备货助手] 备货区扫描完成"), true);
                    });
                }).bounds(scaledWidth / 2 - 50, 28, 100, 20).build();
            Screens.getButtons(screen).add(btn);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            dispatcher.register(ClientCommandManager.literal("materialstock").executes(ctx -> {
                Minecraft.getInstance().setScreen(new GuiStockMain());
                return 1;
            }))
        );

        LOGGER.info("[MaterialStock] 材料备货助手初始化完成 (材料清单 / 合成计算器 / 备货区)");
    }
}
