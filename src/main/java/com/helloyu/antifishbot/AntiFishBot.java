package com.helloyu.antifishbot;

import com.helloyu.antifishbot.commands.AdminCommand;
import com.helloyu.antifishbot.config.ConfigManager;
import com.helloyu.antifishbot.data.FishingData;
import com.helloyu.antifishbot.detection.FishingAnalyzer;
import com.helloyu.antifishbot.listeners.ChatVerificationListener;
import com.helloyu.antifishbot.listeners.FishingListener;
import com.helloyu.antifishbot.listeners.GUIVerificationListener;
import com.helloyu.antifishbot.utils.MessageManager;
import com.helloyu.antifishbot.verification.VerificationHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AntiFishBot 主類別
 * 防掛機釣魚插件
 */
public class AntiFishBot extends JavaPlugin {

    private static AntiFishBot instance;

    private ConfigManager configManager;
    private MessageManager messageManager;
    private FishingAnalyzer fishingAnalyzer;
    private VerificationHandler verificationHandler;
    private com.helloyu.antifishbot.detection.TrapManager trapManager;

    // 玩家釣魚數據
    private final Map<UUID, FishingData> fishingDataMap = new ConcurrentHashMap<>();

    // 被禁止釣魚的玩家
    private final Map<UUID, Long> bannedPlayers = new ConcurrentHashMap<>();

    // 統計數據
    private int todayVerifications = 0;
    private int todayPunishments = 0;

    @Override
    public void onEnable() {
        instance = this;

        // 載入設定
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        // 初始化處理器
        fishingAnalyzer = new FishingAnalyzer(this);
        verificationHandler = new VerificationHandler(this);
        trapManager = new com.helloyu.antifishbot.detection.TrapManager(this);

        // 註冊監聽器
        Bukkit.getPluginManager().registerEvents(new FishingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatVerificationListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GUIVerificationListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.helloyu.antifishbot.listeners.InteractListener(this), this);

        // 註冊指令
        AdminCommand adminCommand = new AdminCommand(this);
        getCommand("afb").setExecutor(adminCommand);
        getCommand("afb").setTabCompleter(adminCommand);

        // 啟動定時任務 - 分數衰減和清理
        startScheduledTasks();

        getLogger().info("AntiFishBot 已啟用！");
    }

    @Override
    public void onDisable() {
        // 清理資源
        fishingDataMap.clear();
        bannedPlayers.clear();

        getLogger().info("AntiFishBot 已停用！");
    }

    /**
     * 啟動定時任務
     */
    private void startScheduledTasks() {
        // 分數衰減任務（每分鐘執行）
        int decayRate = configManager.getScoreDecayPerMinute();
        if (decayRate > 0) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                for (FishingData data : fishingDataMap.values()) {
                    data.decayScore(decayRate);
                }
            }, 1200L, 1200L); // 每分鐘（1200 ticks）
        }

        // 解除釣魚禁令檢查（每秒執行）
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            long now = System.currentTimeMillis();
            bannedPlayers.entrySet().removeIf(entry -> {
                if (now >= entry.getValue()) {
                    Player player = Bukkit.getPlayer(entry.getKey());
                    if (player != null && player.isOnline()) {
                        messageManager.sendMessage(player, "player.fishing-unbanned");
                    }
                    return true;
                }
                return false;
            });
        }, 20L, 20L); // 每秒
    }

    /**
     * 重新載入插件
     */
    public void reload() {
        reloadConfig();
        configManager.reload();
        messageManager.reload();
    }

    /**
     * 取得玩家的釣魚數據
     */
    public FishingData getFishingData(Player player) {
        return fishingDataMap.computeIfAbsent(player.getUniqueId(),
                uuid -> new FishingData(player.getUniqueId()));
    }

    /**
     * 移除玩家的釣魚數據
     */
    public void removeFishingData(UUID uuid) {
        fishingDataMap.remove(uuid);
    }

    /**
     * 重置玩家的釣魚數據
     */
    public void resetFishingData(UUID uuid) {
        fishingDataMap.put(uuid, new FishingData(uuid));
    }

    /**
     * 禁止玩家釣魚
     */
    public void banFishing(Player player, int durationSeconds) {
        long unbanTime = System.currentTimeMillis() + (durationSeconds * 1000L);
        bannedPlayers.put(player.getUniqueId(), unbanTime);

        messageManager.sendMessage(player, "player.fishing-banned",
                "%duration%", String.valueOf(durationSeconds));

        todayPunishments++;
    }

    /**
     * 檢查玩家是否被禁止釣魚
     */
    public boolean isFishingBanned(Player player) {
        Long unbanTime = bannedPlayers.get(player.getUniqueId());
        if (unbanTime == null) {
            return false;
        }
        if (System.currentTimeMillis() >= unbanTime) {
            bannedPlayers.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    /**
     * 取得玩家剩餘的禁止釣魚秒數
     */
    public long getFishingBanRemaining(Player player) {
        Long unbanTime = bannedPlayers.get(player.getUniqueId());
        if (unbanTime == null) {
            return 0;
        }
        long remaining = (unbanTime - System.currentTimeMillis()) / 1000;
        return Math.max(0, remaining);
    }

    /**
     * 增加驗證計數
     */
    public void incrementVerificationCount() {
        todayVerifications++;
    }

    /**
     * 增加懲罰計數
     */
    public void incrementPunishmentCount() {
        todayPunishments++;
    }

    // ===== Getters =====

    public static AntiFishBot getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public FishingAnalyzer getFishingAnalyzer() {
        return fishingAnalyzer;
    }

    public com.helloyu.antifishbot.detection.TrapManager getTrapManager() {
        return trapManager;
    }

    public VerificationHandler getVerificationHandler() {
        return verificationHandler;
    }

    public Map<UUID, FishingData> getFishingDataMap() {
        return fishingDataMap;
    }

    public int getTodayVerifications() {
        return todayVerifications;
    }

    public int getTodayPunishments() {
        return todayPunishments;
    }

    // ===== Debug Mode =====
    private final java.util.Set<UUID> debugPlayers = java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    public boolean toggleDebug(Player player) {
        if (debugPlayers.contains(player.getUniqueId())) {
            debugPlayers.remove(player.getUniqueId());
            return false;
        } else {
            debugPlayers.add(player.getUniqueId());
            return true;
        }
    }

    public boolean isDebugEnabled(Player player) {
        return debugPlayers.contains(player.getUniqueId());
    }

    public java.util.Set<UUID> getDebugPlayers() {
        return debugPlayers;
    }
}
