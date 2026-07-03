package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SRERoleWorldComponent;
import io.wifi.starrailexpress.event.OnRoleSkillUse;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.AbilityHandler;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Unified role skill registry.
 *
 * Legacy one-handler registration remains supported. New code should register
 * one or more {@link Definition}s so cooldowns, charges, input phases and HUD
 * state all use the same path.
 */
public final class RoleSkill {
    public enum Phase {
        PRESS,
        HOLD,
        RELEASE
    }

    public record RoleSkillContext(
            ServerPlayer player,
            @Nullable UUID target,
            ResourceLocation skillId,
            Phase phase,
            boolean skillReady) {
        public RoleSkillContext(ServerPlayer player, @Nullable UUID target) {
            this(player, target, ResourceLocation.withDefaultNamespace("legacy"), Phase.PRESS, true);
        }
    }

    @FunctionalInterface
    public interface Handler {
        boolean use(RoleSkillContext context);
    }

    public record Definition(
            ResourceLocation id,
            String nameKey,
            int cooldownTicks,
            int maxCharges,
            boolean continuous,
            int holdIntervalTicks,
            boolean announceToSelf,
            boolean toggleable,
            boolean shifted,
            boolean showOnHud,
            Handler handler) {
        public Definition {
            if (id == null || nameKey == null || handler == null) {
                throw new IllegalArgumentException("Skill id, name key and handler are required");
            }
            cooldownTicks = Math.max(0, cooldownTicks);
            holdIntervalTicks = Math.max(1, holdIntervalTicks);
            if (maxCharges == 0 || maxCharges < -1) {
                throw new IllegalArgumentException("maxCharges must be -1 or greater than 0");
            }
        }

        public static Builder builder(ResourceLocation id, String nameKey, Handler handler) {
            return new Builder(id, nameKey, handler);
        }
    }

    public static final class Builder {
        private final ResourceLocation id;
        private final String nameKey;
        private final Handler handler;
        private int cooldownTicks;
        private int maxCharges = -1;
        private boolean continuous;
        private int holdIntervalTicks = 1;
        private boolean announceToSelf = false;
        private boolean toggleable;
        private boolean shifted;
        private boolean showOnHud = false;

        private Builder(ResourceLocation id, String nameKey, Handler handler) {
            this.id = id;
            this.nameKey = nameKey;
            this.handler = handler;
        }

        public Builder cooldownTicks(int ticks) {
            this.cooldownTicks = ticks;
            return this;
        }

        public Builder cooldownSeconds(int seconds) {
            return cooldownTicks(seconds * 20);
        }

        public Builder charges(int charges) {
            this.maxCharges = charges;
            return this;
        }

        public Builder continuous(int intervalTicks) {
            this.continuous = true;
            this.holdIntervalTicks = intervalTicks;
            return this;
        }

        public Builder announceToSelf(boolean announce) {
            this.announceToSelf = announce;
            return this;
        }

        /** Allow this skill's handler to run even while on cooldown, for toggle-style deactivation. */
        public Builder toggleable(boolean toggleable) {
            this.toggleable = toggleable;
            return this;
        }

        /** Mark this skill as triggered only when the player is sneaking (shift + G). Excluded from V-key cycling. */
        public Builder shifted(boolean shifted) {
            this.shifted = shifted;
            return this;
        }

        /** Whether this skill should appear on the HUD. Defaults to false. Set true for skills that need HUD display. */
        public Builder showOnHud(boolean showOnHud) {
            this.showOnHud = showOnHud;
            return this;
        }

        public Definition build() {
            return new Definition(id, nameKey, cooldownTicks, maxCharges, continuous,
                    holdIntervalTicks, announceToSelf, toggleable, shifted, showOnHud, handler);
        }
    }

    /** Global registry entry for every skill definition, keyed by skill id. */
    public record SkillEntry(ResourceLocation skillId, ResourceLocation roleId, String nameKey) {
    }

    private static final Map<ResourceLocation, Consumer<RoleSkillContext>> LEGACY_SKILLS = new HashMap<>();
    private static final Map<ResourceLocation, List<Definition>> UNIFIED_SKILLS = new HashMap<>();
    private static final Map<ResourceLocation, SkillEntry> SKILL_REGISTRY = new HashMap<>();

    private RoleSkill() {
    }

    public static Builder skill(ResourceLocation id, String nameKey, Handler handler) {
        return Definition.builder(id, nameKey, handler);
    }

    public static void register(ResourceLocation role, Definition... definitions) {
        if (role == null || definitions == null || definitions.length == 0) {
            throw new IllegalArgumentException("Role and at least one skill definition are required");
        }
        List<Definition> skills = new ArrayList<>();
        Collections.addAll(skills, definitions);
        validateUniqueIds(role, skills);
        UNIFIED_SKILLS.put(role, List.copyOf(skills));
        for (Definition def : definitions) {
            SKILL_REGISTRY.put(def.id(), new SkillEntry(def.id(), role, def.nameKey()));
        }
    }

    public static void register(SRERole role, Definition... definitions) {
        register(role.identifier(), definitions);
    }

    public static List<Definition> getDefinitions(ResourceLocation role) {
        return UNIFIED_SKILLS.getOrDefault(role, List.of());
    }

    public static List<Definition> getDefinitions(SRERole role) {
        return role == null ? List.of() : getDefinitions(role.identifier());
    }

    public static boolean hasUnifiedSkills(ResourceLocation role) {
        return role != null && !getDefinitions(role).isEmpty();
    }

    public static boolean hasUnifiedSkills(SRERole role) {
        return role != null && hasUnifiedSkills(role.identifier());
    }

    /** Look up a skill by its global id. */
    public static Optional<SkillEntry> getSkillEntry(ResourceLocation skillId) {
        return Optional.ofNullable(SKILL_REGISTRY.get(skillId));
    }

    /** Return all registered skill entries. */
    public static Collection<SkillEntry> getAllSkills() {
        return SKILL_REGISTRY.values();
    }

    public static boolean selectSkill(ServerPlayer player, int slot) {
        SRERole role = getRole(player);
        List<Definition> definitions = getSelectableDefinitions(role);
        if (definitions.isEmpty()) {
            return false;
        }
        int selected = Math.floorMod(slot, definitions.size());
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);
        ability.selectSkill(selected, definitions);
        player.displayClientMessage(Component.translatable(
                "message.sre.skill.selected",
                Component.translatable(definitions.get(selected).nameKey())), true);
        return true;
    }

    /** Definitions that participate in V-key cycling (excludes shifted skills). */
    public static List<Definition> getSelectableDefinitions(ResourceLocation role) {
        return getDefinitions(role).stream()
                .filter(d -> !d.shifted())
                .toList();
    }

    public static List<Definition> getSelectableDefinitions(SRERole role) {
        return role == null ? List.of() : getSelectableDefinitions(role.identifier());
    }

    /**
     * 全局旁观者检查：回报该玩家是否为旁观者且不允许使用技能。
     * 返回 true = 被拦截（不应释放技能）。
     * 所有技能派发路径应在入口处调用此方法。
     */
    public static boolean blockForSpectator(ServerPlayer player) {
        if (!player.isSpectator()) {
            return false;
        }
        SRERole role = getRole(player);
        if (role != null && role.canUseSkillWhileSpectator()) {
            return false;
        }
        return true;
    }

    public static boolean beginUse(ServerPlayer player) {
        return beginUse(player, null, -1, Phase.PRESS, false);
    }

    public static boolean beginUseWithTarget(ServerPlayer player, UUID target) {
        return beginUse(player, target, -1, Phase.PRESS, false);
    }

    /** Convenience: use while respecting the player's current sneak state. */
    public static boolean beginUseShifted(ServerPlayer player) {
        return beginUse(player, null, -1, Phase.PRESS, player.isShiftKeyDown());
    }

    /** Convenience: use with target while respecting the player's current sneak state. */
    public static boolean beginUseShiftedWithTarget(ServerPlayer player, UUID target) {
        return beginUse(player, target, -1, Phase.PRESS, player.isShiftKeyDown());
    }

    public static boolean beginUse(ServerPlayer player, @Nullable UUID target, int requestedSlot, Phase phase) {
        return beginUse(player, target, requestedSlot, phase, false);
    }

    /**
     * 以"被附身"方式释放该玩家技能：合法绕过 {@code SKILL_BANED} 拦截。
     * 用于操纵师附身期间，以目标身份释放目标自身的技能（冷却记在目标身上）。
     */
    public static boolean beginUsePossessed(ServerPlayer player) {
        return beginUse(player, null, -1, Phase.PRESS, player.isShiftKeyDown(), true);
    }

    public static boolean beginUse(ServerPlayer player, @Nullable UUID target, int requestedSlot, Phase phase, boolean shifted) {
        return beginUse(player, target, requestedSlot, phase, shifted, false);
    }

    public static boolean beginUse(ServerPlayer player, @Nullable UUID target, int requestedSlot, Phase phase, boolean shifted, boolean possessed) {
        if (player == null) {
            return false;
        }
        SRERole role = getRole(player);
        if (role == null) {
            return false;
        }
        // 旁观者模式禁止使用技能（通过 canUseSkillWhileSpectator() 标记豁免）
        if (blockForSpectator(player)) {
            return false;
        }

        List<Definition> definitions = getDefinitions(role);
        if (!definitions.isEmpty()) {
            return useUnified(player, role, definitions, target, requestedSlot, phase, shifted);
        }
        if (phase != Phase.PRESS) {
            return false;
        }

        if (!beforeUse(player, role)) {
            return false;
        }
        Consumer<RoleSkillContext> consumer = LEGACY_SKILLS.get(role.identifier());
        if (consumer != null) {
            consumer.accept(new RoleSkillContext(player, target));
        } else if (target != null) {
            AbilityHandler.handlerWithTarget(player, target, possessed);
        } else if (!RoleMethodDispatcher.callOnAbilityUse(player)) {
            AbilityHandler.handler(player, possessed);
        }
        afterUse(player, role);
        return true;
    }

    private static boolean useUnified(ServerPlayer player, SRERole role, List<Definition> definitions,
                                      @Nullable UUID target, int requestedSlot, Phase phase, boolean shifted) {
        SREAbilityPlayerComponent ability = SREAbilityPlayerComponent.KEY.get(player);

        // Filter definitions by shifted state and select the right one
        List<Definition> applicable = definitions.stream()
                .filter(d -> d.shifted() == shifted)
                .toList();
        if (applicable.isEmpty()) {
            return false;
        }
        int slot = requestedSlot < 0 ? ability.getSelectedSkill() : requestedSlot;
        slot = Math.floorMod(slot, applicable.size());
        Definition definition = applicable.get(slot);
        ability.ensureSkills(definitions);

        if (phase == Phase.HOLD && !definition.continuous()) {
            return false;
        }
        if (phase == Phase.HOLD && !ability.shouldRunHold(definition.id(), definition.holdIntervalTicks())) {
            return false;
        }
        // RELEASE phase should only stop ongoing casting for continuous skills.
        // Calling the handler on RELEASE is dangerous for toggleable skills:
        // the skill just went on cooldown from PRESS, so skillReady=false,
        // which causes toggleable handlers to deactivate (e.g. Phantom
        // immediately removing the Invisibility it just applied).
        if (phase == Phase.RELEASE) {
            ability.stopCasting(definition.id());
            return false;
        }

        boolean skillReady = ability.canUseSkill(definition.id());

        if (phase == Phase.PRESS && !skillReady) {
            // Toggleable skills can still fire while on cooldown (for deactivation)
            if (!definition.toggleable()) {
                ability.showUnavailableMessage(definition.id());
                return false;
            }
        }
        if (!beforeUse(player, role)) {
            return false;
        }

        boolean used = definition.handler().use(
                new RoleSkillContext(player, target, definition.id(), phase, skillReady));

        if (!used) {
            return false;
        }

        // 只有 handler 真正执行成功时才消耗冷却/充能
        if (skillReady) {
            ability.markSkillUsed(definition);
        } else {
            // Toggleable deactivation: just stop casting if applicable
            ability.stopCasting(definition.id());
        }
        if (definition.announceToSelf()) {
            MutableComponent stateLabel;
            if (definition.toggleable() && !skillReady) {
                stateLabel = Component.translatable("message.sre.skill.toggled_off",
                        Component.translatable(definition.nameKey()));
            } else {
                stateLabel = Component.translatable("message.sre.skill.cast",
                        Component.translatable(definition.nameKey()),
                        ability.getCastCount(definition.id()));
            }
            player.displayClientMessage(stateLabel.withStyle(ChatFormatting.AQUA), true);
        }
        afterUse(player, role);
        return true;
    }

    public static boolean unregister(ResourceLocation role) {
        boolean legacy = LEGACY_SKILLS.remove(role) != null;
        boolean unified = UNIFIED_SKILLS.remove(role) != null;
        return legacy || unified;
    }

    public static boolean tryRegister(ResourceLocation role, Consumer<RoleSkillContext> handler) {
        if (role == null || handler == null || isRegistered(role)) {
            return false;
        }
        LEGACY_SKILLS.put(role, handler);
        return true;
    }

    public static void register(ResourceLocation role, Consumer<RoleSkillContext> handler) {
        if (!tryRegister(role, handler)) {
            throw new IllegalStateException("The handler of role '" + role + "' is already registered");
        }
    }

    /**
     * 访问此应当在初始化中访问而非在static中直接调用，这很可能会因为role为null导致崩溃。
     * @param role
     * @param handler
     */
    public static void register(SRERole role, Consumer<RoleSkillContext> handler) {
        register(role.identifier(), handler);
    }

    public static boolean isRegistered(ResourceLocation role) {
        return role != null && (LEGACY_SKILLS.containsKey(role) || UNIFIED_SKILLS.containsKey(role));
    }

    public static boolean isRegistered(SRERole role) {
        return role != null && isRegistered(role.identifier());
    }

    public static boolean beforeUse(ServerPlayer player, SRERole role) {
        if (!isRegistered(role)) {
            return true;
        }
        return OnRoleSkillUse.BEFORE.invoker().onUse(player, role);
    }

    public static void afterUse(ServerPlayer player, SRERole role) {
        if (!isRegistered(role)) {
            return;
        }
        OnRoleSkillUse.AFTER.invoker().onUse(player, role);
    }

    private static SRERole getRole(ServerPlayer player) {
        return SRERoleWorldComponent.KEY.get(player.level()).getRole(player);
    }

    private static void validateUniqueIds(ResourceLocation role, List<Definition> definitions) {
        long unique = definitions.stream().map(Definition::id).distinct().count();
        if (unique != definitions.size()) {
            throw new IllegalArgumentException("Duplicate skill id registered for role " + role);
        }
    }
}
