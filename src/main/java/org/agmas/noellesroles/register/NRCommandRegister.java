/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
        TimeRewindCommand.register();

        // 注册疫使测试指令
        org.agmas.noellesroles.commands.InfectedCommand.register();
    }
}
