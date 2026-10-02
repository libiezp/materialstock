package com.materialstock;

import com.materialstock.gui.GuiStockArea;
import com.materialstock.gui.GuiStockMain;
import com.materialstock.scan.SelectionManager;
import com.materialstock.scan.StockAreaScanner;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

/**
 * 快捷键：可在 游戏菜单-选项-控制-按键绑定 中修改。
 */
public class KeyBindings {
    public static final KeyMapping OPEN_GUI = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.materialstock.open_gui", GLFW.GLFW_KEY_Y, "key.categories.materialstock"));
    public static final KeyMapping SELECT_POINT = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.materialstock.select_point", GLFW.GLFW_KEY_K, "key.categories.materialstock"));
    public static final KeyMapping SCAN_AREAS = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.materialstock.scan_areas", GLFW.GLFW_KEY_UNKNOWN, "key.categories.materialstock"));
    public static final KeyMapping OPEN_AREA_GUI = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.materialstock.open_area_gui", GLFW.GLFW_KEY_UNKNOWN, "key.categories.materialstock"));
    /** 切换材料区高亮显示/隐藏（材料区默认不显示框，按下此键才显示） */
    public static final KeyMapping TOGGLE_MATERIAL_VIS = KeyBindingHelper.registerKeyBinding(
        new KeyMapping("key.materialstock.toggle_material_vis", GLFW.GLFW_KEY_H, "key.categories.materialstock"));

    /** 获取选点键的显示名（如 "K"） */
    public static String selectKeyName() {
        return SELECT_POINT.getTranslatedKeyMessage().getString().toUpperCase();
    }

    /** 获取高亮切换键的显示名（如 "H"） */
    public static String visKeyName() {
        return TOGGLE_MATERIAL_VIS.getTranslatedKeyMessage().getString().toUpperCase();
    }

    /**
     * 必须在模组初始化阶段调用（在 GameOptions 初始化完成之前注册快捷键）。
     * 仅通过引用本类触发上面的静态字段初始化。
     */
    public static void init() {
        // 静态字段注册快捷键（见上方 OPEN_GUI 等）
    }

    public static void onTick(Minecraft mc) {
        while (OPEN_GUI.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new GuiStockMain());
            }
        }
        while (OPEN_AREA_GUI.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new GuiStockArea());
            }
        }
        while (SELECT_POINT.consumeClick()) {
            if (mc.level != null && mc.player != null && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
                SelectionManager.getInstance().addPoint(pos);
                boolean done = SelectionManager.getInstance().hasBoth();
                mc.player.displayClientMessage(
                    Component.literal("[材料备货助手] 已选点 " + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + (done ? "（两点已选好，可在备货区/材料区页面保存区域）" : "（再对准另一个角按 " + com.materialstock.KeyBindings.selectKeyName() + "）"))
                        .withStyle(net.minecraft.ChatFormatting.GOLD), false);
            }
        }
        while (SCAN_AREAS.consumeClick()) {
            StockAreaScanner.scanAll(() -> {});
        }
        while (TOGGLE_MATERIAL_VIS.consumeClick()) {
            StockConfig config = StockConfig.get();
            config.materialAreaVis = !config.materialAreaVis;
            StockConfig.save();
            if (mc.player != null) {
                mc.player.displayClientMessage(
                    Component.literal("[材料备货助手] 材料区高亮已" + (config.materialAreaVis ? "显示" : "隐藏"))
                        .withStyle(net.minecraft.ChatFormatting.GOLD), true);
            }
        }
    }
}
