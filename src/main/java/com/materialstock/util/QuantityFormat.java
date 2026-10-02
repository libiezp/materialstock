package com.materialstock.util;

import net.minecraft.world.item.Item;

/**
 * 数量格式化：把数量显示为"几盒 + 几组 + 几个"。
 * 1 盒 = 潜影盒容量 = 27 格 × 物品堆叠上限；1 组 = 物品堆叠上限。
 * 支持分数（double）：非整数数量化简为分数显示（如 1/9 个、2/3 个、1组+1/2个）。
 */
public class QuantityFormat {

    public static String format(int count, int stackSize) {
        return format((long) count, stackSize);
    }

    public static String format(int count, Item item) {
        int stack = stackOf(item);
        return format((long) count, stack);
    }

    /** double 数量格式化：整数部分按盒/组/个，分数部分化简显示 */
    public static String format(double count, int stackSize) {
        if (count <= 0) return "0个";
        if (stackSize <= 0) stackSize = 64;
        double wholeD = Math.floor(count + 1e-9);
        long whole = (long) wholeD;
        double frac = count - wholeD;
        if (frac < 1e-9) {
            return format(whole, stackSize);
        }
        long[] f = toFraction(frac, Math.max(64L, stackSize));
        StringBuilder sb = new StringBuilder();
        if (whole > 0) {
            sb.append(format(whole, stackSize)).append('+');
        }
        sb.append(f[0]).append('/').append(f[1]).append("个");
        return sb.toString();
    }

    public static String format(double count, Item item) {
        int stack = stackOf(item);
        return format(count, stack);
    }

    /** 输入框文本：整数显示整数，非整数显示化简分数（如 1/9） */
    public static String formatInput(double count) {
        if (count <= 0) return "0";
        long whole = (long) Math.floor(count + 1e-9);
        double frac = count - whole;
        if (frac < 1e-9) {
            return String.valueOf(whole);
        }
        long[] f = toFraction(frac, 64);
        if (whole > 0) {
            return whole + "+" + f[0] + "/" + f[1];
        }
        return f[0] + "/" + f[1];
    }

    /** 解析输入框文本（支持 "1/9"、"1+1/9"、"0.5"、整数） */
    public static double parseInput(String text) {
        if (text == null) return 0;
        String s = text.trim();
        if (s.isEmpty()) return 0;
        try {
            if (s.contains("+")) {
                String[] parts = s.split("\\+");
                double sum = 0;
                for (String p : parts) {
                    sum += parseInput(p);
                }
                return sum;
            }
            if (s.contains("/")) {
                String[] parts = s.split("/");
                double num = Double.parseDouble(parts[0].trim());
                double den = Double.parseDouble(parts[1].trim());
                return den == 0 ? 0 : num / den;
            }
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return 0;
        }
    }

    /** 把 0<x<1 的小数化简为分数，分母不超过 maxDen（超过则用最接近的近似） */
    private static long[] toFraction(double x, long maxDen) {
        long bestNum = 1, bestDen = 1;
        double bestErr = Double.MAX_VALUE;
        for (long den = 1; den <= maxDen; den++) {
            long num = Math.round(x * den);
            double err = Math.abs((double) num / den - x);
            if (err < 1e-9) {
                long g = gcd(num, den);
                return new long[]{num / g, den / g};
            }
            if (err < bestErr) {
                bestErr = err;
                bestNum = num;
                bestDen = den;
            }
        }
        long g = gcd(bestNum, bestDen);
        return new long[]{bestNum / g, bestDen / g};
    }

    private static long gcd(long a, long b) {
        a = Math.abs(a);
        b = Math.abs(b);
        while (b != 0) {
            long t = a % b;
            a = b;
            b = t;
        }
        return Math.max(1, a);
    }

    private static String format(long count, int stackSize) {
        if (count <= 0) return "0个";
        if (stackSize <= 0) stackSize = 64;
        // 不可堆叠物品（药水/工具/床/船等，stackSize=1）不存在"组"的概念，
        // 否则 5 个会显示成"5组+0个"、1 个显示成"1组+0个"
        if (stackSize == 1) return count + "个";
        long box = 27L * stackSize;
        if (count >= box) {
            long b = count / box;
            long rem = count % box;
            long g = rem / stackSize;
            long s = rem % stackSize;
            return b + "盒+" + g + "组+" + s + "个";
        }
        if (count >= stackSize) {
            return (count / stackSize) + "组+" + (count % stackSize) + "个";
        }
        return count + "个";
    }

    private static int stackOf(Item item) {
        int stack = 64;
        if (item != null) {
            try {
                stack = new net.minecraft.world.item.ItemStack(item).getMaxStackSize();
            } catch (Exception ignored) {
            }
        }
        return stack;
    }
}
