package com.nadaess.notaloneanymore.util;

import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;
import java.util.Map;

public interface DialogAgent {
    int notAlone$startDialog(UUID playerUuid);
    void notAlone$executeComplexAction(String nav, String targetType, String anim, String emo, ServerPlayer player);

    String notAlone$getNavState();
    String notAlone$getAnimState();
    String notAlone$getTargetName();

    void notAlone$addMessageToHistory(String role, String content);
    String notAlone$getChatHistoryForPrompt();

    void notAlone$updateLongTermMemory(String fact);
    String notAlone$getLongTermMemory();

    String notAlone$getRamMemory();
    void notAlone$updateRamMemory(String newRam);

    int notAlone$getInertia();
    void notAlone$setInertia(int value);

    Map<String, Integer> notAlone$getGenome();
    void notAlone$setGene(String geneName, int value);

    Map<String, Integer> notAlone$getNeeds();
    void notAlone$setNeed(String needName, int value);

    void notAlone$triggerReactiveEvent(String eventType, String description);
}