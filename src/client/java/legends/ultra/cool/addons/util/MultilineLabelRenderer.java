package legends.ultra.cool.addons.util;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public final class MultilineLabelRenderer {
    private static final String CFG = "Nameplates";
    private static final Identifier NAMEPLATE_BORDER =
            Identifier.of("legends-addon", "textures/gui/nameplate_border_grayscaled.png");

    private MultilineLabelRenderer() {
    }

    public static boolean isMultiline(Text label) {
        return label != null && label.getString().contains("\n");
    }

    public static boolean render(Text label, Vec3d pos, MatrixStack matrices, OrderedRenderCommandQueue queue, int light) {
        if (pos == null || !isMultiline(label)) {
            return false;
        }

        String[] lines = label.getString().split("\\R");
        if (lines.length == 0) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.getEntityRenderDispatcher().camera == null) {
            return false;
        }

        float yOffset = WidgetConfigManager.getFloat(CFG, "yOffset", 1f);
        float scale = WidgetConfigManager.getFloat(CFG, "scale", 1.0f);
        int argb = WidgetConfigManager.getInt(CFG, "bgColor", 0xFF520016);

        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >>> 16) & 0xFF) / 255f;
        float g = ((argb >>> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        TextRenderer textRenderer = client.textRenderer;
        int lineHeight = textRenderer.fontHeight + 1;

        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, textRenderer.getWidth(line));
        }

        int totalHeight = lines.length * lineHeight;
        int padX = 4;
        int padY = 3;

        float panelWidth = maxWidth + padX * 2f;
        float panelHeight = totalHeight + padY * 2f;
        float panelX = -panelWidth / 2f;
        float panelY = -padY - (panelHeight / 3f);

        matrices.push();
        matrices.translate(pos.x, pos.y + yOffset, pos.z);
        matrices.multiply(client.getEntityRenderDispatcher().camera.getRotation());
        matrices.scale(0.025f * scale, -0.025f * scale, 0.025f * scale);

        RenderLayer layer = RenderLayers.text(NAMEPLATE_BORDER);
        queue.submitCustom(matrices, layer, (entry, vertexConsumer) -> {
            Matrix4f matrix = entry.getPositionMatrix();
            drawNineSlice(
                    vertexConsumer,
                    matrix,
                    panelX,
                    panelY,
                    panelWidth,
                    panelHeight,
                    9,
                    9,
                    4,
                    r,
                    g,
                    b,
                    a,
                    light
            );
        });

        float startY = (-(lines.length - 1) * lineHeight * 0.5f);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float x = -textRenderer.getWidth(line) / 2.0f;
            float y = startY + i * lineHeight;
            OrderedText ordered = Text.literal(line).asOrderedText();

            queue.submitText(
                    matrices,
                    x,
                    y,
                    ordered,
                    false,
                    TextRenderer.TextLayerType.NORMAL,
                    light,
                    0xFFFFFFFF,
                    0x00FFFFFF,
                    0x00FFFFFF
            );
        }

        matrices.pop();
        return true;
    }

    private static void vertex(
            VertexConsumer vertexConsumer,
            Matrix4f matrix,
            float x, float y, float z,
            float u, float v,
            float r, float g, float b, float a,
            int light
    ) {
        vertexConsumer.vertex(matrix, x, y, z);
        vertexConsumer.texture(u, v);
        vertexConsumer.color(r, g, b, a);
        vertexConsumer.overlay(OverlayTexture.DEFAULT_UV);
        vertexConsumer.light(light);
        vertexConsumer.normal(0f, 1f, 0f);
    }

    private static void quad(
            VertexConsumer vertexConsumer,
            Matrix4f matrix,
            float x1, float y1, float x2, float y2,
            float u1, float v1, float u2, float v2,
            float r, float g, float b, float a,
            int light
    ) {
        float z = -0.01f;
        vertex(vertexConsumer, matrix, x1, y2, z, u1, v2, r, g, b, a, light);
        vertex(vertexConsumer, matrix, x2, y2, z, u2, v2, r, g, b, a, light);
        vertex(vertexConsumer, matrix, x2, y1, z, u2, v1, r, g, b, a, light);
        vertex(vertexConsumer, matrix, x1, y1, z, u1, v1, r, g, b, a, light);
    }

    private static void drawNineSlice(
            VertexConsumer vertexConsumer,
            Matrix4f matrix,
            float x, float y,
            float width, float height,
            int texW, int texH,
            int corner,
            float r, float g, float b, float a,
            int light
    ) {
        float cornerU = corner / (float) texW;
        float cornerV = corner / (float) texH;

        float x0 = x;
        float y0 = y;
        float x1 = x + corner;
        float y1 = y + corner;
        float x2 = x + width - corner;
        float y2 = y + height - corner;
        float x3 = x + width;
        float y3 = y + height;

        if (width < corner * 2f) x2 = x1;
        if (height < corner * 2f) y2 = y1;

        quad(vertexConsumer, matrix, x0, y0, x1, y1, 0f, 0f, cornerU, cornerV, r, g, b, a, light);
        quad(vertexConsumer, matrix, x2, y0, x3, y1, 1f - cornerU, 0f, 1f, cornerV, r, g, b, a, light);
        quad(vertexConsumer, matrix, x0, y2, x1, y3, 0f, 1f - cornerV, cornerU, 1f, r, g, b, a, light);
        quad(vertexConsumer, matrix, x2, y2, x3, y3, 1f - cornerU, 1f - cornerV, 1f, 1f, r, g, b, a, light);

        quad(vertexConsumer, matrix, x1, y0, x2, y1, cornerU, 0f, 1f - cornerU, cornerV, r, g, b, a, light);
        quad(vertexConsumer, matrix, x1, y2, x2, y3, cornerU, 1f - cornerV, 1f - cornerU, 1f, r, g, b, a, light);
        quad(vertexConsumer, matrix, x0, y1, x1, y2, 0f, cornerV, cornerU, 1f - cornerV, r, g, b, a, light);
        quad(vertexConsumer, matrix, x2, y1, x3, y2, 1f - cornerU, cornerV, 1f, 1f - cornerV, r, g, b, a, light);

        quad(vertexConsumer, matrix, x1, y1, x2, y2, cornerU, cornerV, 1f - cornerU, 1f - cornerV, r, g, b, a, light);
    }
}
