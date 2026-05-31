package org.agmas.noellesroles.init;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.UUID;

import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.RicesRoleRhapsody;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.NRSounds;
import org.agmas.noellesroles.game.roles.Innocent.accountant.AccountantPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.alchemist.AlchemistPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.ghost.GhostPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.hoan_meirin.HoanMeirinPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.dio.DIOPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.watcher.WatcherPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mercenary.MercenaryPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.painter.PainterPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.nian_shou.NianShouPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.pelican.PelicanPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.mortician.MorticianPlayerComponent;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

public class ModRolesInitialEventRegister {

    public static void register() {

        // 初始化仇杀客事件
        BloodFeudistPlayerComponent.registerEvents();
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            // 魔术师角色初始化
            if (role.identifier().equals(ModRoles.BARTENDER.identifier())) {
                FoodDrinkGlowComponent.KEY.get(player).init();
            }
            if (role.identifier().equals(ModRoles.CHEF.identifier())) {
                FoodDrinkGlowComponent.KEY.get(player).init();
            }
            if (role.identifier().equals(ModRoles.MAGICIAN.identifier())) {
                var magicianComponent = ModComponents.MAGICIAN.maybeGet(player).orElse(null);
                if (magicianComponent != null) {
                    // 停止疯狂模式（如果之前存在）
                    var psychoComponent = SREPlayerPsychoComponent.KEY.get(player);
                    if (psychoComponent != null) {
                        psychoComponent.init();
                    }
                    // 随机分配一个杀手身份给魔术师（原版杀手、毒师和清道夫除外）
                    magicianComponent.startDisguiseRandomRole();
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
                var tpc = DIOPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(RedHouseRoles.HOAN_MEIRIN.identifier())) {
                var tpc = HoanMeirinPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(RedHouseRoles.FURANDORU.identifier())) {
                var tpc = GhostPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(RedHouseRoles.MAID_SAKUYA.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            if (role.identifier().equals(ModRoles.JOJO.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            // 初始化记录员
            if (role.identifier().equals(ModRoles.RECORDER.identifier())) {
                var tpc = RecorderPlayerComponent.KEY.get(player);
                tpc.initRecorder();
            }
            if (role.identifier().equals(ModRoles.EXAMPLER.identifier())) {
                var tpc = SREAbilityPlayerComponent.KEY.get(player);
                tpc.init();
                tpc.charges = 0;
                tpc.sync();
                return;
            }
            if (role.identifier().equals(ModRoles.THIEF.identifier())) {
                int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                var tpc = ThiefPlayerComponent.KEY.get(player);
                tpc.updateHonorCost(totalPlayers);
            }
            if (role.identifier().equals(ModRoles.WATCHER.identifier())) {
                var tpc = WatcherPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(ModRoles.MERCENARY.identifier())) {
                var mercenary = MercenaryPlayerComponent.KEY.get(player);
                mercenary.init();
                mercenary.sync();
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
                // 会计角色初始化
                var accountantComponent = AccountantPlayerComponent.KEY.get(player);
                accountantComponent.init();
                return;
            }
            if (role.identifier().equals(ModRoles.ALCHEMIST.identifier())) {
                // 药剂师角色初始化
                var alchemistComponent = AlchemistPlayerComponent.KEY.get(player);
                alchemistComponent.init();
                return;
            }
            // 派对狂角色初始化 - 基于开局玩家数设置threshold
            if (role.identifier().equals(ModRoles.PARTY_KILLER.identifier())) {
                int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                var partyComponent = org.agmas.noellesroles.game.roles.killer.party.PartyPlayerComponent.KEY
                        .get(player);
                partyComponent.initThreshold(totalPlayers);
                return;
            }
            if (role.identifier().equals(TMMRoles.KILLER.identifier())) {
                player.addItem(TMMItems.KNIFE.getDefaultInstance().copy());
                return;
            }
            if (role.identifier().equals(TMMRoles.VIGILANTE.identifier())) {
                player.addItem(TMMItems.REVOLVER.getDefaultInstance().copy());
                return;
            }
            if (role.identifier().equals(ModRoles.ATTENDANT.identifier())) {
                if (player instanceof ServerPlayer sp)
                    SRE.SendRoomInfoToPlayer(sp);
                RoleInitialItems.addInitialItemsForRole(player, role);
                return;
            }
            if (role.identifier().equals(ModRoles.GUEST_GHOST.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
            }
            SREAbilityPlayerComponent abilityPlayerComponent = (SREAbilityPlayerComponent) SREAbilityPlayerComponent.KEY
                    .get(player);
            abilityPlayerComponent.cooldown = NoellesRolesConfig.HANDLER.instance().generalCooldownTicks;

            if (role.equals(ModRoles.BROADCASTER)) {
                abilityPlayerComponent.cooldown = 0;
                SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
                playerShopComponent.setBalance(200);
                playerShopComponent.sync();
            } else {
                abilityPlayerComponent.cooldown = NoellesRolesConfig.HANDLER.instance().generalCooldownTicks;
            }
            if (role.equals(ModRoles.EXECUTIONER)) {
                ExecutionerPlayerComponent executionerPlayerComponent = (ExecutionerPlayerComponent) ExecutionerPlayerComponent.KEY
                        .get(player);
                executionerPlayerComponent.won = false;
                SREPlayerShopComponent playerShopComponent = (SREPlayerShopComponent) SREPlayerShopComponent.KEY
                        .get(player);
                executionerPlayerComponent.init();
                playerShopComponent.setBalance(100);
                executionerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.VULTURE)) {
                if (VulturePlayerComponent.KEY.isProvidedBy(player)) {
                    VulturePlayerComponent vulturePlayerComponent = VulturePlayerComponent.KEY.get(player);
                    vulturePlayerComponent.init();
                    vulturePlayerComponent.bodiesRequired = Math.max(1, (int) ((player.level().players().size() / 3f)
                            - Math.floor(player.level().players().size() / 6f)));
                    vulturePlayerComponent.sync();
                }
            }
            if (role.equals(ModRoles.PELICAN)) {
                if (PelicanPlayerComponent.KEY.isProvidedBy(player)) {
                    var pelicanComponent = PelicanPlayerComponent.KEY.get(player);
                    pelicanComponent.init();
                    int totalPlayers = SREGameWorldComponent.KEY.get(player.level()).getPlayerCount();
                    double percent = NoellesRolesConfig.HANDLER.instance().pelicanEatPercentage;
                    pelicanComponent.requiredEaten = Math.max(1, (int) Math.ceil(totalPlayers * (percent / 100.0D)) - 1);
                    pelicanComponent.sync();
                }
            }
            if (role.equals(ModRoles.INSANE_KILLER)) {
                final var insaneKillerPlayerComponent = InsaneKillerPlayerComponent.KEY.get(player);
                insaneKillerPlayerComponent.init();
                insaneKillerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.RECORDER)) {
                final var recorderPlayerComponent = RecorderPlayerComponent.KEY.get(player);
                recorderPlayerComponent.initializeRoles();
            }

            // 更新所有记录员的可用角色列表
            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                if (SREGameWorldComponent.KEY.get(p.level()).isRole(p, ModRoles.RECORDER)) {
                    RecorderPlayerComponent.KEY.get(p).updateAvailableRoles();
                }
            }
            if (role.equals(ModRoles.RECORDER)) {
                final var recorderPlayerComponent = RecorderPlayerComponent.KEY.get(player);
                recorderPlayerComponent.init();
                recorderPlayerComponent.sync();
            }
            // 使用映射表添加初始物品
            RoleInitialItems.addInitialItemsForRole(player, role);

            if (role.equals(ModRoles.GAMBLER)) {
                org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent gamblerPlayerComponent = org.agmas.noellesroles.game.roles.neutral.gambler.GamblerPlayerComponent.KEY
                        .get(player);
                gamblerPlayerComponent.init();
                gamblerPlayerComponent.sync();
            }

            if (role.equals(ModRoles.NOISEMAKER)) {
                org.agmas.noellesroles.game.roles.Innocent.noise_maker.NoiseMakerPlayerComponent noiseMakerPlayerComponent = org.agmas.noellesroles.game.roles.Innocent.noise_maker.NoiseMakerPlayerComponent.KEY
                        .get(player);
                noiseMakerPlayerComponent.init();
                noiseMakerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.GHOST)) {
                org.agmas.noellesroles.game.roles.Innocent.ghost.GhostPlayerComponent ghostPlayerComponent = org.agmas.noellesroles.game.roles.Innocent.ghost.GhostPlayerComponent.KEY
                        .get(player);
                ghostPlayerComponent.init();
                ghostPlayerComponent.sync();
            }
            if (role.equals(ModRoles.CANDLE_BEARER)) {
                CandleBearerPlayerComponent candleBearer = CandleBearerPlayerComponent.KEY.get(player);
                candleBearer.init();
                RoleUtils.insertStackInFreeSlot(player, Items.CANDLE.getDefaultInstance());
                candleBearer.sync();
            }
            // 操纵师角色初始化
            if (role.equals(ModRoles.MANIPULATOR)) {
                ManipulatorPlayerComponent manipulatorPlayerComponent = ManipulatorPlayerComponent.KEY.get(player);
                manipulatorPlayerComponent.init();
                manipulatorPlayerComponent.sync();
            }
            // 巫毒师角色初始化 - 开局75秒冷却
            if (role.equals(ModRoles.VOODOO)) {
                abilityPlayerComponent.cooldown = 100 * 20; 
                abilityPlayerComponent.sync();
                return; 
            }
            if (role.equals(ModRoles.BOMBER)) {
                if (role.equals(ModRoles.MONITOR)) {
                    MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY.get(player);
                    monitorComponent.init();
                    monitorComponent.sync();
                }
                // bomberPlayerComponent.reset(); // 如果有 reset 方法
                ModComponents.BOMBER.sync(player);
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
            StalkerPlayerComponent stalkerComp = ModComponents.STALKER.get(player);
            if (!stalkerComp.isActiveStalker()) {
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
            if (role.equals(ModRoles.NIAN_SHOU)) {
                var comc = NianShouPlayerComponent.KEY.maybeGet(player).orElse(null);
                if (comc != null) {
                    comc.init();
                }
            }
            if (role.equals(ModRoles.PUPPETEER)) {
                var comc = PuppeteerPlayerComponent.KEY.maybeGet(player).orElse(null);
                if (comc != null) {
                    if (!comc.isActivePuppeteer())
                        comc.init();
                }
            }
            // 画家角色初始化
            if (role.equals(ModRoles.PAINTER)) {
                var painterComponent = PainterPlayerComponent.KEY.get(player);
                painterComponent.init();
                painterComponent.sync();
            }
            // 葬仪角色初始化
            if (role.equals(ModRoles.MORTICIAN_BODYMAKER)) {
                var morticianComponent = MorticianPlayerComponent.KEY.get(player);
                morticianComponent.init();
                morticianComponent.sync();
            }
            if (role.equals(ModRoles.GODFATHER)) {
                if (player instanceof ServerPlayer sp) {
                    for (var p : sp.serverLevel().players()) {
                        if (p != null) {
                            p.playNotifySound(NRSounds.MAFIA, SoundSource.MASTER, 1.0F, 1.0F);
                        }
                    }
                }
            }
        });
    }

    static {
        // 疫使技能注册：按技能键感染目标玩家
        RoleSkill.register(ModRoles.INFECTED, context -> {
            ServerPlayer player = context.player();
            UUID targetUuid = context.target();

            if (targetUuid == null) {
                // 无目标时不执行任何操作
                return;
            }

            Player target = player.level().getPlayerByUUID(targetUuid);
            if (target == null) {
                return;
            }

            // 检查游戏状态
            if (!GameUtils.isPlayerAliveAndSurvival(target)) {
                return;
            }

            // 检查目标是否已被感染
            InfectedPlayerComponent targetComponent = ModComponents.INFECTED.get(target);
            if (targetComponent.infectedTicks > 0) {
                return; // 已被感染
            }

            // 检查疫使技能冷却
            SREAbilityPlayerComponent abilityComponent = SREAbilityPlayerComponent.KEY.get(player);
            if (abilityComponent.cooldown > 0) {
                return;
            }

            // 感染目标
            targetComponent.infect(player);

            // 设置疫使技能冷却为80秒
            abilityComponent.cooldown = GameConstants.getInTicks(1, 20); // 80秒冷却
            abilityComponent.sync();

            // 播放感染音效
            if (NRSounds.INFECTED_INFECT != null) {
                player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    NRSounds.SYRINGE_STAB, SoundSource.MASTER, 0.5f, 0.5f);
            }
        });

        // 鹈鹕技能注册：按技能键吞噬玩家，蹲下按技能键释放最后吞噬的玩家
        RoleSkill.register(ModRoles.PELICAN, context -> {
            ServerPlayer player = context.player();
            PelicanPlayerComponent comp = PelicanPlayerComponent.KEY.get(player);
            if (comp == null) return;

            if (player.isShiftKeyDown()) {
                comp.releaseLast();
                return;
            }

            // 先在传入的 target 中查找；没有则搜索附近3.15格内最近的存活玩家
            ServerPlayer target = null;
            UUID targetUuid = context.target();
            if (targetUuid != null) {
                Player p = player.level().getPlayerByUUID(targetUuid);
                if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp) && player.distanceToSqr(sp) <= 3.15D * 3.15D) {
                    target = sp;
                }
            }
            if (target == null) {
                double closest = 3.15D * 3.15D;
                for (ServerPlayer p : player.serverLevel().getPlayers(p2 -> p2 != player && GameUtils.isPlayerAliveAndSurvival(p2))) {
                    double dist = player.distanceToSqr(p);
                    if (dist < closest) {
                        closest = dist;
                        target = p;
                    }
                }
            }

            if (target != null) {
                comp.tryEat(target);
            } else {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.pelican.no_target")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        });

        // 葬仪技能注册：使用当前模式的技能
        RoleSkill.register(ModRoles.MORTICIAN_BODYMAKER, context -> {
            ServerPlayer player = context.player();
            MorticianPlayerComponent morticianComponent = MorticianPlayerComponent.KEY.get(player);
            if (morticianComponent != null) {
                morticianComponent.useAbility();
            }
        });

        // 咒法师技能注册：标记目标玩家
        RoleSkill.register(ModRoles.WARLOCK, context -> {
            ServerPlayer player = context.player();
            var comp = org.agmas.noellesroles.game.roles.killer.warlock.WarlockPlayerComponent.KEY.get(player);
            if (comp == null) return;
            UUID targetUuid = context.target();
            ServerPlayer target = null;
            if (targetUuid != null) {
                Player p = player.level().getPlayerByUUID(targetUuid);
                if (p instanceof ServerPlayer sp && GameUtils.isPlayerAliveAndSurvival(sp) && player.distanceToSqr(sp) <= 4.0D * 4.0D) {
                    target = sp;
                }
            }
            if (target != null && comp.tryMark(target)) {
                player.displayClientMessage(Component.translatable("message.noellesroles.warlock.marked", target.getName().getString()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
            } else {
                player.displayClientMessage(Component.translatable("message.noellesroles.warlock.mark_fail").withStyle(ChatFormatting.RED), true);
            }
        });
    }

}
