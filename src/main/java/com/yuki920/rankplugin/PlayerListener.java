package com.example.rankplugin;

import net.luckperms.api.event.node.NodeAddEvent;
import net.luckperms.api.event.node.NodeRemoveEvent;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {
    
    private final RankPlugin plugin;
    private final DisplayManager displayManager;
    
    public PlayerListener(RankPlugin plugin, DisplayManager displayManager) {
        this.plugin = plugin;
        this.displayManager = displayManager;
        
        // LuckPermsのイベントリスナーを登録
        registerLuckPermsListeners();
    }
    
    /**
     * プレイヤーがサーバーに参加したとき
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        // 少し遅延させてから表示を更新（LuckPermsのデータ読み込みを待つ）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            displayManager.updatePlayerDisplay(player);
        }, 10L); // 0.5秒後
    }
    
    /**
     * プレイヤーがサーバーから退出したとき
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        displayManager.removePlayerFromTeam(player);
    }
    
    /**
     * LuckPermsのイベントリスナーを登録
     */
    private void registerLuckPermsListeners() {
        // ユーザーデータが再計算されたとき（権限変更時）
        plugin.getLuckPerms().getEventBus().subscribe(UserDataRecalculateEvent.class, event -> {
            UUID uuid = event.getUser().getUniqueId();
            Player player = Bukkit.getPlayer(uuid);
            
            if (player != null && player.isOnline()) {
                // メインスレッドで実行
                Bukkit.getScheduler().runTask(plugin, () -> {
                    displayManager.updatePlayerDisplay(player);
                });
            }
        });
        
        // ノード（権限/グループ）が追加されたとき
        plugin.getLuckPerms().getEventBus().subscribe(NodeAddEvent.class, event -> {
            if (event.isUser()) {
                UUID uuid;
                if (event.getTarget() instanceof net.luckperms.api.model.user.User user) {
                    uuid = user.getUniqueId();
                } else {
                    return;
                }
                Player player = Bukkit.getPlayer(uuid);
                
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        displayManager.updatePlayerDisplay(player);
                    }, 5L);
                }
            }
        });
        
        // ノード（権限/グループ）が削除されたとき
        plugin.getLuckPerms().getEventBus().subscribe(NodeRemoveEvent.class, event -> {
            if (event.isUser()) {
                UUID uuid;
                if (event.getTarget() instanceof net.luckperms.api.model.user.User user) {
                    uuid = user.getUniqueId();
                } else {
                    return;
                }
                Player player = Bukkit.getPlayer(uuid);
                
                if (player != null && player.isOnline()) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        displayManager.updatePlayerDisplay(player);
                    }, 5L);
                }
            }
        });
    }
}