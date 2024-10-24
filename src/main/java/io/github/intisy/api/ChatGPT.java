package io.github.intisy.api;

import com.google.gson.Gson;
import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.SQL;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class ChatGPT {
    String apiKey;
    ChatGPT(String apiKey) {
        this.apiKey = apiKey;
    }
    public JSONArray prompt(String prompt) {
        return prompt(new JSONArray(), prompt);
    }
    public JSONArray prompt(JSONArray messageList, String prompt) {
        JSONObject message = new JSONObject();
        message.put("role", "user");
        message.put("content", prompt);
        messageList.put(message);
        JSONObject payload = new JSONObject();
        payload.put("model", "gpt-4o");
        payload.put("messages", messageList);
        payload.put("temperature", 2);
        payload.put("max_tokens", 16383);
        payload.put("top_p", 1);
        payload.put("frequency_penalty", 2);
        payload.put("presence_penalty", 0);
        StringEntity inputEntity = new StringEntity(payload.toString(), ContentType.APPLICATION_JSON);
        HttpPost post = new HttpPost("https://api.openai.com/v1/chat/completions");
        post.setEntity(inputEntity);
        post.setHeader("Authorization", "Bearer " + apiKey);
        post.setHeader("Content-Type", "application/json");
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {
            JSONObject resJson = getJsonObject(response);
            if (resJson.has("error")) {
                String errorMsg = resJson.getString("error");
                StaticLogger.error("Chatbot API error: " + errorMsg);
                return prompt(messageList, prompt);
            }
            JSONArray responseArray = resJson.getJSONArray("choices");
            List<String> responseList = new ArrayList<>();
            for (int i = 0; i < responseArray.length(); i++) {
                JSONObject responseObj = responseArray.getJSONObject(i);
                String responseString = responseObj.getJSONObject("message").getString("content");
                responseList.add(responseString);
            }
            Gson gson = new Gson();
            String jsonResponse = gson.toJson(responseList);
            jsonResponse = jsonResponse.substring(2, jsonResponse.length()-2).replace("\\\\", "\\").replace("\\\"", "\"");
            message = new JSONObject();
            message.put("role", "assistant");
            message.put("content", jsonResponse);
            messageList.put(message);
            return messageList;
        } catch (IOException | JSONException e) {
            StaticLogger.error("Error sending request:" + e.getMessage());
            return prompt(messageList, prompt);
        }
    }

    @NotNull
    private static JSONObject getJsonObject(CloseableHttpResponse response) {
        HttpEntity resEntity = response.getEntity();
        String resJsonString;
        try (InputStream inputStream = resEntity.getContent()) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096]; // Adjust buffer size as needed
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            resJsonString = outputStream.toString(StandardCharsets.UTF_8.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new JSONObject(resJsonString);
    }
}