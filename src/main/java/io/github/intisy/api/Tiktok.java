package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.Triplet;
import io.github.intisy.utils.utils.ConnectionUtils;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Scanner;

@SuppressWarnings("unused")
public class TikTok {
    private final String clientKey;
    private final String clientSecret;
    private final String redirectUri;
    public TikTok(String clientKey, String clientSecret, String redirectUri) {
        this.clientKey = clientKey;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }
    public void uploadVideo(String token, File mediaFile) throws IOException {
        long videoSize = Files.size(mediaFile.toPath());
        String data = "{\"source_info\": { \"source\": \"FILE_UPLOAD\", \"video_size\": " + videoSize +
                ", \"chunk_size\" : " + videoSize + ", \"total_chunk_count\": 1 } }";

        URL url = new URL("https://open.tiktokapis.com/v2/post/publish/inbox/video/init/");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("Content-Type", "application/json");

        DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
        wr.writeBytes(data);
        wr.flush();
        wr.close();

        System.out.println(connection.getResponseCode());
        ConnectionUtils.printOutput(connection);
        String uploadUrl = getUrl(connection);
        org.apache.hc.client5.http.classic.HttpClient httpClient = HttpClients.createDefault();
        HttpPut httpPut = new HttpPut(uploadUrl);
        long fileSize = mediaFile.length();
        url = new URL(uploadUrl);
        connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("PUT");
        connection.setDoOutput(true);

        connection.setRequestProperty("Content-Range", "bytes 0-" + (fileSize - 1) + "/" + fileSize);
        connection.setRequestProperty("Content-Length", String.valueOf(fileSize));
        connection.setRequestProperty("Content-Type", "video/mp4");

        HttpEntity entity = new FileEntity(mediaFile, ContentType.create("video/mp4"));
        httpPut.setEntity(entity);
        httpPut.setHeader("Content-Range", "bytes 0-" + (mediaFile.length()-1) + "/" + (mediaFile.length()));
        httpPut.setHeader("Content-Type", "video/mp4");
        FileInputStream inputStream = new FileInputStream(mediaFile);
        byte[] buffer = new byte[4096];
        int bytesRead;

        org.apache.hc.core5.http.HttpResponse response = httpClient.execute(httpPut);
        try (DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        int responseCode = connection.getResponseCode();
        if (responseCode != 201) {
            System.err.println("Error uploading video: " + responseCode);
            // Handle error here (e.g., read error stream and log details)
        } else {
            System.out.println("Video uploaded successfully!");
        }
    }
    /*public void postVideo(File mediaFile, String title) {
        postVideo(mediaFile, title, "PUBLIC_TO_EVERYONE", false, false, false, 1000);
    }
    public void postVideo(File mediaFile, String title, String privacy_level, boolean disable_duet, boolean disable_comment, boolean disable_stitch, int video_cover_timestamp_ms) {
        try {
            postVideo(token(), mediaFile, title, privacy_level, String.valueOf(disable_duet), String.valueOf(disable_comment), String.valueOf(disable_stitch), String.valueOf(video_cover_timestamp_ms));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }*/
    public Triplet<String, String, String> info(String token) {
        try {
            // URL for the TikTok API
            String urlString = "https://open.tiktokapis.com/v2/user/info/?fields=display_name,avatar_url,username";

            // Creating URL object
            URL url = new URL(urlString);

            // Creating HTTPURLConnection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            // Setting the request method
            connection.setRequestMethod("GET");

            // Setting request headers
            connection.setRequestProperty("Authorization", "Bearer " + token);

            // Getting the response code
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new RuntimeException("Failed with HTTP error code: " + responseCode);
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            // Closing the connection
            connection.disconnect();

            JSONObject jsonObject = new JSONObject(response.toString());
            JSONObject userData = jsonObject.getJSONObject("data").getJSONObject("user");

            return new Triplet<>(userData.getString("avatar_url"), userData.getString("display_name"), userData.getString("username"));
        } catch (IOException e) {
            StaticLogger.exception(e);
            return null;
        }
    }
    public void postVideo(String token, File mediaFile, String title, String privacy_level, String disable_duet, String disable_comment, String disable_stitch, String video_cover_timestamp_ms) {
        try {
            long videoSize = Files.size(mediaFile.toPath());
            String data/* =
                    "{" +
                            "\"post_info\": {" +
                            "\"title\": \"" + title + "\", " +
                            "\"privacy_level\": \"" + privacy_level + "\", " +
                            "\"disable_duet\": " + disable_duet + ", " +
                            "\"disable_comment\": " + disable_comment + ", " +
                            "\"disable_stitch\": " + disable_stitch + ", " +
                            "\"video_cover_timestamp_ms\": " + video_cover_timestamp_ms +
                            "}," +
                            "\"source_info\": {" +
                            "\"source\": \"FILE_UPLOAD\"," +
                            "\"video_size\": " + videoSize + "," +
                            "\"chunk_size\": " + videoSize + "," +
                            "\"total_chunk_count\": 1" +
                            "}" +
                            "}"*/;
            data = "{\n" +
                    "  \"post_info\": {\n" +
                    "    \"title\": \"this will be a funny #cat video on your @tiktok #fyp\",\n" +
                    "    \"privacy_level\": \"MUTUAL_FOLLOW_FRIENDS\",\n" +
                    "    \"disable_duet\": false,\n" +
                    "    \"disable_comment\": true,\n" +
                    "    \"disable_stitch\": false,\n" +
                    "    \"video_cover_timestamp_ms\": 1000\n" +
                    "  },\n" +
                    "  \"source_info\": {\n" +
                    "      \"source\": \"FILE_UPLOAD\",\n" +
                    "      \"video_size\": 50000123,\n" +
                    "      \"chunk_size\":  10000000,\n" +
                    "      \"total_chunk_count\": 5\n" +
                    "  }\n" +
                    "}";

            URL url = new URL("https://open.tiktokapis.com/v2/post/publish/video/init/");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            System.out.println(token);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
            wr.writeBytes(data);
            wr.flush();
            wr.close();

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            System.out.println(data);

            if (responseCode != HttpURLConnection.HTTP_OK) {
                ConnectionUtils.printOutput(connection);
            } else {
                System.out.println("Video upload initiated successfully!");
            }

            /*String uploadUrl = getUrl(connection);
            long fileSize = mediaFile.length();
            url = new URL(uploadUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);

            connection.setRequestProperty("Content-Range", "bytes 0-" + (fileSize - 1) + "/" + fileSize);
            connection.setRequestProperty("Content-Length", String.valueOf(fileSize));
            connection.setRequestProperty("Content-Type", "video/mp4");

            FileInputStream inputStream = new FileInputStream(mediaFile);
            byte[] buffer = new byte[4096];
            int bytesRead;

            try (inputStream;
                 DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream())) {
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 201) {
                System.err.println("Error uploading video: " + responseCode);
                // Handle error here (e.g., read error stream and log details)
            } else {
                System.out.println("Video uploaded successfully!");
            }*/

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String getUrl(HttpURLConnection connection) throws IOException {
        BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }
        br.close();
        int startIndex = response.indexOf("upload_url") + 13; // Adding the length of "upload_url":" to get the start index
        int endIndex = response.indexOf("\"", startIndex);

        // Extract the upload URL
        String uploadUrl = response.substring(startIndex, endIndex);

        // Replace unicode escape sequences if any
        uploadUrl = uploadUrl.replace("\\u0026", "&");
        return uploadUrl;
    }

    public String generateAuthorizationUrl(String scopes) throws UnsupportedEncodingException {
        String AUTHORIZATION_ENDPOINT = "https://www.tiktok.com/v2/auth/authorize";
        return AUTHORIZATION_ENDPOINT +
                "?client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8.toString()) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString()) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8.toString());
    }
    public String createRefreshToken(String authorizationCode) throws IOException {
        String urlParameters = buildFormData(authorizationCode);
        byte[] postDataBytes = urlParameters.getBytes();

        String TOKEN_ENDPOINT = "https://open.tiktokapis.com/v2/oauth/token/";
        URL url = URI.create(TOKEN_ENDPOINT).toURL();
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(postDataBytes.length);

        connection.getOutputStream().write(postDataBytes);

        int responseCode = connection.getResponseCode();
        if (responseCode == 200) {
            StringBuilder response = new StringBuilder();
            Scanner scanner = new Scanner(connection.getInputStream());
            while (scanner.hasNextLine()) {
                response.append(scanner.nextLine());
            }
            scanner.close();

            String json = response.toString();
            int startIndex = json.indexOf("\"refresh_token\":\"") + 17;
            int endIndex = json.indexOf("\"", startIndex);
            return json.substring(startIndex, endIndex);
        } else {
            throw new IOException("API request failed with status code: " + responseCode);
        }
    }

    private String buildFormData(String authorizationCode) throws UnsupportedEncodingException {
        return "grant_type=authorization_code" +
                "&code=" + authorizationCode +
                "&client_key=" + URLEncoder.encode(clientKey, StandardCharsets.UTF_8.toString()) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8.toString()) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString());
    }
    public String refreshAccessToken(String refreshToken) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://open.tiktokapis.com/v2/oauth/token/").openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setDoOutput(true);

        String postData = "client_key=" + clientKey +
                "&client_secret=" + clientSecret +
                "&grant_type=refresh_token" +
                "&refresh_token=" + refreshToken;

        try (OutputStream outputStream = connection.getOutputStream()) {
            byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
            outputStream.write(postDataBytes, 0, postDataBytes.length);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        int startIndex = response.indexOf("\"access_token\":\"") + 16;
        int endIndex = response.indexOf("\"", startIndex);
        return response.substring(startIndex, endIndex);
    }

}
