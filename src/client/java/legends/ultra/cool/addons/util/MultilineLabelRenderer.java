package legends.ultra.cool.addons.util;

import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.otherTypes.NameplateWidget;
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

    public static boolean render(
            Text nameText,
            Text statsText,
            Vec3d pos,
            double health,
            double maxHealth,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            int light
    ) {
        if (pos == null) {
            return false;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null || client.getEntityRenderDispatcher().camera == null) {
            return false;
        }

        boolean showName = NameplateWidget.shouldShowNameText() && hasVisibleText(nameText);
        boolean showBar = NameplateWidget.shouldShowHealthBar();
        boolean showStats = NameplateWidget.shouldShowStatsText() && hasVisibleText(statsText);
        if (!showName && !showBar && !showStats) {
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
        float rowGap = 0f;
        float barHeight = 10f;
        float barOuterPadX = 3f;
        float barOuterPadY = 2f;
        float barInnerPadX = 1f;
        float barInnerPadY = 1f;
        float minBarWidth = 60f;
        float progressTextScale = 0.85f;
        int padX = 4;
        int padY = 3;

        OrderedText orderedName = showName ? nameText.asOrderedText() : null;
        OrderedText orderedStats = showStats ? statsText.asOrderedText() : null;
        OrderedText progressText = null;

        int nameWidth = orderedName != null ? textRenderer.getWidth(orderedName) : 0;
        int statsWidth = orderedStats != null ? textRenderer.getWidth(orderedStats) : 0;

        float progress = maxHealth <= 0.0 ? 0.0f : (float) (health / maxHealth);
        progress = Math.max(0.0f, Math.min(1.0f, progress));
        if (showBar) {
            progressText = Text.literal(Math.round(progress * 100f) + "%").asOrderedText();
        }

        float contentWidth = Math.max(nameWidth, statsWidth);
        if (showBar) {
            contentWidth = Math.max(contentWidth, minBarWidth + (barOuterPadX * 2f));
        }

        int rowCount = 0;
        float contentHeight = 0f;
        if (showName) {
            contentHeight += lineHeight;
            rowCount++;
        }
        if (showBar) {
            contentHeight += barHeight + (barOuterPadY * 2f);
            rowCount++;
        }
        if (showStats) {
            contentHeight += lineHeight;
            rowCount++;
        }
        if (rowCount > 1) {
            contentHeight += rowGap * (rowCount - 1);
        }

        float panelWidth = contentWidth + (padX * 2f);
        float panelHeight = contentHeight + (padY * 2f);
        float panelX = -panelWidth / 2f;
        float panelY = -padY - (panelHeight / 3f);

        float currentY = panelY + padY;
        float nameX = 0f;
        float nameY = 0f;
        float barX = panelX + padX + barOuterPadX;
        float barY = 0f;
        float barWidth = contentWidth - (barOuterPadX * 2f);
        float statsX = 0f;
        float statsY = 0f;

        if (showName) {
            nameX = panelX + padX + ((contentWidth - nameWidth) / 2f);
            nameY = currentY + 2f;
            currentY += lineHeight;
            if (showBar || showStats) {
                currentY += rowGap;
            }
        }

        if (showBar) {
            barY = currentY + barOuterPadY;
            currentY += barHeight + (barOuterPadY * 2f);
            if (showStats) {
                currentY += rowGap;
            }
        }

        if (showStats) {
            statsX = panelX + padX + ((contentWidth - statsWidth) / 2f);
            statsY = currentY;
        }

        matrices.push();
        matrices.translate(pos.x, pos.y + yOffset, pos.z);
        matrices.multiply(client.getEntityRenderDispatcher().camera.getRotation());
        matrices.scale(0.025f * scale, -0.025f * scale, 0.025f * scale);

        RenderLayer borderLayer = RenderLayers.text(NAMEPLATE_BORDER);
        queue.submitCustom(matrices, borderLayer, (entry, vertexConsumer) -> {
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

        if (showBar) {
            float fillWidth = Math.max(0f, (barWidth - (barInnerPadX * 2f)) * progress);
            final float finalBarX = barX;
            final float finalBarY = barY;
            final float finalBarWidth = barWidth;
            final float finalFillWidth = fillWidth;
            RenderLayer barLayer = RenderLayers.textBackground();
            queue.submitCustom(matrices, barLayer, (entry, vertexConsumer) -> {
                Matrix4f matrix = entry.getPositionMatrix();
                drawBar(
                        vertexConsumer,
                        matrix,
                        finalBarX,
                        finalBarY,
                        -0.02f,
                        finalBarWidth,
                        barHeight,
                        0xFF000000,
                        light
                );
                if (finalFillWidth > 0f) {
                    drawBar(
                            vertexConsumer,
                            matrix,
                            finalBarX + barInnerPadX,
                            finalBarY + barInnerPadY,
                            0,
                            finalFillWidth,
                            barHeight - (barInnerPadY * 2f),
                            0xFFA00A0A,
                            light
                    );
                }
            });
        }

        if (showName) {
            queue.submitText(
                    matrices,
                    nameX,
                    nameY,
                    orderedName,
                    false,
                    TextRenderer.TextLayerType.NORMAL,
                    light,
                    0xFFFFFFFF,
                    0x00FFFFFF,
                    0x00FFFFFF
            );
        }

        if (showBar && progressText != null) {
            float progressTextX = barX + (barWidth / 2f);
            float progressTextY = barY + (barHeight / 2f) + progressTextScale;
            matrices.push();
            matrices.translate(progressTextX, progressTextY, 0f);
            matrices.scale(progressTextScale, progressTextScale, 1f);
            queue.submitText(
                    matrices,
                    -(textRenderer.getWidth(progressText) / 2f),
                    -(textRenderer.fontHeight / 2f),
                    progressText,
                    false,
                    TextRenderer.TextLayerType.POLYGON_OFFSET,
                    light,
                    0xFFFFFFFF,
                    0x00FFFFFF,
                    0x00FFFFFF
            );
            matrices.pop();
        }

        if (showStats) {
            queue.submitText(
                    matrices,
                    statsX,
                    statsY,
                    orderedStats,
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

    private static boolean hasVisibleText(Text text) {
        return text != null && !text.getString().isBlank();
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
        float z = -0.04f;
        vertex(vertexConsumer, matrix, x1, y2, z, u1, v2, r, g, b, a, light);
        vertex(vertexConsumer, matrix, x2, y2, z, u2, v2, r, g, b, a, light);
        vertex(vertexConsumer, matrix, x2, y1, z, u2, v1, r, g, b, a, light);
        vertex(vertexConsumer, matrix, x1, y1, z, u1, v1, r, g, b, a, light);
    }

    private static void vertexSolid(
            VertexConsumer vertexConsumer,
            Matrix4f matrix,
            float x, float y, float z,
            int argb,
            int light
    ) {
        vertexConsumer.vertex(matrix, x, y, z);
        vertexConsumer.color(argb);
        vertexConsumer.overlay(OverlayTexture.DEFAULT_UV);
        vertexConsumer.light(light);
        vertexConsumer.normal(0f, 1f, 0f);
    }

    private static void quadSolid(
            VertexConsumer vertexConsumer,
            Matrix4f matrix,
            float x1, float y1, float x2, float y2, float z,
            int argb,
            int light
    ) {
        vertexSolid(vertexConsumer, matrix, x1, y2, z, argb, light);
        vertexSolid(vertexConsumer, matrix, x2, y2, z, argb, light);
        vertexSolid(vertexConsumer, matrix, x2, y1, z, argb, light);
        vertexSolid(vertexConsumer, matrix, x1, y1, z, argb, light);
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

    private static void drawBar(
            VertexConsumer vertexConsumer,
            Matrix4f matrix,
            float x, float y, float z,
            float width, float height,
            int argb,
            int light
    ) {
        if (width <= 0f || height <= 0f) {
            return;
        }

        quadSolid(vertexConsumer, matrix, x, y, x + width, y + height, z, argb, light);
    }
}
