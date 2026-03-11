package io.wifi.syncrequests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

public class SyncRequests {
    public String url_root;
    public String pswKey;

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public SyncRequests(String url, String key) {
        this.url_root = url;
        this.pswKey = key;
    }

    public Boolean setValue(UUID playerUUID, @Nullable String key, String value) {
        String uuidStr = playerUUID.toString();
        String reqUrl = url_root;
        if (key != null)
            reqUrl = reqUrl + "/set/" + pswKey + "/" + uuidStr + "/" + key;
        else
            reqUrl = reqUrl + "/set/" + pswKey + "/" + uuidStr;
        try {
            return sendPost(reqUrl, value);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public String getValue(UUID playerUUID, @Nullable String key) {
        String uuidStr = playerUUID.toString();
        String reqUrl = url_root;
        if (key != null)
            reqUrl = reqUrl + "/get/" + pswKey + "/" + uuidStr + "/" + key;
        else
            reqUrl = reqUrl + "/get/" + pswKey + "/" + uuidStr;
        try {
            return sendGet(reqUrl);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static String sendGet(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public static boolean sendPost(String url, String textBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(textBody))
                .build();

        CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        return true;
    }
}