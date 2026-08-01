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