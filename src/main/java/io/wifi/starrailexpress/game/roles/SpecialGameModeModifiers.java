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

package io.wifi.starrailexpress.game.roles;

import io.wifi.starrailexpress.SRE;
import org.agmas.harpymodloader.modifiers.HMLModifiers;
import org.agmas.harpymodloader.modifiers.SREModifier;
import org.agmas.noellesroles.config.SpawnInfoConfig.SpawnInfo;

import java.awt.*;
import java.util.HashSet;

public class SpecialGameModeModifiers {

    public static SREModifier TNT_TAGGED = HMLModifiers.registerModifier(new SREModifier(
            SRE.wifiId("tnt_tagged"),
            Color.RED.getRGB(),
            null,
            new HashSet<>(),
            false,
            false)).setDefaultMax(0).setOtherModeRole(true).setCanSetSpawnInfoInConfig(false)
            .setSpawnInfo(new SpawnInfo().setMax(0));

    public static void init() {

    }
}
