package org.agmas.noellesroles.component;

import io.wifi.starrailexpress.cca.PlayerBodyEntityComponent;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.Noellesroles;
import net.exmo.sre.repair.component.RepairRolePlayerComponent;
import org.agmas.noellesroles.content.entity.DoomedSinnerBodyEntity;
import org.agmas.noellesroles.game.roles.innocence.accountant.AccountantPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.adventurer.AdventurerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.alchemist.AlchemistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.athlete.AthletePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.awesome_binglus.AwesomePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.ayayaya.AyayayaPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.broadcaster.BroadcasterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.builder.BuilderPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent;
import org.agmas.noellesroles.game.roles.innocence.clock_maker.ClockmakerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.detective.AgentPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.diviner.DivinerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.driver.DiverPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.fortuneteller.FortunetellerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.glitch_robot.GlitchRobotPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.great_detective.GreatDetectivePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.hoan_meirin.HoanMeirinPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.jade_general.JadeGeneralPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.locksmith_inspiration.LocksmithInspirationComponent;
import org.agmas.noellesroles.game.roles.innocence.magician.MagicianPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.meatball.MeatballPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.mortician.MorticianPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.noise_maker.NoiseMakerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.painter.PainterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.pilot.PilotPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.psychologist.PsychologistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.super_star.SuperStarPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.telegrapher.TelegrapherPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.bandit.BanditPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.bomber.BomberPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.conspirator.ConspiratorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.creeper.CreeperPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.dio.DIOPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ShootingFrenzyPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ninja.NinjaPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.hunter.HunterPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.shadow_falcon.ShadowFalconPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.swapper.SwapperPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.water_ghost.WaterGhostPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.wizard.WizardPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cupid.CupidPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mafia.GodfatherComponent;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.panda.PandaComponent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.musician_phantom.PhantomMusicianPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.reasoner.ReasonerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.slippery_ghost.SlipperyGhostPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.special.super_loose_end.SuperLooseEndPlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.ghost_eye.GhostEyePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.patroller.PatrollerPlayerComponent;
import org.agmas.noellesroles.voice.HeliumBuzzPlayerComponent;
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

  public static final ComponentKey<SlipperyGhostPlayerComponent> PRANKSTER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "prankster"),
      SlipperyGhostPlayerComponent.class);

  public static final ComponentKey<BroadcasterPlayerComponent> BROADCASTER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "broadcaster"),
      BroadcasterPlayerComponent.class);

  public static final ComponentKey<AyayayaPlayerComponent> AYAYAYA = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ayayaya"),
      AyayayaPlayerComponent.class);

  public static final ComponentKey<AgentPlayerComponent> AGENT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "agent"),
      AgentPlayerComponent.class);

  public static final ComponentKey<NoiseMakerPlayerComponent> NOISEMAKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "noise_maker"),
      NoiseMakerPlayerComponent.class);
  public static final ComponentKey<BoxerPlayerComponent> FIGHTER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "fighter"),
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

  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent> WARLOCK = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "warlock"),
      org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent> EMBALMER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "embalmer"),
      org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent.class);

  /** Dream（梦魇）：狂暴 / 面具冷却 / 船。 */
  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent> DREAM = org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent.KEY;
  /** Dream（梦魇）：全员虚拟血量（默认 20 滴血，只被 Dream 铁斧扣除）。 */
  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent> DREAM_HEALTH = org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent.KEY;

  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent> SKINCRAWLER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "skincrawler"),
      org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent.class);

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
  public static final ComponentKey<PandaComponent> panda = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "panda"),
          PandaComponent.class);
  public static final ComponentKey<BetterVigilantePlayerComponent> BETTER_VIGILANTE = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "better_vigilante"),
          BetterVigilantePlayerComponent.class);
  public static final ComponentKey<RecorderPlayerComponent> RECORDER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "recorder"),
      RecorderPlayerComponent.class);

  public static final ComponentKey<CuckooPlayerComponent> CUCKOO = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "cuckoo"),
      CuckooPlayerComponent.class);

  public static final ComponentKey<ReasonerPlayerComponent> REASONER = ReasonerPlayerComponent.KEY;

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
  public static final ComponentKey<MercenaryPlayerComponent> MERCENARY = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mercenary"),
      MercenaryPlayerComponent.class);
  public static final ComponentKey<BanditPlayerComponent> BANDIT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "bandit"),
      BanditPlayerComponent.class);
  public static final ComponentKey<NinjaPlayerComponent> NINJA = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ninja"),
      NinjaPlayerComponent.class);

  public static final ComponentKey<NostalgistPlayerComponent> NOSTALGIST = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "nostalgist"),
      NostalgistPlayerComponent.class);

  public static final ComponentKey<WraithAssassinPlayerComponent> WRAITH_ASSASSIN = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "wraith_assassin"),
      WraithAssassinPlayerComponent.class);

  public static final ComponentKey<SaltedFishPlayerComponent> SALTED_FISH = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "salted_fish"),
      SaltedFishPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent> LEATHER_PIG = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "leather_pig"),
      org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.roles.vigilante.leon.LeonPlayerComponent> LEON = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "leon"),
      org.agmas.noellesroles.game.roles.vigilante.leon.LeonPlayerComponent.class);

  public static final ComponentKey<BloodFeudistPlayerComponent> BLOOD_FEUDIST = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "blood_feudist"),
      BloodFeudistPlayerComponent.class);

  public static final ComponentKey<ClockmakerPlayerComponent> CLOCKMAKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "clockmaker"),
      ClockmakerPlayerComponent.class);

  public static final ComponentKey<CreeperPlayerComponent> CREEPER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "creeper"),
      CreeperPlayerComponent.class);

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

  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent> DELAYER = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "delayer"),
          org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent> EXPEDITION = org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent.KEY;

  public static final ComponentKey<TemporaryEffectPlayerComponent> TEMPORARY_EFFECT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "temporary_effect"),
      TemporaryEffectPlayerComponent.class);

  // 氦气变声组件 - 独立同步给所有玩家
  public static final ComponentKey<HeliumBuzzPlayerComponent> HELIUM_BUZZ = HeliumBuzzPlayerComponent.KEY;

  public static final ComponentKey<LocksmithInspirationComponent> LOCKSMITH_INSPIRATION = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "locksmith_inspiration"),
          LocksmithInspirationComponent.class);

  public static final ComponentKey<ImitatorPlayerComponent> IMITATOR = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "imitator"),
      ImitatorPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent> PARTY = ComponentRegistry
      .getOrCreate(
          ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "party_killer"),
          org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent.class);

  public static final ComponentKey<org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent> FOOL = org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent.KEY;

  public static final ComponentKey<org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent> MONOKUMA = org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent.KEY;

  // 影隼组件 - 杀手阵营，掠食技能
  public static final ComponentKey<ShadowFalconPlayerComponent> SHADOW_FALCON = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "shadow_falcon"),
      ShadowFalconPlayerComponent.class);

  // 猎人组件 - 杀手阵营，弓击杀
  public static final ComponentKey<HunterPlayerComponent> HUNTER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "hunter"),
      HunterPlayerComponent.class);

  public static final ComponentKey<SpellbreakerPlayerComponent> SPELLBREAKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "spellbreaker"),
      SpellbreakerPlayerComponent.class);

  // 飞行员组件 - 平民阵营，喷气背包
  public static final ComponentKey<PilotPlayerComponent> PILOT = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "pilot"),
      PilotPlayerComponent.class);

  // 肉汁组件 - 平民阵营，赏金系统
  public static final ComponentKey<MeatballPlayerComponent> MEATBALL = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "meatball"),
      MeatballPlayerComponent.class);

  // 殡仪员组件 - 平民阵营，透视物品和搜刮尸体
  public static final ComponentKey<MorticianPlayerComponent> MORTICIAN = MorticianPlayerComponent.KEY;

  public static final ComponentKey<RepairRolePlayerComponent> REPAIR_ROLES = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "repair_roles"),
      RepairRolePlayerComponent.class);
  // 画家组件 - 平民阵营，绘画灵感、求索、挚友技能
  public static final ComponentKey<PainterPlayerComponent> PAINTER = PainterPlayerComponent.KEY;

  // 建筑师组件 - 平民阵营，建造/拆除客户端墙
  public static final ComponentKey<BuilderPlayerComponent> BUILDER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "builder"),
      BuilderPlayerComponent.class);

  // 玉将军组件 - 平民阵营，飞踢位移技能（击退/眩晕/变老人）
  public static final ComponentKey<JadeGeneralPlayerComponent> JADE_GENERAL = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "jade_general"),
      JadeGeneralPlayerComponent.class);

  // 巫师组件 - 杀手阵营，魔素/法杖/魔药/法术池
  public static final ComponentKey<WizardPlayerComponent> WIZARD = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "wizard"),
      WizardPlayerComponent.class);

  // 占卜家组件 - 乘客阵营，晶球占卜尸体
  public static final ComponentKey<DivinerPlayerComponent> DIVINER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "diviner"),
      DivinerPlayerComponent.class);

  // 鬼眼·杨间组件 - 警长阵营，鬼眼扫描 + 诡域
  public static final ComponentKey<GhostEyePlayerComponent> GHOST_EYE = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "ghost_eye"),
      GhostEyePlayerComponent.class);

  // 摄影师组件 - 记录画框购买次数
  public static final ComponentKey<PhotographerPlayerComponent> PHOTOGRAPHER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "photographer"),
      PhotographerPlayerComponent.class);

  // 超级亡命徒组件
  public static final ComponentKey<SuperLooseEndPlayerComponent> SUPER_LOOSE_END = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "super_loose_end"),
      SuperLooseEndPlayerComponent.class);

  // 疫使组件 - 杀手方中立阵营，病毒感染
  public static final ComponentKey<InfectedPlayerComponent> INFECTED = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "infected"),
      InfectedPlayerComponent.class);

  // 葬仪组件 - 杀手方中立阵营，曳柩/丧钟/清洗技能，造尸能力
  public static final ComponentKey<org.agmas.noellesroles.game.roles.neutral.mortician.MorticianBodyMakerPlayerComponent> MORTICIAN_BODYMAKER = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "mortician_bodymaker"),
      org.agmas.noellesroles.game.roles.neutral.mortician.MorticianBodyMakerPlayerComponent.class);

  // 幻音师组件 - 杀手方中立阵营，音效商店+传送技能
  public static final ComponentKey<PhantomMusicianPlayerComponent> PHANTOM_MUSICIAN = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "phantom_musician"),
      PhantomMusicianPlayerComponent.class);

  // 亡灵之主组件 - 杀手阵营，亡灵召唤 + 感染滚雪球
  public static final ComponentKey<org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordPlayerComponent> UNDEAD_LORD = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "undead_lord"),
      org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordPlayerComponent.class);

  public static final ComponentKey<CupidPlayerComponent> CUPID = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "cupid"),
      CupidPlayerComponent.class);

  public static final ComponentKey<RavenPlayerComponent> RAVEN = ComponentRegistry.getOrCreate(
      ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "raven"), RavenPlayerComponent.class);
  public static final ComponentKey<org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent> AMON =
      org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent.KEY;
  public static final ComponentKey<CakeMakerComponent> CAKE_MAKER = CakeMakerComponent.KEY;
  public static final ComponentKey<AdventurerPlayerComponent> ADVENTURER = AdventurerPlayerComponent.KEY;

  public ModComponents() {
    // CCA 需要无参构造函数
  }

  @Override
  public void registerWorldComponentFactories(WorldComponentFactoryRegistry worldComponentFactoryRegistry) {
    worldComponentFactoryRegistry.register(ConfigWorldComponent.KEY, ConfigWorldComponent::new);
  }

  @Override
  public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
    registry.beginRegistration(DoomedSinnerBodyEntity.class, PlayerBodyEntityComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PlayerBodyEntityComponent::new);

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

    registry.beginRegistration(Player.class, NINJA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(NinjaPlayerComponent::new);

    // 注册怀旧者组件 - 里世界状态机
    registry.beginRegistration(Player.class, NOSTALGIST)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(NostalgistPlayerComponent::new);

    registry.beginRegistration(Player.class, WRAITH_ASSASSIN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(WraithAssassinPlayerComponent::new);

    registry.beginRegistration(Player.class, SALTED_FISH)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SaltedFishPlayerComponent::new);

    registry.beginRegistration(Player.class, LEATHER_PIG)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent::new);

    // 注册里昂组件 - 「幸存之人」被动草药发放
    registry.beginRegistration(Player.class, LEON)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.vigilante.leon.LeonPlayerComponent::new);

    // 注册算命大师组件 - 存储目标和死亡倒计时
    registry.beginRegistration(Player.class, FORTUNETELLER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(FortunetellerPlayerComponent::new);

    // 注册阴谋家组件 - 存储目标和死亡倒计时
    registry.beginRegistration(Player.class, CONSPIRATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ConspiratorPlayerComponent::new);

    // 注册捣蛋鬼组件 - 被动收入计时器
    registry.beginRegistration(Player.class, PRANKSTER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SlipperyGhostPlayerComponent::new);

    registry.beginRegistration(Player.class, panda)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PandaComponent::new);

    // 注册电报员组件 - 存储使用次数
    registry.beginRegistration(Player.class, BROADCASTER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BroadcasterPlayerComponent::new);

    // 注册射命丸文组件 - 存储传递状态和物品
    registry.beginRegistration(Player.class, AYAYAYA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AyayayaPlayerComponent::new);

    // 注册探员组件 - 存储审查技能冷却和目标状态
    registry.beginRegistration(Player.class, AGENT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AgentPlayerComponent::new);

    // 注册斗士组件 - 存储钢筋铁骨技能状态
    registry.beginRegistration(Player.class, FIGHTER)
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

    registry.beginRegistration(Player.class, DELAYER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.delayer.DelayerPlayerComponent::new);

    registry.beginRegistration(Player.class, GamblerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(GamblerPlayerComponent::new);
    registry.beginRegistration(Player.class, MorphlingPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(MorphlingPlayerComponent::new);
    registry.beginRegistration(Player.class, org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent::new);

    registry.beginRegistration(Player.class, VoodooPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(VoodooPlayerComponent::new);
    registry.beginRegistration(Player.class, ExecutionerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(ExecutionerPlayerComponent::new);
    registry.beginRegistration(Player.class, RecallerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(RecallerPlayerComponent::new);
    // 注册魔术师组件
    registry.beginRegistration(Player.class, MAGICIAN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MagicianPlayerComponent::new);

    // 注册雇佣兵组件
    registry.beginRegistration(Player.class, MERCENARY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MercenaryPlayerComponent::new);

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
    registry.beginRegistration(Player.class, PelicanPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(PelicanPlayerComponent::new);
    registry.beginRegistration(Player.class, GodfatherComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(GodfatherComponent::new);
    registry.beginRegistration(Player.class, ThiefPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(ThiefPlayerComponent::new);
    registry.beginRegistration(Player.class, CandleBearerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY).end(CandleBearerPlayerComponent::new);
    registry.beginRegistration(Player.class,
            org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent::new);
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
    registry.beginRegistration(Player.class, CUCKOO)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(CuckooPlayerComponent::new);

    registry.beginRegistration(Player.class, REASONER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ReasonerPlayerComponent::new);
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

    // 注册苦力怕组件
    registry.beginRegistration(Player.class, CREEPER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(CreeperPlayerComponent::new);

    // 注册远征队组件
    registry.beginRegistration(Player.class, EXPEDITION)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.modifier.expedition.ExpeditionComponent::new);
    registry.beginRegistration(Player.class, MA_CHEN_XU)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MaChenXuPlayerComponent::new);
    // 注册临时效果组件 - 存储肾上腺素体力提升和狗皮膏药保护
    registry.beginRegistration(Player.class, TEMPORARY_EFFECT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(TemporaryEffectPlayerComponent::new);

    // 注册氦气变声组件 - 独立同步给所有玩家以便变声效果生效
    registry.beginRegistration(Player.class, HELIUM_BUZZ)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(HeliumBuzzPlayerComponent::new);

    // 注册锁匠灵感组件 - 存储灵感点数和看门进度
    registry.beginRegistration(Player.class, LOCKSMITH_INSPIRATION)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(LocksmithInspirationComponent::new);

    // 注册模仿者组件 - 存储复制能力、槽位、充能状态
    registry.beginRegistration(Player.class, IMITATOR)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ImitatorPlayerComponent::new);

    registry.beginRegistration(Player.class, PARTY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent::new);

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

    registry.beginRegistration(Player.class, GhostStateComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(GhostStateComponent::new);

    // 注册射击狂热组件 - 刽子手的狂暴射击模式
    registry.beginRegistration(Player.class, SHOOTING_FRENZY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ShootingFrenzyPlayerComponent::new);

    registry.beginRegistration(Player.class, WATCHER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(WatcherPlayerComponent::new);

    // 注册愚者组件 - 存储塔罗会成员、处刑者手枪子弹、异端效果等
    registry.beginRegistration(Player.class, FOOL)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent::new);

    // 注册黑白组件 - 存储阶段、狂暴前奏计时器、光环状态
    registry.beginRegistration(Player.class, MONOKUMA)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaPlayerComponent::new);

    // 注册影隼组件 - 存储掠食技能冷却、临时护盾
    registry.beginRegistration(Player.class, SHADOW_FALCON)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(ShadowFalconPlayerComponent::new);

    // 注册猎人组件 - 存储杀敌计数
    registry.beginRegistration(Player.class, HUNTER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(HunterPlayerComponent::new);

    registry.beginRegistration(Player.class, SPELLBREAKER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SpellbreakerPlayerComponent::new);

    // 注册飞行员组件 - 存储喷气背包状态
    registry.beginRegistration(Player.class, PILOT)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PilotPlayerComponent::new);

    // 注册肉汁组件 - 存储赏金
    registry.beginRegistration(Player.class, MEATBALL)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MeatballPlayerComponent::new);

    // 注册殡仪员组件 - 存储冷却和已打开的尸体
    registry.beginRegistration(Player.class, MORTICIAN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(MorticianPlayerComponent::new);

    // 注册大侦探组件 - 存储已检查尸体、各凶手线索与目标距离快照
    registry.beginRegistration(Player.class, GreatDetectivePlayerComponent.KEY)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(GreatDetectivePlayerComponent::new);

    registry.beginRegistration(Player.class, REPAIR_ROLES)
        .respawnStrategy(RespawnCopyStrategy.ALWAYS_COPY)
        .end(RepairRolePlayerComponent::new);
    // 注册画家组件 - 存储绘画灵感、求索、挚友技能状态
    registry.beginRegistration(Player.class, PAINTER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PainterPlayerComponent::new);

    // 注册建筑师组件 - 存储建造/拆除模式、冷却时间
    registry.beginRegistration(Player.class, BUILDER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(BuilderPlayerComponent::new);

    // 注册玉将军组件 - 飞踢冷却外的位移/命中状态
    registry.beginRegistration(Player.class, JADE_GENERAL)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(JadeGeneralPlayerComponent::new);

    // 注册巫师组件 - 魔素/法术状态
    registry.beginRegistration(Player.class, WIZARD)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(WizardPlayerComponent::new);

    // 注册占卜家组件 - 晶球占卜冷却/已占卜尸体
    registry.beginRegistration(Player.class, DIVINER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(DivinerPlayerComponent::new);

    // 注册鬼眼·杨间组件 - 鬼眼扫描计时 / 诡域状态
    registry.beginRegistration(Player.class, GHOST_EYE)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(GhostEyePlayerComponent::new);

    // 注册摄影师组件 - 画框购买次数
    registry.beginRegistration(Player.class, PHOTOGRAPHER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PhotographerPlayerComponent::new);

    // 注册超级亡命徒组件
    registry.beginRegistration(Player.class, SUPER_LOOSE_END)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(SuperLooseEndPlayerComponent::new);

    // 注册疫使组件 - 杀手方中立阵营，病毒感染
    registry.beginRegistration(Player.class, INFECTED)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(InfectedPlayerComponent::new);

    // 注册葬仪组件 - 杀手方中立阵营，曳柩/丧钟/清洗技能，造尸能力
    registry.beginRegistration(Player.class, MORTICIAN_BODYMAKER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.neutral.mortician.MorticianBodyMakerPlayerComponent::new);

    // 注册幻音师组件 - 杀手方中立阵营，音效商店+传送技能
    registry.beginRegistration(Player.class, PHANTOM_MUSICIAN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(PhantomMusicianPlayerComponent::new);

    // 注册亡灵之主组件 - 杀手阵营，亡灵召唤 + 感染滚雪球
    registry.beginRegistration(Player.class, UNDEAD_LORD)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordPlayerComponent::new);

    // 注册咒法师组件
    registry.beginRegistration(Player.class, CUPID)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(CupidPlayerComponent::new);

    registry.beginRegistration(Player.class, RAVEN)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(RavenPlayerComponent::new);
    registry.beginRegistration(Player.class, AMON)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent::new);
    registry.beginRegistration(Player.class, CAKE_MAKER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(CakeMakerComponent::new);
    registry.beginRegistration(Player.class, ADVENTURER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(AdventurerPlayerComponent::new);

    registry.beginRegistration(Player.class, WARLOCK)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent::new);

    // 注册嬉命人组件
    registry.beginRegistration(Player.class, EMBALMER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent::new);

    // 注册 Dream（梦魇）组件
    registry.beginRegistration(Player.class, DREAM)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent::new);
    // Dream 虚拟血量：挂在所有玩家身上
    registry.beginRegistration(Player.class, DREAM_HEALTH)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent::new);

    // 注册窃皮者组件
    registry.beginRegistration(Player.class, SKINCRAWLER)
        .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
        .end(org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent::new);

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
