package com.materialstock.data;

import net.minecraft.core.BlockPos;

/**
 * 备货区：一个三维区域（含维度、两点坐标、名称）。
 * 序列化为 JSON 存于配置。
 */
public class StockArea {
    public String name = "";
    public String dimension = "";
    public int x1, y1, z1;
    public int x2, y2, z2;

    public StockArea() {}

    public StockArea(String name, String dimension, BlockPos p1, BlockPos p2) {
        this.name = name;
        this.dimension = dimension;
        this.x1 = Math.min(p1.getX(), p2.getX());
        this.y1 = Math.min(p1.getY(), p2.getY());
        this.z1 = Math.min(p1.getZ(), p2.getZ());
        this.x2 = Math.max(p1.getX(), p2.getX());
        this.y2 = Math.max(p1.getY(), p2.getY());
        this.z2 = Math.max(p1.getZ(), p2.getZ());
    }

    public BlockPos getMin() {
        return new BlockPos(this.x1, this.y1, this.z1);
    }

    public BlockPos getMax() {
        return new BlockPos(this.x2, this.y2, this.z2);
    }

    public boolean contains(BlockPos pos) {
        return pos.getX() >= this.x1 && pos.getX() <= this.x2
            && pos.getY() >= this.y1 && pos.getY() <= this.y2
            && pos.getZ() >= this.z1 && pos.getZ() <= this.z2;
    }

    public boolean isSameDimension(String dim) {
        return this.dimension.equals(dim);
    }

    public int getBlockCount() {
        long x = (long)(this.x2 - this.x1 + 1);
        long y = (long)(this.y2 - this.y1 + 1);
        long z = (long)(this.z2 - this.z1 + 1);
        return (int)Math.min(Integer.MAX_VALUE, x * y * z);
    }

    @Override
    public String toString() {
        return this.name.isEmpty() ? "(" + this.x1 + "," + this.y1 + "," + this.z1 + ")~(" + this.x2 + "," + this.y2 + "," + this.z2 + ")" : this.name;
    }
}
