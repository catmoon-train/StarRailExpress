package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

public interface GameConstants {
    // Logistics
    int FADE_TIME = 40;
    int FADE_PAUSE = 20;
    int MIN_PLAYER_COUNT = 6;
    Function<Long, Integer> PASSIVE_MONEY_TICKER = time -> {
        if (time % getInTicks(0, 10) == 0) {
            return 5;
        }
        return 0;
    };

    // Role Configuration (Server-side, mutable via command)
    class RoleConfig {
        public static int killerCount = 1;
        public static int vigilanteCount = 1;
    }

    // Blocks
    int DOOR_AUTOCLOSE_TIME = getInTicks(0, 5);

    // Items
    Map<Item, Integer> ITEM_COOLDOWNS = new HashMap<>();

    /**
     * 初始化游戏常量
     * 在mod初始化时调用
     */
    static void init() {
        reloadItemCooldowns();
    }

    /**
     * 重新加载物品冷却时间
     * 可以在运行时调用以应用配置更改
     */
    static void reloadItemCooldowns() {
        ITEM_COOLDOWNS.clear();
        ITEM_COOLDOWNS.put(TMMItems.KNIFE, SREConfig.knifeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.REVOLVER, SREConfig.revolverCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.DERRINGER, SREConfig.derringerCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.GRENADE, SREConfig.grenadeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.LOCKPICK, SREConfig.lockpickCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.CROWBAR, SREConfig.crowbarCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.BODY_BAG, SREConfig.bodyBagCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.PSYCHO_MODE, SREConfig.psychoModeCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.BLACKOUT, SREConfig.blackoutCooldown * 20);
        ITEM_COOLDOWNS.put(TMMItems.NUNCHUCK, 160); // 8秒冷却
        ITEM_COOLDOWNS.put(TMMItems.SNIPER_RIFLE, 80); // 4秒冷却

        SRE.LOGGER.debug("物品冷却时间已重载: 小刀={}秒, 左轮={}秒",
                SREConfig.knifeCooldown, SREConfig.revolverCooldown);
    }

    int JAMMED_DOOR_TIME = getInTicks(1, 0);

    // Corpses
    int TIME_TO_DECOMPOSITION = getInTicks(1, 0);
    int DECOMPOSING_TIME = getInTicks(4, 0);

    // Task Variables
    float MOOD_GAIN = 0.5f;
    float MOOD_DRAIN = 1f / getInTicks(4, 0);
    int TIME_TO_FIRST_TASK = getInTicks(0, 30);
    int MIN_TASK_COOLDOWN = getInTicks(0, 30);
    int MAX_TASK_COOLDOWN = getInTicks(1, 0);
    int SLEEP_TASK_DURATION = getInTicks(0, 8);
    int OUTSIDE_TASK_DURATION = getInTicks(0, 8);
    int READ_BOOK_TASK_DURATION = getInTicks(0, 8);
    int EXERCISE_TASK_DURATION = getInTicks(0, 6);
    int MEDITATE_TASK_DURATION = getInTicks(0, 10); // 冥想
    int NOTE_BLOCK_TASK_CLICK_COUNTS = 10; // 音符盒点击次数
    int TOILET_TASK_DURATION = getInTicks(0, 6);
    int CHAIR_TASK_DURATION = getInTicks(0, 8);
    int BATHE_TASK_DURATION = getInTicks(0, 10); // 洗澡任务持续时间
    float MID_MOOD_THRESHOLD = 0.55f;
    float DEPRESSIVE_MOOD_THRESHOLD = 0.2f;
    float ANGRY_MOOD_THRESHOLD = 0.75f;
    float ITEM_PSYCHOSIS_CHANCE = .5f; // in percent
    int ITEM_PSYCHOSIS_REROLL_TIME = 200;

    // Shop Variables

    static int getMoneyStart() {
        return SREConfig.startingMoney;
    }

    static Function<Long, Integer> getPassiveMoneyTicker() {
        return time -> {
            if (time % (SREConfig.passiveMoneyInterval * 20) == 0) {
                return SREConfig.passiveMoneyAmount;
            }
            return 0;
        };
    }

    static int getMoneyPerKill() {
        return SREConfig.moneyPerKill;
    }

    static int getPsychoModeArmour() {
        return SREConfig.psychoModeArmor;
    }

    // Timers
    static int getPsychoTimer() {
        return SREConfig.psychoModeDuration * 20;
    }

    static int getFirecrackerTimer() {
        return SREConfig.firecrackerDuration * 20;
    }

    static int getBlackoutMinDuration() {
        return SREConfig.blackoutMinDuration * 20;
    }

    static int getBlackoutMaxDuration() {
        return SREConfig.blackoutMaxDuration * 20;
    }

    int TIME_ON_CIVILIAN_KILL = getInTicks(0, 30);

    static int getInTicks(int minutes, int seconds) {
        return (minutes * 60 + seconds) * 20;
    }

    interface DeathReasons {
        ResourceLocation GENERIC = SRE.id("generic");
        ResourceLocation KNIFE = SRE.id("knife_stab");
        ResourceLocation REVOLVER = SRE.id("revolver_shot");
        ResourceLocation DERRINGER = SRE.id("derringer_shot");
        ResourceLocation BAT = SRE.id("bat_hit");
        ResourceLocation GRENADE = SRE.id("grenade");
        ResourceLocation POISON = SRE.id("poison");
        ResourceLocation FELL_OUT_OF_TRAIN = SRE.id("fell_out_of_train");
        ResourceLocation ARROW = SRE.id("arrow");
        ResourceLocation SNIPER_RIFLE = SRE.id("sniper_rifle");
        ResourceLocation SNIPER_RIFLE_BACKFIRE = SRE.id("sniper_rifle_backfire");
        ResourceLocation NUNCHUCK = SRE.id("nunchuck_hit");
    }
}