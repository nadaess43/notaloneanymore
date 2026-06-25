package com.nadaess.notaloneanymore;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {
    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "notaloneanymore.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String apiKey = "YOUR_API_KEY_HERE";
    public String apiUrl = "https://openrouter.ai/api/v1/chat/completions";
    public String modelName = "openai/gpt-oss-120b:free";
    public double aiTemperature = 0.5;

    public static ModConfig load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                return GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                Notaloneanymore.LOGGER.error("Не удалось загрузить конфиг мода, создаем дефолтный", e);
            }
        }
        ModConfig config = new ModConfig();
        config.save();
        return config;
    }

    public void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(this, writer);
        } catch (Exception e) {
            Notaloneanymore.LOGGER.error("Не удалось сохранить конфиг мода", e);
        }
    }
}