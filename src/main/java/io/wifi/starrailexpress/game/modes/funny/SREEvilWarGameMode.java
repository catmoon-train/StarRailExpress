/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.ItemComponentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.level.Level;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.commands.BroadcastCommand;
import org.agmas.noellesroles.content.item.TimeStopClock;
import org.agmas.noellesroles.game.roles.special.variant_loose_end.VariantLooseEndRole;
import org.agmas.noellesroles.role_data.killer.BloodFeudistRoleData;
import org.agmas.noellesroles.role_data.killer.ImitatorRoleData;
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorSkillRegistry;
import org.agmas.noellesroles.role_data.killer.InsaneKillerRoleData;
import org.agmas.noellesroles.role_data.killer.StalkerRoleData;
import org.agmas.noellesroles.role_data.killer.TrapperRoleData;
import org.agmas.noellesroles.role_data.killer.WatcherRoleData;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.game.roles.killer.boom_maniac.BoomManiacRole;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.role.bouns.BounsRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.role.touhou.THMountainRoles;
import org.agmas.noellesroles.role.ModMeetingRoles;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.role_data.killer.WizardRoleData;
import org.agmas.noellesroles.role_data.killer.UndeadLordRoleData;
import org.agmas.noellesroles.role_data.killer.SkincrawlerRoleData;
import io.wifi.starrailexpress.api.RoleSkill;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.*;
import java.util.function.Supplier;

/**
 * 邪恶战局
 * <p>
 * - 模式特性：每 7 狼（开局 1000 金币）对战 1 名变种亡命徒（无友军透视，但是必定矮小）
 * </p>
 */
public class SREEvilWarGameMode extends WTLooseEndsGameMode {
    /** 有特殊改动的职业 */
    public static final Set<SRERole> EX_ABILITY_ROLE = new HashSet<>();
    /** 幽灵亡命徒允许承受的、能够明确识别为单体命中的战斗死因 */
    private static final Set<ResourceLocation> GHOST_LOOSE_END_DAMAGE_ALLOWLIST = new HashSet<>();
    static {
        EX_ABILITY_ROLE.add(THRedHouseRoles.REMILIA);
        EX_ABILITY_ROLE.add(ModRoles.IMITATOR);
        EX_ABILITY_ROLE.add(ModRoles.TRAPPER);
        EX_ABILITY_ROLE.add(ModRoles.BANDIT);
        EX_ABILITY_ROLE.add(TraitorAndModifiers.TRAITOR);
        EX_ABILITY_ROLE.add(ModRoles.CONSPIRATOR);
        EX_ABILITY_ROLE.add(ModRoles.DIO);
        EX_ABILITY_ROLE.add(ModRoles.CLEANER);
        EX_ABILITY_ROLE.add(ModRoles.DELAYER);
        EX_ABILITY_ROLE.add(ModRoles.EXECUTIONER);
        EX_ABILITY_ROLE.add(ModRoles.BOMBER);
        EX_ABILITY_ROLE.add(SERoles.NECROMANCER);
        EX_ABILITY_ROLE.add(BounsRoles.CAT_NECROMANCER);
        EX_ABILITY_ROLE.add(ModRoles.BLOOD_FEUDIST);
        EX_ABILITY_ROLE.add(SERoles.AVARICIOUS);
        EX_ABILITY_ROLE.add(ModRoles.PARTY_KILLER);
        EX_ABILITY_ROLE.add(ModRoles.NINJA);
        EX_ABILITY_ROLE.add(ModRoles.POISONER);
        EX_ABILITY_ROLE.add(BounsRoles.CREEPER);
        EX_ABILITY_ROLE.add(ModRoles.STALKER);
        EX_ABILITY_ROLE.add(ModRoles.INSANE_KILLER);
        EX_ABILITY_ROLE.add(ModRoles.SILENT_KILLER);
        EX_ABILITY_ROLE.add(ModRoles.WIZARD);
        EX_ABILITY_ROLE.add(ModRoles.UNDEAD_LORD);
        EX_ABILITY_ROLE.add(ModRoles.EXAMPLER);
        EX_ABILITY_ROLE.add(ModRoles.NIAOSHOU_SHOU);
        EX_ABILITY_ROLE.add(ModRoles.NOSTALGIST);
        EX_ABILITY_ROLE.add(ModRoles.BOOM_MANIAC);
        EX_ABILITY_ROLE.add(ModRoles.SILENCER);
        EX_ABILITY_ROLE.add(ModRoles.GANGSTERS);
        EX_ABILITY_ROLE.add(ModRoles.WATCHER);
        EX_ABILITY_ROLE.add(ModRoles.YOULU);
        EX_ABILITY_ROLE.add(ModRoles.DREAM);
        EX_ABILITY_ROLE.add(ModRoles.SKINCRAWLER);
        EX_ABILITY_ROLE.add(ModRoles.TARTAGLIA);
        EX_ABILITY_ROLE.add(BounsRoles.PROGRAMMER);
        EX_ABILITY_ROLE.add(THMiscRoles.MAMIZOU);
        EX_ABILITY_ROLE.add(THMiscRoles.IBUKI_SUIKA);
        EX_ABILITY_ROLE.add(THMiscRoles.HAKUREI_REIMU);
        EX_ABILITY_ROLE.add(THMiscRoles.REIUJI_UTSUHO);
        EX_ABILITY_ROLE.add(THMiscRoles.KOMACHI);
        EX_ABILITY_ROLE.add(THMiscRoles.KIJIN_SEIJA);
        EX_ABILITY_ROLE.add(THMiscRoles.HOUJUU_NUE);
        EX_ABILITY_ROLE.add(THMiscRoles.DOREMY);
        EX_ABILITY_ROLE.add(THMiscRoles.YAKUMO_YUKARI);
        EX_ABILITY_ROLE.add(THMiscRoles.HATA_NO_KOKORO);
        EX_ABILITY_ROLE.add(THMountainRoles.AYA);

        Collections.addAll(GHOST_LOOSE_END_DAMAGE_ALLOWLIST,
            GameConstants.DeathReasons.GUN_SHOT,                 // 枪械射击
            GameConstants.DeathReasons.KNIFE,                    // 普通小刀
            GameConstants.DeathReasons.REVOLVER,                 // 左轮手枪
            GameConstants.DeathReasons.DERRINGER,                // 德林杰手枪
            GameConstants.DeathReasons.GENERAL_ATTACK,           // 通用近战攻击
            GameConstants.DeathReasons.BAT,                      // 球棒攻击
            GameConstants.DeathReasons.POISON,                   // 中毒
            GameConstants.DeathReasons.ARROW,                    // 弓箭命中
            GameConstants.DeathReasons.TRIDENT,                  // 三叉戟命中
            GameConstants.DeathReasons.SNIPER_RIFLE,             // 狙击步枪
            GameConstants.DeathReasons.NUNCHUCK,                 // 双截棍
            GameConstants.DeathReasons.ZERO_ONE_FIVE,            // 015 的单体攻击
            GameConstants.DeathReasons.ANCIENT_BITE,             // 远古生物撕咬
            GameConstants.DeathReasons.TARTAGLIA,                // 达达利亚近战攻击
            GameConstants.DeathReasons.TARTAGLIA_ARROW,          // 达达利亚弓箭
            GameConstants.DeathReasons.FAKE_AI_BACKSTAB,         // 伪人背刺
            GameConstants.DeathReasons.BOMB_DEATH,               // 炸弹直接命中结算
            GameConstants.DeathReasons.PUPPETEER_BODY_DEATH,     // 操偶师处决傀儡本体
            GameConstants.DeathReasons.BATON_KILL,               // 警棍攻击
            GameConstants.DeathReasons.FIRE_AXE,                 // 消防斧攻击
            GameConstants.DeathReasons.NINJA_KNIFE_KILL,         // 忍者小刀
            GameConstants.DeathReasons.THROWING_KNIFE_HIT,       // 飞刀命中
            GameConstants.DeathReasons.BAMBOO_SPEAR,             // 竹矛命中
            GameConstants.DeathReasons.WATCHER_CALM_KILL,        // 监视者冷静击杀
            GameConstants.DeathReasons.HOAN_MEIRIN_ATTACK,       // 红美铃攻击
            GameConstants.DeathReasons.WRAITH_ASSAULT,           // 怨灵突袭
            GameConstants.DeathReasons.DREAM_AXE,                // 梦境斧攻击
            GameConstants.DeathReasons.DREAM_DIAMOND_SWORD,      // 梦境钻石剑攻击
            GameConstants.DeathReasons.DREAM_MACE,               // 梦境重锤攻击
            GameConstants.DeathReasons.SPEAR,                    // 长矛攻击
            GameConstants.DeathReasons.KATANA,                   // 武士刀攻击
            GameConstants.DeathReasons.DANMUKU,                  // 弹幕命中
            GameConstants.DeathReasons.HIM_SWORD,                // HIM 剑攻击
            GameConstants.DeathReasons.CANDLE_SNIP,              // 蜡烛剪击
            GameConstants.DeathReasons.WHITE_EYES_NEEDLE,        // 白眼针攻击
            GameConstants.DeathReasons.STRINGER_BLADE,           // 刺刃攻击
            GameConstants.DeathReasons.ENTITY_303_PEEL,          // Entity 303 剥离攻击
            GameConstants.DeathReasons.REAPER_SCYTHE,            // 死神镰刀攻击
            GameConstants.DeathReasons.SECOND_NEEDLE,            // 秒针攻击
            GameConstants.DeathReasons.PAPER_CUT,                // 纸张切割
            GameConstants.DeathReasons.FOG_BLADE,                // 雾刃攻击
            GameConstants.DeathReasons.STONE_SPEAR,              // 石矛攻击
            GameConstants.DeathReasons.MINUTE_NEEDLE);           // 分针攻击
        // 同时纳入核心注册的全部物品直接击杀死因，避免新物品型单体攻击遗漏。
        GHOST_LOOSE_END_DAMAGE_ALLOWLIST.addAll(GameConstants.DeathReasons.getItemDeathReasons());
    }
    public static final int ADD_BALANCE_TIME = 600;
    public static final int REVIVE_TIME = 200;
    public static final int ONE_SECOND_TICK = 20;
    public static final int START_CROSS_BAR_COOLDOWN = 40 * 20;
    /** 刽子手德林加数量 */
    public static final int EXECUTIONER_GUN_NUMBER = 6;
    /** 观者切换姿态的冷却 */
    private static final int WATCHER_STANCE_COOLDOWN_TICKS = 5 * 20;
    /** 猫娘召唤师的冷却 */
    private  static final int CAT_NECROMANCER_COOLDOWN_TICKS = 10 * 20;
    /** 死灵法师的冷却 */
    private  static final int NECROMANCER_COOLDOWN_TICKS = 10 * 20;
    protected static AttributeModifier tinyModifier = new AttributeModifier(
            StupidExpress.id("tiny_modifier"), -0.15, AttributeModifier.Operation.ADD_VALUE);
    public final List<SRERole> BANED_ROLES = new ArrayList<>();
    int curBalanceTick = 0;
    int curReviveTick = 0;
    int curOneSecondTick = 0;
    
    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    public SREEvilWarGameMode(ResourceLocation identifier) {
        super(identifier, 10, 2);
        addBanedRoles();
    }

    protected void addBanedRoles() {
        // 禁用 水鬼，布袋鬼，猫娘杀手，影隼，寻找者，迷失杀手
        BANED_ROLES.add(ModRoles.WATER_GHOST);
        BANED_ROLES.add(ModRoles.MA_CHEN_XU);
        BANED_ROLES.add(BounsRoles.CAT_KILLER);
        BANED_ROLES.add(ModRoles.SHADOW_FALCON);
        BANED_ROLES.add(SpecialGameModeRoles.SEEKER);
        BANED_ROLES.add(ModRoles.LOST_KILLER);
        // 新增禁用：冤魂、森蕈僵尸、猎人、传教士（寻找者已在上方）
        BANED_ROLES.add(ModRoles.WRAITH_ASSASSIN);
        BANED_ROLES.add(BounsRoles.FOREST_MUSHROOM_ZOMBIE);
        BANED_ROLES.add(ModRoles.HUNTER);
        BANED_ROLES.add(ModMeetingRoles.MISSIONARY);

    }

    public RoleAssignmentPool createEvilWarRolePool(Level world) {
        return RoleAssignmentPool.createUnlimited("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN &&
                        // 禁用角色
                        role.canBeRandomed(world) &&
                        !BANED_ROLES.contains(role));
    }

    @Override
    public boolean isLooseEndMode() {
        return false;
    }

    @Override
    public void killPlayer(Player victim, boolean spawnBody, @Nullable Player _killer,
            ResourceLocation deathReason, boolean forceDeath) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(victim.level());
        SRERole role = gameWorldComponent.getRole(victim);
        // 对幽灵亡命徒的专属判断 ：非强制击杀时，不在白名单的伤害来源免疫
        if (!forceDeath
                && role == SpecialGameModeRoles.GHOST_LOOSE_END
                && _killer != null
                && !GHOST_LOOSE_END_DAMAGE_ALLOWLIST.contains(deathReason)) {
            return;
        }
        super.killPlayer(victim, spawnBody, _killer, deathReason, forceDeath);
        Level level = victim.level();
        // 雷米莉亚死亡给予附近杀手护盾
        if (role == THRedHouseRoles.REMILIA) {
            for (Player player : level.players()) {
                // 排除被淘汰的、玩家自己、距离超过2格的玩家
                if (GameUtils.isPlayerEliminated(player) || player == victim
                        || player.distanceToSqr(victim) > 1.5 * 1.5)
                    continue;
                SRERole playerRole = gameWorldComponent.getRole(player);
                // 排除亡命徒
                if (VariantLooseEndRole.isVariant(playerRole))
                    continue;
                SREArmorPlayerComponent armorComponent = SREArmorPlayerComponent.KEY.get(player);
                armorComponent.addArmor();
            }
        }
    }

    // 初始化模式物品列表
    protected void initItemList() {
        looseEndsItems.add(TMMItems.KNIFE::getDefaultInstance);
        looseEndsItems.add(TMMItems.DERRINGER::getDefaultInstance);
        looseEndsItems.add(ModItems.PATROLLER_REVOLVER::getDefaultInstance);
        looseEndsItems.add(TMMItems.CROWBAR::getDefaultInstance);
        looseEndsItems.add(TMMItems.DEFENSE_VIAL::getDefaultInstance);
        looseEndsItems.add(TMMItems.DEFENSE_VIAL::getDefaultInstance);
    }

    @Override
    protected void initCoolDownItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        super.initCoolDownItems(players, gameWorldComponent);
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(ModItems.PATROLLER_REVOLVER, cooldown);
            itemCooldownManager.addCooldown(ModItems.DESPERADO_KNIFE, cooldown);
            // 撬棍开局冷却40s
            itemCooldownManager.addCooldown(TMMItems.CROWBAR, START_CROSS_BAR_COOLDOWN);
        }
    }

    protected int getLooseEndVariantCount(List<ServerPlayer> players) {
        return Math.max(players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1), 1);
    }

    @Override
    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,ServerLevel world) {
        RandomSource levelRandomSource = world.getRandom();
        // 处理强制角色
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE);
        /** 非强制角色，可以随机分配玩家 */
        List<ServerPlayer> playersWithoutForcedRoles = new ArrayList<>();
        // 每有一组狼人产生一个变种亡命徒：8 人局对应 7 狼 1 亡命徒
        int looseEndVariantCount = getLooseEndVariantCount(players);
        int ghostLooseEndCount = Math.min(1, looseEndVariantCount);
        for (ServerPlayer player : players) {
            if (!forcedRoles.containsKey(player.getUUID())) {
                playersWithoutForcedRoles.add(player);
            }
        }
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            ServerPlayer forcePlayer = null;
            for (ServerPlayer player : players) {
                if (player.getUUID().equals(entry.getKey()))
                    forcePlayer = player;
            }
            if (forcePlayer != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    if (VariantLooseEndRole.isVariant(role)) {
                        --looseEndVariantCount;
                    }
                    if (role == SpecialGameModeRoles.GHOST_LOOSE_END)
                        --ghostLooseEndCount;
                    gameWorldComponent.addRole(forcePlayer, role);
                } else
                    playersWithoutForcedRoles.add(forcePlayer);
            }
        }
        if (looseEndVariantCount < 0)
            looseEndVariantCount = 0;
        else if (looseEndVariantCount > playersWithoutForcedRoles.size()) {
            looseEndVariantCount = playersWithoutForcedRoles.size();
        }
        ghostLooseEndCount = Math.clamp(ghostLooseEndCount, 0, looseEndVariantCount);
        // 幽灵亡命徒属于邪恶战争的特殊分配规则：每局最多一名，并有 50% 概率出现
        ghostLooseEndCount = levelRandomSource.nextInt(0, ghostLooseEndCount + 1);

        // 生成杀手池
        RoleAssignmentPool killerPool = createEvilWarRolePool(world);
        List<SRERole> assignedKillers = killerPool.selectRoles(playersWithoutForcedRoles.size() - looseEndVariantCount);
        // 打乱需要分配的玩家列表
        Collections.shuffle(playersWithoutForcedRoles);
        // 前 looseEndVariantCount 个是变种亡命徒，剩下的为普通杀手。
        if (assignedKillers.isEmpty()) {
            return;
        }
        for (int i = 0,
                curKillerIdx = 0; i < playersWithoutForcedRoles.size(); ++i, curKillerIdx %= assignedKillers.size()) {
            if (i < looseEndVariantCount) {
                gameWorldComponent.addRole(playersWithoutForcedRoles.get(i),
                        i < ghostLooseEndCount
                                ? SpecialGameModeRoles.GHOST_LOOSE_END
                                : SpecialGameModeRoles.SUPER_LOOSE_END);
            } else if (curKillerIdx < assignedKillers.size()) {
                SRERole role = assignedKillers.get(curKillerIdx++);
                // 操纵师只会在有2个亡命徒以上时出现
                if (role == ModRoles.MANIPULATOR &&
                        (players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1) < 2)) {
                    role = TMMRoles.KILLER;
                }
                gameWorldComponent.addRole(playersWithoutForcedRoles.get(i), role);
            } else
                gameWorldComponent.addRole(playersWithoutForcedRoles.get(i), TMMRoles.KILLER);
        }
        Harpymodloader.FORCED_MODDED_ROLE.clear();
    }

    /** 为邪恶战争特定职业添加特有物品 */
    public static void addEvilWarItemsByRole(Player player, SRERole role) {
        if (player == null)
            return;
        // 刽子手自带6把手枪
        if (role == ModRoles.EXECUTIONER) {
            ItemStack gun = new ItemStack(TMMItems.DERRINGER);
            gun.setCount(EXECUTIONER_GUN_NUMBER);
            player.addItem(gun);
        }
        // 清道夫拥有各种枪和几把飞刀
        else if (role == ModRoles.CLEANER) {
            player.addItem(new ItemStack(TMMItems.DERRINGER));
            player.addItem(new ItemStack(ModItems.PATROLLER_REVOLVER));
            player.addItem(new ItemStack(ModItems.BANDIT_REVOLVER));
            ItemStack throwingKnife = new ItemStack(ModItems.THROWING_KNIFE);
            throwingKnife.setCount(4);
            player.addItem(throwingKnife);
        }
        // 滞时鬼20s可以停1s
        else if (role == ModRoles.DELAYER) {
            ItemStack timeStopClock = new ItemStack(ModItems.TIME_STOP_CLOCK);
            ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_STOP_TIME, 20);
            ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_COOLDOWN, 400);
            // timeStopClock.setDamageValue(TimeStopClock.MAX_DURABILITY);
            player.addItem(timeStopClock);
        }
        // 强盗有狙
        else if (role == ModRoles.BANDIT) {
            player.addItem(new ItemStack(TMMItems.SNIPER_RIFLE));
            player.addItem(new ItemStack(TMMItems.SCOPE));
            ItemStack bullet = new ItemStack(TMMItems.MAGNUM_BULLET);
            bullet.setCount(64);
            player.addItem(bullet);
        }
        // // 叛徒有强盗手枪
        // else if (role == TraitorAndModifiers.TRAITOR) {
        // player.addItem(new ItemStack(ModItems.BANDIT_REVOLVER));
        // }
        // 炸弹客自带2次时停1s的机会
        else if (role == ModRoles.BOMBER) {
            ItemStack timeStopClock = new ItemStack(ModItems.TIME_STOP_CLOCK);
            ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_STOP_TIME, 20);
            ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_COOLDOWN, 400);
            timeStopClock.setDamageValue(TimeStopClock.MAX_DURABILITY - 2);
            player.addItem(timeStopClock);
        }
        // 死灵法师有轮椅
        else if (role == SERoles.NECROMANCER || role == BounsRoles.CAT_NECROMANCER) {
            player.addItem(new ItemStack(ModItems.WHEELCHAIR));
            player.addItem(new ItemStack(ModItems.WHEELCHAIR));
        }
    }

    /** 初始化物品 */
    @Override
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        int looseEndVariantCount = getLooseEndVariantCount(players);
        int beyondKillerCount = players.size()- looseEndVariantCount * (SREConfig.instance().evilWarKillGroupNumber + 1);
        // 是否需要 人数补偿护盾
        boolean giveOverflowShield = beyondKillerCount > SREConfig.instance().evilWarKillGroupNumber / 3;

        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            SRERole role = gameWorldComponent.getRole(player);
            // 为特定角色添加特定物品
            if (role == SpecialGameModeRoles.SUPER_LOOSE_END) {
                // 新增亡命徒小刀
                looseEndsItems.add(ModItems.DESPERADO_KNIFE::getDefaultInstance);
                // 添加亡命徒模式专属物品
                for (Supplier<ItemStack> itemSupplier : looseEndsItems) {
                    ItemStack itemStack = itemSupplier.get();
                    if (itemStack != null && !itemStack.isEmpty()) {
                        player.addItem(itemStack);
                    }
                }
                // 如果超出要面临敌人的 1 / 3 则额外获得一个护盾
                if (giveOverflowShield) {
                    player.addItem(TMMItems.DEFENSE_VIAL.getDefaultInstance());
                }
            } else if (role == SpecialGameModeRoles.GHOST_LOOSE_END) {
//                player.addItem(ModItems.DESPERADO_KNIFE.getDefaultInstance());
                player.addItem(TMMItems.KNIFE.getDefaultInstance());
                for (Supplier<ItemStack> itemSupplier : looseEndsItems) {
                    ItemStack itemStack = itemSupplier.get();
                    // 亡命徒基础物品中 仅为幽灵亡命徒添加 防御药剂 和 撬棍
                    if (itemStack != null && (itemStack.is(TMMItems.DEFENSE_VIAL) || itemStack.is(TMMItems.CROWBAR))) {
                        player.addItem(itemStack.copy());
                    }
                }
                if (giveOverflowShield) {
                    player.addItem(TMMItems.DEFENSE_VIAL.getDefaultInstance());
                }
            } else
                addEvilWarItemsByRole(player, role);
        }
    }

    /** 发送欢迎包，根据特定角色发送 */
    @Override
    protected void sendWelcomePackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
            SRERole role1) {
        int looseEndCount = players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1);
        looseEndCount = Math.max(looseEndCount, 1);
        int killerCount = players.size() - looseEndCount;
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            if (role == null)
                continue;
            RoleUtils.sendWelcomeAnnouncement(player, role.identifier(), killerCount);
        }
    }

    protected void initModifier(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
            ServerLevel serverWorld) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        // 所有亡命徒都有矮小修饰符
        for (ServerPlayer player : players) {
            if (VariantLooseEndRole.isVariant(gameWorldComponent.getRole(player))) {
                worldModifierComponent.addModifier(player.getUUID(), SEModifiers.TINY, false);
                // worldModifierComponent.addModifier(player.getUUID(), SEModifiers.FEATHER,
                // false);
                // 使玩家缩小
                Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).removeModifier(tinyModifier);
                Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).addPermanentModifier(tinyModifier);
            }
        }
        // 一次性同步
        worldModifierComponent.sync();
    }

    /** 亡命徒特殊职业是否可以逃离虚空伤害并回到房间，true:可以抵御(超级亡命徒需要使用1层护盾，幽灵亡命徒不消耗) */
    public boolean tryRescueLooseEndVariantFromVoid(ServerPlayer player, SREGameWorldComponent gameWorldComponent) {
        SRERole role = gameWorldComponent.getRole(player);
        if (!VariantLooseEndRole.isVariant(role)
                || GameUtils.getPlayerRoomSpawnPos(player) == null) {
            return false;
        }

        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
        // 不是幽灵亡命徒（免疫虚空无需消耗护盾）且 无法触发护盾效果（护盾不够）  则无法从虚空中被拯救
        if (role != SpecialGameModeRoles.GHOST_LOOSE_END && !armor.triggerAndRemoveArmor(1)) {
            return false;
        }

        // 传送回房间
        GameUtils.teleportBackToRoom(player);
        // 重置动量
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        // player.setHealth(Math.max(player.getHealth(), 1.0F));
        // 提示护盾破损
        player.displayClientMessage(Component.translatable("message.bartender.armor_broke_self").withStyle(ChatFormatting.YELLOW), true);
        // 播放音效
        player.playNotifySound(TMMSounds.ITEM_PSYCHO_ARMOUR, SoundSource.MASTER, 5.0F, 1.0F);
        SRE.REPLAY_MANAGER.breakArmor(player.getUUID());
        return true;
    }

    protected void broadcastRolesAbility(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);
            // 变种亡命徒自带提示
            if (!VariantLooseEndRole.isVariant(role) && !EX_ABILITY_ROLE.contains(role))
                continue;
            BroadcastCommand.BroadcastMessage(player, Component.translatable(
                    "message.gamemode.evil_war.tip.role." + role.identifier().getPath())
                    .withStyle(ChatFormatting.GOLD));
        }
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        initModifier(players, gameWorldComponent, serverWorld);
        assignModdedRole(players, gameWorldComponent);
        broadcastRolesAbility(players, gameWorldComponent);

        int looseEndVariantCount = getLooseEndVariantCount(players);
        int beyondKillerCount = players.size() - looseEndVariantCount * (SREConfig.instance().evilWarKillGroupNumber + 1);

        // 初始化后处理 component
        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);

            // 资金初始化
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
            // 阴谋家首次获取资金时间推迟
            if (role == ModRoles.CONSPIRATOR) {
                playerShopComponent.setBalance(-200);
            }
            // dio初始资金减少
            else if (role == ModRoles.DIO) {
                playerShopComponent.setBalance(0);
            }
            // 变种亡命徒开局根据溢出的杀手数结算资金，每名溢出杀手给予 50
            else if (VariantLooseEndRole.isVariant(role)) {
                playerShopComponent.setBalance(beyondKillerCount * 50);
                // 清道夫开局700块
            } else if (role == ModRoles.CLEANER) {
                playerShopComponent.setBalance(700);
            }
            // 滞时鬼开局-100块防止开局出双刀
            else if (role == ModRoles.DELAYER) {
                playerShopComponent.setBalance(-100);
            }
            // 默认安全时间结束后有700（一般杀手狂暴400，手雷330，手枪285)，需要斟酌启动配置
            else if (role == THMiscRoles.MAMIZOU) {
                playerShopComponent.setBalance(200 + 1000);
            } else if (role == THMiscRoles.IBUKI_SUIKA || role == THMiscRoles.KOMACHI
                    || role == THMiscRoles.DOREMY || role == ModRoles.NIAOSHOU_SHOU
                    || role == ModRoles.SILENCER) {
                playerShopComponent.setBalance(200 + 500);
            } else if (role == ModRoles.YOULU) {
                playerShopComponent.setBalance(200 + 200);
            } else
                playerShopComponent.setBalance(200);

            // 角色添加初始特性
            // 组件等数据初始化
            if (role == ModRoles.IMITATOR) {
                // 模仿者初始化：随机3个技能，目前没有合适的公有修改方法
                ImitatorRoleData imitatorPlayerComponent = RoleData.getNullable(ImitatorRoleData.class, player);
                if (imitatorPlayerComponent != null) {
                    // 获取所有可复制技能并打乱
                    List<ResourceLocation> roleIds = new ArrayList<>(ImitatorSkillRegistry.ALLOWED_ROLES);
                    Collections.shuffle(roleIds);
                    for (int slotIndex = 0; slotIndex < ImitatorRoleData.MAX_SLOTS; ++slotIndex) {
                        // 每个槽插入一个技能
                        imitatorPlayerComponent.slotRoleId[slotIndex] = roleIds
                                .get(Math.min(slotIndex, roleIds.size() - 1));
                        imitatorPlayerComponent.slotCooldown[slotIndex] = 0;
                        imitatorPlayerComponent.slotFillOrder[slotIndex] = slotIndex;
                        imitatorPlayerComponent.activeSlotIndex = slotIndex;
                        imitatorPlayerComponent.filledSlots++;
                    }
                    imitatorPlayerComponent.sync();
                }
            }
            // 蕾米莉亚开局获得20分钟速度2
            else if (role == THRedHouseRoles.REMILIA) {
                if (player.hasEffect(MobEffects.MOVEMENT_SPEED))
                    player.removeEffect(MobEffects.MOVEMENT_SPEED);
                player.addEffect(
                        new MobEffectInstance(
                                MobEffects.MOVEMENT_SPEED, // 速度效果
                                20 * 1200, // 持续时间（tick）
                                2,
                                false, // 是否显示粒子效果
                                false // 是否显示图标
                        ));
            }
            // 削弱设陷者：开局即进入放置冷却，无法立刻布陷阱
            else if (role == ModRoles.TRAPPER) {
                TrapperRoleData trapperPlayerComponent = RoleData.getNullable(TrapperRoleData.class, player);
                if (trapperPlayerComponent != null) {
                    trapperPlayerComponent.tripwireCooldownTicks = TrapperRoleData.PLACE_COOLDOWN_TICKS;
                    trapperPlayerComponent.mudCooldownTicks = GameConstants.getInTicks(0, 45);
                    trapperPlayerComponent.netGunCooldownTicks = GameConstants.getInTicks(1, 30);
                    trapperPlayerComponent.sync();
                }
            }
            // 强盗开局自带1层护盾
            else if (role == ModRoles.BANDIT) {
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                armor.giveArmor();
            }
            // 叛徒有很厚的盾 初始速度快
            else if (role == TraitorAndModifiers.TRAITOR) {
                SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                armor.armor = 30;

                if (player.hasEffect(MobEffects.MOVEMENT_SPEED))
                    player.removeEffect(MobEffects.MOVEMENT_SPEED);
                player.addEffect(
                        new MobEffectInstance(
                                MobEffects.MOVEMENT_SPEED, // 速度效果
                                20 * 1200, // 持续时间（tick）
                                3,
                                false, // 是否显示粒子效果
                                false // 是否显示图标
                        ));
            }
            // 邪恶战争专属职业额外增益（护盾/弹幕/面具次数/物品/效果等）
            initEvilWarRoleBonuses(player, role);
        }

        // 所有增益处理完后 给所有人25s的速度2 方便调整位置（不会被亡命徒逮着薅）
        for (ServerPlayer player : players) {
            if (!player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,
                    25 * 20,
                    2,
                    false,
                    false));
            }
        }
        curBalanceTick = 0;
        curReviveTick = 0;
        curOneSecondTick = 0;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        tickEvilWarCooldowns(serverWorld, gameWorldComponent);

        // tick计数
        if (curOneSecondTick++ >= ONE_SECOND_TICK) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                    continue;
                SRERole role = gameWorldComponent.getRole(player);

                // 超级亡命徒速度效果降级，速度效果完全消失时死亡
                if (role == SpecialGameModeRoles.SUPER_LOOSE_END) {
                    MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
                    if (speed == null) {
                        GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                    } else if (speed.getDuration() < 2 * ONE_SECOND_TICK && speed.getAmplifier() > 2) {
                        player.removeEffect(MobEffects.MOVEMENT_SPEED);
                        player.addEffect(new MobEffectInstance(
                                MobEffects.MOVEMENT_SPEED,
                                1200 + speed.getDuration(),
                                speed.getAmplifier() - 2,
                                false,
                                false));
                    }
                }
                // 幽灵亡命徒如果隐身结束 则直接死亡 
                else if (role == SpecialGameModeRoles.GHOST_LOOSE_END
                        && !player.hasEffect(MobEffects.INVISIBILITY)) {
                    GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                }
            }
            curOneSecondTick = 0;
        }
        // 10s cd事件
        if (curReviveTick++ >= REVIVE_TIME) {
            boolean hasOtherKiller = false;
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                    continue;

                SRERole role = gameWorldComponent.getRole(player);
                // 仇杀客每10s涨一个误杀数
                if (role == ModRoles.BLOOD_FEUDIST) {
                    var bloodFeudistData = RoleData.getNullable(BloodFeudistRoleData.class, player);
                    if (bloodFeudistData != null)
                        bloodFeudistData.onAccidentalKill();
                }
                // 派对狂和扒手每10s额外获得200
                else if (role == SERoles.AVARICIOUS || role == ModRoles.PARTY_KILLER || role == ModRoles.NINJA) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(200);
                }
                // 毒师每10s获得一个催化剂减cd
                else if (role == ModRoles.POISONER) {
                    // 如果催化剂数量少于2个，则补一个
                    if (player.getInventory().countItem(ModItems.CATALYST) < 2) {
                        player.addItem(ModItems.CATALYST.getDefaultInstance());
                    }
                    ItemCooldowns itemCooldownManager = player.getCooldowns();
                    itemCooldownManager.removeCooldown(ModItems.CATALYST);
                }
                // 炸弹客手雷cd减为10s
                else if (role == ModRoles.BOMBER) {
                    ItemCooldowns itemCooldownManager = player.getCooldowns();
                    itemCooldownManager.removeCooldown(ModItems.BOMB);
                }

                // 反向检测
                // 每10s检测玩家身上是否有药丸，如果有则受到对应攻击，除了毒师
                if (role != ModRoles.POISONER) {
                    Inventory inventory = player.getInventory();
                    for (var item : inventory.items) {
                        if (item.isEmpty())
                            continue;
                        if (item.is(ModItems.PILL)) {
                            GameUtils.killPlayer(player, true, player, SRE.id("poison"));
                        }
                    }
                }
                // 如果没有非亡语杀手角色和亡命徒
                else if (role != ModRoles.INSANE_KILLER && !VariantLooseEndRole.isVariant(role)) {
                    hasOtherKiller = true;
                }
            }
            // 只剩亡语杀手,则自动结束装死
            if (!hasOtherKiller) {
                for (ServerPlayer player : serverWorld.players()) {
                    if (GameUtils.isPlayerEliminated(player))
                        continue;
                    SRERole role = gameWorldComponent.getRole(player);
                    if (role == ModRoles.INSANE_KILLER) {
                        InsaneKillerRoleData insaneKillerPlayerComponent = RoleData
                                .getNullable(InsaneKillerRoleData.class, player);
                        if (insaneKillerPlayerComponent != null && insaneKillerPlayerComponent.isActive) {
                            insaneKillerPlayerComponent.toggleAbility();
                        }
                    }
                }
            }

            curReviveTick = 0;
        }
        // 经济增长cd事件
        if (curBalanceTick++ >= ADD_BALANCE_TIME) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                    continue;

                // 给予角色金币
                SRERole role = gameWorldComponent.getRole(player);
                if (role == ModRoles.DIO) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(200);
                } else if (role == ModRoles.CONSPIRATOR) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(200);
                } else if (!VariantLooseEndRole.isVariant(role))
                    // 默认获取 500 金币
                    SREPlayerShopComponent.KEY.get(player).addToBalance(500);

                // 角色相关数据修改
                // 苦力怕每30s获得 2 颗雷
                if (role == BounsRoles.CREEPER) {
                    ItemStack creeperItem = TMMItems.GRENADE.getDefaultInstance();
                    creeperItem.setCount(2);
                    player.addItem(creeperItem);
                }
                // 判断是否是特定角色，进行特定操作，每30秒判断一次
                else if (role == ModRoles.STALKER) {
                    StalkerRoleData stalkerPlayerComponent = RoleData.getNullable(StalkerRoleData.class, player);
                    if (stalkerPlayerComponent != null) {
                        // 潜行每30s获得 500 能量
                        stalkerPlayerComponent.energy += 500;
                        // 每30s获得 4 击杀数
                        stalkerPlayerComponent.phase2Kills += 4;
                        stalkerPlayerComponent.sync();
                        // 30s获得1个盾
                        SREArmorPlayerComponent armor = SREArmorPlayerComponent.KEY.get(player);
                        armor.giveArmor();
                    }
                }
                // 刽子手每30秒重新锁定目标为超级亡命徒：现已能自动锁定
                else if (role == ModRoles.EXECUTIONER) {
                    // ExecutionerRoleData executionerRoleData =
                    // RoleData.getNullable(ExecutionerRoleData.class, player);
                    // for (ServerPlayer target : serverWorld.players())
                    // if (!GameUtils.isPlayerEliminated(target)
                    // && gameWorldComponent.isRole(target, SpecialGameModeRoles.SUPER_LOOSE_END)) {
                    // executionerRoleData.target = target.getUUID();
                    // executionerRoleData.targetSelected = true;
                    // executionerRoleData.sync();
                    // }

                    // 刽子手每30s回复德林加子弹和消耗的德林加
                    int leftDerringerCount = 0;
                    Inventory inventory = player.getInventory();
                    for (var item : inventory.items) {
                        if (item.isEmpty())
                            continue;
                        if (item.is(TMMItems.DERRINGER)) {
                            if (item.getOrDefault(SREDataComponentTypes.USED, false))
                                item.set(SREDataComponentTypes.USED, false);
                            ++leftDerringerCount;
                        }
                    }
                    // 补充掉落的德林加
                    if (leftDerringerCount < EXECUTIONER_GUN_NUMBER) {
                        ItemStack gun = new ItemStack(TMMItems.DERRINGER);
                        gun.setCount(EXECUTIONER_GUN_NUMBER - leftDerringerCount);
                        player.addItem(gun);
                    }
                }
            }
            curBalanceTick = 0;
        }

        boolean civilianAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            // check if some civilians are still alive
            if (gameWorldComponent.isInnocent(player) && !GameUtils.isPlayerEliminated(player)) {
                civilianAlive = true;
            }
        }
        // check killer win condition (killed all civilians)
        if (!civilianAlive) {
            winStatus = GameUtils.WinStatus.KILLERS;
        }

        // 检查场上是否存在亡命徒
        if (winStatus != GameUtils.WinStatus.NONE) {
            boolean hasLooseEndAlive = false;
            List<ServerPlayer> lastLooseEnds = new ArrayList<>();

            for (ServerPlayer player : serverWorld.players()) {
                if ((gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                        || VariantLooseEndRole.isVariant(gameWorldComponent.getRole(player)))
                        && !GameUtils.isPlayerEliminated(player)) {
                    hasLooseEndAlive = true;
                    lastLooseEnds.add(player);
                }
            }

            // 如果只有亡命徒存活，且没有其他存活玩家，触发亡命徒获胜
            if (hasLooseEndAlive) {
                // 检查是否有其他非亡命徒的存活玩家
                boolean hasOtherAlive = false;
                for (ServerPlayer player : serverWorld.players()) {
                    if ((!gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                            && !VariantLooseEndRole.isVariant(gameWorldComponent.getRole(player)))
                            && !GameUtils.isPlayerEliminated(player)) {
                        hasOtherAlive = true;
                        break;
                    }
                }
                if (!hasOtherAlive) {
                    winStatus = GameUtils.WinStatus.LOOSE_END;
                    // 补充 CustomWinnerID: loose_end
                    var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                    roundEnd.CustomWinnerID = "loose_end";
                    for (ServerPlayer looseEnd : lastLooseEnds)
                        roundEnd.CustomWinnerPlayers.add(looseEnd.getUUID());
                } else {
                    // 有其他玩家存活，游戏继续
                    winStatus = GameUtils.WinStatus.NONE;
                }
            }
        }

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        // GameUtils.WinStatus modifiedWinStatus =
        // AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, winStatus, false);
        // if (!modifiedWinStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
        // winStatus = modifiedWinStatus;
        // }
        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }

    @Override
    public boolean autoTriggerGameTrueStarted() {
        return false;
    }

    @Override
    public void gameStarted(ServerLevel serverWorld, SREGameWorldComponent gameComponent,
            ArrayList<ServerPlayer> readyPlayerList) {
        super.gameStarted(serverWorld, gameComponent, readyPlayerList);
        OnGameTrueStarted.EVENT.invoker().onGameTrueStarted(serverWorld);
    }

    // ==================== 邪恶战争专属职业适配 ====================

    /** 文文新闻（邪恶战争专属偷取技能）：偷取视线范围内目标背包中的一个物品 */
    static {
        RoleSkill.register(THMountainRoles.AYA,
                RoleSkill.skill(SRE.id("aya_steal"), "skill.noellesroles.aya_steal", ctx -> {
                    ServerPlayer player = ctx.player();
                    if (!(SREGameWorldComponent.KEY.get(player.level()).getGameMode() instanceof SREEvilWarGameMode)) {
                        return false;
                    }
                    ServerPlayer target = findLookAtPlayer(player, 6.0);
                    if (target == null) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.aya.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    ItemStack stolen = stealFrom(target);
                    if (stolen.isEmpty()) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.aya.empty")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    player.addItem(stolen);
                    return true;
                }).cooldownSeconds(30).showOnHud(true).announceToSelf().build());
    }

    /** 返回视线范围内、距离不超过 range 的最准目标玩家（用于文文偷取） */
    private static ServerPlayer findLookAtPlayer(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        ServerLevel level = player.serverLevel();
        double bestDot = Math.cos(Math.toRadians(35));
        ServerPlayer best = null;
        for (ServerPlayer p : level.players()) {
            if (p == player || GameUtils.isPlayerEliminated(p)) {
                continue;
            }
            Vec3 to = p.getEyePosition(1.0f).subtract(eye);
            double dist = to.length();
            if (dist > range || dist < 0.1) {
                continue;
            }
            double dot = to.normalize().dot(look);
            if (dot < bestDot) {
                continue;
            }
            bestDot = dot;
            best = p;
        }
        return best;
    }

    /** 从目标背包偷取一个物品：优先非信件/钥匙，否则任意物品 */
    private static ItemStack stealFrom(ServerPlayer victim) {
        Inventory inv = victim.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || isLetterOrKey(s)) {
                continue;
            }
            ItemStack taken = inv.removeItem(i, 1);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) {
                continue;
            }
            ItemStack taken = inv.removeItem(i, 1);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    private static boolean isLetterOrKey(ItemStack stack) {
        if (stack.is(TMMItems.LETTER)) {
            return true;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getPath().contains("key");
    }

    /** 开局初始化各杀手在邪恶战争中的额外增益（仅对邪恶战争生效） */
    private void initEvilWarRoleBonuses(ServerPlayer player, SRERole role) {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        if (VariantLooseEndRole.isVariant(role)) {
            player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,
                    GameConstants.getInTicks(2, 0), 0, false, true, true));
        } else if (role == ModRoles.WIZARD) {
            WizardRoleData wiz = RoleData.getNullable(WizardRoleData.class, player);
            if (wiz != null) {
                wiz.mana = wiz.maxMana();
                wiz.sync();
            }
        } else if (role == ModRoles.UNDEAD_LORD) {
            UndeadLordRoleData und = RoleData.getNullable(UndeadLordRoleData.class, player);
            if (und != null) {
                und.ignoreUndeadCap = true;
                und.boostUndeadSpeed = true;
                und.syncedMaxUndead = Integer.MAX_VALUE;
                und.sync();
            }
        } else if (role == ModRoles.SKINCRAWLER) {
            SkincrawlerRoleData sc = RoleData.getNullable(SkincrawlerRoleData.class, player);
            if (sc != null) {
                sc.blockCharges += 2;
                sc.sync();
            }
        } else if (role == THMiscRoles.IBUKI_SUIKA) {
            SREArmorPlayerComponent.KEY.get(player).setArmor(2);
        } else if (role == THMiscRoles.HATA_NO_KOKORO) {
            addHataMaskCharges(ability, 3);
        } else if (role == THMiscRoles.HAKUREI_REIMU) {
            for (int i = 0; i < 4; i++) {
                player.addItem(new ItemStack(ModItems.DANMUKU, 8));
            }
        } else if (role == ModRoles.DREAM) {
            if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
            }
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, Integer.MAX_VALUE, 1, false, true, true));
            if (player.hasEffect(MobEffects.DIG_SPEED)) {
                player.removeEffect(MobEffects.DIG_SPEED);
            }
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, Integer.MAX_VALUE, 1, false, true, true));
        } else if (role == ModRoles.EXAMPLER) {
            for (int i = 0; i < 2; i++) {
                player.addItem(new ItemStack(ModItems.ONCE_REVOLVER));
            }
        } else if (role == ModRoles.NOSTALGIST) {
            player.addItem(new ItemStack(ModItems.INCENDIARY_GRENADE));
        } else if (role == ModRoles.GANGSTERS) {
            player.addItem(new ItemStack(ModItems.C4, 4));
        } else if (role == ModRoles.TARTAGLIA) {
            player.addItem(new ItemStack(Items.ARROW, 64));
        } else if (role == ModRoles.BOOM_MANIAC) {
            ItemStack rocket = BoomManiacRole.createBoomFirework(FireworkExplosion.Shape.LARGE_BALL);
            rocket.setCount(64);
            player.addItem(rocket);
            player.addItem(rocket.copy());
        } else if (role == BounsRoles.PROGRAMMER) {
            player.addItem(new ItemStack(FunnyItems.TERMINAL, 4));
        }
    }

    /** 面灵气面具使用次数 +extra（4 张面具各 +extra） */
    private void addHataMaskCharges(SREAbilityPlayerComponent ability, int extra) {
        ability.ensureSkills(RoleSkill.getDefinitions(THMiscRoles.HATA_NO_KOKORO));
        for (String emotion : new String[] { "happiness", "anger", "sorrow", "joy" }) {
            SREAbilityPlayerComponent.SkillState st = ability.getSkillState(SRE.id("hata_no_kokoro/" + emotion));
            st.charges += extra;
            st.maxCharges += extra;
        }
        ability.sync();
    }

    /** 每 tick 处理特定杀手的技能/物品冷却（仅邪恶战争生效） */
    private void tickEvilWarCooldowns(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : serverWorld.players()) {
            if (GameUtils.isPlayerEliminated(player)) {
                continue;
            }
            SRERole role = gameWorldComponent.getRole(player);
            if (role == null) {
                continue;
            }
            SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
            // 按上限钳制而非周期清零，使客户端 HUD 能连续显示正确的复活冷却
            if (role == BounsRoles.CAT_NECROMANCER) {
                capAbilityCooldown(ability, CAT_NECROMANCER_COOLDOWN_TICKS);
            } else if (role == SERoles.NECROMANCER) {
                capAbilityCooldown(ability, NECROMANCER_COOLDOWN_TICKS);
            } else if (role == THMiscRoles.MAMIZOU) {
                ability.resetAllCooldowns();
            } else if (role == THMiscRoles.REIUJI_UTSUHO || role == THMiscRoles.KOMACHI
                    || role == THMiscRoles.KIJIN_SEIJA || role == THMiscRoles.YAKUMO_YUKARI) {
                capSkillCooldowns(ability, role);
            }
            // 观者切换姿态耗时 5 s，每次切换到宁静后重置护盾
            else if (role == ModRoles.WATCHER) {
                ResourceLocation stanceSkillId = SRE.id("watcher_stance");
                SREAbilityPlayerComponent.SkillState stanceState = ability.getSkillState(stanceSkillId);
                // 技能刚使用时会先写入普通模式冷却；只在这一刻处理姿态切换
                if (stanceState.cooldown > WATCHER_STANCE_COOLDOWN_TICKS) {
                    WatcherRoleData watcher = RoleData.getNullable(WatcherRoleData.class, player);
                    if (watcher != null && watcher.isInCalmStance()) {
                        SREArmorPlayerComponent.KEY.get(player).setArmor(1, true);
                    }
                }
                capSkillCooldown(ability, stanceSkillId, WATCHER_STANCE_COOLDOWN_TICKS);
            }
            if (role == THMiscRoles.HAKUREI_REIMU) {
                player.getCooldowns().removeCooldown(ModItems.DANMUKU);
            }
            if (role == ModRoles.BOOM_MANIAC) {
                player.getCooldowns().removeCooldown(Items.CROSSBOW);
            }
            if (role == BounsRoles.PROGRAMMER) {
                player.getCooldowns().removeCooldown(FunnyItems.TERMINAL);
            }
            if (role == ModRoles.UNDEAD_LORD) {
                ability.setCooldown(0);
            }
        }
    }

    /** 将旧版总冷却钳制到指定上限 */
    private void capAbilityCooldown(SREAbilityPlayerComponent ability, int cap) {
        if (ability.cooldown > cap) {
            ability.cooldown = cap;
            ability.sync();
        }
    }

    /** 将单个技能的当前冷却钳制到指定上限。 */
    private void capSkillCooldown(SREAbilityPlayerComponent ability, ResourceLocation skillId, int cap) {
        boolean changed = false;
        SREAbilityPlayerComponent.SkillState state = ability.getSkillState(skillId);
        if (state.cooldown > cap) {
            state.cooldown = cap;
            changed = true;
        }
        if (ability.cooldown > cap) {
            ability.cooldown = cap;
            changed = true;
        }
        if (changed) {
            ability.sync();
        }
    }

    /** 将角色所有技能冷却上限减半（每 tick 钳制，抵消注册时的固定冷却） */
    private void capSkillCooldowns(SREAbilityPlayerComponent ability, SRERole role) {
        List<RoleSkill.Definition> defs = RoleSkill.getDefinitions(role);
        int minHalf = Integer.MAX_VALUE;
        for (RoleSkill.Definition def : defs) {
            if (def.cooldownTicks() <= 0) {
                continue;
            }
            int cap = def.cooldownTicks() / 2;
            SREAbilityPlayerComponent.SkillState st = ability.getSkillState(def.id());
            if (st.cooldown > cap) {
                st.cooldown = cap;
            }
            if (cap < minHalf) {
                minHalf = cap;
            }
        }
        if (minHalf != Integer.MAX_VALUE && ability.cooldown > minHalf) {
            ability.cooldown = minHalf;
        }
        ability.sync();
    }
}
