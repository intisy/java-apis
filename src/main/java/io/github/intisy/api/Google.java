package io.github.intisy.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import io.github.intisy.utils.enums.Languages;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Collection;

@SuppressWarnings("deprecation")
public class Google {
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final Collection<String> scopes;
    public Google(String clientId, String clientSecret, String redirectUri, Collection<String> scopes) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.scopes = scopes;
    }
    public final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    public Credential authorize(final NetHttpTransport httpTransport) throws IOException {
        // Load client secrets.
        InputStream in = new FileInputStream(clientSecret);
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, scopes)
                        .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
    public String createRefreshToken(final String code) throws GeneralSecurityException, IOException {
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        // Exchange the authorization code for an access token
        return new GoogleAuthorizationCodeTokenRequest(
                GoogleNetHttpTransport.newTrustedTransport(), jsonFactory, clientId, clientSecret, code, redirectUri)
                .execute().getRefreshToken();
    }
    public Credential refreshAccessToken(final String refreshToken) throws IOException, GeneralSecurityException {
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(GoogleNetHttpTransport.newTrustedTransport())
                .setJsonFactory(jsonFactory)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(refreshToken);
        credential.refreshToken();
        return credential;
    }
    public String generateAuthorizationUrl(final NetHttpTransport httpTransport) throws IOException {
        // Load client secrets.
        InputStream in = Files.newInputStream(Paths.get(clientSecret));
        GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build the authorization code flow object
        GoogleAuthorizationCodeFlow flow =
                new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY, clientSecrets, scopes)
                        .build();
        return flow.newAuthorizationUrl().setRedirectUri(redirectUri)
                .setScopes(scopes)
                .build();
    }
    public String translate(String text, String language) {
        if (!language.equalsIgnoreCase("english")) {
            // Instantiate a client
            Translate translate = TranslateOptions.newBuilder().setApiKey(clientSecret).build().getService();

            // Translating text
            Translation translation = translate.translate(
                    text,
                    Translate.TranslateOption.sourceLanguage("en"), // Source language (optional)
                    Translate.TranslateOption.targetLanguage(Languages.getEnum(language).getCode()),// Target language
                    Translate.TranslateOption.model("base")
            );
            return translation.getTranslatedText();
        } else
            return text;
    }
    /*public String translate(String text, String sourceLanguage, String targetLanguage) {
        String endpoint = "https://www.googleapis.com/language/translate/v2?key=" + Secrets.GoogleKey.getKey() + "&source=" + Languages.getEnum(sourceLanguage).getCode() + "&target=" + Languages.getEnum(targetLanguage).getCode() + "&q=" + URLEncoder.encode(text);
        System.out.println(endpoint);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String at = "\"translatedText\": \"";
            int begin = response.body().indexOf(at)+at.length();
            return response.body().substring(begin, begin+response.body().substring(begin).indexOf("\""));
        } catch (Exception e) {
            return null;
        }
    }*/
}