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

import java.util.ArrayList;
import java.util.List;

public class RankShopGUI implements Listener {
    
    private final RankPlugin plugin;
    private final RankManager rankManager;
    private final Economy economy;
    
    public RankShopGUI(RankPlugin plugin, RankManager rankManager, Economy economy) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.economy = economy;
    }
    
    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lランクショップ");
        
        int slot = 10;
        for (RankManager.RankInfo rank : rankManager.getRanks().values()) {
            ItemStack item = createRankItem(rank);
            inv.setItem(slot, item);
            slot++;
            if (slot == 17) slot = 19;
        }
        
        // 閉じるボタン
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.setDisplayName("§c閉じる");
        closeItem.setItemMeta(closeMeta);
        inv.setItem(26, closeItem);
        
        player.openInventory(inv);
    }
    
    private ItemStack createRankItem(RankManager.RankInfo rank) {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        
        meta.setDisplayName("§6§l" + rank.getDisplayName());
        
        List<String> lore = new ArrayList<>();
        lore.add("§7");
        lore.add("§eTier: §f" + rank.getTier());
        lore.add("§e期間: §f" + rank.getDurationText());
        lore.add("§e通常価格: §a$" + String.format("%.2f", rank.getPrice()));
        lore.add("§7");
        
        // プレイヤーの現在のランクをチェック
        Player viewer = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTitle().equals("§6§lランクショップ")) {
                viewer = p;
                break;
            }
        }
        
        if (viewer != null) {
            RankManager.UpgradeInfo upgradeInfo = rankManager.checkUpgrade(viewer, rank);
            
            if (!upgradeInfo.canUpgrade()) {
                lore.add("§c✗ 購入不可（同等以下のランクを所持）");
                lore.add("§7");
            } else if (upgradeInfo.isUpgrade()) {
                lore.add("§b§l⬆ アップグレード可能！");
                lore.add("§7現在: §e" + upgradeInfo.getCurrentRank().getDisplayName());
                lore.add("§aアップグレード費用: §2$" + String.format("%.2f", upgradeInfo.getUpgradeCost()));
                lore.add("§7(通常 §7$" + String.format("%.2f", rank.getPrice()) + "§7)");
                lore.add("§7");
                lore.add("§e左クリック: §a差額でアップグレード");
                lore.add("§eShift+左クリック: §bギフト用に購入");
                lore.add("§7");
            } else {
                lore.add("§e左クリック: §a購入");
                lore.add("§eShift+左クリック: §bギフト用に購入");
                lore.add("§7");
            }
        } else {
            lore.add("§e左クリック: §a自分用に購入");
            lore.add("§eShift+左クリック: §bギフト用に購入");
            lore.add("§7");
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals("§6§lランクショップ")) return;
        
        event.setCancelled(true);
        
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // 閉じるボタン
        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }
        
        // ランクアイテムをクリック
        if (clicked.getType() == Material.DIAMOND) {
            String displayName = clicked.getItemMeta().getDisplayName();
            RankManager.RankInfo rank = findRankByDisplayName(displayName);
            
            if (rank == null) return;
            
            boolean isShiftClick = event.isShiftClick();
            
            if (isShiftClick) {
                // ギフト用購入
                handleGiftPurchase(player, rank);
            } else {
                // 自分用購入
                handleSelfPurchase(player, rank);
            }
        }
    }
    
    private RankManager.RankInfo findRankByDisplayName(String displayName) {
        String cleaned = displayName.replace("§6§l", "");
        for (RankManager.RankInfo rank : rankManager.getRanks().values()) {
            if (rank.getDisplayName().equals(cleaned)) {
                return rank;
            }
        }
        return null;
    }
    
    private void handleSelfPurchase(Player player, RankManager.RankInfo rank) {
        RankManager.UpgradeInfo upgradeInfo = rankManager.checkUpgrade(player, rank);
        
        if (!upgradeInfo.canUpgrade()) {
            player.sendMessage("§c同等以下のランクを既に所持しています。");
            player.closeInventory();
            return;
        }
        
        double cost = upgradeInfo.isUpgrade() ? upgradeInfo.getUpgradeCost() : rank.getPrice();
        double balance = economy.getBalance(player);
        
        if (balance < cost) {
            player.sendMessage("§c残高が足りません！ 必要: §e$" + 
                String.format("%.2f", cost) + " §c所持: §e$" + 
                String.format("%.2f", balance));
            player.closeInventory();
            return;
        }
        
        economy.withdrawPlayer(player, cost);
        
        rankManager.grantRank(player, rank.getKey(), null).thenAccept(success -> {
            if (success) {
                if (upgradeInfo.isUpgrade()) {
                    player.sendMessage("§a§l[✓] §6" + rank.getDisplayName() + 
                        " §aにアップグレードしました！ §7(差額: §2$" + String.format("%.2f", cost) + "§7)");
                } else {
                    player.sendMessage("§a§l[✓] §6" + rank.getDisplayName() + 
                        " §aランクを購入しました！");
                }
            } else {
                player.sendMessage("§cランクの付与に失敗しました。");
                economy.depositPlayer(player, cost);
            }
        });
        
        player.closeInventory();
    }
    
    private void handleGiftPurchase(Player player, RankManager.RankInfo rank) {
        double balance = economy.getBalance(player);
        
        if (balance < rank.getPrice()) {
            player.sendMessage("§c残高が足りません！ 必要: §e$" + 
                String.format("%.2f", rank.getPrice()) + " §c所持: §e$" + 
                String.format("%.2f", balance));
            player.closeInventory();
            return;
        }
        
        player.closeInventory();
        
        // ギフト対象選択GUIを開く
        GiftGUI giftGUI = new GiftGUI(plugin, rankManager, economy);
        giftGUI.openGiftTargetMenu(player, rank);
    }
}