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

package org.agmas.noellesroles.content.item.angler;

import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.network.packet.CustomNarratorPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerRules;
import org.agmas.noellesroles.game.roles.innocence.angler.AnglerWorldMemory;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role_data.innocence.AnglerRoleData;
import org.agmas.noellesroles.utils.MoneyUtils;
import io.wifi.starrailexpress.api.data.RoleData;
import org.jetbrains.annotations.NotNull;

public class AnglerOddityItem extends Item {
    public enum Kind {
        BLINKING_KELP, WET_TICKET, EMPTY_WALLET, EMPTY_COFFIN, EMPTY_HOLSTER, JUMPING_HEART,
        UNADDRESSED_LETTER, ABYSS_BAIT, GLOVES, HAIR_REEL, INK, TASK_LIST, DRIPPING_WATCH,
        FALSE_TOOTH, ERROR_AIR, EMPTY_HOOK
    }

    private final Kind kind;

    public AnglerOddityItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
            @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!GameUtils.isGameRunning(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.fail(stack);
        }
        boolean consumed = switch (kind) {
            case WET_TICKET -> AnglerWorldMemory.tryUseTicket(serverPlayer);
            case JUMPING_HEART -> {
                AnglerWorldMemory.startHeart(serverPlayer);
                yield true;
            }
            case UNADDRESSED_LETTER -> {
                broadcastLetter(serverPlayer);
                yield true;
            }
            case ABYSS_BAIT -> {
                AnglerRoleData data = RoleData.getNullable(AnglerRoleData.class, serverPlayer);
                if (data != null) {
                    data.bonusRareNext = true;
                    data.sync();
                }
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.angler.bait")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
                yield true;
            }
            case GLOVES -> {
                serverPlayer.addEffect(ModEffects.of(ModEffects.MOUSE_UPSIDE_DOWN, AnglerRules.GLOVE_TICKS, 0,
                        false, false, true));
                yield true;
            }
            case HAIR_REEL -> {
                serverPlayer.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, AnglerRules.HAIR_STUN_TICKS, 9,
                        false, false, true));
                AnglerWorldMemory.delay(serverLevel, AnglerRules.HAIR_STUN_TICKS, () -> {
                    if (GameUtils.isPlayerAliveAndSurvival(serverPlayer)) {
                        serverPlayer.addEffect(ModEffects.of(MobEffects.MOVEMENT_SPEED, AnglerRules.HAIR_SPEED_TICKS, 1,
                                false, false, true));
                    }
                });
                yield true;
            }
            case INK -> {
                AnglerWorldMemory.addInkPuddle(serverLevel, serverPlayer.position());
                serverPlayer.addEffect(ModEffects.of(MobEffects.DARKNESS, AnglerRules.INK_TICKS, 0, false, true, true));
                yield true;
            }
            case TASK_LIST -> {
                boolean success = serverPlayer.getRandom().nextBoolean();
                if (success) {
                    MoneyUtils.addToBalance(serverPlayer, AnglerRules.TASK_GAIN);
                    serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.angler.task_ok")
                            .withStyle(ChatFormatting.GREEN), true);
                } else {
                    MoneyUtils.addToBalance(serverPlayer, -AnglerRules.TASK_LOSS);
                    serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.angler.task_fail")
                            .withStyle(ChatFormatting.RED), true);
                }
                yield true;
            }
            case DRIPPING_WATCH -> {
                AnglerWorldMemory.startWatch(serverPlayer);
                yield true;
            }
            case FALSE_TOOTH -> {
                AnglerWorldMemory.startFalseTooth(serverPlayer);
                yield true;
            }
            case EMPTY_WALLET, EMPTY_COFFIN, EMPTY_HOLSTER, EMPTY_HOOK -> {
                serverPlayer.displayClientMessage(Component.translatable("message.noellesroles.angler.empty_thing")
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
                yield false;
            }
            default -> false;
        };
        if (consumed && !serverPlayer.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot,
            boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (kind == Kind.ERROR_AIR) {
            player.getInventory().setItem(slot, ItemStack.EMPTY);
            player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.6f, 0.4f);
            player.displayClientMessage(Component.translatable("message.noellesroles.angler.error_air")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }
        if (kind != Kind.BLINKING_KELP || (!selected && player.getOffhandItem() != stack)) {
            return;
        }
        if (!GameUtils.isGameRunning(player)) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(AnglerRules.KELP_RADIUS);
        for (ServerPlayer other : player.serverLevel().getEntitiesOfClass(ServerPlayer.class, box,
                GameUtils::isPlayerAliveAndSurvival)) {
            if (other != player) {
                other.addEffect(ModEffects.of(MobEffects.GLOWING, AnglerRules.KELP_GLOW_TICKS, 0, false, false, true));
            }
        }
        player.addEffect(ModEffects.of(MobEffects.MOVEMENT_SLOWDOWN, AnglerRules.KELP_SLOW_TICKS, 0, false, false, true));
    }

    private static void broadcastLetter(ServerPlayer sender) {
        Component title = Component.literal(AnglerWorldMemory.scramble("没有收件人的信：列车将在——抵达"))
                .withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : sender.server.getPlayerList().getPlayers()) {
            if (!GameUtils.isGameRunning(player)) {
                continue;
            }
            player.connection.send(new ClientboundSetTitleTextPacket(title));
            player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 60, 10));
            ServerPlayNetworking.send(player, new CustomNarratorPacket(title, false));
        }
    }
}
