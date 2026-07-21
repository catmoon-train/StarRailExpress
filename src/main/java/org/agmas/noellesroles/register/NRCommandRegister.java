package org.agmas.noellesroles.register;

import net.exmo.sre.repair.command.*;
import org.agmas.noellesroles.commands.*;

/**
 * Noellesroles 命令注册，从 {@link org.agmas.noellesroles.Noellesroles#onInitialize()} 中按类别剥离归一化而来。
 */
public class NRCommandRegister {

    public static void registerCommands() {
        BroadcastCommand.register();
        NewspaperCommand.register();
        AdminFreeCamCommand.register();
        SetRoleMaxCommand.register();
        NoellesrolesConfigCommand.register();
        VTCommand.register();
        org.agmas.noellesroles.commands.HeliumCommand.register();
        ExtraItemsManagerCommand.register();
        GameUtilsCommand.register();
        RoomCommand.register();
        StuckCommand.register();
        DisplayItemCommand.register();
        GoodsManagerCommand.register();
        DynamicShopCommand.register();
        WheelchairFieldItemCommand.register();
        GamblerMiracleCommand.register();
        EggClearCommand.register();
        RepairShopCommand.register();
        RepairStartCommand.register();
        RepairRoleCommand.register();
        RepairMapCommand.register();
        RepairPresetCommand.register();
        MurderTimeCommand.register();

        // 末日60秒模式：启动 + 区域配置命令
        net.exmo.sre.sixtyseconds.command.SixtySecondsStartCommand.register();
        net.exmo.sre.sixtyseconds.command.SixtySecondsAreaCommand.register();
        net.exmo.sre.sixtyseconds.command.SixtySecondsHelicopterCommand.register();
        net.exmo.sre.sixtyseconds.command.OceanCreatureCommand.register();

        // 注册疫使测试指令
        org.agmas.noellesroles.commands.InfectedCommand.register();

        // 基于用户名(username)的玩家白名单（可给非正版玩家开白，/namewl import 用 ; 批量导入）
        net.exmo.sre.namewhitelist.NameWhitelistCommand.register();
    }
}
