package legends.ultra.cool.addons.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import legends.ultra.cool.addons.LegendsAddon;
import legends.ultra.cool.addons.data.AddonConfigPaths;
import legends.ultra.cool.addons.hud.widget.otherTypes.VaultBrowserWidget;
import legends.ultra.cool.addons.mixin.client.HandledScreenAccessor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.component.ComponentType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class VaultStorageManager {
    private static final String STORAGE_MENU_TITLE = "storage menu";
    private static final String VAULT_KEYWORD = "vault";
    private static final String VAULT_NAMES_FILE = "legendsaddon_vault_names.json";
    private static final String VAULT_SETTINGS_FILE = "legendsaddon_vault_settings.json";
    private static final String VAULT_SNAPSHOTS_FILE = "legendsaddon_vault_snapshots.json";
    private static final Pattern VAULT_NUMBER_PATTERN = Pattern.compile("(?:vault\\s*#?\\s*|/pv\\s+)(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PURCHASE_PRICE_PATTERN = Pattern.compile("buy\\s+this\\s+vault\\s+for\\s+(.+?\\bcoins?\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEGEND_LEVEL_PATTERN = Pattern.compile("requires\\s+legend\\s+level\\s+\\d+", Pattern.CASE_INSENSITIVE);
    private static final int PLAYER_INVENTORY_SLOTS = 36;
    private static final int VAULTS_PER_PROFILE = 14;
    private static final int PROFILE_COUNT = 5;
    private static final int DEFAULT_PROFILE = 1;
    private static final int VAULT_OPEN_TIMEOUT_TICKS = 50;
    private static final int VAULT_SNAPSHOT_DELAY_TICKS = 4;
    private static final int VAULT_UPDATE_STABLE_TICKS = 3;
    private static final int NEXT_COMMAND_DELAY_TICKS = 6;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type NAME_MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final Type SNAPSHOT_MAP_TYPE = new TypeToken<Map<String, PersistedVaultSnapshot>>() {}.getType();
    private static final Type SETTINGS_TYPE = new TypeToken<VaultRangeSettings>() {}.getType();

    private static final Map<Integer, VaultSnapshot> SNAPSHOTS = new TreeMap<>();
    private static final Map<Integer, String> CUSTOM_NAMES = new TreeMap<>();
    private static final Map<Integer, PersistedVaultSnapshot> PERSISTED_SNAPSHOTS = new TreeMap<>();
    private static final Map<Integer, VaultMenuEntry> VAULT_MENU_ENTRIES = new TreeMap<>();
    private static final Map<HandledScreen<?>, VaultBrowserScreen> BROWSER_OVERLAYS = new WeakHashMap<>();
    private static final java.util.Set<net.minecraft.client.gui.screen.Screen> BROWSER_EVENT_SCREENS =
            Collections.newSetFromMap(new WeakHashMap<>());

    private static boolean loadingAll;
    private static boolean customNamesLoaded;
    private static boolean persistedSnapshotsLoaded;
    private static boolean persistedSnapshotsHydrated;
    private static boolean rangeSettingsLoaded;
    private static int nextVaultToLoad = 1;
    private static int requestedVault = -1;
    private static int requestTicks;
    private static int cooldownTicks;
    private static int selectedProfile = DEFAULT_PROFILE;
    private static boolean browserOpenRequested;
    private static boolean browserOverlayRequested;
    private static String pendingBrowserStatusMessage = "";
    private static Object trackedVaultScreen;
    private static int trackedVaultNumber = -1;
    private static int trackedVaultOpenTicks;
    private static int trackedVaultStableTicks;
    private static boolean trackedVaultDirty;
    private static List<ItemStack> trackedVaultStacks = List.of();
    private static String lastStatus = "Idle";

    private VaultStorageManager() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(literal("pvmenu").executes(context -> {
                    MinecraftClient client = context.getSource().getClient();
                    if (!VaultBrowserWidget.isEnabledGlobal()) {
                        sendStatusMessage(client, "Vault Browser widget is disabled in the HUD editor.");
                        return 0;
                    }

                    requestBrowserOpen("Opened vault browser.");
                    return 1;
                })));

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof HandledScreen<?> handledScreen)) {
                return;
            }

            if (!VaultBrowserWidget.isEnabledGlobal()) {
                return;
            }

            if (isStorageMenuScreen(handledScreen)) {
                refreshStorageMenuEntries(handledScreen);
                addStorageMenuButtons(screen, handledScreen);
                installStorageMenuOverlayEvents(screen, handledScreen);
                if (browserOverlayRequested || shouldAutoOpenBrowserOnStorageMenu()) {
                    openBrowserOverlay(client, handledScreen);
                }
                return;
            }

            if (isVaultScreen(handledScreen, -1)) {
                addVaultScreenButtons(screen, handledScreen);
            }
        });
    }

    public static void tick(MinecraftClient client) {
        ensureRangeSettingsLoaded();
        ensureSnapshotsLoaded(client);

        if (client == null || client.player == null) {
            stopLoading("Waiting for player.");
            return;
        }

        if (!VaultBrowserWidget.isEnabledGlobal()) {
            if (loadingAll) {
                stopLoading("Vault Browser disabled.");
            } else {
                resetTrackedVault();
            }
            return;
        }

        if (browserOpenRequested) {
            if (client.currentScreen instanceof ChatScreen) {
                return;
            }

            browserOpenRequested = false;
            openBrowser(client);
            if (!pendingBrowserStatusMessage.isBlank()) {
                sendStatusMessage(client, pendingBrowserStatusMessage);
                pendingBrowserStatusMessage = "";
            }
        }

        HandledScreen<?> handledScreen = client.currentScreen instanceof HandledScreen<?> hs ? hs : null;
        processStorageMenu(handledScreen);
        processVisibleVaultScreen(client, handledScreen);

        if (!loadingAll) {
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        if (requestedVault == -1) {
            if (nextVaultToLoad > getSelectedRangeEnd()) {
                finishLoading(client, "Loaded vaults " + getRangeLabel() + ".");
                return;
            }

            sendVaultCommand(client, nextVaultToLoad);
            requestedVault = nextVaultToLoad;
            requestTicks = 0;
            lastStatus = "Opening vault " + requestedVault + "...";
            return;
        }

        requestTicks++;
        if (handledScreen != null && isVaultScreen(handledScreen, requestedVault)) {
            return;
        }

        if (requestTicks >= VAULT_OPEN_TIMEOUT_TICKS) {
            int failedVault = requestedVault;
            requestedVault = -1;
            loadingAll = false;
            nextVaultToLoad = failedVault;
            lastStatus = "Stopped at vault " + failedVault + ".";
            if (client.player != null) {
                client.player.sendMessage(Text.literal("[LegendsAddon] Stopped at vault " + failedVault + "."), false);
            }
            if (shouldAutoOpenBrowserAfterLoad()) {
                openBrowser(client);
            }
        }
    }

    public static List<VaultSnapshot> getSnapshots() {
        ensureRangeSettingsLoaded();
        return SNAPSHOTS.values().stream()
                .filter(snapshot -> isWithinSelectedRange(snapshot.vaultNumber()))
                .sorted(Comparator.comparingInt(VaultSnapshot::vaultNumber))
                .toList();
    }

    public static List<VaultBrowserEntry> getBrowserEntries() {
        Map<Integer, VaultBrowserEntry> entries = new TreeMap<>();
        for (VaultSnapshot snapshot : getSnapshots()) {
            entries.put(snapshot.vaultNumber(), new VaultBrowserEntry(
                    snapshot.vaultNumber(),
                    false,
                    snapshot,
                    ItemStack.EMPTY,
                    "",
                    ""
            ));
        }

        for (VaultMenuEntry menuEntry : VAULT_MENU_ENTRIES.values()) {
            if (!isWithinSelectedRange(menuEntry.vaultNumber())) {
                continue;
            }

            VaultBrowserEntry cachedEntry = entries.get(menuEntry.vaultNumber());
            if (menuEntry.locked()) {
                entries.put(menuEntry.vaultNumber(), new VaultBrowserEntry(
                        menuEntry.vaultNumber(),
                        true,
                        null,
                        menuEntry.icon().copy(),
                        menuEntry.price(),
                        menuEntry.legendLevelRequirement()
                ));
            } else if (cachedEntry == null) {
                entries.put(menuEntry.vaultNumber(), new VaultBrowserEntry(
                        menuEntry.vaultNumber(),
                        false,
                        null,
                        menuEntry.icon().copy(),
                        "",
                        ""
                ));
            }
        }

        return List.copyOf(entries.values());
    }

    public static void clearSnapshots() {
        ensureRangeSettingsLoaded();
        SNAPSHOTS.entrySet().removeIf(entry -> isWithinSelectedRange(entry.getKey()));
        persistedSnapshotsHydrated = true;
        saveSnapshots(MinecraftClient.getInstance());
        lastStatus = "Cleared range " + getRangeLabel() + ".";
    }

    public static List<Text> getLoreLines(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return List.of();
        }

        return lore.lines().stream()
                .map(line -> (Text) line.copy())
                .toList();
    }

    public static String getDisplayName(int vaultNumber) {
        String customName = getCustomName(vaultNumber);
        return customName.isBlank() ? getDefaultName(vaultNumber) : customName;
    }

    public static int getSelectedRangeStart() {
        ensureRangeSettingsLoaded();
        return getRangeStartForProfile(selectedProfile);
    }

    public static int getSelectedRangeEnd() {
        ensureRangeSettingsLoaded();
        return getRangeEndForProfile(selectedProfile);
    }

    public static int getSelectedProfile() {
        ensureRangeSettingsLoaded();
        return selectedProfile;
    }

    public static int getProfileCount() {
        return PROFILE_COUNT;
    }

    public static int getProfileForVaultNumber(int vaultNumber) {
        return getProfileForVault(vaultNumber);
    }

    public static String getSelectedProfileLabel() {
        return "Profile " + getSelectedProfile() + " (" + getRangeLabel() + ")";
    }

    public static String getRangeLabel() {
        return getSelectedRangeStart() + "-" + getSelectedRangeEnd();
    }

    public static boolean selectPreviousProfile() {
        return setSelectedProfile(selectedProfile - 1);
    }

    public static boolean selectNextProfile() {
        return setSelectedProfile(selectedProfile + 1);
    }

    public static boolean setSelectedProfile(int profile) {
        ensureRangeSettingsLoaded();
        int normalizedProfile = normalizeProfile(profile);
        if (normalizedProfile == selectedProfile) {
            return false;
        }

        selectedProfile = normalizedProfile;
        saveRangeSettings();
        lastStatus = "Selected " + getSelectedProfileLabel() + ".";
        return true;
    }

    public static String getDefaultName(int vaultNumber) {
        return "Vault #" + vaultNumber;
    }

    public static boolean hasCustomName(int vaultNumber) {
        ensureCustomNamesLoaded();
        return CUSTOM_NAMES.containsKey(vaultNumber);
    }

    public static String getCustomName(int vaultNumber) {
        ensureCustomNamesLoaded();
        return CUSTOM_NAMES.getOrDefault(vaultNumber, "");
    }

    public static void setCustomName(int vaultNumber, String customName) {
        if (vaultNumber < 1) {
            return;
        }

        ensureCustomNamesLoaded();
        String normalized = normalizeCustomName(customName);
        if (normalized.isBlank()) {
            CUSTOM_NAMES.remove(vaultNumber);
        } else {
            CUSTOM_NAMES.put(vaultNumber, normalized);
        }
        saveCustomNames();
    }

    public static void openBrowser(MinecraftClient client) {
        if (client == null
                || client.player == null
                || client.getNetworkHandler() == null
                || !VaultBrowserWidget.isEnabledGlobal()) {
            return;
        }

        ensureSnapshotsLoaded(client);
        if (client.currentScreen instanceof HandledScreen<?> handledScreen && isStorageMenuScreen(handledScreen)) {
            openBrowserOverlay(client, handledScreen);
            return;
        }

        browserOverlayRequested = true;
        lastStatus = "Opening Storage Menu...";
        client.getNetworkHandler().sendChatCommand("ec");
    }

    public static void openStorageMenu(MinecraftClient client) {
        if (client == null
                || client.player == null
                || client.getNetworkHandler() == null
                || !VaultBrowserWidget.isEnabledGlobal()) {
            return;
        }

        browserOverlayRequested = false;
        lastStatus = "Opening Storage Menu...";
        client.getNetworkHandler().sendChatCommand("ec");
    }

    public static boolean reloadCurrentRange(MinecraftClient client) {
        if (client == null || client.player == null || client.getNetworkHandler() == null || !VaultBrowserWidget.isEnabledGlobal()) {
            return false;
        }

        client.setScreen(null);
        startLoadAll(client);
        return true;
    }

    public static boolean openVault(MinecraftClient client, int vaultNumber) {
        if (client == null || client.player == null || client.getNetworkHandler() == null || vaultNumber < 1 || !VaultBrowserWidget.isEnabledGlobal()) {
            return false;
        }

        if (loadingAll) {
            stopLoading("Load cancelled.");
        }

        lastStatus = "Opening vault " + vaultNumber + "...";
        resetTrackedVault();
        client.setScreen(null);
        client.getNetworkHandler().sendChatCommand("pv " + vaultNumber);
        return true;
    }

    public static boolean shouldHideJeiOverlay(Screen screen) {
        if (!VaultBrowserWidget.isEnabledGlobal()) {
            return false;
        }

        if (screen instanceof VaultBrowserScreen || screen instanceof VaultRenameScreen) {
            return true;
        }

        return screen instanceof HandledScreen<?> handledScreen
                && (isStorageMenuScreen(handledScreen) || isVaultScreen(handledScreen, -1));
    }

    public static boolean clickStorageMenuVault(
            MinecraftClient client,
            HandledScreen<?> storageMenuScreen,
            int vaultNumber
    ) {
        if (client == null
                || client.player == null
                || client.interactionManager == null
                || vaultNumber < 1
                || storageMenuScreen == null
                || client.currentScreen != storageMenuScreen
                || !isStorageMenuScreen(storageMenuScreen)
                || !VaultBrowserWidget.isEnabledGlobal()) {
            return false;
        }

        Slot targetSlot = findVaultMenuSlot(storageMenuScreen, vaultNumber);
        if (targetSlot == null) {
            lastStatus = "Could not find vault " + vaultNumber + " in the Storage Menu.";
            sendStatusMessage(client, lastStatus);
            return false;
        }

        client.interactionManager.clickSlot(
                storageMenuScreen.getScreenHandler().syncId,
                targetSlot.id,
                0,
                SlotActionType.PICKUP,
                client.player
        );
        lastStatus = "Clicked vault " + vaultNumber + " in the Storage Menu.";
        return true;
    }

    public static void closeBrowserOverlay(HandledScreen<?> storageMenuScreen) {
        if (storageMenuScreen != null) {
            BROWSER_OVERLAYS.remove(storageMenuScreen);
        }
    }

    public static boolean hasBrowserOverlay(HandledScreen<?> handledScreen) {
        return handledScreen != null && BROWSER_OVERLAYS.containsKey(handledScreen);
    }

    public static void requestBrowserOverlay() {
        browserOverlayRequested = true;
    }

    public static boolean shouldAutoOpenBrowserOnStorageMenu() {
        return VaultBrowserWidget.shouldAutoOpenFromStorageMenu();
    }

    public static boolean shouldAutoOpenBrowserAfterLoad() {
        return VaultBrowserWidget.shouldAutoOpenAfterLoad();
    }

    public static boolean shouldShowBrowserHint() {
        return VaultBrowserWidget.shouldShowBrowserHint();
    }

    public static boolean shouldAlwaysShowVaultNumber() {
        return VaultBrowserWidget.shouldAlwaysShowVaultNumber();
    }

    private static void addStorageMenuButtons(net.minecraft.client.gui.screen.Screen screen, HandledScreen<?> handledScreen) {
        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        int x = accessor.legends$getX();
        int y = accessor.legends$getY() - 24;
        int width = accessor.legends$getBackgroundWidth();

        ButtonWidget profileLabelButton = ButtonWidget.builder(Text.literal("Profile " + getSelectedProfile()), button -> {
                })
                .dimensions(x, y - 24, width/2 - 2, 20)
                .build();
        profileLabelButton.active = false;

        ButtonWidget rangeLabelButton = ButtonWidget.builder(Text.literal(getRangeLabel()), button -> {
                })
                .dimensions(x + width - 20 - 36 - 5, y - 24, 36, 20)
                .build();
        rangeLabelButton.active = false;

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("Load All"), button -> {
                    if (!loadingAll) {
                        startLoadAll(Screens.getClient(screen));
                    }
                })
                .dimensions(x, y, width/2 - 2, 20)
                .build());

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("View Loaded"), button -> openBrowser(Screens.getClient(screen)))
                .dimensions(x + width - (width/2 - 2), y, width/2 - 2, 20)
                .build());

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("<"), button -> {
                    if (selectPreviousProfile()) {
                        rangeLabelButton.setMessage(Text.literal(getRangeLabel()));
                        profileLabelButton.setMessage(Text.literal("Profile " + getSelectedProfile()));

                    }
                })
                .dimensions(x + width - 40 - rangeLabelButton.getWidth() - 10, y - 24, 20, 20)
                .build());

        Screens.getButtons(screen).add(rangeLabelButton);
        Screens.getButtons(screen).add(profileLabelButton);

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal(">"), button -> {
                    if (selectNextProfile()) {
                        rangeLabelButton.setMessage(Text.literal(getRangeLabel()));
                        profileLabelButton.setMessage(Text.literal("Profile " + getSelectedProfile()));
                    }
                })
                .dimensions(x + width - 20, y - 24, 20, 20)
                .build());

    }

    private static void addVaultScreenButtons(net.minecraft.client.gui.screen.Screen screen, HandledScreen<?> handledScreen) {
        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        int x = accessor.legends$getX();
        int y = accessor.legends$getY() - 24;
        int width = accessor.legends$getBackgroundWidth();

        Screens.getButtons(screen).add(ButtonWidget.builder(Text.literal("PV Menu"), button ->
                        returnToBrowser(Screens.getClient(screen), handledScreen))
                .dimensions(x, y, width, 20)
                .build());
    }

    private static void renderStorageMenuStatus(HandledScreen<?> handledScreen, DrawContext context) {
        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        int x = accessor.legends$getX() + accessor.legends$getBackgroundWidth() + 6;
        int y = accessor.legends$getY() + 100;

        int loadedCount = getSnapshots().size();
        int unlockedCount = countUnlockedVaultIcons(handledScreen);

        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Loaded: " + loadedCount), x, y, 0xB8E0B8);
        if (unlockedCount > 0) {
            context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Shown: " + unlockedCount), x, y + 12, 0xD0D0D0);
        }
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Profile: " + getSelectedProfile() + "/" + PROFILE_COUNT), x, y + 24, 0xD8C090);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal("Range: " + getRangeLabel()), x, y + 36, 0xD8C090);
        context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, Text.literal(lastStatus), x, y + 48, 0xE0C090);
    }

    private static void installStorageMenuOverlayEvents(
            net.minecraft.client.gui.screen.Screen screen,
            HandledScreen<?> handledScreen
    ) {
        if (!BROWSER_EVENT_SCREENS.add(screen)) {
            VaultBrowserScreen overlay = BROWSER_OVERLAYS.get(handledScreen);
            if (overlay != null) {
                overlay.init(screen.width, screen.height);
            }
            return;
        }

        ScreenMouseEvents.allowMouseClick(screen).register((scr, click) -> {
            VaultBrowserScreen overlay = BROWSER_OVERLAYS.get(handledScreen);
            if (overlay == null) {
                return true;
            }
            overlay.mouseClicked(click, false);
            return false;
        });

        ScreenMouseEvents.allowMouseScroll(screen).register((scr, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
            VaultBrowserScreen overlay = BROWSER_OVERLAYS.get(handledScreen);
            if (overlay == null) {
                return true;
            }
            overlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            return false;
        });

        ScreenKeyboardEvents.allowKeyPress(screen).register((scr, input) -> {
            VaultBrowserScreen overlay = BROWSER_OVERLAYS.get(handledScreen);
            if (overlay == null) {
                return true;
            }

            boolean handled = overlay.keyPressed(input);
            return !handled && !overlay.capturesKeyboard();
        });

        ScreenMouseEvents.allowMouseRelease(screen).register((scr, click) ->
                BROWSER_OVERLAYS.get(handledScreen) == null
        );

        ScreenMouseEvents.allowMouseDrag(screen).register((scr, click, mouseX, mouseY) ->
                BROWSER_OVERLAYS.get(handledScreen) == null
        );

        ScreenEvents.afterRender(screen).register((scr, context, mouseX, mouseY, delta) -> {
            VaultBrowserScreen overlay = BROWSER_OVERLAYS.get(handledScreen);
            if (overlay != null) {
                overlay.render(context, mouseX, mouseY, delta);
            } else {
                renderStorageMenuStatus(handledScreen, context);
            }
        });

        ScreenEvents.remove(screen).register(scr -> {
            BROWSER_OVERLAYS.remove(handledScreen);
            BROWSER_EVENT_SCREENS.remove(scr);
        });
    }

    private static void openBrowserOverlay(MinecraftClient client, HandledScreen<?> handledScreen) {
        if (client == null || handledScreen == null || !isStorageMenuScreen(handledScreen)) {
            return;
        }

        browserOverlayRequested = false;
        refreshStorageMenuEntries(handledScreen);
        VaultBrowserScreen overlay = new VaultBrowserScreen(handledScreen);
        overlay.init(handledScreen.width, handledScreen.height);
        BROWSER_OVERLAYS.put(handledScreen, overlay);
    }

    private static void processStorageMenu(HandledScreen<?> handledScreen) {
        if (handledScreen != null && isStorageMenuScreen(handledScreen)) {
            refreshStorageMenuEntries(handledScreen);
        }
    }

    private static boolean refreshStorageMenuEntries(HandledScreen<?> handledScreen) {
        List<Slot> slots = handledScreen.getScreenHandler().slots;
        int containerSlotCount = Math.max(0, slots.size() - PLAYER_INVENTORY_SLOTS);
        List<VaultMenuEntry> discoveredEntries = new ArrayList<>();

        for (int i = 0; i < containerSlotCount; i++) {
            VaultMenuEntry menuEntry = inspectVaultMenuItem(slots.get(i).getStack());
            if (menuEntry != null) {
                discoveredEntries.add(menuEntry);
            }
        }

        if (discoveredEntries.isEmpty()) {
            return false;
        }

        int rangeStart = getSelectedRangeStart();
        int rangeEnd = getSelectedRangeEnd();
        VAULT_MENU_ENTRIES.entrySet().removeIf(entry ->
                entry.getKey() >= rangeStart && entry.getKey() <= rangeEnd);
        for (VaultMenuEntry entry : discoveredEntries) {
            VAULT_MENU_ENTRIES.put(entry.vaultNumber(), entry);
        }
        return true;
    }

    private static Slot findVaultMenuSlot(HandledScreen<?> handledScreen, int vaultNumber) {
        List<Slot> slots = handledScreen.getScreenHandler().slots;
        int containerSlotCount = Math.max(0, slots.size() - PLAYER_INVENTORY_SLOTS);

        for (int i = 0; i < containerSlotCount; i++) {
            Slot slot = slots.get(i);
            VaultMenuEntry menuEntry = inspectVaultMenuItem(slot.getStack());
            if (menuEntry != null && menuEntry.vaultNumber() == vaultNumber) {
                return slot;
            }
        }

        return null;
    }

    private static VaultMenuEntry inspectVaultMenuItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        int displayedVaultNumber = extractVaultNumber(stack.getName().getString());
        if (displayedVaultNumber <= 0) {
            displayedVaultNumber = extractVaultNumber(readLoreText(stack));
        }
        if (displayedVaultNumber <= 0) {
            return null;
        }

        int vaultNumber = resolveStorageMenuVaultNumber(displayedVaultNumber);
        String price = extractPurchasePrice(stack);
        String legendLevelRequirement = extractLegendLevelRequirement(stack);
        boolean unlocked = stack.isOf(Items.BARREL) || isUnlockedVaultIcon(stack);
        boolean locked = !unlocked && (stack.isOf(Items.CHEST) || !price.isBlank());
        if (!unlocked && !locked) {
            return null;
        }

        return new VaultMenuEntry(vaultNumber, locked, stack.copy(), price, legendLevelRequirement);
    }

    private static int resolveStorageMenuVaultNumber(int displayedVaultNumber) {
        if (displayedVaultNumber >= 1 && displayedVaultNumber <= VAULTS_PER_PROFILE) {
            return getSelectedRangeStart() + displayedVaultNumber - 1;
        }
        return displayedVaultNumber;
    }

    private static String extractPurchasePrice(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return "";
        }

        for (Text line : lore.lines()) {
            Matcher matcher = PURCHASE_PRICE_PATTERN.matcher(line.getString());
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }

        return "";
    }

    private static String extractLegendLevelRequirement(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return "";
        }

        for (Text line : lore.lines()) {
            String text = line.getString().trim();
            if (LEGEND_LEVEL_PATTERN.matcher(text).find()) {
                return text;
            }
        }

        return "";
    }

    private static void processVisibleVaultScreen(MinecraftClient client, HandledScreen<?> handledScreen) {
        if (handledScreen == null || !isVaultScreen(handledScreen, -1)) {
            resetTrackedVault();
            return;
        }

        int vaultNumber = extractVaultNumber(handledScreen.getTitle().getString());
        if (vaultNumber < 1) {
            return;
        }

        List<ItemStack> currentStacks = readVaultContents(handledScreen);
        Object currentScreen = client.currentScreen;
        if (currentScreen != trackedVaultScreen || vaultNumber != trackedVaultNumber) {
            trackedVaultScreen = currentScreen;
            trackedVaultNumber = vaultNumber;
            trackedVaultOpenTicks = 0;
            trackedVaultStableTicks = 0;
            trackedVaultDirty = true;
            trackedVaultStacks = currentStacks;
        } else if (!areStackListsEqual(currentStacks, trackedVaultStacks)) {
            trackedVaultStacks = currentStacks;
            trackedVaultStableTicks = 0;
            trackedVaultDirty = true;
            trackedVaultOpenTicks++;
            return;
        } else {
            trackedVaultStableTicks++;
        }

        trackedVaultOpenTicks++;
        if (trackedVaultDirty
                && trackedVaultOpenTicks >= VAULT_SNAPSHOT_DELAY_TICKS
                && trackedVaultStableTicks >= VAULT_UPDATE_STABLE_TICKS) {
            snapshotVault(vaultNumber, trackedVaultStacks);
            trackedVaultDirty = false;
            lastStatus = "Cached vault " + vaultNumber + ".";

            if (loadingAll && requestedVault == vaultNumber) {
                requestedVault = -1;
                nextVaultToLoad = vaultNumber + 1;
                cooldownTicks = NEXT_COMMAND_DELAY_TICKS;
                if (client.player != null) {
                    client.player.closeHandledScreen();
                    client.player.sendMessage(Text.literal("[LegendsAddon] Cached vault " + vaultNumber + "."), false);
                }
            }
        }
    }

    private static void snapshotVault(int vaultNumber, List<ItemStack> stacks) {
        int rows = Math.max(1, (int) Math.ceil(stacks.size() / 9.0D));
        SNAPSHOTS.put(vaultNumber, new VaultSnapshot(vaultNumber, rows, copyStacks(stacks)));
        persistedSnapshotsHydrated = true;
        saveSnapshots(MinecraftClient.getInstance());
    }

    public static void refreshVisibleVaultSnapshot(HandledScreen<?> handledScreen) {
        if (handledScreen == null || !isVaultScreen(handledScreen, -1)) {
            return;
        }

        int vaultNumber = extractVaultNumber(handledScreen.getTitle().getString());
        if (vaultNumber < 1) {
            return;
        }

        List<ItemStack> stacks = readVaultContents(handledScreen);
        ensureSnapshotsLoaded(MinecraftClient.getInstance());
        trackedVaultScreen = handledScreen;
        trackedVaultStacks = stacks;
        trackedVaultNumber = vaultNumber;
        trackedVaultOpenTicks = VAULT_SNAPSHOT_DELAY_TICKS;
        trackedVaultStableTicks = VAULT_UPDATE_STABLE_TICKS;
        trackedVaultDirty = false;
        snapshotVault(vaultNumber, stacks);
        lastStatus = "Cached vault " + vaultNumber + ".";
    }

    private static void startLoadAll(MinecraftClient client) {
        if (client == null || client.player == null) {
            return;
        }

        ensureRangeSettingsLoaded();
        ensureSnapshotsLoaded(client);
        SNAPSHOTS.entrySet().removeIf(entry -> isWithinSelectedRange(entry.getKey()));
        persistedSnapshotsHydrated = true;
        saveSnapshots(client);
        loadingAll = true;
        nextVaultToLoad = getSelectedRangeStart();
        requestedVault = -1;
        requestTicks = 0;
        cooldownTicks = 0;
        trackedVaultScreen = null;
        resetTrackedVault();
        lastStatus = "Loading vaults...";
        client.player.sendMessage(Text.literal("[LegendsAddon] Loading vaults " + getRangeLabel() + "."), false);
    }

    private static void finishLoading(MinecraftClient client, String message) {
        loadingAll = false;
        requestedVault = -1;
        nextVaultToLoad = 1;
        requestTicks = 0;
        cooldownTicks = 0;
        lastStatus = message;
        if (client.player != null) {
            client.player.sendMessage(Text.literal("[LegendsAddon] " + message), false);
        }
        if (shouldAutoOpenBrowserAfterLoad()) {
            openBrowser(client);
        }
    }

    private static void stopLoading(String message) {
        loadingAll = false;
        requestedVault = -1;
        requestTicks = 0;
        cooldownTicks = 0;
        resetTrackedVault();
        lastStatus = message;
    }

    private static void resetTrackedVault() {
        trackedVaultScreen = null;
        trackedVaultNumber = -1;
        trackedVaultOpenTicks = 0;
        trackedVaultStableTicks = 0;
        trackedVaultDirty = false;
        trackedVaultStacks = List.of();
    }

    private static void sendVaultCommand(MinecraftClient client, int vaultNumber) {
        if (client.getNetworkHandler() == null) {
            stopLoading("No network handler.");
            return;
        }

        client.getNetworkHandler().sendChatCommand("pv " + vaultNumber);
    }

    private static void returnToBrowser(MinecraftClient client, HandledScreen<?> handledScreen) {
        if (client == null || !VaultBrowserWidget.isEnabledGlobal()) {
            return;
        }

        if (loadingAll) {
            stopLoading("Load cancelled.");
        }

        refreshVisibleVaultSnapshot(handledScreen);
        openBrowser(client);
    }

    private static void sendStatusMessage(MinecraftClient client, String message) {
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.literal("[LegendsAddon] " + message), false);
        }
    }

    private static void requestBrowserOpen(String statusMessage) {
        browserOpenRequested = true;
        pendingBrowserStatusMessage = statusMessage == null ? "" : statusMessage;
    }

    private static boolean isStorageMenuScreen(HandledScreen<?> handledScreen) {
        return handledScreen.getTitle().getString().toLowerCase(Locale.ROOT).contains(STORAGE_MENU_TITLE);
    }

    private static boolean isVaultScreen(HandledScreen<?> handledScreen, int expectedVault) {
        String title = handledScreen.getTitle().getString().toLowerCase(Locale.ROOT);
        if (title.contains(STORAGE_MENU_TITLE) || !title.contains(VAULT_KEYWORD)) {
            return false;
        }

        int parsed = extractVaultNumber(title);
        if (expectedVault <= 0) {
            return parsed > 0;
        }

        return parsed == expectedVault;
    }

    private static int countUnlockedVaultIcons(HandledScreen<?> handledScreen) {
        int unlocked = 0;
        List<Slot> slots = handledScreen.getScreenHandler().slots;
        int containerSlotCount = Math.max(0, slots.size() - PLAYER_INVENTORY_SLOTS);

        for (int i = 0; i < containerSlotCount; i++) {
            if (isUnlockedVaultIcon(slots.get(i).getStack())) {
                unlocked++;
            }
        }

        return unlocked;
    }

    private static boolean isUnlockedVaultIcon(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (stack.isOf(Items.BARREL)) {
            return true;
        }

        int vaultNumber = extractVaultNumber(stack.getName().getString());
        if (vaultNumber <= 0) {
            vaultNumber = extractVaultNumber(readLoreText(stack));
        }

        if (vaultNumber <= 0) {
            return false;
        }

        String combined = (stack.getName().getString() + " " + readLoreText(stack)).toLowerCase(Locale.ROOT);
        return combined.contains("/pv " + vaultNumber) || combined.contains("click to open");
    }

    private static List<ItemStack> readVaultContents(HandledScreen<?> handledScreen) {
        List<Slot> slots = handledScreen.getScreenHandler().slots;
        int containerSlotCount = Math.max(0, slots.size() - PLAYER_INVENTORY_SLOTS);
        List<ItemStack> stacks = new ArrayList<>(containerSlotCount);

        for (int i = 0; i < containerSlotCount; i++) {
            stacks.add(slots.get(i).getStack().copy());
        }

        return stacks;
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        return List.copyOf(copy);
    }

    private static boolean areStackListsEqual(List<ItemStack> left, List<ItemStack> right) {
        if (left.size() != right.size()) {
            return false;
        }

        for (int i = 0; i < left.size(); i++) {
            if (!ItemStack.areEqual(left.get(i), right.get(i))) {
                return false;
            }
        }

        return true;
    }

    private static String readLoreText(ItemStack stack) {
        LoreComponent lore = stack.get(DataComponentTypes.LORE);
        if (lore == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (Text line : lore.lines()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(line.getString());
        }
        return builder.toString();
    }

    private static int extractVaultNumber(String text) {
        if (text == null || text.isBlank()) {
            return -1;
        }

        Matcher matcher = VAULT_NUMBER_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }

        return -1;
    }

    private static void ensureCustomNamesLoaded() {
        if (customNamesLoaded) {
            return;
        }

        customNamesLoaded = true;
        CUSTOM_NAMES.clear();

        Path path = getCustomNamesPath();
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, String> storedNames = GSON.fromJson(reader, NAME_MAP_TYPE);
            if (storedNames == null) {
                return;
            }

            for (Map.Entry<String, String> entry : storedNames.entrySet()) {
                if (entry == null) {
                    continue;
                }

                try {
                    int vaultNumber = Integer.parseInt(entry.getKey());
                    String normalizedName = normalizeCustomName(entry.getValue());
                    if (vaultNumber > 0 && !normalizedName.isBlank()) {
                        CUSTOM_NAMES.put(vaultNumber, normalizedName);
                    }
                } catch (NumberFormatException ignored) {
                    LegendsAddon.LOGGER.warn("[LegendsAddon] Ignoring invalid vault name key '{}'.", entry.getKey());
                }
            }
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to load vault names.", e);
        }
    }

    private static void saveCustomNames() {
        Path path = getCustomNamesPath();
        if (path == null) {
            return;
        }

        Map<String, String> storedNames = new TreeMap<>();
        for (Map.Entry<Integer, String> entry : CUSTOM_NAMES.entrySet()) {
            if (entry == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            storedNames.put(Integer.toString(entry.getKey()), entry.getValue());
        }

        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to create vault name config directory.", e);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(storedNames, NAME_MAP_TYPE, writer);
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to save vault names.", e);
        }
    }

    private static Path getCustomNamesPath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return null;
        }
        return AddonConfigPaths.configFile(client, VAULT_NAMES_FILE);
    }

    private static void ensureRangeSettingsLoaded() {
        if (rangeSettingsLoaded) {
            return;
        }

        rangeSettingsLoaded = true;
        selectedProfile = DEFAULT_PROFILE;

        Path path = getRangeSettingsPath();
        if (path == null || !Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            VaultRangeSettings settings = GSON.fromJson(reader, SETTINGS_TYPE);
            if (settings == null) {
                return;
            }

            if (settings.profile > 0) {
                selectedProfile = normalizeProfile(settings.profile);
            } else {
                int start = Math.max(1, settings.start);
                selectedProfile = getProfileForVault(start);
            }

        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to load vault range settings.", e);
        }
    }

    private static void saveRangeSettings() {
        Path path = getRangeSettingsPath();
        if (path == null) {
            return;
        }

        VaultRangeSettings settings = new VaultRangeSettings();
        settings.profile = selectedProfile;
        settings.start = getSelectedRangeStart();
        settings.end = getSelectedRangeEnd();

        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to create vault range config directory.", e);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(settings, SETTINGS_TYPE, writer);
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to save vault range settings.", e);
        }
    }

    private static Path getRangeSettingsPath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return null;
        }
        return AddonConfigPaths.configFile(client, VAULT_SETTINGS_FILE);
    }

    private static String normalizeCustomName(String customName) {
        if (customName == null) {
            return "";
        }

        String trimmed = customName.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 30 ? trimmed.substring(0, 30).trim() : trimmed;
    }

    private static void ensureSnapshotsLoaded(MinecraftClient client) {
        ensurePersistedSnapshotsLoaded();
        if (persistedSnapshotsHydrated || PERSISTED_SNAPSHOTS.isEmpty()) {
            return;
        }

        RegistryWrapper.WrapperLookup registries = getRegistries(client);
        if (registries == null) {
            return;
        }

        SNAPSHOTS.clear();
        for (Map.Entry<Integer, PersistedVaultSnapshot> entry : PERSISTED_SNAPSHOTS.entrySet()) {
            PersistedVaultSnapshot persistedSnapshot = entry.getValue();
            if (persistedSnapshot == null) {
                continue;
            }

            List<ItemStack> stacks = decodeStacks(persistedSnapshot.stacks, registries, entry.getKey());
            int rows = persistedSnapshot.rows > 0 ? persistedSnapshot.rows : Math.max(1, (int) Math.ceil(stacks.size() / 9.0D));
            SNAPSHOTS.put(entry.getKey(), new VaultSnapshot(entry.getKey(), rows, List.copyOf(stacks)));
        }

        persistedSnapshotsHydrated = true;
        if (!SNAPSHOTS.isEmpty()) {
            lastStatus = "Loaded cached vaults.";
        }
    }

    private static void ensurePersistedSnapshotsLoaded() {
        if (persistedSnapshotsLoaded) {
            return;
        }

        persistedSnapshotsLoaded = true;
        PERSISTED_SNAPSHOTS.clear();

        Path path = getSnapshotsPath();
        if (path == null || !Files.exists(path)) {
            persistedSnapshotsHydrated = true;
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, PersistedVaultSnapshot> storedSnapshots = GSON.fromJson(reader, SNAPSHOT_MAP_TYPE);
            if (storedSnapshots == null) {
                persistedSnapshotsHydrated = true;
                return;
            }

            for (Map.Entry<String, PersistedVaultSnapshot> entry : storedSnapshots.entrySet()) {
                if (entry == null || entry.getValue() == null) {
                    continue;
                }

                try {
                    int vaultNumber = Integer.parseInt(entry.getKey());
                    if (vaultNumber > 0) {
                        PersistedVaultSnapshot snapshot = entry.getValue();
                        if (snapshot.stacks == null) {
                            snapshot.stacks = new ArrayList<>();
                        }
                        PERSISTED_SNAPSHOTS.put(vaultNumber, snapshot);
                    }
                } catch (NumberFormatException ignored) {
                    LegendsAddon.LOGGER.warn("[LegendsAddon] Ignoring invalid persisted vault key '{}'.", entry.getKey());
                }
            }
        } catch (Exception e) {
            persistedSnapshotsHydrated = true;
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to load cached vault snapshots.", e);
        }
    }

    private static void saveSnapshots(MinecraftClient client) {
        Path path = getSnapshotsPath();
        if (path == null) {
            return;
        }

        if (!SNAPSHOTS.isEmpty()) {
            RegistryWrapper.WrapperLookup registries = getRegistries(client);
            if (registries == null) {
                return;
            }

            PERSISTED_SNAPSHOTS.clear();
            for (VaultSnapshot snapshot : SNAPSHOTS.values()) {
                PersistedVaultSnapshot persistedSnapshot = new PersistedVaultSnapshot();
                persistedSnapshot.rows = snapshot.rows();
                persistedSnapshot.stacks = encodeStacks(snapshot.stacks(), registries, snapshot.vaultNumber());
                PERSISTED_SNAPSHOTS.put(snapshot.vaultNumber(), persistedSnapshot);
            }
        }

        Map<String, PersistedVaultSnapshot> storedSnapshots = new TreeMap<>();
        for (Map.Entry<Integer, PersistedVaultSnapshot> entry : PERSISTED_SNAPSHOTS.entrySet()) {
            if (entry == null || entry.getValue() == null) {
                continue;
            }
            storedSnapshots.put(Integer.toString(entry.getKey()), entry.getValue());
        }

        try {
            Files.createDirectories(path.getParent());
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to create vault snapshot config directory.", e);
            return;
        }

        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(storedSnapshots, SNAPSHOT_MAP_TYPE, writer);
        } catch (Exception e) {
            LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to save cached vault snapshots.", e);
        }
    }

    private static List<String> encodeStacks(List<ItemStack> stacks, RegistryWrapper.WrapperLookup registries, int vaultNumber) {
        DynamicOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        List<String> encodedStacks = new ArrayList<>(stacks.size());

        for (int slot = 0; slot < stacks.size(); slot++) {
            int slotIndex = slot;
            ItemStack stack = stacks.get(slot);
            if (stack == null || stack.isEmpty()) {
                encodedStacks.add("");
                continue;
            }

            ItemStack persistenceStack = prepareStackForPersistence(stack, registries);
            String encoded = encodeStack(persistenceStack, ops, vaultNumber, slotIndex);
            if (encoded.isBlank()) {
                ItemStack displayFallback = createDisplayFallback(stack);
                encoded = encodeStack(displayFallback, ops, vaultNumber, slotIndex);
            }
            encodedStacks.add(encoded);
        }

        return encodedStacks;
    }

    private static ItemStack prepareStackForPersistence(ItemStack stack, RegistryWrapper.WrapperLookup registries) {
        ItemStack copy = stack.copy();
        RegistryEntryLookup<Enchantment> enchantments = registries.getOptional(RegistryKeys.ENCHANTMENT).orElse(null);
        if (enchantments == null) {
            return copy;
        }

        rebindEnchantments(copy, DataComponentTypes.ENCHANTMENTS, enchantments);
        rebindEnchantments(copy, DataComponentTypes.STORED_ENCHANTMENTS, enchantments);
        return copy;
    }

    private static void rebindEnchantments(
            ItemStack stack,
            ComponentType<ItemEnchantmentsComponent> componentType,
            RegistryEntryLookup<Enchantment> enchantmentLookup
    ) {
        ItemEnchantmentsComponent current = stack.get(componentType);
        if (current == null || current.isEmpty()) {
            return;
        }

        ItemEnchantmentsComponent.Builder rebound = new ItemEnchantmentsComponent.Builder(ItemEnchantmentsComponent.DEFAULT);
        boolean droppedEntry = false;
        for (RegistryEntry<Enchantment> enchantment : current.getEnchantments()) {
            RegistryEntry<Enchantment> activeEntry = enchantment.getKey()
                    .flatMap(enchantmentLookup::getOptional)
                    .orElse(null);
            if (activeEntry == null) {
                droppedEntry = true;
                continue;
            }
            rebound.set(activeEntry, current.getLevel(enchantment));
        }

        stack.set(componentType, rebound.build());
        if (droppedEntry) {
            stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
    }

    private static String encodeStack(
            ItemStack stack,
            DynamicOps<NbtElement> ops,
            int vaultNumber,
            int slotIndex
    ) {
        DataResult<NbtElement> result = ItemStack.CODEC.encodeStart(ops, stack);
        return result.resultOrPartial(error ->
                        LegendsAddon.LOGGER.warn(
                                "[LegendsAddon] Failed to encode cached vault {} slot {}: {}",
                                vaultNumber,
                                slotIndex,
                                error
                        ))
                .map(NbtElement::toString)
                .orElse("");
    }

    private static ItemStack createDisplayFallback(ItemStack source) {
        ItemStack fallback = new ItemStack(source.getItem(), source.getCount());
        fallback.copy(DataComponentTypes.CUSTOM_NAME, source);
        fallback.copy(DataComponentTypes.ITEM_NAME, source);
        fallback.copy(DataComponentTypes.LORE, source);
        fallback.copy(DataComponentTypes.ITEM_MODEL, source);
        fallback.copy(DataComponentTypes.CUSTOM_MODEL_DATA, source);
        fallback.copy(DataComponentTypes.DYED_COLOR, source);
        fallback.copy(DataComponentTypes.TRIM, source);
        fallback.copy(DataComponentTypes.EQUIPPABLE, source);
        fallback.copy(DataComponentTypes.PROFILE, source);
        fallback.copy(DataComponentTypes.MAX_DAMAGE, source);
        fallback.copy(DataComponentTypes.DAMAGE, source);
        if (source.hasGlint()) {
            fallback.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        return fallback;
    }

    private static List<ItemStack> decodeStacks(List<String> encodedStacks, RegistryWrapper.WrapperLookup registries, int vaultNumber) {
        DynamicOps<NbtElement> ops = registries.getOps(NbtOps.INSTANCE);
        List<ItemStack> decodedStacks = new ArrayList<>(encodedStacks == null ? 0 : encodedStacks.size());
        if (encodedStacks == null) {
            return decodedStacks;
        }

        for (int slot = 0; slot < encodedStacks.size(); slot++) {
            int slotIndex = slot;
            String encodedStack = encodedStacks.get(slot);
            if (encodedStack == null || encodedStack.isBlank()) {
                decodedStacks.add(ItemStack.EMPTY);
                continue;
            }

            try {
                NbtElement element = StringNbtReader.readCompound(encodedStack);
                ItemStack stack = ItemStack.CODEC.parse(ops, element)
                        .resultOrPartial(error -> LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to decode cached vault {} slot {}: {}", vaultNumber, slotIndex, error))
                        .orElse(ItemStack.EMPTY);
                decodedStacks.add(stack);
            } catch (Exception e) {
                LegendsAddon.LOGGER.warn("[LegendsAddon] Failed to parse cached vault {} slot {}.", vaultNumber, slotIndex, e);
                decodedStacks.add(ItemStack.EMPTY);
            }
        }

        return decodedStacks;
    }

    private static RegistryWrapper.WrapperLookup getRegistries(MinecraftClient client) {
        if (client == null) {
            return null;
        }

        if (client.world != null) {
            return client.world.getRegistryManager();
        }

        if (client.getNetworkHandler() != null) {
            return client.getNetworkHandler().getRegistryManager();
        }

        return null;
    }

    private static Path getSnapshotsPath() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.runDirectory == null) {
            return null;
        }
        return AddonConfigPaths.configFile(client, VAULT_SNAPSHOTS_FILE);
    }

    private static final class PersistedVaultSnapshot {
        public int rows;
        public List<String> stacks = new ArrayList<>();
    }

    private static final class VaultRangeSettings {
        public int profile = DEFAULT_PROFILE;
        public int start = 1;
        public int end = VAULTS_PER_PROFILE;
    }

    private static boolean isWithinSelectedRange(int vaultNumber) {
        ensureRangeSettingsLoaded();
        return vaultNumber >= getSelectedRangeStart() && vaultNumber <= getSelectedRangeEnd();
    }

    private static int normalizeProfile(int profile) {
        return Math.max(1, Math.min(PROFILE_COUNT, profile));
    }

    private static int getRangeStartForProfile(int profile) {
        return ((normalizeProfile(profile) - 1) * VAULTS_PER_PROFILE) + 1;
    }

    private static int getRangeEndForProfile(int profile) {
        return getRangeStartForProfile(profile) + VAULTS_PER_PROFILE - 1;
    }

    private static int getProfileForVault(int vaultNumber) {
        if (vaultNumber < 1) {
            return DEFAULT_PROFILE;
        }
        return normalizeProfile(((vaultNumber - 1) / VAULTS_PER_PROFILE) + 1);
    }

    public record VaultSnapshot(int vaultNumber, int rows, List<ItemStack> stacks) {
    }

    public record VaultBrowserEntry(
            int vaultNumber,
            boolean locked,
            VaultSnapshot snapshot,
            ItemStack menuIcon,
            String price,
            String legendLevelRequirement
    ) {
    }

    private record VaultMenuEntry(
            int vaultNumber,
            boolean locked,
            ItemStack icon,
            String price,
            String legendLevelRequirement
    ) {
    }
}
