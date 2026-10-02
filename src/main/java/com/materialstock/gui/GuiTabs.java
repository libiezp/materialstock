package com.materialstock.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;

/**
 * 顶部四个页签的集中定义：文案、鼠标悬停提示、目标界面。
 *
 * 四个界面（材料清单 / 合成计算器 / 备货区 / 材料区）原本各自重复一份 addTab 调用与布局算法，
 * 这里收拢成一处，悬停提示才不会出现"改了三个界面漏了第四个"的情况。
 *
 * 悬停提示用 malilib 自带的 ButtonBase.setHoverStrings + postRenderHovered 渲染，
 * 但 malilib 不会自动帮我们调 postRenderHovered（它的 widget 循环里带不上鼠标坐标），
 * 所以由各界面在 drawContents 里调 renderTooltips(mouseX, mouseY, ctx) 手动触发。
 */
public final class GuiTabs {

    private GuiTabs() {
    }

    /** 页签按钮高亮颜色（青色），仅在未选中页签上显示 */
    private static final int TOOLTIP_COLOR = 0xFFFFFFFF;

    /**
     * 给页签按钮挂上悬停提示。
     * 提示内容 = 该界面做什么 + 点下去会跳到哪个界面。
     */
    public static void applyHover(ButtonGeneric button, String title) {
        if (button == null) return;
        for (int i = 0; i < TITLES.length; i++) {
            if (TITLES[i].equals(title)) {
                button.setHoverStrings(TOOLTIPS[i]);
                return;
            }
        }
    }

    /**
     * 渲染页签悬停提示。必须在所有其它控件绘制之后调用，
     * 否则提示框会被后绘制的控件盖住。
     */
    public static void renderTooltip(ButtonGeneric button, int mouseX, int mouseY,
                                     net.minecraft.client.gui.GuiGraphics ctx) {
        if (button == null) return;
        try {
            button.postRenderHovered(mouseX, mouseY, false, ctx);
        } catch (Exception ignored) {
            // 悬停提示属于纯提示性功能，任何渲染异常都不应影响界面本身
        }
    }

    public static final String[] TITLES = {
        "材料清单", "合成计算器", "备货区", "材料区"
    };

    private static final String[] TOOLTIPS = {
        "材料清单\n打开投影文件，按原版标签分类列出需要的材料\n并对比备货区已有数量（点下去切到「材料清单」页）",
        "合成计算器\n输入成品与数量，自动推导基础材料与合成配方\n并显示备货区已有 / 还缺多少（点下去切到「合成计算器」页）",
        "备货区\n框选备货区并扫描，统计已备好的材料数量\n作为「已有」参与材料清单与计算器的对比（点下去切到「备货区」页）",
        "材料区\n框选材料区并扫描，作为独立的一份库存统计\n支持 [展开] 看明细、[追踪] 在世界上高亮箱子（点下去切到「材料区」页）",
    };

    /** 供界面类继承/调用：统一的页签行绘制宽度（保持四处布局一致） */
    public static int tabWidth(int screenWidth) {
        return Math.max(80, Math.min(160, (screenWidth - 40) / 4 - 8));
    }

    /** 第一个页签的 X 坐标（居中排布） */
    public static int firstTabX(int screenWidth, int tabW) {
        int mid = screenWidth / 2;
        return mid - 2 * tabW - 12;
    }

    /** 页签间距 */
    public static int tabStep(int tabW) {
        return tabW + 8;
    }

    /** 打开目标界面（供 addTab 复用） */
    public static void open(GuiBase target) {
        GuiBase.openGui(target);
    }
}
