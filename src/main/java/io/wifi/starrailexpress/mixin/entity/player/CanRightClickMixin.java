package io.wifi.starrailexpress.mixin.entity.player;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.CantRightClickBlocks;
import dev.upcraft.datasync.api.ext.DataSyncPlayerExt;
import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(Player.class)
public abstract class CanRightClickMixin extends LivingEntity implements DataSyncPlayerExt {
    protected CanRightClickMixin(EntityType<? extends LivingEntity> entityType, Level world) {
        super(entityType, world);
    }

    @Inject(method = "canInteractWithBlock", at = @At("TAIL"), cancellable = true)
    public void canInteractWithBlockAt(BlockPos pos, double additionalRange,
            CallbackInfoReturnable<Boolean> cir) {
        if (SRE.isLobby)
            return;
        if (!cir.getReturnValue())
            return;
        final var player = (Player) (Object) this;
        final var mainHandItem = player.getMainHandItem();
        if (SRE.canDropItem.contains(BuiltInRegistries.ITEM.getKey(mainHandItem.getItem()).toString())
                || SRE.canDrop.stream().anyMatch((p) -> {
                    return p.test(player);
                })) {
            if (player.isShiftKeyDown()) {
                final var drop = player.drop(mainHandItem.copy(), true);
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

                if (drop != null) {
                    drop.setGlowingTag(true);
                    drop.setPickUpDelay(20);
                }
            }
        }
        if (!GameUtils.isPlayerAliveAndSurvival(player)) {
            return;
        }

        BlockState state = level().getBlockState(pos);
        Block block = state.getBlock();

        if (CantRightClickBlocks.shouldPreventInteraction(block)) {
            cir.setReturnValue(false);
        }
    }

}