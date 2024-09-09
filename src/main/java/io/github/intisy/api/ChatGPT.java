package io.github.intisy.api;

import com.google.gson.Gson;
import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.Database;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ChatGPT {
    String apiKey;
    ChatGPT(String apiKey) {
        this.apiKey = apiKey;
    }
    public String prompt(Database database, String prompt) {
        List<String> prompts = database.quickSelectData("server", "value", "token", "prompts");
        JSONArray messageList;
        if (prompts.isEmpty()) {
            messageList = new JSONArray();
        } else {
            messageList = new JSONArray(prompts.get(0));
        }
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messageList.put(message);

        // Build input and API key params
        JSONObject payload = new JSONObject();

        payload.put("model", "gpt-3.5-turbo"); // model is important
        payload.put("messages", messageList);
        payload.put("temperature", 0.9);
        payload.put("top_p", 0.5); //experimental

        StringEntity inputEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);

        // Build POST request
        HttpPost post = new HttpPost("https://api.openai.com/v1/chat/completions");
        post.setEntity(inputEntity);
        post.setHeader("Authorization", "Bearer " + apiKey);
        post.setHeader("Content-Type", "application/json");

        // Send POST request and parse response
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            HttpEntity resEntity = response.getEntity();
            String resJsonString;
            try (InputStream inputStream = resEntity.getContent()) {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096]; // Adjust buffer size as needed
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                byte[] contentBytes = outputStream.toByteArray();
                resJsonString = new String(contentBytes, StandardCharsets.UTF_8.toString());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            JSONObject resJson = new JSONObject(resJsonString);

            if (resJson.has("error")) {
                String errorMsg = resJson.getString("error");
                StaticLogger.error("Chatbot API error: " + errorMsg);
                return "Error: " + errorMsg;
            }

            // Parse JSON response
            JSONArray responseArray = resJson.getJSONArray("choices");
            List<String> responseList = new ArrayList<>();

            for (int i = 0; i < responseArray.length(); i++) {
                JSONObject responseObj = responseArray.getJSONObject(i);
                String responseString = responseObj.getJSONObject("message").getString("content");
                responseList.add(responseString);
            }

            // Convert response list to JSON and return it
            Gson gson = new Gson();
            String jsonResponse = gson.toJson(responseList);
            jsonResponse = jsonResponse.substring(2, jsonResponse.length()-2).replace("\\\\", "\\").replace("\\\"", "\"");
            message = new JSONObject();
            message.put("role", "assistant");
            message.put("content", jsonResponse);
            messageList.put(message);
            if (database.quickSelectData("server", "token", "token", "prompts").isEmpty())
                database.insertData("server", "token", "prompts", "value", messageList.toString());
            else
                database.updateData("server", "token", "prompts", "value", messageList.toString());
            return jsonResponse;
        } catch (IOException | JSONException e) {
            StaticLogger.error("Error sending request:" + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
