// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
package org.agmas.noellesroles.item;

// import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
// import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
// import org.agmas.noellesroles.packet.OpenIntroPayload;
import org.jetbrains.annotations.NotNull;

public class LetterItem extends Item {
    public LetterItem(Properties properties) {
        super(properties);
    }

    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        return InteractionResultHolder.pass(user.getItemInHand(hand));
        // if (!user.level().isClientSide()) {
        // if (user instanceof ServerPlayer sp)
        // ServerPlayNetworking.send(sp, new OpenIntroPayload());
        // }
        // return InteractionResultHolder.success(user.getItemInHand(hand));
    }
}
