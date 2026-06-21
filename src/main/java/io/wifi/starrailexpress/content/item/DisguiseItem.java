package io.wifi.starrailexpress.content.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 伪装道具。
 * <p>
 * 右键使用后<b>消耗 1 个</b>，并立即获得 {@link ModEffects#DISGUISE} 效果，持续 30 秒。
 * 效果的<b>等级（amplifier）</b>由本物品的变体下标 {@link #variantIndex} 决定，
 * 客户端据此在 {@link DisguiseVariants} 中查出对应皮肤并替换玩家显示，
 * 从而实现「不同伪装道具 / 不同药水效果等级 → 不同皮肤」。
 * <p>
 * 注意：原版只会把玩家自身的 MobEffect 同步给他自己，其它玩家的效果不会下发。
 * 因此为了让<b>其他玩家也能看到</b>伪装皮肤，需要由 {@link DisguiseEffectSync}
 * 在服务端把本效果广播给所有其它客户端。
 */
public class DisguiseItem extends Item {

    /** 伪装持续时间（tick），30 秒 = 600 tick。 */
    public static final int DISGUISE_DURATION_TICKS = 30 * 20;

    /** 该道具对应的伪装变体下标，即应用到效果上的等级（amplifier）。 */
    private final int variantIndex;

    public DisguiseItem(Properties properties, int variantIndex) {
        super(properties);
        this.variantIndex = variantIndex;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand interactionHand) {
        ItemStack itemStack = player.getItemInHand(interactionHand);
        if (!level.isClientSide) {
            player.addEffect(new MobEffectInstance(
                    ModEffects.DISGUISE,
                    DISGUISE_DURATION_TICKS,
                    variantIndex, // 等级 = 变体下标，决定使用哪套皮肤
                    false, // ambient
                    false, // showParticles
                    true // showIcon
            ));
            // 记录伪装事件（低频关键事件）
            io.wifi.starrailexpress.SRE.REPLAY_MANAGER.recordDisguise(player.getUUID());
            // 消耗物品（创造模式下不消耗）
            itemStack.consume(1, player);
        }
        player.swing(interactionHand, true);
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }
}
