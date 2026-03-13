package org.agmas.noellesroles.mixin;

import io.wifi.starrailexpress.cca.StarGameWorldComponent;
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
 */
@Mixin(targets = "org.agmas.harpymodloader.modded_murder.ModdedMurderGameMode")
public class MagicianKillerCountMixin {

    /**
     * 在 initializeGame 方法中，修改发送给玩家的 AnnounceWelcomePayload 中的 killerCount
     * 加上魔术师的数量
     */
    @ModifyArgs(
        method = "assignRole",
        at = @At(
            value = "INVOKE",
            target = "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking;send(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/protocol/common/custom/CustomPacketPayload;)V"
        )
    )
    private static void modifyAnnounceWelcomePayload(Args args, ServerLevel serverWorld, StarGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        // 获取第二个参数（AnnounceWelcomePayload）
        Object payload = args.get(1);
        
        if (payload instanceof AnnounceWelcomePayload announcePayload) {
            // 计算场上魔术师的数量
            int magicianCount = 0;
            for (ServerPlayer player : players) {
                if (gameWorldComponent.isRole(player, ModRoles.MAGICIAN)) {
                    magicianCount++;
                }
            }
            
            // 如果有魔术师，则修改杀手数量
            if (magicianCount > 0) {
                int originalKillers = announcePayload.killers();
                int modifiedKillers = originalKillers + magicianCount;
                
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
