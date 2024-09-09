package io.github.intisy.api;

import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.utils.ConnectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

public class Elevenlabs {
    String apiKey;
    public Elevenlabs(String apiKey) {
        this.apiKey = apiKey;
    }
    public File tts(File output, String text) throws InterruptedException {
        StaticLogger.note("Getting tts from: " + text);
        if (!output.exists()) {
            String jsonBody = "{\n  \"text\": \"" + text + "\",\n  \"voice_settings\": {\n    \"similarity_boost\": 1,\n    \"stability\": 1\n  },\n  \"model_id\": \"eleven_multilingual_v1\"\n}";
            try {
                URL url = URI.create("https://api.elevenlabs.io/v1/text-to-speech/" + "pNInz6obpgDQGcFmaJgB").toURL();
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("xi-api-key", apiKey);
                connection.setDoOutput(true);
                byte[] bodyBytes = jsonBody.getBytes();
                connection.getOutputStream().write(bodyBytes);
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    // Write audio data directly to file
                    try (FileOutputStream fos = new FileOutputStream(output);
                         ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = connection.getInputStream().read(buffer)) != -1) {
                            bos.write(buffer, 0, bytesRead);
                        }
                        fos.write(bos.toByteArray());
                    }
                    StaticLogger.success("Wrote audio to file: " + output);
                } else {
                    throw new IOException("API request failed with status code: " + responseCode + " (" + ConnectionUtils.getOutput(connection) + ")");
                }
            } catch (IOException e) {
                StaticLogger.warning("TTS error occurred: " + e.getMessage());
                StaticLogger.warning(jsonBody);
                Thread.sleep(30000); // Retry after delay
                return tts(output, text);
            }
        }
        return output;
    }
}
