package cn.zbx1425.minopp.mixin;

import cn.zbx1425.minopp.Mino;
import cn.zbx1425.minopp.block.BlockEntityMinoTable;
import cn.zbx1425.minopp.game.CardPlayer;
import cn.zbx1425.minopp.gui.TurnDeadMan;
import cn.zbx1425.minopp.item.ItemHandCards;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Inventory.class)
public class InventoryMixin {

    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    void swapPaint(double direction, CallbackInfo ci) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        ItemStack holding = player.getMainHandItem();
        if (holding.is(Mino.ITEM_HAND_CARDS.get())) {
            BlockPos handCardGamePos = ItemHandCards.getHandCardGamePos(player);
            if (handCardGamePos != null) {
                if (player.level().getBlockEntity(handCardGamePos) instanceof BlockEntityMinoTable tableEntity) {
                    if (tableEntity.game == null) return;

                    CardPlayer playerWithoutHand = ItemHandCards.getCardPlayer(player);
                    CardPlayer realPlayer = tableEntity.game.players.stream()
                            .filter(p -> p.equals(playerWithoutHand)).findFirst().orElse(null);
                    if (realPlayer == null) return;

                    int handIndex = holding.getOrDefault(Mino.DATA_COMPONENT_TYPE_CLIENT_HAND_INDEX.get(), 0);
                    holding.set(Mino.DATA_COMPONENT_TYPE_CLIENT_HAND_INDEX.get(),
                            Mth.clamp(handIndex - (int)Math.signum(direction), 0, realPlayer.hand.size() - 1));

                    TurnDeadMan.pedal();
                    ci.cancel();
                }
            }
        }
    }
}
