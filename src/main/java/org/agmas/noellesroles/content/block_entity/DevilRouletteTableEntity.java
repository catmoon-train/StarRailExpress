package org.agmas.noellesroles.content.block_entity;

import com.mojang.math.Transformation;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.game.modes.funny.SREDevilRouletteGameMode;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.mini_gme.DevilRouletteGame;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.*;

// TODO : 添加延迟开始和翻译键
public class DevilRouletteTableEntity extends BlockEntity {
    public static class TableTextDisplay extends Display.TextDisplay {
        public TableTextDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }
    }

    public static class TableItemDisplay extends Display.ItemDisplay {
        public TableItemDisplay(EntityType<?> entityType, Level level) {
            super(entityType, level);
        }
    }

    public DevilRouletteTableEntity(BlockPos blockPos, BlockState blockState) {
        super(ModBlocks.DEVIL_ROULETTE_TABLE_ENTITY, blockPos, blockState);
        Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        CENTER_POS = new Vec3(worldPosition.getX() + 0.5, worldPosition.getY() + 0.3, worldPosition.getZ() + 0.5);
        BlockPos centerSidePos = worldPosition.relative(facing.getClockWise());
        TIME_POS = new Vec3(centerSidePos.getX() + 0.5, centerSidePos.getY() + 0.2, centerSidePos.getZ() + 0.5);

        init();
        // 不随游戏初始化的成员
        gameMode = DevilRouletteGame.GameMode.Lobby;
        winnerText = null;

        BlockPos posOffset = new BlockPos(0, -1, 0);
        for (int i = 2; i >= -2; i -= 4) {
            // 玩家视角的方向
            for (int j = -1; j < 1; ++j) {
                // 左
                seatArea.add(worldPosition.offset(posOffset).relative(facing, i).relative(facing.getClockWise()));
                // 中
                seatArea.add(worldPosition.offset(posOffset).relative(facing, i));
                // 右
                seatArea.add(
                        worldPosition.offset(posOffset).relative(facing, i).relative(facing.getCounterClockWise()));
            }
        }
        for (int i = 1; i >= -1; i -= 2) {
            // 玩家视角的方向
            for (int j = -1; j < 1; ++j) {
                // 左
                playerOperateArea.add(worldPosition.relative(facing, i).relative(facing.getClockWise(), i));
                playerOperateArea.add(worldPosition.relative(facing, i));
                playerOperateArea.add(worldPosition.relative(facing, i).relative(facing.getCounterClockWise(), i));
            }
        }
    }

    /** 初始化成员 */
    public void init() {
        game = null;
        frontPlayerUUID = null;
        backPlayerUUID = null;

        gunStack = ItemStack.EMPTY;
        frontPlayerName = Component.empty();
        backPlayerName = Component.empty();
        shootSelfComponent = Component.translatable("noellesroles.game.devil_roulette.operate.shoot_self");
        shootOppositeComponent = Component.translatable("noellesroles.game.devil_roulette.operate.shoot_opposite");
        bulletComponent = Component.empty();

        isCollectFloatingText = true;

        curGunRotationY = 0;

        curAFKTick = 0;
        curAFKCount = 1;

        for (var floatingText : floatingTexts.values())
            floatingText.discard();
        floatingTexts.clear();
        for (var itemDisplay : itemDisplays.values())
            itemDisplay.discard();
        itemDisplays.clear();
        for (var display : tempSubDisplays) {
            display.discard();
        }
        tempSubDisplays.clear();
        itemDisplays.clear();
    }

    public void reset() {
        init();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        reset();
        if (winnerText != null) {

            winnerText.discard();
        }
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        // 当有玩家游玩时进行计数
        if (frontPlayerUUID == null && backPlayerUUID == null) {
            return;
        }

        // 显示玩家剩余操作时长，如果大于5s则5s更新一次，否则每隔1s更新一次
        if (curLeftTickGap == LEFT_TICK_LONG_GAP ||
                (curLeftTickGap % LEFT_TICK_GAP == 0 && AFK_TICK - curAFKTick <= LEFT_TICK_LONG_GAP)) {
            addFloatingText(TIME_POS, Component.literal((AFK_TICK - curAFKTick) / 20 + " Second"), 20, MIDDLE_SCALE);
            if (curLeftTickGap >= LEFT_TICK_LONG_GAP)
                curLeftTickGap = 0;
        }
        ++curLeftTickGap;

        if (curAFKTick++ >= AFK_TICK) {
            if (isGameActive()) {
                // 玩家未操作则自动向自己开火
                processFireResult(game.fire(DevilRouletteGame.Target.self));
                // 挂机响应时长会越来越短，以尽快结束等待的对局
                curAFKTick = AFK_TICK - Math.max(20, AFK_TICK / Math.max(2, ++curAFKCount));

                // 轮盘赌模式挂机会判断对手是否存活，如果不存活则强制结束
                if (gameMode == DevilRouletteGame.GameMode.Roulette && level != null) {
                    if (frontPlayerUUID != null
                            && GameUtils.isPlayerEliminated(level.getPlayerByUUID(frontPlayerUUID))) {
                        processFireResult(game.forceGameOverByKillPlayer(backPlayerUUID));
                    } else if (backPlayerUUID != null
                            && GameUtils.isPlayerEliminated(level.getPlayerByUUID(backPlayerUUID))) {
                        processFireResult(game.forceGameOverByKillPlayer(frontPlayerUUID));
                    }
                }
                tempSubDisplays.removeIf(display -> display == null || display.isRemoved());
            }
            // 游戏30未启动：重置
            else {
                removeFrontPlayer();
                removeBackPlayer();
                reset();
                curAFKTick = 0;
            }
        }
    }

    /**
     * 创建悬浮文字显示
     * - 会自动根据方块坐标偏移至中心
     */
    protected TableTextDisplay addFloatingTextInBlockPosCenter(BlockPos pos, Component text, int duration) {
        return addFloatingText(new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                text, duration);
    }

    /**
     * 创建悬浮文字显示
     * - 如果已存在则仅修改文本，不会重置之前的定时器
     */
    protected TableTextDisplay addFloatingText(Vec3 pos, Component text, int duration, Vec3 scale) {
        TableTextDisplay displayText = null;
        if (level != null) {
            if (floatingTexts.containsKey(text)) {
                displayText = floatingTexts.get(text);
            } else {
                displayText = new TableTextDisplay(EntityType.TEXT_DISPLAY, level);
                if (duration > 0) {
                    Scheduler.schedule(displayText::discard, duration);
                    // tempSubDisplays.removeIf(display -> display == null || !display.isAlive());
                    if (isCollectFloatingText)
                        tempSubDisplays.add(displayText);
                } else if (isCollectFloatingText) {
                    floatingTexts.put(text, displayText);
                }
                level.addFreshEntity(displayText);
            }
            displayText.setText(text);
            displayText.setPos(pos.x, pos.y, pos.z);
            // 设置缩放
            Matrix4f matrix = new Matrix4f().scale((float) scale.x, (float) scale.y, (float) scale.z);
            displayText.setTransformation(new Transformation(matrix));

            // 始终面向玩家
            displayText.setBillboardConstraints(Display.BillboardConstraints.CENTER);
        }
        return displayText;
    }

    protected TableTextDisplay addFloatingText(Vec3 pos, Component text, int duration) {
        return addFloatingText(pos, text, duration, NORMAL_SCALE);
    }

    protected void removeFloatingText(Component text) {
        if (floatingTexts.containsKey(text)) {
            floatingTexts.get(text).discard();
            floatingTexts.remove(text);
        }
    }

    protected TableTextDisplay replaceFloatingText(Component oldText, Component newText, int duration, Vec3 scale) {
        if (!floatingTexts.containsKey(oldText))
            return addFloatingText(CENTER_POS,
                    newText, duration);
        var oldTextPos = floatingTexts.get(oldText).position();
        removeFloatingText(oldText);
        return addFloatingText(oldTextPos, newText, duration, scale);
    }

    public void clientTick() {
        // 如果游戏未创建，则在两个方向显示对应的玩家名
        if (game == null) {
            return;
        }
    }

    public void startGame() {
        // 移除之前显示的结果
        if (winnerText != null) {
            winnerText.discard();
        }
        RandomSource random = RandomSource.create();
        if (level != null)
            random = level.getRandom();
        // 创建游戏
        game = new DevilRouletteGame(frontPlayerUUID, backPlayerUUID, random, level);
        if (gameMode != null)
            game.setGameMode(gameMode);
        // 初始化游戏
        game.init();
        if (!game.start()) {
            // 开始游戏失败，重置游戏
            reset();
        }
        removeFloatingText(Component.translatable("noellesroles.game.devil_roulette.wait_start"));
        updatePlayerHealthDisplay();

        // 生成左轮手枪动画
        if (level != null) {
            // 为当前操作玩家添加操作提示
            int operatorIdxOffset = 0;
            if (!game.canOperate(frontPlayerUUID)) {
                operatorIdxOffset = playerOperateArea.size() / 2;
            }
            offsetOperateText(operatorIdxOffset);

            // 创建左轮手枪
            TableItemDisplay gun = new TableItemDisplay(EntityType.ITEM_DISPLAY, level);
            gunStack = new ItemStack(TMMItems.REVOLVER);
            gun.setItemStack(gunStack);
            itemDisplays.put(gunStack, gun);

            // 设置位置
            gun.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5);
            // TODO : 旋转动画
            // 根据操作玩家正反决定旋转
            Direction facing = getFacing();
            // 基础旋转映射
            curGunRotationY = switch (facing) {
                // Y 轴朝上时，逆时针为正
                case NORTH -> 90f;
                case SOUTH -> -90f;
                case WEST -> 180f;
                // 默认 0°
                default -> 0f;
            };
            float additionalYaw = 0;
            // 反向玩家 +180°
            if (operatorIdxOffset != 0)
                additionalYaw = 180;
            updateGunRotation(additionalYaw);

            level.addFreshEntity(gun);
            // 播放装填声音
            level.playSound(null, worldPosition,
                    TMMSounds.ITEM_DERRINGER_RELOAD, SoundSource.BLOCKS,
                    0.15625f, 1f);

            showBulletText();
        }
    }

    public void assignMatch(Player player1, Player player2) {
        reset();
        if (player1 == null || player2 == null)
            return;
        if (!player1.isAlive() || !player2.isAlive())
            return;
        addPlayer(player1, true);
        addPlayer(player2, false);
        startGame();
    }

    /** 实际交互函数 */
    public ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide)
            return ItemInteractionResult.SUCCESS;
        DevilRouletteGame.FireResult fireResult = null;
        // 查询玩家操作位置的偏移
        int idxOffset = 0;
        if (player.getUUID() != frontPlayerUUID) {
            idxOffset = playerOperateArea.size() / 2;
        }
        for (int i = 0; i < playerOperateArea.size() / 2 && i + idxOffset < playerOperateArea.size(); ++i) {
            if (playerOperateArea.get(i + idxOffset).equals(pos)) {
                switch (i) {
                    // 点击左侧
                    case 0 -> {
                        fireResult = game.fire(DevilRouletteGame.Target.self);
                        break;
                    }
                    // 点击中间
                    case 1 -> {
                        boolean isNeedRemove = true;
                        // 使用道具
                        Item item = stack.getItem();
                        if (item == ModItems.MAGNIFYING_GLASS) {
                            // 放大镜
                            player.displayClientMessage(
                                    Component.translatable(
                                            game.getCurBullet() ? "noellesroles.game.devil_roulette.tip.is_real_bullet"
                                                    : "noellesroles.game.devil_roulette.tip.is_empty_bullet")
                                            .withStyle(game.getCurBullet() ? ChatFormatting.RED : ChatFormatting.AQUA),
                                    true);
                        } else if (item == ModItems.CHEWING) {
                            // 口香糖
                            game.getCurrentPlayerData().addHealth(1);
                            updatePlayerHealthDisplay();
                        } else if (item == ModItems.CLIP) {
                            // 弹夹逻辑
                            game.reloadBullet();
                            // 使用弹夹后获得一个道具
                            game.sendItemToPlayer(player);
                            afterReload();
                        } else if (item == ModItems.STEEL_BALL) {
                            // 钢球逻辑
                            game.addDamage(1);
                            showStrongBullet();
                        } else if (item == ModItems.REVERSING_CARD) {
                            // 反转卡逻辑
                            game.reverseCurBullet();
                            player.displayClientMessage(
                                    Component.translatable(
                                            "noellesroles.game.devil_roulette.tip.reverse_bullet")
                                            .withStyle(ChatFormatting.AQUA),
                                    true);
                        } else if (item == ModItems.TELEPHONE) {
                            // 电话逻辑
                            int idx = game.getRandomTrueBulletIdx();
                            player.displayClientMessage(
                                    Component.translatable(
                                            idx == -1 ? "noellesroles.game.devil_roulette.tip.no_real_bullet"
                                                    : "noellesroles.game.devil_roulette.tip.random_real_bullet",
                                            idx + 1)
                                            .withStyle(idx == -1 ? ChatFormatting.AQUA : ChatFormatting.RED),
                                    true);
                        } else
                            isNeedRemove = false;
                        if (isNeedRemove)
                            stack.shrink(1);
                        break;
                    }
                    // 点击右侧
                    case 2 -> {
                        fireResult = game.fire(DevilRouletteGame.Target.opposite);
                        break;
                    }
                }
                break;
            }
        }

        // 进行了开火操作
        processFireResult(fireResult);

        // 当玩家进行了操作 则重置AFK计数
        resetTick();

        return ItemInteractionResult.SUCCESS;
    }

    protected void processFireResult(DevilRouletteGame.FireResult fireResult) {
        if (fireResult == null || level == null)
            return;
        int idxOffset = 0;
        if (fireResult.operatorUUID != frontPlayerUUID) {
            idxOffset = playerOperateArea.size() / 2;
        }

        updatePlayerHealthDisplay();
        // 根据是否是实弹播放不同音效
        if (fireResult.isTrueBullet) {
            level.playSound(null, worldPosition,
                    TMMSounds.ITEM_REVOLVER_SHOOT, SoundSource.BLOCKS,
                    0.15625f, 1f);
        } else {
            level.playSound(null, worldPosition,
                    TMMSounds.ITEM_REVOLVER_CLICK, SoundSource.BLOCKS,
                    0.15625f, 1f);
        }
        // 播放重装弹药音效
        if (fireResult.isReload) {
            afterReload();
        }
        if (fireResult.isSwitch) {
            // 偏移到对方位置
            idxOffset = (idxOffset + playerOperateArea.size() / 2) % playerOperateArea.size();
            // 修改操作提示文本的位置到对方
            if (floatingTexts.get(shootSelfComponent) != null && floatingTexts.get(shootOppositeComponent) != null) {
                offsetOperateText(idxOffset);
            }
            updateGunRotation(180);
        }
        if (!fireResult.isTargetAlive || game.isGameEnd()) {
            Player winner = null;
            winner = level.getPlayerByUUID(game.getWinner().getPlayerUUID());

            switch (gameMode) {
                case Roulette -> {
                    // 对局中失败会被击杀
                    if (GameUtils.isGameStarted) {
                        if (level.getPlayerByUUID(frontPlayerUUID) != null
                                && level.getPlayerByUUID(frontPlayerUUID) != winner) {
                            GameUtils.killPlayer(
                                    level.getPlayerByUUID(frontPlayerUUID), false,
                                    winner, SRE.id("gun_shot"), false);
                        }
                        if (level.getPlayerByUUID(backPlayerUUID) != null
                                && level.getPlayerByUUID(backPlayerUUID) != winner) {
                            GameUtils.killPlayer(
                                    level.getPlayerByUUID(backPlayerUUID), false,
                                    winner, SRE.id("gun_shot"), false);
                        }
                    }

                    SREGameWorldComponent gameComponent = SREGameWorldComponent.KEY.get(level);
                    if (gameComponent.gameMode instanceof SREDevilRouletteGameMode mode) {
                        if (winner != null) {
                            SREPlayerShopComponent shopComponent = SREPlayerShopComponent.KEY.get(winner);
                            shopComponent.addToBalance(
                                    SREDevilRouletteGameMode.WINNER_COIN * game.getHealth(winner.getUUID()));
                            mode.addWinner(winner);
                        }
                        // 判断两次防止winner为空
                        Player player = level.getPlayerByUUID(frontPlayerUUID);
                        if (player != null && player != winner) {
                            mode.addLooser(player);
                        }
                        player = level.getPlayerByUUID(backPlayerUUID);
                        if (player != null && player != winner) {
                            mode.addLooser(player);
                        }
                    }
                }
                default -> {
                    // 普通对局没有小脑，直接穿盾
                    if (GameUtils.isGameStarted) {
                        if (level.getPlayerByUUID(frontPlayerUUID) != null
                                && level.getPlayerByUUID(frontPlayerUUID) != winner) {
                            GameUtils.killPlayer(
                                    level.getPlayerByUUID(frontPlayerUUID), false,
                                    // 杀手使用null 防止被小脑惩罚
                                    null, SRE.id("gun_shot"), false);
                        }
                        if (level.getPlayerByUUID(backPlayerUUID) != null
                                && level.getPlayerByUUID(backPlayerUUID) != winner) {
                            GameUtils.killPlayer(
                                    level.getPlayerByUUID(backPlayerUUID), false,
                                    null, SRE.id("gun_shot"), false);
                        }
                    }
                }
            }
            if (winner != null) {
                isCollectFloatingText = false;
                winnerText = addFloatingTextInBlockPosCenter(worldPosition,
                        Component.translatable("noellesroles.game.devil_roulette.winner",
                                winner.getName().getString()),
                        200);
                isCollectFloatingText = true;
                // 播放胜利音效
                level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS,
                        0.15625f, 1f);
                reset();
            }
        }
    }

    protected void afterReload() {
        if (level != null) {
            level.playSound(null, worldPosition,
                    TMMSounds.ITEM_DERRINGER_RELOAD, SoundSource.BLOCKS,
                    0.15625f, 1f);
        }
        showBulletText();
    }

    protected void showBulletText() {
        // TODO : 添加文本上升动画
        // 添加弹药信息文本
        bulletComponent = Component.translatable("noellesroles.game.devil_roulette.real_bullet",
                game.getTrueBulletNumber(), DevilRouletteGame.GUN_BULLET_SLOT_NUMBER);
        addFloatingText(CENTER_POS,
                bulletComponent, 60);
    }

    /** 当子弹被强化时显示强化信息 */
    protected void showStrongBullet() {
        addFloatingText(CENTER_POS, Component.literal("💀".repeat(Math.max(game.getDamage(), 0))), 30);
    }

    /**
     * 偏移操作提示文本
     * 
     * @param idxOffset 偏移量
     */
    protected void offsetOperateText(int idxOffset) {
        if (idxOffset + 2 >= playerOperateArea.size())
            return;
        BlockPos nextPos = playerOperateArea.get(idxOffset);
        addFloatingText(new Vec3(nextPos.getX() + 0.5, nextPos.getY() + 0.3, nextPos.getZ() + 0.5),
                shootSelfComponent, -1, MIDDLE_SCALE);
        // 偏移到右手边
        nextPos = playerOperateArea.get(idxOffset + 2);
        addFloatingText(new Vec3(nextPos.getX() + 0.5, nextPos.getY() + 0.3, nextPos.getZ() + 0.5),
                shootOppositeComponent, -1, MIDDLE_SCALE);
    }

    public boolean checkCanStartGame() {
        if (frontPlayerUUID != null && backPlayerUUID != null) {
            addFloatingTextInBlockPosCenter(worldPosition.offset(0, 1, 0),
                    Component.translatable("noellesroles.game.devil_roulette.wait_start"), -1);
            return true;
        }
        return false;
    }

    public boolean checkPlayerInRightSeat(Player player, boolean isFront) {
        if (isFront) {
            return player.getUUID() == frontPlayerUUID;
        }
        return player.getUUID() == backPlayerUUID;
    }

    public boolean canPlayerOperate(Player player) {
        return game != null && game.canOperate(player.getUUID());
    }

    /** 更新玩家生命显示 */
    public void updatePlayerHealthDisplay() {
        StringBuilder healthText = new StringBuilder();
        Component lastText = null;
        Player player = null;
        if (level != null) {
            player = level.getPlayerByUUID(frontPlayerUUID);
        }
        // 创建玩家生命显示
        if (player != null) {
            lastText = frontPlayerName;
            healthText.append(player.getName().getString()).append("\n");
            healthText.append("❤".repeat(Math.max(game.getHealth(frontPlayerUUID), 0)));
            frontPlayerName = Component.literal(String.valueOf(healthText));
            replaceFloatingText(lastText, frontPlayerName, -1, MIDDLE_SCALE);
        }

        if (level != null) {
            player = level.getPlayerByUUID(backPlayerUUID);
        }
        if (player != null) {
            lastText = backPlayerName;
            healthText = new StringBuilder();
            healthText.append(player.getName().getString()).append("\n");
            healthText.append("❤".repeat(Math.max(game.getHealth(backPlayerUUID), 0)));
            backPlayerName = Component.literal(String.valueOf(healthText));
            replaceFloatingText(lastText, backPlayerName, -1, MIDDLE_SCALE);
        }
    }

    /**
     * 更新枪的旋转角度
     * 
     * @param additionalYaw 额外的 Y 轴旋转角度（度数）
     */
    public void updateGunRotation(float additionalYaw) {
        TableItemDisplay gun = itemDisplays.get(gunStack);
        if (gun == null || !gun.isAlive())
            return;

        curGunRotationY += additionalYaw;
        curGunRotationY %= 360;

        // 绕自定义轴旋转（例如绕 (1, 0, 0) 即 X 轴）
        Quaternionf rotation = new Quaternionf()
                // 先绕Y轴旋转
                .rotateY((float) Math.toRadians(curGunRotationY))
                .rotateZ((float) Math.toRadians(-90));

        Matrix4f matrix = new Matrix4f().rotate(rotation);
        gun.setTransformation(new Transformation(matrix));
    }

    public boolean addPlayer(@NotNull Player player, boolean isFront) {
        BlockState state = getBlockState();
        if (isFront) {
            if (frontPlayerUUID != null || backPlayerUUID == player.getUUID()) {
                return false;
            }
            frontPlayerUUID = player.getUUID();
            BlockPos frontPos = worldPosition.relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            frontPlayerName = player.getName();
            addFloatingText(new Vec3(frontPos.getX() + 0.5, frontPos.getY() + 0.2, frontPos.getZ() + 0.5),
                    frontPlayerName, -1, MIDDLE_SCALE);
        } else {
            if (backPlayerUUID != null || frontPlayerUUID == player.getUUID()) {
                return false;
            }
            backPlayerUUID = player.getUUID();
            BlockPos backPos = worldPosition
                    .relative(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
            backPlayerName = player.getName();
            addFloatingText(new Vec3(backPos.getX() + 0.5, backPos.getY() + 0.2, backPos.getZ() + 0.5),
                    backPlayerName, -1, MIDDLE_SCALE);
        }
        checkCanStartGame();
        return true;
    }

    protected void removeFrontPlayer() {
        if (frontPlayerName != null) {
            removeFloatingText(frontPlayerName);
        }
        frontPlayerUUID = null;
        frontPlayerName = Component.empty();
    }

    protected void removeBackPlayer() {
        if (backPlayerName != null) {
            removeFloatingText(backPlayerName);
        }
        backPlayerUUID = null;
        backPlayerName = Component.empty();
    }

    /** 移除同方向相同的玩家，如果成功则为true */
    public boolean removePlayerIfSame(Player player, boolean isFront) {
        if (isFront) {
            if (frontPlayerUUID != null && frontPlayerUUID == player.getUUID()) {
                removeFrontPlayer();
                return true;
            }
        } else {
            if (backPlayerUUID != null && backPlayerUUID == player.getUUID()) {
                removeBackPlayer();
                return true;
            }
        }
        return false;
    }

    public void removeIfTextNull() {
        floatingTexts.values().removeIf(display -> display == null || !display.isAlive());
    }

    // 召集玩家到桌子上
    public void convenePlayers() {
        if (level != null && frontPlayerUUID != null && backPlayerUUID != null) {
            Player player1 = level.getPlayerByUUID(frontPlayerUUID);
            Player player2 = level.getPlayerByUUID(backPlayerUUID);
            if (player1 != null)
                player1.teleportTo(CENTER_POS.x, CENTER_POS.y, CENTER_POS.z);
            if (player2 != null)
                player2.teleportTo(CENTER_POS.x, CENTER_POS.y, CENTER_POS.z);
        }
    }

    public int getTrueBulletNumber() {
        if (game != null) {
            return game.getTrueBulletNumber();
        }
        return 0;
    }

    public boolean isGameActive() {
        return game != null && !game.isGameEnd();
    }

    public boolean isSeatAvailable(BlockPos pos) {
        return seatArea.contains(pos);
    }

    public boolean isFrontSeat(BlockPos pos) {
        return seatArea.contains(pos) && seatArea.indexOf(pos) < seatArea.size() / 2;
    }

    public void resetTick() {
        curAFKTick = 0;
        curAFKCount = 1;
        curLeftTickGap = 0;
    }

    public void setGameMode(DevilRouletteGame.GameMode gameMode) {
        this.gameMode = gameMode;
    }

    public DevilRouletteGame.GameMode getGameMode() {
        return gameMode;
    }

    public Direction getFacing() {
        if (this.level == null)
            return Direction.NORTH;
        BlockState state = this.getBlockState();

        // 如果方块有 HORIZONTAL_FACING 属性
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }

        // 如果方块有 FACING 属性（包含上下）
        if (state.hasProperty(BlockStateProperties.FACING)) {
            return state.getValue(BlockStateProperties.FACING);
        }

        return Direction.NORTH; // 默认
    }

    public List<BlockPos> getSeatArea() {
        return seatArea;
    }

    /** 0.5 倍 */
    public static final Vec3 MIDDLE_SCALE = new Vec3(0.5, 0.5, 0.5);
    /** 正常倍数 */
    public static final Vec3 NORMAL_SCALE = new Vec3(1, 1, 1);
    /** 玩家AFK时间 */
    public static final int AFK_TICK = 20 * 30;
    /** 显示玩家剩余时间的长间隔（超过5秒，每5s显示一次 */
    public static final int LEFT_TICK_LONG_GAP = 100;
    /** 显示玩家剩余时间的短间隔（小于5秒，每1s显示一次 */
    public static final int LEFT_TICK_GAP = 20;
    public final Vec3 CENTER_POS;
    /** 玩家操作时间显示位置 */
    public final Vec3 TIME_POS;
    /**
     * 悬浮文本
     */
    protected final Map<Component, TableTextDisplay> floatingTexts = new HashMap<>();
    protected final Map<ItemStack, TableItemDisplay> itemDisplays = new HashMap<>();
    /**
     * 游戏可用的座位区域
     * <p>
     * 前半是前方座位，后半是后方座位
     * </p>
     */
    protected final List<BlockPos> seatArea = new ArrayList<>();
    /**
     * 玩家操作区域
     * <p>
     * - 前半是前方玩家，后半是后方玩家，顺序为玩家的左中右
     * </p>
     */
    protected final List<BlockPos> playerOperateArea = new ArrayList<>();
    /**
     * 存储会自动消失的显示实体，当方块被移除时清空
     */
    protected final List<Display> tempSubDisplays = new ArrayList<>();
    protected DevilRouletteGame game;
    protected DevilRouletteGame.GameMode gameMode;
    protected Component frontPlayerName;
    protected Component backPlayerName;
    protected Component shootSelfComponent;
    protected Component shootOppositeComponent;
    protected Component bulletComponent;
    protected TableTextDisplay winnerText;
    /** 前方玩家 */
    protected UUID frontPlayerUUID;
    /** 后方玩家 */
    protected UUID backPlayerUUID;
    protected ItemStack gunStack;
    protected boolean isCollectFloatingText;
    protected float curGunRotationY;
    protected int curLeftTickGap;
    protected int curAFKTick;
    protected int curAFKCount;
}
