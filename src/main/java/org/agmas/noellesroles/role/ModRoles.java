package org.agmas.noellesroles.role;

import com.mojang.serialization.Codec;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.*;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.client.gui.RoleAnnouncementTexts;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.item.StalkerKnifeItem;
import org.agmas.noellesroles.game.roles.innocence.accountant.AccountantPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.adventurer.AdventurerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.adventurer.AdventurerRole;
import org.agmas.noellesroles.game.roles.innocence.alchemist.AlchemistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.avenger.AvengerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.boxer.BoxerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.broadcaster.BroadcasterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerRole;
import org.agmas.noellesroles.game.roles.innocence.clock_maker.ClockmakerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.detective.AgentPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.driver.DiverPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.glitch_robot.GlitchRobotPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.great_detective.GreatDetectivePlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.great_detective.GreatDetectiveRole;
import org.agmas.noellesroles.game.roles.innocence.locksmith_inspiration.LocksmithInspirationComponent;
import org.agmas.noellesroles.game.roles.innocence.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.mortician.MorticianRole;
import org.agmas.noellesroles.game.roles.innocence.painter.PainterPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.photographer.PhotographerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.psychologist.PsychologistPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.recaller.RecallerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.salted_fish.SaltedFishPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.singer.SingerPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.super_star.SuperStarPlayerComponent;
import org.agmas.noellesroles.game.roles.innocence.voodoo.VoodooPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.bomber.BomberPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.conspirator.ConspiratorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorRole;
import org.agmas.noellesroles.game.roles.killer.morphling.MorphlingPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ninja.NinjaRole;
import org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistRole;
import org.agmas.noellesroles.game.roles.killer.spellbreaker.SpellbreakerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordRole;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherRole;
import org.agmas.noellesroles.game.roles.killer.wraith_assassin.WraithAssassinRole;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.chef.ChefRole;
import org.agmas.noellesroles.game.roles.neutral.doomedsinner.DoomedSinnerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.gambler.GamblerRole;
import org.agmas.noellesroles.game.roles.neutral.jester.JesterHandler;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaRole;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouRole;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.raven.RavenPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.reasoner.ReasonerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.game.roles.vigilante.patroller.PatrollerPlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.utils.RandomColorUtil;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

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

    public static final ResourceLocation MA_CHEN_XU_ID = Noellesroles.id("ma_chen_xu");
    public static ResourceLocation JESTER_ID = Noellesroles.id("jester");
    public static ResourceLocation CONDUCTOR_ID = Noellesroles.id("conductor");
    public static ResourceLocation BARTENDER_ID = Noellesroles.id("bartender");
    public static ResourceLocation NOISEMAKER_ID = Noellesroles.id("noisemaker");
    public static ResourceLocation AWESOME_BINGLUS_ID = Noellesroles.id("awesome_binglus");
    public static ResourceLocation VOODOO_ID = Noellesroles.id("voodoo");
    public static ResourceLocation RECALLER_ID = Noellesroles.id("recaller");
    public static final ResourceLocation BETTER_VIGILANTE_ID = Noellesroles.id("better_vigilante");
    public static ResourceLocation BROADCASTER_ID = Noellesroles.id("broadcaster");
    public static ResourceLocation GHOST_ID = Noellesroles.id("ghost");
    public static ResourceLocation DOCTOR_ID = Noellesroles.id("doctor");
    public static ResourceLocation ATTENDANT_ID = Noellesroles.id("attendant");
    public static ResourceLocation CORONER_ID = Noellesroles.id("coroner");
    public static ResourceLocation PATROLLER_ID = Noellesroles.id("patroller");
    public static final ResourceLocation SHERIFF_ID = Noellesroles.id("sheriff");
    // 鬼眼·杨间角色 ID - 警长阵营
    public static final ResourceLocation GHOST_EYE_ID = Noellesroles.id("ghost_eye");
    public static final ResourceLocation LEON_ID = Noellesroles.id("leon");
    public static final ResourceLocation GLITCH_ROBOT_ID = Noellesroles.id("glitch_robot");
    public static final ResourceLocation AVENGER_ID = Noellesroles.id("avenger");
    public static final ResourceLocation PRANKSTER_ID = Noellesroles.id("prankster");
    public static final ResourceLocation ENGINEER_ID = Noellesroles.id("engineer");
    public static final ResourceLocation FIGHTER_ID = Noellesroles.id("fighter");
    public static final ResourceLocation AGENT_ID = Noellesroles.id("agent");
    public static final ResourceLocation ATHLETE_ID = Noellesroles.id("athlete");
    public static final ResourceLocation SUPERSTAR_ID = Noellesroles.id("star");
    public static final ResourceLocation VETERAN_ID = Noellesroles.id("veteran");
    public static final ResourceLocation SINGER_ID = Noellesroles.id("singer");
    public static final ResourceLocation PSYCHOLOGIST_ID = Noellesroles.id("psychologist");
    public static final ResourceLocation PHOTOGRAPHER_ID = Noellesroles.id("photographer");
    public static final ResourceLocation PAINTER_ID = Noellesroles.id("painter");
    public static ResourceLocation ELF_ID = Noellesroles.id("elf");
    public static ResourceLocation WIND_YAOSE_ID = Noellesroles.id("wind_yaose");
    public static ResourceLocation CHEF_ID = Noellesroles.id("chef");
    public static ResourceLocation MAGICIAN_ID = Noellesroles.id("magician");
    public static ResourceLocation CLOCKMAKER_ID = Noellesroles.id("clockmaker");
    public static final ResourceLocation RESCUER_ID = Noellesroles.id("rescuer");
    public static final ResourceLocation FIREFIGHTER_ID = Noellesroles.id("firefighter");
    public static final ResourceLocation ACCOUNTANT_ID = Noellesroles.id("accountant");
    public static final ResourceLocation ALCHEMIST_ID = Noellesroles.id("alchemist");
    public static final ResourceLocation DIVER_ID = Noellesroles.id("diver");
    public static final ResourceLocation SWAST_ID = Noellesroles.id("swast");
    public static final ResourceLocation MARTIAL_ARTS_INSTRUCTOR_ID = Noellesroles.id("martial_arts_instructor");
    public static final ResourceLocation SEA_KING_ID = Noellesroles.id("sea_king");
    public static final ResourceLocation WATER_GHOST_ID = Noellesroles.id("water_ghost");

    // 飞行员角色 ID
    public static final ResourceLocation PILOT_ID = Noellesroles.id("pilot");
    // 影隼角色 ID
    public static final ResourceLocation SHADOW_FALCON_ID = Noellesroles.id("shadow_falcon");
    // 肉汁角色 ID
    public static final ResourceLocation MEATBALL_ID = Noellesroles.id("meatball");
    // 殡仪员角色 ID
    public static final ResourceLocation MORTICIAN_ID = Noellesroles.id("mortician");
    // 大侦探角色 ID
    public static final ResourceLocation GREAT_DETECTIVE_ID = Noellesroles.id("great_detective");
    // 建筑师角色 ID
    public static final ResourceLocation BUILDER_ID = Noellesroles.id("builder");
    // 玉将军角色 ID
    public static final ResourceLocation JADE_GENERAL_ID = Noellesroles.id("jade_general");
    // 巫师角色 ID
    public static final ResourceLocation WIZARD_ID = Noellesroles.id("wizard");
    public static final ResourceLocation CAKE_MAKER_ID = Noellesroles.id("cake_maker");
    public static final ResourceLocation ADVENTURER_ID = Noellesroles.id("adventurer");
    public static final ResourceLocation SALTED_FISH_ID = Noellesroles.id("salted_fish");
    // 皮革噶的角色 ID
    public static final ResourceLocation LEATHER_PIG_ID = Noellesroles.id("leather_pig");
    // 亡灵之主角色 ID
    public static final ResourceLocation UNDEAD_LORD_ID = Noellesroles.id("undead_lord");

    // 悍匪角色 ID
    public static final ResourceLocation GANGSTERS_ID = Noellesroles.id("gangsters");
    // 钳工角色 ID
    public static final ResourceLocation FITTER_ID = Noellesroles.id("fitter");

    // 杀手阵营角色 ID
    public static ResourceLocation MORPHLING_ID = Noellesroles.id("morphling");
    public static ResourceLocation PARTY_KILLER_ID = Noellesroles.id("party_killer");
    public static ResourceLocation PHANTOM_ID = Noellesroles.id("phantom");
    public static ResourceLocation SWAPPER_ID = Noellesroles.id("swapper");
    public static ResourceLocation EXECUTIONER_ID = Noellesroles.id("executioner");
    public static ResourceLocation SHOOTING_FRENZY_ID = Noellesroles.id("shooting_frenzy");
    public static ResourceLocation GAMBLER_ID = Noellesroles.id("gambler");
    public static ResourceLocation TAMER_ID = Noellesroles.id("tamer");
    public static ResourceLocation HUNTER_ID = Noellesroles.id("hunter");
    public static ResourceLocation POISONER_ID = Noellesroles.id("poisoner");
    public static ResourceLocation SPELLBREAKER_ID = Noellesroles.id("spellbreaker");

    public static ResourceLocation LOCKSMITH_ID = Noellesroles.id("locksmith");
    public static ResourceLocation EXAMPLER_ID = Noellesroles.id("exampler");
    public static final ResourceLocation NINJA_ID = Noellesroles.id("ninja");

    public static ResourceLocation INSANE_KILLER_ID = Noellesroles
            .id("the_insane_damned_paranoid_killer");
    public static ResourceLocation DELAYER_ID = Noellesroles.id("delayer");

    public static final ResourceLocation CONSPIRATOR_ID = Noellesroles.id("conspirator");
    public static final ResourceLocation CLEANER_ID = Noellesroles.id("cleaner");
    public static final ResourceLocation TRAPPER_ID = Noellesroles.id("trapper");
    public static final ResourceLocation BOMBER_ID = Noellesroles.id("bomber");
    public static final ResourceLocation LOST_KILLER_ID = Noellesroles.id("lost_killer");
    public static final ResourceLocation MANIPULATOR_ID = Noellesroles.id("manipulator");
    public static final ResourceLocation BANDIT_ID = Noellesroles.id("bandit");
    public static final ResourceLocation BLOOD_FEUDIST_ID = Noellesroles.id("blood_feudist");
    public static final ResourceLocation GUEST_GHOST_ID = Noellesroles.id("guest_ghost");
    public static final ResourceLocation SILENCER_ID = Noellesroles.id("silencer");
    public static final ResourceLocation WATCHER_ID = Noellesroles.id("watcher");
    public static final ResourceLocation IMITATOR_ID = Noellesroles.id("imitator");
    public static final ResourceLocation NOSTALGIST_ID = Noellesroles.id("nostalgist");
    public static final ResourceLocation WRAITH_ASSASSIN_ID = Noellesroles.id("wraith_assassin");

    // 中立阵营
    public static final ResourceLocation STALKER_ID = Noellesroles.id("stalker");
    public static final ResourceLocation ADMIRER_ID = Noellesroles.id("admirer");
    public static final ResourceLocation PUPPETEER_ID = Noellesroles.id("puppeteer");
    public static final ResourceLocation MONITOR_ID = Noellesroles.id("monitor");
    public static final ResourceLocation COMMANDER_ID = Noellesroles.id("commander");
    public static final ResourceLocation RECORDER_ID = Noellesroles.id("recorder");
    public static ResourceLocation VULTURE_ID = Noellesroles.id("vulture");
    public static ResourceLocation PELICAN_ID = Noellesroles.id("pelican");
    public static ResourceLocation GODFATHER_ID = Noellesroles.id("godfather");
    public static ResourceLocation MAFIOSO_ID = Noellesroles.id("mafioso");
    public static ResourceLocation JANITOR_ID = Noellesroles.id("janitor");
    public static ResourceLocation NUTRITIONIST_ID = Noellesroles.id("nutritionist");
    public static ResourceLocation PARASOL_ID = Noellesroles.id("parasol");
    public static final ResourceLocation NIAN_SHOU_ID = Noellesroles.id("nianshou");
    public static final ResourceLocation OLDMAN_ID = Noellesroles.id("oldman");
    public static final ResourceLocation THIEF_ID = Noellesroles.id("thief");
    public static final ResourceLocation MERCENARY_ID = Noellesroles.id("mercenary");
    public static final ResourceLocation WARLOCK_ID = Noellesroles.id("warlock");
    public static final ResourceLocation DREAM_ID = Noellesroles.id("dream");
    public static final ResourceLocation EMBALMER_ID = Noellesroles.id("embalmer");
    public static final ResourceLocation SKINCRAWLER_ID = Noellesroles.id("skincrawler");
    public static final ResourceLocation CANDLE_BEARER_ID = Noellesroles.id("candlebearer");
    public static final ResourceLocation RAVEN_ID = Noellesroles.id("raven");
    public static final ResourceLocation REASONER_ID = Noellesroles.id("reasoner");
    public static final ResourceLocation AMON_ID = Noellesroles.id("amon");
    public static final ResourceLocation DOOMED_SINNER_ID = Noellesroles.id("doomed_sinner");
    public static final ResourceLocation FORTUNETELLER_ID = Noellesroles.id("fortuneteller");
    // 占卜家角色 ID
    public static final ResourceLocation DIVINER_ID = Noellesroles.id("diviner");
    // 疫使 ID - 杀手方中立
    public static final ResourceLocation INFECTED_ID = Noellesroles.id("infected");

    // 葬仪 ID - 杀手方中立
    public static final ResourceLocation MORTICIAN_BODYMAKER_ID = Noellesroles.id("mortician_bodymaker");

    // 幻音师 ID - 杀手方中立
    public static final ResourceLocation PHANTOM_MUSICIAN_ID = Noellesroles.id("musician_phantom");
    public static final ResourceLocation CUPID_ID = Noellesroles.id("cupid");

    public static final ResourceLocation WAYFARER_ID = Noellesroles.id("wayfarer");
    public static final ResourceLocation DIO_ID = Noellesroles.id("dio");
    public static final ResourceLocation JOJO_ID = Noellesroles.id("jojo");

    // 愚者 (好人阵营)
    public static final ResourceLocation THE_FOOL_ID = Noellesroles.id("the_fool");
    // 黑白 (中立阵营)
    public static final ResourceLocation MONOKUMA_ID = Noellesroles.id("monokuma");

    /**
     * 飞行员角色 - 平民阵营
     * - 属于平民阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 与影隼绑定生成
     * - 仅在空港(areas_konggang)生成
     * - 可在商店花费175金币购买喷气背包
     * - 技能：按下技能键脱下喷气背包
     */
    public static SRERole PILOT = TMMRoles.registerRole(new NormalRole(
            PILOT_ID, // 角色 ID
            new Color(135, 206, 250).getRGB(), // 天空蓝色 - 代表飞行员/航空
            true, // isInnocent = 平民阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 显示计分板
    )).setCanSeeCoin(true).setCanBeRandomedByOtherRoles(false)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.FLY).setDefaultMax(0)
            .setComponentKey(org.agmas.noellesroles.component.ModComponents.PILOT);

    /**
     * 影隼角色 - 杀手阵营
     * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
     * - 假心情系统
     * - 无限体力 (Integer.MAX_VALUE)
     * - 在计分板上隐藏
     * - 与飞行员绑定生成
     * - 仅在空港(areas_konggang)生成
     * - 商店：刀(130g)、飞刀(200g)、跳跃提升2(180g, 30秒)、手榴弹(350g)、撬棍(35g)、撬锁器(100g)
     * - 技能：掠食
     * - 开局60秒冷却
     * - 使用后获得20秒创造模式飞行
     * - 浮空时获得1层临时护盾（被打掉就没了）
     * - 技能持续20秒，冷却240秒
     * - 死亡后为所有存活杀手提供喷气背包
     */
    public static SRERole SHADOW_FALCON = TMMRoles.registerRole(new NormalRole(
            SHADOW_FALCON_ID, // 角色 ID
            new Color(47, 79, 79).getRGB(), // 暗灰色 - 代表影隼的隐匿
            false, // isInnocent = 非平民阵营（杀手）
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限体力
            true // 隐藏计分板
    )).setCanSeeCoin(true).setCanBeRandomedByOtherRoles(false)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.FLY).setDefaultMax(1)
            .setComponentKey(org.agmas.noellesroles.component.ModComponents.SHADOW_FALCON);

    /**
     * 肉汁角色 - 乘客阵营
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 被动技能：san值消耗较慢
     * - 自带 mood_drain_reduction 效果等级2（减少60% san消耗）
     * - 被动技能：无碰撞
     * - 自带 no_collide 效果，无需担心被玩家碰撞卡位
     * - 被动技能：独处保护
     * - 杀手/中立只能在与你单独相处时击杀你
     * - 条件：4格半径范围内（y轴为3格）没有其他好人
     * - 必须判断造成伤害的来源是否来自非乘客阵营
     * - 被动技能：悬赏
     * - 每完成一个任务会增加自己40金币的悬赏
     * - HUD显示当前悬赏金额
     * - 杀手击杀你会获得所有悬赏
     * - 提示：请尽量通过非任务的方式回复san值
     */
    public static SRERole MEATBALL = TMMRoles
            .registerRole(new org.agmas.noellesroles.game.roles.innocence.meatball.MeatballRole(
                    MEATBALL_ID, // 角色 ID
                    new Color(205, 133, 63).getRGB(), // 棕色 - 代表肉汁
                    true, // isInnocent = 乘客阵营
                    false, // canUseKiller = 无杀手能力
                    SRERole.MoodType.REAL, // 真实心情
                    TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
                    false // 不隐藏计分板
            ).addEffect(
                    new MobEffectInstance(
                            ModEffects.MOOD_DRAIN_REDUCTION,
                            30 * 20, // 持续时间 30s（tick），ambient=true时自动续期
                            0,
                            true, // ambient（环境效果，如信标）
                            false, // showParticles（显示粒子）
                            false // showIcon（显示图标）
                    ))
                    .addEffect(
                            new MobEffectInstance(
                                    ModEffects.NO_COLLIDE,
                                    60 * 20, // 持续时间 60s（tick），ambient=true时自动续期
                                    0,
                                    true, // ambient（环境效果，如信标）
                                    false, // showParticles（显示粒子）
                                    false // showIcon（显示图标）
                            )))
            .setCanSeeCoin(true).setComponentKey(ModComponents.MEATBALL).setDefaultMax(1)
            .setCanBeRandomedByOtherRoles(false)
            .setDefaultEnableChance(2500).setDefaultEnableNeededPlayerCount(12);

    /**
     * 殡仪员角色 - 平民阵营
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 被动技能：透视物品掉落物
     * - 10格范围内（y轴3格）的物品掉落物发光
     * - 技能：搜刮尸体
     * - 打开尸体的物品栏
     * - 最多拿取2个物品
     * - 无法拿取命令方块（普通、循环、连锁）
     * - 拿取后物品放到物品栏，关闭页面
     * - 无法再次打开已打开过的尸体
     * - CD 240秒
     */
    public static SRERole MORTICIAN = TMMRoles.registerRole(new MorticianRole(
            MORTICIAN_ID, // 角色 ID
            new Color(105, 105, 105).getRGB(), // 深灰色 - 代表殡仪员
            true, // isInnocent = 平民阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setComponentKey(ModComponents.MORTICIAN).setDefaultMax(1);

    /**
     * 大侦探角色 - 平民阵营
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 开局自带"推理之书"，右键打开界面
     * - 技能"推理"：对着尸体右键，获取该尸体凶手的一条线索（无凶手则无法推敲）
     * - 一具尸体只能使用一次技能
     * - 某凶手线索 >= 3 条时，可在书上点击"目标情况"查明其与自己的距离（快照）
     */
    public static SRERole GREAT_DETECTIVE = TMMRoles.registerRole(new GreatDetectiveRole(
            GREAT_DETECTIVE_ID, // 角色 ID
            new Color(72, 61, 139).getRGB(), // 暗紫蓝 - 名侦探
            true, // isInnocent = 平民阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setComponentKey(GreatDetectivePlayerComponent.KEY).setDefaultMax(1);

    /**
     * 建筑师角色 - 平民阵营
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 技能：建造模式（默认）- 按技能键在自身位置建造一堵客户端墙
     * - 墙长4格高3格厚1格，沿视角朝向垂直建造
     * - 只替换空气方块
     * - 墙会在60秒后消失
     * - 技能冷却100秒，开局120秒冷却
     * - 技能：拆除模式 - 按技能键拆除墙体，无冷却
     * - 蹲下按技能键切换模式（不受冷却影响）
     * - 游戏结束时清除所有客户端墙
     */
    public static SRERole BUILDER = TMMRoles.registerRole(new NormalRole(
            BUILDER_ID, // 角色 ID
            new Color(205, 133, 63).getRGB(), // 铜棕色 - 代表建筑师/砖块
            true, // isInnocent = 平民阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setComponentKey(ModComponents.BUILDER).setDefaultMax(1)
            .setDefaultEnableChance(7000).setDefaultEnableNeededPlayerCount(12);

    /**
     * 玉将军（平民阵营）。
     * 飞踢（X 技能）：向视线方向位移约五格，可踹开沿途任意房门；踢中目标将其击退两格，
     * 击退撞墙眩晕 4 秒、否则 2 秒，并附加减速 5 秒；命中有概率使目标变老人（无法购买轮椅），
     * 踢得越多概率越高（1%→2%→4%→8% 封顶）。释放后清空自身体力条。冷却 35 秒。
     */
    public static SRERole JADE_GENERAL = TMMRoles.registerRole(new EggRole(
            JADE_GENERAL_ID, // 角色 ID
            new Color(0, 168, 107).getRGB(), // 玉绿色
            true, // isInnocent = 平民阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setComponentKey(ModComponents.JADE_GENERAL).setDefaultMax(1)
            .setDefaultEnableChance(9000).setDefaultEnableNeededPlayerCount(8);

    /**
     * 巫师（杀手阵营）。开局携带法杖与魔药；所有金币收入转化为魔素（bossbar）。
     * 法杖：右键蓄力火焰箭（最多贯穿 2 名、命中即死），左键击退；魔药：大量魔素 + 60 秒一次攻击免疫。
     * 法术池（潜行+技能键切换，技能键释放）：盔甲护身 / 冰霜震慑 / 笼罩暗影 / Explosion!。
     */
    public static SRERole WIZARD = TMMRoles.registerRole(new NormalRole(
            WIZARD_ID, // 角色 ID
            new Color(123, 104, 238).getRGB(), // 紫罗兰 - 魔法
            false, // isInnocent = 杀手阵营
            true, // canUseKiller = 杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺
            true // 隐藏计分板
    )).setCanSeeCoin(false).setComponentKey(ModComponents.WIZARD).setCanBeRandomedByOtherRoles(false)
            .setDefaultMax(1).setDefaultEnableChance(2500);

    /**
     * 亡灵之主（杀手阵营，控场 / 滚雪球）。
     * 右键尸体发动【亡者复苏】将其转化为无意识亡灵（最多同时 3 个，45 秒冷却）。
     * 亡灵追击活人并以攻击累积感染值，满值 3 秒后死亡并转化为新的亡灵。
     * 专属商店：亡灵延命药剂 / 瘟疫之雾 / 亡者召唤符 / 感染增幅器 / 灵魂锁链 / 时之沙漏。
     */
    public static SRERole UNDEAD_LORD = TMMRoles.registerRole(
            new UndeadLordRole(
                    UNDEAD_LORD_ID, // 角色 ID
                    new Color(148, 0, 211).getRGB(), // 灰紫色 - 亡灵
                    false, // isInnocent = 杀手阵营
                    true, // canUseKiller = 有杀手能力
                    SRERole.MoodType.FAKE, // 假心情
                    Integer.MAX_VALUE, // 无限冲刺
                    true // 隐藏计分板
            )).setCanSeeCoin(true).setComponentKey(ModComponents.UNDEAD_LORD)
            .setCanBeRandomedByOtherRoles(false).setDefaultMax(1).setDefaultEnableChance(5000)
            .setDefaultEnableNeededPlayerCount(12);

    public static SRERole GUEST_GHOST = TMMRoles.registerRole(new NormalRole(
            GUEST_GHOST_ID, // 角色 ID
            new Color(175, 245, 130).getRGB(), // 不知道啥颜色
            true, // isInnocent = 非乘客阵营（杀手）
            false, // canUseKiller = 有杀手能力
            SRERole.MoodType.REAL, // 假心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 无限冲刺时间
            true // 隐藏计分板
    )).setCanSeeCoin(true).setOccupiedRoleCount(2).setVigilanteTeam(true).setCanBeRandomedByOtherRoles(false)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.QIYUCUN).setDefaultMax(0);
    public static SRERole MA_CHEN_XU = TMMRoles.registerRole(new NormalRole(
            MA_CHEN_XU_ID, // 角色 ID
            new Color(75, 0, 130).getRGB(), // 深紫色 - 代表恐惧与神秘
            false, // isInnocent = 非乘客阵营（杀手）
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺时间
            true // 隐藏计分板
    )).setComponentKey(ModComponents.MA_CHEN_XU).setCanSeeCoin(true).setOccupiedRoleCount(2)
            .setCanBeRandomedByOtherRoles(false).setSpecialMapRole(SRERole.SpecialMapRoleMap.QIYUCUN)
            .setDefaultMax(1);

    // DIO 迪奥
    public static SRERole DIO = TMMRoles.registerRole(new EggRole(
            DIO_ID, // 角色 ID
            new Color(255, 215, 0).getRGB(), // 黄色 - 代表 DIO 的金色气场
            false, // isInnocent = 非乘客阵营（杀手）
            true, // canUseKiller = 杀手能力
            SRERole.MoodType.FAKE, // 真实心情
            Integer.MAX_VALUE, // 无限冲刺时间
            true // 不隐藏计分板
    )).setCanSeeCoin(true).setComponentKey(ModComponents.DIO).setOccupiedRoleCount(2).setCanSeeBodyDeathReason(true)
            .setCanBeRandomedByOtherRoles(false).setDefaultMax(1);
    // JOJO 承太郎
    public static SRERole JOJO = TMMRoles.registerRole(new EggRole(
            JOJO_ID, // 角色 ID
            Color.YELLOW.getRGB(),
            true, // isInnocent = 非乘客阵营（杀手）
            false, // canUseKiller = 杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setVigilanteTeam(true).setCanBeRandomedByOtherRoles(false).setDefaultMax(0).setCanSetSpawnInfoInConfig(false);

    // ==================== 已注册角色定义 ====================
    // 乘客阵营角色
    // 中立偏狼：小镇做题家
    public static SRERole EXAMPLER = TMMRoles.registerRole(
            new NormalRole(EXAMPLER_ID, new Color(213, 95, 214).getRGB(),
                    false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setCanSeeCoin(true).setCanSeeTeammateKillerRole(true)
            .setCanUseInstinctAndNightVision(true).setDefaultMax(1).setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(400);

    // 好人：锁匠
    public static SRERole LOCKSMITH = TMMRoles.registerRole(
            new NormalRole(LOCKSMITH_ID, new Color(100, 200, 200).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeCoin(true).setComponentKey(LocksmithInspirationComponent.KEY)
            .setCanSetSpawnInfoInConfig(true)
            .setDefaultMax(0);

    public static SRERole OLDMAN = TMMRoles.registerRole(
            new ExtraEffectRole(OLDMAN_ID, new Color(112, 146, 190).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false).addEffect(
                            new MobEffectInstance(
                                    MobEffects.MOVEMENT_SLOWDOWN,
                                    30 * 20, // 持续时间 60s（tick）
                                    1, // 等级（0 = 速度 I）
                                    true, // ambient（环境效果，如信标）
                                    false, // showParticles（显示粒子）
                                    true // showIcon（显示图标）
                            )))
            .setCanSeeCoin(true).setServerGameTickEvent((p, g) -> RoleTickers.oldmanTick(p, g));
    // 算命大师
    public static SRERole FORTUNETELLER = TMMRoles.registerRole(
            new NormalRole(FORTUNETELLER_ID, new Color(239, 228, 176).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeCoin(true).setCanSeeTime(false);

    /**
     * 占卜家（乘客阵营）。开局携带【晶球】，右键对准尸体开始 10 秒施法，获知死者死亡时间 + 随机凶手线索；
     * 50% 概率晶球破碎，60 秒冷却，每具尸体一次。
     * 若占卜对象为亡语杀手伪装的尸体，视为亡语杀手用刀刺死了自己。
     */
    public static SRERole DIVINER = TMMRoles.registerRole(
            new NormalRole(DIVINER_ID, new Color(148, 0, 211).getRGB(), // 紫水晶色
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeCoin(true).setComponentKey(ModComponents.DIVINER)
            .setDefaultMax(1).setDefaultEnableChance(7000);

    public static SRERole SALTED_FISH = TMMRoles.registerRole(
            new NormalRole(SALTED_FISH_ID, new Color(255, 184, 87).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeCoin(true)
            .setComponentKey(SaltedFishPlayerComponent.KEY)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    /**
     * 皮革噶的 - 平民阵营
     * - 被动：模型变成一头猪
     * - 技能（G）：消耗 150 金币进入疯魔模式 30 秒，开启直觉并获得速度 III，
     * 期间播放神秘追杀音效（Dream带带带！）
     */
    public static SRERole LEATHER_PIG = TMMRoles.registerRole(
            new EggRole(LEATHER_PIG_ID, new Color(255, 158, 170).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
                    // registerRole 在此刻读取 componentKey 填 TMMRoles.COMPONENT_KEYS，
                    // 放到链式调用后面会漏掉，开局/结算的 clear()+sync() 就不会覆盖本组件
                    .setComponentKey(
                            org.agmas.noellesroles.game.roles.innocence.leather_pig.LeatherPigPlayerComponent.KEY))
            .setCanSeeCoin(true)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000)
            .setCanBeRandomedByOtherRoles(false);

    // 忍者
    public static final SRERole NINJA = TMMRoles.registerRole(
            new NinjaRole(
                    NINJA_ID,
                    new Color(44, 44, 44).getRGB(),
                    false, // 杀手阵营
                    true, // 有杀手能力
                    SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE,
                    true)
                    .setComponentKey(ModComponents.NINJA)
                    .setCanSeeCoin(true)
                    .setDefaultMax(1));

    /**
     * 怀旧者（杀手阵营）。
     * - 处于「里世界」时：视角灰白，对所有阵营隐身、奔跑无声无粒子、不可被看见/听见/攻击；
     * 但身处里世界无法击杀任何人，只能潜行/开锁/侦察。
     * - 商店仅出售撬锁器与刀。
     * - 当场上仅剩怀旧者一名杀手时，里世界崩塌，现身为普通杀手并可正常击杀。
     */
    public static SRERole NOSTALGIST = TMMRoles.registerRole(new NostalgistRole(
            NOSTALGIST_ID, // 角色 ID
            new Color(150, 160, 170).getRGB(), // 灰白色 - 代表里世界
            false, // isInnocent = 杀手阵营
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺
            true // 隐藏计分板
    ).addEffect(new MobEffectInstance(ModEffects.NO_COLLIDE, 99999, 0, false, false, false)))
            .setComponentKey(ModComponents.NOSTALGIST).setCanSeeCoin(true)
            .setCanBeRandomedByOtherRoles(false).setDefaultMax(1).setDefaultEnableChance(2500);

    public static SRERole WRAITH_ASSASSIN = TMMRoles.registerRole(new WraithAssassinRole(
            WRAITH_ASSASSIN_ID,
            new Color(49, 91, 124).getRGB(),
            false,
            true,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true))
            .setComponentKey(ModComponents.WRAITH_ASSASSIN)
            .setCanSeeCoin(true)
            .setCanUseInstinctAndNightVision(true)
            .setCanSeeTeammateKillerRole(true)
            .setCanBeRandomedByOtherRoles(false)
            .setDefaultMax(1)
            .setDefaultEnableChance(2500)
            .setDefaultEnableNeededPlayerCount(12);

    public static SRERole DELAYER = TMMRoles.registerRole(new NormalRole(
            DELAYER_ID,
            new Color(100, 100, 200).getRGB(), // 淡蓝紫色
            false, // 非乘客阵营
            true, // 有杀手能力
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE, // 无限冲刺 / 疲劳
            true // 隐藏计分板
    )).setComponentKey(ModComponents.DELAYER).setCanSeeCoin(true).setDefaultMax(1).setDefaultEnableChance(8000);
    public static SRERole ELF = TMMRoles.registerRole(
            new NormalRole(ELF_ID, new Color(106, 255, 179).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setVigilanteTeam(true).setCanSeeCoin(true).setCanPickUpRevolver(false).setCanAutoAddMoney(true)
            .setSpecialVigilante(true).setDefaultMax(1).setDefaultEnableChance(7000)
            .setRefreshableSpecialVigilante(1000, true);
    public static final ResourceLocation GUARD_ID = Noellesroles.id("guard");
    public static SRERole GUARD = TMMRoles.registerRole(
            new NormalRole(GUARD_ID, new Color(170, 170, 170).getRGB(), true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
                @Override
                public java.util.function.Predicate<net.minecraft.world.item.Item> cantPickupItem(
                        net.minecraft.world.entity.player.Player player) {
                    return item -> {
                        // 检查是否是左轮手枪或巡警手枪
                        if (item == io.wifi.starrailexpress.index.TMMItems.REVOLVER
                                || item == org.agmas.noellesroles.init.ModItems.PATROLLER_REVOLVER) {
                            // 检查主手、副手和背包是否有警棍
                            if (player.getMainHandItem()
                                    .is(org.agmas.noellesroles.init.ModItems.BATON))
                                return true;
                            if (player.getOffhandItem()
                                    .is(org.agmas.noellesroles.init.ModItems.BATON))
                                return true;
                            for (int i = 0; i < player.getInventory()
                                    .getContainerSize(); i++) {
                                if (player.getInventory().getItem(i).is(
                                        org.agmas.noellesroles.init.ModItems.BATON))
                                    return true;
                            }
                            return false;
                        }
                        return false;
                    };
                }
            }).setCanSeeCoin(true).setCanPickUpRevolver(true).setCanAutoAddMoney(false)
            .setVigilanteTeam(true)
            .setDefaultMax(1).setCanSetSpawnInfoInConfig(false);

    /**
     * 警卫角色
     * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 技能：完成两个任务后可获得一把左轮手枪（单局仅触发一次）
     * - 商店：可花费150金币购买手铐
     */
    public static SRERole SHERIFF = TMMRoles.registerRole(
            new NormalRole(SHERIFF_ID, 0x1B8AE5, true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
                private final java.util.Map<java.util.UUID, Integer> sheriffTaskCounts = new java.util.HashMap<>();
                private final java.util.Set<java.util.UUID> sheriffHasReceivedRevolver = new java.util.HashSet<>();

                @Override
                public void onFinishQuest(Player player, String quest) {
                    java.util.UUID playerUuid = player.getUUID();
                    // 如果已经获得过左轮手枪，不再处理
                    if (sheriffHasReceivedRevolver.contains(playerUuid))
                        return;

                    int count = sheriffTaskCounts.getOrDefault(playerUuid, 0) + 1;
                    sheriffTaskCounts.put(playerUuid, count);

                    if (count >= 2) {
                        sheriffHasReceivedRevolver.add(playerUuid);
                        player.addItem(io.wifi.starrailexpress.index.TMMItems.REVOLVER
                                .getDefaultInstance().copy());
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "message.noellesroles.sheriff.revolver_received")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                                true);
                    }
                }

                @Override
                public void onInit(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
                    // 每局开始时重置任务计数
                    sheriffTaskCounts.remove(player.getUUID());
                    sheriffHasReceivedRevolver.remove(player.getUUID());
                }

                @Override
                public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
                        net.minecraft.resources.ResourceLocation deathReason) {
                    // 未解锁左轮手枪前死亡：在死亡位置掉落一把左轮手枪
                    dropUnearnedRevolverOnDeath(victim, sheriffHasReceivedRevolver);
                    super.onDeath(victim, spawnBody, killer, deathReason);
                }
            })
            .setVigilanteTeam(true).setCanPickUpRevolver(true).setCanAutoAddMoney(true);

    /**
     * 鬼眼·杨间（警长阵营）。完成两个任务后获得左轮手枪。
     * - 被动·鬼眼：每隔 16 秒自动扫描周身 20 格，短暂（2 秒）以白色直觉显示所有玩家轮廓。
     * - 主动·诡域（冷却 70 秒）：在脚下展开半径 12 格、持续 6 秒的领域。领域内所有人减速（缓慢 II）；
     * 领域内杀手无法开启透视；除杨间外所有人失明并陷入黑暗。
     */
    public static SRERole GHOST_EYE = TMMRoles.registerRole(
            new EggRole(GHOST_EYE_ID, new Color(132, 196, 200).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
                private final java.util.Map<java.util.UUID, Integer> taskCounts = new java.util.HashMap<>();
                private final java.util.Set<java.util.UUID> hasReceivedRevolver = new java.util.HashSet<>();

                @Override
                public void onFinishQuest(Player player, String quest) {
                    java.util.UUID playerUuid = player.getUUID();
                    // 如果已经获得过左轮手枪，不再处理
                    if (hasReceivedRevolver.contains(playerUuid))
                        return;
                    int count = taskCounts.getOrDefault(playerUuid, 0) + 1;
                    taskCounts.put(playerUuid, count);
                    if (count >= 2) {
                        hasReceivedRevolver.add(playerUuid);
                        player.addItem(io.wifi.starrailexpress.index.TMMItems.REVOLVER
                                .getDefaultInstance().copy());
                        player.displayClientMessage(
                                net.minecraft.network.chat.Component.translatable(
                                        "message.noellesroles.ghost_eye.revolver_received")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD),
                                true);
                    }
                }

                @Override
                public void onInit(net.minecraft.server.MinecraftServer server, ServerPlayer player) {
                    // 每局开始时重置任务计数
                    taskCounts.remove(player.getUUID());
                    hasReceivedRevolver.remove(player.getUUID());
                }

                @Override
                public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
                        net.minecraft.resources.ResourceLocation deathReason) {
                    // 未解锁左轮手枪前死亡：在死亡位置掉落一把左轮手枪
                    dropUnearnedRevolverOnDeath(victim, hasReceivedRevolver);
                    super.onDeath(victim, spawnBody, killer, deathReason);
                }
            })
            .setVigilanteTeam(true).setCanPickUpRevolver(true).setCanAutoAddMoney(true)
            .setComponentKey(ModComponents.GHOST_EYE)
            .setSpecialVigilante(true).setDefaultMax(1).setDefaultEnableChance(5000)
            .setDefaultEnableNeededPlayerCount(8);

    /**
     * 警长 / 鬼眼·杨间 共用：在尚未通过完成两个任务解锁左轮手枪、且身上也没有左轮手枪时死亡，
     * 于死亡位置掉落一把左轮手枪。
     */
    private static void dropUnearnedRevolverOnDeath(Player victim, java.util.Set<java.util.UUID> received) {
        if (!(victim instanceof ServerPlayer sp))
            return;
        if (received.contains(sp.getUUID()))
            return;
        for (ItemStack stack : sp.getInventory().items) {
            if (stack.is(io.wifi.starrailexpress.index.TMMItems.REVOLVER))
                return;
        }
        sp.drop(io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance().copy(), false);
    }

    public static SRERole WIND_YAOSE = TMMRoles.registerRole(
            new ExtraEffectRole(WIND_YAOSE_ID, new Color(127, 231, 255).getRGB(),
                    false, false, SRERole.MoodType.FAKE,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false).addEffect(
                            new MobEffectInstance(
                                    MobEffects.INVISIBILITY,
                                    30 * 20, // 持续时间 60s（tick）
                                    1, // 等级（0 = 速度 I）
                                    true, // ambient（环境效果，如信标）
                                    false, // showParticles（显示粒子）
                                    true // showIcon（显示图标）
                            )))
            .setCanSeeCoin(true).setCanPickUpRevolver(false).setNeutrals(true).setCanUseInstinctAndNightVision(true)
            .setNeutralForKiller(true);
    public static SRERole CHEF = TMMRoles.registerRole(
            new ChefRole(CHEF_ID, new Color(229, 255, 0).getRGB(),
                    true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeCoin(true).setCanPickUpRevolver(true)
            .setComponentKey(FoodDrinkGlowComponent.KEY);
    public static SRERole CAKE_MAKER = TMMRoles.registerRole(
            new CakeMakerRole(CAKE_MAKER_ID, new Color(244, 173, 193).getRGB(), true, false,
                    SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeCoin(true).setCanPickUpRevolver(true).setDefaultEnableNeededPlayerCount(8);
    // 冒险家
    public static SRERole ADVENTURER = TMMRoles.registerRole(
            new AdventurerRole(ADVENTURER_ID, new Color(34, 139, 34).getRGB(), true, false,
                    SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime() * 2, false))
            .setCanSeeCoin(true).setCanPickUpRevolver(true).setCanJumpManhole(true).setCanAcrossFog(true)
            .setComponentKey(AdventurerPlayerComponent.KEY).setDefaultEnableNeededPlayerCount(6)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.TRAP).setDefaultMax(1)
            .setCanBeRandomedByOtherRoles(false);
    // 红尘客
    public static SRERole WAYFARER = TMMRoles.registerRole(
            new NormalRole(WAYFARER_ID, new Color(255, 54, 105).getRGB(),
                    false, false, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, false))
            .setCanSeeCoin(true).setNeutrals(true).setCanPickUpRevolver(false)
            .setComponentKey(ModComponents.WAYFARER).setCanUseInstinctAndNightVision(false)
            .setCanSeeBodyDeathReason(true)
            .setDefaultEnableChance(2500).setDefaultEnableNeededPlayerCount(10);
    public static final ResourceLocation CUCKOO_ID = Noellesroles.id("cuckoo");

    public static SRERole CUCKOO = TMMRoles.registerRole(
            new NormalRole(CUCKOO_ID, new Color(200, 170, 60).getRGB(),
                    false, false, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setCanSeeCoin(true).setComponentKey(ModComponents.CUCKOO).setCanBeRandomedByOtherRoles(false)
            .setCanUseInstinctAndNightVision(true).setNeutrals(true).setDefaultMax(1).setDefaultEnableChance(4500);
    public static SRERole JESTER = TMMRoles
            .registerRole(new NormalRole(JESTER_ID, new Color(186, 85, 211).getRGB(), false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true) {
                @Override
                public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
                    return SRE.id("textures/entity/custom_psycho/jester.png");
                };
            })
            .setNeutralForKiller(true).setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setPassiveIncome(true)
            .setServerGameTickEvent((sp, cca) -> JesterHandler.handler(sp, cca))
            .setDefaultMax(1);
    public static SRERole CONDUCTOR = TMMRoles
            .registerRole(new NormalRole(CONDUCTOR_ID, new Color(184, 134, 11).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setDefaultMax(1);
    public static SRERole BARTENDER = TMMRoles
            .registerRole(new NormalRole(BARTENDER_ID, new Color(217, 241, 240).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setComponentKey(FoodDrinkGlowComponent.KEY).setDefaultMax(1);
    public static SRERole NOISEMAKER = TMMRoles
            .registerRole(new NormalRole(NOISEMAKER_ID, new Color(200, 255, 0).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false) {
                @Override
                public void onDeathWithBody(Player victim, boolean spawnBody, @Nullable Player killer,
                        ResourceLocation deathReason, PlayerBodyEntity playerBodyEntity) {
                    super.onDeathWithBody(victim, spawnBody, killer, deathReason, playerBodyEntity);
                    SREGameWorldComponent gameWorldComponent = (SREGameWorldComponent) SREGameWorldComponent.KEY
                            .get(victim.level());
                    if (gameWorldComponent.isRole(victim, ModRoles.NOISEMAKER)) {
                        playerBodyEntity.addEffect(
                                new MobEffectInstance(MobEffects.GLOWING, 20 * 60, 0));
                        var serverLevel = victim.level();
                        for (Player p : serverLevel.players()) {
                            if (p.isSpectator()) {
                                continue;
                            }
                            if (!GameUtils.isPlayerAliveAndSurvival(p)) {
                                continue;
                            }
                            serverLevel.playSound(
                                    p,
                                    victim.getX(),
                                    victim.getY(),
                                    victim.getZ(),
                                    SoundEvents.WITHER_DEATH,
                                    SoundSource.MASTER,
                                    3.0F,
                                    1.0F);
                        }
                    }
                }
            }).setDefaultMax(1);
    public static SRERole AWESOME_BINGLUS = TMMRoles
            .registerRole(new NormalRole(AWESOME_BINGLUS_ID, new Color(155, 255, 168).getRGB(), true, false,
                    SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setDefaultMax(1);
    public static SRERole VOODOO = TMMRoles
            .registerRole(new NormalRole(VOODOO_ID, new Color(128, 114, 253).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)
                    .setComponentKey(VoodooPlayerComponent.KEY))
            .setDefaultMax(1);
    public static SRERole RECALLER = TMMRoles
            .registerRole(new NormalRole(RECALLER_ID, new Color(135, 206, 235).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)
                    .setComponentKey(RecallerPlayerComponent.KEY))
            .setDefaultMax(1);
    public static SRERole BETTER_VIGILANTE = TMMRoles
            .registerRole(new NormalRole(BETTER_VIGILANTE_ID, new Color(0, 255, 255).getRGB(), true, false,
                    SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false)
                    .setComponentKey(BetterVigilantePlayerComponent.KEY))
            .setCanBeRandomedByOtherRoles(false).setDefaultMax(0);
    public static SRERole BROADCASTER = TMMRoles
            .registerRole(new NormalRole(BROADCASTER_ID, new Color(0, 255, 0).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), true)
                    .setComponentKey(BroadcasterPlayerComponent.KEY))
            .setDefaultMax(1);
    public static SRERole GHOST = TMMRoles
            .registerRole(new NormalRole(GHOST_ID, new Color(200, 200, 200).getRGB(), true, false,
                    SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
            .setDefaultMax(1).setHiddenForRoleRotation(true);
    public static SRERole DOCTOR = TMMRoles
            .registerRole(new NormalRole(DOCTOR_ID, new Color(30, 144, 255).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSetSpawnInfoInConfig(true).setDefaultMax(0);
    public static SRERole ATTENDANT = TMMRoles
            .registerRole(new NormalRole(ATTENDANT_ID, (new Color(198, 185, 36)).getRGB(),
                    true, false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(),
                    false));
    public static SRERole PATROLLER = TMMRoles
            .registerRole(new NormalRole(PATROLLER_ID, 0x2F6BFF, true, false, SRERole.MoodType.REAL,
                    io.wifi.starrailexpress.game.GameConstants.getInTicks(0, 10), false)
                    .setVigilanteTeam(true).setComponentKey(PatrollerPlayerComponent.KEY))
            .setCanPickUpRevolver(true).setSpecialVigilante(true).setDefaultMax(1)
            .setDefaultEnableChance(8000)
            .setRefreshableSpecialVigilante(2000, true);

    /**
     * 里昂（警长阵营）。
     * - 警长阵营（isInnocent = true, setVigilanteTeam = true），不能使用杀手能力。
     * - 开局拥有一把左轮手枪（{@link io.wifi.starrailexpress.index.TMMItems#REVOLVER}，死亡时掉落）。
     * - 格斗体术（按 G 触发，见 {@link org.agmas.noellesroles.AbilityHandler}）：向面前玩家猛踹一脚，
     * 造成较远击退与减速。
     * - 被动「幸存之人」（见
     * {@link org.agmas.noellesroles.game.roles.vigilante.leon.LeonPlayerComponent}）：
     * 场上剩 6 人时获得蓝色草药（刷新格斗体术），剩 3 人时获得红色草药（套盾，不可叠加）。
     * - 不与远征队等任何修饰符共存（见 {@link org.agmas.noellesroles.game.modifier.NRModifiers}）。
     */
    public static SRERole LEON = TMMRoles
            .registerRole(new NormalRole(LEON_ID, 0x2E6FB0, true, false, SRERole.MoodType.REAL,
                    TMMRoles.CIVILIAN.getMaxSprintTime(), false)
                    .setVigilanteTeam(true)
                    .setComponentKey(
                            org.agmas.noellesroles.game.roles.vigilante.leon.LeonPlayerComponent.KEY))
            .setCanPickUpRevolver(true).setDefaultMax(1).setDefaultEnableChance(5000)
            .setDefaultEnableNeededPlayerCount(12)
            .setSpecialVigilante(true);

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
    public static SRERole RESCUER = TMMRoles.registerRole(new NormalRole(
            RESCUER_ID, // 角色 ID
            new Color(255, 140, 0).getRGB(), // 橙色 - 代表救援
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
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
    public static SRERole FIREFIGHTER = TMMRoles.registerRole(new NormalRole(
            FIREFIGHTER_ID, // 角色 ID
            new Color(255, 69, 0).getRGB(), // 红橙色 - 代表消防
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
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
    public static SRERole ACCOUNTANT = TMMRoles.registerRole(new NormalRole(
            ACCOUNTANT_ID, // 角色 ID
            new Color(46, 139, 87).getRGB(), // 海绿色 - 代表会计的稳重与秩序
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setComponentKey(AccountantPlayerComponent.KEY);

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
    public static SRERole ALCHEMIST = TMMRoles.registerRole(new NormalRole(
            ALCHEMIST_ID, // 角色 ID
            new Color(128, 0, 128).getRGB(), // 紫色 - 代表药剂
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setComponentKey(AlchemistPlayerComponent.KEY);

    /**
     * 潜水员角色
     * - 属于平民阵营 (isInnocent = true)
     * - 无限体力 (Integer.MAX_VALUE)
     * - 自带水下呼吸效果
     * - 可以购买潜水头盔(125金币)和潜水靴(225金币)
     * - 潜水头盔：提供水下呼吸和海豚恩惠1，可以丢出给其他人，渲染为钻石头盔
     * - 潜水靴：自带深海探索者3，渲染为金靴子
     * - 技能：按技能键脱掉身上的装备
     */
    // 潜水员角色 - 乘客阵营
    public static SRERole DIVER = TMMRoles.registerRole(new ExtraEffectRole(
            DIVER_ID, // 角色 ID
            new Color(0, 105, 148).getRGB(), // 深青色 - 代表海洋
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            Integer.MAX_VALUE, // 无限冲刺时间
            false // 不隐藏计分板
    ).addEffect(
            new MobEffectInstance(
                    MobEffects.WATER_BREATHING,
                    30 * 20, // 持续时间 60s（tick）
                    0, // 等级（水下呼吸 I）
                    true, // ambient（环境效果，如信标）
                    false, // showParticles（显示粒子）
                    true // showIcon（显示图标）
            )))
            .setCanSeeCoin(true).setCanBeRandomedByOtherRoles(false).setComponentKey(DiverPlayerComponent.KEY)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.UNDERWATER).setDefaultMax(0);

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
    public static SRERole SWAST = TMMRoles.registerRole(new NormalRole(
            SWAST_ID, // 角色 ID
            new Color(0, 191, 255).getRGB(), // 深天蓝色 - 代表特警的专业与冷静
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setVigilanteTeam(true).setCanPickUpRevolver(false)
            .setServerGameTickEvent((player, gameComponent) -> {
                org.agmas.noellesroles.game.roles.vigilante.swast.SwastTickHandler.serverTick(player,
                        gameComponent);
            }).setSpecialVigilante(true).setSpecialMapRole(SRERole.SpecialMapRoleMap.BIGMAP)
            .setDefaultMax(1).setDefaultEnableChance(7000);

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
    public static SRERole MARTIAL_ARTS_INSTRUCTOR = TMMRoles.registerRole(new NormalRole(
            MARTIAL_ARTS_INSTRUCTOR_ID, // 角色 ID
            new Color(255, 170, 0).getRGB(), // 琥珀金 - 代表武术的荣耀与威严
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            (int) (TMMRoles.CIVILIAN.getMaxSprintTime() * 2.5), // 2.5倍平民体力
            false // 不隐藏计分板
    )).setCanSeeCoin(true).setVigilanteTeam(true).setCanPickUpRevolver(false)
            .setSpecialVigilante(true).setDefaultMax(1).setDefaultEnableChance(6000);

    /**
     * 海王角色
     * - 属于警长阵营 (isInnocent = true, setVigilanteTeam = true)
     * - 仅在areas14地图生成
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 无限体力
     * - 在计分板上显示
     * - 无法捡起左轮手枪
     * - 开局物品：忠诚3的三叉戟
     * - 专属商店：普通三叉戟(150金币)
     */
    // 海王角色 - 警长阵营
    public static SRERole SEA_KING = TMMRoles.registerRole(new ExtraEffectRole(
            SEA_KING_ID, // 角色 ID
            new Color(0, 180, 216).getRGB(), // 海洋蓝 - 代表海王的海洋力量
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            Integer.MAX_VALUE, // 无限体力
            false // 不隐藏计分板
    ).addEffect(
            new MobEffectInstance(
                    MobEffects.WATER_BREATHING,
                    30 * 20, // 持续时间 60s（tick）
                    0, // 等级（水下呼吸 I）
                    true, // ambient（环境效果，如信标）
                    false, // showParticles（显示粒子）
                    true // showIcon（显示图标）
            )))
            .setCanSeeCoin(true).setVigilanteTeam(true).setCanPickUpRevolver(false)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.UNDERWATER).setDefaultMax(1);

    /**
     * 水鬼角色
     *
     * 杀手阵营，假心情，无限体力
     *
     * 武器：激流2三叉戟（Mixin实现）
     *
     * 商店：可花费100金币购买开锁器，150金币购买下雨
     *
     * 技能：按下技能键获得10秒海豚的恩惠1，冷却40秒
     *
     * 被动：在非水中环境超过90秒时会死亡（死因：干涸而死）
     */
    public static SRERole WATER_GHOST = TMMRoles.registerRole(new ExtraEffectRole(
            WATER_GHOST_ID, // 角色 ID
            new Color(30, 100, 180).getRGB(), // 深蓝色 - 代表水鬼的水属性
            false, // isInnocent = 非乘客阵营（杀手）
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限体力
            true // 隐藏计分板
    ).addEffect(
            new MobEffectInstance(
                    MobEffects.WATER_BREATHING,
                    30 * 20, // 持续时间 60s（tick）
                    0, // 等级（水下呼吸 I）
                    true, // ambient（环境效果，如信标）
                    false, // showParticles（显示粒子）
                    true // showIcon（显示图标）
            )))
            .setComponentKey(ModComponents.WATER_GHOST).setCanSeeCoin(true)
            .setCanBeRandomedByOtherRoles(false).setSpecialMapRole(SRERole.SpecialMapRoleMap.UNDERWATER)
            .setDefaultMax(1);

    // 杀手阵营角色
    public static SRERole CLEANER = TMMRoles
            .registerRole(new NormalRole(CLEANER_ID, new Color(255, 1, 124).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setCanPickUpRevolver(true));
    public static SRERole MORPHLING = TMMRoles
            .registerRole(new NormalRole(MORPHLING_ID, new Color(220, 20, 60).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(MorphlingPlayerComponent.KEY))
            .setDefaultMax(1);

    /**
     * 静语者角色 - 杀手阵营
     * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
     * - 假心情 (MoodType.FAKE)
     * - 无限体力 (Integer.MAX_VALUE)
     * - 计分板隐藏
     * - 技能：打开技能页面点击玩家头像，使目标进入静语阶段
     * - 第一阶段（禁言）：45秒 voice_silence + chat_ban
     * - 第二阶段（求助）：30秒 chat_ban，其它玩家可右键解救
     * - 第三阶段（惩罚）：清空心情+体力，全体静语者+120金币
     */
    public static SRERole SILENCER = TMMRoles
            .registerRole(new NormalRole(SILENCER_ID, new Color(160, 40, 100).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(
                            org.agmas.noellesroles.game.roles.killer.silencer.SilencerPlayerComponent.KEY))
            .setDefaultEnableChance(6000).setDefaultMax(1);

    public static SRERole PARTY_KILLER = TMMRoles.registerRole(new NormalRole(PARTY_KILLER_ID,
            new Color(255, 105, 180).getRGB(), // 派对色
            false, // 非乘客（杀手）
            true, // 有杀手功能
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true)).setComponentKey(ModComponents.PARTY).setCanSeeCoin(true).setDefaultMax(1);
    public static SRERole MANIPULATOR = TMMRoles
            .registerRole(new ManipulatorRole(MANIPULATOR_ID, new Color(90, 20, 61).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(ManipulatorPlayerComponent.KEY))
            .setComponentKey(ModComponents.MANIPULATOR).setDefaultMax(0);
    public static SRERole PHANTOM = TMMRoles
            .registerRole(new NormalRole(PHANTOM_ID, new Color(80, 5, 5, 192).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setComponentKey(ModComponents.ABILITY).setDefaultMax(1);
    public static SRERole SWAPPER = TMMRoles
            .registerRole(new NormalRole(SWAPPER_ID, new Color(255, 0, 255).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setComponentKey(ModComponents.SWAPPER).setDefaultMax(1);
    public static SRERole EXECUTIONER = TMMRoles
            .registerRole(new NormalRole(EXECUTIONER_ID, new Color(74, 27, 5).getRGB(),
                    false, true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true) {
                @Override

                public Item getPsychoItem() {
                    return TMMItems.REVOLVER;
                };
            }
                    .setComponentKey(ExecutionerPlayerComponent.KEY))
            .setDefaultMax(1);
    public static SRERole GAMBLER = TMMRoles
            .registerRole(new GamblerRole(GAMBLER_ID, new Color(72, 61, 139).getRGB(), false,
                    false, SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime(), true))
            .setCanPickUpRevolver(true).setComponentKey(GamblerPlayerComponent.KEY).setNeutrals(true)
            .setDefaultMax(1).setHiddenForRoleRotation(true);
    public static SRERole TAMER = TMMRoles
            .registerRole(new NormalRole(TAMER_ID, new Color(210, 180, 140).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setNeutrals(false).setCanSeeCoin(true)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.HORSE).setDefaultMax(0).setCanBeRandomedByOtherRoles(false);
    public static SRERole HUNTER = TMMRoles
            .registerRole(new NormalRole(HUNTER_ID, new Color(160, 82, 45).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setNeutrals(false).setCanSeeCoin(true)
            .setSpecialMapRole(SRERole.SpecialMapRoleMap.HORSE).setDefaultMax(1).setCanBeRandomedByOtherRoles(false)
            .setComponentKey(org.agmas.noellesroles.component.ModComponents.HUNTER);
    public static SRERole POISONER = TMMRoles
            .registerRole(new NormalRole(POISONER_ID, (new Color(115, 0, 57)).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setCanBePoisoned(false)
            .setDefaultEnableChance(5500).setDefaultEnableNeededPlayerCount(12);

    // 疫使与毒师互斥生成
    public static SRERole INFECTED = TMMRoles
            .registerRole(new NormalRole(INFECTED_ID, new Color(66, 181, 0).getRGB(), false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setNeutralForKiller(true)
            .setCanUseInstinctAndNightVision(true)
            .setDefaultMax(1)
            .setCanSeeCoin(true)
            .setCanBeRandomedByOtherRoles(false)
            .setDefaultEnableChance(5000).setDefaultEnableNeededPlayerCount(12);

    /**
     * 葬仪角色 - 杀手方中立阵营
     * - 杀手方中立阵营 (isInnocent = false, canUseKiller = false, setNeutralForKiller =
     * true)
     * - 假心情系统
     * - 无限冲刺时间
     * - 在计分板上隐藏
     *
     * 技能（蹲下按技能键切换模式）：
     * - 曳柩：对尸体按下技能键，可以拖动尸体，再次按下放下并进入45秒冷却
     * - 丧钟：5格半径内玩家体力减少60%，进入60秒冷却
     * - 清洗：消除3格半径内血液，进入45秒冷却
     *
     * 尸匠：拥有造尸能力（搬运KinsWathe中造尸怪bodymaker的技能）
     * - 造出来的尸体物品栏为空
     *
     * 被动-引渡：杀手/杀手方中立/魔术师死亡时向所有杀手、杀手方中立和魔术师广播
     *
     * 商店：乘务员钥匙(100金币)、裹尸袋(150金币)、血瓶(75金币)
     */
    public static SRERole MORTICIAN_BODYMAKER = TMMRoles
            .registerRole(new NormalRole(MORTICIAN_BODYMAKER_ID, new Color(180, 160, 220).getRGB(), false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setComponentKey(ModComponents.MORTICIAN_BODYMAKER)
            .setNeutralForKiller(true)
            .setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true)
            .setDefaultMax(1);

    public static SRERole CUPID = TMMRoles
            .registerRole(new NormalRole(CUPID_ID, new Color(255, 105, 180).getRGB(), false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true))
            .setComponentKey(ModComponents.CUPID)
            .setNeutralForKiller(true)
            .setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000).setDefaultEnableNeededPlayerCount(8);

    public static SRERole SPELLBREAKER = TMMRoles
            .registerRole(new NormalRole(SPELLBREAKER_ID, (new Color(132, 46, 170)).getRGB(), false,
                    true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(SpellbreakerPlayerComponent.KEY))
            .setCanSeeCoin(true).setDefaultMax(1);

    public static SRERole INSANE_KILLER = TMMRoles
            .registerRole(new NormalRole(
                    INSANE_KILLER_ID,
                    new Color(255, 0, 0, 192).getRGB(), false, true, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true).setComponentKey(InsaneKillerPlayerComponent.KEY));

    // 中立阵营角色
    public static SRERole COMMANDER = TMMRoles.registerRole(
            new NormalRole(COMMANDER_ID, new Color(185, 122, 87).getRGB(),
                    false, false, SRERole.MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setCanSeeCoin(true).setCanPickUpRevolver(false).setNeutrals(true).setNeutralForKiller(true)
            .setCanUseInstinctAndNightVision(true);
    public static SRERole VULTURE = TMMRoles
            .registerRole(new NormalRole(VULTURE_ID, new Color(210, 105, 30).getRGB(), false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(VulturePlayerComponent.KEY))
            .setNeutralForKiller(true).setCanSeeTeammateKillerRole(false).setCanSeeBodyDeathReason(true)
            .setDefaultEnableNeededPlayerCount(8).setDefaultMax(1);

    /**
     * 鹈鹕角色 (Pelican)
     * - 独立胜利中立阵营 (isInnocent = false, canUseKiller = false, setNeutrals(true))
     * - 不帮助杀手阵营 (setNeutralForKiller(false))
     * - 技能：吞噬面前2.15格内的存活玩家进肚子里
     * - 蹲下+技能：释放最后吞噬的玩家
     * - 胜利条件：吞噬开局玩家数80%的玩家
     * - 本能透视范围：25格
     * - 只在12人及以上对局中出现（可配置）
     * - 刷新几率25%（可配置）
     */
    public static SRERole PELICAN = TMMRoles
            .registerRole(new NormalRole(PELICAN_ID, new Color(111, 138, 36).getRGB(), false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(
                            org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent.KEY))
            .setNeutrals(true).setNeutralForKiller(false).setCanSeeTeammateKillerRole(false).setDefaultMax(1)
            .setCanUseInstinctAndNightVision(true).setCanSeeCoin(true).setCanPickUpRevolver(false)
            .setCanBeRandomedByOtherRoles(false).setDefaultEnableNeededPlayerCount(16)
            .setDefaultEnableChance(4000);

    // ==================== Mafia 家族角色 归到彩蛋里 ====================
    public static SRERole GODFATHER = TMMRoles
            .registerRole(new EggRole(GODFATHER_ID, new Color(199, 21, 133).getRGB(), false,
                    false, SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true))
            .setNeutrals(true).setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true).setOccupiedRoleCount(3).setDefaultMax(1)
            .setCanBeRandomedByOtherRoles(false)
            .setMafiaTeam(true).setDefaultEnableNeededPlayerCount(18).setDefaultEnableChance(2000);
    public static SRERole MAFIOSO = TMMRoles
            .registerRole(new EggRole(MAFIOSO_ID, new Color(218, 112, 214).getRGB(), false,
                    false, SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true))
            .setNeutrals(true).setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false).setMafiaTeam(true);
    public static SRERole JANITOR = TMMRoles
            .registerRole(new EggRole(JANITOR_ID, new Color(255, 105, 180).getRGB(), false,
                    false, SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true))
            .setNeutrals(true).setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false).setMafiaTeam(true);
    public static SRERole NUTRITIONIST = TMMRoles
            .registerRole(new EggRole(NUTRITIONIST_ID, new Color(50, 205, 50).getRGB(), false,
                    false, SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true))
            .setNeutrals(true).setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false).setMafiaTeam(true);
    public static SRERole PARASOL = TMMRoles
            .registerRole(new EggRole(PARASOL_ID, new Color(0, 139, 139).getRGB(), false,
                    false, SRERole.MoodType.FAKE, TMMRoles.CIVILIAN.getMaxSprintTime() * 2, true))
            .setNeutrals(true).setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true).setDefaultMax(0).setCanBeRandomedByOtherRoles(false).setMafiaTeam(true);

    // 验尸官
    public static SRERole CORONER = TMMRoles
            .registerRole(new NormalRole(CORONER_ID, new Color(122, 122, 122).getRGB(), true,
                    false, SRERole.MoodType.REAL, TMMRoles.CIVILIAN.getMaxSprintTime(), false))
            .setCanSeeBodyDeathReason(true).setCanSeeBodyRoleInfo(true).setCanSeeBodyItems(true)
            .setDefaultMax(1).setCanSeeBodyKiller(false);

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
    public static SRERole AVENGER = TMMRoles.registerRole(new NormalRole(
            AVENGER_ID, // 角色 ID
            new Color(255, 140, 0).getRGB(), // 橙黄色 - 代表复仇的火焰
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(AvengerPlayerComponent.KEY));

    /**
     * 捣蛋鬼角色
     * - 不属于乘客阵营 (isInnocent = false)
     * - 不能使用杀手能力 (canUseKiller = false)，但有专属商店
     * - 假心情系统
     * - 标准冲刺时间
     * - 在计分板上隐藏
     * - 被动技能：每10秒获取50金币
     * - 专属商店：空包弹(100)、烟雾弹(300)、撬锁器(50)、关灯(200)
     * - 胜利条件：与杀手同胜
     */
    public static SRERole PRANKSTER = TMMRoles.registerRole(new NormalRole(
            PRANKSTER_ID, // 角色 ID
            new Color(176, 196, 222).getRGB(), // 灰色 - 代表捣蛋鬼的隐匿
            false, // isInnocent = 非乘客阵营
            false, // canUseKiller = 无杀手能力（使用专属商店）
            SRERole.MoodType.FAKE, // 假心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            true // 隐藏计分板
    )).setNeutralForKiller(true).setCanSeeTeammateKillerRole(false);;

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
    public static SRERole ENGINEER = TMMRoles.registerRole(new NormalRole(
            ENGINEER_ID, // 角色 ID
            new Color(255, 179, 71).getRGB(), // 琥珀橙 - 代表工程帽/工具
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ));;

    /**
     * 斗士角色
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
    public static SRERole FIGHTER = TMMRoles.registerRole(new NormalRole(
            FIGHTER_ID, // 角色 ID
            new Color(205, 92, 92).getRGB(), // 猩红色 - 代表热血/格斗
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(BoxerPlayerComponent.KEY));;

    /**
     * 探员角色
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
    public static SRERole AGENT = TMMRoles.registerRole(new NormalRole(
            AGENT_ID, // 角色 ID
            new Color(205, 133, 63).getRGB(), // 棕色 - 代表侦探风衣
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(AgentPlayerComponent.KEY));;

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
    public static SRERole ATHLETE = TMMRoles.registerRole(new NormalRole(
            ATHLETE_ID, // 角色 ID
            new Color(65, 105, 225).getRGB(), // 天蓝色 - 代表运动/活力
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            Integer.MAX_VALUE, // 无限冲刺
            false // 不显示计分板
    ));;

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
    public static SRERole SUPERSTAR = TMMRoles.registerRole(new NormalRole(
            SUPERSTAR_ID, // 角色 ID
            new Color(255, 240, 120).getRGB(), // 星辉黄 - 代表明星的聚光灯
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(SuperStarPlayerComponent.KEY));

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
    public static SRERole VETERAN = TMMRoles.registerRole(new NormalRole(
            VETERAN_ID, // 角色 ID
            new Color(85, 107, 47).getRGB(), // 暗橄榄绿 - 代表军装颜色
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ));

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
    public static SRERole SINGER = TMMRoles.registerRole(new NormalRole(
            SINGER_ID, // 角色 ID
            new Color(255, 105, 180).getRGB(), // 热粉色 - 代表音乐与激情
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(SingerPlayerComponent.KEY));

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
    public static SRERole PSYCHOLOGIST = TMMRoles.registerRole(new NormalRole(
            PSYCHOLOGIST_ID, // 角色 ID
            new Color(64, 224, 208).getRGB(), // 青绿色 - 代表心灵治愈
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(PsychologistPlayerComponent.KEY));

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
    public static SRERole PHOTOGRAPHER = TMMRoles.registerRole(new NormalRole(
            PHOTOGRAPHER_ID, // 角色 ID
            new Color(72, 209, 204).getRGB(), // 青石色 - 代表相机镜头
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(PhotographerPlayerComponent.KEY));

    /**
     * 画家角色
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 技能一：绘画灵感 - 触发以下场景时获得画板，每种场景只会给予一次
     * 1. 捡起摄影师丢在地上的照片（exposure:stacked_photographs 或 exposure:photograph）
     * 2. 从地上捡起左轮手枪/巡警手枪
     * 3. 坐着的时间达到40秒
     * - 技能二：求索 - 游戏时间每过4分钟获得一个画板
     * - 技能三：挚友 - 当场上同时存在画家和作家时，同时给予作家和画家一个画板
     */
    public static SRERole PAINTER = TMMRoles.registerRole(new NormalRole(
            PAINTER_ID, // 角色 ID
            new Color(255, 182, 193).getRGB(), // 粉红色 - 代表画家的艺术气息
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不隐藏计分板
    )).setComponentKey(PainterPlayerComponent.KEY).setCanSeeCoin(true).setDefaultMax(1)
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000);

    // 杀手阵营角色
    /**
     * 阴谋家角色
     * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
     * - 可以在商店购买"书页"物品（250金币）
     * - 右键使用书页打开GUI：选择玩家头像，再选择角色
     * - 如果猜测正确，目标玩家40秒后死亡
     * - 猜测错误无惩罚，但书页消耗
     */
    public static SRERole CONSPIRATOR = TMMRoles.registerRole(new NormalRole(
            CONSPIRATOR_ID, // 角色 ID
            new Color(85, 26, 139).getRGB(), // 深紫色 - 代表阴谋与神秘
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 有杀手能力（可以使用地道、杀手聊天）
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true // 隐藏计分板
    ).setComponentKey(ConspiratorPlayerComponent.KEY)).setCanBeRandomedByOtherRoles(false);

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
    public static SRERole TRAPPER = TMMRoles.registerRole(new NormalRole(
            TRAPPER_ID, // 角色 ID
            new Color(180, 30, 20).getRGB(), // 深红色 - 代表陷阱与危险
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true // 隐藏计分板
    ).setComponentKey(TrapperPlayerComponent.KEY));

    /**
     * 炸弹客角色
     * - 属于杀手阵营 (isInnocent = false, canUseKiller = true)
     * - 可以在商店购买炸弹
     * - 炸弹倒计时10秒，前5秒隐形
     * - 右键传递炸弹
     */
    public static SRERole BOMBER = TMMRoles.registerRole(new NormalRole(
            BOMBER_ID, // 角色 ID
            new Color(51, 51, 51).getRGB(), // 黑色/深灰色
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 标准冲刺时间
            true // 隐藏计分板
    ).setComponentKey(BomberPlayerComponent.KEY));

    /**
     * 迷失杀手角色 - 杀手阵营
     * - 杀手阵营 (isInnocent = false, canUseKiller = true)
     * - 默认杀手商店
     * - 与魔术师互斥生成
     * - 不占用杀手位 (setOccupiedRoleCount(0))
     * - 没有杀手透视 (setCanUseInstinct(false))
     * - 杀手本能透视你时框与平民一致，看不到杀手同伙和职业信息 (setCanSeeTeammateKiller(false))
     * - 开局自带一把左轮手枪
     */
    public static SRERole LOST_KILLER = TMMRoles.registerRole(new NormalRole(
            LOST_KILLER_ID, // 角色 ID
            new Color(180, 30, 45).getRGB(), // 暗红色 - 独特的迷失感
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 有杀手能力（默认杀手商店）
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺时间
            false // 隐藏计分板
    )).setOccupiedRoleCount(0) // 不占用杀手位
            .setCanUseInstinctAndNightVision(false) // 没有杀手透视
            .setCanSeeTeammateKillerRole(false) // 杀手本能看不到队友，对杀手的框显示如平民
            .setCanBeRandomedByOtherRoles(false)
            .setDefaultMax(1).setDefaultEnableChance(2000).setDefaultEnableNeededPlayerCount(12);

    /**
     * 判断角色是否应该在技能页面（Widget）中显示为可见的杀手同伙。
     * 迷失杀手虽然属于杀手阵营，但被设计为不暴露身份，因此排除。
     */
    public static boolean isVisibleKillerTeammate(io.wifi.starrailexpress.api.SRERole role) {
        if (role == null)
            return false;
        if (role.isKillerTeam() || role.isKiller() || role.isNeutralForKiller()) {
            return !LOST_KILLER_ID.equals(role.identifier());
        }
        return false;
    }

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
    public static SRERole STALKER = TMMRoles.registerRole(new NormalRole(
            STALKER_ID, // 角色 ID
            new Color(47, 79, 79).getRGB(), // 暗紫色 #4B0082
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 杀手阵营
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺
            true // 隐藏计分板
    ) {
        @Override
        public void serverTick(ServerPlayer player) {
            if (player.getOffhandItem().getItem() instanceof StalkerKnifeItem) {
                if (player.getMainHandItem().getItem() instanceof StalkerKnifeItem) {
                    if (player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())
                            && !player.getCooldowns().isOnCooldown(
                                    player.getOffhandItem().getItem())) {
                        // 交换位置
                        var temp = player.getMainHandItem();
                        var temp2 = player.getOffhandItem();
                        player.setItemInHand(InteractionHand.MAIN_HAND, temp2);
                        player.setItemInHand(InteractionHand.OFF_HAND, temp);

                    }
                    if (player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem())
                            && !player.getCooldowns().isOnCooldown(
                                    player.getOffhandItem().getItem())) {
                        // 交换位置
                        var temp = player.getMainHandItem();
                        var temp2 = player.getOffhandItem();
                        player.setItemInHand(InteractionHand.MAIN_HAND, temp2);
                        player.setItemInHand(InteractionHand.OFF_HAND, temp);

                    }
                }
            }
            super.serverTick(player);
        }
    }.setComponentKey(StalkerPlayerComponent.KEY))
            .setMaxSprintTime(StalkerPlayerComponent.MAX_SPRINT_TIME_IntSupplier);

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
    public static SRERole ADMIRER = TMMRoles.registerRole(new NormalRole(
            ADMIRER_ID, // 角色 ID
            new Color(255, 192, 203).getRGB(), false, false, SRERole.MoodType.FAKE, Integer.MAX_VALUE,
            true)).setComponentKey(AdmirerPlayerComponent.KEY).setNeutralForKiller(true)
            .setCanUseInstinctAndNightVision(true)
            .setCanSeeTeammateKillerRole(false);;

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
    public static RandomColorUtil PUPPETEER_COLOR = new RandomColorUtil(50, true);
    public static SRERole PUPPETEER = TMMRoles.registerRole(new NormalRole(
            PUPPETEER_ID, // 角色 ID
            new Color(138, 43, 226).getRGB(), // 深紫罗兰色 - 代表操控与神秘
            false, // isInnocent = 非乘客阵营
            false, // canUseKiller = 无杀手能力（初始）
            SRERole.MoodType.FAKE, // 假心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            true // 隐藏计分板
    ) {
        @Override
        public int getMoodColor() {
            return PUPPETEER_COLOR.getOrRandomColor();
        }
    }).setComponentKey(PuppeteerPlayerComponent.KEY).setAutoReset(false).setNeutralForKiller(true)
            .setCanUseInstinctAndNightVision(true);

    /**
     * 监察员角色
     * - 属于好人阵营 (isInnocent = true)
     * - 技能：标记一名玩家并透视其位置，冷却60秒
     */
    public static SRERole MONITOR = TMMRoles.registerRole(new NormalRole(
            MONITOR_ID, // 角色 ID
            new Color(32, 201, 151).getRGB(), // 蓝绿色
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    ).setComponentKey(MonitorPlayerComponent.KEY).setCanSeeCoin(true))
            .setDefaultEnableChance(7500);

    /**
     * 记录员角色
     * - 中立阵营 (isInnocent = false, canUseKiller = false)
     * - 假心情系统
     * - 标准冲刺时间
     * - 在计分板上隐藏
     * - 目标：使用笔记选择人和对应职业，如果正确人数达到2/5,获得独立胜利
     */
    public static SRERole RECORDER = TMMRoles.registerRole(new NormalRole(
            RECORDER_ID, // 角色 ID
            new Color(95, 158, 160).getRGB(), // 矢车菊蓝
            false, // isInnocent = 非乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 假心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            true // 隐藏计分板
    )).setComponentKey(RecorderPlayerComponent.KEY).setCanUseInstinctAndNightVision(true).setNeutrals(true)
            .setDefaultEnableNeededPlayerCount(10);

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
    public static SRERole GLITCH_ROBOT = TMMRoles.registerRole(new NormalRole(
            GLITCH_ROBOT_ID, // 角色 ID
            new Color(211, 196, 250).getRGB(), // 灰色 - 代表机器人
            true, // isInnocent = 乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 小丑心情（假心情）
            TMMRoles.CIVILIAN.getMaxSprintTime() * 2, // 双倍体力上限
            false // 不隐藏计分板
    ), "slay_the_spire").setComponentKey(GlitchRobotPlayerComponent.KEY).setCanSeeCoin(true)
            .setCanBePoisoned(false)
            .setDefaultMax(1);

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
    public static SRERole NIAN_SHOU = TMMRoles.registerRole(new NianShouRole(
            NIAN_SHOU_ID, // 角色 ID
            new Color(255, 99, 71).getRGB(), // 番茄红 - 代表年兽的喜庆与压迫感
            false, // isInnocent = 非乘客阵营（中立）
            false, // canUseKiller = 无杀手能力（但可以购买关灯）
            SRERole.MoodType.REAL, // 真实心情
            (int) (TMMRoles.CIVILIAN.getMaxSprintTime() * 1.5), // 1.5倍体力
            true // 隐藏计分板
    ).setComponentKey(NianShouPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true))
            .setDefaultEnableChance(2000);

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
    public static SRERole THIEF = TMMRoles.registerRole(new NormalRole(
            THIEF_ID, // 角色 ID
            new Color(212, 175, 55).getRGB(), // 金棕色 - 代表财富与贪婪
            false, // isInnocent = 非乘客阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺时间
            true // 隐藏计分板
    ) {
        @Override
        public List<ItemStack> getDefaultItems() {
            ArrayList<ItemStack> itemStacks = new ArrayList<>();
            itemStacks.add(new ItemStack(Items.BUNDLE));
            return itemStacks;
        }
    }).setComponentKey(ThiefPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true)
            .setCanSeeTeammateKillerRole(false).setDefaultEnableChance(5000)
            .setDefaultEnableNeededPlayerCount(10);

    /**
     * 雇佣兵角色 - 中立阵营（非独立胜利）
     * - 默认无雇佣任务时发光
     * - 默认无雇佣任务时拥有2层护盾
     * - 仅可击杀雇佣目标或打破其护盾者
     */
    public static SRERole MERCENARY = TMMRoles.registerRole(new NormalRole(
            MERCENARY_ID,
            new Color(176, 128, 96).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true)).setComponentKey(MercenaryPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true)
            .setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(false).setDefaultMax(1)
            .setDefaultEnableChance(1000).setDefaultEnableNeededPlayerCount(12);

    /**
     * 秉烛人角色 - 中立阵营
     * - 仅在12人及以上对局刷新
     * - 独立胜利条件：完成指定次数的尸体秉烛
     * - 技能：消耗次数隐身18秒（次数来自成功为对应尸体秉烛）
     */
    public static SRERole CANDLE_BEARER = TMMRoles.registerRole(new NormalRole(
            CANDLE_BEARER_ID,
            new Color(255, 210, 120).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true)).setComponentKey(CandleBearerPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true)
            .setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setDefaultEnableNeededPlayerCount(12);

    public static SRERole RAVEN = TMMRoles.registerRole(new NormalRole(
            RAVEN_ID,
            new Color(130, 100, 160).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true)).setComponentKey(RavenPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true)
            .setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setDefaultEnableNeededPlayerCount(10);

    public static SRERole REASONER = TMMRoles.registerRole(new NormalRole(
            REASONER_ID,
            new Color(212, 178, 92).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true)).setComponentKey(ReasonerPlayerComponent.KEY).setCanSeeCoin(true).setNeutrals(true)
            .setCanSeeTeammateKillerRole(false).setCanUseInstinctAndNightVision(true)
            .setDefaultMax(1).setDefaultEnableNeededPlayerCount(10).setDefaultEnableChance(6500);

    /**
     * 阿蒙（诡秘之主）—— 中立彩蛋独立胜利角色，核心机制「寄生」。
     * - 中立独立胜利 (setNeutrals(true)，setNeutralForKiller(false) 杀手视角为好人)
     * - 无武器、不开杀手商店、不能捡枪、无杀手直觉
     * - 隐秘种下时之虫同化他人，可主动夺舍 / 致命伤时自动夺舍续命
     * - 胜利条件「夺舍并幸存」在 CustomWinnerClass 判定
     */
    public static SRERole AMON = TMMRoles.registerRole(new EggRole(
            AMON_ID,
            new Color(120, 110, 140).getRGB(),
            false,
            false,
            SRERole.MoodType.FAKE,
            TMMRoles.CIVILIAN.getMaxSprintTime() * 2,
            true) {
        @Override
        public List<ItemStack> getDefaultItems() {
            var itemStacks = new ArrayList<ItemStack>();
            itemStacks.add(Items.BUNDLE.getDefaultInstance());
            return itemStacks;
        }
    }, "lord_of_mysteries")
            .setComponentKey(org.agmas.noellesroles.game.roles.neutral.amon.AmonPlayerComponent.KEY)
            .setNeutrals(true).setNeutralForKiller(false)
            .setCanSeeTeammateKillerRole(false).setCanPickUpRevolver(false)
            .setCanUseInstinctAndNightVision(false)
            .setCanSeeCoin(true)
            .setCanBeRandomedByOtherRoles(false)
            .setDefaultMax(1)
            .setDefaultEnableNeededPlayerCount(10)
            .setDefaultEnableChance(2500);

    /**
     * 宿命的罪人 —— 中立独立胜利角色。
     * - 中立独立胜利（setNeutrals(true)，杀手视角不视为队友）
     * - 通过以不同死因死去累积胜利进度（人数越多需要越多，最低 5 最高 10）
     * - 每次非彻底死亡后在自己的房间复活，尸体数秒后消失
     * - 同一死因死去 3 次则彻底死亡
     * - 技能 1「命运的启示」近距离查看目标杀人方式；技能 2「重启」死亡脱离
     * 胜利条件在 CustomWinnerClass 判定，技能在 ModRolesInitialEventRegister 注册。
     */
    public static SRERole DOOMED_SINNER = TMMRoles.registerRole(new EggRole(
            DOOMED_SINNER_ID,
            new Color(126, 36, 84).getRGB(), // 暗紫红 - 宿命与罪
            false,
            false,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true), "lord_of_mysteries")
            .setComponentKey(DoomedSinnerPlayerComponent.KEY)
            .setNeutrals(true).setCanSeeTeammateKillerRole(false)
            .setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true)
            .setDefaultMax(1)
            .setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(5000);

    /**
     * 魔术师角色 - 好人阵营（从模仿者移植）
     * - 好人阵营 (isInnocent = true)
     * - 能捡枪 (setCanPickUpRevolver(true))
     * - 做任务维持san值
     * - 商店购买假枪（175金币）
     * - 商店购买假疯狂模式（250金币）：获得假球棒，穿上疯狂模式外观，不播放音乐
     * - 如果指挥官在场，加入指挥官频道
     */
    public static SRERole MAGICIAN = TMMRoles.registerRole(new NormalRole(
            MAGICIAN_ID, // 角色 ID
            new Color(255, 165, 0).getRGB(), // 橙色 - 代表魔术师的魅力
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            false // 不显示计分板
    )).setCanPickUpRevolver(true).setCanSeeCoin(true)
            .setNeutralForKiller(true).setCanSeeTeammateKillerRole(false).setNeutrals(false)
            .setCanBeRandomedByOtherRoles(false)
            .setDefaultEnableChance(2500)
            .setDefaultEnableNeededPlayerCount(16)
            .setHiddenForRoleRotation(true);

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
    public static SRERole CLOCKMAKER = TMMRoles.registerRole(new NormalRole(
            CLOCKMAKER_ID, // 角色 ID
            new Color(218, 165, 32).getRGB(), // 金色 - 代表钟表与时间
            true, // isInnocent = 好人阵营
            false, // canUseKiller = 无杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
            true // 不显示计分板
    )).setComponentKey(ClockmakerPlayerComponent.KEY).setCanSeeTime(true).setCanSeeCoin(true)
            .setDefaultEnableNeededPlayerCount(12);

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
    public static SRERole // 强盗角色 - 杀手阵营
    BANDIT = TMMRoles.registerRole(new NormalRole(
            BANDIT_ID, // 角色 ID
            new Color(139, 69, 19).getRGB(), // 棕色 - 代表强盗的粗糙感
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺时间
            true // 隐藏计分板
    )).setComponentKey(ModComponents.BANDIT);

    /**
     * 悍匪角色
     * - 属于杀手阵营 (isInnocent = false)
     * - 可以使用杀手能力 (canUseKiller = true)
     * - 假心情系统
     * - 无限体力 (Integer.MAX_VALUE)
     * - 隐藏计分板
     * - 与钳工绑定生成
     * - 初始物品：1个C4炸药 + 1个C4引爆器
     * - 专属商店：短管霰弹枪(185金币)、C4炸药(300金币)、撬棍(25金币)、开锁器(80金币)、关灯(100金币)
     */
    public static SRERole GANGSTERS = TMMRoles.registerRole(new NormalRole(
            GANGSTERS_ID,
            new Color(60, 60, 60).getRGB(),
            false,
            true,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true)).setCanSeeCoin(true).setDefaultMax(1).setDefaultEnableNeededPlayerCount(12)
            .setDefaultEnableChance(7500);

    /**
     * 钳工角色
     * - 属于平民阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 在计分板上显示
     * - 初始物品：拆弹钳（无限次使用，拆C4必定成功）
     * - 专属商店：开灯(175金币)、监控恢复(75金币)
     * - 死亡后拆弹钳传递给附近平民
     */
    public static SRERole FITTER = TMMRoles.registerRole(new NormalRole(
            FITTER_ID,
            new Color(70, 130, 180).getRGB(),
            true,
            false,
            SRERole.MoodType.REAL,
            TMMRoles.CIVILIAN.getMaxSprintTime(),
            false)).setCanSeeCoin(true).setDefaultMax(0);

    public static SRERole BLOOD_FEUDIST = TMMRoles.registerRole(new NormalRole(
            BLOOD_FEUDIST_ID, // 角色 ID
            new Color(178, 34, 34).getRGB(), // 暗红色 - 代表复仇与愤怒
            false, // isInnocent = 非乘客阵营
            true, // canUseKiller = 有杀手能力
            SRERole.MoodType.FAKE, // 假心情
            Integer.MAX_VALUE, // 无限冲刺时间
            true // 隐藏计分板
    )).setComponentKey(ModComponents.BLOOD_FEUDIST).setCanSeeCoin(true)
            .setDefaultEnableNeededPlayerCount(12);
    public static SRERole WATCHER = TMMRoles.registerRole(new WatcherRole(
            WATCHER_ID,
            new Color(52, 73, 94).getRGB(),
            false,
            true,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true), "slay_the_spire").setComponentKey(ModComponents.WATCHER).setCanSeeCoin(true);

    // 模仿者 - 杀手角色，右键尸体吃掉获得永久能力
    public static SRERole IMITATOR = TMMRoles.registerRole(new NormalRole(
            IMITATOR_ID,
            new Color(100, 0, 0).getRGB(),
            false,
            true,
            SRERole.MoodType.FAKE,
            Integer.MAX_VALUE,
            true) {
        @Override
        public InteractionResult rightClickEntity(Player player, Entity target) {
            if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvivalIgnoreShitSplit(player))
                return InteractionResult.PASS;
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp))
                return InteractionResult.PASS;
            if (target instanceof io.wifi.starrailexpress.content.entity.PlayerBodyEntity body) {
                org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent comp = ModComponents.IMITATOR
                        .get(sp);
                if (!comp.isCharging) {
                    comp.startCharging(body.getUUID());
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }
    }).setComponentKey(ModComponents.IMITATOR).setCanSeeCoin(true);

    /**
     * 愚者角色 - 好人阵营
     * - 属于乘客阵营 (isInnocent = true)
     * - 不能使用杀手能力 (canUseKiller = false)
     * - 真实心情系统
     * - 标准冲刺时间
     * - 不隐藏计分板
     * - 核心机制：尊名纸条、塔罗会、处刑者手枪
     * - 商店：尊名纸条(50金币)、灵性斗篷(200金币)
     */
    public static SRERole THE_FOOL = TMMRoles
            .registerRole(new org.agmas.noellesroles.game.roles.innocence.fool.FoolRole(
                    THE_FOOL_ID, // 角色 ID
                    new Color(180, 160, 220).getRGB(), // 淡紫色 - 代表神秘与命运
                    true, // isInnocent = 好人阵营
                    false, // canUseKiller = 无杀手能力
                    SRERole.MoodType.REAL, // 真实心情
                    TMMRoles.CIVILIAN.getMaxSprintTime(), // 标准冲刺时间
                    false // 不隐藏计分板
            ), "lord_of_mysteries")
            .setComponentKey(org.agmas.noellesroles.game.roles.innocence.fool.FoolPlayerComponent.KEY)
            .setCanSeeCoin(true)
            .setCanPickUpRevolver(true)
            .setCanUseSkillWhileSpectator(true)
            .setDefaultMax(1)
            .setCanBeRandomedByOtherRoles(false)
            .setSpecialVigilante(true)
            // 划分到警长阵营（isVigilanteTeam 为权威的警长归属判定）
            .setVigilanteTeam(true)
            // 出现概率下调一半（3000 -> 1500）
            .setDefaultEnableChance(1500).setDefaultEnableNeededPlayerCount(12);

    /**
     * 黑白角色 - 中立阵营（伪装义警）
     * - 中立阵营 (isInnocent = false, canUseKiller = false)
     * - 对外伪装为义警
     * - 假心情系统
     * - 标准冲刺时间
     * - 三阶段机制：伪装义警 → 狂暴前奏 → 黑白熊
     * - 黑白熊形态无敌+光环效果
     * - 获胜条件：游戏结束时6格内最近玩家的阵营
     */
    public static final SRERole MONOKUMA = TMMRoles.registerRole(new MonokumaRole())
            .setNeutralForKiller(false) // 杀手视角为好人
            .setCanSeeTeammateKillerRole(false)
            .setCanPickUpRevolver(false) // 伪装义警可以捡枪
            .setNeutrals(true)
            .setCanUseInstinctAndNightVision(false) // 不能使用杀手直觉
            .setCanSeeCoin(true)
            .setDefaultMax(0)
            .setCanBeRandomedByOtherRoles(false).addBothRelatedModifier(SEModifiers.BLACK_WHITE);

    // ─────────────────────── 信使 Courier ───────────────────────
    public static final ResourceLocation COURIER_ID = Noellesroles.id("courier");
    public static SRERole COURIER = TMMRoles.registerRole(new NormalRole(
            COURIER_ID,
            new Color(210, 180, 140).getRGB(), // 淡棕色 — 信封色
            true, // isInnocent = 平民
            false, // 不能使用杀手能力
            SRERole.MoodType.REAL, // 真实心情
            TMMRoles.CIVILIAN.getMaxSprintTime(), // 冲刺时间
            false // 不能看时间
    )).setDefaultMax(1).setCanSeeCoin(true);

    // ==================== 其他变量定义 ====================
    public static ArrayList<SRERole> SHOW_MONEY_ROLES = new ArrayList<>();
    public static HashMap<SRERole, RoleAnnouncementTexts.RoleAnnouncementText> roleRoleAnnouncementTextHashMap = new HashMap<>();

    // ==================== 咒法师 ====================
    public static SRERole WARLOCK = TMMRoles.registerRole(new NormalRole(
            WARLOCK_ID, new java.awt.Color(139, 0, 139).getRGB(), false,
            true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
            .setComponentKey(org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent.KEY))
            .setCanUseKiller(true).setCanSeeTeammateKillerRole(true).setCanBeRandomedByOtherRoles(false)
            .setDefaultMax(1)
            .setCanUseInstinctAndNightVision(true).setCanSeeCoin(true);

    // ==================== Dream（梦魇）====================
    // "噢，皮革噶的，i want to 和你蹦蹦蹦。"
    public static SRERole DREAM = TMMRoles.registerRole(new EggRole(
            DREAM_ID, new Color(0, 168, 107).getRGB(), false,
            true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true) {
        @Override
        public boolean onPsychoGiveItem(net.minecraft.world.entity.player.Player player,
                io.wifi.starrailexpress.cca.SREPlayerPsychoComponent psychoComponent) {
            // 巨幕面具狂暴走 Psycho 逻辑，但不发球棒/面具到手上
            return true;
        }

        @Override
        public net.minecraft.world.item.Item getPsychoItem() {
            // 疯魔结束时的清理目标：面具从不入包，等于什么都不清理（避免误清球棒）
            return org.agmas.noellesroles.init.ModItems.DREAM_MASK;
        }
    }).setComponentKey(org.agmas.noellesroles.game.roles.killer.dream.DreamPlayerComponent.KEY)
            .setCanUseKiller(true).setCanSeeTeammateKillerRole(true).setCanBeRandomedByOtherRoles(false)
            .setDefaultMax(1)
            .setCanUseInstinctAndNightVision(true).setCanSeeCoin(true);

    // ==================== 嬉命人（Embalmer）====================
    public static SRERole EMBALMER = TMMRoles.registerRole(new NormalRole(
            EMBALMER_ID, new java.awt.Color(255, 140, 140).getRGB(), false,
            false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
            .setComponentKey(org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerPlayerComponent.KEY))
            .setNeutralForKiller(true).setCanSeeTeammateKillerRole(true)
            .setCanUseInstinctAndNightVision(true).setCanSeeCoin(true).setDefaultEnableChance(4500);

    // ==================== 窃皮者 ====================
    public static SRERole SKINCRAWLER = TMMRoles.registerRole(new NormalRole(
            SKINCRAWLER_ID, new java.awt.Color(204, 68, 68).getRGB(), false,
            true, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
            .setComponentKey(
                    org.agmas.noellesroles.game.roles.killer.skincrawler.SkincrawlerPlayerComponent.KEY))
            .setCanUseKiller(true).setCanSeeTeammateKillerRole(true)
            .setCanUseInstinctAndNightVision(true).setCanSeeCoin(true);

    /**
     * 幻音师角色 (Musician Phantom)
     * - 杀手方中立阵营 (isInnocent = false, canUseKiller = false)
     * - 假心情系统
     * - 无限冲刺时间
     * - 在计分板上隐藏
     *
     * 被动：每30秒获得50金币
     *
     * 商店音效（图标为音符盒，购买时播放音效）：
     * - 出刀的声音 (50金币, 冷却30秒)
     * - 左轮手枪开火的声音 (75金币, 冷却30秒)
     * - 潜行者觉醒的声音 (100金币, 冷却120秒, MASTER类型全场播放)
     * - 疯狂模式的声音 (450金币, 冷却5分钟)
     * - 撬棍撬门的声音 (75金币, 冷却1分钟)
     * - 随机播放音效 (100金币, 冷却40秒, 图标为音乐唱片)
     *
     * 技能：花费100金币传送到30格外随机一人的身边，冷却120秒
     */
    public static SRERole PHANTOM_MUSICIAN = TMMRoles
            .registerRole(new NormalRole(PHANTOM_MUSICIAN_ID, new java.awt.Color(180, 120, 220).getRGB(),
                    false,
                    false, SRERole.MoodType.FAKE, Integer.MAX_VALUE, true)
                    .setComponentKey(
                            org.agmas.noellesroles.game.roles.neutral.musician_phantom.PhantomMusicianPlayerComponent.KEY))
            .setNeutralForKiller(true)
            .setNeutrals(true)
            .setCanUseInstinctAndNightVision(true)
            .setCanSeeCoin(true)
            .setDefaultMax(1);

    /**
     * 初始化并注册所有角色
     * 在模组初始化时调用
     */
    public static void init() {
        BounsRoles.init();
        SREPlayerPoisonComponent.canSyncedRolePaths.add(ModRoles.POISONER_ID.getPath());
        SREPlayerPoisonComponent.canSyncedRolePaths.add(ModRoles.BARTENDER_ID.getPath());
        SREArmorPlayerComponent.canSyncedRolePaths.add(ModRoles.BARTENDER_ID.getPath());
        SREPlayerMoodComponent.canSyncedRolePaths.add(ModRoles.MA_CHEN_XU_ID.getPath());
        SREPlayerMoodComponent.canSyncedRolePaths.add(ModRoles.WRAITH_ASSASSIN_ID.getPath());
        SREPlayerPoisonComponent.canSyncedRolePaths.add(ModRoles.INFECTED_ID.getPath());

        // 设置疫使与毒师互斥
        ModRoles.INFECTED.addTwoWayOpposingRole(ModRoles.POISONER);

        // 设置迷失杀手与魔术师互斥
        ModRoles.LOST_KILLER.addTwoWayOpposingRole(ModRoles.MAGICIAN);

        // 设置皮猪与阴谋家互斥生成
        ModRoles.LEATHER_PIG.addTwoWayOpposingRole(ModRoles.CONSPIRATOR);

        // 设置鹈鹕与纵火犯互斥
        ModRoles.PELICAN.addTwoWayOpposingRole(SERoles.ARSONIST);
        // 设置鹈鹕与秉烛人互斥
        ModRoles.PELICAN.addTwoWayOpposingRole(ModRoles.CANDLE_BEARER);
        ModRoles.PELICAN.addTwoWayOpposingRole(SERoles.INITIATE);

        // 设置教父与初学者互斥
        ModRoles.GODFATHER.addTwoWayOpposingRole(SERoles.INITIATE);
        // 设置鹈鹕与教父互斥
        ModRoles.PELICAN.addTwoWayOpposingRole(ModRoles.GODFATHER);
        // 设置鹈鹕与傀儡师互斥
        ModRoles.PELICAN.addTwoWayOpposingRole(ModRoles.PUPPETEER);
        // 设置鹈鹕与渡鸦互斥
        ModRoles.PELICAN.addTwoWayOpposingRole(ModRoles.RAVEN);

        RoleSkill.register(ModRoles.THE_FOOL, FoolPlayerComponent::useSkill);

        // 初始化叛徒职业和新修饰符
        TraitorAndModifiers.init();
        ModifierEffects.init();
    }
}
