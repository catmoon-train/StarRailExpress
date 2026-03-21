package org.agmas.noellesroles.init;

import org.agmas.harpymodloader.events.ModdedRoleAssigned;
import org.agmas.noellesroles.RicesRoleRhapsody;
import org.agmas.noellesroles.component.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.component.DIOPlayerComponent;
import org.agmas.noellesroles.component.HoanMeirinPlayerComponent;
import org.agmas.noellesroles.component.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.component.MonitorPlayerComponent;
import org.agmas.noellesroles.component.NianShouPlayerComponent;
import org.agmas.noellesroles.component.PuppeteerPlayerComponent;
import org.agmas.noellesroles.component.RecorderPlayerComponent;
import org.agmas.noellesroles.component.StalkerPlayerComponent;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.roles.thief.ThiefPlayerComponent;
import org.agmas.noellesroles.roles.vulture.VulturePlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import pro.fazeclan.river.stupid_express.constants.SEItems;
import pro.fazeclan.river.stupid_express.constants.SERoles;

public class ModRolesInitialEventRegister {

    public static void register() {

        // 初始化仇杀客事件
        BloodFeudistPlayerComponent.registerEvents();
        ModdedRoleAssigned.EVENT.register((player, role) -> {
            // 魔术师角色初始化
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
            if (role.identifier().equals(ModRoles.HOAN_MEIRIN.identifier())) {
                var tpc = HoanMeirinPlayerComponent.KEY.get(player);
                tpc.init();
            }
            if (role.identifier().equals(ModRoles.MAID_SAKUYA.identifier())) {
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
                int totalPlayers = player.level().players().size();
                var tpc = ThiefPlayerComponent.KEY.get(player);
                tpc.updateHonorCost(totalPlayers);
            }
            if (role.identifier().equals(ModRoles.WAYFARER.identifier())) {
                player.getInventory().clearContent();
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
                var accountantComponent = org.agmas.noellesroles.component.AccountantPlayerComponent.KEY.get(player);
                accountantComponent.init();
                return;
            }
            if (role.identifier().equals(ModRoles.ALCHEMIST.identifier())) {
                // 药剂师角色初始化
                var alchemistComponent = org.agmas.noellesroles.component.AlchemistPlayerComponent.KEY.get(player);
                alchemistComponent.init();
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
                return;
            }
            if (role.identifier().equals(ModRoles.GUEST_GHOST.identifier())) {
                SREPlayerShopComponent.KEY.get(player).setBalance(100);
                return;
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
            if (role.equals(ModRoles.THE_INSANE_DAMNED_PARANOID_KILLER_OF_DOOM_DEATH_DESTRUCTION_AND_WAFFLES)) {
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
                org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent gamblerPlayerComponent = org.agmas.noellesroles.roles.gambler.GamblerPlayerComponent.KEY
                        .get(player);
                gamblerPlayerComponent.init();
                gamblerPlayerComponent.sync();
            }

            if (role.equals(ModRoles.NOISEMAKER)) {
                org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent noiseMakerPlayerComponent = org.agmas.noellesroles.roles.noise_maker.NoiseMakerPlayerComponent.KEY
                        .get(player);
                noiseMakerPlayerComponent.init();
                noiseMakerPlayerComponent.sync();
            }
            if (role.equals(ModRoles.GHOST)) {
                org.agmas.noellesroles.roles.ghost.GhostPlayerComponent ghostPlayerComponent = org.agmas.noellesroles.roles.ghost.GhostPlayerComponent.KEY
                        .get(player);
                ghostPlayerComponent.init();
                ghostPlayerComponent.sync();
            }
            // 操纵师角色初始化
            if (role.equals(ModRoles.MANIPULATOR)) {
                ManipulatorPlayerComponent manipulatorPlayerComponent = ManipulatorPlayerComponent.KEY.get(player);
                manipulatorPlayerComponent.init();
                manipulatorPlayerComponent.sync();
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
            // org.agmas.noellesroles.roles.sheriff.SheriffPlayerComponent
            // sheriffPlayerComponent =
            // org.agmas.noellesroles.roles.sheriff.SheriffPlayerComponent.KEY.get(player);
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
        });
    }

}
