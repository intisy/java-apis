package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.Triplet;
import io.github.intisy.utils.utils.ConnectionUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@SuppressWarnings("unused")
public class Snapchat {
    String AUTHORIZATION_ENDPOINT = "https://accounts.snapchat.com/login/oauth2/authorize";
    String clientId;
    String clientSecret;
    String organizationId;
    String redirectUri;
    public Snapchat(String clientId, String clientSecret, String organizationId, String redirectUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.organizationId = organizationId;
        this.redirectUri = redirectUri;
    }
    public String refreshAccessToken(String refreshToken) throws IOException {
        String url = "https://accounts.snapchat.com/login/oauth2/access_token";
        HttpURLConnection connection = getHttpURLConnection("refresh_token=" + refreshToken +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&grant_type=refresh_token", url);

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }

        int start = response.indexOf("\"access_token\": \"")+17;
        int end = response.indexOf("\"", start);
        return response.substring(start, end);
    }

    @NotNull
    private HttpURLConnection getHttpURLConnection(String refreshToken, String url) throws IOException {
        String postData = refreshToken;

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        connection.setDoOutput(true);

        try (OutputStream outputStream = connection.getOutputStream()) {
            byte[] postDataBytes = postData.getBytes(StandardCharsets.UTF_8);
            outputStream.write(postDataBytes, 0, postDataBytes.length);
        }
        return connection;
    }

    public String generateAuthorizationUrl(String scopes) throws UnsupportedEncodingException {
        return AUTHORIZATION_ENDPOINT +
                "?client_id=" + clientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString()) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scopes, StandardCharsets.UTF_8.toString());
    }
    public Triplet<String, String, String> info(String token) throws IOException {
        String url = "https://businessapi.snapchat.com/v1/organizations/" + organizationId + "/public_profiles?limit=1";

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray publicProfiles = jsonObject.getJSONArray("public_profiles");
            JSONObject userProfile = publicProfiles.getJSONObject(0).getJSONObject("public_profile");
            return new Triplet<>(userProfile.getJSONObject("logo_urls").getString("original_logo_url"), userProfile.getString("display_name"), userProfile.getString("snap_user_name"));
        } else {
            throw new IOException("HTTP request failed with response code: " + responseCode);
        }
    }
    public String id(String token) throws IOException {
        String url = "https://businessapi.snapchat.com/v1/organizations/" + organizationId + "/public_profiles?limit=1";

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Authorization", "Bearer " + token);

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }
            JSONObject jsonObject = new JSONObject(response.toString());
            JSONArray publicProfiles = jsonObject.getJSONArray("public_profiles");
            JSONObject userProfile = publicProfiles.getJSONObject(0).getJSONObject("public_profile");
            return userProfile.getString("id");
        } else {
            throw new IOException("HTTP request failed with response code: " + responseCode);
        }
    }
    public String createRefreshToken(String code) throws IOException {
        String url = "https://accounts.snapchat.com/login/oauth2/access_token";
        HttpURLConnection connection = getHttpURLConnection("grant_type=authorization_code" +
                "&client_id=" + clientId +
                "&client_secret=" + clientSecret +
                "&code=" + code +
                "&redirect_uri=" + redirectUri, url);

        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        int start = response.indexOf("\"refresh_token\": \"")+18;
        int end = response.indexOf("\"", start);
        return response.substring(start, end);
    }
    public void postSpotlight(String id, String token, String media) {
        try {
            URL url = new URL("https://businessapi.snapchat.com/v1/public_profiles/" + id + "/spotlights");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Authorization", "Bearer " + token);
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);
            DataOutputStream out = new DataOutputStream(con.getOutputStream());
            String postData = "{\"media_id\":\"" + media + "\", \"skip_save_to_profile\":false, \"description\":\"hello #world\", \"locale\":\"en_US\"}";
            out.writeBytes(postData);
            out.flush();
            out.close();
            int responseCode = con.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();
            System.out.println("Response: " + response);
        } catch (Exception e) {
            StaticLogger.exception(e);
        }
    }

    public void multipartUpload(File file, String id, String token) {
        try {
            // Endpoint URL
            URL url = new URL("https://businessapi.snapchat.com/us/v1/public_profiles/" + id + "/media/123c1688-3725-452e-ade2-d89645cd2486/multipart-upload");

            // Open connection
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // Set request method
            con.setRequestMethod("POST");

            // Set request headers
            con.setRequestProperty("Authorization", "Bearer " + token);
            con.setRequestProperty("Content-Type", "multipart/form-data");

            // Enable output and set request body
            con.setDoOutput(true);
            OutputStream os = con.getOutputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));

            // Add form fields
            writer.write("--BoundaryString\r\n");
            writer.write("Content-Disposition: form-data; name=\"action\"\r\n\r\n");
            writer.write("ADD\r\n");

            writer.write("--BoundaryString\r\n");
            writer.write("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
            writer.write("Content-Type: application/octet-stream\r\n\r\n");
            FileInputStream fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
            os.flush();
            fileInputStream.close();

            writer.write("\r\n");

            writer.write("--BoundaryString\r\n");
            writer.write("Content-Disposition: form-data; name=\"part_number\"\r\n\r\n");
            writer.write("1\r\n");

            writer.write("--BoundaryString--\r\n");
            writer.flush();
            writer.close();
            os.close();

            // Read response
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print response
            System.out.println("Response: " + response);
        } catch (IOException e) {
            StaticLogger.exception(e);
        }
    }
    public void createMedia(String id, String token, String name) {
        try {
            byte[] keyBytes = new byte[32];
            SecureRandom random = new SecureRandom();
            random.nextBytes(keyBytes);
            SecretKeySpec secretKeySpec = new SecretKeySpec(keyBytes, "AES"); // for encoding the video file later

            // Generate random IV
            byte[] ivBytes = new byte[16];
            random.nextBytes(ivBytes);
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes); // for encoding the video file later

            // Encoding key and iv to base64
            String key64 = Base64.getEncoder().encodeToString(keyBytes);
            String iv64 = Base64.getEncoder().encodeToString(ivBytes);

            JSONObject payload = new JSONObject();
            payload.put("type", "VIDEO");
            payload.put("name", name);
            payload.put("key", key64);
            payload.put("iv", iv64);

            // Making the POST request
            URL url = new URL("https://businessapi.snapchat.com/v1/public_profiles/" + id + "/media");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream outputStream = connection.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                outputStream.write(input, 0, input.length);
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);
            ConnectionUtils.printOutput(connection);

            /*BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print response
            System.out.println("Response: " + response.toString());*/
        } catch (Exception e) {
            StaticLogger.error(Arrays.toString(e.getStackTrace()));
            throw new RuntimeException(e);
        }
    }

    // Method to convert hexadecimal string to byte array
    public byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
