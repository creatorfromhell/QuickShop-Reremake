package org.maxgamer.quickshop.Shop;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;
import org.jetbrains.annotations.*;
import org.maxgamer.quickshop.Event.ShopCreateEvent;
import org.maxgamer.quickshop.Event.ShopPreCreateEvent;
import org.maxgamer.quickshop.Event.ShopPurchaseEvent;
import org.maxgamer.quickshop.Event.ShopSuccessPurchaseEvent;
import org.maxgamer.quickshop.QuickShop;
import org.maxgamer.quickshop.Util.MsgUtil;
import org.maxgamer.quickshop.Util.Util;

/**
 * Manage a lot of shops.
 */
@SuppressWarnings("WeakerAccess")
public class ShopManager {
    private QuickShop plugin;
    private HashMap<UUID, Info> actions = new HashMap<UUID, Info>();
    private HashMap<String, HashMap<ShopChunk, HashMap<Location, Shop>>> shops = new HashMap<>();
    final private static ItemStack AIR = new ItemStack(Material.AIR);

    public ShopManager(@NotNull QuickShop plugin) {
        this.plugin = plugin;
    }

    /**
     * @return Returns the HashMap. Info contains what
     * their last question etc was.
     */
    public HashMap<UUID, Info> getActions() {
        return this.actions;
    }

    /**
     * Create a shop use Shop and Info object.
     *
     * @param shop The shop object
     * @param info The info object
     */
    public void createShop(@NotNull Shop shop, @NotNull Info info) {
        ShopCreateEvent ssShopCreateEvent = new ShopCreateEvent(shop, Bukkit.getPlayer(shop.getOwner()));
        Bukkit.getPluginManager().callEvent(ssShopCreateEvent);
        if (ssShopCreateEvent.isCancelled()) {
            return;
        }
        Location loc = shop.getLocation();
        ItemStack item = shop.getItem();
        try {
            // Write it to the database
            plugin.getDatabaseHelper().createShop(plugin.getDatabase(), ShopModerator.serialize(shop.getModerator()), shop
                    .getPrice(), item, (shop.isUnlimited() ?
                    1 :
                    0), shop.getShopType().toID(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
            // Add it to the world
            addShop(loc.getWorld().getName(), shop);
        } catch (SQLException error) {
            plugin.getLogger().warning("SQLException detected, trying to auto fix the database...");
            boolean backupSuccess = Util.backupDatabase();
            try {
                if (backupSuccess) {
                    plugin.getDatabaseHelper().removeShop(plugin.getDatabase(), loc.getBlockX(), loc.getBlockY(), loc
                            .getBlockZ(), loc.getWorld().getName());
                } else {
                    plugin.getLogger().warning("Failed to backup the database, all changes will revert after a reboot.");
                }
            } catch (SQLException error2) {
                //Failed removing
                plugin.getLogger().warning("Failed to autofix the database, all changes will revert after a reboot.");
                error.printStackTrace();
                error2.printStackTrace();
            }
        }
        //Create sign
        if (info.getSignBlock() != null && plugin.getConfig().getBoolean("shop.auto-sign")) {
            if (!Util.isAir(info.getSignBlock().getType())) {
                Util.debugLog("Sign cannot placed cause no enough space(Not air block)");
                return;
            }
            boolean isWaterLogged = false;
            if (info.getSignBlock().getType() == Material.WATER)
                isWaterLogged = true;

            info.getSignBlock().setType(Util.getSignMaterial());
            BlockState bs = info.getSignBlock().getState();
            if (isWaterLogged) {
                Waterlogged waterable = (Waterlogged) bs.getBlockData();
                waterable.setWaterlogged(true); // Looks like sign directly put in water
            }
            org.bukkit.block.data.type.WallSign signBlockDataType = (org.bukkit.block.data.type.WallSign) bs.getBlockData();
            BlockFace bf = info.getLocation().getBlock().getFace(info.getSignBlock());
            if (bf != null) {
                signBlockDataType.setFacing(bf);
                bs.setBlockData(signBlockDataType);
            }
            bs.update(true);
            shop.setSignText();
        }
    }

    /**
     * Loads the given shop into storage. This method is used for loading data
     * from the database. Do not use this method to create a shop.
     *
     * @param world The world the shop is in
     * @param shop  The shop to load
     */
    public void loadShop(@NotNull String world, @NotNull Shop shop) {
        this.addShop(world, shop);
    }

    /**
     * Returns a hashmap of World - Chunk - Shop
     *
     * @return a hashmap of World - Chunk - Shop
     */
    public HashMap<String, HashMap<ShopChunk, HashMap<Location, Shop>>> getShops() {
        return this.shops;
    }

    /**
     * Returns a hashmap of Chunk - Shop
     *
     * @param world The name of the world (case sensitive) to get the list of
     *              shops from
     * @return a hashmap of Chunk - Shop
     */
    public HashMap<ShopChunk, HashMap<Location, Shop>> getShops(@NotNull String world) {
        return this.shops.get(world);
    }

    /**
     * Returns a hashmap of Shops
     *
     * @param c The chunk to search. Referencing doesn't matter, only
     *          coordinates and world are used.
     * @return Shops
     */
    public HashMap<Location, Shop> getShops(@NotNull Chunk c) {
        // long start = System.nanoTime();
        HashMap<Location, Shop> shops = getShops(c.getWorld().getName(), c.getX(), c.getZ());
        // long end = System.nanoTime();
        // plugin.getLogger().log(Level.WARNING, "Chunk lookup in " + ((end - start)/1000000.0) +
        // "ms.");
        return shops;
    }

    public HashMap<Location, Shop> getShops(String world, int chunkX, int chunkZ) {
        HashMap<ShopChunk, HashMap<Location, Shop>> inWorld = this.getShops(world);
        if (inWorld == null) {
            return null;
        }
        ShopChunk shopChunk = new ShopChunk(world, chunkX, chunkZ);
        return inWorld.get(shopChunk);
    }

    /**
     * Gets a shop in a specific location
     *
     * @param loc The location to get the shop from
     * @return The shop at that location
     */
    public Shop getShop(@NotNull Location loc) {
        HashMap<Location, Shop> inChunk = getShops(loc.getChunk());
        if (inChunk == null) {
            return null;
        }
        // We can do this because WorldListener updates the world reference so
        // the world in loc is the same as world in inChunk.get(loc)
        return inChunk.get(loc);
    }

    /**
     * Adds a shop to the world. Does NOT require the chunk or world to be loaded
     * Call shop.onLoad by yourself
     *
     * @param world The name of the world
     * @param shop  The shop to add
     */
    public void addShop(@NotNull String world, @NotNull Shop shop) {
        HashMap<ShopChunk, HashMap<Location, Shop>> inWorld = this.getShops()
                .computeIfAbsent(world, k -> new HashMap<>(3));
        // There's no world storage yet. We need to create that hashmap.
        // Put it in the data universe
        // Calculate the chunks coordinates. These are 1,2,3 for each chunk, NOT
        // location rounded to the nearest 16.
        int x = (int) Math.floor((shop.getLocation().getBlockX()) / 16.0);
        int z = (int) Math.floor((shop.getLocation().getBlockZ()) / 16.0);
        // Get the chunk set from the world info
        ShopChunk shopChunk = new ShopChunk(world, x, z);
        HashMap<Location, Shop> inChunk = inWorld.computeIfAbsent(shopChunk, k -> new HashMap<>(1));
        // That chunk data hasn't been created yet - Create it!
        // Put it in the world
        // Put the shop in its location in the chunk list.
        inChunk.put(shop.getLocation(), shop);
        // shop.onLoad();

    }

    /**
     * Removes a shop from the world. Does NOT remove it from the database. *
     * REQUIRES * the world to be loaded
     * Call shop.onUnload by your self.
     *
     * @param shop The shop to remove
     */
    public void removeShop(@NotNull Shop shop) {
        // shop.onUnload();
        Location loc = shop.getLocation();
        String world = loc.getWorld().getName();
        HashMap<ShopChunk, HashMap<Location, Shop>> inWorld = this.getShops().get(world);
        int x = (int) Math.floor((shop.getLocation().getBlockX()) / 16.0);
        int z = (int) Math.floor((shop.getLocation().getBlockZ()) / 16.0);
        ShopChunk shopChunk = new ShopChunk(world, x, z);
        HashMap<Location, Shop> inChunk = inWorld.get(shopChunk);
        if (inChunk == null)
            return;
        inChunk.remove(loc);
        // shop.onUnload();
    }

    /**
     * Removes all shops from memory and the world. Does not delete them from
     * the database. Call this on plugin disable ONLY.
     */
    public void clear() {
        if (plugin.isDisplay()) {
            for (World world : Bukkit.getWorlds()) {
                for (Chunk chunk : world.getLoadedChunks()) {
                    HashMap<Location, Shop> inChunk = this.getShops(chunk);
                    if (inChunk == null || inChunk.isEmpty())
                        continue;
                    for (Shop shop : inChunk.values()) {
                        shop.onUnload();
                    }
                }
            }
        }
        this.actions.clear();
        this.shops.clear();
    }

    /**
     * Checks other plugins to make sure they can use the chest they're making a
     * shop.
     *
     * @param p The player to check
     * @param b The block to check
     * @param bf The blockface to check
     * @return True if they're allowed to place a shop there.
     */
    public boolean canBuildShop(@NotNull Player p, @NotNull Block b, @NotNull BlockFace bf) {
        RegisteredListener openInvRegisteredListener = null; // added for compatibility reasons with OpenInv - see https://github.com/KaiKikuchi/QuickShop/issues/139
        // try {
        // 	if (plugin.openInvPlugin != null) {
        // 		for (RegisteredListener listener : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
        // 			if (listener.getPlugin().getName().equals(plugin.openInvPlugin.getName())) {
        // 				openInvRegisteredListener = listener;
        // 				PlayerInteractEvent.getHandlerList().unregister(listener);
        // 				break;
        // 			}
        // 		}
        // 	}
        try {
            plugin.getCompatibilityTool().toggleInteractListeners(false);

            if (plugin.isLimit()) {
                int owned = 0;
                if (!plugin.getConfig().getBoolean("limits.old-algorithm")) {
                    for (HashMap<ShopChunk, HashMap<Location, Shop>> shopmap : getShops().values()) {
                        for (HashMap<Location, Shop> shopLocs : shopmap.values()) {
                            for (Shop shop : shopLocs.values()) {
                                if (shop.getOwner().equals(p.getUniqueId()) && !shop.isUnlimited()) {
                                    owned++;
                                }
                            }
                        }
                    }
                } else {
                    Iterator<Shop> it = getShopIterator();
                    while (it.hasNext()) {
                        if (it.next().getOwner().equals(p.getUniqueId())) {
                            owned++;
                        }
                    }
                }

                int max = plugin.getShopLimit(p);
                if (owned + 1 > max) {
                    //p.sendMessage(ChatColor.RED + "You have already created a maximum of " + owned + "/" + max + " shops!");
                    p.sendMessage(MsgUtil.getMessage("reached-maximum-can-create", String.valueOf(owned), String.valueOf(max)));
                    return false;
                }
            }
            if (!plugin.getPermissionChecker().canBuild(p, b, true)) {
                Util.debugLog("PermissionChecker canceled shop creation");
                return false;
            }
            ShopPreCreateEvent spce = new ShopPreCreateEvent(p, b.getLocation());
            Bukkit.getPluginManager().callEvent(spce);
            if (spce.isCancelled()) {
                return false;
            }
        } finally {
            // if (plugin.openInvPlugin != null && openInvRegisteredListener != null) {
            // 	PlayerInteractEvent.getHandlerList().register(openInvRegisteredListener);
            // }
            plugin.getCompatibilityTool().toggleInteractListeners(true);
        }

        return true;
    }

    public void handleChat(@NotNull Player p, @NotNull String msg) {
        final String message = ChatColor.stripColor(msg);
        // Use from the main thread, because Bukkit hates life
        HashMap<UUID, Info> actions = getActions();
        // They wanted to do something.
        Info info = actions.remove(p.getUniqueId());
        if (info == null)
            return; // multithreaded means this can happen
        if (info.getLocation().getWorld() != p.getLocation().getWorld()) {
            p.sendMessage(MsgUtil.getMessage("shop-creation-cancelled"));
            return;
        }
        if (info.getLocation().distanceSquared(p.getLocation()) > 25) {
            p.sendMessage(MsgUtil.getMessage("shop-creation-cancelled"));
            return;
        }
        /* Creation handling */
        if (info.getAction() == ShopAction.CREATE) {
            actionCreate(p, actions, info, message);
            /* Purchase Handling */
        } else if (info.getAction() == ShopAction.BUY) {
            actionTrade(p, actions, info, message);
        }
        /* If it was already cancelled (from destroyed) */
    }

    private void actionTrade(@NotNull Player p, @NotNull HashMap<UUID, Info> actions, @NotNull Info info, @NotNull String message) {
        int amount;
        try {
            amount = Integer.parseInt(message);
        } catch (NumberFormatException e) {
            p.sendMessage(MsgUtil.getMessage("shop-purchase-cancelled"));
            return;
        }
        // Get the shop they interacted with
        Shop shop = plugin.getShopManager().getShop(info.getLocation());
        // It's not valid anymore
        if (shop == null || !Util.canBeShop(info.getLocation().getBlock())) {
            p.sendMessage(MsgUtil.getMessage("chest-was-removed"));
            return;
        }
        if (info.hasChanged(shop)) {
            p.sendMessage(MsgUtil.getMessage("shop-has-changed"));
            return;
        }
        if (shop.isBuying()) {
            actionBuy(p, actions, info, message, shop, amount);
        } else if (shop.isSelling()) {
            actionSell(p, actions, info, message, shop, amount);
        } else {
            p.sendMessage(MsgUtil.getMessage("shop-purchase-cancelled"));
            plugin.getLogger().warning("Shop data broken? Loc:" + shop.getLocation().toString());
        }
    }

    @SuppressWarnings("deprecation")
    private void actionBuy(@NotNull Player p, @NotNull HashMap<UUID, Info> actions2, @NotNull Info info, @NotNull String message, @NotNull Shop shop, int amount) {
        int space = shop.getRemainingSpace();
        if (space == -1)
            space = 10000;
        if (space < amount) {
            p.sendMessage(MsgUtil
                    .getMessage("shop-has-no-space", "" + space, Util.getItemStackName(shop.getItem())));
            return;
        }
        int count = Util.countItems(p.getInventory(), shop.getItem());
        // Not enough items
        if (amount > count) {
            p.sendMessage(MsgUtil.getMessage("you-dont-have-that-many-items", "" + count, Util.getItemStackName(shop.getItem())));
            return;
        }
        if (amount == 0) {
            // Dumb.
            MsgUtil.sendPurchaseSuccess(p, shop, amount);
            return;
        } else if (amount < 0) {
            // & Dumber
            p.sendMessage(MsgUtil.getMessage("negative-amount"));
            return;
        }
        ShopPurchaseEvent e = new ShopPurchaseEvent(shop, p, amount);
        Bukkit.getPluginManager().callEvent(e);
        if (e.isCancelled())
            return; // Cancelled
        // Money handling
        double tax = plugin.getConfig().getDouble("tax");
        double total = amount * shop.getPrice();
        if (!p.getUniqueId().equals(shop.getOwner())) {
            // Don't tax them if they're purchasing from
            // themselves.
            // Do charge an amount of tax though.
            if (shop.getOwner().equals(p.getUniqueId())) {
                tax = 0;
            }

            if (!shop.isUnlimited() || plugin.getConfig().getBoolean("shop.pay-unlimited-shop-owners")) {
                // Tries to check their balance nicely to see if
                // they can afford it.
                if (plugin.getEconomy().getBalance(shop.getOwner()) < amount * shop.getPrice()) {
                    p.sendMessage(MsgUtil
                            .getMessage("the-owner-cant-afford-to-buy-from-you", format(amount * shop.getPrice()), format(plugin
                                    .getEconomy().getBalance(shop.getOwner()))));
                    return;
                }
                // Check for plugins faking econ.has(amount)
                if (!plugin.getEconomy().withdraw(shop.getOwner(), total)) {
                    p.sendMessage(MsgUtil
                            .getMessage("the-owner-cant-afford-to-buy-from-you", format(amount * shop.getPrice()), format(plugin
                                    .getEconomy().getBalance(shop.getOwner()))));
                    return;
                }
                if (tax != 0) {
                    plugin.getEconomy().deposit(Bukkit.getOfflinePlayer(plugin.getConfig().getString("tax-account"))
                            .getUniqueId(), total * tax);
                }
            }
            // Give them the money after we know we succeeded
            plugin.getEconomy().deposit(p.getUniqueId(), total * (1 - tax));
            // Notify the owner of the purchase.
            String msg = MsgUtil.getMessage("player-sold-to-your-store", p.getName(), "" + amount, Util
                    .getItemStackName(shop.getItem()));
            if (space == amount)
                msg += "\n" + MsgUtil.getMessage("shop-out-of-space", "" + shop.getLocation().getBlockX(), "" + shop.getLocation()
                        .getBlockY(), "" + shop.getLocation().getBlockZ());
            MsgUtil.send(shop.getOwner(), msg, shop.isUnlimited());
        }
        shop.buy(p, amount);
        MsgUtil.sendSellSuccess(p, shop, amount);
        ShopSuccessPurchaseEvent se = new ShopSuccessPurchaseEvent(shop, p, amount, tax * total);
        Bukkit.getPluginManager().callEvent(se);
        plugin.log(p.getName() + " sold " + amount + " for " + (shop.getPrice() * amount) + " to " + shop.toString());
        //shop.setSignText(); // Update the signs count
    }

    @SuppressWarnings("deprecation")
    private void actionSell(@NotNull Player p, @NotNull HashMap<UUID, Info> actions2, @NotNull Info info, @NotNull String message, @NotNull Shop shop2, int amount) {
        Shop shop = plugin.getShopManager().getShop(info.getLocation());
        // It's not valid anymore
        if (shop == null || !Util.canBeShop(info.getLocation().getBlock())) {
            p.sendMessage(MsgUtil.getMessage("chest-was-removed"));
            return;
        }
        if (info.hasChanged(shop)) {
            p.sendMessage(MsgUtil.getMessage("shop-has-changed"));
            return;
        }
        int stock = shop.getRemainingStock();
        if (stock == -1)
            stock = 10000;
        if (stock < amount) {
            p.sendMessage(MsgUtil
                    .getMessage("shop-stock-too-low", "" + shop.getRemainingStock(), Util.getItemStackName(shop.getItem())));
            return;
        }
        if (amount == 0) {
            // Dumb.
            MsgUtil.sendPurchaseSuccess(p, shop, amount);
            return;
        } else if (amount < 0) {
            // & Dumber
            p.sendMessage(MsgUtil.getMessage("negative-amount"));
            return;
        }
        int pSpace = Util.countSpace(p.getInventory(), shop.getItem());
        if (amount > pSpace) {
            p.sendMessage(MsgUtil.getMessage("not-enough-space", "" + pSpace));
            return;
        }
        ShopPurchaseEvent e = new ShopPurchaseEvent(shop, p, amount);
        Bukkit.getPluginManager().callEvent(e);
        if (e.isCancelled())
            return; // Cancelled
        // Money handling
        double tax = plugin.getConfig().getDouble("tax");
        double total = amount * shop.getPrice();
        if (!p.getUniqueId().equals(shop.getOwner())) {
            // Check their balance. Works with *most* economy
            // plugins*
            if (plugin.getEconomy().getBalance(p.getUniqueId()) < amount * shop.getPrice()) {
                p.sendMessage(MsgUtil
                        .getMessage("you-cant-afford-to-buy", format(amount * shop.getPrice()), format(plugin.getEconomy()
                                .getBalance(p.getUniqueId()))));
                return;
            }
            // Don't tax them if they're purchasing from
            // themselves.
            // Do charge an amount of tax though.
            if (shop.getOwner().equals(p.getUniqueId())) {
                tax = 0;
            }
            if (!plugin.getEconomy().withdraw(p.getUniqueId(), total)) {
                p.sendMessage(MsgUtil
                        .getMessage("you-cant-afford-to-buy", format(amount * shop.getPrice()), format(plugin.getEconomy()
                                .getBalance(p.getUniqueId()))));
                return;
            }
            if (!shop.isUnlimited() || plugin.getConfig().getBoolean("shop.pay-unlimited-shop-owners")) {
                plugin.getEconomy().deposit(shop.getOwner(), total * (1 - tax));
                if (tax != 0) {
                    plugin.getEconomy().deposit(Bukkit.getOfflinePlayer(plugin.getConfig().getString("tax-account"))
                            .getUniqueId(), total * tax);
                }
            }
            // Notify the shop owner
            if (plugin.getConfig().getBoolean("show-tax")) {
                String msg = MsgUtil.getMessage("player-bought-from-your-store-tax", p.getName(), "" + amount, Util
                        .getItemStackName(shop.getItem()), Util.format((tax * total)));
                if (stock == amount)
                    msg += "\n" + MsgUtil.getMessage("shop-out-of-stock", "" + shop.getLocation().getBlockX(), "" + shop
                            .getLocation().getBlockY(), "" + shop.getLocation().getBlockZ(), Util
                            .getItemStackName(shop.getItem()));
                MsgUtil.send(shop.getOwner(), msg, shop.isUnlimited());
            } else {
                String msg = MsgUtil.getMessage("player-bought-from-your-store", p.getName(), "" + amount, Util
                        .getItemStackName(shop.getItem()));
                if (stock == amount)
                    msg += "\n" + MsgUtil.getMessage("shop-out-of-stock", "" + shop.getLocation().getBlockX(), "" + shop
                            .getLocation().getBlockY(), "" + shop.getLocation().getBlockZ(), Util
                            .getItemStackName(shop.getItem()));
                MsgUtil.send(shop.getOwner(), msg, shop.isUnlimited());
            }
            // Transfers the item from A to B

        }
        shop.sell(p, amount);
        MsgUtil.sendPurchaseSuccess(p, shop, amount);
        ShopSuccessPurchaseEvent se = new ShopSuccessPurchaseEvent(shop, p, amount, tax * total);
        Bukkit.getPluginManager().callEvent(se);
        plugin.log(p.getName() + " bought " + amount + " for " + (shop.getPrice() * amount) + " from " + shop.toString());
    }

    @SuppressWarnings("deprecation")
    private void actionCreate(@NotNull Player p, @NotNull HashMap<UUID, Info> actions2, @NotNull Info info, @NotNull String message) {
        Util.debugLog("actionCreate");
        try {
            // Checking the shop can be created
            Util.debugLog("Calling for protection check...");
            //Fix openInv compatiable issue

            plugin.getCompatibilityTool().toggleInteractListeners(false);

            if (!plugin.getPermissionChecker().canBuild(p, info.getLocation(), true)) {
                p.sendMessage(MsgUtil.getMessage("no-permission") + ": BUILD CHECK");
                Util.debugLog("Failed to create shop: Protection check failed:");
                for (RegisteredListener belisteners : BlockBreakEvent.getHandlerList().getRegisteredListeners()) {
                    Util.debugLog(belisteners.getPlugin().getName());
                }
                return;
            }

            plugin.getCompatibilityTool().toggleInteractListeners(true);

            if (plugin.getShopManager().getShop(info.getLocation()) != null) {
                p.sendMessage(MsgUtil.getMessage("shop-already-owned"));
                return;
            }
            if (Util.getSecondHalf(info.getLocation().getBlock()) != null
                    && !p.hasPermission("quickshop.create.double")) {
                p.sendMessage(MsgUtil.getMessage("no-double-chests"));
                return;
            }
            if (!Util.canBeShop(info.getLocation().getBlock())) {
                p.sendMessage(MsgUtil.getMessage("chest-was-removed"));
                return;
            }
            if (info.getLocation().getWorld().getBlockAt(info.getLocation()).getType() == Material.ENDER_CHEST) {
                if (!p.hasPermission("quickshop.create.enderchest"))
                    return;
            }

            // allow-shop-without-space-for-sign check
            if (plugin.getConfig().getBoolean("shop.auto-sign")
                    && !plugin.getConfig().getBoolean("allow-shop-without-space-for-sign")) {
                if (info.getSignBlock() == null) {
                    p.sendMessage(MsgUtil.getMessage("failed-to-put-sign"));
                    return;
                }
                Material signType = info.getSignBlock().getType();
                if (signType != Material.AIR && signType != Material.CAVE_AIR
                        && signType != Material.VOID_AIR && signType != Material.WATER) {
                    p.sendMessage(MsgUtil.getMessage("failed-to-put-sign"));
                    return;
                }
            }
            // Price per item
            double price;
            if (plugin.getConfig().getBoolean("whole-number-prices-only")) {
                price = Integer.parseInt(message);
            } else {
                price = Double.parseDouble(message);
            }
            if (price < 0.01) {
                p.sendMessage(MsgUtil.getMessage("price-too-cheap"));
                return;
            }
            double price_limit = plugin.getConfig().getInt("shop.maximum-price");
            if (price_limit != -1) {
                if (price > price_limit) {
                    p.sendMessage(MsgUtil.getMessage("price-too-high", String.valueOf(price_limit)));
                    return;
                }
            }
            // Check price restriction
            Entry<Double, Double> priceRestriction = Util.getPriceRestriction(info.getItem().getType());
            if (priceRestriction != null) {
                if (price < priceRestriction.getKey() || price > priceRestriction.getValue()) {
                    // p.sendMessage(ChatColor.RED+"Restricted prices for
                    // "+info.getItem().getType()+": min "+priceRestriction.getKey()+", max
                    // "+priceRestriction.getValue());
                    p.sendMessage(MsgUtil.getMessage("restricted-prices", Util.getItemStackName(info.getItem()),
                            String.valueOf(priceRestriction.getKey()), String.valueOf(priceRestriction.getValue())));
                }
            }

            double createCost = plugin.getConfig().getDouble("shop.cost");
            // Tax refers to the cost to create a shop. Not actual
            // tax, that would be silly
            if (createCost != 0 && plugin.getEconomy().getBalance(p.getUniqueId()) < createCost) {
                p.sendMessage(MsgUtil.getMessage("you-cant-afford-a-new-shop", format(createCost)));
                return;
            }
            // Create the sample shop.
            ContainerShop shop = new ContainerShop(info.getLocation(), price, info.getItem(), new ShopModerator(p
                    .getUniqueId()), false, ShopType.SELLING);
            shop.onLoad();
            ShopCreateEvent e = new ShopCreateEvent(shop, p);
            Bukkit.getPluginManager().callEvent(e);
            if (e.isCancelled()) {
                shop.onUnload();
                return;
            }

            // This must be called after the event has been called.
            // Else, if the event is cancelled, they won't get their
            // money back.
            if (createCost != 0) {
                if (!plugin.getEconomy().withdraw(p.getUniqueId(), createCost)) {
                    p.sendMessage(MsgUtil.getMessage("you-cant-afford-a-new-shop", format(createCost)));
                    shop.onUnload();
                    return;
                }
                try {
                    plugin.getEconomy().deposit(
                            Bukkit.getOfflinePlayer(plugin.getConfig().getString("tax-account")).getUniqueId(), createCost);
                } catch (Exception e2) {
                    e2.printStackTrace();
                    plugin.getLogger().log(Level.WARNING,
                            "QuickShop can't pay tax to account in config.yml, Please set tax account name to a existing player!");
                }
            }

            /* The shop has hereforth been successfully created */
            createShop(shop, info);
            Location loc = shop.getLocation();
            plugin.log(p.getName() + " created a " + Util.getItemStackName(shop.getItem()) + " shop at ("
                    + loc.getWorld().getName() + " - " + loc.getX() + "," + loc.getY() + "," + loc.getZ() + ")");
            if (!plugin.getConfig().getBoolean("shop.lock")) {
                // Warn them if they haven't been warned since
                // reboot
                if (!plugin.getWarnings().contains(p.getName())) {
                    p.sendMessage(MsgUtil.getMessage("shops-arent-locked"));
                    plugin.getWarnings().add(p.getName());
                }
            }
            // Figures out which way we should put the sign on and
            // sets its text.

            if (shop.isDoubleShop()) {
                Shop nextTo = shop.getAttachedShop();
                if (nextTo.getPrice() > shop.getPrice()) {
                    // The one next to it must always be a
                    // buying shop.
                    p.sendMessage(MsgUtil.getMessage("buying-more-than-selling"));
                }
            }
        }
        /* They didn't enter a number. */ catch (NumberFormatException ex) {
            p.sendMessage(MsgUtil.getMessage("shop-creation-cancelled"));
        }
    }

    /**
     * Returns a new shop iterator object, allowing iteration over shops easily,
     * instead of sorting through a 3D hashmap.
     *
     * @return a new shop iterator object.
     */
    public Iterator<Shop> getShopIterator() {
        return new ShopIterator();
    }

    /**
     * Format the price use economy system
     *
     * @param d price
     * @return formated price
     */
    public String format(double d) {
        return plugin.getEconomy().format(d);
    }

    public class ShopIterator implements Iterator<Shop> {
        private Iterator<Shop> shops;
        private Iterator<HashMap<Location, Shop>> chunks;
        private Iterator<HashMap<ShopChunk, HashMap<Location, Shop>>> worlds;
        private Shop current;

        public ShopIterator() {
            worlds = getShops().values().iterator();
        }

        /**
         * Returns true if there is still more shops to iterate over.
         */
        @Override
        public boolean hasNext() {
            if (shops == null || !shops.hasNext()) {
                if (chunks == null || !chunks.hasNext()) {
                    if (!worlds.hasNext()) {
                        return false;
                    } else {
                        chunks = worlds.next().values().iterator();
                        return hasNext();
                    }
                } else {
                    shops = chunks.next().values().iterator();
                    return hasNext();
                }
            }
            return true;
        }

        /**
         * Fetches the next shop. Throws NoSuchElementException if there are no
         * more shops.
         */
        @Override
        public Shop next() {
            if (shops == null || !shops.hasNext()) {
                if (chunks == null || !chunks.hasNext()) {
                    if (!worlds.hasNext()) {
                        throw new NoSuchElementException("No more shops to iterate over!");
                    }
                    chunks = worlds.next().values().iterator();
                }
                shops = chunks.next().values().iterator();
            }
            if (!shops.hasNext())
                return this.next(); // Skip to the next one (Empty iterator?)
            current = shops.next();
            return current;
        }
    }
}
