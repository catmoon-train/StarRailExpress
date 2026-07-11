package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.RainbowHorseEntity;
import org.agmas.noellesroles.init.ModEntities;

import java.util.List;

/**
 * 彩虹马蹄铁 - 召唤海曼彩虹马从天而降。
 */
public class RainbowHorseshoeItem extends Item {

    /** 最高从落点上方多少格开始下落（室内会自动降低到天花板以下） */
    private static final int SPAWN_HEIGHT = 25;
    /** 召唤冷却：30 秒 */
    private static final int COOLDOWN_TICKS = 30 * 20;
    /** 落点选取的最远准星距离 */
    private static final double TARGET_RANGE = 16.0;

    public RainbowHorseshoeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        // 落点：准星指向的方块表面，否则玩家前方 2 格
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(TARGET_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        BlockPos base;
        if (hit.getType() == HitResult.Type.MISS) {
            base = BlockPos.containing(player.position().add(look.x * 2, 0, look.z * 2));
        } else {
            base = hit.getBlockPos().relative(hit.getDirection());
        }

        // 向上探测空间，室内（如列车内）自动降低起落高度，避免卡进天花板
        int clearance = 0;
        while (clearance < SPAWN_HEIGHT) {
            BlockPos probe = base.above(clearance + 1);
            if (!level.getBlockState(probe).getCollisionShape(level, probe).isEmpty()) {
                break;
            }
            clearance++;
        }
        // 马高约 1.6 格，预留 2 格身位
        int dropHeight = Math.max(0, clearance - 2);

        RainbowHorseEntity horse = new RainbowHorseEntity(ModEntities.RAINBOW_HORSE, level);
        horse.setPos(base.getX() + 0.5, base.getY() + dropHeight, base.getZ() + 0.5);
        horse.setYRot(player.getYRot());
        level.addFreshEntity(horse);

        // 召唤音效 + 天降起点闪光
        level.playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);
        ((ServerLevel) level).sendParticles(ParticleTypes.FLASH,
                base.getX() + 0.5, base.getY() + dropHeight + 1.0, base.getZ() + 0.5, 1, 0, 0, 0, 0);

        // 冒险等非创造模式下为一次性物品，使用后消耗
        if (!player.isCreative()) {
            stack.shrink(1);
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.noellesroles.rainbow_horseshoe.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
