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

package io.wifi.starrailexpress.client.fourthroom;

public final class FourthRoomClientState {
    public static String lastSnapshotJson = "{}";
    private static FourthRoomClientSnapshot snapshot = FourthRoomClientSnapshot.empty();
    private static int snapshotVersion;

    private FourthRoomClientState() {
    }

    public static synchronized void updateSnapshot(String json) {
        lastSnapshotJson = json == null ? "{}" : json;
        snapshot = FourthRoomClientSnapshot.parse(lastSnapshotJson);
        snapshotVersion++;
    }

    public static synchronized FourthRoomClientSnapshot snapshot() {
        return snapshot;
    }

    public static synchronized int snapshotVersion() {
        return snapshotVersion;
    }

    public static synchronized void clear() {
        lastSnapshotJson = "{}";
        snapshot = FourthRoomClientSnapshot.empty();
        snapshotVersion++;
    }
}