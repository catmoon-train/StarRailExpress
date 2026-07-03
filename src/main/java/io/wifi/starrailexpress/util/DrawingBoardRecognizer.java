package io.wifi.starrailexpress.util;

import io.wifi.utils.ai.SimpleKNN;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 画板图像识别器
 * 基于 SimpleKNN 识别 16x16 像素图案，匹配到模组物品
 */
public class DrawingBoardRecognizer {

    private static DrawingBoardRecognizer instance;
    private final SimpleKNN knn;
    // 存储每个类别的代表pattern，用于像素级校验
    private final Map<Integer, byte[][]> categoryPatterns = new HashMap<>();
    // 存储每个类别pattern的有效颜色数量（归一化后，排除背景白色）
    private final Map<Integer, Integer> categoryEffectiveColorCount = new HashMap<>();

    // 物品类别定义（与翻译键对应）
    public static final int UNKNOWN = -1;

    // ==================== 像素验证结果类 ====================
    /**
     * 像素验证结果，包含验证状态和超标程度
     */
    private static class PixelValidationResult {
        final boolean passed;                    // 是否通过验证
        final double extraPixelRatio;             // 透明超标程度（超过阈值的比例）
        final double missingPixelRatio;           // 遗漏超标程度（超过阈值的比例）

        PixelValidationResult(boolean passed, double extraPixelRatio, double missingPixelRatio) {
            this.passed = passed;
            this.extraPixelRatio = extraPixelRatio;
            this.missingPixelRatio = missingPixelRatio;
        }
    }

    /**
     * 识别结果类，包含识别结果和提示信息
     */
    public static class RecognizeResult {
        public final int category;
        public final int closestCategory;  // 最接近的类别（用于提示）
        public final String hint;            // 提示文本
        public final int voteCount;          // 得票数
        public final int totalVotes;         // 总投票权重
        public final double similarity;     // 相似程度，0.0-100.0

        public RecognizeResult(int category, int closestCategory, String hint, int voteCount, int totalVotes) {
            this(category, closestCategory, hint, voteCount, totalVotes, 0.0);
        }

        public RecognizeResult(int category) {
            this(category, category, "", 0, 0, 0.0);
        }

        public RecognizeResult(int category, int closestCategory, String hint) {
            this(category, closestCategory, hint, 0, 0, 0.0);
        }

        public RecognizeResult(int category, int closestCategory, String hint, int voteCount, int totalVotes, double similarity) {
            this.category = category;
            this.closestCategory = closestCategory;
            this.hint = hint;
            this.voteCount = voteCount;
            this.totalVotes = totalVotes;
            this.similarity = similarity;
        }

        public RecognizeResult(int category, double similarity) {
            this(category, category, "", 0, 0, similarity);
        }
    }

    // 物品类别ID
    public static final int KNIFE = 0;
    public static final int CROWBAR = 1;
    public static final int FIRECRACKER = 2;
    public static final int REVOLVER = 3;
    public static final int NOTE = 4;
    public static final int BODY_BAG = 5;
    public static final int DEFENSE_VIAL = 6;
    public static final int ANTIDOTE = 7;
    public static final int TOXIN = 8;
    public static final int CATALYST = 9;
    public static final int BOTTLE_OF_WATER = 10;
    public static final int LINGSHI = 11;
    public static final int KUNAI = 12;
    public static final int SHURIKEN = 13;
    public static final int HANDCUFFS = 14;
    public static final int NIGHT_VISION = 15;
    public static final int DIVING_HELMET = 16;
    public static final int DIVING_BOOTS = 17;
    public static final int MASTER_KEY_P = 18;
    public static final int DEFIBRILLATOR = 19;
    public static final int BOXING_GLOVE = 20;
    public static final int ANTIDOTE_REAGENT = 21;
    public static final int SMOKE_GRENADE = 22;
    public static final int FLASH_GRENADE = 23;
    public static final int REPAIR_TOOL = 24;
    public static final int SCREWDRIVER = 25;
    public static final int ALARM_TRAP = 26;
    public static final int DELIVERY_BOX = 27;
    public static final int HALLUCINATION = 28;
    public static final int MINT_CANDIES = 29;
    public static final int WHEELCHAIR = 31;
    public static final int SHORT_SHOTGUN = 32;
    public static final int BATON = 33;
    public static final int RADIO = 34;
    public static final int MONITORING_TERMINAL = 35;
    public static final int LOCK = 36;
    public static final int POCKET_WATCH = 37;
    public static final int VITAMIN = 38;
    public static final int FIRE_AXE = 39;
    public static final int THROWING_KNIFE = 40;
    public static final int ROPE = 41;
    public static final int EXTINGUISHER = 42;
    public static final int PASSBOOK = 43;
    public static final int TIME_STOP_CLOCK = 44;
    public static final int SHISYE = 45;
    public static final int PROBLEM_SET = 46;

    public static final int POISON_VIAL = 47;

    // 回形针 - noellesroles
    public static final int PAPERCLIP = 48;
    // 诱饵弹 - noellesroles
    public static final int DECOY_GRENADE = 49;

    // 类别数量
    public static final int CATEGORY_COUNT = 49;

    // 物品ID到Minecraft物品的映射
    private static final Map<Integer, Item> CATEGORY_TO_ITEM = new HashMap<>();

    static {
        // 初始化物品映射 - trainmurdermystery
        CATEGORY_TO_ITEM.put(KNIFE, findItem("trainmurdermystery", "knife"));
        CATEGORY_TO_ITEM.put(CROWBAR, findItem("trainmurdermystery", "crowbar"));
        CATEGORY_TO_ITEM.put(FIRECRACKER, findItem("trainmurdermystery", "firecracker"));
        CATEGORY_TO_ITEM.put(REVOLVER, findItem("trainmurdermystery", "revolver"));
        CATEGORY_TO_ITEM.put(NOTE, findItem("trainmurdermystery", "note"));
        CATEGORY_TO_ITEM.put(BODY_BAG, findItem("trainmurdermystery", "body_bag"));
        CATEGORY_TO_ITEM.put(DEFENSE_VIAL, findItem("trainmurdermystery", "defense_vial"));
        CATEGORY_TO_ITEM.put(POISON_VIAL, findItem("trainmurdermystery", "poison_vial"));

        // noellesroles 物品
        CATEGORY_TO_ITEM.put(ANTIDOTE, findItem("noellesroles", "antidote"));
        CATEGORY_TO_ITEM.put(TOXIN, findItem("noellesroles", "toxin"));
        CATEGORY_TO_ITEM.put(CATALYST, findItem("noellesroles", "catalyst"));
        CATEGORY_TO_ITEM.put(BOTTLE_OF_WATER, findItem("noellesroles", "a_bottle_of_water"));
        CATEGORY_TO_ITEM.put(LINGSHI, findItem("noellesroles", "lingshi"));  // 一包零食
        CATEGORY_TO_ITEM.put(KUNAI, findItem("noellesroles", "ninja_knife"));
        CATEGORY_TO_ITEM.put(SHURIKEN, findItem("noellesroles", "ninja_shuriken"));
        CATEGORY_TO_ITEM.put(HANDCUFFS, findItem("noellesroles", "handcuffs"));
        CATEGORY_TO_ITEM.put(NIGHT_VISION, findItem("noellesroles", "night_vision_glasses"));
        CATEGORY_TO_ITEM.put(DIVING_HELMET, findItem("noellesroles", "diving_helmet"));
        CATEGORY_TO_ITEM.put(DIVING_BOOTS, findItem("noellesroles", "diving_boots"));
        CATEGORY_TO_ITEM.put(MASTER_KEY_P, findItem("noellesroles", "master_key_p"));  // 乘务员钥匙
        CATEGORY_TO_ITEM.put(DEFIBRILLATOR, findItem("noellesroles", "defibrillator"));
        CATEGORY_TO_ITEM.put(BOXING_GLOVE, findItem("noellesroles", "boxing_glove"));
        CATEGORY_TO_ITEM.put(ANTIDOTE_REAGENT, findItem("noellesroles", "antidote_reagent"));
        CATEGORY_TO_ITEM.put(SMOKE_GRENADE, findItem("noellesroles", "smoke_grenade"));
        CATEGORY_TO_ITEM.put(FLASH_GRENADE, findItem("noellesroles", "flash_grenade"));
        CATEGORY_TO_ITEM.put(REPAIR_TOOL, findItem("noellesroles", "reinforcement"));
        CATEGORY_TO_ITEM.put(SCREWDRIVER, findItem("noellesroles", "screwdriver"));
        CATEGORY_TO_ITEM.put(ALARM_TRAP, findItem("noellesroles", "alarm_trap"));
        CATEGORY_TO_ITEM.put(DELIVERY_BOX, findItem("noellesroles", "delivery_box"));
        CATEGORY_TO_ITEM.put(HALLUCINATION, findItem("noellesroles", "hallucination_bottle"));
        CATEGORY_TO_ITEM.put(MINT_CANDIES, findItem("noellesroles", "mint_candies"));
        CATEGORY_TO_ITEM.put(WHEELCHAIR, findItem("noellesroles", "wheelchair"));
        CATEGORY_TO_ITEM.put(SHORT_SHOTGUN, findItem("noellesroles", "short_shotgun"));
        CATEGORY_TO_ITEM.put(BATON, findItem("noellesroles", "baton"));
        CATEGORY_TO_ITEM.put(RADIO, findItem("noellesroles", "radio"));
        CATEGORY_TO_ITEM.put(MONITORING_TERMINAL, findItem("noellesroles", "monitoring_terminal"));
        CATEGORY_TO_ITEM.put(LOCK, findItem("noellesroles", "lock"));
        CATEGORY_TO_ITEM.put(POCKET_WATCH, findItem("noellesroles", "pocket_watch"));
        CATEGORY_TO_ITEM.put(VITAMIN, findItem("noellesroles", "alchemist_buff_potion"));
        CATEGORY_TO_ITEM.put(FIRE_AXE, findItem("noellesroles", "fire_axe"));
        CATEGORY_TO_ITEM.put(THROWING_KNIFE, findItem("noellesroles", "throwing_knife"));
        CATEGORY_TO_ITEM.put(ROPE, findItem("noellesroles", "rope"));
        CATEGORY_TO_ITEM.put(EXTINGUISHER, findItem("noellesroles", "extinguisher"));
        CATEGORY_TO_ITEM.put(PASSBOOK, findItem("noellesroles", "passbook"));
        CATEGORY_TO_ITEM.put(TIME_STOP_CLOCK, findItem("noellesroles", "time_stop_clock"));
        CATEGORY_TO_ITEM.put(SHISYE, findItem("noellesroles", "shisiye"));
        CATEGORY_TO_ITEM.put(PROBLEM_SET, findItem("noellesroles", "problem_set"));
        CATEGORY_TO_ITEM.put(PAPERCLIP, findItem("noellesroles", "noell_paperclip"));  // 回形针
        CATEGORY_TO_ITEM.put(DECOY_GRENADE, findItem("noellesroles", "decoy_grenade"));  // 诱饵弹
    }

    private static Item findItem(String modId, String itemName) {
        try {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(modId, itemName);
            return BuiltInRegistries.ITEM.get(id);
        } catch (Exception e) {
            return null;
        }
    }

    private DrawingBoardRecognizer() {
        this.knn = new SimpleKNN(3);  // K=3，每个算法最多3票
        initializeTrainingData();
        initializeMoreTrainingVariants();
        computeCategoryColorCounts();
    }

    public static DrawingBoardRecognizer getInstance() {
        if (instance == null) {
            instance = new DrawingBoardRecognizer();
        }
        return instance;
    }

    private void initializeTrainingData() {
        // 添加各种物品的训练样本，并存储代表pattern用于像素级校验
        // 刀
        byte[][] knife = createKnifePattern();
        knn.addSample(SimpleKNN.matrixToFeature(knife), KNIFE);
        knn.addSample(SimpleKNN.matrixToFeature(createKnifePattern2()), KNIFE);
        categoryPatterns.put(KNIFE, knife);

        // 撬棍
        byte[][] crowbar = createCrowbarPattern();
        knn.addSample(SimpleKNN.matrixToFeature(crowbar), CROWBAR);
        knn.addSample(SimpleKNN.matrixToFeature(createCrowbarPattern2()), CROWBAR);
        categoryPatterns.put(CROWBAR, crowbar);

        // 鞭炮
        byte[][] firecracker = createFirecrackerPattern();
        knn.addSample(SimpleKNN.matrixToFeature(firecracker), FIRECRACKER);
        knn.addSample(SimpleKNN.matrixToFeature(createFirecrackerPattern2()), FIRECRACKER);
        categoryPatterns.put(FIRECRACKER, firecracker);

        // 左轮手枪
        byte[][] revolver = createRevolverPattern();
        knn.addSample(SimpleKNN.matrixToFeature(revolver), REVOLVER);
        knn.addSample(SimpleKNN.matrixToFeature(createRevolverPattern2()), REVOLVER);
        categoryPatterns.put(REVOLVER, revolver);

        // 便签
        byte[][] note = createNotePattern();
        knn.addSample(SimpleKNN.matrixToFeature(note), NOTE);
        knn.addSample(SimpleKNN.matrixToFeature(createNotePattern2()), NOTE);
        categoryPatterns.put(NOTE, note);

        // 裹尸袋
        byte[][] bodyBag = createBodyBagPattern();
        knn.addSample(SimpleKNN.matrixToFeature(bodyBag), BODY_BAG);
        knn.addSample(SimpleKNN.matrixToFeature(createBodyBagPattern2()), BODY_BAG);
        categoryPatterns.put(BODY_BAG, bodyBag);

        // 防御药剂
        byte[][] defenseVial = createDefenseVialPattern();
        knn.addSample(SimpleKNN.matrixToFeature(defenseVial), DEFENSE_VIAL);
        categoryPatterns.put(DEFENSE_VIAL, defenseVial);

        // 毒药瓶
        byte[][] poisonVial = createPoisonVialPattern();
        knn.addSample(SimpleKNN.matrixToFeature(poisonVial), POISON_VIAL);
        categoryPatterns.put(POISON_VIAL, poisonVial);

        // 解药
        byte[][] antidote = createAntidotePattern();
        knn.addSample(SimpleKNN.matrixToFeature(antidote), ANTIDOTE);
        knn.addSample(SimpleKNN.matrixToFeature(createAntidotePattern2()), ANTIDOTE);
        categoryPatterns.put(ANTIDOTE, antidote);

        // 毒针
        byte[][] toxin = createToxinPattern();
        knn.addSample(SimpleKNN.matrixToFeature(toxin), TOXIN);
        knn.addSample(SimpleKNN.matrixToFeature(createToxinPattern2()), TOXIN);
        categoryPatterns.put(TOXIN, toxin);

        // 催化剂
        byte[][] catalyst = createCatalystPattern();
        knn.addSample(SimpleKNN.matrixToFeature(catalyst), CATALYST);
        knn.addSample(SimpleKNN.matrixToFeature(createCatalystPattern2()), CATALYST);
        categoryPatterns.put(CATALYST, catalyst);

        // 一杯水
        byte[][] bottle = createBottlePattern();
        knn.addSample(SimpleKNN.matrixToFeature(bottle), BOTTLE_OF_WATER);
        knn.addSample(SimpleKNN.matrixToFeature(createBottlePattern2()), BOTTLE_OF_WATER);
        categoryPatterns.put(BOTTLE_OF_WATER, bottle);

        // 一包零食
        byte[][] lingshi = createLingshiPattern();
        knn.addSample(SimpleKNN.matrixToFeature(lingshi), LINGSHI);
        knn.addSample(SimpleKNN.matrixToFeature(createLingshiPattern2()), LINGSHI);
        categoryPatterns.put(LINGSHI, lingshi);

        // 苦无
        byte[][] kunai = createKunaiPattern();
        knn.addSample(SimpleKNN.matrixToFeature(kunai), KUNAI);
        knn.addSample(SimpleKNN.matrixToFeature(createKunaiPattern2()), KUNAI);
        categoryPatterns.put(KUNAI, kunai);

        // 手里剑
        byte[][] shuriken = createShurikenPattern();
        knn.addSample(SimpleKNN.matrixToFeature(shuriken), SHURIKEN);
        knn.addSample(SimpleKNN.matrixToFeature(createShurikenPattern2()), SHURIKEN);
        categoryPatterns.put(SHURIKEN, shuriken);

        // 手铐
        byte[][] handcuffs = createHandcuffsPattern();
        knn.addSample(SimpleKNN.matrixToFeature(handcuffs), HANDCUFFS);
        knn.addSample(SimpleKNN.matrixToFeature(createHandcuffsPattern2()), HANDCUFFS);
        categoryPatterns.put(HANDCUFFS, handcuffs);

        // 夜视仪
        byte[][] nightVision = createNightVisionPattern();
        knn.addSample(SimpleKNN.matrixToFeature(nightVision), NIGHT_VISION);
        knn.addSample(SimpleKNN.matrixToFeature(createNightVisionPattern2()), NIGHT_VISION);
        categoryPatterns.put(NIGHT_VISION, nightVision);

        // 潜水头盔
        byte[][] divingHelmet = createDivingHelmetPattern();
        knn.addSample(SimpleKNN.matrixToFeature(divingHelmet), DIVING_HELMET);
        knn.addSample(SimpleKNN.matrixToFeature(createDivingHelmetPattern2()), DIVING_HELMET);
        categoryPatterns.put(DIVING_HELMET, divingHelmet);

        // 潜水靴
        byte[][] divingBoots = createDivingBootsPattern();
        knn.addSample(SimpleKNN.matrixToFeature(divingBoots), DIVING_BOOTS);
        knn.addSample(SimpleKNN.matrixToFeature(createDivingBootsPattern2()), DIVING_BOOTS);
        categoryPatterns.put(DIVING_BOOTS, divingBoots);

        // 乘务员钥匙
        byte[][] masterKey = createMasterKeyPPattern();
        knn.addSample(SimpleKNN.matrixToFeature(masterKey), MASTER_KEY_P);
        knn.addSample(SimpleKNN.matrixToFeature(createMasterKeyPPattern2()), MASTER_KEY_P);
        categoryPatterns.put(MASTER_KEY_P, masterKey);

        // 心脏起搏器
        byte[][] defib = createDefibrillatorPattern();
        knn.addSample(SimpleKNN.matrixToFeature(defib), DEFIBRILLATOR);
        knn.addSample(SimpleKNN.matrixToFeature(createDefibrillatorPattern2()), DEFIBRILLATOR);
        categoryPatterns.put(DEFIBRILLATOR, defib);

        // 拳套
        byte[][] boxingGlove = createBoxingGlovePattern();
        knn.addSample(SimpleKNN.matrixToFeature(boxingGlove), BOXING_GLOVE);
        knn.addSample(SimpleKNN.matrixToFeature(createBoxingGlovePattern2()), BOXING_GLOVE);
        categoryPatterns.put(BOXING_GLOVE, boxingGlove);

        // 验毒试剂
        byte[][] antidoteReagent = createAntidoteReagentPattern();
        knn.addSample(SimpleKNN.matrixToFeature(antidoteReagent), ANTIDOTE_REAGENT);
        knn.addSample(SimpleKNN.matrixToFeature(createAntidoteReagentPattern2()), ANTIDOTE_REAGENT);
        categoryPatterns.put(ANTIDOTE_REAGENT, antidoteReagent);

        // 烟雾弹
        byte[][] smokeGrenade = createSmokeGrenadePattern();
        knn.addSample(SimpleKNN.matrixToFeature(smokeGrenade), SMOKE_GRENADE);
        knn.addSample(SimpleKNN.matrixToFeature(createSmokeGrenadePattern2()), SMOKE_GRENADE);
        categoryPatterns.put(SMOKE_GRENADE, smokeGrenade);

        // 闪光弹
        byte[][] flashGrenade = createFlashGrenadePattern();
        knn.addSample(SimpleKNN.matrixToFeature(flashGrenade), FLASH_GRENADE);
        knn.addSample(SimpleKNN.matrixToFeature(createFlashGrenadePattern2()), FLASH_GRENADE);
        categoryPatterns.put(FLASH_GRENADE, flashGrenade);

        // 维修工具
        byte[][] repairTool = createRepairToolPattern();
        knn.addSample(SimpleKNN.matrixToFeature(repairTool), REPAIR_TOOL);
        knn.addSample(SimpleKNN.matrixToFeature(createRepairToolPattern2()), REPAIR_TOOL);
        categoryPatterns.put(REPAIR_TOOL, repairTool);

        // 螺丝刀
        byte[][] screwdriver = createScrewdriverPattern();
        knn.addSample(SimpleKNN.matrixToFeature(screwdriver), SCREWDRIVER);
        knn.addSample(SimpleKNN.matrixToFeature(createScrewdriverPattern2()), SCREWDRIVER);
        categoryPatterns.put(SCREWDRIVER, screwdriver);

        // 警报陷阱
        byte[][] alarmTrap = createAlarmTrapPattern();
        knn.addSample(SimpleKNN.matrixToFeature(alarmTrap), ALARM_TRAP);
        knn.addSample(SimpleKNN.matrixToFeature(createAlarmTrapPattern2()), ALARM_TRAP);
        categoryPatterns.put(ALARM_TRAP, alarmTrap);

        // 邮件
        byte[][] deliveryBox = createDeliveryBoxPattern();
        knn.addSample(SimpleKNN.matrixToFeature(deliveryBox), DELIVERY_BOX);
        knn.addSample(SimpleKNN.matrixToFeature(createDeliveryBoxPattern2()), DELIVERY_BOX);
        categoryPatterns.put(DELIVERY_BOX, deliveryBox);

        // 迷幻瓶
        byte[][] hallucination = createHallucinationPattern();
        knn.addSample(SimpleKNN.matrixToFeature(hallucination), HALLUCINATION);
        knn.addSample(SimpleKNN.matrixToFeature(createHallucinationPattern2()), HALLUCINATION);
        categoryPatterns.put(HALLUCINATION, hallucination);

        // 薄荷糖
        byte[][] mintCandies = createMintCandiesPattern();
        knn.addSample(SimpleKNN.matrixToFeature(mintCandies), MINT_CANDIES);
        knn.addSample(SimpleKNN.matrixToFeature(createMintCandiesPattern2()), MINT_CANDIES);
        categoryPatterns.put(MINT_CANDIES, mintCandies);



        // 轮椅
        byte[][] wheelchair = createWheelchairPattern();
        knn.addSample(SimpleKNN.matrixToFeature(wheelchair), WHEELCHAIR);
        knn.addSample(SimpleKNN.matrixToFeature(createWheelchairPattern2()), WHEELCHAIR);
        categoryPatterns.put(WHEELCHAIR, wheelchair);

        // 短管霰弹枪
        byte[][] shotgun = createShortShotgunPattern();
        knn.addSample(SimpleKNN.matrixToFeature(shotgun), SHORT_SHOTGUN);
        knn.addSample(SimpleKNN.matrixToFeature(createShortShotgunPattern2()), SHORT_SHOTGUN);
        categoryPatterns.put(SHORT_SHOTGUN, shotgun);

        // 警棍
        byte[][] baton = createBatonPattern();
        knn.addSample(SimpleKNN.matrixToFeature(baton), BATON);
        knn.addSample(SimpleKNN.matrixToFeature(createBatonPattern2()), BATON);
        categoryPatterns.put(BATON, baton);

        // 对讲机
        byte[][] radio = createRadioPattern();
        knn.addSample(SimpleKNN.matrixToFeature(radio), RADIO);
        knn.addSample(SimpleKNN.matrixToFeature(createRadioPattern2()), RADIO);
        categoryPatterns.put(RADIO, radio);

        // 远程监控终端
        byte[][] monitor = createMonitoringTerminalPattern();
        knn.addSample(SimpleKNN.matrixToFeature(monitor), MONITORING_TERMINAL);
        knn.addSample(SimpleKNN.matrixToFeature(createMonitoringTerminalPattern2()), MONITORING_TERMINAL);
        categoryPatterns.put(MONITORING_TERMINAL, monitor);

        // 锁
        byte[][] lock = createLockPattern();
        knn.addSample(SimpleKNN.matrixToFeature(lock), LOCK);
        knn.addSample(SimpleKNN.matrixToFeature(createLockPattern2()), LOCK);
        categoryPatterns.put(LOCK, lock);

        // 怀表
        byte[][] pocketWatch = createPocketWatchPattern();
        knn.addSample(SimpleKNN.matrixToFeature(pocketWatch), POCKET_WATCH);
        knn.addSample(SimpleKNN.matrixToFeature(createPocketWatchPattern2()), POCKET_WATCH);
        categoryPatterns.put(POCKET_WATCH, pocketWatch);

        // 维生素
        byte[][] vitamin = createVitaminPattern();
        knn.addSample(SimpleKNN.matrixToFeature(vitamin), VITAMIN);
        knn.addSample(SimpleKNN.matrixToFeature(createVitaminPattern2()), VITAMIN);
        categoryPatterns.put(VITAMIN, vitamin);

        // 消防斧
        byte[][] fireAxe = createFireAxePattern();
        knn.addSample(SimpleKNN.matrixToFeature(fireAxe), FIRE_AXE);
        knn.addSample(SimpleKNN.matrixToFeature(createFireAxePattern2()), FIRE_AXE);
        categoryPatterns.put(FIRE_AXE, fireAxe);

        // 飞刀
        byte[][] throwingKnife = createThrowingKnifePattern();
        knn.addSample(SimpleKNN.matrixToFeature(throwingKnife), THROWING_KNIFE);
        knn.addSample(SimpleKNN.matrixToFeature(createThrowingKnifePattern2()), THROWING_KNIFE);
        categoryPatterns.put(THROWING_KNIFE, throwingKnife);

        // 绳索
        byte[][] rope = createRopePattern();
        knn.addSample(SimpleKNN.matrixToFeature(rope), ROPE);
        knn.addSample(SimpleKNN.matrixToFeature(createRopePattern2()), ROPE);
        categoryPatterns.put(ROPE, rope);

        // 灭火器
        byte[][] extinguisher = createExtinguisherPattern();
        knn.addSample(SimpleKNN.matrixToFeature(extinguisher), EXTINGUISHER);
        knn.addSample(SimpleKNN.matrixToFeature(createExtinguisherPattern2()), EXTINGUISHER);
        categoryPatterns.put(EXTINGUISHER, extinguisher);

        // 存折
        byte[][] passbook = createPassbookPattern();
        knn.addSample(SimpleKNN.matrixToFeature(passbook), PASSBOOK);
        knn.addSample(SimpleKNN.matrixToFeature(createPassbookPattern2()), PASSBOOK);
        categoryPatterns.put(PASSBOOK, passbook);

        // 时停钟
        byte[][] timeStopClock = createTimeStopClockPattern();
        knn.addSample(SimpleKNN.matrixToFeature(timeStopClock), TIME_STOP_CLOCK);
        knn.addSample(SimpleKNN.matrixToFeature(createTimeStopClockPattern2()), TIME_STOP_CLOCK);
        categoryPatterns.put(TIME_STOP_CLOCK, timeStopClock);

        // 十四夜
        byte[][] shisiye = createShisiyePattern();
        knn.addSample(SimpleKNN.matrixToFeature(shisiye), SHISYE);
        knn.addSample(SimpleKNN.matrixToFeature(createShisiyePattern2()), SHISYE);
        categoryPatterns.put(SHISYE, shisiye);

        // 习题集
        byte[][] problemSet = createProblemSetPattern();
        knn.addSample(SimpleKNN.matrixToFeature(problemSet), PROBLEM_SET);
        knn.addSample(SimpleKNN.matrixToFeature(createProblemSetPattern2()), PROBLEM_SET);
        categoryPatterns.put(PROBLEM_SET, problemSet);

        // 回形针
        byte[][] paperclip = createPaperclipPattern();
        knn.addSample(SimpleKNN.matrixToFeature(paperclip), PAPERCLIP);
        knn.addSample(SimpleKNN.matrixToFeature(createPaperclipPattern2()), PAPERCLIP);
        categoryPatterns.put(PAPERCLIP, paperclip);

        // 诱饵弹
        byte[][] decoyGrenade = createDecoyGrenadePattern();
        knn.addSample(SimpleKNN.matrixToFeature(decoyGrenade), DECOY_GRENADE);
        knn.addSample(SimpleKNN.matrixToFeature(createDecoyGrenadePattern2()), DECOY_GRENADE);
        categoryPatterns.put(DECOY_GRENADE, decoyGrenade);
    }

    /**
     * 添加更多训练样本变体，放宽识别要求
     */
    private void initializeMoreTrainingVariants() {
        // 添加更多变体的方法 - 使用旋转、缩放偏移等

        // 回形针 - 更多变体
        knn.addSample(SimpleKNN.matrixToFeature(createPaperclipPattern3()), PAPERCLIP);

        // 诱饵弹 - 更多变体
        knn.addSample(SimpleKNN.matrixToFeature(createDecoyGrenadePattern3()), DECOY_GRENADE);
    }

    // ==================== 物品图案生成方法 ====================

    /**
     * 创建一个16x16的空画布，所有像素初始化为背景白色(16)
     */
    private byte[][] createEmptyCanvas() {
        byte[][] canvas = new byte[16][16];
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                canvas[y][x] = (byte) COLOR_BACKGROUND_WHITE;
            }
        }
        return canvas;
    }

    // 刀 - 左下至右上对角线，柄在左下
            private byte[][] createKnifePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][11] = 11;
        p[2][12] = 11;
        p[2][13] = 11;
        p[2][14] = 11;
        p[3][9] = 11;
        p[3][10] = 11;
        p[3][11] = 11;
        p[3][12] = 11;
        p[3][13] = 11;
        p[4][8] = 11;
        p[4][9] = 11;
        p[4][10] = 11;
        p[4][11] = 11;
        p[4][12] = 11;
        p[5][7] = 11;
        p[5][8] = 11;
        p[5][9] = 11;
        p[5][10] = 11;
        p[5][11] = 11;
        p[6][6] = 11;
        p[6][7] = 11;
        p[6][8] = 11;
        p[6][9] = 11;
        p[6][10] = 11;
        p[7][5] = 11;
        p[7][6] = 11;
        p[7][7] = 11;
        p[7][8] = 11;
        p[7][9] = 11;
        p[8][4] = 11;
        p[8][5] = 11;
        p[8][6] = 11;
        p[8][7] = 11;
        p[8][8] = 11;
        p[9][3] = 11;
        p[9][4] = 11;
        p[9][5] = 11;
        p[9][6] = 11;
        p[9][7] = 11;
        p[10][4] = 11;
        p[10][5] = 11;
        p[11][3] = 15;
        p[11][4] = 15;
        p[12][2] = 15;
        p[12][3] = 15;
        p[13][1] = 15;
        p[13][2] = 15;
        p[14][0] = 15;
        p[14][1] = 15;
        return p;
    }


            private byte[][] createKnifePattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][4] = 10;
        p[2][3] = 10;
        p[3][6] = 10;
        p[3][5] = 10;
        p[3][4] = 10;
        p[3][3] = 11;
        p[3][2] = 10;
        p[4][7] = 10;
        p[4][6] = 11;
        p[4][5] = 11;
        p[4][4] = 11;
        p[4][3] = 11;
        p[5][8] = 10;
        p[5][7] = 11;
        p[5][6] = 11;
        p[5][5] = 11;
        p[5][4] = 10;
        p[6][9] = 10;
        p[6][8] = 11;
        p[6][7] = 1;
        p[6][6] = 10;
        p[6][5] = 10;
        p[7][10] = 10;
        p[7][9] = 11;
        p[7][8] = 1;
        p[7][7] = 10;
        p[7][6] = 10;
        p[8][11] = 10;
        p[8][10] = 11;
        p[8][9] = 1;
        p[8][8] = 10;
        p[8][7] = 10;
        p[9][12] = 10;
        p[9][11] = 11;
        p[9][10] = 11;
        p[9][9] = 10;
        p[9][8] = 10;
        p[10][11] = 10;
        p[10][10] = 10;
        p[11][12] = 12;
        p[11][11] = 12;
        p[12][13] = 12;
        p[12][12] = 12;
        p[13][14] = 12;
        p[13][13] = 12;
        p[14][15] = 12;
        p[14][14] = 12;
        p[14][13] = 12;
        return p;
    }

    // 撬棍 - J顺时针旋转180度 (即反J，├形状，柄在下)
            private byte[][] createCrowbarPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][8] = 10;
        p[0][9] = 10;
        p[0][10] = 10;
        p[1][7] = 10;
        p[1][8] = 11;
        p[1][9] = 11;
        p[1][10] = 10;
        p[1][11] = 10;
        p[2][6] = 10;
        p[2][7] = 10;
        p[2][9] = 11;
        p[2][11] = 10;
        p[2][12] = 10;
        p[3][6] = 10;
        p[3][8] = 11;
        p[3][11] = 10;
        p[3][12] = 10;
        p[4][10] = 10;
        p[4][11] = 10;
        p[5][10] = 10;
        p[5][11] = 10;
        p[6][9] = 10;
        p[6][10] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[8][7] = 10;
        p[8][8] = 11;
        p[9][6] = 10;
        p[9][7] = 11;
        p[10][5] = 10;
        p[10][6] = 10;
        p[11][4] = 10;
        p[11][5] = 10;
        p[12][3] = 10;
        p[12][4] = 10;
        p[13][2] = 10;
        p[13][3] = 10;
        return p;
    }


            private byte[][] createCrowbarPattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][7] = 10;
        p[0][6] = 10;
        p[1][8] = 10;
        p[1][7] = 11;
        p[1][6] = 11;
        p[1][5] = 10;
        p[1][4] = 10;
        p[2][9] = 10;
        p[2][8] = 10;
        p[2][6] = 11;
        p[2][3] = 10;
        p[3][9] = 10;
        p[3][7] = 10;
        p[3][3] = 10;
        p[5][5] = 10;
        p[6][6] = 10;
        p[7][7] = 10;
        p[7][6] = 10;
        p[8][8] = 10;
        p[8][7] = 11;
        p[9][9] = 10;
        p[9][8] = 11;
        p[10][10] = 10;
        p[10][9] = 10;
        p[11][10] = 10;
        p[12][11] = 10;
        return p;
    }

            private byte[][] createFirecrackerPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][9] = 10;
        p[2][10] = 10;
        p[2][11] = 10;
        p[3][5] = 10;
        p[3][8] = 10;
        p[3][12] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][13] = 10;
        p[5][8] = 12;
        p[5][9] = 12;
        p[5][10] = 12;
        p[5][13] = 10;
        p[6][7] = 12;
        p[6][8] = 8;
        p[6][9] = 8;
        p[6][10] = 8;
        p[6][11] = 12;
        p[6][12] = 10;
        p[7][6] = 12;
        p[7][7] = 12;
        p[7][8] = 8;
        p[7][9] = 15;
        p[7][10] = 10;
        p[7][11] = 10;
        p[7][12] = 12;
        p[8][5] = 12;
        p[8][6] = 2;
        p[8][7] = 12;
        p[8][8] = 8;
        p[8][9] = 15;
        p[8][10] = 15;
        p[8][11] = 8;
        p[8][12] = 12;
        p[9][4] = 12;
        p[9][5] = 12;
        p[9][6] = 2;
        p[9][7] = 12;
        p[9][8] = 12;
        p[9][9] = 8;
        p[9][10] = 8;
        p[9][11] = 12;
        p[10][3] = 12;
        p[10][4] = 2;
        p[10][5] = 12;
        p[10][6] = 12;
        p[10][7] = 2;
        p[10][8] = 12;
        p[10][9] = 12;
        p[10][10] = 12;
        p[11][3] = 12;
        p[11][4] = 2;
        p[11][5] = 12;
        p[11][6] = 12;
        p[11][7] = 12;
        p[11][8] = 2;
        p[11][9] = 12;
        p[12][3] = 12;
        p[12][4] = 12;
        p[12][5] = 2;
        p[12][6] = 12;
        p[12][7] = 12;
        p[12][8] = 12;
        p[13][4] = 12;
        p[13][5] = 12;
        p[13][6] = 2;
        p[13][7] = 12;
        p[14][5] = 12;
        p[14][6] = 12;
        return p;
    }


            private byte[][] createFirecrackerPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][6] = 10;
        p[2][5] = 10;
        p[2][4] = 10;
        p[3][10] = 10;
        p[3][7] = 10;
        p[3][3] = 10;
        p[4][9] = 10;
        p[4][8] = 10;
        p[4][2] = 10;
        p[5][7] = 12;
        p[5][6] = 12;
        p[5][5] = 12;
        p[5][2] = 10;
        p[6][8] = 12;
        p[6][7] = 10;
        p[6][6] = 15;
        p[6][5] = 15;
        p[6][4] = 12;
        p[6][3] = 10;
        p[7][9] = 12;
        p[7][8] = 12;
        p[7][7] = 15;
        p[7][6] = 12;
        p[7][5] = 10;
        p[7][4] = 10;
        p[7][3] = 12;
        p[8][10] = 12;
        p[8][9] = 12;
        p[8][8] = 10;
        p[8][7] = 15;
        p[8][6] = 12;
        p[8][5] = 12;
        p[8][4] = 10;
        p[8][3] = 12;
        p[9][11] = 12;
        p[9][10] = 12;
        p[9][9] = 8;
        p[9][8] = 10;
        p[9][7] = 12;
        p[9][6] = 10;
        p[9][5] = 10;
        p[9][4] = 12;
        p[9][3] = 12;
        p[10][12] = 12;
        p[10][11] = 12;
        p[10][10] = 12;
        p[10][9] = 10;
        p[10][8] = 12;
        p[10][7] = 12;
        p[10][6] = 12;
        p[10][5] = 12;
        p[11][12] = 12;
        p[11][11] = 12;
        p[11][10] = 10;
        p[11][9] = 12;
        p[11][8] = 12;
        p[11][7] = 12;
        p[11][6] = 12;
        p[12][12] = 12;
        p[12][11] = 12;
        p[12][10] = 12;
        p[12][9] = 12;
        p[12][8] = 12;
        p[12][7] = 12;
        p[13][11] = 12;
        p[13][10] = 12;
        p[13][9] = 12;
        p[13][8] = 12;
        return p;
    }

    // 左轮手枪 - 右下至左上，柄在右下
            private byte[][] createRevolverPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][5] = 10;
        p[0][6] = 10;
        p[1][4] = 10;
        p[1][5] = 11;
        p[1][6] = 10;
        p[1][7] = 10;
        p[2][4] = 10;
        p[2][5] = 10;
        p[2][6] = 11;
        p[2][7] = 10;
        p[3][5] = 10;
        p[3][6] = 10;
        p[3][7] = 11;
        p[3][8] = 10;
        p[4][5] = 10;
        p[4][6] = 11;
        p[4][7] = 11;
        p[4][8] = 11;
        p[4][9] = 10;
        p[5][5] = 10;
        p[5][6] = 10;
        p[5][7] = 11;
        p[5][8] = 11;
        p[5][9] = 11;
        p[5][10] = 10;
        p[6][6] = 10;
        p[6][7] = 10;
        p[6][8] = 11;
        p[6][9] = 10;
        p[6][10] = 10;
        p[6][11] = 10;
        p[6][12] = 10;
        p[7][6] = 10;
        p[7][7] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[7][10] = 11;
        p[7][11] = 10;
        p[8][6] = 10;
        p[8][8] = 10;
        p[8][9] = 11;
        p[8][10] = 10;
        p[9][7] = 10;
        p[9][8] = 10;
        p[9][9] = 10;
        p[9][10] = 10;
        p[10][8] = 15;
        p[10][9] = 15;
        p[10][10] = 15;
        p[11][6] = 15;
        p[11][7] = 15;
        p[11][8] = 15;
        p[11][9] = 15;
        p[11][10] = 15;
        p[12][5] = 15;
        p[12][6] = 15;
        p[12][7] = 15;
        p[12][8] = 15;
        p[12][9] = 15;
        p[12][10] = 15;
        p[13][5] = 15;
        p[13][6] = 15;
        p[13][7] = 15;
        p[13][8] = 15;
        p[13][9] = 15;
        p[14][6] = 15;
        p[14][7] = 15;
        p[14][8] = 15;
        return p;
    }


            private byte[][] createRevolverPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][10] = 10;
        p[1][9] = 10;
        p[2][11] = 10;
        p[2][10] = 11;
        p[2][9] = 10;
        p[2][8] = 10;
        p[3][10] = 10;
        p[3][9] = 10;
        p[3][8] = 10;
        p[4][9] = 10;
        p[4][8] = 11;
        p[4][7] = 10;
        p[5][9] = 11;
        p[5][8] = 1;
        p[5][7] = 11;
        p[5][6] = 10;
        p[6][10] = 10;
        p[6][9] = 10;
        p[6][8] = 11;
        p[6][7] = 11;
        p[6][6] = 11;
        p[7][9] = 10;
        p[7][8] = 10;
        p[7][7] = 10;
        p[8][8] = 10;
        p[8][5] = 10;
        p[9][6] = 10;
        p[11][7] = 12;
        p[11][6] = 12;
        p[12][9] = 12;
        p[12][8] = 12;
        p[12][7] = 12;
        p[12][6] = 12;
        p[13][10] = 12;
        p[13][9] = 12;
        p[13][8] = 12;
        p[13][7] = 12;
        p[14][9] = 12;
        p[14][8] = 12;
        return p;
    }

            private byte[][] createNotePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][6] = 10;
        p[1][7] = 10;
        p[1][8] = 10;
        p[2][5] = 10;
        p[2][6] = 11;
        p[2][7] = 11;
        p[2][8] = 10;
        p[2][9] = 0;
        p[3][3] = 12;
        p[3][4] = 10;
        p[3][5] = 11;
        p[3][6] = 11;
        p[3][7] = 10;
        p[3][8] = 10;
        p[3][9] = 12;
        p[3][10] = 12;
        p[3][11] = 12;
        p[3][12] = 12;
        p[4][3] = 12;
        p[4][4] = 0;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 0;
        p[4][9] = 15;
        p[4][10] = 15;
        p[4][11] = 12;
        p[4][12] = 12;
        p[4][13] = 12;
        p[5][3] = 12;
        p[5][4] = 12;
        p[5][5] = 15;
        p[5][6] = 15;
        p[5][7] = 0;
        p[5][8] = 0;
        p[5][9] = 15;
        p[5][10] = 15;
        p[5][11] = 15;
        p[5][12] = 15;
        p[5][13] = 12;
        p[6][4] = 12;
        p[6][5] = 15;
        p[6][6] = 15;
        p[6][7] = 15;
        p[6][8] = 15;
        p[6][9] = 15;
        p[6][10] = 15;
        p[6][11] = 15;
        p[6][12] = 15;
        p[6][13] = 12;
        p[7][4] = 12;
        p[7][5] = 15;
        p[7][6] = 15;
        p[7][7] = 15;
        p[7][8] = 15;
        p[7][9] = 15;
        p[7][10] = 15;
        p[7][11] = 15;
        p[7][12] = 15;
        p[7][13] = 12;
        p[8][4] = 12;
        p[8][5] = 15;
        p[8][6] = 15;
        p[8][7] = 15;
        p[8][8] = 15;
        p[8][9] = 15;
        p[8][10] = 15;
        p[8][11] = 15;
        p[8][12] = 15;
        p[8][13] = 12;
        p[9][4] = 12;
        p[9][5] = 15;
        p[9][6] = 15;
        p[9][7] = 15;
        p[9][8] = 15;
        p[9][9] = 15;
        p[9][10] = 15;
        p[9][11] = 15;
        p[9][12] = 15;
        p[9][13] = 12;
        p[10][3] = 12;
        p[10][4] = 15;
        p[10][5] = 15;
        p[10][6] = 15;
        p[10][7] = 15;
        p[10][8] = 15;
        p[10][9] = 15;
        p[10][10] = 15;
        p[10][11] = 15;
        p[10][12] = 12;
        p[11][3] = 12;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 15;
        p[11][7] = 15;
        p[11][8] = 15;
        p[11][9] = 15;
        p[11][10] = 15;
        p[11][11] = 15;
        p[11][12] = 12;
        p[12][2] = 12;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[12][6] = 15;
        p[12][7] = 15;
        p[12][8] = 15;
        p[12][9] = 15;
        p[12][10] = 12;
        p[12][11] = 12;
        p[13][2] = 12;
        p[13][3] = 12;
        p[13][4] = 12;
        p[13][5] = 12;
        p[13][6] = 12;
        p[13][7] = 12;
        p[13][8] = 12;
        p[13][9] = 12;
        p[13][10] = 12;
        return p;
    }


            private byte[][] createNotePattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][9] = 10;
        p[2][9] = 10;
        p[2][8] = 10;
        p[2][7] = 10;
        p[3][12] = 12;
        p[3][10] = 10;
        p[3][9] = 10;
        p[3][6] = 12;
        p[3][5] = 12;
        p[3][4] = 12;
        p[3][3] = 12;
        p[4][12] = 12;
        p[4][6] = 10;
        p[4][5] = 10;
        p[4][4] = 12;
        p[4][3] = 12;
        p[4][2] = 12;
        p[5][12] = 12;
        p[5][11] = 10;
        p[5][10] = 10;
        p[5][9] = 12;
        p[5][6] = 12;
        p[5][5] = 10;
        p[5][4] = 10;
        p[5][3] = 10;
        p[5][2] = 12;
        p[6][11] = 12;
        p[6][10] = 11;
        p[6][9] = 11;
        p[6][8] = 10;
        p[6][7] = 12;
        p[6][6] = 12;
        p[6][5] = 10;
        p[6][4] = 10;
        p[6][3] = 10;
        p[6][2] = 12;
        p[7][11] = 10;
        p[7][10] = 10;
        p[7][9] = 11;
        p[7][8] = 11;
        p[7][7] = 11;
        p[7][6] = 10;
        p[7][5] = 10;
        p[7][4] = 10;
        p[7][3] = 10;
        p[7][2] = 12;
        p[8][11] = 10;
        p[8][10] = 10;
        p[8][9] = 10;
        p[8][8] = 11;
        p[8][7] = 11;
        p[8][6] = 11;
        p[8][5] = 11;
        p[8][4] = 10;
        p[8][3] = 10;
        p[8][2] = 12;
        p[9][11] = 10;
        p[9][10] = 10;
        p[9][9] = 15;
        p[9][8] = 15;
        p[9][7] = 15;
        p[9][6] = 15;
        p[9][5] = 15;
        p[9][4] = 11;
        p[9][3] = 12;
        p[9][2] = 12;
        p[10][12] = 10;
        p[10][11] = 11;
        p[10][10] = 11;
        p[10][9] = 15;
        p[10][8] = 15;
        p[10][7] = 15;
        p[10][6] = 15;
        p[10][5] = 15;
        p[10][4] = 10;
        p[10][3] = 12;
        p[11][12] = 10;
        p[11][11] = 10;
        p[11][10] = 15;
        p[11][9] = 15;
        p[11][8] = 15;
        p[11][7] = 11;
        p[11][6] = 11;
        p[11][5] = 10;
        p[11][4] = 10;
        p[11][3] = 10;
        p[12][13] = 12;
        p[12][12] = 10;
        p[12][11] = 10;
        p[12][10] = 10;
        p[12][9] = 11;
        p[12][8] = 11;
        p[12][7] = 11;
        p[12][6] = 10;
        p[12][5] = 10;
        p[12][4] = 10;
        p[13][13] = 12;
        p[13][12] = 12;
        p[13][11] = 12;
        p[13][10] = 12;
        p[13][9] = 12;
        p[13][8] = 10;
        p[13][7] = 10;
        p[13][6] = 10;
        p[13][5] = 10;
        return p;
    }

            private byte[][] createBodyBagPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][8] = 15;
        p[1][9] = 15;
        p[1][10] = 15;
        p[2][6] = 15;
        p[2][7] = 15;
        p[2][8] = 5;
        p[2][9] = 5;
        p[2][10] = 5;
        p[2][11] = 15;
        p[3][4] = 15;
        p[3][5] = 15;
        p[3][6] = 5;
        p[3][7] = 5;
        p[3][8] = 5;
        p[3][9] = 5;
        p[3][10] = 5;
        p[3][11] = 5;
        p[3][12] = 15;
        p[4][2] = 15;
        p[4][3] = 15;
        p[4][4] = 5;
        p[4][5] = 5;
        p[4][6] = 5;
        p[4][7] = 5;
        p[4][8] = 5;
        p[4][9] = 5;
        p[4][10] = 5;
        p[4][11] = 5;
        p[4][12] = 5;
        p[4][13] = 15;
        p[5][1] = 15;
        p[5][2] = 5;
        p[5][3] = 5;
        p[5][4] = 1;
        p[5][5] = 5;
        p[5][6] = 1;
        p[5][7] = 5;
        p[5][8] = 5;
        p[5][9] = 5;
        p[5][10] = 1;
        p[5][11] = 5;
        p[5][12] = 5;
        p[5][13] = 5;
        p[5][14] = 15;
        p[6][1] = 15;
        p[6][2] = 5;
        p[6][3] = 5;
        p[6][4] = 5;
        p[6][5] = 1;
        p[6][6] = 5;
        p[6][7] = 5;
        p[6][8] = 5;
        p[6][9] = 1;
        p[6][10] = 5;
        p[6][11] = 1;
        p[6][12] = 1;
        p[6][13] = 1;
        p[6][14] = 5;
        p[6][15] = 15;
        p[7][1] = 15;
        p[7][2] = 5;
        p[7][3] = 1;
        p[7][4] = 5;
        p[7][5] = 5;
        p[7][6] = 1;
        p[7][7] = 5;
        p[7][8] = 5;
        p[7][9] = 5;
        p[7][10] = 1;
        p[7][11] = 1;
        p[7][12] = 5;
        p[7][13] = 5;
        p[7][14] = 5;
        p[7][15] = 15;
        p[8][1] = 15;
        p[8][2] = 5;
        p[8][3] = 5;
        p[8][4] = 1;
        p[8][5] = 5;
        p[8][6] = 5;
        p[8][7] = 1;
        p[8][8] = 1;
        p[8][9] = 2;
        p[8][10] = 5;
        p[8][11] = 5;
        p[8][12] = 15;
        p[8][13] = 15;
        p[8][14] = 5;
        p[8][15] = 15;
        p[9][2] = 15;
        p[9][3] = 5;
        p[9][4] = 15;
        p[9][5] = 1;
        p[9][6] = 5;
        p[9][7] = 5;
        p[9][8] = 5;
        p[9][9] = 5;
        p[9][10] = 2;
        p[9][11] = 15;
        p[9][12] = 15;
        p[9][13] = 5;
        p[9][14] = 5;
        p[9][15] = 15;
        p[10][3] = 15;
        p[10][4] = 5;
        p[10][5] = 15;
        p[10][6] = 5;
        p[10][7] = 5;
        p[10][8] = 15;
        p[10][9] = 15;
        p[10][10] = 2;
        p[10][11] = 5;
        p[10][12] = 5;
        p[10][13] = 15;
        p[10][14] = 15;
        p[11][4] = 15;
        p[11][5] = 5;
        p[11][6] = 15;
        p[11][7] = 15;
        p[11][8] = 15;
        p[11][9] = 8;
        p[11][10] = 8;
        p[11][11] = 15;
        p[11][12] = 15;
        p[12][5] = 15;
        p[12][6] = 5;
        p[12][7] = 15;
        p[12][8] = 8;
        p[12][9] = 15;
        p[12][10] = 8;
        p[12][11] = 15;
        p[13][6] = 15;
        p[13][7] = 8;
        p[13][8] = 15;
        p[13][9] = 8;
        p[13][10] = 15;
        p[14][6] = 15;
        p[14][7] = 15;
        p[14][8] = 8;
        p[14][9] = 15;
        p[15][7] = 15;
        p[15][8] = 15;
        return p;
    }


            private byte[][] createBodyBagPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][7] = 12;
        p[1][6] = 12;
        p[1][5] = 12;
        p[2][9] = 12;
        p[2][8] = 12;
        p[2][7] = 10;
        p[2][6] = 10;
        p[2][5] = 10;
        p[2][4] = 12;
        p[3][11] = 12;
        p[3][10] = 12;
        p[3][9] = 10;
        p[3][8] = 10;
        p[3][7] = 10;
        p[3][6] = 10;
        p[3][5] = 10;
        p[3][4] = 10;
        p[3][3] = 12;
        p[4][13] = 12;
        p[4][12] = 12;
        p[4][11] = 10;
        p[4][10] = 10;
        p[4][9] = 10;
        p[4][8] = 10;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[4][4] = 10;
        p[4][3] = 10;
        p[4][2] = 12;
        p[5][14] = 12;
        p[5][13] = 10;
        p[5][12] = 10;
        p[5][11] = 11;
        p[5][10] = 10;
        p[5][9] = 11;
        p[5][8] = 10;
        p[5][7] = 10;
        p[5][6] = 10;
        p[5][5] = 11;
        p[5][4] = 10;
        p[5][3] = 10;
        p[5][2] = 10;
        p[5][1] = 12;
        p[6][14] = 12;
        p[6][13] = 10;
        p[6][12] = 10;
        p[6][11] = 10;
        p[6][10] = 11;
        p[6][9] = 10;
        p[6][8] = 10;
        p[6][7] = 10;
        p[6][6] = 11;
        p[6][5] = 10;
        p[6][4] = 11;
        p[6][3] = 11;
        p[6][2] = 11;
        p[6][1] = 10;
        p[6][0] = 12;
        p[7][13] = 10;
        p[7][12] = 11;
        p[7][11] = 10;
        p[7][10] = 10;
        p[7][9] = 11;
        p[7][8] = 10;
        p[7][7] = 10;
        p[7][6] = 10;
        p[7][5] = 11;
        p[7][4] = 11;
        p[7][3] = 10;
        p[7][2] = 10;
        p[7][1] = 10;
        p[7][0] = 12;
        p[8][14] = 12;
        p[8][13] = 10;
        p[8][12] = 10;
        p[8][11] = 11;
        p[8][10] = 10;
        p[8][9] = 10;
        p[8][8] = 11;
        p[8][7] = 11;
        p[8][6] = 12;
        p[8][5] = 10;
        p[8][4] = 10;
        p[8][3] = 12;
        p[8][2] = 12;
        p[8][1] = 10;
        p[8][0] = 12;
        p[9][13] = 12;
        p[9][12] = 10;
        p[9][11] = 12;
        p[9][10] = 11;
        p[9][9] = 10;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][6] = 10;
        p[9][5] = 12;
        p[9][4] = 12;
        p[9][3] = 12;
        p[9][2] = 10;
        p[9][1] = 10;
        p[9][0] = 12;
        p[10][12] = 12;
        p[10][11] = 10;
        p[10][10] = 12;
        p[10][9] = 10;
        p[10][8] = 10;
        p[10][7] = 12;
        p[10][6] = 12;
        p[10][5] = 12;
        p[10][4] = 10;
        p[10][3] = 10;
        p[10][2] = 12;
        p[10][1] = 12;
        p[11][11] = 12;
        p[11][10] = 10;
        p[11][9] = 12;
        p[11][8] = 12;
        p[11][7] = 12;
        p[11][6] = 11;
        p[11][5] = 10;
        p[11][4] = 12;
        p[11][3] = 12;
        p[12][10] = 12;
        p[12][9] = 10;
        p[12][8] = 12;
        p[12][7] = 11;
        p[12][6] = 12;
        p[12][5] = 10;
        p[12][4] = 12;
        p[13][9] = 12;
        p[13][8] = 10;
        p[13][7] = 12;
        p[13][6] = 10;
        p[13][5] = 12;
        p[14][9] = 12;
        p[14][8] = 12;
        p[14][7] = 10;
        p[14][6] = 12;
        p[15][8] = 12;
        p[15][7] = 12;
        return p;
    }

    private byte[][] createDefenseVialPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][6] = 15;
        p[2][7] = 15;
        p[2][8] = 15;
        p[2][9] = 15;
        p[3][5] = 10;
        p[3][6] = 15;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 15;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][10] = 10;
        p[5][6] = 10;
        p[5][7] = 15;
        p[5][8] = 15;
        p[5][9] = 10;
        p[6][6] = 10;
        p[6][9] = 10;
        p[7][6] = 10;
        p[7][7] = 14;
        p[7][8] = 14;
        p[7][9] = 10;
        p[8][6] = 10;
        p[8][7] = 14;
        p[8][8] = 14;
        p[8][9] = 10;
        p[9][6] = 10;
        p[9][7] = 14;
        p[9][8] = 14;
        p[9][9] = 10;
        p[10][6] = 10;
        p[10][7] = 14;
        p[10][8] = 14;
        p[10][9] = 10;
        p[11][6] = 10;
        p[11][7] = 14;
        p[11][8] = 14;
        p[11][9] = 10;
        p[12][6] = 10;
        p[12][7] = 14;
        p[12][8] = 14;
        p[12][9] = 10;
        p[13][6] = 10;
        p[13][7] = 14;
        p[13][8] = 14;
        p[13][9] = 10;
        p[14][7] = 10;
        p[14][8] = 10;
        return p;
    }


        private byte[][] createAntidotePattern() {
        byte[][] p = createEmptyCanvas();
        p[0][3] = 1;
        p[0][4] = 1;
        p[1][2] = 1;
        p[1][3] = 1;
        p[2][1] = 1;
        p[2][2] = 1;
        p[3][0] = 1;
        p[3][1] = 1;
        p[3][3] = 1;
        p[3][6] = 1;
        p[4][0] = 1;
        p[4][4] = 1;
        p[4][5] = 1;
        p[4][7] = 1;
        p[5][4] = 1;
        p[5][8] = 1;
        p[6][3] = 1;
        p[6][4] = 10;
        p[6][5] = 7;
        p[6][6] = 7;
        p[6][7] = 7;
        p[6][8] = 7;
        p[6][9] = 1;
        p[7][4] = 1;
        p[7][5] = 10;
        p[7][6] = 10;
        p[7][7] = 7;
        p[7][8] = 1;
        p[7][9] = 7;
        p[7][10] = 1;
        p[8][5] = 1;
        p[8][6] = 10;
        p[8][7] = 10;
        p[8][8] = 7;
        p[8][9] = 1;
        p[8][10] = 7;
        p[8][11] = 1;
        p[9][6] = 1;
        p[9][7] = 14;
        p[9][8] = 10;
        p[9][9] = 7;
        p[9][10] = 7;
        p[9][11] = 7;
        p[9][12] = 1;
        p[10][7] = 1;
        p[10][8] = 14;
        p[10][9] = 10;
        p[10][10] = 10;
        p[10][11] = 10;
        p[11][8] = 1;
        p[11][9] = 14;
        p[11][10] = 1;
        p[11][11] = 10;
        p[12][9] = 1;
        p[12][12] = 10;
        p[13][13] = 10;
        p[14][14] = 7;
        return p;
    }

        private byte[][] createAntidotePattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][12] = 1;
        p[0][11] = 1;
        p[1][13] = 1;
        p[1][12] = 1;
        p[2][14] = 1;
        p[2][13] = 1;
        p[3][15] = 1;
        p[3][14] = 1;
        p[3][12] = 1;
        p[3][9] = 1;
        p[4][15] = 1;
        p[4][11] = 1;
        p[4][10] = 1;
        p[4][8] = 1;
        p[5][11] = 1;
        p[5][7] = 1;
        p[6][12] = 1;
        p[6][11] = 10;
        p[6][10] = 7;
        p[6][9] = 7;
        p[6][8] = 7;
        p[6][7] = 7;
        p[6][6] = 1;
        p[7][11] = 1;
        p[7][10] = 10;
        p[7][9] = 10;
        p[7][8] = 7;
        p[7][7] = 1;
        p[7][6] = 7;
        p[7][5] = 1;
        p[8][10] = 1;
        p[8][9] = 10;
        p[8][8] = 10;
        p[8][7] = 7;
        p[8][6] = 1;
        p[8][5] = 7;
        p[8][4] = 1;
        p[9][9] = 1;
        p[9][8] = 14;
        p[9][7] = 10;
        p[9][6] = 7;
        p[9][5] = 7;
        p[9][4] = 7;
        p[9][3] = 1;
        p[10][8] = 1;
        p[10][7] = 14;
        p[10][6] = 10;
        p[10][5] = 10;
        p[10][4] = 10;
        p[11][7] = 1;
        p[11][6] = 14;
        p[11][5] = 1;
        p[11][4] = 10;
        p[12][6] = 1;
        p[12][3] = 10;
        p[13][2] = 10;
        p[14][1] = 7;
        return p;
    }

        private byte[][] createToxinPattern() {
        byte[][] p = createEmptyCanvas();
        p[0][3] = 1;
        p[0][4] = 1;
        p[1][2] = 1;
        p[1][3] = 1;
        p[2][1] = 1;
        p[2][2] = 1;
        p[3][0] = 1;
        p[3][1] = 1;
        p[3][3] = 1;
        p[3][6] = 1;
        p[4][0] = 1;
        p[4][4] = 1;
        p[4][5] = 1;
        p[4][7] = 1;
        p[5][4] = 1;
        p[5][8] = 1;
        p[6][3] = 1;
        p[6][4] = 12;
        p[6][5] = 2;
        p[6][6] = 2;
        p[6][7] = 2;
        p[6][8] = 2;
        p[6][9] = 1;
        p[7][4] = 1;
        p[7][5] = 12;
        p[7][6] = 2;
        p[7][7] = 2;
        p[7][8] = 1;
        p[7][9] = 2;
        p[7][10] = 1;
        p[8][5] = 1;
        p[8][6] = 12;
        p[8][7] = 2;
        p[8][8] = 2;
        p[8][9] = 1;
        p[8][10] = 2;
        p[8][11] = 1;
        p[9][6] = 1;
        p[9][7] = 12;
        p[9][8] = 2;
        p[9][9] = 2;
        p[9][10] = 2;
        p[9][11] = 2;
        p[9][12] = 1;
        p[10][7] = 1;
        p[10][8] = 12;
        p[10][9] = 12;
        p[10][10] = 12;
        p[10][11] = 10;
        p[11][8] = 1;
        p[11][9] = 12;
        p[11][10] = 1;
        p[11][11] = 10;
        p[12][9] = 1;
        p[12][12] = 10;
        p[13][13] = 10;
        p[14][14] = 12;
        return p;
    }

        private byte[][] createToxinPattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][12] = 1;
        p[0][11] = 1;
        p[1][13] = 1;
        p[1][12] = 1;
        p[2][14] = 1;
        p[2][13] = 1;
        p[3][15] = 1;
        p[3][14] = 1;
        p[3][12] = 1;
        p[3][9] = 1;
        p[4][15] = 1;
        p[4][11] = 1;
        p[4][10] = 1;
        p[4][8] = 1;
        p[5][11] = 1;
        p[5][7] = 1;
        p[6][12] = 1;
        p[6][11] = 12;
        p[6][10] = 2;
        p[6][9] = 2;
        p[6][8] = 2;
        p[6][7] = 2;
        p[6][6] = 1;
        p[7][11] = 1;
        p[7][10] = 12;
        p[7][9] = 2;
        p[7][8] = 2;
        p[7][7] = 1;
        p[7][6] = 2;
        p[7][5] = 1;
        p[8][10] = 1;
        p[8][9] = 12;
        p[8][8] = 2;
        p[8][7] = 2;
        p[8][6] = 1;
        p[8][5] = 2;
        p[8][4] = 1;
        p[9][9] = 1;
        p[9][8] = 12;
        p[9][7] = 2;
        p[9][6] = 2;
        p[9][5] = 2;
        p[9][4] = 2;
        p[9][3] = 1;
        p[10][8] = 1;
        p[10][7] = 12;
        p[10][6] = 12;
        p[10][5] = 12;
        p[10][4] = 10;
        p[11][7] = 1;
        p[11][6] = 12;
        p[11][5] = 1;
        p[11][4] = 10;
        p[12][6] = 1;
        p[12][3] = 10;
        p[13][2] = 10;
        p[14][1] = 12;
        return p;
    }

        private byte[][] createCatalystPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][6] = 15;
        p[2][7] = 15;
        p[2][8] = 15;
        p[2][9] = 15;
        p[3][5] = 10;
        p[3][6] = 15;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 15;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][10] = 10;
        p[5][6] = 10;
        p[5][7] = 15;
        p[5][8] = 15;
        p[5][9] = 10;
        p[6][6] = 10;
        p[6][9] = 10;
        p[7][6] = 10;
        p[7][7] = 7;
        p[7][8] = 7;
        p[7][9] = 10;
        p[8][6] = 10;
        p[8][7] = 13;
        p[8][8] = 7;
        p[8][9] = 10;
        p[9][6] = 10;
        p[9][7] = 7;
        p[9][8] = 13;
        p[9][9] = 10;
        p[10][6] = 10;
        p[10][7] = 13;
        p[10][8] = 7;
        p[10][9] = 10;
        p[11][6] = 10;
        p[11][7] = 7;
        p[11][8] = 13;
        p[11][9] = 10;
        p[12][6] = 10;
        p[12][7] = 13;
        p[12][8] = 13;
        p[12][9] = 10;
        p[13][6] = 10;
        p[13][7] = 13;
        p[13][8] = 13;
        p[13][9] = 10;
        p[14][7] = 10;
        p[14][8] = 10;
        return p;
    }


        private byte[][] createCatalystPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][9] = 10;
        p[2][8] = 10;
        p[2][7] = 10;
        p[2][6] = 10;
        p[3][10] = 10;
        p[3][9] = 12;
        p[3][8] = 12;
        p[3][7] = 12;
        p[3][6] = 12;
        p[3][5] = 10;
        p[4][10] = 11;
        p[4][9] = 11;
        p[4][8] = 11;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[5][9] = 10;
        p[5][8] = 12;
        p[5][7] = 12;
        p[6][9] = 10;
        p[6][6] = 10;
        p[7][9] = 10;
        p[7][8] = 7;
        p[7][7] = 7;
        p[7][6] = 10;
        p[8][9] = 10;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 11;
        p[9][9] = 11;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][6] = 11;
        p[10][9] = 11;
        p[10][8] = 10;
        p[10][7] = 10;
        p[10][6] = 11;
        p[11][9] = 11;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][6] = 10;
        p[12][9] = 11;
        p[12][8] = 10;
        p[12][7] = 14;
        p[12][6] = 10;
        p[13][9] = 10;
        p[13][8] = 14;
        p[13][7] = 14;
        p[13][6] = 10;
        p[14][8] = 10;
        p[14][7] = 10;
        return p;
    }

            private byte[][] createBottlePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][4] = 10;
        p[2][5] = 10;
        p[2][6] = 10;
        p[2][7] = 10;
        p[2][8] = 10;
        p[2][9] = 10;
        p[2][10] = 10;
        p[2][11] = 10;
        p[3][3] = 10;
        p[3][12] = 10;
        p[4][3] = 10;
        p[4][4] = 10;
        p[4][11] = 10;
        p[4][12] = 10;
        p[5][3] = 10;
        p[5][5] = 10;
        p[5][6] = 10;
        p[5][7] = 10;
        p[5][8] = 10;
        p[5][9] = 10;
        p[5][10] = 10;
        p[5][12] = 10;
        p[6][3] = 10;
        p[6][5] = 4;
        p[6][6] = 4;
        p[6][7] = 4;
        p[6][8] = 4;
        p[6][9] = 4;
        p[6][10] = 4;
        p[6][12] = 10;
        p[7][3] = 10;
        p[7][4] = 4;
        p[7][5] = 11;
        p[7][6] = 11;
        p[7][7] = 4;
        p[7][8] = 4;
        p[7][9] = 4;
        p[7][10] = 4;
        p[7][11] = 4;
        p[7][12] = 10;
        p[8][3] = 10;
        p[8][4] = 4;
        p[8][5] = 11;
        p[8][6] = 4;
        p[8][7] = 4;
        p[8][8] = 4;
        p[8][9] = 4;
        p[8][10] = 4;
        p[8][11] = 4;
        p[8][12] = 10;
        p[9][3] = 10;
        p[9][4] = 4;
        p[9][5] = 4;
        p[9][6] = 4;
        p[9][7] = 4;
        p[9][8] = 4;
        p[9][9] = 4;
        p[9][10] = 4;
        p[9][11] = 4;
        p[9][12] = 10;
        p[10][3] = 10;
        p[10][4] = 4;
        p[10][5] = 4;
        p[10][6] = 4;
        p[10][7] = 4;
        p[10][8] = 4;
        p[10][9] = 4;
        p[10][10] = 10;
        p[10][11] = 4;
        p[10][12] = 10;
        p[11][3] = 10;
        p[11][4] = 4;
        p[11][5] = 4;
        p[11][6] = 4;
        p[11][7] = 4;
        p[11][8] = 4;
        p[11][9] = 10;
        p[11][10] = 10;
        p[11][11] = 4;
        p[11][12] = 10;
        p[12][3] = 10;
        p[12][4] = 4;
        p[12][5] = 4;
        p[12][6] = 4;
        p[12][7] = 4;
        p[12][8] = 4;
        p[12][9] = 10;
        p[12][10] = 4;
        p[12][11] = 4;
        p[12][12] = 10;
        p[13][4] = 10;
        p[13][5] = 4;
        p[13][6] = 4;
        p[13][7] = 4;
        p[13][8] = 4;
        p[13][9] = 4;
        p[13][10] = 4;
        p[13][11] = 10;
        p[14][5] = 10;
        p[14][6] = 10;
        p[14][7] = 10;
        p[14][8] = 10;
        p[14][9] = 10;
        p[14][10] = 10;
        return p;
    }


            private byte[][] createBottlePattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][11] = 11;
        p[2][10] = 1;
        p[2][9] = 11;
        p[2][8] = 11;
        p[2][7] = 11;
        p[2][6] = 11;
        p[2][5] = 11;
        p[2][4] = 11;
        p[3][10] = 11;
        p[3][9] = 11;
        p[3][8] = 11;
        p[3][7] = 11;
        p[3][6] = 11;
        p[3][5] = 11;
        p[4][12] = 11;
        p[4][3] = 10;
        p[5][12] = 11;
        p[5][11] = 11;
        p[5][10] = 11;
        p[5][9] = 11;
        p[5][8] = 11;
        p[5][7] = 11;
        p[5][6] = 10;
        p[5][5] = 10;
        p[5][4] = 10;
        p[5][3] = 10;
        p[6][12] = 1;
        p[6][10] = 10;
        p[6][9] = 11;
        p[6][8] = 11;
        p[6][7] = 11;
        p[6][6] = 10;
        p[6][5] = 10;
        p[6][3] = 10;
        p[7][12] = 11;
        p[7][11] = 10;
        p[7][10] = 11;
        p[7][9] = 11;
        p[7][8] = 4;
        p[7][7] = 4;
        p[7][6] = 10;
        p[7][5] = 9;
        p[7][4] = 10;
        p[7][3] = 10;
        p[8][12] = 11;
        p[8][11] = 10;
        p[8][10] = 11;
        p[8][9] = 11;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 10;
        p[8][5] = 10;
        p[8][4] = 10;
        p[8][3] = 10;
        p[9][12] = 11;
        p[9][11] = 10;
        p[9][10] = 10;
        p[9][9] = 4;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][6] = 10;
        p[9][5] = 10;
        p[9][4] = 10;
        p[9][3] = 10;
        p[10][12] = 11;
        p[10][11] = 10;
        p[10][10] = 4;
        p[10][9] = 10;
        p[10][8] = 4;
        p[10][7] = 4;
        p[10][6] = 4;
        p[10][5] = 10;
        p[10][4] = 4;
        p[10][3] = 10;
        p[11][12] = 11;
        p[11][11] = 10;
        p[11][10] = 4;
        p[11][9] = 10;
        p[11][8] = 4;
        p[11][7] = 10;
        p[11][6] = 10;
        p[11][5] = 10;
        p[11][4] = 10;
        p[11][3] = 10;
        p[12][12] = 11;
        p[12][11] = 4;
        p[12][10] = 4;
        p[12][9] = 4;
        p[12][8] = 4;
        p[12][7] = 4;
        p[12][6] = 10;
        p[12][5] = 10;
        p[12][4] = 4;
        p[12][3] = 10;
        p[13][12] = 11;
        p[13][11] = 10;
        p[13][10] = 4;
        p[13][9] = 4;
        p[13][8] = 4;
        p[13][7] = 4;
        p[13][6] = 10;
        p[13][5] = 4;
        p[13][4] = 10;
        p[13][3] = 10;
        p[14][10] = 10;
        p[14][9] = 10;
        p[14][8] = 10;
        p[14][7] = 10;
        p[14][6] = 10;
        p[14][5] = 10;
        p[15][9] = 10;
        p[15][8] = 10;
        p[15][7] = 10;
        p[15][6] = 10;
        return p;
    }

                private byte[][] createLingshiPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][3] = 5;
        p[2][4] = 5;
        p[2][5] = 5;
        p[2][6] = 5;
        p[2][7] = 5;
        p[2][8] = 5;
        p[2][9] = 5;
        p[2][10] = 5;
        p[2][11] = 5;
        p[2][12] = 5;
        p[3][3] = 5;
        p[3][4] = 5;
        p[3][5] = 5;
        p[3][6] = 5;
        p[3][7] = 5;
        p[3][8] = 5;
        p[3][9] = 5;
        p[3][10] = 2;
        p[3][11] = 2;
        p[3][12] = 5;
        p[4][3] = 5;
        p[4][4] = 5;
        p[4][5] = 5;
        p[4][6] = 2;
        p[4][7] = 2;
        p[4][8] = 5;
        p[4][9] = 5;
        p[4][10] = 5;
        p[4][11] = 5;
        p[4][12] = 5;
        p[5][3] = 5;
        p[5][4] = 5;
        p[5][5] = 2;
        p[5][6] = 1;
        p[5][7] = 2;
        p[5][8] = 2;
        p[5][9] = 2;
        p[5][10] = 2;
        p[5][11] = 5;
        p[6][4] = 5;
        p[6][5] = 2;
        p[6][6] = 1;
        p[6][7] = 2;
        p[6][8] = 1;
        p[6][9] = 2;
        p[6][10] = 2;
        p[6][11] = 5;
        p[7][4] = 5;
        p[7][5] = 2;
        p[7][6] = 2;
        p[7][7] = 1;
        p[7][8] = 2;
        p[7][9] = 1;
        p[7][10] = 2;
        p[7][11] = 5;
        p[8][4] = 5;
        p[8][5] = 2;
        p[8][6] = 1;
        p[8][7] = 1;
        p[8][8] = 2;
        p[8][9] = 2;
        p[8][10] = 5;
        p[8][11] = 5;
        p[9][3] = 5;
        p[9][4] = 5;
        p[9][5] = 5;
        p[9][6] = 2;
        p[9][7] = 2;
        p[9][8] = 5;
        p[9][9] = 5;
        p[9][10] = 5;
        p[9][11] = 5;
        p[10][3] = 5;
        p[10][4] = 5;
        p[10][5] = 5;
        p[10][6] = 5;
        p[10][7] = 5;
        p[10][8] = 5;
        p[10][9] = 5;
        p[10][10] = 5;
        p[10][11] = 5;
        p[10][12] = 5;
        p[11][3] = 5;
        p[11][4] = 5;
        p[11][5] = 5;
        p[11][6] = 5;
        p[11][7] = 5;
        p[11][8] = 5;
        p[11][9] = 5;
        p[11][10] = 5;
        p[11][11] = 5;
        p[11][12] = 5;
        p[12][3] = 5;
        p[12][4] = 5;
        p[12][5] = 5;
        p[12][6] = 5;
        p[12][7] = 5;
        p[12][8] = 5;
        p[12][9] = 5;
        p[12][10] = 5;
        p[12][11] = 5;
        p[12][12] = 5;
        p[13][3] = 5;
        p[13][4] = 5;
        p[13][5] = 5;
        p[13][6] = 5;
        p[13][7] = 5;
        p[13][8] = 5;
        p[13][9] = 5;
        p[13][10] = 5;
        p[13][11] = 5;
        p[13][12] = 5;
        return p;
    }


                private byte[][] createLingshiPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][12] = 13;
        p[2][11] = 13;
        p[2][10] = 13;
        p[2][9] = 10;
        p[2][8] = 13;
        p[2][7] = 13;
        p[2][6] = 13;
        p[2][5] = 13;
        p[2][4] = 8;
        p[2][3] = 10;
        p[3][12] = 15;
        p[3][11] = 15;
        p[3][10] = 5;
        p[3][9] = 15;
        p[3][8] = 5;
        p[3][7] = 5;
        p[3][6] = 15;
        p[3][5] = 13;
        p[3][4] = 15;
        p[3][3] = 10;
        p[4][12] = 10;
        p[4][11] = 15;
        p[4][10] = 10;
        p[4][9] = 2;
        p[4][8] = 8;
        p[4][7] = 8;
        p[4][6] = 8;
        p[4][5] = 11;
        p[4][4] = 13;
        p[4][3] = 12;
        p[5][12] = 13;
        p[5][11] = 15;
        p[5][10] = 11;
        p[5][9] = 10;
        p[5][8] = 11;
        p[5][7] = 10;
        p[5][6] = 11;
        p[5][5] = 12;
        p[5][4] = 13;
        p[6][11] = 11;
        p[6][10] = 11;
        p[6][9] = 15;
        p[6][8] = 10;
        p[6][7] = 15;
        p[6][6] = 12;
        p[6][5] = 12;
        p[6][4] = 13;
        p[7][11] = 11;
        p[7][10] = 8;
        p[7][9] = 5;
        p[7][8] = 13;
        p[7][7] = 8;
        p[7][6] = 8;
        p[7][5] = 11;
        p[7][4] = 13;
        p[8][11] = 5;
        p[8][10] = 15;
        p[8][9] = 5;
        p[8][8] = 13;
        p[8][7] = 13;
        p[8][6] = 5;
        p[8][5] = 5;
        p[8][4] = 8;
        p[9][12] = 5;
        p[9][11] = 13;
        p[9][10] = 15;
        p[9][9] = 15;
        p[9][8] = 15;
        p[9][7] = 15;
        p[9][6] = 15;
        p[9][5] = 13;
        p[9][4] = 13;
        p[10][12] = 12;
        p[10][11] = 13;
        p[10][10] = 11;
        p[10][9] = 15;
        p[10][8] = 15;
        p[10][7] = 13;
        p[10][6] = 15;
        p[10][5] = 15;
        p[10][4] = 15;
        p[10][3] = 8;
        p[11][12] = 12;
        p[11][11] = 8;
        p[11][10] = 15;
        p[11][9] = 15;
        p[11][8] = 15;
        p[11][7] = 15;
        p[11][6] = 13;
        p[11][5] = 15;
        p[11][4] = 13;
        p[11][3] = 8;
        p[12][12] = 5;
        p[12][11] = 13;
        p[12][10] = 8;
        p[12][9] = 13;
        p[12][8] = 13;
        p[12][7] = 13;
        p[12][6] = 13;
        p[12][5] = 13;
        p[12][4] = 11;
        p[12][3] = 10;
        p[13][12] = 13;
        p[13][11] = 13;
        p[13][10] = 8;
        p[13][9] = 8;
        p[13][8] = 12;
        p[13][7] = 12;
        p[13][6] = 12;
        p[13][5] = 13;
        p[13][4] = 8;
        p[13][3] = 13;
        return p;
    }

            private byte[][] createKunaiPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][12] = 10;
        p[1][13] = 10;
        p[1][14] = 10;
        p[2][11] = 10;
        p[2][12] = 10;
        p[2][13] = 10;
        p[2][14] = 10;
        p[3][10] = 10;
        p[3][11] = 10;
        p[3][12] = 10;
        p[3][13] = 10;
        p[3][14] = 10;
        p[4][9] = 10;
        p[4][10] = 10;
        p[4][11] = 10;
        p[4][12] = 10;
        p[4][13] = 10;
        p[5][8] = 10;
        p[5][9] = 10;
        p[5][10] = 10;
        p[5][11] = 10;
        p[5][12] = 10;
        p[6][7] = 10;
        p[6][8] = 10;
        p[6][9] = 11;
        p[6][10] = 10;
        p[6][11] = 10;
        p[7][7] = 10;
        p[7][8] = 11;
        p[7][9] = 10;
        p[7][10] = 10;
        p[8][6] = 10;
        p[8][7] = 10;
        p[8][8] = 10;
        p[8][9] = 10;
        p[9][6] = 10;
        p[9][7] = 10;
        p[10][5] = 10;
        p[10][6] = 10;
        p[11][4] = 10;
        p[11][5] = 10;
        p[12][3] = 10;
        p[12][4] = 10;
        p[13][2] = 10;
        p[13][4] = 10;
        p[14][2] = 10;
        p[14][4] = 10;
        p[15][3] = 10;
        return p;
    }


            private byte[][] createKunaiPattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][1] = 10;
        p[1][2] = 10;
        p[1][1] = 10;
        p[2][4] = 12;
        p[2][3] = 10;
        p[2][2] = 10;
        p[2][1] = 10;
        p[3][5] = 10;
        p[3][4] = 10;
        p[3][3] = 10;
        p[3][2] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[4][4] = 10;
        p[4][3] = 10;
        p[5][7] = 10;
        p[5][6] = 10;
        p[5][5] = 10;
        p[5][4] = 10;
        p[6][8] = 10;
        p[6][7] = 10;
        p[6][6] = 11;
        p[6][5] = 10;
        p[6][4] = 12;
        p[7][8] = 10;
        p[7][7] = 11;
        p[7][6] = 10;
        p[7][5] = 12;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 10;
        p[9][9] = 10;
        p[9][8] = 10;
        p[10][10] = 10;
        p[10][9] = 10;
        p[11][11] = 10;
        p[11][10] = 10;
        p[12][13] = 10;
        p[12][12] = 10;
        p[12][11] = 10;
        p[13][13] = 10;
        p[13][11] = 10;
        p[14][13] = 10;
        p[14][11] = 10;
        p[15][12] = 10;
        return p;
    }

            private byte[][] createShurikenPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][11] = 0;
        p[1][12] = 0;
        p[2][1] = 0;
        p[2][2] = 0;
        p[2][3] = 0;
        p[2][10] = 0;
        p[2][11] = 1;
        p[2][12] = 0;
        p[3][1] = 0;
        p[3][2] = 1;
        p[3][3] = 0;
        p[3][4] = 0;
        p[3][5] = 0;
        p[3][9] = 0;
        p[3][10] = 0;
        p[3][11] = 10;
        p[3][12] = 0;
        p[4][2] = 0;
        p[4][3] = 10;
        p[4][4] = 1;
        p[4][5] = 1;
        p[4][6] = 0;
        p[4][8] = 0;
        p[4][9] = 1;
        p[4][10] = 1;
        p[4][11] = 1;
        p[4][12] = 0;
        p[5][3] = 0;
        p[5][4] = 10;
        p[5][5] = 1;
        p[5][6] = 1;
        p[5][7] = 0;
        p[5][8] = 0;
        p[5][9] = 1;
        p[5][10] = 10;
        p[5][11] = 0;
        p[6][4] = 0;
        p[6][5] = 10;
        p[6][6] = 1;
        p[6][7] = 1;
        p[6][8] = 1;
        p[6][9] = 10;
        p[6][10] = 11;
        p[6][11] = 0;
        p[7][5] = 0;
        p[7][6] = 1;
        p[7][7] = 0;
        p[7][8] = 0;
        p[7][9] = 0;
        p[7][10] = 0;
        p[8][4] = 0;
        p[8][5] = 10;
        p[8][6] = 1;
        p[8][7] = 0;
        p[8][8] = 1;
        p[8][9] = 0;
        p[9][4] = 0;
        p[9][5] = 1;
        p[9][6] = 10;
        p[9][7] = 0;
        p[9][8] = 10;
        p[9][9] = 10;
        p[9][10] = 0;
        p[10][3] = 0;
        p[10][4] = 1;
        p[10][5] = 10;
        p[10][6] = 0;
        p[10][8] = 0;
        p[10][9] = 10;
        p[10][10] = 10;
        p[10][11] = 0;
        p[11][2] = 0;
        p[11][3] = 1;
        p[11][4] = 1;
        p[11][5] = 0;
        p[11][9] = 0;
        p[11][10] = 11;
        p[11][11] = 1;
        p[11][12] = 0;
        p[12][2] = 0;
        p[12][3] = 1;
        p[12][4] = 0;
        p[12][10] = 0;
        p[12][11] = 0;
        p[12][12] = 0;
        p[13][2] = 0;
        p[13][3] = 11;
        p[13][4] = 0;
        p[14][2] = 0;
        p[14][3] = 0;
        return p;
    }


            private byte[][] createShurikenPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][4] = 1;
        p[3][13] = 1;
        p[3][4] = 11;
        p[4][12] = 10;
        p[4][11] = 1;
        p[4][10] = 1;
        p[4][6] = 1;
        p[4][5] = 1;
        p[4][4] = 1;
        p[5][11] = 10;
        p[5][10] = 1;
        p[5][9] = 1;
        p[5][6] = 1;
        p[5][5] = 10;
        p[6][10] = 12;
        p[6][9] = 1;
        p[6][8] = 1;
        p[6][7] = 1;
        p[6][6] = 10;
        p[6][5] = 11;
        p[7][9] = 1;
        p[8][10] = 10;
        p[8][9] = 1;
        p[8][7] = 1;
        p[9][10] = 1;
        p[9][9] = 12;
        p[9][7] = 10;
        p[9][6] = 10;
        p[10][11] = 1;
        p[10][10] = 10;
        p[10][6] = 10;
        p[10][5] = 10;
        p[11][12] = 1;
        p[11][11] = 1;
        p[11][5] = 11;
        p[11][4] = 1;
        p[12][12] = 1;
        p[13][12] = 10;
        return p;
    }

            private byte[][] createHandcuffsPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][7] = 0;
        p[1][8] = 0;
        p[2][6] = 10;
        p[2][7] = 10;
        p[2][8] = 10;
        p[2][9] = 10;
        p[3][6] = 10;
        p[3][7] = 0;
        p[3][8] = 0;
        p[3][9] = 0;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][6] = 0;
        p[4][10] = 0;
        p[4][11] = 10;
        p[5][4] = 0;
        p[5][5] = 10;
        p[5][6] = 0;
        p[5][10] = 0;
        p[5][11] = 10;
        p[6][4] = 0;
        p[6][5] = 11;
        p[6][6] = 11;
        p[6][7] = 0;
        p[6][9] = 0;
        p[6][10] = 10;
        p[6][11] = 11;
        p[6][12] = 0;
        p[7][3] = 0;
        p[7][4] = 11;
        p[7][5] = 11;
        p[7][6] = 11;
        p[7][7] = 0;
        p[7][9] = 0;
        p[7][10] = 11;
        p[7][11] = 11;
        p[7][12] = 10;
        p[7][13] = 0;
        p[8][2] = 0;
        p[8][3] = 10;
        p[8][6] = 10;
        p[8][7] = 10;
        p[8][8] = 0;
        p[8][9] = 10;
        p[8][12] = 10;
        p[8][13] = 11;
        p[8][14] = 0;
        p[9][2] = 0;
        p[9][7] = 10;
        p[9][8] = 10;
        p[9][13] = 11;
        p[9][14] = 0;
        p[10][2] = 0;
        p[10][7] = 10;
        p[10][8] = 10;
        p[10][13] = 10;
        p[10][14] = 0;
        p[11][2] = 0;
        p[11][3] = 10;
        p[11][6] = 10;
        p[11][7] = 10;
        p[11][8] = 10;
        p[11][9] = 10;
        p[11][12] = 10;
        p[11][13] = 0;
        p[12][3] = 0;
        p[12][4] = 10;
        p[12][5] = 10;
        p[12][6] = 0;
        p[12][7] = 0;
        p[12][8] = 0;
        p[12][9] = 0;
        p[12][10] = 10;
        p[12][11] = 10;
        p[12][12] = 10;
        p[12][13] = 0;
        p[13][4] = 0;
        p[13][5] = 0;
        p[13][10] = 0;
        p[13][11] = 0;
        p[13][12] = 0;
        return p;
    }


            private byte[][] createHandcuffsPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][7] = 10;
        p[2][9] = 12;
        p[2][8] = 10;
        p[2][7] = 10;
        p[2][6] = 10;
        p[3][9] = 10;
        p[3][8] = 10;
        p[3][7] = 10;
        p[3][6] = 10;
        p[3][5] = 10;
        p[4][10] = 10;
        p[4][9] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[5][11] = 12;
        p[5][10] = 10;
        p[5][9] = 12;
        p[5][5] = 12;
        p[5][4] = 12;
        p[6][11] = 11;
        p[6][10] = 11;
        p[6][9] = 11;
        p[6][8] = 10;
        p[6][6] = 10;
        p[6][5] = 10;
        p[6][4] = 11;
        p[6][3] = 10;
        p[7][12] = 10;
        p[7][11] = 11;
        p[7][10] = 11;
        p[7][9] = 11;
        p[7][8] = 10;
        p[7][6] = 11;
        p[7][5] = 11;
        p[7][4] = 11;
        p[7][3] = 11;
        p[8][13] = 10;
        p[8][12] = 11;
        p[8][9] = 10;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 10;
        p[8][5] = 10;
        p[8][3] = 10;
        p[8][2] = 11;
        p[9][13] = 11;
        p[9][12] = 10;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][2] = 11;
        p[9][1] = 10;
        p[10][13] = 12;
        p[10][7] = 10;
        p[10][2] = 10;
        p[10][1] = 12;
        p[11][13] = 10;
        p[11][12] = 10;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][2] = 10;
        p[12][12] = 10;
        p[12][11] = 10;
        p[12][10] = 10;
        p[12][9] = 10;
        p[12][8] = 10;
        p[12][7] = 10;
        p[12][6] = 10;
        p[12][5] = 10;
        p[12][4] = 10;
        p[12][3] = 10;
        p[12][2] = 12;
        p[13][11] = 10;
        p[13][10] = 10;
        p[13][5] = 10;
        p[13][4] = 10;
        p[13][3] = 12;
        return p;
    }

            private byte[][] createNightVisionPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[3][1] = 0;
        p[3][2] = 0;
        p[3][13] = 0;
        p[3][14] = 0;
        p[4][1] = 0;
        p[4][2] = 0;
        p[4][13] = 0;
        p[4][14] = 0;
        p[5][1] = 0;
        p[5][2] = 0;
        p[5][13] = 0;
        p[5][14] = 0;
        p[6][1] = 0;
        p[6][2] = 0;
        p[6][13] = 0;
        p[6][14] = 0;
        p[7][1] = 0;
        p[7][2] = 0;
        p[7][13] = 0;
        p[7][14] = 0;
        p[8][1] = 0;
        p[8][2] = 0;
        p[8][3] = 0;
        p[8][4] = 0;
        p[8][5] = 0;
        p[8][6] = 0;
        p[8][7] = 0;
        p[8][8] = 0;
        p[8][9] = 0;
        p[8][10] = 0;
        p[8][11] = 0;
        p[8][12] = 0;
        p[8][13] = 0;
        p[8][14] = 0;
        p[9][1] = 4;
        p[9][2] = 0;
        p[9][3] = 0;
        p[9][4] = 0;
        p[9][5] = 0;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 0;
        p[9][9] = 0;
        p[9][10] = 0;
        p[9][11] = 0;
        p[9][12] = 0;
        p[9][13] = 0;
        p[9][14] = 4;
        p[10][1] = 4;
        p[10][2] = 4;
        p[10][3] = 4;
        p[10][4] = 4;
        p[10][5] = 4;
        p[10][6] = 4;
        p[10][7] = 4;
        p[10][8] = 4;
        p[10][9] = 4;
        p[10][10] = 4;
        p[10][11] = 4;
        p[10][12] = 4;
        p[10][13] = 4;
        p[10][14] = 4;
        p[11][1] = 0;
        p[11][2] = 0;
        p[11][3] = 0;
        p[11][4] = 0;
        p[11][5] = 0;
        p[11][6] = 0;
        p[11][7] = 0;
        p[11][8] = 0;
        p[11][9] = 0;
        p[11][10] = 0;
        p[11][11] = 0;
        p[11][12] = 0;
        p[11][13] = 0;
        p[11][14] = 0;
        return p;
    }


            private byte[][] createNightVisionPattern2() {
        byte[][] p = createEmptyCanvas();
        // 眼镜框 - 黑色
        p[4][13] = 0;
        p[4][14] = 0;
        p[5][1] = 0;
        p[5][2] = 0;
        p[5][13] = 0;
        p[5][14] = 0;
        p[6][1] = 0;
        p[6][2] = 0;
        p[6][13] = 0;
        p[6][14] = 0;
        p[7][1] = 0;
        p[7][2] = 0;
        p[7][13] = 0;
        p[7][14] = 0;
        p[8][1] = 0;
        p[8][2] = 0;
        p[8][13] = 0;
        p[8][14] = 0;
        // 镜腿 - 黑色
        p[9][1] = 0;
        p[9][2] = 0;
        p[9][3] = 0;
        p[9][4] = 0;
        p[9][5] = 0;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 0;
        p[9][9] = 0;
        p[9][10] = 0;
        p[9][11] = 0;
        p[9][12] = 0;
        p[9][13] = 0;
        p[9][14] = 0;
        // 镜片 - 蓝色（用户要求偏向4号蓝色）
        p[5][3] = 4;
        p[5][4] = 4;
        p[5][5] = 4;
        p[5][6] = 4;
        p[5][7] = 4;
        p[5][8] = 4;
        p[5][9] = 4;
        p[5][10] = 4;
        p[5][11] = 4;
        p[5][12] = 4;
        p[6][3] = 4;
        p[6][4] = 4;
        p[6][5] = 4;
        p[6][6] = 4;
        p[6][7] = 4;
        p[6][8] = 4;
        p[6][9] = 4;
        p[6][10] = 4;
        p[6][11] = 4;
        p[6][12] = 4;
        p[7][3] = 4;
        p[7][4] = 4;
        p[7][5] = 4;
        p[7][6] = 4;
        p[7][7] = 4;
        p[7][8] = 4;
        p[7][9] = 4;
        p[7][10] = 4;
        p[7][11] = 4;
        p[7][12] = 4;
        // 下边框 - 黑色
        p[10][1] = 0;
        p[10][2] = 0;
        p[10][3] = 0;
        p[10][4] = 0;
        p[10][5] = 0;
        p[10][6] = 0;
        p[10][7] = 0;
        p[10][8] = 0;
        p[10][9] = 0;
        p[10][10] = 0;
        p[10][11] = 0;
        p[10][12] = 0;
        p[10][13] = 0;
        p[10][14] = 0;
        return p;
    }

            private byte[][] createDivingHelmetPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[3][5] = 7;
        p[3][6] = 7;
        p[3][7] = 7;
        p[3][8] = 7;
        p[3][9] = 7;
        p[3][10] = 7;
        p[4][4] = 7;
        p[4][5] = 1;
        p[4][6] = 10;
        p[4][7] = 7;
        p[4][8] = 7;
        p[4][9] = 10;
        p[4][10] = 7;
        p[4][11] = 7;
        p[5][3] = 7;
        p[5][4] = 1;
        p[5][5] = 7;
        p[5][6] = 10;
        p[5][7] = 7;
        p[5][8] = 7;
        p[5][9] = 10;
        p[5][10] = 7;
        p[5][11] = 7;
        p[5][12] = 7;
        p[6][3] = 7;
        p[6][4] = 7;
        p[6][5] = 7;
        p[6][6] = 10;
        p[6][7] = 10;
        p[6][8] = 10;
        p[6][9] = 10;
        p[6][10] = 7;
        p[6][11] = 7;
        p[6][12] = 7;
        p[7][2] = 7;
        p[7][3] = 7;
        p[7][4] = 7;
        p[7][5] = 7;
        p[7][6] = 7;
        p[7][7] = 7;
        p[7][8] = 7;
        p[7][9] = 7;
        p[7][10] = 7;
        p[7][11] = 7;
        p[7][12] = 7;
        p[7][13] = 7;
        p[8][2] = 7;
        p[8][3] = 0;
        p[8][4] = 7;
        p[8][5] = 7;
        p[8][6] = 0;
        p[8][7] = 0;
        p[8][8] = 0;
        p[8][9] = 0;
        p[8][10] = 7;
        p[8][11] = 7;
        p[8][12] = 0;
        p[8][13] = 7;
        p[9][2] = 7;
        p[9][3] = 0;
        p[9][4] = 7;
        p[9][5] = 7;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 0;
        p[9][9] = 0;
        p[9][10] = 7;
        p[9][11] = 7;
        p[9][12] = 0;
        p[9][13] = 7;
        p[10][2] = 7;
        p[10][3] = 7;
        p[10][4] = 7;
        p[10][5] = 7;
        p[10][6] = 0;
        p[10][7] = 0;
        p[10][8] = 0;
        p[10][9] = 0;
        p[10][10] = 7;
        p[10][11] = 7;
        p[10][12] = 7;
        p[10][13] = 7;
        p[11][4] = 7;
        p[11][5] = 7;
        p[11][6] = 7;
        p[11][7] = 7;
        p[11][8] = 7;
        p[11][9] = 7;
        p[11][10] = 7;
        p[11][11] = 7;
        p[12][6] = 7;
        p[12][7] = 7;
        p[12][8] = 7;
        p[12][9] = 7;
        return p;
    }


            private byte[][] createDivingHelmetPattern2() {
        byte[][] p = createEmptyCanvas();
        // 头盔顶部 - 深绿色（用户要求偏向13号深绿色）
        p[3][10] = 13;
        p[3][9] = 13;
        p[3][8] = 13;
        p[3][7] = 13;
        p[3][6] = 13;
        p[3][5] = 13;
        // 头盔主体
        p[4][11] = 13;
        p[4][10] = 13;
        p[4][9] = 13;
        p[4][8] = 13;
        p[4][7] = 13;
        p[4][6] = 13;
        p[4][5] = 13;
        p[4][4] = 13;
        p[5][12] = 13;
        p[5][11] = 13;
        p[5][10] = 13;
        p[5][9] = 13;
        p[5][8] = 13;
        p[5][7] = 13;
        p[5][6] = 13;
        p[5][5] = 13;
        p[5][4] = 13;
        p[5][3] = 13;
        p[6][12] = 13;
        p[6][11] = 13;
        p[6][10] = 13;
        p[6][9] = 13;
        p[6][8] = 13;
        p[6][7] = 13;
        p[6][6] = 13;
        p[6][5] = 13;
        p[6][4] = 13;
        p[6][3] = 13;
        // 头盔侧面装饰
        p[7][13] = 13;
        p[7][12] = 13;
        p[7][11] = 13;
        p[7][10] = 13;
        p[7][9] = 10;
        p[7][8] = 10;
        p[7][7] = 10;
        p[7][6] = 10;
        p[7][5] = 13;
        p[7][4] = 13;
        p[7][3] = 13;
        p[7][2] = 13;
        // 头盔下半部分
        p[8][13] = 13;
        p[8][11] = 13;
        p[8][10] = 10;
        p[8][5] = 10;
        p[8][4] = 13;
        p[8][3] = 0;
        p[8][2] = 13;
        p[9][13] = 13;
        p[9][12] = 0;
        p[9][11] = 13;
        p[9][10] = 10;
        p[9][9] = 0;
        p[9][8] = 0;
        p[9][7] = 0;
        p[9][6] = 0;
        p[9][5] = 10;
        p[9][4] = 13;
        p[9][3] = 0;
        p[9][2] = 13;
        p[10][13] = 13;
        p[10][12] = 13;
        p[10][11] = 13;
        p[10][10] = 10;
        p[10][9] = 0;
        p[10][8] = 0;
        p[10][7] = 0;
        p[10][6] = 0;
        p[10][5] = 10;
        p[10][4] = 13;
        p[10][3] = 13;
        p[10][2] = 13;
        p[11][11] = 13;
        p[11][10] = 13;
        p[11][9] = 13;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][6] = 13;
        p[11][5] = 13;
        p[11][4] = 13;
        p[12][9] = 13;
        p[12][8] = 13;
        p[12][7] = 13;
        p[12][6] = 13;
        return p;
    }

            private byte[][] createDivingBootsPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[3][2] = 12;
        p[3][3] = 12;
        p[3][4] = 12;
        p[3][5] = 12;
        p[3][6] = 12;
        p[3][9] = 12;
        p[3][10] = 12;
        p[3][11] = 12;
        p[3][12] = 12;
        p[3][13] = 12;
        p[4][2] = 12;
        p[4][3] = 8;
        p[4][4] = 8;
        p[4][5] = 8;
        p[4][6] = 12;
        p[4][9] = 12;
        p[4][10] = 8;
        p[4][11] = 8;
        p[4][12] = 8;
        p[4][13] = 12;
        p[5][3] = 12;
        p[5][4] = 8;
        p[5][5] = 8;
        p[5][6] = 12;
        p[5][9] = 12;
        p[5][10] = 8;
        p[5][11] = 8;
        p[5][12] = 12;
        p[6][3] = 12;
        p[6][4] = 8;
        p[6][5] = 8;
        p[6][6] = 12;
        p[6][9] = 12;
        p[6][10] = 8;
        p[6][11] = 8;
        p[6][12] = 12;
        p[7][3] = 12;
        p[7][4] = 8;
        p[7][5] = 8;
        p[7][6] = 12;
        p[7][9] = 12;
        p[7][10] = 8;
        p[7][11] = 8;
        p[7][12] = 12;
        p[8][3] = 12;
        p[8][4] = 8;
        p[8][5] = 8;
        p[8][6] = 12;
        p[8][9] = 12;
        p[8][10] = 8;
        p[8][11] = 8;
        p[8][12] = 12;
        p[9][2] = 12;
        p[9][3] = 8;
        p[9][4] = 8;
        p[9][5] = 8;
        p[9][6] = 12;
        p[9][9] = 12;
        p[9][10] = 8;
        p[9][11] = 8;
        p[9][12] = 8;
        p[9][13] = 12;
        p[10][1] = 12;
        p[10][2] = 8;
        p[10][3] = 8;
        p[10][4] = 8;
        p[10][5] = 8;
        p[10][6] = 12;
        p[10][9] = 12;
        p[10][10] = 8;
        p[10][11] = 8;
        p[10][12] = 8;
        p[10][13] = 8;
        p[10][14] = 12;
        p[11][1] = 12;
        p[11][2] = 8;
        p[11][3] = 8;
        p[11][4] = 8;
        p[11][5] = 8;
        p[11][6] = 12;
        p[11][9] = 12;
        p[11][10] = 8;
        p[11][11] = 8;
        p[11][12] = 8;
        p[11][13] = 8;
        p[11][14] = 12;
        p[12][1] = 12;
        p[12][2] = 12;
        p[12][3] = 12;
        p[12][4] = 12;
        p[12][5] = 12;
        p[12][6] = 12;
        p[12][9] = 12;
        p[12][10] = 12;
        p[12][11] = 12;
        p[12][12] = 12;
        p[12][13] = 12;
        p[12][14] = 12;
        return p;
    }


            private byte[][] createDivingBootsPattern2() {
        byte[][] p = createEmptyCanvas();
        p[3][13] = 12;
        p[3][12] = 12;
        p[3][11] = 12;
        p[3][10] = 12;
        p[3][9] = 12;
        p[3][6] = 12;
        p[3][5] = 12;
        p[3][4] = 12;
        p[3][3] = 12;
        p[4][13] = 12;
        p[4][12] = 10;
        p[4][11] = 15;
        p[4][10] = 8;
        p[4][6] = 12;
        p[4][5] = 10;
        p[4][4] = 8;
        p[4][3] = 8;
        p[5][12] = 12;
        p[5][11] = 12;
        p[5][10] = 12;
        p[5][6] = 12;
        p[5][5] = 12;
        p[5][4] = 12;
        p[6][12] = 12;
        p[6][11] = 8;
        p[6][10] = 12;
        p[6][6] = 12;
        p[6][5] = 8;
        p[6][4] = 12;
        p[7][12] = 12;
        p[7][11] = 8;
        p[7][10] = 12;
        p[7][6] = 12;
        p[7][5] = 8;
        p[7][4] = 12;
        p[8][12] = 12;
        p[8][11] = 12;
        p[8][10] = 12;
        p[8][6] = 12;
        p[8][5] = 12;
        p[8][4] = 12;
        p[9][13] = 12;
        p[9][12] = 12;
        p[9][11] = 12;
        p[9][10] = 12;
        p[9][6] = 12;
        p[9][5] = 12;
        p[9][4] = 12;
        p[9][3] = 12;
        p[10][14] = 12;
        p[10][13] = 8;
        p[10][12] = 15;
        p[10][11] = 8;
        p[10][10] = 12;
        p[10][6] = 12;
        p[10][5] = 12;
        p[10][4] = 8;
        p[10][3] = 8;
        p[10][2] = 8;
        p[11][14] = 12;
        p[11][13] = 12;
        p[11][12] = 12;
        p[11][11] = 12;
        p[11][10] = 12;
        p[11][5] = 12;
        p[11][4] = 12;
        p[11][3] = 12;
        p[11][2] = 12;
        p[12][14] = 12;
        return p;
    }

                private byte[][] createMasterKeyPPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][7] = 15;
        p[1][8] = 15;
        p[2][6] = 15;
        p[2][7] = 8;
        p[2][8] = 8;
        p[2][9] = 15;
        p[3][5] = 15;
        p[3][6] = 8;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 8;
        p[3][10] = 15;
        p[4][5] = 15;
        p[4][6] = 8;
        p[4][7] = 15;
        p[4][8] = 11;
        p[4][9] = 15;
        p[4][10] = 8;
        p[4][11] = 15;
        p[5][6] = 15;
        p[5][7] = 11;
        p[5][8] = 15;
        p[5][9] = 15;
        p[5][10] = 8;
        p[5][11] = 15;
        p[6][5] = 15;
        p[6][6] = 8;
        p[6][7] = 11;
        p[6][8] = 8;
        p[6][9] = 8;
        p[6][10] = 15;
        p[6][11] = 11;
        p[7][4] = 15;
        p[7][5] = 8;
        p[7][6] = 15;
        p[7][7] = 11;
        p[7][8] = 15;
        p[7][9] = 15;
        p[7][11] = 11;
        p[8][3] = 15;
        p[8][4] = 8;
        p[8][5] = 15;
        p[8][8] = 11;
        p[8][9] = 11;
        p[8][10] = 11;
        p[8][11] = 15;
        p[8][12] = 15;
        p[8][13] = 15;
        p[9][2] = 15;
        p[9][3] = 8;
        p[9][4] = 15;
        p[9][10] = 15;
        p[9][11] = 8;
        p[9][12] = 8;
        p[9][13] = 8;
        p[9][14] = 15;
        p[10][1] = 15;
        p[10][2] = 8;
        p[10][3] = 8;
        p[10][4] = 15;
        p[10][10] = 15;
        p[10][11] = 8;
        p[10][12] = 15;
        p[10][13] = 15;
        p[10][14] = 8;
        p[10][15] = 15;
        p[11][1] = 15;
        p[11][2] = 15;
        p[11][3] = 15;
        p[11][4] = 8;
        p[11][5] = 15;
        p[11][10] = 15;
        p[11][11] = 8;
        p[11][12] = 8;
        p[11][13] = 15;
        p[11][14] = 8;
        p[11][15] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[12][11] = 15;
        p[12][12] = 8;
        p[12][13] = 8;
        p[12][14] = 8;
        p[12][15] = 15;
        p[13][12] = 15;
        p[13][13] = 15;
        p[13][14] = 15;
        return p;
    }


                private byte[][] createMasterKeyPPattern2() {
        
        byte[][] p = createEmptyCanvas();
        p[1][7] = 15;
        p[1][8] = 15;
        p[2][6] = 15;
        p[2][7] = 5;
        p[2][8] = 5;
        p[2][9] = 15;
        p[3][5] = 15;
        p[3][6] = 5;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 5;
        p[3][10] = 15;
        p[4][5] = 15;
        p[4][6] = 5;
        p[4][7] = 15;
        p[4][8] = 11;
        p[4][9] = 15;
        p[4][10] = 5;
        p[4][11] = 15;
        p[5][6] = 15;
        p[5][7] = 11;
        p[5][8] = 15;
        p[5][9] = 15;
        p[5][10] = 5;
        p[5][11] = 15;
        p[6][5] = 15;
        p[6][6] = 5;
        p[6][7] = 11;
        p[6][8] = 5;
        p[6][9] = 5;
        p[6][10] = 15;
        p[6][11] = 11;
        p[7][4] = 15;
        p[7][5] = 5;
        p[7][6] = 15;
        p[7][7] = 11;
        p[7][8] = 15;
        p[7][9] = 15;
        p[7][11] = 11;
        p[8][3] = 15;
        p[8][4] = 5;
        p[8][5] = 15;
        p[8][8] = 11;
        p[8][9] = 11;
        p[8][10] = 11;
        p[8][11] = 15;
        p[8][12] = 15;
        p[8][13] = 15;
        p[9][2] = 15;
        p[9][3] = 5;
        p[9][4] = 15;
        p[9][10] = 15;
        p[9][11] = 5;
        p[9][12] = 5;
        p[9][13] = 5;
        p[9][14] = 15;
        p[10][1] = 15;
        p[10][2] = 5;
        p[10][3] = 5;
        p[10][4] = 15;
        p[10][10] = 15;
        p[10][11] = 5;
        p[10][12] = 15;
        p[10][13] = 15;
        p[10][14] = 5;
        p[10][15] = 15;
        p[11][1] = 15;
        p[11][2] = 15;
        p[11][3] = 15;
        p[11][4] = 5;
        p[11][5] = 15;
        p[11][10] = 15;
        p[11][11] = 5;
        p[11][12] = 5;
        p[11][13] = 15;
        p[11][14] = 5;
        p[11][15] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[12][11] = 15;
        p[12][12] = 5;
        p[12][13] = 5;
        p[12][14] = 5;
        p[12][15] = 15;
        p[13][12] = 15;
        p[13][13] = 15;
        p[13][14] = 15;
        return p;
    }


            private byte[][] createDefibrillatorPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][3] = 0;
        p[1][4] = 0;
        p[1][10] = 10;
        p[1][11] = 10;
        p[2][2] = 0;
        p[2][5] = 0;
        p[2][8] = 10;
        p[2][9] = 10;
        p[2][12] = 10;
        p[3][1] = 0;
        p[3][2] = 0;
        p[3][6] = 10;
        p[3][7] = 10;
        p[3][13] = 10;
        p[4][1] = 0;
        p[4][2] = 0;
        p[4][5] = 0;
        p[4][13] = 10;
        p[5][1] = 0;
        p[5][2] = 0;
        p[5][5] = 0;
        p[5][12] = 10;
        p[6][4] = 0;
        p[6][11] = 10;
        p[7][4] = 0;
        p[7][9] = 0;
        p[7][10] = 0;
        p[7][11] = 0;
        p[7][12] = 0;
        p[8][3] = 0;
        p[8][8] = 0;
        p[8][9] = 10;
        p[8][10] = 10;
        p[8][11] = 10;
        p[8][12] = 10;
        p[8][13] = 0;
        p[9][2] = 10;
        p[9][3] = 0;
        p[9][7] = 0;
        p[9][8] = 10;
        p[9][9] = 1;
        p[9][10] = 10;
        p[9][11] = 10;
        p[9][12] = 10;
        p[9][13] = 10;
        p[9][14] = 0;
        p[10][2] = 0;
        p[10][3] = 0;
        p[10][7] = 0;
        p[10][8] = 1;
        p[10][9] = 10;
        p[10][10] = 0;
        p[10][11] = 0;
        p[10][12] = 10;
        p[10][13] = 10;
        p[10][14] = 0;
        p[11][2] = 0;
        p[11][3] = 0;
        p[11][7] = 0;
        p[11][8] = 10;
        p[11][9] = 10;
        p[11][10] = 0;
        p[11][11] = 0;
        p[11][12] = 10;
        p[11][13] = 10;
        p[11][14] = 0;
        p[12][7] = 0;
        p[12][8] = 10;
        p[12][9] = 10;
        p[12][10] = 10;
        p[12][11] = 10;
        p[12][12] = 10;
        p[12][13] = 10;
        p[12][14] = 0;
        p[13][8] = 0;
        p[13][9] = 10;
        p[13][10] = 10;
        p[13][11] = 10;
        p[13][12] = 10;
        p[13][13] = 0;
        p[14][9] = 0;
        p[14][10] = 0;
        p[14][11] = 0;
        p[14][12] = 0;
        return p;
    }


            private byte[][] createDefibrillatorPattern2() {
        byte[][] p = createEmptyCanvas();
        // 心脏起搏器主体 - 整体偏灰
        p[1][11] = 10;
        p[1][5] = 10;
        p[1][4] = 10;
        p[2][13] = 10;
        p[2][7] = 10;
        p[2][6] = 10;
        p[3][14] = 10;
        p[3][9] = 10;
        p[3][8] = 10;
        p[3][2] = 10;
        p[4][14] = 10;
        p[4][2] = 10;
        p[5][10] = 10;
        p[6][11] = 10;
        p[7][11] = 10;
        p[7][3] = 10;
        p[8][12] = 10;
        p[8][6] = 10;
        p[8][5] = 10;
        p[8][4] = 10;
        p[8][3] = 10;
        p[8][2] = 10;
        p[9][13] = 10;
        p[9][12] = 10;
        p[9][7] = 10;
        p[9][6] = 1;
        p[9][5] = 10;
        p[9][4] = 10;
        p[9][3] = 10;
        p[9][2] = 10;
        p[9][1] = 10;
        p[10][12] = 10;
        p[10][7] = 1;
        p[10][6] = 10;
        p[10][3] = 10;
        p[10][2] = 10;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][6] = 10;
        p[11][3] = 10;
        p[11][2] = 10;
        p[11][1] = 10;
        p[12][7] = 10;
        p[12][6] = 10;
        p[12][5] = 10;
        p[12][4] = 10;
        p[12][3] = 10;
        p[12][2] = 10;
        p[13][6] = 10;
        p[13][5] = 10;
        p[13][4] = 10;
        p[13][3] = 10;
        p[13][2] = 10;
        p[14][6] = 10;
        p[14][5] = 10;
        p[14][3] = 10;
        return p;
    }

            private byte[][] createBoxingGlovePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][5] = 15;
        p[2][6] = 15;
        p[2][7] = 15;
        p[3][3] = 15;
        p[3][4] = 15;
        p[3][5] = 8;
        p[3][6] = 8;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 15;
        p[4][2] = 15;
        p[4][3] = 15;
        p[4][4] = 8;
        p[4][5] = 8;
        p[4][6] = 15;
        p[4][7] = 8;
        p[4][8] = 8;
        p[4][9] = 8;
        p[4][10] = 15;
        p[5][2] = 15;
        p[5][3] = 8;
        p[5][4] = 8;
        p[5][5] = 15;
        p[5][6] = 8;
        p[5][7] = 8;
        p[5][8] = 8;
        p[5][9] = 8;
        p[5][10] = 8;
        p[5][11] = 15;
        p[6][2] = 15;
        p[6][3] = 8;
        p[6][4] = 15;
        p[6][5] = 8;
        p[6][6] = 8;
        p[6][7] = 8;
        p[6][8] = 8;
        p[6][9] = 8;
        p[6][10] = 8;
        p[6][11] = 8;
        p[6][12] = 15;
        p[7][3] = 15;
        p[7][4] = 15;
        p[7][5] = 8;
        p[7][6] = 8;
        p[7][7] = 8;
        p[7][8] = 8;
        p[7][9] = 8;
        p[7][10] = 8;
        p[7][11] = 8;
        p[7][12] = 12;
        p[7][13] = 15;
        p[8][4] = 15;
        p[8][5] = 12;
        p[8][6] = 8;
        p[8][7] = 12;
        p[8][8] = 8;
        p[8][9] = 8;
        p[8][10] = 12;
        p[8][11] = 15;
        p[8][12] = 8;
        p[8][13] = 8;
        p[8][14] = 15;
        p[9][4] = 15;
        p[9][5] = 12;
        p[9][6] = 15;
        p[9][7] = 12;
        p[9][8] = 8;
        p[9][9] = 15;
        p[9][10] = 8;
        p[9][11] = 12;
        p[9][12] = 15;
        p[9][13] = 8;
        p[9][14] = 15;
        p[10][3] = 15;
        p[10][4] = 12;
        p[10][5] = 15;
        p[10][7] = 15;
        p[10][8] = 8;
        p[10][9] = 8;
        p[10][10] = 15;
        p[10][11] = 8;
        p[10][12] = 12;
        p[10][13] = 8;
        p[10][14] = 15;
        p[11][3] = 15;
        p[11][4] = 15;
        p[11][8] = 15;
        p[11][9] = 8;
        p[11][10] = 8;
        p[11][11] = 15;
        p[11][12] = 12;
        p[11][13] = 15;
        p[11][14] = 15;
        p[12][8] = 15;
        p[12][9] = 8;
        p[12][10] = 15;
        p[12][11] = 8;
        p[12][12] = 15;
        p[12][13] = 12;
        p[12][14] = 15;
        p[13][9] = 15;
        p[13][10] = 15;
        p[13][11] = 15;
        p[13][12] = 15;
        p[13][13] = 15;
        return p;
    }


            private byte[][] createBoxingGlovePattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][10] = 8;
        p[2][9] = 8;
        p[2][8] = 12;
        p[3][12] = 8;
        p[3][11] = 8;
        p[3][10] = 12;
        p[3][9] = 12;
        p[3][8] = 8;
        p[3][7] = 8;
        p[3][6] = 12;
        p[4][13] = 13;
        p[4][12] = 8;
        p[4][10] = 8;
        p[4][9] = 8;
        p[4][8] = 13;
        p[4][7] = 13;
        p[4][6] = 13;
        p[4][5] = 8;
        p[5][13] = 8;
        p[5][12] = 12;
        p[5][11] = 8;
        p[5][10] = 12;
        p[5][9] = 8;
        p[5][8] = 8;
        p[5][7] = 13;
        p[5][6] = 13;
        p[5][5] = 13;
        p[5][4] = 8;
        p[6][13] = 12;
        p[6][12] = 12;
        p[6][11] = 12;
        p[6][10] = 8;
        p[6][9] = 8;
        p[6][8] = 13;
        p[6][7] = 13;
        p[6][6] = 13;
        p[6][5] = 8;
        p[6][4] = 8;
        p[6][3] = 13;
        p[7][12] = 12;
        p[7][11] = 12;
        p[7][10] = 8;
        p[7][9] = 8;
        p[7][8] = 8;
        p[7][7] = 8;
        p[7][6] = 8;
        p[7][5] = 8;
        p[7][4] = 13;
        p[7][3] = 12;
        p[7][2] = 8;
        p[8][11] = 8;
        p[8][10] = 12;
        p[8][9] = 8;
        p[8][8] = 12;
        p[8][7] = 8;
        p[8][6] = 13;
        p[8][5] = 12;
        p[8][4] = 12;
        p[8][3] = 8;
        p[8][1] = 12;
        p[9][11] = 8;
        p[9][10] = 12;
        p[9][9] = 12;
        p[9][8] = 12;
        p[9][7] = 12;
        p[9][6] = 12;
        p[9][5] = 13;
        p[9][4] = 12;
        p[9][3] = 12;
        p[9][2] = 8;
        p[9][1] = 12;
        p[10][12] = 8;
        p[10][11] = 12;
        p[10][10] = 12;
        p[10][8] = 12;
        p[10][7] = 8;
        p[10][5] = 12;
        p[10][4] = 8;
        p[10][3] = 12;
        p[10][2] = 12;
        p[10][1] = 12;
        p[11][12] = 12;
        p[11][11] = 12;
        p[11][7] = 12;
        p[11][6] = 12;
        p[11][5] = 12;
        p[11][4] = 12;
        p[11][3] = 12;
        p[11][2] = 12;
        p[11][1] = 12;
        p[12][7] = 12;
        p[12][6] = 12;
        p[12][5] = 12;
        p[12][3] = 12;
        p[12][2] = 12;
        p[12][1] = 12;
        p[13][8] = 12;
        p[13][7] = 12;
        p[13][6] = 12;
        p[13][5] = 12;
        p[13][4] = 12;
        p[13][3] = 12;
        p[13][2] = 12;
        return p;
    }

            private byte[][] createAntidoteReagentPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][6] = 15;
        p[2][7] = 15;
        p[2][8] = 15;
        p[2][9] = 15;
        p[3][5] = 10;
        p[3][6] = 15;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 15;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][10] = 10;
        p[5][6] = 10;
        p[5][7] = 15;
        p[5][8] = 15;
        p[5][9] = 10;
        p[6][6] = 10;
        p[6][9] = 10;
        p[7][6] = 10;
        p[7][7] = 9;
        p[7][8] = 9;
        p[7][9] = 10;
        p[8][6] = 10;
        p[8][7] = 9;
        p[8][8] = 9;
        p[8][9] = 10;
        p[9][6] = 10;
        p[9][7] = 9;
        p[9][8] = 9;
        p[9][9] = 10;
        p[10][6] = 10;
        p[10][7] = 9;
        p[10][8] = 9;
        p[10][9] = 10;
        p[11][6] = 10;
        p[11][7] = 9;
        p[11][8] = 9;
        p[11][9] = 10;
        p[12][6] = 10;
        p[12][7] = 9;
        p[12][8] = 9;
        p[12][9] = 10;
        p[13][6] = 10;
        p[13][7] = 9;
        p[13][8] = 9;
        p[13][9] = 10;
        p[14][7] = 10;
        p[14][8] = 10;
        return p;
    }


            private byte[][] createAntidoteReagentPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][9] = 10;
        p[2][8] = 10;
        p[2][7] = 10;
        p[2][6] = 10;
        p[3][10] = 10;
        p[3][9] = 12;
        p[3][8] = 12;
        p[3][7] = 12;
        p[3][6] = 12;
        p[3][5] = 10;
        p[4][10] = 11;
        p[4][9] = 11;
        p[4][8] = 11;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[5][9] = 10;
        p[5][8] = 12;
        p[5][7] = 12;
        p[6][9] = 10;
        p[6][6] = 10;
        p[7][9] = 10;
        p[7][8] = 9;
        p[7][7] = 9;
        p[7][6] = 10;
        p[8][9] = 10;
        p[8][7] = 10;
        p[8][6] = 11;
        p[9][9] = 11;
        p[9][8] = 10;
        p[9][6] = 11;
        p[10][9] = 11;
        p[10][8] = 12;
        p[10][7] = 10;
        p[10][6] = 11;
        p[11][9] = 11;
        p[11][8] = 12;
        p[11][7] = 12;
        p[11][6] = 10;
        p[12][9] = 11;
        p[12][8] = 12;
        p[12][6] = 10;
        p[13][9] = 10;
        p[13][6] = 10;
        p[14][8] = 10;
        p[14][7] = 10;
        return p;
    }

            private byte[][] createPoisonVialPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][6] = 15;
        p[2][7] = 15;
        p[2][8] = 15;
        p[2][9] = 15;
        p[3][5] = 10;
        p[3][6] = 15;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 15;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][10] = 10;
        p[5][6] = 10;
        p[5][7] = 15;
        p[5][8] = 15;
        p[5][9] = 10;
        p[6][6] = 10;
        p[6][9] = 10;
        p[7][6] = 10;
        p[7][7] = 2;
        p[7][8] = 2;
        p[7][9] = 10;
        p[8][6] = 10;
        p[8][7] = 2;
        p[8][8] = 2;
        p[8][9] = 10;
        p[9][6] = 10;
        p[9][7] = 2;
        p[9][8] = 2;
        p[9][9] = 10;
        p[10][6] = 10;
        p[10][7] = 2;
        p[10][8] = 2;
        p[10][9] = 10;
        p[11][6] = 10;
        p[11][7] = 2;
        p[11][8] = 2;
        p[11][9] = 10;
        p[12][6] = 10;
        p[12][7] = 2;
        p[12][8] = 2;
        p[12][9] = 10;
        p[13][6] = 10;
        p[13][7] = 2;
        p[13][8] = 2;
        p[13][9] = 10;
        p[14][7] = 10;
        p[14][8] = 10;
        return p;
    }


            private byte[][] createSmokeGrenadePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][7] = 15;
        p[0][8] = 15;
        p[0][9] = 15;
        p[0][10] = 15;
        p[0][11] = 15;
        p[1][6] = 15;
        p[1][7] = 15;
        p[1][8] = 10;
        p[1][9] = 5;
        p[1][10] = 15;
        p[1][11] = 15;
        p[1][12] = 15;
        p[2][5] = 15;
        p[2][6] = 15;
        p[2][7] = 10;
        p[2][8] = 15;
        p[2][9] = 5;
        p[2][10] = 5;
        p[2][11] = 15;
        p[2][12] = 15;
        p[2][13] = 15;
        p[3][5] = 15;
        p[3][6] = 11;
        p[3][7] = 15;
        p[3][8] = 15;
        p[3][9] = 15;
        p[3][10] = 5;
        p[3][11] = 10;
        p[3][14] = 15;
        p[4][4] = 15;
        p[4][5] = 15;
        p[4][6] = 11;
        p[4][7] = 15;
        p[4][8] = 15;
        p[4][9] = 15;
        p[4][10] = 15;
        p[4][11] = 10;
        p[4][14] = 15;
        p[5][3] = 15;
        p[5][4] = 15;
        p[5][5] = 11;
        p[5][6] = 15;
        p[5][7] = 15;
        p[5][8] = 15;
        p[5][9] = 15;
        p[5][10] = 5;
        p[5][11] = 15;
        p[5][12] = 15;
        p[5][13] = 15;
        p[6][3] = 15;
        p[6][4] = 11;
        p[6][5] = 15;
        p[6][6] = 15;
        p[6][7] = 15;
        p[6][8] = 15;
        p[6][9] = 5;
        p[6][10] = 15;
        p[6][11] = 15;
        p[7][2] = 15;
        p[7][3] = 15;
        p[7][4] = 11;
        p[7][5] = 15;
        p[7][6] = 15;
        p[7][7] = 15;
        p[7][8] = 5;
        p[7][9] = 15;
        p[7][10] = 15;
        p[8][2] = 15;
        p[8][3] = 10;
        p[8][4] = 15;
        p[8][5] = 15;
        p[8][6] = 15;
        p[8][7] = 15;
        p[8][8] = 5;
        p[8][9] = 15;
        p[9][3] = 15;
        p[9][4] = 15;
        p[9][5] = 15;
        p[9][6] = 15;
        p[9][7] = 5;
        p[9][8] = 15;
        p[9][9] = 15;
        p[10][2] = 15;
        p[10][3] = 10;
        p[10][4] = 10;
        p[10][5] = 15;
        p[10][6] = 15;
        p[10][7] = 15;
        p[10][8] = 15;
        p[11][2] = 15;
        p[11][3] = 15;
        p[11][4] = 10;
        p[11][5] = 11;
        p[11][6] = 11;
        p[11][7] = 15;
        p[12][2] = 15;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[12][6] = 10;
        p[12][7] = 10;
        p[13][3] = 15;
        p[13][4] = 15;
        p[13][5] = 15;
        p[13][6] = 15;
        p[14][4] = 15;
        p[14][5] = 15;
        return p;
    }


            private byte[][] createSmokeGrenadePattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][7] = 12;
        p[0][6] = 12;
        p[1][8] = 10;
        p[1][7] = 10;
        p[1][6] = 12;
        p[1][5] = 12;
        p[1][4] = 12;
        p[1][3] = 12;
        p[2][9] = 12;
        p[2][8] = 10;
        p[2][7] = 12;
        p[2][6] = 12;
        p[2][5] = 12;
        p[2][4] = 12;
        p[2][3] = 12;
        p[3][9] = 12;
        p[3][8] = 12;
        p[3][7] = 12;
        p[3][6] = 12;
        p[3][5] = 10;
        p[3][4] = 10;
        p[4][10] = 12;
        p[4][9] = 12;
        p[4][8] = 12;
        p[4][7] = 12;
        p[4][6] = 12;
        p[4][5] = 12;
        p[4][4] = 10;
        p[4][3] = 12;
        p[5][10] = 12;
        p[5][9] = 12;
        p[5][8] = 12;
        p[5][7] = 12;
        p[5][6] = 12;
        p[5][5] = 10;
        p[5][4] = 12;
        p[5][3] = 12;
        p[5][2] = 12;
        p[6][11] = 12;
        p[6][10] = 12;
        p[6][9] = 12;
        p[6][8] = 12;
        p[6][7] = 12;
        p[6][6] = 10;
        p[6][5] = 12;
        p[6][4] = 12;
        p[7][11] = 12;
        p[7][10] = 12;
        p[7][9] = 12;
        p[7][8] = 12;
        p[7][7] = 10;
        p[7][6] = 12;
        p[7][5] = 12;
        p[8][12] = 12;
        p[8][11] = 12;
        p[8][10] = 12;
        p[8][9] = 12;
        p[8][8] = 12;
        p[8][7] = 10;
        p[8][6] = 12;
        p[9][12] = 12;
        p[9][11] = 12;
        p[9][10] = 12;
        p[9][9] = 12;
        p[9][8] = 10;
        p[9][7] = 12;
        p[9][6] = 12;
        p[10][12] = 10;
        p[10][11] = 10;
        p[10][10] = 12;
        p[10][9] = 12;
        p[10][8] = 12;
        p[10][7] = 12;
        p[11][12] = 12;
        p[11][11] = 10;
        p[11][10] = 11;
        p[11][9] = 11;
        p[11][8] = 12;
        p[12][13] = 12;
        p[12][12] = 12;
        p[12][11] = 12;
        p[12][10] = 12;
        p[12][9] = 10;
        p[12][8] = 10;
        p[13][12] = 12;
        p[13][11] = 12;
        p[13][10] = 10;
        p[13][9] = 12;
        p[14][11] = 15;
        p[14][10] = 12;
        return p;
    }

            private byte[][] createFlashGrenadePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][11] = 0;
        p[2][12] = 0;
        p[3][10] = 0;
        p[3][11] = 0;
        p[3][12] = 0;
        p[3][13] = 0;
        p[4][8] = 15;
        p[4][9] = 15;
        p[4][10] = 15;
        p[4][11] = 0;
        p[4][14] = 0;
        p[5][7] = 7;
        p[5][8] = 15;
        p[5][9] = 15;
        p[5][10] = 15;
        p[5][11] = 15;
        p[5][13] = 0;
        p[5][14] = 0;
        p[6][6] = 7;
        p[6][7] = 7;
        p[6][8] = 7;
        p[6][9] = 15;
        p[6][10] = 15;
        p[6][11] = 15;
        p[6][13] = 0;
        p[7][5] = 7;
        p[7][6] = 7;
        p[7][7] = 7;
        p[7][8] = 7;
        p[7][9] = 7;
        p[7][10] = 15;
        p[7][12] = 0;
        p[8][4] = 15;
        p[8][5] = 15;
        p[8][6] = 7;
        p[8][7] = 7;
        p[8][8] = 7;
        p[8][9] = 7;
        p[9][3] = 15;
        p[9][4] = 15;
        p[9][5] = 15;
        p[9][6] = 15;
        p[9][7] = 7;
        p[9][8] = 7;
        p[10][2] = 15;
        p[10][3] = 15;
        p[10][4] = 15;
        p[10][5] = 15;
        p[10][6] = 15;
        p[10][7] = 15;
        p[11][2] = 15;
        p[11][3] = 15;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 15;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[13][4] = 15;
        return p;
    }


            private byte[][] createFlashGrenadePattern2() {
        byte[][] p = createEmptyCanvas();
        p[3][4] = 12;
        p[4][6] = 10;
        p[4][5] = 12;
        p[5][8] = 10;
        p[5][7] = 12;
        p[5][6] = 12;
        p[5][5] = 10;
        p[5][4] = 12;
        p[6][9] = 12;
        p[6][8] = 10;
        p[6][7] = 11;
        p[6][6] = 12;
        p[6][5] = 12;
        p[6][4] = 12;
        p[7][10] = 12;
        p[7][9] = 10;
        p[7][8] = 11;
        p[7][7] = 11;
        p[7][6] = 10;
        p[8][11] = 12;
        p[8][10] = 11;
        p[8][9] = 11;
        p[8][8] = 11;
        p[8][7] = 10;
        p[8][6] = 10;
        p[9][12] = 12;
        p[9][11] = 12;
        p[9][10] = 10;
        p[9][9] = 10;
        p[9][8] = 10;
        p[9][7] = 10;
        p[10][12] = 12;
        p[10][11] = 10;
        p[10][10] = 12;
        p[10][9] = 12;
        p[10][8] = 12;
        p[11][11] = 12;
        p[11][10] = 10;
        return p;
    }

            private byte[][] createRepairToolPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][10] = 10;
        p[2][11] = 10;
        p[3][9] = 10;
        p[3][10] = 10;
        p[4][9] = 10;
        p[4][13] = 10;
        p[5][9] = 10;
        p[5][10] = 10;
        p[5][12] = 10;
        p[5][13] = 10;
        p[6][8] = 15;
        p[6][9] = 10;
        p[6][10] = 10;
        p[6][11] = 10;
        p[6][12] = 10;
        p[7][7] = 10;
        p[7][8] = 15;
        p[7][9] = 15;
        p[8][6] = 10;
        p[8][7] = 10;
        p[8][8] = 10;
        p[9][5] = 15;
        p[9][6] = 15;
        p[9][7] = 10;
        p[10][2] = 10;
        p[10][3] = 10;
        p[10][4] = 10;
        p[10][5] = 10;
        p[10][6] = 15;
        p[11][1] = 10;
        p[11][2] = 10;
        p[11][4] = 10;
        p[11][5] = 10;
        p[12][1] = 10;
        p[12][5] = 10;
        p[13][4] = 10;
        p[13][5] = 10;
        p[14][3] = 10;
        p[14][4] = 10;
        return p;
    }


            private byte[][] createRepairToolPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][5] = 10;
        p[2][4] = 10;
        p[3][6] = 10;
        p[3][5] = 10;
        p[3][2] = 10;
        p[4][5] = 12;
        p[4][2] = 10;
        p[4][1] = 10;
        p[5][5] = 12;
        p[5][3] = 12;
        p[5][2] = 10;
        p[6][6] = 10;
        p[6][3] = 10;
        p[7][8] = 12;
        p[8][9] = 12;
        p[8][8] = 12;
        p[9][10] = 12;
        p[9][9] = 12;
        p[10][13] = 10;
        p[10][12] = 12;
        p[10][10] = 12;
        p[11][14] = 10;
        p[11][13] = 10;
        p[11][11] = 12;
        p[12][14] = 10;
        p[12][11] = 10;
        p[12][10] = 12;
        p[13][12] = 12;
        p[13][11] = 10;
        p[14][12] = 12;
        return p;
    }

            private byte[][] createScrewdriverPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][13] = 11;
        p[1][14] = 11;
        p[2][12] = 11;
        p[2][13] = 10;
        p[2][14] = 10;
        p[3][11] = 11;
        p[3][12] = 10;
        p[3][13] = 10;
        p[4][10] = 11;
        p[4][11] = 10;
        p[4][12] = 10;
        p[5][9] = 11;
        p[5][10] = 10;
        p[5][11] = 10;
        p[6][8] = 11;
        p[6][9] = 10;
        p[6][10] = 10;
        p[7][5] = 15;
        p[7][6] = 15;
        p[7][7] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[8][5] = 15;
        p[8][6] = 15;
        p[8][7] = 15;
        p[8][8] = 10;
        p[9][4] = 15;
        p[9][5] = 15;
        p[9][6] = 15;
        p[9][7] = 15;
        p[9][8] = 15;
        p[10][3] = 15;
        p[10][4] = 15;
        p[10][5] = 15;
        p[10][6] = 15;
        p[10][7] = 15;
        p[10][8] = 15;
        p[11][2] = 15;
        p[11][3] = 15;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 15;
        p[12][1] = 15;
        p[12][2] = 15;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[13][1] = 15;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][4] = 15;
        p[14][2] = 15;
        p[14][3] = 15;
        return p;
    }


            private byte[][] createScrewdriverPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][2] = 11;
        p[1][1] = 11;
        p[2][3] = 11;
        p[2][2] = 10;
        p[2][1] = 10;
        p[3][4] = 11;
        p[3][3] = 10;
        p[3][2] = 10;
        p[4][5] = 11;
        p[4][4] = 10;
        p[4][3] = 10;
        p[5][6] = 11;
        p[5][5] = 10;
        p[5][4] = 10;
        p[6][7] = 11;
        p[6][6] = 10;
        p[6][5] = 10;
        p[7][10] = 12;
        p[7][9] = 12;
        p[7][8] = 10;
        p[7][7] = 10;
        p[7][6] = 10;
        p[8][10] = 12;
        p[8][9] = 12;
        p[8][8] = 12;
        p[8][7] = 10;
        p[9][11] = 12;
        p[9][10] = 12;
        p[9][9] = 12;
        p[9][8] = 12;
        p[9][7] = 12;
        p[10][12] = 12;
        p[10][11] = 12;
        p[10][10] = 12;
        p[10][9] = 12;
        p[10][8] = 12;
        p[10][7] = 12;
        p[11][13] = 12;
        p[11][12] = 12;
        p[11][11] = 12;
        p[11][10] = 12;
        p[11][9] = 12;
        p[12][14] = 12;
        p[12][13] = 12;
        p[12][12] = 12;
        p[12][11] = 12;
        p[12][10] = 12;
        p[13][14] = 12;
        p[13][13] = 12;
        p[13][12] = 12;
        p[13][11] = 12;
        p[14][13] = 12;
        p[14][12] = 12;
        return p;
    }

            private byte[][] createAlarmTrapPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[8][5] = 2;
        p[8][6] = 2;
        p[8][7] = 2;
        p[8][8] = 2;
        p[8][9] = 2;
        p[8][10] = 2;
        p[9][4] = 2;
        p[9][5] = 2;
        p[9][6] = 2;
        p[9][7] = 2;
        p[9][8] = 2;
        p[9][9] = 2;
        p[9][10] = 2;
        p[9][11] = 2;
        p[10][3] = 2;
        p[10][4] = 2;
        p[10][5] = 2;
        p[10][6] = 2;
        p[10][7] = 2;
        p[10][8] = 2;
        p[10][9] = 2;
        p[10][10] = 2;
        p[10][11] = 2;
        p[10][12] = 2;
        p[11][1] = 15;
        p[11][2] = 15;
        p[11][3] = 15;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 15;
        p[11][7] = 15;
        p[11][8] = 15;
        p[11][9] = 15;
        p[11][10] = 15;
        p[11][11] = 15;
        p[11][12] = 15;
        p[11][13] = 15;
        p[11][14] = 15;
        p[12][1] = 15;
        p[12][2] = 10;
        p[12][3] = 10;
        p[12][4] = 10;
        p[12][5] = 10;
        p[12][6] = 10;
        p[12][7] = 10;
        p[12][8] = 10;
        p[12][9] = 10;
        p[12][10] = 10;
        p[12][11] = 10;
        p[12][12] = 10;
        p[12][13] = 10;
        p[12][14] = 15;
        p[13][1] = 15;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][4] = 15;
        p[13][5] = 15;
        p[13][6] = 15;
        p[13][7] = 15;
        p[13][8] = 15;
        p[13][9] = 15;
        p[13][10] = 15;
        p[13][11] = 15;
        p[13][12] = 15;
        p[13][13] = 15;
        p[13][14] = 15;
        return p;
    }


            private byte[][] createAlarmTrapPattern2() {
        byte[][] p = createEmptyCanvas();
        // 警报陷阱 - 上半圆形的部分应该是2号红色
        p[9][10] = 2;
        p[9][9] = 2;
        p[9][8] = 2;
        p[9][7] = 2;
        p[9][6] = 2;
        p[9][5] = 2;
        p[10][11] = 2;
        p[10][10] = 2;
        p[10][9] = 2;
        p[10][8] = 2;
        p[10][7] = 2;
        p[10][6] = 2;
        p[10][5] = 2;
        // 底部主体 - 棕色
        p[11][12] = 10;
        p[11][11] = 10;
        p[11][10] = 10;
        p[11][9] = 10;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][6] = 10;
        p[11][5] = 10;
        p[11][4] = 10;
        p[12][15] = 10;
        p[12][14] = 10;
        p[13][14] = 10;
        p[13][13] = 10;
        p[13][12] = 10;
        p[13][11] = 10;
        p[13][10] = 10;
        p[13][9] = 10;
        p[13][8] = 10;
        p[13][7] = 10;
        p[13][6] = 10;
        p[13][5] = 10;
        p[13][4] = 10;
        p[13][3] = 10;
        p[13][2] = 10;
        p[13][1] = 10;
        p[14][9] = 10;
        return p;
    }

            private byte[][] createDeliveryBoxPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][1] = 15;
        p[2][2] = 15;
        p[2][3] = 15;
        p[2][4] = 15;
        p[2][5] = 15;
        p[2][6] = 15;
        p[2][7] = 15;
        p[2][8] = 15;
        p[2][9] = 15;
        p[2][10] = 15;
        p[2][11] = 15;
        p[2][12] = 15;
        p[2][13] = 15;
        p[2][14] = 15;
        p[3][0] = 15;
        p[3][1] = 5;
        p[3][2] = 5;
        p[3][3] = 5;
        p[3][4] = 5;
        p[3][5] = 5;
        p[3][6] = 5;
        p[3][7] = 5;
        p[3][8] = 5;
        p[3][9] = 5;
        p[3][10] = 5;
        p[3][11] = 5;
        p[3][12] = 5;
        p[3][13] = 5;
        p[3][14] = 5;
        p[3][15] = 15;
        p[4][0] = 15;
        p[4][1] = 15;
        p[4][2] = 5;
        p[4][3] = 5;
        p[4][4] = 5;
        p[4][5] = 5;
        p[4][6] = 5;
        p[4][7] = 5;
        p[4][8] = 5;
        p[4][9] = 5;
        p[4][10] = 5;
        p[4][11] = 5;
        p[4][12] = 5;
        p[4][13] = 5;
        p[4][14] = 15;
        p[4][15] = 15;
        p[5][0] = 15;
        p[5][1] = 5;
        p[5][2] = 15;
        p[5][3] = 5;
        p[5][4] = 5;
        p[5][5] = 5;
        p[5][6] = 5;
        p[5][7] = 5;
        p[5][8] = 5;
        p[5][9] = 5;
        p[5][10] = 5;
        p[5][11] = 5;
        p[5][12] = 5;
        p[5][13] = 15;
        p[5][14] = 5;
        p[5][15] = 15;
        p[6][0] = 15;
        p[6][1] = 5;
        p[6][2] = 5;
        p[6][3] = 15;
        p[6][4] = 5;
        p[6][5] = 5;
        p[6][6] = 2;
        p[6][7] = 2;
        p[6][8] = 2;
        p[6][9] = 2;
        p[6][10] = 5;
        p[6][11] = 5;
        p[6][12] = 15;
        p[6][13] = 5;
        p[6][14] = 5;
        p[6][15] = 15;
        p[7][0] = 15;
        p[7][1] = 5;
        p[7][2] = 5;
        p[7][3] = 5;
        p[7][4] = 15;
        p[7][5] = 15;
        p[7][6] = 2;
        p[7][7] = 15;
        p[7][8] = 15;
        p[7][9] = 2;
        p[7][10] = 15;
        p[7][11] = 15;
        p[7][12] = 5;
        p[7][13] = 5;
        p[7][14] = 5;
        p[7][15] = 15;
        p[8][0] = 15;
        p[8][1] = 5;
        p[8][2] = 5;
        p[8][3] = 5;
        p[8][4] = 5;
        p[8][5] = 5;
        p[8][6] = 2;
        p[8][7] = 2;
        p[8][8] = 2;
        p[8][9] = 2;
        p[8][10] = 5;
        p[8][11] = 5;
        p[8][12] = 5;
        p[8][13] = 5;
        p[8][14] = 5;
        p[8][15] = 15;
        p[9][0] = 15;
        p[9][1] = 5;
        p[9][2] = 5;
        p[9][3] = 5;
        p[9][4] = 5;
        p[9][5] = 5;
        p[9][6] = 5;
        p[9][7] = 5;
        p[9][8] = 5;
        p[9][9] = 5;
        p[9][10] = 5;
        p[9][11] = 5;
        p[9][12] = 5;
        p[9][13] = 5;
        p[9][14] = 5;
        p[9][15] = 15;
        p[10][0] = 15;
        p[10][1] = 5;
        p[10][2] = 5;
        p[10][3] = 5;
        p[10][4] = 5;
        p[10][5] = 5;
        p[10][6] = 5;
        p[10][7] = 5;
        p[10][8] = 5;
        p[10][9] = 5;
        p[10][10] = 5;
        p[10][11] = 5;
        p[10][12] = 5;
        p[10][13] = 5;
        p[10][14] = 5;
        p[10][15] = 15;
        p[11][0] = 15;
        p[11][1] = 5;
        p[11][2] = 5;
        p[11][3] = 5;
        p[11][4] = 5;
        p[11][5] = 5;
        p[11][6] = 5;
        p[11][7] = 5;
        p[11][8] = 5;
        p[11][9] = 5;
        p[11][10] = 5;
        p[11][11] = 5;
        p[11][12] = 5;
        p[11][13] = 5;
        p[11][14] = 5;
        p[11][15] = 15;
        p[12][0] = 15;
        p[12][1] = 5;
        p[12][2] = 5;
        p[12][3] = 5;
        p[12][4] = 5;
        p[12][5] = 5;
        p[12][6] = 5;
        p[12][7] = 5;
        p[12][8] = 5;
        p[12][9] = 5;
        p[12][10] = 5;
        p[12][11] = 5;
        p[12][12] = 5;
        p[12][13] = 5;
        p[12][14] = 5;
        p[12][15] = 15;
        p[13][0] = 15;
        p[13][1] = 15;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][4] = 15;
        p[13][5] = 15;
        p[13][6] = 15;
        p[13][7] = 15;
        p[13][8] = 15;
        p[13][9] = 15;
        p[13][10] = 15;
        p[13][11] = 15;
        p[13][12] = 15;
        p[13][13] = 15;
        p[13][14] = 15;
        p[13][15] = 15;
        return p;
    }


            private byte[][] createDeliveryBoxPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][14] = 12;
        p[2][13] = 12;
        p[2][12] = 12;
        p[2][11] = 12;
        p[2][10] = 12;
        p[2][9] = 12;
        p[2][8] = 12;
        p[2][7] = 12;
        p[2][6] = 12;
        p[2][5] = 12;
        p[2][4] = 12;
        p[2][3] = 12;
        p[2][2] = 12;
        p[2][1] = 12;
        p[3][15] = 12;
        p[3][14] = 10;
        p[3][13] = 10;
        p[3][12] = 10;
        p[3][11] = 10;
        p[3][10] = 10;
        p[3][9] = 10;
        p[3][8] = 10;
        p[3][7] = 10;
        p[3][6] = 10;
        p[3][5] = 10;
        p[3][4] = 10;
        p[3][3] = 10;
        p[3][2] = 10;
        p[3][1] = 10;
        p[3][0] = 12;
        p[4][15] = 12;
        p[4][14] = 12;
        p[4][13] = 10;
        p[4][12] = 10;
        p[4][11] = 10;
        p[4][10] = 10;
        p[4][9] = 10;
        p[4][8] = 10;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[4][4] = 10;
        p[4][3] = 11;
        p[4][2] = 11;
        p[4][1] = 12;
        p[4][0] = 12;
        p[5][15] = 12;
        p[5][14] = 10;
        p[5][13] = 12;
        p[5][12] = 10;
        p[5][11] = 10;
        p[5][10] = 10;
        p[5][9] = 10;
        p[5][8] = 10;
        p[5][7] = 10;
        p[5][6] = 10;
        p[5][5] = 11;
        p[5][4] = 11;
        p[5][3] = 11;
        p[5][2] = 12;
        p[5][1] = 10;
        p[5][0] = 12;
        p[6][15] = 12;
        p[6][14] = 10;
        p[6][13] = 10;
        p[6][12] = 12;
        p[6][11] = 10;
        p[6][10] = 10;
        p[6][9] = 15;
        p[6][8] = 12;
        p[6][7] = 12;
        p[6][6] = 12;
        p[6][5] = 10;
        p[6][4] = 10;
        p[6][3] = 12;
        p[6][2] = 10;
        p[6][1] = 10;
        p[6][0] = 12;
        p[7][15] = 12;
        p[7][14] = 11;
        p[7][13] = 10;
        p[7][12] = 10;
        p[7][11] = 12;
        p[7][10] = 12;
        p[7][9] = 12;
        p[7][8] = 12;
        p[7][7] = 12;
        p[7][6] = 12;
        p[7][5] = 12;
        p[7][4] = 12;
        p[7][3] = 10;
        p[7][2] = 10;
        p[7][1] = 10;
        p[7][0] = 12;
        p[8][15] = 12;
        p[8][14] = 11;
        p[8][13] = 11;
        p[8][12] = 10;
        p[8][11] = 10;
        p[8][10] = 10;
        p[8][9] = 12;
        p[8][8] = 12;
        p[8][7] = 12;
        p[8][6] = 12;
        p[8][5] = 11;
        p[8][4] = 10;
        p[8][3] = 11;
        p[8][2] = 10;
        p[8][1] = 10;
        p[8][0] = 12;
        p[9][15] = 12;
        p[9][14] = 11;
        p[9][13] = 11;
        p[9][12] = 10;
        p[9][11] = 10;
        p[9][10] = 10;
        p[9][9] = 10;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][6] = 10;
        p[9][5] = 10;
        p[9][4] = 10;
        p[9][3] = 10;
        p[9][2] = 10;
        p[9][1] = 10;
        p[9][0] = 12;
        p[10][15] = 12;
        p[10][14] = 10;
        p[10][13] = 10;
        p[10][12] = 11;
        p[10][11] = 10;
        p[10][10] = 10;
        p[10][9] = 10;
        p[10][8] = 10;
        p[10][7] = 10;
        p[10][6] = 10;
        p[10][5] = 10;
        p[10][4] = 10;
        p[10][3] = 10;
        p[10][2] = 10;
        p[10][1] = 10;
        p[10][0] = 12;
        p[11][15] = 12;
        p[11][14] = 10;
        p[11][13] = 10;
        p[11][12] = 10;
        p[11][11] = 11;
        p[11][10] = 11;
        p[11][9] = 10;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][6] = 10;
        p[11][5] = 10;
        p[11][4] = 10;
        p[11][3] = 10;
        p[11][2] = 10;
        p[11][1] = 10;
        p[11][0] = 12;
        p[12][15] = 12;
        p[12][14] = 10;
        p[12][13] = 10;
        p[12][12] = 10;
        p[12][11] = 10;
        p[12][10] = 11;
        p[12][9] = 11;
        p[12][8] = 11;
        p[12][7] = 10;
        p[12][6] = 10;
        p[12][5] = 10;
        p[12][4] = 10;
        p[12][3] = 10;
        p[12][2] = 10;
        p[12][1] = 10;
        p[12][0] = 12;
        p[13][15] = 12;
        p[13][14] = 12;
        p[13][13] = 12;
        p[13][12] = 12;
        p[13][11] = 12;
        p[13][10] = 12;
        p[13][9] = 12;
        p[13][8] = 12;
        p[13][7] = 12;
        p[13][6] = 12;
        p[13][5] = 12;
        p[13][4] = 12;
        p[13][3] = 12;
        p[13][2] = 12;
        p[13][1] = 12;
        p[13][0] = 12;
        return p;
    }

            private byte[][] createHallucinationPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][10] = 7;
        p[1][12] = 5;
        p[1][13] = 5;
        p[2][9] = 7;
        p[2][11] = 7;
        p[2][12] = 9;
        p[2][13] = 9;
        p[2][14] = 5;
        p[3][8] = 7;
        p[3][9] = 7;
        p[3][10] = 9;
        p[3][11] = 9;
        p[3][12] = 9;
        p[3][13] = 7;
        p[3][14] = 5;
        p[4][7] = 9;
        p[4][8] = 9;
        p[4][9] = 9;
        p[4][10] = 9;
        p[4][11] = 9;
        p[4][12] = 7;
        p[4][13] = 7;
        p[5][5] = 9;
        p[5][6] = 9;
        p[5][7] = 7;
        p[5][8] = 7;
        p[5][9] = 7;
        p[5][10] = 9;
        p[5][11] = 9;
        p[6][3] = 9;
        p[6][4] = 9;
        p[6][5] = 7;
        p[6][6] = 7;
        p[6][7] = 7;
        p[6][8] = 7;
        p[6][9] = 7;
        p[6][10] = 7;
        p[6][11] = 9;
        p[6][12] = 9;
        p[7][2] = 9;
        p[7][3] = 7;
        p[7][4] = 7;
        p[7][5] = 7;
        p[7][6] = 7;
        p[7][7] = 7;
        p[7][8] = 7;
        p[7][9] = 7;
        p[7][10] = 7;
        p[7][11] = 7;
        p[7][12] = 9;
        p[8][2] = 5;
        p[8][3] = 5;
        p[8][4] = 5;
        p[8][5] = 7;
        p[8][6] = 7;
        p[8][7] = 7;
        p[8][8] = 5;
        p[8][9] = 5;
        p[8][10] = 7;
        p[8][11] = 7;
        p[8][12] = 9;
        p[9][2] = 5;
        p[9][3] = 7;
        p[9][4] = 7;
        p[9][5] = 5;
        p[9][6] = 5;
        p[9][7] = 7;
        p[9][8] = 7;
        p[9][9] = 5;
        p[9][10] = 7;
        p[9][11] = 9;
        p[10][2] = 5;
        p[10][3] = 5;
        p[10][4] = 7;
        p[10][5] = 7;
        p[10][6] = 7;
        p[10][7] = 7;
        p[10][8] = 5;
        p[10][9] = 7;
        p[10][10] = 9;
        p[11][2] = 9;
        p[11][3] = 7;
        p[11][4] = 5;
        p[11][5] = 7;
        p[11][6] = 7;
        p[11][7] = 5;
        p[11][8] = 7;
        p[11][9] = 5;
        p[11][10] = 9;
        p[12][2] = 9;
        p[12][3] = 9;
        p[12][4] = 5;
        p[12][5] = 7;
        p[12][6] = 5;
        p[12][7] = 5;
        p[12][8] = 7;
        p[12][9] = 9;
        p[13][3] = 9;
        p[13][4] = 9;
        p[13][5] = 9;
        p[13][6] = 9;
        p[13][7] = 9;
        p[13][8] = 9;
        p[13][9] = 9;
        return p;
    }


            private byte[][] createHallucinationPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][4] = 11;
        p[2][3] = 10;
        p[2][2] = 10;
        p[2][1] = 11;
        p[3][6] = 11;
        p[3][5] = 10;
        p[3][4] = 10;
        p[3][3] = 10;
        p[3][2] = 10;
        p[4][8] = 11;
        p[4][7] = 11;
        p[4][6] = 11;
        p[4][5] = 10;
        p[4][4] = 10;
        p[4][3] = 10;
        p[4][2] = 11;
        p[5][9] = 11;
        p[5][8] = 1;
        p[5][7] = 1;
        p[5][6] = 1;
        p[5][5] = 11;
        p[5][4] = 10;
        p[6][12] = 11;
        p[6][11] = 11;
        p[6][10] = 1;
        p[6][9] = 1;
        p[6][8] = 1;
        p[6][7] = 1;
        p[6][6] = 1;
        p[6][5] = 1;
        p[6][4] = 11;
        p[6][3] = 10;
        p[7][13] = 11;
        p[7][12] = 1;
        p[7][11] = 1;
        p[7][10] = 1;
        p[7][9] = 1;
        p[7][8] = 11;
        p[7][7] = 1;
        p[7][6] = 1;
        p[7][5] = 1;
        p[7][4] = 1;
        p[7][3] = 11;
        p[8][13] = 11;
        p[8][12] = 11;
        p[8][11] = 11;
        p[8][10] = 11;
        p[8][9] = 11;
        p[8][8] = 11;
        p[8][7] = 11;
        p[8][6] = 1;
        p[8][5] = 1;
        p[8][4] = 1;
        p[8][3] = 10;
        p[9][13] = 11;
        p[9][12] = 11;
        p[9][11] = 11;
        p[9][10] = 11;
        p[9][9] = 11;
        p[9][8] = 11;
        p[9][7] = 11;
        p[9][6] = 1;
        p[9][5] = 1;
        p[9][4] = 11;
        p[10][13] = 11;
        p[10][12] = 11;
        p[10][11] = 11;
        p[10][10] = 11;
        p[10][9] = 11;
        p[10][8] = 1;
        p[10][7] = 1;
        p[10][6] = 1;
        p[10][5] = 11;
        p[11][13] = 10;
        p[11][12] = 11;
        p[11][11] = 11;
        p[11][10] = 11;
        p[11][9] = 11;
        p[11][8] = 11;
        p[11][7] = 1;
        p[11][6] = 1;
        p[11][5] = 10;
        p[12][13] = 10;
        p[12][12] = 10;
        p[12][11] = 11;
        p[12][10] = 11;
        p[12][9] = 11;
        p[12][8] = 15;
        p[12][7] = 1;
        p[12][6] = 11;
        p[13][12] = 10;
        p[13][11] = 10;
        p[13][10] = 10;
        p[13][9] = 11;
        p[13][8] = 11;
        p[13][7] = 11;
        p[13][6] = 11;
        return p;
    }

    // 薄荷糖 - 白色部分不变，其余为3号绿色
            private byte[][] createMintCandiesPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][5] = 3;
        p[2][6] = 3;
        p[2][7] = 3;
        p[2][8] = 3;
        p[2][9] = 3;
        p[2][10] = 3;
        p[3][4] = 3;
        p[3][5] = 3;
        p[3][6] = 3;
        p[3][7] = 1;
        p[3][8] = 3;
        p[3][9] = 3;
        p[3][10] = 3;
        p[3][11] = 3;
        p[4][3] = 3;
        p[4][4] = 3;
        p[4][5] = 3;
        p[4][6] = 3;
        p[4][7] = 3;
        p[4][8] = 1;
        p[4][9] = 1;
        p[4][10] = 3;
        p[4][11] = 3;
        p[4][12] = 3;
        p[5][2] = 3;
        p[5][3] = 3;
        p[5][4] = 3;
        p[5][5] = 1;
        p[5][6] = 3;
        p[5][7] = 3;
        p[5][8] = 3;
        p[5][9] = 1;
        p[5][10] = 1;
        p[5][11] = 3;
        p[5][12] = 3;
        p[5][13] = 3;
        p[6][2] = 3;
        p[6][3] = 3;
        p[6][4] = 3;
        p[6][5] = 1;
        p[6][6] = 3;
        p[6][7] = 3;
        p[6][8] = 3;
        p[6][9] = 3;
        p[6][10] = 3;
        p[6][11] = 3;
        p[6][12] = 3;
        p[6][13] = 3;
        p[7][2] = 3;
        p[7][3] = 3;
        p[7][4] = 3;
        p[7][5] = 1;
        p[7][6] = 1;
        p[7][7] = 3;
        p[7][8] = 3;
        p[7][9] = 3;
        p[7][10] = 3;
        p[7][11] = 3;
        p[7][12] = 3;
        p[7][13] = 3;
        p[8][2] = 3;
        p[8][3] = 3;
        p[8][4] = 3;
        p[8][5] = 3;
        p[8][6] = 3;
        p[8][7] = 3;
        p[8][8] = 3;
        p[8][9] = 3;
        p[8][10] = 3;
        p[8][11] = 3;
        p[8][12] = 3;
        p[8][13] = 3;
        p[9][2] = 3;
        p[9][3] = 3;
        p[9][4] = 3;
        p[9][5] = 3;
        p[9][6] = 3;
        p[9][7] = 3;
        p[9][8] = 3;
        p[9][9] = 3;
        p[9][10] = 3;
        p[9][11] = 3;
        p[9][12] = 3;
        p[9][13] = 3;
        p[10][2] = 3;
        p[10][3] = 3;
        p[10][4] = 3;
        p[10][5] = 3;
        p[10][6] = 3;
        p[10][7] = 3;
        p[10][8] = 3;
        p[10][9] = 3;
        p[10][10] = 3;
        p[10][11] = 3;
        p[10][12] = 3;
        p[10][13] = 3;
        p[11][3] = 3;
        p[11][4] = 3;
        p[11][5] = 3;
        p[11][6] = 3;
        p[11][7] = 3;
        p[11][8] = 3;
        p[11][9] = 3;
        p[11][10] = 3;
        p[11][11] = 3;
        p[11][12] = 3;
        p[12][4] = 3;
        p[12][5] = 3;
        p[12][6] = 3;
        p[12][7] = 3;
        p[12][8] = 3;
        p[12][9] = 3;
        p[12][10] = 3;
        p[12][11] = 3;
        p[13][5] = 3;
        p[13][6] = 3;
        p[13][7] = 3;
        p[13][8] = 3;
        p[13][9] = 3;
        p[13][10] = 3;
        return p;
    }


            private byte[][] createMintCandiesPattern2() {
        byte[][] p = createEmptyCanvas();
        // 薄荷糖 - 白色部分不变，其余为3号绿色
        p[2][9] = 3; p[2][6] = 3;
        p[3][10] = 3; p[3][9] = 3; p[3][8] = 3; p[3][7] = 3; p[3][6] = 3; p[3][5] = 3;
        p[4][11] = 3; p[4][10] = 3; p[4][9] = 3; p[4][8] = 1; p[4][7] = 1; p[4][6] = 1; p[4][5] = 3; p[4][4] = 3;
        p[5][12] = 3; p[5][11] = 3; p[5][10] = 1; p[5][9] = 3; p[5][8] = 1; p[5][7] = 1; p[5][6] = 3; p[5][5] = 3; p[5][4] = 3; p[5][3] = 3;
        p[6][13] = 3; p[6][12] = 3; p[6][11] = 3; p[6][10] = 3; p[6][9] = 3; p[6][8] = 1; p[6][7] = 3; p[6][6] = 3; p[6][5] = 3; p[6][4] = 1; p[6][3] = 3; p[6][2] = 3;
        p[7][12] = 3; p[7][11] = 3; p[7][10] = 3; p[7][9] = 1; p[7][8] = 3; p[7][7] = 3; p[7][6] = 3; p[7][5] = 1; p[7][4] = 1; p[7][3] = 3;
        p[8][12] = 3; p[8][11] = 3; p[8][10] = 1; p[8][9] = 3; p[8][8] = 3; p[8][7] = 3; p[8][6] = 1; p[8][5] = 3; p[8][4] = 3; p[8][3] = 3;
        p[9][13] = 3; p[9][12] = 3; p[9][11] = 3; p[9][10] = 3; p[9][9] = 3; p[9][8] = 3; p[9][7] = 1; p[9][6] = 3; p[9][5] = 3; p[9][4] = 3; p[9][3] = 3; p[9][2] = 3;
        p[10][12] = 3; p[10][11] = 3; p[10][10] = 3; p[10][9] = 3; p[10][8] = 1; p[10][7] = 3; p[10][6] = 3; p[10][5] = 3; p[10][4] = 3; p[10][3] = 3;
        p[11][11] = 3; p[11][10] = 3; p[11][9] = 1; p[11][8] = 1; p[11][7] = 1; p[11][6] = 3;
        p[12][10] = 3; p[12][9] = 3; p[12][8] = 3; p[12][7] = 3; p[12][6] = 3; p[12][5] = 3;
        p[13][9] = 3; p[13][6] = 3;
        return p;
    }

    

            private byte[][] createWheelchairPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][4] = 0;
        p[2][5] = 0;
        p[2][6] = 0;
        p[2][7] = 0;
        p[2][8] = 0;
        p[2][9] = 0;
        p[2][10] = 0;
        p[2][11] = 0;
        p[3][4] = 0;
        p[3][5] = 0;
        p[3][6] = 0;
        p[3][7] = 0;
        p[3][8] = 0;
        p[3][9] = 0;
        p[3][10] = 0;
        p[3][11] = 0;
        p[4][3] = 10;
        p[4][4] = 0;
        p[4][5] = 0;
        p[4][6] = 0;
        p[4][7] = 0;
        p[4][8] = 0;
        p[4][9] = 0;
        p[4][10] = 0;
        p[4][11] = 0;
        p[4][12] = 10;
        p[5][3] = 10;
        p[5][4] = 0;
        p[5][5] = 0;
        p[5][6] = 0;
        p[5][7] = 0;
        p[5][8] = 0;
        p[5][9] = 0;
        p[5][10] = 0;
        p[5][11] = 0;
        p[5][12] = 10;
        p[6][3] = 10;
        p[6][4] = 0;
        p[6][5] = 0;
        p[6][6] = 0;
        p[6][7] = 0;
        p[6][8] = 0;
        p[6][9] = 0;
        p[6][10] = 0;
        p[6][11] = 0;
        p[6][12] = 10;
        p[7][3] = 10;
        p[7][4] = 0;
        p[7][5] = 0;
        p[7][6] = 0;
        p[7][7] = 0;
        p[7][8] = 0;
        p[7][9] = 0;
        p[7][10] = 0;
        p[7][11] = 0;
        p[7][12] = 10;
        p[8][3] = 10;
        p[8][4] = 0;
        p[8][5] = 0;
        p[8][6] = 0;
        p[8][7] = 0;
        p[8][8] = 0;
        p[8][9] = 0;
        p[8][10] = 0;
        p[8][11] = 0;
        p[8][12] = 10;
        p[9][2] = 0;
        p[9][3] = 10;
        p[9][4] = 0;
        p[9][5] = 0;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 0;
        p[9][9] = 0;
        p[9][10] = 0;
        p[9][11] = 0;
        p[9][12] = 10;
        p[9][13] = 0;
        p[10][2] = 0;
        p[10][3] = 10;
        p[10][4] = 0;
        p[10][5] = 0;
        p[10][6] = 0;
        p[10][7] = 0;
        p[10][8] = 0;
        p[10][9] = 0;
        p[10][10] = 0;
        p[10][11] = 0;
        p[10][12] = 10;
        p[10][13] = 0;
        p[11][2] = 0;
        p[11][3] = 10;
        p[11][4] = 10;
        p[11][5] = 10;
        p[11][6] = 10;
        p[11][7] = 10;
        p[11][8] = 10;
        p[11][9] = 10;
        p[11][10] = 10;
        p[11][11] = 10;
        p[11][12] = 10;
        p[11][13] = 0;
        p[12][2] = 0;
        p[12][13] = 0;
        p[13][2] = 0;
        p[13][13] = 0;
        return p;
    }


            private byte[][] createWheelchairPattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][9] = 10;
        p[0][8] = 10;
        p[1][9] = 10;
        p[1][8] = 10;
        p[2][9] = 10;
        p[2][8] = 10;
        p[3][12] = 10;
        p[3][11] = 10;
        p[3][10] = 10;
        p[3][9] = 12;
        p[3][8] = 12;
        p[3][7] = 12;
        p[3][6] = 12;
        p[3][3] = 12;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[4][4] = 10;
        p[5][7] = 10;
        p[5][6] = 10;
        p[5][5] = 10;
        p[5][4] = 10;
        p[6][7] = 10;
        p[6][6] = 10;
        p[6][5] = 10;
        p[6][4] = 10;
        p[7][12] = 11;
        p[7][11] = 11;
        p[7][7] = 10;
        p[7][6] = 10;
        p[7][5] = 10;
        p[7][4] = 10;
        p[7][3] = 12;
        p[7][2] = 12;
        p[8][3] = 11;
        p[9][4] = 11;
        p[9][3] = 11;
        p[11][11] = 10;
        p[12][15] = 10;
        p[12][14] = 10;
        p[12][13] = 10;
        p[12][12] = 10;
        p[12][7] = 11;
        p[12][6] = 10;
        p[13][15] = 12;
        p[13][14] = 10;
        p[13][13] = 10;
        p[13][12] = 10;
        return p;
    }

            private byte[][] createShortShotgunPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][3] = 10;
        p[2][2] = 10;
        p[2][3] = 10;
        p[2][4] = 10;
        p[3][3] = 10;
        p[3][4] = 15;
        p[3][5] = 10;
        p[4][4] = 15;
        p[4][5] = 15;
        p[4][6] = 10;
        p[5][5] = 15;
        p[5][6] = 15;
        p[5][7] = 10;
        p[6][6] = 15;
        p[6][7] = 10;
        p[6][8] = 10;
        p[7][6] = 15;
        p[7][7] = 15;
        p[7][8] = 10;
        p[7][9] = 10;
        p[8][7] = 15;
        p[8][8] = 15;
        p[8][9] = 10;
        p[8][10] = 10;
        p[8][12] = 11;
        p[8][13] = 11;
        p[9][6] = 10;
        p[9][8] = 10;
        p[9][9] = 10;
        p[9][10] = 10;
        p[9][11] = 15;
        p[9][14] = 11;
        p[9][15] = 11;
        p[10][5] = 10;
        p[10][7] = 10;
        p[10][9] = 10;
        p[10][10] = 15;
        p[10][11] = 15;
        p[10][12] = 15;
        p[11][5] = 10;
        p[11][8] = 10;
        p[11][9] = 15;
        p[11][10] = 15;
        p[11][11] = 15;
        p[11][12] = 10;
        p[12][6] = 10;
        p[12][7] = 10;
        p[12][8] = 10;
        p[12][10] = 15;
        p[12][11] = 10;
        p[12][12] = 10;
        p[12][13] = 15;
        p[13][11] = 10;
        p[13][12] = 10;
        p[13][13] = 15;
        p[14][11] = 15;
        p[14][12] = 15;
        return p;
    }


            private byte[][] createShortShotgunPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][12] = 10;
        p[2][11] = 10;
        p[3][12] = 12;
        p[3][11] = 10;
        p[3][10] = 10;
        p[4][11] = 10;
        p[4][10] = 10;
        p[4][9] = 10;
        p[5][10] = 12;
        p[5][9] = 10;
        p[5][8] = 10;
        p[6][9] = 12;
        p[6][8] = 10;
        p[6][7] = 10;
        p[7][9] = 12;
        p[7][8] = 12;
        p[7][7] = 10;
        p[7][6] = 10;
        p[8][8] = 12;
        p[8][7] = 12;
        p[8][6] = 10;
        p[8][5] = 10;
        p[9][7] = 10;
        p[9][6] = 12;
        p[9][5] = 10;
        p[9][4] = 10;
        p[10][6] = 12;
        p[10][5] = 12;
        p[10][4] = 12;
        p[11][7] = 10;
        p[11][6] = 12;
        p[11][5] = 12;
        p[11][4] = 10;
        p[12][5] = 12;
        p[12][4] = 10;
        p[12][3] = 10;
        p[13][4] = 10;
        p[13][3] = 10;
        return p;
    }

            private byte[][] createBatonPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][13] = 0;
        p[1][14] = 0;
        p[2][12] = 0;
        p[2][13] = 0;
        p[3][11] = 0;
        p[3][12] = 0;
        p[4][10] = 0;
        p[4][11] = 0;
        p[5][9] = 0;
        p[5][10] = 0;
        p[6][8] = 0;
        p[6][9] = 0;
        p[7][7] = 0;
        p[7][8] = 0;
        p[8][4] = 0;
        p[8][6] = 0;
        p[8][7] = 0;
        p[9][5] = 10;
        p[9][6] = 10;
        p[10][4] = 10;
        p[10][5] = 10;
        p[10][6] = 0;
        p[11][3] = 0;
        p[11][4] = 0;
        p[11][6] = 0;
        p[11][7] = 0;
        p[12][2] = 0;
        p[12][3] = 0;
        p[12][7] = 0;
        p[12][8] = 0;
        p[13][1] = 0;
        p[13][2] = 0;
        p[14][1] = 0;
        p[14][2] = 0;
        return p;
    }


            private byte[][] createBatonPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][2] = 12;
        p[1][1] = 12;
        p[2][3] = 12;
        p[2][2] = 12;
        p[3][4] = 12;
        p[3][3] = 12;
        p[4][5] = 12;
        p[4][4] = 12;
        p[5][6] = 12;
        p[5][5] = 12;
        p[6][7] = 12;
        p[6][6] = 12;
        p[7][8] = 12;
        p[7][7] = 12;
        p[8][9] = 12;
        p[8][8] = 12;
        p[9][10] = 12;
        p[9][9] = 12;
        p[10][11] = 10;
        p[10][10] = 12;
        p[11][11] = 12;
        p[11][9] = 12;
        p[12][9] = 12;
        return p;
    }

            private byte[][] createRadioPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][10] = 0;
        p[1][6] = 0;
        p[1][7] = 0;
        p[1][8] = 0;
        p[1][10] = 0;
        p[2][4] = 10;
        p[2][5] = 10;
        p[2][7] = 0;
        p[2][10] = 0;
        p[3][4] = 10;
        p[3][5] = 0;
        p[3][6] = 0;
        p[3][7] = 0;
        p[3][8] = 0;
        p[3][9] = 0;
        p[3][10] = 0;
        p[4][4] = 0;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][10] = 0;
        p[5][4] = 0;
        p[5][5] = 0;
        p[5][6] = 0;
        p[5][7] = 0;
        p[5][8] = 0;
        p[5][9] = 0;
        p[5][10] = 0;
        p[6][4] = 0;
        p[6][5] = 7;
        p[6][6] = 7;
        p[6][7] = 7;
        p[6][8] = 7;
        p[6][9] = 7;
        p[6][10] = 0;
        p[7][4] = 0;
        p[7][5] = 7;
        p[7][6] = 11;
        p[7][7] = 11;
        p[7][8] = 11;
        p[7][9] = 7;
        p[7][10] = 0;
        p[8][4] = 0;
        p[8][5] = 7;
        p[8][6] = 7;
        p[8][7] = 7;
        p[8][8] = 7;
        p[8][9] = 7;
        p[8][10] = 0;
        p[9][4] = 0;
        p[9][5] = 0;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 0;
        p[9][9] = 0;
        p[9][10] = 0;
        p[10][5] = 0;
        p[10][6] = 10;
        p[10][7] = 10;
        p[10][8] = 10;
        p[10][9] = 0;
        p[11][5] = 0;
        p[11][6] = 0;
        p[11][7] = 0;
        p[11][8] = 0;
        p[11][9] = 0;
        p[12][5] = 0;
        p[12][6] = 10;
        p[12][7] = 10;
        p[12][8] = 10;
        p[12][9] = 0;
        p[13][5] = 0;
        p[13][6] = 0;
        p[13][7] = 0;
        p[13][8] = 0;
        p[13][9] = 0;
        return p;
    }


            private byte[][] createRadioPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][5] = 12;
        p[2][8] = 12;
        p[2][5] = 12;
        p[3][10] = 12;
        p[3][5] = 12;
        p[4][11] = 12;
        p[4][10] = 12;
        p[5][11] = 12;
        p[6][11] = 12;
        p[6][10] = 10;
        p[6][9] = 10;
        p[6][8] = 10;
        p[6][7] = 10;
        p[6][6] = 10;
        p[7][10] = 10;
        p[7][9] = 11;
        p[7][8] = 11;
        p[7][7] = 11;
        p[7][6] = 10;
        p[8][10] = 12;
        p[8][9] = 10;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 10;
        p[9][8] = 12;
        p[10][10] = 12;
        p[10][6] = 12;
        p[12][9] = 12;
        p[12][8] = 12;
        p[12][6] = 12;
        p[13][10] = 12;
        p[13][9] = 12;
        p[13][8] = 12;
        p[13][6] = 12;
        return p;
    }

            private byte[][] createMonitoringTerminalPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][5] = 0;
        p[1][5] = 0;
        p[2][5] = 0;
        p[3][5] = 11;
        p[3][6] = 11;
        p[3][7] = 11;
        p[3][8] = 11;
        p[3][9] = 11;
        p[3][10] = 11;
        p[3][11] = 11;
        p[4][4] = 11;
        p[4][5] = 11;
        p[4][6] = 14;
        p[4][7] = 14;
        p[4][8] = 14;
        p[4][9] = 14;
        p[4][10] = 14;
        p[4][11] = 11;
        p[4][12] = 11;
        p[5][4] = 11;
        p[5][5] = 14;
        p[5][6] = 0;
        p[5][7] = 0;
        p[5][8] = 0;
        p[5][9] = 0;
        p[5][10] = 0;
        p[5][11] = 14;
        p[5][12] = 11;
        p[6][4] = 11;
        p[6][5] = 14;
        p[6][6] = 0;
        p[6][7] = 0;
        p[6][8] = 0;
        p[6][9] = 0;
        p[6][10] = 0;
        p[6][11] = 14;
        p[6][12] = 11;
        p[7][4] = 11;
        p[7][5] = 14;
        p[7][6] = 0;
        p[7][7] = 0;
        p[7][8] = 0;
        p[7][9] = 0;
        p[7][10] = 0;
        p[7][11] = 14;
        p[7][12] = 11;
        p[8][4] = 11;
        p[8][5] = 14;
        p[8][6] = 0;
        p[8][7] = 0;
        p[8][8] = 0;
        p[8][9] = 0;
        p[8][10] = 0;
        p[8][11] = 14;
        p[8][12] = 11;
        p[9][4] = 11;
        p[9][5] = 14;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 0;
        p[9][9] = 0;
        p[9][10] = 0;
        p[9][11] = 14;
        p[9][12] = 11;
        p[10][4] = 11;
        p[10][5] = 11;
        p[10][6] = 14;
        p[10][7] = 14;
        p[10][8] = 14;
        p[10][9] = 14;
        p[10][10] = 14;
        p[10][11] = 11;
        p[10][12] = 11;
        p[11][4] = 11;
        p[11][5] = 11;
        p[11][6] = 14;
        p[11][7] = 11;
        p[11][8] = 11;
        p[11][9] = 11;
        p[11][10] = 11;
        p[11][11] = 11;
        p[11][12] = 11;
        p[12][4] = 11;
        p[12][5] = 14;
        p[12][6] = 11;
        p[12][7] = 14;
        p[12][8] = 11;
        p[12][9] = 11;
        p[12][10] = 14;
        p[12][11] = 11;
        p[12][12] = 11;
        p[13][4] = 11;
        p[13][5] = 11;
        p[13][6] = 14;
        p[13][7] = 11;
        p[13][8] = 11;
        p[13][9] = 11;
        p[13][10] = 14;
        p[13][11] = 11;
        p[13][12] = 11;
        p[14][5] = 11;
        p[14][6] = 11;
        p[14][7] = 11;
        p[14][8] = 11;
        p[14][9] = 11;
        p[14][10] = 11;
        p[14][11] = 11;
        return p;
    }


            private byte[][] createMonitoringTerminalPattern2() {
        byte[][] p = createEmptyCanvas();
        p[3][10] = 10;
        p[3][8] = 1;
        p[3][5] = 1;
        p[4][11] = 1;
        p[4][10] = 11;
        p[4][9] = 10;
        p[4][8] = 10;
        p[4][7] = 11;
        p[4][6] = 10;
        p[4][5] = 10;
        p[4][4] = 15;
        p[5][11] = 11;
        p[5][4] = 10;
        p[5][3] = 11;
        p[6][11] = 10;
        p[6][10] = 12;
        p[6][9] = 12;
        p[6][8] = 12;
        p[6][7] = 12;
        p[6][6] = 12;
        p[6][4] = 10;
        p[7][11] = 10;
        p[7][9] = 12;
        p[7][8] = 12;
        p[7][7] = 12;
        p[7][6] = 12;
        p[7][4] = 10;
        p[8][11] = 10;
        p[8][4] = 10;
        p[9][11] = 11;
        p[9][8] = 12;
        p[9][4] = 10;
        p[10][11] = 15;
        p[10][10] = 11;
        p[10][9] = 11;
        p[10][8] = 11;
        p[10][7] = 11;
        p[10][6] = 11;
        p[10][5] = 11;
        p[10][4] = 1;
        p[11][11] = 11;
        p[11][10] = 11;
        p[11][9] = 11;
        p[11][8] = 15;
        p[11][7] = 1;
        p[11][6] = 15;
        p[11][5] = 10;
        p[11][4] = 1;
        p[12][11] = 11;
        p[12][10] = 11;
        p[12][9] = 10;
        p[12][8] = 11;
        p[12][7] = 1;
        p[12][6] = 11;
        p[12][5] = 10;
        p[12][4] = 11;
        p[12][3] = 11;
        p[13][11] = 11;
        p[13][10] = 1;
        p[13][9] = 11;
        p[13][8] = 1;
        p[13][7] = 1;
        p[13][6] = 1;
        p[13][5] = 11;
        p[13][4] = 1;
        p[14][10] = 11;
        p[14][9] = 11;
        p[14][8] = 11;
        p[14][7] = 11;
        p[14][6] = 11;
        p[14][5] = 11;
        p[14][4] = 11;
        return p;
    }

        private byte[][] createLockPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][5] = 11;
        p[1][6] = 10;
        p[1][7] = 10;
        p[1][8] = 10;
        p[1][9] = 10;
        p[1][10] = 10;
        p[2][4] = 10;
        p[2][5] = 10;
        p[2][6] = 10;
        p[2][7] = 10;
        p[2][8] = 10;
        p[2][9] = 10;
        p[2][10] = 10;
        p[2][11] = 10;
        p[3][3] = 11;
        p[3][4] = 10;
        p[3][5] = 10;
        p[3][10] = 10;
        p[3][11] = 10;
        p[3][12] = 10;
        p[4][3] = 10;
        p[4][4] = 10;
        p[4][11] = 10;
        p[4][12] = 10;
        p[5][3] = 10;
        p[5][4] = 10;
        p[5][11] = 10;
        p[5][12] = 10;
        p[6][3] = 10;
        p[6][4] = 10;
        p[6][11] = 10;
        p[6][12] = 10;
        p[7][2] = 10;
        p[7][3] = 10;
        p[7][4] = 10;
        p[7][5] = 10;
        p[7][6] = 10;
        p[7][7] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[7][10] = 10;
        p[7][11] = 10;
        p[7][12] = 10;
        p[7][13] = 10;
        p[8][2] = 10;
        p[8][3] = 10;
        p[8][4] = 10;
        p[8][5] = 11;
        p[8][6] = 11;
        p[8][7] = 10;
        p[8][8] = 10;
        p[8][9] = 10;
        p[8][10] = 10;
        p[8][11] = 10;
        p[8][12] = 10;
        p[8][13] = 10;
        p[9][2] = 10;
        p[9][3] = 10;
        p[9][4] = 10;
        p[9][5] = 0;
        p[9][6] = 0;
        p[9][7] = 10;
        p[9][8] = 10;
        p[9][9] = 0;
        p[9][10] = 0;
        p[9][11] = 10;
        p[9][12] = 10;
        p[9][13] = 10;
        p[10][2] = 10;
        p[10][3] = 11;
        p[10][4] = 11;
        p[10][5] = 10;
        p[10][6] = 0;
        p[10][7] = 0;
        p[10][8] = 0;
        p[10][9] = 0;
        p[10][10] = 10;
        p[10][11] = 11;
        p[10][12] = 10;
        p[10][13] = 10;
        p[11][2] = 10;
        p[11][3] = 11;
        p[11][4] = 11;
        p[11][5] = 11;
        p[11][6] = 10;
        p[11][7] = 0;
        p[11][8] = 0;
        p[11][9] = 10;
        p[11][10] = 11;
        p[11][11] = 11;
        p[11][12] = 10;
        p[11][13] = 10;
        p[12][2] = 10;
        p[12][3] = 11;
        p[12][4] = 11;
        p[12][5] = 10;
        p[12][6] = 10;
        p[12][7] = 10;
        p[12][8] = 10;
        p[12][9] = 10;
        p[12][10] = 11;
        p[12][11] = 10;
        p[12][12] = 10;
        p[12][13] = 10;
        p[13][2] = 10;
        p[13][3] = 10;
        p[13][4] = 10;
        p[13][5] = 10;
        p[13][6] = 10;
        p[13][7] = 10;
        p[13][8] = 10;
        p[13][9] = 11;
        p[13][10] = 10;
        p[13][11] = 10;
        p[13][12] = 10;
        p[13][13] = 10;
        p[14][2] = 10;
        p[14][3] = 10;
        p[14][4] = 10;
        p[14][5] = 10;
        p[14][6] = 10;
        p[14][7] = 10;
        p[14][8] = 11;
        p[14][9] = 10;
        p[14][10] = 10;
        p[14][11] = 10;
        p[14][12] = 10;
        p[15][4] = 10;
        p[15][5] = 10;
        p[15][6] = 11;
        p[15][7] = 11;
        p[15][8] = 10;
        p[15][9] = 10;
        p[15][10] = 10;
        p[15][11] = 10;
        return p;
    }


        private byte[][] createLockPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][10] = 11;
        p[1][9] = 10;
        p[1][8] = 10;
        p[1][7] = 10;
        p[1][6] = 10;
        p[1][5] = 10;
        p[2][11] = 10;
        p[2][10] = 10;
        p[2][9] = 12;
        p[2][8] = 12;
        p[2][7] = 10;
        p[2][6] = 12;
        p[2][5] = 10;
        p[2][4] = 10;
        p[3][12] = 11;
        p[3][11] = 10;
        p[3][10] = 12;
        p[3][5] = 10;
        p[3][4] = 10;
        p[3][3] = 10;
        p[4][12] = 10;
        p[4][11] = 10;
        p[4][4] = 10;
        p[4][3] = 10;
        p[5][12] = 10;
        p[5][11] = 10;
        p[5][4] = 12;
        p[5][3] = 10;
        p[6][4] = 12;
        p[7][13] = 12;
        p[7][12] = 10;
        p[7][11] = 10;
        p[7][10] = 12;
        p[7][9] = 10;
        p[7][8] = 10;
        p[7][7] = 10;
        p[7][6] = 12;
        p[7][5] = 12;
        p[7][4] = 10;
        p[7][3] = 10;
        p[7][2] = 12;
        p[8][13] = 12;
        p[8][12] = 10;
        p[8][11] = 10;
        p[8][10] = 11;
        p[8][9] = 11;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 10;
        p[8][5] = 10;
        p[8][4] = 10;
        p[8][3] = 10;
        p[8][2] = 12;
        p[9][13] = 12;
        p[9][12] = 10;
        p[9][11] = 10;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][4] = 10;
        p[9][3] = 10;
        p[9][2] = 12;
        p[10][13] = 12;
        p[10][12] = 11;
        p[10][11] = 11;
        p[10][10] = 10;
        p[10][5] = 10;
        p[10][4] = 11;
        p[10][3] = 10;
        p[10][2] = 12;
        p[11][13] = 10;
        p[11][12] = 11;
        p[11][11] = 11;
        p[11][10] = 11;
        p[11][9] = 10;
        p[11][6] = 10;
        p[11][5] = 11;
        p[11][4] = 11;
        p[11][3] = 10;
        p[11][2] = 10;
        p[12][13] = 10;
        p[12][12] = 11;
        p[12][11] = 11;
        p[12][10] = 10;
        p[12][9] = 10;
        p[12][8] = 10;
        p[12][7] = 10;
        p[12][6] = 10;
        p[12][5] = 11;
        p[12][4] = 10;
        p[12][3] = 10;
        p[12][2] = 12;
        p[13][13] = 10;
        p[13][12] = 10;
        p[13][11] = 10;
        p[13][10] = 10;
        p[13][9] = 10;
        p[13][8] = 10;
        p[13][7] = 10;
        p[13][6] = 11;
        p[13][5] = 10;
        p[13][4] = 10;
        p[13][3] = 10;
        p[13][2] = 12;
        p[14][13] = 12;
        p[14][12] = 10;
        p[14][11] = 10;
        p[14][10] = 10;
        p[14][9] = 10;
        p[14][8] = 10;
        p[14][7] = 11;
        p[14][6] = 10;
        p[14][5] = 10;
        p[14][4] = 10;
        p[14][3] = 10;
        p[15][11] = 10;
        p[15][10] = 10;
        p[15][9] = 11;
        p[15][8] = 11;
        p[15][7] = 10;
        p[15][6] = 10;
        p[15][5] = 10;
        p[15][4] = 10;
        return p;
    }

            private byte[][] createPocketWatchPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][4] = 5;
        p[1][6] = 8;
        p[1][7] = 8;
        p[1][8] = 8;
        p[2][2] = 5;
        p[2][3] = 5;
        p[2][5] = 5;
        p[2][6] = 5;
        p[2][7] = 8;
        p[3][1] = 5;
        p[3][6] = 8;
        p[3][7] = 8;
        p[3][8] = 8;
        p[4][0] = 5;
        p[4][4] = 8;
        p[4][5] = 8;
        p[4][6] = 5;
        p[4][7] = 5;
        p[4][8] = 5;
        p[4][9] = 8;
        p[4][10] = 8;
        p[5][0] = 5;
        p[5][3] = 8;
        p[5][4] = 5;
        p[5][5] = 5;
        p[5][6] = 4;
        p[5][7] = 4;
        p[5][8] = 4;
        p[5][9] = 5;
        p[5][10] = 5;
        p[5][11] = 8;
        p[6][0] = 5;
        p[6][3] = 8;
        p[6][4] = 5;
        p[6][5] = 4;
        p[6][6] = 4;
        p[6][7] = 4;
        p[6][8] = 4;
        p[6][9] = 8;
        p[6][10] = 5;
        p[6][11] = 8;
        p[7][0] = 5;
        p[7][2] = 8;
        p[7][3] = 5;
        p[7][4] = 0;
        p[7][5] = 0;
        p[7][6] = 4;
        p[7][7] = 4;
        p[7][8] = 8;
        p[7][9] = 4;
        p[7][10] = 4;
        p[7][11] = 5;
        p[7][12] = 8;
        p[8][2] = 8;
        p[8][3] = 5;
        p[8][4] = 0;
        p[8][5] = 0;
        p[8][6] = 0;
        p[8][7] = 8;
        p[8][8] = 4;
        p[8][9] = 4;
        p[8][10] = 4;
        p[8][11] = 5;
        p[8][12] = 8;
        p[9][2] = 8;
        p[9][3] = 5;
        p[9][4] = 0;
        p[9][5] = 0;
        p[9][6] = 0;
        p[9][7] = 0;
        p[9][8] = 5;
        p[9][9] = 4;
        p[9][10] = 4;
        p[9][11] = 5;
        p[9][12] = 8;
        p[10][3] = 8;
        p[10][4] = 5;
        p[10][5] = 0;
        p[10][6] = 0;
        p[10][7] = 0;
        p[10][8] = 0;
        p[10][9] = 5;
        p[10][10] = 5;
        p[10][11] = 8;
        p[11][3] = 8;
        p[11][4] = 5;
        p[11][5] = 5;
        p[11][6] = 0;
        p[11][7] = 0;
        p[11][8] = 0;
        p[11][9] = 5;
        p[11][10] = 5;
        p[11][11] = 8;
        p[12][4] = 8;
        p[12][5] = 8;
        p[12][6] = 5;
        p[12][7] = 5;
        p[12][8] = 5;
        p[12][9] = 8;
        p[12][10] = 8;
        p[13][6] = 8;
        p[13][7] = 8;
        p[13][8] = 8;
        return p;
    }


            private byte[][] createPocketWatchPattern2() {
        byte[][] p = createEmptyCanvas();
        // 怀表 - 外壳橙色(8号)和黄色(5号)，内心圆形表盘蓝色(4号)
        p[1][11] = 8;
        p[1][9] = 5;
        p[1][8] = 5;
        p[1][7] = 5;
        p[2][13] = 8;
        p[2][12] = 8;
        p[2][10] = 8;
        p[2][9] = 8;
        p[2][8] = 5;
        p[3][14] = 8;
        p[3][9] = 5;
        p[3][8] = 5;
        p[3][7] = 5;
        p[4][15] = 8;
        p[4][11] = 5;
        p[4][10] = 5;
        p[4][9] = 8;
        p[4][8] = 8;
        p[4][7] = 8;
        p[4][6] = 5;
        p[4][5] = 5;
        p[5][15] = 8;
        p[5][12] = 5;
        p[5][11] = 8;
        p[5][10] = 8;
        p[5][6] = 8;
        p[5][5] = 8;
        p[5][4] = 5;
        p[6][15] = 8;
        p[6][12] = 5;
        p[6][11] = 8;
        p[6][9] = 4;  // 蓝色表盘
        p[6][8] = 4;
        p[6][7] = 4;
        p[6][6] = 5;
        p[6][5] = 8;
        p[6][4] = 5;
        p[7][15] = 8;
        p[7][13] = 5;
        p[7][12] = 8;
        p[7][8] = 4;  // 蓝色表盘
        p[7][7] = 5;
        p[7][6] = 4;
        p[7][4] = 5;
        p[7][3] = 5;
        p[8][13] = 5;
        p[8][12] = 8;
        p[8][8] = 8;
        p[8][7] = 4;  // 蓝色表盘
        p[8][6] = 4;
        p[8][5] = 4;
        p[8][4] = 5;
        p[8][3] = 5;
        p[9][13] = 5;
        p[9][12] = 8;
        p[9][7] = 5;
        p[9][6] = 4;  // 蓝色表盘
        p[9][5] = 4;
        p[9][4] = 4;
        p[9][3] = 5;
        p[10][12] = 5;
        p[10][11] = 8;
        p[10][6] = 4;  // 蓝色表盘
        p[10][5] = 4;
        p[10][4] = 5;
        p[11][12] = 5;
        p[11][11] = 5;
        p[11][10] = 5;
        p[11][6] = 5;
        p[11][5] = 5;
        p[11][4] = 5;
        p[12][11] = 5;
        p[12][10] = 5;
        p[12][9] = 5;
        p[12][8] = 5;
        p[12][7] = 5;
        p[12][6] = 5;
        p[12][5] = 5;
        p[13][9] = 8;
        p[13][8] = 8;
        p[13][7] = 8;
        return p;
    }

            private byte[][] createVitaminPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][4] = 0;
        p[0][5] = 0;
        p[0][6] = 0;
        p[0][7] = 0;
        p[0][8] = 0;
        p[0][9] = 0;
        p[0][10] = 0;
        p[0][11] = 0;
        p[1][3] = 0;
        p[1][4] = 11;
        p[1][5] = 10;
        p[1][6] = 11;
        p[1][7] = 10;
        p[1][8] = 11;
        p[1][9] = 10;
        p[1][10] = 11;
        p[1][11] = 10;
        p[1][12] = 0;
        p[2][3] = 0;
        p[2][4] = 11;
        p[2][5] = 10;
        p[2][6] = 11;
        p[2][7] = 10;
        p[2][8] = 11;
        p[2][9] = 10;
        p[2][10] = 11;
        p[2][11] = 10;
        p[2][12] = 0;
        p[3][3] = 0;
        p[3][4] = 0;
        p[3][5] = 0;
        p[3][6] = 0;
        p[3][7] = 0;
        p[3][8] = 0;
        p[3][9] = 0;
        p[3][10] = 0;
        p[3][11] = 0;
        p[3][12] = 0;
        p[4][4] = 0;
        p[4][5] = 11;
        p[4][6] = 11;
        p[4][7] = 11;
        p[4][8] = 11;
        p[4][9] = 11;
        p[4][10] = 11;
        p[4][11] = 0;
        p[5][3] = 10;
        p[5][4] = 11;
        p[5][5] = 11;
        p[5][6] = 11;
        p[5][7] = 11;
        p[5][8] = 11;
        p[5][9] = 11;
        p[5][10] = 11;
        p[5][11] = 11;
        p[5][12] = 10;
        p[6][2] = 10;
        p[6][3] = 11;
        p[6][4] = 11;
        p[6][5] = 11;
        p[6][6] = 11;
        p[6][7] = 11;
        p[6][8] = 11;
        p[6][9] = 11;
        p[6][10] = 11;
        p[6][11] = 11;
        p[6][12] = 11;
        p[6][13] = 10;
        p[7][2] = 10;
        p[7][3] = 5;
        p[7][4] = 5;
        p[7][5] = 5;
        p[7][6] = 8;
        p[7][7] = 8;
        p[7][8] = 8;
        p[7][9] = 8;
        p[7][10] = 5;
        p[7][11] = 5;
        p[7][12] = 5;
        p[7][13] = 10;
        p[8][2] = 10;
        p[8][3] = 5;
        p[8][4] = 5;
        p[8][5] = 5;
        p[8][6] = 8;
        p[8][7] = 5;
        p[8][8] = 5;
        p[8][9] = 8;
        p[8][10] = 5;
        p[8][11] = 5;
        p[8][12] = 5;
        p[8][13] = 10;
        p[9][2] = 10;
        p[9][3] = 5;
        p[9][4] = 5;
        p[9][5] = 5;
        p[9][6] = 8;
        p[9][7] = 5;
        p[9][8] = 5;
        p[9][9] = 5;
        p[9][10] = 5;
        p[9][11] = 5;
        p[9][12] = 5;
        p[9][13] = 10;
        p[10][2] = 10;
        p[10][3] = 5;
        p[10][4] = 5;
        p[10][5] = 5;
        p[10][6] = 8;
        p[10][7] = 5;
        p[10][8] = 5;
        p[10][9] = 8;
        p[10][10] = 5;
        p[10][11] = 5;
        p[10][12] = 5;
        p[10][13] = 10;
        p[11][2] = 10;
        p[11][3] = 5;
        p[11][4] = 5;
        p[11][5] = 5;
        p[11][6] = 8;
        p[11][7] = 8;
        p[11][8] = 8;
        p[11][9] = 8;
        p[11][10] = 5;
        p[11][11] = 5;
        p[11][12] = 5;
        p[11][13] = 10;
        p[12][2] = 10;
        p[12][3] = 11;
        p[12][4] = 11;
        p[12][5] = 11;
        p[12][6] = 11;
        p[12][7] = 11;
        p[12][8] = 11;
        p[12][9] = 11;
        p[12][10] = 11;
        p[12][11] = 11;
        p[12][12] = 11;
        p[12][13] = 10;
        p[13][2] = 10;
        p[13][3] = 11;
        p[13][4] = 11;
        p[13][5] = 11;
        p[13][6] = 11;
        p[13][7] = 11;
        p[13][8] = 11;
        p[13][9] = 11;
        p[13][10] = 11;
        p[13][11] = 11;
        p[13][12] = 11;
        p[13][13] = 10;
        p[14][3] = 10;
        p[14][4] = 10;
        p[14][5] = 10;
        p[14][6] = 10;
        p[14][7] = 10;
        p[14][8] = 10;
        p[14][9] = 10;
        p[14][10] = 10;
        p[14][11] = 10;
        p[14][12] = 10;
        return p;
    }


            private byte[][] createVitaminPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][11] = 12;
        p[1][10] = 10;
        p[1][9] = 11;
        p[1][8] = 11;
        p[1][7] = 11;
        p[1][6] = 10;
        p[1][5] = 11;
        p[1][4] = 10;
        p[2][10] = 10;
        p[2][9] = 11;
        p[2][8] = 10;
        p[2][7] = 11;
        p[2][6] = 10;
        p[2][5] = 11;
        p[2][4] = 10;
        p[4][10] = 11;
        p[4][9] = 10;
        p[4][8] = 11;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][5] = 10;
        p[5][12] = 10;
        p[5][11] = 11;
        p[5][10] = 11;
        p[5][9] = 11;
        p[5][8] = 11;
        p[5][7] = 11;
        p[5][6] = 11;
        p[5][5] = 11;
        p[5][4] = 11;
        p[5][3] = 10;
        p[6][13] = 10;
        p[6][12] = 11;
        p[6][11] = 11;
        p[6][10] = 11;
        p[6][9] = 11;
        p[6][8] = 11;
        p[6][7] = 11;
        p[6][6] = 11;
        p[6][5] = 11;
        p[6][4] = 11;
        p[6][3] = 11;
        p[6][2] = 10;
        p[7][13] = 10;
        p[7][12] = 12;
        p[7][11] = 13;
        p[7][10] = 13;
        p[7][9] = 8;
        p[7][8] = 8;
        p[7][7] = 8;
        p[7][6] = 8;
        p[7][5] = 13;
        p[7][4] = 13;
        p[7][3] = 13;
        p[7][2] = 10;
        p[8][13] = 10;
        p[8][12] = 12;
        p[8][11] = 13;
        p[8][10] = 13;
        p[8][9] = 8;
        p[8][8] = 13;
        p[8][7] = 13;
        p[8][6] = 8;
        p[8][5] = 13;
        p[8][4] = 13;
        p[8][3] = 13;
        p[8][2] = 10;
        p[9][13] = 10;
        p[9][12] = 12;
        p[9][11] = 13;
        p[9][10] = 13;
        p[9][9] = 8;
        p[9][8] = 13;
        p[9][7] = 13;
        p[9][6] = 13;
        p[9][5] = 13;
        p[9][4] = 13;
        p[9][3] = 13;
        p[9][2] = 10;
        p[10][13] = 10;
        p[10][12] = 12;
        p[10][11] = 13;
        p[10][10] = 13;
        p[10][9] = 8;
        p[10][8] = 13;
        p[10][7] = 13;
        p[10][6] = 8;
        p[10][5] = 13;
        p[10][4] = 13;
        p[10][3] = 13;
        p[10][2] = 10;
        p[11][13] = 10;
        p[11][12] = 12;
        p[11][11] = 13;
        p[11][10] = 13;
        p[11][9] = 8;
        p[11][8] = 8;
        p[11][7] = 8;
        p[11][6] = 8;
        p[11][5] = 13;
        p[11][4] = 13;
        p[11][3] = 13;
        p[11][2] = 10;
        p[12][13] = 10;
        p[12][12] = 11;
        p[12][11] = 10;
        p[12][10] = 11;
        p[12][9] = 11;
        p[12][8] = 10;
        p[12][7] = 10;
        p[12][6] = 11;
        p[12][5] = 11;
        p[12][4] = 11;
        p[12][3] = 10;
        p[12][2] = 10;
        p[13][12] = 10;
        p[13][11] = 10;
        p[13][10] = 11;
        p[13][9] = 11;
        p[13][8] = 10;
        p[13][7] = 10;
        p[13][6] = 11;
        p[13][5] = 11;
        p[13][4] = 11;
        p[13][3] = 10;
        return p;
    }

            private byte[][] createFireAxePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][9] = 12;
        p[1][10] = 12;
        p[2][8] = 12;
        p[2][9] = 1;
        p[2][10] = 1;
        p[2][11] = 12;
        p[3][7] = 12;
        p[3][8] = 1;
        p[3][9] = 2;
        p[3][10] = 2;
        p[3][11] = 12;
        p[4][6] = 12;
        p[4][7] = 1;
        p[4][8] = 2;
        p[4][9] = 2;
        p[4][10] = 2;
        p[4][11] = 12;
        p[4][12] = 12;
        p[5][6] = 12;
        p[5][7] = 1;
        p[5][8] = 2;
        p[5][9] = 2;
        p[5][10] = 2;
        p[5][11] = 2;
        p[5][12] = 12;
        p[6][7] = 12;
        p[6][8] = 12;
        p[6][9] = 12;
        p[6][10] = 2;
        p[6][11] = 2;
        p[6][12] = 2;
        p[6][13] = 12;
        p[7][8] = 8;
        p[7][9] = 8;
        p[7][10] = 12;
        p[7][11] = 2;
        p[7][12] = 2;
        p[7][13] = 12;
        p[8][7] = 8;
        p[8][8] = 8;
        p[8][9] = 8;
        p[8][11] = 12;
        p[8][12] = 12;
        p[9][6] = 8;
        p[9][7] = 8;
        p[9][8] = 8;
        p[10][5] = 8;
        p[10][6] = 8;
        p[10][7] = 8;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 8;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][4] = 15;
        p[14][2] = 15;
        p[14][3] = 15;
        return p;
    }


            private byte[][] createFireAxePattern2() {
        
        byte[][] p = createEmptyCanvas();
        p[1][9] = 12;
        p[1][10] = 12;
        p[2][8] = 12;
        p[2][9] = 1;
        p[2][10] = 1;
        p[2][11] = 12;
        p[3][7] = 12;
        p[3][8] = 1;
        p[3][9] = 2;
        p[3][10] = 2;
        p[3][11] = 12;
        p[4][6] = 12;
        p[4][7] = 1;
        p[4][8] = 2;
        p[4][9] = 2;
        p[4][10] = 2;
        p[4][11] = 12;
        p[4][12] = 12;
        p[5][6] = 12;
        p[5][7] = 1;
        p[5][8] = 2;
        p[5][9] = 2;
        p[5][10] = 2;
        p[5][11] = 2;
        p[5][12] = 12;
        p[6][7] = 12;
        p[6][8] = 12;
        p[6][9] = 12;
        p[6][10] = 2;
        p[6][11] = 2;
        p[6][12] = 2;
        p[6][13] = 12;
        p[7][8] = 5;
        p[7][9] = 5;
        p[7][10] = 12;
        p[7][11] = 2;
        p[7][12] = 2;
        p[7][13] = 12;
        p[8][7] = 5;
        p[8][8] = 5;
        p[8][9] = 5;
        p[8][11] = 12;
        p[8][12] = 12;
        p[9][6] = 5;
        p[9][7] = 5;
        p[9][8] = 5;
        p[10][5] = 5;
        p[10][6] = 5;
        p[10][7] = 5;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 5;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][4] = 15;
        p[14][2] = 15;
        p[14][3] = 15;
        return p;
    }


        private byte[][] createThrowingKnifePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][13] = 10;
        p[1][14] = 10;
        p[2][12] = 10;
        p[2][13] = 11;
        p[2][14] = 10;
        p[3][11] = 10;
        p[3][12] = 11;
        p[3][13] = 10;
        p[4][10] = 10;
        p[4][11] = 11;
        p[4][12] = 10;
        p[5][8] = 10;
        p[5][9] = 10;
        p[5][10] = 11;
        p[5][11] = 10;
        p[6][7] = 10;
        p[6][8] = 11;
        p[6][9] = 11;
        p[6][10] = 10;
        p[7][6] = 10;
        p[7][7] = 11;
        p[7][8] = 10;
        p[7][9] = 11;
        p[7][10] = 10;
        p[8][3] = 15;
        p[8][5] = 10;
        p[8][6] = 11;
        p[8][7] = 10;
        p[8][8] = 11;
        p[8][9] = 10;
        p[9][2] = 15;
        p[9][3] = 15;
        p[9][5] = 10;
        p[9][6] = 10;
        p[9][7] = 11;
        p[9][8] = 10;
        p[10][2] = 15;
        p[10][3] = 15;
        p[10][4] = 15;
        p[10][5] = 10;
        p[10][6] = 10;
        p[10][7] = 10;
        p[11][3] = 15;
        p[11][4] = 15;
        p[11][5] = 15;
        p[12][2] = 15;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[12][6] = 15;
        p[12][7] = 15;
        p[13][1] = 15;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][5] = 15;
        p[13][6] = 15;
        p[14][2] = 15;
        return p;
    }


        private byte[][] createThrowingKnifePattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][2] = 12;
        p[1][1] = 12;
        p[2][3] = 10;
        p[2][2] = 11;
        p[2][1] = 12;
        p[3][4] = 10;
        p[3][3] = 11;
        p[3][2] = 10;
        p[4][5] = 10;
        p[4][4] = 11;
        p[4][3] = 10;
        p[5][7] = 12;
        p[5][6] = 10;
        p[5][5] = 11;
        p[5][4] = 10;
        p[6][8] = 10;
        p[6][7] = 11;
        p[6][6] = 11;
        p[6][5] = 10;
        p[7][9] = 10;
        p[7][8] = 11;
        p[7][7] = 10;
        p[7][6] = 11;
        p[7][5] = 12;
        p[8][10] = 12;
        p[8][9] = 11;
        p[8][8] = 10;
        p[8][7] = 11;
        p[8][6] = 10;
        p[9][12] = 12;
        p[9][10] = 12;
        p[9][9] = 10;
        p[9][8] = 11;
        p[9][7] = 10;
        p[10][13] = 12;
        p[10][12] = 12;
        p[10][11] = 12;
        p[10][10] = 12;
        p[10][9] = 12;
        p[10][8] = 12;
        p[11][12] = 12;
        p[11][11] = 12;
        p[11][10] = 12;
        p[12][13] = 12;
        p[12][12] = 12;
        p[12][11] = 12;
        p[12][10] = 12;
        p[12][9] = 12;
        p[13][14] = 12;
        p[13][13] = 12;
        p[13][12] = 12;
        p[14][13] = 12;
        return p;
    }

        private byte[][] createRopePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][11] = 15;
        p[0][12] = 15;
        p[1][9] = 15;
        p[1][10] = 15;
        p[1][11] = 8;
        p[1][12] = 8;
        p[1][13] = 15;
        p[1][14] = 15;
        p[2][8] = 15;
        p[2][9] = 15;
        p[2][10] = 8;
        p[2][11] = 0;
        p[2][12] = 0;
        p[2][13] = 8;
        p[2][14] = 15;
        p[2][15] = 15;
        p[3][8] = 15;
        p[3][9] = 8;
        p[3][10] = 0;
        p[3][11] = 8;
        p[3][12] = 15;
        p[3][13] = 12;
        p[3][14] = 8;
        p[3][15] = 15;
        p[4][7] = 15;
        p[4][8] = 8;
        p[4][9] = 12;
        p[4][10] = 8;
        p[4][11] = 12;
        p[4][12] = 12;
        p[4][13] = 12;
        p[4][14] = 8;
        p[4][15] = 15;
        p[5][6] = 15;
        p[5][7] = 15;
        p[5][8] = 15;
        p[5][9] = 8;
        p[5][10] = 12;
        p[5][11] = 12;
        p[5][13] = 12;
        p[5][14] = 8;
        p[5][15] = 15;
        p[6][5] = 15;
        p[6][6] = 15;
        p[6][7] = 15;
        p[6][8] = 15;
        p[6][9] = 15;
        p[6][10] = 8;
        p[6][11] = 8;
        p[6][12] = 12;
        p[6][13] = 8;
        p[6][14] = 15;
        p[7][5] = 15;
        p[7][6] = 8;
        p[7][7] = 8;
        p[7][8] = 15;
        p[7][9] = 15;
        p[7][10] = 15;
        p[7][11] = 15;
        p[7][12] = 15;
        p[7][13] = 15;
        p[8][3] = 15;
        p[8][4] = 15;
        p[8][5] = 15;
        p[8][6] = 15;
        p[8][7] = 15;
        p[8][8] = 8;
        p[8][9] = 15;
        p[8][10] = 15;
        p[8][11] = 15;
        p[8][12] = 15;
        p[9][2] = 15;
        p[9][3] = 8;
        p[9][4] = 8;
        p[9][5] = 15;
        p[9][6] = 15;
        p[9][7] = 15;
        p[9][8] = 8;
        p[9][9] = 8;
        p[9][10] = 15;
        p[10][1] = 15;
        p[10][2] = 8;
        p[10][3] = 15;
        p[10][4] = 15;
        p[10][5] = 15;
        p[10][6] = 15;
        p[10][7] = 15;
        p[10][8] = 15;
        p[10][9] = 15;
        p[10][10] = 15;
        p[11][1] = 15;
        p[11][2] = 8;
        p[11][3] = 15;
        p[11][4] = 8;
        p[11][5] = 8;
        p[11][6] = 15;
        p[11][7] = 15;
        p[11][8] = 15;
        p[11][9] = 15;
        p[12][0] = 15;
        p[12][1] = 8;
        p[12][2] = 15;
        p[12][3] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        p[12][6] = 8;
        p[12][7] = 15;
        p[13][0] = 15;
        p[13][1] = 8;
        p[13][2] = 15;
        p[13][3] = 15;
        p[13][4] = 15;
        p[13][5] = 8;
        p[13][6] = 15;
        p[13][7] = 15;
        p[14][0] = 15;
        p[14][1] = 8;
        p[14][2] = 8;
        p[14][3] = 8;
        p[14][4] = 8;
        p[14][5] = 15;
        p[14][6] = 15;
        p[15][1] = 15;
        p[15][2] = 15;
        p[15][3] = 15;
        p[15][4] = 15;
        return p;
    }


        private byte[][] createRopePattern2() {
        byte[][] p = createEmptyCanvas();
        p[0][4] = 12;
        p[0][3] = 12;
        p[1][6] = 12;
        p[1][5] = 12;
        p[1][4] = 10;
        p[1][3] = 10;
        p[1][2] = 12;
        p[1][1] = 12;
        p[2][7] = 12;
        p[2][6] = 10;
        p[2][5] = 10;
        p[2][4] = 12;
        p[2][3] = 12;
        p[2][2] = 10;
        p[2][1] = 12;
        p[2][0] = 12;
        p[3][7] = 12;
        p[3][6] = 10;
        p[3][5] = 12;
        p[3][4] = 10;
        p[3][3] = 12;
        p[3][2] = 12;
        p[3][1] = 10;
        p[3][0] = 12;
        p[4][8] = 12;
        p[4][7] = 10;
        p[4][6] = 12;
        p[4][5] = 10;
        p[4][4] = 12;
        p[4][3] = 12;
        p[4][2] = 12;
        p[4][1] = 10;
        p[4][0] = 12;
        p[5][9] = 12;
        p[5][8] = 12;
        p[5][7] = 12;
        p[5][6] = 10;
        p[5][5] = 12;
        p[5][4] = 12;
        p[5][2] = 12;
        p[5][1] = 10;
        p[5][0] = 12;
        p[6][10] = 12;
        p[6][9] = 12;
        p[6][8] = 12;
        p[6][7] = 12;
        p[6][6] = 12;
        p[6][5] = 10;
        p[6][4] = 10;
        p[6][3] = 12;
        p[6][2] = 10;
        p[6][1] = 12;
        p[7][10] = 12;
        p[7][9] = 10;
        p[7][8] = 10;
        p[7][7] = 12;
        p[7][6] = 12;
        p[7][5] = 12;
        p[7][4] = 12;
        p[7][3] = 12;
        p[7][2] = 12;
        p[7][1] = 12;
        p[8][12] = 12;
        p[8][11] = 12;
        p[8][10] = 12;
        p[8][9] = 12;
        p[8][8] = 12;
        p[8][7] = 10;
        p[8][6] = 12;
        p[8][5] = 12;
        p[8][4] = 12;
        p[8][3] = 12;
        p[9][13] = 12;
        p[9][12] = 10;
        p[9][11] = 10;
        p[9][10] = 12;
        p[9][9] = 12;
        p[9][8] = 12;
        p[9][7] = 12;
        p[9][6] = 12;
        p[9][5] = 12;
        p[10][14] = 12;
        p[10][13] = 10;
        p[10][12] = 12;
        p[10][11] = 12;
        p[10][10] = 12;
        p[10][9] = 12;
        p[10][8] = 12;
        p[10][7] = 12;
        p[10][6] = 12;
        p[10][5] = 12;
        p[11][14] = 12;
        p[11][13] = 12;
        p[11][12] = 12;
        p[11][11] = 10;
        p[11][10] = 10;
        p[11][9] = 12;
        p[11][8] = 12;
        p[11][7] = 12;
        p[11][6] = 12;
        p[12][15] = 12;
        p[12][14] = 12;
        p[12][13] = 12;
        p[12][12] = 12;
        p[12][11] = 12;
        p[12][10] = 12;
        p[12][9] = 10;
        p[12][8] = 12;
        p[13][15] = 12;
        p[13][14] = 10;
        p[13][13] = 12;
        p[13][12] = 12;
        p[13][11] = 12;
        p[13][10] = 10;
        p[13][9] = 12;
        p[13][8] = 12;
        p[14][15] = 12;
        p[14][14] = 12;
        p[14][13] = 10;
        p[14][12] = 10;
        p[14][11] = 10;
        p[14][10] = 12;
        p[14][9] = 12;
        p[15][14] = 12;
        p[15][13] = 12;
        p[15][12] = 12;
        p[15][11] = 12;
        return p;
    }

            private byte[][] createExtinguisherPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][8] = 10;
        p[1][9] = 11;
        p[1][10] = 11;
        p[2][7] = 10;
        p[2][8] = 11;
        p[2][9] = 11;
        p[3][6] = 10;
        p[3][7] = 10;
        p[3][8] = 10;
        p[3][9] = 10;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 12;
        p[4][8] = 12;
        p[4][9] = 12;
        p[4][10] = 10;
        p[5][5] = 10;
        p[5][6] = 12;
        p[5][7] = 2;
        p[5][8] = 2;
        p[5][9] = 2;
        p[5][10] = 12;
        p[6][5] = 10;
        p[6][6] = 12;
        p[6][7] = 2;
        p[6][8] = 2;
        p[6][9] = 2;
        p[6][10] = 12;
        p[7][5] = 10;
        p[7][6] = 12;
        p[7][7] = 2;
        p[7][8] = 2;
        p[7][9] = 2;
        p[7][10] = 12;
        p[8][4] = 10;
        p[8][5] = 10;
        p[8][6] = 12;
        p[8][7] = 2;
        p[8][8] = 2;
        p[8][9] = 2;
        p[8][10] = 12;
        p[9][0] = 10;
        p[9][2] = 10;
        p[9][3] = 10;
        p[9][4] = 10;
        p[9][6] = 12;
        p[9][7] = 2;
        p[9][8] = 2;
        p[9][9] = 2;
        p[9][10] = 12;
        p[10][0] = 10;
        p[10][1] = 10;
        p[10][2] = 10;
        p[10][3] = 10;
        p[10][6] = 12;
        p[10][7] = 2;
        p[10][8] = 2;
        p[10][9] = 2;
        p[10][10] = 12;
        p[11][6] = 12;
        p[11][7] = 2;
        p[11][8] = 2;
        p[11][9] = 2;
        p[11][10] = 12;
        p[12][6] = 12;
        p[12][7] = 2;
        p[12][8] = 2;
        p[12][9] = 2;
        p[12][10] = 12;
        p[13][6] = 12;
        p[13][7] = 2;
        p[13][8] = 2;
        p[13][9] = 2;
        p[13][10] = 12;
        p[14][6] = 12;
        p[14][7] = 2;
        p[14][8] = 2;
        p[14][9] = 2;
        p[14][10] = 12;
        p[15][7] = 12;
        p[15][8] = 12;
        p[15][9] = 12;
        return p;
    }


            private byte[][] createExtinguisherPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][7] = 10;
        p[1][6] = 11;
        p[1][5] = 11;
        p[2][8] = 10;
        p[2][7] = 11;
        p[2][6] = 11;
        p[3][9] = 12;
        p[3][8] = 10;
        p[3][7] = 10;
        p[3][6] = 10;
        p[3][5] = 10;
        p[4][10] = 10;
        p[4][9] = 12;
        p[4][8] = 12;
        p[4][7] = 12;
        p[4][6] = 12;
        p[4][5] = 12;
        p[5][10] = 12;
        p[5][9] = 12;
        p[5][8] = 12;
        p[5][7] = 2;
        p[5][6] = 2;
        p[5][5] = 12;
        p[6][10] = 12;
        p[6][9] = 12;
        p[6][8] = 2;
        p[6][7] = 2;
        p[6][6] = 2;
        p[6][5] = 12;
        p[7][10] = 12;
        p[7][9] = 12;
        p[7][8] = 2;
        p[7][7] = 2;
        p[7][6] = 2;
        p[7][5] = 12;
        p[8][11] = 10;
        p[8][10] = 12;
        p[8][9] = 12;
        p[8][8] = 2;
        p[8][7] = 2;
        p[8][6] = 2;
        p[8][5] = 12;
        p[9][15] = 10;
        p[9][13] = 10;
        p[9][12] = 10;
        p[9][11] = 12;
        p[9][9] = 12;
        p[9][8] = 2;
        p[9][7] = 2;
        p[9][6] = 2;
        p[9][5] = 12;
        p[10][15] = 10;
        p[10][14] = 10;
        p[10][13] = 12;
        p[10][12] = 10;
        p[10][9] = 12;
        p[10][8] = 2;
        p[10][7] = 2;
        p[10][6] = 2;
        p[10][5] = 2;
        p[11][9] = 12;
        p[11][8] = 2;
        p[11][7] = 2;
        p[11][6] = 2;
        p[11][5] = 2;
        p[12][9] = 12;
        p[12][8] = 2;
        p[12][7] = 2;
        p[12][6] = 2;
        p[12][5] = 12;
        p[13][9] = 12;
        p[13][8] = 10;
        p[13][7] = 10;
        p[13][6] = 11;
        p[13][5] = 10;
        p[14][9] = 12;
        p[14][8] = 12;
        p[14][7] = 12;
        p[14][6] = 12;
        p[14][5] = 12;
        p[15][8] = 12;
        p[15][7] = 12;
        p[15][6] = 12;
        return p;
    }

        private byte[][] createPassbookPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[1][7] = 11;
        p[1][8] = 11;
        p[2][6] = 11;
        p[2][7] = 1;
        p[2][8] = 1;
        p[2][9] = 11;
        p[3][5] = 11;
        p[3][6] = 1;
        p[3][7] = 1;
        p[3][8] = 1;
        p[3][9] = 1;
        p[3][10] = 11;
        p[4][4] = 11;
        p[4][5] = 1;
        p[4][6] = 1;
        p[4][7] = 1;
        p[4][8] = 1;
        p[4][9] = 1;
        p[4][10] = 1;
        p[4][11] = 11;
        p[5][3] = 11;
        p[5][4] = 1;
        p[5][5] = 1;
        p[5][6] = 1;
        p[5][7] = 1;
        p[5][8] = 1;
        p[5][9] = 1;
        p[5][10] = 1;
        p[5][11] = 1;
        p[5][12] = 11;
        p[6][3] = 11;
        p[6][4] = 11;
        p[6][5] = 1;
        p[6][6] = 1;
        p[6][7] = 1;
        p[6][8] = 1;
        p[6][9] = 1;
        p[6][10] = 1;
        p[6][11] = 11;
        p[6][12] = 11;
        p[7][2] = 11;
        p[7][3] = 1;
        p[7][4] = 11;
        p[7][5] = 1;
        p[7][6] = 1;
        p[7][7] = 1;
        p[7][8] = 1;
        p[7][9] = 1;
        p[7][10] = 1;
        p[7][11] = 11;
        p[7][12] = 1;
        p[7][13] = 11;
        p[8][2] = 11;
        p[8][3] = 1;
        p[8][4] = 1;
        p[8][5] = 11;
        p[8][6] = 1;
        p[8][7] = 1;
        p[8][8] = 1;
        p[8][9] = 1;
        p[8][10] = 11;
        p[8][11] = 1;
        p[8][12] = 1;
        p[8][13] = 11;
        p[9][2] = 11;
        p[9][3] = 1;
        p[9][4] = 1;
        p[9][5] = 1;
        p[9][6] = 11;
        p[9][7] = 1;
        p[9][8] = 1;
        p[9][9] = 11;
        p[9][10] = 1;
        p[9][11] = 1;
        p[9][12] = 1;
        p[9][13] = 11;
        p[10][2] = 11;
        p[10][3] = 1;
        p[10][4] = 1;
        p[10][5] = 1;
        p[10][6] = 1;
        p[10][7] = 11;
        p[10][8] = 11;
        p[10][9] = 1;
        p[10][10] = 1;
        p[10][11] = 1;
        p[10][12] = 1;
        p[10][13] = 11;
        p[11][2] = 11;
        p[11][3] = 1;
        p[11][4] = 1;
        p[11][5] = 1;
        p[11][6] = 11;
        p[11][7] = 1;
        p[11][8] = 1;
        p[11][9] = 11;
        p[11][10] = 1;
        p[11][11] = 1;
        p[11][12] = 1;
        p[11][13] = 11;
        p[12][2] = 11;
        p[12][3] = 1;
        p[12][4] = 1;
        p[12][5] = 11;
        p[12][6] = 1;
        p[12][7] = 1;
        p[12][8] = 1;
        p[12][9] = 1;
        p[12][10] = 11;
        p[12][11] = 1;
        p[12][12] = 1;
        p[12][13] = 11;
        p[13][2] = 11;
        p[13][3] = 11;
        p[13][4] = 11;
        p[13][5] = 1;
        p[13][6] = 1;
        p[13][7] = 1;
        p[13][8] = 1;
        p[13][9] = 1;
        p[13][10] = 1;
        p[13][11] = 11;
        p[13][12] = 11;
        p[13][13] = 11;
        p[14][3] = 11;
        p[14][4] = 11;
        p[14][5] = 11;
        p[14][6] = 11;
        p[14][7] = 11;
        p[14][8] = 11;
        p[14][9] = 11;
        p[14][10] = 11;
        p[14][11] = 11;
        p[14][12] = 11;
        return p;
    }


        private byte[][] createPassbookPattern2() {
        byte[][] p = createEmptyCanvas();
        p[1][8] = 11;
        p[1][7] = 11;
        p[2][9] = 11;
        p[2][8] = 1;
        p[2][7] = 1;
        p[2][6] = 11;
        p[3][10] = 11;
        p[3][9] = 1;
        p[3][8] = 1;
        p[3][7] = 1;
        p[3][6] = 1;
        p[3][5] = 11;
        p[4][11] = 11;
        p[4][10] = 15;
        p[4][9] = 1;
        p[4][8] = 1;
        p[4][7] = 1;
        p[4][6] = 1;
        p[4][5] = 15;
        p[4][4] = 11;
        p[5][12] = 10;
        p[5][11] = 15;
        p[5][10] = 15;
        p[5][9] = 15;
        p[5][8] = 1;
        p[5][7] = 1;
        p[5][6] = 15;
        p[5][5] = 15;
        p[5][4] = 15;
        p[5][3] = 10;
        p[6][12] = 10;
        p[6][11] = 11;
        p[6][10] = 15;
        p[6][9] = 15;
        p[6][8] = 15;
        p[6][7] = 15;
        p[6][6] = 15;
        p[6][5] = 15;
        p[6][4] = 11;
        p[6][3] = 10;
        p[7][13] = 10;
        p[7][12] = 15;
        p[7][11] = 11;
        p[7][10] = 11;
        p[7][9] = 1;
        p[7][8] = 1;
        p[7][7] = 1;
        p[7][6] = 1;
        p[7][5] = 11;
        p[7][4] = 11;
        p[7][3] = 15;
        p[7][2] = 10;
        p[8][13] = 10;
        p[8][12] = 15;
        p[8][11] = 15;
        p[8][10] = 11;
        p[8][9] = 15;
        p[8][8] = 1;
        p[8][7] = 1;
        p[8][6] = 15;
        p[8][5] = 11;
        p[8][4] = 15;
        p[8][3] = 15;
        p[8][2] = 10;
        p[9][13] = 10;
        p[9][12] = 15;
        p[9][11] = 1;
        p[9][10] = 15;
        p[9][9] = 11;
        p[9][8] = 15;
        p[9][7] = 15;
        p[9][6] = 11;
        p[9][5] = 15;
        p[9][4] = 1;
        p[9][3] = 15;
        p[9][2] = 10;
        p[10][13] = 10;
        p[10][12] = 15;
        p[10][11] = 1;
        p[10][10] = 15;
        p[10][9] = 15;
        p[10][8] = 11;
        p[10][7] = 11;
        p[10][6] = 15;
        p[10][5] = 15;
        p[10][4] = 1;
        p[10][3] = 15;
        p[10][2] = 10;
        p[11][13] = 10;
        p[11][12] = 15;
        p[11][11] = 15;
        p[11][10] = 15;
        p[11][9] = 11;
        p[11][8] = 1;
        p[11][7] = 1;
        p[11][6] = 11;
        p[11][5] = 15;
        p[11][4] = 15;
        p[11][3] = 15;
        p[11][2] = 10;
        p[12][13] = 10;
        p[12][12] = 11;
        p[12][11] = 15;
        p[12][10] = 11;
        p[12][9] = 1;
        p[12][8] = 1;
        p[12][7] = 1;
        p[12][6] = 1;
        p[12][5] = 11;
        p[12][4] = 15;
        p[12][3] = 11;
        p[12][2] = 10;
        p[13][13] = 10;
        p[13][12] = 10;
        p[13][11] = 11;
        p[13][10] = 15;
        p[13][9] = 15;
        p[13][8] = 15;
        p[13][7] = 15;
        p[13][6] = 15;
        p[13][5] = 15;
        p[13][4] = 11;
        p[13][3] = 10;
        p[13][2] = 10;
        p[14][12] = 10;
        p[14][11] = 10;
        p[14][10] = 10;
        p[14][9] = 10;
        p[14][8] = 10;
        p[14][7] = 10;
        p[14][6] = 10;
        p[14][5] = 10;
        p[14][4] = 10;
        p[14][3] = 10;
        return p;
    }

        private byte[][] createTimeStopClockPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[0][7] = 10;
        p[0][8] = 10;
        p[0][9] = 10;
        p[1][6] = 10;
        p[1][10] = 10;
        p[2][5] = 5;
        p[2][6] = 10;
        p[2][7] = 5;
        p[2][10] = 10;
        p[3][4] = 5;
        p[3][7] = 10;
        p[3][8] = 10;
        p[3][9] = 10;
        p[4][3] = 5;
        p[4][6] = 5;
        p[4][8] = 10;
        p[5][2] = 5;
        p[5][7] = 10;
        p[5][8] = 10;
        p[5][9] = 10;
        p[5][10] = 10;
        p[6][2] = 5;
        p[6][6] = 10;
        p[6][7] = 1;
        p[6][10] = 0;
        p[6][11] = 10;
        p[7][1] = 5;
        p[7][5] = 10;
        p[7][6] = 0;
        p[7][7] = 1;
        p[7][8] = 1;
        p[7][9] = 0;
        p[7][10] = 1;
        p[7][11] = 1;
        p[7][12] = 10;
        p[8][1] = 5;
        p[8][4] = 10;
        p[8][5] = 1;
        p[8][6] = 1;
        p[8][7] = 0;
        p[8][8] = 1;
        p[8][9] = 0;
        p[8][10] = 1;
        p[8][11] = 1;
        p[8][12] = 1;
        p[8][13] = 10;
        p[9][1] = 5;
        p[9][4] = 10;
        p[9][5] = 1;
        p[9][6] = 1;
        p[9][7] = 2;
        p[9][8] = 0;
        p[9][9] = 1;
        p[9][10] = 1;
        p[9][11] = 0;
        p[9][12] = 0;
        p[9][13] = 10;
        p[10][1] = 5;
        p[10][4] = 10;
        p[10][5] = 1;
        p[10][6] = 2;
        p[10][7] = 1;
        p[10][8] = 1;
        p[10][9] = 0;
        p[10][10] = 1;
        p[10][11] = 1;
        p[10][12] = 1;
        p[10][13] = 10;
        p[11][1] = 5;
        p[11][5] = 2;
        p[11][6] = 1;
        p[11][7] = 0;
        p[11][9] = 2;
        p[11][10] = 0;
        p[11][11] = 1;
        p[11][12] = 10;
        p[12][1] = 5;
        p[12][5] = 12;
        p[12][6] = 0;
        p[12][7] = 2;
        p[12][9] = 1;
        p[12][10] = 1;
        p[12][11] = 10;
        p[13][1] = 5;
        p[13][4] = 5;
        p[13][6] = 12;
        p[13][7] = 10;
        p[13][8] = 10;
        p[13][9] = 10;
        p[13][10] = 10;
        p[14][2] = 5;
        p[14][3] = 5;
        return p;
    }


        private byte[][] createTimeStopClockPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][9] = 10;
        p[2][5] = 10;
        p[3][8] = 10;
        p[3][7] = 10;
        p[3][6] = 10;
        p[4][7] = 10;
        p[5][8] = 10;
        p[5][7] = 10;
        p[5][6] = 10;
        p[5][5] = 10;
        p[6][9] = 10;
        p[6][8] = 15;
        p[6][7] = 10;
        p[6][6] = 11;
        p[6][5] = 10;
        p[6][4] = 10;
        p[7][10] = 10;
        p[7][9] = 11;
        p[7][8] = 1;
        p[7][7] = 1;
        p[7][6] = 10;
        p[7][5] = 11;
        p[7][4] = 10;
        p[7][3] = 10;
        p[8][11] = 10;
        p[8][10] = 1;
        p[8][9] = 1;
        p[8][8] = 11;
        p[8][7] = 11;
        p[8][6] = 11;
        p[8][5] = 1;
        p[8][4] = 1;
        p[8][3] = 10;
        p[9][11] = 10;
        p[9][10] = 10;
        p[9][9] = 15;
        p[9][8] = 10;
        p[9][7] = 10;
        p[9][6] = 11;
        p[9][5] = 1;
        p[9][4] = 11;
        p[9][3] = 10;
        p[9][2] = 12;
        p[10][11] = 10;
        p[10][10] = 10;
        p[10][9] = 11;
        p[10][8] = 1;
        p[10][7] = 11;
        p[10][6] = 11;
        p[10][5] = 1;
        p[10][4] = 15;
        p[10][3] = 10;
        p[11][10] = 12;
        p[11][9] = 10;
        p[11][8] = 11;
        p[11][7] = 11;
        p[11][6] = 11;
        p[11][5] = 11;
        p[11][4] = 11;
        p[11][3] = 10;
        p[12][10] = 12;
        p[12][9] = 10;
        p[12][8] = 10;
        p[12][7] = 11;
        p[12][6] = 15;
        p[12][5] = 11;
        p[12][4] = 10;
        p[13][15] = 10;
        p[13][9] = 12;
        p[13][8] = 10;
        p[13][7] = 10;
        p[13][6] = 10;
        p[13][5] = 10;
        return p;
    }

        private byte[][] createShisiyePattern() {
        byte[][] p = createEmptyCanvas();
        // 十四夜 - 碗的颜色是4号蓝色，内容物椭圆形的部分是白色(1号)和5号黄色
        // 碗顶部
        p[6][4] = 4;
        p[6][5] = 4;
        p[6][6] = 4;
        p[6][7] = 4;
        p[6][8] = 4;
        p[6][9] = 4;
        p[6][10] = 4;
        p[6][11] = 4;
        p[7][3] = 4;
        p[7][4] = 4;
        p[7][5] = 4;
        p[7][6] = 4;
        p[7][7] = 4;
        p[7][8] = 4;
        p[7][9] = 4;
        p[7][10] = 4;
        p[7][11] = 4;
        p[7][12] = 4;
        // 碗身 - 内容物（白色和黄色）
        p[8][2] = 4;
        p[8][3] = 4;
        p[8][4] = 4;
        p[8][5] = 4;
        p[8][6] = 1;  // 白色
        p[8][7] = 5;  // 黄色
        p[8][8] = 5;  // 黄色
        p[8][9] = 5;  // 黄色
        p[8][10] = 1;  // 白色
        p[8][11] = 4;
        p[8][12] = 4;
        p[8][13] = 4;
        p[9][2] = 4;
        p[9][3] = 4;
        p[9][4] = 4;
        p[9][5] = 4;
        p[9][6] = 5;  // 黄色
        p[9][7] = 5;  // 黄色
        p[9][8] = 1;  // 白色
        p[9][9] = 1;  // 白色
        p[9][10] = 5;  // 黄色
        p[9][11] = 5;  // 黄色
        p[9][12] = 4;
        p[9][13] = 4;
        p[10][2] = 4;
        p[10][3] = 4;
        p[10][4] = 4;
        p[10][5] = 4;
        p[10][6] = 5;  // 黄色
        p[10][7] = 5;  // 黄色
        p[10][8] = 1;  // 白色
        p[10][9] = 1;  // 白色
        p[10][10] = 5;  // 黄色
        p[10][11] = 4;
        p[10][12] = 4;
        p[10][13] = 4;
        // 碗底部
        p[11][3] = 4;
        p[11][4] = 4;
        p[11][5] = 4;
        p[11][6] = 4;
        p[11][7] = 4;
        p[11][8] = 4;
        p[11][9] = 4;
        p[11][10] = 4;
        p[11][11] = 4;
        p[11][12] = 4;
        p[12][4] = 4;
        p[12][5] = 4;
        p[12][6] = 4;
        p[12][7] = 4;
        p[12][8] = 4;
        p[12][9] = 4;
        p[12][10] = 4;
        p[12][11] = 4;
        p[13][5] = 4;
        p[13][6] = 4;
        p[13][7] = 4;
        p[13][8] = 4;
        p[13][9] = 4;
        p[13][10] = 4;
        return p;
    }

        private byte[][] createShisiyePattern2() {
        byte[][] p = createEmptyCanvas();
        // 十四夜 - 碗的颜色是4号蓝色，内容物椭圆形的部分是白色(1号)和5号黄色
        p[6][11] = 4;
        p[6][10] = 4;
        p[6][9] = 4;
        p[6][8] = 4;
        p[6][7] = 4;
        p[6][6] = 4;
        p[6][5] = 4;
        p[6][4] = 4;
        p[7][12] = 4;
        p[7][11] = 4;
        p[7][10] = 4;
        p[7][9] = 4;
        p[7][8] = 4;
        p[7][7] = 4;
        p[7][6] = 4;
        p[7][5] = 4;
        p[7][4] = 4;
        p[7][3] = 4;
        p[8][13] = 4;
        p[8][12] = 4;
        p[8][11] = 4;
        p[8][10] = 4;
        p[8][9] = 1;  // 白色
        p[8][8] = 5;  // 黄色
        p[8][7] = 5;  // 黄色
        p[8][6] = 5;  // 黄色
        p[8][5] = 1;  // 白色
        p[8][4] = 4;
        p[8][3] = 4;
        p[8][2] = 4;
        p[9][13] = 4;
        p[9][12] = 4;
        p[9][11] = 5;  // 黄色
        p[9][10] = 5;  // 黄色
        p[9][9] = 1;  // 白色
        p[9][8] = 1;  // 白色
        p[9][7] = 5;  // 黄色
        p[9][6] = 5;  // 黄色
        p[9][5] = 5;  // 黄色
        p[9][4] = 4;
        p[9][3] = 4;
        p[9][2] = 4;
        p[10][13] = 4;
        p[10][12] = 4;
        p[10][11] = 4;
        p[10][10] = 4;
        p[10][9] = 5;  // 黄色
        p[10][8] = 5;  // 黄色
        p[10][7] = 1;  // 白色
        p[10][6] = 1;  // 白色
        p[10][5] = 5;  // 黄色
        p[10][4] = 4;
        p[10][3] = 4;
        p[10][2] = 4;
        p[11][12] = 4;
        p[11][11] = 4;
        p[11][10] = 4;
        p[11][9] = 4;
        p[11][8] = 4;
        p[11][7] = 4;
        p[11][6] = 4;
        p[11][5] = 4;
        p[11][4] = 4;
        p[11][3] = 4;
        p[12][11] = 4;
        p[12][10] = 4;
        p[12][9] = 4;
        p[12][8] = 4;
        p[12][7] = 4;
        p[12][6] = 4;
        p[12][5] = 4;
        p[12][4] = 4;
        p[13][10] = 4;
        p[13][9] = 4;
        p[13][8] = 4;
        p[13][7] = 4;
        p[13][6] = 4;
        p[13][5] = 4;
        return p;
    }

        private byte[][] createProblemSetPattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][1] = 13;
        p[2][2] = 13;
        p[2][3] = 13;
        p[2][4] = 13;
        p[2][5] = 13;
        p[2][6] = 13;
        p[2][7] = 13;
        p[2][8] = 13;
        p[2][9] = 13;
        p[2][11] = 13;
        p[2][12] = 13;
        p[2][13] = 13;
        p[2][14] = 13;
        p[2][15] = 13;
        p[3][1] = 13;
        p[3][9] = 13;
        p[3][11] = 13;
        p[3][12] = 3;
        p[3][13] = 3;
        p[3][14] = 3;
        p[3][15] = 13;
        p[4][1] = 13;
        p[4][3] = 13;
        p[4][4] = 13;
        p[4][5] = 13;
        p[4][6] = 13;
        p[4][7] = 13;
        p[4][8] = 3;
        p[4][9] = 13;
        p[4][11] = 13;
        p[4][12] = 3;
        p[4][13] = 3;
        p[4][14] = 3;
        p[4][15] = 13;
        p[5][1] = 13;
        p[5][3] = 3;
        p[5][4] = 3;
        p[5][5] = 3;
        p[5][6] = 3;
        p[5][7] = 3;
        p[5][8] = 3;
        p[5][9] = 13;
        p[5][11] = 13;
        p[5][12] = 13;
        p[5][13] = 13;
        p[5][14] = 13;
        p[5][15] = 13;
        p[6][1] = 13;
        p[6][3] = 3;
        p[6][4] = 3;
        p[6][5] = 3;
        p[6][6] = 3;
        p[6][7] = 3;
        p[6][8] = 3;
        p[6][9] = 13;
        p[6][11] = 13;
        p[6][15] = 13;
        p[7][1] = 13;
        p[7][3] = 13;
        p[7][4] = 13;
        p[7][5] = 13;
        p[7][6] = 13;
        p[7][7] = 13;
        p[7][8] = 3;
        p[7][9] = 13;
        p[7][11] = 13;
        p[7][15] = 13;
        p[8][1] = 13;
        p[8][3] = 3;
        p[8][4] = 3;
        p[8][5] = 3;
        p[8][6] = 3;
        p[8][7] = 3;
        p[8][8] = 3;
        p[8][9] = 13;
        p[8][11] = 13;
        p[8][15] = 13;
        p[9][1] = 13;
        p[9][3] = 3;
        p[9][4] = 3;
        p[9][5] = 3;
        p[9][6] = 3;
        p[9][7] = 3;
        p[9][8] = 3;
        p[9][9] = 13;
        p[9][11] = 13;
        p[9][15] = 13;
        p[10][1] = 13;
        p[10][3] = 13;
        p[10][4] = 13;
        p[10][5] = 13;
        p[10][6] = 13;
        p[10][7] = 13;
        p[10][8] = 3;
        p[10][9] = 13;
        p[10][11] = 13;
        p[10][15] = 13;
        p[11][1] = 13;
        p[11][3] = 3;
        p[11][4] = 3;
        p[11][5] = 3;
        p[11][6] = 3;
        p[11][7] = 3;
        p[11][8] = 3;
        p[11][9] = 13;
        p[11][11] = 13;
        p[11][15] = 13;
        p[12][1] = 13;
        p[12][3] = 3;
        p[12][4] = 3;
        p[12][5] = 3;
        p[12][6] = 3;
        p[12][7] = 3;
        p[12][8] = 3;
        p[12][9] = 13;
        p[12][11] = 13;
        p[12][12] = 13;
        p[12][13] = 13;
        p[12][14] = 13;
        p[12][15] = 13;
        p[13][1] = 13;
        p[13][2] = 13;
        p[13][3] = 13;
        p[13][4] = 13;
        p[13][5] = 13;
        p[13][6] = 13;
        p[13][7] = 13;
        p[13][8] = 13;
        p[13][9] = 13;
        p[13][12] = 13;
        p[13][13] = 13;
        p[13][14] = 13;
        p[14][13] = 13;
        return p;
    }


        private byte[][] createProblemSetPattern2() {
        byte[][] p = createEmptyCanvas();
        // 习题集 - 整体应该是13号深绿色和3号绿色
        p[2][14] = 13;
        p[2][13] = 13;
        p[2][12] = 13;
        p[2][11] = 13;
        p[2][10] = 13;
        p[2][9] = 13;
        p[2][8] = 13;
        p[2][7] = 13;
        p[2][6] = 13;
        p[2][4] = 3;
        p[2][3] = 3;
        p[2][2] = 3;
        p[2][1] = 13;
        p[3][14] = 13;
        p[3][6] = 13;
        p[3][4] = 3;
        p[3][3] = 3;
        p[3][2] = 3;
        p[3][1] = 13;
        p[4][14] = 13;
        p[4][12] = 3;
        p[4][11] = 3;
        p[4][10] = 3;
        p[4][9] = 3;
        p[4][8] = 3;
        p[4][7] = 3;
        p[4][6] = 13;
        p[4][4] = 3;
        p[4][3] = 3;
        p[4][2] = 3;
        p[4][1] = 13;
        p[5][14] = 13;
        p[5][12] = 3;
        p[5][11] = 3;
        p[5][10] = 3;
        p[5][9] = 3;
        p[5][8] = 3;
        p[5][7] = 3;
        p[5][6] = 13;
        p[5][4] = 13;
        p[5][1] = 13;
        p[6][14] = 13;
        p[6][12] = 13;
        p[6][11] = 13;
        p[6][10] = 13;
        p[6][9] = 13;
        p[6][8] = 13;
        p[6][7] = 3;
        p[6][6] = 13;
        p[6][4] = 13;
        p[6][1] = 13;
        p[7][14] = 13;
        p[7][12] = 3;
        p[7][11] = 3;
        p[7][10] = 3;
        p[7][9] = 3;
        p[7][8] = 3;
        p[7][7] = 3;
        p[7][6] = 13;
        p[7][4] = 13;
        p[7][1] = 13;
        p[8][14] = 14;
        p[8][12] = 10;
        p[8][11] = 10;
        p[8][10] = 10;
        p[8][9] = 10;
        p[8][8] = 10;
        p[8][7] = 10;
        p[8][6] = 14;
        p[8][4] = 14;
        p[8][1] = 14;
        p[9][14] = 14;
        p[9][12] = 11;
        p[9][11] = 11;
        p[9][10] = 11;
        p[9][9] = 11;
        p[9][8] = 11;
        p[9][7] = 10;
        p[9][6] = 14;
        p[9][4] = 14;
        p[9][1] = 14;
        p[10][14] = 14;
        p[10][12] = 10;
        p[10][11] = 10;
        p[10][10] = 10;
        p[10][9] = 10;
        p[10][8] = 10;
        p[10][7] = 10;
        p[10][6] = 14;
        p[10][4] = 14;
        p[10][1] = 14;
        p[11][14] = 14;
        p[11][12] = 10;
        p[11][11] = 10;
        p[11][10] = 10;
        p[11][9] = 10;
        p[11][8] = 10;
        p[11][7] = 10;
        p[11][6] = 14;
        p[11][4] = 14;
        p[11][1] = 14;
        p[12][14] = 14;
        p[12][12] = 11;
        p[12][11] = 11;
        p[12][10] = 11;
        p[12][9] = 11;
        p[12][8] = 11;
        p[12][7] = 10;
        p[12][6] = 14;
        p[12][4] = 14;
        p[12][3] = 14;
        p[12][2] = 14;
        p[13][14] = 14;
        p[13][13] = 14;
        p[13][12] = 14;
        p[13][11] = 14;
        p[13][10] = 14;
        p[13][9] = 14;
        p[13][8] = 10;
        p[13][7] = 14;
        p[13][6] = 14;
        p[13][3] = 14;
        return p;
    }

    // ==================== 回形针图案 ====================
    // 回形针 - U形弯曲的金属线
            private byte[][] createPaperclipPattern() {
        byte[][] p = createEmptyCanvas();
        p[2][7] = 10;
        p[2][8] = 10;
        p[2][9] = 10;
        p[3][6] = 10;
        p[3][10] = 10;
        p[4][5] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][11] = 10;
        p[5][5] = 10;
        p[5][7] = 10;
        p[5][9] = 10;
        p[5][11] = 10;
        p[6][5] = 10;
        p[6][7] = 10;
        p[6][9] = 10;
        p[6][11] = 10;
        p[7][5] = 10;
        p[7][7] = 10;
        p[7][9] = 10;
        p[7][11] = 10;
        p[8][5] = 10;
        p[8][7] = 10;
        p[8][9] = 10;
        p[8][11] = 10;
        p[9][5] = 10;
        p[9][7] = 10;
        p[9][9] = 10;
        p[9][11] = 10;
        p[10][5] = 10;
        p[10][7] = 10;
        p[10][9] = 10;
        p[10][11] = 10;
        p[11][7] = 10;
        p[11][9] = 10;
        p[11][11] = 10;
        p[12][7] = 10;
        p[12][9] = 10;
        p[12][11] = 10;
        p[13][7] = 10;
        p[13][11] = 10;
        p[14][8] = 10;
        p[14][9] = 10;
        p[14][10] = 10;
        return p;
    }

            private byte[][] createPaperclipPattern2() {
        byte[][] p = createEmptyCanvas();
        p[2][8] = 10;
        p[2][7] = 10;
        p[2][6] = 10;
        p[3][9] = 10;
        p[3][5] = 10;
        p[4][10] = 10;
        p[4][7] = 10;
        p[4][6] = 10;
        p[4][4] = 10;
        p[5][10] = 10;
        p[5][8] = 10;
        p[5][6] = 10;
        p[5][4] = 10;
        p[6][10] = 10;
        p[6][8] = 10;
        p[6][6] = 10;
        p[6][4] = 10;
        p[7][10] = 10;
        p[7][8] = 10;
        p[7][6] = 10;
        p[7][4] = 10;
        p[8][10] = 10;
        p[8][8] = 10;
        p[8][6] = 10;
        p[8][4] = 10;
        p[9][10] = 10;
        p[9][8] = 10;
        p[9][6] = 10;
        p[9][4] = 10;
        p[10][10] = 10;
        p[10][8] = 10;
        p[10][6] = 10;
        p[10][4] = 10;
        p[11][8] = 10;
        p[11][6] = 10;
        p[11][4] = 10;
        p[12][8] = 10;
        p[12][6] = 10;
        p[12][4] = 10;
        p[13][8] = 10;
        p[13][4] = 10;
        p[14][7] = 10;
        p[14][6] = 10;
        p[14][5] = 10;
        return p;
    }

    private byte[][] createPaperclipPattern3() {
        byte[][] p = createEmptyCanvas();
        // 回形针的简化U形结构
        // 右侧垂直部分
        for (int y = 5; y < 10; y++) p[y][10] = 10;
        // 顶部水平部分
        for (int x = 5; x < 11; x++) p[5][x] = 10;
        // 左侧垂直部分
        for (int y = 5; y < 10; y++) p[y][5] = 10;
        // 底部水平部分
        for (int x = 5; x < 11; x++) p[10][x] = 10;
        return p;
    }

    // ==================== 诱饵弹图案 ====================
    // 诱饵弹 - 蛋形手雷形状（整体偏灰色）
    private byte[][] createDecoyGrenadePattern() {
        
        byte[][] p = createEmptyCanvas();
        p[2][11] = 10;
        p[2][12] = 10;
        p[3][9] = 15;
        p[3][10] = 15;
        p[3][11] = 10;
        p[3][12] = 10;
        p[3][13] = 10;
        p[4][8] = 2;
        p[4][9] = 8;
        p[4][10] = 15;
        p[4][11] = 15;
        p[4][12] = 10;
        p[4][13] = 10;
        p[4][14] = 10;
        p[5][7] = 2;
        p[5][8] = 10;
        p[5][9] = 8;
        p[5][10] = 8;
        p[5][11] = 15;
        p[5][13] = 10;
        p[5][14] = 10;
        p[6][6] = 2;
        p[6][7] = 10;
        p[6][8] = 10;
        p[6][9] = 10;
        p[6][10] = 8;
        p[6][11] = 2;
        p[6][14] = 10;
        p[7][5] = 2;
        p[7][6] = 10;
        p[7][7] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[7][10] = 2;
        p[7][13] = 10;
        p[7][14] = 10;
        p[8][4] = 15;
        p[8][5] = 8;
        p[8][6] = 8;
        p[8][7] = 10;
        p[8][8] = 10;
        p[8][9] = 2;
        p[8][12] = 10;
        p[8][13] = 10;
        p[9][3] = 15;
        p[9][4] = 15;
        p[9][5] = 15;
        p[9][6] = 8;
        p[9][7] = 8;
        p[9][8] = 2;
        p[9][11] = 10;
        p[9][12] = 10;
        p[10][2] = 15;
        p[10][3] = 15;
        p[10][4] = 15;
        p[10][5] = 15;
        p[10][6] = 15;
        p[10][7] = 15;
        p[10][10] = 10;
        p[10][11] = 10;
        p[11][3] = 15;
        p[11][4] = 15;
        p[11][5] = 15;
        p[11][6] = 15;
        p[12][4] = 15;
        p[12][5] = 15;
        return p;
    }


    private byte[][] createDecoyGrenadePattern2() {
        byte[][] p = createEmptyCanvas();
        // 诱饵弹 - 整体偏灰色
        p[4][4] = 10;
        p[4][5] = 10;
        p[4][6] = 10;
        p[4][7] = 10;
        p[4][8] = 10;
        p[4][9] = 10;
        p[4][10] = 10;
        p[4][11] = 10;
        p[5][3] = 10;
        p[5][4] = 10;
        p[5][5] = 10;
        p[5][6] = 10;
        p[5][7] = 10;
        p[5][8] = 10;
        p[5][9] = 10;
        p[5][10] = 10;
        p[5][11] = 10;
        p[5][12] = 10;
        p[6][3] = 10;
        p[6][4] = 10;
        p[6][5] = 10;
        p[6][6] = 10;
        p[6][7] = 10;
        p[6][8] = 10;
        p[6][9] = 10;
        p[6][10] = 10;
        p[6][11] = 10;
        p[6][12] = 10;
        p[7][4] = 10;
        p[7][5] = 10;
        p[7][6] = 10;
        p[7][7] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[7][10] = 10;
        p[7][11] = 10;
        p[8][5] = 10;
        p[8][6] = 10;
        p[8][7] = 10;
        p[8][8] = 10;
        p[8][9] = 10;
        p[8][10] = 10;
        p[8][11] = 10;
        p[9][6] = 10;
        p[9][7] = 10;
        p[9][8] = 10;
        p[9][9] = 10;
        p[9][10] = 10;
        p[9][11] = 10;
        p[10][7] = 10;
        p[10][8] = 10;
        p[10][9] = 10;
        p[10][10] = 10;
        p[10][11] = 10;
        p[11][8] = 10;
        p[11][9] = 10;
        p[11][10] = 10;
        p[11][11] = 10;
        return p;
    }

    private byte[][] createDecoyGrenadePattern3() {
        byte[][] p = createEmptyCanvas();
        // 诱饵弹 - 整体偏灰色（紧凑型）
        p[5][6] = 10;
        p[5][7] = 10;
        p[5][8] = 10;
        p[5][9] = 10;
        p[6][5] = 10;
        p[6][6] = 10;
        p[6][7] = 10;
        p[6][8] = 10;
        p[6][9] = 10;
        p[6][10] = 10;
        p[7][4] = 10;
        p[7][5] = 10;
        p[7][6] = 10;
        p[7][7] = 10;
        p[7][8] = 10;
        p[7][9] = 10;
        p[7][10] = 10;
        p[7][11] = 10;
        p[8][4] = 10;
        p[8][5] = 10;
        p[8][6] = 10;
        p[8][7] = 10;
        p[8][8] = 10;
        p[8][9] = 10;
        p[8][10] = 10;
        p[8][11] = 10;
        p[9][5] = 10;
        p[9][6] = 10;
        p[9][7] = 10;
        p[9][8] = 10;
        p[9][9] = 10;
        p[9][10] = 10;
        p[10][6] = 10;
        p[10][7] = 10;
        p[10][8] = 10;
        p[10][9] = 10;
        p[11][7] = 10;
        p[11][8] = 10;
        return p;
    }

    // ==================== 颜色常量定义 ====================
    // 调色板索引（与 DrawingBoardScreen.PALETTE 对应）
    private static final int COLOR_BLACK = 0;
    // 调色盘白色（id=1）- 允许被识别
    private static final int COLOR_PALETTE_WHITE = 1;
    // 背景白色（id=16）- 纯白色，不被识别，视为透明
    private static final int COLOR_BACKGROUND_WHITE = 16;
    private static final int COLOR_WHITE = COLOR_PALETTE_WHITE; // 保持兼容性
    private static final int COLOR_RED = 2;
    private static final int COLOR_GREEN = 3;
    private static final int COLOR_BLUE = 4;
    private static final int COLOR_YELLOW = 5;
    private static final int COLOR_GRAY = 10;
    private static final int COLOR_LIGHT_GRAY = 11;
    private static final int COLOR_DARK_RED = 12;
    private static final int COLOR_DARK_GREEN = 13;
    private static final int COLOR_DARK_BLUE = 14;
    private static final int COLOR_BROWN = 15;

    // ==================== 识别方法 ====================

    /**
     * 识别 16x16 像素数据 - 使用多算法融合
     */
    public int recognize(byte[][] pixels) {
        RecognizeResult result = recognizeWithHint(pixels);
        return result.category;
    }

    /**
     * 带提示信息的识别方法
     * @param pixels 像素矩阵
     * @return 识别结果，包含最接近的类别提示
     */
    public RecognizeResult recognizeWithHint(byte[][] pixels) {
        if (pixels == null || pixels.length != 16 || pixels[0].length != 16) {
            return new RecognizeResult(UNKNOWN);
        }

        // 检查画板是否全部为背景白色(16)，如果是则不识别
        boolean allWhite = true;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if ((pixels[y][x] & 0xFF) != COLOR_BACKGROUND_WHITE) {
                    allWhite = false;
                    break;
                }
            }
            if (!allWhite) break;
        }
        if (allWhite) {
            return new RecognizeResult(UNKNOWN);
        }

        // 复制像素数据以避免修改原始数据
        byte[][] normalizedPixels = normalizeColors(pixels);

        // 精确匹配检查：如果用户画的图案与某个pattern像素色彩、位置完全一致
        // （至多允许4个像素差异），直接给予对应物品
        int exactMatch = checkExactMatch(normalizedPixels);
        if (exactMatch != UNKNOWN) {
            return new RecognizeResult(exactMatch, exactMatch, "", 0, 0, 100.0);
        }

        double[] features = SimpleKNN.matrixToFeature(normalizedPixels, true);

        // 多算法融合检测（与SimpleKNN.predictMultiAlgorithmWithDims一致的权重）
        // 算法严格程度排名（从高到低）:
        // 1. euclidean    - 最严格，逐像素精确匹配（权重6）
        // 2. histogram    - 颜色直方图，统计颜色分布（权重5）
        // 3. colorCount   - 色块数量统计，权重高数量多（权重4）
        // 4. shape        - 宽松形状匹配，允许多余像素（权重3）
        // 5. pureShape    - 忽略颜色，只看轮廓（权重2）
        // 6. centroid     - 最宽松，只看位置和数量（权重1）
        Map<Integer, Integer> votes = new java.util.HashMap<>();

        // euclidean（权重6）- 最严格，逐像素精确匹配
        Map<Integer, Integer> euclideanVotes = knn.getVotesByAlgorithm(features, "euclidean");
        for (Map.Entry<Integer, Integer> entry : euclideanVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 6);
        }

        // histogram（权重5）- 颜色直方图
        Map<Integer, Integer> histogramVotes = knn.getVotesByAlgorithm(features, "histogram");
        for (Map.Entry<Integer, Integer> entry : histogramVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 5);
        }

        // colorCount（权重3）- 色块数量（降低为与 shape 相同）
        Map<Integer, Integer> colorCountVotes = knn.getVotesByAlgorithm(features, "colorCount");
        for (Map.Entry<Integer, Integer> entry : colorCountVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 3);
        }

        // shape（权重3）- 宽松形状匹配，考虑颜色
        Map<Integer, Integer> shapeVotes = knn.getVotesByAlgorithm(features, "shape");
        for (Map.Entry<Integer, Integer> entry : shapeVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 3);
        }

        // 新增：shapeColor 组合算法（权重1） - 结合 shape 与 colorCount 的判断，补回权重总和
        Map<Integer, Integer> shapeColorVotes = knn.getVotesByAlgorithm(features, "shapeColor");
        for (Map.Entry<Integer, Integer> entry : shapeColorVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 1);
        }

        // pureShape（权重2）- 忽略颜色只看轮廓
        Map<Integer, Integer> pureShapeVotes = knn.getVotesByAlgorithm(features, "pureShape");
        for (Map.Entry<Integer, Integer> entry : pureShapeVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 2);
        }

        // centroid（权重1）- 最宽松，只看位置和数量
        Map<Integer, Integer> centroidVotes = knn.getVotesByAlgorithm(features, "centroid", 16, 16);
        for (Map.Entry<Integer, Integer> entry : centroidVotes.entrySet()) {
            votes.put(entry.getKey(), votes.getOrDefault(entry.getKey(), 0) + entry.getValue() * 1);
        }

        // 计算总投票权重
        int totalVoteWeight = 0;
        for (int weight : votes.values()) {
            totalVoteWeight += weight;
        }

        // 返回得票最多的标签（用于识别结果）
        int bestLabel = UNKNOWN;
        int bestCount = 0;
        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                bestLabel = entry.getKey();
            }
        }

        // 计算最高得票率
        double bestVoteRatio = totalVoteWeight > 0 ? (double) bestCount / totalVoteWeight : 0.0;

        // 颜色数量校验：pattern有2+种有效颜色时，用户画布必须至少有2种对应颜色
        if (bestCount >= 2 && bestLabel != UNKNOWN) {
            int requiredColors = categoryEffectiveColorCount.getOrDefault(bestLabel, 1);
            if (requiredColors >= 2) {
                int userColors = countEffectiveColors(normalizedPixels);
                if (userColors < 2) {
                    bestCount = 1;
                    bestLabel = UNKNOWN;
                    bestVoteRatio = totalVoteWeight > 0 ? (double) bestCount / totalVoteWeight : 0.0;
                }
            }
        }

        // 颜色错误率校验：如果颜色错误率达到35%以上，直接降低得票数到2以下
        if (bestCount >= 2) {
            double colorMismatchRatio = calculateColorMismatchRatio(normalizedPixels, bestLabel);
            if (colorMismatchRatio > 0.35) {
                // 颜色错误率超过35%，降低得票数
                bestCount = 1;
                bestVoteRatio = totalVoteWeight > 0 ? (double) bestCount / totalVoteWeight : 0.0;
            }
        }


        // 像素级校验：使用规范化后的像素进行检查（颜色互通生效）
        // 将校验结果作为直接成功的必要条件，但仍允许显示预测提示
        boolean pixelValidationPassed = true;
        PixelValidationResult validationResult = null;
        if (bestCount >= 2) {
            validationResult = validatePixelConstraints(normalizedPixels, bestLabel);
            pixelValidationPassed = validationResult.passed;
        }

        // 根据超标程度减少票数（放宽策略）：取透明超标与遗漏超标中的最大值决定减少量
        // - 透明超标影响更大，但缩减系数降低以避免过度严格
        // - 遗漏超标影响较小
        // - 缩减量不会把票数降到低于可预测最低标准（2票）
        if (validationResult != null && !pixelValidationPassed && bestCount >= 2) {
            double extra = validationResult.extraPixelRatio;
            double missing = validationResult.missingPixelRatio;
            double major = Math.max(extra, missing);

            // 缩减系数：透明 12x，遗漏 6x
            int reduction;
            if (extra >= missing) {
                reduction = (int) Math.ceil(extra * 12.0);
            } else {
                reduction = (int) Math.ceil(missing * 6.0);
            }

            if (reduction <= 0 && major > 0) reduction = 1; // 有超标时至少减少1票

            int minPredict = 2;
            int maxReductionAllowed = Math.max(0, bestCount - minPredict);
            // 限制缩减量不超过可允许的最大值
            reduction = Math.min(reduction, maxReductionAllowed);

            // 严重超标时允许更大的缩减，但仍不低于 minPredict
            if (major >= 0.50 && maxReductionAllowed > 0) {
                reduction = Math.max(reduction, Math.min(2, maxReductionAllowed));
            }

            bestCount = Math.max(minPredict, bestCount - reduction);
            bestVoteRatio = totalVoteWeight > 0 ? (double) bestCount / totalVoteWeight : 0.0;
        }

        // 识别结果三级标准：
        // 1. 直接成功：bestCount >= 15 && bestVoteRatio >= 0.33
        // 2. 预测（提示可能画的）：2 <= bestCount < 15
        // 3. 识别失败（out_of_range）：bestCount < 2

        // 计算相似度：直接成功为100%，识别失败为0%，中间按得票数与投票比例综合归一化
        double similarity = 0.0;
        if (totalVoteWeight > 0) {
            // 归一化得票数部分（将 2 映射为 0，15 映射为 1）
            double countPart = (double) (bestCount - 2) / (15 - 2);
            countPart = Math.max(0.0, Math.min(1.0, countPart));
            // 归一化投票比例部分（将 0 映射为 0，0.33 映射为 1）
            double ratioPart = bestVoteRatio / 0.33;
            ratioPart = Math.max(0.0, Math.min(1.0, ratioPart));
            // 综合相似度为两部分平均
            similarity = ((countPart + ratioPart) / 2.0) * 100.0;
            // 如果像素校验未通过，降低相似度（因为最终无法识别成功）
            if (!pixelValidationPassed && bestCount >= 2) {
                similarity = similarity * 0.7; // 像素校验失败时相似度打7折
            }
        }

        if (bestCount >= 15 && bestVoteRatio >= 0.33 && pixelValidationPassed) {
            // 直接成功
            return new RecognizeResult(bestLabel, bestLabel, "", bestCount, totalVoteWeight, 100.0);
        } else if (bestCount >= 2) {
            // 预测：票数在2-12之间，将权数最高的pattern作为预测，并返回相似度
            return new RecognizeResult(UNKNOWN, bestLabel, generateHintMessage(bestLabel), bestCount, totalVoteWeight, similarity);
        } else {
            // 票数 < 2，识别失败
            return new RecognizeResult(UNKNOWN, UNKNOWN, "starrailexpress.drawing_board.hint.out_of_range", bestCount, totalVoteWeight, 0.0);
        }
    }

    /**
     * 生成提示信息
     * @param closestCategory 最接近的类别
     * @return 提示文本
     */
    private String generateHintMessage(int closestCategory) {
        if (closestCategory == -1) {
            return "";
        }
        return "starrailexpress.drawing_board.hint.closest_category";
    }

    /**
     * 获取最接近类别的翻译键
     * @param closestCategory 最接近的类别
     * @return 翻译键
     */
    public static String getClosestCategoryTranslationKey(int closestCategory) {
        return "starrailexpress.drawing_board.hint.category_" + closestCategory;
    }

    /**
     * 获取最近匹配的类别（不进行严格校验）
     * 用于保底机制
     * @param pixels 像素矩阵
     * @return 最接近的类别，如果无法确定返回 UNKNOWN
     */
    public int getClosestCategory(byte[][] pixels) {
        if (pixels == null || pixels.length != 16 || pixels[0].length != 16) {
            return UNKNOWN;
        }

        // 检查画板是否全部为背景白色
        boolean allWhite = true;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                if ((pixels[y][x] & 0xFF) != COLOR_BACKGROUND_WHITE) {
                    allWhite = false;
                    break;
                }
            }
            if (!allWhite) break;
        }
        if (allWhite) {
            return UNKNOWN;
        }

        // 复制像素数据以避免修改原始数据
        byte[][] normalizedPixels = normalizeColors(pixels);
        double[] features = SimpleKNN.matrixToFeature(normalizedPixels, true);

        // 直接返回最近类别
        int closestId = knn.getClosestCategory(features);
        // 颜色数量校验：pattern有2+种有效颜色时，用户画布必须至少有2种对应颜色
        if (closestId != UNKNOWN) {
            int requiredColors = categoryEffectiveColorCount.getOrDefault(closestId, 1);
            if (requiredColors >= 2) {
                int userColors = countEffectiveColors(normalizedPixels);
                if (userColors < 2) {
                    return UNKNOWN;
                }
            }
        }
        return closestId;
    }

    /**
     * 计算颜色错误率（在pattern有色位置上，输入颜色与pattern不匹配的比例，考虑颜色互通）
     * @param input 输入像素矩阵（已规范化）
     * @param label 识别的类别
     * @return 颜色错误率（0.0 - 1.0），-1表示无法计算
     */
    private double calculateColorMismatchRatio(byte[][] input, int label) {
        byte[][] pattern = categoryPatterns.get(label);
        if (pattern == null) {
            return 0.0; // 没有pattern数据，默认通过
        }

        int coloredCount = 0;      // pattern中有色位置总数
        int mismatchCount = 0;      // 颜色不匹配的数量（考虑互通）

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int patternColor = pattern[y][x] & 0xFF;
                int inputColor = input[y][x] & 0xFF;

                // 判断是否为透明/背景色（仅背景白色ID视为透明）
                boolean patternIsTransparent = patternColor == COLOR_BACKGROUND_WHITE;
                boolean inputIsTransparent = inputColor == COLOR_BACKGROUND_WHITE;

                if (!patternIsTransparent && !inputIsTransparent) {
                    coloredCount++;
                    // 检查颜色是否兼容（考虑颜色互通）
                    if (!areColorsCompatible(patternColor, inputColor)) {
                        mismatchCount++;
                    }
                }
            }
        }

        if (coloredCount == 0) {
            return 0.0; // 没有有色位置，默认通过
        }

        return (double) mismatchCount / coloredCount;
    }

    /**
     * 像素级校验：检查输入是否符合pattern的约束
     * - pattern中透明位置被画上颜色的比例不超过50%
     * - pattern中有色位置被画成透明的比例不超过50%
     * - 如果原始位置校验失败，允许将pattern向四个方向偏移1-2像素后再次校验
     * @param input 输入像素矩阵（原始）
     * @param label 识别的类别
     * @return PixelValidationResult 验证结果
     */
    private PixelValidationResult validatePixelConstraints(byte[][] input, int label) {
        byte[][] pattern = categoryPatterns.get(label);
        if (pattern == null) {
            return new PixelValidationResult(true, 0, 0); // 没有pattern数据，默认通过
        }

        // 记录所有偏移中的最大超标程度
        double maxExtraPixelRatio = 0.0;
        double maxMissingPixelRatio = 0.0;

        // 先尝试原始位置校验
        PixelValidationResult result = validatePixelConstraintsAtOffset(input, pattern, 0, 0);
        if (result.passed) {
            return result;
        }
        maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
        maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);

        // 原始位置校验失败，尝试四个方向的偏移
        for (int offset = 1; offset <= 2; offset++) {
            // 左
            result = validatePixelConstraintsAtOffset(input, pattern, -offset, 0);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 右
            result = validatePixelConstraintsAtOffset(input, pattern, offset, 0);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 上
            result = validatePixelConstraintsAtOffset(input, pattern, 0, -offset);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 下
            result = validatePixelConstraintsAtOffset(input, pattern, 0, offset);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 左上
            result = validatePixelConstraintsAtOffset(input, pattern, -offset, -offset);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 右上
            result = validatePixelConstraintsAtOffset(input, pattern, offset, -offset);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 左下
            result = validatePixelConstraintsAtOffset(input, pattern, -offset, offset);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
            // 右下
            result = validatePixelConstraintsAtOffset(input, pattern, offset, offset);
            if (result.passed) {
                return result;
            }
            maxExtraPixelRatio = Math.max(maxExtraPixelRatio, result.extraPixelRatio);
            maxMissingPixelRatio = Math.max(maxMissingPixelRatio, result.missingPixelRatio);
        }

        return new PixelValidationResult(false, maxExtraPixelRatio, maxMissingPixelRatio);
    }

    /**
     * 在指定偏移量下校验像素约束
     * @param input 输入像素矩阵
     * @param pattern pattern矩阵
     * @param xOffset 水平偏移量（负数向左，正数向右）
     * @param yOffset 垂直偏移量（负数向上，正数向下）
     * @return PixelValidationResult 验证结果，包含通过状态和超标程度
     */
    private PixelValidationResult validatePixelConstraintsAtOffset(byte[][] input, byte[][] pattern, int xOffset, int yOffset) {
        // 透明阈值：pattern中透明位置被画上颜色的比例不超过此值
        final double EXTRA_PIXEL_THRESHOLD = 0.50;
        // 遗漏阈值：pattern中有色位置被画成透明的比例不超过此值
        final double MISSING_PIXEL_THRESHOLD = 0.50;
        // 颜色错误阈值：有色位置颜色错误（考虑互通）的比例不超过此值
        final double COLOR_MISMATCH_THRESHOLD = 0.40;

        int patternTransparentCount = 0; // pattern中透明位置总数
        int extraColorCount = 0;          // pattern中透明位置被画上颜色的数量

        int patternColoredCount = 0;      // pattern中有色位置总数
        int missingColorCount = 0;         // pattern中有色位置被画成透明的数量
        int colorMismatchCount = 0;       // 颜色不匹配的数量（考虑互通）

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                // 计算pattern中的对应位置（应用偏移）
                int patternX = x - xOffset;
                int patternY = y - yOffset;

                // 边界检查：超出范围视为透明
                if (patternX < 0 || patternX >= 16 || patternY < 0 || patternY >= 16) {
                    // 超出边界的pattern位置算作透明
                    int inputColor = input[y][x] & 0xFF;
                    boolean inputIsTransparent = inputColor == 16;
                    patternTransparentCount++;
                    if (!inputIsTransparent) {
                        extraColorCount++;
                    }
                    continue;
                }

                int patternColor = pattern[patternY][patternX] & 0xFF;
                int inputColor = input[y][x] & 0xFF;

                // 判断是否为透明/背景色（仅背景白色ID视为透明）
                boolean patternIsTransparent = patternColor == COLOR_BACKGROUND_WHITE;
                boolean inputIsTransparent = inputColor == COLOR_BACKGROUND_WHITE;

                if (patternIsTransparent) {
                    patternTransparentCount++;
                    // pattern是透明的，但输入画上了颜色
                    if (!inputIsTransparent) {
                        extraColorCount++;
                    }
                } else {
                    patternColoredCount++;
                    // pattern是有色的，但输入画成了透明
                    if (inputIsTransparent) {
                        missingColorCount++;
                    } else {
                        // 检查颜色是否正确（考虑颜色组互通）
                        if (!areColorsCompatible(patternColor, inputColor)) {
                            colorMismatchCount++;
                        }
                    }
                }
            }
        }

        // 计算实际超标程度
        double extraPixelRatio = 0.0;  // 透明超标程度（超出阈值的部分）
        double missingPixelRatio = 0.0; // 遗漏超标程度（超出阈值的部分）
        boolean passed = true;

        // 检查透明位置被占领的比例
        if (patternTransparentCount > 0) {
            double actualExtraRatio = (double) extraColorCount / patternTransparentCount;
            if (actualExtraRatio > EXTRA_PIXEL_THRESHOLD) {
                extraPixelRatio = actualExtraRatio - EXTRA_PIXEL_THRESHOLD;
                passed = false;
            }
        }

        // 检查有色位置变成透明的比例
        if (patternColoredCount > 0) {
            double actualMissingRatio = (double) missingColorCount / patternColoredCount;
            if (actualMissingRatio > MISSING_PIXEL_THRESHOLD) {
                missingPixelRatio = actualMissingRatio - MISSING_PIXEL_THRESHOLD;
                passed = false;
            }
        }

        // 检查颜色不匹配的比例（考虑颜色互通）
        if (patternColoredCount > 0) {
            double colorMismatchRatio = (double) colorMismatchCount / patternColoredCount;
            if (colorMismatchRatio > COLOR_MISMATCH_THRESHOLD) {
                return new PixelValidationResult(false, 0, 0);
            }
        }

        return new PixelValidationResult(passed, extraPixelRatio, missingPixelRatio);
    }

    /**
     * 颜色规范化：将相近颜色统一，放宽识别限制
     * - 灰色和淡灰色互通
     * - 蓝色和深蓝色互通
     * - 红色和深红色互通
     * - 绿色和深绿色互通
     * - 调色盘白色保持不变
     */
    private byte[][] normalizeColors(byte[][] pixels) {
        byte[][] result = createEmptyCanvas();
        
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = pixels[y][x] & 0xFF;
                int normalized = color;
                

                // 灰色 -> 淡灰色
                if (color == COLOR_GRAY) {
                    normalized = COLOR_LIGHT_GRAY;
                }
                // 深红色 -> 棕色
                else if (color == COLOR_DARK_RED) {
                    normalized = COLOR_BROWN;
                }
                // 深蓝色 -> 蓝色
                else if (color == COLOR_DARK_BLUE) {
                    normalized = COLOR_BLUE;
                }
                // 深绿色 -> 绿色
                else if (color == COLOR_DARK_GREEN) {
                    normalized = COLOR_GREEN;
                }
                // 白色保持不变
                
                result[y][x] = (byte) normalized;
            }
        }
        
        return result;
    }

    /**
     * 归一化单个颜色值（颜色互通规则）
     */
    private static int normalizeColor(int color) {
        if (color == COLOR_GRAY) return COLOR_LIGHT_GRAY;
        if (color == COLOR_DARK_RED) return COLOR_BROWN;
        if (color == COLOR_DARK_BLUE) return COLOR_BLUE;
        if (color == COLOR_DARK_GREEN) return COLOR_GREEN;
        return color;
    }

    /**
     * 统计画布中有效颜色数量（归一化后，排除背景白色16）
     */
    private static int countEffectiveColors(byte[][] canvas) {
        Set<Integer> colors = new HashSet<>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int color = canvas[y][x] & 0xFF;
                if (color != COLOR_BACKGROUND_WHITE) {
                    colors.add(normalizeColor(color));
                }
            }
        }
        return colors.size();
    }

    /**
     * 预计算每个类别pattern的有效颜色数量
     */
    private void computeCategoryColorCounts() {
        for (Map.Entry<Integer, byte[][]> entry : categoryPatterns.entrySet()) {
            int category = entry.getKey();
            byte[][] pattern = entry.getValue();
            int count = countEffectiveColors(pattern);
            categoryEffectiveColorCount.put(category, count);
        }
    }

    /**
     * 根据类别获取对应的 Minecraft 物品
     */
    public static Item getItemForCategory(int category) {
        return CATEGORY_TO_ITEM.get(category);
    }

    /**
     * 获取类别翻译键
     */
    public static String getTranslationKey(int category) {
        if (category == UNKNOWN) return "starrailexpress.drawing_board.recognize.unknown";
        return "starrailexpress.drawing_board.recognize.item_" + category;
    }

    public static String getCategoryName(int category) {
        return getTranslationKey(category);
    }

    public static int getCategoryCount() {
        return CATEGORY_COUNT;
    }

    /**
     * 获取指定类别的代表图案（16x16字节数组）
     * 返回深拷贝
     * @param category 类别ID
     * @return 图案字节数组，若不存在则返回null
     */
    public byte[][] getPatternForCategory(int category) {
        byte[][] original = categoryPatterns.get(category);
        if (original == null) return null;
        byte[][] copy = createEmptyCanvas();
        for (int y = 0; y < 16; y++) {
            System.arraycopy(original[y], 0, copy[y], 0, 16);
        }
        return copy;
    }

    /**
     * 获取所有已定义pattern的类别ID列表
     * @return 类别ID数组
     */
    public int[] getAvailableCategories() {
        return categoryPatterns.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * 检查两个颜色是否兼容（属于同一颜色组）
     * 颜色组互通规则：
     * - 灰色(8) <-> 淡灰色(7)
     * - 深红色(1) <-> 棕色(3)
     * - 深蓝色(11) <-> 蓝色(4)
     * - 深绿色(2) <-> 绿色(13)
     */
    private boolean areColorsCompatible(int color1, int color2) {
        // 规范化两个颜色
        int norm1 = normalizeColorGroup(color1);
        int norm2 = normalizeColorGroup(color2);
        return norm1 == norm2;
    }

    /**
     * 将颜色规范化到颜色组
     */
    private int normalizeColorGroup(int color) {
        // 灰色 -> 淡灰色
        if (color == COLOR_GRAY) {
            return COLOR_LIGHT_GRAY;
        }
        // 深红色 -> 棕色
        if (color == COLOR_DARK_RED) {
            return COLOR_BROWN;
        }
        // 深蓝色 -> 蓝色
        if (color == COLOR_DARK_BLUE) {
            return COLOR_BLUE;
        }
        // 深绿色 -> 绿色
        if (color == COLOR_DARK_GREEN) {
            return COLOR_GREEN;
        }
        return color;
    }

    /**
     * 精确匹配检查：将用户画的图案与所有存储的pattern逐一比对
     * 如果像素色彩和位置完全一致（至多允许4个像素差异），直接返回匹配的类别
     * @param normalizedPixels 经过颜色规范化后的输入像素
     * @return 匹配的类别ID，如果不匹配则返回 UNKNOWN
     */
    private int checkExactMatch(byte[][] normalizedPixels) {
        int bestCategory = UNKNOWN;
        int bestDiff = Integer.MAX_VALUE;

        for (Map.Entry<Integer, byte[][]> entry : categoryPatterns.entrySet()) {
            int category = entry.getKey();
            byte[][] pattern = entry.getValue();
            int differences = 0;

            for (int y = 0; y < 16 && differences <= 4; y++) {
                for (int x = 0; x < 16 && differences <= 4; x++) {
                    int patternColor = pattern[y][x] & 0xFF;
                    int inputColor = normalizedPixels[y][x] & 0xFF;

                    boolean patternIsTransparent = (patternColor == COLOR_BACKGROUND_WHITE);
                    boolean inputIsTransparent = (inputColor == COLOR_BACKGROUND_WHITE);

                    if (patternIsTransparent && inputIsTransparent) {
                        // 双方都是透明背景，一致，跳过
                        continue;
                    }

                    if (patternIsTransparent != inputIsTransparent) {
                        // 一方透明、另一方着色：差异
                        differences++;
                    } else {
                        // 双方都着色：检查颜色是否兼容
                        if (!areColorsCompatible(patternColor, inputColor)) {
                            differences++;
                        }
                    }
                }
            }

            if (differences <= 4 && differences < bestDiff) {
                bestDiff = differences;
                bestCategory = category;
            }
        }

        return bestCategory;
    }
}
