package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Facebook {
    String redirectUri;
    String appId;
    String appSecret;
    public Facebook(String appId, String appSecret, String redirectUri) {
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
    }
    public String getAuthorizationUrl() {
        return String.format("https://www.facebook.com/v19.0/dialog/oauth?client_id=%s&redirect_uri=%s&state=%s",
                appId, redirectUri, UUID.randomUUID());
    }
    public String buildUrl(String... permissions) {
        return "https://www.facebook.com/v19.0/dialog/oauth" +
                "?client_id=" + URLEncoder.encode(appId) +
                "&redirect_uri=" + URLEncoder.encode(redirectUri) +
                "&state=" + "facebook" +
                "&scope=" + String.join(",", permissions);
    }
    public String exchangeCodeForToken(String authorizationCode) {
        try {

            // Construct URL
            String urlString = String.format("https://graph.facebook.com/v19.0/oauth/access_token?client_id=%s&redirect_uri=%s&client_secret=%s&code=%s",
                    appId, URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString().toString()), appSecret, authorizationCode);

            // Create URL object
            URL url = new URL(urlString);

            // Open connection
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // Set request method
            con.setRequestMethod("GET");

            // Get response code
            int responseCode = con.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read response
            BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Print response
            System.out.println("Response: " + response.toString());
        } catch (Exception e) {
            StaticLogger.exception(e);
        }
        return "";
    }
}
