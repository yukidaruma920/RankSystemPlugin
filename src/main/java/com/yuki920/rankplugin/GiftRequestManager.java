package com.yuki920.rankplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GiftRequestManager {
    
    private final RankPlugin plugin;
    private final Map<UUID, GiftRequest> pendingRequests;
    private final Map<UUID, Long> lastRequestTime;
    private static final long REQUEST_TIMEOUT = 60000; // 60秒
    
    public GiftRequestManager(RankPlugin plugin) {
        this.plugin = plugin;
        this.pendingRequests = new ConcurrentHashMap<>();
        this.lastRequestTime = new ConcurrentHashMap<>();
        
        // 定期的に期限切れリクエストをクリーンアップ
        Bukkit.getScheduler().runTaskTimer(plugin, this::cleanupExpiredRequests, 20L * 30, 20L * 30);
    }
    
    /**
     * ギフトリクエストを作成
     */
    public void createGiftRequest(Player sender, Player recipient, RankManager.RankInfo rank, double cost) {
        // 既存のリクエストをキャンセル
        if (pendingRequests.containsKey(recipient.getUniqueId())) {
            sender.sendMessage("§c" + recipient.getName() + " は既に別のギフトを受け取り待ちです。");
            return;
        }
        
        GiftRequest request = new GiftRequest(sender, recipient, rank, cost);
        pendingRequests.put(recipient.getUniqueId(), request);
        lastRequestTime.put(recipient.getUniqueId(), System.currentTimeMillis());
        
        // 送信者に通知
        sender.sendMessage("§a§l[✓] §e" + recipient.getName() + " §aに §6" + rank.getDisplayName() + " §aランクのギフトリクエストを送信しました。");
        
        // 受信者に通知（GUIとチャット両方）
        sendGiftNotification(recipient, request);
        
        // 60秒後に自動キャンセル
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (pendingRequests.containsKey(recipient.getUniqueId())) {
                cancelRequest(recipient.getUniqueId(), true);
            }
        }, 20L * 60);
    }
    
    /**
     * ギフト通知を送信（チャットとGUI）
     */
    private void sendGiftNotification(Player recipient, GiftRequest request) {
        // サウンド再生
        recipient.playSound(recipient.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        
        // チャット通知（クリック可能）
        recipient.sendMessage("§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        recipient.sendMessage("§6§l                    ギフトリクエスト");
        recipient.sendMessage("");
        recipient.sendMessage("§e" + request.getSender().getName() + " §7から §6" + request.getRank().getDisplayName() + " §7ランクが贈られました！");
        recipient.sendMessage("");
        
        // Adventure API を使用したクリック可能なメッセージ
        Component acceptButton = Component.text("[受け取る]")
            .color(NamedTextColor.GREEN)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/giftaccept"))
            .hoverEvent(HoverEvent.showText(Component.text("クリックして受け取る").color(NamedTextColor.GREEN)));
        
        Component denyButton = Component.text("[拒否]")
            .color(NamedTextColor.RED)
            .decorate(TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/giftdeny"))
            .hoverEvent(HoverEvent.showText(Component.text("クリックして拒否").color(NamedTextColor.RED)));
        
        Component message = Component.text("         ")
            .append(acceptButton)
            .append(Component.text("     ").color(NamedTextColor.WHITE))
            .append(denyButton);
        
        recipient.sendMessage(message);
        recipient.sendMessage("");
        recipient.sendMessage("§7または §e/giftgui §7でGUIを開く");
        recipient.sendMessage("§7§o※60秒以内に応答してください");
        recipient.sendMessage("§e§l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
    }
    
    /**
     * ギフト承認GUI を開く
     */
    public void openGiftAcceptanceGUI(Player player) {
        GiftRequest request = pendingRequests.get(player.getUniqueId());
        if (request == null) {
            player.sendMessage("§c受け取り待ちのギフトがありません。");
            return;
        }
        
        Inventory inv = Bukkit.createInventory(null, 27, "§6§lギフトリクエスト");
        
        // ギフト情報表示
        ItemStack giftItem = new ItemStack(Material.DIAMOND);
        ItemMeta giftMeta = giftItem.getItemMeta();
        giftMeta.setDisplayName("§6§l" + request.getRank().getDisplayName());
        List<String> giftLore = new ArrayList<>();
        giftLore.add("§7");
        giftLore.add("§e送信者: §f" + request.getSender().getName());
        giftLore.add("§eTier: §f" + request.getRank().getTier());
        giftLore.add("§e期間: §f" + request.getRank().getDurationText());
        giftLore.add("§e価値: §a$" + String.format("%.2f", request.getCost()));
        giftLore.add("§7");
        giftMeta.setLore(giftLore);
        giftItem.setItemMeta(giftMeta);
        inv.setItem(13, giftItem);
        
        // 受け取るボタン
        ItemStack acceptItem = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta acceptMeta = acceptItem.getItemMeta();
        acceptMeta.setDisplayName("§a§l受け取る");
        List<String> acceptLore = new ArrayList<>();
        acceptLore.add("§7");
        acceptLore.add("§aこのギフトを受け取ります");
        acceptLore.add("§7");
        acceptMeta.setLore(acceptLore);
        acceptItem.setItemMeta(acceptMeta);
        inv.setItem(11, acceptItem);
        
        // 拒否ボタン
        ItemStack denyItem = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta denyMeta = denyItem.getItemMeta();
        denyMeta.setDisplayName("§c§l拒否");
        List<String> denyLore = new ArrayList<>();
        denyLore.add("§7");
        denyLore.add("§cこのギフトを拒否します");
        denyLore.add("§7");
        denyMeta.setLore(denyLore);
        denyItem.setItemMeta(denyMeta);
        inv.setItem(15, denyItem);
        
        player.openInventory(inv);
    }
    
    /**
     * ギフトを承認
     */
    public void acceptGift(Player player) {
        GiftRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage("§c受け取り待ちのギフトがありません。");
            return;
        }
        
        Player sender = request.getSender();
        if (!sender.isOnline()) {
            player.sendMessage("§cギフト送信者がオフラインです。");
            return;
        }
        
        // ランクを付与
        plugin.getRankManager().grantRank(player, request.getRank().getKey(), sender);
        
        // 通知
        player.sendMessage("§a§l[✓] §6" + request.getRank().getDisplayName() + " §aランクを受け取りました！");
        sender.sendMessage("§a§l[✓] §e" + player.getName() + " §aがギフトを受け取りました！");
        
        lastRequestTime.remove(player.getUniqueId());
    }
    
    /**
     * ギフトを拒否
     */
    public void denyGift(Player player) {
        GiftRequest request = pendingRequests.remove(player.getUniqueId());
        if (request == null) {
            player.sendMessage("§c受け取り待ちのギフトがありません。");
            return;
        }
        
        Player sender = request.getSender();
        
        // 返金
        if (sender.isOnline()) {
            plugin.getEconomy().depositPlayer(sender, request.getCost());
            sender.sendMessage("§c" + player.getName() + " がギフトを拒否しました。§a$" + 
                String.format("%.2f", request.getCost()) + " §cが返金されました。");
        }
        
        player.sendMessage("§cギフトを拒否しました。");
        lastRequestTime.remove(player.getUniqueId());
    }
    
    /**
     * リクエストをキャンセル（タイムアウト）
     */
    private void cancelRequest(UUID recipientId, boolean timeout) {
        GiftRequest request = pendingRequests.remove(recipientId);
        if (request == null) return;
        
        Player sender = request.getSender();
        Player recipient = Bukkit.getPlayer(recipientId);
        
        if (timeout) {
            // 返金
            if (sender.isOnline()) {
                plugin.getEconomy().depositPlayer(sender, request.getCost());
                sender.sendMessage("§cギフトリクエストがタイムアウトしました。§a$" + 
                    String.format("%.2f", request.getCost()) + " §cが返金されました。");
            }
            
            if (recipient != null && recipient.isOnline()) {
                recipient.sendMessage("§cギフトリクエストが期限切れになりました。");
            }
        }
        
        lastRequestTime.remove(recipientId);
    }
    
    /**
     * 期限切れリクエストをクリーンアップ
     */
    private void cleanupExpiredRequests() {
        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();
        
        for (Map.Entry<UUID, Long> entry : lastRequestTime.entrySet()) {
            if (now - entry.getValue() > REQUEST_TIMEOUT) {
                expired.add(entry.getKey());
            }
        }
        
        for (UUID uuid : expired) {
            cancelRequest(uuid, true);
        }
    }
    
    /**
     * ギフトリクエストクラス
     */
    public static class GiftRequest {
        private final Player sender;
        private final Player recipient;
        private final RankManager.RankInfo rank;
        private final double cost;
        
        public GiftRequest(Player sender, Player recipient, RankManager.RankInfo rank, double cost) {
            this.sender = sender;
            this.recipient = recipient;
            this.rank = rank;
            this.cost = cost;
        }
        
        public Player getSender() { return sender; }
        public Player getRecipient() { return recipient; }
        public RankManager.RankInfo getRank() { return rank; }
        public double getCost() { return cost; }
    }
    
    public boolean hasPendingRequest(UUID playerId) {
        return pendingRequests.containsKey(playerId);
    }
}