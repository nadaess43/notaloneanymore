package com.nadaess.notaloneanymore;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class DeepSeekClient {
    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    public static CompletableFuture<String> askAI(String systemPrompt, String userPrompt) {
        ModConfig directConfig = new ModConfig();
        if (directConfig.apiKey.equals("YOUR_API_KEY_HERE") || directConfig.apiKey.isEmpty()) {
            Notaloneanymore.LOGGER.warn("Ключ API не установлен в конфиге!");
            return CompletableFuture.completedFuture("{\"thought\": \"[Ошибка: Нет API ключа в конфиге]\"}");
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("model", directConfig.modelName);
        payload.addProperty("temperature", directConfig.aiTemperature);

        JsonObject responseFormat = new JsonObject();
        responseFormat.addProperty("type", "json_object");
        payload.add("response_format", responseFormat);

        JsonArray messages = new JsonArray();
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        systemMessage.addProperty("content", systemPrompt);

        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);

        messages.add(systemMessage);
        messages.add(userMessage);
        payload.add("messages", messages);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(directConfig.apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + directConfig.apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        String body = response.body();
                        Notaloneanymore.LOGGER.info("Ответ от OpenRouter получен. Статус: " + response.statusCode());

                        if (response.statusCode() != 200) {
                            Notaloneanymore.LOGGER.error("OpenRouter вернул ошибку: " + body);
                            return "{\"thought\": \"*глухо мычит* (Код ошибки: " + response.statusCode() + ")\"}";
                        }

                        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
                        JsonArray choices = root.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            if (message != null && message.has("content")) {
                                return message.get("content").getAsString();
                            }
                        }
                        return "{\"thought\": \"*задумался и промолчал*\"}";
                    } catch (Exception e) {
                        Notaloneanymore.LOGGER.error("Ошибка парсинга ответа OpenRouter: ", e);
                        return "{\"thought\": \"*потерял мысль*\"}";
                    }
                })
                .exceptionally(ex -> {
                    Notaloneanymore.LOGGER.error("Критическая ошибка сети при запросе: ", ex);
                    return "{\"thought\": \"*не может связаться с космосом*\"}";
                });
    }
}