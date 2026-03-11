package org.agmas.noellesroles.role;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.AdmirerPlayerComponent;
import org.agmas.noellesroles.component.AvengerPlayerComponent;
import org.agmas.noellesroles.component.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.component.BomberPlayerComponent;
import org.agmas.noellesroles.component.BoxerPlayerComponent;
import org.agmas.noellesroles.component.BroadcasterPlayerComponent;
import org.agmas.noellesroles.component.ClockmakerPlayerComponent;
import org.agmas.noellesroles.component.ConspiratorPlayerComponent;
import org.agmas.noellesroles.component.DetectivePlayerComponent;
import org.agmas.noellesroles.component.GlitchRobotPlayerComponent;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.MonitorPlayerComponent;
import org.agmas.noellesroles.component.NianShouPlayerComponent;
import org.agmas.noellesroles.component.PatrollerPlayerComponent;
import org.agmas.noellesroles.component.PostmanPlayerComponent;
import org.agmas.noellesroles.component.PsychologistPlayerComponent;
import org.agmas.noellesroles.component.PuppeteerPlayerComponent;
import org.agmas.noellesroles.component.RecorderPlayerComponent;
import org.agmas.noellesroles.component.SingerPlayerComponent;
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.component.StarPlayerComponent;
import org.agmas.noellesroles.component.TrapperPlayerComponent;
import org.agmas.noellesroles.component.VeteranPlayerComponent;
import org.agmas.noellesroles.roles.chef.ChefRole;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.roles.gambler.GamblerRole;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.roles.manipulator.ManipulatorRole;
import org.agmas.noellesroles.roles.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.roles.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.roles.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.roles.vulture.VulturePlayerComponent;

import com.mojang.serialization.Codec;

import io.wifi.starrailexpress.api.NoramlRole;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.Role;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.BartenderPlayerComponent;
import io.wifi.starrailexpress.cca.PlayerPoisonComponent;
import io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;

/**
 * 角色定义类
 *
 * 在这里定义所有自定义角色
 *
 * ==================== 角色参数说明 ====================
 *
 * Role 构造函数参数：
 * 1. identifier - 角色唯一标识符 (Identifier)
 * 2. color - 角色颜色 (int RGB)，用于 UI 显示
 * 3. isInnocent - 是否为无辜者阵营 (boolean)
 * true = 乘客阵营（需要完成任务，被杀手视为目标）
 * false = 非乘客阵营
 * 4. canUseKiller - 是否可以使用杀手功能 (boolean)
 * true = 可以使用刀、地道、杀手聊天、杀手商店
 * false = 不能使用杀手功能
 * 5. moodType - 心情类型 (Role.MoodType)
 * REAL = 真实心情（乘客用）
 * FAKE = 假心情（杀手用，不会真正疯狂）
 * 6. maxSprintTime - 最大冲刺时间 (int)
 * 使用 TMMRoles.CIVILIAN.getMaxSprintTime() 获取默认值
 * Integer.MAX_VALUE = 无限冲刺
 * 7. hideOnScoreboard - 是否在计分板上隐藏 (boolean)
 * true = 隐藏（杀手/中立通常隐藏）
 * false = 显示（乘客通常显示）
 *
 * ==================== 阵营类型 ====================
 *
 * | 阵营 | isInnocent | canUseKiller | 说明 |
 * |----------|------------|--------------|------|
 * | 乘客 | true | false | 普通平民，需要完成任务 |
 * | 杀手 | false | true | 可以杀人，使用地道和杀手商店 |
 * | 中立 | false | false | 特殊胜利条件，无杀手能力 |
 * | 邪恶乘客 | true | true | 乘客阵营但有杀手能力（特殊） |
 */
public class ModRoles {
  @SuppressWarnings("deprecation")
  public static final AttachmentType<String> ENTITY_NOTE_MAKER = AttachmentRegistry.<String>builder()
      .persistent(Codec.STRING)
      .buildAndRegister(Noellesroles.id("entity_note_maker"));
  // ==================== 角色 ID 定义 ====================
  // 建议格式：MOD_ID:role_name

  // 乘客阵营角色 ID
  public static ResourceLocation JESTER_ID = Noellesroles.id("jester");
  public static ResourceLocation CONDUCTOR_ID = Noellesroles.id("conductor");
  public static ResourceLocation BARTENDER_ID = Noellesroles.id("bartender");
  public static ResourceLocation NOISEMAKER_ID = Noellesroles.id("noisemaker");
  public static ResourceLocation AWESOME_BINGLUS_ID = Noellesroles.id("awesome_binglus");
  public static ResourceLocation VOODOO_ID = Noellesroles.id("voodoo");
  public static ResourceLocation RECALLER_ID = Noellesroles.id("recaller");
  public static final ResourceLocation BETTER_VIGILANTE_ID = Noellesroles.id("better_vigilante");
  public static final ResourceLocation BEST_VIGILANTE_ID = Noellesroles.id("best_vigilante");
  public static ResourceLocation BROADCASTER_ID = Noellesroles.id("broadcaster");
  public static ResourceLocation GHOST_ID = Noellesroles.id("ghost");
  public static ResourceLocation DOCTOR_ID = Noellesroles.id("doctor");
  public static ResourceLocation ATTENDANT_ID = Noellesroles.id("attendant");
  public static ResourceLocation SHERIFF_ID = Noellesroles.id("sheriff");
  public static ResourceLocation CORONER_ID = Noellesroles.id("coroner");
  public static ResourceLocation PATROLLER_ID = Noellesroles.id("patroller");
  public static final ResourceLocation GLITCH_ROBOT_ID = Noellesroles.id("glitch_robot");
  public static final ResourceLocation AVENGER_ID = Noellesroles.id("avenger");
  public static final ResourceLocation SLIPPERY_GHOST_ID = Noellesroles.id("slippery_ghost");
  public static final ResourceLocation ENGINEER_ID = Noellesroles.id("engineer");
  public static final ResourceLocation BOXER_ID = Noellesroles.id("boxer");
  public static final ResourceLocation POSTMAN_ID = Noellesroles.id("postman");
  public static final ResourceLocation DETECTIVE_ID = Noellesroles.id("detective");
  public static final ResourceLocation ATHLETE_ID = Noellesroles.id("athlete");
  public static final ResourceLocation STAR_ID = Noellesroles.id("star");
  public static final ResourceLocation VETERAN_ID = Noellesroles.id("veteran");
  public static final ResourceLocation SINGER_ID = Noellesroles.id("singer");
  public static final ResourceLocation PSYCHOLOGIST_ID = Noellesroles.id("psychologist");
  public static final ResourceLocation PHOTOGRAPHER_ID = Noellesroles.id("photographer");
  public static ResourceLocation ELF_ID = Noellesroles.id("elf");
  public static ResourceLocation WIND_YAOSE_ID = Noellesroles.id("wind_yaose");
  public static ResourceLocation CHEF_ID = Noellesroles.id("chef");
  public static ResourceLocation MAGICIAN_ID = Noellesroles.id("magician");
  public static ResourceLocation CLOCKMAKER_ID = Noellesroles.id("clockmaker");
  public static final ResourceLocation WRITER_ID = Noellesroles.id("writer");
  public static final ResourceLocation TELEGRAPHER_ID = Noellesroles.id("telegrapher");
  public static final ResourceLocation RESCUER_ID = Noellesroles.id("rescuer");
  public static final ResourceLocation FIREFIGHTER_ID = Noellesroles.id("firefighter");
  public static final ResourceLocation ACCOUNTANT_ID = Noellesroles.id("accountant");
  public static final ResourceLocation ALCHEMIST_ID = Noellesroles.id("alchemist");
  public static final ResourceLocation SWAST_ID = Noellesroles.id("swast");
  public static final ResourceLocation MARTIAL_ARTS_INSTRUCTOR_ID = Noellesroles.id("martial_arts_instructor");

  // 杀手阵营角色 ID
  public static ResourceLocation MORPHLING_ID = Noellesroles.id("morphling");
  public static ResourceLocation PHANTOM_ID = Noellesroles.id("phantom");
  public static ResourceLocation SWAPPER_ID = Noellesroles.id("swapper");
  public static ResourceLocation EXECUTIONER_ID = Noellesroles.id("executioner");
  public static ResourceLocation GAMBLER_ID = Noellesroles.id("gambler");
  public static ResourceLocation POISONER_ID = Noellesroles.id("poisoner");
  public static ResourceLocation BAKA_ID = Noellesroles.id("baka");
  public static ResourceLocation PACHURI_ID = Noellesroles.id("pachuri");
  public static ResourceLocation MAID_SAKUYA_ID = Noellesroles.id("maid_sakuya");
  public static ResourceLocation HOAN_MEIRIN_ID = Noellesroles.id("hoan_meirin");
  public static ResourceLocation LOCKSMITH_ID = Noellesroles.id("locksmith");
  public static ResourceLocation EXAMPLER_ID = Noellesroles.id("exampler");

  public static ResourceLocation THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES_ID = Noellesroles
      .id("the_insane_damned_paranoid_killer");
  public static final ResourceLocation CONSPIRATOR_ID = Noellesroles.id("conspirator");
  public static final ResourceLocation CLEANER_ID = Noellesroles.id("cleaner");
  public static final ResourceLocation TRAPPER_ID = Noellesroles.id("trapper");
  public static final ResourceLocation BOMBER_ID = Noellesroles.id("bomber");
  public static final ResourceLocation MANIPULATOR_ID = Noellesroles.id("manipulator");
  public static final ResourceLocation BANDIT_ID = Noellesroles.id("bandit");
  public static final ResourceLocation BLOOD_FEUDIST_ID = Noellesroles.id("blood_feudist");

  // 中立阵营
  public static final ResourceLocation STALKER_ID = Noellesroles.id("stalker");
  public static final ResourceLocation ADMIRER_ID = Noellesroles.id("admirer");
  public static final ResourceLocation PUPPETEER_ID = Noellesroles.id("puppeteer");
  public static final ResourceLocation MONITOR_ID = Noellesroles.id("monitor");
  public static final ResourceLocation COMMANDER_ID = Noellesroles.id("commander");
  public static final ResourceLocation RECORDER_ID = Noellesroles.id("recorder");
  public static ResourceLocation VULTURE_ID = Noellesroles.id("vulture");
  public static final ResourceLocation NIAN_SHOU_ID = Noellesroles.id("nianshou");
  public static final ResourceLocation OLDMAN_ID = Noellesroles.id("oldman");
  public static final ResourceLocation THIEF_ID = Noellesroles.id("thief");
  public static final ResourceLocation FORTUNETELLER_ID = Noellesroles.id("fortuneteller");

  public static final ResourceLocation WAYFARER_ID = Noellesroles.id("wayfarer");
  public static final ResourceLocation DIO_ID = Noellesroles.id("dio");
  public static final ResourceLocation JOJO_ID = Noellesroles.id("jojo");

  // MAID_SAKUYA 十六夜咲夜
  public static Role MAID_SAKUYA = TMMRoles.registerRole(new NormalRole(
      MAID_SAKUYA_ID, // 角色 ID
      new Color(164, 173, 193).getRGB(), // 蓝灰色
      true, // isInnocent = 非乘客阵营（杀手）
      false, // canUseKiller = 杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime() * 2, // 2 倍冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setCanSeeTime(true);
  // DIO 迪奥
  public static Role DIO = TMMRoles.registerRole(new NoramlRole(
      DIO_ID, // 角色 ID
      new Color(255, 215, 0).getRGB(), // 黄色 - 代表 DIO 的金色气场
      false, // isInnocent = 非乘客阵营（杀手）
      true, // canUseKiller = 杀手能力
      Role.MoodType.FAKE, // 真实心情
      Integer.MAX_VALUE, // 无限冲刺时间
      true // 不隐藏计分板
  )).setCanSeeCoin(true).setComponentKey(ModComponents.DIO).setOccupiedRoleCount(2);
  // JOJO 承太郎
  public static Role JOJO = TMMRoles.registerRole(new NoramlRole(
      JOJO_ID, // 角色 ID
      Color.YELLOW.getRGB(),
      true, // isInnocent = 非乘客阵营（杀手）
      false, // canUseKiller = 杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(),
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setVigilanteTeam(true);
  // ==================== 已注册角色定义 ====================
  // 乘客阵营角色
  // 中立偏狼：小镇做题家
  public static Role EXAMPLER = TMMRoles.registerRole(
      new NormalRole(EXAMPLER_ID, new Color(213, 95, 214).getRGB(),
          false, true, Role.MoodType.FAKE,
          TMMRoles.CIVILIAN.getMaxSprintTime(), true))
      .setCanSeeCoin(true).setCanSeeTeammateKiller(true)
      .setCanUseInstinct(true);
  // 好人：大妖精baka
  public static Role BAKA = TMMRoles.registerRole(
      new NormalRole(BAKA_ID, new Color(185, 240, 243).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true);
  // 好人：锁匠
  public static Role LOCKSMITH  = TMMRoles.registerRole(
      new NormalRole(LOCKSMITH_ID, new Color(100,200,200).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true);
  // 红美铃
  public static Role HOAN_MEIRIN  = TMMRoles.registerRole(
      new NormalRole(HOAN_MEIRIN_ID, new Color(243,140,132).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true);
  // 好人：帕秋莉 Patchouli Knowledge
  public static Role PACHURI  = TMMRoles.registerRole(
      new NormalRole(PACHURI_ID, new Color(184,144,182).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true);
  public static Role OLDMAN = TMMRoles.registerRole(
      new NormalRole(OLDMAN_ID, new Color(112, 146, 190).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true);
  // 算命大师
  public static Role FORTUNETELLER = TMMRoles.registerRole(
      new NormalRole(FORTUNETELLER_ID, new Color(239, 228, 176).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true).setCanSeeTime(true);
  public static Role ELF = TMMRoles.registerRole(
      new NormalRole(ELF_ID, new Color(106, 255, 179).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setVigilanteTeam(true).setCanSeeCoin(true).setCanPickUpRevolver(false);
  public static Role WIND_YAOSE = TMMRoles.registerRole(
      new NormalRole(WIND_YAOSE_ID, new Color(106, 255, 179).getRGB(),
          false, false, Role.MoodType.FAKE,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true).setCanPickUpRevolver(false).setNeutrals(true).setCanUseInstinct(true)
      .setNeutralForKiller(true);
  public static Role CHEF = TMMRoles.registerRole(
      new ChefRole(CHEF_ID, new Color(229, 255, 0).getRGB(),
          true, false, Role.MoodType.REAL,
          TMMRoles.CIVILIAN.getMaxSprintTime(), false))
      .setCanSeeCoin(true).setCanPickUpRevolver(true);
  // 红尘客
  public static Role WAYFARER = TMMRoles.registerRole(
      new NormalRole(WAYFARER_ID, new Color(255, 54, 105).getRGB(),
          false, false, Role.MoodType.FAKE,
          Integer.MAX_VALUE, false))
      .setCanSeeCoin(true).setNeutrals(true).setCanPickUpRevolver(false)
      .setComponentKey(ModComponents.WAYFARER).setCanUseInstinct(false);
  public static Role JESTER = TMMRoles
      .registerRole(new NoramlRole(JESTER_ID, new Color(186, 85, 211).getRGB(), false,
          false, Role.MoodType.FAKE, Integer.MAX_VALUE, true))
      .setNeutralForKiller(true).setCanSeeTeammateKiller(false).setCanUseInstinct(true);
  public static Role CONDUCTOR = TMMRoles
      .registerRole(new NoramlRole(CONDUCTOR_ID, new Color(184, 134, 11).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));
  public static Role BARTENDER = TMMRoles
      .registerRole(new NoramlRole(BARTENDER_ID, new Color(217, 241, 240).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));
  public static Role NOISEMAKER = TMMRoles
      .registerRole(new NoramlRole(NOISEMAKER_ID, new Color(200, 255, 0).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));
  public static Role AWESOME_BINGLUS = TMMRoles
      .registerRole(new NoramlRole(AWESOME_BINGLUS_ID, new Color(155, 255, 168).getRGB(), true, false,
          Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));
  public static Role VOODOO = TMMRoles
      .registerRole(new NoramlRole(VOODOO_ID, new Color(128, 114, 253).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)
          .setComponentKey(VoodooPlayerComponent.KEY));
  public static Role RECALLER = TMMRoles
      .registerRole(new NoramlRole(RECALLER_ID, new Color(135, 206, 235).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)
          .setComponentKey(RecallerPlayerComponent.KEY));
  public static Role BETTER_VIGILANTE = TMMRoles
      .registerRole(new NoramlRole(BETTER_VIGILANTE_ID, new Color(0, 255, 255).getRGB(), true, false,
          Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)
          .setComponentKey(BetterVigilantePlayerComponent.KEY));
  public static Role BROADCASTER = TMMRoles
      .registerRole(new NoramlRole(BROADCASTER_ID, new Color(0, 255, 0).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), true)
          .setComponentKey(BroadcasterPlayerComponent.KEY));
  public static Role GHOST = TMMRoles
      .registerRole(new NoramlRole(GHOST_ID, new Color(200, 200, 200).getRGB(), true, false,
          Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), true));
  public static Role DOCTOR = TMMRoles
      .registerRole(new NoramlRole(DOCTOR_ID, new Color(30, 144, 255).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));
  public static Role ATTENDANT = TMMRoles
      .registerRole(new NoramlRole(ATTENDANT_ID, (new Color(198, 185, 36)).getRGB(),
          true, false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));
  public static Role PATROLLER = TMMRoles
      .registerRole(new NoramlRole(PATROLLER_ID, 0x1B8AE5, true, false, Role.MoodType.REAL,
          io.wifi.starrailexpress.game.GameConstants.getInTicks(0, 10), false)
          .setVigilanteTeam(true).setComponentKey(PatrollerPlayerComponent.KEY))
      .setCanPickUpRevolver(true);

  /**
   * 更好的义警角色
   * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 技能：开局自带一颗手榴弹
   */
  public static Role BEST_VIGILANTE;

  /**
   * 作家角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 专属商店：书与笔(100金币)
   * - 2%概率刷新
   */
  // 作家角色 - 乘客阵营
  public static Role WRITER = TMMRoles.registerRole(new NoramlRole(
      WRITER_ID, // 角色 ID
      new Color(254, 254, 254).getRGB(), // 白色 - 代表书与笔
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true);

  /**
   * 电报员角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 不隐藏计分板
   * - 技能：可以发送匿名消息给所有玩家
   * - 每局最多发送6次
   * - 2%概率刷新
   */
  // 电报员角色 - 乘客阵营
  public static Role TELEGRAPHER = TMMRoles.registerRole(new NoramlRole(
      TELEGRAPHER_ID, // 角色 ID
      new Color(199, 155, 233).getRGB(), // 浅紫色
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setComponentKey(org.agmas.noellesroles.component.TelegrapherPlayerComponent.KEY);

  /**
   * 搜救员角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 专属商店：绳索(150金币)、裹尸袋(150金币)
   * - 只在中级及高级场中出现
   */
  // 搜救员角色 - 乘客阵营
  public static Role RESCUER = TMMRoles.registerRole(new NoramlRole(
      RESCUER_ID, // 角色 ID
      new Color(255, 140, 0).getRGB(), // 橙色 - 代表救援
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true);

  /**
   * 消防员角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 专属商店：消防斧(150金币)、灭火器(150金币)
   * - 只在中级及高级场中出现
   */
  // 消防员角色 - 乘客阵营
  public static Role FIREFIGHTER = TMMRoles.registerRole(new NoramlRole(
      FIREFIGHTER_ID, // 角色 ID
      new Color(255, 69, 0).getRGB(), // 红橙色 - 代表消防
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true);

  /**
   * 会计角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 被动：每60秒获得25金币
   * - 技能：蹲下按技能键切换收入/支出模式，直接按技能键花费175金币发动技能
   * - 收入模式：查看目标玩家金币量是否超过300
   * - 支出模式：查看半径4格内玩家30秒内总支出金币数量的大致范围
   * - 专属商店：存折(100金币)
   */
  // 会计角色 - 乘客阵营
  public static Role ACCOUNTANT = TMMRoles.registerRole(new NoramlRole(
      ACCOUNTANT_ID, // 角色 ID
      new Color(0, 128, 128).getRGB(), // 青色 - 代表会计
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setComponentKey(org.agmas.noellesroles.component.AccountantPlayerComponent.KEY);

  /**
   * 药剂师角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 被动：持续蹲下每30秒获取一次药剂素材
   * - 技能：蹲下按技能键切换药剂，直接按技能键调制药剂
   * - 药剂：肾上腺素(100金币)、抗生素(100金币)、鹤顶红(200金币)、狗皮膏药(150金币)
   * - 限制：每种药剂只能调两次
   */
  // 药剂师角色 - 乘客阵营
  public static Role ALCHEMIST = TMMRoles.registerRole(new NoramlRole(
      ALCHEMIST_ID, // 角色 ID
      new Color(128, 0, 128).getRGB(), // 紫色 - 代表药剂
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setComponentKey(org.agmas.noellesroles.component.AlchemistPlayerComponent.KEY);

  /**
   * 特警角色
   * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
   * - 仅在特定地图生成（areas1/areas3/areas4/areas7/areas10）
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 无法捡起左轮手枪
   * - 开局物品：狙击枪、马格南子弹×1
   * - 专属商店：马格南子弹(150金币)、瞄准镜(100金币)、铁门钥匙(75金币)
   */
  // 特警角色 - 警长阵营
  public static Role SWAST = TMMRoles.registerRole(new NoramlRole(
      SWAST_ID, // 角色 ID
      new Color(0, 191, 255).getRGB(), // 深天蓝色 - 代表特警的专业与冷静
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setVigilanteTeam(true).setCanPickUpRevolver(false);

  /**
   * 武术教官角色
   * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 无法捡起左轮手枪
   * - 开局物品：双节棍
   */
  // 武术教官角色 - 警长阵营
  public static Role MARTIAL_ARTS_INSTRUCTOR = TMMRoles.registerRole(new NoramlRole(
      MARTIAL_ARTS_INSTRUCTOR_ID, // 角色 ID
      new Color(255, 215, 0).getRGB(), // 金黄色 - 代表武术的荣耀与威严
      true, // isInnocent = 乘客阵营
      false, // canUseKiller = 无杀手能力
      Role.MoodType.REAL, // 真实心情
      TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
      false // 不隐藏计分板
  )).setCanSeeCoin(true).setVigilanteTeam(true).setCanPickUpRevolver(false);

  // 杀手阵营角色
  public static Role CLEANER = TMMRoles
      .registerRole(new NoramlRole(CLEANER_ID, new Color(255, 1, 124).getRGB(), false,
          true, Role.MoodType.FAKE, Integer.MAX_VALUE, true).setCanPickUpRevolver(true));
  public static Role MORPHLING = TMMRoles
      .registerRole(new NoramlRole(MORPHLING_ID, new Color(220, 20, 60).getRGB(), false,
          true, Role.MoodType.FAKE, Integer.MAX_VALUE, true)
          .setComponentKey(MorphlingPlayerComponent.KEY));
  public static Role MANIPULATOR = TMMRoles
      .registerRole(new ManipulatorRole(MANIPULATOR_ID, new Color(90, 20, 61).getRGB(), false,
          true, Role.MoodType.FAKE, Integer.MAX_VALUE, true)
          .setComponentKey(ManipulatorPlayerComponent.KEY))
      .setComponentKey(ModComponents.MANIPULATOR);
  public static Role PHANTOM = TMMRoles
      .registerRole(new NoramlRole(PHANTOM_ID, new Color(80, 5, 5, 192).getRGB(), false,
          true, Role.MoodType.FAKE, Integer.MAX_VALUE, true))
      .setComponentKey(ModComponents.ABILITY);
  public static Role SWAPPER = TMMRoles
      .registerRole(new NoramlRole(SWAPPER_ID, new Color(255, 0, 255).getRGB(), false,
          true, Role.MoodType.FAKE, Integer.MAX_VALUE, true))
      .setComponentKey(ModComponents.SWAPPER);
  public static Role EXECUTIONER = TMMRoles
      .registerRole(new NoramlRole(EXECUTIONER_ID, new Color(74, 27, 5).getRGB(),
          false, true, Role.MoodType.FAKE, Integer.MAX_VALUE, true)
          .setComponentKey(ExecutionerPlayerComponent.KEY));
  public static Role GAMBLER = TMMRoles
      .registerRole(new GamblerRole(GAMBLER_ID, new Color(72, 61, 139).getRGB(), false,
          false, Role.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
      .setCanPickUpRevolver(true).setComponentKey(GamblerPlayerComponent.KEY).setNeutrals(true);
  public static Role POISONER = TMMRoles
      .registerRole(new NoramlRole(POISONER_ID, (new Color(115, 0, 57)).getRGB(), false,
          true, Role.MoodType.FAKE, Integer.MAX_VALUE, true));

  public static Role THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES = TMMRoles
      .registerRole(new NoramlRole(
          THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES_ID,
          new Color(255, 0, 0, 192).getRGB(), false, true, Role.MoodType.FAKE,
          Integer.MAX_VALUE, true).setComponentKey(InsaneKillerPlayerComponent.KEY));

  // 中立阵营角色
  public static Role COMMANDER = TMMRoles.registerRole(
      new NormalRole(COMMANDER_ID, new Color(185, 122, 87).getRGB(),
          false, false, Role.MoodType.FAKE,
          Integer.MAX_VALUE, true))
      .setCanSeeCoin(true).setCanPickUpRevolver(false).setNeutrals(true).setNeutralForKiller(true)
      .setCanUseInstinct(true);
  public static Role VULTURE = TMMRoles
      .registerRole(new NoramlRole(VULTURE_ID, new Color(210, 105, 30).getRGB(), false,
          false, Role.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true)
          .setComponentKey(VulturePlayerComponent.KEY))
      .setNeutralForKiller(true).setCanSeeTeammateKiller(false);
  public static Role CORONER = TMMRoles
      .registerRole(new NoramlRole(CORONER_ID, new Color(122, 122, 122).getRGB(), true,
          false, Role.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false));

  // ==================== 自定义角色对象定义 ====================
  // 乘客阵营角色
  /**
   * 复仇者角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 开局白板，没有任何物品
   * - 当绑定的玩家死亡时，获得左轮手枪并看到凶手
   * - 绑定方式：默认随机，可配置为瞄准绑定
   */
  public static Role AVENGER;

  /**
   * 滑头鬼角色
   * - 不属于乘客阵营 (isInnocent = false)
   * - 不能使用杀手能力 (canUseKiller = false)，但有专属商店
   * - 假心情系统
   * - 标准冲刺时间
   * - 在计分板上隐藏
   * - 被动技能：每10秒获取50金币
   * - 专属商店：空包弹(100)、烟雾弹(300)、撬锁器(50)、关灯(200)
   * - 胜利条件：与杀手同胜
   */
  public static Role SLIPPERY_GHOST;

  /**
   * 工程师角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 专属商店：
   * - 加固门道具(75金币)：右键门使其能防一次撬棍，蹲下右键被卡住的门可解除卡住
   * - 警报陷阱(120金币)：放置在门上，撬棍触发时发出警报声
   */
  public static Role ENGINEER;

  /**
   * 拳击手角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 技能"准备姿势"：
   * - 开局冷却45秒
   * - 使用后进入3秒攻击架势，获得"拳头"武器
   * - 进入架势时有1秒无敌
   * - 拳头左键：击退目标并造成4秒缓慢效果
   * - 攻击间隔1.2秒
   * - 使用后冷却80秒
   */
  public static Role BOXER;

  /**
   * 邮差角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 技能"隐秘传递"：
   * - 花费350金币购买传递盒
   * - 指针对准玩家并右键使用，打开传递界面（一格）
   * - 双方可以将一样物品放入并交给对方
   * - 无使用次数限制，但每次需要购买传递盒
   */
  public static Role POSTMAN;

  /**
   * 私家侦探角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 技能"审查"：
   * - 花费100金币
   * - 指针对准一名玩家并按下技能键
   * - 可以查看目标玩家的物品栏界面
   * - 如果目标玩家移动则会关闭界面
   * - 使用后冷却60秒
   */
  public static Role DETECTIVE;

  /**
   * 运动员角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 无限冲刺时间 (Integer.MAX_VALUE)
   * - 在计分板上显示
   * - 技能"疾跑"：
   * - 使用后获得20秒的速度效果（无粒子，不显示效果图标）
   * - 使用后冷却120秒（2分钟）
   */
  public static Role ATHLETE;

  /**
   * 明星角色
   * - 属于乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 被动技能：每20秒自动发光2秒
   * - 主动技能"聚光灯"：
   * - 使用后让10格范围内的玩家视野都看向自己
   * - 30秒冷却
   */
  public static Role STAR;

  /**
   * 退伍军人角色
   * - 属于好人阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 特殊能力：
   * - 开局获得一把刀
   * - 左键或右键击杀一人后刀消失
   */
  public static Role VETERAN;

  /**
   * 歌手角色
   * - 属于好人阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 特殊能力：
   * - 按技能键随机播放原版唱片音乐
   * - 60秒冷却
   */
  public static Role SINGER;

  /**
   * 心理学家角色
   * - 属于好人阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 特殊能力：
   * - san满时，使用技能对准一个人
   * - 对方不动，超过10秒可以把对方san回复满
   * - 3分钟冷却
   */
  public static Role PSYCHOLOGIST;

  /**
   * 摄影师角色
   * - 属于好人阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 特殊能力：
   * - 可在商店购买拍立得相机
   * - 闪光灯致盲前方玩家并使隐身玩家发光3秒
   * - 可以拍摄照片记录犯罪现场
   * - 死亡时掉落照片
   */
  public static Role PHOTOGRAPHER;

  // 杀手阵营角色
  /**
   * 阴谋家角色
   * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
   * - 可以在商店购买"书页"物品（250金币）
   * - 右键使用书页打开GUI：选择玩家头像，再选择角色
   * - 如果猜测正确，目标玩家40秒后死亡
   * - 猜测错误无惩罚，但书页消耗
   */
  public static Role CONSPIRATOR;

  /**
   * 设陷者角色
   * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
   * - 假心情系统
   * - 标准冲刺时间
   * - 在计分板上隐藏
   * - 技能"灾厄印记"：
   * - 使用技能对准地面设置隐形陷阱
   * - 隐形的灾厄印记，其他玩家踩中会触发
   * - 触发效果：发出巨响暴露位置并发光，施加"标记"
   * - 被标记的玩家被囚禁在原地3秒
   * - 触发两次后囚禁延长到10秒
   * - 触发三次后囚禁延长到25秒
   */
  public static Role TRAPPER;

  /**
   * 炸弹客角色
   * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
   * - 可以在商店购买炸弹
   * - 炸弹倒计时10秒，前5秒隐形
   * - 右键传递炸弹
   */
  public static Role BOMBER;

  // 中立阵营角色
  /**
   * 跟踪者角色
   * - 初始为中立阵营 (isInnocent = false, canUseKiller = false)
   * - 假心情系统
   * - 无限冲刺（一阶段）
   * - 在计分板上隐藏
   * - 三阶段进化机制：
   * - 一阶段（潜伏者）：群体窥视积累能量，满150能量进阶
   * - 二阶段（觉醒猎手）：转为杀手阵营，获得刀和一次免疫，杀2人+30能量进阶
   * - 三阶段（狂暴追击者）：蓄力突进处决，180秒倒计时
   */
  public static Role STALKER;

  /**
   * 慕恋者角色
   * - 中立阵营 (isInnocent = false, canUseKiller = false)
   * - 假心情系统
   * - 标准冲刺时间
   * - 在计分板上隐藏
   * - 技能"群体窥视"：
   * - 按住技能键观察视野内的玩家
   * - 每名被观察玩家每秒 +1 能量
   * - 满300能量后变为随机杀手角色
   */
  public static Role ADMIRER;

  /**
   * 傀儡师角色
   * - 初始为中立阵营 (isInnocent = false, canUseKiller = false)
   * - 假心情系统
   * - 标准冲刺时间
   * - 在计分板上隐藏
   * - 阶段一（收集阶段）：
   * - 右键尸体回收（消失），10秒冷却
   * - 收集人数 >= 游戏总人数/6 时变为杀手阵营
   * - 阶段二（杀手阶段）：
   * - 无法再回收尸体
   * - 使用技能制造假人（使用收集的尸体皮肤）
   * - 操控假人时，随机获得杀手职业
   * - 假人和本体物品栏独立
   * - 假人死亡回到本体，本体死亡则真正死亡
   * - 操控限时1分钟，技能冷却3分钟
   * - 本体状态无法购买商店
   */
  public static Role PUPPETEER;

  /**
   * 监察员角色
   * - 属于好人阵营 (isInnocent = true)
   * - 技能：标记一名玩家并透视其位置，冷却60秒
   */
  public static Role MONITOR;

  /**
   * 记录员角色
   * - 中立阵营 (isInnocent = false, canUseKiller = false)
   * - 假心情系统
   * - 标准冲刺时间
   * - 在计分板上隐藏
   * - 目标：使用笔记选择人和对应职业，如果正确人数达到2/5,获得独立胜利
   */
  public static Role RECORDER;

  /**
   * 故障机器人角色
   * - 乘客阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 小丑心情 (假心情)
   * - 双倍体力上限
   * - 每局只能有 1 个
   * - 专属商店：夜视仪(150金币)、萤石粉(50金币)
   * - 每1分钟自动获得缓慢10 2秒
   * - 被击倒时生成半径4的缓慢2效果云，持续5秒
   */
  public static Role GLITCH_ROBOT;

  /**
   * 年兽角色 - 中立阵营
   * - 中立阵营 (isInnocent = false, canUseKiller = false)
   * - 真实心情
   * - 1.5倍体力
   * - 在杀手视角为好人
   * - 被动：完成任务维持san值
   * - 黑暗环境获得护盾试剂和速度二（一局一次）
   * - 可购买关灯（200金币）
   * - 除岁：所有人获得4个鞭炮，年兽5格内12个鞭炮则年兽死亡
   * - 红包：每2个任务获得1个红包，对他人发放红包可获得100金币
   * - 恭喜发财：剩余5分钟时播放音乐，全场存活玩家获得100金币并回满san
   * - 胜利条件：游戏结束时存活
   */
  public static Role NIAN_SHOU;

  /**
   * 小偷角色 - 中立阵营
   * - 中立阵营 (isInnocent = false, canUseKiller = false)
   * - 假心情 (MoodType.FAKE)
   * - 无限冲刺时间
   * - 技能：蹲下按技能键切换偷钱/偷物品模式，按技能键释放技能（冷却30s，偷取失败不进入冷却）
   * - 偷钱：偷取目标100金币（目标必须至少有100金币）
   * - 偷物品：仿照StupidExpress2的小偷机制
   * - 被动：杀一人获得100金币
   * - 独立胜利条件：手持小偷的荣誉（金锭）回房间睡觉则独立胜利
   * - 小偷的荣誉所需金币数 = 游戏开始总人数 * 75
   */
  public static Role THIEF;

  /**
   * 魔术师角色 - 好人阵营（从模仿者移植）
   * - 好人阵营 (isInnocent = true)
   * - 能捡枪 (setCanPickUpRevolver(true))
   * - 做任务维持san值
   * - 商店购买假枪（175金币）
   * - 商店购买假疯狂模式（250金币）：获得假球棒，穿上疯狂模式外观，不播放音乐
   * - 如果指挥官在场，加入指挥官频道
   */
  public static Role MAGICIAN;

  /**
   * 钟表匠角色 - 好人阵营
   * - 好人阵营 (isInnocent = true)
   * - 不能使用杀手能力 (canUseKiller = false)
   * - 真实心情系统
   * - 标准冲刺时间
   * - 在计分板上显示
   * - 特殊能力：
   * - 能看到游戏时间
   * - 按下技能键花费125金币，减少游戏时间45秒
   * - 世界时间加快2000tick
   * - 游戏时间最多减少至1分30秒
   */
  public static Role CLOCKMAKER;

  /**
   * 强盗角色 - 杀手阵营
   * - 杀手阵营 (isInnocent = false, canUseKiller = true)
   * - 假心情系统
   * - 无限冲刺时间
   * - 在计分板上隐藏
   * - 杀手直觉：只能透视半径10格内的玩家，透视杀手队友无距离限制
   * - 开局自带一把匪徒手枪，一把撬棍
   * - 被动技能：杀人之后可以盗取被杀者一半的钱，被杀害的玩家会减少一半的钱
   * - 专属商店：
   * - 刀 (200金币)
   * - 匪徒手枪 (175金币)
   * - 手榴弹 (600金币)
   * - 关灯 (150金币)
   * - 无疯狂模式、无开锁器和撬棍
   */
  public static Role BANDIT;
  public static Role BLOOD_FEUDIST;

  // ==================== 其他变量定义 ====================
  public static ArrayList<Role> SHOW_MONEY_ROLES = new ArrayList<>();
  public static HashMap<Role, RoleAnnouncementTexts.RoleAnnouncementText> roleRoleAnnouncementTextHashMap = new HashMap<>();

  /**
   * 初始化并注册所有角色
   * 在模组初始化时调用
   */
  public static void init() {
    TMMRoles.LOOSE_END.setCanUseInstinct(true);
    // ModRoles.RECORDER;
    // ModRoles.JESTER.setCanUseInstinct(true);
    // ==================== 注册乘客阵营角色 ====================
    // 复仇者角色 - 乘客阵营
    AVENGER = TMMRoles.registerRole(new NoramlRole(
        AVENGER_ID, // 角色 ID
        new Color(139, 0, 0).getRGB(), // 暗红色 - 代表复仇的血色
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(AvengerPlayerComponent.KEY));

    // 滑头鬼角色 - 中立阵营（使用专属商店）
    SLIPPERY_GHOST = TMMRoles.registerRole(new NoramlRole(
        SLIPPERY_GHOST_ID, // 角色 ID
        new Color(176, 196, 222).getRGB(), // 灰色 - 代表滑头鬼的隐匿
        false, // isInnocent = 非乘客阵营
        false, // canUseKiller = 无杀手能力（使用专属商店）
        Role.MoodType.FAKE, // 假心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 隐藏计分板
    )).setNeutralForKiller(true).setCanSeeTeammateKiller(false);

    // 工程师角色 - 乘客阵营
    ENGINEER = TMMRoles.registerRole(new NoramlRole(
        ENGINEER_ID, // 角色 ID
        new Color(255, 140, 0).getRGB(), // 橙色 - 代表工程帽/工具
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ));

    // 拳击手角色 - 乘客阵营
    BOXER = TMMRoles.registerRole(new NoramlRole(
        BOXER_ID, // 角色 ID
        new Color(205, 92, 92).getRGB(), // 猩红色 - 代表热血/格斗
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(BoxerPlayerComponent.KEY));

    // 邮差角色 - 乘客阵营
    POSTMAN = TMMRoles.registerRole(new NoramlRole(
        POSTMAN_ID, // 角色 ID
        new Color(70, 130, 180).getRGB(), // 钢蓝色 - 代表邮差制服
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(PostmanPlayerComponent.KEY));

    // 私家侦探角色 - 乘客阵营
    DETECTIVE = TMMRoles.registerRole(new NoramlRole(
        DETECTIVE_ID, // 角色 ID
        new Color(205, 133, 63).getRGB(), // 棕色 - 代表侦探风衣
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(DetectivePlayerComponent.KEY));

    // 运动员角色 - 乘客阵营
    ATHLETE = TMMRoles.registerRole(new NoramlRole(
        ATHLETE_ID, // 角色 ID
        new Color(65, 105, 225).getRGB(), // 天蓝色 - 代表运动/活力
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        Integer.MAX_VALUE, // 无限冲刺
        false // 不显示计分板
    ));

    // 明星角色 - 乘客阵营
    STAR = TMMRoles.registerRole(new NoramlRole(
        STAR_ID, // 角色 ID
        new Color(255, 215, 0).getRGB(), // 金色 - 代表明星的光芒
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(StarPlayerComponent.KEY));

    // 退伍军人角色 - 好人阵营
    VETERAN = TMMRoles.registerRole(new NoramlRole(
        VETERAN_ID, // 角色 ID
        new Color(85, 107, 47).getRGB(), // 暗橄榄绿 - 代表军装颜色
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(VeteranPlayerComponent.KEY));

    // 歌手角色 - 好人阵营
    SINGER = TMMRoles.registerRole(new NoramlRole(
        SINGER_ID, // 角色 ID
        new Color(255, 105, 180).getRGB(), // 热粉色 - 代表音乐与激情
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(SingerPlayerComponent.KEY));

    // 心理学家角色 - 好人阵营
    PSYCHOLOGIST = TMMRoles.registerRole(new NoramlRole(
        PSYCHOLOGIST_ID, // 角色 ID
        new Color(64, 224, 208).getRGB(), // 青绿色 - 代表心灵治愈
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(PsychologistPlayerComponent.KEY));

    // 摄影师角色 - 好人阵营
    PHOTOGRAPHER = TMMRoles.registerRole(new NoramlRole(
        PHOTOGRAPHER_ID, // 角色 ID
        new Color(72, 209, 204).getRGB(), // 青石色 - 代表相机镜头
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ));

    // ==================== 注册杀手阵营角色 ====================
    // 阴谋家角色 - 杀手阵营
    CONSPIRATOR = TMMRoles.registerRole(new NoramlRole(
        CONSPIRATOR_ID, // 角色 ID
        new Color(85, 26, 139).getRGB(), // 深紫色 - 代表阴谋与神秘
        false, // isInnocent = 非乘客阵营
        true, // canUseKiller = 有杀手能力（可以使用地道、杀手聊天）
        Role.MoodType.FAKE, // 假心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 隐藏计分板
    ).setComponentKey(ConspiratorPlayerComponent.KEY));

    // 设陷者角色 - 杀手阵营
    TRAPPER = TMMRoles.registerRole(new NoramlRole(
        TRAPPER_ID, // 角色 ID
        new Color(239, 69, 30).getRGB(), // 棕色 - 代表陷阱与大地
        false, // isInnocent = 非乘客阵营
        true, // canUseKiller = 有杀手能力
        Role.MoodType.FAKE, // 假心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 隐藏计分板
    ).setComponentKey(TrapperPlayerComponent.KEY));

    // 炸弹客角色 - 杀手阵营
    BOMBER = TMMRoles.registerRole(new NoramlRole(
        BOMBER_ID, // 角色 ID
        new Color(51, 51, 51).getRGB(), // 黑色/深灰色
        false, // isInnocent = 非乘客阵营
        true, // canUseKiller = 有杀手能力
        Role.MoodType.FAKE, // 假心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 隐藏计分板
    ).setComponentKey(BomberPlayerComponent.KEY));

    // ==================== 注册中立阵营角色 ====================
    // 跟踪者角色 - 杀手阵营（一开始就是杀手，通过阶段控制能力）
    STALKER = TMMRoles.registerRole(new NoramlRole(
        STALKER_ID, // 角色 ID
        new Color(47, 79, 79).getRGB(), // 暗紫色 #4B0082
        false, // isInnocent = 非乘客阵营
        true, // canUseKiller = 杀手阵营
        Role.MoodType.FAKE, // 假心情
        Integer.MAX_VALUE, // 无限冲刺
        true // 隐藏计分板
    ).setComponentKey(StalkerPlayerComponent.KEY))
        .setMaxSprintTime(StalkerPlayerComponent.MAX_SPRINT_TIME_IntSupplier);

    // 慕恋者角色 - 中立阵营
    ADMIRER = TMMRoles.registerRole(new NoramlRole(
        ADMIRER_ID, // 角色 ID
        new Color(255, 192, 203).getRGB(), false, false, Role.MoodType.FAKE, Integer.MAX_VALUE,
        true)).setComponentKey(AdmirerPlayerComponent.KEY).setNeutralForKiller(true)
        .setCanSeeTeammateKiller(false);

    // 傀儡师角色 - 中立阵营（初始）
    PUPPETEER = TMMRoles.registerRole(new NoramlRole(
        PUPPETEER_ID, // 角色 ID
        new Color(138, 43, 226).getRGB(), // 深紫罗兰色 - 代表操控与神秘
        false, // isInnocent = 非乘客阵营
        false, // canUseKiller = 无杀手能力（初始）
        Role.MoodType.FAKE, // 假心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 隐藏计分板
    )).setComponentKey(PuppeteerPlayerComponent.KEY).setAutoReset(false).setNeutralForKiller(true)
        .setCanUseInstinct(false);
    // 记录员角色 - 中立阵营
    RECORDER = TMMRoles.registerRole(new NormalRole(
        RECORDER_ID, // 角色 ID
        new Color(95, 158, 160).getRGB(), // 矢车菊蓝
        false, // isInnocent = 非乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.FAKE, // 假心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 隐藏计分板
    )).setComponentKey(RecorderPlayerComponent.KEY).setCanUseInstinct(true);

    // 故障机器人角色 - 乘客阵营
    GLITCH_ROBOT = TMMRoles.registerRole(new NoramlRole(
        GLITCH_ROBOT_ID, // 角色 ID
        new Color(211, 196, 250).getRGB(), // 灰色 - 代表机器人
        true, // isInnocent = 乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.FAKE, // 小丑心情（假心情）
        TMMRoles.CIVILIAN.getMaxSprintTime() * 2, // 双倍体力上限
        false // 不隐藏计分板
    )).setComponentKey(GlitchRobotPlayerComponent.KEY).setCanSeeCoin(true);

    // 监察员角色 - 好人阵营
    MONITOR = TMMRoles.registerRole(new NoramlRole(
        MONITOR_ID, // 角色 ID
        new Color(0, 255, 255).getRGB(), // 青色
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    ).setComponentKey(MonitorPlayerComponent.KEY).setCanSeeCoin(true));

    // 年兽角色 - 中立阵营
    NIAN_SHOU = TMMRoles.registerRole(new NianShouRole(
        NIAN_SHOU_ID, // 角色 ID
        new Color(255, 69, 0).getRGB(), // 红橙色 - 代表年兽的颜色
        false, // isInnocent = 非乘客阵营（中立）
        false, // canUseKiller = 无杀手能力（但可以购买关灯）
        Role.MoodType.REAL, // 真实心情
        (int) (TMMRoles.CIVILIAN.getMaxSprintTime() * 1.5), // 1.5倍体力
        true // 隐藏计分板
    ).setComponentKey(NianShouPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true));

    // 小偷角色 - 中立阵营
    THIEF = TMMRoles.registerRole(new NoramlRole(
        THIEF_ID, // 角色 ID
        new Color(255, 215, 0).getRGB(), // 金色 - 代表财富
        false, // isInnocent = 非乘客阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.FAKE, // 假心情
        Integer.MAX_VALUE, // 无限冲刺时间
        true // 隐藏计分板
    )).setComponentKey(ThiefPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true)
        .setCanSeeTeammateKiller(false);

    // 魔术师角色 - 好人阵营
    MAGICIAN = TMMRoles.registerRole(new NoramlRole(
        MAGICIAN_ID, // 角色 ID
        new Color(255, 165, 0).getRGB(), // 橙色 - 代表魔术师的魅力
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 不显示计分板
    )).setCanPickUpRevolver(true).setCanSeeCoin(true)
        .setNeutralForKiller(true).setCanSeeTeammateKiller(false).setNeutrals(false);

    // 强盗角色 - 杀手阵营
    BANDIT = TMMRoles.registerRole(new NoramlRole(
        BANDIT_ID, // 角色 ID
        new Color(139, 69, 19).getRGB(), // 棕色 - 代表强盗的粗糙感
        false, // isInnocent = 非乘客阵营
        true, // canUseKiller = 有杀手能力
        Role.MoodType.FAKE, // 假心情
        Integer.MAX_VALUE, // 无限冲刺时间
        true // 隐藏计分板
    )).setComponentKey(ModComponents.BANDIT);

    // 仇杀客角色 - 杀手阵营
    BLOOD_FEUDIST = TMMRoles.registerRole(new NoramlRole(
        BLOOD_FEUDIST_ID, // 角色 ID
        new Color(178, 34, 34).getRGB(), // 暗红色 - 代表复仇与愤怒
        false, // isInnocent = 非乘客阵营
        true, // canUseKiller = 有杀手能力
        Role.MoodType.FAKE, // 假心情
        Integer.MAX_VALUE, // 无限冲刺时间
        true // 隐藏计分板
    )).setComponentKey(ModComponents.BLOOD_FEUDIST).setCanSeeCoin(true);

    // 钟表匠角色 - 好人阵营
    CLOCKMAKER = TMMRoles.registerRole(new NoramlRole(
        CLOCKMAKER_ID, // 角色 ID
        new Color(218, 165, 32).getRGB(), // 金色 - 代表钟表与时间
        true, // isInnocent = 好人阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        true // 不显示计分板
    )).setComponentKey(ClockmakerPlayerComponent.KEY).setCanSeeTime(true).setCanSeeCoin(true);

    // 更好的义警角色 - 警长阵营
    BEST_VIGILANTE = TMMRoles.registerRole(new NoramlRole(
        BEST_VIGILANTE_ID, // 角色 ID
        new Color(0, 128, 128).getRGB(), // 深青色 - 代表更强悍的义警
        true, // isInnocent = 警长阵营
        false, // canUseKiller = 无杀手能力
        Role.MoodType.REAL, // 真实心情
        TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
        false // 显示计分板
    )).setVigilanteTeam(true).setCanPickUpRevolver(true).setComponentKey(ModComponents.BEST_VIGILANTE);

    PlayerPoisonComponent.canSyncedRolePaths.add(ModRoles.POISONER_ID.getPath());
    PlayerPoisonComponent.canSyncedRolePaths.add(ModRoles.BARTENDER_ID.getPath());
    BartenderPlayerComponent.canSyncedRolePaths.add(ModRoles.BARTENDER_ID.getPath());
    BartenderPlayerComponent.canSyncedRolePaths.add(ModRoles.CHEF_ID.getPath());
  }

  // ==================== 工具方法 ====================

  /**
   * 检查角色是否为乘客阵营
   */
  public static boolean isCrewRole(Role role) {
    return role.isInnocent();
  }

  /**
   * 检查角色是否为杀手阵营
   */
  public static boolean isKillerRole(Role role) {
    return role.canUseKiller();
  }

  /**
   * 检查角色是否为中立阵营
   */
  public static boolean isNeutralRole(Role role) {
    return !role.isInnocent() && !role.canUseKiller();
  }
}