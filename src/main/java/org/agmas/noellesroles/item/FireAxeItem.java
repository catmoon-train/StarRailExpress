package org.agmas.noellesroles.item;

import io.wifi.starrailexpress.block_entity.DoorBlockEntity;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.AdventureUsable;
import io.wifi.starrailexpress.SRE;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.FireAxeStabPayload;

import java.util.List;

/**
 * 消防斧
 * <p>
 * - 3点耐久
 * - Shift+右键：直接撬开门，消耗1点耐久，30秒冷却
 * - 直接右键：像刀一样举起，蓄力2秒，可击杀一名玩家，消耗3点耐久（需满耐久）
 * - 击杀玩家会触发误杀惩罚
 * </p>
 */
public class FireAxeItem extends Item implements AdventureUsable {
    private static final int MAX_DURABILITY = 3;
    private static final int PRY_COOLDOWN = 30 * 20; // 30秒
    private static final int CHARGE_TIME = 2 * 20; // 2秒
    private static final int KILL_COOLDOWN = 60 * 20; // 60秒冷却，防止连续使用

    // 消防斧死因
    public static final ResourceLocation DEATH_REASON_FIRE_AXE = Noellesroles.id("fire_axe");

    public FireAxeItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level world = context.getLevel();
        Player player = context.getPlayer();

        // Shift+右键：撬门
        if (player != null && player.isShiftKeyDown()) {
            BlockEntity entity = world.getBlockEntity(context.getClickedPos());
            if (!(entity instanceof DoorBlockEntity)) {
                entity = world.getBlockEntity(context.getClickedPos().below());
            }

            if (entity instanceof DoorBlockEntity door && !door.isBlasted()) {
                ItemStack stack = context.getItemInHand();

                // 检查耐久（耐久值为3时已损坏，不能使用）
                if (stack.getDamageValue() >= MAX_DURABILITY) {
                    if (!world.isClientSide) {
                        player.displayClientMessage(
                                Component.translatable("item.noellesroles.fire_axe.no_durability")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    return InteractionResult.FAIL;
                }

                // 检查冷却
                if (!player.getCooldowns().isOnCooldown(this)) {
                    if (!player.isCreative()) {
                        player.getCooldowns().addCooldown(this, PRY_COOLDOWN);
                        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
                    }

                    world.playSound(null, context.getClickedPos(), TMMSounds.ITEM_CROWBAR_PRY,
                            SoundSource.BLOCKS, 2.5f, 1f);
                    player.swing(InteractionHand.MAIN_HAND, true);

                    if (!world.isClientSide) {
                        if (SRE.REPLAY_MANAGER != null) {
                            SRE.REPLAY_MANAGER.recordItemUse(player.getUUID(),
                                    BuiltInRegistries.ITEM.getKey(this));
                        }
                        door.blast();
                    }
                    return InteractionResult.SUCCESS;
                } else {
                    if (!world.isClientSide) {
                        player.displayClientMessage(
                                Component.translatable("item.noellesroles.fire_axe.on_cooldown")
                                        .withStyle(ChatFormatting.RED),
                                true);
                    }
                    return InteractionResult.FAIL;
                }
            }
        }

        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 检查是否是Shift+右键（撬门已在useOn处理）
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }

        // 检查耐久是否满
        if (stack.getDamageValue() > 0) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.fire_axe.not_full_durability")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 检查冷却
        if (player.getCooldowns().isOnCooldown(this)) {
            if (!world.isClientSide) {
                player.displayClientMessage(
                        Component.translatable("item.noellesroles.fire_axe.on_cooldown")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
            return InteractionResultHolder.fail(stack);
        }

        // 开始蓄力
        player.startUsingItem(hand);
        player.playSound(TMMSounds.ITEM_KNIFE_PREPARE, 1.0F, 1.0F);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return CHARGE_TIME;
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        // 蓄力过程中检查耐久
        if (stack.getDamageValue() > 0 && !world.isClientSide) {
            user.stopUsingItem();
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level world, LivingEntity user, int remainingUseTicks) {
        if (user.isSpectator()) {
            return;
        }
        if (remainingUseTicks >= this.getUseDuration(stack, user) - 8 || !(user instanceof Player attacker) || !world.isClientSide)
            return;
            
        HitResult collision = getFireAxeTarget(attacker);
        if (collision instanceof EntityHitResult entityHitResult) {
            Entity target = entityHitResult.getEntity();
            if (SRE.REPLAY_MANAGER != null) {
                SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(), BuiltInRegistries.ITEM.getKey(this));
            }
            ClientPlayNetworking.send(new FireAxeStabPayload(target.getId()));
        }
    }

    public static HitResult getFireAxeTarget(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, entity -> entity instanceof Player player && GameUtils.isPlayerAliveAndSurvival(player), 3f);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level world, LivingEntity user) {
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.fire_axe.tooltip.durability",
                MAX_DURABILITY - stack.getDamageValue(), MAX_DURABILITY)
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.noellesroles.fire_axe.tooltip.pry")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.noellesroles.fire_axe.tooltip.kill")
                .withStyle(ChatFormatting.RED));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
