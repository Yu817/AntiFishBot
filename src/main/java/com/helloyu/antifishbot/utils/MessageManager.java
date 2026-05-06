package com.helloyu.antifishbot.utils;

import com.helloyu.antifishbot.AntiFishBot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.kyori.adventure.title.Title;

import java.time.Duration;

import java.io.File;

/**
 * 訊息管理器
 */
public class MessageManager {

    private final AntiFishBot plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration messagesConfig;
    private String prefix;

    public MessageManager(AntiFishBot plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    /**
     * 重新載入訊息設定
     */
    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");

        // 如果檔案不存在，從資源複製
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        prefix = messagesConfig.getString("prefix", "<gray>[<aqua>AntiFishBot</aqua>]</gray> ");
    }

    /**
     * 發送訊息給玩家
     * 
     * @param player       目標玩家
     * @param key          訊息鍵值（如 "player.warning"）
     * @param replacements 替換對，如 "%player%", "Steve"
     */
    public void sendMessage(Player player, String key, String... replacements) {
        String message = getMessage(key);

        if (message == null || message.isEmpty()) {
            return;
        }

        // 應用替換
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        // 添加前綴並解析 MiniMessage
        Component component = miniMessage.deserialize(prefix + message);
        player.sendMessage(component);
    }

    /**
     * 發送無前綴的訊息
     */
    public void sendRawMessage(Player player, String key, String... replacements) {
        String message = getMessage(key);

        if (message == null || message.isEmpty()) {
            return;
        }

        // 應用替換
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        Component component = miniMessage.deserialize(message);
        player.sendMessage(component);
    }

    /**
     * 取得格式化後的訊息文字
     */
    public String getFormattedMessage(String key, String... replacements) {
        String message = getMessage(key);

        if (message == null) {
            return "";
        }

        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        return message;
    }

    /**
     * 取得原始訊息
     */
    public String getMessage(String key) {
        return messagesConfig.getString(key);
    }

    /**
     * 解析 MiniMessage 格式
     */
    public Component parse(String message) {
        return miniMessage.deserialize(message);
    }

    /**
     * 解析並添加前綴
     */
    public Component parseWithPrefix(String message) {
        return miniMessage.deserialize(prefix + message);
    }

    /**
     * 發送 Title 訊息（螢幕中間大字）
     *
     * @param player       目標玩家
     * @param titleKey     標題訊息鍵值
     * @param subtitleKey  副標題訊息鍵值（可為 null）
     * @param replacements 替換對
     */
    public void sendTitle(Player player, String titleKey, String subtitleKey, String... replacements) {
        String titleMessage = getMessage(titleKey);
        String subtitleMessage = subtitleKey != null ? getMessage(subtitleKey) : null;

        if (titleMessage == null || titleMessage.isEmpty()) {
            return;
        }

        // 應用替換
        for (int i = 0; i < replacements.length - 1; i += 2) {
            titleMessage = titleMessage.replace(replacements[i], replacements[i + 1]);
            if (subtitleMessage != null) {
                subtitleMessage = subtitleMessage.replace(replacements[i], replacements[i + 1]);
            }
        }

        Component title = miniMessage.deserialize(titleMessage);
        Component subtitle = subtitleMessage != null ? miniMessage.deserialize(subtitleMessage) : Component.empty();

        Title.Times times = Title.Times.times(
                Duration.ofMillis(200), // fadeIn
                Duration.ofMillis(2000), // stay
                Duration.ofMillis(500) // fadeOut
        );

        player.showTitle(Title.title(title, subtitle, times));
    }

    /**
     * 發送 ActionBar 訊息（螢幕下方快捷列上方）
     *
     * @param player       目標玩家
     * @param key          訊息鍵值
     * @param replacements 替換對
     */
    public void sendActionBar(Player player, String key, String... replacements) {
        String message = getMessage(key);

        if (message == null || message.isEmpty()) {
            return;
        }

        // 應用替換
        for (int i = 0; i < replacements.length - 1; i += 2) {
            message = message.replace(replacements[i], replacements[i + 1]);
        }

        Component component = miniMessage.deserialize(message);
        player.sendActionBar(component);
    }

    /**
     * 直接發送 ActionBar 訊息（使用原始文字）
     *
     * @param player  目標玩家
     * @param message 直接的訊息內容（MiniMessage 格式）
     */
    public void sendActionBarRaw(Player player, String message) {
        if (message == null || message.isEmpty()) {
            return;
        }
        Component component = miniMessage.deserialize(message);
        player.sendActionBar(component);
    }
}
