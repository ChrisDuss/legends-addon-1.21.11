package legends.ultra.cool.addons.hud.widget.settings;

import legends.ultra.cool.addons.hud.HudWidget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CustomListFormScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int LABEL_HEIGHT = 9;
    private static final int LABEL_FIELD_GAP = 4;
    private static final int FIELD_HEIGHT = 20;
    private static final int FIELD_GAP = 10;

    private final Screen parent;
    private final HudWidget.HudSetting setting;
    private final int editIndex;
    private final HudWidget.CustomEntry initialEntry;
    private final List<FieldInput> inputs = new ArrayList<>();
    private String errorMessage = "";

    public CustomListFormScreen(Screen parent, HudWidget.HudSetting setting) {
        this(parent, setting, -1, null);
    }

    public CustomListFormScreen(Screen parent, HudWidget.HudSetting setting,
                                int editIndex, HudWidget.CustomEntry initialEntry) {
        super(Text.literal((editIndex >= 0 ? "Edit " : "Add ") + setting.label()));
        this.parent = parent;
        this.setting = setting;
        this.editIndex = editIndex;
        this.initialEntry = initialEntry;
    }

    @Override
    protected void init() {
        inputs.clear();

        HudWidget.CustomListSpec spec = setting.customList();
        if (spec == null) {
            close();
            return;
        }

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();
        int labelY = top + 38;

        for (HudWidget.CustomField field : spec.fields()) {
            int fieldY = labelY + LABEL_HEIGHT + LABEL_FIELD_GAP;

            TextFieldWidget input = new TextFieldWidget(
                    this.textRenderer,
                    left + 20,
                    fieldY,
                    PANEL_WIDTH - 40,
                    FIELD_HEIGHT,
                    Text.literal(field.label())
            );
            input.setMaxLength(Math.max(1, field.maxLength()));
            if (field.inputType() == HudWidget.CustomField.InputType.POSITIVE_NUMBER) {
                input.setTextPredicate(CustomListFormScreen::isPotentialPositiveNumber);
            }
            if (initialEntry != null) {
                input.setText(initialEntry.get(field.key()));
            }

            addDrawableChild(input);
            inputs.add(new FieldInput(field, input, labelY));
            labelY = fieldY + FIELD_HEIGHT + FIELD_GAP;
        }

        int buttonY = top + panelHeight() - 32;
        String saveLabel = editIndex >= 0 ? "Save" : "Add";
        addDrawableChild(ButtonWidget.builder(Text.literal(saveLabel), button -> save())
                .dimensions(left + 64, buttonY, 76, 20)
                .build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), button -> close())
                .dimensions(left + 160, buttonY, 76, 20)
                .build());

        if (!inputs.isEmpty()) {
            setFocused(inputs.getFirst().input());
            setInitialFocus(inputs.getFirst().input());
            inputs.getFirst().input().setFocused(true);
        }
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER) {
            save();
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        context.fillGradient(0, 0, this.width, this.height, 0xD0100E14, 0xE008080A);

        int left = (this.width - PANEL_WIDTH) / 2;
        int top = panelTop();
        int bottom = top + panelHeight();

        context.fillGradient(left, top, left + PANEL_WIDTH, bottom, 0xF0221C28, 0xF0100D12);
        drawBorder(context, left, top, PANEL_WIDTH, panelHeight(), 0xFFD0D0D0);
        context.drawCenteredTextWithShadow(textRenderer, title, this.width / 2, top + 12, 0xFFFFFFFF);

        for (FieldInput fieldInput : inputs) {
            String suffix = fieldInput.field().required() ? " *" : " (optional)";
            context.drawTextWithShadow(
                    textRenderer,
                    Text.literal(fieldInput.field().label() + suffix),
                    left + 20,
                    fieldInput.labelY(),
                    0xFFC8C8C8
            );
        }

        if (!errorMessage.isBlank()) {
            context.drawCenteredTextWithShadow(
                    textRenderer,
                    Text.literal(errorMessage),
                    this.width / 2,
                    bottom - 48,
                    0xFFFF6666
            );
        }

        super.render(context, mouseX, mouseY, deltaTicks);
    }

    private void save() {
        HudWidget.CustomListSpec spec = setting.customList();
        if (spec == null) {
            close();
            return;
        }

        Map<String, String> values = initialEntry == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(initialEntry.values());
        for (FieldInput fieldInput : inputs) {
            HudWidget.CustomField field = fieldInput.field();
            String value = fieldInput.input().getText().trim();

            if (field.required() && value.isBlank()) {
                errorMessage = field.label() + " is required.";
                fieldInput.input().setFocused(true);
                setFocused(fieldInput.input());
                return;
            }

            if (!value.isBlank() && field.inputType() == HudWidget.CustomField.InputType.POSITIVE_NUMBER) {
                try {
                    if (Double.parseDouble(value) <= 0d) {
                        throw new NumberFormatException();
                    }
                } catch (NumberFormatException ignored) {
                    errorMessage = field.label() + " must be greater than 0.";
                    fieldInput.input().setFocused(true);
                    setFocused(fieldInput.input());
                    return;
                }
            }

            values.put(field.key(), value);
        }

        List<HudWidget.CustomEntry> entries = new ArrayList<>(spec.getEntries().get());
        HudWidget.CustomEntry updatedEntry = new HudWidget.CustomEntry(values);
        if (editIndex >= 0 && editIndex < entries.size()) {
            entries.set(editIndex, updatedEntry);
        } else {
            entries.add(updatedEntry);
        }
        spec.setEntries().accept(List.copyOf(entries));
        close();
    }

    private int panelHeight() {
        HudWidget.CustomListSpec spec = setting.customList();
        int fieldCount = spec == null ? 0 : spec.fields().size();
        int fieldBlockHeight = LABEL_HEIGHT + LABEL_FIELD_GAP + FIELD_HEIGHT + FIELD_GAP;
        return 82 + fieldCount * fieldBlockHeight;
    }

    private int panelTop() {
        return Math.max(8, (this.height - panelHeight()) / 2);
    }

    private static boolean isPotentialPositiveNumber(String value) {
        if (value.isEmpty()) return true;
        return value.matches("\\d*(\\.\\d*)?");
    }

    private static void drawBorder(DrawContext context, int x, int y, int width, int height, int color) {
        context.drawVerticalLine(x, y, y + height, color);
        context.drawVerticalLine(x + width, y, y + height, color);
        context.drawHorizontalLine(x, x + width, y, color);
        context.drawHorizontalLine(x, x + width, y + height, color);
    }

    private record FieldInput(HudWidget.CustomField field, TextFieldWidget input, int labelY) {
    }
}
