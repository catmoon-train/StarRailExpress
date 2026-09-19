package org.agmas.noellesroles.game.roles.killer.nature_spirit;

import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.game.DiscountShopEntry;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 自然精灵：杀手阵营。技能键伪装成脚下的方块并对齐到该方格。
 * MorphApi 只能换玩家皮肤，方块外形走 {@link EntityDisguise}（falling_block）。
 */
public class NatureSpiritRole extends io.wifi.starrailexpress.api.NormalRole {

    public static final int SKILL_COOLDOWN_SECONDS = 60;
    public static final int BAMBOO_SPEAR_PRICE = 130;
    public static final int BAMBOO_PRICE = 50;
    public static final int AREA_BLACKOUT_PRICE = 120;
    public static final int LOCKPICK_PRICE = 60;

    public NatureSpiritRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new DiscountShopEntry(ModItems.BAMBOO_SPEAR.getDefaultInstance(), BAMBOO_SPEAR_PRICE));
        shop.add(new ShopEntry(ModItems.BAMBOO.getDefaultInstance(), BAMBOO_PRICE, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.AREA_BLACKOUT.getDefaultInstance(), AREA_BLACKOUT_PRICE, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                if (player.getCooldowns().isOnCooldown(ModItems.AREA_BLACKOUT)) {
                    return false;
                }
                SREWorldBlackoutComponent blackout = SREWorldBlackoutComponent.KEY.get(player.level());
                if (blackout.isBlackoutActive()) {
                    return false;
                }
                blackout.triggerBlackout(player.blockPosition(),
                        NoellesRolesConfig.HANDLER.instance().dreamBlackoutRadius, true,
                        SREWorldBlackoutComponent.getMaxDuration(player.level()));
                player.getCooldowns().addCooldown(ModItems.AREA_BLACKOUT,
                        Math.max(60 * 20, GameConstants.getBlackoutCooldownGlobal()));
                return true;
            }
        });
        shop.add(new ShopEntry(TMMItems.LOCKPICK.getDefaultInstance(), LOCKPICK_PRICE, ShopEntry.Type.TOOL));
        return shop;
    }

    public static boolean triggerSkill(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        if (EntityDisguise.isDisguised(player)) {
            EntityDisguise.clear(player);
            player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.end")
                    .withStyle(ChatFormatting.GREEN), true);
            return false;
        }
        return disguiseAsFloorBlock(player);
    }

    private static boolean disguiseAsFloorBlock(ServerPlayer player) {
        BlockPos floor = player.getOnPos();
        BlockState state = player.level().getBlockState(floor);
        if (state.isAir() || state.getRenderShape() == RenderShape.INVISIBLE) {
            player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.fail")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        CompoundTag nbt = new CompoundTag();
        nbt.put("BlockState", NbtUtils.writeBlockState(state));
        if (!EntityDisguise.disguise(player, EntityType.FALLING_BLOCK, nbt, 0)) {
            player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.fail")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

        VoxelShape shape = state.getCollisionShape(player.level(), floor);
        double top = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
        if (Double.isNaN(top) || top <= 0.0) {
            top = 1.0;
        }
        double x = floor.getX() + 0.5;
        double y = floor.getY() + top;
        double z = floor.getZ() + 0.5;
        player.connection.teleport(x, y, z, player.getYRot(), player.getXRot());
        player.setDeltaMovement(Vec3.ZERO);
        player.hasImpulse = true;

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    x, y + 0.4, z, 16, 0.25, 0.25, 0.25, 0.05);
            serverLevel.playSound(null, x, y, z, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.PLAYERS, 0.9f, 1.15f);
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.nature_spirit.camouflage.start")
                .withStyle(ChatFormatting.GREEN), true);
        return true;
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            boolean forceDeath) {
        if (victim instanceof ServerPlayer serverPlayer) {
            EntityDisguise.clear(serverPlayer);
        }
        super.onDeath(victim, spawnBody, killer, deathReason, forceDeath);
    }
}
