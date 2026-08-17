/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.game.roles.innocence.fool;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.content.entity.NoteEntity;
import io.wifi.starrailexpress.content.item.NoteItem;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

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
public class HonoredNoteItem extends NoteItem {

    public HonoredNoteItem(Properties settings) {
        super(settings);
    }

    static final Component[] HONORED_NOTE_MESSAGES = new Component[] {
            Component
                    .translatable("item.noellesroles.honored_note.text1",
                            Component.keybind("key.noellesroles.fool_prayer").withStyle(ChatFormatting.RED,
                                    ChatFormatting.BOLD))
                    .withStyle(ChatFormatting.GOLD,
                            ChatFormatting.BOLD),
            Component.translatable("item.noellesroles.honored_note.text2").withStyle(ChatFormatting.GOLD),
            Component.translatable("item.noellesroles.honored_note.text3").withStyle(ChatFormatting.GOLD),
            Component.translatable("item.noellesroles.honored_note.text4").withStyle(ChatFormatting.GOLD)
    };

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, Player user, InteractionHand hand) {
        ItemStack itemStack = user.getItemInHand(hand);
        return InteractionResultHolder.pass(itemStack);
    }

    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown())
            return InteractionResult.PASS;

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
        }

        // 调用父类的 useOn 逻辑来放置纸条
        return createNote(context, HONORED_NOTE_MESSAGES);
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
