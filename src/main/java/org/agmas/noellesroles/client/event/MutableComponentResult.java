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

package org.agmas.noellesroles.client.event;

import net.minecraft.network.chat.MutableComponent;
import org.agmas.noellesroles.utils.MessageDetail;

import java.util.ArrayList;

public class MutableComponentResult {
    public MessageDetail singleContent = null;
    public ArrayList<MessageDetail> mutipleContent = new ArrayList<>();

    public MutableComponentResult() {

    }

    public MutableComponentResult(MessageDetail content) {
        this.singleContent = content;
    }

    public MutableComponentResult(MutableComponent content) {
        this.singleContent = new MessageDetail(content, false);
    }
}
