package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.SREArmorPlayerComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerPoisonComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.event.OnGetInstinctHighlight;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.TMMItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.*;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.roles.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.roles.manipulator.ManipulatorPlayerComponent;
import org.agmas.noellesroles.utils.MCItemsUtils;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.modifier.split_personality.cca.SplitPersonalityComponent;
import pro.fazeclan.river.stupid_express.role.arsonist.cca.DousedPlayerComponent;

import java.awt.*;

public class InstinctRenderer {
    public static void registerInstinctEvents() {
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
            var itemStack = MCItemsUtils.getFirstMatchedItem(self, ModItems.SIGNED_PAPER);
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
            if (GameUtils.isPlayerAliveAndSurvival(self))
                return -1;
            if (self == null)
                return -1;
            if (hasInstinct) {
                var deathPenalty = org.agmas.noellesroles.component.ModComponents.DEATH_PENALTY.get(self);
                if (deathPenalty.hasPenalty()) {
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
        // 傀儡师
        OnGetInstinctHighlight.EVENT.register((target, hasInstinct) -> {
            var client = Minecraft.getInstance();
            if (client == null || client.player == null)
                return -1;
            if (SREClient.gameComponent == null) {
                return -1;
            }
            if (GameUtils.isPlayerSpectatingOrCreative(client.player))
                return -1;
            if (target instanceof Player) {
                PuppeteerPlayerComponent selfPuppeteerComp = ModComponents.PUPPETEER.get(client.player);
                if (selfPuppeteerComp.isControllingPuppet && SREClient.isPlayerAliveAndInSurvival()) {
                    return ModRoles.PUPPETEER.color();
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
            if (TMMItemUtils.hasItem(player, TMMItems.KNIFE) <= 0) {
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
            if (Minecraft.getInstance() == null)
                return -1;
            var self = Minecraft.getInstance().player;
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
                                return ModRoles.DIO.color();
                            }
                        }
                    }
                }
                var target_role = SREClient.gameComponent.getRole(target_player);
                SREArmorPlayerComponent bartenderPlayerComponent = SREArmorPlayerComponent.KEY.get(target_player);
                SREPlayerPoisonComponent playerPoisonComponent = SREPlayerPoisonComponent.KEY.get(target_player);
                if (SREClient.gameComponent.isRole(self, ModRoles.BETTER_VIGILANTE)) {
                    var betterC = BetterVigilantePlayerComponent.KEY.get(self);
                    if (betterC.lastStandActivated) {
                        return (Color.BLUE.getRGB());
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.CHEF)) {
                    // LoggerFactory.getLogger("renderer").info("glowTick {}",
                    // bartenderPlayerComponent.glowTicks);
                    int t = bartenderPlayerComponent.glowTicks.getOrDefault(1, 0);
                    if (t > 0) {
                        return (Color.GREEN.getRGB());
                    }
                }
                if (SREClient.gameComponent.isRole(self, ModRoles.BARTENDER)) {
                    // LoggerFactory.getLogger("renderer").info("glowTick {}",
                    // bartenderPlayerComponent.glowTicks);
                    if (bartenderPlayerComponent.getArmor() > 0 && playerPoisonComponent.poisonTicks > 0) {
                        return (new Color(186, 255, 65).getRGB());
                    }
                    if (bartenderPlayerComponent.getArmor() > 0) {
                        return (Color.BLUE.getRGB());
                    }
                    if (bartenderPlayerComponent.glowTicks.getOrDefault(0, 0) > 0) {
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
                        if (executionerPlayerComponent.target.equals(target.getUUID())) {
                            return (Color.orange.getRGB());
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
                // 风精灵
                if (SREClient.gameComponent.isRole(self, ModRoles.WIND_YAOSE)) {
                    return ModRoles.WIND_YAOSE.getColor();
                }
                // 傀儡师
                PuppeteerPlayerComponent selfPuppeteerComp = ModComponents.PUPPETEER.get(self);
                if (selfPuppeteerComp.isPuppeteerMarked && SREClient.isPlayerAliveAndInSurvival()
                        && selfPuppeteerComp.phase >= 1) {
                    return -1;
                }
                // 小透明：杀手无法看到高亮（所有，包括爱慕）
                if (SREClient.gameComponent.isRole(target_player, ModRoles.GHOST) && isKillerTeam(self_role)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return -2;
                }
                // 记录员
                if (SREClient.gameComponent.isRole(self, ModRoles.RECORDER)) {
                    if (target instanceof Player targetPlayer) {
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
                        || SREClient.gameComponent.isRole(self, TMMRoles.LOOSE_END))
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    if (SREClient.gameComponent.isRole(target_player, ModRoles.GHOST)) {
                        return -2;
                    }
                    return (Color.PINK.getRGB());
                }
                // 柜子区
                if (SREClient.gameComponent.isRole(self, ModRoles.EXECUTIONER)
                        && SREClient.isPlayerAliveAndInSurvival()) {
                    return (ModRoles.EXECUTIONER.color());
                }

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
                    if (RoleUtils.compareRole(target_role, ModRoles.SLIPPERY_GHOST)) {
                        return -2;
                    }
                    if (RoleUtils.compareRole(target_role, SERoles.AMNESIAC)) {
                        if (StupidExpress.CONFIG.rolesSection.amnesiacSection.amnesiacGlowsDifferently) {
                            return SERoles.AMNESIAC.color();
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
                        if (SREClient.gameComponent.isRole(target_player, ModRoles.GAMBLER)) {
                            return -2;
                        }
                        return TMMRoles.CIVILIAN.color();
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