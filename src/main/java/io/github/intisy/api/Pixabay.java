package io.github.intisy.api;

import org.json.JSONArray;
import org.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

@SuppressWarnings("unused")
public class Pixabay {
    String apiKey;
    public Pixabay(String apiKey) {
        this.apiKey = apiKey;
    }
    public File image(File outputFile, String query) throws IOException {
        PrintWriter writer = new PrintWriter(outputFile);

        URL url = new URL("https://pixabay.com/api/?key=" + apiKey + "&q=" + query);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            URL imageURL = getUrl(connection);
            BufferedImage image = ImageIO.read(imageURL);
            ImageIO.write(image, "png", outputFile);

            System.out.println("Image downloaded: " + outputFile);
        } else {
            writer.println("Error: " + responseCode);
        }

        writer.close();
        return outputFile;
    }

    private URL getUrl(HttpURLConnection connection) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }
        in.close();

        JSONObject jsonResponse = new JSONObject(response.toString());
        JSONArray hits = jsonResponse.getJSONArray("hits");

        JSONObject hit = hits.getJSONObject(0);
        String imageUrl = hit.getString("webformatURL");
        // Download image
        return new URL(imageUrl);
    }
}
