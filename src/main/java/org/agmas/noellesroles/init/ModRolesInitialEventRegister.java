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

package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.harpymodloader.events.ModdedRoleRemoved;
import org.agmas.noellesroles.ConfigWorldComponent;
import org.agmas.noellesroles.RicesRoleRhapsody;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.PlayerVolumeComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.role_data.innocence.AccountantRoleData;
import org.agmas.noellesroles.role_data.innocence.AlchemistRoleData;
import org.agmas.noellesroles.game.roles.innocence.attendant.AttendantHandler;
import org.agmas.noellesroles.role_data.innocence.GhostRoleData;
import org.agmas.noellesroles.role_data.innocence.ClockmakerRoleData;
import org.agmas.noellesroles.role_data.innocence.NoiseMakerRoleData;
import org.agmas.noellesroles.role_data.innocence.ReturnTravelerRoleData;
import org.agmas.noellesroles.role_data.innocence.SaltedFishRoleData;
import org.agmas.noellesroles.role_data.killer.BloodFeudistRoleData;
import org.agmas.noellesroles.role_data.killer.DIORoleData;
import org.agmas.noellesroles.role_data.killer.DelayerRoleData;
import org.agmas.noellesroles.role_data.killer.MaChenXuRoleData;
import org.agmas.noellesroles.game.roles.killer.manipulator.InControlCCA;
import org.agmas.noellesroles.role_data.innocence.BuilderRoleData;
import org.agmas.noellesroles.role_data.innocence.FortunetellerRoleData;
import org.agmas.noellesroles.role_data.neutral.AmonRoleData;
import org.agmas.noellesroles.role_data.neutral.CandleBearerRoleData;
import org.agmas.noellesroles.role_data.neutral.CuckooRoleData;
import org.agmas.noellesroles.role_data.neutral.RecorderRoleData;
import org.agmas.noellesroles.role_data.neutral.MorticianBodyMakerRoleData;
import org.agmas.noellesroles.role_data.innocence.LeatherPigRoleData;
import org.agmas.noellesroles.role_data.innocence.MagicianRoleData;
import org.agmas.noellesroles.role_data.killer.StalkerRoleData;
import org.agmas.noellesroles.role_data.killer.TrapperRoleData;
import org.agmas.noellesroles.role_data.killer.WraithAssassinRoleData;
import org.agmas.noellesroles.game.roles.neutral.commander.CommanderHandler;
import org.agmas.noellesroles.role_data.killer.BomberRoleData;
import org.agmas.noellesroles.role_data.killer.WarlockRoleData;
import org.agmas.noellesroles.role_data.killer.PartyRoleData;
import org.agmas.noellesroles.role_data.killer.SpellbreakerRoleData;
import org.agmas.noellesroles.role_data.killer.WatcherRoleData;
import org.agmas.noellesroles.role_data.killer.YouluRoleData;
import org.agmas.noellesroles.role_data.neutral.NianShouRoleData;
import org.agmas.noellesroles.role_data.neutral.PelicanRoleData;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.role_data.neutral.ThiefRoleData;
import org.agmas.noellesroles.role_data.neutral.VultureRoleData;
import org.agmas.noellesroles.role_data.neutral.VoiceChangerRoleData;
import org.agmas.noellesroles.role_data.neutral.PhantomMusicianRoleData;
import org.agmas.noellesroles.role_data.killer.ImitatorRoleData;
import org.agmas.noellesroles.role_data.special.SuperLooseEndRoleData;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.role.touhou.THLostForestRoles;
import org.agmas.noellesroles.role.touhou.THMiscRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.UUID;

public class ModRolesInitialEventRegister {

    public static void register() {

        // 初始化亡灵之主事件（亡者复苏 / 角色初始化）
        org.agmas.noellesroles.game.roles.killer.undead_lord.UndeadLordHandler.init();

        // 初始化仇杀客事件
        BloodFeudistRoleData.registerEvents();
        // 初始化皮革噶的事件（疯魔推开致死→平民则小脑归因）
        LeatherPigRoleData.registerEvents();
        // 初始化操纵师操控限制（被拖入水/岩浆/虚空/摔落致死时否决并弹回）
        InControlCCA.registerEvents();
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(player);
            // 通用：设置职业的初始金币数（未配置则不改变，默认 -1）
            int initialCoin = role.getInitialCoinCount();
            if (initialCoin >= 0) {
                SREPlayerShopComponent.KEY.get(player).setBalance(initialCoin);
            }
            if (RoleUtils.compareRole(role, ModRoles.CONSPIRATOR)) {
                ModEventsRegister.reJudgeSpectatorsPenalty(player.level());
            }
            if (role.identifier().equals(ModRoles.BARTENDER.identifier())) {
                FoodDrinkGlowComponent.KEY.get(player).init();
            }
            // 魔术师角色初始化
            if (role.identifier().equals(ModRoles.CHEF.identifier())) {
                FoodDrinkGlowComponent.KEY.get(player).init();
            }
            if (RoleUtils.compareRole(role, THLostForestRoles.KAGUYA)
                    || RoleUtils.compareRole(role, ModRoles.MAGICIAN)) {
                var magicianComponent = RoleData.getNullable(MagicianRoleData.class, player);
                {
                    // 停止疯狂模式（如果之前存在）
                    var psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
                    if (psychoComponent != null) {
                        psychoComponent.clear();
                    }
                    // 随机分配一个杀手身份给魔术师（原版杀手、毒师和清道夫除外）
                    if (magicianComponent != null) {
                        magicianComponent.startDisguiseRandomRole();
                    }
                }
                // 检查是否有指挥官，如果有则加入指挥官频道
                boolean hasCommander = player.getServer().getPlayerList().getPlayers().stream()
                        .anyMatch(p -> {
                            SREGameWorldComponent gw = SREGameWorldComponent.KEY.get(p.level());
                            var ro = gw.getRole(p);
                            if (ro != null) {
                                return ro.identifier().equals(ModRoles.COMMANDER_ID);
                            }
                            return false;
                        });
                if (hasCommander) {
                    // 魔术师加入指挥官频道
                    player.sendSystemMessage(Component.translatable("message.magician.commander_present_joined_channel")
                            .withStyle(ChatFormatting.GOLD));
                }
            }

            if (role.identifier().equals(ModRoles.DIO.identifier())) {
                // 初始化由 RoleData 在职业赋予时自动处理
            }
            if (role.identifier().equals(THRedHouseRoles.FURANDORU.identifier())) {
                // 初始化由 RoleData 在职业赋予时自动处理
            }
            if (role.identifier().equals(THRedHouseRoles.MAID_SAKUYA.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            if (role.identifier().equals(ModRoles.JOJO.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            // 初始化记录员
            if (role.identifier().equals(ModRoles.RECORDER.identifier())) {
                var recorderData = RoleData.getNullable(RecorderRoleData.class, player);
                if (recorderData != null) {
                    recorderData.initRecorder();
                }
            }
            if (role.identifier().equals(ModRoles.EXAMPLER.identifier())) {
                var tpc = SREAbilityPlayerComponent.KEY.get(player);
                tpc.init(false);
                tpc.status = 0;
                tpc.sync();
                return;
            }
            if (role.identifier().equals(ModRoles.THIEF.identifier())) {
                int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                var tpc = RoleData.getNullable(ThiefRoleData.class, player);
                if (tpc != null) {
                    tpc.updateHonorCost(totalPlayers);
                }
            }
            if (role.identifier().equals(ModRoles.MERCENARY.identifier())) {
                // 佣兵数据在职业赋予时由 RoleData.init() 自动初始化
            }
            if (role.identifier().equals(ModRoles.WAYFARER.identifier())) {
                MCItemsUtils.clearItem(player);
                RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_REVOLVER.getDefaultInstance());
                RoleUtils.insertStackInFreeSlot(player, ModItems.FAKE_KNIFE.getDefaultInstance());
                // (WayfarerPlayerComponent.KEY.get(player)).reset();
                return;
            }
            if (role.identifier().equals(ModRoles.WIND_YAOSE.identifier())) {
                // 现在在NoellesRolesAbilityPlayerComponent serverTick中处理。
                return;
            }
            if (role.identifier().equals(ModRoles.ACCOUNTANT.identifier())) {
                // 会计角色初始化（init 由 RoleData 在职业赋予时自动调用）
                return;
            }
            if (role.identifier().equals(ModRoles.ALCHEMIST.identifier())) {
                // 药剂师角色初始化（init 由 RoleData 在职业赋予时自动调用）
                return;
            }
            // 派对狂角色初始化 - 基于开局玩家数设置threshold
            if (role.identifier().equals(ModRoles.PARTY_KILLER.identifier())) {
                int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                RoleData.getOptional(PartyRoleData.class, player).ifPresent(pc -> pc.initThreshold(totalPlayers));
                return;
            }
            if (role.identifier().equals(TMMRoles.KILLER.identifier())) {
                player.addItem(TMMItems.KNIFE.getDefaultInstance().copy());
                return;
            }
            if (role.identifier().equals(TMMRoles.VIGILANTE.identifier())) {
                if (!SREItemUtils.hasItem(player, TMMItems.REVOLVER)) {
                    player.addItem(TMMItems.REVOLVER.getDefaultInstance().copy());
                }
                return;
            }
            if (role.identifier().equals(ModRoles.SHERIFF_ID)) {
                // 警卫角色初始化：重置任务计数
                return;
            }
            if (role.identifier().equals(ModRoles.ATTENDANT.identifier())) {
                if (player instanceof ServerPlayer sp)
                    SRE.SendRoomInfoToPlayer(sp);
                return;
            }
            if (role.identifier().equals(ModRoles.GUEST_GHOST.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }

            if (role.equals(ModRoles.BROADCASTER)) {
                abilityPlayerComponent.cooldown = 0;
                SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                playerShopComponent.setBalance(200);
                playerShopComponent.sync();
            }
            if (role.equals(ModRoles.EXECUTIONER)) {
                SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                        .get(player);
                playerShopComponent.setBalance(100);
            }
            if (role.equals(ModRoles.VULTURE)) {
                VultureRoleData vulturePlayerComponent = RoleData.getNullable(VultureRoleData.class, player);
                if (vulturePlayerComponent != null) {
                    vulturePlayerComponent.bodiesRequired = Math.max(1, (int) ((player.level().players().size() / 3f)
                            - Math.floor(player.level().players().size() / 6f)));
                    vulturePlayerComponent.sync();
                }
            }
            if (role.equals(ModRoles.PELICAN)) {
                var pelicanComponent = RoleData.getNullable(PelicanRoleData.class, player);
                if (pelicanComponent != null) {
                    int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                    double percent = NoellesRolesConfig.HANDLER.instance().pelicanEatPercentage;
                    pelicanComponent.requiredEaten = Math.max(1,
                            (int) Math.ceil(totalPlayers * (percent / 100.0D)) - 1);
                    pelicanComponent.sync();
                }
            }
            if (role.equals(ModRoles.INSANE_KILLER)) {
                // 疯狂杀手数据在职业赋予时由 RoleData.init() 自动初始化
            }
            if (role.equals(ModRoles.RECORDER)) {
                final var recorderPlayerComponent = RoleData.getNullable(RecorderRoleData.class, player);
                if (recorderPlayerComponent != null) {
                    recorderPlayerComponent.initializeRoles();
                }
            }

            // 更新所有记录员的可用角色列表
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                if (SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.RECORDER)) {
                    var recorderData = RoleData.getNullable(RecorderRoleData.class, p);
                    if (recorderData != null) {
                        recorderData.updateAvailableRoles();
                    }
                }
            }
            // 记录员数据在职业赋予时由 RoleData.init() 自动初始化

            if (role.equals(ModRoles.GAMBLER)) {
                // 赌徒数据在职业赋予时由 RoleData.init() 自动初始化
            }

            if (role.equals(ModRoles.NOISEMAKER)) {
                // init 由 RoleData 在职业赋予时自动调用
            }
            if (role.equals(ModRoles.CANDLE_BEARER)) {
                // 烛台数据在职业赋予时由 RoleData.init() 自动初始化
                RoleUtils.insertStackInFreeSlot(player, Items.CANDLE.getDefaultInstance());
            }
            if (role.equals(ModRoles.CAKE_MAKER)) {
                ModComponents.CAKE_MAKER.get(player).init();
            }
            if (role.equals(ModRoles.AMON)) {
                // 阿蒙数据在职业赋予时由 RoleData.init() 自动初始化
            }
            // 操纵师角色初始化（init 由 RoleData 在职业赋予时自动调用）
            // 巫毒师角色初始化 - 开局75秒冷却
            if (role.equals(ModRoles.VOODOO)) {
                abilityPlayerComponent.cooldown = 100 * 20;
                abilityPlayerComponent.sync();
                return;
            }
            // if (role.equals(SHERIFF)) {
            // player.giveItemStack(TMMItems.REVOLVER.getDefaultStack());
            // org.agmas.noellesroles.game.roles.sheriff.SheriffPlayerComponent
            // sheriffPlayerComponent =
            // org.agmas.noellesroles.game.roles.sheriff.SheriffPlayerComponent.KEY.get(player);
            // sheriffPlayerComponent.reset();
            // sheriffPlayerComponent.sync();
            // }
            // 在角色分配时清除之前的跟踪者状态（如果有）
            // 但是如果跟踪者正在进化（切换角色），不清除状态
            StalkerRoleData stalkerComp = RoleData.getNullable(StalkerRoleData.class, player);
            if (stalkerComp != null && !stalkerComp.isActiveStalker()) {
                stalkerComp.clearAll();
            }

            // // 在角色分配时清除之前的傀儡师状态（如果有）
            // // 但是如果傀儡师正在操控假人（临时切换角色），不清除状态
            // PuppeteerPlayerComponent puppeteerComp = ModComponents.PUPPETEER.get(player);
            // if (!puppeteerComp.isPuppeteerMarked) {
            // puppeteerComp.clearAll();
            // }
            RicesRoleRhapsody.onRoleAssigned(player, role);
            if (role.identifier().equals(ModRoles.ELF.identifier())) {
                SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(player);
                shopComponent.setBalance(45);
                return;
            }

            // 纵火犯物品初始化
            if (role.equals(SERoles.ARSONIST)) {
                player.addItem(SEItems.JERRY_CAN.getDefaultInstance().copy());
                player.addItem(SEItems.LIGHTER.getDefaultInstance().copy());
            }
            if (role.equals(ModRoles.PUPPETEER)) {
                var comc = PuppeteerPlayerComponent.KEY.maybeGet(player).orElse(null);
                if (comc != null) {
                    if (!comc.isActivePuppeteer())
                        comc.init();
                }
            }
            // 画家角色初始化（init 由 RoleData 在职业赋予时自动调用）
            // 葬仪角色初始化（数据由 RoleData 在职业赋予时自动初始化）
            // 幻音师角色初始化（init 由 RoleData 在职业赋予时自动调用）
            if (role.equals(ModRoles.GODFATHER)) {
                if (player instanceof ServerPlayer sp) {
                    for (var p : sp.serverLevel().players()) {
                        if (p != null) {
                            p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
                        }
                    }
                }
            }
            // 如果不拦截就同步
            abilityPlayerComponent.sync();
        });

        // 四季映姬离开职业时，清除德林加手枪
        // (哪来的刀)
        ModdedRoleRemoved.EVENT.register((player, role) -> {
            if (RoleUtils.compareRole(role, THMiscRoles.SHIKIEIKI)) {
                SREItemUtils.clearItem(player, (stack) -> stack.is(TMMItems.DERRINGER));
            }
        });
    }

    static {
        // 宿命的罪人技能注册：
        // 技能 1「命运的启示」(G)：近距离查看准星目标最近 3 次杀人方式
        // 技能 2「重启」(潜行+技能键)：随机死因死亡脱离，回房间 + 短暂无敌
        RoleSkill.register(ModRoles.DOOMED_SINNER,
                RoleSkill.skill(SRE.id("doomed_sinner_revelation"),
                        "skill.noellesroles.doomed_sinner.revelation",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) {
                                return false;
                            }
                            ServerPlayer target = context.target() != null
                                    && player.level().getPlayerByUUID(context.target()) instanceof ServerPlayer sp
                                            ? sp
                                            : null;
                            return org.agmas.noellesroles.role_data.neutral.DoomedSinnerRoleData
                                    .revealFate(player, target);
                        }).cooldownSeconds(40).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("doomed_sinner_reboot"),
                        "skill.noellesroles.doomed_sinner.reboot",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) {
                                return false;
                            }
                            return org.agmas.noellesroles.role_data.neutral.DoomedSinnerRoleData
                                    .reboot(player);
                        }).cooldownSeconds(75).shifted(true).showOnHud(true).announceToSelf(true).build());

        RoleSkill.register(ModRoles.LIN_FAMILY,
                RoleSkill.skill(SRE.id("lin_family_generosity"),
                        "skill.noellesroles.lin_family.generosity",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) {
                                return false;
                            }
                            ServerPlayer target = context.getTargetAsPlayer();
                            return org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData
                                    .useGenerosity(player, target);
                        }).cooldownSeconds(45).withTarget().showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("lin_family_collector"),
                        "skill.noellesroles.lin_family.collector",
                        context -> {
                            ServerPlayer player = context.player();
                            if (player.isSpectator()) {
                                return false;
                            }
                            return org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData
                                    .useCollector(player);
                        }).cooldownSeconds(90).shifted(true).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(org.agmas.noellesroles.role_data.neutral.LinFamilyRoleData.MACHINE_SKILL_ID,
                        "skill.noellesroles.lin_family.machine",
                        context -> false)
                        .cooldownSeconds(60).shifted(true).noCastCCA(true)
                        .showOnHud(true).announceToSelf(false).build());

        // 疫使技能注册：按技能键感染目标玩家
        RoleSkill.register(ModRoles.INFECTED, RoleSkill.skill(
                SRE.id("infected_infect"),
                "skill.noellesroles.infected.infect",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();

                    if (targetUuid == null) {
                        return false;
                    }

                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (target == null) {
                        return false;
                    }

                    if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                        return false;
                    }

                    InfectedPlayerComponent targetComponent = ModComponents.INFECTED.get(target);
                    if (targetComponent.infectedTicks > 0) {
                        return false;
                    }

                    targetComponent.infect(player);

                    if (NRSounds.INFECTED_INFECT != null) {
                        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                                NRSounds.SYRINGE_STAB, SoundSource.MASTER, 0.5f, 0.5f);
                    }
                    if (context.abilityCCA().status == 2) {
                        context.setSkillCooldown(20 * 10);
                        return false;
                    }
                    return true;
                }).cooldownSeconds(80).build());

        // 鹈鹕技能注册：按技能键吞噬鼠标准星对准的玩家，蹲下按技能键释放最后吞噬的玩家
        RoleSkill.register(ModRoles.PELICAN,
                RoleSkill.skill(SRE.id("pelican_eat"), "skill.noellesroles.pelican.eat", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    PelicanRoleData comp = RoleData.getNullable(PelicanRoleData.class, player);
                    if (comp == null || context.target() == null)
                        return false;
                    Player candidate = player.level().getPlayerByUUID(context.target());
                    if (!(candidate instanceof ServerPlayer target)
                            || !GameUtils.isPlayerAliveAndSurvival(target)
                            || player.distanceToSqr(target) > 2.15D * 2.15D
                            || !player.hasLineOfSight(target)) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.pelican.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    return comp.tryEat(target);
                    // 不在此处设统一技能冷却：统一技能系统无论 handler 是否成功都会进入冷却
                    // （见 RoleSkill.useUnified），会导致"没吃到人也进CD"。鹈鹕冷却由
                    // PelicanRoleData.eatCooldownUntil 管理，仅在成功吞噬后生效（并由 PelicanHud 显示）。
                }).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("pelican_release"), "skill.noellesroles.pelican.release", context -> {
                    PelicanRoleData comp = RoleData.getNullable(PelicanRoleData.class, context.player());
                    return comp != null && comp.releaseLast();
                }).shifted(true).announceToSelf(false).build());

        // 阿蒙技能：
        // - G 键：对准星玩家静默种下时之虫（附身期间也可为其他人种虫）
        // - 潜行+技能键 键：附身期间完成夺舍（变成目标、令其死亡、本体处生成尸体）
        RoleSkill.register(ModRoles.AMON,
                RoleSkill.skill(SRE.id("amon_plant_seed"), "skill.noellesroles.amon.plant_seed", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var comp = RoleData.getNullable(AmonRoleData.class, player);
                    if (comp == null)
                        return false;
                    // G 键始终执行种时之虫（附身期间不夺舍，夺舍改用 潜行+技能键）
                    if (!context.skillReady())
                        return false;
                    ServerPlayer target = context.target() == null ? null
                            : (player.level().getPlayerByUUID(context.target()) instanceof ServerPlayer sp ? sp : null);
                    return comp.plantSeed(target);
                }).cooldownSeconds(20).toggleable(true).announceToSelf(false).build(),

                // 潜行+技能键：附身期间完成夺舍
                RoleSkill.skill(SRE.id("amon_usurp"), "skill.noellesroles.amon.usurp", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var comp = RoleData.getNullable(AmonRoleData.class, player);
                    if (comp == null)
                        return false;
                    if (!comp.isPossessing())
                        return false;
                    return comp.finalizePossession();
                }).shifted(true).announceToSelf(false).build());

        // 葬仪技能注册：使用当前模式的技能
        RoleSkill.register(ModRoles.MORTICIAN_BODYMAKER, context -> {
            ServerPlayer player = context.player();
            MorticianBodyMakerRoleData morticianComponent = RoleData.getNullable(MorticianBodyMakerRoleData.class,
                    player);
            if (morticianComponent != null) {
                morticianComponent.useAbility();
            }
        });

        // 咒术师技能注册（重做版）：窃取发肤（G）/ 蚀骨之咒（V 切换）/ 领域展开（潜行+技能键）
        org.agmas.noellesroles.game.roles.killer.warlock.WarlockDomainManager.register();
        RoleSkill.register(ModRoles.WARLOCK,
                RoleSkill.skill(SRE.id("warlock_steal"), "skill.noellesroles.warlock.steal", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var comp = RoleData.getNullable(WarlockRoleData.class, player);
                    if (comp == null)
                        return false;
                    ServerPlayer target = context.target() != null
                            && player.level().getPlayerByUUID(context.target()) instanceof ServerPlayer sp ? sp : null;
                    return comp.trySteal(target);
                }).cooldownSeconds(18).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("warlock_curse"), "skill.noellesroles.warlock.curse", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    var comp = RoleData.getNullable(WarlockRoleData.class, player);
                    if (comp == null)
                        return false;
                    ServerPlayer target = context.target() != null
                            && player.level().getPlayerByUUID(context.target()) instanceof ServerPlayer sp ? sp : null;
                    return comp.tryCurse(target);
                }).cooldownSeconds(45).showOnHud(true).build());
        // 领域展开（技能三）改为在背包 LimitedInventoryScreen 点选已被诅咒且存活的目标触发，
        // 见 WarlockRoleScreenExtension / WarlockDomainWidget /
        // WarlockDomainC2SPacket（冷却记在组件里，60s）。

        // Dream（梦魇）技能注册：制酒 —— 酿一瓶酒，喝下隐身10s（期间无法攻击/无法受伤）
        RoleSkill.register(ModRoles.DREAM,
                RoleSkill.skill(SRE.id("dream_brew"), "skill.noellesroles.dream.brew", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(player))
                        return false;
                    // 已持有酒时不能再酿新的
                    if (io.wifi.starrailexpress.util.SREItemUtils.hasItem(player, ModItems.DREAM_WINE)) {
                        player.displayClientMessage(net.minecraft.network.chat.Component
                                .translatable("message.noellesroles.dream.brew_already_has")
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
                        return false;
                    }
                    if (!io.wifi.starrailexpress.util.SREItemUtils.insertStackInFreeSlot(player,
                            ModItems.DREAM_WINE.getDefaultInstance())) {
                        player.displayClientMessage(net.minecraft.network.chat.Component
                                .translatable("message.noellesroles.dream.brew_no_space")
                                .withStyle(net.minecraft.ChatFormatting.RED), true);
                        return false;
                    }
                    player.level().playSound(null, player.blockPosition(),
                            net.minecraft.sounds.SoundEvents.BREWING_STAND_BREW,
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 1.0f);
                    player.displayClientMessage(net.minecraft.network.chat.Component
                            .translatable("message.noellesroles.dream.brew_done")
                            .withStyle(net.minecraft.ChatFormatting.GREEN), true);
                    return true;
                }).cooldownSeconds(NoellesRolesConfig.instance().dreamBrewCooldownSeconds)
                        .showOnHud(true).announceToSelf(true).build());

        // 幽露（Youlu）G 键技能：【魂游】—— 第一次按 G 进入自由摄像机（返回 false 不进冷却），
        // 再按 G 在摄像机位置生成球烟并进入 60s 冷却；ESC 取消由 YouluFreeCamCancelC2SPacket 处理。
        RoleSkill.register(ModRoles.YOULU,
                RoleSkill.skill(SRE.id("youlu_freecam"), "skill.noellesroles.youlu.freecam", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    return RoleData.getOptional(YouluRoleData.class, player)
                            .map(rd -> rd.useCamSkill(player)).orElse(false);
                }).cooldownSeconds(60).showOnHud(true).announceToSelf(true).build());

        // 滞时鬼（Delayer）技能注册：【时间锚点】——消耗金币锚定当前状态，
        // delayerRewindDelaySeconds 秒后自动沿原路平滑回溯（详见 DelayerRoleData）。
        RoleSkill.register(ModRoles.DELAYER,
                RoleSkill.skill(SRE.id("delayer_anchor"), "skill.noellesroles.delayer.anchor", context -> {
                    ServerPlayer player = context.player();
                    if (player.isSpectator())
                        return false;
                    if (!GameUtils.isPlayerAliveAndSurvival(player))
                        return false;
                    var delayer = RoleData.getNullable(DelayerRoleData.class, player);
                    if (delayer == null || delayer.isAnchored())
                        return false; // 已锚定，等待回溯
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    int cost = 75;
                    if (shop.balance < cost) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.delayer.no_money", cost)
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.balance -= cost;
                    shop.sync();
                    delayer.anchor();
                    return true; // 进入冷却
                }).cooldownSeconds(120)
                        .showOnHud(true).build());

        // 幻音师技能注册：花费100金币传送到30格外随机一人的身边
        RoleSkill.register(ModRoles.PHANTOM_MUSICIAN, context -> {
            ServerPlayer player = context.player();
            var comp = RoleData.getNullable(PhantomMusicianRoleData.class, player);
            if (comp == null)
                return;
            comp.useTeleport();
        });

        // 海王技能注册：20格外水下玩家施加禁锢效果5秒，冷却60秒
        RoleSkill.register(ModRoles.SEA_KING, RoleSkill.skill(
                SRE.id("sea_king_aoe"),
                "skill.noellesroles.sea_king.aoe",
                context -> {
                    ServerPlayer player = context.player();
                    final double radius = 20.0D;
                    final int duration = 5 * 20;
                    int affected = 0;

                    for (var target : player.serverLevel().getEntitiesOfClass(
                            ServerPlayer.class,
                            player.getBoundingBox().inflate(radius),
                            p -> !p.getUUID().equals(player.getUUID()) && GameUtils.isPlayerAliveAndSurvival(p))) {
                        if (player.distanceToSqr(target) > radius * radius) {
                            continue;
                        }
                        if (!(target.isInWater() || target.isUnderWater())) {
                            continue;
                        }

                        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, duration, 0, false, true, false));
                        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, duration, 0, false, true, false));
                        target.addEffect(new MobEffectInstance(ModEffects.USED_BANED, duration, 0, false, true, false));
                        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, duration, 0, false, true, false));
                        affected++;
                    }

                    player.level().playSound(null, player.blockPosition(),
                            SoundEvents.TRIDENT_RETURN, SoundSource.MASTER, 5.0F, 1.0F);

                    if (affected > 0) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.sea_king.skill_used", affected)
                                        .withStyle(ChatFormatting.AQUA),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.sea_king.skill_no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }

                    return true; // 始终进入冷却
                }).cooldownSeconds(60).build());

        // 清洁工技能注册：清除附近5格外掉落物，冷却90秒
        RoleSkill.register(ModRoles.CLEANER, RoleSkill.skill(
                SRE.id("cleaner_cleanup"),
                "skill.noellesroles.cleaner.cleanup",
                context -> {
                    ServerPlayer player = context.player();
                    var items = player.level().getEntitiesOfClass(ItemEntity.class,
                            player.getBoundingBox().inflate(5.0), (p) -> true);
                    int count = 0;
                    for (var it : items) {
                        it.discard();
                        count++;
                    }
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                            SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F,
                            1.0F + player.level().random.nextFloat() * 0.1F - 0.05F);
                    player.displayClientMessage(Component.translatable(
                            "message.noellesroles.cleaner.cleanned", count)
                            .withStyle(ChatFormatting.GOLD), true);
                    return true;
                }).cooldownSeconds(90).build());

        // 布谷鸟技能注册：在脚下放置蛋，冷却20秒
        RoleSkill.register(ModRoles.CUCKOO, RoleSkill.skill(
                SRE.id("cuckoo_place_egg"),
                "skill.noellesroles.cuckoo.place_egg",
                context -> {
                    ServerPlayer player = context.player();
                    if (!(player instanceof ServerPlayer sp))
                        return false;
                    var comp = RoleData.getNullable(CuckooRoleData.class, player);
                    if (comp == null)
                        return false;
                    return comp.placeEgg(sp);
                }).cooldownSeconds(20).build());

        // 风妖精技能注册：30格外玩家降低音量10秒，冷却120秒
        RoleSkill.register(ModRoles.WIND_YAOSE, RoleSkill.skill(
                SRE.id("wind_yaose_volume"),
                "skill.noellesroles.wind_yaose.volume",
                context -> {
                    ServerPlayer player = context.player();
                    for (var p : player.level().players()) {
                        if (p.distanceTo(player) <= 30.0) {
                            PlayerVolumeComponent.KEY.get(p).setVolume(600, 0.05f);
                        }
                    }
                    return true;
                }).cooldownSeconds(120).build());

        // 噪音制造者技能注册：制造噪音，冷却60秒
        RoleSkill.register(ModRoles.NOISEMAKER, RoleSkill.skill(
                SRE.id("noisemaker_ability"),
                "skill.noellesroles.noisemaker.ability",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = RoleData.getNullable(NoiseMakerRoleData.class, player);
                    if (comp == null)
                        return false;
                    comp.useAbility(); // 组件内部已管理效果逻辑
                    return true;
                }).cooldownSeconds(60).build());

        // 小透明技能注册：隐身，冷却20秒，消耗150金币
        RoleSkill.register(ModRoles.GHOST, RoleSkill.skill(
                SRE.id("ghost_invisibility"),
                "skill.noellesroles.ghost.invisibility",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = RoleData.getNullable(GhostRoleData.class, player);
                    if (comp == null)
                        return false;
                    if (!comp.abilityUnlocked) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.ghost.not_unlocked")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    return comp.useAbility();
                }).cooldownSeconds(20).build());

        // 点灯人技能注册：隐身（无统一充数限制，次数由组件内部管理）
        RoleSkill.register(ModRoles.CANDLE_BEARER, RoleSkill.skill(
                SRE.id("candlebearer_invisibility"),
                "skill.noellesroles.candlebearer.invisibility",
                context -> {
                    ServerPlayer player = context.player();
                    var comp = RoleData.getNullable(CandleBearerRoleData.class, player);
                    if (comp == null)
                        return false;
                    return comp.useAbility();
                }).build());

        // 破魔师技能注册：沉默50格外非杀手玩家，冷却130秒
        RoleSkill.register(ModRoles.SPELLBREAKER, RoleSkill.skill(
                SRE.id("spellbreaker_silence"),
                "skill.noellesroles.spellbreaker.silence",
                context -> {
                    ServerPlayer player = context.player();
                    RoleData.getOptional(SpellbreakerRoleData.class, player)
                            .ifPresent(SpellbreakerRoleData::useAbility);
                    return true;
                }).cooldownSeconds(130).build());

        // 侍者技能注册：开启灯光，冷却60秒
        RoleSkill.register(ModRoles.ATTENDANT, RoleSkill.skill(
                SRE.id("attendant_light"),
                "skill.noellesroles.attendant.light",
                context -> {
                    ServerPlayer player = context.player();
                    AttendantHandler.openLight(player);
                    return true;
                }).cooldownSeconds(60).build());

        // 守望者技能注册：切换姿态
        RoleSkill.register(ModRoles.WATCHER, RoleSkill.skill(
                SRE.id("watcher_stance"),
                "skill.noellesroles.watcher.stance",
                context -> {
                    ServerPlayer player = context.player();
                    RoleData.getOptional(WatcherRoleData.class, player).ifPresent(WatcherRoleData::toggleStance);
                    return true;
                }).cooldownSeconds(30).build());

        // 方名美铃技能注册：可切换飘浮效果，冷却60秒
        // RoleSkill.register(RedHouseRoles.HOAN_MEIRIN, RoleSkill.skill(
        // SRE.id("hoan_meirin_levitation"),
        // "skill.hoan_meirin.levitation",
        // context -> {

        // return true;
        // }).cooldownSeconds(60).toggleable(true).build());

        // 窃贼技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式
        RoleSkill.register(ModRoles.THIEF,
                RoleSkill.skill(SRE.id("thief_ability"),
                        "skill.noellesroles.thief.ability",
                        context -> {
                            ThiefRoleData thiefData = RoleData.getNullable(ThiefRoleData.class, context.player());
                            return thiefData != null && thiefData.useAbility();
                        }).build(),
                RoleSkill.skill(SRE.id("thief_toggle_mode"),
                        "skill.noellesroles.thief.toggle_mode",
                        context -> {
                            ThiefRoleData thiefData = RoleData.getNullable(ThiefRoleData.class, context.player());
                            if (thiefData != null) {
                                thiefData.toggleMode();
                            }
                            return true;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).showOnHud(false).build());

        // 变声怪杰技能注册：
        // - 蹲下+技能键(G)：标记准星玩家（冷却20秒）
        // - 技能键(G)：对全部被标记目标施加当前选择的变声效果（持续60秒，冷却60秒）
        // - 技能切换键(Y)：切换变声种类
        // - 蹲下+技能切换键(Y)：切换变声等级
        RoleSkill.register(ModRoles.VOICE_CHANGER,
                RoleSkill.skill(SRE.id("voice_changer_apply"),
                        "skill.noellesroles.voice_changer.apply",
                        context -> {
                            return RoleData.getOptional(VoiceChangerRoleData.class, context.player())
                                    .map(vc -> vc.applyVoice()).orElse(false);
                        }).cooldownSeconds(60).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("voice_changer_mark"),
                        "skill.noellesroles.voice_changer.mark",
                        context -> {
                            return RoleData.getOptional(VoiceChangerRoleData.class, context.player())
                                    .map(vc -> vc.markTarget(context.target())).orElse(false);
                        }).shifted(true).cooldownSeconds(20).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("voice_changer_switch"),
                        "skill.noellesroles.voice_changer.switch",
                        context -> {
                            if (context.player().isShiftKeyDown()) {
                                RoleData.getOptional(VoiceChangerRoleData.class, context.player())
                                        .ifPresent(VoiceChangerRoleData::switchVoiceLevel);
                            } else {
                                RoleData.getOptional(VoiceChangerRoleData.class, context.player())
                                        .ifPresent(VoiceChangerRoleData::switchVoiceType);
                            }
                            return true;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).build());

        // 会计技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式
        RoleSkill.register(ModRoles.ACCOUNTANT,
                RoleSkill.skill(SRE.id("accountant_ability"),
                        "skill.noellesroles.accountant.ability",
                        context -> {
                            return RoleData.getOptional(AccountantRoleData.class, context.player())
                                    .map(AccountantRoleData::useAbility).orElse(false);
                        }).announceToSelf(false).showOnHud(false).build(),
                RoleSkill.skill(SRE.id("accountant_toggle_mode"),
                        "skill.noellesroles.accountant.toggle_mode",
                        context -> {
                            RoleData.getOptional(AccountantRoleData.class, context.player())
                                    .ifPresent(AccountantRoleData::toggleMode);
                            return true;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).build());

        // 炼金术师技能注册：普通按 G 调制药剂，蹲下+ G 切换药剂
        RoleSkill.register(ModRoles.ALCHEMIST,
                RoleSkill.skill(SRE.id("alchemist_craft"),
                        "skill.noellesroles.alchemist.craft",
                        context -> {
                            RoleData.getOptional(AlchemistRoleData.class, context.player())
                                    .ifPresent(AlchemistRoleData::craftPotion);
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("alchemist_switch_potion"),
                        "skill.noellesroles.alchemist.switch_potion",
                        context -> {
                            RoleData.getOptional(AlchemistRoleData.class, context.player())
                                    .ifPresent(AlchemistRoleData::switchPotion);
                            return true;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 建筑师技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式 ====================
        RoleSkill.register(ModRoles.BUILDER,
                RoleSkill.skill(SRE.id("builder_ability"),
                        "skill.noellesroles.builder.ability",
                        context -> {
                            var comp = RoleData.getNullable(BuilderRoleData.class, context.player());
                            if (comp == null)
                                return false;
                            if (comp.isBuildMode()) {
                                return comp.useBuildAbility();
                            } else {
                                return comp.useDemolishAbility();
                            }
                        }).build(),
                RoleSkill.skill(SRE.id("builder_toggle_mode"),
                        "skill.noellesroles.builder.toggle_mode",
                        context -> {
                            BuilderRoleData builder = RoleData.getNullable(BuilderRoleData.class, context.player());
                            if (builder != null) {
                                builder.switchMode();
                                return true;
                            }
                            return false;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 葬仪技能注册：普通按 G 使用技能，按技能切换键(Y) 切换模式 ====================
        RoleSkill.register(ModRoles.MORTICIAN_BODYMAKER,
                RoleSkill.skill(SRE.id("mortician_bodymaker_ability"),
                        "skill.noellesroles.mortician_bodymaker.ability",
                        context -> {
                            var mb = RoleData.getNullable(MorticianBodyMakerRoleData.class, context.player());
                            return mb != null && mb.useAbility();
                        }).build(),
                RoleSkill.skill(SRE.id("mortician_bodymaker_toggle_mode"),
                        "skill.noellesroles.mortician_bodymaker.toggle_mode",
                        context -> {
                            MorticianBodyMakerRoleData mb = RoleData.getNullable(MorticianBodyMakerRoleData.class,
                                    context.player());
                            if (mb != null) {
                                mb.toggleMode();
                                return true;
                            }
                            return false;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 设陷者技能注册：普通按 G 使用技能，按技能切换键(Y) 切换陷阱类型 ====================
        RoleSkill.register(ModRoles.TRAPPER,
                RoleSkill.skill(SRE.id("trapper_ability"),
                        "skill.noellesroles.trapper.ability",
                        context -> {
                            TrapperRoleData trapper = RoleData.getNullable(TrapperRoleData.class, context.player());
                            return trapper != null && trapper.tryPlaceTrap();
                        }).build(),
                RoleSkill.skill(SRE.id("trapper_toggle_mode"),
                        "skill.noellesroles.trapper.toggle_mode",
                        context -> {
                            TrapperRoleData trapper = RoleData.getNullable(TrapperRoleData.class, context.player());
                            if (trapper != null) {
                                trapper.switchTrapType();
                                return true;
                            }
                            return false;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).showOnHud(false).build());

        // ==================== 模仿者技能注册：普通按 G 使用技能，按技能切换键(Y) 切换槽位 ====================
        RoleSkill.register(ModRoles.IMITATOR,
                RoleSkill.skill(SRE.id("imitator_ability"),
                        "skill.noellesroles.imitator.ability",
                        context -> {
                            var comp = RoleData.getNullable(ImitatorRoleData.class, context.player());
                            if (comp == null) {
                                return false;
                            }
                            if (context.target() != null) {
                                comp.tryCopyAbility(context.player(), context.target());
                            } else {
                                comp.useActiveAbility(context.player(), null);
                            }
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("imitator_toggle_slot"),
                        "skill.noellesroles.imitator.toggle_slot",
                        context -> {
                            RoleData.getOptional(ImitatorRoleData.class, context.player())
                                    .ifPresent(ImitatorRoleData::switchSlot);
                            return true;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).showOnHud(false).build());

        // 幽灵技能注册：可切换隐身效果
        RoleSkill.register(ModRoles.PHANTOM, RoleSkill.skill(
                SRE.id("phantom_invisibility"),
                "skill.noellesroles.phantom.invisibility",
                context -> {
                    ServerPlayer player = context.player();
                    if (context.skillReady()) {
                        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,
                                NoellesRolesConfig.HANDLER.instance().phantomInvisibilityDuration * 20,
                                0, true, false, true));
                        return true;
                    } else {
                        var effect = player.getEffect(MobEffects.INVISIBILITY);
                        if (effect != null && effect.getDuration() > 0) {
                            player.removeEffect(MobEffects.INVISIBILITY);
                            player.displayClientMessage(
                                    Component.translatable("tip.phantom.exited").withStyle(ChatFormatting.YELLOW),
                                    true);
                            return true;
                        }
                        return false;
                    }
                }).cooldownSeconds(NoellesRolesConfig.instance().phantomInvisibilityCooldown).toggleable(true).build());

        // 指挥官技能注册：切换杀手/普通广播频道
        RoleSkill.register(ModRoles.COMMANDER, RoleSkill.skill(
                SRE.id("commander_switch_channel"),
                "skill.noellesroles.commander.switch_channel",
                context -> {
                    CommanderHandler.tryActiveAbility(context.player());
                    return true;
                }).build());

        // 炸弹人技能注册：购买炸弹
        RoleSkill.register(ModRoles.BOMBER, RoleSkill.skill(
                SRE.id("bomber_buy_bomb"),
                "skill.noellesroles.bomber.buy_bomb",
                context -> {
                    RoleData.getOptional(BomberRoleData.class, context.player())
                            .ifPresent(BomberRoleData::buyBomb);
                    return true;
                }).build());

        // 仇杀客技能注册：切换效果开关
        RoleSkill.register(ModRoles.BLOOD_FEUDIST, RoleSkill.skill(
                SRE.id("blood_feudist_toggle"),
                "skill.noellesroles.blood_feudist.toggle",
                context -> {
                    RoleData.getOptional(BloodFeudistRoleData.class, context.player())
                            .ifPresent(BloodFeudistRoleData::toggleEffects);
                    return true;
                }).toggleable(true).build());

        // 钟表匠技能注册：削减他人回合时间
        RoleSkill.register(ModRoles.CLOCKMAKER, RoleSkill.skill(
                SRE.id("clockmaker_use_skill"),
                "skill.noellesroles.clockmaker.use_skill",
                context -> {
                    RoleData.getOptional(ClockmakerRoleData.class, context.player())
                            .ifPresent(ClockmakerRoleData::useSkill);
                    return true;
                }).build());

        // 超级亡命徒技能注册：使用技能，蹲下+ G 为特殊模式
        RoleSkill.register(SpecialGameModeRoles.SUPER_LOOSE_END,
                RoleSkill.skill(SRE.id("super_loose_end_ability"),
                        "skill.noellesroles.super_loose_end.ability",
                        context -> {
                            RoleData.getOptional(SuperLooseEndRoleData.class, context.player())
                                    .ifPresent(sle -> sle.useAbility(false));
                            return true;
                        }).build(),
                RoleSkill.skill(SRE.id("super_loose_end_shift"),
                        "skill.noellesroles.super_loose_end.shift",
                        context -> {
                            RoleData.getOptional(SuperLooseEndRoleData.class, context.player())
                                    .ifPresent(sle -> sle.useAbility(true));
                            return true;
                        }).shifted(true).build());

        // 布袋鬼鬼术注册：4 个鬼术作为可选槽位（V 切换、G 释放、Sneak+G 开里世界大招）。
        // 冷却/门控由 MaChenXuRoleData 自有逻辑负责（cooldownTicks=0 让引擎不拦截），
        // announceToSelf(false) 由组件自定义提示。槽位顺序须与 MaChenXuRoleData.ART_ORDER 一致。
        RoleSkill.register(ModRoles.MA_CHEN_XU,
                RoleSkill.skill(SRE.id("ma_chen_xu_veil"), "hud.noellesroles.ma_chen_xu.skill.veil",
                        context -> {
                            var d = RoleData.getNullable(MaChenXuRoleData.class, context.player());
                            return d != null && d.onGhostArt("veil");
                        }).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_effigy"), "hud.noellesroles.ma_chen_xu.skill.effigy",
                        context -> {
                            var d = RoleData.getNullable(MaChenXuRoleData.class, context.player());
                            return d != null && d.onGhostArt("effigy");
                        }).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_wail"), "hud.noellesroles.ma_chen_xu.skill.wail",
                        context -> {
                            var d = RoleData.getNullable(MaChenXuRoleData.class, context.player());
                            return d != null && d.onGhostArt("wail");
                        }).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("ma_chen_xu_seize"), "hud.noellesroles.ma_chen_xu.skill.seize",
                        context -> {
                            var d = RoleData.getNullable(MaChenXuRoleData.class, context.player());
                            return d != null && d.onGhostArt("seize");
                        }).announceToSelf(false).build());

        RoleSkill.register(ModRoles.WRAITH_ASSASSIN,
                RoleSkill.skill(SRE.id("wraith_assault"), "skill.noellesroles.wraith_assassin.assault",
                        context -> {
                            var d = RoleData.getNullable(WraithAssassinRoleData.class, context.player());
                            return d != null && d.useAssault(context.player());
                        }).cooldownSeconds(4).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("wraith_wail"), "skill.noellesroles.wraith_assassin.wail",
                        context -> {
                            var d = RoleData.getNullable(WraithAssassinRoleData.class, context.player());
                            return d != null && d.useWail(context.player());
                        }).cooldownSeconds(50).showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("wraith_manifest"), "skill.noellesroles.wraith_assassin.manifest",
                        context -> {
                            var d = RoleData.getNullable(WraithAssassinRoleData.class, context.player());
                            return d != null && d.useManifest(context.player());
                        }).cooldownSeconds(110).showOnHud(true).announceToSelf(false).build());

        RoleSkill.register(ModRoles.SALTED_FISH,
                RoleSkill.skill(SaltedFishRoleData.SKILL_ID, "skill.noellesroles.salted_fish.sunbathe",
                        context -> {
                            SaltedFishRoleData saltedFishData = RoleData.getNullable(SaltedFishRoleData.class,
                                    context.player());
                            return saltedFishData != null && saltedFishData.useSkill(context.player());
                        })
                        .cooldownTicks(SaltedFishRoleData.COOLDOWN_TICKS)
                        .toggleable(true).showOnHud(true).announceToSelf(false).build());

        // 归途旅人技能注册：普通按 G 释放当前技能，按技能切换键(Y) 直接切换技能
        RoleSkill.register(ModRoles.RETURN_TRAVELER,
                RoleSkill.skill(ReturnTravelerRoleData.SKILL_ID,
                        "skill.noellesroles.return_traveler.ability",
                        context -> {
                            ReturnTravelerRoleData rt = RoleData.getNullable(ReturnTravelerRoleData.class,
                                    context.player());
                            return rt != null && rt.useAbility();
                        })
                        .showOnHud(true).announceToSelf(false).build(),
                RoleSkill.skill(SRE.id("return_traveler_toggle_mode"),
                        "skill.noellesroles.return_traveler.toggle_mode",
                        context -> {
                            ReturnTravelerRoleData rt = RoleData.getNullable(ReturnTravelerRoleData.class,
                                    context.player());
                            if (rt != null) {
                                rt.toggleMode();
                                return true;
                            }
                            return false;
                        }).shifted(true).modeSwitch(true).announceToSelf(false).build());

        // 皮革噶的技能注册：消耗 150 金币进入疯魔模式（直觉 + 速度 III + 追杀音效）
        RoleSkill.register(ModRoles.LEATHER_PIG,
                RoleSkill.skill(LeatherPigRoleData.SKILL_ID, "skill.noellesroles.leather_pig.frenzy",
                        context -> {
                            var lp = RoleData.getNullable(LeatherPigRoleData.class, context.player());
                            return lp != null && lp.useSkill(context.player());
                        })
                        .cooldownSeconds(LeatherPigRoleData.COOLDOWN_SECONDS)
                        .showOnHud(true).announceToSelf(false).build());

        // 出题人不适用于统一的技能注册：其需要不同的触发方式但这个api不兼容。
        // 年兽技能注册：发送红包给目标玩家（客户端选目标）
        RoleSkill.register(ModRoles.NIAN_SHOU, RoleSkill.skill(
                SRE.id("nian_shou_red_packet"),
                "skill.noellesroles.nian_shou.red_packet",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();
                    if (targetUuid == null) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.nianshou.no_target")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (!(target instanceof ServerPlayer targetPlayer))
                        return false;
                    var comp = RoleData.getOptional(NianShouRoleData.class, player);
                    if (comp.map(NianShouRoleData::getRedPacketCount).orElse(0) <= 0) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.nianshou.no_red_packet")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    comp.ifPresent(NianShouRoleData::useRedPacket);
                    ConfigWorldComponent configWorld = ConfigWorldComponent.KEY.get(targetPlayer.level());
                    configWorld.addRedPacketTimer(targetPlayer.getUUID());
                    player.displayClientMessage(
                            Component.translatable("message.noellesroles.nianshou.red_packet_sent", target.getName())
                                    .withStyle(ChatFormatting.GOLD),
                            true);
                    return true;
                }).build());

        // 幸运使者技能注册：保护目标玩家，冷却120秒，消耗200金币
        RoleSkill.register(ModRoles.FORTUNETELLER, RoleSkill.skill(
                SRE.id("fortuneteller_protect"),
                "skill.noellesroles.fortuneteller.protect",
                context -> {
                    ServerPlayer player = context.player();
                    UUID targetUuid = context.target();
                    if (targetUuid == null)
                        return false;
                    Player target = player.level().getPlayerByUUID(targetUuid);
                    if (target == null)
                        return false;
                    SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
                    if (shop.balance < 200) {
                        player.displayClientMessage(
                                Component.translatable("message.noellesroles.insufficient_funds")
                                        .withStyle(ChatFormatting.RED),
                                true);
                        return false;
                    }
                    shop.addToBalance(-200);
                    var fortuneData = RoleData.getNullable(FortunetellerRoleData.class, player);
                    if (fortuneData != null) {
                        fortuneData.protectPlayer(target);
                    }
                    return true;
                }).cooldownSeconds(120).build());

        // 十六夜咲夜技能注册：时间停止5秒，冷却240秒
        RoleSkill.register(THRedHouseRoles.MAID_SAKUYA, RoleSkill.skill(
                SRE.id("maid_sakuya_timestop"),
                "skill.maid_sakuya.timestop",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.getCooldowns().isOnCooldown(Items.CLOCK))
                        return false;
                    return TimeStopEffect.tryTriggerStart(player, 20 * 5,
                            Component.translatable("title.maid_sakuya.timestopper")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }).cooldownSeconds(240).build());

        // JOJO技能注册：时间停止3秒，冷却240秒
        RoleSkill.register(ModRoles.JOJO, RoleSkill.skill(
                SRE.id("jojo_timestop"),
                "skill.noellesroles.jojo.timestop",
                context -> {
                    ServerPlayer player = context.player();
                    if (player.getCooldowns().isOnCooldown(Items.CLOCK))
                        return false;
                    return TimeStopEffect.tryTriggerStart(player, 20 * 5,
                            Component.translatable("hud.noellesroles.jojo.the_world")
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
                }).cooldownSeconds(240).build());

        // DIO技能注册：时间停止，委托组件
        RoleSkill.register(ModRoles.DIO, RoleSkill.skill(
                SRE.id("dio_timestop"),
                "skill.noellesroles.dio.timestop",
                context -> {
                    RoleData.getOptional(DIORoleData.class, context.player()).ifPresent(d -> d.tryActivateTimeStop());
                    return true;
                }).build());
    }

}
