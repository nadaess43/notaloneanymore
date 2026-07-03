package com.nadaess.notaloneanymore.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nadaess.notaloneanymore.DeepSeekClient;
import com.nadaess.notaloneanymore.Notaloneanymore;
import com.nadaess.notaloneanymore.ai.brain.VillagerBrainProvider;
import com.nadaess.notaloneanymore.ai.brain.tasks.AutonomousThoughtTask;
import com.nadaess.notaloneanymore.ai.state.VillagerAiState;
import com.nadaess.notaloneanymore.util.DialogAgent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.tslat.smartbrainlib.api.SmartBrainOwner;
import net.tslat.smartbrainlib.api.core.ActivityBuilder;
import net.tslat.smartbrainlib.api.core.sensor.ExtendedSensor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Рефакторинговый миксин для {@link Villager}.
 *
 * <ul>
 *   <li>Хранит только одну {@link Unique} переменную: {@link #aiState}</li>
 *   <li>Имплементирует {@link SmartBrainOwner} для интеграции SmartBrainLib</li>
 *   <li>Делегирует все методы {@link DialogAgent} в {@link VillagerAiState}</li>
 *   <li>onTick() очищен — управление через SBL-задачи</li>
 * </ul>
 *
 * <p>Внимание: из-за CRTP-ограничения {@code SmartBrainOwner<BO extends LivingEntity & SmartBrainOwner<BO>>}
 * компилятор не может проверить bound для {@link Villager} (который получает интерфейс через миксин).
 * Используем raw type с подавлением предупреждений.</p>
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Mixin(Villager.class)
public abstract class VillagerMixin implements DialogAgent, SmartBrainOwner,
        AutonomousThoughtTask.AiStateProvider {

    // ========================================================================
    // ЕДИНСТВЕННОЕ ПОЛЕ — выделенный слой данных
    // ========================================================================
    @Unique
    private final VillagerAiState aiState = new VillagerAiState();

    @Override
    public VillagerAiState getAiState() {
        return aiState;
    }

    // ========================================================================
    // SmartBrainOwner — конфигурация SBL
    // ========================================================================

    @Unique @Override
    public List<? extends ExtendedSensor<?>> getSensors(LivingEntity entity) {
        return VillagerBrainProvider.getSensors();
    }

    @Unique @Override
    public List<? extends net.minecraft.world.entity.ai.behavior.BehaviorControl<?>> getAlwaysRunningBehaviours(LivingEntity entity) {
        return VillagerBrainProvider.getAlwaysRunningBehaviours();
    }

    @Unique @Override
    public ActivityBuilder getCoreBehaviourGroup(LivingEntity entity) {
        return VillagerBrainProvider.getCoreBehaviourGroup();
    }

    @Unique @Override
    public ActivityBuilder getIdleBehaviourGroup(LivingEntity entity) {
        return VillagerBrainProvider.getIdleBehaviourGroup();
    }

    @Unique @Override
    public List<ActivityBuilder> getAdditionalActivities(LivingEntity entity, Activity[] activites) {
        return List.of(
                VillagerBrainProvider.getWorkBehaviourGroup(),
                VillagerBrainProvider.getMeetBehaviourGroup(),
                VillagerBrainProvider.getRestBehaviourGroup(),
                VillagerBrainProvider.getFightBehaviourGroup(),
                VillagerBrainProvider.getPanicBehaviourGroup(),
                VillagerBrainProvider.getAvoidBehaviourGroup(),
                VillagerBrainProvider.getHideBehaviourGroup()
        );
    }

    @Unique @Override
    public Set<Activity> getAlwaysRunningActivities(LivingEntity entity) {
        return VillagerBrainProvider.getAlwaysRunningActivities();
    }

    @Unique @Override
    public Activity getDefaultActivity(LivingEntity entity) {
        return VillagerBrainProvider.getDefaultActivity();
    }

    @Unique @Override
    public Activity[] getActivityActivationPriority() {
        return VillagerBrainProvider.getActivityActivationPriority();
    }

    // ========================================================================
    // onTick — ПОЛНОСТЬЮ ОЧИЩЕН. SBL управляет тиками через задачи.
    // ========================================================================

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        // Пусто: вся логика теперь в AutonomousThoughtTask.tick()
        // и ExecuteAiOrderTask.tick(), которые запускаются SBL.
    }

    // ========================================================================
    // NBT — сохранение и загрузка через VillagerAiState
    // ========================================================================

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onWriteNbt(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        aiState.saveToNbt(output);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadNbt(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        aiState.loadFromNbt(input);
    }

    // ========================================================================
    // DialogAgent — делегирующие методы
    // ========================================================================

    @Override @Unique
    public int notAlone$startDialog(UUID playerUuid) {
        return aiState.startDialog(playerUuid);
    }

    @Override @Unique
    public void notAlone$addMessageToHistory(String role, String content) {
        aiState.addMessageToHistory(role, content);
    }

    @Override @Unique
    public String notAlone$getChatHistoryForPrompt() {
        return aiState.getChatHistoryForPrompt();
    }

    @Override @Unique
    public void notAlone$updateLongTermMemory(String fact) {
        aiState.updateLongTermMemory(fact);
    }

    @Override @Unique
    public String notAlone$getLongTermMemory() {
        return aiState.getLongTermMemory();
    }

    @Override @Unique
    public String notAlone$getRamMemory() {
        return aiState.getRamMemory();
    }

    @Override @Unique
    public void notAlone$updateRamMemory(String newRam) {
        aiState.updateRamMemory(newRam);
    }

    @Override @Unique
    public int notAlone$getInertia() {
        return aiState.getInertia();
    }

    @Override @Unique
    public void notAlone$setInertia(int value) {
        aiState.setInertia(value);
    }

    @Override @Unique
    public Map<String, Integer> notAlone$getGenome() {
        return aiState.getGenome();
    }

    @Override @Unique
    public void notAlone$setGene(String geneName, int value) {
        aiState.setGene(geneName, value);
    }

    @Override @Unique
    public Map<String, Integer> notAlone$getNeeds() {
        return aiState.getNeeds();
    }

    @Override @Unique
    public void notAlone$setNeed(String needName, int value) {
        aiState.setNeed(needName, value);
    }

    @Override @Unique
    public String notAlone$getNavState() {
        return aiState.getNavState();
    }

    @Override @Unique
    public String notAlone$getAnimState() {
        return aiState.getAnimState();
    }

    @Override @Unique
    public String notAlone$getTargetName() {
        return aiState.getTargetName();
    }

    // ========================================================================
    // executeComplexAction — вызывается из ServerChatMixin и MindCommand
    // Устанавливает состояние, спавнит частицы, запускает анимации
    // ========================================================================

    @Override @Unique
    public void notAlone$executeComplexAction(String nav, String targetType, String anim, String emo, ServerPlayer player) {
        Villager villager = (Villager) (Object) this;
        ServerLevel level = (ServerLevel) villager.level();

        aiState.setNavState(nav);
        aiState.setAnimState(anim);
        aiState.setActionTimer(140);

        // Частицы эмоций
        if (emo.equals("happy")) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    villager.getX(), villager.getY() + 2, villager.getZ(), 5, 0.3, 0.3, 0.3, 0);
        } else if (emo.equals("angry")) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    villager.getX(), villager.getY() + 2, villager.getZ(), 4, 0.2, 0.2, 0.2, 0);
        }

        // Анимации
        if (anim.equalsIgnoreCase("sleep")) {
            villager.startSleeping(villager.blockPosition());
        } else if (anim.equalsIgnoreCase("wake")) {
            villager.stopSleeping();
        } else if (anim.equalsIgnoreCase("jump")) {
            villager.getJumpControl().jump();
            villager.setDeltaMovement(villager.getDeltaMovement().add(0, 0.5, 0));
        } else if (anim.equalsIgnoreCase("work")) {
            level.broadcastEntityEvent(villager, (byte) 15);
        }

        // Установка цели
        if (!targetType.equals("none")) {
            if (targetType.equals("player") && player != null) {
                aiState.setTargetEntity(player);
            } else {
                List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class, villager.getBoundingBox().inflate(12.0),
                        e -> e != villager && (
                                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equalsIgnoreCase(targetType)
                                        || (targetType.equals("monster") && e instanceof Enemy)
                        ));
                if (!targets.isEmpty()) {
                    aiState.setTargetEntity(targets.stream()
                            .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(villager)))
                            .get());
                }
            }
        }

        // Запуск навигации
        if ((nav.equals("go_to_target") || nav.equals("follow") || nav.equals("flee")) && aiState.getTargetEntity() != null) {
            villager.getNavigation().moveTo(aiState.getTargetEntity(), nav.equals("flee") ? 0.8 : 0.5);
        } else {
            villager.getNavigation().stop();
        }
    }

    // ========================================================================
    // triggerReactiveEvent — вызывается из Notaloneanymore (блоки/смерти) и MindCommand
    // Асинхронно вызывает DeepSeek и обновляет состояние
    // ========================================================================

    @Override @Unique
    public void notAlone$triggerReactiveEvent(String eventType, String description) {
        Villager villager = (Villager) (Object) this;
        ServerLevel world = (ServerLevel) villager.level();

        if (aiState.getReactiveCooldown() > 0) return;
        aiState.setReactiveCooldown(100);

        Notaloneanymore.LOGGER.warn("[ИИ ЛОГ] [РЕАКТИВНЫЙ ТРИГГЕР] {} заметил: {} - {}",
                villager.getName().getString(), eventType, description);

        aiState.updateRamMemory("[КРИТИЧЕСКОЕ СОБЫТИЕ]: " + description);

        String systemPrompt = String.format(
                "КРИТИЧЕСКОЕ СОБЫТИЕ! Произошло: [%s] -> %s.\n" +
                        "Ты житель %s. Память: \"%s\".\n" +
                        "В поле 'thought' напиши свою КРИЧАЩУЮ РЕЧЬ персонажем.\n" +
                        "Верни строго JSON:\n" +
                        "{\n" +
                        "  \"thought\": \"What you scream out loud!\",\n" +
                        "  \"navigation\": \"none/wander/work/home/flee/go_to_target/follow\",\n" +
                        "  \"target\": \"none\",\n" +
                        "  \"animation\": \"none/panic/inspect/sleep/work/jump\",\n" +
                        "  \"emotion\": \"neutral/happy/sad/angry\",\n" +
                        "  \"new_fact\": \"none\"\n" +
                        "}",
                eventType, description, villager.getName().getString(),
                aiState.getLongTermMemory()
        );

        DeepSeekClient.askAI(systemPrompt, "[Триггер " + eventType + "]").thenAccept(response -> {
            world.getServer().execute(() -> {
                try {
                    if (!response.contains("{") || !response.contains("}")) return;

                    String clean = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
                    clean = clean.replace("\"navigation\"", " \"navigation\"")
                            .replace("\"target\"", " \"target\"");

                    JsonObject json = JsonParser.parseString(clean).getAsJsonObject();

                    String thought = json.has("thought") ? json.get("thought").getAsString() : "...";
                    String nav = json.has("navigation") ? json.get("navigation").getAsString() : "none";
                    String anim = json.has("animation") ? json.get("animation").getAsString() : "none";
                    String emo = json.has("emotion") ? json.get("emotion").getAsString() : "neutral";

                    if (json.has("new_fact")) {
                        aiState.updateLongTermMemory(json.get("new_fact").getAsString());
                    }

                    nav = AutonomousThoughtTask.normalizeNav(nav);

                    String professionKey = BuiltInRegistries.VILLAGER_PROFESSION
                            .getKey(villager.getVillagerData().profession().value()).getPath();

                    Component chatMessage;
                    if (Notaloneanymore.showThoughtsInChat) {
                        String color = (eventType.startsWith("VANDALISM")) ? "§c" : "§e";
                        String tag = (eventType.startsWith("VANDALISM"))
                                ? " РЕАГИРУЕТ]: §o"
                                : " ЗАМЕТИЛ]: §o";
                        chatMessage = Component.literal(color + "["
                                + villager.getName().getString() + " (" + professionKey + ")" + tag + thought);
                    } else {
                        chatMessage = Component.literal("§f"
                                + villager.getName().getString() + ": " + thought);
                    }

                    for (ServerPlayer p : world.players()) {
                        if (p.distanceToSqr(villager) < 576.0) {
                            p.sendSystemMessage(chatMessage);
                        }
                    }

                    // Применяем состояние
                    aiState.setNavState(nav);
                    aiState.setAnimState(anim);
                    aiState.setActionTimer(160);
                    if (nav.equals("flee")) {
                        aiState.setTargetEntity(world.getNearestPlayer(villager, 15.0));
                    }
                    this.notAlone$executeComplexAction(nav, "none", anim, emo, null);

                } catch (Exception e) {
                    Notaloneanymore.LOGGER.error("Ошибка реактивного парсинга JSON: {}", e.getMessage());
                }
            });
        });
    }
}
