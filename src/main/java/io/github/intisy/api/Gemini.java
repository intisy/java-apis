package io.github.intisy.api;

import com.google.cloud.vertexai.VertexAI;
import com.google.cloud.vertexai.api.*;
import com.google.cloud.vertexai.generativeai.ChatSession;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.gson.*;
import io.github.intisy.simple.logger.StaticLogger;
import io.github.intisy.utils.custom.Database;
import io.github.intisy.utils.utils.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Gemini {
    public static String projectId = "blizzity";
    public static String location = "europe-west3";
    public static String modelName = "gemini-1.5-pro-preview-0409";
    public static ChatSession chatSession = null;
    public static void env() throws IOException {
        String os = System.getProperty("os.name");
        String command;
        if (os.toLowerCase().contains("windows")) {
            command = "cmd.exe /c setx GOOGLE_APPLICATION_CREDENTIALS \"" + new File("com/github/WildePizza/api/service_account.json").getAbsolutePath() + "\"";
        } else {
            command = "echo 'export VARIABLE_NAME=\"" + new File("com/github/WildePizza/api/service_account.json").getAbsolutePath() + "\"' >> ~/.bashrc";
        }
        ProcessBuilder builder = new ProcessBuilder(command.split(" "));
        builder.redirectErrorStream(true);
        watch(builder.start());
    }
    public static String prompt(Database database, String prompt) throws IOException {
        if (chatSession == null) {
            try (VertexAI vertexAI = new VertexAI(projectId, location)) {
                GenerationConfig generationConfig =
                        GenerationConfig.newBuilder()
                                .setMaxOutputTokens(8192)
                                .setTemperature(1F)
                                .setTopP(0.95F)
                                .build();
                List<SafetySetting> safetySettings = Arrays.asList(
                        SafetySetting.newBuilder()
                                .setCategory(HarmCategory.HARM_CATEGORY_HATE_SPEECH)
                                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE)
                                .build(),
                        SafetySetting.newBuilder()
                                .setCategory(HarmCategory.HARM_CATEGORY_DANGEROUS_CONTENT)
                                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE)
                                .build(),
                        SafetySetting.newBuilder()
                                .setCategory(HarmCategory.HARM_CATEGORY_SEXUALLY_EXPLICIT)
                                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE)
                                .build(),
                        SafetySetting.newBuilder()
                                .setCategory(HarmCategory.HARM_CATEGORY_HARASSMENT)
                                .setThreshold(SafetySetting.HarmBlockThreshold.BLOCK_MEDIUM_AND_ABOVE)
                                .build()
                );
                GenerativeModel model =
                        new GenerativeModel.Builder()
                                .setModelName(modelName)
                                .setVertexAi(vertexAI)
                                .setGenerationConfig(generationConfig)
                                .setSafetySettings(safetySettings)
                                .build();
                chatSession = new ChatSession(model);
            } catch (Exception e) {
                StaticLogger.exception(e);
                env();
                return prompt(database, prompt);
            }
        }
        List<String> prompts = database.quickSelectData("server", "value", "token", "prompts");
        if (!prompts.isEmpty()) {
            List<Content> contentList = new ArrayList<>();
            JsonArray jsonArray = JsonParser.parseString(prompts.get(0)).getAsJsonArray();
            if (jsonArray.size() > 20) {
                jsonArray.remove(0);
                jsonArray.remove(1);
            }
            String role = "";
            for (JsonElement content : jsonArray) {
                String value = content.getAsString();
                while (value.contains("\\\\"))
                    value = value.replace("\\\\", "\\");
                while (value.contains("\\n"))
                    value = value.replace("\\n", "\n");
                while (value.contains("\n\n"))
                    value = value.replace("\n\n", "\n");
                Content.Builder contentBuilder = Content.newBuilder();
                String a = StringUtils.value(value, "role: \"");
                if (role.isEmpty() && a.equals("model"))
                    continue;
                else if (role.equals(a))
                    continue;
                else
                    role = a;
                contentBuilder.setRole(role);
                contentBuilder.addParts(Part.newBuilder().setText(StringUtils.value(value, "text: \"")).build());
                contentList.add(contentBuilder.build());
            }
            chatSession.setHistory(contentList);
        }
        GenerateContentResponse response = chatSession.sendMessage(prompt);
        JsonArray contentArray = new JsonArray();
        for (Content content : chatSession.getHistory()) {
            contentArray.add(content.toString().replace("\\\\", "\\"));
        }
        if (database.quickSelectData("server", "token", "token", "prompts").isEmpty())
            database.insertData("server", "token", "prompts", "value", contentArray.toString());
        else
            database.updateData("server", "token", "prompts", "value", contentArray.toString());
        String[] texts = response.toString().split("text: \"");
        String text = texts[texts.length - 1];
        return text.substring(0, text.indexOf("\"\n    }"));
    }
    private static String watch(final Process process) {
        StringBuilder string = new StringBuilder();
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        BufferedReader input = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        try {
            while ((line = input.readLine()) != null) {
                string.append(line);
            }
        } catch (IOException e) {
            StaticLogger.exception(e);
        }
        return string.toString();
    }
}