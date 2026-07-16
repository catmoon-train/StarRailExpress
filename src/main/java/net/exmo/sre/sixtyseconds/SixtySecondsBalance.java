package net.exmo.sre.sixtyseconds;

/**
 * 末日60秒模式的数值平衡集中表——所有可调数值放这里，便于统一调参。
 */
public final class SixtySecondsBalance {
    private SixtySecondsBalance() {
    }

    // ── 每分钟状态消耗（在家基准；户外 ×{@link #OUTDOOR_DRAIN_MULT}）───────
    /** 在家：~8×0.8=6.4/分钟 × 9.5分钟 ≈ 61/天；户外(×1.2)：~7.7/分钟 ≈ 73/天 */
    public static final int HUNGER_DRAIN_PER_MIN = 8;
    /** 在家：~10×0.8=8/分钟 × 9.5分钟 ≈ 76/天；户外(×1.2)：~9.6/分钟 ≈ 91/天 */
    public static final int THIRST_DRAIN_PER_MIN = 10;
    public static final int SANITY_DRAIN_PER_MIN = 1;   // san 缓慢下降
    public static final int POLLUTION_GAIN_PER_MIN = 1; // 污染缓慢累积
    /** 在家消耗倍率（基准=1.0，即上述常量即在家消耗） */
    public static final double HOME_DRAIN_MULT = 1.0;
    /** 户外消耗倍率（在家 ×1.2；原×1.5，-20%） */
    public static final double OUTDOOR_DRAIN_MULT = 1.2;
    /** 户外环境污染增速额外倍率（-60%；小数部分按概率进位结算）。 */
    public static final double POLLUTION_OUTDOOR_MULT = 0.4;
    /** 全局消耗倍率（所有状态降低 -20%） */
    public static final double DRAIN_MULT_GLOBAL = 0.8;
    /** 污染增速全局倍率（-40%，叠加在全局/位置倍率上） */
    public static final double POLLUTION_DRAIN_MULT = 0.6;
    /** 前两天的消耗倍率（-50%，叠加在全局/位置倍率上） */
    public static final double DRAIN_MULT_EARLY_DAYS = 0.5;
    /** 第三天起的消耗倍率（-20%，叠加在全局/位置倍率上） */
    public static final double DRAIN_MULT_LATE_DAYS = 0.8;

    // ── 健康保护（避免多状态叠加导致掉血过快）─────────────────────────────
    /** 饥饿或口渴清空时，每秒最多扣的健康（<b>单一来源、封顶、不叠加</b>）。100 血≈100s 才死，留足反制窗口。 */
    public static final int HEALTH_LOSS_PER_SEC = 1;
    /**
     * 环境/自然伤害（火、岩浆、窒息、溺水、冰冻、坠落等）的无敌帧（tick）。ALLOW_DAMAGE 取消原版伤害后，
     * 原版无敌帧 {@code invulnerableTime} 不会被设置，逐 tick 触发的环境伤害会全额连扣（卡墙/掉火里瞬间秒杀）；
     * 这里自建无敌帧，同一玩家的环境伤害在此窗口内只结算一次（更强的一击补差额），与原版 10 tick(0.5s) 一致。
     */
    public static final int ENV_INVULN_TICKS = 10;

    // ── 高污染的负面（缓慢侵蚀健康，不立即死亡）─────────────────────────
    public static final int POLLUTION_SICK_ROLL_INTERVAL = 20 * 120; // 满污染每 2 分钟一次生病判定
    public static final double POLLUTION_SICK_CHANCE = 0.15;   // 从 33% 降至 15%（-50%+）
    public static final int POLLUTION_HEALTH_THRESHOLD = 70;   // 污染 ≥70 开始侵蚀健康
    public static final int POLLUTION_HEALTH_LOSS_HIGH = 3;    // 污染 70~99：每分钟基准 -3 健康（×下方倍率）
    public static final int POLLUTION_HEALTH_LOSS_FULL = 5;    // 污染满(100)：每分钟基准 -5 健康（×下方倍率）
    /** 污染侵蚀健康的整体倍率（-60%；小数部分按概率进位，实际 ≈1.2/2 每分钟）。 */
    public static final double POLLUTION_HEALTH_LOSS_MULT = 0.4;

    // ── 事件系统 ──────────────────────────────────────────────────────
    public static final int EVENT_CHECK_INTERVAL = 20 * 60 * 3; // 每 3 分钟尝试触发一次事件
    public static final double EVENT_CHANCE = 0.3;              // 触发概率（原 0.6，-50%）
    public static final int POLLUTION_RAIN_DURATION = 20 * 60 * 2; // 污雨持续 2 分钟
    /** 污雨中·户外·无伞：每 10 秒额外污染（7 → 3，户外污染增速再 -60%）。 */
    public static final int POLLUTION_RAIN_GAIN_PER_10S = 3;

    // ── san 归零变怪物 ────────────────────────────────────────────────
    public static final int MONSTER_DELAY_TICKS = 20 * 30;    // san 归零后 30s 变怪
    public static final int SAN_LOSS_ON_DEATH = 15;           // 目睹死亡损失的 san
    public static final double DEATH_SAN_RANGE_SQR = 24 * 24; // 目睹死亡的范围（平方）

    // ── 杀人代价：理智上限永久扣减（SixtySecondsHealthSystem.die）───────────
    public static final double PVP_DAMAGE_MULT = 0.8; // 玩家对玩家伤害倍率（-20%）
    public static final int KILL_SANITY_CAP_LOSS_MIN = 8;     // 每次杀人扣理智上限下限（×1.5: 5→8）
    public static final int KILL_SANITY_CAP_LOSS_MAX = 14;     // 每次杀人扣理智上限上限（×1.5: 9→14）
    public static final int SANITY_CAP_FLOOR = 10;            // 理智上限最低值（防连环杀直接锁死变怪）

    // ── 倒地系统 ──────────────────────────────────────────────────────
    /** 倒地时的初始健康值（需被打空才会真正死亡，取代一击处决）。30 ≈ 枪械 1 击、近战 2 击。 */
    public static final int DOWNED_MAX_HEALTH = 30;
    /** 倒地后每秒自然流失的健康值（约 30 秒无人补刀/救起则死）。 */
    public static final int DOWNED_BLEED_PER_SEC = 1;

    // ── 日内子相位：时间轴见 {@link SixtySecondsDayCycle}（清晨1/白天6/晚上2.5分钟，末45s睡觉）──

    // ── 睡眠（晚上最后 45 秒睡觉时间）─────────────────────────────────────
    public static final int SLEEP_HEAL_PER_SEC = 3;            // 在家床上睡觉每秒回血
    public static final int NIGHT_NO_SLEEP_LOSS_PER_SEC = 1;   // 睡觉时间不在床/在户外每秒扣血
    public static final double NIGHT_OUTDOOR_SICK_CHANCE = 0.10; // 户外过夜每 10s 生病判定（从 20% 降至 10%）
    /** 户外过夜生病概率受状态影响：概率 = baseChance × (1 − factor × minStat/maxStat)。
     *  minStat=100 时概率仅剩 20%；minStat=0 时为满概率。 */
    public static final double SICK_CHANCE_STAT_FACTOR = 0.8;

    // ── 低语怪 / 黑暗惩罚（SixtySecondsWhisperSystem）──────────────────────
    public static final int WHISPER_LIGHT_THRESHOLD = 6;      // 低于此方块亮度视为「黑暗区块」
    public static final int WHISPER_MAX_PER_TEAM = 2;         // 每队家中同时最多低语怪数量
    public static final int WHISPER_SPAWN_INTERVAL = 20 * 20; // 夜间每 20s 尝试在黑暗处刷一只
    public static final int WHISPER_SAN_DRAIN_PER_SEC = 1;    // 低语怪 4 格内每秒掉 san
    public static final double WHISPER_RANGE_SQR = 4 * 4;
    public static final int DARK_DAWN_SAN_PENALTY = 15;       // 清晨家中仍有黑暗区块 → 全队 san -15
    /** 手电筒右键驱散低语怪的半径（格）：比掉 san 半径(4)大，够清掉一个房间。 */
    public static final double FLASHLIGHT_DISPEL_RADIUS = 8.0;
    /** 每次驱散消耗的手电筒耐久（电量）。手电筒共 150 耐久 → 约可驱散 3 次，耗尽即损坏。 */
    public static final int FLASHLIGHT_DISPEL_DURABILITY = 50;
    /** 驱散冷却（tick），防连点。 */
    public static final int FLASHLIGHT_DISPEL_COOLDOWN = 20 * 3;

    // ── 家门攻防（SixtySecondsDefenseSystem）──────────────────────────────
    public static final int DOOR_BASE_HP = 100;
    public static final int DOOR_REINFORCE_PLANK = 10;        // 木板加固 +10 耐久（可超上限提升上限）
    public static final int DOOR_REINFORCE_IRON = 25;         // 铁锭加固 +25 耐久
    public static final int DOOR_IRON_PER_LEVEL = 3;          // 每 3 次铁锭加固门升 1 级（上限 3 级）
    public static final int ASSAULT_BASE_COUNT = 2;           // 夜袭怪物基础数量（+当前天数）
    public static final int ASSAULT_MOB_DOOR_DPS = 2;         // 每只怪物每秒对门伤害
    public static final double ASSAULT_DOOR_RANGE_SQR = 2.5 * 2.5;
    public static final double ASSAULT_AGGRO_RANGE_SQR = 8 * 8; // 主动索敌半径：8 格内追打玩家（优先于冲门）
    public static final int ASSAULT_SPAWN_MIN_DIST = 12;      // 夜袭怪刷新点离门最近距离（远刷，给防守方反应窗口）
    public static final int ASSAULT_SPAWN_RAND_DIST = 9;      // 刷新距离随机加成 0..8（即离门 12~20 格）
    /** 避难所物理门<b>外侧</b>刷新距离（首选模式：怪从门外压来，屋内可见可防）。 */
    public static final int ASSAULT_DOOR_OUTSIDE_MIN = 5;
    public static final int ASSAULT_DOOR_OUTSIDE_MAX = 12;
    public static final int ASSAULT_FORCE_CHUNK_RADIUS = 2;   // 战场常加载区块半径（需覆盖刷新距离）
    public static final int BARRICADE_HP = 60;                // 木路障耐久
    public static final int BARRICADE_HEAVY_HP = 120;         // 书柜/沙发重型路障耐久
    public static final int BARRICADE_REINFORCED_HP = 220;    // 钢筋强化路障耐久（工事强化科技）
    public static final float SPIKE_TRAP_DAMAGE = 3.0F;       // 尖刺陷阱每秒对怪伤害
    public static final float BARBED_WIRE_DAMAGE = 1.5F;      // 铁丝网每秒对怪伤害（廉价版陷阱，减速为主）
    public static final double DOOR_BROKEN_DRAIN_MULT = 2.0;  // 门被攻破：户外消耗再 ×2（即户外 ×1.5×2=×3.0）
    /** 门锁 / 门陷阱 安装后的有效时长（6 分钟；过期自然失效，可重新安装续期）。 */
    public static final int DOOR_LOCK_DURATION_TICKS = 20 * 360;
    public static final int DOOR_TRAP_DURATION_TICKS = 20 * 360;

    // ── PVE：自研怪物（SixtySecondsMonsterEntity / SixtySecondsPveSystem）──────
    /** 非战场怪身边 64 格无人累计此时长自散（防游荡怪堆积）。 */
    public static final int PVE_LONELY_DESPAWN_TICKS = 20 * 60;
    /** 吐酸者吐酸冷却。 */
    public static final int PVE_SPIT_COOLDOWN_TICKS = 20 * 3;
    /** 酸液命中玩家的健康伤害 / 附加污染。 */
    public static final int PVE_SPIT_INJURY = 10;
    public static final int PVE_SPIT_POLLUTION = 5;

    // ── PVE：探索区游荡怪（每 30s 对探索区玩家做一次刷新判定）──────────────────
    public static final int AMBIENT_CHECK_INTERVAL = 20 * 30;
    /** 每次判定的基础刷新概率（+每区域等级加成；夜间再乘倍率）。 */
    public static final double AMBIENT_SPAWN_CHANCE = 0.30;
    public static final double AMBIENT_SPAWN_CHANCE_PER_AREA_LEVEL = 0.08;
    public static final double AMBIENT_NIGHT_CHANCE_MULT = 1.6;
    /** 玩家 40 格内游荡怪数量上限（+区域等级），达到则不再刷。 */
    public static final int AMBIENT_MAX_NEARBY = 4;
    /** 游荡怪刷新点离玩家 10~17 格（给反应窗口又不至于看不见）。 */
    public static final int AMBIENT_SPAWN_MIN_DIST = 10;
    public static final int AMBIENT_SPAWN_RAND_DIST = 8;
    /** 每区域等级的游荡怪生命加成（Lv5 = ×1.6）。 */
    public static final double AMBIENT_HEALTH_PER_AREA_LEVEL = 0.15;
    /** 感染体（游荡怪/夜袭怪）掉落废料的基础概率（-35%，原 100% 必掉）。 */
    public static final double MONSTER_SCRAP_DROP_CHANCE = 0.65;

    /**
     * 游荡怪刷新概率的天数倍率（前期压低、逐步爬升）：
     * 第1天 25%、第2天 35%、第3天 55%、第4天 75%、第5天 90%、第6~7天 100%。
     */
    public static double ambientSpawnDayMult(int day) {
        if (day <= 1) return 0.25;
        if (day == 2) return 0.35;
        if (day == 3) return 0.55;
        if (day == 4) return 0.75;
        if (day == 5) return 0.90;
        return 1.0;
    }

    /**
     * Boss 刷新概率的天数倍率（仅影响非保底日：第1/2/4/6天）。
     * 第1天 40%、第2天 55%、第4天 80%、第6天 100%（第3/5/7天保底不受影响）。
     */
    public static double bossSpawnDayMult(int day) {
        if (day <= 1) return 0.40;
        if (day == 2) return 0.55;
        if (day == 4) return 0.80;
        return 1.0;
    }

    // ── PVE：区域危险等级（SixtySecondsAreaLevels）────────────────────────────
    public static final int AREA_LEVEL_MAX = 5;
    /** loot 权重压平系数 α：weight^(1/(1+α(level-1)))。0.35 → Lv5 时指数≈0.42，稀有物明显更常见。 */
    public static final double AREA_LEVEL_LOOT_FLATTEN = 0.35;

    // ── PVE：Boss 尸潮领主（SixtySecondsBossEntity）──────────────────────────
    public static final int BOSS_MAX_LEVEL = 5;
    /** 夜晚开始时的 Boss 刷新概率（+每天加成；第 3/5/7 天保底必刷）。 */
    public static final double BOSS_NIGHT_CHANCE = 0.12;
    public static final double BOSS_NIGHT_CHANCE_PER_DAY = 0.03;
    public static final double BOSS_BASE_HEALTH = 300;
    public static final double BOSS_HEALTH_PER_LEVEL = 150;
    /** 单次受击伤害封顶：枪械 1000 伤「怪即死」对 Boss 只按此值生效（狙击=满额 100）。 */
    public static final float BOSS_MAX_SINGLE_HIT = 100.0F;
    public static final int BOSS_MELEE_INJURY = 24;         // 近战健康伤害（+4/级）
    public static final int BOSS_SLAM_INJURY = 18;          // 震地 AoE 健康伤害（+4/级）
    public static final int BOSS_ROAR_SAN_LOSS = 4;         // 咆哮扣 san（+1/级）
    public static final int BOSS_SLAM_COOLDOWN_TICKS = 20 * 10;
    public static final int BOSS_ROAR_COOLDOWN_TICKS = 20 * 18;
    public static final int BOSS_SUMMON_COOLDOWN_TICKS = 20 * 25;
    public static final int BOSS_CHARGE_COOLDOWN_TICKS = 20 * 14;
    /** Boss 掉落：loot 掷骰件数 = BASE + PER_LEVEL×等级；保底废料 = BASE + PER_LEVEL×等级（+40%）。 */
    public static final int BOSS_LOOT_ROLLS_BASE = 13;
    public static final int BOSS_LOOT_ROLLS_PER_LEVEL = 7;
    public static final int BOSS_SCRAP_BASE = 13;
    public static final int BOSS_SCRAP_PER_LEVEL = 7;
    // ── Boss 变体权重（生成时随机选取；总值建议=1.0，剩余概率为 RAVAGER 破坏者）──
    /** 巨像权重 */
    public static final double BOSS_VARIANT_COLOSSUS_WEIGHT = 0.15;
    /** 亡灵术士权重 */
    public static final double BOSS_VARIANT_NECROMANCER_WEIGHT = 0.12;
    /** 疫病者权重 */
    public static final double BOSS_VARIANT_PLAGUEBEARER_WEIGHT = 0.12;
    /** 鬼魅权重 */
    public static final double BOSS_VARIANT_SPECTER_WEIGHT = 0.10;
    /** 变体权重随天数递增倍率（第 N 天权重 × (1+递增×天数)）；0 代表不变。 */
    public static final double BOSS_VARIANT_DAY_BONUS = 0.04;
    // ── 新技能数值 ────────────────────────────────────────────────────
    /** 铁壁冷却（巨像） */
    public static final int BOSS_IRON_SKIN_COOLDOWN_TICKS = 20 * 35;
    /** 生命汲取冷却（亡灵术士） */
    public static final int BOSS_DRAIN_COOLDOWN_TICKS = 20 * 16;
    /** 骨矛冷却（亡灵术士） */
    public static final int BOSS_SPEAR_COOLDOWN_TICKS = 20 * 5;
    /** 毒息冷却（疫病者） */
    public static final int BOSS_BREATH_COOLDOWN_TICKS = 20 * 12;
    /** 暗影突袭冷却（鬼魅） */
    public static final int BOSS_SHADOW_COOLDOWN_TICKS = 20 * 10;
    /** 剧毒新星冷却（疫病者终焉） */
    public static final int BOSS_NOVA_COOLDOWN_TICKS = 20 * 22;
    /** 狂怒触发血量阈值（<最大生命百分比） */
    public static final double FRENZY_HP_THRESHOLD = 0.35;

    // ── PVE：哨戒炮 / 陷阱对玩家（SixtySecondsPveSystem）──────────────────────
    public static final double TURRET_RANGE = 12.0;
    public static final int TURRET_COOLDOWN_TICKS = 30;      // 1.5s/发
    public static final float TURRET_MOB_DAMAGE = 8.0F;      // 对怪原版伤害/发
    public static final int TURRET_PLAYER_INJURY = 7;        // 对敌队玩家健康伤害/发
    /** 陷阱对敌队玩家的健康伤害 = 注册伤害 × 此倍率（尖刺 3.0→8、铁丝网 1.5→4）。 */
    public static final float TRAP_PLAYER_INJURY_MULT = 2.5F;

    // ── 电力（SixtySecondsPowerSystem）───────────────────────────────────
    public static final int POWER_PER_FUEL_TICKS = 20 * 10;   // 每份燃料基础 10 秒；发电机内部按倍率换算

    // ── 扩展事件（SixtySecondsEventSystem）────────────────────────────────
    public static final int SMOG_DURATION = 20 * 60 * 2;      // 浓烟持续 2 分钟
    public static final int SMOG_POLLUTION_PER_2S = 1;        // 浓烟中·不在家：每 2 秒额外污染 1（原每秒 1，-50%；伞无效）
    public static final int COLD_SNAP_DURATION = 20 * 60 * 2; // 寒潮持续 2 分钟
    public static final int COLD_HUNGER_PER_10S = 1;          // 寒潮中·不在家：每 10s 额外饥饿
    public static final int AIRDROP_ROLLS = 9;                // 空投奖励箱一次性搜出的物资件数
    /** 新天气事件的通用持续时间（酸雾/电磁风暴/虫潮/热浪 = 1.5分钟） */
    public static final int EVENT_BASE_DURATION = 20 * 60 * 3 / 2;

    // ── 洗澡器（SixtySecondsShowerBlock：每人每天一次，消耗小瓶水洗去污染）───
    public static final double SHOWER_POLLUTION_MULT = 0.5;  // 洗澡后污染 ×0.5（-50%）

    // ── 培育箱（SixtySecondsPlanterBlock：种子→蔬菜的耕地系统）──────────────
    /** 每生长阶段 3 分 5 秒（-35%，原 2 分钟；共 2 段 ≈ 6 分 10 秒成熟）。 */
    public static final int PLANTER_GROW_STAGE_TICKS = 20 * 185;
    public static final int PLANTER_HARVEST_MIN = 1;             // 收获蔬菜下限（-40%: 2→1）
    public static final int PLANTER_HARVEST_MAX = 2;             // 收获蔬菜上限（-40%: 3→2）
    public static final double PLANTER_SEED_RETURN_CHANCE = 0.4; // 收获时返还 1 包种子的概率

    // ── 集水器（SixtySecondsWaterCollectorBlock：被动产污染水，右键收取）─────
    public static final int COLLECTOR_BASIC_INTERVAL = 20 * 170; // 雨水桶：170s/瓶（+35%: 260→170）
    public static final int COLLECTOR_BASIC_CAPACITY = 2;
    public static final int COLLECTOR_ROOF_INTERVAL = 20 * 110;  // 雨棚集水器：110s/瓶（+35%: 170→110）
    public static final int COLLECTOR_ROOF_CAPACITY = 4;
    public static final int COLLECTOR_CONDENSER_INTERVAL = 20 * 75; // 冷凝集水器：75s/瓶（+35%: 115→75）
    public static final int COLLECTOR_CONDENSER_CAPACITY = 6;

    // ── 娱乐物品（SixtySecondsEntertainmentItem：AoE 恢复理智）─────────────
    public static final double ENTERTAINMENT_RADIUS = 8.0;          // 作用半径（格）
    public static final int ENTERTAINMENT_COOLDOWN_TICKS = 20 * 45; // 使用冷却 45 秒

    // ── 每日事件门（SixtySecondsDailyEvents）──────────────────────────────
    public static final int DAILY_EVENT_DELAY_TICKS = 20 * 15;      // 开日 15s 后触发当日事件
    public static final int DAILY_EVENT_CHOICE_TICKS = 20 * 90;     // 抉择 90s 未决按保守选项处理
    public static final int DAILY_EVENT_EXPEDITION_TICKS = 20 * 45; // 探险出发到归来 45s
    /** 事件产出物资数量倍率（不可堆叠物品不加量；小数部分按概率进位）。 */
    public static final double DAILY_EVENT_LOOT_MULT = 1.5;
    /** 事件产出中废料（sixty_seconds_scrap）的数量倍率（覆盖上面的通用倍率）。 */
    public static final double DAILY_EVENT_SCRAP_MULT = 2.0;

    // ── 隐藏通关 · 救援信标（SixtySecondsRescue）────────────────────────────
    public static final int RESCUE_COUNTDOWN_TICKS = 20 * 120; // 信标激活后 2 分钟救援抵达

    // ── 绳索（SixtySecondsRopeItem：原地向上放临时可攀爬绳索）───────────────
    public static final int ROPE_HEIGHT = 16;            // 绳索最大向上延伸（遇非空气截断）
    public static final int ROPE_DURATION_TICKS = 20 * 30; // 30 秒后消失

    // ── 钩锁（SixtySecondsGrapplingHookItem：钩住准星落点把自己荡过去）────────
    public static final int GRAPPLE_RANGE = 24;               // 最大射程（格）
    public static final int GRAPPLE_COOLDOWN_TICKS = 20 * 15; // 使用冷却 15 秒
    public static final int GRAPPLE_DURABILITY = 20;          // 耐久 20 次
    public static final int GRAPPLE_NO_FALL_TICKS = 20 * 20;  // 荡索摔落保护窗口上限（落地即提前结束）

    // ── 开局保底物资（准备阶段结束随搜刮所得一起装进避难所补给箱；见 SixtySecondsManager.placeSupplyChests）──
    /** 人均份：小瓶水（消耗品不可堆叠，逐件入箱）。 */
    public static final int STARTER_WATER_PER_MEMBER = 2;
    /** 人均份：罐头食品。 */
    public static final int STARTER_FOOD_PER_MEMBER = 1;
    /** 人均份：绷带。 */
    public static final int STARTER_BANDAGE_PER_MEMBER = 1;
    /** 每队固定份：废料（第一晚照明/解锁基础科技的底子）。 */
    public static final int STARTER_SCRAP_PER_TEAM = 6;
    /** 每队固定份：破布。 */
    public static final int STARTER_RAG_PER_TEAM = 4;
    /** 每队固定份：火把。 */
    public static final int STARTER_TORCH_PER_TEAM = 2;
    /** 每队固定份：污染水（配合净化链）。 */
    public static final int STARTER_DIRTY_WATER_PER_TEAM = 2;

    // ── 拆解台（SixtySecondsDismantle：把可合成物品拆回基础资源）──────────────
    /** 拆解返还率：按配方展开成基础资源后 ×0.4（即 -60%），向下取整；全为 0 时保底返还占比最高的 1 件。 */
    public static final double DISMANTLE_RETURN_RATE = 0.4;

    // ── 物资箱搜刮（搜打撤式定时搜刮进度条；见 SixtySecondsLootSearch）─────────
    public static final int SUPPLY_SEARCH_TICKS = 20 * 3;        // 搜刮时长 3 秒
    public static final double SUPPLY_SEARCH_MAX_DIST_SQR = 3 * 3; // 离箱超过 3 格中断搜刮

    // ── 枪械（SixtySecondsGunItem：需子弹、攻击冷却、降噪枪声）──────────────
    /** 命中玩家的健康伤害（受护甲减免；倒地者=处决，怪物=立即死亡）。 */
    public static final int GUN_PLAYER_DAMAGE = 50;
    /** 降噪枪声音量（原版枪声硬编码 5f 过响）。 */
    public static final float GUN_SOUND_VOLUME = 0.9F;
    /** 每把枪的冷却（tick）与射程（格）。 */
    public static final int GUN_PISTOL_COOLDOWN = 20;           // 手枪 1s
    public static final double GUN_PISTOL_RANGE = 24.0;
    public static final int GUN_SHOTGUN_COOLDOWN = 20 * 2;      // 猎枪 2s（模板）
    public static final double GUN_SHOTGUN_RANGE = 30.0;
    public static final int GUN_RIFLE_COOLDOWN = 20 * 3;        // 步枪 3s
    public static final double GUN_RIFLE_RANGE = 48.0;
    public static final int GUN_SNIPER_COOLDOWN = 20 * 20;      // 狙击枪 20s（全枪械共享）
    public static final double GUN_SNIPER_RANGE = 80.0;
    public static final int GUN_SNIPER_DAMAGE = 100;            // 狙击枪一枪打空健康（直接倒地）
    public static final int GUN_RPG_COOLDOWN = 20 * 8;          // RPG 8s
    public static final double GUN_RPG_RANGE = 48.0;
    public static final int GUN_RPG_AMMO_COST = 5;              // RPG 每发消耗 5 发子弹
    public static final int GUN_RPG_DAMAGE = 80;                // RPG 玩家伤害 80（高于普通枪械的 50）
    public static final double GUN_RPG_BLAST_RADIUS = 4.0;      // 爆炸半径（波及自己，小心近射）
    public static final double GUN_RPG_ROCKET_SPEED = 1.6;      // 火箭飞行速度（格/tick）

    // ── 觉醒职业的本模式数值修正（末日生存平衡；见 SixtySecondsRoleTweaks）───────
    public static final double JADE_GENERAL_COOLDOWN_MULT = 2.0; // 玉将军 技能冷却×2（35s→70s）
    public static final double FIGHTER_COOLDOWN_MULT = 3.0;      // 斗士 技能冷却×3（120s→360s）
    public static final int BROADCASTER_BROADCAST_COST = 100;    // 广播员 每次广播消耗（原版 50）
    public static final int REPORTER_NOTE_COUNT = 8;             // 记者 觉醒补发便签数（原版 4，×2）
}
