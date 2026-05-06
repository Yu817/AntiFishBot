package com.helloyu.antifishbot.commands;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.data.FishingData;
import com.helloyu.antifishbot.utils.MessageManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 管理員指令處理
 */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private final AntiFishBot plugin;
    private final MessageManager messages;

    public AdminCommand(AntiFishBot plugin) {
        this.plugin = plugin;
        this.messages = plugin.getMessageManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("antifishbot.admin")) {
            sender.sendMessage(Component.text("§c你沒有權限使用此指令！"));
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "check":
                handleCheck(sender, args);
                break;
            case "stats":
                handleStats(sender);
                break;
            case "reset":
                handleReset(sender, args);
                break;
            case "debug":
                handleDebug(sender);
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    /**
     * 處理 debug 指令
     */
    private void handleDebug(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c此指令只能由玩家使用！");
            return;
        }
        Player player = (Player) sender;
        boolean enabled = plugin.toggleDebug(player);
        if (enabled) {
            sender.sendMessage("§a已開啟除錯模式，您將能看到偵測日誌。");
        } else {
            sender.sendMessage("§c已關閉除錯模式。");
        }
    }

    /**
     * 處理 reload 指令
     */
    private void handleReload(CommandSender sender) {
        try {
            plugin.reload();
            sendAdminMessage(sender, "admin.reload-success");
        } catch (Exception e) {
            sendAdminMessage(sender, "admin.reload-failed", "%error%", e.getMessage());
        }
    }

    /**
     * 處理 check 指令
     */
    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendAdminMessage(sender, "admin.player-not-found", "%player%", args[1]);
            return;
        }

        FishingData data = plugin.getFishingData(target);

        // 發送檢查報告
        sendAdminMessage(sender, "admin.check-header", "%player%", target.getName());
        sendAdminMessage(sender, "admin.check-score", "%score%", String.valueOf(data.getSuspiciousScore()));
        sendAdminMessage(sender, "admin.check-fishing-count", "%count%", String.valueOf(data.getContinuousCount()));

        double successRate = data.getSuccessRate() * 100;
        sendAdminMessage(sender, "admin.check-success-rate", "%rate%", String.format("%.1f", successRate));

        double deviation = data.calculateIntervalDeviation();
        String deviationStr = deviation == Double.MAX_VALUE ? "N/A" : String.format("%.0f", deviation);
        sendAdminMessage(sender, "admin.check-last-interval", "%deviation%", deviationStr);

        // 狀態
        String statusKey;
        if (plugin.isFishingBanned(target)) {
            statusKey = "admin.status-banned";
        } else if (data.getSuspiciousScore() >= plugin.getConfigManager().getWarningThreshold()) {
            statusKey = "admin.status-suspicious";
        } else {
            statusKey = "admin.status-normal";
        }

        String status = messages.getMessage(statusKey);
        sendAdminMessage(sender, "admin.check-status", "%status%", status);
    }

    /**
     * 處理 stats 指令
     */
    private void handleStats(CommandSender sender) {
        sendAdminMessage(sender, "admin.stats-header");
        sendAdminMessage(sender, "admin.stats-tracking",
                "%count%", String.valueOf(plugin.getFishingDataMap().size()));
        sendAdminMessage(sender, "admin.stats-verifications",
                "%count%", String.valueOf(plugin.getTodayVerifications()));
        sendAdminMessage(sender, "admin.stats-punishments",
                "%count%", String.valueOf(plugin.getTodayPunishments()));
    }

    /**
     * 處理 reset 指令
     */
    private void handleReset(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sendAdminMessage(sender, "admin.player-not-found", "%player%", args[1]);
            return;
        }

        plugin.resetFishingData(target.getUniqueId());
        sendAdminMessage(sender, "admin.reset-success", "%player%", target.getName());
    }

    /**
     * 發送用法訊息
     */
    private void sendUsage(CommandSender sender) {
        String usage = messages.getMessage("admin.usage");
        if (usage != null) {
            sender.sendMessage(messages.parseWithPrefix(usage));
        }
    }

    /**
     * 發送管理員訊息
     */
    private void sendAdminMessage(CommandSender sender, String key, String... replacements) {
        String message = messages.getFormattedMessage(key, replacements);
        if (message != null && !message.isEmpty()) {
            sender.sendMessage(messages.parseWithPrefix(message));
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("antifishbot.admin")) {
            return new ArrayList<>();
        }

        if (args.length == 1) {
            return Arrays.asList("reload", "check", "stats", "reset", "debug").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("check") || args[0].equalsIgnoreCase("reset"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
