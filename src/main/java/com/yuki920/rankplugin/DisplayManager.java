package com.example.rankplugin;

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
        
        // プレフィックスから最後の色コードを抽出してプレイヤー名に適用
        String nameColor = extractLastColorCode(prefix);
        
        // TABリストの表示名を設定
        String displayName = prefix + nameColor + player.getName() + suffix;
        player.setPlayerListName(displayName);
        
        // スコアボードチームの設定（ネームタグ用）
        updateTeam(player, prefix, suffix, nameColor);
    }
    
    /**
     * 文字列から最後の色コードを抽出
     */
    private String extractLastColorCode(String text) {
        if (text == null || text.isEmpty()) return "§f";
        
        String lastColor = "§f"; // デフォルトは白
        for (int i = 0; i < text.length() - 1; i++) {
            if (text.charAt(i) == '§' || text.charAt(i) == '&') {
                char code = text.charAt(i + 1);
                // 色コードまたはフォーマットコード
                if ("0123456789abcdefklmnor".indexOf(Character.toLowerCase(code)) != -1) {
                    lastColor = "§" + code;
                }
            }
        }
        return lastColor;
    }
    
    /**
     * スコアボードチームを使ってネームタグを設定
     */
    private void updateTeam(Player player, String prefix, String suffix, String nameColor) {
        String teamName = getTeamName(player);
        Team team = scoreboard.getTeam(teamName);
        
        // チームが存在しない場合は作成
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }
        
        // プレフィックスとサフィックスを設定（名前の色も含める）
        team.setPrefix(prefix);
        team.setSuffix(suffix);
        
        // プレイヤー名の色を設定
        try {
            // Paperの新しいAPI（1.13+）
            team.setColor(getChatColorFromCode(nameColor));
        } catch (Exception e) {
            // フォールバック: Prefixに名前の色を含める
            team.setPrefix(prefix + nameColor);
        }
        
        // プレイヤーをチームに追加
        if (!team.hasEntry(player.getName())) {
            team.addEntry(player.getName());
        }
        
        // ネームタグの表示設定
        team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
    }
    
    /**
     * カラーコードからChatColorを取得
     */
    private ChatColor getChatColorFromCode(String colorCode) {
        if (colorCode == null || colorCode.length() < 2) return ChatColor.WHITE;
        
        char code = colorCode.charAt(1);
        return switch (Character.toLowerCase(code)) {
            case '0' -> ChatColor.BLACK;
            case '1' -> ChatColor.DARK_BLUE;
            case '2' -> ChatColor.DARK_GREEN;
            case '3' -> ChatColor.DARK_AQUA;
            case '4' -> ChatColor.DARK_RED;
            case '5' -> ChatColor.DARK_PURPLE;
            case '6' -> ChatColor.GOLD;
            case '7' -> ChatColor.GRAY;
            case '8' -> ChatColor.DARK_GRAY;
            case '9' -> ChatColor.BLUE;
            case 'a' -> ChatColor.GREEN;
            case 'b' -> ChatColor.AQUA;
            case 'c' -> ChatColor.RED;
            case 'd' -> ChatColor.LIGHT_PURPLE;
            case 'e' -> ChatColor.YELLOW;
            case 'f' -> ChatColor.WHITE;
            default -> ChatColor.WHITE;
        };
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
            case "mod", "moderator" -> 3;
            case "helper" -> 4;
            case "mvpplusplus" -> 10;
            case "mvpplus" -> 11;
            case "mvp" -> 12;
            case "vipplus" -> 13;
            case "vip" -> 14;
            case "member" -> 50;
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