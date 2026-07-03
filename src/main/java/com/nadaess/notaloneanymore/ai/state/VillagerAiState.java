package com.nadaess.notaloneanymore.ai.state;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Выделенный слой данных состояния ИИ жителя.
 * Содержит геном, потребности, память, состояния навигации/анимации,
 * историю диалога и таймеры.
 */
public class VillagerAiState {

    private static final Logger LOGGER = LoggerFactory.getLogger("notaloneanymore");

    // ===== Диалоговые поля =====
    private UUID dialogTargetUuid = null;
    private int dialogTicksLeft = 0;
    private int currentMaxDialogSeconds = 25;
    private boolean isInActiveConversation = false;

    // ===== Навигация и анимация =====
    private String navState = "none";
    private String animState = "none";
    private LivingEntity targetEntity = null;

    // ===== Инфраструктура очереди Voyager-лайк действий =====
    private final java.util.Queue<com.nadaess.notaloneanymore.ai.brain.tasks.AtomicAction> actionQueue = new java.util.LinkedList<>();
    private boolean actionInitialized = false;
    private int actionTimer = 0;

    // ===== Таймеры ИИ =====
    private int autonomousCooldown = 240;
    private int reactiveCooldown = 0;

    // ===== Память =====
    private final List<String> chatHistory = new ArrayList<>();
    private String longTermMemory = "Ничего примечательного не помню. Отношение к игрокам нейтральное.";
    private String ramMemory = "Наблюдаю за обстановкой вокруг.";
    private int cognitiveInertia = 50;

    // ===== Геном и потребности =====
    private final Map<String, Integer> genome = new HashMap<>();
    private final Map<String, Integer> needs = new HashMap<>();

    // ===== Новые поля для технических задач =====
    private net.minecraft.core.BlockPos targetPos = null;
    private String currentAction = "none";
    private net.minecraft.world.level.block.state.BlockState targetBlockState = null;

    // ===== Флаг инициализации =====
    private boolean initialized = false;

    /**
     * Инициализация генома и потребностей жителя на основе UUID.
     */
    public void initDefaultStats(Villager villager) {
        if (initialized) return;
        initialized = true;

        Random rng = new Random(villager.getUUID().getMostSignificantBits());

        String[] genes = {"moral", "empathy", "stubborn", "aggression", "industry", "greed",
                "extraversion", "bravery", "curiosity", "intellect", "paranoia",
                "machiavellianism", "narcissism", "sociability", "gossip"};
        for (String g : genes) {
            genome.putIfAbsent(g, rng.nextInt(101));
        }

        needs.putIfAbsent("hunger", 10 + rng.nextInt(20));
        needs.putIfAbsent("fatigue", 5 + rng.nextInt(25));
        needs.putIfAbsent("social", 50 + rng.nextInt(30));
        needs.putIfAbsent("finance", 20 + rng.nextInt(40));

        LOGGER.info("[ИИ ЛОГ] Сгенерирован научный геном для жителя {} ({})",
                villager.getName().getString(), villager.getUUID());
    }

    /**
     * Тик физиологических потребностей (голод, усталость, социализация).
     * Вызывается раз в 1200 тиков (1 минута).
     */
    public void tickNeeds() {
        needs.put("hunger", Math.min(100, needs.get("hunger") + 2));
        needs.put("fatigue", Math.min(100, needs.get("fatigue") + 1));
        needs.put("social", Math.max(0, needs.get("social") - 3));
    }

    // ========================================================================
    // DialogAgent — делегируемые методы
    // ========================================================================

    public int startDialog(UUID playerUuid) {
        this.dialogTargetUuid = playerUuid;
        if (!this.isInActiveConversation) {
            this.currentMaxDialogSeconds = 25;
            this.isInActiveConversation = true;
        } else {
            this.currentMaxDialogSeconds += 1;
        }
        this.dialogTicksLeft = this.currentMaxDialogSeconds * 20;
        return this.currentMaxDialogSeconds;
    }

    public void addMessageToHistory(String role, String content) {
        if (this.chatHistory.size() > 8) this.chatHistory.remove(0);
        this.chatHistory.add(role + ": " + content);
    }

    public String getChatHistoryForPrompt() {
        return this.chatHistory.isEmpty() ? "Разговор начат." : String.join("\n", this.chatHistory);
    }

    public void updateLongTermMemory(String fact) {
        if (fact != null && !fact.equalsIgnoreCase("none")) this.longTermMemory = fact;
    }

    public String getLongTermMemory() { return this.longTermMemory; }
    public String getRamMemory() { return this.ramMemory; }
    public void updateRamMemory(String newRam) { this.ramMemory = newRam; }
    public int getInertia() { return this.cognitiveInertia; }
    public void setInertia(int value) { this.cognitiveInertia = value; }
    public Map<String, Integer> getGenome() { return this.genome; }
    public void setGene(String geneName, int value) { this.genome.put(geneName, value); }
    public Map<String, Integer> getNeeds() { return this.needs; }
    public void setNeed(String needName, int value) { this.needs.put(needName, value); }
    public String getNavState() { return this.navState; }
    public void setNavState(String navState) { this.navState = navState; }
    public String getAnimState() { return this.animState; }
    public void setAnimState(String animState) { this.animState = animState; }
    public LivingEntity getTargetEntity() { return this.targetEntity; }
    public void setTargetEntity(LivingEntity entity) { this.targetEntity = entity; }

    public UUID getDialogTargetUuid() { return dialogTargetUuid; }
    public void setDialogTargetUuid(UUID uuid) { this.dialogTargetUuid = uuid; }
    public int getDialogTicksLeft() { return dialogTicksLeft; }
    public void setDialogTicksLeft(int ticks) { this.dialogTicksLeft = ticks; }
    public int getCurrentMaxDialogSeconds() { return currentMaxDialogSeconds; }
    public void setCurrentMaxDialogSeconds(int seconds) { this.currentMaxDialogSeconds = seconds; }
    public boolean isInActiveConversation() { return isInActiveConversation; }
    public void setInActiveConversation(boolean value) { this.isInActiveConversation = value; }
    public int getActionTimer() { return actionTimer; }
    public void setActionTimer(int ticks) { this.actionTimer = ticks; }

    // ===== Методы для работы с очередью действий =====
    public java.util.Queue<com.nadaess.notaloneanymore.ai.brain.tasks.AtomicAction> getActionQueue() {
        return this.actionQueue;
    }

    public void setActionSequence(java.util.List<com.nadaess.notaloneanymore.ai.brain.tasks.AtomicAction> actions) {
        this.actionQueue.clear();
        this.actionQueue.addAll(actions);
        this.actionInitialized = false;
        this.actionTimer = 0;
    }

    public boolean isActionInitialized() { return this.actionInitialized; }
    public void setActionInitialized(boolean initialized) { this.actionInitialized = initialized; }

    public int getAutonomousCooldown() { return autonomousCooldown; }
    public void setAutonomousCooldown(int cooldown) { this.autonomousCooldown = cooldown; }
    public int getReactiveCooldown() { return reactiveCooldown; }
    public void setReactiveCooldown(int cooldown) { this.reactiveCooldown = cooldown; }
    public List<String> getChatHistory() { return chatHistory; }
    public boolean isInitialized() { return initialized; }
    public void setInitialized(boolean value) { this.initialized = value; }

    // ===== Геттеры/сеттеры для технических задач =====
    public net.minecraft.core.BlockPos getTargetPos() { return this.targetPos; }
    public void setTargetPos(net.minecraft.core.BlockPos pos) { this.targetPos = pos; }

    public String getCurrentAction() { return this.currentAction; }
    public void setCurrentAction(String action) { this.currentAction = action; }

    public net.minecraft.world.level.block.state.BlockState getTargetBlockState() { return this.targetBlockState; }
    public void setTargetBlockState(net.minecraft.world.level.block.state.BlockState state) { this.targetBlockState = state; }

    /**
     * Получение имени цели для отладки.
     */
    public String getTargetName() {
        return this.targetEntity != null
                ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(this.targetEntity.getType()).getPath()
                : "none";
    }

    // ========================================================================
    // NBT — сохранение и загрузка
    // ========================================================================

    public void saveToNbt(net.minecraft.world.level.storage.ValueOutput output) {
        output.putString("RamMemory", this.ramMemory);
        output.putString("LongTermMemory", this.longTermMemory);
        output.putInt("CognitiveInertia", this.cognitiveInertia);

        for (Map.Entry<String, Integer> entry : genome.entrySet())
            output.putInt("Gene_" + entry.getKey(), entry.getValue());

        for (Map.Entry<String, Integer> entry : needs.entrySet())
            output.putInt("Need_" + entry.getKey(), entry.getValue());
    }

    public void loadFromNbt(net.minecraft.world.level.storage.ValueInput input) {
        input.getString("RamMemory").ifPresent(val -> this.ramMemory = val);
        input.getString("LongTermMemory").ifPresent(val -> this.longTermMemory = val);
        input.getInt("CognitiveInertia").ifPresent(val -> this.cognitiveInertia = val);

        String[] genes = {"moral", "empathy", "stubborn", "aggression", "industry", "greed",
                "extraversion", "bravery", "curiosity", "intellect", "paranoia",
                "machiavellianism", "narcissism", "sociability", "gossip"};
        for (String g : genes) {
            final String geneKey = g;
            input.getInt("Gene_" + g).ifPresent(val -> genome.put(geneKey, val));
        }

        String[] needsList = {"hunger", "fatigue", "social", "finance"};
        for (String n : needsList) {
            final String needKey = n;
            input.getInt("Need_" + n).ifPresent(val -> needs.put(needKey, val));
        }

        this.initialized = true;
    }
}
