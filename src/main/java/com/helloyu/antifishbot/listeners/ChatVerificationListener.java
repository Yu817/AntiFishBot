package com.helloyu.antifishbot.listeners;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.config.ConfigManager;
import com.helloyu.antifishbot.data.FishingData;
import com.helloyu.antifishbot.verification.VerificationHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * 聊天驗證監聽器
 */
public class ChatVerificationListener implements Listener {

    private final AntiFishBot plugin;
    private final ConfigManager config;
    private final VerificationHandler verification;

    public ChatVerificationListener(AntiFishBot plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.verification = plugin.getVerificationHandler();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        FishingData data = plugin.getFishingData(player);

        // 檢查是否正在等待驗證
        if (!data.isPendingVerification()) {
            return;
        }

        // 檢查驗證類型是否為 chat 或 math
        String type = data.getVerificationType();
        if (!"chat".equals(type) && !"math".equals(type)) {
            return;
        }

        // 檢查驗證是否超時
        if (verification.isVerificationExpired(data)) {
            // 切換回主執行緒處理失敗邏輯（因為這是 Async 事件）
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> verification.handleVerificationFailure(player, data));
            return;
        }

        String message = event.getMessage();
        String expectedCode = config.getVerificationChatCode();

        // 檢查是否匹配驗證碼
        if (message.equals(expectedCode)) {
            event.setCancelled(true); // 取消訊息發送

            // 切換回主執行緒處理成功邏輯
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> verification.handleVerificationSuccess(player, data));
        }
    }
}
