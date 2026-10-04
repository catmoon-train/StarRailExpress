package org.agmas.noellesroles.mixin.network;

import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.katana.KatanaCombat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 武士刀突刺（第二招式）的触发入口：捕获<b>挥手包</b>。
 *
 * <p>为什么用挥手包：原版左键只有在命中实体时才会调用 {@code Player#attack}，
 * 而突刺的位移与伤害完全不看准星目标，于是<b>左键空挥没有任何服务端入口</b>。
 * 空挥时客户端唯一的动作就是挥一下手（发 {@link ServerboundSwingPacket}），
 * 所以这里从挥手包反推「玩家刚按了左键」——项目里的反作弊
 * （{@code ClickAntiCheatMixin}）用的正是同一个包、同一处注入来识别空左键。
 *
 * <p>相比「客户端拦 startAttack 再发自定义 C2S 包」的方案，这条路径不依赖客户端
 * 模组是否加载、也不会因招式预测不同步而失效，判定全部在服务端完成。
 *
 * <p>不会与「瞄到人」的旧路径重复出刀：{@link KatanaCombat#handleThrust} 内部有
 * {@code nextMove} 与 {@code thrustDashing} 双重判定，已在突刺中就直接丢弃。
 */
@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplKatanaMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleAnimate", at = @At("HEAD"))
    private void noellesroles$katanaThrustOnSwing(ServerboundSwingPacket packet, CallbackInfo ci) {
        ServerPlayer player = this.player;
        if (player == null) {
            return;
        }
        // 主手优先，其次副手（不依赖挥手包的手部访问器，规避 API 差异）
        ItemStack main = player.getMainHandItem();
        if (!main.isEmpty() && main.is(ModItems.KATANA)) {
            KatanaCombat.handleThrust(player, main);
            return;
        }
        ItemStack off = player.getOffhandItem();
        if (!off.isEmpty() && off.is(ModItems.KATANA)) {
            KatanaCombat.handleThrust(player, off);
        }
    }
}
