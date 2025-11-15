package com.example.rankplugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RankManager {
    
    private final RankPlugin plugin;
    private final LuckPerms luckPerms;
    private final Map<String, RankInfo> ranks;
    private final Map<String, Integer> rankHierarchy;
    
    public RankManager(RankPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.ranks = new HashMap<>();
        this.rankHierarchy = new HashMap<>();
        loadRanks();
    }
    
    private void loadRanks() {
        ConfigurationSection ranksSection = plugin.getConfig().getConfigurationSection("ranks");
        if (ranksSection == null) return;
        
        for (String key : ranksSection.getKeys(false)) {
            ConfigurationSection rankSection = ranksSection.getConfigurationSection(key);
            if (rankSection == null) continue;
            
            String groupName = rankSection.getString("group");
            double price = rankSection.getDouble("price");
            int duration = rankSection.getInt("duration", -1);
            String displayName = rankSection.getString("display-name", key);
            int tier = rankSection.getInt("tier", 0);
            
            ranks.put(key, new RankInfo(key, groupName, price, duration, displayName, tier));
            rankHierarchy.put(groupName.toLowerCase(), tier);
        }
    }
    
    public Map<String, RankInfo> getRanks() {
        return ranks;
    }
    
    /**
     * プレイヤーの現在のランクを取得
     */
    public RankInfo getCurrentRank(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return null;
        
        String primaryGroup = user.getPrimaryGroup().toLowerCase();
        
        // 所持している最高ティアのランクを検索
        RankInfo currentRank = null;
        int highestTier = -1;
        
        for (RankInfo rank : ranks.values()) {
            if (user.getNodes().stream()
                .anyMatch(node -> node.getKey().equals("group." + rank.getGroupName()))) {
                if (rank.getTier() > highestTier) {
                    highestTier = rank.getTier();
                    currentRank = rank;
                }
            }
        }
        
        return currentRank;
    }
    
    /**
     * アップグレード可能かチェック
     */
    public UpgradeInfo checkUpgrade(Player player, RankInfo targetRank) {
        RankInfo currentRank = getCurrentRank(player);
        
        if (currentRank == null) {
            // 現在ランクなし - フル価格
            return new UpgradeInfo(true, targetRank.getPrice(), targetRank.getPrice(), null);
        }
        
        if (currentRank.getTier() >= targetRank.getTier()) {
            // 同等または下位ランクへの変更不可
            return new UpgradeInfo(false, 0, 0, currentRank);
        }
        
        // アップグレード - 差額計算
        double upgradeCost = targetRank.getPrice() - currentRank.getPrice();
        return new UpgradeInfo(true, upgradeCost, targetRank.getPrice(), currentRank);
    }
    
    public void showRank(CommandSender sender, Player target) {
        User user = luckPerms.getUserManager().getUser(target.getUniqueId());
        if (user == null) {
            sender.sendMessage("§cユーザーデータが見つかりません。");
            return;
        }
        
        RankInfo currentRank = getCurrentRank(target);
        String prefix = user.getCachedData().getMetaData().getPrefix();
        
        sender.sendMessage("§e========== §6ランク情報 §e==========");
        sender.sendMessage("§eプレイヤー: §f" + target.getName());
        
        if (currentRank != null) {
            sender.sendMessage("§e現在のランク: §6" + currentRank.getDisplayName() + " §7(Tier " + currentRank.getTier() + ")");
        } else {
            sender.sendMessage("§e現在のランク: §7なし");
        }
        
        if (prefix != null && !prefix.isEmpty()) {
            sender.sendMessage("§eプレフィックス: " + prefix);
        }
        sender.sendMessage("§e================================");
    }
    
    public CompletableFuture<Boolean> grantRank(Player target, String rankKey, Player gifter) {
        RankInfo rankInfo = ranks.get(rankKey);
        if (rankInfo == null) {
            return CompletableFuture.completedFuture(false);
        }
        
        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(target.getUniqueId());
        
        return userFuture.thenApplyAsync(user -> {
            if (user == null) return false;
            
            // 既存の下位ランクを削除
            removeAllRanks(user);
            
            // 新しいノードを作成
            Node node;
            if (rankInfo.getDuration() > 0) {
                Duration duration = Duration.ofDays(rankInfo.getDuration());
                node = Node.builder("group." + rankInfo.getGroupName())
                    .expiry(duration)
                    .build();
            } else {
                node = Node.builder("group." + rankInfo.getGroupName()).build();
            }
            
            user.data().add(node);
            
            // プライマリグループを設定
            user.setPrimaryGroup(rankInfo.getGroupName());
            
            luckPerms.getUserManager().saveUser(user);
            
            // LuckPermsのキャッシュを強制更新
            Bukkit.getScheduler().runTask(plugin, () -> {
                // ユーザーデータを再読み込み
                luckPerms.getUserManager().loadUser(target.getUniqueId()).thenAccept(updatedUser -> {
                    if (updatedUser != null && target.isOnline()) {
                        // 少し遅延させて表示を更新
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            plugin.getDisplayManager().updatePlayerDisplay(target);
                            
                            // 全プレイヤーに対して更新を通知（他のプレイヤーから見える名前も更新）
                            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                                if (!onlinePlayer.equals(target)) {
                                    // 他のプレイヤーのクライアントにも更新を送信
                                    onlinePlayer.hidePlayer(plugin, target);
                                    onlinePlayer.showPlayer(plugin, target);
                                }
                            }
                        }, 20L); // 1秒後
                    }
                });
            });
            
            // 通知メッセージ
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (gifter != null) {
                    target.sendMessage("§e§l[!] §a" + gifter.getName() + " §7から §6" + 
                        rankInfo.getDisplayName() + " §7ランクをギフトされました！");
                } else {
                    target.sendMessage("§e§l[!] §6" + rankInfo.getDisplayName() + 
                        " §7ランクを購入しました！");
                }
            });
            
            return true;
        }).exceptionally(throwable -> {
            throwable.printStackTrace();
            return false;
        });
    }
    
    /**
     * プレイヤーの全ランクを削除
     */
    private void removeAllRanks(User user) {
        for (RankInfo rank : ranks.values()) {
            user.data().clear(node -> node.getKey().equals("group." + rank.getGroupName()));
        }
    }
    
    /**
     * 次のアップグレード可能なランクを取得
     */
    public List<RankInfo> getUpgradeableRanks(Player player) {
        RankInfo currentRank = getCurrentRank(player);
        int currentTier = currentRank != null ? currentRank.getTier() : -1;
        
        List<RankInfo> upgradeable = new ArrayList<>();
        for (RankInfo rank : ranks.values()) {
            if (rank.getTier() > currentTier) {
                upgradeable.add(rank);
            }
        }
        
        upgradeable.sort(Comparator.comparingInt(RankInfo::getTier));
        return upgradeable;
    }
    
    public static class RankInfo {
        private final String key;
        private final String groupName;
        private final double price;
        private final int duration;
        private final String displayName;
        private final int tier;
        
        public RankInfo(String key, String groupName, double price, int duration, String displayName, int tier) {
            this.key = key;
            this.groupName = groupName;
            this.price = price;
            this.duration = duration;
            this.displayName = displayName;
            this.tier = tier;
        }
        
        public String getKey() { return key; }
        public String getGroupName() { return groupName; }
        public double getPrice() { return price; }
        public int getDuration() { return duration; }
        public String getDisplayName() { return displayName; }
        public int getTier() { return tier; }
        
        public String getDurationText() {
            if (duration <= 0) return "永久";
            return duration + "日間";
        }
    }
    
    public static class UpgradeInfo {
        private final boolean canUpgrade;
        private final double upgradeCost;
        private final double fullPrice;
        private final RankInfo currentRank;
        
        public UpgradeInfo(boolean canUpgrade, double upgradeCost, double fullPrice, RankInfo currentRank) {
            this.canUpgrade = canUpgrade;
            this.upgradeCost = upgradeCost;
            this.fullPrice = fullPrice;
            this.currentRank = currentRank;
        }
        
        public boolean canUpgrade() { return canUpgrade; }
        public double getUpgradeCost() { return upgradeCost; }
        public double getFullPrice() { return fullPrice; }
        public RankInfo getCurrentRank() { return currentRank; }
        public boolean isUpgrade() { return currentRank != null; }
    }
}