package com.helloyu.antifishbot.detection;

import com.helloyu.antifishbot.AntiFishBot;
import com.helloyu.antifishbot.data.FishingData;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

/**
 * 主動陷阱管理器
 * 負責生成假訊號與延遲干擾
 */
public class TrapManager {

    private final AntiFishBot plugin;

    public TrapManager(AntiFishBot plugin) {
        this.plugin = plugin;
    }

    /**
     * 對可疑玩家施放 "假咬鉤" (Ghost Bite)
     * 發送聲音與粒子效果，但伺服器端不設定為咬鉤狀態。
     * 如果玩家在假訊號後 1 秒內收竿，極有可能是機器人。
     */
    public void triggerGhostBite(Player player, FishingData data) {
        FishHook hook = data.getHook();
        if (hook == null || !hook.isValid()) {
            return;
        }

        // 僅對目標玩家播放聲音與粒子
        Location loc = hook.getLocation();

        // 模擬咬鉤聲音
        player.playSound(loc, Sound.ENTITY_FISHING_BOBBER_SPLASH, 1.0f, 1.0f);

        // 模擬咬鉤粒子 (Splash)
        // 數量, offsetX, offsetY, offsetZ, speed
        player.spawnParticle(Particle.SPLASH, loc, 15, 0.2, 0.0, 0.2, 0.0);

        // 記錄陷阱時間
        data.setLastGhostBiteTime(System.currentTimeMillis());

        if (plugin.getConfigManager().isDebugEnabled()) {
            plugin.getLogger().info("[Trap] Triggered Ghost Bite for " + player.getName());
        }
    }
}
