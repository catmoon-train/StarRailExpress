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

package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import org.agmas.noellesroles.voice.client.HeliumBuzzClientReceiver;

/**
 * Voicechat plugin for Helium Buzz effect.
 * Registers the client-side audio processing on the client environment.
 */
public class HeliumBuzzVoicechatPlugin implements VoicechatPlugin {

    @Override
    public String getPluginId() {
        return "noellesroles_helium_buzz";
    }

    @Override
    public void registerEvents(EventRegistration r) {
        // Only register on client side
        if (FabricLoader.getInstance().getEnvironmentType() != EnvType.CLIENT) {
            return;
        }
        HeliumBuzzClientReceiver.register(r);
    }
}
