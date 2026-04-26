package org.agmas.noellesroles.mixin.roles.awesome_binglus;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.ModRoles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class NoteMixin {

    // 注入到 Item.interactLivingEntity，只针对普通 NOTE 生效，排除 GiantNoteItem
    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    public void onInteractLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity,
            InteractionHand interactionHand, CallbackInfoReturnable<InteractionResult> cir) {
        // 排除 GiantNoteItem，让它自己处理
        if (itemStack.getItem() instanceof org.agmas.noellesroles.content.item.GiantNoteItem) {
            return;
        }
        // 只对 TMMItems.NOTE 生效
        if (!itemStack.is(TMMItems.NOTE)) {
            return;
        }
        
        if (player instanceof ServerPlayer serverPlayer) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(serverPlayer.level());
            if (gameWorld.isRole(player, ModRoles.AWESOME_BINGLUS)) {
                final var playerShopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
                if (playerShopComponent.balance >= 50) {
                    if (player != null && !player.isShiftKeyDown()) {
                        SREPlayerNoteComponent component = (SREPlayerNoteComponent) SREPlayerNoteComponent.KEY.get(player);
                        if (!component.written) {
                            player.displayClientMessage(Component.translatable("message.note.write_sth")
                                    .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
                            cir.setReturnValue(InteractionResult.PASS);
                            return;
                        } else {
                            Level world = player.level();
                            if (world.isClientSide) {
                                cir.setReturnValue(InteractionResult.PASS);
                                return;
                            } else {
                                NoteEntity note = (NoteEntity) TMMEntities.NOTE.create(world);
                                playerShopComponent.addToBalance(-50);

                                note.setAttached(ModRoles.ENTITY_NOTE_MAKER, livingEntity.getUUID().toString());
                                note.setYRot(livingEntity.getYHeadRot());
                                note.setPos(livingEntity.getX(), livingEntity.getY() + 1, livingEntity.getZ());

                                note.setDirection(Direction.EAST);
                                note.setLines(component.text);
                                player.displayClientMessage(
                                        Component.translatable("message.note.put_back", livingEntity.getName())
                                                .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)),
                                        true);

                                world.addFreshEntity(note);
                                if (!player.isCreative()) {
                                    if (SRE.REPLAY_MANAGER != null) {
                                        SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                                                BuiltInRegistries.ITEM.getKey(itemStack.getItem()));
                                    }

                                    itemStack.shrink(1);
                                }

                                cir.setReturnValue(InteractionResult.SUCCESS);
                                return;
                            }
                        }
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.note.not_enough_money",50)
                            .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
                    cir.setReturnValue(InteractionResult.PASS);
                    return;
                }
            }
        }
    }
}
