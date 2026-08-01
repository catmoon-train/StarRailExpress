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

package pro.fazeclan.river.stupid_express.mixin.modifier.secretive;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pro.fazeclan.river.stupid_express.modifier.secretive.cca.SecretiveComponent;

/**
 * 玩家死亡时，如果是隐秘词条玩家，则隐藏尸体名字
 */
@Mixin(ServerPlayer.class)
public class SecretiveDeathMixin {

    @Inject(method = "die", at = @At("TAIL"))
    private void onDeath(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        SecretiveComponent secretiveComponent = SecretiveComponent.KEY.get(player);

        // 如果玩家是隐秘词条，则在死后修改尸体名字
        if (secretiveComponent.isSecretive()) {
            ServerLevel serverLevel = player.serverLevel();
            // 延迟执行，等待尸体实体生成
            serverLevel.getServer().execute(() -> {
                // 遍历所有实体找到刚生成的尸体
                serverLevel.getAllEntities().forEach(entity -> {
                    if (entity instanceof io.wifi.starrailexpress.content.entity.PlayerBodyEntity body) {
                        if (body.getPlayerUuid() != null && body.getPlayerUuid().equals(player.getUUID())) {
                            body.setCustomName(Component.literal("???"));
                            body.setCustomNameVisible(true);
                        }
                    }
                });
            });
        }
    }
}
