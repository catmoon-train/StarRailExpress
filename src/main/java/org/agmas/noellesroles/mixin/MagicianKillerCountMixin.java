package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.network.original.AnnounceWelcomePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import java.util.List;

/**
 * 魔术师杀手数量修正 Mixin
 * 
 * 当魔术师在场时，开局显示的杀手数量需要加上魔术师的数量
 * 这样可以让其他玩家误以为魔术师也是杀手，增加混淆效果
 * 
 * 目标: SREMurderGameMode.assignRole - 修改 AnnounceWelcomePayload 中的 killers 数量
 */
@Mixin(io.wifi.starrailexpress.game.modes.SREMurderGameMode.class)
public class MagicianKillerCountMixin {

    /**
     * 在 assignRole 方法中，修改发送给玩家的 AnnounceWelcomePayload 中的 killerCount
     * 加上魔术师的数量
     */
    @ModifyArgs(
        method = "assignRole",
        at = @At(
            value = "INVOKE",
            target = "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking;send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
        )
    )
    private static void modifyAnnounceWelcomePayload(Args args, ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        // 获取第一个参数（玩家）
        Object playerObj = args.get(0);
        // 获取第二个参数（AnnounceWelcomePayload）
        Object payload = args.get(1);
        
        if (payload instanceof AnnounceWelcomePayload announcePayload) {
            // 计算场上魔术师的数量
            long magicianCount = players.stream()
                .filter(p -> gameWorldComponent.isRole(p, ModRoles.MAGICIAN))
                .count();
            
            // 如果有魔术师，则修改杀手数量
            if (magicianCount > 0) {
                int originalKillers = announcePayload.killers();
                int modifiedKillers = originalKillers + (int) magicianCount;
                
                // 创建新的 payload，修改杀手数量
                AnnounceWelcomePayload modifiedPayload = new AnnounceWelcomePayload(
                    announcePayload.role(),
                    modifiedKillers,
                    announcePayload.targets()
                );
                
                // 替换原来的 payload
                args.set(1, modifiedPayload);
            }
        }
    }
}
