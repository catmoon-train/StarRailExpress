package org.agmas.noellesroles.mixin.roles.awesome_binglus;

import io.wifi.starrailexpress.cca.GameWorldComponent;
import io.wifi.starrailexpress.cca.PlayerNoteComponent;
import io.wifi.starrailexpress.cca.PlayerShopComponent;
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.item.NoteItem;
import io.wifi.starrailexpress.SRE;
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

@Mixin(NoteItem.class)
public abstract class NoteMixin extends Item {
    public NoteMixin(Properties properties) {
        super(properties);
    }

    // private static HitResult getTarget(Player user) {
    // return ProjectileUtil.getHitResultOnViewVector(user, entity -> entity
    // instanceof Player player && GameFunctions.isPlayerAliveAndSurvival(player),
    // 4f);
    // }

    @Override
    public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity,
            InteractionHand interactionHand) {
        if (player instanceof ServerPlayer serverPlayer) {
            GameWorldComponent gameWorld = GameWorldComponent.KEY.get(serverPlayer.level());
            if (gameWorld.isRole(player, ModRoles.AWESOME_BINGLUS)) {
                final var playerShopComponent = PlayerShopComponent.KEY.get(serverPlayer);
                if (playerShopComponent.balance >= 75) {
                    if (player != null && !player.isShiftKeyDown()) {
                        PlayerNoteComponent component = (PlayerNoteComponent) PlayerNoteComponent.KEY.get(player);
                        if (!component.written) {
                            player.displayClientMessage(Component.translatable("message.note.write_sth")
                                    .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
                            return InteractionResult.PASS;
                        } else {
                            Level world = player.level();
                            if (world.isClientSide) {
                                return InteractionResult.PASS;
                            } else {
                                NoteEntity note = (NoteEntity) TMMEntities.NOTE.create(world);
                                playerShopComponent.addToBalance(-75);

                                note.setAttached(ModRoles.ENTITY_NOTE_MAKER, livingEntity.getUUID().toString());
                                {
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

                                    return InteractionResult.SUCCESS;
                                }
                            }
                        }
                    }
                } else {
                    player.displayClientMessage(Component.translatable("message.note.not_enough_money")
                            .withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
                }
            }
        }
        return super.interactLivingEntity(itemStack, player, livingEntity, interactionHand);
    }
}
