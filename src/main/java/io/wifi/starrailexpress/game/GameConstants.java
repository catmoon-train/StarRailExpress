package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public class GameConstants {
    // Logistics
    public static int FADE_TIME = 40;
    public static int FADE_PAUSE = 20;
    public static int MIN_PLAYER_COUNT = 6;
    public static Function<Long, Integer> PASSIVE_MONEY_TICKER = time -> {
        if (time % getInTicks(0, 10) == 0) {
            return 5;
        }
        return 0;
    };

    // Role Configuration (Server-side, mutable via command)
    public class RoleConfig {
        public static int killerCount = 1;
        public static int vigilanteCount = 1;
    }

    // Blocks
    public static int DOOR_AUTOCLOSE_TIME = getInTicks(0, 5);

    // Items
    public static Map<Item, Integer> ITEM_COOLDOWNS = new HashMap<>();

    /**
     * 初始化游戏常量
     * 在mod初始化时调用
     */
    public static void init() {
        reloadItemCooldowns();
    }

    /**
     * 重新加载物品冷却时间
     * 可以在运行时调用以应用配置更改
     */
    static void reloadItemCooldowns() {
        ITEM_COOLDOWNS.clear();
        ITEM_COOLDOWNS.put(TMMItems.KNIFE, SREConfig.instance().knifeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.REVOLVER, SREConfig.instance().revolverCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.DERRINGER, SREConfig.instance().derringerCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.GRENADE, SREConfig.instance().grenadeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.LOCKPICK, SREConfig.instance().lockpickCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.CROWBAR, SREConfig.instance().crowbarCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.BODY_BAG, SREConfig.instance().bodyBagCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.PSYCHO_MODE, SREConfig.instance().psychoModeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.BLACKOUT, SREConfig.instance().blackoutCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.NUNCHUCK, 160); // 8秒冷却
        ITEM_COOLDOWNS.put(TMMItems.SNIPER_RIFLE, 80); // 4秒冷却

        SRE.LOGGER.debug("物品冷却时间已重载: 小刀={}秒, 左轮={}秒",
                SREConfig.instance().knifeCooldown, SREConfig.instance().revolverCooldown);
    }

    public static int JAMMED_DOOR_TIME = getInTicks(1, 0);

    // Corpses
    public static int TIME_TO_DECOMPOSITION = getInTicks(1, 0);
    public static int DECOMPOSING_TIME = getInTicks(4, 0);

    // Task Variables
    public static float MOOD_GAIN = 0.5f;
    public static float MOOD_DRAIN = 1f / getInTicks(4, 0);
    public static int TIME_TO_FIRST_TASK = getInTicks(0, 30);
    public static int MIN_TASK_COOLDOWN = getInTicks(0, 30);
    public static int MAX_TASK_COOLDOWN = getInTicks(1, 0);

    // 连击奖励系统
    public static int STREAK_BONUS_PER_LEVEL = 5; // 每级连击额外金币
    public static int MAX_STREAK_BONUS = 25; // 最大连击额外金币（5级封顶）

    // 并列任务系统
    public static int PARALLEL_TASK_THRESHOLD = getInTicks(1, 0); // 任务超时阈值：60秒
    public static float PARALLEL_TASK_MOOD_DROP = 0.3f; // 情绪下降30%时触发并列任务
    public static float PARALLEL_TASK_REWARD_MULTIPLIER = 0.7f; // 并列任务奖励倍率（70%）

    /**
     * 根据游戏已过时间动态调整任务冷却
     * 游戏前期（<2分钟）：正常冷却 30-60秒
     * 游戏中期（2-5分钟）：冷却缩短至 25-50秒（约83%）
     * 游戏后期（>5分钟）：冷却缩短至 20-40秒（约67%）
     */
    public static int getDynamicMinTaskCooldown(long gameElapsedTicks) {
        if (gameElapsedTicks > getInTicks(5, 0)) {
            return getInTicks(0, 20); // 后期：20秒
        } else if (gameElapsedTicks > getInTicks(2, 0)) {
            return getInTicks(0, 25); // 中期：25秒
        }
        return MIN_TASK_COOLDOWN; // 前期：30秒
    }

    public static int getDynamicMaxTaskCooldown(long gameElapsedTicks) {
        if (gameElapsedTicks > getInTicks(5, 0)) {
            return getInTicks(0, 40); // 后期：40秒
        } else if (gameElapsedTicks > getInTicks(2, 0)) {
            return getInTicks(0, 50); // 中期：50秒
        }
        return MAX_TASK_COOLDOWN; // 前期：60秒
    }
    public static int SLEEP_TASK_DURATION = getInTicks(0, 8);
    public static int OUTSIDE_TASK_DURATION = getInTicks(0, 8);
    public static int READ_BOOK_TASK_DURATION = getInTicks(0, 8);
    public static int EXERCISE_TASK_DURATION = getInTicks(0, 6);
    public static int MEDITATE_TASK_DURATION = getInTicks(0, 10); // 冥想
    public static int NOTE_BLOCK_TASK_CLICK_COUNTS = 10; // 音符盒点击次数
    public static int TOILET_TASK_DURATION = getInTicks(0, 6);
    public static int CHAIR_TASK_DURATION = getInTicks(0, 8);
    public static int BATHE_TASK_DURATION = getInTicks(0, 10); // 洗澡任务持续时间
    public static int BREATHE_TASK_DURATION = getInTicks(0, 8); // 呼吸任务持续时间
    public static float MID_MOOD_THRESHOLD = 0.55f;
    public static float DEPRESSIVE_MOOD_THRESHOLD = 0.2f;
    public static float ANGRY_MOOD_THRESHOLD = 0.75f;
    public static float ITEM_PSYCHOSIS_CHANCE = .5f; // in percent
    public static int ITEM_PSYCHOSIS_REROLL_TIME = 200;

    // Shop Variables

    public static int getMoneyStart() {
        return SREConfig.instance().startingMoney;
    }

    public static Function<Long, Integer> getPassiveMoneyTicker() {
        return time -> {
            if (time % (SREConfig.instance().passiveMoneyInterval * 20) == 0) {
                return SREConfig.instance().passiveMoneyAmount;
            }
            return 0;
        };
    }

    public static int getMoneyPerKill() {
        return SREConfig.instance().moneyPerKill;
    }

    public static int getPsychoModeArmour() {
        return SREConfig.instance().psychoModeArmor;
    }

    // Timers
    public static int getPsychoTimer() {
        return SREConfig.instance().psychoModeDuration * 20;
    }

    public static int getFirecrackerTimer() {
        return SREConfig.instance().firecrackerDuration * 20;
    }

    public static int getBlackoutMinDuration() {
        return SREConfig.instance().blackoutMinDuration * 20;
    }

    public static int getBlackoutMaxDuration() {
        return SREConfig.instance().blackoutMaxDuration * 20;
    }

    public static int TIME_ON_CIVILIAN_KILL = getInTicks(0, 30);

    public static int getInTicks(int minutes, int seconds) {
        return (minutes * 60 + seconds) * 20;
    }

    public static class DeathReasons {
        public static ResourceLocation BACKFIRE = SRE.id("backfire");
        public static ResourceLocation GENERIC = SRE.id("generic");
        public static ResourceLocation KNIFE = SRE.id("knife_stab");
        public static ResourceLocation REVOLVER = SRE.id("revolver_shot");
        public static ResourceLocation DERRINGER = SRE.id("derringer_shot");
        public static ResourceLocation BAT = SRE.id("bat_hit");
        public static ResourceLocation GRENADE = SRE.id("grenade");
        public static ResourceLocation POISON = SRE.id("poison");
        public static ResourceLocation FELL_OUT_OF_TRAIN = SRE.id("fell_out_of_train");
        public static ResourceLocation ARROW = SRE.id("arrow");
        public static ResourceLocation TRIDENT = SRE.id("trident");
        public static ResourceLocation SNIPER_RIFLE = SRE.id("sniper_rifle");
        public static ResourceLocation SNIPER_RIFLE_BACKFIRE = SRE.id("sniper_rifle_backfire");
        public static ResourceLocation NUNCHUCK = SRE.id("nunchuck_hit");
    }
}