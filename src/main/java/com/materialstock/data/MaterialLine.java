package com.materialstock.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 基础材料行：合成某成品时，每单位成品消耗的材料。
 * 由用户维护，可增删改。itemId 为物品注册名（如 minecraft:iron_ingot）。
 * 支持子合成：craftable 表示该材料还有可展开的合成配方；
 * children 为其直接原料（一步步展开，不跨级）；expanded 为界面展开状态。
 */
public class MaterialLine {
    public String itemId = "";
    /** 每单位成品的材料数量，可为分数（如 1 铁锭 = 1/9 铁块 → perUnit = 0.111…） */
    public double perUnit = 1.0;
    public boolean craftable = false;
    public boolean expanded = false;
    public List<MaterialLine> children = new ArrayList<>();

    public MaterialLine() {}

    public MaterialLine(String itemId, double perUnit) {
        this.itemId = itemId;
        this.perUnit = perUnit;
    }
}
