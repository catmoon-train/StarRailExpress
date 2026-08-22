/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
