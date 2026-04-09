package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemCooldowns;
import org.agmas.noellesroles.init.ModItems;
import pro.fazeclan.river.stupid_express.StupidExpress;

import java.util.List;
import java.util.Objects;

public class SREAntWarGameMode extends WTLooseEndsGameMode {
    private static final AttributeModifier antModifier = new AttributeModifier(
            StupidExpress.id("ant_modifier"), -0.7, AttributeModifier.Operation.ADD_VALUE);

    public SREAntWarGameMode(ResourceLocation identifier) {
        super(identifier);
    }
    @Override
    protected void initItemList() {
        super.initItemList();
        looseEndsItems.add(ModItems.PATROLLER_REVOLVER::getDefaultInstance);
    }
    @Override
    protected void initCoolDownItems(List<ServerPlayer> players) {
        super.initCoolDownItems(players);
        int cooldown = GameConstants.getInTicks(0, 10);
        for (ServerPlayer player : players) {
            // 给所有人的武器添加冷却
            ItemCooldowns itemCooldownManager = player.getCooldowns();
            itemCooldownManager.addCooldown(ModItems.PATROLLER_REVOLVER, cooldown);
        }
    }
    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
                               List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        for (ServerPlayer player : players) {
            player.removeEffect(MobEffects.MOVEMENT_SPEED);
            player.addEffect(
                    new MobEffectInstance(
                    MobEffects.MOVEMENT_SPEED,  // 速度效果
                    12000,                  // 持续时间（tick）
                    5,                    // 等级（VI）
                    false,                // 是否显示粒子效果
                    true                  // 是否显示图标
            ));
            Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).removeModifier(antModifier);
            Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).addPermanentModifier(antModifier);
        }
    }
}
