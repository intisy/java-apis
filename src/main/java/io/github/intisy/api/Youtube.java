package io.github.intisy.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import io.github.intisy.utils.custom.Triplet;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collection;
import java.util.Collections;

@SuppressWarnings("deprecation")
public class Youtube {
    private final String APPLICATION_NAME = "Blizzity";
    private final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Collection<String> scopes;
    public Youtube(String clientId, String clientSecret, String redirectUri, Collection<String> scopes) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
    }
    public YouTube getService() throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = new Google(clientId, clientSecret, redirectUri, scopes).authorize(httpTransport);
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    public YouTube getService(Credential credential) throws GeneralSecurityException, IOException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new YouTube.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
    public Triplet<String, String, String> info(Credential credential) throws GeneralSecurityException, IOException {
        YouTube youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        YouTube.Channels.List channelRequest = youtube.channels().list(Collections.singletonList("snippet"));
        channelRequest.setMine(true);
        channelRequest.setFields("items(id,snippet(title,thumbnails/default/url,customUrl))");
        ChannelListResponse channelResult = channelRequest.execute();

        // Extract profile picture, username (handle), and display name
        Channel channel = channelResult.getItems().get(0);
        String profilePictureUrl = channel.getSnippet().getThumbnails().getDefault().getUrl();
        String displayName = channel.getSnippet().getTitle();
        String username = channel.getSnippet().getCustomUrl().substring(1);
        return new Triplet<>(profilePictureUrl, displayName, username);
    }
    public String id(Credential credential) throws GeneralSecurityException, IOException {
        YouTube youtube = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(), JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
        YouTube.Channels.List channelRequest = youtube.channels().list(Collections.singletonList("snippet"));
        channelRequest.setMine(true);
        channelRequest.setFields("items(id,snippet(title,thumbnails/default/url,customUrl))");
        ChannelListResponse channelResult = channelRequest.execute();

        // Extract profile picture, username (handle), and display name
        Channel channel = channelResult.getItems().get(0);
        return channel.getId();
    }
    public void uploadVideo(Credential credential, File mediaFile, String title, String description, String status) {
        try {
            YouTube youtubeService = getService(credential);

            // Define the Video object, which will be uploaded as the request body.
            Video video = getVideo(title, description, status);

            InputStreamContent mediaContent =
                    new InputStreamContent("application/octet-stream",
                            new BufferedInputStream(new FileInputStream(mediaFile)));
            mediaContent.setLength(mediaFile.length());

            // Define and execute the API request
            YouTube.Videos.Insert request = youtubeService.videos()
                    .insert(Collections.singletonList("snippet,status"), video, mediaContent);
            Video response = request.execute();
            System.out.println(response);
        } catch (GeneralSecurityException | IOException exception) {
            throw new RuntimeException(exception);
        }
    }
    public void uploadVideo(File mediaFile, String title, String description, String status) {
        try {
            YouTube youtubeService = getService();

            // Define the Video object, which will be uploaded as the request body.
            Video video = getVideo(title, description, status);

            InputStreamContent mediaContent =
                    new InputStreamContent("application/octet-stream",
                            new BufferedInputStream(new FileInputStream(mediaFile)));
            mediaContent.setLength(mediaFile.length());

            // Define and execute the API request
            YouTube.Videos.Insert request = youtubeService.videos()
                    .insert(Collections.singletonList("snippet,status"), video, mediaContent);
            Video response = request.execute();
            System.out.println(response);
        } catch (GeneralSecurityException | IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    private Video getVideo(String title, String description, String status) {
        Video video = new Video();

        // Add the snippet object property to the Video object.
        VideoSnippet snippet = new VideoSnippet();
        snippet.setCategoryId("22");
        snippet.setDescription(description);
        snippet.setTitle(title);
        video.setSnippet(snippet);

        // Add the status object property to the Video object.
        VideoStatus videoStatus = new VideoStatus();
        videoStatus.setPrivacyStatus(status);
        video.setStatus(videoStatus);
        return video;
    }
}