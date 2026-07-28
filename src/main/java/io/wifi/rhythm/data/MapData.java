package io.wifi.rhythm.data;

import java.util.List;

public class MapData {
    public String MusicDisplayName;
    public String MapName;
    public String MapFolderName;
    public String Original;
    public String Mapper;
    public int Level;
    public List<Note> Notes;
    public List<ClickData> Clicks;
    public OffsetData CoverPicOffset;
    public ColorData CoverPicBorderColor;
    public List<NoteClick> NoteClick;    // 节拍/音效事件（暂未使用）
    public String Src;
}