package com.helloyu.antifishbot.listeners;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.config.ConfigManager;
import com.helloyu.antifishbot.data.FishingData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class InteractListener implements Listener {

    private final AntiFishBot plugin;
    private final ConfigManager config;

    public InteractListener(AntiFishBot plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!config.isInteractSpamEnabled()) {
            return;
        }

        // 只檢查右鍵點擊方塊
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 只檢查主手 (避免雙手觸發兩次)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();

        // 必須手持釣竿
        if (mainHand.getType() != Material.FISHING_ROD) {
            return;
        }

        Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        // 檢查是否為監控的方塊類型
        if (!config.getInteractMonitoredBlocks().contains(clickedBlock.getType().name())) {
            return;
        }

        // 檢查繞過權限
        if (player.hasPermission("antifishbot.bypass")) {
            return;
        }

        FishingData data = plugin.getFishingData(player);
        int cpsThreshold = config.getInteractCpsThreshold();

        // 記錄互動並檢查是否達到當秒的閾值
        boolean isHighCps = data.recordInteract(cpsThreshold);

        if (isHighCps) {
            int currentHighSeconds = data.getHighCpsSeconds();
            int triggerSeconds = config.getInteractSpamTriggerSeconds();

            // 如果連續達到閾值的秒數超過設定
            if (currentHighSeconds >= triggerSeconds) {
                // 懲罰機制
                // 降低信任分數
                double currentTrust = data.getTrustScore();
                if (currentTrust > 0.1) {
                    data.setTrustScore(currentTrust - 0.1);
                }

                // 增加可疑分數
                data.addScore(10);

                if (config.isDebugEnabled()) {
                    plugin.getLogger().info(String.format("[DEBUG] 玩家 %s 觸發互動頻率偵測 (連續 %d 秒 CPS >= %d)",
                            player.getName(), currentHighSeconds, cpsThreshold));
                }

                // 發送給開啟除錯模式的在線管理員
                String debugMsg = String.format("§7[DEBUG] 玩家 %s 觸發互動頻率偵測 (連續 %d 秒 CPS >= %d)",
                        player.getName(), currentHighSeconds, cpsThreshold);
                for (java.util.UUID uuid : plugin.getDebugPlayers()) {
                    Player p = org.bukkit.Bukkit.getPlayer(uuid);
                    if (p != null && p.isOnline()) {
                        p.sendMessage(debugMsg);
                    }
                }

                // 重置計數，避免一直連續觸發
                data.resetHighCpsSeconds();
            }
        }
    }
}
