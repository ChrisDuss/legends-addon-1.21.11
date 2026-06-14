package legends.ultra.cool.addons.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.WidgetConfigManager;
import legends.ultra.cool.addons.hud.widget.otherTypes.VaultBrowserWidget;
import legends.ultra.cool.addons.mixin.client.HandledScreenAccessor;
import legends.ultra.cool.addons.util.AddonServerGate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.EnchantmentEffectComponentTypes;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WardrobeManager {
    private static final String CONFIG_ID = "__wardrobe__";
    private static final String VAULT_KEY = "vault";
    private static final String ACTIVE_SET_KEY = "activeSet";
    private static final String SETS_FILE = "legendsaddon_wardrobe_sets.json";
    private static final String STORAGE_MENU_TITLE = "storage menu";
    private static final String CUSTOM_ITEM_ID_KEY = "custom_item_id";
    private static final Pattern VAULT_NUMBER_PATTERN =
            Pattern.compile("(?:vault\\s*#?\\s*|/pv\\s+)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int SLOTS_PER_SET = 4;
    private static final int OUTER_MARGIN = 20;
    private static final int HEADER_HEIGHT = 52;
    private static final int SET_PANEL_WIDTH = 64;
    private static final int SET_PANEL_HEIGHT = 112;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_RENDER_SIZE = 16;
    private static final int SET_SELECTOR_SIZE = 14;
    private static final int INVENTORY_PANEL_WIDTH = (9 * SLOT_SIZE) + 16;
    private static final int INVENTORY_PANEL_HEIGHT = (4 * SLOT_SIZE) + 30;
    private static final int TOP_BUTTON_WIDTH = 82;
    private static final int TOP_BUTTON_HEIGHT = 20;
    private static final int ACTION_TIMEOUT_TICKS = 600;
    private static final int VAULT_OPEN_TIMEOUT_TICKS = 160;
    private static final int ACTION_RETRY_TICKS = 10;
    private static final int MAX_VAULT_OPEN_ATTEMPTS = 4;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SETS_FILE_TYPE = new TypeToken<PersistedWardrobeFile>() {}.getType();

    private static final Set<Screen> INSTALLED_SCREENS =
            Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<Screen, WardrobeUiState> UI_STATES = new WeakHashMap<>();

    private static SelectionState selection;
    private static WardrobeOperation operation;
    private static String status = "";
    private static boolean savedSetsLoaded;
    private static int loadedSetsProfile = -1;
    private static int loadedSetsVault = -1;
    private static List<WardrobeSet> savedSets = List.of();

    private WardrobeManager() {
    }

    public static void init() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenMouseEvents.allowMouseClick(screen).register((scr, click) -> {
                if (operation != null) {
                    return false;
                }
                return handleMouseClick(client, scr, click);
            });

            ScreenEvents.afterRender(screen).register((scr, context, mouseX, mouseY, delta) -> {
                if (scr instanceof HandledScreen<?> handledScreen && isConfiguredWardrobe(handledScreen)) {
                    renderWardrobe(handledScreen, context, mouseX, mouseY);
                }
                if (operation != null && !operation.status().isBlank()) {
                    context.drawCenteredTextWithShadow(
                            client.textRenderer,
                            Text.literal(operation.status()),
                            scr.width / 2,
                            8,
                            0xFFFFE080
                    );
                }
            });

            ScreenEvents.remove(screen).register(scr -> {
                UI_STATES.remove(scr);
                INSTALLED_SCREENS.remove(scr);
                if (selection != null && selection.screen == scr) {
                    selection = null;
                }
            });

            if (screen instanceof HandledScreen<?> handledScreen && isVaultScreen(handledScreen)) {
                addConfigurationButton(client, screen, handledScreen);
                if (isConfiguredWardrobe(handledScreen)) {
                    installWardrobeControls(screen);
                }
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(WardrobeManager::tick);
    }

    private static void addConfigurationButton(
            MinecraftClient client,
            Screen screen,
            HandledScreen<?> handledScreen
    ) {
        int vaultNumber = extractVaultNumber(handledScreen.getTitle().getString());
        int profile = VaultStorageManager.getProfileForVaultNumber(vaultNumber);
        if (vaultNumber < 1 || vaultNumber == getWardrobeVault(profile)) {
            return;
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        ButtonWidget useButton = ButtonWidget.builder(Text.literal("Use Wardrobe"), button -> {
                    if (!isContainerEmpty(handledScreen)) {
                        sendStatus(client, "The PV must be completely empty before it can become a wardrobe.");
                        return;
                    }
                    if (getContainerSlotCount(handledScreen) < SLOTS_PER_SET) {
                        sendStatus(client, "This container is too small for a wardrobe.");
                        return;
                    }

                    setWardrobeVault(profile, vaultNumber);
                    setActiveSet(profile, -1);
                    savedSetsLoaded = true;
                    loadedSetsProfile = profile;
                    loadedSetsVault = vaultNumber;
                    savedSets = createEmptySetList(getContainerSlotCount(handledScreen) / SLOTS_PER_SET);
                    saveSavedSets(client);
                    button.visible = false;
                    installWardrobeControls(screen);
                    sendStatus(client, "PV " + vaultNumber + " is now the wardrobe.");
                })
                .dimensions(
                        accessor.legends$getX() + accessor.legends$getBackgroundWidth() + 6,
                        accessor.legends$getY() + 30,
                        88,
                        20
                )
                .build();
        Screens.getButtons(screen).add(useButton);
    }

    private static void installWardrobeControls(Screen screen) {
        if (!INSTALLED_SCREENS.add(screen)) {
            return;
        }
        UI_STATES.put(screen, new WardrobeUiState());
    }

    private static void handleCreateButton(
            MinecraftClient client,
            Screen screen,
            HandledScreen<?> handledScreen
    ) {
        if (operation != null) {
            return;
        }

        if (selection == null || selection.screen != screen) {
            WardrobeView view = inspectWardrobe(handledScreen);
            if (!view.valid()) {
                sendStatus(client, view.warning());
                return;
            }

            int targetSet = findEmptySet(view);
            if (targetSet < 0) {
                sendStatus(client, "No empty wardrobe set is available.");
                return;
            }

            selection = new SelectionState(screen, targetSet);
            status = "Select 1-4 armor pieces from your inventory, then press Save Set.";
            return;
        }

        if (selection.selectedSlots.isEmpty()) {
            sendStatus(client, "Select at least one armor piece.");
            return;
        }

        startCreateOperation(client, handledScreen, selection);
    }

    private static void cancelSelection(Screen screen) {
        if (selection == null || selection.screen != screen) {
            return;
        }
        selection = null;
        status = "";
    }

    private static boolean handleMouseClick(MinecraftClient client, Screen screen, Click click) {
        if (!(screen instanceof HandledScreen<?> handledScreen) || !isConfiguredWardrobe(handledScreen)) {
            return true;
        }

        WardrobeUiState uiState = UI_STATES.computeIfAbsent(screen, ignored -> new WardrobeUiState());
        WardrobeView view = inspectWardrobe(handledScreen);
        float uiScale = getWardrobeUiScale();
        int uiWidth = getScaledDimension(screen.width, uiScale);
        int uiHeight = getScaledDimension(screen.height, uiScale);
        double mouseX = click.x() / uiScale;
        double mouseY = click.y() / uiScale;
        WardrobeBrowserLayout layout = getBrowserLayout(uiWidth, uiHeight, view.sets().size());

        if (uiState.rawView) {
            if (click.button() == 0 && layout.rawReturnButton().contains(mouseX, mouseY)) {
                if (!handledScreen.getScreenHandler().getCursorStack().isEmpty()) {
                    sendStatus(client, "Put down the item on your cursor before returning to Wardrobe View.");
                    return false;
                }
                uiState.rawView = false;
                return false;
            }
            return true;
        }

        if (click.button() == 1) {
            int clickedSet = getClickedSetCard(view, layout, mouseX, mouseY);
            if (clickedSet >= 0
                    && selection == null
                    && view.sets().get(clickedSet).hasItems()) {
                client.setScreen(new WardrobeSetEditScreen(screen, clickedSet));
            }
            return false;
        }

        if (click.button() != 0) {
            return false;
        }

        if (layout.createButton().contains(mouseX, mouseY)) {
            handleCreateButton(client, screen, handledScreen);
            return false;
        }
        if (selection != null
                && selection.screen == screen
                && layout.cancelButton().contains(mouseX, mouseY)) {
            cancelSelection(screen);
            return false;
        }
        if (layout.rawButton().contains(mouseX, mouseY)) {
            cancelSelection(screen);
            uiState.rawView = true;
            return false;
        }
        if (layout.unassignButton().contains(mouseX, mouseY)) {
            if (selection != null || !handledScreen.getScreenHandler().getCursorStack().isEmpty()) {
                if (!handledScreen.getScreenHandler().getCursorStack().isEmpty()) {
                    sendStatus(client, "Put down the item on your cursor before unassigning the wardrobe.");
                }
                return false;
            }
            cancelSelection(screen);
            int vaultNumber = extractVaultNumber(handledScreen.getTitle().getString());
            int profile = VaultStorageManager.getProfileForVaultNumber(vaultNumber);
            setWardrobeVault(profile, -1);
            setActiveSet(profile, -1);
            savedSetsLoaded = true;
            loadedSetsProfile = profile;
            loadedSetsVault = -1;
            savedSets = List.of();
            saveSavedSets(client);
            sendStatus(client, "Wardrobe PV unassigned. Its items were not moved.");
            if (client.player != null) {
                client.player.closeHandledScreen();
            }
            return false;
        }

        int clickedSet = getClickedSet(view, layout, mouseX, mouseY);
        if (clickedSet >= 0) {
            startSwapOperation(client, handledScreen, clickedSet);
            return false;
        }

        if (selection != null && selection.screen == screen) {
            int inventoryIndex = getClickedInventoryIndex(layout, mouseX, mouseY);
            if (inventoryIndex < 0 || client.player == null) {
                return false;
            }

            ItemStack stack = client.player.getInventory().getStack(inventoryIndex);
            ArmorPart part = ArmorPart.fromStack(stack);
            if (part == null) {
                sendStatus(client, "Select armor pieces or a player head.");
                return false;
            }
            if (stack.getCount() != 1) {
                sendStatus(client, "Split stacked player heads before adding one to a wardrobe set.");
                return false;
            }

            Integer selectedIndex = selection.selectedSlots.get(part);
            if (selectedIndex != null && selectedIndex == inventoryIndex) {
                selection.selectedSlots.remove(part);
            } else {
                selection.selectedSlots.put(part, inventoryIndex);
            }
            return false;
        }

        return false;
    }

    private static void startCreateOperation(
            MinecraftClient client,
            HandledScreen<?> handledScreen,
            SelectionState state
    ) {
        if (client.player == null || handledScreen.getScreenHandler().getCursorStack().isEmpty() == false) {
            sendStatus(client, "Put down the item on your cursor before creating a set.");
            return;
        }

        WardrobeView view = inspectWardrobe(handledScreen);
        if (!view.valid() || state.targetSet >= view.sets().size() || view.sets().get(state.targetSet).hasItems()) {
            sendStatus(client, "The target wardrobe set is no longer empty.");
            return;
        }

        List<Integer> emptyPhysicalSlots = new ArrayList<>();
        int containerSlotCount = getContainerSlotCount(handledScreen);
        for (int physicalIndex = 0; physicalIndex < containerSlotCount; physicalIndex++) {
            if (!handledScreen.getScreenHandler().slots.get(physicalIndex).hasStack()) {
                emptyPhysicalSlots.add(physicalIndex);
            }
        }
        if (emptyPhysicalSlots.size() < state.selectedSlots.size()) {
            sendStatus(client, "The wardrobe PV needs "
                    + (state.selectedSlots.size() - emptyPhysicalSlots.size()) + " more empty slot(s).");
            return;
        }

        List<CreateTask> tasks = new ArrayList<>();
        int emptySlotCursor = 0;
        for (ArmorPart part : ArmorPart.values()) {
            Integer inventoryIndex = state.selectedSlots.get(part);
            if (inventoryIndex == null) {
                continue;
            }

            Slot source = findPlayerInventorySlot(handledScreen, client.player.getInventory(), inventoryIndex);
            if (source == null || ArmorPart.fromStack(source.getStack()) != part) {
                sendStatus(client, part.label + " is no longer in the selected inventory slot.");
                return;
            }

            int targetPhysicalIndex = emptyPhysicalSlots.get(emptySlotCursor++);
            Slot target = handledScreen.getScreenHandler().slots.get(targetPhysicalIndex);
            if (target.hasStack()) {
                sendStatus(client, "The wardrobe PV changed before the set could be saved.");
                return;
            }
            ItemStack savedStack = source.getStack().copy();
            tasks.add(new CreateTask(part, inventoryIndex, savedStack));
        }

        if (tasks.isEmpty()) {
            sendStatus(client, "Select at least one armor piece.");
            return;
        }

        selection = null;
        operation = new CreateOperation(
                handledScreen,
                state.targetSet,
                tasks
        );
        status = "";
    }

    private static void startSwapOperation(
            MinecraftClient client,
            HandledScreen<?> handledScreen,
            int setIndex
    ) {
        if (operation != null || selection != null || client.player == null || client.interactionManager == null) {
            return;
        }
        if (client.player.isInCreativeMode()) {
            sendStatus(client, "Wardrobe armor switching is unavailable in Creative mode. Switch to Survival or Adventure.");
            return;
        }
        if (!handledScreen.getScreenHandler().getCursorStack().isEmpty()) {
            sendStatus(client, "Put down the item on your cursor before switching armor.");
            return;
        }

        WardrobeView view = inspectWardrobe(handledScreen);
        if (!view.valid()) {
            sendStatus(client, view.warning());
            return;
        }
        if (setIndex < 0 || setIndex >= view.sets().size()) {
            return;
        }

        WardrobeSet selectedSet = view.sets().get(setIndex);
        if (!selectedSet.hasItems()) {
            sendStatus(client, "That wardrobe set is empty.");
            return;
        }

        int vaultNumber = extractVaultNumber(handledScreen.getTitle().getString());
        int profile = VaultStorageManager.getProfileForVaultNumber(vaultNumber);
        int equippedSetIndex = findEquippedSet(client, view.sets(), profile);
        boolean unequipSelectedSet = equippedSetIndex == setIndex
                || (getActiveSet(profile) == setIndex
                && isDefinedSetArmorEquipped(client, selectedSet));

        int stagingHotbarIndex = unequipSelectedSet
                ? -1
                : findEmptyHotbarSlot(client.player.getInventory());
        if (!unequipSelectedSet && stagingHotbarIndex < 0) {
            sendStatus(client, "You need one empty hotbar slot to switch wardrobe sets safely.");
            return;
        }

        List<Integer> emptyInventorySlots = new ArrayList<>();
        PlayerInventory inventory = client.player.getInventory();
        for (int index = 9; index < PLAYER_INVENTORY_SLOTS; index++) {
            if (inventory.getStack(index).isEmpty()) {
                emptyInventorySlots.add(index);
            }
        }

        List<SwapTask> tasks = new ArrayList<>();
        for (ArmorPart part : ArmorPart.values()) {
            ItemStack selectedPiece = selectedSet.stacks.getOrDefault(part, ItemStack.EMPTY);
            if (unequipSelectedSet && selectedPiece.isEmpty()) {
                continue;
            }

            ItemStack target = unequipSelectedSet
                    ? ItemStack.EMPTY
                    : selectedPiece;
            ItemStack current = client.player.getEquippedStack(part.slot);
            if (unequipSelectedSet && !sameWardrobeTransferItem(current, selectedPiece)) {
                continue;
            }
            if (!current.isEmpty() && ArmorPart.fromStack(current) != part) {
                sendStatus(client, "Remove the non-armor item equipped in the " + part.label.toLowerCase(Locale.ROOT) + " slot.");
                return;
            }
            if (EnchantmentHelper.hasAnyEnchantmentsWith(current, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)
                    || EnchantmentHelper.hasAnyEnchantmentsWith(target, EnchantmentEffectComponentTypes.PREVENT_ARMOR_CHANGE)) {
                sendStatus(client, "Binding armor cannot be switched through the wardrobe.");
                return;
            }

            if (current.isEmpty() && target.isEmpty()) {
                continue;
            }

            int targetPhysicalIndex = -1;
            if (!target.isEmpty()) {
                Integer locatedPhysicalIndex = selectedSet.physicalSlots.get(part);
                if (locatedPhysicalIndex == null) {
                    sendStatus(client, "The saved " + part.label.toLowerCase(Locale.ROOT)
                            + " for set " + (setIndex + 1) + " is missing from the wardrobe PV.");
                    return;
                }
                targetPhysicalIndex = locatedPhysicalIndex;
            }

            tasks.add(new SwapTask(
                    part,
                    targetPhysicalIndex,
                    -1,
                    target.copy(),
                    current.copy()
            ));
        }

        if (tasks.isEmpty()) {
            setActiveSet(VaultStorageManager.getProfileForVaultNumber(vaultNumber), -1);
            sendStatus(client, "No wardrobe armor is equipped.");
            return;
        }

        if (emptyInventorySlots.size() < tasks.size()) {
            sendStatus(client, "You need " + tasks.size()
                    + " empty main-inventory slots before switching this set.");
            return;
        }
        for (int taskIndex = 0; taskIndex < tasks.size(); taskIndex++) {
            int inventoryIndex = emptyInventorySlots.get(taskIndex);
            tasks.get(taskIndex).inventoryIndex = inventoryIndex;
            tasks.get(taskIndex).reservedInventoryIndex = inventoryIndex;
        }

        boolean returnCurrentToWardrobe = unequipSelectedSet || equippedSetIndex >= 0;

        operation = new SwapOperation(
                handledScreen,
                vaultNumber,
                setIndex,
                tasks,
                stagingHotbarIndex,
                returnCurrentToWardrobe,
                unequipSelectedSet,
                VaultBrowserWidget.getReopenWardrobeAfterSwitchSetting()
        );
        status = "";
    }

    private static int findMatchingEquippedSet(MinecraftClient client, List<WardrobeSet> sets) {
        if (client.player == null) {
            return -1;
        }
        for (int setIndex = 0; setIndex < sets.size(); setIndex++) {
            WardrobeSet set = sets.get(setIndex);
            if (!set.hasItems()) {
                continue;
            }
            boolean matches = true;
            for (ArmorPart part : ArmorPart.values()) {
                ItemStack equipped = client.player.getEquippedStack(part.slot);
                ItemStack expected = set.stacks.getOrDefault(part, ItemStack.EMPTY);
                if (!sameWardrobeItem(equipped, expected)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                return setIndex;
            }
        }
        return -1;
    }

    private static boolean isDefinedSetArmorEquipped(
            MinecraftClient client,
            WardrobeSet set
    ) {
        if (client.player == null || !set.hasItems()) {
            return false;
        }
        for (ArmorPart part : ArmorPart.values()) {
            ItemStack expected = set.stacks.getOrDefault(part, ItemStack.EMPTY);
            if (expected.isEmpty()) {
                continue;
            }
            if (!sameWardrobeTransferItem(
                    client.player.getEquippedStack(part.slot),
                    expected
            )) {
                return false;
            }
        }
        return true;
    }

    private static int findEquippedSet(
            MinecraftClient client,
            List<WardrobeSet> sets,
            int profile
    ) {
        int exactMatch = findMatchingEquippedSet(client, sets);
        if (exactMatch >= 0) {
            return exactMatch;
        }

        int activeSet = getActiveSet(profile);
        if (activeSet < 0 || activeSet >= sets.size() || client.player == null) {
            return -1;
        }

        WardrobeSet set = sets.get(activeSet);
        if (!set.hasItems()) {
            return -1;
        }
        for (ArmorPart part : ArmorPart.values()) {
            ItemStack equipped = client.player.getEquippedStack(part.slot);
            ItemStack expected = set.stacks.getOrDefault(part, ItemStack.EMPTY);
            if (!sameWardrobeTransferItem(equipped, expected)) {
                return -1;
            }
        }
        return activeSet;
    }

    private static void tick(MinecraftClient client) {
        if (!AddonServerGate.shouldRunOnCurrentServer()) {
            operation = null;
            selection = null;
            return;
        }
        if (operation == null) {
            return;
        }

        OperationResult result = operation.tick(client);
        if (result == OperationResult.RUNNING) {
            return;
        }

        String finalStatus = operation.status();
        if (result == OperationResult.COMPLETE) {
            if (operation instanceof CreateOperation createOperation) {
                replaceSavedSet(createOperation.setIndex, createOperation.completedDefinition());
                saveSavedSets(client);
            } else if (operation instanceof SwapOperation swapOperation) {
                int profile = VaultStorageManager.getProfileForVaultNumber(swapOperation.vaultNumber);
                setActiveSet(profile, swapOperation.unequipSelectedSet ? -1 : swapOperation.setIndex);
            }
            if (client.currentScreen instanceof HandledScreen<?> handledScreen) {
                VaultStorageManager.refreshVisibleVaultSnapshot(handledScreen);
            }
        } else if (operation instanceof CreateOperation createOperation
                && createOperation.hasConfirmedStacks()) {
            replaceSavedSet(createOperation.setIndex, createOperation.completedDefinition());
            saveSavedSets(client);
            finalStatus += " Confirmed pieces were kept as a partial set.";
        }
        operation = null;
        if (!finalStatus.isBlank()) {
            sendStatus(client, finalStatus);
        }
    }

    private static void renderWardrobe(
            HandledScreen<?> handledScreen,
            DrawContext context,
            int mouseX,
            int mouseY
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }

        WardrobeView view = inspectWardrobe(handledScreen);
        WardrobeUiState uiState = UI_STATES.computeIfAbsent(handledScreen, ignored -> new WardrobeUiState());
        float uiScale = getWardrobeUiScale();
        int uiWidth = getScaledDimension(handledScreen.width, uiScale);
        int uiHeight = getScaledDimension(handledScreen.height, uiScale);
        int uiMouseX = (int) Math.floor(mouseX / uiScale);
        int uiMouseY = (int) Math.floor(mouseY / uiScale);

        context.getMatrices().pushMatrix();
        context.getMatrices().scale(uiScale, uiScale);
        renderWardrobeUi(
                handledScreen,
                context,
                view,
                uiState,
                uiWidth,
                uiHeight,
                uiMouseX,
                uiMouseY
        );
        context.getMatrices().popMatrix();
    }

    private static void renderWardrobeUi(
            HandledScreen<?> handledScreen,
            DrawContext context,
            WardrobeView view,
            WardrobeUiState uiState,
            int uiWidth,
            int uiHeight,
            int mouseX,
            int mouseY
    ) {
        MinecraftClient client = MinecraftClient.getInstance();
        WardrobeBrowserLayout layout = getBrowserLayout(uiWidth, uiHeight, view.sets().size());

        if (uiState.rawView) {
            drawBrowserButton(
                    context,
                    client,
                    layout.rawReturnButton(),
                    "Wardrobe View",
                    true,
                    layout.rawReturnButton().contains(mouseX, mouseY)
            );
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal("Raw PV view: remove or rearrange invalid items, then return."),
                    uiWidth / 2,
                    8,
                    0xFFFFE080
            );
            return;
        }

        context.fillGradient(0, 0, uiWidth, uiHeight, 0xF0181018, 0xF0080808);
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal("Wardrobe - PV #" + extractVaultNumber(handledScreen.getTitle().getString())),
                uiWidth / 2,
                36,
                0xFFFFFFFF
        );

        drawBrowserButton(
                context,
                client,
                layout.createButton(),
                selection != null && selection.screen == handledScreen ? "Save Set" : "Create Set",
                view.valid(),
                layout.createButton().contains(mouseX, mouseY)
        );
        if (selection != null && selection.screen == handledScreen) {
            drawBrowserButton(
                    context,
                    client,
                    layout.cancelButton(),
                    "Cancel",
                    true,
                    layout.cancelButton().contains(mouseX, mouseY)
            );
        }
        drawBrowserButton(
                context,
                client,
                layout.rawButton(),
                "Raw PV",
                true,
                layout.rawButton().contains(mouseX, mouseY)
        );
        drawBrowserButton(
                context,
                client,
                layout.unassignButton(),
                "Unassign",
                selection == null,
                layout.unassignButton().contains(mouseX, mouseY)
        );

        if (!view.valid()) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal(view.warning()),
                    uiWidth / 2,
                    uiHeight / 2 - 8,
                    0xFFFF7070
            );
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal("Open Raw PV to remove the invalid contents."),
                    uiWidth / 2,
                    uiHeight / 2 + 8,
                    0xFFFFFFFF
            );
            return;
        }

        ItemStack hoveredStack = ItemStack.EMPTY;
        int wardrobeVault = extractVaultNumber(handledScreen.getTitle().getString());
        int equippedSetIndex = findEquippedSet(
                client,
                view.sets(),
                VaultStorageManager.getProfileForVaultNumber(wardrobeVault)
        );
        UiRect wardrobePanel = layout.wardrobePanel();
        context.fillGradient(
                wardrobePanel.left,
                wardrobePanel.top,
                wardrobePanel.right(),
                wardrobePanel.bottom(),
                0xE0201A24,
                0xE0100D12
        );
        drawPanelBorder(context, wardrobePanel, 0xFF7A2A24, 0xFF3A1612);

        for (int setIndex = 0; setIndex < view.sets().size(); setIndex++) {
            WardrobeSet set = view.sets().get(setIndex);
            UiRect card = layout.card(setIndex);
            boolean active = setIndex == equippedSetIndex;
            boolean target = selection != null
                    && selection.screen == handledScreen
                    && selection.targetSet == setIndex;
            boolean hovered = card.contains(mouseX, mouseY);
            int borderColor = target ? 0xFFD88C34 : active ? 0xFF55A866 : hovered ? 0xFF9A4A34 : 0xFF7A2A24;

            if (set.backgroundColor != 0) {
                context.fill(
                        card.left + 1,
                        card.top + 1,
                        card.right() - 1,
                        card.bottom() - 1,
                        set.backgroundColor
                );
            }
            if (target || active || hovered) {
                context.fill(
                        card.left + 1,
                        card.top + 1,
                        card.right() - 1,
                        card.bottom() - 1,
                        target ? 0x503A2A16 : active ? 0x50305A38 : 0x302A2028
                );
                context.fill(card.left + 1, card.top + 1, card.right() - 1, card.top + 3, borderColor);
            }
            if (!layout.isLastColumn(setIndex)) {
                context.fill(card.right() - 1, card.top + 7, card.right(), card.bottom() - 7, 0xFF3A2428);
            }
            if (!layout.isLastRow(setIndex)) {
                context.fill(card.left + 7, card.bottom() - 1, card.right() - 7, card.bottom(), 0xFF3A2428);
            }
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal(fitSetLabel(client, set.displayName(setIndex), card.width - 8)),
                    card.centerX(),
                    card.top + 5,
                    active ? 0xFF7CDE8B : target ? 0xFFFFC060 : 0xFFFFFFFF
            );

            for (ArmorPart part : ArmorPart.values()) {
                UiRect slotRect = layout.setSlot(setIndex, part);
                context.fill(slotRect.left, slotRect.top, slotRect.right(), slotRect.bottom(), 0xA0101010);
                ItemStack stack = set.stacks.getOrDefault(part, ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    context.drawItem(stack, slotRect.left, slotRect.top);
                    context.drawStackOverlay(client.textRenderer, stack, slotRect.left, slotRect.top);
                    if (slotRect.contains(mouseX, mouseY)) {
                        hoveredStack = stack;
                    }
                }
            }

            UiRect selector = layout.setButton(setIndex);
            drawSetSelector(
                    context,
                    selector,
                    active,
                    set.hasItems() || active,
                    selector.contains(mouseX, mouseY)
            );
        }

        UiRect inventoryPanel = layout.inventoryPanel();
        context.fillGradient(
                inventoryPanel.left,
                inventoryPanel.top,
                inventoryPanel.right(),
                inventoryPanel.bottom(),
                0xE0201A24,
                0xE0100D12
        );
        drawPanelBorder(context, inventoryPanel, 0xFF7A2A24, 0xFF3A1612);
        String inventoryTitle = selection != null && selection.screen == handledScreen
                ? "Select armor from inventory"
                : "Player Inventory";
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(inventoryTitle),
                inventoryPanel.centerX(),
                inventoryPanel.top + 6,
                0xFFFFFFFF
        );

        for (int inventoryIndex = 0; inventoryIndex < PLAYER_INVENTORY_SLOTS; inventoryIndex++) {
            UiRect slotRect = layout.inventorySlot(inventoryIndex);
            boolean selected = selection != null
                    && selection.screen == handledScreen
                    && selection.selectedSlots.containsValue(inventoryIndex);
            context.fill(slotRect.left, slotRect.top, slotRect.right(), slotRect.bottom(), 0xA0101010);
            if (selected) {
                drawSelectionBorder(context, slotRect.left, slotRect.top, 0xFF55FF55);
            }

            ItemStack stack = client.player.getInventory().getStack(inventoryIndex);
            if (!stack.isEmpty()) {
                context.drawItem(stack, slotRect.left, slotRect.top);
                context.drawStackOverlay(client.textRenderer, stack, slotRect.left, slotRect.top);
                if (slotRect.contains(mouseX, mouseY)) {
                    hoveredStack = stack;
                }
            }
        }

        if (selection != null && selection.screen == handledScreen) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal("Creating set " + (selection.targetSet + 1) + ": choose 1-4 pieces, then Save Set"),
                    uiWidth / 2,
                    inventoryPanel.bottom() + 4,
                    0xFFFFE080
            );
        } else if (!status.isBlank()) {
            context.drawCenteredTextWithShadow(
                    client.textRenderer,
                    Text.literal(status),
                    uiWidth / 2,
                    uiHeight - 14,
                    0xFFFFE080
            );
        }

        if (!hoveredStack.isEmpty()) {
            List<Text> tooltip = new ArrayList<>();
            tooltip.add(hoveredStack.getName());
            tooltip.addAll(VaultStorageManager.getLoreLines(hoveredStack));
            context.drawTooltip(client.textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private static void drawSelectionBorder(DrawContext context, int x, int y, int color) {
        context.fill(x - 1, y - 1, x + 17, y, color);
        context.fill(x - 1, y + 16, x + 17, y + 17, color);
        context.fill(x - 1, y, x, y + 16, color);
        context.fill(x + 16, y, x + 17, y + 16, color);
    }

    private static void drawPanelBorder(DrawContext context, UiRect rect, int topLeftColor, int bottomRightColor) {
        context.fill(rect.left, rect.top, rect.right(), rect.top + 1, topLeftColor);
        context.fill(rect.left, rect.top + 1, rect.left + 1, rect.bottom(), topLeftColor);
        context.fill(rect.right() - 1, rect.top + 1, rect.right(), rect.bottom(), bottomRightColor);
        context.fill(rect.left + 1, rect.bottom() - 1, rect.right(), rect.bottom(), bottomRightColor);
    }

    private static void drawBrowserButton(
            DrawContext context,
            MinecraftClient client,
            UiRect rect,
            String label,
            boolean active,
            boolean hovered
    ) {
        int color = !active ? 0xFF292329 : hovered ? 0xFF754132 : 0xFF4A302C;
        int border = !active ? 0xFF403840 : hovered ? 0xFFD88C34 : 0xFF7A2A24;
        context.fill(rect.left, rect.top, rect.right(), rect.bottom(), color);
        drawPanelBorder(context, rect, border, 0xFF2A1210);
        context.drawCenteredTextWithShadow(
                client.textRenderer,
                Text.literal(label),
                rect.centerX(),
                rect.top + Math.max(1, (rect.height - client.textRenderer.fontHeight) / 2),
                active ? 0xFFFFFFFF : 0xFF777777
        );
    }

    private static void drawSetSelector(
            DrawContext context,
            UiRect rect,
            boolean selected,
            boolean active,
            boolean hovered
    ) {
        int background = active ? 0xFF241F24 : 0xFF181518;
        int border = !active ? 0xFF403840 : selected ? 0xFF55A866 : hovered ? 0xFFD88C34 : 0xFF7A2A24;
        context.fill(rect.left, rect.top, rect.right(), rect.bottom(), background);
        drawPanelBorder(context, rect, border, 0xFF2A1210);
        if (selected) {
            context.fill(rect.left + 3, rect.top + 3, rect.right() - 3, rect.bottom() - 3, 0xFF55A866);
        }
    }

    private static int getClickedSet(
            WardrobeView view,
            WardrobeBrowserLayout layout,
            double mouseX,
            double mouseY
    ) {
        if (!view.valid()) {
            return -1;
        }
        for (int setIndex = 0; setIndex < view.sets().size(); setIndex++) {
            if (layout.setButton(setIndex).contains(mouseX, mouseY)) {
                return setIndex;
            }
        }
        return -1;
    }

    private static int getClickedSetCard(
            WardrobeView view,
            WardrobeBrowserLayout layout,
            double mouseX,
            double mouseY
    ) {
        if (!view.valid()) {
            return -1;
        }
        for (int setIndex = 0; setIndex < view.sets().size(); setIndex++) {
            if (layout.card(setIndex).contains(mouseX, mouseY)) {
                return setIndex;
            }
        }
        return -1;
    }

    private static int getClickedInventoryIndex(
            WardrobeBrowserLayout layout,
            double mouseX,
            double mouseY
    ) {
        for (int inventoryIndex = 0; inventoryIndex < PLAYER_INVENTORY_SLOTS; inventoryIndex++) {
            if (layout.inventorySlot(inventoryIndex).contains(mouseX, mouseY)) {
                return inventoryIndex;
            }
        }
        return -1;
    }

    private static WardrobeBrowserLayout getBrowserLayout(int screenWidth, int screenHeight, int setCount) {
        int usableWidth = Math.max(SET_PANEL_WIDTH, screenWidth - (OUTER_MARGIN * 2));
        int columns = Math.max(1, usableWidth / SET_PANEL_WIDTH);
        columns = Math.min(Math.max(1, setCount), columns);
        int rows = Math.max(1, (int) Math.ceil(setCount / (double) columns));
        int cardsWidth = columns * SET_PANEL_WIDTH;
        int cardsLeft = (screenWidth - cardsWidth) / 2;
        int cardsTop = HEADER_HEIGHT;
        int cardsBottom = cardsTop + rows * SET_PANEL_HEIGHT;
        int inventoryTop = Math.max(cardsBottom + 12, screenHeight - INVENTORY_PANEL_HEIGHT - 20);
        int inventoryLeft = (screenWidth - INVENTORY_PANEL_WIDTH) / 2;

        return new WardrobeBrowserLayout(
                columns,
                rows,
                setCount,
                cardsLeft,
                cardsTop,
                inventoryLeft,
                inventoryTop,
                new UiRect(OUTER_MARGIN, 10, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT),
                new UiRect(OUTER_MARGIN + TOP_BUTTON_WIDTH + 6, 10, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT),
                new UiRect(screenWidth - OUTER_MARGIN - (TOP_BUTTON_WIDTH * 2) - 6, 10, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT),
                new UiRect(screenWidth - OUTER_MARGIN - TOP_BUTTON_WIDTH, 10, TOP_BUTTON_WIDTH, TOP_BUTTON_HEIGHT)
        );
    }

    private static float getWardrobeUiScale() {
        return Math.max(0.6f, Math.min(1.5f, VaultBrowserWidget.getWardrobeScaleSetting()));
    }

    private static int getScaledDimension(int dimension, float scale) {
        return Math.max(1, (int) Math.ceil(dimension / scale));
    }

    private static WardrobeView inspectWardrobe(HandledScreen<?> handledScreen) {
        int containerSlotCount = getContainerSlotCount(handledScreen);
        int setCount = containerSlotCount / SLOTS_PER_SET;
        ensureSavedSetsLoaded(handledScreen, setCount);

        List<Integer> availablePhysicalSlots = new ArrayList<>();
        for (int physicalIndex = 0; physicalIndex < containerSlotCount; physicalIndex++) {
            ItemStack stack = handledScreen.getScreenHandler().slots.get(physicalIndex).getStack();
            if (stack.isEmpty()) {
                continue;
            }
            if (ArmorPart.fromStack(stack) == null) {
                continue;
            }
            if (stack.getCount() != 1) {
                continue;
            }
            if (isPendingCreateTarget(handledScreen, physicalIndex, stack)) {
                continue;
            }
            availablePhysicalSlots.add(physicalIndex);
        }

        List<WardrobeSet> sets = new ArrayList<>(setCount);
        int screenVault = extractVaultNumber(handledScreen.getTitle().getString());
        int equippedSetIndex = findEquippedSet(
                MinecraftClient.getInstance(),
                savedSets,
                VaultStorageManager.getProfileForVaultNumber(screenVault)
        );
        for (int setIndex = 0; setIndex < setCount; setIndex++) {
            WardrobeSet definition = savedSets.get(setIndex);
            EnumMap<ArmorPart, Integer> physicalSlots = new EnumMap<>(ArmorPart.class);
            if (setIndex != equippedSetIndex) {
                for (ArmorPart part : ArmorPart.values()) {
                    ItemStack expected = definition.stacks.getOrDefault(part, ItemStack.EMPTY);
                    if (expected.isEmpty()) {
                        continue;
                    }
                    int physicalIndex = findMatchingPhysicalSlot(handledScreen, availablePhysicalSlots, expected);
                    if (physicalIndex >= 0) {
                        physicalSlots.put(part, physicalIndex);
                        availablePhysicalSlots.remove(Integer.valueOf(physicalIndex));
                    }
                }
            }
            sets.add(new WardrobeSet(
                    copySetStacks(definition.stacks),
                    physicalSlots,
                    definition.customLabel,
                    definition.backgroundColor
            ));
        }

        return new WardrobeView(List.copyOf(sets), "");
    }

    private static int findEmptySet(WardrobeView view) {
        for (int setIndex = 0; setIndex < view.sets().size(); setIndex++) {
            if (!view.sets().get(setIndex).hasItems()) {
                return setIndex;
            }
        }
        return -1;
    }

    private static int findMatchingPhysicalSlot(
            HandledScreen<?> handledScreen,
            List<Integer> candidates,
            ItemStack expected
    ) {
        for (int physicalIndex : candidates) {
            if (sameWardrobeItem(handledScreen.getScreenHandler().slots.get(physicalIndex).getStack(), expected)) {
                return physicalIndex;
            }
        }
        return -1;
    }

    private static Slot findMatchingContainerSlot(
            HandledScreen<?> screen,
            int preferredPhysicalIndex,
            ItemStack expected
    ) {
        int containerSlotCount = getContainerSlotCount(screen);
        if (preferredPhysicalIndex >= 0 && preferredPhysicalIndex < containerSlotCount) {
            Slot preferred = screen.getScreenHandler().slots.get(preferredPhysicalIndex);
            if (sameWardrobeItem(preferred.getStack(), expected)) {
                return preferred;
            }
        }
        for (int physicalIndex = 0; physicalIndex < containerSlotCount; physicalIndex++) {
            if (physicalIndex == preferredPhysicalIndex) {
                continue;
            }
            Slot slot = screen.getScreenHandler().slots.get(physicalIndex);
            if (sameWardrobeItem(slot.getStack(), expected)) {
                return slot;
            }
        }
        return null;
    }

    private static EnumMap<ArmorPart, ItemStack> copySetStacks(Map<ArmorPart, ItemStack> source) {
        EnumMap<ArmorPart, ItemStack> copy = new EnumMap<>(ArmorPart.class);
        for (ArmorPart part : ArmorPart.values()) {
            ItemStack stack = source.getOrDefault(part, ItemStack.EMPTY);
            if (!stack.isEmpty()) {
                copy.put(part, stack.copy());
            }
        }
        return copy;
    }

    private static List<WardrobeSet> createEmptySetList(int setCount) {
        List<WardrobeSet> sets = new ArrayList<>(setCount);
        for (int index = 0; index < setCount; index++) {
            sets.add(WardrobeSet.empty());
        }
        return List.copyOf(sets);
    }

    private static void ensureSavedSetsLoaded(HandledScreen<?> handledScreen, int setCount) {
        int screenVault = extractVaultNumber(handledScreen.getTitle().getString());
        int profile = VaultStorageManager.getProfileForVaultNumber(screenVault);
        int vaultNumber = getWardrobeVault(profile);
        if (savedSetsLoaded && loadedSetsProfile == profile && loadedSetsVault == vaultNumber) {
            savedSets = resizeSavedSets(savedSets, setCount);
            return;
        }

        savedSetsLoaded = true;
        loadedSetsProfile = profile;
        loadedSetsVault = vaultNumber;
        savedSets = createEmptySetList(setCount);

        MinecraftClient client = MinecraftClient.getInstance();
        Path path = getSavedSetsPath(client);
        boolean loadedFromDisk = false;
        if (path != null && Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                PersistedWardrobeFile file = GSON.fromJson(reader, SETS_FILE_TYPE);
                PersistedWardrobeProfile persistedProfile = getPersistedProfile(file, profile);
                if (persistedProfile != null && persistedProfile.vaultNumber == vaultNumber) {
                    List<WardrobeSet> decoded = decodeSavedSets(client, persistedProfile.sets);
                    savedSets = resizeSavedSets(decoded, setCount);
                    loadedFromDisk = !decoded.isEmpty();
                }
            } catch (Exception e) {
                LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to load wardrobe sets.", e);
            }
        }

        if (!loadedFromDisk && hasContainerArmor(handledScreen)) {
            savedSets = migrateColumnSets(handledScreen, setCount);
            saveSavedSets(client);
        }
    }

    private static List<WardrobeSet> resizeSavedSets(List<WardrobeSet> source, int setCount) {
        List<WardrobeSet> resized = new ArrayList<>(setCount);
        for (int index = 0; index < setCount; index++) {
            resized.add(index < source.size() ? source.get(index) : WardrobeSet.empty());
        }
        return List.copyOf(resized);
    }

    private static boolean hasContainerArmor(HandledScreen<?> handledScreen) {
        int containerSlotCount = getContainerSlotCount(handledScreen);
        for (int physicalIndex = 0; physicalIndex < containerSlotCount; physicalIndex++) {
            if (ArmorPart.fromStack(handledScreen.getScreenHandler().slots.get(physicalIndex).getStack()) != null) {
                return true;
            }
        }
        return false;
    }

    private static List<WardrobeSet> migrateColumnSets(HandledScreen<?> handledScreen, int setCount) {
        List<WardrobeSet> migrated = new ArrayList<>(setCount);
        for (int setIndex = 0; setIndex < setCount; setIndex++) {
            EnumMap<ArmorPart, ItemStack> stacks = new EnumMap<>(ArmorPart.class);
            int baseIndex = setIndex * SLOTS_PER_SET;
            for (int offset = 0; offset < SLOTS_PER_SET; offset++) {
                ItemStack stack = handledScreen.getScreenHandler().slots.get(baseIndex + offset).getStack();
                ArmorPart part = ArmorPart.fromStack(stack);
                if (part != null && stack.getCount() == 1 && !stacks.containsKey(part)) {
                    stacks.put(part, stack.copy());
                }
            }
            migrated.add(new WardrobeSet(stacks, new EnumMap<>(ArmorPart.class), "", 0));
        }
        return List.copyOf(migrated);
    }

    private static void replaceSavedSet(int setIndex, WardrobeSet replacement) {
        if (setIndex < 0 || setIndex >= savedSets.size()) {
            return;
        }
        List<WardrobeSet> updated = new ArrayList<>(savedSets);
        updated.set(setIndex, new WardrobeSet(
                copySetStacks(replacement.stacks),
                new EnumMap<>(ArmorPart.class),
                replacement.customLabel,
                replacement.backgroundColor
        ));
        savedSets = List.copyOf(updated);
    }

    static String getSetCustomLabel(int setIndex) {
        if (setIndex < 0 || setIndex >= savedSets.size()) {
            return "";
        }
        return savedSets.get(setIndex).customLabel;
    }

    static int getSetBackgroundColor(int setIndex) {
        if (setIndex < 0 || setIndex >= savedSets.size()) {
            return 0;
        }
        return savedSets.get(setIndex).backgroundColor;
    }

    static String getSetDisplayName(int setIndex) {
        if (setIndex < 0 || setIndex >= savedSets.size()) {
            return "Set " + (setIndex + 1);
        }
        return savedSets.get(setIndex).displayName(setIndex);
    }

    static void updateSetAppearance(
            MinecraftClient client,
            int setIndex,
            String customLabel,
            int backgroundColor
    ) {
        if (setIndex < 0 || setIndex >= savedSets.size()) {
            return;
        }

        WardrobeSet current = savedSets.get(setIndex);
        List<WardrobeSet> updated = new ArrayList<>(savedSets);
        updated.set(setIndex, new WardrobeSet(
                copySetStacks(current.stacks),
                new EnumMap<>(current.physicalSlots),
                customLabel == null ? "" : customLabel.trim(),
                backgroundColor
        ));
        savedSets = List.copyOf(updated);
        saveSavedSets(client);
    }

    static void unassignSet(MinecraftClient client, int setIndex) {
        if (setIndex < 0 || setIndex >= savedSets.size()) {
            return;
        }

        List<WardrobeSet> updated = new ArrayList<>(savedSets);
        updated.set(setIndex, WardrobeSet.empty());
        savedSets = List.copyOf(updated);
        if (loadedSetsProfile > 0 && getActiveSet(loadedSetsProfile) == setIndex) {
            setActiveSet(loadedSetsProfile, -1);
        }
        saveSavedSets(client);
        sendStatus(client, "Unassigned wardrobe set " + (setIndex + 1) + ". Items were not moved.");
    }

    private static Slot findPlayerInventorySlot(
            HandledScreen<?> screen,
            PlayerInventory inventory,
            int inventoryIndex
    ) {
        for (Slot slot : screen.getScreenHandler().slots) {
            if (slot.inventory == inventory && slot.getIndex() == inventoryIndex) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findMatchingPlayerInventorySlot(
            HandledScreen<?> screen,
            PlayerInventory inventory,
            int preferredInventoryIndex,
            int excludedInventoryIndex,
            ItemStack expected
    ) {
        Slot preferred = findPlayerInventorySlot(screen, inventory, preferredInventoryIndex);
        if (preferred != null
                && preferred.getIndex() != excludedInventoryIndex
                && sameWardrobeItem(preferred.getStack(), expected)) {
            return preferred;
        }

        for (Slot slot : screen.getScreenHandler().slots) {
            int inventoryIndex = slot.getIndex();
            if (slot.inventory == inventory
                    && inventoryIndex >= 0
                    && inventoryIndex < PLAYER_INVENTORY_SLOTS
                    && inventoryIndex != excludedInventoryIndex
                    && sameWardrobeItem(slot.getStack(), expected)) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findMatchingPlayerInventoryTransferSlot(
            HandledScreen<?> screen,
            PlayerInventory inventory,
            int preferredInventoryIndex,
            int excludedInventoryIndex,
            ItemStack expected
    ) {
        Slot exact = findMatchingPlayerInventorySlot(
                screen,
                inventory,
                preferredInventoryIndex,
                excludedInventoryIndex,
                expected
        );
        if (exact != null) {
            return exact;
        }

        Slot preferred = findPlayerInventorySlot(screen, inventory, preferredInventoryIndex);
        if (preferred != null
                && preferred.getIndex() != excludedInventoryIndex
                && sameWardrobeTransferItem(preferred.getStack(), expected)) {
            return preferred;
        }

        for (Slot slot : screen.getScreenHandler().slots) {
            int inventoryIndex = slot.getIndex();
            if (slot.inventory == inventory
                    && inventoryIndex >= 0
                    && inventoryIndex < PLAYER_INVENTORY_SLOTS
                    && inventoryIndex != excludedInventoryIndex
                    && sameWardrobeTransferItem(slot.getStack(), expected)) {
                return slot;
            }
        }
        return null;
    }

    private static Slot findArmorSlot(
            InventoryScreen screen,
            PlayerInventory inventory,
            ArmorPart part
    ) {
        int armorInventoryIndex = 39 - part.ordinal();
        return findPlayerInventorySlot(screen, inventory, armorInventoryIndex);
    }

    private static int findEmptyHotbarSlot(PlayerInventory inventory) {
        for (int index = 0; index < PlayerInventory.getHotbarSize(); index++) {
            if (inventory.getStack(index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    private static int countMatchingPlayerInventory(
            PlayerInventory inventory,
            ItemStack expected
    ) {
        int matches = 0;
        for (int inventoryIndex = 0; inventoryIndex < PLAYER_INVENTORY_SLOTS; inventoryIndex++) {
            if (sameWardrobeTransferItem(inventory.getStack(inventoryIndex), expected)) {
                matches++;
            }
        }
        return matches;
    }

    private static boolean sameWardrobeItem(ItemStack actual, ItemStack expected) {
        return sameWardrobeTransferItem(actual, expected);
    }

    private static String normalizeItemName(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        String name = stack.getName().getString();
        StringBuilder normalized = new StringBuilder(name.length());
        name.codePoints()
                .filter(Character::isLetterOrDigit)
                .map(Character::toLowerCase)
                .forEach(normalized::appendCodePoint);
        return normalized.toString();
    }

    private static boolean sameWardrobeTransferItem(ItemStack actual, ItemStack expected) {
        if (actual == null || actual.isEmpty()) {
            return expected == null || expected.isEmpty();
        }
        if (expected == null || expected.isEmpty()) {
            return false;
        }
        if (!ItemStack.areItemsEqual(actual, expected)
                || actual.getCount() != expected.getCount()) {
            return false;
        }

        String actualCustomItemId = getCustomItemId(actual);
        String expectedCustomItemId = getCustomItemId(expected);
        if (!actualCustomItemId.isBlank() || !expectedCustomItemId.isBlank()) {
            if (actualCustomItemId.isBlank()
                    || !actualCustomItemId.equals(expectedCustomItemId)) {
                return false;
            }
        }

        return normalizeItemName(actual).equals(normalizeItemName(expected));
    }

    private static String getCustomItemId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }

        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null || customData.isEmpty()) {
            return "";
        }

        NbtCompound nbt = customData.copyNbt();
        NbtElement value = nbt.get(CUSTOM_ITEM_ID_KEY);
        if (value == null) {
            return "";
        }
        return value.asString()
                .orElseGet(value::toString)
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static boolean isPendingCreateTarget(
            HandledScreen<?> screen,
            int physicalIndex,
            ItemStack stack
    ) {
        if (!(operation instanceof CreateOperation createOperation) || createOperation.screen != screen) {
            return false;
        }
        for (CreateTask task : createOperation.tasks) {
            if (sameWardrobeItem(stack, task.stack)) {
                return true;
            }
        }
        return false;
    }

    private static int getContainerSlotCount(HandledScreen<?> handledScreen) {
        return Math.max(0, handledScreen.getScreenHandler().slots.size() - PLAYER_INVENTORY_SLOTS);
    }

    private static boolean isContainerEmpty(HandledScreen<?> handledScreen) {
        int containerSlotCount = getContainerSlotCount(handledScreen);
        for (int index = 0; index < containerSlotCount; index++) {
            if (handledScreen.getScreenHandler().slots.get(index).hasStack()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isConfiguredWardrobe(HandledScreen<?> handledScreen) {
        int vaultNumber = extractVaultNumber(handledScreen.getTitle().getString());
        int profile = VaultStorageManager.getProfileForVaultNumber(vaultNumber);
        return AddonServerGate.shouldRunOnCurrentServer()
                && vaultNumber > 0
                && vaultNumber == getWardrobeVault(profile);
    }

    private static boolean isVaultScreen(HandledScreen<?> handledScreen) {
        String title = handledScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        return !title.contains(STORAGE_MENU_TITLE) && extractVaultNumber(title) > 0;
    }

    private static int extractVaultNumber(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }
        Matcher matcher = VAULT_NUMBER_PATTERN.matcher(text);
        if (!matcher.find()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    public static boolean isWardrobeVault(int vaultNumber) {
        if (vaultNumber < 1) {
            return false;
        }
        int profile = VaultStorageManager.getProfileForVaultNumber(vaultNumber);
        return getWardrobeVault(profile) == vaultNumber;
    }

    public static boolean openSelectedProfileWardrobe(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null) {
            return false;
        }

        int profile = VaultStorageManager.getSelectedProfile();
        int vaultNumber = getWardrobeVault(profile);
        if (vaultNumber < 1) {
            sendStatus(client, "No wardrobe is assigned for Profile " + profile + ".");
            return false;
        }

        client.getNetworkHandler().sendChatCommand("pv " + vaultNumber);
        return true;
    }

    public static int getWardrobeVault(int profile) {
        ensureLegacyProfileConfig(profile);
        return WidgetConfigManager.getInt(CONFIG_ID, profileKey(VAULT_KEY, profile), -1);
    }

    private static void setWardrobeVault(int profile, int vaultNumber) {
        WidgetConfigManager.setInt(CONFIG_ID, profileKey(VAULT_KEY, profile), vaultNumber, true);
    }

    private static int getActiveSet(int profile) {
        ensureLegacyProfileConfig(profile);
        return WidgetConfigManager.getInt(CONFIG_ID, profileKey(ACTIVE_SET_KEY, profile), -1);
    }

    private static void setActiveSet(int profile, int setIndex) {
        WidgetConfigManager.setInt(CONFIG_ID, profileKey(ACTIVE_SET_KEY, profile), setIndex, true);
    }

    private static String profileKey(String key, int profile) {
        return key + "_profile_" + profile;
    }

    private static void ensureLegacyProfileConfig(int profile) {
        int missing = Integer.MIN_VALUE;
        String vaultKey = profileKey(VAULT_KEY, profile);
        if (WidgetConfigManager.getInt(CONFIG_ID, vaultKey, missing) != missing) {
            return;
        }

        int legacyVault = WidgetConfigManager.getInt(CONFIG_ID, VAULT_KEY, -1);
        boolean ownsLegacyWardrobe = legacyVault > 0
                && VaultStorageManager.getProfileForVaultNumber(legacyVault) == profile;
        int wardrobeVault = ownsLegacyWardrobe ? legacyVault : -1;
        int activeSet = ownsLegacyWardrobe
                ? WidgetConfigManager.getInt(CONFIG_ID, ACTIVE_SET_KEY, -1)
                : -1;

        WidgetConfigManager.setInt(CONFIG_ID, vaultKey, wardrobeVault, false);
        WidgetConfigManager.setInt(CONFIG_ID, profileKey(ACTIVE_SET_KEY, profile), activeSet, true);
    }

    private static void saveSavedSets(MinecraftClient client) {
        Path path = getSavedSetsPath(client);
        RegistryWrapper.WrapperLookup registries = getRegistries(client);
        if (path == null || registries == null || loadedSetsProfile < 1) {
            return;
        }

        PersistedWardrobeFile file = readPersistedWardrobeFile(path);
        if (file == null) {
            file = new PersistedWardrobeFile();
        }
        migrateLegacyPersistedProfile(file);
        if (file.profiles == null) {
            file.profiles = new java.util.TreeMap<>();
        }

        PersistedWardrobeProfile persistedProfile = new PersistedWardrobeProfile();
        persistedProfile.vaultNumber = loadedSetsVault;
        for (WardrobeSet set : savedSets) {
            PersistedWardrobeSet persistedSet = new PersistedWardrobeSet();
            persistedSet.label = set.customLabel;
            persistedSet.backgroundColor = set.backgroundColor;
            for (ArmorPart part : ArmorPart.values()) {
                ItemStack stack = set.stacks.getOrDefault(part, ItemStack.EMPTY);
                if (!stack.isEmpty()) {
                    persistedSet.pieces.put(part.name(), encodeStack(stack, registries));
                }
            }
            persistedProfile.sets.add(persistedSet);
        }
        file.profiles.put(Integer.toString(loadedSetsProfile), persistedProfile);
        file.vaultNumber = -1;
        file.sets = new ArrayList<>();

        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(file, SETS_FILE_TYPE, writer);
            }
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to save wardrobe sets.", e);
        }
    }

    private static PersistedWardrobeFile readPersistedWardrobeFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(path)) {
            return GSON.fromJson(reader, SETS_FILE_TYPE);
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to read wardrobe sets before saving.", e);
            return null;
        }
    }

    private static PersistedWardrobeProfile getPersistedProfile(
            PersistedWardrobeFile file,
            int profile
    ) {
        if (file == null) {
            return null;
        }
        if (file.profiles != null) {
            PersistedWardrobeProfile persistedProfile = file.profiles.get(Integer.toString(profile));
            if (persistedProfile != null) {
                return persistedProfile;
            }
        }
        if (file.vaultNumber > 0
                && VaultStorageManager.getProfileForVaultNumber(file.vaultNumber) == profile) {
            PersistedWardrobeProfile legacyProfile = new PersistedWardrobeProfile();
            legacyProfile.vaultNumber = file.vaultNumber;
            legacyProfile.sets = file.sets == null ? new ArrayList<>() : file.sets;
            return legacyProfile;
        }
        return null;
    }

    private static void migrateLegacyPersistedProfile(PersistedWardrobeFile file) {
        if (file.profiles == null) {
            file.profiles = new java.util.TreeMap<>();
        }
        if (file.vaultNumber < 1) {
            return;
        }

        int legacyProfile = VaultStorageManager.getProfileForVaultNumber(file.vaultNumber);
        String profileKey = Integer.toString(legacyProfile);
        if (!file.profiles.containsKey(profileKey)) {
            PersistedWardrobeProfile persistedProfile = new PersistedWardrobeProfile();
            persistedProfile.vaultNumber = file.vaultNumber;
            persistedProfile.sets = file.sets == null ? new ArrayList<>() : file.sets;
            file.profiles.put(profileKey, persistedProfile);
        }
    }

    private static List<WardrobeSet> decodeSavedSets(
            MinecraftClient client,
            List<PersistedWardrobeSet> persistedSets
    ) {
        RegistryWrapper.WrapperLookup registries = getRegistries(client);
        if (registries == null || persistedSets == null) {
            return List.of();
        }

        List<WardrobeSet> decoded = new ArrayList<>(persistedSets.size());
        for (PersistedWardrobeSet persistedSet : persistedSets) {
            EnumMap<ArmorPart, ItemStack> stacks = new EnumMap<>(ArmorPart.class);
            if (persistedSet != null && persistedSet.pieces != null) {
                for (ArmorPart part : ArmorPart.values()) {
                    ItemStack stack = decodeStack(persistedSet.pieces.get(part.name()), registries);
                    if (!stack.isEmpty()) {
                        stacks.put(part, stack);
                    }
                }
            }
            String customLabel = persistedSet == null || persistedSet.label == null
                    ? ""
                    : persistedSet.label.trim();
            int backgroundColor = persistedSet == null ? 0 : persistedSet.backgroundColor;
            decoded.add(new WardrobeSet(
                    stacks,
                    new EnumMap<>(ArmorPart.class),
                    customLabel,
                    backgroundColor
            ));
        }
        return List.copyOf(decoded);
    }

    private static String fitSetLabel(MinecraftClient client, String label, int maxWidth) {
        if (client.textRenderer.getWidth(label) <= maxWidth) {
            return label;
        }

        String suffix = "...";
        int end = label.length();
        while (end > 0 && client.textRenderer.getWidth(label.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return end == 0 ? suffix : label.substring(0, end) + suffix;
    }

    private static String encodeStack(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        DynamicOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        DataResult<NbtElement> result = ItemStack.CODEC.encodeStart(ops, stack);
        return result.resultOrPartial(error ->
                        LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to encode wardrobe item: {}", error))
                .map(NbtElement::toString)
                .orElse("");
    }

    private static ItemStack decodeStack(String encoded, RegistryWrapper.WrapperLookup registries) {
        if (encoded == null || encoded.isBlank()) {
            return ItemStack.EMPTY;
        }
        try {
            NbtElement element = StringNbtReader.readCompound(encoded);
            return ItemStack.CODEC.parse(registries.getOps(NbtOps.INSTANCE), element)
                    .resultOrPartial(error ->
                            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to decode wardrobe item: {}", error))
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to parse a saved wardrobe item.", e);
            return ItemStack.EMPTY;
        }
    }

    private static RegistryWrapper.WrapperLookup getRegistries(MinecraftClient client) {
        if (client == null) {
            return null;
        }
        if (client.world != null) {
            return client.world.getRegistryManager();
        }
        return client.getNetworkHandler() == null ? null : client.getNetworkHandler().getRegistryManager();
    }

    private static Path getSavedSetsPath(MinecraftClient client) {
        if (client == null || client.runDirectory == null) {
            return null;
        }
        return client.runDirectory.toPath().resolve("config").resolve(SETS_FILE);
    }

    private static void sendStatus(MinecraftClient client, String message) {
        status = message == null ? "" : message;
        if (client != null && client.player != null && !status.isBlank()) {
            client.player.sendMessage(Text.literal("[LegendsAddon] " + status), false);
        }
    }

    private enum ArmorPart {
        HEAD(EquipmentSlot.HEAD, "Helmet"),
        CHEST(EquipmentSlot.CHEST, "Chestplate"),
        LEGS(EquipmentSlot.LEGS, "Leggings"),
        FEET(EquipmentSlot.FEET, "Boots");

        private final EquipmentSlot slot;
        private final String label;

        ArmorPart(EquipmentSlot slot, String label) {
            this.slot = slot;
            this.label = label;
        }

        private static ArmorPart fromStack(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            if (stack.isOf(Items.PLAYER_HEAD) || stack.isIn(ItemTags.HEAD_ARMOR)) {
                return HEAD;
            }
            if (stack.isIn(ItemTags.CHEST_ARMOR)) {
                return CHEST;
            }
            if (stack.isIn(ItemTags.LEG_ARMOR)) {
                return LEGS;
            }
            if (stack.isIn(ItemTags.FOOT_ARMOR)) {
                return FEET;
            }
            return null;
        }
    }

    private interface WardrobeOperation {
        OperationResult tick(MinecraftClient client);

        String status();
    }

    private enum OperationResult {
        RUNNING,
        COMPLETE,
        FAILED
    }

    private static final class CreateOperation implements WardrobeOperation {
        private final HandledScreen<?> screen;
        private final int setIndex;
        private final List<CreateTask> tasks;
        private final Set<Integer> occupiedPhysicalSlots = new HashSet<>();
        private final Set<Integer> confirmedPhysicalSlots = new HashSet<>();
        private final EnumMap<ArmorPart, ItemStack> confirmedStacks = new EnumMap<>(ArmorPart.class);
        private int taskIndex;
        private int waitTicks;
        private int stableTicks;
        private int retryTicks;
        private String currentStatus;

        private CreateOperation(
                HandledScreen<?> screen,
                int setIndex,
                List<CreateTask> tasks
        ) {
            this.screen = screen;
            this.setIndex = setIndex;
            this.tasks = List.copyOf(tasks);
            this.currentStatus = "Saving wardrobe set " + (setIndex + 1) + "...";
            int containerSlotCount = getContainerSlotCount(screen);
            for (int physicalIndex = 0; physicalIndex < containerSlotCount; physicalIndex++) {
                if (screen.getScreenHandler().slots.get(physicalIndex).hasStack()) {
                    occupiedPhysicalSlots.add(physicalIndex);
                }
            }
        }

        @Override
        public OperationResult tick(MinecraftClient client) {
            if (client.player == null || client.interactionManager == null || client.currentScreen != screen) {
                currentStatus = "Set creation stopped because the wardrobe PV was closed.";
                return OperationResult.FAILED;
            }
            if (++waitTicks > ACTION_TIMEOUT_TICKS) {
                currentStatus = "Set creation timed out while waiting for the server.";
                return OperationResult.FAILED;
            }
            if (taskIndex >= tasks.size()) {
                if (++stableTicks < 5) {
                    return OperationResult.RUNNING;
                }
                currentStatus = "Created wardrobe set " + (setIndex + 1) + ".";
                return OperationResult.COMPLETE;
            }

            CreateTask task = tasks.get(taskIndex);
            Slot confirmedSlot = findNewContainerSlot(task.stack);
            if (confirmedSlot != null) {
                confirmedPhysicalSlots.add(confirmedSlot.getIndex());
                confirmedStacks.put(task.part, confirmedSlot.getStack().copy());
                taskIndex++;
                waitTicks = 0;
                stableTicks = 0;
                retryTicks = 0;
                return OperationResult.RUNNING;
            }

            Slot source = findPlayerInventorySlot(
                    screen,
                    client.player.getInventory(),
                    task.inventoryIndex
            );
            if (source == null || !sameWardrobeTransferItem(source.getStack(), task.stack)) {
                return OperationResult.RUNNING;
            }

            if (retryTicks <= 0) {
                clickQuickMove(client, screen, source.id);
                retryTicks = ACTION_RETRY_TICKS;
            } else {
                retryTicks--;
            }
            return OperationResult.RUNNING;
        }

        private Slot findNewContainerSlot(ItemStack expected) {
            int containerSlotCount = getContainerSlotCount(screen);
            for (int physicalIndex = 0; physicalIndex < containerSlotCount; physicalIndex++) {
                if (occupiedPhysicalSlots.contains(physicalIndex)
                        || confirmedPhysicalSlots.contains(physicalIndex)) {
                    continue;
                }
                Slot slot = screen.getScreenHandler().slots.get(physicalIndex);
                if (sameWardrobeTransferItem(slot.getStack(), expected)) {
                    return slot;
                }
            }
            return null;
        }

        private boolean hasConfirmedStacks() {
            return !confirmedStacks.isEmpty();
        }

        private WardrobeSet completedDefinition() {
            return new WardrobeSet(
                    copySetStacks(confirmedStacks),
                    new EnumMap<>(ArmorPart.class),
                    "",
                    0
            );
        }

        @Override
        public String status() {
            return currentStatus;
        }
    }

    private static final class SwapOperation implements WardrobeOperation {
        private final HandledScreen<?> initialScreen;
        private final int vaultNumber;
        private final int setIndex;
        private final List<SwapTask> tasks;
        private final int stagingHotbarIndex;
        private final boolean returnCurrentToWardrobe;
        private final boolean unequipSelectedSet;
        private final boolean reopenWardrobeAfterSwitch;
        private SwapPhase phase = SwapPhase.WITHDRAW;
        private int taskIndex;
        private int step;
        private int waitTicks;
        private int stableTicks;
        private int retryTicks;
        private int vaultOpenAttempts;
        private String currentStatus;

        private SwapOperation(
                HandledScreen<?> initialScreen,
                int vaultNumber,
                int setIndex,
                List<SwapTask> tasks,
                int stagingHotbarIndex,
                boolean returnCurrentToWardrobe,
                boolean unequipSelectedSet,
                boolean reopenWardrobeAfterSwitch
        ) {
            this.initialScreen = initialScreen;
            this.vaultNumber = vaultNumber;
            this.setIndex = setIndex;
            this.tasks = List.copyOf(tasks);
            this.stagingHotbarIndex = stagingHotbarIndex;
            this.returnCurrentToWardrobe = returnCurrentToWardrobe;
            this.unequipSelectedSet = unequipSelectedSet;
            this.reopenWardrobeAfterSwitch = reopenWardrobeAfterSwitch;
            this.currentStatus = unequipSelectedSet
                    ? "Unequipping wardrobe set " + (setIndex + 1) + "..."
                    : "Switching wardrobe set " + (setIndex + 1) + "...";
        }

        @Override
        public OperationResult tick(MinecraftClient client) {
            if (client.player == null || client.interactionManager == null || client.getNetworkHandler() == null) {
                currentStatus = "Wardrobe switch stopped because the player connection changed.";
                return OperationResult.FAILED;
            }

            if (++waitTicks > ACTION_TIMEOUT_TICKS) {
                currentStatus = getTimeoutStatus(client);
                return OperationResult.FAILED;
            }
            if (phase == SwapPhase.WAIT_VAULT && waitTicks > VAULT_OPEN_TIMEOUT_TICKS) {
                if (vaultOpenAttempts >= MAX_VAULT_OPEN_ATTEMPTS) {
                    currentStatus = getTimeoutStatus(client);
                    return OperationResult.FAILED;
                }
                client.setScreen(null);
                client.getNetworkHandler().sendChatCommand("pv " + vaultNumber);
                vaultOpenAttempts++;
                waitTicks = 0;
                stableTicks = 0;
            }

            return switch (phase) {
                case WITHDRAW -> withdraw(client);
                case WAIT_INVENTORY -> waitForInventory(client);
                case SWAP_ARMOR -> swapArmor(client);
                case WAIT_VAULT -> waitForVault(client);
                case STORE_PREVIOUS -> storePrevious(client);
            };
        }

        private OperationResult withdraw(MinecraftClient client) {
            if (client.currentScreen != initialScreen) {
                currentStatus = "Wardrobe switch stopped because the PV was closed.";
                return OperationResult.FAILED;
            }
            if (taskIndex >= tasks.size()) {
                if (++stableTicks < 5) {
                    return OperationResult.RUNNING;
                }
                taskIndex = 0;
                step = 0;
                waitTicks = 0;
                stableTicks = 0;
                client.player.closeHandledScreen();
                client.setScreen(new InventoryScreen(client.player));
                phase = SwapPhase.WAIT_INVENTORY;
                return OperationResult.RUNNING;
            }

            SwapTask task = tasks.get(taskIndex);
            if (task.target.isEmpty()) {
                finishTask();
                return OperationResult.RUNNING;
            }

            if (step == 0) {
                Slot target = findMatchingContainerSlot(
                        initialScreen,
                        task.targetPhysicalIndex,
                        task.target
                );
                if (target == null) {
                    return OperationResult.RUNNING;
                }

                task.withdrawPhysicalIndex = initialScreen.getScreenHandler().slots.indexOf(target);
                task.inventoryMatchesBeforeWithdraw = countMatchingPlayerInventory(
                        client.player.getInventory(),
                        task.target
                );
                clickQuickMove(client, initialScreen, target.id);
                step = 1;
                waitTicks = 0;
                stableTicks = 0;
                retryTicks = ACTION_RETRY_TICKS;
                return OperationResult.RUNNING;
            }

            int currentMatches = countMatchingPlayerInventory(
                    client.player.getInventory(),
                    task.target
            );
            if (currentMatches > task.inventoryMatchesBeforeWithdraw) {
                Slot work = findMatchingPlayerInventoryTransferSlot(
                        initialScreen,
                        client.player.getInventory(),
                        task.inventoryIndex,
                        -1,
                        task.target
                );
                if (work != null) {
                    task.inventoryIndex = work.getIndex();
                }
                finishTask();
                return OperationResult.RUNNING;
            }

            if (task.withdrawPhysicalIndex >= 0
                    && task.withdrawPhysicalIndex < getContainerSlotCount(initialScreen)) {
                Slot target = initialScreen.getScreenHandler().slots.get(task.withdrawPhysicalIndex);
                if (sameWardrobeTransferItem(target.getStack(), task.target)) {
                    retryQuickMove(client, initialScreen, target.id);
                }
            }
            return OperationResult.RUNNING;
        }

        private OperationResult waitForInventory(MinecraftClient client) {
            if (!(client.currentScreen instanceof InventoryScreen)) {
                return OperationResult.RUNNING;
            }
            taskIndex = 0;
            step = 0;
            waitTicks = 0;
            phase = SwapPhase.SWAP_ARMOR;
            return OperationResult.RUNNING;
        }

        private OperationResult swapArmor(MinecraftClient client) {
            if (!(client.currentScreen instanceof InventoryScreen inventoryScreen)) {
                currentStatus = "Wardrobe switch stopped because the inventory was closed.";
                return OperationResult.FAILED;
            }
            if (taskIndex >= tasks.size()) {
                if (!inventoryScreen.getScreenHandler().getCursorStack().isEmpty()) {
                    currentStatus = "Wardrobe switch paused with an item on the cursor. Place it in your inventory.";
                    return OperationResult.FAILED;
                }
                if (++stableTicks < 5) {
                    return OperationResult.RUNNING;
                }
                taskIndex = 0;
                step = 0;
                waitTicks = 0;
                stableTicks = 0;
                setActiveSet(
                        VaultStorageManager.getProfileForVaultNumber(vaultNumber),
                        unequipSelectedSet ? -1 : setIndex
                );
                if (!returnCurrentToWardrobe && !reopenWardrobeAfterSwitch) {
                    client.setScreen(null);
                    currentStatus = "Equipped wardrobe set " + (setIndex + 1)
                            + ". Previous armor was moved to your inventory.";
                    return OperationResult.COMPLETE;
                }
                client.setScreen(null);
                client.getNetworkHandler().sendChatCommand("pv " + vaultNumber);
                phase = SwapPhase.WAIT_VAULT;
                vaultOpenAttempts = 1;
                return OperationResult.RUNNING;
            }

            SwapTask task = tasks.get(taskIndex);
            Slot armor = findArmorSlot(inventoryScreen, client.player.getInventory(), task.part);
            if (armor == null) {
                currentStatus = "Wardrobe switch could not find the " + task.part.label.toLowerCase(Locale.ROOT) + " slot.";
                return OperationResult.FAILED;
            }

            if (!task.target.isEmpty()) {
                return equipTarget(client, inventoryScreen, armor, task);
            }
            return unequipCurrent(client, inventoryScreen, armor, task);
        }

        private OperationResult equipTarget(
                MinecraftClient client,
                InventoryScreen screen,
                Slot armor,
                SwapTask task
        ) {
            if (step == 0 && sameWardrobeTransferItem(armor.getStack(), task.target)) {
                finishTask();
                return OperationResult.RUNNING;
            }

            if (step == 0) {
                Slot source = findMatchingPlayerInventoryTransferSlot(
                        screen,
                        client.player.getInventory(),
                        task.inventoryIndex,
                        -1,
                        task.target
                );
                if (source == null) {
                    return OperationResult.RUNNING;
                }

                task.inventoryIndex = source.getIndex();
                task.swapSourceIndex = source.getIndex();
                if (source.getIndex() < PlayerInventory.getHotbarSize()) {
                    clickSwap(client, screen, armor.id, source.getIndex());
                    step = 3;
                } else {
                    if (!client.player.getInventory().getStack(stagingHotbarIndex).isEmpty()) {
                        return OperationResult.RUNNING;
                    }
                    clickSwap(client, screen, source.id, stagingHotbarIndex);
                    step = 1;
                }
                resetActionWait();
                return OperationResult.RUNNING;
            }

            ItemStack staging = client.player.getInventory().getStack(stagingHotbarIndex);
            if (step == 1 && sameWardrobeTransferItem(staging, task.target)) {
                clickSwap(client, screen, armor.id, stagingHotbarIndex);
                step = 2;
                resetActionWait();
                return OperationResult.RUNNING;
            }
            if (step == 2 && sameWardrobeTransferItem(armor.getStack(), task.target)) {
                Slot source = findPlayerInventorySlot(
                        screen,
                        client.player.getInventory(),
                        task.swapSourceIndex
                );
                if (staging.isEmpty()) {
                    step = 3;
                } else {
                    Slot parking = source != null && source.getStack().isEmpty()
                            ? source
                            : findAvailableEmptyWorkSlot(
                                    screen,
                                    client.player.getInventory(),
                                    task
                            );
                    if (parking == null) {
                        return OperationResult.RUNNING;
                    }
                    clickSwap(client, screen, parking.id, stagingHotbarIndex);
                    task.swapSourceIndex = parking.getIndex();
                    step = 3;
                    resetActionWait();
                }
                return OperationResult.RUNNING;
            }

            if (step == 3 && sameWardrobeTransferItem(armor.getStack(), task.target)) {
                staging = client.player.getInventory().getStack(stagingHotbarIndex);
                if (!staging.isEmpty()) {
                    Slot parking = findAvailableEmptyWorkSlot(
                            screen,
                            client.player.getInventory(),
                            task
                    );
                    if (parking != null) {
                        clickSwap(client, screen, parking.id, stagingHotbarIndex);
                        task.swapSourceIndex = parking.getIndex();
                        step = 4;
                        resetActionWait();
                    }
                    return OperationResult.RUNNING;
                }

                Slot previous = findPreviousArmorSlot(screen, client.player.getInventory(), task);
                if (task.current.isEmpty() || previous != null) {
                    if (previous != null) {
                        task.inventoryIndex = previous.getIndex();
                    }
                    finishTask();
                }
                return OperationResult.RUNNING;
            }

            if (step == 4
                    && client.player.getInventory().getStack(stagingHotbarIndex).isEmpty()) {
                Slot previous = findPreviousArmorSlot(screen, client.player.getInventory(), task);
                if (task.current.isEmpty() || previous != null) {
                    if (previous != null) {
                        task.inventoryIndex = previous.getIndex();
                    }
                    finishTask();
                }
            }
            return OperationResult.RUNNING;
        }

        private OperationResult unequipCurrent(
                MinecraftClient client,
                InventoryScreen screen,
                Slot armor,
                SwapTask task
        ) {
            if (task.current.isEmpty()) {
                finishTask();
                return OperationResult.RUNNING;
            }

            if (armor.getStack().isEmpty()) {
                Slot previous = findPreviousArmorSlot(screen, client.player.getInventory(), task);
                if (previous != null) {
                    task.inventoryIndex = previous.getIndex();
                    finishTask();
                }
                return OperationResult.RUNNING;
            }

            if (!sameWardrobeTransferItem(armor.getStack(), task.current)) {
                return OperationResult.RUNNING;
            }

            retryQuickMove(client, screen, armor.id);
            return OperationResult.RUNNING;
        }

        private OperationResult waitForVault(MinecraftClient client) {
            if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)
                    || extractVaultNumber(handledScreen.getTitle().getString()) != vaultNumber) {
                return OperationResult.RUNNING;
            }

            if (stagingHotbarIndex >= 0
                    && !client.player.getInventory().getStack(stagingHotbarIndex).isEmpty()) {
                stableTicks = 0;
                return OperationResult.RUNNING;
            }
            if (++stableTicks < 3) {
                return OperationResult.RUNNING;
            }

            if (!returnCurrentToWardrobe) {
                currentStatus = "Equipped wardrobe set " + (setIndex + 1)
                        + ". Previous armor was moved to your inventory.";
                return OperationResult.COMPLETE;
            }

            taskIndex = 0;
            step = 0;
            waitTicks = 0;
            phase = SwapPhase.STORE_PREVIOUS;
            return OperationResult.RUNNING;
        }

        private OperationResult storePrevious(MinecraftClient client) {
            if (!(client.currentScreen instanceof HandledScreen<?> handledScreen)
                    || extractVaultNumber(handledScreen.getTitle().getString()) != vaultNumber) {
                currentStatus = "Wardrobe switch stopped because the PV was closed.";
                return OperationResult.FAILED;
            }
            if (taskIndex >= tasks.size()) {
                currentStatus = unequipSelectedSet
                        ? "Unequipped wardrobe set " + (setIndex + 1) + "."
                        : "Equipped wardrobe set " + (setIndex + 1) + ".";
                if (!reopenWardrobeAfterSwitch && client.player != null) {
                    VaultStorageManager.refreshVisibleVaultSnapshot(handledScreen);
                    client.player.closeHandledScreen();
                }
                return OperationResult.COMPLETE;
            }

            SwapTask task = tasks.get(taskIndex);
            if (task.current.isEmpty()) {
                finishTask();
                return OperationResult.RUNNING;
            }

            Slot work = step == 0
                    ? findMatchingPlayerInventoryTransferSlot(
                            handledScreen,
                            client.player.getInventory(),
                            task.inventoryIndex,
                            -1,
                            task.current
                    )
                    : findPlayerInventorySlot(
                            handledScreen,
                            client.player.getInventory(),
                            task.storeSourceInventoryIndex
                    );
            if (work == null) {
                return OperationResult.RUNNING;
            }

            if (step == 0) {
                task.inventoryIndex = work.getIndex();
                task.storeSourceInventoryIndex = work.getIndex();
                clickQuickMove(client, handledScreen, work.id);
                step = 1;
                waitTicks = 0;
                stableTicks = 0;
                retryTicks = ACTION_RETRY_TICKS;
            } else if (!sameWardrobeTransferItem(work.getStack(), task.current)) {
                finishTask();
            } else {
                retryQuickMove(client, handledScreen, work.id);
            }
            return OperationResult.RUNNING;
        }

        private Slot findAvailableEmptyWorkSlot(
                HandledScreen<?> screen,
                PlayerInventory inventory,
                SwapTask currentTask
        ) {
            Slot preferred = findPlayerInventorySlot(
                    screen,
                    inventory,
                    currentTask.reservedInventoryIndex
            );
            if (preferred != null && preferred.getStack().isEmpty()) {
                return preferred;
            }

            Set<Integer> reservedIndices = new HashSet<>();
            for (SwapTask task : tasks) {
                if (task != currentTask) {
                    reservedIndices.add(task.reservedInventoryIndex);
                }
            }
            for (Slot slot : screen.getScreenHandler().slots) {
                int inventoryIndex = slot.getIndex();
                if (slot.inventory == inventory
                        && inventoryIndex >= PlayerInventory.getHotbarSize()
                        && inventoryIndex < PLAYER_INVENTORY_SLOTS
                        && !reservedIndices.contains(inventoryIndex)
                        && slot.getStack().isEmpty()) {
                    return slot;
                }
            }
            return null;
        }

        private Slot findPreviousArmorSlot(
                HandledScreen<?> screen,
                PlayerInventory inventory,
                SwapTask task
        ) {
            if (task.current.isEmpty()) {
                return null;
            }
            return findMatchingPlayerInventoryTransferSlot(
                    screen,
                    inventory,
                    task.swapSourceIndex >= 0 ? task.swapSourceIndex : task.inventoryIndex,
                    -1,
                    task.current
            );
        }

        private void retryQuickMove(
                MinecraftClient client,
                HandledScreen<?> screen,
                int slotId
        ) {
            if (retryTicks <= 0) {
                clickQuickMove(client, screen, slotId);
                retryTicks = ACTION_RETRY_TICKS;
            } else {
                retryTicks--;
            }
        }

        private void finishTask() {
            taskIndex++;
            step = 0;
            waitTicks = 0;
            stableTicks = 0;
            retryTicks = 0;
        }

        private void resetActionWait() {
            waitTicks = 0;
            stableTicks = 0;
            retryTicks = 0;
        }

        private String getTimeoutStatus(MinecraftClient client) {
            String taskLabel = taskIndex < tasks.size()
                    ? tasks.get(taskIndex).part.label.toLowerCase(Locale.ROOT)
                    : "transition";
            boolean cursorOccupied = client.currentScreen instanceof HandledScreen<?> handledScreen
                    && !handledScreen.getScreenHandler().getCursorStack().isEmpty();
            String recovery = cursorOccupied
                    ? " Place the cursor item in your inventory."
                    : " Items remain in the PV or inventory.";
            return "Wardrobe timed out during " + phase.label + " (" + taskLabel
                    + ", step " + step + ")." + recovery;
        }

        @Override
        public String status() {
            return currentStatus;
        }
    }

    private static void clickSwap(
            MinecraftClient client,
            HandledScreen<?> screen,
            int slotId,
            int hotbarIndex
    ) {
        client.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slotId,
                hotbarIndex,
                SlotActionType.SWAP,
                client.player
        );
    }

    private static void clickQuickMove(
            MinecraftClient client,
            HandledScreen<?> screen,
            int slotId
    ) {
        client.interactionManager.clickSlot(
                screen.getScreenHandler().syncId,
                slotId,
                0,
                SlotActionType.QUICK_MOVE,
                client.player
        );
    }

    private enum SwapPhase {
        WITHDRAW("PV withdrawal"),
        WAIT_INVENTORY("inventory opening"),
        SWAP_ARMOR("armor equip"),
        WAIT_VAULT("PV reopening"),
        STORE_PREVIOUS("previous armor storage");

        private final String label;

        SwapPhase(String label) {
            this.label = label;
        }
    }

    private static final class WardrobeUiState {
        private boolean rawView;
    }

    private static final class SelectionState {
        private final Screen screen;
        private final int targetSet;
        private final EnumMap<ArmorPart, Integer> selectedSlots = new EnumMap<>(ArmorPart.class);

        private SelectionState(Screen screen, int targetSet) {
            this.screen = screen;
            this.targetSet = targetSet;
        }
    }

    private record CreateTask(
            ArmorPart part,
            int inventoryIndex,
            ItemStack stack
    ) {
    }

    private static final class SwapTask {
        private final ArmorPart part;
        private final int targetPhysicalIndex;
        private int inventoryIndex;
        private int reservedInventoryIndex;
        private int swapSourceIndex = -1;
        private int withdrawPhysicalIndex = -1;
        private int inventoryMatchesBeforeWithdraw;
        private int storeSourceInventoryIndex = -1;
        private final ItemStack target;
        private ItemStack current;

        private SwapTask(
                ArmorPart part,
                int targetPhysicalIndex,
                int inventoryIndex,
                ItemStack target,
                ItemStack current
        ) {
            this.part = part;
            this.targetPhysicalIndex = targetPhysicalIndex;
            this.inventoryIndex = inventoryIndex;
            this.reservedInventoryIndex = inventoryIndex;
            this.target = target;
            this.current = current;
        }
    }

    private record WardrobeSet(
            EnumMap<ArmorPart, ItemStack> stacks,
            EnumMap<ArmorPart, Integer> physicalSlots,
            String customLabel,
            int backgroundColor
    ) {
        private static WardrobeSet empty() {
            return new WardrobeSet(
                    new EnumMap<>(ArmorPart.class),
                    new EnumMap<>(ArmorPart.class),
                    "",
                    0
            );
        }

        private boolean hasItems() {
            return !stacks.isEmpty();
        }

        private String displayName(int setIndex) {
            return customLabel == null || customLabel.isBlank()
                    ? "Set " + (setIndex + 1)
                    : customLabel;
        }
    }

    private static final class PersistedWardrobeFile {
        private int vaultNumber = -1;
        private List<PersistedWardrobeSet> sets = new ArrayList<>();
        private Map<String, PersistedWardrobeProfile> profiles = new java.util.TreeMap<>();
    }

    private static final class PersistedWardrobeProfile {
        private int vaultNumber = -1;
        private List<PersistedWardrobeSet> sets = new ArrayList<>();
    }

    private static final class PersistedWardrobeSet {
        private Map<String, String> pieces = new java.util.LinkedHashMap<>();
        private String label = "";
        private int backgroundColor;
    }

    private record WardrobeView(List<WardrobeSet> sets, String warning) {
        private boolean valid() {
            return warning == null || warning.isBlank();
        }
    }

    private record WardrobeBrowserLayout(
            int columns,
            int rows,
            int setCount,
            int cardsLeft,
            int cardsTop,
            int inventoryLeft,
            int inventoryTop,
            UiRect createButton,
            UiRect cancelButton,
            UiRect rawButton,
            UiRect unassignButton
    ) {
        private UiRect card(int setIndex) {
            int column = setIndex % columns;
            int row = setIndex / columns;
            return new UiRect(
                    cardsLeft + column * SET_PANEL_WIDTH,
                    cardsTop + row * SET_PANEL_HEIGHT,
                    SET_PANEL_WIDTH,
                    SET_PANEL_HEIGHT
            );
        }

        private UiRect wardrobePanel() {
            return new UiRect(
                    cardsLeft,
                    cardsTop,
                    columns * SET_PANEL_WIDTH,
                    rows * SET_PANEL_HEIGHT
            );
        }

        private boolean isLastColumn(int setIndex) {
            return setIndex % columns == columns - 1 || setIndex == setCount - 1;
        }

        private boolean isLastRow(int setIndex) {
            return setIndex / columns == rows - 1;
        }

        private UiRect setSlot(int setIndex, ArmorPart part) {
            UiRect card = card(setIndex);
            return new UiRect(
                    card.left + (SET_PANEL_WIDTH - SLOT_RENDER_SIZE) / 2,
                    card.top + 21 + part.ordinal() * SLOT_SIZE,
                    SLOT_RENDER_SIZE,
                    SLOT_RENDER_SIZE
            );
        }

        private UiRect setButton(int setIndex) {
            UiRect card = card(setIndex);
            return new UiRect(
                    card.left + (card.width - SET_SELECTOR_SIZE) / 2,
                    card.bottom() - SET_SELECTOR_SIZE - 6,
                    SET_SELECTOR_SIZE,
                    SET_SELECTOR_SIZE
            );
        }

        private UiRect inventoryPanel() {
            return new UiRect(inventoryLeft, inventoryTop, INVENTORY_PANEL_WIDTH, INVENTORY_PANEL_HEIGHT);
        }

        private UiRect inventorySlot(int inventoryIndex) {
            int row;
            int column;
            if (inventoryIndex < 9) {
                row = 3;
                column = inventoryIndex;
            } else {
                int mainIndex = inventoryIndex - 9;
                row = mainIndex / 9;
                column = mainIndex % 9;
            }
            return new UiRect(
                    inventoryLeft + 8 + column * SLOT_SIZE,
                    inventoryTop + 22 + row * SLOT_SIZE,
                    SLOT_RENDER_SIZE,
                    SLOT_RENDER_SIZE
            );
        }

        private UiRect rawReturnButton() {
            return createButton;
        }
    }

    private record UiRect(int left, int top, int width, int height) {
        private int right() {
            return left + width;
        }

        private int bottom() {
            return top + height;
        }

        private int centerX() {
            return left + width / 2;
        }

        private boolean contains(double x, double y) {
            return x >= left && x < right() && y >= top && y < bottom();
        }
    }
}
