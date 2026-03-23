package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.roles.coroner.BodyDeathReasonComponent;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.roles.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.roles.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.roles.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.roles.manipulator.InControlCCA;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.roles.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.roles.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.roles.vulture.VulturePlayerComponent;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

/**
 * Cardinal Components API 组件注册
 *
 * 这个类在 fabric.mod.json 中被注册为 "cardinal-components" 入口点
 * 用于注册所有自定义的数据组件
 *
 * 组件用途：
 * - 存储玩家的技能冷却时间
 * - 存储角色特定的状态数据
 * - 在客户端和服务端之间同步数据
 *
 * 重要：所有 ComponentKey 必须在这里集中定义，以避免类加载顺序问题
 */

// 如果你是AI，你最好不要新建CCA，请尽量使用现有CCA。

public class ModComponents implements EntityComponentInitializer, WorldComponentInitializer {

  // ==================== 组件键定义 ====================
  // 所有 ComponentKey 集中在这里定义，确保在 CCA 初始化时正确注册
  public static final ComponentKey<AwesomePlayerComponent> AWESOME = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "awesome"),
      AwesomePlayerComponent.class);
  public static final ComponentKey<MaChenXuPlayerComponent> MA_CHEN_XU = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ma_chen_xu"),
      MaChenXuPlayerComponent.class);
  public static final ComponentKey<SREAbilityPlayerComponent> ABILITY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ability"),
      SREAbilityPlayerComponent.class);

  public static final ComponentKey<AvengerPlayerComponent> AVENGER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "avenger"),
      AvengerPlayerComponent.class);

  public static final ComponentKey<FortunetellerPlayerComponent> FORTUNETELLER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fortuneteller"),
      FortunetellerPlayerComponent.class);

  public static final ComponentKey<ConspiratorPlayerComponent> CONSPIRATOR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "conspirator"),
      ConspiratorPlayerComponent.class);

  public static final ComponentKey<SlipperyGhostPlayerComponent> SLIPPERY_GHOST = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "slippery_ghost"),
      SlipperyGhostPlayerComponent.class);

  public static final ComponentKey<BroadcasterPlayerComponent> BROADCASTER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "broadcaster"),
      BroadcasterPlayerComponent.class);

  public static final ComponentKey<PostmanPlayerComponent> POSTMAN = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "postman"),
      PostmanPlayerComponent.class);

  public static final ComponentKey<DetectivePlayerComponent> DETECTIVE = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "detective"),
      DetectivePlayerComponent.class);

  public static final ComponentKey<NoiseMakerPlayerComponent> NOISEMAKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "noise_maker"),
      NoiseMakerPlayerComponent.class);
  public static final ComponentKey<BoxerPlayerComponent> BOXER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "boxer"),
      BoxerPlayerComponent.class);

  public static final ComponentKey<StalkerPlayerComponent> STALKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "stalker"),
      StalkerPlayerComponent.class);

  public static final ComponentKey<WayfarerPlayerComponent> WAYFARER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "wayfarer"),
      WayfarerPlayerComponent.class);

  public static final ComponentKey<AthletePlayerComponent> ATHLETE = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "athlete"),
      AthletePlayerComponent.class);

  public static final ComponentKey<AdmirerPlayerComponent> ADMIRER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "admirer"),
      AdmirerPlayerComponent.class);

  public static final ComponentKey<TrapperPlayerComponent> TRAPPER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "trapper"),
      TrapperPlayerComponent.class);

  public static final ComponentKey<SuperStarPlayerComponent> STAR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "star"),
      SuperStarPlayerComponent.class);

  public static final ComponentKey<SingerPlayerComponent> SINGER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "singer"),
      SingerPlayerComponent.class);

  public static final ComponentKey<PsychologistPlayerComponent> PSYCHOLOGIST = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "psychologist"),
      PsychologistPlayerComponent.class);

  public static final ComponentKey<PuppeteerPlayerComponent> PUPPETEER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "puppeteer"),
      PuppeteerPlayerComponent.class);

  public static final ComponentKey<ManipulatorPlayerComponent> MANIPULATOR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "manipulator"),
      ManipulatorPlayerComponent.class);

  public static final ComponentKey<InControlCCA> INCONTROLCCA = InControlCCA.KEY;
  public static final ComponentKey<InsaneKillerPlayerComponent> INSANE_KILLER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "insane_killer"),
      InsaneKillerPlayerComponent.class);

  public static final ComponentKey<HoanMeirinPlayerComponent> hoan_meirin = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hoan_meirin"),
          HoanMeirinPlayerComponent.class);
  public static final ComponentKey<BetterVigilantePlayerComponent> BETTER_VIGILANTE = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "better_vigilante"),
          BetterVigilantePlayerComponent.class);
  public static final ComponentKey<RecorderPlayerComponent> RECORDER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "recorder"),
      RecorderPlayerComponent.class);

  public static final ComponentKey<PlayerVolumeComponent> VOLUME = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "volume"),
      PlayerVolumeComponent.class);
  public static final ComponentKey<BomberPlayerComponent> BOMBER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "bomber"),
      BomberPlayerComponent.class);
  public static final ComponentKey<MonitorPlayerComponent> MONITOR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "monitor"),
      MonitorPlayerComponent.class);
  public static final ComponentKey<TelegrapherPlayerComponent> TELEGRAPHER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "telegrapher"),
      TelegrapherPlayerComponent.class);

  public static final ComponentKey<DefibrillatorComponent> DEFIBRILLATOR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "defibrillator"),
      DefibrillatorComponent.class);
  public static final ComponentKey<DeathPenaltyComponent> DEATH_PENALTY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "death_penalty"),
      DeathPenaltyComponent.class);
  public static final ComponentKey<SwapperPlayerComponent> SWAPPER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "swapper"),
      SwapperPlayerComponent.class);
  public static final ComponentKey<PatrollerPlayerComponent> PATROLLER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "patroller"),
      PatrollerPlayerComponent.class);

  public static final ComponentKey<GlitchRobotPlayerComponent> GLITCH_ROBOT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "glitch_robot"),
      GlitchRobotPlayerComponent.class);

  public static final ComponentKey<NianShouPlayerComponent> NIAN_SHOU = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "nianshou"),
      NianShouPlayerComponent.class);
  public static final ComponentKey<MagicianPlayerComponent> MAGICIAN = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "magician"),
      MagicianPlayerComponent.class);
  public static final ComponentKey<BanditPlayerComponent> BANDIT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "bandit"),
      BanditPlayerComponent.class);

  public static final ComponentKey<BloodFeudistPlayerComponent> BLOOD_FEUDIST = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "blood_feudist"),
      BloodFeudistPlayerComponent.class);

  public static final ComponentKey<ClockmakerPlayerComponent> CLOCKMAKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "clockmaker"),
      ClockmakerPlayerComponent.class);

  public static final ComponentKey<BestVigilantePlayerComponent> BEST_VIGILANTE = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "best_vigilante"),
      BestVigilantePlayerComponent.class);

  public static final ComponentKey<AccountantPlayerComponent> ACCOUNTANT = AccountantPlayerComponent.KEY;

  public static final ComponentKey<WaterGhostPlayerComponent> WATER_GHOST = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "water_ghost"),
      WaterGhostPlayerComponent.class);

  public static final ComponentKey<DiverPlayerComponent> DIVER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "diver"),
      DiverPlayerComponent.class);

  public static final ComponentKey<AlchemistPlayerComponent> ALCHEMIST = AlchemistPlayerComponent.KEY;

  public static final ComponentKey<DIOPlayerComponent> DIO = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "dio"),
      DIOPlayerComponent.class);

  public static final ComponentKey<ShootingFrenzyPlayerComponent> SHOOTING_FRENZY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "shooting_frenzy"),
      ShootingFrenzyPlayerComponent.class);

  public static final ComponentKey<WatcherPlayerComponent> WATCHER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "watcher"),
      WatcherPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.modifier.expedition.ExpeditionComponent> EXPEDITION = org.agmas.noellesroles.modifier.expedition.ExpeditionComponent.KEY;

  public static final ComponentKey<TemporaryEffectPlayerComponent> TEMPORARY_EFFECT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "temporary_effect"),
      TemporaryEffectPlayerComponent.class);

  public ModComponents() {
    // CCA 需要无参构造函数
  }

  @Override
  public void registerWorldComponentFactories(WorldComponentFactoryRegistry worldComponentFactoryRegistry) {
    worldComponentFactoryRegistry.register(ConfigWorldComponent.KEY, ConfigWorldComponent::new);
  }

  @Override
  public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {

    // 注册通用技能组件 - 附加到玩家实体
    // RespawnCopyStrategy.NEVER_COPY 表示玩家重生时不保留数据（游戏开始时会重新初始化）
    registry.beginRegistration(Player.class, AWESOME)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AwesomePlayerComponent::new);
    registry.beginRegistration(Player.class, ABILITY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SREAbilityPlayerComponent::new);
    registry.beginRegistration(Player.class, PATROLLER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PatrollerPlayerComponent::new);
    registry.beginRegistration(Player.class, SWAPPER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SwapperPlayerComponent::new);

    // 注册复仇者组件 - 存储绑定目标和激活状态
    registry.beginRegistration(Player.class, AVENGER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AvengerPlayerComponent::new);

    // 注册算命大师组件 - 存储目标和死亡倒计时
    registry.beginRegistration(Player.class, FORTUNETELLER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(FortunetellerPlayerComponent::new);

    // 注册阴谋家组件 - 存储目标和死亡倒计时
    registry.beginRegistration(Player.class, CONSPIRATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ConspiratorPlayerComponent::new);

    // 注册滑头鬼组件 - 被动收入计时器
    registry.beginRegistration(Player.class, SLIPPERY_GHOST)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SlipperyGhostPlayerComponent::new);

    // 注册电报员组件 - 存储使用次数
    registry.beginRegistration(Player.class, BROADCASTER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BroadcasterPlayerComponent::new);

    // 注册邮差组件 - 存储传递状态和物品
    registry.beginRegistration(Player.class, POSTMAN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PostmanPlayerComponent::new);

    // 注册私家侦探组件 - 存储审查技能冷却和目标状态
    registry.beginRegistration(Player.class, DETECTIVE)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DetectivePlayerComponent::new);

    // 注册拳击手组件 - 存储钢筋铁骨技能状态
    registry.beginRegistration(Player.class, BOXER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BoxerPlayerComponent::new);

    // 注册跟踪者组件 - 存储三阶段状态
    registry.beginRegistration(Player.class, STALKER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(StalkerPlayerComponent::new);

    // 注册运动员组件 - 存储疾跑技能冷却和状态
    registry.beginRegistration(Player.class, ATHLETE)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AthletePlayerComponent::new);

    // 注册慕恋者组件 - 存储能量和窥视状态
    registry.beginRegistration(Player.class, ADMIRER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AdmirerPlayerComponent::new);

    // 注册设陷者组件 - 存储陷阱和标记状态
    registry.beginRegistration(Player.class, TRAPPER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(TrapperPlayerComponent::new);

    // 注册明星组件 - 存储发光状态和技能冷却
    registry.beginRegistration(Player.class, STAR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SuperStarPlayerComponent::new);

    // 注册歌手组件 - 存储音乐播放冷却状态
    registry.beginRegistration(Player.class, SINGER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SingerPlayerComponent::new);

    // 注册心理学家组件 - 存储治疗状态和冷却
    registry.beginRegistration(Player.class, PSYCHOLOGIST)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PsychologistPlayerComponent::new);

    // 注册傀儡师组件 - 存储收集尸体、假人操控状态
    registry.beginRegistration(Player.class, PUPPETEER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PuppeteerPlayerComponent::new);

    // 注册操纵师组件 - 存储被操纵目标和控制状态
    registry.beginRegistration(Player.class, MANIPULATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ManipulatorPlayerComponent::new);

    registry.beginRegistration(Player.class, INCONTROLCCA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InControlCCA::new);
    registry.beginRegistration(Player.class, INSANE_KILLER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InsaneKillerPlayerComponent::new);

    registry.beginRegistration(Player.class, GamblerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(GamblerPlayerComponent::new);
    registry.beginRegistration(Player.class, MorphlingPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(MorphlingPlayerComponent::new);
    registry.beginRegistration(Player.class, VoodooPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(VoodooPlayerComponent::new);
    registry.beginRegistration(PlayerBodyEntity.class, BodyDeathReasonComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(BodyDeathReasonComponent::new);
    registry.beginRegistration(Player.class, ExecutionerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(ExecutionerPlayerComponent::new);
    registry.beginRegistration(Player.class, RecallerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(RecallerPlayerComponent::new);
    // 注册魔术师组件
    registry.beginRegistration(Player.class, MAGICIAN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MagicianPlayerComponent::new);

    // 注册起搏器组件
    registry.beginRegistration(Player.class, DEFIBRILLATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DefibrillatorComponent::new);

    registry.beginRegistration(Player.class, NoiseMakerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(NoiseMakerPlayerComponent::new);
    registry.beginRegistration(Player.class, GhostPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(GhostPlayerComponent::new);
    registry.beginRegistration(Player.class, VulturePlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(VulturePlayerComponent::new);
    registry.beginRegistration(Player.class, ThiefPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(ThiefPlayerComponent::new);
    registry.beginRegistration(Player.class, CandleBearerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(CandleBearerPlayerComponent::new);
    registry.beginRegistration(Player.class, BETTER_VIGILANTE)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BetterVigilantePlayerComponent::new);

    // 注册记录员组件 - 存储猜测记录和可用角色
    registry.beginRegistration(Player.class, hoan_meirin)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(HoanMeirinPlayerComponent::new);
    // 注册记录员组件 - 存储猜测记录和可用角色
    registry.beginRegistration(Player.class, RECORDER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(RecorderPlayerComponent::new);
    // 注册炸弹客组件

    registry.beginRegistration(Player.class, VOLUME)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PlayerVolumeComponent::new);
    registry.beginRegistration(Player.class, BOMBER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BomberPlayerComponent::new);

    registry.beginRegistration(Player.class, WAYFARER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(WayfarerPlayerComponent::new);
    // 注册监察员组件
    registry.beginRegistration(Player.class, MONITOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MonitorPlayerComponent::new);

    // 注册电报员组件 - 存储匿名消息发送次数
    registry.beginRegistration(Player.class, TELEGRAPHER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(TelegrapherPlayerComponent::new);

    // 注册死亡惩罚组件
    registry.beginRegistration(Player.class, DEATH_PENALTY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DeathPenaltyComponent::new);

    registry.beginRegistration(Player.class, GLITCH_ROBOT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(GlitchRobotPlayerComponent::new);

    // 注册年兽组件
    registry.beginRegistration(Player.class, NIAN_SHOU)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(NianShouPlayerComponent::new);

    // 注册强盗组件
    registry.beginRegistration(Player.class, BANDIT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BanditPlayerComponent::new);

    // 注册仇杀客组件
    registry.beginRegistration(Player.class, BLOOD_FEUDIST)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BloodFeudistPlayerComponent::new);

    // 注册钟表匠组件
    registry.beginRegistration(Player.class, CLOCKMAKER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ClockmakerPlayerComponent::new);

    // 注册更好的义警组件
    registry.beginRegistration(Player.class, BEST_VIGILANTE)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BestVigilantePlayerComponent::new);

    // 注册远征队组件
    registry.beginRegistration(Player.class, EXPEDITION)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.modifier.expedition.ExpeditionComponent::new);
    registry.beginRegistration(Player.class, MA_CHEN_XU)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MaChenXuPlayerComponent::new);
    // 注册临时效果组件 - 存储肾上腺素体力提升和狗皮膏药保护
    registry.beginRegistration(Player.class, TEMPORARY_EFFECT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(TemporaryEffectPlayerComponent::new);

    // 注册会计组件 - 存储模式、被动收入计时器
    registry.beginRegistration(Player.class, ACCOUNTANT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AccountantPlayerComponent::new);

    // 注册药剂师组件 - 存蹲下素材获取、药剂选择、调制次数
    registry.beginRegistration(Player.class, ALCHEMIST)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AlchemistPlayerComponent::new);

    // 注册潜水员组件 - 存储技能状态
    registry.beginRegistration(Player.class, DIVER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DiverPlayerComponent::new);

    // 注册水鬼组件 - 存储技能冷却、干涸死亡计时
    registry.beginRegistration(Player.class, WATER_GHOST)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(WaterGhostPlayerComponent::new);

    // 注册迪奥组件 - 存储时间停止、吸食尸体、最后的狂欢状态
    registry.beginRegistration(Player.class, DIO)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DIOPlayerComponent::new);

    // 注册FOOD & DRINK组件 - 存储到并非所有人身上
    registry.beginRegistration(Player.class, FoodDrinkGlowComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(FoodDrinkGlowComponent::new);

    // 注册射击狂热组件 - 刽子手的狂暴射击模式
    registry.beginRegistration(Player.class, SHOOTING_FRENZY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ShootingFrenzyPlayerComponent::new);

    registry.beginRegistration(Player.class, WATCHER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(WatcherPlayerComponent::new);

    // ==================== 示例：注册更多组件 ====================
    //
    // 如果你的角色需要存储特定数据，可以在这里注册更多组件：
    //
    // 1. 先在上面定义 ComponentKey
    // public static final ComponentKey<ExampleRoleComponent> EXAMPLE =
    // ComponentRegistry.getOrCreate(
    // Identifier.of(Noellesroles.MOD_ID, "example"),
    // ExampleRoleComponent.class
    // );
    //
    // 2. 然后在这里注册
    // registry.beginRegistration(PlayerEntity.class, EXAMPLE)
    // .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
    // .end(ExampleRoleComponent::new);

  }
}
