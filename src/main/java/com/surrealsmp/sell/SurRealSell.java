package com.surrealsmp.sell;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SurRealSell extends JavaPlugin implements Listener {
    private Economy economy;
    private final Map<UUID, SellHolder> openSessions = new HashMap<>();
    private final Set<UUID> completedSessions = new HashSet<>();
    private final Map<Material, Double> prices = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPrices();
        if (!setupEconomy()) {
            getLogger().severe("Vault or an economy provider was not found. Install Vault and an economy plugin such as EssentialsX Economy.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("SurRealSell enabled. Author: surrealsmpofficial");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> registration =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (registration == null) return false;
        economy = registration.getProvider();
        return economy != null;
    }

    private String color(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private String message(String key) {
        return color(getConfig().getString("messages.prefix", "&5&lSurRealSell &8» ")
                + getConfig().getString("messages." + key, "&cMissing message: " + key));
    }

    private void loadPrices() {
        prices.clear();
        ConfigurationSection section = getConfig().getConfigurationSection("prices");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem()) {
                getLogger().warning("Ignoring invalid item material in prices: " + key);
                continue;
            }
            double price = section.getDouble(key, 0.0);
            if (!Double.isFinite(price) || price < 0.0) {
                getLogger().warning("Ignoring invalid negative/non-finite price for " + key);
                continue;
            }
            prices.put(material, price);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("sell")) return false;
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("surrealsell.admin")) {
                sender.sendMessage(message("no-permission"));
                return true;
            }
            reloadConfig();
            loadPrices();
            sender.sendMessage(message("reloaded"));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("player-only"));
            return true;
        }
        if (!player.hasPermission("surrealsell.use")) {
            player.sendMessage(message("no-permission"));
            return true;
        }
        openSellMenu(player);
        player.sendMessage(message("opened"));
        return true;
    }

    private void openSellMenu(Player player) {
        SellHolder holder = new SellHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 54,
                color(getConfig().getString("gui.title", "&8&lSurRealSell")));
        holder.setInventory(inventory);
        openSessions.put(player.getUniqueId(), holder);

        Material filler = materialSetting("gui.filler", Material.BLACK_STAINED_GLASS_PANE);
        ItemStack pane = namedItem(filler, "&r", List.of());
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, pane.clone());

        int totalSlot = clampControlSlot(getConfig().getInt("gui.total-slot", 4));
        int confirmSlot = clampControlSlot(getConfig().getInt("gui.confirm-slot", 49));
        if (totalSlot == confirmSlot) totalSlot = confirmSlot == 4 ? 3 : 4;
        inventory.setItem(totalSlot, makeTotalItem(0.0, 0));
        inventory.setItem(confirmSlot, makeConfirmItem(0.0));
        // Only the upper 45 slots are deposit slots; the last row is controls.
        player.openInventory(inventory);
    }

    private int clampControlSlot(int slot) {
        return Math.max(45, Math.min(53, slot));
    }

    private Material materialSetting(String path, Material fallback) {
        Material material = Material.matchMaterial(getConfig().getString(path, fallback.name()));
        return material != null && material.isItem() ? material : fallback;
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>();
                for (String line : lore) colored.add(color(line));
                meta.setLore(colored);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private double currentTotal(Inventory inventory) {
        double total = 0.0;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            Double price = prices.get(item.getType());
            if (price != null && price > 0.0) total += price * item.getAmount();
        }
        return total;
    }

    private int sellableItemCount(Inventory inventory) {
        int count = 0;
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            Double price = prices.get(item.getType());
            if (price != null && price > 0.0) count += item.getAmount();
        }
        return count;
    }

    private ItemStack makeTotalItem(double total, int count) {
        List<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList("gui.total-lore")) {
            lore.add(line.replace("{total}", formatMoney(total)).replace("{count}", String.valueOf(count)));
        }
        return namedItem(materialSetting("gui.total-item", Material.GOLD_INGOT),
                getConfig().getString("gui.total-name", "&6&lSale total"), lore);
    }

    private ItemStack makeConfirmItem(double total) {
        List<String> lore = new ArrayList<>();
        for (String line : getConfig().getStringList("gui.confirm-lore")) {
            lore.add(line.replace("{total}", formatMoney(total)));
        }
        return namedItem(materialSetting("gui.confirm-item", Material.LIME_STAINED_GLASS_PANE),
                getConfig().getString("gui.confirm-name", "&a&lSELL ITEMS"), lore);
    }

    private String formatMoney(double amount) {
        String symbol = getConfig().getString("gui.currency-symbol", "$");
        return symbol + String.format(Locale.US, "%,.2f", amount);
    }

    private void updatePreview(Inventory inventory) {
        double total = currentTotal(inventory);
        int count = sellableItemCount(inventory);
        int totalSlot = clampControlSlot(getConfig().getInt("gui.total-slot", 4));
        int confirmSlot = clampControlSlot(getConfig().getInt("gui.confirm-slot", 49));
        if (totalSlot == confirmSlot) totalSlot = confirmSlot == 4 ? 3 : 4;
        inventory.setItem(totalSlot, makeTotalItem(total, count));
        inventory.setItem(confirmSlot, makeConfirmItem(total));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder holder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= event.getView().getTopInventory().getSize()) {
            // Avoid shift-clicking inventory items into the GUI; normal clicks are allowed in player inventory.
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        if (rawSlot < 0) return;

        int confirmSlot = clampControlSlot(getConfig().getInt("gui.confirm-slot", 49));
        int totalSlot = clampControlSlot(getConfig().getInt("gui.total-slot", 4));
        if (totalSlot == confirmSlot) totalSlot = confirmSlot == 4 ? 3 : 4;

        if (rawSlot == confirmSlot) {
            completeSale(player, event.getView().getTopInventory(), holder);
            return;
        }
        if (rawSlot >= 45 || rawSlot == totalSlot) return;

        // Handle normal cursor placement/removal ourselves to keep the GUI controlled.
        Inventory inventory = event.getView().getTopInventory();
        ItemStack cursor = event.getCursor();
        ItemStack current = inventory.getItem(rawSlot);
        if (cursor != null && !cursor.getType().isAir()) {
            if (current == null || current.getType().isAir()) {
                inventory.setItem(rawSlot, cursor.clone());
                player.setItemOnCursor(null);
            } else if (current.isSimilar(cursor) && current.getAmount() < current.getMaxStackSize()) {
                int transfer = Math.min(cursor.getAmount(), current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + transfer);
                inventory.setItem(rawSlot, current);
                if (transfer == cursor.getAmount()) player.setItemOnCursor(null);
                else {
                    ItemStack remaining = cursor.clone();
                    remaining.setAmount(cursor.getAmount() - transfer);
                    player.setItemOnCursor(remaining);
                }
            } else {
                inventory.setItem(rawSlot, cursor.clone());
                player.setItemOnCursor(current == null ? null : current.clone());
            }
        } else if (current != null && !current.getType().isAir()) {
            inventory.setItem(rawSlot, null);
            player.setItemOnCursor(current.clone());
        }
        updatePreview(inventory);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SellHolder)) return;
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < event.getView().getTopInventory().getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void completeSale(Player player, Inventory inventory, SellHolder holder) {
        double total = currentTotal(inventory);
        int count = sellableItemCount(inventory);
        if (total <= 0.0 || count <= 0) {
            player.sendMessage(message("nothing-to-sell"));
            return;
        }

        EconomyResponse response = economy.depositPlayer(player, total);
        if (response == null || !response.transactionSuccess()) {
            player.sendMessage(color(getConfig().getString("messages.prefix", "&5&lSurRealSell &8» ")
                    + "&cThe payment failed. Your items have not been sold."));
            return;
        }

        // Remove only priced items after the economy confirms payment; unpriced items are returned.
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            Double price = prices.get(item.getType());
            if (price != null && price > 0.0) inventory.setItem(slot, null);
        }

        completedSessions.add(player.getUniqueId());
        player.closeInventory();
        returnUnsoldItems(player, inventory);
        player.sendMessage(message("sold")
                .replace("{count}", String.valueOf(count))
                .replace("{total}", formatMoney(total)));
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof SellHolder holder)) return;
        UUID uuid = holder.playerId();
        openSessions.remove(uuid);

        Player player = (Player) event.getPlayer();
        if (completedSessions.remove(uuid)) {
            // The green sell button already completed the sale. Return only unpriced items.
            returnUnsoldItems(player, event.getInventory());
            return;
        }

        // Closing the GUI with Escape (or any other close action) automatically sells
        // priced items. If payment fails or there is nothing sellable, return all items.
        sellOnClose(player, event.getInventory());
    }

    private void sellOnClose(Player player, Inventory inventory) {
        double total = currentTotal(inventory);
        int count = sellableItemCount(inventory);

        if (total <= 0.0 || count <= 0) {
            returnUnsoldItems(player, inventory);
            player.sendMessage(message("nothing-to-sell"));
            return;
        }

        EconomyResponse response = economy.depositPlayer(player, total);
        if (response == null || !response.transactionSuccess()) {
            returnUnsoldItems(player, inventory);
            player.sendMessage(color(getConfig().getString("messages.prefix", "&5&lSurRealSell &8» ")
                    + "&cThe payment failed. Your items have been returned."));
            return;
        }

        // Payment succeeded: remove priced items and return anything without a configured price.
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            Double price = prices.get(item.getType());
            if (price != null && price > 0.0) inventory.setItem(slot, null);
        }

        returnUnsoldItems(player, inventory);
        player.sendMessage(message("sold")
                .replace("{count}", String.valueOf(count))
                .replace("{total}", formatMoney(total)));
    }

    private void returnUnsoldItems(Player player, Inventory inventory) {
        for (int slot = 0; slot < 45; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            inventory.setItem(slot, null);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    private static final class SellHolder implements InventoryHolder {
        private final UUID playerId;
        private Inventory inventory;

        private SellHolder(UUID playerId) { this.playerId = playerId; }
        UUID playerId() { return playerId; }
        void setInventory(Inventory inventory) { this.inventory = inventory; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
