package org.agmas.noellesroles.mixin.modifier;

import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.role.TraitorAndModifiers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 腐化效果Mixin - 让腐化修饰符玩家的尸体立即下沉到完全阶段
 * 通过在实体创建时设置y定位来实现
 */
@Mixin(PlayerBodyEntity.class)
public class CorruptedBodyMixin {

    /**
     * 在实体创建时（构造函数执行后）直接设置位置
     * 这样可以确保腐化效果在第一时间生效
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onCorruptedBodyTick(CallbackInfo ci) {
        PlayerBodyEntity body = (PlayerBodyEntity) (Object) this;
        
        // 只在服务端处理，且只处理一次
        if (body.level().isClientSide) return;
        
        // 检查尸体是否属于腐化修饰符玩家
        if (body.getPlayerUuid() != null) {
            WorldModifierComponent modifiers = WorldModifierComponent.KEY.get(body.level());
            if (modifiers != null && modifiers.isModifier(body.getPlayerUuid(), TraitorAndModifiers.CORRUPTED)) {
                // 检查是否已经处理过（通过检查y位置是否已经是下沉状态）
                // 如果y位置已经很低，说明已经处理过了
                if (body.getY() > -10) {
                    // 将尸体下沉到地下（使其不可见/不可交互）
                    body.setPos(body.getX(), -64.0, body.getZ());
                }
            }
        }
    }
}
