package com.nadaess.notaloneanymore.entity;

import net.minecraft.server.level.ServerPlayer;
import java.util.Map;
import java.util.UUID;

/**
 * Интерфейс ИИ-компаньона. Заменил старый DialogAgent (был привязан к Villager).
 * Реализуется CompanionEntity напрямую, а не через миксин.
 */
public interface CompanionAgent {
    int companion$startDialog(UUID playerUuid);
    void companion$executeComplexAction(String nav, String targetType, String anim, String emo, ServerPlayer player);

    String companion$getNavState();
    String companion$getAnimState();
    String companion$getTargetName();

    void companion$addMessageToHistory(String role, String content);
    String companion$getChatHistoryForPrompt();

    void companion$updateLongTermMemory(String fact);
    String companion$getLongTermMemory();

    String companion$getRamMemory();
    void companion$updateRamMemory(String newRam);

    int companion$getInertia();
    void companion$setInertia(int value);

    Map<String, Integer> companion$getGenome();
    void companion$setGene(String geneName, int value);

    Map<String, Integer> companion$getNeeds();
    void companion$setNeed(String needName, int value);

    void companion$triggerReactiveEvent(String eventType, String description);

    CompanionAiState companion$getAiState();
}
