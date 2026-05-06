package com.helloyu.antifishbot.listeners;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.data.FishingData;
import com.helloyu.antifishbot.utils.MessageManager;
import com.helloyu.antifishbot.verification.VerificationHandler;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;

/**
 * GUI 驗證監聽器
 */
public class GUIVerificationListener implements Listener {

    private final AntiFishBot plugin;
    private final VerificationHandler verification;
    private final MessageManager messages;

    public GUIVerificationListener(AntiFishBot plugin) {
        this.plugin = plugin;
        this.verification = plugin.getVerificationHandler();
        this.messages = plugin.getMessageManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        FishingData data = plugin.getFishingData(player);

        // 檢查是否正在等待驗證且類型為 gui
        if (!data.isPendingVerification() || !"gui".equals(data.getVerificationType())) {
            return;
        }

        // 檢查標題（簡單檢查）
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        // 為了兼容性，這裡做一個簡單的標題包含檢查，或者依賴 InventoryHolder
        // 這裡假設驗證 GUI 是我們打開的，且在驗證狀態下

        // 禁止拿取物品
        event.setCancelled(true);

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 檢查是否點擊正確物品 (綠色玻璃)
        if (clickedItem.getType() == Material.LIME_STAINED_GLASS_PANE) {
            player.closeInventory();
            verification.handleVerificationSuccess(player, data);
        } else {
            // 點錯了 (紅色玻璃)
            player.closeInventory();
            messages.sendMessage(player, "verification.gui.failure");
            verification.handleVerificationFailure(player, data);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        FishingData data = plugin.getFishingData(player);

        // 如果在驗證期間關閉 GUI，視為失敗（或要求重新打開，這裡從嚴處理）
        if (data.isPendingVerification() && "gui".equals(data.getVerificationType())) {
            // 延遲檢查，避免因為驗證成功導致的關閉被誤判
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (data.isPendingVerification()) {
                    verification.handleVerificationFailure(player, data);
                }
            }, 5L);
        }
    }
}
