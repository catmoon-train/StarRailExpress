package org.agmas.noellesroles.component;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import io.wifi.starrailexpress.api.RoleComponent;

/**
 * 临时效果玩家组件
 * - 用于存储肾上腺素的体力提升和狗皮膏药的san值保护
 */
public class TemporaryEffectPlayerComponent implements RoleComponent, ServerTickingComponent {
    
    public static final ComponentKey<TemporaryEffectPlayerComponent> KEY = 
        ComponentRegistry.getOrCreate(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "temporary_effect"),
            TemporaryEffectPlayerComponent.class
        );
    
    private final Player player;
    
    /** 肾上腺素体力提升值（ticks） */
    private float staminaBoost = 0f;
    
    /** 狗皮膏药san值保护结束时间（game tick） */
    private int dogskinPlasterProtectionEnd = 0;
    
    public TemporaryEffectPlayerComponent(Player player) {
        this.player = player;
    }
    
    @Override
    public Player getPlayer() {
        return player;
    }
    
    public void sync() {
        KEY.sync(player);
    }
    
    @Override
    public boolean shouldSyncWith(net.minecraft.server.level.ServerPlayer player) {
        return player == this.player;
    }
    
    @Override
    public void init() {
        this.staminaBoost = 0f;
        this.dogskinPlasterProtectionEnd = 0;
        this.sync();
    }
    
    @Override
    public void clear() {
        this.init();
    }
    
    @Override
    public void serverTick() {
        // 检查狗皮膏药保护是否过期
        if (dogskinPlasterProtectionEnd > 0 && player.level().getGameTime() >= dogskinPlasterProtectionEnd) {
            dogskinPlasterProtectionEnd = 0;
            this.sync();
        }

        // 如果受狗皮膏药保护，主动恢复san值以抵消下降，保持san值不会下降到下界
        if (hasDogskinPlasterProtection()) {
            io.wifi.starrailexpress.cca.SREPlayerMoodComponent mood =
                io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY.get(player);
            mood.setMood(mood.getMood() + io.wifi.starrailexpress.game.GameConstants.MOOD_DRAIN);
        }
    }
    
    /**
     * 添加肾上腺素体力提升
     */
    public void addStaminaBoost(float boostInTicks) {
        this.staminaBoost += boostInTicks;
        this.sync();
    }
    
    /**
     * 获取肾上腺素体力提升
     */
    public float getStaminaBoost() {
        return staminaBoost;
    }
    
    /**
     * 设置狗皮膏药保护
     * @param duration 保护持续时间（秒）
     */
    public void setDogskinPlasterProtection(int duration) {
        this.dogskinPlasterProtectionEnd = (int) player.level().getGameTime() + duration * 20;
        this.sync();
    }
    
    /**
     * 检查是否受狗皮膏药保护
     */
    public boolean hasDogskinPlasterProtection() {
        return dogskinPlasterProtectionEnd > 0 && player.level().getGameTime() < dogskinPlasterProtectionEnd;
    }
    
    @Override
    public void writeToNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        tag.putFloat("stamina_boost", staminaBoost);
        tag.putInt("dogskin_plaster_protection", dogskinPlasterProtectionEnd);
    }
    
    @Override
    public void readFromNbt(@NotNull CompoundTag tag, @NotNull HolderLookup.Provider registryLookup) {
        this.staminaBoost = tag.contains("stamina_boost") ? tag.getFloat("stamina_boost") : 0f;
        this.dogskinPlasterProtectionEnd = tag.contains("dogskin_plaster_protection") ? tag.getInt("dogskin_plaster_protection") : 0;
    }
}
