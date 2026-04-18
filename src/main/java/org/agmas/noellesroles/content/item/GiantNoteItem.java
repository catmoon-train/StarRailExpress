package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

public class GiantNoteItem extends io.wifi.starrailexpress.content.item.NoteItem {
    public GiantNoteItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack itemStack, Player player, LivingEntity livingEntity,
            InteractionHand interactionHand) {
        if (player instanceof Player) {
            var serverPlayer = player;
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(serverPlayer);
            if (playerShopComponent != null && serverPlayer != null && !serverPlayer.isShiftKeyDown()) {
                if (playerShopComponent.balance >= 125) {
                    SREPlayerNoteComponent component = SREPlayerNoteComponent.KEY.get(serverPlayer);
                    if (!component.written) {
                        serverPlayer.displayClientMessage(Component.translatable("message.note.write_sth").withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
                        return InteractionResult.PASS;
                    } else {
                        Level world = serverPlayer.level();
                        if (world.isClientSide) {
                            return InteractionResult.PASS;
                        } else {
                            var note = createNoteEntity(world);
                            if (note == null) return InteractionResult.PASS;

                            playerShopComponent.addToBalance(-125);

                            note.setAttached(ModRoles.ENTITY_NOTE_MAKER, livingEntity.getUUID().toString());
                            note.setYRot(livingEntity.getYHeadRot());
                            note.setPos(livingEntity.getX(), livingEntity.getY() + 1, livingEntity.getZ());
                            note.setDirection(Direction.EAST);
                            note.setLines(component.text);
                                serverPlayer.displayClientMessage(Component.translatable("message.note.put_back", livingEntity.getName()).withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);

                            world.addFreshEntity(note);
                            if (!serverPlayer.isCreative()) {
                                if (SRE.REPLAY_MANAGER != null) {
                                    SRE.REPLAY_MANAGER.recordItemUse(serverPlayer.getUUID(), BuiltInRegistries.ITEM.getKey(itemStack.getItem()));
                                }
                                itemStack.shrink(1);
                            }

                            return InteractionResult.SUCCESS;
                        }
                    }
                } else {
                    serverPlayer.displayClientMessage(Component.translatable("message.note.not_enough_money").withColor(Mth.hsvToRgb(0.0F, 1.0F, 0.6F)), true);
                }
            }
        }
        return super.interactLivingEntity(itemStack, player, livingEntity, interactionHand);
    }

    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        SREPlayerNoteComponent component = SREPlayerNoteComponent.KEY.get(player);
        if (!component.written) {
            player.displayClientMessage(Component.translatable("message.note.write_sth").withColor(Mth.hsvToRgb(0F, 1.0F, 0.6F)), true);
            return InteractionResult.PASS;
        }
        Level world = player.level();
        if (world.isClientSide) return InteractionResult.PASS;
        var note = createNoteEntity(world);

        if (note == null) return InteractionResult.PASS;

        switch (context.getClickedFace()) {
            case DOWN -> {
                return InteractionResult.PASS;
            }
            case UP -> note.setYRot(player.getYHeadRot());
            case NORTH, SOUTH, WEST, EAST -> note.setYRot(180f + (world.random.nextFloat() - .5f) * 30f);
        }

        Direction side = context.getClickedFace();
        note.setDirection(side);
        note.setLines(component.text);
        Vec3 hitPos = context.getClickLocation().add(context.getClickLocation().subtract(player.getEyePosition()).normalize().scale(-.01f)).subtract(0, note.getBbHeight() / 2f, 0);
        note.setPos(hitPos.x(), hitPos.y(), hitPos.z());
        world.addFreshEntity(note);
        if (!player.isCreative()) {
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            player.getItemInHand(context.getHand()).shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected io.wifi.starrailexpress.content.entity.NoteEntity createNoteEntity(Level world) {
        return (io.wifi.starrailexpress.content.entity.NoteEntity) ModEntities.GIANT_NOTE.create(world);
    }
}
