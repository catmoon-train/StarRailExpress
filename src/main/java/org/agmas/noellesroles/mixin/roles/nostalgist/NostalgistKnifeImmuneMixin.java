package org.agmas.noellesroles.mixin.roles.nostalgist;

import io.wifi.starrailexpress.network.original.KnifeStabPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.killer.nostalgist.NostalgistPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 怀旧者在里世界中无法被左键（刀）攻击。
 *
 * <p>直接在刀捅处理入口取消针对“活跃里世界怀旧者”的处理，使捅击的音效、挥手、
 * 冷却乃至击杀尝试都不会发生，从而既不暴露隐身怀旧者的位置，也彻底无法对其造成攻击。
 * 击杀本身另有 {@code NostalgistPlayerComponent} 的死亡事件兜底，本拦截负责消除攻击反馈。</p>
 */
@Mixin(KnifeStabPayload.Receiver.class)
public class NostalgistKnifeImmuneMixin {

    @Inject(method = "receive", at = @At("HEAD"), cancellable = true)
    private void noe$nostalgistMeleeImmune(KnifeStabPayload payload, ServerPlayNetworking.Context context,
            CallbackInfo ci) {
        ServerPlayer attacker = context.player();
        if (attacker.serverLevel().getEntity(payload.target()) instanceof ServerPlayer target) {
            NostalgistPlayerComponent comp = NostalgistPlayerComponent.KEY.maybeGet(target).orElse(null);
            if (comp != null && comp.isActiveBackWorld()) {
                ci.cancel();
            }
        }
    }
}
