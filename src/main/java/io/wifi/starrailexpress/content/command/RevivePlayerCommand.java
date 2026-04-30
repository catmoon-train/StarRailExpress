package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import org.agmas.noellesroles.component.DefibrillatorComponent;
import org.agmas.noellesroles.component.ModComponents;
import io.wifi.starrailexpress.compat.TrainVoicePlugin;

public class RevivePlayerCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tmm:revive")
                .requires(source -> Harpymodloader.isMojangVerify && source.hasPermission(2))
                .then(Commands.argument("target", EntityArgument.player())
                        .executes(ctx -> revivePlayer(
                                ctx.getSource(),
                                EntityArgument.getPlayer(ctx, "target"),
                                null,
                                false))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> revivePlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"),
                                        Vec3Argument.getVec3(ctx, "pos"),
                                        true)))
                        .then(Commands.argument("force_use_target_pos", BoolArgumentType.bool())
                                .executes(ctx -> revivePlayer(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"),
                                        null,
                                        BoolArgumentType.getBool(ctx, "force_use_target_pos"))))));
    }

    private static int revivePlayer(CommandSourceStack source, ServerPlayer target, Vec3 explicitPos,
                                    boolean forceUseTargetPos) {
        DefibrillatorComponent defib = ModComponents.DEFIBRILLATOR.get(target);
        Vec3 revivePos = explicitPos;

        if (revivePos == null) {
            revivePos = defib.deathPos;
        }
        if (revivePos == null && forceUseTargetPos) {
            revivePos = target.position();
        }

        DeathPenaltyComponent.KEY.get(target).init();

        if (revivePos != null) {
            target.teleportTo(revivePos.x, revivePos.y, revivePos.z);
        }
        target.setGameMode(GameType.ADVENTURE);
        target.setHealth(target.getMaxHealth());

        // 重点：确保离开死人语音群组
        TrainVoicePlugin.resetPlayer(target.getUUID());

        // 清理可能存在的复活器死亡状态，避免后续自动逻辑冲突
        defib.init();

        final String positionSuffix = revivePos != null ? (" at " + revivePos) : "";
        source.sendSuccess(() -> Component.literal("Revived " + target.getName().getString()
                + " to adventure mode" + positionSuffix), true);
        return 1;
    }
}
