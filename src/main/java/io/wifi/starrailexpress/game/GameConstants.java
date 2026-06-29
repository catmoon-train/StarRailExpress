package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.util.DeathReasonRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

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

    public static int getBlackoutCooldownGlobal() {
        return SREConfig.instance().blackoutCooldownGlobal * 20;
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
        ITEM_COOLDOWNS.put(Items.TRIDENT, 5 * 20);
        ITEM_COOLDOWNS.put(TMMItems.REVOLVER, SREConfig.instance().revolverCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.STANDARD_REVOLVER, SREConfig.instance().revolverCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.DERRINGER, SREConfig.instance().derringerCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.GRENADE, SREConfig.instance().grenadeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.LOCKPICK, SREConfig.instance().lockpickCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.CROWBAR, SREConfig.instance().crowbarCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.BODY_BAG, SREConfig.instance().bodyBagCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.PSYCHO_MODE, SREConfig.instance().psychoModeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.BLACKOUT, SREConfig.instance().blackoutCooldown * 20);
        ITEM_COOLDOWNS.put(ModItems.SHERIFF_REVOLVER, SREConfig.instance().sheriffRevolverReloadCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.MONITOR_BROKEN, SREConfig.instance().monitorBrokenCooldown * 20);
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
    // 完成一个小游戏任务（指定方块）发放的游戏代币数量（降频后翻倍补偿）
    public static int MINIGAME_TASK_TOKEN_REWARD = 2;
    // 理智流失：单个任务从满到空的时间，4分钟→6分钟，减轻任务treadmill
    public static float MOOD_DRAIN = 1f / getInTicks(6, 0);
    public static int TIME_TO_FIRST_TASK = getInTicks(0, 30);
    public static int MIN_TASK_COOLDOWN = getInTicks(0, 40);
    public static int MAX_TASK_COOLDOWN = getInTicks(1, 15);

    // 连击奖励系统
    public static int STREAK_BONUS_PER_LEVEL = 5; // 每级连击额外金币
    public static int MAX_STREAK_BONUS = 10; // 最大连击额外金币（5级封顶）

    // 并列任务系统
    public static int PARALLEL_TASK_THRESHOLD = getInTicks(1, 10); // 任务超时阈值：70秒
    public static float PARALLEL_TASK_MOOD_DROP = 0.4f; // 情绪下降40%时触发并列任务
    public static float PARALLEL_TASK_REWARD_MULTIPLIER = 1.0f; // 并列任务奖励倍率（完成一个另一个消失，给予完整奖励）
    public static float PARALLEL_TASK_COMPLETION_BONUS = 0.3f; // 并列任务完成额外情绪加成（选择奖励）

    /**
     * 根据游戏已过时间动态调整任务冷却
     * 游戏前期（<2分钟）：正常冷却 30-60秒
     * 游戏中期（2-5分钟）：冷却缩短至 25-50秒（约83%）
     * 游戏后期（>5分钟）：冷却缩短至 20-40秒（约67%）
     */
    public static int getDynamicMinTaskCooldown(long gameElapsedTicks) {
        if (gameElapsedTicks > getInTicks(5, 0)) {
            return getInTicks(0, 30); // 后期：30秒
        } else if (gameElapsedTicks > getInTicks(2, 0)) {
            return getInTicks(0, 35); // 中期：35秒
        }
        return MIN_TASK_COOLDOWN; // 前期：40秒
    }

    public static int getDynamicMaxTaskCooldown(long gameElapsedTicks) {
        if (gameElapsedTicks > getInTicks(5, 0)) {
            return getInTicks(0, 55); // 后期：55秒
        } else if (gameElapsedTicks > getInTicks(2, 0)) {
            return getInTicks(1, 5); // 中期：65秒
        }
        return MAX_TASK_COOLDOWN; // 前期：75秒
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
    public static int BE_ALONE_TASK_DURATION = getInTicks(0, 10); // 一个人静静任务持续时间（与冥想一致）
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

    public static float getBlackoutRandomRangePercent() {
        return SREConfig.instance().blackoutRandomRangePercent;
    }

    public static int getBlackoutMaxDuration() {
        return SREConfig.instance().blackoutMaxDuration * 20;
    }

    public static int TIME_ON_CIVILIAN_KILL = getInTicks(0, 30);

    public static int getInTicks(int minutes, int seconds) {
        return (minutes * 60 + seconds) * 20;
    }

    public static class DeathReasons {
        public static ResourceLocation DISCONNECT = DeathReasonRegistry.gameConstant(SRE.id("disconnected"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation BLACK_WHITE_TIMEOUT = DeathReasonRegistry.gameConstant(SRE.id("black_white"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation AMON_USURP = DeathReasonRegistry.gameConstant(SRE.id("amon_usurp"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation BACKFIRE = DeathReasonRegistry.gameConstant(SRE.id("backfire"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation EXECUTE = DeathReasonRegistry.gameConstant(SRE.id("execute"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation GENERIC = DeathReasonRegistry.gameConstant(SRE.id("generic"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation KNIFE = DeathReasonRegistry.gameConstant(SRE.id("knife_stab"), DeathReasonRegistry.KillUsage.NORMAL, DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation REVOLVER = DeathReasonRegistry.gameConstant(SRE.id("revolver_shot"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation DERRINGER = DeathReasonRegistry.gameConstant(SRE.id("derringer_shot"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation BAT = DeathReasonRegistry.gameConstant(SRE.id("bat_hit"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation GRENADE = DeathReasonRegistry.gameConstant(SRE.id("grenade"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation POISON = DeathReasonRegistry.gameConstant(SRE.id("poison"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation SELF_EXPLOSION = DeathReasonRegistry.gameConstant(SRE.id("self_explosion"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation FELL_OUT_OF_TRAIN = DeathReasonRegistry.gameConstant(SRE.id("fell_out_of_train"), DeathReasonRegistry.KillUsage.NORMAL, DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation ARROW = DeathReasonRegistry.gameConstant(SRE.id("arrow"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation TRIDENT = DeathReasonRegistry.gameConstant(SRE.id("trident"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation SNIPER_RIFLE = DeathReasonRegistry.gameConstant(SRE.id("sniper_rifle"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation SNIPER_RIFLE_BACKFIRE = DeathReasonRegistry.gameConstant(SRE.id("sniper_rifle_backfire"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation NUNCHUCK = DeathReasonRegistry.gameConstant(SRE.id("nunchuck_hit"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation ZERO_ONE_FIVE = DeathReasonRegistry.gameConstant(SRE.id("zero_one_five_shot"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation SELF_LOST = DeathReasonRegistry.gameConstant(SRE.id("self_lost"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation MANHOLE_SUFFOCATION = DeathReasonRegistry.gameConstant(SRE.id("manhole_suffocation"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation STALACTITE_IMPALE = DeathReasonRegistry.gameConstant(SRE.id("stalactite_impale"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation FLAMETHROWER_BURNED = DeathReasonRegistry.gameConstant(SRE.id("flamethrower_burned"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation BOULDER_CRUSH = DeathReasonRegistry.gameConstant(SRE.id("boulder_crush"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation INCINERATOR_PUSHED = DeathReasonRegistry.gameConstant(SRE.id("incinerator_pushed"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation ANCIENT_BITE = DeathReasonRegistry.gameConstant(SRE.id("ancient_bite"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation DROWNED = DeathReasonRegistry.gameConstant(SRE.id("drowned"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation FROZEN = DeathReasonRegistry.gameConstant(SRE.id("frozen"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation THIRST = DeathReasonRegistry.gameConstant(SRE.id("thirst"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation STARVED = DeathReasonRegistry.gameConstant(SRE.id("starved"), DeathReasonRegistry.KillUsage.FORCE);
        public static ResourceLocation GOD_COMMAND = DeathReasonRegistry.gameConstant(Noellesroles.id("god_command"), DeathReasonRegistry.KillUsage.NORMAL);
        public static ResourceLocation GENERAL_ATTACK = DeathReasonRegistry.gameConstant(SRE.id("general_attack"), DeathReasonRegistry.KillUsage.NORMAL);
    }

    public static int getFurandoruSafeLine() {
        return SREConfig.instance().furandoruSafeTime * 20;
    }

    public static int getMonitorBrokenCooldownGlobal() {
        return SREConfig.instance().monitorBrokenCooldownGlobal * 20;
    }

    public static int getRevolverDefaultTicks() {
        return SREConfig.instance().revolverCooldown * 20;
    }
}
