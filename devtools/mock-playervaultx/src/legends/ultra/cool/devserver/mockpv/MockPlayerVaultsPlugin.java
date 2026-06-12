package legends.ultra.cool.devserver.mockpv;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class MockPlayerVaultsPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int MAX_VAULTS = 100;
    private static final int STORAGE_MENU_SIZE = 27;
    private static final int[] STORAGE_MENU_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};

    private File dataFile;
    private YamlConfiguration dataConfig;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadDataConfig();

        Objects.requireNonNull(getCommand("pv"), "pv command missing").setExecutor(this);
        Objects.requireNonNull(getCommand("pv"), "pv command missing").setTabCompleter(this);
        Objects.requireNonNull(getCommand("pvgui"), "pvgui command missing").setExecutor(this);
        Objects.requireNonNull(getCommand("storage"), "storage command missing").setExecutor(this);
        Objects.requireNonNull(getCommand("pvmax"), "pvmax command missing").setExecutor(this);
        Objects.requireNonNull(getCommand("pvmax"), "pvmax command missing").setTabCompleter(this);

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Loaded mock PlayerVaultsX dev plugin.");
    }

    @Override
    public void onDisable() {
        saveDataConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        return switch (name) {
            case "pv" -> handlePvCommand(sender, args);
            case "pvgui", "storage" -> handleStorageCommand(sender);
            case "pvmax" -> handlePvMaxCommand(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("pv")) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>();
                values.add("menu");
                for (int i = 1; i <= MAX_VAULTS; i++) {
                    values.add(Integer.toString(i));
                }
                return filterCompletions(values, args[0]);
            }
            return List.of();
        }

        if (name.equals("pvmax")) {
            if (args.length == 1) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i <= MAX_VAULTS; i++) {
                    values.add(Integer.toString(i));
                }
                if (sender.hasPermission("playervaults.admin") || sender.isOp()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        values.add(player.getName());
                    }
                }
                return filterCompletions(values, args[0]);
            }

            if (args.length == 2 && (sender.hasPermission("playervaults.admin") || sender.isOp())) {
                List<String> values = new ArrayList<>();
                for (int i = 0; i <= MAX_VAULTS; i++) {
                    values.add(Integer.toString(i));
                }
                return filterCompletions(values, args[1]);
            }
        }

        return List.of();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        InventoryHolder holder = topInventory.getHolder();
        if (!(holder instanceof StorageMenuHolder storageMenuHolder)) {
            return;
        }

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getClickedInventory() == null || event.getClickedInventory() != topInventory) {
            return;
        }

        int vaultNumber = storageMenuHolder.getVaultNumber(event.getSlot());
        if (vaultNumber < 1) {
            return;
        }

        if (!isVaultUnlocked(player.getUniqueId(), vaultNumber)) {
            sendConfiguredMessage(player, "messages.locked", Map.of("{number}", Integer.toString(vaultNumber)));
            return;
        }

        openVault(player, vaultNumber);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof VaultHolder vaultHolder)) {
            return;
        }

        saveVaultContents(vaultHolder.playerId(), vaultHolder.vaultNumber(), event.getInventory());
    }

    private boolean handlePvCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(colorize(getConfig().getString("messages.player-only", "&cPlayers only.")));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            openStorageMenu(player);
            return true;
        }

        int vaultNumber = parseVaultNumber(args[0]);
        if (vaultNumber < 1) {
            sendConfiguredMessage(player, "messages.invalid-number", Map.of("{input}", args[0]));
            return true;
        }

        if (!isVaultUnlocked(player.getUniqueId(), vaultNumber)) {
            sendConfiguredMessage(player, "messages.locked", Map.of("{number}", Integer.toString(vaultNumber)));
            return true;
        }

        openVault(player, vaultNumber);
        return true;
    }

    private boolean handleStorageCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(colorize(getConfig().getString("messages.player-only", "&cPlayers only.")));
            return true;
        }

        openStorageMenu(player);
        return true;
    }

    private boolean handlePvMaxCommand(CommandSender sender, String[] args) {
        if (args.length == 1 && sender instanceof Player player) {
            int count = parseVaultNumber(args[0]);
            if (count < 0 || count > MAX_VAULTS) {
                sendConfiguredMessage(sender, "messages.usage-pvmax", Map.of());
                return true;
            }

            setUnlockedVaultCount(player.getUniqueId(), count);
            sendConfiguredMessage(sender, "messages.updated-max", Map.of("{number}", Integer.toString(count)));
            return true;
        }

        if (args.length == 2 && (sender.hasPermission("playervaults.admin") || sender.isOp())) {
            Player target = Bukkit.getPlayerExact(args[0]);
            int count = parseVaultNumber(args[1]);
            if (target == null || count < 0 || count > MAX_VAULTS) {
                sendConfiguredMessage(sender, "messages.usage-pvmax-admin", Map.of());
                return true;
            }

            setUnlockedVaultCount(target.getUniqueId(), count);
            sendConfiguredMessage(sender, "messages.updated-max-admin", Map.of(
                    "{player}", target.getName(),
                    "{number}", Integer.toString(count)
            ));
            return true;
        }

        sendConfiguredMessage(sender, "messages.usage-pvmax", Map.of());
        return true;
    }

    private void openStorageMenu(Player player) {
        StorageMenuHolder holder = new StorageMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, STORAGE_MENU_SIZE, colorize(getConfig().getString("storage-menu-title", "Storage Menu")));
        holder.setInventory(inventory);

        for (int vaultNumber = 1; vaultNumber <= MAX_VAULTS; vaultNumber++) {
            boolean unlocked = isVaultUnlocked(player.getUniqueId(), vaultNumber);
            inventory.setItem(STORAGE_MENU_SLOTS[vaultNumber - 1], createMenuItem(vaultNumber, unlocked));
        }

        player.openInventory(inventory);
    }

    private void openVault(Player player, int vaultNumber) {
        int rows = Math.max(1, Math.min(6, getConfig().getInt("default-vault-rows", 6)));
        String titleTemplate = getConfig().getString("vault-title", "Personal Vault {number}");
        String title = colorize(titleTemplate.replace("{number}", Integer.toString(vaultNumber)));

        VaultHolder holder = new VaultHolder(player.getUniqueId(), vaultNumber);
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, title);
        holder.setInventory(inventory);
        loadVaultContents(player.getUniqueId(), vaultNumber, inventory);
        player.openInventory(inventory);
    }

    private ItemStack createMenuItem(int vaultNumber, boolean unlocked) {
        Material material = unlocked ? Material.BARREL : Material.RED_STAINED_GLASS_PANE;
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();

        String displayTemplate = unlocked
                ? getConfig().getString("menu.unlocked-name", "&bPersonal Vault {number}")
                : getConfig().getString("menu.locked-name", "&cLocked Vault {number}");
        meta.setDisplayName(colorize(displayTemplate.replace("{number}", Integer.toString(vaultNumber))));

        List<String> loreTemplate = unlocked
                ? getConfig().getStringList("menu.unlocked-lore")
                : getConfig().getStringList("menu.locked-lore");
        List<String> lore = new ArrayList<>(loreTemplate.size());
        for (String line : loreTemplate) {
            lore.add(colorize(line
                    .replace("{number}", Integer.toString(vaultNumber))
                    .replace("{command}", "/pv " + vaultNumber)));
        }
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isVaultUnlocked(UUID playerId, int vaultNumber) {
        return vaultNumber > 0 && vaultNumber <= getUnlockedVaultCount(playerId);
    }

    private int getUnlockedVaultCount(UUID playerId) {
        return Math.max(0, Math.min(MAX_VAULTS, dataConfig.getInt("players." + playerId + ".maxVaults", getConfig().getInt("default-unlocked-vaults", 7))));
    }

    private void setUnlockedVaultCount(UUID playerId, int count) {
        dataConfig.set("players." + playerId + ".maxVaults", Math.max(0, Math.min(MAX_VAULTS, count)));
        saveDataConfig();
    }

    private void loadVaultContents(UUID playerId, int vaultNumber, Inventory inventory) {
        String path = getVaultPath(playerId, vaultNumber);
        List<?> stored = dataConfig.getList(path, Collections.emptyList());
        ItemStack[] contents = new ItemStack[inventory.getSize()];

        for (int i = 0; i < Math.min(stored.size(), contents.length); i++) {
            Object value = stored.get(i);
            if (value instanceof ItemStack stack) {
                contents[i] = stack;
            }
        }

        inventory.setContents(contents);
    }

    private void saveVaultContents(UUID playerId, int vaultNumber, Inventory inventory) {
        String path = getVaultPath(playerId, vaultNumber);
        dataConfig.set(path, Arrays.asList(inventory.getContents()));
        saveDataConfig();
    }

    private String getVaultPath(UUID playerId, int vaultNumber) {
        return "players." + playerId + ".vaults." + vaultNumber;
    }

    private void loadDataConfig() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                if (dataFile.getParentFile() != null) {
                    dataFile.getParentFile().mkdirs();
                }
                dataFile.createNewFile();
            } catch (IOException e) {
                throw new IllegalStateException("Failed to create data.yml", e);
            }
        }

        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void saveDataConfig() {
        if (dataConfig == null || dataFile == null) {
            return;
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            getLogger().warning("Failed to save data.yml: " + e.getMessage());
        }
    }

    private void sendConfiguredMessage(CommandSender sender, String path, Map<String, String> replacements) {
        String raw = getConfig().getString(path, "&cMissing message: " + path);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }
        sender.sendMessage(colorize(raw));
    }

    private int parseVaultNumber(String input) {
        try {
            return Integer.parseInt(input);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private List<String> filterCompletions(List<String> values, String input) {
        String loweredInput = input.toLowerCase(Locale.ROOT);
        return values.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(loweredInput))
                .distinct()
                .toList();
    }

    private String colorize(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }

    private static final class StorageMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private int getVaultNumber(int slot) {
            for (int i = 0; i < STORAGE_MENU_SLOTS.length; i++) {
                if (STORAGE_MENU_SLOTS[i] == slot) {
                    return i + 1;
                }
            }
            return -1;
        }
    }

    private static final class VaultHolder implements InventoryHolder {
        private final UUID playerId;
        private final int vaultNumber;
        private Inventory inventory;

        private VaultHolder(UUID playerId, int vaultNumber) {
            this.playerId = playerId;
            this.vaultNumber = vaultNumber;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        private UUID playerId() {
            return playerId;
        }

        private int vaultNumber() {
            return vaultNumber;
        }
    }
}
