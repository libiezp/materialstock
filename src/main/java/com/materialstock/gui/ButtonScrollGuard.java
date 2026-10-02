package com.materialstock.gui;

import java.util.ArrayList;
import java.util.List;

/**
 * 按钮滚轮拦截：malilib 的按钮基类会把滚轮当作点击触发按钮动作，
 * 需要在滚轮事件传给按钮前先拦截。各页面把按钮区域登记到这里，
 * onMouseScrolled 开头调用 {@link #isOver} 判断鼠标是否悬停在按钮上。
 */
public class ButtonScrollGuard {
    private final List<int[]> bounds = new ArrayList<>();

    public void clear() {
        this.bounds.clear();
    }

    public void add(int x, int y, int w, int h) {
        this.bounds.add(new int[]{x, y, w, h});
    }

    /** 鼠标是否悬停在任一按钮区域上 */
    public boolean isOver(int mouseX, int mouseY) {
        for (int[] b : this.bounds) {
            if (mouseX >= b[0] && mouseX <= b[0] + b[2]
                    && mouseY >= b[1] && mouseY <= b[1] + b[3]) {
                return true;
            }
        }
        return false;
    }
}
