package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.SREGameModes;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.roles.SpecialGameModeModifiers;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.FoodDrinkGlowComponent;
import org.agmas.noellesroles.component.InfectedPlayerComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.content.item.SignedPaperItem;
import org.agmas.noellesroles.game.roles.Innocent.awesome_binglus.AwesomePlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.detective.DetectivePlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.fool.FoolPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.magician.MagicianPlayerComponent;
import org.agmas.noellesroles.game.roles.Innocent.monitor.MonitorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.insane_killer.InsaneKillerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.ma_chen_xu.MaChenXuPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.admirer.AdmirerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.candlebearer.CandleBearerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggData;
import org.agmas.noellesroles.game.roles.neutral.monokuma.MonokumaEventHandler;
import org.agmas.noellesroles.game.roles.neutral.puppeteer.PuppeteerPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.recorder.RecorderPlayerComponent;
import org.agmas.noellesroles.game.roles.neutral.wayfarer.WayfarerPlayerComponent;
import org.agmas.noellesroles.game.roles.special.better_vigilante.BetterVigilantePlayerComponent;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.lovers.cca.LoversComponent;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

import java.awt.*;
import java.util.HashMap;

public class InstinctRenderer {
    public static void registerInstinctEvents() {
        // 鬼祟效果：当目标玩家8格范围内时，禁用杀手直觉高亮
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer)) {
                return -1;
            }
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null) {
                return -1;
            }
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRunning()) {
                return -1;
            }
            if (!SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
                return -1;
            }
            
            Player localPlayer = Minecraft.getInstance().player;
            
            // 检查目标玩家是否有鬼祟修饰符
            try {
                WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(targetPlayer.level());
                if (modifiers != null && modifiers.isModifier(targetPlayer.getUUID(), TraitorAndModifiers.SNEAKY)) {
                    // 检查目标是否在当前玩家8格范围内
                    double dist = localPlayer.distanceTo(targetPlayer);
                    if (dist <= 8.0) {
                        // 鬼祟生效：禁用直觉高亮
                        return -2;
                    }
                }
            } catch (Exception e) {
                // 静默处理错误
            }
            
            return -1;
        });
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player target_player))
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            if (!SREClient.gameComponent.isRunning()) {
                return -1;
            }
            if (!SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
                return -1;
            }
            var self = Minecraft.getInstance().player;
            if (SREClient.gameComponent.gameMode.identifier.equals(SREGameModes.HIDE_AND_SEEK_MODE.identifier)) {
                if (SREClient.gameComponent.isKillerTeam(self)) {
                    if (SREClient.gameComponent.isKillerTeam(target_player)) {
                        return TMMRoles.KILLER.color();
                    }
                } else {
                    if (self.hasEffect(ModEffects.SAFE_TIME) && SREClient.gameComponent.isKillerTeam(target_player)) {
                        return TMMRoles.KILLER.color();
                    }
                }
                if (self.hasEffect(MobEffects.GLOWING)) {
                    return TMMRoles.VIGILANTE.color();
                }
                return -2;
            }
            if (SREClient.gameComponent.gameMode.identifier.equals(SREGameModes.TNT_TAG_MODE.identifier)) {
                if (SREClient.modifierComponent.isModifier(target_player, SpecialGameModeModifiers.TNT_TAGGED))
                    return SpecialGameModeModifiers.TNT_TAGGED.color();
                return TMMRoles.CIVILIAN.color();
            }
            return -1;
        });
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (!(target instanceof Player targetPlayer))
                return -1;
            if (Minecraft.getInstance() == null || Minecraft.getInstance().player == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (SREClient.gameComponent == null || !SREClient.gameComponent.isRole(self, ModRoles.THE_FOOL))
                return -1;

            FoolPlayerComponent component = FoolPlayerComponent.KEY.get(self);
            if (component.hereticTarget == null)
                return -1;
            if (!component.hereticTarget.equals(targetPlayer.getUUID()))
                return -1;
            return 0xF2C56A;
        });

        // 记者便签
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            if (!(target instanceof io.wifi.starrailexpress.content.entity.NoteEntity note))
                return -1;
            if (SREClient.gameComponent.isRole(self, ModRoles.AWESOME_BINGLUS)) {
                return getGradientColor(note.getId());
            }

            return -1;
        });

        // 殡仪员：透视物品掉落物
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            if (!(target instanceof net.minecraft.world.entity.item.ItemEntity itemEntity))
                return -1;

            // 检查是否是殡仪员
            if (!SREClient.gameComponent.isRole(self, ModRoles.MORTICIAN))
                return -1;

            // 检查是否在范围内（10格水平，3格垂直）
            double dx = self.getX() - itemEntity.getX();
            double dy = self.getY() - itemEntity.getY();
            double dz = self.getZ() - itemEntity.getZ();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);
            if (horizontalDist > 10.0 || Math.abs(dy) > 3.0)
                return -1;

            // 返回渐变颜色高亮
            return getGradientColor(itemEntity.getId());
        });

        // 恋人
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (self == null)
                return -1;
            if (!(target instanceof Player))
                return -1;
            if (!WorldModifierComponent.KEY.get(self.level()).isModifier(self, SEModifiers.LOVERS))
                return -1;
            var lc = LoversComponent.KEY.get(self);
            if (lc.getLover().equals(target.getUUID())) {
                return SEModifiers.LOVERS.color();
            }
            return -1;
        });
        // 明星
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (self == null)
                return -1;
            if (!(target instanceof Player targetPlayer))
                return -1;
            var itemStack = MCItemsUtils.getFirstMatchedItem(self, (it) -> it.getItem() instanceof SignedPaperItem);
            if (itemStack != null) {
                String owner = itemStack.getOrDefault(SREDataComponentTypes.OWNER, "NULL");
                if (targetPlayer.getScoreboardName().equals(owner)) {
                    return new Color(254, 254, 254).getRGB();
                }
            }
            return -1;
        });
        // 死亡惩罚
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (!(self.isSpectator()))
                return -1;
            if (hasInstinct) {
                var deathPenalty = org.agmas.noellesroles.component.ModComponents.DEATH_PENALTY.get(self);
                if (deathPenalty.hasPenalty()) {
                    if (!deathPenalty.chatEnabled)
                        return -2;
                    if (target instanceof Player target_player) {
                        if (target_player.isSpectator())
                            return -2;
                        return new java.awt.Color(253, 253, 253).getRGB();
                    } else {
                        return -2;
                    }
                }
            }
            return -1;

        });

        // 秉烛人：可透视被秉烛的活人与对应尸体
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.CANDLE_BEARER))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!hasInstinct)
                return -1;
            CandleBearerPlayerComponent component = CandleBearerPlayerComponent.KEY.get(self);
            // 尸体：已被完成秉烛的显示蓝色
            if (target instanceof PlayerBodyEntity body) {
                if (body.getPlayerUuid() != null && component.isCorpseCandleCompleted(body.getPlayerUuid())) {
                    return Color.BLUE.getRGB();
                }
                if (body.getPlayerUuid() != null && component.isCandleLit(body.getPlayerUuid())) {
                    return ModRoles.CANDLE_BEARER.color();
                }
                return Color.GRAY.getRGB();
            }
            // 活人：被秉烛过的显示原色
            if (target instanceof Player targetPlayer) {
                if (targetPlayer.distanceToSqr(self) > 40 * 40)
                    return -2;
                if (component.isCandleLit(targetPlayer.getUUID())) {
                    return ModRoles.CANDLE_BEARER.color();
                }
                return Color.GRAY.getRGB();
            }
            return -1;
        });

        // 疫使：透视所有玩家，被感染者显示橙色边框
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.INFECTED))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (!hasInstinct)
                return -1;

            if (target instanceof Player targetPlayer) {
                // 检查目标玩家是否被感染（非疫使角色的玩家被感染）
                InfectedPlayerComponent infectedComponent = ModComponents.INFECTED.get(targetPlayer);
                if (infectedComponent != null && infectedComponent.infectedTicks > 0) {
                    // 被感染者显示橙色边框
                    return Color.ORANGE.getRGB();
                }
                // 其他玩家显示疫使的颜色
                return ModRoles.INFECTED.color();
            }
            return -1;
        });

        // 葬仪：看所有人和尸体都是自己的颜色，且可以透视场上所有尸体
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.MORTICIAN_BODYMAKER))
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;

            // 葬仪总是可以看到尸体（不需要开启杀手直觉）
            if (target instanceof PlayerBodyEntity) {
                return ModRoles.MORTICIAN_BODYMAKER.color();
            }

            // 需要开启杀手直觉才能看到玩家
            if (!hasInstinct)
                return -1;

            // 所有玩家都显示葬仪的颜色
            if (target instanceof Player) {
                return ModRoles.MORTICIAN_BODYMAKER.color();
            }
            return -1;
        });

        // 雇佣兵：仅在雇佣兵客户端将其合约目标高亮显示
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null)
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.MERCENARY))
                return -1;
            if (!GameUtils.isPlayerAliveAndSurvival(self))
                return -1;

            var mercComp = ModComponents.MERCENARY.get(self);
            if (mercComp == null || !mercComp.contractActive)
                return -1;

            // 检查目标是否为合约目标（支持活人或尸体实体）
            if (target instanceof Player targetPlayer) {
                if (targetPlayer.getUUID().equals(mercComp.contractTargetUuid)) {
                    return ModRoles.MERCENARY.color();
                }
                return -1;
            }
            if (target instanceof PlayerBodyEntity body) {
                if (body.getPlayerUuid() != null && body.getPlayerUuid().equals(mercComp.contractTargetUuid)) {
                    return ModRoles.MERCENARY.color();
                }
                return -1;
            }
            return -1;
        });
        // 验尸官
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(self, ModRoles.CORONER)) {
                return -1;
            }

            long time = self.level().getGameTime();
            if (time % 400 >= 100) {
                return -1;

            }

            if (target instanceof PlayerBodyEntity) {
                return (ModRoles.CORONER.color());
            }

            if (target instanceof Player targetPlayer) {
                InsaneKillerPlayerComponent component = InsaneKillerPlayerComponent.KEY.get(targetPlayer);
                if (component.isActive) {
                    return (ModRoles.CORONER.color());
                }
            }
            return -1;
        });
        // 初学者
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            Player player = Minecraft.getInstance().player;
            if (!SREClient.gameComponent.isRole(Minecraft.getInstance().player, SERoles.INITIATE)) {
                return -1;
            }
            if (SREItemUtils.countItem(player, TMMItems.KNIFE) <= 0) {
                return -1;
            }
            if (target instanceof Player targettedPlayer) {
                if (SREClient.gameComponent.isRole(targettedPlayer, SERoles.INITIATE)) {
                    return (SERoles.INITIATE.color());
                }
            }
            return -1;
        });
        // 纵火犯
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            var player = Minecraft.getInstance().player;
            if (!(target instanceof Player targettedPlayer)) {
                return -1;
            }
            if (Minecraft.getInstance() == null)
                return -1;
            if (Minecraft.getInstance().player == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(Minecraft.getInstance().player))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(player, SERoles.ARSONIST)) {
                return -1;
            }
            if (SREClient.isPlayerSpectatingOrCreative()) {
                return -1;
            }
            if (!SREClient.isInstinctEnabled()) {
                return -1;
            }

            if (targettedPlayer.distanceToSqr(player) > 40 * 40)
                return -2;
            var douse = DousedPlayerComponent.KEY.get(targettedPlayer);
            if (douse.getDoused()) {
                return (SERoles.ARSONIST.color());
            } else {
                return (Color.GRAY.getRGB());
            }
        });
        // 记者
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!(target instanceof Player targetPlayer)) {
                return -1;
            }
            if (targetPlayer.isInvisibleTo(self))
                return -1;
            if (!SREClient.gameComponent.isRole(self, ModRoles.AWESOME_BINGLUS)) {
                return -1;
            }
            if (SREClient.isPlayerSpectatingOrCreative()) {
                return -1;
            }
            if (targetPlayer.distanceTo(self) <= 5) {
                var awpc = AwesomePlayerComponent.KEY.get(targetPlayer);
                if (awpc.nearByDeathTime <= 1)
                    return -1;
                int redDepth = (int) (255
                        * ((float) awpc.nearByDeathTime
                                / (float) AwesomePlayerComponent.nearByDeathTimeRecordTime));
                redDepth = Math.clamp(redDepth, 0, 255);
                return new Color(redDepth, 0, 0).getRGB();
            }
            return -1;
        });
        // 侦探
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!(target instanceof Player targetPlayer)) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(targetPlayer, ModRoles.CONSPIRATOR)) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(self, ModRoles.DETECTIVE)) {
                return -1;
            }
            var awpc = DetectivePlayerComponent.KEY.get(self);
            if (awpc.conspiratorInstinctTime <= 0)
                return -1;
            return ModRoles.DETECTIVE.color();
        });
        // 失忆
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
            if (self == null)
                return -1;
            if (GameUtils.isPlayerSpectatingOrCreative(self))
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (!(target instanceof PlayerBodyEntity)) {
                return -1;
            }
            if (!SREClient.gameComponent.isRole(self, SERoles.AMNESIAC)) {
                return -1;
            }
            if (SREClient.isPlayerSpectatingOrCreative()) {
                return -1;
            }
            return SERoles.AMNESIAC.color();
        });

        // 通用逻辑
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            Minecraft client = Minecraft.getInstance();
            if (client == null)
                return -1;
            var self = client.player;
            if (self == null)
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(target.level());
            var self_role = SREClient.gameComponent.getRole(self);
            if (worldModifierComponent != null) {
                if (worldModifierComponent.isModifier(self, SEModifiers.SPLIT_PERSONALITY)) {
                    if (self.isSpectator()) {
                        var splitComponent = SplitPersonalityComponent.KEY.get(self);
                        if (splitComponent != null && !splitComponent.isDeath()) {
                            return -2;
                        }
                    }
                }
            }
            // 布谷鸟：无法透视玩家；非布谷鸟：无法透视蛋
            if (SREClient.gameComponent.isRole(self, ModRoles.CUCKOO)) {
                if (target instanceof Player) {
                    return -2;
                }
            } else {
                if (target instanceof Display.BlockDisplay) {
                    return -2;
                }
            }
            if (target instanceof Player target_player) {
                // 不开直觉，默认有
                // 红尘客
                if (SREClient.gameComponent.isRole(self, ModRoles.WAYFARER)) {
                    if (GameUtils.isPlayerAliveAndSurvival(target_player)) {
                        var wayC = WayfarerPlayerComponent.KEY.get(self);
                        if (wayC.phase == 1) {
                            if (wayC.killer != null) {
                                if (target_player.getUUID().equals(wayC.killer)) {
                                    return Color.RED.getRGB();
                                }
                            }
                        }
                    }
                }
                // JOJO
                if (SREClient.gameComponent.isRole(self, ModRoles.JOJO)) {
                    if (GameUtils.isPlayerAliveAndSurvival(target_player)) {
                        if (target_player.distanceTo(self) <= 3) {
                            if (SREClient.gameComponent.isRole(target_player, ModRoles.DIO)) {
                                if (self.hasEffect(ModEffects.SKILL_BANED)) {
                                    return -1;
                                }
                                return ModRoles.DIO.color();
                            }
                        }
                    }
                }
                var target_role = SREClient.gameComponent.getRole(target_player);
                SREArmorPlayerComponent armorPlayerComponent = SREArmorPlayerComponent.KEY.get(target_player);
                SREPlayerPoisonComponent playerPoisonComponent = SREPlayerPoisonComponent.KEY.get(target_player);
                if (SREClient.gameComponent.isRole(self, ModRoles.BETTER_VIGILANTE)) {
                    var betterC = BetterVigilantePlayerComponent.KEY.get(self);
                    if (betterC.lastStandActivated) {
                        return (Color.BLUE.getRGB());
                    }
                }
                if (SREClient.gameComponent.isRole(self, RedHouseRoles.PACHURI)) {
                    if (!self.hasEffect(ModEffects.SAFE_TIME)) {
                        if (target.distanceToSqr(self) <= 25) {
                            if (SREClient.gameComponent.isRole(target_player, RedHouseRoles.FURANDORU)) {
                                return RedHouseRoles.FURANDORU.color();
                            }
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.CHEF)) {
                    // LoggerFactory.getLogger("renderer").info("glowTick {}",
                    // bartenderPlayerComponent.glowTicks);
                    if (self.hasEffect(ModEffects.SAFE_TIME))
                        return -1;
                    int t = FoodDrinkGlowComponent.KEY.get(self).glowTicks
                            .getOrDefault(target.getScoreboardName(), new HashMap<>())
                            .getOrDefault(1, 0);
                    if (t > 0) {
                        return (Color.GREEN.getRGB());
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.BARTENDER)) {
                    // LoggerFactory.getLogger("renderer").info("glowTick {}",
                    // bartenderPlayerComponent.glowTicks);
                    if (self.hasEffect(ModEffects.SAFE_TIME))
                        return -1;
                    if (armorPlayerComponent.getArmor() > 0 && playerPoisonComponent.poisonTicks > 0) {
                        return (new Color(186, 255, 65).getRGB());

                    }
                    if (armorPlayerComponent.getArmor() > 0) {
                        if (target_role.identifier().equals(ModRoles.WATCHER_ID)) {
                            return -1;
                        }
                        return (Color.BLUE.getRGB());
                    }
                    int t = FoodDrinkGlowComponent.KEY.get(self).glowTicks
                            .getOrDefault(target.getScoreboardName(), new HashMap<>())
                            .getOrDefault(0, 0);
                    if (t > 0) {
                        return (Color.GREEN.getRGB());
                    }
                }
                if ((SREClient.gameComponent.isRole(self, ModRoles.BARTENDER)
                        || SREClient.gameComponent.isRole(self, ModRoles.POISONER))
                        && playerPoisonComponent.poisonTicks > 0) {
                    return (Color.RED.getRGB());
                }

                if (SREClient.gameComponent.isRole(self, ModRoles.EXECUTIONER)) {
                    ExecutionerPlayerComponent executionerPlayerComponent = (ExecutionerPlayerComponent) ExecutionerPlayerComponent.KEY
                            .get(self);
                    if (executionerPlayerComponent != null && executionerPlayerComponent.target != null) {
                        if (executionerPlayerComponent.target.equals(target.getUUID())
                                && !SREClient.gameComponent.isRole(target.getUUID(), ModRoles.GHOST)) {
                            return new java.awt.Color(0, 254, 254).getRGB();
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.MANIPULATOR)) {
                    ManipulatorPlayerComponent manipulatorPlayerComponent = (ManipulatorPlayerComponent) ManipulatorPlayerComponent.KEY
                            .get(self);
                    if (manipulatorPlayerComponent != null && manipulatorPlayerComponent.target != null) {
                        if (manipulatorPlayerComponent.target.equals(target.getUUID())) {
                            return (Color.orange.getRGB());
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.ADMIRER)) {
                    AdmirerPlayerComponent admirerPlayerComponent = (AdmirerPlayerComponent) AdmirerPlayerComponent.KEY
                            .get(self);
                    if (admirerPlayerComponent != null && admirerPlayerComponent.getBoundTarget() != null) {
                        if (admirerPlayerComponent.getBoundTarget().getUUID().equals(target.getUUID())) {
                            // LoggerFactory.getLogger("Instinct").info("PINK");
                            return (Color.PINK.getRGB());
                        }
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.MONITOR)) {
                    MonitorPlayerComponent monitorComponent = MonitorPlayerComponent.KEY
                            .get(self);
                    if (monitorComponent != null && monitorComponent.getMarkedTarget() != null) {
                        if (monitorComponent.getMarkedTarget().equals(target.getUUID())) {
                            return (Color.CYAN.getRGB());
                        }
                    }
                }
                // 需要开启直觉
                if (!hasInstinct)
                    return -1;
                if (GameUtils.isPlayerSpectatingOrCreative(self))
                    return -1; // 旁观默认高亮
                // 直觉看不到旁观
                if ((target_player).isSpectator())
                    return -2;

                // 小透明：杀手无法看到高亮（杀手，与大部分中立偏狼）
                if (SREClient.gameComponent.isRole(target_player, ModRoles.GHOST) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }

                // 风精灵
                if (SREClient.gameComponent.isRole(self, ModRoles.WIND_YAOSE)) {
                    return ModRoles.WIND_YAOSE.getColor();
                }

                if (SREClient.gameComponent.isRole(self, RedHouseRoles.FURANDORU)) {
                    if (target_role != null) {
                        if (RoleUtils.compareRole(target_role, RedHouseRoles.PACHURI)) {
                            return RedHouseRoles.PACHURI.color();
                        }
                        return new Color(2, 224, 2).getRGB();
                    }
                    return -1;
                }
                // 傀儡师
                PuppeteerPlayerComponent selfPuppeteerComp = ModComponents.PUPPETEER.get(self);
                if (selfPuppeteerComp.isControllingPuppet && SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()) {
                    return ModRoles.PUPPETEER.color();
                }
                if (selfPuppeteerComp.isPuppeteerMarked && SREClient.isPlayerAliveAndInSurvivalIgnoreShitSplit()
                        && selfPuppeteerComp.phase >= 1) {
                    return -1;
                }
                // 黑白熊形态：对所有人隐藏高亮
                if (SREClient.gameComponent.isRole(target_player, ModRoles.MONOKUMA)
                        && MonokumaEventHandler.isMonokumaBearForm(target_player)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.MONOKUMA) && SREClient.isPlayerAliveAndInSurvival()) {
                    return ModRoles.MONOKUMA.color();
                }

                // 黑白狂暴前奏：杀手看到灰色
                // if (SREClient.gameComponent.isRole(target_player, ModRoles.MONOKUMA)
                // && MonokumaEventHandler.isInFrenzy(target_player)
                // && SREClient.isPlayerAliveAndInSurvival()) {
                // return Color.RED.getRGB();
                // }
                // 秉烛人：杀手无法透视察觉
                if (SREClient.gameComponent.isRole(target_player, ModRoles.CANDLE_BEARER) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                // 雇佣兵：杀手直觉无法透视
                if (SREClient.gameComponent.isRole(target_player, ModRoles.MERCENARY) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                // 记录员
                if (SREClient.gameComponent.isRole(self, ModRoles.RECORDER)) {
                    if (target instanceof Player targetPlayer) {
                        if (self.isSpectator())
                            return -1;
                        if (targetPlayer.distanceToSqr(self) > 20 * 20)
                            return -2;
                        if (targetPlayer == self)
                            return -2;

                        RecorderPlayerComponent recorder = ModComponents.RECORDER.get(self);
                        if (recorder.getGuesses().containsKey(targetPlayer.getUUID())) {
                            // 已记录（猜测过）：亮黄色
                            return (0xFFFF55);
                        } else {
                            // 未记录：暗蓝色
                            return (0x0000AA);
                        }
                    }
                }
                // 爱慕
                if (SREClient.gameComponent.isRole(self, ModRoles.ADMIRER) && SREClient.isPlayerAliveAndInSurvival()) {
                    return (Color.PINK.getRGB());
                }
                // 小丑&LOOSE END
                if ((SREClient.gameComponent.isRole(self, ModRoles.JESTER)
                        || SREClient.gameComponent.isRole(self, TMMRoles.LOOSE_END)
                        || SREClient.gameComponent.isRole(self, SpecialGameModeRoles.SUPER_LOOSE_END)
                        || SREClient.gameComponent.isRole(self, SpecialGameModeRoles.DIRT))
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.GHOST)) {
                        return -2;
                    }
                    // 超级亡命徒无法看到隐身的人
                    if (SREClient.gameComponent.isRole(self, SpecialGameModeRoles.SUPER_LOOSE_END) &&
                        target_player.isInvisible()) {
                        return -2;
                    }
                    return (Color.PINK.getRGB());
                }
                // // 柜子区
                // if (SREClient.gameComponent.isRole(self, ModRoles.EXECUTIONER)
                // && SREClient.isPlayerAliveAndInSurvival()) {
                // return (ModRoles.EXECUTIONER.color());
                // }

                // 杀手直觉
                if (isKillerTeam(self_role) && SREClient.isPlayerAliveAndInSurvival()) {
                    // 布袋鬼：里世界期间无杀手直觉
                    if (SREClient.gameComponent.isRole(self, ModRoles.MA_CHEN_XU)) {
                        MaChenXuPlayerComponent macComp = MaChenXuPlayerComponent.KEY.get(self);
                        if (macComp != null && macComp.otherworldActive) {
                            return -2;
                        }
                    }
                    // 强盗直觉：只能透视半径10格内的玩家，透视杀手队友无距离限制
                    if (SREClient.gameComponent.isRole(self, ModRoles.BANDIT)) {
                        // 检查目标是否是杀手队友
                        if (target_role != null && SREClient.gameComponent.isKillerTeamRole(target_role)) {
                            // 杀手队友无距离限制
                        } else {
                            // 普通玩家只能透视10格内
                            if (target_player.distanceTo(self) >= 10) {
                                return -2;
                            }
                        }
                    }

                    // 魔术师：杀手看魔术师时显示红色边框（像看其他杀手一样）
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.MAGICIAN)) {
                        target_role = RoleUtils
                                .getRole(MagicianPlayerComponent.KEY.get(target_player).getDisguiseRoleId());
                    }

                    if (RoleUtils.compareRole(target_role, ModRoles.PUPPETEER)) {
                        // int entityOffset = target_player.getId() * 7;
                        return (ModRoles.PUPPETEER.color());
                    }
                    if (SREClient.gameComponent.isRole(self, ModRoles.COMMANDER)) {
                        if (isKillerTeam(target_role)) {
                            return getRoleColor(target_role);
                        }
                        if (target_player.distanceTo(self) <= 5) {
                            var role = SREClient.gameComponent.getRole(target_player);
                            if (role != null && role.isVigilanteTeam()) {
                                return new Color(63, 72, 204).getRGB();
                            }
                        }
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.VULTURE)) {
                        return (ModRoles.VULTURE.color());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.ADMIRER)) {
                        return (ModRoles.ADMIRER.color());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.EXECUTIONER)) {
                        return (ModRoles.EXECUTIONER.color());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.JESTER)) {
                        return (Color.PINK.getRGB());
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.LOST_KILLER)) {
                        return TMMRoles.CIVILIAN.color();
                    }
                    if (RoleUtils.compareRole(target_role, ModRoles.SLIPPERY_GHOST)) {
                        return -2;
                    }
                    if (RoleUtils.compareRole(target_role, SERoles.AMNESIAC)) {
                        if (StupidExpress.CONFIG.rolesSection.amnesiacSection.amnesiacGlowsDifferently) {
                            return SERoles.AMNESIAC.color();
                        }
                    }

                    if (SREClient.gameComponent.isRole(self, RedHouseRoles.REMILIA)) {
                        if (!self.hasEffect(ModEffects.SAFE_TIME)) {
                            if (target.distanceToSqr(self) <= 25) {
                                if (RoleUtils.compareRole(target_role, RedHouseRoles.PACHURI)) {
                                    return RedHouseRoles.PACHURI.color();
                                } else if (RoleUtils.compareRole(target_role, RedHouseRoles.FURANDORU)) {
                                    return RedHouseRoles.FURANDORU.color();
                                }
                            }
                        }

                    }
                    // 疫使：杀手本能中透视的框为深绿色
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.INFECTED)) {
                        return new Color(0, 100, 0).getRGB(); // 深绿色
                    }
                    // 葬仪：杀手本能中透视的框为淡灰色
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.MORTICIAN_BODYMAKER)) {
                        return new Color(180, 180, 180).getRGB(); // 淡灰色
                    }
                    // 肉汁：当杀手在4格范围内时，该杀手的透视框变为深蓝色
                    if (RoleUtils.compareRole(target_role, ModRoles.MEATBALL)) {
                        if (self.distanceTo(target_player) <= 4.0) {
                            return new Color(0, 0, 180).getRGB(); // 深蓝色
                        }
                    }
                    
                // 默认fallback
                    if (target_role == null)
                        return Color.WHITE.getRGB();
                    if (target_role.canUseKiller()) {
                        return Color.RED.getRGB();
                    } else if (target_role.isNeutralForKiller()) {
                        return Color.ORANGE.getRGB();
                    } else {
                        if (SREClient.gameComponent.isRole(self, ModRoles.MA_CHEN_XU)) {
                            if (SREPlayerMoodComponent.KEY.get(target_player).getMood() <= 0.1) {
                                return java.awt.Color.CYAN.getRGB();// 青色
                            }
                        }
                        if (SREClient.gameComponent.isRole(self, ModRoles.DIO)) {
                            if (RoleUtils.compareRole(target_role, ModRoles.JOJO)) {
                                return Color.CYAN.getRGB();
                            }
                        }
                        if (SREGameTimeComponent.KEY.get(client.level).getTime() >= GameConstants
                                .getFurandoruSafeLine()) {
                            if (SREClient.gameComponent.isRole(target_player, RedHouseRoles.FURANDORU)) {
                                return -2;
                            }
                        }
                        if (SREClient.gameComponent.isRole(target_player, ModRoles.GAMBLER)) {
                            return -2;
                        }
                        return TMMRoles.CIVILIAN.color();
                    }
                }
            }
            // 布谷鸟：只透视自己下的蛋（BlockDisplay）
            if (SREClient.gameComponent.isRole(self, ModRoles.CUCKOO)) {
                if (target instanceof Display.BlockDisplay blockDisplay) {
                    if (!hasInstinct)
                        return -1;
                    if (!GameUtils.isPlayerAliveAndSurvival(self))
                        return -1;
                    try {
                        if (CuckooEggData.isOwnEggClient(blockDisplay)) {
                            return ModRoles.CUCKOO.color();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            return -1;
        });
    }

    private static int getRoleColor(SRERole target_role) {
        if (target_role == null)
            return TMMRoles.CIVILIAN.color();
        return target_role.color();
    }

    private static boolean isKillerTeam(SRERole role) {
        if (role == null)
            return false;
        if (role.canUseKiller())
            return true;
        if (role.canUseInstinct() && role.isNeutralForKiller())
            return true;
        return false;
    }

    private static final int[] GRADIENT_COLORS = {
            new Color(255, 0, 0).getRGB(), // 红色
            new Color(255, 85, 0).getRGB(), // 橙红
            new Color(255, 170, 0).getRGB(), // 橙色
            new Color(255, 255, 0).getRGB(), // 黄色
            new Color(255, 170, 0).getRGB(), // 橙色
            new Color(255, 85, 0).getRGB(), // 橙红
    };

    // 渐变周期（tick）
    private static final int GRADIENT_CYCLE = 60; // 3秒一个周期

    /**
     * 获取渐变颜色
     * 
     * @param tickOffset 每个实体的偏移量，使不同实体颜色略有不同
     * @return 当前渐变颜色
     */
    public static int getGradientColor(int tickOffset) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null)
            return GRADIENT_COLORS[0];

        long worldTime = client.level.getGameTime();
        int cyclePosition = (int) ((worldTime + tickOffset) % GRADIENT_CYCLE);

        // 计算在颜色数组中的位置
        float progress = (float) cyclePosition / GRADIENT_CYCLE * GRADIENT_COLORS.length;
        int colorIndex = (int) progress;
        float blend = progress - colorIndex;

        // 获取当前颜色和下一个颜色
        int currentColor = GRADIENT_COLORS[colorIndex % GRADIENT_COLORS.length];
        int nextColor = GRADIENT_COLORS[(colorIndex + 1) % GRADIENT_COLORS.length];

        // 混合两个颜色
        return blendColors(currentColor, nextColor, blend);
    }

    /**
     * 混合两个颜色
     */
    public static int blendColors(int color1, int color2, float blend) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * blend);
        int g = (int) (g1 + (g2 - g1) * blend);
        int b = (int) (b1 + (b2 - b1) * blend);

        return (r << 16) | (g << 8) | b;
    }
}
