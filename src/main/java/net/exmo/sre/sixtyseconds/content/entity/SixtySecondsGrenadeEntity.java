package net.exmo.sre.sixtyseconds.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.exmo.sre.sixtyseconds.content.item.SixtySecondsGrenadeItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModItems;

/**
 * 60s 投掷武器（燃烧瓶/土制炸弹/闪光弹/燃烧弹/破片手雷）的飞行实体：真正被丢出去，
 * <b>命中方块或实体时才引爆</b>（旧实现是右键即刻在准星落点引爆，没有飞行过程）。
 * <p>
 * 所有变体共用这一个实体类型——爆炸参数与<b>渲染外观</b>都取自实体自带的 {@code ItemStack}
 * （基类用 EntityData 同步该 stack，客户端 {@code ThrownItemRenderer} 据此渲染对应手雷贴图）。
 */
public class SixtySecondsGrenadeEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 反序列化/注册用工厂构造器。 */
    public SixtySecondsGrenadeEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> type,
            Level level) {
        super(type, level);
    }

    /** 兜底外观：未 setItem 时按土制炸弹渲染（正常投掷路径总会 setItem 成实际手雷）。 */
    @Override
    protected Item getDefaultItem() {
        return ModItems.SIXTY_SECONDS_PIPE_BOMB;
    }

    @Override
    protected void onHit(HitResult hitResult) {
        super.onHit(hitResult);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // 爆炸参数取自实体携带的手雷物品（半径/对怪伤害/对玩家健康伤害/点燃/致盲）
        if (this.getItem().getItem() instanceof SixtySecondsGrenadeItem grenade) {
            ServerPlayer thrower = this.getOwner() instanceof ServerPlayer owner ? owner : null;
            grenade.explode(serverLevel, this.position(), thrower);
        }
        this.discard();
    }
}
