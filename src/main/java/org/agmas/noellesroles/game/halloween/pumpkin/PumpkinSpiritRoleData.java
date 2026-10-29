package org.agmas.noellesroles.game.halloween.pumpkin;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.disguise.EntityDisguiseState;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** 本人同步资源 HUD；真实账本留在单局 Handler，避免重连和回溯套利。 */
public final class PumpkinSpiritRoleData extends SimpleRoleData {
    private static final ResourceLocation SCALE = ResourceLocation.fromNamespaceAndPath("noellesroles", "pumpkin_spirit_scale");
    public int collected;
    public int target = 4;
    public boolean suppressed;
    private EntityDisguiseState batAppearance;
    private EntityDisguiseState previousAppearance;
    private long previousExpires;
    private long batUntil;
    private boolean oldMayfly;
    private boolean oldFlying;

    public PumpkinSpiritRoleData(RoleDataContext context) { super(context); }

    @Override public void serverTick() {
        if (!(player instanceof ServerPlayer sp)) return;
        PumpkinSpiritHandler.tickRole(sp, this);
    }

    void applyScale(ServerPlayer sp) {
        var attribute = sp.getAttribute(Attributes.SCALE);
        if (attribute != null && !attribute.hasModifier(SCALE)) {
            attribute.addTransientModifier(new AttributeModifier(SCALE, -0.8, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    boolean startBat(ServerPlayer sp) {
        // 不覆盖带有不可恢复 predicate 的其他实体伪装；失败不消耗技能冷却。
        if (batAppearance != null || EntityDisguise.hasEndPredicate(sp)) return false;
        previousAppearance = EntityDisguise.get(sp);
        int remaining = EntityDisguise.getRemainingTicks(sp);
        previousExpires = remaining < 0 ? 0 : PumpkinSpiritHandler.now(sp.serverLevel()) + remaining;
        oldMayfly = sp.getAbilities().mayfly;
        oldFlying = sp.getAbilities().flying;
        if (!EntityDisguise.disguise(sp, EntityType.BAT)) return false;
        batAppearance = EntityDisguise.get(sp);
        batUntil = PumpkinSpiritHandler.now(sp.serverLevel()) + 300;
        sp.getAbilities().mayfly = true;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        return true;
    }

    void tickBat(ServerPlayer sp) {
        if (batAppearance != null && (!PumpkinSpiritHandler.alive(sp)
                || PumpkinSpiritHandler.now(sp.serverLevel()) >= batUntil
                || EntityDisguise.get(sp) != batAppearance)) stopBat(sp);
    }

    void stopBat(ServerPlayer sp) {
        if (batAppearance == null) return;
        // 比较实例所有权：别人中途换了形态，不抹掉新形态（即使也是蝙蝠）。
        if (EntityDisguise.get(sp) == batAppearance) {
            if (previousAppearance != null && !previousAppearance.isNone()
                    && (previousExpires == 0 || previousExpires > PumpkinSpiritHandler.now(sp.serverLevel()))) {
                io.wifi.starrailexpress.disguise.EntityDisguiseManager.set(sp, previousAppearance, previousExpires, null);
            } else EntityDisguise.clear(sp);
        }
        previousAppearance = null;
        batAppearance = null;
        // 旁观/创造已由游戏模式接管能力，不能把它们的飞行关掉。
        if (!sp.isSpectator() && !sp.isCreative()) {
            sp.getAbilities().mayfly = oldMayfly;
            sp.getAbilities().flying = oldFlying;
            sp.onUpdateAbilities();
        }
    }

    void restore(ServerPlayer sp) {
        stopBat(sp);
        var attribute = sp.getAttribute(Attributes.SCALE);
        if (attribute != null) attribute.removeModifier(SCALE);
    }

    @Override public void clear() {
        if (player instanceof ServerPlayer sp) {
            restore(sp);
            PumpkinSpiritHandler.roleRemoved(sp);
        }
    }

    void update(PumpkinSpiritState state) {
        if (collected == state.collected() && target == state.target && suppressed == state.suppressed()) return;
        collected = state.collected();
        target = state.target;
        suppressed = state.suppressed();
        sync();
    }

    @Override public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putInt("collected", collected);
        tag.putInt("target", target);
        tag.putBoolean("suppressed", suppressed);
    }

    @Override public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        collected = tag.getInt("collected");
        target = tag.getInt("target");
        suppressed = tag.getBoolean("suppressed");
    }

    @Override public void readFromRewindNbt(CompoundTag tag, HolderLookup.Provider lookup) {
        // 资源账本和租约只由当前局的服务端推进，时间回溯不得重发十南瓜奖励。
    }
}
