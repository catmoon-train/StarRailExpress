package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerPsychoComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.content.gui.PlayerBodyEntityContainer;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.NonNullList;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.agmas.noellesroles.config.NoellesRolesConfig.SpawnInfo;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public abstract class SRERole extends SREAbstractInfoClass {
    protected final Random random = new Random();
    private ResourceLocation identifier;
    private boolean canSetSpawnInfoInConfig = true;
    private boolean canSeeCoin = true;
    private boolean canSeeBodyItems = false;
    private boolean canGetBodyItems = false;
    private boolean canBeRandomed = true;
    private boolean canSeeBodyDeathReason = false;
    private boolean canSeeBodyRoleInfo = false;
    private boolean canUseInstinct = false;
    private boolean canIgnoreBlackout = false;
    private boolean canUseSkillWhileSpectator = false;
    private boolean mafiaTeam = false;
    /**
     * -1
     * 表示不设置。将不会调整普通刷新最大数量。与canSetSpawnInfoInConfig设置为false不同的是，此不会覆盖SpawnInfo。而canSetSpawnInfoInConfig将会覆盖SpawnInfo来达到配置项起作用。
     */
    public int defaultMaxCount = 1;
    public SpawnInfo spawnInfo = new SpawnInfo();
    /**
     * 1 / 10000
     */
    public int defaultEnableChance = -1;
    public int defaultEnableNeedPlayerCount = -1;
    public int defaultEnableMaxPlayerCount = -1;
    private SpecialMapRoleMap specialMapRole = SpecialMapRoleMap.all;
    private boolean specialVigilante = false;
    private boolean refreshableSpecialVigilante = false;
    private int refreshableSpecialVigilanteChance = -1;
    private int occupiedRoleCount = 1;
    public BiConsumer<ServerPlayer, SREGameWorldComponent> serverTickEvent = null;
    public BiConsumer<Player, SREGameWorldComponent> clientTickEvent = null;
    public HashSet<SRERole> opposingJobs = new HashSet<>();

    /**
     * 添加显示FLAG
     */
    public SRERole addFlag(String... flag) {
        for (var i : flag) {
            this.flags.add(i);
        }
        return this;
    }

    /**
     * 是否为指定flag
     * 
     * @param flags
     * @return
     */
    public boolean isFlag(String... flags) {
        for (var f : flags) {
            if (!this.flags.contains(f))
                return false;
        }
        return true;

    }

    /**
     * 是否为指定flag
     * 
     * @param flags
     * @return
     */
    public boolean isFlag(Set<String> flags) {
        return this.flags.containsAll(flags);
    }

    /**
     * 是否为指定flag，带inner.的标签。
     * 
     * @param flags
     * @return
     */
    public boolean isFlagWithInner(Set<String> flags) {
        var test = new HashSet<>(flags);
        if (test.contains("inner.enable")) {
            test.remove("inner.enable");
            if (SREDisableManager.isRoleDisabled(this))
                return false;
        }
        if (test.contains("inner.disable")) {
            test.remove("inner.disable");
            if (!SREDisableManager.isRoleDisabled(this))
                return false;
        }
        return this.flags.containsAll(test);
    }

    /**
     * 删除显示FLAG
     */
    public SRERole removeFlag(String... flag) {
        for (var i : flag) {
            this.flags.remove(i);
        }
        return this;
    }

    public Random getRandom() {
        return random;
    }

    public SRERole setClientGameTickEvent(BiConsumer<Player, SREGameWorldComponent> event) {
        this.clientTickEvent = event;
        return this;
    };

    public int getMoodColor() {
        if (moodType == MoodType.FAKE) {
            return Color.red.getRGB();
        }
        if (moodType == MoodType.REAL) {
            return Color.green.getRGB();
        } else
            return Color.PINK.getRGB();
    }

    /**
     * canSetSpawnInfoInConfig为true将会覆盖SpawnInfo来达到配置项起作用。
     */
    public SRERole setCanSetSpawnInfoInConfig(boolean flag) {
        this.canSetSpawnInfoInConfig = flag;
        return this;
    }

    public boolean canSetSpawnInfoInConfig() {
        return this.canSetSpawnInfoInConfig;
    }

    public boolean canBeRandomed() {
        return this.canBeRandomed;
    }

    /**
     * 是否可以出现在其他角色（例如赌徒）的随机池
     * 
     * @return
     */
    public SRERole setCanBeRandomedByOtherRoles(boolean flag) {
        this.canBeRandomed = flag;
        return this;
    }

    public boolean isKillerTeam() {
        return !this.isInnocent && (this.isNeutralForKiller || this.canUseKiller);
    }

    public boolean isKiller() {
        return !this.isInnocent && !this.isNeutrals && !this.isNeutralForKiller && this.canUseKiller;
    }

    public boolean isMafiaTeam() {
        return mafiaTeam;
    }

    public SRERole setMafiaTeam(boolean flag) {
        this.mafiaTeam = flag;
        this.flags.add("mafia_team");
        return this;
    }

    public boolean canIgnoreBlackout(Player player) {
        return canIgnoreBlackout;
    }

    public SRERole setCanIgnoreBlackout(Boolean bl) {
        this.canIgnoreBlackout = bl;
        return this;
    }

    public SRERole setCanSeeBodyItems(boolean flag) {
        canSeeBodyItems = flag;
        return this;
    }

    public SRERole setCanGetBodyItems(boolean flag) {
        canGetBodyItems = flag;
        canSeeBodyItems = flag;
        return this;
    }

    public boolean canGetBodyItems(Player player) {
        return canGetBodyItems;
    }

    public boolean canSeeBodyItems(Player player, PlayerBodyEntity body) {
        return canSeeBodyItems;
    }

    public boolean canSeeBodyRoleInfo(Player player) {
        return canSeeBodyRoleInfo;
    }

    public SRERole setCanSeeBodyRoleInfo(boolean bl) {
        this.canSeeBodyRoleInfo = bl;
        return this;
    }

    public boolean canSeeBodyDeathReason(Player player) {
        return canSeeBodyDeathReason;
    }

    public SRERole setCanSeeBodyDeathReason(boolean bl) {
        this.canSeeBodyDeathReason = bl;
        return this;
    }

    public SRERole setServerGameTickEvent(BiConsumer<ServerPlayer, SREGameWorldComponent> event) {
        this.serverTickEvent = event;
        return this;
    };

    public void autoGameTickEvent(Player player, SREGameWorldComponent gameWorldComponent) {
        if (player instanceof ServerPlayer sl) {
            this.serverGameTickEvent(sl, gameWorldComponent);
        } else {
            this.clientGameTickEvent(player, gameWorldComponent);
        }
    }

    public void clientGameTickEvent(Player player, SREGameWorldComponent gameWorldComponent) {
        if (clientTickEvent != null)
            clientTickEvent.accept(player, gameWorldComponent);
        clientTick(player);
    }

    public void serverGameTickEvent(ServerPlayer player, SREGameWorldComponent gameWorldComponent) {
        if (serverTickEvent != null)
            serverTickEvent.accept(player, gameWorldComponent);
        serverTick(player);
    }

    public int getOccupiedRoleCount() {
        return this.occupiedRoleCount;
    }

    public enum SpecialMapRoleMap {
        all, qiyucun, bigmap, underwater, fly, trap
    }

    public SpecialMapRoleMap getSpecialMapRole() {
        return this.specialMapRole;
    }

    public SRERole setSpecialMapRole(SpecialMapRoleMap specialMapRole) {
        this.specialMapRole = specialMapRole == null ? SpecialMapRoleMap.all : specialMapRole;
        return this;
    }

    public boolean isSpecialMapRole() {
        return this.specialMapRole != SpecialMapRoleMap.all;
    }

    public boolean isSpecialVigilante() {
        return this.specialVigilante && this.isVigilanteTeam();
    }

    public SRERole setSpecialVigilante(boolean specialVigilante) {
        this.specialVigilante = specialVigilante;
        return this;
    }

    public SRERole setSpecialPolice(boolean specialVigilante) {
        return setSpecialVigilante(specialVigilante);
    }

    public boolean canRefreshableSpecialVigilante() {
        return this.isSpecialVigilante() && this.refreshableSpecialVigilante;
    }

    public int getRefreshableSpecialVigilanteChance() {
        return this.refreshableSpecialVigilanteChance;
    }

    public SRERole setRefreshableSpecialVigilante(int chance, boolean refreshable) {
        this.refreshableSpecialVigilanteChance = chance;
        this.refreshableSpecialVigilante = refreshable;
        return this;
    }

    /**
     * 删除互斥职业
     * 
     * @param role
     * @return
     */
    public SRERole removeOpposingJobs(SRERole role) {
        this.opposingJobs.remove(role);
        return this;
    }

    /**
     * 添加双向互斥职业。互斥职业可能导致平民阵营角色数量增加。
     * 
     * @param role
     * @return
     */
    public SRERole addTwoWayOpposingJobs(SRERole role) {
        this.opposingJobs.add(role);
        role.opposingJobs.add(this);
        return this;
    }

    /**
     * 添加单向互斥职业。互斥职业可能导致平民阵营角色数量增加。
     * 
     * @param role
     * @return
     */
    public SRERole addOpposingJobs(SRERole role) {
        this.opposingJobs.add(role);
        return this;
    }

    /**
     * 设置单向互斥职业
     * 
     * @param roles
     * @return
     */
    public SRERole setOpposingJobs(List<SRERole> roles) {
        this.opposingJobs.clear();
        this.opposingJobs.addAll(roles);
        return this;
    }

    public SRERole setOccupiedRoleCount(int occupiedRoleCount) {
        this.occupiedRoleCount = occupiedRoleCount;
        return this;
    }

    public SRERole setCanUseInstinct(boolean canUseInstinct) {
        this.canUseInstinct = canUseInstinct;
        return this;
    }

    public boolean canUseInstinct() {
        return this.canUseInstinct;
    }

    public SRERole setColor(int color) {
        this.color = color;
        return this;
    }

    public SRERole setIdentifier(ResourceLocation identifier) {
        this.identifier = identifier;
        return this;
    }

    public SRERole setInnocent(boolean innocent) {
        isInnocent = innocent;
        return this;
    }

    public SRERole setCanUseKiller(boolean canUseKiller) {
        this.canUseKiller = canUseKiller;
        return this;
    }

    public SRERole setMoodType(MoodType moodType) {
        this.moodType = moodType;
        return this;
    }

    public ToIntFunction<Player> getMaxSprintTimeSupplier() {
        return this.customSprintTimeGetter;
    }

    public SRERole setMaxSprintTime(ToIntFunction<Player> func) {
        this.customSprintTimeGetter = func;
        return this;
    }

    public SRERole setMaxSprintTime(int maxSprintTime) {
        this.maxSprintTime = maxSprintTime;
        return this;
    }

    public SRERole setCanSeeTime(boolean canSeeTime) {
        this.canSeeTime = canSeeTime;
        return this;
    }

    private int color;
    private boolean isInnocent;
    private boolean canUseKiller;
    private MoodType moodType;

    public boolean isAutoReset() {
        return autoReset;
    }

    public SRERole setAutoReset(boolean autoReset) {
        this.autoReset = autoReset;
        return this;
    }

    private boolean isNeutrals = false;
    private boolean autoReset = true;
    private boolean ableToPickUpRevolver;
    private boolean isNeutralForKiller = false;
    private boolean canSeeTeammateKiller = true;
    private boolean canUseSabotage = false;
    private boolean canJumpManhole = false;
    private boolean canAcrossFog = false;

    public boolean isNeutrals() {
        return this.isNeutrals;
    }

    public boolean isVigilanteTeam() {
        return isVigilanteTeam;
    }

    public boolean isNeutralForKiller() {
        return this.isNeutralForKiller;
    }

    public boolean getNeutralForKiller() {
        return this.isNeutralForKiller;
    }

    public boolean canSeeTeammateKiller() {
        return this.canSeeTeammateKiller;
    }

    public SRERole setCanSeeTeammateKiller(boolean canSeeKiller) {
        this.canSeeTeammateKiller = canSeeKiller;
        return this;
    }

    public boolean canUseSabotage() {
        return this.canUseSabotage;
    }

    public SRERole setCanUseSabotage(boolean v) {
        this.canUseSabotage = v;
        return this;
    }

    public boolean canJumpManhole() {
        return this.canJumpManhole;
    }

    public SRERole setCanJumpManhole(boolean v) {
        this.canJumpManhole = v;
        return this;
    }

    public boolean canAcrossFog() {
        return this.canAcrossFog;
    }

    public SRERole setCanAcrossFog(boolean v) {
        this.canAcrossFog = v;
        return this;
    }

    public SRERole setNeutralForKiller(boolean forKiller) {
        this.isNeutralForKiller = forKiller;
        this.isNeutrals = true;
        return this;
    }

    public SRERole setNeutrals(boolean neutrals) {
        this.isNeutrals = neutrals;
        return this;
    }

    public SRERole setVigilanteTeam(boolean vigilanteTeam) {
        isVigilanteTeam = vigilanteTeam;
        return this;
    }

    public boolean isCanSeeCoin() {
        return canSeeCoin;
    }

    public boolean isAbleToPickUpRevolver() {
        return ableToPickUpRevolver;
    }

    public SRERole setAbleToPickUpRevolver(boolean ableToPickUpRevolver) {
        this.ableToPickUpRevolver = ableToPickUpRevolver;
        return this;
    }

    public ComponentKey<? extends RoleComponent> getComponentKey() {
        return componentKey;
    }

    public SRERole setComponentKey(ComponentKey<? extends RoleComponent> componentKey) {
        this.componentKey = componentKey;
        return this;
    }

    private boolean isVigilanteTeam;

    public int getColor() {
        return color;
    }

    public boolean isCanUseKiller() {
        return canUseKiller;
    }

    public boolean isCanSeeTime() {
        return canSeeTime;
    }

    public SRERole setAddChild(Consumer<LimitedInventoryScreen> addChild) {
        this.addChild = addChild;
        return this;
    }

    public void addChild(LimitedInventoryScreen screen) {
        if (addChild != null) {
            addChild.accept(screen);
        }
    }

    public boolean allowDeath(Player victim, @Nullable Player killer, ResourceLocation deathReason, boolean spawnBody) {
        return true;
    }

    public boolean afterShieldAllowDeath(Player victim, @Nullable Player killer, ResourceLocation deathReason,
            boolean spawnBody) {
        return true;
    }

    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        return;
    }

    public void onDeathWithBody(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason,
            PlayerBodyEntity playerBodyEntity) {
        return;
    }

    public void onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        return;
    }

    public void onPsychoStart(Player player, SREPlayerPsychoComponent psychoComponent) {
        return;
    }

    public void onPsychoOver(Player player, SREPlayerPsychoComponent psychoComponent) {
        return;
    }

    public void onFinishQuest(Player player, String quest) {

    }

    /**
     * 当玩家尝试获取物品时触发。该回调先于以下检查执行：{@code cantPickupItem}、捡起枪支逻辑以及物品栏已满的检测。
     * <p>
     * 根据返回值决定后续行为：
     * <ul>
     * <li><b>{@link InteractionResult#PASS}</b> — 使用默认逻辑，继续正常的物品拾取流程。</li>
     * <li><b>{@link InteractionResult#CONSUME}</b> — 取消当前拾取逻辑，方法直接返回，不执行后续操作。</li>
     * <li><b>{@link InteractionResult#SUCCESS}</b> — 禁止捡起物品，效果等同于取消逻辑。</li>
     * <li><b>{@link InteractionResult#FAIL}</b> — 禁止捡起物品，效果等同于取消逻辑。</li>
     * </ul>
     *
     * @param player 尝试获取物品的玩家，不可为 {@code null}
     * @param item   被尝试获取的物品对象
     * @return 行为控制结果，推荐在不需要特殊处理时返回 {@link InteractionResult#PASS}
     */
    public InteractionResult onPickUpItem(Player player, ItemStack item) {
        return InteractionResult.PASS;
    }

    public Predicate<Item> cantPickupItem(Player player) {
        return a -> false;
    }

    // public boolean onPickupItem(Player player, Item item) {
    // return true;
    // }

    public void serverTick(ServerPlayer player) {
    }

    public void clientTick(Player player) {
    }

    public InteractionResult rightClickEntity(Player player, Entity victim) {
        return InteractionResult.PASS;
    }

    /**
     * 左键时发生
     * 
     * @param player
     * @param victim
     * @return 返回InteractionResult.CONSUME取消原有逻辑。返回其余将继续。
     */
    public InteractionResult leftClickEntity(Player player, Entity victim) {
        return InteractionResult.PASS;
    }

    public List<ShopEntry> getShopEntries() {
        return new ArrayList<>();
    }

    /**
     * 在使用枪时触发。
     * 
     * @return 返回true继续执行，返回false不允许使用枪。
     */
    public boolean onUseGun(Player player) {
        return true;
    }

    /**
     * 在使用德林加手枪时触发。
     * 
     * @return 返回true继续执行，返回false不允许使用枪。
     */
    public boolean onUseDerringer(Player player) {
        return true;
    }

    /**
     * 在使用枪枪中人时触发。
     * 
     * @return 返回true继续执行，返回false终止。
     */
    public boolean onGunHit(Player killer, Player victim) {
        return true;
    }

    /**
     * 在使用刀时触发。
     * 
     * @return 返回true继续执行，返回false不允许使用刀。
     */
    public boolean onUseKnife(Player player) {
        return true;
    }

    /**
     * 在使用刀刀中人时触发。在onUseKnife后。
     * 
     * @return 返回true继续执行，返回false不执行。
     */
    public boolean onUseKnifeHit(Player player, Player target) {
        return true;
    }

    /**
     * 在HarpyModLoader中使用
     */
    public List<ItemStack> getDefaultItems() {
        return new ArrayList<>();
    }

    /**
     * 在HarpyModLoader中使用
     */
    public void onInit(MinecraftServer server, ServerPlayer serverPlayer) {

    }

    public static SREAbilityPlayerComponent getCooldownComponent(Player player) {
        return SREAbilityPlayerComponent.KEY.get(player);
    }

    /**
     * 玩家按下技能键时触发（服务端）
     * 
     * @param player
     * @return 是否成功触发，返回true取消后续逻辑。
     */
    public boolean onAbilityUse(ServerPlayer player) {
        return false;
    }

    /**
     * 在使用物品时触发（从AFK组件）
     */
    public InteractionResultHolder<ItemStack> onItemUse(Player player, Level world, InteractionHand hand) {
        return InteractionResultHolder.pass(ItemStack.EMPTY);
    }

    /**
     * 在与方块交互时触发（从AFK组件）
     */
    public InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    private ComponentKey<? extends RoleComponent> componentKey;
    private int maxSprintTime;
    private ToIntFunction<Player> customSprintTimeGetter = null;
    private boolean canSeeTime;
    private boolean isOtherModeRole = false;

    public Consumer<LimitedInventoryScreen> getAddChild() {
        return addChild;
    }

    private Consumer<LimitedInventoryScreen> addChild;
    private boolean canAutoAddMoney = false;
    private boolean bodyKillerVisibility = false;
    public ArrayList<String> defaultSpawnMaps = new ArrayList<>();

    /**
     * 设置是否允许看到尸体的杀手
     * 
     * @param flag
     * @return
     */
    public SRERole setCanSeeBodyKiller(boolean flag) {
        this.bodyKillerVisibility = flag;
        return this;
    }

    public enum MoodType {
        NONE, REAL, FAKE
    }

    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public SRERole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller, MoodType moodType,
            int maxSprintTime, boolean canSeeTime) {
        this.identifier = identifier;
        this.color = color;
        this.isInnocent = isInnocent;
        this.ableToPickUpRevolver = isInnocent;
        this.canUseKiller = canUseKiller;
        this.moodType = moodType;
        this.maxSprintTime = maxSprintTime;
        this.canSeeTime = canSeeTime;
        this.canUseInstinct = this.canUseKiller;
    }

    public SRERole setCanAutoAddMoney(boolean bl) {
        this.canAutoAddMoney = bl;
        return this;
    }

    public SRERole setCanHavePassiveIncome(boolean bl) {
        this.canAutoAddMoney = bl;
        return this;
    }

    public SRERole setPassiveIncome(boolean bl) {
        this.canAutoAddMoney = bl;
        return this;
    }

    public SRERole addChild(Consumer<LimitedInventoryScreen> addChild) {
        this.addChild = addChild;
        return this;
    }

    @Override
    public ResourceLocation identifier() {
        return identifier;
    }

    @Override
    public int color() {
        return color;
    }

    public boolean isInnocent() {
        return isInnocent;
    }

    public boolean canUseKiller() {
        return canUseKiller;
    }

    public MoodType getMoodType() {
        return moodType;
    }

    public int getMaxSprintTime(Player player) {
        if (this.customSprintTimeGetter != null) {
            return this.customSprintTimeGetter.applyAsInt(player);
        }
        return maxSprintTime;
    }

    public int getMaxSprintTime() {
        return maxSprintTime;
    }

    public boolean canSeeTime() {
        return canSeeTime;
    }

    public boolean canPickUpRevolver() {
        return this.ableToPickUpRevolver;
    }

    public SRERole setCanSeeCoin(boolean able) {
        this.canSeeCoin = able;
        return this;
    }

    public boolean canSeeCoin() {
        return this.canSeeCoin;
    }

    public SRERole setCanUseSkillWhileSpectator(boolean able) {
        this.canUseSkillWhileSpectator = able;
        return this;
    }

    public boolean canUseSkillWhileSpectator() {
        return this.canUseSkillWhileSpectator;
    }

    public SRERole setCanPickUpRevolver(boolean able) {
        this.ableToPickUpRevolver = able;
        return this;
    }

    public boolean isGambler() {
        return false;
    }

    public boolean canAutoAddMoney() {
        return this.canAutoAddMoney;
    }

    /**
     * 获取一局里最大可出现此职业数量。-1表示不变。
     * 
     * @param gameWorldComponent
     * @param serverLevel
     * @param players
     * @return
     */
    public int getRoundMaxCount(ServerLevel serverLevel, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players, String mapName) {
        if (spawnInfo.maxSpawn == -1)
            return -1;
        // 优先使用 spawnInfo（来自用户配置），若未设置则跳过
        int minPlayer = this.spawnInfo.minEnabledPlayer;
        if (minPlayer >= 0) {
            int playerCount = players.size();
            if (playerCount < minPlayer) {
                return 0;
            }
        }
        int maxPlayer = this.spawnInfo.maxEnabledPlayer;
        if (maxPlayer >= 0) {
            int playerCount = players.size();
            if (playerCount > maxPlayer) {
                return 0;
            }
        }
        int chance = this.spawnInfo.enableChance;
        if (chance >= 0) {
            int nchance = random.nextInt(0, 10000);
            if (nchance > chance) {
                return 0;
            }
        }
        if (!this.spawnInfo.map.isEmpty()) {
            if (!this.spawnInfo.map.contains(mapName)) {
                return 0;
            }
        }
        return this.spawnInfo.maxSpawn;
    }

    /**
     * -1
     * 表示不设置。将不会调整普通刷新最大数量。与canSetSpawnInfoInConfig设置为false不同的是，此不会覆盖SpawnInfo。而canSetSpawnInfoInConfig将会覆盖SpawnInfo来达到配置项起作用。
     */
    public SRERole setDefaultMax(int count) {
        defaultMaxCount = count;
        return this;
    };

    public SRERole addDefaultSpawnMaps(String... maps) {
        return this.setDefaultSpawnMaps(maps);
    };

    public SRERole setDefaultSpawnMaps(String... maps) {
        for (String s : maps) {
            this.defaultSpawnMaps.add(s);
        }
        return this;
    };

    public SRERole setDefaultEnableMaxPlayerCount(int count) {
        defaultEnableMaxPlayerCount = count;
        return this;
    };

    public SRERole setDefaultEnableNeededPlayerCount(int count) {
        defaultEnableNeedPlayerCount = count;
        return this;
    };

    public SRERole setSpawnInfo(Function<SpawnInfo, SpawnInfo> func) {
        this.spawnInfo = func.apply(this.spawnInfo);
        return this;
    }

    public SRERole setSpawnInfo(SpawnInfo spinfo) {
        this.spawnInfo = spinfo;
        return this;
    }

    /**
     * 1 = 1/10000
     * 
     * @param count
     * @return
     */
    public SRERole setDefaultEnableChance(int count) {
        defaultEnableChance = count;
        return this;
    };

    /**
     * 给予疯魔物品
     * 
     * @return 是否成功给予。给予失败将不会启动疯魔
     */
    public boolean onPsychoGiveItem(Player player, SREPlayerPsychoComponent srePlayerPsychoComponent) {
        return RoleUtils.insertStackInFreeSlot(player, new ItemStack(this.getPsychoItem()));
    }

    /**
     * 获取疯魔物品
     * 
     * @return
     */
    public Item getPsychoItem() {
        return TMMItems.BAT;
    };

    /**
     * 获取普通状态下的皮肤
     * 
     * @return 返回皮肤地址。如果不改变皮肤则返回null
     */
    public ResourceLocation getNormalSkin(Player player, boolean isSlim) {
        return null;
    };

    /**
     * 获取疯魔皮肤
     * 
     * @return
     */
    public ResourceLocation getPsychoSkin(Player player, boolean isSlim) {
        String suffix = isSlim ? "_thin" : "";
        ResourceLocation texture = SRE.watheId("textures/entity/psycho" + suffix + ".png");
        return texture;
    }

    /**
     * -1: Unknown - 1: Innocent - 2: Neturals but not for killer - 3: Neturals for
     * killer - 4: Killer - 5: Vigilante
     * 
     * @return
     */
    public int getRoleType() {
        return PlayerRoleWeightManager.getRoleType(this);
    }

    public boolean canSeeBodyKiller() {
        return this.bodyKillerVisibility;
    };

    /**
     * 是否是"其它模式"的职业（用于U键职业介绍页面的模式筛选）
     * 其他模式包括：游客、职业待定、超级亡命徒、土块、寻找者等
     * 
     * @return 是否为其他模式职业
     */
    public boolean isOtherModeRole() {
        return this.isOtherModeRole;
    }

    /**
     * 设置是否为"其它模式"的职业
     * 
     * @param isOtherModeRole 是否为其他模式职业
     * @return this
     */
    public SRERole setOtherModeRole(boolean isOtherModeRole) {
        this.isOtherModeRole = isOtherModeRole;
        this.addFlag("other_gamemode");
        return this;
    }

    /**
     * 玩家关闭尸体获取容器时触发
     * 
     * @param player       玩家
     * @param corpseEntity 尸体实体
     * @param container    尸体物品容器
     */
    public void onClosedPlayerBodyChest(Player player, PlayerBodyEntity corpseEntity,
            PlayerBodyEntityContainer container) {
    }

    /**
     * 玩家在容器左键时触发
     * 
     * @param slotId
     * @param button
     * @param clickType
     * @param player
     * @param slots
     * @param rows
     * @param container
     * @return 返回 true 默认逻辑，返回 false 阻止。
     */
    public boolean canGetBodyContent(int slotId, int button, ClickType clickType, Player player,
            PlayerBodyEntityContainer container, int rows, NonNullList<Slot> slots) {
        return canGetBodyItems(player);
    }

    /**
     * 玩家打开玩家尸体容器时触发
     * 
     * @param player
     */
    public void startOpenPlayerBody(Player player) {
    }

    /**
     * 玩家关闭玩家尸体容器时触发
     * 
     * @param player
     */
    public void stopOpenPlayerBody(Player player) {
    }

    /**
     * 打开玩家尸体时可否带走物品
     * 
     * @param player
     * @param container
     * @param slot
     * @param stack
     */
    public boolean canTakePlayerBodyItem(Player player, Container container, int slot, ItemStack stack) {
        return canGetBodyItems(player);
    }

    /**
     * 玩家在打开玩家尸体的时候quickMoveStack时触发。
     * 
     * @param player
     * @param index
     * @return
     */
    public boolean playerBodyQuickMoveStack(Player player, int index) {
        return true;
    }

    /**
     * 当玩家尝试丢弃物品时触发。该回调在物品真正被移除前执行，可用于拦截或自定义丢弃行为。
     * <p>
     * 根据返回值决定后续处理：
     * <ul>
     * <li><b>{@link InteractionResult#PASS}</b> — 使用默认逻辑，正常执行物品丢弃。</li>
     * <li><b>{@link InteractionResult#CONSUME}</b> — 取消本次丢弃行为</li>
     * <li><b>{@link InteractionResult#SUCCESS}</b> — 正常物品丢弃行为</li>
     * <li><b>{@link InteractionResult#FAIL}</b> — 取消本次丢弃行为</li>
     * </ul>
     *
     * @param player 尝试丢弃物品的玩家，不可为 {@code null}
     * @param item   准备丢弃的物品堆
     * @return 控制丢弃行为的交互结果，默认可返回 {@link InteractionResult#PASS}
     */
    public InteractionResult onDropItem(Player player, ItemStack item) {
        return InteractionResult.PASS;
    }

    @Override
    public Component getName() {
        String translationKey = "announcement.star.role." + this.identifier().getPath();
        // if (!Language.getInstance().has(translationKey)) {
        //     return Component.translatable("info.screen.role.name.error", translationKey);
        // }
        return Component.translatable(translationKey);
    }

    @Override
    public Component getDescription() {
        var id = this.identifier();
        String path = "info.screen.roleid." + id.getPath();
        if (!Language.getInstance().has(path)) {
            return Component.translatable("info.screen.role.desc.error", path);
        }
        return Component.translatable(path);
    }

    @Override
    public boolean hasSimpleDescription() {
        var id = this.identifier();
        String path = "info.screen.roleid." + id.getPath() + ".simple";
        if (!Language.getInstance().has(path)) {
            return false;
        }
        return true;
    }

    public Component getSimpleDescription() {
        var id = this.identifier();
        String path = "info.screen.roleid." + id.getPath() + ".simple";
        if (!Language.getInstance().has(path)) {
            return getDescription();
        }
        return Component.translatable(path);
    }
}
