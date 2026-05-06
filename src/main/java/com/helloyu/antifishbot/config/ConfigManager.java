package com.helloyu.antifishbot.config;

import com.helloyu.antifishbot.AntiFishBot;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * 設定檔管理器
 */
public class ConfigManager {

    private final AntiFishBot plugin;
    private FileConfiguration config;

    public ConfigManager(AntiFishBot plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    // ===== 偵測啟用狀態 =====

    public boolean isIntervalAnalysisEnabled() {
        return config.getBoolean("detection.enabled.interval-analysis", true);
    }

    public boolean isLookDirectionEnabled() {
        return config.getBoolean("detection.enabled.look-direction", true);
    }

    public boolean isContinuousFishingEnabled() {
        return config.getBoolean("detection.enabled.continuous-fishing", true);
    }

    public boolean isSuccessRateEnabled() {
        return config.getBoolean("detection.enabled.success-rate", true);
    }

    // ===== 時間間隔分析參數 =====

    public double getMinIntervalDeviation() {
        return config.getDouble("detection.interval.min-deviation-ms", 150.0);
    }

    public int getMinIntervalSamples() {
        return config.getInt("detection.interval.min-samples", 5);
    }

    // ===== 視角偵測參數 =====

    public double getMinLookChange() {
        return config.getDouble("detection.look.min-change-degrees", 0.5);
    }

    public int getLookConsecutiveCount() {
        return config.getInt("detection.look.consecutive-count", 8);
    }

    // ===== 位置偵測參數 (壓力板+鐵地板門+音階盒釣魚機) =====

    public boolean isPositionAnalysisEnabled() {
        return config.getBoolean("detection.enabled.position-analysis", true);
    }

    public double getMinPositionDeviation() {
        return config.getDouble("detection.position.min-deviation-blocks", 0.5);
    }

    public double getYAxisJitterThreshold() {
        return config.getDouble("detection.position.y-jitter-threshold", 0.5);
    }

    public int getPositionMinSamples() {
        return config.getInt("detection.position.min-samples", 8);
    }

    // ===== 連續釣魚計數 =====

    public int getContinuousTriggerCount() {
        return config.getInt("detection.continuous.trigger-count", 50);
    }

    public int getContinuousResetTime() {
        return config.getInt("detection.continuous.reset-after-seconds", 60);
    }

    // ===== 成功率偵測 =====

    public double getSuspiciousSuccessRate() {
        return config.getDouble("detection.success-rate.suspicious-threshold", 0.95);
    }

    public int getMinSuccessRateAttempts() {
        return config.getInt("detection.success-rate.min-attempts", 20);
    }

    // ===== 分數權重 =====

    public int getIntervalWeight() {
        return config.getInt("scoring.weights.interval-analysis", 30);
    }

    public int getLookWeight() {
        return config.getInt("scoring.weights.look-direction", 20);
    }

    public int getContinuousWeight() {
        return config.getInt("scoring.weights.continuous-fishing", 35);
    }

    public int getSuccessRateWeight() {
        return config.getInt("scoring.weights.success-rate", 20);
    }

    // ===== 分數閾值 =====

    public int getWarningThreshold() {
        return config.getInt("scoring.thresholds.warning", 40);
    }

    public int getCancelCatchThreshold() {
        return config.getInt("scoring.thresholds.cancel-catch", 60);
    }

    public int getTempBanThreshold() {
        return config.getInt("scoring.thresholds.temp-ban-fishing", 80);
    }

    public int getExecuteCommandThreshold() {
        return config.getInt("scoring.thresholds.execute-command", 100);
    }

    // ===== 驗證設定 =====

    public boolean isVerificationEnabled() {
        return config.getBoolean("verification.enabled", true);
    }

    public List<String> getAllowedVerificationTypes() {
        return config.getStringList("verification.allowed-types");
    }

    public String getVerificationChatCode() {
        return config.getString("verification.chat-code", "verify");
    }

    public int getVerificationTimeout() {
        return config.getInt("verification.timeout-seconds", 10);
    }

    public int getVerificationFailurePenalty() {
        return config.getInt("verification.failure-penalty-level", 3);
    }

    public String getVerificationGuiTitle() {
        return config.getString("verification.gui.title", "Verification");
    }

    public String getVerificationGuiItemName() {
        return config.getString("verification.gui.item-name", "<green>Click Me!</green>");
    }

    // ===== 懲罰設定 =====

    public int getTempBanDuration() {
        return config.getInt("punishment.temp-ban-duration-seconds", 300);
    }

    public List<String> getExecuteCommands() {
        return config.getStringList("punishment.execute-commands");
    }

    // ===== 其他設定 =====

    public int getCheckInterval() {
        return config.getInt("general.check-interval-seconds", 30);
    }

    public boolean isDebugEnabled() {
        return config.getBoolean("general.debug", false);
    }

    public int getScoreDecayPerMinute() {
        return config.getInt("general.score-decay-per-minute", 5);
    }

    // ===== 互動頻率偵測 =====

    public boolean isInteractSpamEnabled() {
        return config.getBoolean("detection.interact-spam.enabled", true);
    }

    public int getInteractCpsThreshold() {
        return config.getInt("detection.interact-spam.cps-threshold", 4);
    }

    public int getInteractSpamTriggerSeconds() {
        return config.getInt("detection.interact-spam.trigger-seconds", 10);
    }

    public List<String> getInteractMonitoredBlocks() {
        return config.getStringList("detection.interact-spam.monitored-blocks");
    }
}
