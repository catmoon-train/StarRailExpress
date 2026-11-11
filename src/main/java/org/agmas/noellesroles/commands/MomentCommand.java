package org.agmas.noellesroles.commands;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.game.moment.MomentManager;
import org.agmas.noellesroles.game.moment.MomentType;
import org.agmas.noellesroles.game.roles.neutral.infected.InfectedWinChecker;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role.touhou.THRedHouseRoles;
import org.agmas.noellesroles.role_data.innocence.GhostRoleData;
import org.agmas.noellesroles.role_data.neutral.AmonRoleData;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.constants.SERoles;
import pro.fazeclan.river.stupid_express.role.arsonist.ArsonistRoleData;

/** 管理员转职并强制启动对应时刻：/moment <amon|devil_sister|arsonist|infected> [player]。 */
public class MomentCommand {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("moment").requires(source -> source.hasPermission(2));
            for (Moment moment : Moment.values()) {
                root.then(Commands.literal(moment.argument)
                        .executes(context -> execute(context.getSource(), moment, context.getSource().getPlayer()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> execute(context.getSource(), moment,
                                        EntityArgument.getPlayer(context, "player")))));
            }
            dispatcher.register(root);
        });
    }

    private static int execute(CommandSourceStack source, Moment moment, ServerPlayer target) {
        if (target == null) {
            source.sendFailure(Component.translatable("commands.noellesroles.moment.player_required"));
            return 0;
        }
        var game = SREGameWorldComponent.KEY.get(target.serverLevel());
        if (game.getGameStatus() != SREGameWorldComponent.GameStatus.ACTIVE) {
            source.sendFailure(Component.translatable("commands.noellesroles.moment.not_active"));
            return 0;
        }
        if (!target.isAlive() || !GameUtils.isPlayerAliveAndSurvival(target)) {
            source.sendFailure(Component.translatable("commands.noellesroles.moment.target_not_alive",
                    target.getDisplayName()));
            return 0;
        }
        // 时刻默认一局只能触发一次：即使当前效果已结束，本局也不允许管理员再触发一遍。
        if (MomentManager.hasTriggered(target.serverLevel(), moment.type)
                || (game.isRole(target, moment.role) && moment.isActive(target))) {
            source.sendSuccess(() -> Component.translatable("commands.noellesroles.moment.already_active",
                    target.getDisplayName(), moment.role.getName()), false);
            return 1;
        }

        // 使用完整转职流程初始化职业数据、物品和同步；相同职业不重新分配。
        if (!game.isRole(target, moment.role)) {
            RoleUtils.changeRoleAndSendWelcome(target, moment.role);
        }
        if (!moment.start(target)) {
            source.sendFailure(Component.translatable("commands.noellesroles.moment.failed",
                    target.getDisplayName(), moment.role.getName()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.noellesroles.moment.success",
                target.getDisplayName(), moment.role.getName()), true);
        return 1;
    }

    private enum Moment {
        AMON("amon", ModRoles.AMON, MomentType.AMON),
        DEVIL_SISTER("devil_sister", THRedHouseRoles.FURANDORU, MomentType.FURANDORU),
        ARSONIST("arsonist", SERoles.ARSONIST, MomentType.ARSONIST),
        INFECTED("infected", ModRoles.INFECTED, MomentType.INFECTED);

        private final String argument;
        private final SRERole role;
        private final MomentType type;

        Moment(String argument, SRERole role, MomentType type) {
            this.argument = argument;
            this.role = role;
            this.type = type;
        }

        private boolean isActive(ServerPlayer target) {
            return switch (this) {
                case AMON -> {
                    var data = RoleData.getNullable(AmonRoleData.class, target);
                    yield data != null && data.finalePhase;
                }
                case DEVIL_SISTER -> {
                    var data = RoleData.getNullable(GhostRoleData.class, target);
                    yield data != null && data.lastStandNotified;
                }
                case ARSONIST -> {
                    var data = RoleData.getNullable(ArsonistRoleData.class, target);
                    yield data != null && data.arsonMoment;
                }
                case INFECTED -> InfectedWinChecker.isInInfectedMoment()
                        && ModComponents.INFECTED.get(target).spreadAccelerated;
            };
        }

        private boolean start(ServerPlayer target) {
            return switch (this) {
                case AMON -> {
                    var data = RoleData.getNullable(AmonRoleData.class, target);
                    yield data != null && data.forceStartMoment();
                }
                case DEVIL_SISTER -> {
                    var data = RoleData.getNullable(GhostRoleData.class, target);
                    yield data != null && data.forceStartMoment();
                }
                case ARSONIST -> {
                    var data = RoleData.getNullable(ArsonistRoleData.class, target);
                    yield data != null && data.forceStartMoment();
                }
                case INFECTED -> InfectedWinChecker.forceStartMoment(target);
            };
        }
    }
}
