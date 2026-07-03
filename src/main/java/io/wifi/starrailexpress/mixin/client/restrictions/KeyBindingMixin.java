package io.wifi.starrailexpress.mixin.client.restrictions;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.wifi.starrailexpress.api.RoleMethodDispatcher;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.rules.DropRules;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(KeyMapping.class)
public abstract class KeyBindingMixin {
    @Shadow
    public abstract boolean same(KeyMapping other);

    @Unique
    private boolean shouldSuppressKey() {
        final var instance = Minecraft.getInstance();
        if (instance == null)
            return false;
        if (instance.options == null)
            return false;
        final var player = instance.player;
        if (player == null)
            return false;
        final var options = instance.options;
        if (SREClient.isInLobby) {
            return false;
        }
        if (!SREClient.isPlayerCreative() && this.same(options.keyDrop)) {
            InteractionResult result = RoleMethodDispatcher.callOnDropItem(player, player.getMainHandItem());
            if (result == InteractionResult.CONSUME || result == InteractionResult.FAIL
                    || result == InteractionResult.CONSUME_PARTIAL) {
                return true;
            } else if (result == InteractionResult.SUCCESS || result == InteractionResult.SUCCESS_NO_ITEM_USED) {
                return false;
            }
            if (DropRules.canDropItem
                    .contains(BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem()).toString())
                    || DropRules.canDrop.stream().anyMatch((p) -> {
                        return p.test(player);
                    })) {
                if (instance.screen == null) {
                    return false;
                }
            }
            return true;
        }
        if (SREClient.gameComponent != null && SREClient.gameComponent.isRunning()
                && SREClient.isPlayerAliveAndInSurvival()) {
            if (this.same(options.keyJump)) {
                if (SREClient.gameComponent.isJumpAvailable())
                    return false;
                return true;
            }
            return this.same(options.keySwapOffhand) || this.same(options.keyAdvancements);
        }
        return false;
    }

    @ModifyReturnValue(method = "consumeClick", at = @At("RETURN"))
    private boolean tmm$restrainWasPressedKeys(boolean original) {
        return !this.shouldSuppressKey() && original;
    }

    @ModifyReturnValue(method = "isDown", at = @At("RETURN"))
    private boolean tmm$restrainIsPressedKeys(boolean original) {
        return !this.shouldSuppressKey() && original;
    }

    @ModifyReturnValue(method = "matches", at = @At("RETURN"))
    private boolean tmm$restrainMatchesKey(boolean original) {
        return !this.shouldSuppressKey() && original;
    }
}
