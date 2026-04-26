package legends.ultra.cool.addons.util;

import legends.ultra.cool.addons.hud.widget.otherTypes.NpcChatWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class VanillaStackedLabelRenderer {
    private VanillaStackedLabelRenderer() {
    }

    public static float getLineStep() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return 9.0F * 1.15F * 0.025F;
        }

        return client.textRenderer.fontHeight * 1.15F * 0.025F;
    }

    public static void render(
            Text label,
            MatrixStack matrices,
            OrderedRenderCommandQueue queue,
            Vec3d pos,
            int verticalOffset,
            boolean visible,
            int light,
            double squaredDistanceToCamera,
            CameraRenderState cameraRenderState
    ) {
        if (label == null || pos == null) {
            return;
        }

        List<Text> lines = wrapStyledLines(label);
        if (lines.isEmpty()) {
            return;
        }

        for (int i = lines.size() - 1; i >= 0; i--) {
            queue.submitLabel(
                    matrices,
                    pos,
                    verticalOffset,
                    lines.get(i),
                    visible,
                    light,
                    squaredDistanceToCamera,
                    cameraRenderState
            );

            if (i > 0) {
                matrices.translate(0.0F, getLineStep(), 0.0F);
            }
        }
    }

    private static List<Text> wrapStyledLines(Text label) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.textRenderer == null) {
            return List.of(label);
        }

        List<StringVisitable> wrapped = client.textRenderer.wrapLinesWithoutLanguage(label, NpcChatWidget.getDialogueWrapWidth());
        if (wrapped.isEmpty()) {
            return List.of(label);
        }

        List<Text> lines = new ArrayList<>(wrapped.size());
        for (StringVisitable visitable : wrapped) {
            lines.add(toStyledText(visitable));
        }

        return lines;
    }

    private static Text toStyledText(StringVisitable visitable) {
        MutableText result = Text.empty();
        visitable.visit((style, segment) -> {
            if (!segment.isEmpty()) {
                result.append(Text.literal(segment).setStyle(style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }
}
