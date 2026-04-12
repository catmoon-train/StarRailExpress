package org.agmas.noellesroles.roles.fool;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerNoteComponent;
import io.wifi.starrailexpress.entity.NoteEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.item.NoteItem;
import io.wifi.starrailexpress.util.AdventureUsable;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.noellesroles.role.ModRoles;
import org.jetbrains.annotations.NotNull;

/**
 * 尊名纸条
 *
 * 右键墙壁或地面贴附，生成一个不可破坏的文本实体（NoteEntity）。
 * 任何玩家距离纸条实体小于5格且视线无障碍时，按V键进行祷告。
 * 祷告完成后玩家获得"塔罗会成员"标签。
 *
 * 价格：50金币
 */
public class HonoredNoteItem extends NoteItem implements AdventureUsable {

    public HonoredNoteItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()) return InteractionResult.PASS;
        
        Level world = context.getLevel();
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

            // 设置固定的尊名内容到玩家的笔记组件
            SREPlayerNoteComponent component = SREPlayerNoteComponent.KEY.get(player);
            component.setNote(
                    "§l§6按下J进行祷告",
                    "§6不属于这个时代的愚者",
                    "§6灰雾之上的神秘主宰",
                    "§6执掌好运的黄黑之王"
            );

        }
        
        // 调用父类的 useOn 逻辑来放置纸条
        return super.useOn(context);
    }

    @Override
    protected NoteEntity createNoteEntity(Level world) {
        HonoredNoteEntity honoredNoteEntity = new HonoredNoteEntity(world);
        honoredNoteEntity.setGlowingTag(true);
        return honoredNoteEntity;
    }

    /**
     * 自定义的尊名纸条实体，具有特殊属性
     */
    public static class HonoredNoteEntity extends NoteEntity {
        public HonoredNoteEntity(Level world) {
            super(io.wifi.starrailexpress.index.TMMEntities.NOTE, world);
        }

        @Override
        public boolean isPushable() {
            return false;
        }

        @Override
        public boolean isInvulnerable() {
            return true;
        }

        @Override
        public boolean fireImmune() {
            return true;
        }
    }
}
