package io.wifi.starrailexpress.api;

import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.agmas.harpymodloader.Harpymodloader;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

public abstract class SRERole {
    private ResourceLocation identifier;
    private boolean canSeeCoin = true;
    private boolean canUseInstinct = false;
    private int occupiedRoleCount = 1;
    public BiConsumer<ServerPlayer, SREGameWorldComponent> serverTickEvent = null;
    public BiConsumer<Player, SREGameWorldComponent> clientTickEvent = null;

    public SRERole setClientGameTickEvent(BiConsumer<Player, SREGameWorldComponent> event) {
        this.clientTickEvent = event;
        return this;
    };

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

    public SRERole setMax(int count) {
        Harpymodloader.setRoleMaximum(this, count);
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

    public ResourceLocation getIdentifier() {
        return identifier;
    }

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

    public boolean onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        return true;
    }

    public boolean onKill(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason) {
        return true;
    }

    public void onFinishQuest(Player player, String quest) {

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

    public void rightClickEntity(Player player, Entity victim) {
    }

    public void leftClickEntity(Player player, Entity victim) {
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

    public void onAbilityUse(Player player) {

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

    public Consumer<LimitedInventoryScreen> getAddChild() {
        return addChild;
    }

    private Consumer<LimitedInventoryScreen> addChild;
    private boolean canAutoAddMoney = false;

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

    public ResourceLocation identifier() {
        return identifier;
    }

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
}