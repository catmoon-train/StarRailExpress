package org.agmas.noellesroles.init;

import net.exmo.sre.repair.content.item.*;
import dev.doctor4t.ratatouille.util.registrar.ItemRegistrar;
import io.wifi.starrailexpress.api.ChargeableItemRegistry;
import io.wifi.starrailexpress.api.impl.KnifeChargeableItem;
import io.wifi.starrailexpress.index.DevItems;
import io.wifi.starrailexpress.index.TMMDescItems;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.food.Foods;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.item.*;
import org.agmas.noellesroles.content.item.charge_item.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.wifi.starrailexpress.game.GameConstants.getInTicks;
import static io.wifi.starrailexpress.index.TMMItems.*;

@SuppressWarnings("unchecked")

public class ModItems {
    public static final ItemRegistrar registrar = new ItemRegistrar(Noellesroles.MOD_ID);

    public static final Item ANTIDOTE = register(new AntidoteItem((new Item.Properties()).stacksTo(1)), "antidote",
            CONSUMABLES_GROUP);

    public static final Item REPAIR_TOOLBOX = register(
            new RepairBoostItem(15, "item.noellesroles.repair_toolbox.tooltip",
                    new Item.Properties().stacksTo(4)),
            "repair_toolbox", REPAIR_MODE_GROUP);
    public static final Item SPARE_PARTS = register(
            new RepairBoostItem(8, "item.noellesroles.spare_parts.tooltip",
                    new Item.Properties().stacksTo(16)),
            "spare_parts", REPAIR_MODE_GROUP);
    public static final Item RESCUE_FLARE = register(
            new RescueFlareItem(new Item.Properties().stacksTo(4)),
            "rescue_flare", ROLE_ITEMS_GROUP);
    // 遗恨德林加 - 处决地点掉落的一发反抗手枪（murder 融合机制）
    public static final Item VENGEANCE_DERRINGER = register(
            new VengeanceDerringerItem(new Item.Properties().stacksTo(1)),
            "vengeance_derringer", REPAIR_MODE_GROUP);
    // 恐鬼症道具：镇静剂 / EMF 探测器 / 守护十字
    public static final Item SANITY_MEDS = register(
            new SanityMedsItem(new Item.Properties().stacksTo(4)),
            "sanity_meds", REPAIR_MODE_GROUP);
    public static final Item EMF_READER = register(
            new EmfReaderItem(new Item.Properties().stacksTo(1)),
            "emf_reader", REPAIR_MODE_GROUP);
    public static final Item CRUCIFIX = register(
            new CrucifixItem(new Item.Properties().stacksTo(2)),
            "crucifix", REPAIR_MODE_GROUP);
    // 推理之书 - 大侦探专属
    public static final Item DEDUCTION_BOOK = register(
            new DeductionBookItem(new Item.Properties().stacksTo(1)),
            "deduction_book", ROLE_ITEMS_GROUP);
    public static final Item REASONER_COMPASS = register(
            new ReasonerCompassItem(new Item.Properties().stacksTo(1)),
            "reasoner_compass", ROLE_ITEMS_GROUP);
    // 区域地图 - 自动生成当前游戏区域的地图（迷宫等地图用），手持显示 HUD 小地图，右键打开全屏地图
    public static final Item AREA_MAP = register(
            new AreaMapItem(new Item.Properties().stacksTo(1)),
            "area_map", TOOLS_GROUP);
    public static final Item FLARE = register(
            new FlareItem(new Item.Properties().stacksTo(8)),
            "flare", ROLE_ITEMS_GROUP);
    public static final Item REPAIR_MEDKIT = register(
            new RepairMedkitItem(new Item.Properties().stacksTo(4)),
            "repair_medkit", REPAIR_MODE_GROUP);
    public static final Item HUNTER_CHAIN = register(
            new HunterChainItem(new Item.Properties().stacksTo(1).durability(6)),
            "hunter_chain", REPAIR_MODE_GROUP);
    public static final Item HUNTER_WEAPON = register(
            new HunterWeaponItem(new Item.Properties().stacksTo(1).durability(96)),
            "hunter_weapon", REPAIR_MODE_GROUP);
    public static final Item HUNTER_HAMMER = register(
            new HunterWeaponItem("hammer", new Item.Properties().stacksTo(1).durability(84)),
            "hunter_hammer", REPAIR_MODE_GROUP);
    public static final Item HUNTER_HOOK = register(
            new HunterWeaponItem("hook", new Item.Properties().stacksTo(1).durability(88)),
            "hunter_hook", REPAIR_MODE_GROUP);
    public static final Item HUNTER_PLUGIN_LACERATION = register(
            new HunterAttackPluginItem("laceration", new Item.Properties().stacksTo(4)),
            "hunter_plugin_laceration", REPAIR_MODE_GROUP);
    public static final Item HUNTER_PLUGIN_CONCUSSION = register(
            new HunterAttackPluginItem("concussion", new Item.Properties().stacksTo(4)),
            "hunter_plugin_concussion", REPAIR_MODE_GROUP);
    public static final Item HUNTER_PLUGIN_TRACKING = register(
            new HunterAttackPluginItem("tracking", new Item.Properties().stacksTo(4)),
            "hunter_plugin_tracking", REPAIR_MODE_GROUP);
    public static final Item HUNTER_PLUGIN_SUPPRESSION = register(
            new HunterAttackPluginItem("suppression", new Item.Properties().stacksTo(4)),
            "hunter_plugin_suppression", REPAIR_MODE_GROUP);

    public static final Item HUNTER_PULSE = register(
            new HunterPulseItem(new Item.Properties().stacksTo(1)),
            "hunter_pulse", REPAIR_MODE_GROUP);
    public static final Item HUNTER_BLINK = register(
            new HunterBlinkItem(new Item.Properties().stacksTo(1).durability(4)),
            "hunter_blink", REPAIR_MODE_GROUP);
    public static final Item HUNTER_JAMMER = register(
            new HunterJammerItem(new Item.Properties().stacksTo(1).durability(3)),
            "hunter_jammer", REPAIR_MODE_GROUP);

    public static final Item SMOKE_PELLET = register(
            new SmokePelletItem(new Item.Properties().stacksTo(8)),
            "smoke_pellet", REPAIR_MODE_GROUP);
    public static final Item DECOY_BEACON = register(
            new DecoyBeaconItem(new Item.Properties().stacksTo(4)),
            "decoy_beacon", REPAIR_MODE_GROUP);
    public static final Item ESCAPE_GRAPPLE = register(
            new EscapeGrappleItem(new Item.Properties().stacksTo(1).durability(3)),
            "escape_grapple", REPAIR_MODE_GROUP);
    public static final Item REPAIR_AREA_KEY = register(
            new RepairRouteItem("area_key", new Item.Properties().stacksTo(8)),
            "repair_area_key", REPAIR_MODE_GROUP);
    public static final Item REPAIR_OLD_KEY = register(
            new RepairRouteItem("old_key", new Item.Properties().stacksTo(4)),
            "repair_old_key", REPAIR_MODE_GROUP);
    public static final Item REPAIR_FUSE = register(
            new RepairRouteItem("fuse", new Item.Properties().stacksTo(4)),
            "repair_fuse", REPAIR_MODE_GROUP);
    public static final Item REPAIR_GEAR_HANDLE = register(
            new RepairRouteItem("gear_handle", new Item.Properties().stacksTo(4)),
            "repair_gear_handle", REPAIR_MODE_GROUP);
    public static final Item REPAIR_CROWBAR = register(
            new RepairRouteItem("crowbar", new Item.Properties().stacksTo(1).durability(24)),
            "repair_crowbar", REPAIR_MODE_GROUP);
    public static final Item REPAIR_LOCKPICK = register(
            new RepairRouteItem("lockpick", new Item.Properties().stacksTo(8)),
            "repair_lockpick", REPAIR_MODE_GROUP);
    public static final Item REPAIR_BATTERY = register(
            new RepairRouteItem("battery", new Item.Properties().stacksTo(4)),
            "repair_battery", REPAIR_MODE_GROUP);
    public static final Item REPAIR_VALVE_HANDLE = register(
            new RepairRouteItem("valve_handle", new Item.Properties().stacksTo(4)),
            "repair_valve_handle", REPAIR_MODE_GROUP);
    public static final Item REPAIR_BOLT_CUTTER = register(
            new RepairRouteItem("bolt_cutter", new Item.Properties().stacksTo(1).durability(18)),
            "repair_bolt_cutter", REPAIR_MODE_GROUP);
    public static final Item REPAIR_PRESET_WAND = register(
            new RepairPresetWandItem(new Item.Properties().stacksTo(1)),
            "repair_preset_wand", REPAIR_MODE_GROUP);
    public static final Item PILL = register(
            new PillItem((new Item.Properties()).stacksTo(16)
                    .food((new FoodProperties.Builder()).nutrition(1).saturationModifier(0.1F)
                            .alwaysEdible().build())),
            "pill", CONSUMABLES_GROUP);
    public static final Item TOXIN = register(
            new ToxinItem((new Item.Properties()).durability(ToxinDurability.MAX_DURABILITY)), "toxin",
            CONSUMABLES_GROUP);
    public static final Item CATALYST = register(new CatalystItem((new Item.Properties()).stacksTo(1)), "catalyst",
            CONSUMABLES_GROUP);
    public static final Item BANDIT_REVOLVER = register(new BanditRevolverItem((new Item.Properties()).stacksTo(1)),
            "bandit_revolver", WEAPONS_GROUP);
    public static final String PILL_POISONOUS_KEY = "poisonous";

    public static final Item COOKED_FOOD = register(
            new ChefFoodItem(new Item.Properties().stacksTo(1)), "cooked_food",
            CONSUMABLES_GROUP);
    public static final Item A_BOTTLE_OF_WATER = register(
            new ChefWaterItem((new Item.Properties()).stacksTo(1).food(Foods.HONEY_BOTTLE)),
            "a_bottle_of_water", CONSUMABLES_GROUP);
    public static final Item LINGSHI = register(
            new ChefFoodItem((new Item.Properties()).stacksTo(1)), "lingshi",
            CONSUMABLES_GROUP);

    public static final Item FOOD_STUFF = register(
            new FoodStuffItem((new Item.Properties()).stacksTo(16)), "foodstuff",
            CONSUMABLES_GROUP);
    public static final Item CAKE_INGREDIENTS = register(
            new CakeIngredientsItem(new Item.Properties().stacksTo(16)),
            "cake_ingredients", CONSUMABLES_GROUP);
    public static final Item CAKE_EGG = register(new Item(new Item.Properties().stacksTo(16)), "cake_egg",
            CONSUMABLES_GROUP);
    public static final Item CAKE_MILK_BUCKET = register(new Item(new Item.Properties().stacksTo(16)),
            "cake_milk_bucket", CONSUMABLES_GROUP);
    public static final Item PAN = register(
            new PanItem((new Item.Properties()).stacksTo(1)), "pan",
            CONSUMABLES_GROUP);
    public static final Item BUCKET_OF_H2SO4 = register(
            new H2SO4AcidItem((new Item.Properties()).stacksTo(1)), "bucket_of_h2so4",
            CONSUMABLES_GROUP);
    public static final Item LETTER_ITEM = TMMItems.LETTER;
    public static final Item NINJA_KNIFE = register(
            new NinjaKnifeItem(new Item.Properties().stacksTo(1)),
            "ninja_knife", WEAPONS_GROUP);
    public static final Item NINJA_SHURIKEN = register(
            new NinjaShurikenItem(new Item.Properties().stacksTo(1)),
            "ninja_shuriken", WEAPONS_GROUP);

    /**
     * 仁之剑
     * - 左键玩家造成1点伤害并扣除受击玩家20%的san值
     * - 材质继承原版木棍
     */
    public static final Item BENEVOLENCE_SWORD = register(
            new BenevolenceSwordItem(new Item.Properties().stacksTo(1)),
            "benevolence_sword", WEAPONS_GROUP);
    public static final Item ONCE_REVOLVER = register(
            new OnceRevolverItem((new Item.Properties()).stacksTo(1).durability(1)), "once_revolver",
            WEAPONS_GROUP);
    public static final Item HANDCUFFS = register(
            new HandCuffsItem((new Item.Properties()).stacksTo(1)), "handcuffs",
            TOOLS_GROUP);
    public static final Item PATROLLER_REVOLVER = register(
            new PatrollerRevolverItem((new Item.Properties()).stacksTo(1)), "patroller_revolver",
            WEAPONS_GROUP);
    public static final Item SHERIFF_REVOLVER = register(
            new SheriffRevolverItem((new Item.Properties()).stacksTo(1)), "sheriff_revolver",
            WEAPONS_GROUP);
    public static final Item SINGER_MUSIC_DISC = register(
            new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE)),
            "singer_music_disc", MISC_ITEMS_GROUP);
    public static final Item NIGHT_VISION_GLASSES = register(
            new NightGlassesItem(ArmorMaterials.TURTLE, net.minecraft.world.item.ArmorItem.Type.HELMET,
                    (new Item.Properties()).durability(60)),
            "night_vision_glasses", EQUIPMENT_GROUP);

    public static final Item DIVING_HELMET = register(
            new DivingHelmetItem(ArmorMaterials.DIAMOND, net.minecraft.world.item.ArmorItem.Type.HELMET,
                    (new Item.Properties()).stacksTo(1)),
            "diving_helmet", EQUIPMENT_GROUP);

    public static final Item DIVING_BOOTS = register(
            new DivingBootsItem(ArmorMaterials.GOLD, net.minecraft.world.item.ArmorItem.Type.BOOTS,
                    (new Item.Properties()).stacksTo(1)),
            "diving_boots", EQUIPMENT_GROUP);

    /**
     * 喷气背包
     * - 穿在身上（渲染为铁胸甲）
     * - 蹲下时给予漂浮1效果（飞行员给予漂浮2）
     * - 每秒消耗1点耐久
     * - 60点耐久
     * - 可丢弃
     */
    public static final Item JETPACK = register(
            new JetpackItem(ArmorMaterials.IRON, net.minecraft.world.item.ArmorItem.Type.CHESTPLATE,
                    (new Item.Properties()).stacksTo(1).durability(60)),
            "jetpack", EQUIPMENT_GROUP);

    public static final Item FAKE_KNIFE = register(
            new FakeKnifeItem(new Item.Properties().stacksTo(1)),
            "fake_knife", WEAPONS_GROUP);
    public static final Item SP_KNIFE = register(
            new SPKnifeItem(new Item.Properties().stacksTo(1)),
            "sp_knife", WEAPONS_GROUP);
    public static final Item STALKER_KNIFE = register(
            new StalkerKnifeItem(new Item.Properties().stacksTo(1)),
            "stalker_knife", WEAPONS_GROUP);
    public static final Item STALKER_KNIFE_OFFHAND = register(
            new StalkerKnifeItem(new Item.Properties().stacksTo(1)),
            "stalker_knife_offhand", WEAPONS_GROUP);
    public static final Item FAKE_REVOLVER = register(
            new FakeRevolverItem(new Item.Properties().stacksTo(1).durability(4)),
            "fake_revolver", WEAPONS_GROUP);

    public static final Item FAKE_BAT = register(
            new FakeBatItem(new Item.Properties().stacksTo(1)),
            "fake_bat", WEAPONS_GROUP);

    /**
     * 阴阳剑 - 黑白狂暴前奏武器
     * - 左键：黑白粒子突进
     * - 右键：前摇1秒范围伤害
     */
    public static final Item YINYANG_SWORD = register(
            new org.agmas.noellesroles.game.roles.neutral.monokuma.YinYangSwordItem(
                    new Item.Properties().stacksTo(1)),
            "yinyang_sword", WEAPONS_GROUP);

    public static final Item FAKE_PSYCHO_MODE = register(
            new Item(new Item.Properties().stacksTo(1)),
            "fake_psycho_mode", WEAPONS_GROUP);

    public static final Item FAKE_GRENADE = register(
            new FakeGrenadeItem(new Item.Properties().stacksTo(1)),
            "fake_grenade", WEAPONS_GROUP);

    public static final Item FAKE_LOCKPICK = register(
            new FakeLockpickItem(new Item.Properties().stacksTo(1)),
            "fake_lockpick", TOOLS_GROUP);

    public static final Item INFERIOR_LOCKPICK = register(
            new InferiorLockpickItem(new Item.Properties().stacksTo(1)),
            "inferior_lockpick", TOOLS_GROUP);

    public static final Item FAKE_CROWBAR = register(
            new FakeCrowbarItem(new Item.Properties().stacksTo(1)),
            "fake_crowbar", TOOLS_GROUP);

    public static final Item FAKE_BODY_BAG = register(
            new FakeBodyBagItem(new Item.Properties().stacksTo(1)),
            "fake_body_bag", TOOLS_GROUP);

    public static final Item MASTER_KEY = register(
            new Item(new Item.Properties().stacksTo(1)),
            "master_key", TOOLS_GROUP);
    public static final Item MASTER_KEY_P = register(
            new MasterKeyItem(new Item.Properties().stacksTo(1).durability(5)),
            "master_key_p", TOOLS_GROUP);
    public static final Item NOELL_ARTISAN_KEY = register(
            new ArtisanKeyItem(new Item.Properties().stacksTo(1)),
            "noell_artisan_key", TOOLS_GROUP);
    public static final Item NOELL_KEY_BLANK = register(
            new KeyBlankItem(new Item.Properties().stacksTo(16)),
            "noell_key_blank", TOOLS_GROUP);
    public static final Item NOELL_PAPERCLIP = register(
            new PaperclipItem(new Item.Properties().stacksTo(16)),
            "noell_paperclip", TOOLS_GROUP);
    public static final Item DELUSION_VIAL = register(
            new Item(new Item.Properties().stacksTo(1)),
            "delusion_vial", ROLE_ITEMS_GROUP);

    /**
     * 马桶毒药
     * - 毒师专属物品
     * - 右键涂在马桶上，使下一个使用马桶的玩家中毒
     * - 中毒时间：40-70秒
     */
    public static final Item TOILET_POISON = register(
            new io.wifi.starrailexpress.content.item.ToiletPoisonItem(new Item.Properties().stacksTo(1)),
            "toilet_poison", ROLE_ITEMS_GROUP);

    /**
     * 角色地雷
     */
    public static final Item ROLE_MINE = register(
            new Item(new Item.Properties().stacksTo(1)),
            "role_mine", ROLE_ITEMS_GROUP);

    public static final Item DEFIBRILLATOR = register(
            new DefibrillatorItem(new Item.Properties().stacksTo(1)),
            "defibrillator", REPAIR_MODE_GROUP);

    public static final Item BOXING_GLOVE = register(
            new BoxingGloveItem(new Item.Properties().stacksTo(1)),
            "boxing_glove", WEAPONS_GROUP);

    public static final Item ANTIDOTE_REAGENT = register(
            new AntidoteReagentItem(new Item.Properties().stacksTo(16).durability(5)),
            "antidote_reagent", CONSUMABLES_GROUP);

    /**
     * 阴谋之书页
     * - 阴谋家专属物品
     * - 在商店以250金币购买
     * - 右键使用打开玩家/角色选择GUI
     */
    public static final Item CONSPIRACY_PAGE = register(
            new ConspiracyPageItem(new Item.Properties().stacksTo(1)),
            "conspiracy_page", ROLE_ITEMS_GROUP);

    /**
     * 空包弹
     * - 捣蛋鬼专属物品
     * - 在商店以100金币购买
     * - 右键对目标玩家使用，使其手中枪械进入30秒冷却
     */
    public static final Item BLANK_CARTRIDGE = register(
            new BlankCartridgeItem(new Item.Properties().stacksTo(16)),
            "blank_cartridge", ROLE_ITEMS_GROUP);

    /**
     * 烟雾弹
     * - 捣蛋鬼专属物品
     * - 在商店以300金币购买
     * - 右键投掷，形成烟雾区域
     * - 进入烟雾的玩家获得失明效果
     * - 直接命中玩家时清空目标san值
     */
    public static final Item SMOKE_GRENADE = register(
            new SmokeGrenadeItem(new Item.Properties().stacksTo(8)),
            "smoke_grenade", WEAPONS_GROUP);

    /**
     * 氯气弹
     * - 可投掷物品
     * - 右键投掷，落地时使半径3格内玩家中毒
     * - 落地时播放火熄灭声
     */
    public static final Item CHLORINE_BOMB = register(
            new ChlorineBombItem(new Item.Properties().stacksTo(8)),
            "chlorine_bomb", WEAPONS_GROUP);

    /**
     * 毒气瓶
     * - 可投掷物品
     * - 右键投掷，落地后释放持续扩散的毒气云
     * - 毒气60秒后消散，在毒气中停留8秒将中毒
     * - 最大扩散半径20格，毒师免疫
     */
    public static final Item POISON_GAS_TANK = register(
            new PoisonGasTankItem(new Item.Properties().stacksTo(16)),
            "poison_gas_tank", WEAPONS_GROUP);

    /**
     * 净化弹
     * - 可投掷物品
     * - 右键投掷，落地时取消半径3格内玩家的中毒状态
     * - 落地时播放守卫者激光射击声
     * - 粒子效果为气泡
     */
    public static final Item PURIFY_BOMB = register(
            new PurifyBombItem(new Item.Properties().stacksTo(8)),
            "purify_bomb", WEAPONS_GROUP);

    /**
     * 血瓶
     * - 右键使用后在附近洒落血液
     * - 使用后消失
     */
    public static final Item BLOOD_BOTTLE = register(
            new BloodBottleItem(new Item.Properties().stacksTo(16)),
            "blood_bottle", ROLE_ITEMS_GROUP);

    /**
     * 闪光弹
     * - 可投掷物品
     * - 右键投掷，落地时使半径6格内有闪光弹的玩家获得试炼之兆效果（WEAVING）3秒
     * - 落地时播放火熄灭声
     */
    public static final Item FLASH_GRENADE = register(
            new FlashGrenadeItem(new Item.Properties().stacksTo(8)),
            "flash_grenade", WEAPONS_GROUP);

    /**
     * 诱饵弹
     * - 可投掷物品
     * - 右键投掷，落地时不会产生粒子效果
     * - 在落地处发生5声左轮手枪射击的声音（时间间隔不一）
     */
    public static final Item DECOY_GRENADE = register(
            new DecoyGrenadeItem(new Item.Properties().stacksTo(8)),
            "decoy_grenade", WEAPONS_GROUP);

    public static final Item SPELLBREAKER_POTION = register(
            new SpellbreakerPotionItem(new Item.Properties().stacksTo(1)),
            "spellbreaker_potion", ROLE_ITEMS_GROUP);

    public static final Item SILENCE_TOTEM = register(
            new SilenceTotemItem(new Item.Properties().stacksTo(8)),
            "silence_totem", ROLE_ITEMS_GROUP);

    /**
     * 加固门道具
     * - 工程师专属物品
     * - 在商店以75金币购买
     * - 右键门：使门能够防御一次撬棍攻击
     * - 蹲下右键被卡住的门：解除卡住状态
     */
    public static final Item REINFORCEMENT = register(
            new ReinforcementItem(new Item.Properties().stacksTo(16)),
            "reinforcement", ROLE_ITEMS_GROUP);

    public static final Item SCREWDRIVER = register(
            new ScrewdriverItem(new Item.Properties().stacksTo(16)),
            "screwdriver", ROLE_ITEMS_GROUP);

    /**
     * 警报陷阱
     * - 工程师专属物品
     * - 在商店以120金币购买
     * - 右键门：在门上放置警报陷阱
     * - 当撬棍使用时触发，发出响亮的警报声
     */
    public static final Item ALARM_TRAP = register(
            new AlarmTrapItem(new Item.Properties().stacksTo(16)),
            "alarm_trap", ROLE_ITEMS_GROUP);

    /**
     * 快递包裹盒子
     * - 射命丸文专属物品
     * - 在商店以150金币购买
     * - 指针对准玩家并右键使用，打开传递界面
     * - 双方可以放入一样物品并交换
     */
    public static final Item DELIVERY_BOX = register(
            new DeliveryBoxItem(new Item.Properties().stacksTo(8)),
            "delivery_box", ROLE_ITEMS_GROUP);
    /**
     * 快递包裹盒子
     * - 射命丸文专属物品
     * - 在商店以150金币购买
     * - 指针对准玩家并右键使用，打开传递界面
     * - 双方可以放入一样物品并交换
     */
    public static final Item NEWSPAPER = register(
            new NewspaperItem(new Item.Properties().stacksTo(8)),
            "newspaper", ROLE_ITEMS_GROUP);

    /**
     * 迷幻瓶
     * - 迷幻师专属物品
     * - 在商店购买
     * - 右键使用，制造大量烟雾
     * - 20格范围内玩家视野会随机偏离视角
     * - 迷雾范围：20格
     * - 持续时间：3秒
     * - 触发间隔：1秒
     * - 耐久：2点
     */
    public static final Item HALLUCINATION_BOTTLE = register(
            new HallucinationBottleItem(new Item.Properties().stacksTo(1).durability(2)),
            "hallucination_bottle", ROLE_ITEMS_GROUP);

    /**
     * 薄荷糖
     * - 心理学家专属物品
     * - 游戏开始时给予一个
     * - 在商店可以花费100金币购买
     * - 吃掉时恢复0.35的san值（35%）
     */
    public static final Item MINT_CANDIES = register(
            new MintCandiesItem(new Item.Properties().stacksTo(16)),
            "mint_candies", SANITY_GROUP);
    /**
     * 石粒架
     * - 食物，食用后恢复基于体力上限 50% 的体力
     */
    public static final Item SHILIJIA = register(
            new ShilijiaItem(new Item.Properties().stacksTo(16)),
            "shilijia", CONSUMABLES_GROUP);
    /**
     * 前人留下的马铠 - 装备到残月萨马/彩虹马时提升移动速度与生命上限
     */
    public static final Item PREDECESSOR_HORSE_ARMOR = register(
            new PredecessorHorseArmorItem(new Item.Properties().stacksTo(1)),
            "predecessor_horse_armor", FUNNY_ITEMS_GROUP);
    /**
     * 花圈
     * - 穿戴在头部时持续恢复san值
     * - 提供 MOOD_REGENERATION 效果
     */
    public static final Item WREATH = register(
            new WreathItem(ArmorMaterials.CHAIN, ArmorItem.Type.HELMET,
                    (new Item.Properties()).stacksTo(1)),
            "wreath", EQUIPMENT_GROUP, SANITY_GROUP);
    /**
     * 巧克力
     * - 食用后15秒内san值不会下降
     * - 提供 MOOD_DRAIN_IMMUNITY 效果
     */
    public static final Item CHOCOLATE = register(
            new ChocolateItem(new Item.Properties().stacksTo(64)),
            "chocolate", SANITY_GROUP);
    /**
     * 安神茶
     * - 饮用后60秒内san值消耗减缓
     * - 提供 MOOD_DRAIN_REDUCTION 效果
     */
    public static final Item CALMING_TEA = register(
            new CalmingTeaItem(new Item.Properties().stacksTo(64)),
            "calming_tea", SANITY_GROUP);
    /**
     * 护身符
     * - 携带在物品栏中即可降低低san视觉干扰并缓慢恢复san值
     * - 提供 LOW_SAN_SHADER_RESISTANCE + MOOD_REGENERATION 效果
     */
    public static final Item TALISMAN = register(
            new TalismanItem(new Item.Properties().stacksTo(1)),
            "talisman", SANITY_GROUP);
    /**
     * 提神咖啡
     * - 饮用后30秒内大幅恢复san值并获得速度提升
     * - 提供 MOOD_REGENERATION Lv.2 + MOVEMENT_SPEED 效果
     */
    public static final Item ENERGIZING_COFFEE = register(
            new EnergizingCoffeeItem(new Item.Properties().stacksTo(64)),
            "energizing_coffee", SANITY_GROUP);
    /**
     * 记录笔记
     * - 记录员专属物品
     * - 开局给予
     * - 右键使用打开记录界面
     */
    public static final Item WRITTEN_NOTE = register(
            new WrittenNoteItem(new Item.Properties().stacksTo(1)),
            "written_note", ROLE_ITEMS_GROUP);
    /**
     * 巨大便签
     * - 记者专属可购买道具
     * - 生成一个10倍大小的便签实体，可贴在人身上
     */
    public static final Item GIANT_NOTE = register(
            new GiantNoteItem(new Item.Properties().stacksTo(1)),
            "giant_note", ROLE_ITEMS_GROUP);
    /**
     * 炸弹
     * - 炸弹客专属物品
     * - 倒计时10秒，前5秒隐形
     * - 右键传递
     */
    public static final Item BOMB = register(
            new BombItem(new Item.Properties().stacksTo(1)),
            "bomb", ROLE_ITEMS_GROUP);
    /**
     * 轮椅
     */
    public static final Item WHEELCHAIR = register(
            new WheelchairItem(),
            "wheelchair", ROLE_ITEMS_GROUP);

    /**
     * 巫师法杖 / 魔药
     */
    public static final Item WIZARD_STAFF = register(
            new org.agmas.noellesroles.content.item.WizardStaffItem(new Item.Properties().stacksTo(1)),
            "wizard_staff", ROLE_ITEMS_GROUP);
    public static final Item WIZARD_POTION = register(
            new org.agmas.noellesroles.content.item.WizardPotionItem(new Item.Properties().stacksTo(16)),
            "wizard_potion", ROLE_ITEMS_GROUP);

    /**
     * 占卜家晶球
     */
    public static final Item CRYSTAL_BALL = register(
            new org.agmas.noellesroles.content.item.CrystalBallItem(new Item.Properties().stacksTo(1)),
            "crystal_ball", ROLE_ITEMS_GROUP);
    // 新增物品：短管霰弹枪 / 防暴盾 / 警棍 / 对讲机
    public static final Item SHORT_SHOTGUN = register(
            new org.agmas.noellesroles.content.item.ShortShotgunItem(
                    new Item.Properties().stacksTo(1).durability(1)),
            "short_shotgun", WEAPONS_GROUP);
    public static final Item RIOT_SHIELD = register(
            new org.agmas.noellesroles.content.item.RiotShieldItem(
                    new Item.Properties().stacksTo(1).durability(1)),
            "riot_shield", WEAPONS_GROUP);
    public static final Item BATON = register(
            new org.agmas.noellesroles.content.item.BatonItem(
                    new Item.Properties().stacksTo(1).durability(4)),
            "baton", WEAPONS_GROUP);
    public static final Item BONE_STAFF = register(
            new org.agmas.noellesroles.content.item.BoneStaffItem(
                    new Item.Properties().stacksTo(1).durability(5)),
            "bone_staff", WEAPONS_GROUP);
    /**
     * 格罗赛尔游记
     * - 右键蓄力1秒将瞄准的目标玩家放逐进游记（配置坐标）
     * - 游记内无法攻击/受伤、无法使用技能/物品，死亡改判为持有者击杀
     * - 站上信标即可回归被放逐前的位置
     * - 使用后进入75秒冷却
     */
    public static final Item GROSELL_TRAVELOG = register(
            new org.agmas.noellesroles.content.item.GrosellTravelogItem(
                    new Item.Properties().stacksTo(1)),
            "grosell_travelog", ROLE_ITEMS_GROUP);
    public static final Item LEON_BLUE_HERB = register(
            new org.agmas.noellesroles.content.item.LeonBlueHerbItem(
                    new Item.Properties().stacksTo(1)),
            "leon_blue_herb", ROLE_ITEMS_GROUP);
    public static final Item LEON_RED_HERB = register(
            new org.agmas.noellesroles.content.item.LeonRedHerbItem(
                    new Item.Properties().stacksTo(1)),
            "leon_red_herb", ROLE_ITEMS_GROUP);
    public static final Item RADIO = register(
            new org.agmas.noellesroles.content.item.RadioItem(new Item.Properties().stacksTo(1)),
            "radio", TOOLS_GROUP);
    public static final Item MONITORING_TERMINAL = register(
            new org.agmas.noellesroles.content.item.MonitoringTerminalItem(
                    new Item.Properties().stacksTo(1)),
            "monitoring_terminal", TOOLS_GROUP);
    public static final Item DEALER_PACKAGE = register(
            new DealerPackageItem(new Item.Properties().stacksTo(1)),
            "dealer_package", ROLE_ITEMS_GROUP);
    /**
     * 锁
     * - 工程师专属物品
     * - 工程师商店购买
     * - 右键门：将门锁上，使用撬锁器时需要解锁，失败后损坏撬锁器
     * - 默认长度为6，如有需要以后可以利用json进行配置
     */
    public static final Item LOCK_ITEM = register(
            new LockItem(6, 0.1f, new Item.Properties().stacksTo(1)),
            "lock", ROLE_ITEMS_GROUP);

    /**
     * 怀表
     * - 右键使用查看当前局内游戏时间
     * - 使用后进入60秒冷却
     * - 钟表匠商店可用100金币购买
     */
    public static final Item POCKET_WATCH = register(
            new PocketWatchItem(new Item.Properties().stacksTo(1)),
            "pocket_watch", TOOLS_GROUP);

    /**
     * 肾上腺素
     * - 一次性道具
     * - 对目标使用后增加体力上限
     */
    public static final Item ADRENALINE = register(
            new AdrenalineItem(new Item.Properties().stacksTo(1)),
            "adrenaline", CONSUMABLES_GROUP);

    /**
     * 抗生素
     * - 一次性道具
     * - 对目标使用后使目标解除中毒
     */
    public static final Item ANTIBIOTIC = register(
            new AntibioticItem(new Item.Properties().stacksTo(1)),
            "antibiotic", CONSUMABLES_GROUP);

    /**
     * 鹤顶红
     * - 一次性道具
     * - 对目标使用后使目标中毒
     */
    public static final Item HEDINGHONG = register(
            new HedinghongItem(new Item.Properties().stacksTo(1)),
            "hedinghong", CONSUMABLES_GROUP);

    /**
     * 狗皮膏药
     * - 一次性道具
     * - 对目标使用后使目标30秒内san值不会下降
     */
    public static final Item DOGSKIN_PLASTER = register(
            new DogskinPlasterItem(new Item.Properties().stacksTo(1)),
            "dogskin_plaster", SANITY_GROUP);

    /**
     * 维生素
     * - 一次性道具
     * - 对目标使用后使其获得san值恢复
     */
    public static final Item ALCHEMIST_BUFF_POTION = register(
            new AlchemistBuffPotionItem(new Item.Properties().stacksTo(1)),
            "alchemist_buff_potion", CONSUMABLES_GROUP);

    /**
     * 消防斧
     * - 3点耐久
     * - Shift+右键：直接撬开门，消耗1点耐久，30秒冷却
     * - 直接右键：像刀一样举起，蓄力2秒，可击杀一名玩家，消耗3点耐久（需满耐久）
     * - 击杀玩家会触发误杀惩罚
     */
    public static final Item FIRE_AXE = register(
            new FireAxeItem(new Item.Properties().stacksTo(1).durability(3)),
            "fire_axe", WEAPONS_GROUP);
    public static final Item THROWING_KNIFE = register(
            new ThrowingKnife((new Item.Properties()).stacksTo(1)), "throwing_knife",
            WEAPONS_GROUP);
    // 飞斧 - 强盗的可投掷武器：直线飞行、穿透击杀 2 名玩家、撞墙钉住 5 秒后消失
    public static final Item THROWING_AXE = register(
            new ThrowingAxeItem((new Item.Properties()).stacksTo(1)), "throwing_axe",
            WEAPONS_GROUP);

    // 灵梦的御币
    public static final Item REIMU_GOHEI = register(
            new GoheiItem((new Item.Properties()).stacksTo(1)), "reimu_gohei",
            WEAPONS_GROUP);
    // 弹幕
    public static final Item DANMUKU = register(
            new DanmukuItem((new Item.Properties()).stacksTo(8)), "danmuku",
            WEAPONS_GROUP);
    // 迷你八卦炉
    public static final Item MINI_BAGUALU = register(
            new MinibagualuItem((new Item.Properties()).stacksTo(1).durability(1)), "mini_bagualu",
            WEAPONS_GROUP);
    // ==================== Dream（梦魇）专属 ====================
    /**
     * Dream 的铁斧
     * - 12点耐久，命中消耗1点；商店第二次购买半价
     * - 左键：蓄力条满才能攻击，扣虚拟血量（平A 9 / 跳劈 12；狂暴 12 / 20），命中眩晕
     * - 右键：原地跳跃（0.2s冷却）
     */
    public static final Item DREAM_AXE = register(
            new org.agmas.noellesroles.content.item.DreamAxeItem(
                    new Item.Properties().stacksTo(1).durability(12)
                            // 斧头攻速：蓄力条 ~1.1 秒充满（伤害走虚拟血量，原版攻击力无效）
                            .attributes(AxeItem.createAttributes(Tiers.IRON, 0.0F, -3.1F))),
            "dream_axe", ROLE_ITEMS_GROUP, WEAPONS_GROUP);
    /**
     * 巨幕面具
     * - 右键：Dream 进入狂暴状态（120s冷却）；购买时附赠一层护盾
     */
    public static final Item DREAM_MASK = register(
            new org.agmas.noellesroles.content.item.DreamMaskItem(new Item.Properties().stacksTo(1)),
            "dream_mask", ROLE_ITEMS_GROUP);
    /**
     * Dream 的钻石镐
     * - 潜行+右键：撬开门（无法锁门），破坏动静很大（额外响亮破坏声）
     */
    public static final Item DREAM_PICKAXE = register(
            new org.agmas.noellesroles.content.item.DreamPickaxeItem(new Item.Properties().stacksTo(1)),
            "dream_pickaxe", ROLE_ITEMS_GROUP, TOOLS_GROUP);
    /**
     * Dream 的船
     * - 对地面使用：放置一条船，强制周围玩家乘坐，10s后消失；60s冷却
     */
    public static final Item DREAM_BOAT = register(
            new org.agmas.noellesroles.content.item.DreamBoatItem(new Item.Properties().stacksTo(1)),
            "dream_boat", ROLE_ITEMS_GROUP);
    /**
     * Dream 的范围关灯
     * - 右键：熄灭半径30格内的灯（一次性）
     */
    public static final Item DREAM_BLACKOUT = register(
            new org.agmas.noellesroles.content.item.DreamBlackoutItem(new Item.Properties().stacksTo(1)),
            "dream_blackout", ROLE_ITEMS_GROUP);
    /**
     * Dream 酿的酒
     * - 制酒技能产物；喝下隐身10s，期间无法攻击、无法受伤
     */
    public static final Item DREAM_WINE = register(
            new org.agmas.noellesroles.content.item.DreamWineItem(new Item.Properties().stacksTo(1)),
            "dream_wine", ROLE_ITEMS_GROUP, CONSUMABLES_GROUP);
    /**
     * 绳索
     * - 2点耐久
     * - 右键：将前方直线距离12格内你瞄准的玩家拉到自己身前
     * - 每次右键后进入3秒冷却，成功拉取且非创造模式时进入5秒冷却并消耗1点耐久
     */
    public static final Item ROPE = register(
            new RopeItem(new Item.Properties().stacksTo(1).durability(2)),
            "rope", TOOLS_GROUP);
    public static final Item CAMERA_SHEARS = register(
            new CameraShearsItem(new Item.Properties().stacksTo(1).durability(3)),
            "camera_shears", TOOLS_GROUP);
    /**
     * 灭火器
     * - 5点耐久
     * - 右键对人喷射：每使用一次消耗1点耐久
     * 长按右键持续喷射：最多持续5秒，持续消耗耐久
     * - 对人喷射效果：缓慢 + 失明（持续1.5秒）
     * - 持续喷射同一人会刷新效果时间
     * - 如果被喷射的人被纵火犯浇湿，则清除浇湿状态
     */
    public static final Item EXTINGUISHER = register(
            new ExtinguisherItem(new Item.Properties().stacksTo(1).durability(5)),
            "extinguisher");

    /**
     * 存折
     * - 用于查看和记录金币数量
     * - 右键使用显示当前金币
     */
    public static final Item PASSBOOK = register(
            new PassbookItem(new Item.Properties().stacksTo(1)),
            "passbook", TOOLS_GROUP);

    /**
     * 药剂素材
     * - 用于药剂相关合成
     */
    public static final Item ALCHEMY_MATERIAL = register(
            new AlchemyMaterialItem(new Item.Properties().stacksTo(64)),
            "alchemy_material", ROLE_ITEMS_GROUP);

    /**
     * 签名纸
     */
    public static final Item SIGNATURE_PAPER = register(
            new SignaturePaperItem(new Item.Properties().stacksTo(1)),
            "signature_paper", ROLE_ITEMS_GROUP);

    /**
     * 生死状
     */
    public static final Item LIFE_AND_DEATH_SHAPE = register(
            new SignedPaperItem(new Item.Properties().stacksTo(1)),
            "life_and_death_shape", ROLE_ITEMS_GROUP);

    /**
     * 明星签名
     */
    public static final Item SIGNED_PAPER = register(
            new SignedPaperItem(new Item.Properties().stacksTo(1)),
            "signed_paper", ROLE_ITEMS_GROUP);

    /**
     * 雇佣契约（未签订/已签订共用物品）
     */
    public static final Item MERCENARY_CONTRACT = register(
            new MercenaryContractItem(new Item.Properties().stacksTo(1)),
            "mercenary_contract", ROLE_ITEMS_GROUP);

    /** 信使信封（发送用） */
    public static final Item COURIER_MAIL = register(
            new org.agmas.noellesroles.content.item.CourierMailItem(new Item.Properties().stacksTo(1)),
            "courier_mail", ROLE_ITEMS_GROUP);

    /** 信使信封（接收用） */
    public static final Item RECEIVED_MAIL = register(
            new org.agmas.noellesroles.content.item.CourierMailItem(new Item.Properties().stacksTo(1)),
            "received_mail", ROLE_ITEMS_GROUP);

    /**
     * 时停钟
     */
    public static final Item TIME_STOP_CLOCK = register(
            new TimeStopClock(new Item.Properties().stacksTo(1).durability(TimeStopClock.MAX_DURABILITY)
                    .component(DataComponents.CUSTOM_DATA, TimeStopClock.getDefaultCustomData())),
            "time_stop_clock", ROLE_ITEMS_GROUP);

    /**
     * 处刑者手枪
     * - 愚者专属武器
     * - 只能对"异端"效果的玩家造成伤害（一击必杀）
     * - 初始子弹数1，只能通过塔罗会补充
     */
    public static final Item EXECUTIONER_GUN = register(
            new org.agmas.noellesroles.game.roles.innocence.fool.ExecutionerGunItem(
                    new Item.Properties().stacksTo(1)),
            "executioner_gun", WEAPONS_GROUP);

    /**
     * 零一五 - 双发手枪
     * - 右键开枪，开枪后0.15秒自动开第二枪
     * - 一枪命中只会给3秒缓慢2
     * - 同一玩家被命中两次则造成击杀
     * - 冷却15秒，射程30格
     * - 材质沿用一次性手枪
     * - 无限耐久，两枪后进入15秒冷却
     */
    public static final Item ZERO_ONE_FIVE_GUN = register(
            new org.agmas.noellesroles.content.item.ZeroOneFiveGunItem(
                    new Item.Properties().stacksTo(1)),
            "zero_one_five_gun", WEAPONS_GROUP);

    /**
     * 尊名纸条
     * - 愚者商店购买（50金币）
     * - 右键墙壁/地面贴附，生成不可破坏的文本实体
     * - 玩家对着纸条按V键祷告，获得"塔罗会成员"标签
     */
    public static final Item HONORED_NOTE = register(
            new org.agmas.noellesroles.game.roles.innocence.fool.HonoredNoteItem(
                    new Item.Properties().stacksTo(16)),
            "honored_note", ROLE_ITEMS_GROUP);

    /**
     * 灵性斗篷
     * - 愚者商店购买（200金币）
     * - 右键使用后获得5秒无敌、无法攻击、移动速度不变
     * - 冷却90秒
     */
    public static final Item SPIRIT_CLOAK = register(
            new org.agmas.noellesroles.game.roles.innocence.fool.SpiritCloakItem(
                    new Item.Properties().stacksTo(1)),
            "spirit_cloak", ROLE_ITEMS_GROUP);

    // 封印物：收益与代价并存的稀有神秘物品。
    public static final Item SEALED_COIN_OF_ECHOES = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    SealedArtifactItem.Tier.FRAGMENT, "sealed_coin_of_echoes"),
            "sealed_coin_of_echoes", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_BLIND_LANTERN = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    SealedArtifactItem.Tier.FRAGMENT, "sealed_blind_lantern"),
            "sealed_blind_lantern", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_RUSTED_ANKLET = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.RARE),
                    SealedArtifactItem.Tier.RELIC, "sealed_rusted_anklet"),
            "sealed_rusted_anklet", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_MIRROR_SHARD = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    SealedArtifactItem.Tier.RELIC, "sealed_mirror_shard"),
            "sealed_mirror_shard", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_BREATHLESS_BREAD = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    SealedArtifactItem.Tier.ANOMALY, "sealed_breathless_bread"),
            "sealed_breathless_bread", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_THUNDERBOLT_NAIL = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    SealedArtifactItem.Tier.ANOMALY, "sealed_thunderbolt_nail"),
            "sealed_thunderbolt_nail", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_VANISHING_CLOAK = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    SealedArtifactItem.Tier.CALAMITY, "sealed_vanishing_cloak"),
            "sealed_vanishing_cloak", SEALED_ARTIFACTS_GROUP);
    public static final Item SEALED_DOORLESS_KEY = register(
            new SealedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC),
                    SealedArtifactItem.Tier.CALAMITY, "sealed_doorless_key"),
            "sealed_doorless_key", SEALED_ARTIFACTS_GROUP);

    public static final List<Item> SEALED_ARTIFACTS = List.of(
            SEALED_COIN_OF_ECHOES,
            SEALED_BLIND_LANTERN,
            SEALED_RUSTED_ANKLET,
            SEALED_MIRROR_SHARD,
            SEALED_BREATHLESS_BREAD,
            SEALED_THUNDERBOLT_NAIL,
            SEALED_VANISHING_CLOAK,
            SEALED_DOORLESS_KEY);

    public static final Item ZHANWEIFU1 = registrar.create("zhanweifu1",
            new Item(new Item.Properties().stacksTo(64)));
    public static final Item ZHANWEIFU2 = registrar.create("zhanweifu2",
            new Item(new Item.Properties().stacksTo(64)));

    // 轮盘赌物品
    public static final Item MAGNIFYING_GLASS = register(new Item(new Item.Properties().stacksTo(64)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable("noellesroles.game.devil_roulette.tooltip.magnifying_glass")
                    .withStyle(ChatFormatting.GRAY));
        }
    }, "magnifying_glass", MISC_ITEMS_GROUP);
    public static final Item CHEWING = register(new Item(new Item.Properties().stacksTo(64)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable("noellesroles.game.devil_roulette.tooltip.chewing")
                    .withStyle(ChatFormatting.GRAY));
        }
    }, "chewing", MISC_ITEMS_GROUP);
    public static final Item CLIP = register(new Item(new Item.Properties().stacksTo(64)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable("noellesroles.game.devil_roulette.tooltip.clip")
                    .withStyle(ChatFormatting.GRAY));
        }
    }, "clip", MISC_ITEMS_GROUP);
    public static final Item STEEL_BALL = register(new Item(new Item.Properties().stacksTo(64)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable("noellesroles.game.devil_roulette.tooltip.steel_ball")
                    .withStyle(ChatFormatting.GRAY));
        }
    }, "steel_ball", MISC_ITEMS_GROUP);
    public static final Item REVERSING_CARD = register(new Item(new Item.Properties().stacksTo(64)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable("noellesroles.game.devil_roulette.tooltip.reversing_card")
                    .withStyle(ChatFormatting.GRAY));
        }
    }, "reversing_card", MISC_ITEMS_GROUP);
    public static final Item TELEPHONE = register(new Item(new Item.Properties().stacksTo(64)) {
        @Override
        public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                TooltipFlag flag) {
            tooltip.add(Component.translatable("noellesroles.game.devil_roulette.tooltip.telephone")
                    .withStyle(ChatFormatting.GRAY));
        }
    }, "telephone", MISC_ITEMS_GROUP);

    /**
     * C4炸药
     * - 右键玩家：在目标玩家身上放置C4
     * - 右键空气：投掷C4实体
     */
    public static final Item C4 = register(
            new org.agmas.noellesroles.content.item.C4Item(new Item.Properties().stacksTo(16)),
            "c4", ROLE_ITEMS_GROUP);

    /**
     * C4引爆器
     * - 右键使用：引爆所有已放置的C4
     */
    public static final Item C4_DETONATOR = register(
            new org.agmas.noellesroles.content.item.C4DetonatorItem(new Item.Properties().stacksTo(1)),
            "c4_detonator", ROLE_ITEMS_GROUP);

    /**
     * 钳子
     * - 右键玩家：拆除玩家身上的C4
     * - 右键空气：拆除地面的C4实体
     */
    public static final Item PLIERS = register(
            new org.agmas.noellesroles.content.item.PliersItem(
                    new Item.Properties().stacksTo(1).durability(3)),
            "pliers", TOOLS_GROUP);

    /**
     * 开灯
     * - 立即结束关灯时间并清除全场黑暗与失明药水效果
     * - 未处于关灯时间无法购买
     */
    public static final Item LIGHTUP = register(
            new Item(new Item.Properties().stacksTo(1)),
            "lightup", MISC_ITEMS_GROUP);

    /**
     * 监控恢复
     * - 立即结束监控失灵时间
     * - 未处于监控失灵期间无法购买
     */
    public static final Item MONITOR_RECOVERY = register(
            new Item(new Item.Properties().stacksTo(1)),
            "monitor_recovery", MISC_ITEMS_GROUP);

    /**
     * 子弹
     * - 右键使用：装填子弹
     */
    public static final Item BULLET = register(
            new org.agmas.noellesroles.content.item.BulletItem(new Item.Properties().stacksTo(64)),
            "bullet", CONSUMABLES_GROUP);

    /**
     * 磁铁
     * - 携带在物品栏中时持续吸取周围8格内的掉落物到自己身边
     */
    public static final Item MAGNET = register(
            new MagnetItem(new Item.Properties().stacksTo(1)),
            "magnet", TOOLS_GROUP);

    /**
     * 运输物品（场景任务「运输点任务」）
     * - 在运输点起点右键获得此物品
     * - 手持此物品右键运输点终点即可完成运输任务
     */
    public static final Item TRANSPORT_PACKAGE = register(
            new Item(new Item.Properties().stacksTo(1)),
            "transport_package", MISC_ITEMS_GROUP);

    public static final Item SCARLET_PERCEPTION_SWORD = register(
            new ScarletPerceptionSwordItem(
                    new Item.Properties().stacksTo(1)
                            .attributes(AxeItem.createAttributes(Tiers.WOOD, 0.0F, -3.0F))),
            "scarlet_perception_sword", ROLE_ITEMS_GROUP, WEAPONS_GROUP);
    public static final ItemStack ExamplerPsychoItemStack = TMMItems.PSYCHO_MODE.getDefaultInstance();
    public static Map<Item, Integer> ITEM_COOLDOWNS = new HashMap<>();
    static {
        var examplerPsychoLore = new ItemLore(
                List.of(Component.translatable("itemstack.exampler.psychoitem.item_lore.1"),
                        Component.translatable("itemstack.exampler.psychoitem.item_lore.2")));
        ExamplerPsychoItemStack.set(DataComponents.LORE, examplerPsychoLore);
        ExamplerPsychoItemStack.set(DataComponents.ITEM_NAME,
                Component.translatable("itemstack.exampler.psychoitem.item_name"));
        ChargeableItemRegistry.register(ANTIDOTE_REAGENT, new AntidoteReagentChargeItem());
        ChargeableItemRegistry.register(FunnyItems.BOWEN_BADGE, new BowenBadgeChargeItem());
        ChargeableItemRegistry.register(ModItems.STALKER_KNIFE, new StalkerKnifeChargeItem());
        ChargeableItemRegistry.register(ModItems.SILENCE_TOTEM, new SilenceTotemChargeItem());
        ChargeableItemRegistry.register(ModItems.STALKER_KNIFE_OFFHAND, new StalkerKnifeChargeItem());
        ChargeableItemRegistry.register(TOXIN, new ToxinChargeItem());
        ChargeableItemRegistry.register(ModItems.THROWING_KNIFE, new KnifeChargeableItem());
        ChargeableItemRegistry.register(ANTIDOTE, new AntidoteChargeItem());
    }
    // public static final Item SHERIFF_GUN_MAINTENANCE = register(
    // new SheriffGunMaintenanceItem(new Item.Settings().maxCount(1)),
    // "sheriff_gun_maintenance"
    // );
    // public static final Item SHERIFF_GUN_MAINTENANCE = register(
    // new SheriffGunMaintenanceItem(new Item.Settings().maxCount(1)),
    // "sheriff_gun_maintenance"
    // );

    /**
     * 桃木钉
     * - 诡客商店专属（Guest Ghost）
     * - 对布袋鬼使用，使其鬼术进入冷却 + 移除护盾
     */
    public static final Item TAOMUDING = register(
            new Item(new Item.Properties().stacksTo(1)),
            "taomuding", ROLE_ITEMS_GROUP);

    public static Item register(Item item, String id, ResourceKey<CreativeModeTab>... extraGroups) {
        ResourceKey<CreativeModeTab>[] allGroups = java.util.Arrays.copyOf(extraGroups, extraGroups.length + 1);
        allGroups[extraGroups.length] = NOELLESROLES_ALL_GROUP;
        var registeredItem = registrar.create(id, item, allGroups);
        TMMDescItems.introItems.add(registeredItem);

        return registeredItem;
    }

    public static void init() {
        registrar.registerEntries();
        // 不再注册旧的 MISC_CREATIVE_GROUP 和 SAN_CREATIVE_GROUP，所有物品已分配到新分类标签页
        TMMItems.INVISIBLE_ITEMS.add(ModItems.PAN);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.GROSELL_TRAVELOG);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.SMOKE_GRENADE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.BLANK_CARTRIDGE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.ALARM_TRAP);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.HALLUCINATION_BOTTLE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.REINFORCEMENT);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.SCREWDRIVER);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.CONSPIRACY_PAGE);
        TMMItems.INVISIBLE_ITEMS.add(Items.BUNDLE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.DEDUCTION_BOOK);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.REASONER_COMPASS);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.CRYSTAL_BALL);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.LETTER_ITEM);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.DEFIBRILLATOR);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.BOMB);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.COURIER_MAIL);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.RECEIVED_MAIL);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.WRITTEN_NOTE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.FLASH_GRENADE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.DECOY_GRENADE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.SILENCE_TOTEM);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.PURIFY_BOMB);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.DEALER_PACKAGE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.HONORED_NOTE);
        TMMItems.INVISIBLE_ITEMS.add(ModItems.SPIRIT_CLOAK);
        // TMMItems.INVISIBLE_ITEMS.add(TMMItems.KNIFE);

        // 为潜水靴添加深海探索者3附魔
        // 在商店或创造模式中生成时自带附魔，使用DataComponent设置

        TMMItems.INIT_ITEMS.LETTER = LETTER_ITEM;
        TMMItems.INIT_ITEMS.LETTER_UpdateItemFunc = (letter, serverPlayerEntity) -> {

        };
        ITEM_COOLDOWNS.put(ModItems.ANTIDOTE, getInTicks(1, 0)); // 60秒冷却
        ITEM_COOLDOWNS.put(ModItems.TOXIN, getInTicks(0, 10));
        ITEM_COOLDOWNS.put(ModItems.BANDIT_REVOLVER, getInTicks(0, 40));
        ITEM_COOLDOWNS.put(ModItems.SHORT_SHOTGUN, getInTicks(30, 0));
        ITEM_COOLDOWNS.put(TMMItems.SCORPION, getInTicks(0, 35));
        ITEM_COOLDOWNS.put(ModItems.CATALYST, getInTicks(0, 75));
        DevItems.init();
    }

    public static ItemStack createPillStack(boolean poisonous) {
        ItemStack stack = PILL.getDefaultInstance();
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(PILL_POISONOUS_KEY, poisonous);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }
}
