package com.materialstock.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.materialstock.StockConfig;
import com.materialstock.data.StockArea;
import com.materialstock.scan.ContainerCache;
import com.materialstock.scan.SelectionManager;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.Color4f;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class WorldRenderer implements IRenderer {
    private static final WorldRenderer INSTANCE = new WorldRenderer();

    public static WorldRenderer getInstance() {
        return INSTANCE;
    }

    @Override
    public void onRenderWorldLast(Matrix4f posMatrix, Matrix4f projMatrix) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        StockConfig config = StockConfig.get();
        String dim = mc.level.dimension().location().toString();

        // 备货区边框
        java.util.List<StockArea> stockSnapshot;
        synchronized (config.stockAreas) {
            stockSnapshot = new java.util.ArrayList<>(config.stockAreas);
        }
        for (StockArea area : stockSnapshot) {
            if (area.isSameDimension(dim)) {
                renderBoxOutline(
                    area.getMin(), area.getMax(),
                    0.01, config.lineWidth,
                    new Color4f(0.2F, 0.9F, 1.0F, 1.0F),
                    mc, false
                );
            }
        }

        // 材料区边框
        if (config.materialAreaVis) {
            java.util.List<StockArea> matSnapshot;
            synchronized (config.materialAreas) {
                matSnapshot = new java.util.ArrayList<>(config.materialAreas);
            }
            for (StockArea area : matSnapshot) {
                if (area.isSameDimension(dim)) {
                    renderBoxOutline(
                        area.getMin(), area.getMax(),
                        0.01, config.lineWidth,
                        new Color4f(1.0F, 0.6F, 0.1F, 1.0F),
                        mc, false
                    );
                }
            }
        }

        // 追踪物品：透视+加粗+半透明填充
        java.util.List<String> trackedSnapshot;
        synchronized (config.trackedItems) {
            trackedSnapshot = new java.util.ArrayList<>(config.trackedItems);
        }
        for (String tid : trackedSnapshot) {
            com.materialstock.craft.CraftCalculator.Variant tv =
                com.materialstock.craft.CraftCalculator.parseVariant(tid);
            if (tv == null) continue;
            net.minecraft.world.item.Item it = tv.item;
            if (it == net.minecraft.world.item.Items.AIR) continue;
            // 位置数据取自 ContainerCache（会持久化），因此重启后无需先扫材料区；
            // 但 ContainerCache 同时含备货区的箱子，必须只保留【材料区】的（isMaterialEntry 过滤）。
            com.materialstock.scan.ContainerCache cc = com.materialstock.scan.ContainerCache.getInstance();
            for (net.minecraft.core.BlockPos pos : cc.findPositionsWithItem(it)) {
                if (!cc.isMaterialEntry(pos)) continue;
                renderBoxOutline(pos, pos, 0.02, 6.0F,
                    new Color4f(1.0F, 0.9F, 0.1F, 1.0F), mc, true);
            }
        }

        // 框选预览
        SelectionManager sel = SelectionManager.getInstance();
        if (sel.hasPos1() && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockPos p1 = sel.getPos1();
            BlockPos p2 = ((BlockHitResult) mc.hitResult).getBlockPos();
            BlockPos min = new BlockPos(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()));
            BlockPos max = new BlockPos(Math.max(p1.getX(), p2.getX()), Math.max(p1.getY(), p2.getY()), Math.max(p1.getZ(), p2.getZ()));
            renderBoxOutline(
                min, max, 0.01, 6.0F,
                new Color4f(0.2F, 0.8F, 0.2F, 1.0F),
                mc, false
            );
            RenderUtils.renderBlockOutline(p1, 0.01F, 5.0F, new Color4f(0.2F, 1.0F, 0.3F, 1.0F), mc);
        }
    }

    private static void renderBoxOutline(BlockPos pos1, BlockPos pos2, double expand, float lineWidth, Color4f color, Minecraft mc, boolean xray) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double dx = cam.x, dy = cam.y, dz = cam.z;
        float minX = (float) (Math.min(pos1.getX(), pos2.getX()) - dx - expand);
        float minY = (float) (Math.min(pos1.getY(), pos2.getY()) - dy - expand);
        float minZ = (float) (Math.min(pos1.getZ(), pos2.getZ()) - dz - expand);
        float maxX = (float) (Math.max(pos1.getX(), pos2.getX()) - dx + expand + 1.0);
        float maxY = (float) (Math.max(pos1.getY(), pos2.getY()) - dy + expand + 1.0);
        float maxZ = (float) (Math.max(pos1.getZ(), pos2.getZ()) - dz + expand + 1.0);

        // 透视：关闭深度测试，穿墙可见
        if (xray) {
            RenderSystem.disableDepthTest();
        }

        RenderSystem.lineWidth(lineWidth);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        line(buf, minX, minY, minZ, maxX, minY, minZ, color);
        line(buf, minX, maxY, minZ, maxX, maxY, minZ, color);
        line(buf, minX, minY, maxZ, maxX, minY, maxZ, color);
        line(buf, minX, maxY, maxZ, maxX, maxY, maxZ, color);
        line(buf, minX, minY, minZ, minX, maxY, minZ, color);
        line(buf, maxX, minY, minZ, maxX, maxY, minZ, color);
        line(buf, minX, minY, maxZ, minX, maxY, maxZ, color);
        line(buf, maxX, minY, maxZ, maxX, maxY, maxZ, color);
        line(buf, minX, minY, minZ, minX, minY, maxZ, color);
        line(buf, maxX, minY, minZ, maxX, minY, maxZ, color);
        line(buf, minX, maxY, minZ, minX, maxY, maxZ, color);
        line(buf, maxX, maxY, minZ, maxX, maxY, maxZ, color);
        MeshData mesh = buf.build();
        BufferUploader.drawWithShader(mesh);
        mesh.close();

        // 透视时加半透明填充面，远处更明显
        if (xray) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            Tesselator tess2 = Tesselator.getInstance();
            BufferBuilder buf2 = tess2.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            float a = 0.2F; // 透明度
            Color4f fc = new Color4f(color.r, color.g, color.b, a);
            // 6个面
            quad(buf2, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, fc);
            quad(buf2, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, fc);
            quad(buf2, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, fc);
            quad(buf2, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, fc);
            quad(buf2, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, fc);
            quad(buf2, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, fc);
            MeshData mesh2 = buf2.build();
            BufferUploader.drawWithShader(mesh2);
            mesh2.close();
            RenderSystem.enableDepthTest();
        }
    }

    private static void quad(BufferBuilder buf, float x1, float y1, float z1, float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4, Color4f c) {
        buf.addVertex(x1, y1, z1).setColor((int)(c.r*255),(int)(c.g*255),(int)(c.b*255),(int)(c.a*255));
        buf.addVertex(x2, y2, z2).setColor((int)(c.r*255),(int)(c.g*255),(int)(c.b*255),(int)(c.a*255));
        buf.addVertex(x3, y3, z3).setColor((int)(c.r*255),(int)(c.g*255),(int)(c.b*255),(int)(c.a*255));
        buf.addVertex(x4, y4, z4).setColor((int)(c.r*255),(int)(c.g*255),(int)(c.b*255),(int)(c.a*255));
    }

    private static void line(BufferBuilder buf, float x1, float y1, float z1, float x2, float y2, float z2, Color4f c) {
        buf.addVertex(x1, y1, z1).setColor((int) (c.r * 255), (int) (c.g * 255), (int) (c.b * 255), (int) (c.a * 255));
        buf.addVertex(x2, y2, z2).setColor((int) (c.r * 255), (int) (c.g * 255), (int) (c.b * 255), (int) (c.a * 255));
    }
}
