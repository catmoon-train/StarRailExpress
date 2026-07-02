package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RolePassive;
import io.wifi.starrailexpress.api.RoleSkill;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModEffects;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通用技能组件
 *
 * 用于管理玩家的技能冷却时间和使用次数
 * 该组件会自动在客户端和服务端之间同步
 *
 * 功能：
 * - 冷却时间管理（自动递减）
 * - 技能使用次数限制
 * - 自动同步到客户端（用于 HUD 显示）
 */
public class SREAbilityPlayerComponent
        implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    @Override
    public Player getPlayer() {
        return player;
    }

    /** 组件键 - 用于从玩家获取此组件 */
    public static final ComponentKey<SREAbilityPlayerComponent> KEY = ModComponents.ABILITY;

    // 持有该组件的玩家
    private final Player player;

    // 技能冷却时间（tick）
    public int cooldown = 100;

    // 技能剩余使用次数（-1 表示无限制）
    public int charges = -1;

    // 最大使用次数（用于 HUD 显示）
    public int maxCharges = -1;

    // 状态
    public int status = -1;

    private final Map<ResourceLocation, SkillState> skillStates = new HashMap<>();
    private int selectedSkill;
    private ResourceLocation castingSkill;
    private long lastHoldTick = Long.MIN_VALUE;

    public static final class SkillState {
        public int cooldown;
        public int charges = -1;
        public int maxCharges = -1;
        public int castCount;
    }

    /**
     * 构造函数
     */
    public SREAbilityPlayerComponent(Player player) {
        this.player = player;
    }

    /**
     * 重置组件状态
     * 在游戏开始时或角色分配时调用
     */
    @Override
    public void init() {
        init(true);
    }

    public void init(boolean sync) {
        this.cooldown = 0;
        this.charges = -1;
        this.maxCharges = -1;
        this.status = -1;
        this.skillStates.clear();
        this.selectedSkill = 0;
        this.castingSkill = null;
        this.lastHoldTick = Long.MIN_VALUE;
        if (sync)
            this.sync();
    }

    @Override
    public void clear() {
        this.init();
    }

    /**
     * 设置冷却时间
     * 
     * @param ticks 冷却时间（tick），20 tick = 1 秒
     */
    public void setCooldown(int ticks) {
        this.cooldown = ticks;
        this.sync();
    }

    /**
     * 设置技能使用次数
     * 
     * @param charges 使用次数
     */
    public void setCharges(int charges) {
        this.charges = charges;
        this.maxCharges = charges;
        this.sync();
    }

    /**
     * 使用一次技能
     * 
     * @return 是否成功使用
     */
    public boolean useAbility() {
        if (cooldown > 0) {
            return false;
        }
        if (charges == 0) {
            return false;
        }
        if (charges > 0) {
            charges--;
        }
        sync();
        return true;
    }

    /**
     * 检查技能是否可用
     */
    public boolean canUseAbility() {
        return cooldown <= 0 && (charges == -1 || charges > 0) && !this.player.hasEffect(ModEffects.SAFE_TIME);
    }

    public int getCooldown() {
        return this.cooldown;
    }

    public boolean hasCooldown() {
        return this.cooldown > 0;
    }

    public int getSelectedSkill() {
        return selectedSkill;
    }

    public ResourceLocation getCastingSkill() {
        return castingSkill;
    }

    public SkillState getSkillState(ResourceLocation skillId) {
        return skillStates.computeIfAbsent(skillId, ignored -> new SkillState());
    }

    public void ensureSkills(List<RoleSkill.Definition> definitions) {
        for (RoleSkill.Definition definition : definitions) {
            SkillState state = getSkillState(definition.id());
            if (state.maxCharges == -1 && definition.maxCharges() > 0) {
                state.maxCharges = definition.maxCharges();
                state.charges = definition.maxCharges();
            }
        }
        if (!definitions.isEmpty()) {
            selectedSkill = Math.floorMod(selectedSkill, definitions.size());
            mirrorSelectedSkill(definitions);
        }
    }

    public void selectSkill(int slot, List<RoleSkill.Definition> definitions) {
        if (definitions.isEmpty()) {
            selectedSkill = 0;
            return;
        }
        selectedSkill = Math.floorMod(slot, definitions.size());
        ensureSkills(definitions);
        sync();
    }

    public boolean canUseSkill(ResourceLocation skillId) {
        SkillState state = getSkillState(skillId);
        return state.cooldown <= 0
                && (state.charges == -1 || state.charges > 0)
                && !player.hasEffect(ModEffects.SAFE_TIME);
    }

    public void showUnavailableMessage(ResourceLocation skillId) {
        SkillState state = getSkillState(skillId);
        if (state.cooldown > 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.sre.skill.cooldown", String.format("%.1f", state.cooldown / 20.0F)), true);
        } else if (state.charges == 0) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.sre.skill.no_charges"), true);
        }
    }

    public void markSkillUsed(RoleSkill.Definition definition) {
        SkillState state = getSkillState(definition.id());
        state.cooldown = definition.cooldownTicks();
        if (state.maxCharges == -1 && definition.maxCharges() > 0) {
            state.maxCharges = definition.maxCharges();
            state.charges = definition.maxCharges();
        }
        if (state.charges > 0) {
            state.charges--;
        }
        state.castCount++;
        castingSkill = definition.continuous() ? definition.id() : null;
        cooldown = state.cooldown;
        charges = state.charges;
        maxCharges = state.maxCharges;
        sync();
    }

    public int getCastCount(ResourceLocation skillId) {
        return getSkillState(skillId).castCount;
    }

    public void setSkillCooldown(ResourceLocation skillId, int ticks) {
        getSkillState(skillId).cooldown = Math.max(0, ticks);
        sync();
    }

    public boolean shouldRunHold(ResourceLocation skillId, int intervalTicks) {
        if (!skillId.equals(castingSkill)) {
            return false;
        }
        long now = player.level().getGameTime();
        if (lastHoldTick != Long.MIN_VALUE && now - lastHoldTick < intervalTicks) {
            return false;
        }
        lastHoldTick = now;
        return true;
    }

    public void stopCasting(ResourceLocation skillId) {
        if (skillId.equals(castingSkill)) {
            castingSkill = null;
            lastHoldTick = Long.MIN_VALUE;
            sync();
        }
    }

    private void mirrorSelectedSkill(List<RoleSkill.Definition> definitions) {
        if (definitions.isEmpty()) {
            return;
        }
        SkillState state = getSkillState(definitions.get(selectedSkill).id());
        cooldown = state.cooldown;
        charges = state.charges;
        maxCharges = state.maxCharges;
    }

    /**
     * 重置所有技能冷却（包括统一冷却和独立技能状态冷却）
     * 用于外部（如疫使时刻触发时）立即刷新冷却
     */
    public void resetAllCooldowns() {
        this.cooldown = 0;
        for (SkillState state : skillStates.values()) {
            state.cooldown = 0;
        }
        this.sync();
    }

    /**
     * 获取冷却时间（秒）
     */
    public float getCooldownSeconds() {
        return cooldown / 20.0f;
    }

    /**
     * 同步到客户端
     */
    public void sync() {
        ModComponents.ABILITY.sync(this.player);
    }

    // ==================== Tick 处理 ====================

    @Override
    public void serverTick() {
        boolean unifiedStateChanged = false;
        if (!skillStates.isEmpty()) {
            for (SkillState state : skillStates.values()) {
                if (state.cooldown > 0) {
                    state.cooldown--;
                    unifiedStateChanged = true;
                }
            }
            var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
            List<RoleSkill.Definition> definitions = RoleSkill.getDefinitions(role);
            mirrorSelectedSkill(definitions);
            if (unifiedStateChanged && (player.level().getGameTime() % 20 == 0 || cooldown == 0)) {
                sync();
            }
        } else if (this.cooldown > 0) {
            this.cooldown--;
            if (this.cooldown % 400 == 0 || this.cooldown == 0) {
                this.sync();
            }
        }
        if (player instanceof ServerPlayer serverPlayer) {
            var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
            RolePassive.tick(serverPlayer, role);
        }
        var gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent.isRunning()) {
            var role = gameWorldComponent.getRole(player);
            if (role != null) {
                role.clientGameTickEvent(player, gameWorldComponent);
            }
        }
    }

    @Override
    public void clientTick() {
        if (this.cooldown > 1)
            this.cooldown--;
        for (SkillState state : skillStates.values()) {
            if (state.cooldown > 1) {
                state.cooldown--;
            }
        }
        var role = SREGameWorldComponent.KEY.get(player.level()).getRole(player);
        List<RoleSkill.Definition> definitions = RoleSkill.getDefinitions(role);
        if (!definitions.isEmpty()) {
            mirrorSelectedSkill(definitions);
        }

        if (SREGameWorldComponent.KEY.get(this.player.level()).isRunning()) {
            io.wifi.starrailexpress.api.RoleMethodDispatcher.callClientTick(this.player);
            var modifiers = WorldModifierComponent.KEY.get(this.player.level()).getModifiers(this.player);
            for (var mo : modifiers) {
                mo.clientGameTickEvent(player);
            }
        }
    }

    // ==================== NBT 序列化 ====================

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("cooldown", this.cooldown);
        tag.putInt("charges", this.charges);
        tag.putInt("maxCharges", this.maxCharges);
        tag.putInt("status", this.status);
        tag.putInt("selectedSkill", this.selectedSkill);
        if (this.castingSkill != null) {
            tag.putString("castingSkill", this.castingSkill.toString());
        }
        CompoundTag statesTag = new CompoundTag();
        for (Map.Entry<ResourceLocation, SkillState> entry : skillStates.entrySet()) {
            CompoundTag stateTag = new CompoundTag();
            stateTag.putInt("cooldown", entry.getValue().cooldown);
            stateTag.putInt("charges", entry.getValue().charges);
            stateTag.putInt("maxCharges", entry.getValue().maxCharges);
            stateTag.putInt("castCount", entry.getValue().castCount);
            statesTag.put(entry.getKey().toString(), stateTag);
        }
        tag.put("skillStates", statesTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.cooldown = tag.contains("cooldown") ? tag.getInt("cooldown") : 0;
        this.charges = tag.contains("charges") ? tag.getInt("charges") : -1;
        this.maxCharges = tag.contains("maxCharges") ? tag.getInt("maxCharges") : -1;
        this.status = tag.contains("status") ? tag.getInt("status") : -1;
        this.selectedSkill = tag.contains("selectedSkill") ? tag.getInt("selectedSkill") : 0;
        this.castingSkill = tag.contains("castingSkill")
                ? ResourceLocation.tryParse(tag.getString("castingSkill"))
                : null;
        this.skillStates.clear();
        if (tag.contains("skillStates")) {
            CompoundTag statesTag = tag.getCompound("skillStates");
            for (String key : statesTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id == null) {
                    continue;
                }
                CompoundTag stateTag = statesTag.getCompound(key);
                SkillState state = new SkillState();
                state.cooldown = stateTag.getInt("cooldown");
                state.charges = stateTag.contains("charges") ? stateTag.getInt("charges") : -1;
                state.maxCharges = stateTag.contains("maxCharges") ? stateTag.getInt("maxCharges") : -1;
                state.castCount = stateTag.getInt("castCount");
                this.skillStates.put(id, state);
            }
        }
    }

    public String getCooldownStr() {
        return String.format("%.1f", getCooldownSeconds());
    }
}
