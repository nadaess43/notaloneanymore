package com.nadaess.notaloneanymore.ai.brain.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nadaess.notaloneanymore.DeepSeekClient;
import com.nadaess.notaloneanymore.Notaloneanymore;
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
import net.minecraft.world.phys.Vec3;
import net.tslat.smartbrainlib.api.core.behaviour.base.ExtendedBehaviour;

import java.util.List;
import java.util.Set;

/**
 * SBL-задача фонового автономного мышления жителя.
 * Каждые 240 тиков (12 секунд) вызывает DeepSeekClient.askAI(),
 * обрабатывает JSON-ответ и обновляет состояние (navState, animState, память).
 */
public class AutonomousThoughtTask extends ExtendedBehaviour<Villager> {

    /**
     * Интерфейс для доступа к {@link VillagerAiState} из SBL-задач.
     * Реализуется в {@code VillagerMixin}.
     */
    public interface AiStateProvider {
        VillagerAiState getAiState();
    }

    public AutonomousThoughtTask() {
        // Работаем без тайм-аута — таск активен постоянно
        noTimeout();
        // Стартуем сразу, без условий
        startCondition(villager -> true);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long gameTime) {
        if (!(villager instanceof AiStateProvider provider)) return;
        VillagerAiState state = provider.getAiState();

        // 1. ИНИЦИАЛИЗАЦИЯ ИНФРАСТРУКТУРЫ ДВИЖЕНИЯ
        // Передаем управление в наш технический исполнитель каждый тик
        ExecuteAiOrderTask.tickPhysicalMovement(level, villager, state);

        // Инициализация генома при первом тике
        if (!state.isInitialized()) {
            state.initDefaultStats(villager);
        }

        // Обновление физиологических потребностей (раз в минуту)
        if (villager.tickCount % 1200 == 0) {
            state.tickNeeds();
        }

        // Кулдаун реактивных событий
        if (state.getReactiveCooldown() > 0) {
            state.setReactiveCooldown(state.getReactiveCooldown() - 1);
        }

        // Обработка урона (реактивный триггер)
        if (villager.hurtTime > 0 && villager.tickCount % 15 == 0) {
            String attacker = villager.getLastHurtByMob() != null
                    ? BuiltInRegistries.ENTITY_TYPE.getKey(villager.getLastHurtByMob().getType()).getPath()
                    : "неизвестно";
            level.getServer().execute(() -> {
                if (villager instanceof DialogAgent agent) {
                    agent.notAlone$triggerReactiveEvent("DAMAGE", "Меня атаковали! Агрессор: " + attacker);
                }
            });
        }

        // Логирование состояния навигации (раз в секунду)
        if (villager.tickCount % 20 == 0 && !state.getNavState().equals("none")) {
            // Можно добавить более детальное логирование при необходимости
        }

        // Автономный цикл мышления
        if (state.isInActiveConversation() || state.getDialogTicksLeft() > 0) {
            state.setAutonomousCooldown(240);
        } else {
            int cooldown = state.getAutonomousCooldown() - 1;
            state.setAutonomousCooldown(cooldown);
            if (cooldown <= 0) {
                state.setAutonomousCooldown(240);
                triggerAutonomousThought(level, villager, state);
            }
        }

        // Таймер диалога
        if (state.getDialogTicksLeft() > 0) {
            state.setDialogTicksLeft(state.getDialogTicksLeft() - 1);
        }

        // Сброс диалога при истечении таймера
        if (state.isInActiveConversation() && state.getDialogTicksLeft() <= 0) {
            state.setCurrentMaxDialogSeconds(25);
            state.setInActiveConversation(false);
            state.setDialogTargetUuid(null);
            state.getChatHistory().clear();
        }
    }

    /**
     * Формирует снапшот окружения жителя.
     */
    private String buildEnvironmentSnapshot(Villager villager, ServerLevel world) {
        Vec3 myPos = villager.position();
        List<LivingEntity> entities = world.getEntitiesOfClass(
                LivingEntity.class, villager.getBoundingBox().inflate(15.0), e -> e != villager);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Моя позиция: [X:%.1f, Y:%.1f, Z:%.1f]. ", myPos.x, myPos.y, myPos.z));
        if (entities.isEmpty()) {
            builder.append("Рядом никого нет.");
        } else {
            builder.append("Объекты рядом: ");
            for (LivingEntity entity : entities) {
                String name = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                if (entity instanceof net.minecraft.world.entity.player.Player) {
                    name = "player_" + entity.getName().getString();
                }
                double dx = entity.getX() - myPos.x;
                double dy = entity.getY() - myPos.y;
                double dz = entity.getZ() - myPos.z;
                builder.append(String.format("%s(X:%.1f, Y:%.1f, Z:%.1f); ", name, dx, dy, dz));
            }
        }
        return builder.toString();
    }

    /**
     * Запускает асинхронный вызов DeepSeek для автономного мышления.
     */
    private void triggerAutonomousThought(ServerLevel level, Villager villager, VillagerAiState state) {
        String name = villager.getName().getString();
        String profession = BuiltInRegistries.VILLAGER_PROFESSION
                .getKey(villager.getVillagerData().profession().value()).getPath();

        long gameTime = level.getLevelData().getGameTime() % 24000;
        String timeText = (gameTime < 13000) ? "день" : "ночь";
        String weatherText = level.isRaining() ? "дождь" : "ясно";
        String snapshot = buildEnvironmentSnapshot(villager, level);

        StringBuilder genStr = new StringBuilder();
        state.getGenome().forEach((g, v) -> genStr.append(g).append(":").append(v).append(", "));

        String systemPrompt = String.format(
                "Ты - игровой персонаж Житель %s (%s). Окружение: %s, %s. Снапшот мира: %s.\n" +
                        "Твои гены: %s\n" +
                        "Твоя память: \"%s\"\n" +
                        "Верни валидный JSON:\n" +
                        "{\n" +
                        "  \"thought\": \"Краткая мысль\",\n" +
                        "  \"say_to_player\": \"Фраза игроку вслух или 'none'\",\n" +
                        "  \"navigation\": \"none/wander/work/home/flee/go_to_target/follow\",\n" +
                        "  \"target\": \"имя_объекта_или_none\",\n" +
                        "  \"animation\": \"none/jump_joy/panic/inspect/sleep/work/jump\",\n" +
                        "  \"emotion\": \"neutral/happy/sad/angry\",\n" +
                        "  \"new_fact\": \"none\"\n" +
                        "}",
                name, profession, timeText, weatherText, snapshot,
                genStr.toString(), state.getLongTermMemory()
        );

        DeepSeekClient.askAI(systemPrompt, "[Фоновый цикл]").thenAccept(response -> {
            // Используем серверный поток для безопасности
            level.getServer().execute(() -> {
                try {
                    if (!response.contains("{") || !response.contains("}")) return;

                    String clean = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
                    clean = clean.replace("\"navigation\"", " \"navigation\"")
                            .replace("\"target\"", " \"target\"");

                    JsonObject json = JsonParser.parseString(clean).getAsJsonObject();

                    String thought = json.has("thought") ? json.get("thought").getAsString() : "...";
                    String sayToPlayer = json.has("say_to_player") ? json.get("say_to_player").getAsString() : "none";
                    String nav = json.has("navigation") ? json.get("navigation").getAsString() : "none";
                    String tgt = json.has("target") ? json.get("target").getAsString() : "none";
                    String anim = json.has("animation") ? json.get("animation").getAsString() : "none";
                    String emo = json.has("emotion") ? json.get("emotion").getAsString() : "neutral";

                    if (json.has("new_fact")) {
                        state.updateLongTermMemory(json.get("new_fact").getAsString());
                    }

                    nav = normalizeNav(nav);
                    String finalNav = nav;

                    if (!sayToPlayer.equalsIgnoreCase("none") && !sayToPlayer.trim().isEmpty()) {
                        Component msg = Component.literal("§e[" + name + " (" + profession + ")] §f" + sayToPlayer);
                        for (ServerPlayer p : level.players()) {
                            if (p.distanceToSqr(villager) < 144.0) {
                                p.sendSystemMessage(msg);
                                state.startDialog(p.getUUID());
                                state.addMessageToHistory("Ты (" + name + ")", sayToPlayer);
                            }
                        }
                    } else if (Notaloneanymore.showThoughtsInChat) {
                        Component msg = Component.literal("§7[" + name + " думает]: §o" + thought);
                        for (ServerPlayer p : level.players()) {
                            if (p.distanceToSqr(villager) < 400.0) {
                                p.sendSystemMessage(msg);
                            }
                        }
                    }

                    // Применяем навигацию и анимацию
                    executeAction(villager, level, state, finalNav, tgt, anim, emo);

                } catch (Exception e) {
                    Notaloneanymore.LOGGER.error("Ошибка парсинга мыслей: {}", e.getMessage());
                }
            });
        });
    }

    /**
     * Выполняет комплексное действие: устанавливает состояния и спавнит частицы.
     */
    private void executeAction(Villager villager, ServerLevel level, VillagerAiState state,
                               String nav, String targetType, String anim, String emo) {
        state.setNavState(nav);
        state.setAnimState(anim);
        state.setActionTimer(140);

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

        // Поиск цели
        if (!targetType.equals("none")) {
            if (targetType.equals("player")) {
                // Ищем ближайшего игрока
                net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(villager, 12.0);
                if (nearest != null) state.setTargetEntity(nearest);
            } else {
                List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class, villager.getBoundingBox().inflate(12.0),
                        e -> e != villager && (
                                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()
                                        .equalsIgnoreCase(targetType)
                                        || (targetType.equals("monster") && e instanceof Enemy)
                        ));
                if (!targets.isEmpty()) {
                    state.setTargetEntity(targets.stream()
                            .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(villager)))
                            .get());
                }
            }
        }
    }

    /**
     * Нормализует строку навигации из JSON.
     */
    public static String normalizeNav(String nav) {
        nav = nav.trim().toLowerCase().replace("\"", "").replace("'", "");
        if (nav.contains("wander")) return "wander";
        if (nav.contains("work")) return "work";
        if (nav.contains("home")) return "home";
        if (nav.contains("flee")) return "flee";
        if (nav.contains("follow")) return "follow";
        if (nav.contains("go_to_target")) return "go_to_target";
        return "none";
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long gameTime) {
        return true; // Не останавливается
    }

    @Override
    public Set<net.minecraft.world.entity.ai.behavior.declarative.MemoryCondition<?, ?>> getMemoryRequirements() {
        return Set.of(); // Не используем vanilla memory system
    }
}
