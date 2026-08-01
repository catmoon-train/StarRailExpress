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

package org.agmas.noellesroles.packet;

import com.google.gson.Gson;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;

public class TaskBlocksInfos {
    public ArrayList<TaskBlocksInfo> infos;
    public static final Gson gson = new Gson();

    public TaskBlocksInfos(HashMap<BlockPos, Integer> taskBlocks) {
        ArrayList<TaskBlocksInfo> arrs = new ArrayList<>();
        for (var set : taskBlocks.entrySet()) {
            BlockPos pos = set.getKey();
            int type = set.getValue();
            var blockInfo = new TaskBlocksInfo(pos, type);
            arrs.add(blockInfo);
        }
        this.infos = arrs;
    }

    public TaskBlocksInfos(String jsonData) {
        try {
            var result = gson.fromJson(jsonData, this.getClass());
            this.infos = result.infos;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public TaskBlocksInfos(ArrayList<TaskBlocksInfo> infos) {
        this.infos = infos;
    }

    public HashMap<BlockPos, Integer> getTaskBlockInfosMap() {
        HashMap<BlockPos, Integer> taskBlocks = new HashMap<>();
        for (var info : this.infos) {
            taskBlocks.put(new BlockPos(info.pos), info.type);
        }
        return taskBlocks;
    }

    public String getStringBuf() {
        String resultStr = "";
        resultStr = gson.toJson(this);
        return resultStr;
    }
}