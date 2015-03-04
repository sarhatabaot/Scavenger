package com.cnaude.scavenger;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.ResidencePermissions;
import com.comphenix.protocol.utility.StreamSerializer;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;

import java.io.*;
import java.util.*;
import mc.alk.arena.BattleArena;
import mc.alk.arena.competition.match.Match;
import me.cnaude.plugin.Scavenger.RestorationS1;
import me.drayshak.WorldInventories.api.WorldInventoriesAPI;
import net.dmulloy2.ultimatearena.UltimateArenaAPI;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import uk.co.tggl.pluckerpluck.multiinv.MultiInvAPI;

public final class RestorationManager implements Serializable {

    Scavenger plugin;
    private static final HashMap<String, Restoration> restorations = new HashMap<>();

    final String PERM_DROP_PREFIX = "scavenger.drop.";
    final String PERM_DROP_ALL = "scavenger.drop.*";
    final String PERM_KEEP_PREFIX = "scavenger.keep.";
    final String PERM_KEEP_ALL = "scavenger.keep.*";
    final String PERM_SCAVENGE_PREFIX = "scavenger.scavenge.";
    final String PERM_SCAVENGE = "scavenger.scavenge";
    final String PERM_FREE = "scavenger.free";
    final String PERM_NO_CHANCE = "scavenger.nochance";
    final String PERM_LEVEL = "scavenger.level";
    final String PERM_EXP = "scavenger.exp";

    public RestorationManager(Scavenger plugin) {
        this.plugin = plugin;
        this.load();
    }

    public void save() {
        StreamSerializer serializer = new StreamSerializer();
        HashMap<String, RestorationS1> restorationsForDisk = new HashMap<>();
        for (Map.Entry<String, Restoration> entry : restorations.entrySet()) {
            String key = entry.getKey();
            Restoration value = entry.getValue();
            RestorationS1 tmpRestoration = new RestorationS1();
            for (ItemStack i : value.inventory) {
                boolean error = false;
                if (i instanceof ItemStack) {
                    plugin.debugMessage("Serializing: " + i.toString());
                    try {
                        tmpRestoration.inventory.add(serializer.serializeItemStack(i));
                    } catch (IOException e) {
                        plugin.logError(e.getMessage());
                        error = true;
                    }
                    if (error) {
                        plugin.logError("Problem serializing item: " + i.toString());
                    }
                    plugin.debugMessage("Done: " + i.toString());
                }
            }
            for (ItemStack i : value.armour) {
                if (i instanceof ItemStack) {
                    plugin.debugMessage("Serializing: " + i.toString());
                    try {
                        tmpRestoration.armour.add(serializer.serializeItemStack(i));
                    } catch (IOException e) {
                        plugin.logError(e.getMessage());
                    }
                    plugin.debugMessage("Done: " + i.toString());
                }
            }
            tmpRestoration.enabled = value.enabled;
            tmpRestoration.level = value.level;
            tmpRestoration.exp = value.exp;
            restorationsForDisk.put(key, tmpRestoration);
            plugin.logInfo("Saving " + key + "'s inventory to disk.");
        }
        try {
            File file = new File("plugins/Scavenger/inv1.ser");
            FileOutputStream f_out = new FileOutputStream(file);
            try (ObjectOutputStream obj_out = new ObjectOutputStream(f_out)) {
                obj_out.writeObject(restorationsForDisk);
            }
        } catch (IOException e) {
            plugin.logError(e.getMessage());
        }
    }

    public void load() {
        StreamSerializer serializer = new StreamSerializer();
        HashMap<String, RestorationS1> restorationsFromDisk;
        File file = new File("plugins/Scavenger/inv1.ser");
        if (!file.exists()) {
            plugin.logDebug("Recovery file '" + file.getAbsolutePath() + "' does not exist.");
            return;
        }
        try {
            FileInputStream f_in = new FileInputStream(file);
            try (ObjectInputStream obj_in = new ObjectInputStream(f_in)) {
                restorationsFromDisk = (HashMap<String, RestorationS1>) obj_in.readObject();
            }
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            plugin.logError(e.getMessage());
            return;
        }

        for (Map.Entry<String, RestorationS1> entry : restorationsFromDisk.entrySet()) {
            String key = entry.getKey();
            RestorationS1 value = entry.getValue();
            Restoration tmpRestoration = new Restoration();
            tmpRestoration.inventory = new ItemStack[value.inventory.size()];
            tmpRestoration.armour = new ItemStack[value.armour.size()];

            for (int i = 0; i < value.inventory.size(); i++) {
                if (value.inventory.get(i) instanceof String) {
                    boolean error = false;
                    ItemStack tmpStack = new ItemStack(Material.AIR);
                    plugin.debugMessage("Deserializing: " + value.inventory.get(i));
                    try {
                        tmpStack = serializer.deserializeItemStack(value.inventory.get(i));
                    } catch (Exception e) {
                        plugin.logError(e.getMessage() + " => " + value.inventory.get(i));
                        error = true;
                    } catch (Throwable e) {
                        plugin.logError(e.getMessage() + " => " + value.inventory.get(i));
                        error = true;
                    }
                    if (tmpStack == null || error) {
                        tmpRestoration.inventory[i] = new ItemStack(Material.AIR);
                    } else {
                        tmpRestoration.inventory[i] = tmpStack;
                    }
                    plugin.debugMessage("Done: " + tmpRestoration.inventory[i].toString());
                }
            }

            for (int i = 0; i < value.armour.size(); i++) {
                if (value.armour.get(i) instanceof String) {
                    ItemStack tmpStack = new ItemStack(Material.AIR);
                    plugin.debugMessage("Deserializing: " + value.armour.get(i));
                    try {
                        tmpStack = serializer.deserializeItemStack(value.armour.get(i));
                    } catch (IOException e) {
                        plugin.logError(e.getMessage());
                    }
                    if (tmpStack == null) {
                        tmpRestoration.armour[i] = new ItemStack(Material.AIR);
                    } else {
                        tmpRestoration.armour[i] = tmpStack;
                    }
                    plugin.debugMessage("Done: " + tmpRestoration.armour[i].toString());
                }
            }

            tmpRestoration.enabled = value.enabled;
            tmpRestoration.level = value.level;
            tmpRestoration.exp = value.exp;

            restorations.put(key, tmpRestoration);
            plugin.logInfo("Loading " + key + "'s inventory from disk.");
        }
    }

    public boolean multipleInventories() {
        return plugin.getMultiInvAPI() != null
                || plugin.myWorldsHook != null
                || plugin.getWorldInvAPI();
    }

    public boolean hasRestoration(Player player) {
        UUID uuid = player.getUniqueId();
        if (multipleInventories()) {
            return restorations.containsKey(uuid + "." + getWorldGroups(player));
        }
        return restorations.containsKey(uuid.toString());
    }

    public Restoration getRestoration(Player player) {
        UUID uuid = player.getUniqueId();
        Restoration restoration = new Restoration();
        restoration.enabled = false;
        if (multipleInventories()) {
            String keyName = uuid + "." + getWorldGroups(player);
            if (restorations.containsKey(keyName)) {
                plugin.logDebug("Getting: " + keyName);
                restoration = restorations.get(keyName);
            }
        }
        if (!restoration.enabled) {
            String keyName = player.getUniqueId().toString();
            if (restorations.containsKey(keyName)) {
                plugin.logDebug("Getting: " + keyName + ":" + player.getName());
                restoration = restorations.get(keyName);
            }
        }
        return restoration;
    }

    public void collect(Player player, List<ItemStack> itemDrops, EntityDeathEvent event) {
        if (itemDrops.isEmpty() && !levelAllow(player) && !expAllow(player)) {
            return;
        }

        if (plugin.config.dropOnPVPDeath()) {
            if (player.getKiller() instanceof Player) {
                plugin.message(player, plugin.config.msgPVPDeath());
                return;
            }
        }

        if (plugin.config.residence()) {
            ClaimedResidence res = Residence.getResidenceManager().getByLoc(player.getLocation());
            if (res != null) {
                ResidencePermissions perms = res.getPermissions();
                if (perms.playerHas(player.getName(), plugin.config.resFlag(), true)) {
                    plugin.logDebug("Player '" + player.getName() + "' is not allowed to use Scavenger in this residence. Items will be dropped.");
                    plugin.message(player, plugin.config.msgInsideRes());
                    return;
                } else {
                    plugin.logDebug("Player '" + player.getName() + "' is allowed to use Scavenger in this residence.");

                }
            }
        }

        if (plugin.config.factionEnemyDrops() && plugin.factionHook != null) {
            if (plugin.factionHook.isPlayerInEnemyFaction(player)) {
                return;
            }
        }

        if (plugin.config.dungeonMazeDrops() && plugin.dmHook != null) {
            plugin.logDebug("Checking if '" + player.getName() + "' is in DungeonMaze.");
            if (plugin.dmHook.isPlayerInDungeon(player)) {
                plugin.logDebug("Player '" + player.getName() + "' is in DungeonMaze.");
                plugin.message(player, plugin.config.msgInsideDungeonMaze());
                return;
            }
        }

        if (plugin.getWorldGuard() != null) {
            plugin.logDebug("Checking region support for '" + player.getWorld().getName() + "'");
            if (plugin.getWorldGuard().getRegionManager(player.getWorld()) != null) {
                try {
                    RegionManager regionManager = plugin.getWorldGuard().getRegionManager(player.getWorld());
                    ApplicableRegionSet set = regionManager.getApplicableRegions(player.getLocation());
                    if (set.allows(DefaultFlag.PVP) && plugin.config.wgPVPIgnore()) {
                        plugin.logDebug("This is a WorldGuard PVP zone and WorldGuardPVPIgnore is " + plugin.config.wgPVPIgnore());
                        if (!plugin.config.msgInsideWGPVP().isEmpty()) {
                            plugin.message(player, plugin.config.msgInsideWGPVP());
                        }
                        return;
                    }
                    if (!set.allows(DefaultFlag.PVP) && plugin.config.wgGuardPVPOnly()) {
                        plugin.logDebug("This is NOT a WorldGuard PVP zone and WorldGuardPVPOnly is " + plugin.config.wgGuardPVPOnly());
                        if (!plugin.config.msgInsideWGPVP().isEmpty()) {
                            plugin.message(player, plugin.config.msgInsideWGPVPOnly());
                        }
                        return;
                    }
                } catch (NullPointerException ex) {
                    plugin.logDebug(ex.getMessage());
                }
            } else {
                plugin.logDebug("Region support disabled for '" + player.getWorld().getName() + "'");
            }
        }

        if (plugin.getUltimateArena() != null) {
            plugin.getUltimateArena();
            if (UltimateArenaAPI.hookIntoUA(plugin).isPlayerInArenaLocation(player)) {
                if (!plugin.config.msgInsideUA().isEmpty()) {
                    plugin.message(player, plugin.config.msgInsideUA());
                }
                return;
            }
        }

        if (plugin.maHandler != null && plugin.maHandler.isPlayerInArena(player)) {
            if (!plugin.config.msgInsideMA().isEmpty()) {
                plugin.message(player, plugin.config.msgInsideMA());
            }
            return;
        }

        if (plugin.pvpHandler != null && !net.slipcor.pvparena.api.PVPArenaAPI.getArenaName(player).equals("")) {
            String x = plugin.config.msgInsidePA();
            if (!x.isEmpty()) {
                x = x.replaceAll("%ARENA%", net.slipcor.pvparena.api.PVPArenaAPI.getArenaName(player));
                plugin.message(player, x);
            }
            return;
        }

        if (plugin.battleArena) {
            mc.alk.arena.objects.ArenaPlayer ap = mc.alk.arena.BattleArena.toArenaPlayer(player);
            if (ap != null) {
                Match match = BattleArena.getBAController().getMatch(ap);
                if (match != null) {
                    if (match.isInMatch(ap)) {
                        String x = plugin.config.msgInsideBA();
                        if (!x.isEmpty()) {
                            plugin.message(player, x);
                        }
                        return;
                    }
                }
            }
        }

        if (plugin.minigames != null) {
            if (plugin.minigames.playerInMinigame(player)) {
                plugin.logInfo("Player '" + player.getName() + "' is in a Minigame. Not recovering items.");
                return;
            }
        }

        if (hasRestoration(player)) {
            plugin.error(player, "Restoration already exists, ignoring.");
            return;
        }

        if (plugin.getEconomy() != null
                && !(player.hasPermission(PERM_FREE)
                || (player.isOp() && plugin.config.opsAllPerms()))
                && plugin.config.economyEnabled()) {
            double restoreCost = plugin.config.restoreCost();
            double withdrawAmount;
            double playeBalance = plugin.getEconomy().getBalance(player.getName());
            double percentCost = plugin.config.percentCost();
            double minCost = plugin.config.minCost();
            double maxCost = plugin.config.maxCost();
            EconomyResponse er;
            String currency;
            if (plugin.config.percent()) {
                withdrawAmount = playeBalance * (percentCost / 100.0);
                if (plugin.config.addMin()) {
                    withdrawAmount = withdrawAmount + minCost;
                } else if (withdrawAmount < minCost) {
                    withdrawAmount = minCost;
                }
                if (withdrawAmount > maxCost && maxCost > 0) {
                    withdrawAmount = maxCost;
                }
            } else {
                withdrawAmount = restoreCost;
            }
            er = plugin.getEconomy().withdrawPlayer(player.getName(), withdrawAmount);
            if (er.transactionSuccess()) {
                if (withdrawAmount == 1) {
                    currency = plugin.getEconomy().currencyNameSingular();
                } else {
                    currency = plugin.getEconomy().currencyNamePlural();
                }
                String x = plugin.config.msgSaveForFee();
                if (!x.isEmpty()) {
                    x = x.replaceAll("%COST%", String.format("%.2f", withdrawAmount));
                    x = x.replaceAll("%CURRENCY%", currency);
                    plugin.message(player, x);
                }
                if (!plugin.config.depositDestination().isEmpty()) {
                    plugin.logDebug("DepositDesination: " + plugin.config.depositDestination());
                    if (plugin.config.depositType().equalsIgnoreCase("bank")) {
                        plugin.logDebug("DepositType: BANK");
                        if (plugin.getEconomy().hasBankSupport()) {
                            plugin.logDebug("Bank support is enabled");
                            plugin.getEconomy().bankDeposit(plugin.config.depositDestination(), withdrawAmount);
                        } else {
                            plugin.logDebug("Bank support is NOT enabled");
                        }
                    } else if (plugin.config.depositType().equalsIgnoreCase("player")) {
                        plugin.logDebug("DepositType: PLAYER");
                        plugin.logDebug("DepositDestination: " + plugin.config.depositDestination());
                        if (plugin.getEconomy().hasAccount(plugin.config.depositDestination())) {
                            plugin.logDebug("DepositDestination: VALID");
                            plugin.getEconomy().depositPlayer(plugin.config.depositDestination(), withdrawAmount);
                        } else {
                            plugin.logDebug("DepositDestination: INVALID");
                        }
                    } else {
                        plugin.logDebug("DepositType: INVALID");
                    }
                } else {
                    plugin.logDebug("No deposit destination!");
                }
            } else {
                if (playeBalance == 1) {
                    currency = plugin.getEconomy().currencyNameSingular();
                } else {
                    currency = plugin.getEconomy().currencyNamePlural();
                }
                String x = plugin.config.msgNotEnoughMoney();
                if (!x.isEmpty()) {
                    x = x.replaceAll("%BALANCE%", String.format("%.2f", playeBalance));
                    x = x.replaceAll("%COST%", String.format("%.2f", withdrawAmount));
                    x = x.replaceAll("%CURRENCY%", currency);
                    plugin.message(player, x);
                }
                return;
            }
        } else {
            plugin.message(player, plugin.config.msgSaving());
        }

        Restoration restoration = new Restoration();
        restoration.enabled = false;

        restoration.inventory = player.getInventory().getContents();
        restoration.armour = player.getInventory().getArmorContents();
        itemDrops.clear();

        if (levelAllow(player)) {
            plugin.logDebug("Collecting level " + player.getLevel() + " for " + player.getName());
            restoration.level = player.getLevel();
        }
        if (expAllow(player)) {
            plugin.logDebug("Collecting exp " + player.getExp() + " for " + player.getName());
            restoration.exp = player.getExp();
            event.setDroppedExp(0);
        }

        String deathCause = "NULL";
        if (player.getLastDamageCause() != null) {
            if (player.getLastDamageCause().getCause() != null) {
                deathCause = player.getLastDamageCause().getCause().toString();
            }
        }
        String deathCausePermission = PERM_SCAVENGE_PREFIX + deathCause;
        plugin.logDebug("[" + player.getName() + "] scavenger.scavenge = " + player.hasPermission(PERM_SCAVENGE));
        plugin.logDebug("[" + player.getName() + "] " + deathCausePermission + " = " + player.hasPermission(deathCausePermission));
        if (player.hasPermission(PERM_SCAVENGE) || player.hasPermission(deathCausePermission)) {
            plugin.logDebug("Permission okay...");
            if (plugin.config.singleItemDrops()) {
                checkSingleItemDrops(player, restoration, itemDrops);
            }
            if (plugin.config.singleItemKeeps()) {
                checkSingleItemKeeps(player, restoration, itemDrops);
            }
            if (plugin.config.chanceToDrop() > 0 && !player.hasPermission(PERM_NO_CHANCE)) {
                checkChanceToDropItems(player, restoration, itemDrops);
            }
            if (plugin.config.slotBasedRecovery()) {
                checkSlots(player, "armour", restoration.armour, itemDrops);
                checkSlots(player, "inv", restoration.inventory, itemDrops);
            }
        } else {
            ItemStack[][] invAndArmour = {restoration.inventory, restoration.armour};
            for (ItemStack[] a : invAndArmour) {
                for (ItemStack i : a) {
                    if (i instanceof ItemStack && !i.getType().equals(Material.AIR)) {
                        plugin.debugMessage(player, "Dropping item " + i.getType());
                        itemDrops.add(i.clone());
                        i.setAmount(0);
                    }
                }
            }
        }
        addRestoration(player, restoration);
    }

    private void checkChanceToDropItems(Player player, Restoration restoration, List<ItemStack> itemDrops) {
        ItemStack[][] invAndArmour = {restoration.inventory, restoration.armour};
        for (ItemStack[] a : invAndArmour) {
            for (ItemStack i : a) {
                if (i instanceof ItemStack && !i.getType().equals(Material.AIR)) {
                    Random randomGenerator = new Random();
                    int randomInt = randomGenerator.nextInt(plugin.config.chanceToDrop()) + 1;
                    plugin.debugMessage(player, "Random number is " + randomInt);
                    if (randomInt == plugin.config.chanceToDrop()) {
                        plugin.debugMessage(player, "Randomly dropping item " + i.getType());
                        itemDrops.add(i.clone());
                        i.setAmount(0);
                    } else {
                        plugin.debugMessage(player, "Randomly keeping item " + i.getType());
                    }
                }
            }
        }
    }

    private void checkSlots(Player p, String type, ItemStack[] itemStackArray, List<ItemStack> itemDrops) {
        for (int i = 0; i < itemStackArray.length; i++) {
            String itemType;
            if (itemStackArray[i] != null) {
                itemType = itemStackArray[i].getType().toString();
            } else {
                itemType = "NULL";
            }
            plugin.debugMessage("[type:" + type + "] [p:" + p.getName() + "] [slot:" + i + "] [item:"
                    + itemType + "] [perm:" + p.hasPermission("scavenger." + type + "." + i) + "]");
            if (!p.hasPermission("scavenger." + type + "." + i) && !itemType.equals("NULL")) {
                if (!itemType.equals("NULL") && !itemType.equals("AIR")) {
                    plugin.debugMessage(p, "[cs]Dropping slot " + i + ": " + itemType);
                }
                itemDrops.add(itemStackArray[i].clone());
                itemStackArray[i].setAmount(0);
            } else if (!itemType.equals("NULL") && !itemType.equals("AIR")) {
                plugin.debugMessage(p, "[cs]Keeping slot " + i + ": " + itemType);
            }
        }
    }

    private void checkSingleItemDrops(Player player, Restoration restoration, List<ItemStack> itemDrops) {
        plugin.logDebug("singleItemDrops()");
        ItemStack[][] invAndArmour = {restoration.inventory, restoration.armour};
        for (ItemStack[] items : invAndArmour) {
            for (ItemStack itemStack : items) {
                if (itemStack instanceof ItemStack && !itemStack.getType().equals(Material.AIR)) {
                    plugin.logDebug(
                            "[p:" + player.getName() + "] "
                            + "[" + PERM_DROP_PREFIX + itemStack.getTypeId() + ":"
                            + player.hasPermission(PERM_DROP_PREFIX + itemStack.getTypeId()) + "] "
                            + "[" + PERM_DROP_PREFIX + itemStack.getType().name().toLowerCase() + ":"
                            + player.hasPermission(PERM_DROP_PREFIX + itemStack.getType().name().toLowerCase()) + "] "
                            + "[" + PERM_DROP_PREFIX + itemStack.getType().name() + ":"
                            + player.hasPermission(PERM_DROP_PREFIX + itemStack.getType().name()) + "] "
                            + "[" + PERM_DROP_ALL + ":" + player.hasPermission(PERM_DROP_ALL) + "]");
                    if (player.hasPermission(PERM_DROP_PREFIX + itemStack.getTypeId())
                            || player.hasPermission(PERM_DROP_PREFIX + itemStack.getType().name().toLowerCase())
                            || player.hasPermission(PERM_DROP_PREFIX + itemStack.getType().name())
                            || player.hasPermission(PERM_DROP_ALL)) {
                        plugin.debugMessage(player, "[sd]Dropping item " + itemStack.getType());
                        itemDrops.add(itemStack.clone());
                        itemStack.setAmount(0);
                    } else {
                        plugin.debugMessage(player, "[sd]Keeping item " + itemStack.getType());
                    }
                }
            }
        }
    }

    private void checkSingleItemKeeps(Player player, Restoration restoration, List<ItemStack> itemDrops) {
        plugin.logDebug("singleItemKeeps()");
        ItemStack[][] invAndArmour = {restoration.inventory, restoration.armour};
        for (ItemStack[] items : invAndArmour) {
            for (ItemStack itemStack : items) {
                if (itemStack instanceof ItemStack && !itemStack.getType().equals(Material.AIR)) {
                    plugin.logDebug(
                            "[p:" + player.getName() + "] "
                            + "[" + PERM_KEEP_PREFIX + itemStack.getTypeId() + ":"
                            + player.hasPermission(PERM_KEEP_PREFIX + itemStack.getTypeId()) + "] "
                            + "[" + PERM_KEEP_PREFIX + itemStack.getType().name().toLowerCase() + ":"
                            + player.hasPermission(PERM_KEEP_PREFIX + itemStack.getType().name().toLowerCase()) + "] "
                            + "[" + PERM_KEEP_PREFIX + itemStack.getType().name() + ":"
                            + player.hasPermission(PERM_KEEP_PREFIX + itemStack.getType().name()) + "] "
                            + "[" + PERM_KEEP_ALL + ":"
                            + player.hasPermission(PERM_KEEP_ALL) + "]");
                    if (player.hasPermission(PERM_KEEP_PREFIX + itemStack.getTypeId())
                            || player.hasPermission(PERM_KEEP_PREFIX + itemStack.getType().name().toLowerCase())
                            || player.hasPermission(PERM_KEEP_PREFIX + itemStack.getType().name())
                            || player.hasPermission(PERM_KEEP_ALL)) {
                        plugin.debugMessage(player, "[sk]Keeping item " + itemStack.getType());
                    } else {
                        plugin.debugMessage(player, "[sk]Dropping item " + itemStack.getType());
                        itemDrops.add(itemStack.clone());
                        itemStack.setAmount(0);
                    }
                }
            }
        }
    }

    public boolean levelAllow(Player p) {
        return (p.hasPermission(PERM_LEVEL)
                || !plugin.config.permsEnabled()
                || (p.isOp() && plugin.config.opsAllPerms())) && p.getLevel() > 0;
    }

    public boolean expAllow(Player p) {
        return (p.hasPermission(PERM_EXP)
                || !plugin.config.permsEnabled()
                || (p.isOp() && plugin.config.opsAllPerms())) && p.getExhaustion() > 0;
    }

    public void printRestorations(CommandSender p) {
        plugin.message(p, "Restorations:");
        for (String key : restorations.keySet()) {
            plugin.message(p, "  " + key);
        }
    }

    public void addRestoration(Player p, Restoration r) {
        UUID uuid = p.getUniqueId();
        if (multipleInventories()) {
            String keyName = uuid + "." + getWorldGroups(p);
            restorations.put(keyName, r);
            plugin.debugMessage("Adding: " + p.getDisplayName() + ":" + keyName);
        } else {
            restorations.put(uuid.toString(), r);
            plugin.debugMessage("Adding: " + p.getDisplayName() + ":" + uuid);
        }
    }

    public void enable(Player p) {
        if (hasRestoration(p)) {
            Restoration restoration = getRestoration(p);
            restoration.enabled = true;
            plugin.debugMessage("Enabling: " + p.getName());
        } else {
            plugin.logDebug("Not enabling: " + p.getName());
        }
    }

    public void restore(Player p) {
        if (!p.isOnline()) {
            plugin.debugMessage("Player " + p.getName() + " is offline. Skipping restore...");
            return;
        }

        Restoration restoration = getRestoration(p);

        if (restoration.enabled) {
            p.getInventory().clear();

            p.getInventory().setContents(restoration.inventory);
            p.getInventory().setArmorContents(restoration.armour);
            if (p.hasPermission(PERM_LEVEL)
                    || !plugin.config.permsEnabled()
                    || (p.isOp() && plugin.config.opsAllPerms())) {
                plugin.logDebug("Player " + p.getName() + " does have " + PERM_LEVEL + " permission.");
                p.setLevel(restoration.level);
                plugin.logDebug("Player " + p.getName() + " gets " + restoration.level + " level.");
            } else {
                plugin.logDebug("Player " + p.getName() + " does NOT have " + PERM_LEVEL + " permission.");
            }
            if (p.hasPermission(PERM_EXP)
                    || !plugin.config.permsEnabled()
                    || (p.isOp() && plugin.config.opsAllPerms())) {
                plugin.logDebug("Player " + p.getName() + " does have " + PERM_EXP + " permission.");
                p.setExp(restoration.exp);
                plugin.logDebug("Player " + p.getName() + " gets " + restoration.exp + " XP.");
            } else {
                plugin.logDebug("Player " + p.getName() + " does NOT have " + PERM_EXP + " permission.");
            }
            if (plugin.config.shouldNotify()) {
                plugin.message(p, plugin.config.msgRecovered());
            }
            removeRestoration(p);
            if (hasRestoration(p)) {
                plugin.message(p, "Restore exists!!!");
            }
        }

    }

    public boolean hasRestoration(UUID uuid) {
        return restorations.containsKey(uuid.toString());
    }

    public void removeRestoration(UUID uuid) {
        if (hasRestoration(uuid)) {
            restorations.remove(uuid.toString());
            plugin.logDebug("Removing: " + uuid);
        }
    }

    public void removeRestoration(Player p) {
        UUID uuid = p.getUniqueId();
        if (multipleInventories()) {
            String keyName = uuid + "." + getWorldGroups(p);
            if (restorations.containsKey(keyName)) {
                restorations.remove(keyName);
                plugin.logDebug("Removing: " + keyName);
            }
        }
        removeRestoration(uuid);
    }

    public String getWorldGroups(Player player) {
        World world = player.getWorld();
        List<String> returnData = new ArrayList<>();
        if (plugin.getMultiInvAPI() != null) {
            String worldname = world.getName();
            MultiInvAPI multiInvAPI = plugin.getMultiInvAPI();
            if (multiInvAPI.getGroups() != null) {
                if (multiInvAPI.getGroups().containsKey(worldname)) {
                    returnData.add(multiInvAPI.getGroups().get(worldname));
                }
            }
        }
        if (plugin.getWorldInvAPI()) {
            String worldname = world.getName();
            try {
                returnData.add(WorldInventoriesAPI.findGroup(worldname).getName());
            } catch (Exception ex) {
            }
        }
        if (plugin.myWorldsHook != null) {
            if (plugin.myWorldsHook.isEnabled()) {
                returnData.add(plugin.myWorldsHook.getLocationName(player.getLocation()));
            }
        }
        if (returnData.isEmpty()) {
            returnData.add("");
        }
        return returnData.get(0);
    }
}
