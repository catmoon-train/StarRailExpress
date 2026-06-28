package org.agmas.noellesroles.commands;

import com.mojang.brigadier.arguments.FloatArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.Blocks;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggData;
import org.agmas.noellesroles.game.roles.neutral.cuckoo.CuckooEggHandler;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class EggClearCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    Commands.literal("sre:eggclear")
                            .requires(source -> source.hasPermission(2)) // 需要OP权限
                            .then(Commands.argument("range", FloatArgumentType.floatArg(1.0f, 500.0f))
                                    .executes(context -> {
                                        float range = FloatArgumentType.getFloat(context, "range");
                                        return clearEggs(context.getSource(), range);
                                    })
                            )
                            .executes(context -> {
                                // 默认范围10格
                                return clearEggs(context.getSource(), 10.0f);
                            })
            );
        });
    }

    private static int clearEggs(CommandSourceStack source, float range) {
        if (!(source.getLevel() instanceof ServerLevel level)) {
            source.sendFailure(Component.literal("This command can only be used on a server"));
            return 0;
        }

        // 获取命令执行者的位置
        BlockPos origin = source.getPlayer() != null 
                ? source.getPlayer().blockPosition() 
                : new BlockPos(0, 64, 0);

        int cleared = 0;
        double rangeSq = range * range;

        // 遍历所有注册的蛋
        Iterator<Map.Entry<UUID, CuckooEggData.EggInfo>> iter = 
                CuckooEggData.getAllEggs().entrySet().iterator();
        
        while (iter.hasNext()) {
            Map.Entry<UUID, CuckooEggData.EggInfo> entry = iter.next();
            CuckooEggData.EggInfo info = entry.getValue();
            Entity eggEntity = info.eggEntity;

            if (eggEntity == null || eggEntity.isRemoved()) {
                // 移除无效实体记录
                iter.remove();
                continue;
            }

            // 检查距离
            double dx = eggEntity.getX() - origin.getX();
            double dy = eggEntity.getY() - origin.getY();
            double dz = eggEntity.getZ() - origin.getZ();
            
            if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                // 在范围内，移除蛋
                CuckooEggHandler.breakEgg(eggEntity, info, level.getServer());
                iter.remove();
                cleared++;
            }
        }

        // 清理嗅探兽蛋的方块展示实体（即使没有布谷鸟蛋标记）
        java.util.List<Entity> orphanEggsToRemove = new java.util.ArrayList<>();
        level.getAllEntities().forEach((entity) -> {
            if (entity instanceof Display.BlockDisplay blockDisplay
                    && blockDisplay.getBlockState().is(Blocks.SNIFFER_EGG)) {
                double dx = entity.getX() - origin.getX();
                double dy = entity.getY() - origin.getY();
                double dz = entity.getZ() - origin.getZ();
                if (dx * dx + dy * dy + dz * dz <= rangeSq) {
                    orphanEggsToRemove.add(entity);
                }
            }
        });
        for (Entity entity : orphanEggsToRemove) {
            if (!entity.isRemoved() && !CuckooEggData.isCuckooEgg(entity)) {
                entity.remove(Entity.RemovalReason.DISCARDED);
                cleared++;
            }
        }

        // 清理蛋糕师烟熏炉方块展示实体和交互实体
        cleared += org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent
                .removeSmokerEntitiesInRange(level, origin, range);
        // 清理蛋糕师蛋糕实体
        cleared += org.agmas.noellesroles.game.roles.innocence.cake_maker.CakeMakerComponent
                .removeCakeEntitiesInRange(level, origin, range);

        final int finalCleared = cleared;
        final int finalRange = (int) range;
        if (finalCleared > 0) {
            source.sendSuccess(() -> Component.literal(
                    "Cleared " + finalCleared + " cuckoo egg(s) / cake maker entity(s) within " + finalRange + " blocks range")
                    .withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal(
                    "No cuckoo eggs or cake maker entities found within " + finalRange + " blocks range")
                    .withStyle(ChatFormatting.YELLOW));
        }

        return finalCleared;
    }
}
