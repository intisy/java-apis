package io.github.intisy.api;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.net.URLEncoder;
import oauth.signpost.OAuthConsumer;
import oauth.signpost.commonshttp.CommonsHttpOAuthConsumer;
import oauth.signpost.exception.OAuthCommunicationException;
import oauth.signpost.exception.OAuthExpectationFailedException;
import oauth.signpost.exception.OAuthMessageSignerException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"resource", "unused"})
public class X {
    private final String consumerKey;
    private final String apiKey;
    public X(String consumerKey, String apiKey) {
        this.consumerKey = consumerKey;
        this.apiKey = apiKey;
    }
    public String createRefreshToken(String code, String verifier) throws Exception {
        BufferedReader rd = getBufferedReader(code, verifier);
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        System.out.println(result);
        return result.substring(result.indexOf("oauth_token_secret="+19, result.indexOf("&", result.indexOf("oauth_token_secret="+19)))) + "&" + result.substring(result.indexOf("oauth_token="+12, result.indexOf("&", result.indexOf("oauth_token="+12))));
    }

    @NotNull
    private BufferedReader getBufferedReader(String code, String verifier) throws OAuthMessageSignerException, OAuthExpectationFailedException, OAuthCommunicationException, IOException {
        OAuthConsumer consumer = new CommonsHttpOAuthConsumer(consumerKey, apiKey);
        HttpClient httpClient = new DefaultHttpClient();
        String VERIFY_TOKEN_URL = "https://api.twitter.com/oauth/access_token";
        HttpGet request = new HttpGet(VERIFY_TOKEN_URL + "?oauth_token=" + code + "&oauth_verifier=" + verifier);
        consumer.sign(request);
        HttpResponse response = httpClient.execute(request);
        return new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
    }

    public String generateAuthorizationUrl() throws Exception {
        OAuthConsumer consumer = new CommonsHttpOAuthConsumer(consumerKey, apiKey);
        HttpClient httpClient = new DefaultHttpClient();
        String REQUEST_TOKEN_URL = "https://api.twitter.com/oauth/request_token";
        HttpGet request = new HttpGet(REQUEST_TOKEN_URL);
        consumer.sign(request);
        HttpResponse response = httpClient.execute(request);
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        return "https://api.twitter.com/oauth/authorize?" + result;
    }
    private String buildAuthorizationHeader(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder header = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getKey().startsWith("oauth_")) {
                header.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString())).append("=\"")
                        .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString())).append("\",");
            }
        }
        return header.substring(0, header.length() - 1); // Remove trailing comma
    }

    private String encodeParams(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8.toString())).append("=")
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8.toString())).append("&");
        }
        return sb.substring(0, sb.length() - 1); // Remove trailing ampersand
    }
    private Map<String, String> parseKeyValueResponse(String response) throws UnsupportedEncodingException {
        Map<String, String> responseMap = new HashMap<>();
        String[] keyValuePairs = response.split("&"); // Split by ampersands (&)

        for (String keyValuePair : keyValuePairs) {
            String[] keyValue = keyValuePair.split("="); // Split each pair by equals sign (=)
            if (keyValue.length == 2) { // Ensure we have a valid key-value pair
                responseMap.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.toString()), URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.toString()));
            } else {
                // Handle invalid key-value pairs (optional: throw an exception or log a warning)
                System.err.println("Warning: Invalid key-value pair encountered in response: " + keyValuePair);
            }
        }

        return responseMap;
    }
}
