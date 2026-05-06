package com.helloyu.antifishbot.listeners;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.config.ConfigManager;
import com.helloyu.antifishbot.data.FishingData;
import com.helloyu.antifishbot.detection.FishingAnalyzer;
import com.helloyu.antifishbot.utils.MessageManager;
import com.helloyu.antifishbot.verification.VerificationHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

/**
 * 釣魚事件監聽器
 */
public class FishingListener implements Listener {

    private final AntiFishBot plugin;
    private final ConfigManager config;
    private final MessageManager messages;
    private final FishingAnalyzer analyzer;
    private final VerificationHandler verification;

    public FishingListener(AntiFishBot plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
        this.analyzer = plugin.getFishingAnalyzer();
        this.verification = plugin.getVerificationHandler();
    }

    /**
     * 監聽釣魚事件
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        // 檢查繞過權限
        if (player.hasPermission("antifishbot.bypass")) {
            return;
        }

        // 檢查是否被禁止釣魚
        if (plugin.isFishingBanned(player)) {
            // 顯示剩餘禁止時間
            long remainingSeconds = plugin.getFishingBanRemaining(player);
            messages.sendActionBar(player, "player.fishing-banned-remaining",
                    "%remaining%", String.valueOf(remainingSeconds));
            event.setCancelled(true);
            return;
        }

        FishingData data = plugin.getFishingData(player);

        // 處理驗證超時
        if (data.isPendingVerification()) {
            if (verification.isVerificationExpired(data)) {
                verification.handleVerificationFailure(player, data);
            }
            // 驗證期間禁止釣魚
            event.setCancelled(true);
            return;
        }

        // 根據釣魚狀態處理
        // 根據釣魚狀態處理
        switch (event.getState()) {
            case CAUGHT_FISH:
                handleCaughtFish(event, player, data);
                // 記錄收竿時間 (用於計算重新拋竿間隔)
                data.recordCaughtTime();
                // 取消陷阱任務
                cancelTrapTask(data);
                break;
            case FISHING:
                // 記錄重新拋竿間隔 (從上次收竿到現在拋竿的時間差)
                data.recordRecast();
                // 開始釣魚，更新視角數據
                data.updateLookDirection(player.getLocation().getYaw(), player.getLocation().getPitch());
                // 記錄魚鉤實體，用於主動陷阱
                if (event.getHook() != null) {
                    data.setHook(event.getHook());
                }

                // 安排主動陷阱 (如果信任分數低)
                scheduleTrap(player, data);
                break;
            case BITE:
                // 魚咬鉤，記錄時間
                data.setLastBiteTime(System.currentTimeMillis());
                break;
            case FAILED_ATTEMPT:
            case IN_GROUND:
                // 檢查是否對假訊號有反應
                checkGhostBiteReaction(player, data);
                // 失敗的嘗試
                data.recordFailedAttempt();
                // 取消陷阱任務
                cancelTrapTask(data);
                break;
            default:
                break;
        }
    }

    /**
     * 安排主動陷阱
     */
    private void scheduleTrap(Player player, FishingData data) {
        // 取消舊任務
        cancelTrapTask(data);

        // 如果信任分數低於 0.4，或者隨機觸發 (機率與信任分數成反比)
        double trust = data.getTrustScore();
        if (trust < 0.4 || Math.random() > trust + 0.2) {
            // 延遲 2-10 秒
            int delaySeconds = 2 + (int) (Math.random() * 8);

            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                // 觸發假咬鉤
                if (player.isOnline() && data.getHook() != null && data.getHook().isValid()) {
                    plugin.getTrapManager().triggerGhostBite(player, data);
                }
                data.setCurrentTrapTask(null);
            }, delaySeconds * 20L).getTaskId();

            data.setCurrentTrapTask(taskId);
        }
    }

    /**
     * 取消陷阱任務
     */
    private void cancelTrapTask(FishingData data) {
        if (data.getCurrentTrapTask() != null) {
            Bukkit.getScheduler().cancelTask(data.getCurrentTrapTask());
            data.setCurrentTrapTask(null);
        }
    }

    /**
     * 檢查是否對假訊號有反應
     */
    private void checkGhostBiteReaction(Player player, FishingData data) {
        long now = System.currentTimeMillis();
        long lastGhost = data.getLastGhostBiteTime();

        // 如果在假訊號後 1 秒內收竿
        if (now - lastGhost < 1000) {
            // 判定為機器人反應
            data.addScore(50); // 大幅增加可疑分數
            messages.sendMessage(player, "&c偵測到異常釣魚行為 (Type: G-Bite)");
            plugin.getLogger().info(
                    "Player " + player.getName() + " failed Ghost Bite test (Reaction: " + (now - lastGhost) + "ms)");
        }
    }

    /**
     * 處理成功釣到魚
     */
    private void handleCaughtFish(PlayerFishEvent event, Player player, FishingData data) {
        // 檢查是否對假訊號有反應 (雖然機率低，但如果碰巧咬鉤也是可能的)
        checkGhostBiteReaction(player, data);

        // 計算反應時間
        long reactionTime = 0;
        if (data.getLastBiteTime() > 0) {
            reactionTime = System.currentTimeMillis() - data.getLastBiteTime();
        }

        // 記錄釣魚位置 (用於位置固定偵測)
        data.recordFishingPosition(
                player.getLocation().getX(),
                player.getLocation().getY(),
                player.getLocation().getZ());

        // 記錄釣魚數據
        data.recordCatch(reactionTime);
        data.updateLookDirection(player.getLocation().getYaw(), player.getLocation().getPitch());

        // 分析行為
        analyzer.analyze(player, data);
        int totalScore = data.getSuspiciousScore();
        int punishmentLevel = analyzer.getPunishmentLevel(totalScore);

        // 檢查是否需要觸發驗證
        if (analyzer.shouldTriggerVerification(data)) {
            verification.startVerification(player, data);
            data.resetContinuousCount();
            return;
        }

        // 根據分數執行懲罰
        switch (punishmentLevel) {
            case 4: // 執行指令
                executeCommands(player);
                plugin.incrementPunishmentCount();
                break;
            case 3: // 禁止釣魚
                plugin.banFishing(player, config.getTempBanDuration());
                event.setCancelled(true);
                plugin.incrementPunishmentCount();
                break;
            case 2: // 取消成果
                messages.sendMessage(player, "player.catch-cancelled");
                event.setCancelled(true);
                plugin.incrementPunishmentCount();
                break;
            case 1: // 警告（螢幕中間 Title + 聊天欄）
                messages.sendTitle(player, "player.warning-title", "player.warning-subtitle");
                messages.sendMessage(player, "player.warning");
                break;
            default:
                break;
        }
    }

    /**
     * 執行懲罰指令
     */
    private void executeCommands(Player player) {
        for (String command : config.getExecuteCommands()) {
            String finalCommand = command.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        }
    }

    /**
     * 監聽玩家移動事件
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        // 檢查繞過權限
        if (player.hasPermission("antifishbot.bypass")) {
            return;
        }

        FishingData data = plugin.getFishingData(player);

        // 如果沒有在驗證中，不需要監聽移動 (優化性能)
        if (!data.isPendingVerification()) {
            return;
        }

        // 記錄玩家有輸入 (已移除，不再強制移動)
        /*
         * if (event.getFrom().getX() != event.getTo().getX() ||
         * event.getFrom().getY() != event.getTo().getY() ||
         * event.getFrom().getZ() != event.getTo().getZ()) {
         * data.recordInput();
         * }
         */

        // 如果正在等待跳躍驗證
        if (data.isPendingVerification() && "jump".equals(data.getVerificationType())) {
            // 檢查是否跳躍（Y 座標增加）
            if (event.getTo().getY() > event.getFrom().getY()) {
                verification.handleVerificationSuccess(player, data);
            }
        }

        // 如果正在等待移動驗證
        if (data.isPendingVerification() && "move".equals(data.getVerificationType())) {
            double distance = event.getFrom().distance(event.getTo());
            if (distance > 0.5) {
                verification.handleVerificationSuccess(player, data);
            }
        }
    }

    /**
     * 監聽玩家蹲下事件
     */
    @EventHandler
    public void onPlayerSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }

        Player player = event.getPlayer();

        // 檢查繞過權限
        if (player.hasPermission("antifishbot.bypass")) {
            return;
        }

        FishingData data = plugin.getFishingData(player);

        // 如果沒有在驗證中，不需要監聽 (優化性能)
        if (!data.isPendingVerification()) {
            return;
        }

        // data.recordInput(); // 已移除

        // 如果正在等待蹲下驗證
        if (data.isPendingVerification() && "sneak".equals(data.getVerificationType())) {
            verification.handleVerificationSuccess(player, data);
        }
    }

    /**
     * 監聽玩家離線事件
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // 清理玩家數據 (修復緩存洩漏)
        plugin.removeFishingData(event.getPlayer().getUniqueId());

        // FishingData data = plugin.getFishingData(event.getPlayer());
        // cancelTrapTask(data); // 數據移除後不需要再手動取消，因為 map 中已經沒有了
    }
}
