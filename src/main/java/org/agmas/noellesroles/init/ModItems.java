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

    // 末日60秒模式：小/中/高三级水（恢复口渴值）
    public static final Item SIXTY_SECONDS_WATER_SMALL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem(
                    new Item.Properties().stacksTo(1), "small", 15),
            "sixty_seconds_water_small", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WATER_MEDIUM = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem(
                    new Item.Properties().stacksTo(1), "medium", 35),
            "sixty_seconds_water_medium", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WATER_HIGH = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem(
                    new Item.Properties().stacksTo(1), "high", 60),
            "sixty_seconds_water_high", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：雨伞（污雨事件时在野外持有可免除额外污染）
    public static final Item SIXTY_SECONDS_UMBRELLA = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_umbrella", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：撬棍（强闯别队避难所+报警，一次性）/ 撬锁器（潜行进入不报警，一次性）
    public static final Item SIXTY_SECONDS_CROWBAR = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem(
                    new Item.Properties().stacksTo(1), true),
            "sixty_seconds_crowbar", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_LOCKPICK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem(
                    new Item.Properties().stacksTo(1), false, 2),
            "sixty_seconds_lockpick", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：门锁（右键门安装，抵御一天撬棍） / 门陷阱（开锁器入室触发警报）
    public static final Item SIXTY_SECONDS_DOOR_LOCK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem(
                    new Item.Properties().stacksTo(4)),
            "sixty_seconds_door_lock", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_DOOR_TRAP = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorTrapItem(
                    new Item.Properties().stacksTo(4)),
            "sixty_seconds_door_trap", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：区域绑定工具（管理员搭图用：把避难所门绑定到独立探索区，三步右键）
    public static final Item SIXTY_SECONDS_AREA_WAND = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsAreaWandItem(
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_area_wand", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：NPC 放置器（管理员搭图用：潜行右键空气切变体，右键方块登记生成点并落盘）
    public static final Item SIXTY_SECONDS_NPC_PLACER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsNpcPlacerItem(
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_npc_placer", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // 末日60秒模式：救援信标（隐藏通关——工程学合成，户外激活呼叫救援）
    public static final Item SIXTY_SECONDS_RESCUE_BEACON = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsRescueBeaconItem(
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_rescue_beacon", CONSUMABLES_GROUP);

    // 末日60秒模式：药品（右键治愈生病 + 解除感染风险）
    public static final Item SIXTY_SECONDS_MEDICINE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMedicineItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_medicine", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：科技树/合成材料 ─────────────────────────────────
    public static final Item SIXTY_SECONDS_SCRAP = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_scrap", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_RAG = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_rag", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 酒精：既是医疗材料也可直接喝（+15 理智 +15 口渴）
    public static final Item SIXTY_SECONDS_ALCOHOL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(16), 0, 0, 15, 15, 0, false, null,
                    40, UseAnim.DRINK),
            "sixty_seconds_alcohol", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_DIRTY_WATER = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_dirty_water", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BANDAGE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBandageItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_bandage", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：照明/工具 ──────────────────────────────────────
    public static final Item SIXTY_SECONDS_TORCH = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsTorchItem(
                    new Item.Properties().stacksTo(1).durability(200)),
            "sixty_seconds_torch", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CLOCK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsClockItem(
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_clock", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WRENCH = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsWrenchItem(
                    new Item.Properties().stacksTo(1).durability(20)),
            "sixty_seconds_wrench", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：近战武器（左键按武器扣健康值，见 SixtySecondsWeapons；durability=可挥砍次数）──
    public static final Item SIXTY_SECONDS_PIPE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(12)
                            .attributes(SwordItem.createAttributes(Tiers.STONE, 3, -2.4F)), 25),
            "sixty_seconds_pipe", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SPIKED_BAT = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(14)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 4, -2.6F)), 30),
            "sixty_seconds_spiked_bat", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_MACHETE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(20)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 5, -2.2F)), 35),
            "sixty_seconds_machete", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：夜袭者召唤哨（对地面右键生成对应强度的夜袭者；见 SixtySecondsAssaultSpawnItem）──
    public static final Item SIXTY_SECONDS_ASSAULT_SPAWNER_WEAK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsAssaultSpawnItem(
                    new Item.Properties().stacksTo(16),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.AssaultTier.WEAK),
            "sixty_seconds_assault_spawner_weak", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ASSAULT_SPAWNER_MEDIUM = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsAssaultSpawnItem(
                    new Item.Properties().stacksTo(16),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.AssaultTier.MEDIUM),
            "sixty_seconds_assault_spawner_medium", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ASSAULT_SPAWNER_STRONG = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsAssaultSpawnItem(
                    new Item.Properties().stacksTo(16),
                    net.exmo.sre.sixtyseconds.logic.SixtySecondsDefenseSystem.AssaultTier.STRONG),
            "sixty_seconds_assault_spawner_strong", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：实体游戏币（1枚=1游戏币；E背包兑出、右键存回余额；见 SixtySecondsCoinItem）──
    public static final Item SIXTY_SECONDS_COIN = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsCoinItem(
                    new Item.Properties().stacksTo(64)),
            "sixty_seconds_coin", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：枪械（右键开火，需子弹，命中怪物即死/玩家扣50血；见 SixtySecondsGunItem）──
    public static final Item SIXTY_SECONDS_AMMO = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_ammo", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PISTOL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem(
                    new Item.Properties().durability(10),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_PISTOL_COOLDOWN,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_PISTOL_RANGE,30,1),
            "sixty_seconds_pistol", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HUNTING_SHOTGUN = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem(
                    new Item.Properties().durability(6),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_SHOTGUN_COOLDOWN,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_SHOTGUN_RANGE,40,1),
            "sixty_seconds_hunting_shotgun", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 步枪：改用步枪子弹
    public static final Item SIXTY_SECONDS_RIFLE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem(
                    new Item.Properties().durability(12),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_RIFLE_COOLDOWN,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_RIFLE_RANGE, 50, 1, false,
                    () -> ModItems.SIXTY_SECONDS_RIFLE_AMMO, 0, 0),
            "sixty_seconds_rifle", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 狙击枪：超远射程、一枪打空健康（直接倒地）、20s 冷却（与全部枪械共享）、带瞄准镜（右键开镜→再右键射击）
    // 每发消耗 1 个马格南子弹
    public static final Item SIXTY_SECONDS_SNIPER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem(
                    new Item.Properties().durability(4),
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_SNIPER_COOLDOWN,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_SNIPER_RANGE,
                    net.exmo.sre.sixtyseconds.SixtySecondsBalance.GUN_SNIPER_DAMAGE, 1, true,
                    () -> ModItems.SIXTY_SECONDS_MAGNUM_AMMO, 0, 0),
            "sixty_seconds_sniper", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // RPG：范围爆炸（含自伤）、每发耗 5 子弹、8s 冷却（与全部枪械共享）
    public static final Item SIXTY_SECONDS_RPG = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsRpgItem(
                    new Item.Properties().durability(4)),
            "sixty_seconds_rpg", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：护甲（按件百分比减免健康伤害，见 SixtySecondsWeapons）──
    public static final Item SIXTY_SECONDS_SCRAP_HELMET = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET,
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_scrap_helmet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SCRAP_CHESTPLATE = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_scrap_chestplate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_IRON_HELMET = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET,
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_iron_helmet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_IRON_CHESTPLATE = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_iron_chestplate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：背包（小9格/中18格/大27格，内容存 CONTAINER 组件）───
    public static final Item SIXTY_SECONDS_BACKPACK_SMALL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBackpackItem(
                    new Item.Properties().stacksTo(1), 1),
            "sixty_seconds_backpack_small", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BACKPACK_MEDIUM = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBackpackItem(
                    new Item.Properties().stacksTo(1), 2),
            "sixty_seconds_backpack_medium", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BACKPACK_LARGE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBackpackItem(
                    new Item.Properties().stacksTo(1), 3),
            "sixty_seconds_backpack_large", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：更多基础资源/工具 ────────────────────────────────
    public static final Item SIXTY_SECONDS_DUCT_TAPE = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_duct_tape", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BATTERY = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_battery", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 手电筒：手持时夜间免疫低语怪掉san并获得夜视（见 SixtySecondsWhisperSystem）；
    // 右键用强光驱散周围低语怪，每次耗 50 耐久（电量），150 耐久 ≈ 3 次
    public static final Item SIXTY_SECONDS_FLASHLIGHT = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsFlashlightItem(
                    new Item.Properties().stacksTo(1).durability(150)),
            "sixty_seconds_flashlight", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：娱乐物品（右键给周围玩家恢复理智；恢复量/耐久按类型不同）──
    public static final Item SIXTY_SECONDS_POKER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem(
                    new Item.Properties().stacksTo(1).durability(3),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem.Kind.POKER, 8),
            "sixty_seconds_poker", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CHESS = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem(
                    new Item.Properties().stacksTo(1).durability(2),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem.Kind.CHESS, 10),
            "sixty_seconds_chess", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HARMONICA = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem(
                    new Item.Properties().stacksTo(1).durability(3),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem.Kind.HARMONICA, 6),
            "sixty_seconds_harmonica", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_GUITAR = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem(
                    new Item.Properties().stacksTo(1).durability(2),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem.Kind.GUITAR, 12),
            "sixty_seconds_guitar", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_TEDDY_BEAR = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem(
                    new Item.Properties().stacksTo(1).durability(2),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsEntertainmentItem.Kind.TEDDY_BEAR, 15),
            "sixty_seconds_teddy_bear", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：合成材料（loot/工具箱获得，供科技树与配方消耗）──────
    public static final Item SIXTY_SECONDS_PLASTIC = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_plastic", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_GLASS_SHARD = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_glass_shard", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WIRE = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_wire", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_GEAR = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_gear", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_FUEL_CAN = register(
            new Item(new Item.Properties().stacksTo(8)),
            "sixty_seconds_fuel_can", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CLOTH_ROLL = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_cloth_roll", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 绳索：右键原地放一条向上延伸的临时可攀爬绳索（30s 后消失），消耗一根
    public static final Item SIXTY_SECONDS_ROPE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsRopeItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_rope", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 钩锁：右键钩住准星落点把自己荡过去（20 耐久 / 15s 冷却 / 荡索落地无摔落伤害）
    public static final Item SIXTY_SECONDS_GRAPPLING_HOOK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrapplingHookItem(
                    new Item.Properties().stacksTo(1)
                            .durability(net.exmo.sre.sixtyseconds.SixtySecondsBalance.GRAPPLE_DURABILITY)),
            "sixty_seconds_grappling_hook", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 勾爪：右键把准星指向的任何有生命值的实体拉到自己面前（12 耐久 / 8s 冷却，不造成伤害）
    public static final Item SIXTY_SECONDS_CLAW_HOOK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsClawHookItem(
                    new Item.Properties().stacksTo(1).durability(12)),
            "sixty_seconds_claw_hook", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // ── 弓/弩（拉弓蓄力发射 60s 箭矢；powerMult / drawTicks / 是否弩）──────────────
    public static final Item SIXTY_SECONDS_CRUDE_BOW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem(
                    new Item.Properties().stacksTo(1).durability(160), 0.9F, 24),
            "sixty_seconds_crude_bow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HUNTING_BOW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem(
                    new Item.Properties().stacksTo(1).durability(300), 1.1F, 20),
            "sixty_seconds_hunting_bow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_RECURVE_BOW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem(
                    new Item.Properties().stacksTo(1).durability(450), 1.3F, 20),
            "sixty_seconds_recurve_bow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_COMPOUND_BOW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem(
                    new Item.Properties().stacksTo(1).durability(600), 1.5F, 18),
            "sixty_seconds_compound_bow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HAND_CROSSBOW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem(
                    new Item.Properties().stacksTo(1).durability(320), 1.25F, 14),
            "sixty_seconds_hand_crossbow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HEAVY_CROSSBOW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBowItem(
                    new Item.Properties().stacksTo(1).durability(520), 1.7F, 26, true),
            "sixty_seconds_heavy_crossbow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // ── 箭矢（弓/弩弹药，携带 ArrowType）─────────────────────────────────
    public static final Item SIXTY_SECONDS_CRUDE_ARROW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem(new Item.Properties().stacksTo(64),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType.CRUDE),
            "sixty_seconds_crude_arrow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_IRON_ARROW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem(new Item.Properties().stacksTo(64),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType.IRON),
            "sixty_seconds_iron_arrow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_STEEL_ARROW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem(new Item.Properties().stacksTo(64),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType.STEEL),
            "sixty_seconds_steel_arrow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_FIRE_ARROW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem(new Item.Properties().stacksTo(64),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType.FIRE),
            "sixty_seconds_fire_arrow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_POISON_ARROW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem(new Item.Properties().stacksTo(64),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType.POISON),
            "sixty_seconds_poison_arrow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_EXPLOSIVE_ARROW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem(new Item.Properties().stacksTo(64),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsArrowItem.ArrowType.EXPLOSIVE),
            "sixty_seconds_explosive_arrow", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CHEMICALS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_chemicals", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ELECTRONICS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_electronics", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_GUNPOWDER_PACK = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_gunpowder_pack", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：食物（带 FoodProperties，自动接入饱食恢复=营养×5）────
    public static final Item SIXTY_SECONDS_CANNED_FOOD = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(8).saturationModifier(0.6F).build())),
            "sixty_seconds_canned_food", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_MRE = register(
            new Item(new Item.Properties().stacksTo(4).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(12).saturationModifier(0.8F).build())),
            "sixty_seconds_mre", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BISCUIT = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationModifier(0.5F).build())),
            "sixty_seconds_biscuit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 肉干：饱食 +30（变废为宝-I，营养×5）
    public static final Item SIXTY_SECONDS_JERKY = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build())),
            "sixty_seconds_jerky", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_INSTANT_NOODLES = register(
            new Item(new Item.Properties().stacksTo(4).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(7).saturationModifier(0.6F).build())),
            "sixty_seconds_instant_noodles", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CHOCOLATE_BAR = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.4F).fast().build())),
            "sixty_seconds_chocolate_bar", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ENERGY_BAR = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(5).saturationModifier(0.7F).fast().build())),
            "sixty_seconds_energy_bar", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SEEDS_PACK = register(
            new Item(new Item.Properties().stacksTo(16).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(2).saturationModifier(0.3F).fast().build())),
            "sixty_seconds_seeds_pack", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：饮品/药品（SixtySecondsStatItem 统一恢复逻辑）────────
    public static final Item SIXTY_SECONDS_WATER_PACK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsWaterItem(
                    new Item.Properties().stacksTo(4), "pack", 45),
            "sixty_seconds_water_pack", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 饮品：2-3 秒（40-60 ticks），DRINK 动画
    public static final Item SIXTY_SECONDS_JUICE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 10, 25, 0, 0, false, null,
                    50, UseAnim.DRINK),
            "sixty_seconds_juice", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_COFFEE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 0, 10, 10, 0, false, null,
                    40, UseAnim.DRINK),
            "sixty_seconds_coffee", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SEDATIVE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 0, 30, 0, false, null,
                    60, UseAnim.DRINK),
            "sixty_seconds_sedative", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 治疗类：5-10 秒（100-200 ticks），EAT / BOW 动画
    public static final Item SIXTY_SECONDS_ANTIBIOTICS = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 10, 0, 0, 0, 0, true, null,
                    140, UseAnim.EAT),
            "sixty_seconds_antibiotics", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PAINKILLERS = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 15, 0, 0, 0, 0, false, null,
                    100, UseAnim.EAT),
            "sixty_seconds_painkillers", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 非治疗类：2-3 秒（40-60 ticks）
    public static final Item SIXTY_SECONDS_PURIFICATION_TABLET = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 0, 0, 40, false, null,
                    60, UseAnim.EAT),
            "sixty_seconds_purification_tablet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_VITAMIN = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 10, 10, 5, 0, false, null,
                    50, UseAnim.EAT),
            "sixty_seconds_vitamin", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 额外的消除污染消耗品（补充 purification_tablet(-40)/anti_pollution_serum(-60+治愈) 的档位与吃法）
    // 活性炭片：常见、便宜，污染 -20（EAT）
    public static final Item SIXTY_SECONDS_CHARCOAL_PILL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(16), 0, 0, 0, 0, 20, false, null,
                    40, UseAnim.EAT),
            "sixty_seconds_charcoal_pill", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 排毒草茶：污染 -30，兼回理智 +8（DRINK）
    public static final Item SIXTY_SECONDS_DETOX_TEA = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 0, 8, 30, false, null,
                    50, UseAnim.DRINK),
            "sixty_seconds_detox_tea", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 纯净水：污染 -20，兼解渴 +35（DRINK）——净化过的水既解渴又冲刷污染
    public static final Item SIXTY_SECONDS_PURIFIED_WATER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 35, 0, 20, false, null,
                    40, UseAnim.DRINK),
            "sixty_seconds_purified_water", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 治疗类：医疗包 10 秒（200 ticks），BOW 动画（包扎动作）
    public static final Item SIXTY_SECONDS_MEDKIT = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 60, 0, 0, 0, 0, true, null,
                    200, UseAnim.BOW),
            "sixty_seconds_medkit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 肾上腺素：3 秒（60 ticks），DRINK 动画（微量回血+速度效果）
    public static final Item SIXTY_SECONDS_ADRENALINE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 5, 0, 0, 0, 0, false,
                    () -> new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20 * 30, 1, false, false, true),
                    60, UseAnim.DRINK),
            "sixty_seconds_adrenaline", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：更多武器（近战按对应健康值扣血；投掷 AoE）─────────────
    public static final Item SIXTY_SECONDS_KNIFE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(10)
                            .attributes(SwordItem.createAttributes(Tiers.STONE, 2, -2.0F)), 20),
            "sixty_seconds_knife", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SLEDGEHAMMER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(22)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 6, -3.0F)), 45),
            "sixty_seconds_sledgehammer", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CHAINSAW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(24)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 7, -2.8F)), 50),
            "sixty_seconds_chainsaw", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_FIRE_AXE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(18)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 5, -2.8F)), 35),
            "sixty_seconds_fire_axe", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_STUN_BATON = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(12)
                            .attributes(SwordItem.createAttributes(Tiers.STONE, 2, -2.0F)), 15, 20 * 3),
            "sixty_seconds_stun_baton", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_MOLOTOV = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem(
                    new Item.Properties().stacksTo(4), 3.0D, 15.0F, 0, true, false),
            "sixty_seconds_molotov", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PIPE_BOMB = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem(
                    new Item.Properties().stacksTo(4), 4.0D, 40.0F, 30, false, false),
            "sixty_seconds_pipe_bomb", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_FLASHBANG = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem(
                    new Item.Properties().stacksTo(4), 5.0D, 5.0F, 0, false, true),
            "sixty_seconds_flashbang", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：更多装备（护甲减伤见 SixtySecondsWeapons）────────────
    public static final Item SIXTY_SECONDS_SCRAP_LEGGINGS = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_scrap_leggings", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SCRAP_BOOTS = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.BOOTS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_scrap_boots", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_IRON_LEGGINGS = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_iron_leggings", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_IRON_BOOTS = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_iron_boots", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 防毒面具：佩戴时污雨/浓烟污染免疫（SixtySecondsEventSystem）
    public static final Item SIXTY_SECONDS_GAS_MASK = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)),
            "sixty_seconds_gas_mask", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 防化服：佩戴时污染累积减半（SixtySecondsStatsSystem）
    public static final Item SIXTY_SECONDS_HAZMAT_SUIT = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)),
            "sixty_seconds_hazmat_suit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 夜视镜：夜间佩戴获得夜视（SixtySecondsWhisperSystem）
    public static final Item SIXTY_SECONDS_NIGHT_GOGGLES = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)),
            "sixty_seconds_night_goggles", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 防暴盾：手持（主/副手）时受到的健康伤害 ×0.75（SixtySecondsWeapons）
    public static final Item SIXTY_SECONDS_RIOT_SHIELD = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_riot_shield", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：科技/功能道具 ────────────────────────────────────
    public static final Item SIXTY_SECONDS_RADIO = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(1),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.RADIO),
            "sixty_seconds_radio", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_COMPASS = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(1),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.COMPASS),
            "sixty_seconds_compass", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_REPAIR_KIT = register(
            new Item(new Item.Properties().stacksTo(4)),
            "sixty_seconds_repair_kit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SOLAR_PANEL = register(
            new Item(new Item.Properties().stacksTo(4)),
            "sixty_seconds_solar_panel", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ALARM = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(4),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.ALARM),
            "sixty_seconds_alarm", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_LURE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(4),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.LURE),
            "sixty_seconds_lure", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_TOOLBOX = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(4),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.TOOLBOX),
            "sixty_seconds_toolbox", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BLUEPRINT = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(4),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.BLUEPRINT),
            "sixty_seconds_blueprint", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 末日60秒模式：高等级撬棍/开锁器（对应门等级 2/3）─────────────────
    public static final Item SIXTY_SECONDS_CROWBAR_STEEL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem(
                    new Item.Properties().stacksTo(1), true, 2),
            "sixty_seconds_crowbar_steel", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CROWBAR_HYDRAULIC = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem(
                    new Item.Properties().stacksTo(1), true, 3),
            "sixty_seconds_crowbar_hydraulic", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_LOCKPICK_PRO = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem(
                    new Item.Properties().stacksTo(1), false, 3),
            "sixty_seconds_lockpick_pro", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_LOCKPICK_MASTER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBreakInItem(
                    new Item.Properties().stacksTo(1), false, 4),
            "sixty_seconds_lockpick_master", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // ══ 末日60秒模式：扩充批次（冶金 / 农业 / 工事强化）══════════════════════
    // ── 合成材料（loot material 类别 + 配方消耗）──
    public static final Item SIXTY_SECONDS_STEEL_INGOT = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_steel_ingot", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_NAILS = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_nails", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_FERTILIZER = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_fertilizer", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CHARCOAL_FILTER = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_charcoal_filter", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 食物（FoodProperties 自动接入饱食恢复=营养×5）──
    public static final Item SIXTY_SECONDS_DRIED_FRUIT = register(
            new Item(new Item.Properties().stacksTo(16).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(4).saturationModifier(0.4F).fast().build())),
            "sixty_seconds_dried_fruit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_TRAIL_MIX = register(
            new Item(new Item.Properties().stacksTo(16).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(6).saturationModifier(0.6F).build())),
            "sixty_seconds_trail_mix", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_FRESH_VEGETABLES = register(
            new Item(new Item.Properties().stacksTo(16).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(5).saturationModifier(0.5F).build())),
            "sixty_seconds_fresh_vegetables", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 创口贴：医疗-I，缓慢恢复10健康+降低10污染
    public static final Item SIXTY_SECONDS_BAND_AID = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(16), 0, 0, 0, 0, 10, false, null, 80, net.minecraft.world.item.UseAnim.EAT),
            "sixty_seconds_band_aid", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 袋装果干：3果干+1纸，恢复60饥饿（营养12×5）
    public static final Item SIXTY_SECONDS_BAGGED_DRIED_FRUIT = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(12).saturationModifier(0.8F).build())),
            "sixty_seconds_bagged_dried_fruit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 袋装压缩饼干：2饼干+1纸，恢复60饥饿（营养12×5）
    public static final Item SIXTY_SECONDS_BAGGED_BISCUIT = register(
            new Item(new Item.Properties().stacksTo(8).food(
                    new net.minecraft.world.food.FoodProperties.Builder().nutrition(12).saturationModifier(0.8F).build())),
            "sixty_seconds_bagged_biscuit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 稿纸（邮箱专用：书写后放入邮箱，次日刊登至报纸）────────────────────
    public static final Item SIXTY_SECONDS_DRAFT_PAPER = register(
            new org.agmas.noellesroles.content.item.DraftPaperItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_draft_paper", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 电话（右键拨号，拨打热线号码）────────────────────────────
    public static final Item SIXTY_SECONDS_PHONE = register(
            new org.agmas.noellesroles.content.item.PhoneItem(
                    new Item.Properties().stacksTo(1)),
            "sixty_seconds_phone", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // ── 快递包裹（放入邮箱，1格存储空间）─────────────────────────
    public static final Item SIXTY_SECONDS_EXPRESS_PACKAGE = register(
            new org.agmas.noellesroles.content.item.ExpressPackageItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_express_package", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 饮品/药品（SixtySecondsStatItem 统一恢复：health,hunger,thirst,san,pollutionReduce,cure,effect）──
    public static final Item SIXTY_SECONDS_CANNED_SOUP = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 20, 20, 0, 0, false, null),
            "sixty_seconds_canned_soup", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SPORTS_DRINK = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 30, 5, 0, false,
                    () -> new net.minecraft.world.effect.MobEffectInstance(
                            net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 20 * 20, 0, false, false, true)),
            "sixty_seconds_sports_drink", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HERBAL_TEA = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 15, 20, 0, true, null),
            "sixty_seconds_herbal_tea", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BLOOD_BAG = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 45, 0, 0, 0, 0, false, null),
            "sixty_seconds_blood_bag", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ANTI_POLLUTION_SERUM = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 0, 0, 60, true, null),
            "sixty_seconds_anti_pollution_serum", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 近战武器（左键按对应健康值扣血；见 SixtySecondsWeapons）──
    public static final Item SIXTY_SECONDS_HATCHET = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().stacksTo(1).durability(12)
                            .attributes(SwordItem.createAttributes(Tiers.STONE, 2, -2.2F)), 25),
            "sixty_seconds_hatchet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CLEAVER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().stacksTo(1).durability(15)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 4, -2.4F)), 35),
            "sixty_seconds_cleaver", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_STEEL_SWORD = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().stacksTo(1).durability(24)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 6, -2.4F)), 55),
            "sixty_seconds_steel_sword", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 钢矛：伤害低于钢剑但命中减速（长柄拒止，配合陷阱/路障放风筝）
    public static final Item SIXTY_SECONDS_STEEL_SPEAR = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().stacksTo(1).durability(20)
                            .attributes(SwordItem.createAttributes(Tiers.IRON, 5, -2.8F)), 45, 30),
            "sixty_seconds_steel_spear", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 投掷武器（燃烧弹：大范围点燃 + 对玩家健康伤害）──
    public static final Item SIXTY_SECONDS_INCENDIARY_GRENADE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem(
                    new Item.Properties().stacksTo(4), 4.0D, 25.0F, 20, true, false),
            "sixty_seconds_incendiary_grenade", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 破片手雷：不点燃不致盲，纯爆炸伤害更高（军械工坊科技合成）
    public static final Item SIXTY_SECONDS_FRAG_GRENADE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem(
                    new Item.Properties().stacksTo(4), 3.5D, 30.0F, 35, false, false),
            "sixty_seconds_frag_grenade", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 保温壶：大量回口渴（50）+ 少量理智（炊事进阶科技合成）
    public static final Item SIXTY_SECONDS_THERMOS = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 0, 50, 3, 0, false, null),
            "sixty_seconds_thermos", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 末日乱炖：饱食 +100 / 解渴 +60（烹饪-IV）
    public static final Item SIXTY_SECONDS_STEW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 100, 60, 5, 0, false, null),
            "sixty_seconds_stew", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 钢制护甲套 + 防弹背心（减伤登记见 SixtySecondsWeapons.armorTable）──
    public static final Item SIXTY_SECONDS_STEEL_HELMET = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)),
            "sixty_seconds_steel_helmet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_STEEL_CHESTPLATE = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)),
            "sixty_seconds_steel_chestplate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_STEEL_LEGGINGS = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_steel_leggings", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_STEEL_BOOTS = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.BOOTS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_steel_boots", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 防弹背心：胸甲位，最高单件减伤（换取无钢套护腿/靴/头协同）
    public static final Item SIXTY_SECONDS_BALLISTIC_VEST = register(
            new ArmorItem(ArmorMaterials.IRON, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)),
            "sixty_seconds_ballistic_vest", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ══ 末日60秒模式：科技树重构批次 ═══════════════════════════════════
    // ── 野外专属搜刮材料（仅野外物资箱可搜到，见 SixtySecondsLootTable "field" 类别）──
    public static final Item SIXTY_SECONDS_SCRAP_METAL = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_scrap_metal", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PRECIOUS_PARTS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_precious_parts", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BREWING_PARTS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_brewing_parts", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 信号枪：仅野外可搜到；朝天开枪全服广播，延迟召唤一次空投（见 SixtySecondsFlareGunItem）
    public static final Item SIXTY_SECONDS_FLARE_GUN = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsFlareGunItem(
                    new Item.Properties().stacksTo(1).durability(1)),
            "sixty_seconds_flare_gun", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 冶金材料链：碎铜/玻璃板/钢锭(已有)/贵金属/合金板 ──────────────────
    public static final Item SIXTY_SECONDS_COPPER_SCRAP = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_copper_scrap", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_GLASS_PLATE = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_glass_plate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PRECIOUS_METAL = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_precious_metal", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ALLOY_PLATE = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_alloy_plate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 便携储蓄电池
    public static final Item SIXTY_SECONDS_PORTABLE_BATTERY = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_portable_battery", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 农业新作物：野米/野茶/工业麻/烟草（种植见 SixtySecondsPlanterBlock）──
    public static final Item SIXTY_SECONDS_WILD_RICE = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_wild_rice", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WILD_RICE_SEEDS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_wild_rice_seeds", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WILD_TEA_LEAF = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_wild_tea_leaf", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_WILD_TEA_SEED = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_wild_tea_seed", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HEMP = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_hemp", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_HEMP_SEEDS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_hemp_seeds", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_TOBACCO = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_tobacco", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_TOBACCO_SEEDS = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_tobacco_seeds", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 营养肥：右键培育箱直接催熟（肥料-II）
    public static final Item SIXTY_SECONDS_NUTRIENT_FERTILIZER = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_nutrient_fertilizer", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 工具类新物品 ─────────────────────────────────────────────────
    // 便签/巨大便签：纸制小玩意（写便签留言用的道具）
    public static final Item SIXTY_SECONDS_NOTE = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_note", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BIG_NOTE = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_big_note", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 磁铁：右键吸附周围掉落物（见 SixtySecondsUtilityItem.Type.MAGNET）
    public static final Item SIXTY_SECONDS_MAGNET = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem(
                    new Item.Properties().stacksTo(1).durability(32),
                    net.exmo.sre.sixtyseconds.content.item.SixtySecondsUtilityItem.Type.MAGNET),
            "sixty_seconds_magnet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 撬箱起子：16 耐久，撬开上锁的物资箱（低级锁）
    public static final Item SIXTY_SECONDS_BOX_PRY = register(
            new Item(new Item.Properties().stacksTo(1).durability(16)),
            "sixty_seconds_box_pry", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 钳子：16 耐久，撬开上锁的高级物资箱
    public static final Item SIXTY_SECONDS_PLIERS = register(
            new Item(new Item.Properties().stacksTo(1).durability(16)),
            "sixty_seconds_pliers", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 保险库撬锁器套组：16 耐久，撬别队保险库（撬锁技艺-III）
    public static final Item SIXTY_SECONDS_VAULT_PICK_KIT = register(
            new Item(new Item.Properties().stacksTo(1).durability(16)),
            "sixty_seconds_vault_pick_kit", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 拆卸扳手：拆白色混凝土上的功能方块（基地设施-I；与扳手同逻辑）
    public static final Item SIXTY_SECONDS_DETACH_WRENCH = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsWrenchItem(
                    new Item.Properties().stacksTo(1).durability(20)),
            "sixty_seconds_detach_wrench", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 背包扩展：军用背包(36格)/商旅背包(54格)（裁缝台）──────────────────
    public static final Item SIXTY_SECONDS_BACKPACK_MILITARY = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBackpackItem(
                    new Item.Properties().stacksTo(1), 4),
            "sixty_seconds_backpack_military", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BACKPACK_TRAVELER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBackpackItem(
                    new Item.Properties().stacksTo(1), 6),
            "sixty_seconds_backpack_traveler", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 电力：大型电池（发电量=电池4倍，见 SixtySecondsGeneratorBlock 燃料表）──
    public static final Item SIXTY_SECONDS_BATTERY_LARGE = register(
            new Item(new Item.Properties().stacksTo(4)),
            "sixty_seconds_battery_large", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 门锁分级：强化门锁(挡开锁器+撬棍+强化撬棍,4分钟)/阻击门锁(挡所有,8分钟)/合金门锁(挡所有,16分钟,通电)──
    public static final Item SIXTY_SECONDS_DOOR_LOCK_REINFORCED = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem(
                    new Item.Properties().stacksTo(4), 2),
            "sixty_seconds_door_lock_reinforced", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_DOOR_LOCK_ULTIMATE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem(
                    new Item.Properties().stacksTo(4), 3),
            "sixty_seconds_door_lock_ultimate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_DOOR_LOCK_ALLOY = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsDoorLockItem(
                    new Item.Properties().stacksTo(4), 4),
            "sixty_seconds_door_lock_alloy", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 炊事新食物 ──────────────────────────────────────────────────
    // 米汤：口渴 +15 / 饥饿 +20（烹饪-II）
    public static final Item SIXTY_SECONDS_RICE_SOUP = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 20, 15, 0, 0, false, null),
            "sixty_seconds_rice_soup", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 泡好的泡面：饱食 +60（烹饪-III）
    public static final Item SIXTY_SECONDS_COOKED_NOODLES = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 60, 5, 0, 0, false, null),
            "sixty_seconds_cooked_noodles", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 末世蛋糕：饱食 +80 / 理智 +40（烹饪-IV）
    public static final Item SIXTY_SECONDS_DOOMSDAY_CAKE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 80, 0, 40, 0, false, null),
            "sixty_seconds_doomsday_cake", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 豪华炖锅：饱食 +70 / 口渴 +50 / 理智 +70 / 污染 -30 / 治愈生病（烹饪-IV）
    public static final Item SIXTY_SECONDS_LUXURY_STEW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 70, 50, 70, 30, true, null),
            "sixty_seconds_luxury_stew", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 一锅炖：饱食 +65 / 污染 -25（变废为宝-III）
    public static final Item SIXTY_SECONDS_ONE_POT_STEW = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 65, 0, 0, 25, false, null),
            "sixty_seconds_one_pot_stew", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 寿司：饱食 +65（变废为宝-II）
    public static final Item SIXTY_SECONDS_SUSHI = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 65, 0, 0, 0, false, null),
            "sixty_seconds_sushi", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 炖汤：饱食 +60（变废为宝-III）
    public static final Item SIXTY_SECONDS_BONE_SOUP = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 60, 5, 0, 0, false, null),
            "sixty_seconds_bone_soup", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 手打怪物肉丸：饱食 +75 / 理智 +15（变废为宝-IV）
    public static final Item SIXTY_SECONDS_HAIMAN_MEATBALL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 75, 0, 15, 0, false, null,
                    50, net.minecraft.world.item.UseAnim.EAT),
            "sixty_seconds_haiman_meatball", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 月饼：饱食 +75 / 污染 -15（变废为宝-IV）
    public static final Item SIXTY_SECONDS_CATMOONCAKE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 75, 0, 0, 15, false, null,
                    50, net.minecraft.world.item.UseAnim.EAT),
            "sixty_seconds_catmooncake", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 安神茶：理智 +25 / 口渴 +15（茶艺）
    public static final Item SIXTY_SECONDS_SOOTHING_TEA = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 0, 15, 25, 0, false, null,
                    50, UseAnim.DRINK),
            "sixty_seconds_soothing_tea", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 医疗新药品 ──────────────────────────────────────────────────
    // 简易绷带：缓包扎，最多回 10 点血（药品-I）
    public static final Item SIXTY_SECONDS_SIMPLE_BANDAGE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsBandageItem(
                    new Item.Properties().stacksTo(16), 10),
            "sixty_seconds_simple_bandage", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 夹板：回 5 点血（药品-I）
    public static final Item SIXTY_SECONDS_SPLINT = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 5, 0, 0, 0, 0, false, null,
                    100, UseAnim.BOW),
            "sixty_seconds_splint", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 医疗箱：血量回满（药品-V）
    public static final Item SIXTY_SECONDS_MEDICAL_BOX = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(2), 999, 0, 0, 0, 0, true, null,
                    200, UseAnim.BOW),
            "sixty_seconds_medical_box", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 止痛剂：理智 +10（理智恢复-I）
    public static final Item SIXTY_SECONDS_SANITY_PILL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 0, 0, 0, 10, 0, false, null,
                    40, UseAnim.EAT),
            "sixty_seconds_sanity_pill", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 理智药：理智 +60（理智恢复-IV）
    public static final Item SIXTY_SECONDS_SANITY_MED = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(4), 0, 0, 0, 60, 0, false, null,
                    60, UseAnim.DRINK),
            "sixty_seconds_sanity_med", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 抗感染剂：污染 -30 / 回 25 血（污染净化-II）
    public static final Item SIXTY_SECONDS_ANTI_INFECTION = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 25, 0, 0, 0, 30, false, null,
                    100, UseAnim.EAT),
            "sixty_seconds_anti_infection", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 全能补剂：饥饿/口渴/理智/血量 +15，污染 -15（综合补剂科技）
    public static final Item SIXTY_SECONDS_OMNI_TONIC = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsStatItem(
                    new Item.Properties().stacksTo(8), 15, 15, 15, 15, 15, false, null,
                    60, UseAnim.DRINK),
            "sixty_seconds_omni_tonic", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 药剂净化试剂：清除身上所有药水效果（药剂净化科技）
    public static final Item SIXTY_SECONDS_POTION_CLEANSER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsPotionCleanserItem(
                    new Item.Properties().stacksTo(4)),
            "sixty_seconds_potion_cleanser", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 军械：分级弹药 + 冲锋枪/霰弹枪 ──────────────────────────────────
    public static final Item SIXTY_SECONDS_RIFLE_AMMO = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_rifle_ammo", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SMG_AMMO = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_smg_ammo", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_SHOTGUN_AMMO = register(
            new Item(new Item.Properties().stacksTo(64)),
            "sixty_seconds_shotgun_ammo", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_MAGNUM_AMMO = register(
            new Item(new Item.Properties().stacksTo(16)),
            "sixty_seconds_magnum_ammo", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 火箭炮（RPG 弹药）
    public static final Item SIXTY_SECONDS_ROCKET = register(
            new Item(new Item.Properties().stacksTo(4)),
            "sixty_seconds_rocket", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 冲锋枪：连发 12 发后进入长冷却，使用冲锋枪子弹
    public static final Item SIXTY_SECONDS_SMG = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGunItem(
                    new Item.Properties().durability(36), 3, 28.0D, 15, 1, false,
                    () -> SIXTY_SECONDS_SMG_AMMO, 12, 100),
            "sixty_seconds_smg", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 霰弹枪：射程短、锥形散射、距离越近伤害越高、对怪高伤，使用霰弹枪子弹
    public static final Item SIXTY_SECONDS_COMBAT_SHOTGUN = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsShotgunItem(
                    new Item.Properties().durability(10), 50, 12.0D, 35,
                    () -> SIXTY_SECONDS_SHOTGUN_AMMO),
            "sixty_seconds_combat_shotgun", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 军刀（冷兵器-I）
    public static final Item SIXTY_SECONDS_SABER = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMeleeWeaponItem(
                    new Item.Properties().durability(16).attributes(
                            net.minecraft.world.item.SwordItem.createAttributes(
                                    net.minecraft.world.item.Tiers.IRON, 4, -2.2F)), 30),
            "sixty_seconds_saber", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 诱饵弹：无伤爆响，吸引怪物注意（投掷物-I）
    public static final Item SIXTY_SECONDS_DECOY_FLARE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem(
                    new Item.Properties().stacksTo(4), 8.0D, 0.0F, 20, false, false),
            "sixty_seconds_decoy_flare", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 烟雾弹(60s版)
    public static final Item SIXTY_SECONDS_SMOKE_GRENADE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsSmokeGrenadeItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_smoke_grenade", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 标记弹
    public static final Item SIXTY_SECONDS_MARKING_GRENADE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsMarkingGrenadeItem(
                    new Item.Properties().stacksTo(16)),
            "sixty_seconds_marking_grenade", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);



    // ── 盔甲：塑料套（废料套与铁套之间）/ 合金套（钢套之上，韧性+击退抗性）──
    public static final Item SIXTY_SECONDS_PLASTIC_HELMET = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)),
            "sixty_seconds_plastic_helmet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PLASTIC_CHESTPLATE = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)),
            "sixty_seconds_plastic_chestplate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PLASTIC_LEGGINGS = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_plastic_leggings", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_PLASTIC_BOOTS = register(
            new ArmorItem(ArmorMaterials.LEATHER, ArmorItem.Type.BOOTS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_plastic_boots", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ALLOY_HELMET = register(
            new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.HELMET, new Item.Properties().stacksTo(1)),
            "sixty_seconds_alloy_helmet", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ALLOY_CHESTPLATE = register(
            new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)),
            "sixty_seconds_alloy_chestplate", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ALLOY_LEGGINGS = register(
            new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.LEGGINGS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_alloy_leggings", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_ALLOY_BOOTS = register(
            new ArmorItem(ArmorMaterials.NETHERITE, ArmorItem.Type.BOOTS, new Item.Properties().stacksTo(1)),
            "sixty_seconds_alloy_boots", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 基地扩容钥匙（车床）：对应等级基地门，一次性消耗 ─────────────────
    public static final Item SIXTY_SECONDS_EXPANSION_KEY_1 = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_expansion_key_1", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_EXPANSION_KEY_2 = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_expansion_key_2", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_EXPANSION_KEY_3 = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_expansion_key_3", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 交通：柴油罐 / 载具（摩托车2座、小汽车4座，见 SixtySecondsVehicleItem）──
    public static final Item SIXTY_SECONDS_DIESEL_CAN = register(
            new Item(new Item.Properties().stacksTo(8)),
            "sixty_seconds_diesel_can", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_MOTORCYCLE = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsVehicleItem(
                    new Item.Properties().stacksTo(1),
                    () -> org.agmas.noellesroles.init.ModEntities.SIXTY_SECONDS_MOTORCYCLE),
            "sixty_seconds_motorcycle", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_CAR = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsVehicleItem(
                    new Item.Properties().stacksTo(1),
                    () -> org.agmas.noellesroles.init.ModEntities.SIXTY_SECONDS_CAR),
            "sixty_seconds_car", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    // 载具修理工具（车床，载具修理科技）：右键载具恢复 15 血量
    public static final Item SIXTY_SECONDS_VEHICLE_REPAIR_TOOL = register(
            new net.exmo.sre.sixtyseconds.content.item.SixtySecondsVehicleRepairItem(
                    new Item.Properties().stacksTo(4)),
            "sixty_seconds_vehicle_repair_tool", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

    // ── 神秘技术：污秽玻璃罐/存血的玻璃罐/复活图腾（不死图腾=原版图腾）──────
    public static final Item SIXTY_SECONDS_FILTHY_JAR = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_filthy_jar", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_BLOOD_JAR = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_blood_jar", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);
    public static final Item SIXTY_SECONDS_REVIVAL_TOTEM = register(
            new Item(new Item.Properties().stacksTo(1)),
            "sixty_seconds_revival_totem", net.exmo.sre.sixtyseconds.SixtySecondsCreativeTab.SIXTY_SECONDS_GROUP);

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
     * - 10点耐久
     * - Shift+右键：直接撬开门，消耗1点耐久，30秒冷却
     * - 直接右键：像刀一样举起，蓄力2秒，可击杀一名玩家，消耗3点耐久（需满耐久）
     * - 击杀玩家会触发误杀惩罚
     */
    public static final Item FIRE_AXE = register(
            new FireAxeItem(new Item.Properties().stacksTo(1).durability(10)),
            "fire_axe", WEAPONS_GROUP);
    public static final Item THROWING_KNIFE = register(
            new ThrowingKnife((new Item.Properties()).stacksTo(1)), "throwing_knife",
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
    public static final Item MAGNIFYING_GLASS = register(
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(
                            "noellesroles.game.devil_roulette.tooltip.magnifying_glass")
                            .withStyle(ChatFormatting.GRAY));
                }
            },
            "magnifying_glass", MISC_ITEMS_GROUP);
    public static final Item CHEWING = register(
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(
                            "noellesroles.game.devil_roulette.tooltip.chewing")
                            .withStyle(ChatFormatting.GRAY));
                }
            },
            "chewing", MISC_ITEMS_GROUP);
    public static final Item CLIP = register(
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component
                            .translatable("noellesroles.game.devil_roulette.tooltip.clip")
                            .withStyle(ChatFormatting.GRAY));
                }
            },
            "clip", MISC_ITEMS_GROUP);
    public static final Item STEEL_BALL = register(
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(
                            "noellesroles.game.devil_roulette.tooltip.steel_ball")
                            .withStyle(ChatFormatting.GRAY));
                }
            },
            "steel_ball", MISC_ITEMS_GROUP);
    public static final Item REVERSING_CARD = register(
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(
                            "noellesroles.game.devil_roulette.tooltip.reversing_card")
                            .withStyle(ChatFormatting.GRAY));
                }
            },
            "reversing_card", MISC_ITEMS_GROUP);
    public static final Item TELEPHONE = register(
            new Item(new Item.Properties().stacksTo(64)) {
                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context,
                        List<Component> tooltip, TooltipFlag flag) {
                    tooltip.add(Component.translatable(
                            "noellesroles.game.devil_roulette.tooltip.telephone")
                            .withStyle(ChatFormatting.GRAY));
                }
            },
            "telephone", MISC_ITEMS_GROUP);

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
