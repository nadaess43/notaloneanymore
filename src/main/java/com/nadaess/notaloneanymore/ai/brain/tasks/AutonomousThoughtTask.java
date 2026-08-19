package com.nadaess.notaloneanymore.ai.brain.tasks;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nadaess.notaloneanymore.DeepSeekClient;
import com.nadaess.notaloneanymore.Notaloneanymore;
import com.nadaess.notaloneanymore.entity.CompanionAiState;
import com.nadaess.notaloneanymore.entity.CompanionEntity;
import com.nadaess.notaloneanymore.entity.CompanionAgent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * РђРІС‚РѕРЅРѕРјРЅРѕРµ РјС‹С€Р»РµРЅРёРµ РєРѕРјРїР°РЅСЊРѕРЅР°. Р—Р°РјРµРЅСЏРµС‚ Р»РѕРіРёРєСѓ РґР»СЏ Villager.
 * РўРµРїРµСЂСЊ СЂР°Р±РѕС‚Р°РµС‚ СЃ CompanionEntity РЅР°РїСЂСЏРјСѓСЋ (Р±РµР· РјРёРєСЃРёРЅРѕРІ).
 */
public class AutonomousThoughtTask {

    public static void tickCustomAi(ServerLevel level, CompanionEntity companion, CompanionAiState state) {
        if (state == null) return;

        if (!state.isInitialized()) {
            state.initDefaultStats(companion);
        }

        if (companion.tickCount % 1200 == 0) {
            state.tickNeeds();
        }

        if (state.getReactiveCooldown() > 0) {
            state.setReactiveCooldown(state.getReactiveCooldown() - 1);
        }

        if (companion.hurtTime > 0 && companion.tickCount % 15 == 0) {
            String attacker = companion.getLastHurtByMob() != null
                    ? BuiltInRegistries.ENTITY_TYPE.getKey(companion.getLastHurtByMob().getType()).getPath()
                    : "РЅРµРёР·РІРµСЃС‚РЅРѕ";
            level.getServer().execute(() -> {
                companion.companion$triggerReactiveEvent("DAMAGE", "РњРµРЅСЏ Р°С‚Р°РєРѕРІР°Р»Рё! РђРіСЂРµСЃСЃРѕСЂ: " + attacker);
            });
        }

        if (state.isInActiveConversation() || state.getDialogTicksLeft() > 0) {
            state.setAutonomousCooldown(240);
        } else {
            int cooldown = state.getAutonomousCooldown() - 1;
            state.setAutonomousCooldown(cooldown);
            if (cooldown <= 0) {
                state.setAutonomousCooldown(240);
                triggerAutonomousThought(level, companion, state);
            }
        }

        if (state.getDialogTicksLeft() > 0) {
            state.setDialogTicksLeft(state.getDialogTicksLeft() - 1);
        }

        if (state.isInActiveConversation() && state.getDialogTicksLeft() <= 0) {
            state.setCurrentMaxDialogSeconds(25);
            state.setInActiveConversation(false);
            state.setDialogTargetUuid(null);
            state.getChatHistory().clear();
        }
    }

    private static String buildEnvironmentSnapshot(CompanionEntity companion, ServerLevel world) {
        Vec3 myPos = companion.position();
        List<LivingEntity> entities = world.getEntitiesOfClass(
                LivingEntity.class, companion.getBoundingBox().inflate(15.0), e -> e != companion);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("РњРѕСЏ РїРѕР·РёС†РёСЏ: [X:%.1f, Y:%.1f, Z:%.1f]. ", myPos.x, myPos.y, myPos.z));
        if (entities.isEmpty()) {
            builder.append("Р СЏРґРѕРј РЅРёРєРѕРіРѕ РЅРµС‚.");
        } else {
            builder.append("РћР±СЉРµРєС‚С‹ СЂСЏРґРѕРј: ");
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

    private static void triggerAutonomousThought(ServerLevel level, CompanionEntity companion, CompanionAiState state) {
        String name = companion.hasCustomName() ? companion.getCustomName().getString() : "РљРѕРјРїР°РЅСЊРѕРЅ";
        String role = "companion"; // РІСЂРµРјРµРЅРЅС‹Р№, РїРѕРєР° РЅРµС‚ РїСЂРѕС„РµСЃСЃРёР№ Hermes-СЃРєРёР»Р»РѕРІ

        long gameTime = level.getLevelData().getGameTime() % 24000;
        String timeText = (gameTime < 13000) ? "РґРµРЅСЊ" : "РЅРѕС‡СЊ";
        String weatherText = level.isRaining() ? "РґРѕР¶РґСЊ" : "СЏСЃРЅРѕ";
        String snapshot = buildEnvironmentSnapshot(companion, level);

        StringBuilder genStr = new StringBuilder();
        state.getGenome().forEach((g, v) -> genStr.append(g).append(":").append(v).append(", "));

        String systemPrompt = String.format(
                "РўС‹ - РёРіСЂРѕРІРѕР№ РїРµСЂСЃРѕРЅР°Р¶ РљРѕРјРїР°РЅСЊРѕРЅ %s (%s). РћРєСЂСѓР¶РµРЅРёРµ: %s, %s. РЎРЅР°РїС€РѕС‚ РјРёСЂР°: %s.\n" +
                        "РўРІРѕРё РіРµРЅС‹: %s\n" +
                        "РўРІРѕСЏ РїР°РјСЏС‚СЊ: \"%s\"\n" +
                        "Р’РµСЂРЅРё РІР°Р»РёРґРЅС‹Р№ JSON:\n" +
                        "{\n" +
                        "  \"thought\": \"РљСЂР°С‚РєР°СЏ РјС‹СЃР»СЊ\",\n" +
                        "  \"say_to_player\": \"Р¤СЂР°Р·Р° РёРіСЂРѕРєСѓ РІСЃР»СѓС… РёР»Рё 'none'\",\n" +
                        "  \"navigation\": \"none/wander/work/home/flee/go_to_target/follow\",\n" +
                        "  \"target\": \"РёРјСЏ_РѕР±СЉРµРєС‚Р°_РёР»Рё_none\",\n" +
                        "  \"animation\": \"none/jump_joy/panic/inspect/sleep/work/jump\",\n" +
                        "  \"emotion\": \"neutral/happy/sad/angry\",\n" +
                        "  \"new_fact\": \"none\"\n" +
                        "}",
                name, role, timeText, weatherText, snapshot,
                genStr.toString(), state.getLongTermMemory()
        );

        DeepSeekClient.askAI(systemPrompt, "[Р¤РѕРЅРѕРІС‹Р№ С†РёРєР»]").thenAccept(response -> {
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
                        Component msg = Component.literal("В§e[" + name + "] В§f" + sayToPlayer);
                        for (ServerPlayer p : level.players()) {
                            if (p.distanceToSqr(companion) < 144.0) {
                                p.sendSystemMessage(msg);
                                state.startDialog(p.getUUID());
                                state.addMessageToHistory("РўС‹ (" + name + ")", sayToPlayer);
                            }
                        }
                    } else if (Notaloneanymore.showThoughtsInChat) {
                        Component msg = Component.literal("В§7[" + name + " РґСѓРјР°РµС‚]: В§o" + thought);
                        for (ServerPlayer p : level.players()) {
                            if (p.distanceToSqr(companion) < 400.0) {
                                p.sendSystemMessage(msg);
                            }
                        }
                    }

                    executeAction(companion, level, state, finalNav, tgt, anim, emo);

                } catch (Exception e) {
                    Notaloneanymore.LOGGER.error("РћС€РёР±РєР° РїР°СЂСЃРёРЅРіР° РјС‹СЃР»РµР№: {}", e.getMessage());
                }
            });
        });
    }

    private static void executeAction(CompanionEntity companion, ServerLevel level, CompanionAiState state,
                                      String nav, String targetType, String anim, String emo) {
        state.setNavState(nav);
        state.setAnimState(anim);
        state.setActionTimer(140);

        if (emo.equals("happy")) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    companion.getX(), companion.getY() + 1.0, companion.getZ(), 5, 0.3, 0.3, 0.3, 0);
        } else if (emo.equals("angry")) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER,
                    companion.getX(), companion.getY() + 1.0, companion.getZ(), 4, 0.2, 0.2, 0.2, 0);
        }

        if (anim.equalsIgnoreCase("sleep")) {
            companion.startSleeping(companion.blockPosition());
        } else if (anim.equalsIgnoreCase("wake")) {
            companion.stopSleeping();
        } else if (anim.equalsIgnoreCase("jump")) {
            companion.getJumpControl().jump();
            companion.setDeltaMovement(companion.getDeltaMovement().add(0, 0.5, 0));
            companion.hurtMarked = true;
        } else if (anim.equalsIgnoreCase("work")) {
            level.broadcastEntityEvent(companion, (byte) 15);
        }

        if (!targetType.equals("none")) {
            if (targetType.equals("player")) {
                net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(companion, 12.0);
                if (nearest != null) state.setTargetEntity(nearest);
            } else {
                List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class, companion.getBoundingBox().inflate(12.0),
                        e -> e != companion && (
                                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath()
                                        .equalsIgnoreCase(targetType)
                                        || (targetType.equals("monster") && e instanceof Enemy)
                        ));
                if (!targets.isEmpty()) {
                    state.setTargetEntity(targets.stream()
                            .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(companion)))
                            .get());
                }
            }
        }

        // РќР°РІРёРіР°С†РёСЋ РґРµР»РµРіРёСЂСѓРµРј РІ entity's executeComplexAction Р»РѕРіРёРєСѓ (РЅРѕ СѓРїСЂРѕС‰РµРЅРЅРѕ Р·РґРµСЃСЊ)
        if ((nav.equals("go_to_target") || nav.equals("follow") || nav.equals("flee")) && state.getTargetEntity() != null) {
            companion.getNavigation().moveTo(state.getTargetEntity(), nav.equals("flee") ? 0.8 : 0.5);
        } else if (nav.equals("wander")) {
            net.minecraft.core.BlockPos pos = companion.blockPosition().offset(companion.getRandom().nextInt(10)-5, 0, companion.getRandom().nextInt(10)-5);
            companion.getNavigation().moveTo(pos.getX()+0.5, pos.getY(), pos.getZ()+0.5, 0.5);
        } else if (nav.equals("none")) {
            companion.getNavigation().stop();
        }
    }

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
}

