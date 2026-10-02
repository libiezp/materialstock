package com.materialstock.scan;

import net.minecraft.core.BlockPos;

/**
 * 备货区框选状态：记录玩家用快捷键选中的两个点。
 */
public class SelectionManager {
    private static final SelectionManager INSTANCE = new SelectionManager();
    private BlockPos pos1;
    private BlockPos pos2;

    public static SelectionManager getInstance() {
        return INSTANCE;
    }

    public boolean hasPos1() {
        return this.pos1 != null;
    }

    public boolean hasBoth() {
        return this.pos1 != null && this.pos2 != null;
    }

    public BlockPos getPos1() {
        return this.pos1;
    }

    public BlockPos getPos2() {
        return this.pos2;
    }

    /** 第一次调用设置点1，第二次调用设置点2 */
    public void addPoint(BlockPos pos) {
        if (this.pos1 == null) {
            this.pos1 = pos.immutable();
            this.pos2 = null;
        } else if (this.pos2 == null) {
            this.pos2 = pos.immutable();
        } else {
            this.pos1 = pos.immutable();
            this.pos2 = null;
        }
    }

    public void clear() {
        this.pos1 = null;
        this.pos2 = null;
    }

    /** 直接设置两个点（手动编辑坐标用） */
    public void setBoth(BlockPos p1, BlockPos p2) {
        this.pos1 = p1.immutable();
        this.pos2 = p2.immutable();
    }
}
