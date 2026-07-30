package io.wifi.rhythm.data;

import java.util.ArrayList;
import java.util.List;

public class RhythmMapData {
    public String MusicDisplayName = "";
    public String MapName = "";
    public String MapFolderName = "";
    public String Original = "";
    public String Mapper = "";
    public int Level = 0;
    public List<RhythmNote> Notes = new ArrayList<>();
    public List<RhythmClickData> Clicks = new ArrayList<>();
    public RhythmOffsetData CoverPicOffset = new RhythmOffsetData();
    public RhythmColorData CoverPicBorderColor = new RhythmColorData();
    public List<RhythmNoteClick> NoteClick = new ArrayList<>(); // 节拍/音效事件（暂未使用）
    public String Src = "";

    public static RhythmMapData empty() {
        var mapData = new RhythmMapData();
        return mapData;
    }

    public int Delayer = 0; // 全局延迟，单位 ms，可正可负，默认 0
}