package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Instagram {
    public void createContainer() {
        try {
            String apiVersion = "v12.0"; // replace with your desired API version
            String igUserId = "your-ig-user-id";
            String reelUrl = "your-reel-url";
            String caption = "your-caption";
            String shareToFeed = "true"; // or "false" depending on your requirement
            String collaborators = "collaborator1,collaborator2"; // comma-separated list of collaborator usernames
            String coverUrl = "your-cover-url";
            String audioName = "your-audio-name";
            String userTags = "tagged-user1,tagged-user2"; // comma-separated list of tagged user IDs
            String locationId = "your-location-id";
            String thumbOffset = "your-thumb-offset";
            String accessToken = "your-access-token";

            // Construct URL
            String urlString = String.format("https://graph.facebook.com/%s/%s/media?media_type=REELS&video_url=%s&caption=%s&share_to_feed=%s&collaborators=%s&cover_url=%s&audio_name=%s&user_tags=%s&location_id=%s&thumb_offset=%s&access_token=%s",
                    apiVersion, igUserId, URLEncoder.encode(reelUrl, StandardCharsets.UTF_8.toString()), URLEncoder.encode(caption, StandardCharsets.UTF_8.toString()),
                    shareToFeed, URLEncoder.encode(collaborators, StandardCharsets.UTF_8.toString()), URLEncoder.encode(coverUrl, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(audioName, StandardCharsets.UTF_8.toString()), URLEncoder.encode(userTags, StandardCharsets.UTF_8.toString()),
                    URLEncoder.encode(locationId, StandardCharsets.UTF_8.toString()), URLEncoder.encode(thumbOffset, StandardCharsets.UTF_8.toString()), accessToken);

            // Create URL object
            URL url = new URL(urlString);

            // Open connection
            HttpURLConnection con = (HttpURLConnection) url.openConnection();

            // Set request method
            con.setRequestMethod("POST");

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
            System.out.println("Response: " + response);
        } catch (Exception e) {
            StaticLogger.error(e);
        }
    }
}
