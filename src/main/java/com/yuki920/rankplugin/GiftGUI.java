package com.yuki920.rankplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;

public class GiftGUI implements Listener {
    
    private final RankPlugin plugin;
    private final RankManager rankManager;
    private final Economy economy;
    private final Map<UUID, Player> giftTargets;
    private final Map<UUID, RankManager.RankInfo> pendingGifts;
    
    public GiftGUI(RankPlugin plugin, RankManager rankManager, Economy economy) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.economy = economy;
        this.giftTargets = new HashMap<>();
        this.pendingGifts = new HashMap<>();
    }
    
    public void openGiftMenu(Player player) {
        // 経済システムが利用できない場合
        if (economy == null) {
            player.sendMessage("§c経済システムが利用できません。");
            player.sendMessage("§c管理者にEssentialsXなどの経済プラグインのインストールを依頼してください。");
            return;
        }
        
        Inventory inv = Bukkit.createInventory(null, 27, "§b§lギフトメニュー");
        
        // オンラインプレイヤー一覧を表示
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(player); // 自分を除外
        
        int slot = 0;
        for (Player target : onlinePlayers) {
            if (slot >= 27) break;
            
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName("§e" + target.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§eクリックしてランクをギフト");
            lore.add("§7");
            meta.setLore(lore);
            
            skull.setItemMeta(meta);
            inv.setItem(slot, skull);
            slot++;
        }
        
        // 閉じるボタン
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c閉じる");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);
        
        player.openInventory(inv);
    }
    
    public void openGiftRankMenu(Player player, Player target) {
        // 経済システムが利用できない場合
        if (economy == null) {
            player.sendMessage("§c経済システムが利用できません。");
            return;
        }
        
        Inventory inv = Bukkit.createInventory(null, 27, "§b§lギフトランク選択 - " + target.getName());
        
        giftTargets.put(player.getUniqueId(), target);
        
        int slot = 10;
        for (RankManager.RankInfo rank : rankManager.getRanks().values()) {
            ItemStack item = createGiftRankItem(rank);
            inv.setItem(slot, item);
            slot++;
            if (slot == 17) slot = 19;
        }
        
        // 戻るボタン
        ItemStack backItem = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backItem.getItemMeta();
        backMeta.setDisplayName("§e戻る");
        backItem.setItemMeta(backMeta);
        inv.setItem(18, backItem);
        
        // 閉じるボタン
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c閉じる");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);
        
        player.openInventory(inv);
    }
    
    public void openGiftTargetMenu(Player player, RankManager.RankInfo rank) {
        pendingGifts.put(player.getUniqueId(), rank);
        
        Inventory inv = Bukkit.createInventory(null, 27, "§b§lギフト先を選択 - " + rank.getDisplayName());
        
        List<Player> onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
        onlinePlayers.remove(player);
        
        int slot = 0;
        for (Player target : onlinePlayers) {
            if (slot >= 26) break;
            
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(target);
            meta.setDisplayName("§e" + target.getName());
            
            List<String> lore = new ArrayList<>();
            lore.add("§7");
            lore.add("§bギフトランク: §6" + rank.getDisplayName());
            lore.add("§e価格: §a$" + String.format("%.2f", rank.getPrice()));
            lore.add("§7");
            lore.add("§eクリックしてギフト");
            lore.add("§7");
            meta.setLore(lore);
            
            skull.setItemMeta(meta);
            inv.setItem(slot, skull);
            slot++;
        }
        
        // 閉じるボタン
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§cキャンセル");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);
        
        player.openInventory(inv);
    }
    
    private ItemStack createGiftRankItem(RankManager.RankInfo rank) {
        ItemStack item = new ItemStack(Material.EMERALD);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6§l" + rank.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§e期間: §f" + rank.getDurationText());
        lore.add("§e価格: §a$" + String.format("%.2f", rank.getPrice()));
        lore.add("§7");
        lore.add("§eクリックしてギフト");
        lore.add("§7");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();
        
        if (title.equals("§b§lギフトメニュー")) {
            handleGiftMenuClick(event, player);
        } else if (title.startsWith("§b§lギフトランク選択")) {
            handleGiftRankSelectClick(event, player);
        } else if (title.startsWith("§b§lギフト先を選択")) {
            handleGiftTargetSelectClick(event, player);
        }
    }
    
    private void handleGiftMenuClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
        
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            Player target = meta.getOwningPlayer().getPlayer();
            
            if (target != null && target.isOnline()) {
                openGiftRankMenu(player, target);
            } else {
                player.sendMessage("§cプレイヤーがオフラインです。");
                player.closeInventory();
            }
        }
    }
    
    private void handleGiftRankSelectClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            giftTargets.remove(player.getUniqueId());
            return;
        }
        
        if (clicked.getType() == Material.ARROW) {
            openGiftMenu(player);
            return;
        }
        
        if (clicked.getType() == Material.EMERALD) {
            String displayName = clicked.getItemMeta().getDisplayName().replace("§6§l", "");
            RankManager.RankInfo rank = findRankByDisplayName(displayName);
            
            if (rank == null) return;
            
            Player target = giftTargets.get(player.getUniqueId());
            if (target == null || !target.isOnline()) {
                player.sendMessage("§cギフト対象が見つかりません。");
                player.closeInventory();
                return;
            }
            
            processGift(player, target, rank);
        }
    }
    
    private void handleGiftTargetSelectClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            pendingGifts.remove(player.getUniqueId());
            return;
        }
        
        if (clicked.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clicked.getItemMeta();
            Player target = meta.getOwningPlayer().getPlayer();
            RankManager.RankInfo rank = pendingGifts.get(player.getUniqueId());
            
            if (target != null && target.isOnline() && rank != null) {
                processGift(player, target, rank);
            } else {
                player.sendMessage("§cエラーが発生しました。");
                player.closeInventory();
            }
        }
    }
    
    private void processGift(Player gifter, Player target, RankManager.RankInfo rank) {
        double balance = economy.getBalance(gifter);
        
        if (balance < rank.getPrice()) {
            gifter.sendMessage("§c残高が足りません！ 必要: §e$" + 
                String.format("%.2f", rank.getPrice()) + " §c所持: §e$" + 
                String.format("%.2f", balance));
            gifter.closeInventory();
            return;
        }
        
        economy.withdrawPlayer(gifter, rank.getPrice());
        
        rankManager.grantRank(target, rank.getKey(), gifter).thenAccept(success -> {
            if (success) {
                gifter.sendMessage("§a§l[✓] §6" + rank.getDisplayName() + 
                    " §aランクを §e" + target.getName() + " §aにギフトしました！");
            } else {
                gifter.sendMessage("§cギフトに失敗しました。");
                economy.depositPlayer(gifter, rank.getPrice());
            }
        });
        
        gifter.closeInventory();
        giftTargets.remove(gifter.getUniqueId());
        pendingGifts.remove(gifter.getUniqueId());
    }
    
    private RankManager.RankInfo findRankByDisplayName(String displayName) {
        for (RankManager.RankInfo rank : rankManager.getRanks().values()) {
            if (rank.getDisplayName().equals(displayName)) {
                return rank;
            }
        }
        return null;
    }
}