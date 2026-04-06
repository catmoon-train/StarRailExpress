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