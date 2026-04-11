package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 尊名纸条
 *
 * 右键墙壁或地面贴附，生成一个不可破坏的文本实体（ArmorStand with CustomName）。
 * 任何玩家距离纸条实体小于5格且视线无障碍时，按V键进行祷告。
 * 祷告完成后玩家获得"塔罗会成员"标签。
 *
 * 价格：50金币
 */
public class HonoredNoteItem extends Item {

    public HonoredNoteItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull InteractionResult useOn(@NotNull UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        if (!world.isClientSide) {
            ServerLevel serverWorld = (ServerLevel) world;
            SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(serverWorld);

            // 只允许愚者使用
            if (!gameComponent.isRole(player, ModRoles.THE_FOOL)) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.fool.not_fool").withStyle(ChatFormatting.RED),
                        true);
                return InteractionResult.FAIL;
            }

            BlockPos pos = context.getClickedPos();
            BlockHitResult hitResult = new BlockHitResult(
                    context.getClickLocation(), context.getClickedFace(), pos, context.isInside());

            // 在点击位置生成ArmorStand作为纸条实体
            double x = hitResult.getLocation().x;
            double y = hitResult.getLocation().y;
            double z = hitResult.getLocation().z;

            ArmorStand noteEntity = new ArmorStand(EntityType.ARMOR_STAND, serverWorld);
            noteEntity.setPos(x, y, z);
            noteEntity.setInvisible(true);
            noteEntity.setNoGravity(true);
            noteEntity.setCustomName(Component.translatable("entity.noellesroles.honored_note.name")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            noteEntity.setCustomNameVisible(true);
            noteEntity.setInvulnerable(true);
            noteEntity.setSmall(true);
            // 标记为尊名纸条实体
            noteEntity.addTag("fool_honored_note");
            noteEntity.addTag("fool_owner_" + player.getUUID());

            serverWorld.addFreshEntity(noteEntity);

            // 消耗物品
            ItemStack stack = context.getItemInHand();
            stack.shrink(1);

            player.displayClientMessage(
                    Component.translatable("message.noellesroles.fool.note_placed").withStyle(ChatFormatting.GOLD),
                    true);

            return InteractionResult.SUCCESS;
        }

        return InteractionResult.sidedSuccess(world.isClientSide);
    }
}
