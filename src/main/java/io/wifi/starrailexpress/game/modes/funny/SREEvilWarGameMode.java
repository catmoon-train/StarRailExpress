package io.wifi.starrailexpress.game.modes.funny;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.cca.*;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.WTLooseEndsGameMode;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;
import io.wifi.starrailexpress.index.SREDataComponentTypes;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ItemComponentUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;
import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.component.WorldModifierComponent;
import org.agmas.harpymodloader.modded_murder.RoleAssignmentPool;
import org.agmas.noellesroles.content.item.TimeStopClock;
import org.agmas.noellesroles.game.roles.killer.blood_feudist.BloodFeudistPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.executioner.ExecutionerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.imitator.ImitatorSkillRegistry;
import org.agmas.noellesroles.game.roles.killer.stalker.StalkerPlayerComponent;
import org.agmas.noellesroles.game.roles.killer.trapper.TrapperPlayerComponent;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import io.wifi.starrailexpress.game.roles.SpecialGameModeRoles;

import org.agmas.noellesroles.role.RedHouseRoles;
import org.agmas.noellesroles.utils.RoleUtils;
import pro.fazeclan.river.stupid_express.StupidExpress;
import pro.fazeclan.river.stupid_express.constants.SEModifiers;
import pro.fazeclan.river.stupid_express.constants.SERoles;

import java.util.*;
import java.util.function.Supplier;

/**
 * 邪恶战局
 * <p>
 * - 模式特性：每 7 狼（开局 1000 金币）对战 1 超级亡命徒（无友军透视，但是必定矮小）
 * </p>
 */
public class SREEvilWarGameMode extends WTLooseEndsGameMode {
    public final List<SRERole> BANED_ROLES = new ArrayList<>();
    protected static AttributeModifier tinyModifier = new AttributeModifier(
            StupidExpress.id("tiny_modifier"), -0.15, AttributeModifier.Operation.ADD_VALUE);
    public static final int ADD_BALANCE_TIME = 600;
    public static final int REVIVE_TIME = 200;
    public static final int ONE_SECOND_TICK = 20;
    public static final int EXECUTIONER_GUN_NUMBER = 6;
    int curBalanceTick = 0;
    int curReviveTick = 0;
    int curOneSecondTick = 0;

    @Override
    public boolean shouldRecordPlayerStats() {
        return false;
    }

    public SREEvilWarGameMode(ResourceLocation identifier) {
        super(identifier);
        addBanedRoles();
    }

    protected void addBanedRoles() {
        // 禁用 水鬼，布袋鬼，猫娘杀手，影隼，寻找者，dnf_killer
        BANED_ROLES.add(ModRoles.WATER_GHOST);
        BANED_ROLES.add(ModRoles.MA_CHEN_XU);
        BANED_ROLES.add(ModRoles.CAT_KILLER);
        BANED_ROLES.add(ModRoles.SHADOW_FALCON);
        BANED_ROLES.add(SpecialGameModeRoles.SEEKER);

    }

    @Override
    public boolean isLooseEndMode() {
        return false;
    }

    @Override
    protected void initItemList() {
        super.initItemList();
        looseEndsItems.add(ModItems.PATROLLER_REVOLVER::getDefaultInstance);
        looseEndsItems.add(TMMItems.DEFENSE_VIAL::getDefaultInstance);
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
        // 处理强制角色
        Map<UUID, SRERole> forcedRoles = new HashMap<>(Harpymodloader.FORCED_MODDED_ROLE_FLIP);
        List<ServerPlayer> playersWithoutForcedRoles = new ArrayList<>();
        // 每有一组狼人产生一个超级亡命徒：8人局对应 7 狼 1 亡命徒
        int superLooseEndCount = players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1);
        superLooseEndCount = Math.max(superLooseEndCount, 1);
        for (ServerPlayer player : players) {
            if (!forcedRoles.containsKey(player.getUUID())) {
                playersWithoutForcedRoles.add(player);
            }
        }
        for (Map.Entry<UUID, SRERole> entry : forcedRoles.entrySet()) {
            ServerPlayer forcePlayer = null;
            for (ServerPlayer player : players) {
                if (player.getUUID() == entry.getKey())
                    forcePlayer = player;
            }
            if (forcePlayer != null) {
                SRERole role = entry.getValue();
                if (role != null) {
                    if (role == SpecialGameModeRoles.SUPER_LOOSE_END)
                        --superLooseEndCount;
                    gameWorldComponent.addRole(forcePlayer, role);
                } else
                    playersWithoutForcedRoles.add(forcePlayer);
            }
        }
        if (superLooseEndCount < 0)
            superLooseEndCount = 0;
        else if (superLooseEndCount > playersWithoutForcedRoles.size()) {
            superLooseEndCount = playersWithoutForcedRoles.size();
        }

        // 生成杀手池
        RoleAssignmentPool killerPool = RoleAssignmentPool.createUnlimited("Killer",
                role -> !Harpymodloader.VANNILA_ROLES.contains(role) &&
                        role.canUseKiller() &&
                        !role.isInnocent() &&
                        role != TMMRoles.CIVILIAN &&
                        // 禁用角色
                        role.canBeRandomed() &&
                        !BANED_ROLES.contains(role));
        List<SRERole> assignedKillers = killerPool.selectRoles(playersWithoutForcedRoles.size() - superLooseEndCount);
        //打乱需要分配的玩家列表
        Collections.shuffle(playersWithoutForcedRoles);
        // 前 superLooseEndeCount 个是亡命徒，剩下的为普通杀手，优先分配亡命徒，然后再分配杀手
        for (int i = 0, curKillerIdx = 0; i < playersWithoutForcedRoles.size(); ++i, curKillerIdx %= assignedKillers.size()) {
            if (i < superLooseEndCount) {
                gameWorldComponent.addRole(playersWithoutForcedRoles.get(i), SpecialGameModeRoles.SUPER_LOOSE_END);
            } else if (curKillerIdx < assignedKillers.size()) {
                SRERole role = assignedKillers.get(curKillerIdx++);
                // 操纵师只会在有2个亡命徒以上时出现
                if (role == ModRoles.MANIPULATOR &&
                        (players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1) < 2)) {
                    role = TMMRoles.KILLER;
                }
                gameWorldComponent.addRole(playersWithoutForcedRoles.get(i), role);
            } else
                gameWorldComponent.addRole(playersWithoutForcedRoles.get(i), TMMRoles.KILLER);
        }
        Harpymodloader.FORCED_MODDED_ROLE_FLIP.clear();
    }

    /** 初始化物品 */
    @Override
    protected void initPlayerItems(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent) {
        for (ServerPlayer player : players) {
            player.getInventory().clearContent();
            SRERole role = gameWorldComponent.getRole(player);
            // 为特定角色添加特定物品
            if (role == SpecialGameModeRoles.SUPER_LOOSE_END) {
                // 添加亡命徒模式专属物品
                for (Supplier<ItemStack> itemSupplier : looseEndsItems) {
                    ItemStack itemStack = itemSupplier.get();
                    if (itemStack != null && !itemStack.isEmpty()) {
                        player.addItem(itemStack);
                    }
                }
            }
            // 刽子手自带6把手枪
            else if (role == ModRoles.EXECUTIONER) {
                ItemStack gun = new ItemStack(TMMItems.DERRINGER);
                gun.setCount(EXECUTIONER_GUN_NUMBER);
                player.addItem(gun);
            }
            // 清道夫拥有各种枪和几把飞刀
            else if (role == ModRoles.CLEANER) {
                player.addItem(new ItemStack(TMMItems.DERRINGER));
                player.addItem(new ItemStack(ModItems.PATROLLER_REVOLVER));
                player.addItem(new ItemStack(ModItems.BANDIT_REVOLVER));
                ItemStack throwingKnife = new ItemStack(ModItems.THROWING_KNIFE);
                throwingKnife.setCount(4);
                player.addItem(throwingKnife);
            }
            // 滞时鬼20s可以停1s
            else if (role == ModRoles.DELAYER) {
                ItemStack timeStopClock = new ItemStack(ModItems.TIME_STOP_CLOCK);
                ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_STOP_TIME, 20);
                ItemComponentUtils.setCustomDataTagIntValue(timeStopClock, TimeStopClock.TAG_COOLDOWN, 400);
//                timeStopClock.setDamageValue(TimeStopClock.MAX_DURABILITY);
                player.addItem(timeStopClock);
                ItemCooldowns itemCooldownManager = player.getCooldowns();
            }
        }
    }

    /** 发送欢迎包，根据特定角色发送 */
    @Override
    protected void sendWelcomePackets(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
            SRERole role1) {
        int looseEndCount = players.size() / (SREConfig.instance().evilWarKillGroupNumber + 1);
        looseEndCount = Math.max(looseEndCount, 1);
        int killerCount = players.size() - looseEndCount;
        for (ServerPlayer player : players) {
            var role = gameWorldComponent.getRole(player);
            if (role == null)
                continue;
            RoleUtils.sendWelcomeAnnouncement(player, role.identifier(), killerCount);
        }
    }

    protected void initModifier(List<ServerPlayer> players, SREGameWorldComponent gameWorldComponent,
            ServerLevel serverWorld) {
        WorldModifierComponent worldModifierComponent = WorldModifierComponent.KEY.get(serverWorld);
        // 所有亡命徒都有矮小修饰符
        for (ServerPlayer player : players) {
            if (gameWorldComponent.isRole(player, SpecialGameModeRoles.SUPER_LOOSE_END)) {
                worldModifierComponent.addModifier(player.getUUID(), SEModifiers.TINY, false);
                // worldModifierComponent.addModifier(player.getUUID(), SEModifiers.FEATHER,
                // false);
                // 使玩家缩小
                Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).removeModifier(tinyModifier);
                Objects.requireNonNull(player.getAttribute(Attributes.SCALE)).addPermanentModifier(tinyModifier);
            }
        }
        // 一次性同步
        worldModifierComponent.sync();
    }

    @Override
    public void initializeGame(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent,
            List<ServerPlayer> players) {
        super.initializeGame(serverWorld, gameWorldComponent, players);
        initModifier(players, gameWorldComponent, serverWorld);
        assignModdedRole(players, gameWorldComponent);

        // 初始化后处理 component
        for (ServerPlayer player : players) {
            SRERole role = gameWorldComponent.getRole(player);

            // 资金初始化
            SREPlayerShopComponent playerShopComponent = SREPlayerShopComponent.KEY.get(player);
            // 阴谋家首次获取资金时间推迟
            if (role == ModRoles.CONSPIRATOR) {
                playerShopComponent.setBalance(-200);
            }
            // dio初始资金减少
            else if (role == ModRoles.DIO) {
                playerShopComponent.setBalance(0);
            }
            // 超级亡命徒开局 0 块
            else if (role == SpecialGameModeRoles.SUPER_LOOSE_END) {
                playerShopComponent.setBalance(0);
            // 清道夫开局700块
            }
            else if (role == ModRoles.CLEANER) {
                playerShopComponent.setBalance(700);
            }
            // 滞时鬼开局-100块防止开局出双刀
            else if (role == ModRoles.DELAYER) {
                playerShopComponent.setBalance(-100);
            }
            // 默认安全时间结束后有700（一般杀手狂暴400，手雷330，手枪285)，需要斟酌启动配置
            else
                playerShopComponent.setBalance(200);


            // 角色添加初始特性
            // 组件等数据初始化
            if (role == ModRoles.IMITATOR) {
               // 模仿者初始化：随机3个技能，目前没有合适的公有修改方法
                ImitatorPlayerComponent imitatorPlayerComponent = ImitatorPlayerComponent.KEY.get(player);
                // 获取所有可复制技能并打乱
                List<ResourceLocation> roleIds = new ArrayList<>(ImitatorSkillRegistry.ALLOWED_ROLES);
                Collections.shuffle(roleIds);
                for (int slotIndex = 0; slotIndex < ImitatorPlayerComponent.MAX_SLOTS; ++slotIndex)
                {
                    // 每个槽插入一个技能
                    imitatorPlayerComponent.slotRoleId[slotIndex] = roleIds.get(Math.min(slotIndex, roleIds.size() - 1));
                    imitatorPlayerComponent.slotCooldown[slotIndex] = 0;
                    imitatorPlayerComponent.slotFillOrder[slotIndex] = slotIndex;
                    imitatorPlayerComponent.activeSlotIndex = slotIndex;
                    imitatorPlayerComponent.filledSlots++;
                }
                imitatorPlayerComponent.sync();
            }
            // 蕾米莉亚开局获得20分钟速度4
            else if (role == RedHouseRoles.REMILIA) {
                player.removeEffect(MobEffects.MOVEMENT_SPEED);
                player.addEffect(
                        new MobEffectInstance(
                                MobEffects.MOVEMENT_SPEED,  // 速度效果
                                20 * 1200,                  // 持续时间（tick）
                                3,
                                false,                // 是否显示粒子效果
                                false                  // 是否显示图标
                        ));
            }
            else if (role == ModRoles.TRAPPER) {
                TrapperPlayerComponent trapperPlayerComponent = TrapperPlayerComponent.KEY.get(player);
                trapperPlayerComponent.trapCharges = 0;
                trapperPlayerComponent.sync();
            }
        }
        curBalanceTick = 0;
        curReviveTick = 0;
        curOneSecondTick = 0;
    }

    @Override
    public void tickServerGameLoop(ServerLevel serverWorld, SREGameWorldComponent gameWorldComponent) {
        GameUtils.WinStatus winStatus = GameUtils.WinStatus.NONE;

        // tick计数
        if (curOneSecondTick++ >= ONE_SECOND_TICK) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                   continue;
                SRERole role = gameWorldComponent.getRole(player);

                // 超级亡命徒速度效果降级，当将为0则死亡
                if (role == SpecialGameModeRoles.SUPER_LOOSE_END) {
                    if (player.hasEffect(MobEffects.MOVEMENT_SPEED)) {
                        int lastDuration = Objects.requireNonNull(player.getEffect(MobEffects.MOVEMENT_SPEED))
                                .getDuration();
                        if (lastDuration < 2 * ONE_SECOND_TICK) {
                            int lastLevel = Objects.requireNonNull(player.getEffect(MobEffects.MOVEMENT_SPEED))
                                    .getAmplifier();
                            if (lastLevel > 2) {
                                player.removeEffect(MobEffects.MOVEMENT_SPEED);
                                player.addEffect(
                                        new MobEffectInstance(
                                                MobEffects.MOVEMENT_SPEED, // 速度效果
                                                1200 + lastDuration, // 持续时间（tick）
                                                lastLevel - 2, // 等级
                                                false, // 是否显示粒子效果
                                                false // 是否显示图标
                                        ));
                            }
                        }
                    } else
                        GameUtils.killPlayer(player, true, null, GameConstants.DeathReasons.FELL_OUT_OF_TRAIN);
                }
            }
            curOneSecondTick = 0;
        }
        // 复活cd事件
        if (curReviveTick++ >= REVIVE_TIME) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                    continue;

                SRERole role = gameWorldComponent.getRole(player);
                // 死灵可以复活cd降至10s
                if (role == SERoles.NECROMANCER || role == ModRoles.CAT_NECROMANCER) {
                    SREAbilityPlayerComponent abilityPlayerComponent = SREAbilityPlayerComponent.KEY.get(player);
                    abilityPlayerComponent.setCooldown(0);
                }
                // 仇杀客每10s涨一个误杀数
                else if (role == ModRoles.BLOOD_FEUDIST) {
                    BloodFeudistPlayerComponent bloodFeudistPlayerComponent = BloodFeudistPlayerComponent.KEY
                            .get(player);
                    bloodFeudistPlayerComponent.onAccidentalKill();
                }
                // 派对狂和扒手每10s额外获得200
                else if (role == SERoles.AVARICIOUS || role == ModRoles.PARTY_KILLER || role == ModRoles.NINJA) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(200);
                }
                // 毒师每10s获得一个催化剂减cd
                else if (role == ModRoles.POISONER) {
                    // 如果催化剂数量少于2个，则补一个
                    if (player.getInventory().countItem(ModItems.CATALYST) < 2) {
                        player.addItem(ModItems.CATALYST.getDefaultInstance());
                    }
                    ItemCooldowns itemCooldownManager = player.getCooldowns();
                    itemCooldownManager.removeCooldown(ModItems.CATALYST);
                }
                // 炸弹客手雷cd减为10s
                else if (role == ModRoles.BOMBER) {
                    ItemCooldowns itemCooldownManager = player.getCooldowns();
                    itemCooldownManager.removeCooldown(ModItems.BOMB);
                }

                // 每10s检测玩家身上是否有药丸，如果有则受到对应攻击，除了毒师
                if (role != ModRoles.POISONER){
                    Inventory inventory = player.getInventory();
                    for (var item : inventory.items) {
                        if (item.isEmpty())
                            continue;
                        if (item.is(ModItems.PILL)) {
                            GameUtils.killPlayer(player, true, player, SRE.id("poison"));
                        }
                    }
                }
            }
            curReviveTick = 0;
        }
        // 经济增长cd事件
        if (curBalanceTick++ >= ADD_BALANCE_TIME) {
            for (ServerPlayer player : serverWorld.players()) {
                if (GameUtils.isPlayerEliminated(player))
                    continue;

                // 给予角色金币
                SRERole role = gameWorldComponent.getRole(player);
                if (role == ModRoles.DIO) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(200);
                } else if (role == ModRoles.CONSPIRATOR) {
                    SREPlayerShopComponent.KEY.get(player).addToBalance(200);
                } else if (role != SpecialGameModeRoles.SUPER_LOOSE_END)
                    // 默认获取 500 金币
                    SREPlayerShopComponent.KEY.get(player).addToBalance(500);

                // 角色相关数据修改
                // 苦力怕每30s获得 2 颗雷
                if (role == ModRoles.CREEPER) {
                    ItemStack creeperItem = TMMItems.GRENADE.getDefaultInstance();
                    creeperItem.setCount(2);
                    player.addItem(creeperItem);
                }
                // 判断是否是特定角色，进行特定操作，每30秒判断一次
                else if (role == ModRoles.STALKER) {
                    StalkerPlayerComponent stalkerPlayerComponent = StalkerPlayerComponent.KEY.get(player);
                    // 潜行每30s获得 500 能量
                    stalkerPlayerComponent.energy += 500;
                    // 每30s获得 4 击杀数
                    stalkerPlayerComponent.phase2Kills += 4;
                    stalkerPlayerComponent.sync();
                }
                // 刽子手每30秒重新锁定目标为超级亡命徒：现已能自动锁定
                else if (role == ModRoles.EXECUTIONER) {
//                    ExecutionerPlayerComponent executionerPlayerComponent = ExecutionerPlayerComponent.KEY.get(player);
//                    for (ServerPlayer target : serverWorld.players())
//                        if (!GameUtils.isPlayerEliminated(target)
//                                && gameWorldComponent.isRole(target, SpecialGameModeRoles.SUPER_LOOSE_END)) {
//                            executionerPlayerComponent.target = target.getUUID();
//                            executionerPlayerComponent.targetSelected = true;
//                            executionerPlayerComponent.sync();
//                        }

                    // 刽子手每30s回复德林加子弹和消耗的德林加
                    int leftDerringerCount = 0;
                    Inventory inventory = player.getInventory();
                    for (var item : inventory.items) {
                        if (item.isEmpty())
                            continue;
                        if (item.is(TMMItems.DERRINGER)) {
                            if(item.getOrDefault(SREDataComponentTypes.USED, false))
                                item.set(SREDataComponentTypes.USED, false);
                            ++leftDerringerCount;
                        }
                    }
                    // 补充掉落的德林加
                    if (leftDerringerCount < EXECUTIONER_GUN_NUMBER) {
                        ItemStack gun = new ItemStack(TMMItems.DERRINGER);
                        gun.setCount(EXECUTIONER_GUN_NUMBER - leftDerringerCount);
                        player.addItem(gun);
                    }
                }
            }
            curBalanceTick = 0;
        }

        boolean civilianAlive = false;
        for (ServerPlayer player : serverWorld.players()) {
            // check if some civilians are still alive
            if (gameWorldComponent.isInnocent(player) && !GameUtils.isPlayerEliminated(player)) {
                civilianAlive = true;
            }
        }
        // check killer win condition (killed all civilians)
        if (!civilianAlive) {
            winStatus = GameUtils.WinStatus.KILLERS;
        }

        // 检查场上是否存在亡命徒
        if (winStatus != GameUtils.WinStatus.NONE) {
            boolean hasLooseEndAlive = false;
            List<ServerPlayer> lastLooseEnds = new ArrayList<>();

            for (ServerPlayer player : serverWorld.players()) {
                if ((gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                        || gameWorldComponent.isRole(player, SpecialGameModeRoles.SUPER_LOOSE_END))
                        && !GameUtils.isPlayerEliminated(player)) {
                    hasLooseEndAlive = true;
                    lastLooseEnds.add(player);
                }
            }

            // 如果只有亡命徒存活，且没有其他存活玩家，触发亡命徒获胜
            if (hasLooseEndAlive) {
                // 检查是否有其他非亡命徒的存活玩家
                boolean hasOtherAlive = false;
                for (ServerPlayer player : serverWorld.players()) {
                    if ((!gameWorldComponent.isRole(player, TMMRoles.LOOSE_END)
                            && !gameWorldComponent.isRole(player, SpecialGameModeRoles.SUPER_LOOSE_END))
                            && !GameUtils.isPlayerEliminated(player)) {
                        hasOtherAlive = true;
                        break;
                    }
                }
                if (!hasOtherAlive) {
                    winStatus = GameUtils.WinStatus.LOOSE_END;
                    // 补充 CustomWinnerID: loose_end
                    var roundEnd = SREGameRoundEndComponent.KEY.get(serverWorld);
                    roundEnd.CustomWinnerID = "loose_end";
                    for (ServerPlayer looseEnd : lastLooseEnds)
                        roundEnd.CustomWinnerPlayers.add(looseEnd.getUUID());
                } else {
                    // 有其他玩家存活，游戏继续
                    winStatus = GameUtils.WinStatus.NONE;
                }
            }
        }

        // check if out of time
        if (!SREGameTimeComponent.KEY.get(serverWorld).hasTime())
            winStatus = GameUtils.WinStatus.TIME;

        // GameUtils.WinStatus modifiedWinStatus =
        // AllowGameEnd.EVENT.invoker().allowGameEnd(serverWorld, winStatus, false);
        // if (!modifiedWinStatus.equals(GameUtils.WinStatus.NOT_MODIFY)) {
        // winStatus = modifiedWinStatus;
        // }
        // game end on win and display
        if (winStatus != GameUtils.WinStatus.NONE
                && gameWorldComponent.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) {
            SREGameRoundEndComponent.KEY.get(serverWorld).setRoundEndData(serverWorld.players(), winStatus);
            GameUtils.stopGame(serverWorld);
        }
    }
}
