package com.materialstock.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 计算器配方：目标成品 + 用户输入的目标数量 + 基础材料表（默认为空，可删改）。
 */
public class RecipeEntry {
    public String itemId = "";       // 成品物品 id
    public int targetCount = 1;      // 要合成的数量（用户输入）
    public List<MaterialLine> materials = new ArrayList<>(); // 基础材料（每单位消耗）
    /** 是否参与计算（勾选状态） */
    public boolean selected = true;
    public int recipeIndex = 0;
    /** 是否由"对比差异"批量带入（已够自动删除） */
    public boolean fromCompare = false;

    public RecipeEntry() {}

    public RecipeEntry(String itemId, int targetCount) {
        this.itemId = itemId;
        this.targetCount = targetCount;
    }
}
