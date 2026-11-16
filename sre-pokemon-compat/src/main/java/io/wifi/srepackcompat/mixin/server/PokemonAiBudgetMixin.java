package io.wifi.srepackcompat.mixin.server;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.wifi.srepackcompat.cobblemon.PokemonAiBudget;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** 只给宝可梦的 Brain 思考降频，不取消整个实体 tick 或父类 AI 收尾。 */
@Pseudo
@Mixin(targets = "com.cobblemon.mod.common.entity.pokemon.PokemonEntity", remap = false)
public class PokemonAiBudgetMixin {
    // 可选模组使用字符串目标；MC 调用点独立映射，兼顾开发与生产方法名。
    @WrapWithCondition(method = {"customServerAiStep", "method_5958"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/Brain;tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)V", remap = true),
            remap = false, require = 1)
    private boolean sre$thinkThirtyPercent(Brain<?> brain, ServerLevel level, LivingEntity pokemon) {
        return PokemonAiBudget.shouldThink(level.getGameTime(), pokemon.getId());
    }
}
