package io.wifi.starrailexpress.game;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
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
    // 小游戏任务轮换模式下全局任务刷新冷却的减缓系数（+15% 冷却时长）
    public static float MINIGAME_ROTATION_REFRESH_SLOWDOWN = 1.15f;
    // 小游戏任务轮换模式：刷新多少个普通任务后轮换到小游戏任务（随机下限/上限）
    public static int MINIGAME_ROTATION_MIN_NORMAL_TASKS = 2;
    public static int MINIGAME_ROTATION_MAX_NORMAL_TASKS = 3;
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
        public static ResourceLocation DANMUKU = SRE.id("danmuku");
        public static ResourceLocation FALL_DAMAGE = SRE.id("fall_damage");
        public static ResourceLocation DISCONNECT = SRE.id("disconnected");
        public static ResourceLocation DEATH_AFK = SRE.id("death_afk");
        public static ResourceLocation BLACK_WHITE_TIMEOUT = SRE.id("black_white");
        public static ResourceLocation AMON_USURP = SRE.id("amon_usurp");
        public static ResourceLocation BACKFIRE = SRE.id("backfire");
        public static ResourceLocation EXECUTE = SRE.id("execute");
        public static ResourceLocation GENERIC = SRE.id("generic");
        public static ResourceLocation GUN_SHOT = SRE.id("gun_shot");
        public static ResourceLocation KNIFE = SRE.id("knife_stab");
        public static ResourceLocation REVOLVER = SRE.id("revolver_shot");
        public static ResourceLocation DERRINGER = SRE.id("derringer_shot");
        public static ResourceLocation BAT = SRE.id("bat_hit");
        public static ResourceLocation GRENADE = SRE.id("grenade");
        public static ResourceLocation POISON = SRE.id("poison");
        public static ResourceLocation SELF_EXPLOSION = SRE.id("self_explosion");
        public static ResourceLocation FELL_OUT_OF_TRAIN = SRE.id("fell_out_of_train");
        public static ResourceLocation TOUCH_INCORRECT = SRE.id("touch_incorrect");
        public static ResourceLocation DEATH_IN_DARKNESS = SRE.id("death_in_darkness");
        public static ResourceLocation CANNOT_SWIM = SRE.id("cant_swim_drowned");
        public static ResourceLocation LAVA = SRE.id("swim_in_lava");
        public static ResourceLocation ARROW = SRE.id("arrow");
        public static ResourceLocation TRIDENT = SRE.id("trident");
        public static ResourceLocation SNIPER_RIFLE = SRE.id("sniper_rifle");
        public static ResourceLocation SNIPER_RIFLE_BACKFIRE = SRE.id("sniper_rifle_backfire");
        public static ResourceLocation NUNCHUCK = SRE.id("nunchuck_hit");
        public static ResourceLocation ZERO_ONE_FIVE = SRE.id("zero_one_five_shot");
        public static ResourceLocation SELF_LOST = SRE.id("self_lost");
        public static ResourceLocation MANHOLE_SUFFOCATION = SRE.id("manhole_suffocation");
        public static ResourceLocation STALACTITE_IMPALE = SRE.id("stalactite_impale");
        public static ResourceLocation FLAMETHROWER_BURNED = SRE.id("flamethrower_burned");
        public static ResourceLocation BOULDER_CRUSH = SRE.id("boulder_crush");
        public static ResourceLocation LOG_CRUSH = SRE.id("log_crush");
        public static ResourceLocation INCINERATOR_PUSHED = SRE.id("incinerator_pushed");
        public static ResourceLocation ANCIENT_BITE = SRE.id("ancient_bite");
        public static ResourceLocation DROWNED = SRE.id("drowned");
        public static ResourceLocation FROZEN = SRE.id("frozen");
        public static ResourceLocation THIRST = SRE.id("thirst");
        public static ResourceLocation STARVED = SRE.id("starved");
        public static ResourceLocation GOD_COMMAND = Noellesroles.id("god_command");
        public static ResourceLocation GENERAL_ATTACK = SRE.id("general_attack");
        public static ResourceLocation HOT_POTATO = SRE.id("hot_potato");
        public static ResourceLocation CAT_KILLER = SRE.wifiId("cat_killer");

        public static ResourceLocation VOODOO = Noellesroles.id("voodoo");
        public static ResourceLocation SHOT_INNOCENT = Noellesroles.id("shot_innocent");
        public static ResourceLocation INSANE_KILLER_DEATH = Noellesroles.id("insane_killer_death");
        public static ResourceLocation NOELLES_ARROW = Noellesroles.id("arrow");
        public static ResourceLocation HEART_ATTACK = Noellesroles.id("heart_attack");
        public static ResourceLocation CONSPIRACY_BACKFIRE = Noellesroles.id("conspiracy_backfire");
        public static ResourceLocation STALKER_EXECUTION = Noellesroles.id("stalker_execution");
        public static ResourceLocation BOMB_DEATH = Noellesroles.id("bomb_death");
        public static ResourceLocation PUPPETEER_PUPPET = Noellesroles.id("puppeteer_puppet");
        public static ResourceLocation PUPPETEER_KNIFE = Noellesroles.id("knife_puppeteer");
        public static ResourceLocation PUPPETEER_GUN = Noellesroles.id("gun_puppeteer");
        public static ResourceLocation RAVEN_BODY_DEATH = Noellesroles.id("raven_body_death");
        public static ResourceLocation RECORDER_MISTAKE = Noellesroles.id("recorder_mistake");
        public static ResourceLocation GAMBLE_SELF_KILL = Noellesroles.id("gamble_self_kill");
        public static ResourceLocation WAYFARER_ERROR = Noellesroles.id("wayfarer_error");
        public static ResourceLocation NIANSHOU_FIRECRACKERS = Noellesroles.id("nianshou_firecrackers");
        public static ResourceLocation BATON_KILL = Noellesroles.id("baton_kill");
        public static ResourceLocation BOWEN = Noellesroles.id("bowen");
        public static ResourceLocation C4_EXPLOSION = Noellesroles.id("c4_explosion");
        public static ResourceLocation FIRE_AXE = Noellesroles.id("fire_axe");
        public static ResourceLocation NINJA_KNIFE_KILL = Noellesroles.id("ninja_knife_kill");
        public static ResourceLocation NINJA_SHURIKEN_KILL = Noellesroles.id("ninja_shuriken_kill");
        public static ResourceLocation SHORT_SHOTGUN = Noellesroles.id("short_shotgun");
        public static ResourceLocation THROWING_KNIFE_HIT = Noellesroles.id("throwing_knife_hit");
        public static ResourceLocation YINYANG_SWORD_AOE = Noellesroles.id("yinyang_sword_aoe");
        public static ResourceLocation FAIL_EXAM = Noellesroles.id("fail_exam");
        public static ResourceLocation BAKA = Noellesroles.id("baka");
        public static ResourceLocation WATCHER_CALM_KILL = Noellesroles.id("watcher_calm_kill");
        public static ResourceLocation REPAIR_TRIAL_EXECUTION = Noellesroles.id("repair_trial_execution");
        public static ResourceLocation INFECTION = Noellesroles.id("infection");
        public static ResourceLocation UNDEAD_INFECTION = Noellesroles.id("undead_infection");
        public static ResourceLocation GROSELL_TRAVELOG = Noellesroles.id("grosell_travelog");
        public static ResourceLocation WIZARD_FIREBALL = Noellesroles.id("wizard_fireball");
        public static ResourceLocation WIZARD_FIRE_ARROW = Noellesroles.id("wizard_fire_arrow");
        public static ResourceLocation DRY_DEATH = Noellesroles.id("dry_death");
        public static ResourceLocation MACHENXU = Noellesroles.id("machenxu");
        public static ResourceLocation HOAN_MEIRIN_LONELY = Noellesroles.id("hoan_meirin_lonely");
        public static ResourceLocation HOAN_MEIRIN_ATTACK = Noellesroles.id("hoan_meirin_attack");
        public static ResourceLocation DIO_FINAL_CARNIVAL_CANCEL = Noellesroles.id("dio_final_carnival_cancel");
        public static ResourceLocation WRAITH_ASSAULT = Noellesroles.id("wraith_assault");
        public static ResourceLocation MORTICIAN_BODYMAKER = Noellesroles.id("mortician_bodymaker");
        public static ResourceLocation VOTED_OUT = Noellesroles.id("voted_out");
        public static ResourceLocation PELICAN_EATEN = Noellesroles.id("pelican_eaten");

        public static ResourceLocation BROKEN_HEART = StupidExpress.id("broken_heart");
        public static ResourceLocation FAILED_INITIATION = StupidExpress.id("failed_initiation");
        public static ResourceLocation ALLERGIST = StupidExpress.id("allergist");
        public static ResourceLocation FAILED_IGNITE = StupidExpress.id("failed_ignite");
        public static ResourceLocation IGNITED = StupidExpress.id("ignited");
        public static ResourceLocation LOOSE_END = StupidExpress.id("loose_end");
        public static ResourceLocation SPLIT_PERSONALITY = StupidExpress.id("split_personality");

        private static ResourceLocation itemId(Item item) {
            return BuiltInRegistries.ITEM.getKey(item);
        }

        public static Set<ResourceLocation> getConstantDeathReasons() {
            Set<ResourceLocation> set = new LinkedHashSet<>();
            Field[] fields = DeathReasons.class.getDeclaredFields();
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers()) && field.getType() == ResourceLocation.class) {
                    try {
                        ResourceLocation value = (ResourceLocation) field.get(null);
                        if (value != null) {
                            set.add(value);
                        }
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            return set;
        }

        public static Set<ResourceLocation> getItemDeathReasons() {
            Set<ResourceLocation> set = new LinkedHashSet<>();
            set.add(itemId(ModItems.THROWING_KNIFE));
            set.add(itemId(ModItems.NINJA_SHURIKEN));
            set.add(itemId(ModItems.SCARLET_PERCEPTION_SWORD));
            set.add(GUN_SHOT);
            set.remove(null);
            return set;
        }

        public static Set<ResourceLocation> getAllDeathReasons() {
            Set<ResourceLocation> set = getConstantDeathReasons();
            set.addAll(getItemDeathReasons());
            return set;
        }

        public static List<String> getAllDeathReasonIds() {
            List<String> ids = new ArrayList<>();
            getAllDeathReasons().stream()
                    .map(ResourceLocation::toString)
                    .distinct()
                    .sorted(Comparator.naturalOrder())
                    .forEach(ids::add);
            return List.copyOf(ids);
        }

        public static ResourceLocation parseOrDefault(String value) {
            if (value == null || value.isBlank() || value.equals("*")) {
                return GENERIC;
            }

            ResourceLocation parsed = ResourceLocation.tryParse(value);
            if (parsed != null && value.contains(":")) {
                return parsed;
            }

            String path = parsed != null ? parsed.getPath() : value;
            for (ResourceLocation deathReason : getAllDeathReasons()) {
                if (deathReason.getPath().equals(path) || deathReason.toString().equals(value)) {
                    return deathReason;
                }
            }
            return parsed != null ? parsed : GENERIC;
        }
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

    public static int getBloodTrackWetDistance() {
        return SREConfig.instance().bloodTrackWetDistance * 20;
    }
}
