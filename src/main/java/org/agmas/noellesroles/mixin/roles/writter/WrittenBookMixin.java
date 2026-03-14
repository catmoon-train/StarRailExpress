package org.agmas.noellesroles.mixin.roles.writter;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

@Mixin(WrittenBookItem.class)
public class WrittenBookMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void use(Level level, Player player,
            InteractionHand interactionHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        if (level.isClientSide)
            return;
        var pmc = SREPlayerMoodComponent.KEY.get(player);
        var readTask = pmc.tasks.getOrDefault(SREPlayerMoodComponent.Task.RAED_BOOK, null);
        if (readTask != null && readTask instanceof SREPlayerMoodComponent.ReadBookTask bookTask) {
            bookTask.setTimer(0);
        }
    }
}