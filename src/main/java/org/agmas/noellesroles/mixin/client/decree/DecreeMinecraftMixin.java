package org.agmas.noellesroles.mixin.client.decree;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import org.agmas.noellesroles.client.decree.DecreeClient;
import org.agmas.noellesroles.game.roles.innocence.decree.Decree;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 与原版直觉、回声轮廓并存，只追加当前观察者有权看到的轮廓。 */
@Mixin(Minecraft.class)
public abstract class DecreeMinecraftMixin {
    @ModifyReturnValue(method = "shouldEntityAppearGlowing", at = @At("RETURN"))
    private boolean decree$glowing(boolean original, @Local(argsOnly = true) Entity entity) {
        return original || DecreeClient.shouldGlow(entity);
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void decree$inventory(Screen screen, CallbackInfo ci) {
        if (!DecreeClient.blocksScreen(screen)) return;
        var player = Minecraft.getInstance().player;
        // 服务端开容器时 menu 已先同步；同时关闭它，避免只隐藏画面却遗留可交互菜单。
        if (player != null) player.closeContainer();
        ci.cancel();
    }

    @WrapOperation(method = "handleKeybinds", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = org.objectweb.asm.Opcodes.PUTFIELD))
    private void decree$numberSelection(Inventory inventory, int slot, Operation<Void> original) {
        if (!DecreeClient.isRestricted(Decree.HOTBAR)) original.call(inventory, slot);
    }
}
