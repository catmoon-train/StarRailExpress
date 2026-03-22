package org.agmas.noellesroles.mixin.roles.writter;

import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WrittenBookItem.class)
public class WrittenBookMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    public void use(Level level, Player player,
            InteractionHand interactionHand,
            CallbackInfoReturnable<InteractionResultHolder<ItemStack>> ci) {
        if (level.isClientSide)
            return;
        var pmc = SREPlayerTaskComponent.KEY.get(player);
        var readTask = pmc.tasks.getOrDefault(SREPlayerTaskComponent.Task.RAED_BOOK, null);
        if (readTask != null && readTask instanceof SREPlayerTaskComponent.ReadBookTask bookTask) {
            bookTask.setTimer(0);
        }
    }
}