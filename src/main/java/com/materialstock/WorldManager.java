package com.materialstock;

import net.minecraft.client.Minecraft;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class WorldManager {
    private static String currentWorldKey = null;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            onWorldJoin();
        });
    }

    private static void onWorldJoin() {
        Minecraft mc = Minecraft.getInstance();
        String key;
        if (mc.isLocalServer() && mc.getSingleplayerServer() != null) {
            try {
                java.nio.file.Path root = mc.getSingleplayerServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                key = root.getFileName().toString();
                if (key.equals(".") || key.isEmpty()) {
                    key = root.getParent().getFileName().toString();
                }
            } catch (Exception e) {
                key = "singleplayer";
            }
        } else if (mc.getCurrentServer() != null) {
            // 只用服务器地址会让同一服务器上的多个世界（多世界/群组切换）共用一份数据而互相覆盖，
            // 因此把当前世界名也纳入 key；拿不到世界名时退回纯地址。
            String server = mc.getCurrentServer().ip;
            String world = null;
            try {
                if (mc.getSingleplayerServer() != null) {
                    world = mc.getSingleplayerServer().getWorldData().getLevelName();
                }
            } catch (Exception ignored) {
            }
            if (world == null && mc.level != null) {
                try {
                    world = mc.level.dimension().location().toString();
                } catch (Exception ignored) {
                }
            }
            key = (world == null || world.isEmpty()) ? server : (server + "@" + world);
        } else {
            key = "unknown";
        }
        if (mc.player != null) {
            mc.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("[材料备货助手] 当前世界key：" + key)
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        }
        switchToWorld(key);
    }

    public static void switchToWorld(String worldKey) {
        StockConfig cfg = StockConfig.get();
        if (currentWorldKey != null && !currentWorldKey.equals(worldKey)) {
            saveCurrentWorld();
        }
        StockConfig.WorldData wd = cfg.perWorld.get(worldKey);
        if (wd != null) {
            cfg.stockAreas = new java.util.ArrayList<>(wd.stockAreas);
            cfg.materialAreas = new java.util.ArrayList<>(wd.materialAreas);
            cfg.containerCache = new java.util.LinkedHashMap<>(wd.containerCache);
            cfg.materialSummary = new java.util.LinkedHashMap<>(wd.materialSummary);
            cfg.materialContainerKeys = new java.util.ArrayList<>(
                wd.materialContainerKeys == null ? java.util.List.of() : wd.materialContainerKeys);
        } else {
            cfg.stockAreas = new java.util.ArrayList<>();
            cfg.materialAreas = new java.util.ArrayList<>();
            cfg.containerCache = new java.util.LinkedHashMap<>();
            cfg.materialSummary = new java.util.LinkedHashMap<>();
            cfg.materialContainerKeys = new java.util.ArrayList<>();
        }
        currentWorldKey = worldKey;
        StockConfig.save();
        // 切世界必须清掉上一次的选区：SelectionManager 只存坐标、不含维度，
        // 若沿用旧世界的两个点，在新世界点"保存"会得到"新世界维度 + 旧世界坐标"的错误区域。
        com.materialstock.scan.SelectionManager.getInstance().clear();
        com.materialstock.scan.StockAreaScanner.materialContainerContents.clear();
        com.materialstock.scan.StockAreaScanner.clearMaterialContainers();
        com.materialstock.scan.ContainerCache.getInstance().restore(cfg.containerCache);
        com.materialstock.scan.StockAreaScanner.restoreMaterial(cfg.materialSummary);
        // 复位材料区容器数，否则界面仍显示上一个世界/上一次扫描的数量
        com.materialstock.scan.StockAreaScanner.resetMaterialContainerCount();
    }

    public static void saveCurrentWorld() {
        if (currentWorldKey == null) return;
        StockConfig cfg = StockConfig.get();
        StockConfig.WorldData wd = cfg.perWorld.computeIfAbsent(currentWorldKey, k -> new StockConfig.WorldData());
        wd.stockAreas = new java.util.ArrayList<>(cfg.stockAreas);
        wd.materialAreas = new java.util.ArrayList<>(cfg.materialAreas);
        wd.containerCache = new java.util.LinkedHashMap<>(cfg.containerCache);
        wd.materialSummary = new java.util.LinkedHashMap<>(cfg.materialSummary);
        wd.materialContainerKeys = new java.util.ArrayList<>(cfg.materialContainerKeys);
    }

    public static String getCurrentWorldKey() {
        return currentWorldKey;
    }
}
