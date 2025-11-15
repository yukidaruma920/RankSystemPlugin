package com.example.rankplugin;

import net.luckperms.api.LuckPerms;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public class RankPlugin extends JavaPlugin implements Listener {
    
    private LuckPerms luckPerms;
    private Economy economy;
    private RankManager rankManager;
    private RankShopGUI rankShopGUI;
    private GiftGUI giftGUI;
    private DisplayManager displayManager;
    private PlayerListener playerListener;
    private GiftRequestManager giftRequestManager;
    
    @Override
    public void onEnable() {
        // LuckPerms APIの取得
        RegisteredServiceProvider<LuckPerms> lpProvider = 
            Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        
        if (lpProvider != null) {
            luckPerms = lpProvider.getProvider();
            getLogger().info("LuckPermsとの連携に成功しました！");
        } else {
            getLogger().severe("LuckPermsが見つかりません！");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        
        // Vault Economyの取得
        RegisteredServiceProvider<Economy> ecoProvider = 
            Bukkit.getServicesManager().getRegistration(Economy.class);
        
        if (ecoProvider != null) {
            economy = ecoProvider.getProvider();
            getLogger().info("Vaultとの連携に成功しました！");
        } else {
            getLogger().warning("Vault経済プラグインが見つかりません！");
            getLogger().warning("EssentialsXなどの経済プラグインをインストールしてください。");
            getLogger().warning("経済機能なしで起動します。（ランク購入機能は無効）");
            // economyがnullのままでも起動を続ける
        }
        
        // コンフィグの作成
        saveDefaultConfig();
        
        // マネージャーとGUIの初期化
        rankManager = new RankManager(this, luckPerms);
        rankShopGUI = new RankShopGUI(this, rankManager, economy);
        giftGUI = new GiftGUI(this, rankManager, economy);
        displayManager = new DisplayManager(this, luckPerms);
        playerListener = new PlayerListener(this, displayManager);
        giftRequestManager = new GiftRequestManager(this);
        
        // イベントリスナー登録
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(rankShopGUI, this);
        getServer().getPluginManager().registerEvents(giftGUI, this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        
        // オンラインプレイヤーの表示を更新
        Bukkit.getScheduler().runTaskLater(this, () -> {
            displayManager.updateAllPlayers();
        }, 20L);
        
        getLogger().info("RankPluginが有効化されました！");
    }
    
    @Override
    public void onDisable() {
        // クリーンアップ
        if (displayManager != null) {
            displayManager.cleanup();
        }
        getLogger().info("RankPluginが無効化されました。");
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cこのコマンドはプレイヤーのみ実行できます。");
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("rankshop")) {
            rankShopGUI.openShop(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("gift")) {
            if (args.length == 0) {
                giftGUI.openGiftMenu(player);
                return true;
            }
            
            // /gift <プレイヤー名> でギフト対象を指定して開く
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage("§cプレイヤーが見つかりません。");
                return true;
            }
            
            if (target.equals(player)) {
                player.sendMessage("§c自分にはギフトできません。");
                return true;
            }
            
            giftGUI.openGiftRankMenu(player, target);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("giftaccept")) {
            giftRequestManager.acceptGift(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("giftdeny")) {
            giftRequestManager.denyGift(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("giftgui")) {
            if (!giftRequestManager.hasPendingRequest(player.getUniqueId())) {
                player.sendMessage("§c受け取り待ちのギフトがありません。");
                return true;
            }
            giftRequestManager.openGiftAcceptanceGUI(player);
            return true;
        }
        
        if (command.getName().equalsIgnoreCase("rank")) {
            if (args.length == 0) {
                rankManager.showRank(player, player);
                return true;
            }
            
            if (args[0].equalsIgnoreCase("check") && args.length >= 2) {
                if (!player.hasPermission("rank.check")) {
                    player.sendMessage("§c権限がありません。");
                    return true;
                }
                
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage("§cプレイヤーが見つかりません。");
                    return true;
                }
                
                rankManager.showRank(player, target);
                return true;
            }
        }
        
        return false;
    }
    
    public LuckPerms getLuckPerms() {
        return luckPerms;
    }
    
    public Economy getEconomy() {
        return economy;
    }
    
    public RankManager getRankManager() {
        return rankManager;
    }
    
    public DisplayManager getDisplayManager() {
        return displayManager;
    }
    
    public GiftRequestManager getGiftRequestManager() {
        return giftRequestManager;
    }
}