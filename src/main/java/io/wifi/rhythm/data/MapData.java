package io.wifi.rhythm.data;

import java.util.ArrayList;
import java.util.List;

public class MapData {
    public String MusicDisplayName = "";
    public String MapName = "";
    public String MapFolderName = "";
    public String Original = "";
    public String Mapper = "";
    public int Level = 0;
    public List<Note> Notes = new ArrayList<>();
    public List<ClickData> Clicks = new ArrayList<>();
    public OffsetData CoverPicOffset = new OffsetData();
    public ColorData CoverPicBorderColor = new ColorData();
    public List<NoteClick> NoteClick = new ArrayList<>();    // 节拍/音效事件（暂未使用）
    public String Src = "";
    public static MapData empty(){
        var mapData = new MapData();
        return mapData;
    }
}