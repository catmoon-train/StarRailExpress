package io.wifi.starrailexpress.mixin.client;

import com.mojang.serialization.Lifecycle;
import net.minecraft.client.gui.screens.worldselection.WorldOpenFlows;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

/** 跳过自定义/实验性世界提示，仅替换客户端确认流程，不修改存档生命周期或其它安全校验。 */
@Mixin(WorldOpenFlows.class)
public abstract class WorldOpenFlowsAdviceMixin {
    @Redirect(
            method = "openWorldCheckWorldStemCompatibility(Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/WorldStem;Lnet/minecraft/server/packs/repository/PackRepository;Ljava/lang/Runnable;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/WorldData;worldGenSettingsLifecycle()Lcom/mojang/serialization/Lifecycle;"))
    private Lifecycle srepackcompat$stableLifecycleForAdvice(WorldData worldData) {
        return Lifecycle.stable();
    }

    @ModifyVariable(
            method = "confirmWorldCreation(Lnet/minecraft/client/Minecraft;Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;Lcom/mojang/serialization/Lifecycle;Ljava/lang/Runnable;Z)V",
            at = @At("HEAD"), argsOnly = true, index = 4)
    private static boolean srepackcompat$bypassCreationAdvice(boolean original) {
        return true;
    }
}
