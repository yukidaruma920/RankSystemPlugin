package com.yuki920.rankplugin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class DisplayManager {
    
    private final RankPlugin plugin;
    private final LuckPerms luckPerms;
    private Scoreboard scoreboard;
    
    public DisplayManager(RankPlugin plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
    }
    
    /**
     * プレイヤーのTABリストとネームタグを更新
     */
    public void updatePlayerDisplay(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        
        String prefix = user.getCachedData().getMetaData().getPrefix();
        String suffix = user.getCachedData().getMetaData().getSuffix();
        
        // プレフィックスとサフィックスの処理
        prefix = prefix != null ? ChatColor.translateAlternateColorCodes('&', prefix) : "";
        suffix = suffix != null ? ChatColor.translateAlternateColorCodes('&', suffix) : "";
        
        // TABリストの表示名を設定
        String displayName = prefix + player.getName() + suffix;
        player.setPlayerListName(displayName);
        
        // スコアボードチームの設定（ネームタグ用）
        updateTeam(player, prefix, suffix);
    }
    
    /**
     * スコアボードチームを使ってネームタグを設定
     */
    private void updateTeam(Player player, String prefix, String suffix) {
        String teamName = getTeamName(player);
        Team team = scoreboard.getTeam(teamName);
        
        // チームが存在しない場合は作成
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        
        // プレフィックスとサフィックスを設定
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        
        // プレイヤーをチームに追加
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        
        // ネームタグの表示設定
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
    }
    
    /**
     * すべてのオンラインプレイヤーの表示を更新
     */
    public void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayerDisplay(player);
        }
    }
    
    /**
     * プレイヤーのチーム名を取得（優先度ベース）
     */
    private String getTeamName(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return "default";
        
        String primaryGroup = user.getPrimaryGroup();
        
        // グループに基づいた優先度を設定
        int priority = getGroupPriority(primaryGroup);
        
        // 優先度を含むチーム名（数字が小さいほど上位）
        return String.format("%03d_%s", priority, primaryGroup);
    }
    
    /**
     * グループの優先度を取得（config.ymlから読み込み可能にも対応）
     */
    private int getGroupPriority(String groupName) {
        // config.ymlに優先度が設定されている場合はそれを使用
        int configPriority = plugin.getConfig().getInt("group-priorities." + groupName, 999);
        if (configPriority != 999) {
            return configPriority;
        }
        
        // デフォルトの優先度
        return switch (groupName.toLowerCase()) {
            case "owner" -> 1;
            case "admin" -> 2;
            case "mod+", "moderator+" -> 3;
            case "mod", "moderator" -> 4;
            case "dev", "developer" -> 5;
            case "build","builder" -> 6;
            case "yt", "youtube" -> 10;
            case "mvp++", "mvp_plus_plus" -> 20;
            case "mvp+", "mvp_plus" -> 21;
            case "mvp" -> 22;
            case "vip+", "vip_plus" -> 23
            case "vip" -> 24;
            default -> 999;
        };
    }
    
    /**
     * プレイヤーのチームをクリーンアップ
     */
    public void removePlayerFromTeam(Player player) {
        String teamName = getTeamName(player);
        Team team = scoreboard.getTeam(teamName);
        
        if (team != null) {
            team.removeEntry(player.getName());
            
            // チームが空になったら削除
            if (team.getEntries().isEmpty()) {
                team.unregister();
            }
        }
    }
    
    /**
     * すべてのチームをクリーンアップ
     */
    public void cleanup() {
        for (Team team : scoreboard.getTeams()) {
            if (team.getName().matches("\\d{3}_.*")) {
                team.unregister();
            }
        }
    }
}