package org.agmas.noellesroles.init;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.content.block_entity.DevilRouletteTableEntity;
import org.agmas.noellesroles.content.entity.*;

public class ModEntities {
    public static final EntityType<RoleMineEntity> ROLE_MINE_ENTITY_ENTITY_TYPE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "cube"),
            EntityType.Builder.of(RoleMineEntity::new, MobCategory.MISC).sized(0.75f, 0.75f).build("cube"));
    public static final EntityType<WheelchairEntity> WHEELCHAIR = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("wheelchair"),
            EntityType.Builder.of(WheelchairEntity::new, MobCategory.MISC).sized(0.8f, 1.6f) // 0.8 宽度，1.6
                                                                                             // 高度
                    .build("wheelchair"));

    // 末日60秒：载具（复用轮椅驾驶骨架；摩托 2 座 / 小汽车 4 座，需燃料）
    public static final EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity> SIXTY_SECONDS_MOTORCYCLE = Registry
            .register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_motorcycle"),
                    EntityType.Builder
                            .<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity>of(
                                    (type, world) -> new net.exmo.sre.sixtyseconds.content.entity
                                            .SixtySecondsVehicleEntity(type, world,
                                                    net.exmo.sre.sixtyseconds.content.entity
                                                            .SixtySecondsVehicleEntity.Kind.MOTORCYCLE),
                                    MobCategory.MISC)
                            .sized(1.8f, 2.8f).build("sixty_seconds_motorcycle"));
    public static final EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity> SIXTY_SECONDS_CAR = Registry
            .register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_car"),
                    EntityType.Builder
                            .<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsVehicleEntity>of(
                                    (type, world) -> new net.exmo.sre.sixtyseconds.content.entity
                                            .SixtySecondsVehicleEntity(type, world,
                                                    net.exmo.sre.sixtyseconds.content.entity
                                                            .SixtySecondsVehicleEntity.Kind.CAR),
                                    MobCategory.MISC)
                            .sized(4.2f, 4.5f).build("sixty_seconds_car"));

    // 末日60秒：海上载具（继承原版 Boat 拿水上物理，外观是自研模型；木筏/汽艇/渔船，见 SixtySecondsSeaVehicleEntity）
    public static final EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity>
            SIXTY_SECONDS_RAFT = registerSeaVehicle("sixty_seconds_raft",
                    net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity.Kind.RAFT,
                    1.6F, 0.45F);
    public static final EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity>
            SIXTY_SECONDS_MOTORBOAT = registerSeaVehicle("sixty_seconds_motorboat",
                    net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity.Kind.MOTORBOAT,
                    1.5F, 0.6F);
    public static final EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity>
            SIXTY_SECONDS_FISHING_BOAT = registerSeaVehicle("sixty_seconds_fishing_boat",
                    net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity.Kind.FISHING_BOAT,
                    2.4F, 1.0F);

    /** 三种海上载具的注册只差 Kind 与碰撞盒，抽一个工厂免得抄三遍。 */
    private static EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity>
            registerSeaVehicle(String name,
                    net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity.Kind kind,
                    float width, float height) {
        return Registry.register(BuiltInRegistries.ENTITY_TYPE, Noellesroles.id(name),
                EntityType.Builder
                        .<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsSeaVehicleEntity>of(
                                (type, world) -> new net.exmo.sre.sixtyseconds.content.entity
                                        .SixtySecondsSeaVehicleEntity(type, world, kind),
                                MobCategory.MISC)
                        .sized(width, height)
                        .build(name));
    }

    public static final EntityType<WheelchairFieldItemEntity> WHEELCHAIR_FIELD_ITEM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("wheelchair_field_item"),
            EntityType.Builder
                    .<WheelchairFieldItemEntity>of(WheelchairFieldItemEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .build("wheelchair_field_item"));

    @SuppressWarnings("deprecation")
    public static final EntityType<SmokeGrenadeEntity> SMOKE_GRENADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "smoke_grenade"),
            FabricEntityTypeBuilder.<SmokeGrenadeEntity>create(MobCategory.MISC, SmokeGrenadeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    @SuppressWarnings("deprecation")
    public static final EntityType<ThrowingKnifeEntity> THROWING_KNIFE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "throwing_knife"),
            FabricEntityTypeBuilder.<ThrowingKnifeEntity>create(MobCategory.MISC, ThrowingKnifeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.2F, 0.2F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    /**
     * 氯气弹实体 - 可投掷物品，落地时使范围内玩家中毒
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<ChlorineBombEntity> CHLORINE_BOMB = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "chlorine_bomb"),
            FabricEntityTypeBuilder.<ChlorineBombEntity>create(MobCategory.MISC, ChlorineBombEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    /**
     * 毒气瓶投掷实体 - 可投掷物品，落地时生成毒气云
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<PoisonGasTankEntity> POISON_GAS_TANK_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "poison_gas_tank"),
            FabricEntityTypeBuilder.<PoisonGasTankEntity>create(MobCategory.MISC, PoisonGasTankEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    /**
     * 毒气云实体 - 气体扩散，60秒消散，停留8秒中毒
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<PoisonGasCloudEntity> POISON_GAS_CLOUD_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "poison_gas_cloud"),
            FabricEntityTypeBuilder.<PoisonGasCloudEntity>create(MobCategory.MISC, PoisonGasCloudEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5F, 0.5F))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    @SuppressWarnings("deprecation")
    public static final EntityType<HurricaneEntity> HURRICANE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("hurricane"),
            FabricEntityTypeBuilder.<HurricaneEntity>create(MobCategory.MISC, HurricaneEntity::new)
                    .dimensions(EntityDimensions.fixed(2.5F, 6.0F))
                    .trackRangeBlocks(96)
                    .trackedUpdateRate(1)
                    .build());

    @SuppressWarnings("deprecation")
    public static final EntityType<MummyEntity> MUMMY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("mummy"),
            FabricEntityTypeBuilder.<MummyEntity>create(MobCategory.CREATURE, MummyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 净化弹实体 - 可投掷物品，落地时取消范围内玩家中毒状态
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<PurifyBombEntity> PURIFY_BOMB = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "purify_bomb"),
            FabricEntityTypeBuilder.<PurifyBombEntity>create(MobCategory.MISC, PurifyBombEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    /**
     * 末日60秒投掷武器实体 - 燃烧瓶/土制炸弹/闪光弹/燃烧弹/破片手雷共用；
     * 飞行中按自带 ItemStack 渲染，命中方块/实体时按该手雷的参数引爆。
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsGrenadeEntity>
            SIXTY_SECONDS_GRENADE = Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "sixty_seconds_grenade"),
                    FabricEntityTypeBuilder
                            .<net.exmo.sre.sixtyseconds.content.entity.SixtySecondsGrenadeEntity>create(
                                    MobCategory.MISC,
                                    net.exmo.sre.sixtyseconds.content.entity.SixtySecondsGrenadeEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                            .trackRangeBlocks(8)
                            .trackedUpdateRate(10)
                            .build());

    /**
     * 灾厄印记实体 - 设陷者专属隐形陷阱
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<CalamityMarkEntity> CALAMITY_MARK = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "calamity_mark"),
            FabricEntityTypeBuilder.<CalamityMarkEntity>create(MobCategory.MISC, CalamityMarkEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5F, 0.1F))
                    .trackRangeBlocks(32)
                    .trackedUpdateRate(20)
                    .build());

    /**
     * 绊索陷阱实体 - 设陷者可见陷阱，可被拆除
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<TripwireTrapEntity> TRIPWIRE_TRAP = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "tripwire_trap"),
            FabricEntityTypeBuilder.<TripwireTrapEntity>create(MobCategory.MISC, TripwireTrapEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5F, 0.1F))
                    .trackRangeBlocks(32)
                    .trackedUpdateRate(20)
                    .build());

    /** 信鸽实体 - 信使快递邮件 */
    @SuppressWarnings("deprecation")
    public static final EntityType<PigeonEntity> PIGEON = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pigeon"),
            FabricEntityTypeBuilder.<PigeonEntity>create(MobCategory.MISC, PigeonEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5F, 0.9F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 傀儡本体实体 - 傀儡师使用假人技能时生成的本体
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<PuppeteerBodyEntity> PUPPETEER_BODY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "puppeteer_body"),
            FabricEntityTypeBuilder.<PuppeteerBodyEntity>create(MobCategory.MISC, PuppeteerBodyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6F, 1.8F)) // 玩家尺寸
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 咸鱼假尸体实体 - 咸鱼晒咸鱼技能时生成的假尸体
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<SaltedFishBodyEntity> SALTED_FISH_BODY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("salted_fish_body"),
            FabricEntityTypeBuilder.<SaltedFishBodyEntity>create(MobCategory.MISC, SaltedFishBodyEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0F, 0.25F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 宿命的罪人假尸体实体 - 罪人死亡时替换的尸体
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<DoomedSinnerBodyEntity> DOOMED_SINNER_BODY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("doomed_sinner_body"),
            FabricEntityTypeBuilder.<DoomedSinnerBodyEntity>create(MobCategory.MISC, DoomedSinnerBodyEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0F, 0.25F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    // @SuppressWarnings("deprecation")
    // public static final EntityType<ManipulatorBodyEntity> MANIPULATOR_BODY =
    // Registry.register(
    // BuiltInRegistries.ENTITY_TYPE,
    // ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
    // "manipulator_body"),
    // FabricEntityTypeBuilder
    // .<ManipulatorBodyEntity>create(MobCategory.MISC, ManipulatorBodyEntity::new)
    // .dimensions(EntityDimensions.fixed(0.6F, 1.8F))
    // .trackRangeBlocks(64)
    // .trackedUpdateRate(2)
    // .build());

    /**
     * 亡灵实体 - 亡灵之主召唤的无意识亡灵（玩家外观 + 追击感染 AI）
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<UndeadEntity> UNDEAD = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("undead"),
            FabricEntityTypeBuilder.<UndeadEntity>create(MobCategory.MISC, UndeadEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6F, 1.8F)) // 玩家尺寸
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 变形者「举刀假人」实体 - 手持匕首向前突进、贴近目标即击杀的假人（玩家外观）
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<MorphlingKnifeDummyEntity> MORPHLING_KNIFE_DUMMY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("morphling_knife_dummy"),
            FabricEntityTypeBuilder.<MorphlingKnifeDummyEntity>create(MobCategory.MISC, MorphlingKnifeDummyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6F, 1.8F)) // 玩家尺寸
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());

    /**
     * 锁实体 - 保护门不被撬锁器打开
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<LockEntity> LOCK_ENTITY = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "lock"),
            FabricEntityTypeBuilder.<LockEntity>create(MobCategory.MISC, LockEntity::new)
                    .dimensions(EntityDimensions.fixed(0.2F, 0.2F))
                    .trackRangeBlocks(32)
                    .build());

    /**
     * 闪光弹实体 - 可投掷物品，落地时使范围内玩家致盲
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<FlashGrenadeEntity> FLASH_GRENADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "flash_grenade"),
            FabricEntityTypeBuilder.<FlashGrenadeEntity>create(MobCategory.MISC, FlashGrenadeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());
    /**
     * 傀戏傀儡实体类型
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<KuiXiPuppetEntity> KUIXI_PUPPET = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("kuixi_puppet"),
            FabricEntityTypeBuilder.create(MobCategory.MISC, KuiXiPuppetEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f)) // 玩家大小
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build());
    /**
     * .dimensions(EntityDimensions.fixed(0.6F, 1.8F)) // 玩家尺寸
     * .trackRangeBlocks(64)
     * .trackedUpdateRate(2)
     * .build()
     * 诱饵弹实体 - 可投掷物品，落地时播放5声左轮手枪射击声
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<DecoyGrenadeEntity> DECOY_GRENADE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "decoy_grenade"),
            FabricEntityTypeBuilder.<DecoyGrenadeEntity>create(MobCategory.MISC, DecoyGrenadeEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    @SuppressWarnings("deprecation")
    public static final EntityType<SilenceTotemEntity> SILENCE_TOTEM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "silence_totem"),
            FabricEntityTypeBuilder.<SilenceTotemEntity>create(MobCategory.MISC, SilenceTotemEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(32)
                    .trackedUpdateRate(10)
                    .build());

    /**
     * 照明弹实体 - 投掷后飞行，撞到方块时放置照明弹方块，10秒后消失
     */
    @SuppressWarnings("deprecation")
    public static final EntityType<FlareEntity> FLARE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "flare"),
            FabricEntityTypeBuilder.<FlareEntity>create(MobCategory.MISC, FlareEntity::new)
                    .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                    .trackRangeBlocks(4)
                    .trackedUpdateRate(10)
                    .build());

    @SuppressWarnings("deprecation")
    public static final EntityType<io.wifi.starrailexpress.content.entity.NoteEntity> GIANT_NOTE = Registry
            .register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("giant_note"),
                    FabricEntityTypeBuilder.<io.wifi.starrailexpress.content.entity.NoteEntity>create(
                            MobCategory.MISC,
                            io.wifi.starrailexpress.content.entity.NoteEntity::new)
                            .dimensions(EntityDimensions.fixed(2.5F, 2.5F))
                            .trackRangeBlocks(128)
                            .trackedUpdateRate(10)
                            .build());

    /** 轮盘赌展示实体 */
    @SuppressWarnings("deprecation")
    public static final EntityType<DevilRouletteTableEntity.TableTextDisplay> TABLE_TEXT_DISpLAY = Registry
            .register(
                    BuiltInRegistries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
                            "table_text_display"),
                    FabricEntityTypeBuilder.<DevilRouletteTableEntity.TableTextDisplay>create(
                            MobCategory.MISC,
                            DevilRouletteTableEntity.TableTextDisplay::new)
                            // .dimensions(EntityDimensions.fixed(0.2F, 0.2F))
                            .trackRangeBlocks(32)
                            .build());
    @SuppressWarnings("deprecation")
    public static final EntityType<DevilRouletteTableEntity.TableItemDisplay> TABLE_ITEM_DISPLAY = Registry
            .register(
                    BuiltInRegistries.ENTITY_TYPE,
                    ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID,
                            "table_item_display"),
                    FabricEntityTypeBuilder.<DevilRouletteTableEntity.TableItemDisplay>create(
                            MobCategory.MISC,
                            DevilRouletteTableEntity.TableItemDisplay::new)
                            // .dimensions(EntityDimensions.fixed(0.2F, 0.2F))
                            .trackRangeBlocks(32)
                            .build());

    /** 滚石实体 - 沿方向滚动碾死玩家 */
    @SuppressWarnings("deprecation")
    public static final EntityType<RollingStoneEntity> ROLLING_STONE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("rolling_stone"),
            FabricEntityTypeBuilder.<RollingStoneEntity>create(MobCategory.MISC, RollingStoneEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0F, 2.0F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /** 滚木实体 - 沿方向滚动碾死玩家 */
    @SuppressWarnings("deprecation")
    public static final EntityType<RollingLogEntity> ROLLING_LOG = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("rolling_log"),
            FabricEntityTypeBuilder.<RollingLogEntity>create(MobCategory.MISC, RollingLogEntity::new)
                    .dimensions(EntityDimensions.fixed(2.0F, 2.0F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /** 海曼彩虹马实体 - 彩虹马蹄铁召唤的双人坐骑，120 秒后消失 */
    @SuppressWarnings("deprecation")
    public static final EntityType<RainbowHorseEntity> RAINBOW_HORSE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("rainbow_horse"),
            FabricEntityTypeBuilder.<RainbowHorseEntity>create(MobCategory.MISC, RainbowHorseEntity::new)
                    .dimensions(EntityDimensions.fixed(1.4F, 1.6F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /** 残月萨马实体 - 残月萨马蹄铁召唤的双人坐骑，120 秒后消失 */
    @SuppressWarnings("deprecation")
    public static final EntityType<CanyuesaHorseEntity> CANYUESA_HORSE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("canyuesa_horse"),
            FabricEntityTypeBuilder.<CanyuesaHorseEntity>create(MobCategory.MISC, CanyuesaHorseEntity::new)
                    .dimensions(EntityDimensions.fixed(1.4F, 1.6F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /** 超级猪马实体 - 超级猪马蹄铁召唤的三人坐骑，120 秒后消失，属性更优 */
    @SuppressWarnings("deprecation")
    public static final EntityType<SuperPigHorseEntity> SUPER_PIG_HORSE = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("super_pig_horse"),
            FabricEntityTypeBuilder.<SuperPigHorseEntity>create(MobCategory.MISC, SuperPigHorseEntity::new)
                    .dimensions(EntityDimensions.fixed(1.8F, 2.0F))
                    .trackRangeBlocks(128)
                    .trackedUpdateRate(2)
                    .build());

    /** 末日60秒：自研怪物（拖行者/奔跑者/重锤兽/吐酸者，变体经 SynchedEntityData 同步换贴图） */
    @SuppressWarnings("deprecation")
    public static final EntityType<net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity> SIXTY_SECONDS_MONSTER =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_monster"),
                    FabricEntityTypeBuilder
                            .<net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity>create(MobCategory.MONSTER,
                                    net.exmo.sre.sixtyseconds.entity.SixtySecondsMonsterEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(2)
                            .build());

    /** 末日60秒：Boss 尸潮领主（带等级/技能/Boss 血条，见 SixtySecondsPveSystem） */
    @SuppressWarnings("deprecation")
    public static final EntityType<net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity> SIXTY_SECONDS_BOSS =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_boss"),
                    FabricEntityTypeBuilder
                            .<net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity>create(MobCategory.MONSTER,
                                    net.exmo.sre.sixtyseconds.entity.SixtySecondsBossEntity::new)
                            // 基础尺寸=僵尸；实际体型由 Attributes.SCALE 按 Boss 等级放大（碰撞箱自动跟随）
                            .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                            .trackRangeBlocks(96)
                            .trackedUpdateRate(2)
                            .build());

    /** 末日60秒：弓/弩发射的箭矢（按箭矢类型结算怪物伤害/玩家健康伤害+附加效果） */
    @SuppressWarnings("deprecation")
    public static final EntityType<net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity> SIXTY_SECONDS_ARROW =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_arrow"),
                    FabricEntityTypeBuilder
                            .<net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity>create(MobCategory.MISC,
                                    net.exmo.sre.sixtyseconds.entity.SixtySecondsArrowEntity::new)
                            .dimensions(EntityDimensions.fixed(0.5F, 0.5F))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(20)
                            .build());

    /** 末日60秒：吐酸者的酸液投射物 */
    @SuppressWarnings("deprecation")
    public static final EntityType<net.exmo.sre.sixtyseconds.entity.SixtySecondsAcidSpitEntity> SIXTY_SECONDS_ACID_SPIT =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_acid_spit"),
                    FabricEntityTypeBuilder
                            .<net.exmo.sre.sixtyseconds.entity.SixtySecondsAcidSpitEntity>create(MobCategory.MISC,
                                    net.exmo.sre.sixtyseconds.entity.SixtySecondsAcidSpitEntity::new)
                            .dimensions(EntityDimensions.fixed(0.25F, 0.25F))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(10)
                            .build());

    /** 末日60秒：NPC（商人/军人/强盗/旅者，变体经 SynchedEntityData 同步换贴图）。
     *  用 CREATURE 而非 MONSTER：本模式全程 PEACEFUL，CREATURE 天然不吃 checkDespawn 的和平清除 */
    @SuppressWarnings("deprecation")
    public static final EntityType<net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity> SIXTY_SECONDS_NPC =
            Registry.register(
                    BuiltInRegistries.ENTITY_TYPE,
                    Noellesroles.id("sixty_seconds_npc"),
                    FabricEntityTypeBuilder
                            .<net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity>create(MobCategory.CREATURE,
                                    net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity::new)
                            .dimensions(EntityDimensions.fixed(0.6F, 1.95F))
                            .trackRangeBlocks(64)
                            .trackedUpdateRate(2)
                            .build());

    /** 移动平台实体 - 可站立、往返移动 */
    @SuppressWarnings("deprecation")
    public static final EntityType<MovingPlatformEntity> MOVING_PLATFORM = Registry.register(
            BuiltInRegistries.ENTITY_TYPE,
            Noellesroles.id("moving_platform"),
            FabricEntityTypeBuilder.<MovingPlatformEntity>create(MobCategory.MISC, MovingPlatformEntity::new)
                    .dimensions(EntityDimensions.fixed(1.0F, 0.2F))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    /**
     * 初始化实体
     * 注册实体属性（LivingEntity 需要）
     */
    public static void init() {
        // 轮椅
        FabricDefaultAttributeRegistry.register(WHEELCHAIR, WheelchairEntity.createAttributes());
        // 60s 载具（属性同轮椅）
        FabricDefaultAttributeRegistry.register(SIXTY_SECONDS_MOTORCYCLE, WheelchairEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SIXTY_SECONDS_CAR, WheelchairEntity.createAttributes());
        // 注册傀儡本体实体属性（LivingEntity 必须注册属性才能生成）
        FabricDefaultAttributeRegistry.register(PUPPETEER_BODY, LivingEntity.createLivingAttributes());
        FabricDefaultAttributeRegistry.register(SALTED_FISH_BODY,
                io.wifi.starrailexpress.content.entity.PlayerBodyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(DOOMED_SINNER_BODY,
                io.wifi.starrailexpress.content.entity.PlayerBodyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(PIGEON, PigeonEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(KUIXI_PUPPET, KuiXiPuppetEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MUMMY, net.minecraft.world.entity.monster.Husk.createAttributes());
        FabricDefaultAttributeRegistry.register(UNDEAD, UndeadEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(MORPHLING_KNIFE_DUMMY, MorphlingKnifeDummyEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(RAINBOW_HORSE, RainbowHorseEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(CANYUESA_HORSE, CanyuesaHorseEntity.createAttributes());
        FabricDefaultAttributeRegistry.register(SUPER_PIG_HORSE, SuperPigHorseEntity.createAttributes());
        // 末日60秒自研怪物/Boss：基础属性用原版僵尸表，生成时按变体/等级覆写
        FabricDefaultAttributeRegistry.register(SIXTY_SECONDS_MONSTER,
                net.minecraft.world.entity.monster.Zombie.createAttributes());
        FabricDefaultAttributeRegistry.register(SIXTY_SECONDS_BOSS,
                net.minecraft.world.entity.monster.Zombie.createAttributes());
        // 末日60秒 NPC：生命/移速在 applyVariant 里按变体覆写
        FabricDefaultAttributeRegistry.register(SIXTY_SECONDS_NPC,
                net.exmo.sre.sixtyseconds.entity.SixtySecondsNpcEntity.createAttributes());
        // 海洋生物：鲨鱼 / 海怪（注册基础属性，变体装配时按 id 覆写）
        net.exmo.sre.sixtyseconds.init.ModOceanEntities.initAttributes();
    }
}
