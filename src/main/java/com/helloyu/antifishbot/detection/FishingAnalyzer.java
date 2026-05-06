package com.helloyu.antifishbot.detection;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.config.ConfigManager;
import com.helloyu.antifishbot.data.FishingData;
import org.bukkit.entity.Player;

/**
 * 釣魚行為分析器
 */
public class FishingAnalyzer {

    private final AntiFishBot plugin;
    private final ConfigManager config;

    public FishingAnalyzer(AntiFishBot plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    /**
     * 分析玩家的釣魚行為，更新信任分數與可疑分數
     * 
     * @return 新增的可疑分數
     */
    public int analyze(Player player, FishingData data) {
        int addedScore = 0;

        // 0. 基礎數據檢查
        if (data.getTotalAttempts() < 5) {
            return 0; // 樣本太少，無法分析
        }

        // 1. 計算各項指標的熵 (Entropy)
        // 熵越高代表越隨機 (人類特徵)，熵越低代表越規律 (機器特徵)
        double reactionEntropy = data.calculateReactionEntropy(); // 反應時間熵
        double intervalEntropy = data.calculateIntervalEntropy(); // 釣魚間隔熵
        double lookEntropy = data.calculateLookEntropy(); // 視角移動熵

        debugLog("玩家 %s 分析數據: 反應熵=%.2f, 間隔熵=%.2f, 視角熵=%.2f",
                player.getName(), reactionEntropy, intervalEntropy, lookEntropy);

        // 2. 計算信任分數 (Trust Score)
        // 初始 0.5，最高 1.0 (完全信任)，最低 0.0 (完全不信任/機器人)
        double currentTrust = data.getTrustScore();
        double newTrust = currentTrust;

        // --- 視角分析 (最關鍵) ---
        // 機器人通常視角完全不動 (entropy 0) 或極其規律
        if (lookEntropy > 0.5) {
            newTrust += 0.02; // 視角自然移動，小幅增加信任
        } else if (lookEntropy < 0.01) {
            // 極度死板 (幾乎完全不動)，才大幅扣分
            newTrust -= 0.08;
            debugLog("玩家 %s 視角極度死板 (entropy=%.2f)", player.getName(), lookEntropy);
        } else if (lookEntropy < 0.05) {
            // 稍微死板，小幅扣分
            newTrust -= 0.02;
        }

        // --- 反應時間分析 ---
        // 人類反應時間通常在 200ms-500ms 波動，且具有 randomness
        if (reactionEntropy > 1.2) {
            newTrust += 0.01; // 反應時間自然波動 (放寬閾值 1.5 -> 1.2)
        } else if (reactionEntropy < 0.5) {
            newTrust -= 0.05; // 反應時間過於穩定 (放寬閾值 0.8 -> 0.5)
            debugLog("玩家 %s 反應時間過於穩定 (entropy=%.2f)", player.getName(), reactionEntropy);
        }

        // --- 間隔分析 ---
        if (intervalEntropy > 1.0) {
            newTrust += 0.005;
        } else if (intervalEntropy < 0.5) {
            newTrust -= 0.03;
        }

        // --- 位置固定分析 (針對壓力板+鐵地板門+音階盒釣魚機) ---
        // --- 位置固定分析 (針對壓力板+鐵地板門+音階盒釣魚機) ---
        if (config.isPositionAnalysisEnabled() && data.getTotalAttempts() > config.getPositionMinSamples()) {
            double posDeviation = data.calculatePositionDeviation();
            boolean yJitter = data.detectYAxisJitter(config.getYAxisJitterThreshold());
            double posThreshold = config.getMinPositionDeviation();

            debugLog("玩家 %s 位置分析: XZ偏差=%.4f (閾值=%.2f), Y軸微抖=%s",
                    player.getName(), posDeviation, posThreshold, yJitter);

            if (posDeviation < posThreshold) {
                // 如果玩家其他行為非常像人類 (熵值高)，則忽略站位不動的懲罰
                // 手動玩家也可能站著不動釣魚
                if (reactionEntropy > 1.0 || lookEntropy > 0.5) {
                    debugLog("玩家 %s 雖站位固定，但行為特徵(熵)正常，忽略位置懲罰", player.getName());
                } else {
                    // XZ 幾乎不動，且其他特徵也不像人類 -> 扣分
                    double ratio = posDeviation / posThreshold; // 0.0 ~ 1.0
                    double penalty = 0.05 + (1.0 - ratio) * 0.15; // 0.05 ~ 0.20
                    newTrust -= penalty;
                    debugLog("玩家 %s XZ 固定站位 (偏差=%.4f, 比率=%.2f, 扣分=%.3f)",
                            player.getName(), posDeviation, ratio, penalty);

                    if (yJitter) {
                        // XZ 不動 + Y 軸微抖 = 額外加重（地板門釣魚機特徵）
                        newTrust -= 0.10;
                        debugLog("玩家 %s 偵測到 Y 軸微抖，額外扣分！", player.getName());
                    }
                }
            }
        }

        // --- 瞬間拋竿偵測 (針對持續按住右鍵的 AFK 機制) ---
        // AFK 釣魚機因持續按住右鍵，收竿後會在 1-2 tick 內立即拋竿 (~50-100ms)
        // 正常玩家需要手動再按右鍵，通常需要 1-5 秒
        double avgRecast = data.getAverageRecastInterval();
        if (avgRecast < Double.MAX_VALUE) {
            debugLog("玩家 %s 拋竿間隔分析: 平均=%.0fms, 標準差=%.0fms",
                    player.getName(), avgRecast, data.getRecastDeviation());

            if (avgRecast < 150) {
                // 平均拋竿間隔 < 150ms = 極度可疑（幾乎確定是持續按住右鍵或高頻連點程式）
                newTrust -= 0.20;
                debugLog("玩家 %s 拋竿間隔極短 (%.0fms)！高度可疑！", player.getName(), avgRecast);
            } else if (avgRecast < 600) {
                // 平均拋竿間隔 < 600ms
                // 檢查標準差：機器人通常很規律 (dev < 50)，手動連點雖快但會有波動
                if (data.getRecastDeviation() < 50) {
                    newTrust -= 0.08;
                    debugLog("玩家 %s 拋竿間隔短且規律 (%.0fms, dev=%.0fms)，可疑", player.getName(), avgRecast,
                            data.getRecastDeviation());
                } else {
                    debugLog("玩家 %s 拋竿間隔短 (%.0fms) 但波動大 (dev=%.0fms)，判定為手動", player.getName(), avgRecast,
                            data.getRecastDeviation());
                }
            }
        }

        // 更新信任分數
        data.setTrustScore(newTrust);
        debugLog("玩家 %s 信任分數: %.2f -> %.2f", player.getName(), currentTrust, data.getTrustScore());

        // 3. 根據信任分數決定是否增加可疑分數
        if (data.getTrustScore() < 0.2) {
            addedScore = 20; // 非常可疑
            debugLog("玩家 %s 信任分數過低 (%.2f)，增加 20 可疑分數", player.getName(), data.getTrustScore());
        } else if (data.getTrustScore() < 0.4) {
            addedScore = 5; // 有點可疑
        }

        // 累積可疑分數
        if (addedScore > 0) {
            data.addScore(addedScore);
        }

        return addedScore;
    }

    /**
     * 判斷是否需要觸發驗證
     * 現在基於信任分數判斷
     */
    public boolean shouldTriggerVerification(FishingData data) {
        if (!config.isVerificationEnabled()) {
            return false;
        }

        // 已經在驗證中
        if (data.isPendingVerification()) {
            return false;
        }

        // 信任分數極低時觸發
        if (data.getTrustScore() < 0.15 && data.getTotalAttempts() > 10) {
            return true;
        }

        // 連續釣魚達到閾值 (傳統檢查作為備案)
        return data.getContinuousCount() >= config.getContinuousTriggerCount();
    }

    /**
     * 根據分數判斷懲罰等級
     * 
     * @return 懲罰等級 (0=無, 1=警告, 2=取消成果, 3=禁止釣魚, 4=執行指令)
     */
    public int getPunishmentLevel(int score) {
        if (score >= config.getExecuteCommandThreshold()) {
            return 4;
        } else if (score >= config.getTempBanThreshold()) {
            return 3;
        } else if (score >= config.getCancelCatchThreshold()) {
            return 2;
        } else if (score >= config.getWarningThreshold()) {
            return 1;
        }
        return 0;
    }

    private void debugLog(String message, Object... args) {
        String formatted = String.format("[DEBUG] " + message, args);
        if (config.isDebugEnabled()) {
            plugin.getLogger().info(formatted);
        }

        // 發送給開啟除錯模式的在線管理員
        for (java.util.UUID uuid : plugin.getDebugPlayers()) {
            Player p = org.bukkit.Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§7" + formatted);
            }
        }
    }
}
