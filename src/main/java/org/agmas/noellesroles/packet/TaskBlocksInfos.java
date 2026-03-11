package org.agmas.noellesroles.packet;

import java.util.ArrayList;
import java.util.HashMap;

import com.google.gson.Gson;

import net.minecraft.core.BlockPos;

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