package com.helloyu.antifishbot.data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 玩家釣魚數據
 */
public class FishingData {

    private final UUID playerUuid;

    // 可疑分數
    private int suspiciousScore = 0;

    // 釣魚統計
    private int totalAttempts = 0; // 總嘗試次數
    private int successfulCatches = 0; // 成功次數
    private int continuousCount = 0; // 連續釣魚計數

    // 時間間隔追蹤
    private final List<Long> catchIntervals = new ArrayList<>();
    private long lastCatchTime = 0;

    // 視角追蹤
    private float lastYaw = 0;
    private float lastPitch = 0;
    private int noLookChangeCount = 0;

    // 移動追蹤 (移除)
    // private long lastInputTime = 0;
    // private Location lastLocation = null;

    // 驗證狀態
    private boolean pendingVerification = false;
    private String verificationType = null;
    private long verificationStartTime = 0;
    private String verificationAnswer = null;

    // 咬鉤時間
    private long lastBiteTime = 0;

    // 反應時間追蹤 (毫秒)
    private final List<Long> reactionTimes = new ArrayList<>();

    // 游標移動追蹤 (最近 20 次)
    private final List<Float> pitchChanges = new ArrayList<>();
    private final List<Float> yawChanges = new ArrayList<>();

    // 信任分數 (0.0 - 1.0)，初始為 0.5
    private double trustScore = 0.5;

    // 位置追蹤 (最近 20 次釣魚位置)
    private final List<double[]> fishingPositions = new ArrayList<>(); // [x, y, z]
    private final List<Double> yPositions = new ArrayList<>(); // 單獨追蹤 Y 軸用於微抖偵測

    // 重新拋竿間隔追蹤 (CAUGHT_FISH → FISHING 的時間差)
    private long lastCaughtTime = 0; // 上次釣到魚的時間
    private final List<Long> recastIntervals = new ArrayList<>(); // 最近 20 次的重新拋竿間隔

    public FishingData(UUID playerUuid) {
        this.playerUuid = playerUuid;
    }

    /**
     * 記錄一次成功的釣魚
     * 
     * @param reactionTime 反應時間 (ms)
     */
    public void recordCatch(long reactionTime) {
        long now = System.currentTimeMillis();

        totalAttempts++;
        successfulCatches++;
        continuousCount++;

        // 記錄反應時間
        reactionTimes.add(reactionTime);
        if (reactionTimes.size() > 20) {
            reactionTimes.remove(0);
        }

        // 記錄間隔
        if (lastCatchTime > 0) {
            long interval = now - lastCatchTime;
            catchIntervals.add(interval);

            // 只保留最近 20 次的間隔
            if (catchIntervals.size() > 20) {
                catchIntervals.remove(0);
            }
        }

        lastCatchTime = now;
    }

    /**
     * 記錄一次失敗的嘗試
     */
    public void recordFailedAttempt() {
        totalAttempts++;
        continuousCount = 0; // 失敗重置連續計數
    }

    /**
     * 更新視角數據
     */
    public void updateLookDirection(float yaw, float pitch) {
        float yawDiff = Math.abs(yaw - lastYaw);
        float pitchDiff = Math.abs(pitch - lastPitch);

        // 處理 yaw 翻轉 (-180 到 180)
        if (yawDiff > 180) {
            yawDiff = 360 - yawDiff;
        }

        double totalChange = Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);

        if (totalChange < 0.1) { // 如果移動極小
            noLookChangeCount++;
        } else {
            noLookChangeCount = 0;
            // 只有顯著的移動才記錄到列表中用於分析
            yawChanges.add(yawDiff);
            pitchChanges.add(pitchDiff);
            if (yawChanges.size() > 20)
                yawChanges.remove(0);
            if (pitchChanges.size() > 20)
                pitchChanges.remove(0);
        }

        lastYaw = yaw;
        lastPitch = pitch;
    }

    /**
     * 記錄玩家輸入（移動）- 已移除
     */
    /*
     * public void recordInput() {
     * lastInputTime = System.currentTimeMillis();
     * }
     */

    /**
     * 計算數列的香農熵 (Shannon Entropy)
     * 用於檢測數據的隨機性。機器人通常具有低熵（規律），人類具有高熵（隨機）。
     */
    public double calculateEntropy(List<? extends Number> data) {
        if (data == null || data.size() < 5)
            return 0.0;

        // 1. 將數據標準化並分桶 (Binning)
        // 為了計算概率分佈，我們需要將連續數值放入離散的桶中
        // 這裡使用簡單的動態分桶

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (Number n : data) {
            long val = n.longValue();
            if (val < min)
                min = val;
            if (val > max)
                max = val;
        }

        if (min == max)
            return 0.0; // 所有數值相同，熵為 0

        int numBins = Math.max(5, data.size() / 2); // 桶的數量
        long range = max - min + 1;
        int[] bins = new int[numBins];

        for (Number n : data) {
            long val = n.longValue();
            int binIndex = (int) ((val - min) * numBins / range);
            if (binIndex >= numBins)
                binIndex = numBins - 1;
            bins[binIndex]++;
        }

        // 2. 計算熵
        double entropy = 0.0;
        int total = data.size();

        for (int count : bins) {
            if (count > 0) {
                double p = (double) count / total;
                entropy -= p * Math.log(p) / Math.log(2); // log base 2
            }
        }

        return entropy;
    }

    /**
     * 計算反應時間的熵
     */
    public double calculateReactionEntropy() {
        return calculateEntropy(reactionTimes);
    }

    /**
     * 計算時間間隔的熵
     */
    public double calculateIntervalEntropy() {
        return calculateEntropy(catchIntervals);
    }

    /**
     * 計算視角變化的熵
     */
    public double calculateLookEntropy() {
        // 合併 yaw 和 pitch 的數據進行分析
        List<Float> combined = new ArrayList<>();
        combined.addAll(yawChanges);
        combined.addAll(pitchChanges);
        return calculateEntropy(combined);
    }

    /**
     * 計算時間間隔的標準差
     */
    public double calculateIntervalDeviation() {
        if (catchIntervals.size() < 2) {
            return Double.MAX_VALUE;
        }

        double sum = 0;
        for (long interval : catchIntervals) {
            sum += interval;
        }
        double mean = sum / catchIntervals.size();

        double standardDeviation = 0;
        for (long interval : catchIntervals) {
            standardDeviation += Math.pow(interval - mean, 2);
        }

        return Math.sqrt(standardDeviation / catchIntervals.size());
    }

    /**
     * 記錄一次釣魚時玩家的位置
     */
    public void recordFishingPosition(double x, double y, double z) {
        fishingPositions.add(new double[] { x, y, z });
        yPositions.add(y);
        if (fishingPositions.size() > 20) {
            fishingPositions.remove(0);
        }
        if (yPositions.size() > 20) {
            yPositions.remove(0);
        }
    }

    /**
     * 計算 XZ 平面位置的標準差
     * 正常玩家會走動換位，AFK 釣魚機的玩家 XZ 幾乎完全不變
     *
     * @return XZ 位移的標準差（格），越小代表越固定
     */
    public double calculatePositionDeviation() {
        if (fishingPositions.size() < 5) {
            return Double.MAX_VALUE; // 樣本不足
        }

        // 計算 X 和 Z 的平均值
        double sumX = 0, sumZ = 0;
        for (double[] pos : fishingPositions) {
            sumX += pos[0];
            sumZ += pos[2];
        }
        double meanX = sumX / fishingPositions.size();
        double meanZ = sumZ / fishingPositions.size();

        // 計算每個點到平均位置的距離的標準差
        double sumSqDist = 0;
        for (double[] pos : fishingPositions) {
            double dx = pos[0] - meanX;
            double dz = pos[2] - meanZ;
            sumSqDist += dx * dx + dz * dz;
        }

        return Math.sqrt(sumSqDist / fishingPositions.size());
    }

    /**
     * 偵測 Y 軸微抖特徵（地板門機制的指紋）
     * 壓力板釣魚機會讓玩家 Y 軸在極小範圍（±0.5 格）內反覆波動
     *
     * @param threshold Y 軸波動閾值（格）
     * @return 是否偵測到微抖特徵
     */
    public boolean detectYAxisJitter(double threshold) {
        if (yPositions.size() < 5) {
            return false;
        }

        // 計算 Y 軸的最大值和最小值差距
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        for (double y : yPositions) {
            if (y < minY)
                minY = y;
            if (y > maxY)
                maxY = y;
        }
        double yRange = maxY - minY;

        // 如果 Y 軸範圍在閾值內，但不是完全靜止（有微小變化）
        // 這是地板門開合的典型特徵
        if (yRange > 0.01 && yRange <= threshold) {
            // 進一步檢查：是否有反覆上下運動的模式
            int directionChanges = 0;
            for (int i = 2; i < yPositions.size(); i++) {
                double prev = yPositions.get(i - 1) - yPositions.get(i - 2);
                double curr = yPositions.get(i) - yPositions.get(i - 1);
                if ((prev > 0 && curr < 0) || (prev < 0 && curr > 0)) {
                    directionChanges++;
                }
            }
            // 如果方向變化次數超過 Y 數據量的 30%，判定為微抖
            return directionChanges >= (yPositions.size() - 2) * 0.3;
        }

        return false;
    }

    public List<double[]> getFishingPositions() {
        return fishingPositions;
    }

    /**
     * 記錄收竿時間 (用於計算重新拋竿間隔)
     */
    public void recordCaughtTime() {
        lastCaughtTime = System.currentTimeMillis();
    }

    /**
     * 記錄重新拋竿並計算間隔
     * 在 FISHING 狀態時呼叫，計算與上次 CAUGHT_FISH 的時間差
     */
    public void recordRecast() {
        if (lastCaughtTime > 0) {
            long interval = System.currentTimeMillis() - lastCaughtTime;
            // 只記錄合理範圍內的間隔 (小於 10 秒，避免重新登入或長時間未釣魚的干擾)
            if (interval < 10000) {
                recastIntervals.add(interval);
                if (recastIntervals.size() > 20) {
                    recastIntervals.remove(0);
                }
            }
            lastCaughtTime = 0; // 重置，避免重複計算
        }
    }

    /**
     * 計算平均重新拋竿間隔 (ms)
     * AFK 釣魚機因持續按住右鍵，間隔會極短 (~50-100ms)
     * 正常玩家通常需要 1-5 秒才會重新拋竿
     */
    public double getAverageRecastInterval() {
        if (recastIntervals.size() < 3) {
            return Double.MAX_VALUE;
        }
        double sum = 0;
        for (long interval : recastIntervals) {
            sum += interval;
        }
        return sum / recastIntervals.size();
    }

    /**
     * 取得重新拋竿間隔的標準差
     * AFK 釣魚機的間隔極其穩定（標準差極小）
     */
    public double getRecastDeviation() {
        if (recastIntervals.size() < 3) {
            return Double.MAX_VALUE;
        }
        double avg = getAverageRecastInterval();
        double sumSq = 0;
        for (long interval : recastIntervals) {
            sumSq += Math.pow(interval - avg, 2);
        }
        return Math.sqrt(sumSq / recastIntervals.size());
    }

    public List<Long> getRecastIntervals() {
        return recastIntervals;
    }

    /**
     * 計算成功率
     */
    public double getSuccessRate() {
        if (totalAttempts == 0) {
            return 0;
        }
        return (double) successfulCatches / totalAttempts;
    }

    /**
     * 取得自上次輸入以來的秒數 (已移除)
     */
    /*
     * public long getSecondsSinceLastInput() {
     * return (System.currentTimeMillis() - lastInputTime) / 1000;
     * }
     */

    /**
     * 增加可疑分數
     */
    public void addScore(int amount) {
        suspiciousScore += amount;
    }

    /**
     * 減少可疑分數（衰減）
     */
    public void decayScore(int amount) {
        suspiciousScore = Math.max(0, suspiciousScore - amount);
    }

    /**
     * 重置連續計數
     */
    public void resetContinuousCount() {
        continuousCount = 0;
    }

    /**
     * 開始驗證
     */
    public void startVerification(String type) {
        pendingVerification = true;
        verificationType = type;
        verificationStartTime = System.currentTimeMillis();
    }

    /**
     * 完成驗證
     */
    public void completeVerification() {
        pendingVerification = false;
        verificationType = null;
        verificationStartTime = 0;
        verificationAnswer = null;
    }

    // ===== Getters and Setters =====

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getSuspiciousScore() {
        return suspiciousScore;
    }

    public void setSuspiciousScore(int score) {
        this.suspiciousScore = score;
    }

    public int getTotalAttempts() {
        return totalAttempts;
    }

    public int getSuccessfulCatches() {
        return successfulCatches;
    }

    public int getContinuousCount() {
        return continuousCount;
    }

    public int getNoLookChangeCount() {
        return noLookChangeCount;
    }

    public long getLastCatchTime() {
        return lastCatchTime;
    }

    public boolean isPendingVerification() {
        return pendingVerification;
    }

    public String getVerificationType() {
        return verificationType;
    }

    public long getVerificationStartTime() {
        return verificationStartTime;
    }

    /*
     * public Location getLastLocation() {
     * return lastLocation;
     * }
     * 
     * public void setLastLocation(Location location) {
     * this.lastLocation = location;
     * }
     */

    public List<Long> getCatchIntervals() {
        return catchIntervals;
    }

    public List<Long> getReactionTimes() {
        return reactionTimes;
    }

    public String getVerificationAnswer() {
        return verificationAnswer;
    }

    public void setVerificationAnswer(String verificationAnswer) {
        this.verificationAnswer = verificationAnswer;
    }

    public double getTrustScore() {
        return trustScore;
    }

    public void setTrustScore(double trustScore) {
        this.trustScore = Math.max(0.0, Math.min(1.0, trustScore));
    }

    public long getLastBiteTime() {
        return lastBiteTime;
    }

    public void setLastBiteTime(long lastBiteTime) {
        this.lastBiteTime = lastBiteTime;
    }

    private org.bukkit.entity.FishHook hook;

    public void setHook(org.bukkit.entity.FishHook hook) {
        this.hook = hook;
    }

    public org.bukkit.entity.FishHook getHook() {
        return hook;
    }

    private long lastGhostBiteTime = 0;

    public void setLastGhostBiteTime(long lastGhostBiteTime) {
        this.lastGhostBiteTime = lastGhostBiteTime;
    }

    public long getLastGhostBiteTime() {
        return lastGhostBiteTime;
    }

    private Integer currentTrapTask = null;

    public void setCurrentTrapTask(Integer currentTrapTask) {
        this.currentTrapTask = currentTrapTask;
    }

    public Integer getCurrentTrapTask() {
        return currentTrapTask;
    }

    // ===== 互動頻率追蹤 =====

    private long lastInteractCheckTime = 0;
    private int currentSecondInteracts = 0;
    private int highCpsSeconds = 0;

    /**
     * 記錄一次互動，並檢查是否超過閾值
     * 
     * @param cpsThreshold 每秒點擊次數閾值
     * @return 是否達到高頻點擊標準 (該秒)
     */
    public boolean recordInteract(int cpsThreshold) {
        long now = System.currentTimeMillis();
        long currentSecond = now / 1000;

        if (currentSecond > lastInteractCheckTime) {
            // 進入新的一秒，重置計數
            lastInteractCheckTime = currentSecond;
            currentSecondInteracts = 1;
            return false; // 新的一秒剛開始，還沒達到閾值
        } else {
            // 同一秒內
            currentSecondInteracts++;

            // 如果剛好達到閾值 (避免重複計算)
            if (currentSecondInteracts == cpsThreshold) {
                highCpsSeconds++;
                return true;
            }
        }
        return false;
    }

    public void resetHighCpsSeconds() {
        highCpsSeconds = 0;
    }

    public int getHighCpsSeconds() {
        return highCpsSeconds;
    }
}
