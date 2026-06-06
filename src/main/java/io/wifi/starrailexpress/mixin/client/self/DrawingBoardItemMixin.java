package io.wifi.starrailexpress.mixin.client.self;

import io.wifi.starrailexpress.client.gui.screen.AdminDrawingBoardScreen;
import io.wifi.starrailexpress.client.gui.screen.DrawingBoardScreen;
import io.wifi.starrailexpress.content.item.AdminDrawingBoardItem;
import io.wifi.starrailexpress.content.item.DrawingBoardItem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(DrawingBoardItem.class)
public class DrawingBoardItemMixin {

    @Inject(method = "use", at = @At("HEAD"))
    private void onUse(Level world, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        if (world.isClientSide) {
            ItemStack stack = player.getItemInHand(hand);
            Minecraft.getInstance().execute(() -> {
                if (stack.getItem() instanceof AdminDrawingBoardItem) {
                    Minecraft.getInstance().setScreen(new AdminDrawingBoardScreen(stack));
                } else {
                    Minecraft.getInstance().setScreen(new DrawingBoardScreen(stack));
                }
            });
        }
    }
}
