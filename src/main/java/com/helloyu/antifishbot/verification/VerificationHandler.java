package com.helloyu.antifishbot.verification;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.config.ConfigManager;
import com.helloyu.antifishbot.data.FishingData;
import com.helloyu.antifishbot.utils.MessageManager;
import org.bukkit.entity.Player;

/**
 * 驗證機制處理器
 */
public class VerificationHandler {

    private final AntiFishBot plugin;
    private final ConfigManager config;
    private final MessageManager messages;

    public VerificationHandler(AntiFishBot plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.messages = plugin.getMessageManager();
    }

    /**
     * 開始驗證流程
     */
    /**
     * 開始驗證流程
     */
    public void startVerification(Player player, FishingData data) {
        // 隨機選擇驗證類型
        java.util.List<String> allowedTypes = config.getAllowedVerificationTypes();
        String type;
        if (allowedTypes == null || allowedTypes.isEmpty()) {
            type = "jump"; // Default fallback
        } else {
            type = allowedTypes.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(allowedTypes.size()));
        }

        int timeout = config.getVerificationTimeout();

        data.startVerification(type);
        plugin.incrementVerificationCount();

        // 發送驗證提示
        String messageKey = "verification." + type + ".prompt";
        if ("chat".equals(type)) {
            messages.sendMessage(player, messageKey,
                    "%timeout%", String.valueOf(timeout),
                    "%code%", config.getVerificationChatCode());
        } else {
            messages.sendMessage(player, messageKey, "%timeout%", String.valueOf(timeout));
        }

        // 設置超時檢查
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (data.isPendingVerification()) {
                handleVerificationFailure(player, data);
            }
        }, timeout * 20L); // 轉換為 ticks
    }

    /**
     * 處理驗證成功
     */
    public void handleVerificationSuccess(Player player, FishingData data) {
        if (!data.isPendingVerification()) {
            return;
        }

        String type = data.getVerificationType();
        data.completeVerification();

        // 減少一些可疑分數作為獎勵
        data.decayScore(20);

        // 發送成功訊息
        String messageKey = "verification." + type + ".success";
        messages.sendMessage(player, messageKey);
    }

    /**
     * 處理驗證失敗
     */
    public void handleVerificationFailure(Player player, FishingData data) {
        if (!data.isPendingVerification()) {
            return;
        }

        String type = data.getVerificationType();
        data.completeVerification();

        // 發送失敗訊息
        String messageKey = "verification." + type + ".failure";
        messages.sendMessage(player, messageKey);

        // 根據設定的懲罰等級執行懲罰
        int penaltyLevel = config.getVerificationFailurePenalty();
        applyPenalty(player, data, penaltyLevel);
    }

    /**
     * 應用懲罰
     */
    private void applyPenalty(Player player, FishingData data, int level) {
        switch (level) {
            case 4: // 執行指令
                for (String command : config.getExecuteCommands()) {
                    String finalCommand = command.replace("%player%", player.getName());
                    plugin.getServer().dispatchCommand(
                            plugin.getServer().getConsoleSender(), finalCommand);
                }
                plugin.incrementPunishmentCount();
                break;
            case 3: // 禁止釣魚
                plugin.banFishing(player, config.getTempBanDuration());
                plugin.incrementPunishmentCount();
                break;
            case 2: // 取消成果（這裡無法取消，改為禁止短時間）
                plugin.banFishing(player, 60);
                plugin.incrementPunishmentCount();
                break;
            case 1: // 警告
                messages.sendMessage(player, "player.warning");
                break;
            default:
                break;
        }

        // 增加可疑分數
        data.addScore(30);
    }

    /**
     * 檢查驗證是否已超時
     */
    /**
     * 開啟驗證 GUI
     */
    private void openVerificationGUI(Player player) {
        // 建立 9 格的物品欄
        org.bukkit.inventory.Inventory gui = plugin.getServer().createInventory(player, 9,
                messages.parse(config.getVerificationGuiTitle()));

        // 隨機放置物品
        int validSlot = java.util.concurrent.ThreadLocalRandom.current().nextInt(9);

        // 填充物品
        org.bukkit.inventory.ItemStack barrier = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.RED_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta barrierMeta = barrier.getItemMeta();
        barrierMeta.displayName(messages.parse("<red>Don't Click Me!</red>"));
        barrier.setItemMeta(barrierMeta);

        org.bukkit.inventory.ItemStack target = new org.bukkit.inventory.ItemStack(
                org.bukkit.Material.LIME_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta targetMeta = target.getItemMeta();
        targetMeta.displayName(
                messages.parse(config.getVerificationGuiItemName()));
        target.setItemMeta(targetMeta);

        for (int i = 0; i < 9; i++) {
            if (i == validSlot) {
                gui.setItem(i, target);
            } else {
                gui.setItem(i, barrier);
            }
        }

        player.openInventory(gui);
    }

    public boolean isVerificationExpired(FishingData data) {
        if (!data.isPendingVerification()) {
            return false;
        }

        long elapsed = System.currentTimeMillis() - data.getVerificationStartTime();
        long timeoutMs = config.getVerificationTimeout() * 1000L;

        return elapsed >= timeoutMs;
    }
}
