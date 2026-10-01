package de.vali.itemfinder.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import de.vali.itemfinder.storage.ChestMemory;
import net.fabricmc.fabric.api.client.rendering.v1.FabricRenderState;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** An emissive, through-wall outline of the remembered chest, including both halves. */
public final class ChestHighlightRenderer {
    private static final RenderStateDataKey<HighlightFrame> HIGHLIGHTS =
            RenderStateDataKey.create(() -> "Item Finder chest highlights");
    private static final RenderPipeline THROUGH_WALLS = createPipeline();
    private static StagedVertexBuffer vertexBuffer;

    private ChestHighlightRenderer() { }

    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(ChestHighlightRenderer::extract);
        LevelRenderEvents.END_MAIN.register(ChestHighlightRenderer::draw);
    }

    private static RenderPipeline createPipeline() {
        // Copy the public vanilla quad pipeline description. Minecraft 26.3 makes
        // its snippets private, while the pipeline cache compiles new descriptions on demand.
        RenderPipeline vanilla = RenderPipelines.DEBUG_QUADS;
        RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
                vanilla.getShaders(), Optional.of(vanilla.getShaderDefines()),
                Optional.of(vanilla.getBindGroupLayouts()),
                vanilla.getColorTargetStates().toArray(ColorTargetState[]::new),
                vanilla.getColorTargetStates().size(), Optional.empty(),
                Optional.of(vanilla.getPolygonMode()), Optional.of(false),
                vanilla.getVertexFormatBindings().toArray(VertexFormat[]::new),
                Optional.of(PrimitiveTopology.QUADS), vanilla.pushConstantSize());
        return RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("itemfinder", "pipeline/chest_highlight"))
                .withDepthStencilState(Optional.empty())
                .build();
    }

    private static void extract(LevelExtractionContext context) {
        ItemFinderClient controller = ItemFinderClient.getInstance();
        List<ChestBox> boxes = new ArrayList<>();
        String dimension = context.level().dimension().identifier().toString();
        if (controller != null) {
            for (ChestMemory.ChestRecord chest : controller.matchingChests()) {
                if (!dimension.equals(chest.dimension())) continue;

                int minX = Integer.MAX_VALUE;
                int minY = Integer.MAX_VALUE;
                int minZ = Integer.MAX_VALUE;
                int maxX = Integer.MIN_VALUE;
                int maxZ = Integer.MIN_VALUE;
                for (ChestMemory.Position position : chest.positions()) {
                    BlockPos pos = new BlockPos(position.x(), position.y(), position.z());
                    // A loaded, removed chest should immediately lose its outline.
                    // Unloaded positions retain the last known location, just like the inventory snapshot.
                    if (context.level().hasChunkAt(pos)
                            && !(context.level().getBlockState(pos).getBlock() instanceof ChestBlock)) continue;
                    minX = Math.min(minX, position.x());
                    minY = Math.min(minY, position.y());
                    minZ = Math.min(minZ, position.z());
                    maxX = Math.max(maxX, position.x());
                    maxZ = Math.max(maxZ, position.z());
                }
                if (minX != Integer.MAX_VALUE) {
                    boxes.add(new ChestBox(minX + 0.0625, minY + 0.0625, minZ + 0.0625,
                            maxX + 0.9375, minY + 0.875, maxZ + 0.9375));
                }
            }
        }
        float pulse = 0.27f + 0.08f * (float) Math.sin(
                (context.levelState().gameTime + context.levelState().worldPartialTicks) * 0.13);
        ((FabricRenderState) context.levelState()).setData(HIGHLIGHTS,
                new HighlightFrame(List.copyOf(boxes), pulse));
    }

    private static void draw(LevelRenderContext context) {
        HighlightFrame frame = ((FabricRenderState) context.levelState()).getData(HIGHLIGHTS);
        if (frame == null || frame.boxes().isEmpty()) return;

        if (vertexBuffer == null) {
            vertexBuffer = new StagedVertexBuffer(() -> "Item Finder chest outlines", RenderType.SMALL_BUFFER_SIZE);
        }
        StagedVertexBuffer.Draw draw = vertexBuffer.appendDraw(
                THROUGH_WALLS.getVertexFormatBinding(0), PrimitiveTopology.QUADS);
        VertexConsumer vertices = vertexBuffer.getVertexBuilder(draw);
        PoseStack poses = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;

        for (ChestBox box : frame.boxes()) {
            poses.pushPose();
            // Translate before converting to float to retain precision far from the world origin.
            poses.translate(box.minX() - camera.x, box.minY() - camera.y, box.minZ() - camera.z);
            Matrix4fc matrix = poses.last().pose();
            float width = (float) (box.maxX() - box.minX());
            float height = (float) (box.maxY() - box.minY());
            float depth = (float) (box.maxZ() - box.minZ());
            double distance = Math.sqrt(square(box.minX() - camera.x)
                    + square(box.minY() - camera.y) + square(box.minZ() - camera.z));
            float lineScale = (float) Math.min(3.0, 1.0 + distance / 100.0);

            cuboid(matrix, vertices, 0, 0, 0, width, height, depth, 1f, 0.85f, 0.25f, 0.035f);
            outline(matrix, vertices, width, height, depth, 0.035f * lineScale,
                    1f, 0.83f, 0.22f, frame.haloAlpha());
            outline(matrix, vertices, width, height, depth, 0.012f * lineScale,
                    1f, 0.98f, 0.82f, 0.96f);
            poses.popPose();
        }

        try {
            vertexBuffer.upload();
            StagedVertexBuffer.ExecuteInfo info = vertexBuffer.getExecuteInfo(draw);
            if (info != null) submit(context, info);
        } finally {
            vertexBuffer.endFrame();
        }
    }

    private static void submit(LevelRenderContext context, StagedVertexBuffer.ExecuteInfo info) {
        RenderTarget target = context.gameRenderer().mainRenderTarget();
        GpuTextureView color = target.getColorTextureView();
        if (color == null) return;
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms()
                .writeTransform(RenderSystem.getModelViewMatrixCopy());
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        // No depth attachment and no depth test: outlines remain visible behind walls.
        try (RenderPass pass = encoder.createRenderPass(() -> "Item Finder highlights", color, Optional.empty())) {
            pass.setPipeline(RenderSystem.getCompiledPipeline(THROUGH_WALLS));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setVertexBuffer(0, info.vertexBuffer().slice());
            pass.setIndexBuffer(info.indexBuffer(), info.indexType());
            pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
        // The device returns Minecraft's shared frame encoder. Closing the render
        // pass queues this draw; Minecraft submits the complete frame itself.
    }

    private static double square(double value) { return value * value; }

    private static void outline(Matrix4fc matrix, VertexConsumer vertices, float x, float y, float z,
                                float thickness, float r, float g, float b, float a) {
        float half = thickness * 0.5f;
        for (float cornerY : new float[]{0, y}) {
            for (float cornerZ : new float[]{0, z}) {
                cuboid(matrix, vertices, -half, cornerY - half, cornerZ - half,
                        x + half, cornerY + half, cornerZ + half, r, g, b, a);
            }
        }
        for (float cornerX : new float[]{0, x}) {
            for (float cornerZ : new float[]{0, z}) {
                cuboid(matrix, vertices, cornerX - half, -half, cornerZ - half,
                        cornerX + half, y + half, cornerZ + half, r, g, b, a);
            }
            for (float cornerY : new float[]{0, y}) {
                cuboid(matrix, vertices, cornerX - half, cornerY - half, -half,
                        cornerX + half, cornerY + half, z + half, r, g, b, a);
            }
        }
    }

    private static void cuboid(Matrix4fc m, VertexConsumer v, float x0, float y0, float z0,
                               float x1, float y1, float z1, float r, float g, float b, float a) {
        quad(m, v, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, a);
        quad(m, v, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, a);
        quad(m, v, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, a);
        quad(m, v, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        quad(m, v, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, a);
        quad(m, v, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, a);
    }

    private static void quad(Matrix4fc m, VertexConsumer v,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             float r, float g, float b, float a) {
        v.addVertex(m, x0, y0, z0).setColor(r, g, b, a);
        v.addVertex(m, x1, y1, z1).setColor(r, g, b, a);
        v.addVertex(m, x2, y2, z2).setColor(r, g, b, a);
        v.addVertex(m, x3, y3, z3).setColor(r, g, b, a);
    }

    public static void close() {
        if (vertexBuffer != null) {
            vertexBuffer.close();
            vertexBuffer = null;
        }
    }

    private record ChestBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) { }
    private record HighlightFrame(List<ChestBox> boxes, float haloAlpha) { }
}
