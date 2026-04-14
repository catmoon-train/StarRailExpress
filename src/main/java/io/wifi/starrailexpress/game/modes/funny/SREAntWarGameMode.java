package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.item.DerringerItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemCooldowns;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.List;
import java.util.Objects;

/**
 * 蚂蚁大战模式
 * <p>
 *     模式特性：所有人被修改体型并获得加速
 *     特殊道具：巡警手枪，时停表
 * </p>
 */
public class SREAntWarGameMode extends WTLooseEndsGameMode {
    public SREAntWarGameMode(ResourceLocation identifier) {
        super(identifier);
    }
    @Override
    protected void initItemList() {
        super.initItemList();
        looseEndsItems.add(ModItems.PATROLLER_REVOLVER::getDefaultInstance);
//        looseEndsItems.add(() -> {
//            ItemStack timeStopClock = new ItemStack(ModItems.TIME_STOP_CLOCK);
//            ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_STOP_TIME, SREConfig.instance().antWarClockStopTick);
//            ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_COOLDOWN, SREConfig.instance().antWarClockCooldownTick);
//            return timeStopClock;
//        });
        looseEndsItems.removeIf(item -> item.get().getItem() instanceof DerringerItem);
    }
    @Override
    protected void initCoolDownItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        super.initCoolDownItems(players, gameWorldComponent);
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(ModItems.PATROLLER_REVOLVER, cooldown);
        }
    }
    @Override
    protected void initRoles(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            gameWorldComponent.addRole(player, ModRoles.SUPER_LOOSE_END);
        }
    }
    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent, List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        AttributeModifier antModifier = new AttributeModifier(
                StupidExpress.id("ant_modifier"), SREConfig.instance().antWarPlayerScale, AttributeModifier.Operation.ADD_VALUE);
        for (ServerPlayer player : players) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.addEffect(
                    new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,  // 速度效果
                    12000,                  // 持续时间（tick）
                    SREConfig.instance().antWarPlayerSpeedLvl,                    // 等级（VI）
                    false,                // 是否显示粒子效果
                    true                  // 是否显示图标
            ));
            Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).removeModifier(antModifier);
            Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).addPermanentModifier(antModifier);
        }
    }
}
