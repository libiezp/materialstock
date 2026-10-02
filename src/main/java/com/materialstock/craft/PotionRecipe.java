package com.materialstock.craft;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * 把「一条酿造配方」适配成 Recipe，好让合成计算器像处理普通配方一样展示与切换。
 *
 * 注意：这是<b>纯展示用</b>的适配器，永远不会被注册进 RecipeManager，
 * 因此 getSerializer()/getType() 返回 null，matches()/assemble() 无实际意义。
 * 计算器只调用 getIngredients() 与 getResultItem()。
 */
final class PotionRecipe implements Recipe<RecipeInput> {

    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;

    PotionRecipe(Ingredient base, Ingredient additive, ItemStack result) {
        NonNullList<Ingredient> list = NonNullList.create();
        list.add(base);
        list.add(additive);
        this.ingredients = list;
        this.result = result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return this.ingredients;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider provider) {
        return this.result;
    }

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, HolderLookup.Provider provider) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /** 从不注册，无需序列化器 */
    @Override
    public RecipeSerializer<?> getSerializer() {
        return null;
    }

    /** 从不注册，无需配方类型 */
    @Override
    public RecipeType<?> getType() {
        return null;
    }
}
