package com.nadaess.notaloneanymore.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nadaess.notaloneanymore.DeepSeekClient;
import com.nadaess.notaloneanymore.Notaloneanymore;
import com.nadaess.notaloneanymore.util.DialogAgent;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mixin(Villager.class)
public abstract class VillagerMixin implements DialogAgent {

    @Unique private static final Logger notAlone$LOGGER = LoggerFactory.getLogger("notaloneanymore");

    @Unique private UUID notAlone$dialogTargetUuid = null;
    @Unique private int notAlone$dialogTicksLeft = 0;
    @Unique private int notAlone$currentMaxDialogSeconds = 25;
    @Unique private boolean notAlone$isInActiveConversation = false;

    @Unique private String notAlone$navState = "none";
    @Unique private String notAlone$animState = "none";
    @Unique private LivingEntity notAlone$targetEntity = null;
    @Unique private int notAlone$actionTimer = 0;

    @Unique private int notAlone$autonomousCooldown = 240;
    @Unique private int notAlone$reactiveCooldown = 0;

    @Unique private final java.util.List<String> notAlone$chatHistory = new java.util.ArrayList<>();
    @Unique private String notAlone$longTermMemory = "Ничего примечательного не помню. Отношение к игрокам нейтральное.";
    @Unique private String notAlone$ramMemory = "Наблюдаю за обстановкой вокруг.";
    @Unique private int notAlone$cognitiveInertia = 50;

    @Unique private final Map<String, Integer> notAlone$genome = new HashMap<>();
    @Unique private final Map<String, Integer> notAlone$needs = new HashMap<>();

    @Unique
    private void notAlone$initDefaultStats() {
        Villager villager = (Villager) (Object) this;
        java.util.Random rng = new java.util.Random(villager.getUUID().getMostSignificantBits());

        String[] genes = {"moral", "empathy", "stubborn", "aggression", "industry", "greed", "extraversion", "bravery", "curiosity", "intellect", "paranoia", "machiavellianism", "narcissism", "sociability", "gossip"};
        for (String g : genes) {
            notAlone$genome.putIfAbsent(g, rng.nextInt(101));
        }

        notAlone$needs.putIfAbsent("hunger", 10 + rng.nextInt(20));
        notAlone$needs.putIfAbsent("fatigue", 5 + rng.nextInt(25));
        notAlone$needs.putIfAbsent("social", 50 + rng.nextInt(30));
        notAlone$needs.putIfAbsent("finance", 20 + rng.nextInt(40));

        notAlone$LOGGER.info("[ИИ ЛОГ] Сгенерирован научный геном для жителя {} ({})", villager.getName().getString(), villager.getUUID());
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        Villager villager = (Villager) (Object) this;
        if (villager.level().isClientSide()) return;

        if (notAlone$genome.isEmpty()) notAlone$initDefaultStats();

        if (villager.tickCount % 20 == 0 && !notAlone$navState.equals("none")) {
            notAlone$LOGGER.info("[ИИ ЛОГ] Житель {} | Нав-Стейт: {} | Цель: {}", villager.getName().getString(), notAlone$navState, notAlone$getTargetName());
        }

        if (villager.tickCount % 1200 == 0) {
            notAlone$needs.put("hunger", Math.min(100, notAlone$needs.get("hunger") + 2));
            notAlone$needs.put("fatigue", Math.min(100, notAlone$needs.get("fatigue") + 1));
            notAlone$needs.put("social", Math.max(0, notAlone$needs.get("social") - 3));
        }

        if (notAlone$reactiveCooldown > 0) notAlone$reactiveCooldown--;

        if (villager.hurtTime > 0 && villager.tickCount % 15 == 0) {
            String attacker = villager.getLastHurtByMob() != null ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(villager.getLastHurtByMob().getType()).getPath() : "неизвестно";
            this.notAlone$triggerReactiveEvent("DAMAGE", "Меня атаковали! Агрессор: " + attacker);
        }

        if (notAlone$dialogTicksLeft > 0 && notAlone$navState.equals("none")) {
            villager.getNavigation().stop();
            villager.setDeltaMovement(0, villager.getDeltaMovement().y, 0);
        }

        if (notAlone$isInActiveConversation && notAlone$dialogTicksLeft <= 0) {
            this.notAlone$currentMaxDialogSeconds = 25;
            this.notAlone$isInActiveConversation = false;
            this.notAlone$dialogTargetUuid = null;
            this.notAlone$chatHistory.clear();
        }

        if (notAlone$isInActiveConversation || notAlone$dialogTicksLeft > 0) {
            notAlone$autonomousCooldown = 240;
        } else {
            if (--notAlone$autonomousCooldown <= 0) {
                notAlone$autonomousCooldown = 240;
                this.notAlone$triggerAutonomousThought(villager);
            }
        }

        if (!notAlone$animState.equals("none")) {
            if (notAlone$animState.equals("jump_joy")) {
                if (villager.onGround() && villager.getRandom().nextInt(6) == 0) villager.jumpFromGround();
            } else if (notAlone$animState.equals("panic")) {
                villager.setYRot(villager.yHeadRot + (villager.getRandom().nextFloat() * 40 - 20));
            } else if (notAlone$animState.equals("inspect")) {
                villager.setXRot(35.0F);
            }
            if (--notAlone$actionTimer <= 0) notAlone$animState = "none";
        }

        if (notAlone$targetEntity != null && notAlone$targetEntity.isAlive()) {
            double distanceSq = villager.distanceToSqr(notAlone$targetEntity);
            villager.getLookControl().setLookAt(notAlone$targetEntity, 30.0F, 30.0F);

            if (notAlone$navState.equals("follow")) {
                if (villager.tickCount % 10 == 0 && distanceSq > 9.0) villager.getNavigation().moveTo(notAlone$targetEntity, 0.6);
                else if (distanceSq <= 4.0) villager.getNavigation().stop();
            } else if (notAlone$navState.equals("flee")) {
                if (villager.tickCount % 12 == 0) {
                    Vec3 away = villager.position().add(villager.position().subtract(notAlone$targetEntity.position()).normalize().scale(12));
                    villager.getNavigation().moveTo(away.x, away.y, away.z, 0.85);
                }
                if (distanceSq > 400.0) {
                    notAlone$navState = "none"; notAlone$targetEntity = null; villager.getNavigation().stop();
                }
            } else if (notAlone$navState.equals("go_to_target") && distanceSq <= 4.0) {
                villager.getNavigation().stop(); notAlone$navState = "none";
            }
        } else if (notAlone$navState.equals("wander")) {
            if (villager.getNavigation().isDone()) {
                Vec3 rngPos = villager.position().add((villager.getRandom().nextDouble() - 0.5) * 10, 0, (villager.getRandom().nextDouble() - 0.5) * 10);
                villager.getNavigation().moveTo(rngPos.x, rngPos.y, rngPos.z, 0.5);
            }
        }

        if (notAlone$dialogTicksLeft > 0) notAlone$dialogTicksLeft--;
    }

    @Unique
    private String buildEnvironmentSnapshot(Villager villager, ServerLevel world) {
        Vec3 myPos = villager.position();
        List<LivingEntity> entities = world.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(15.0), e -> e != villager);
        StringBuilder builder = new StringBuilder();
        builder.append(String.format("Моя позиция: [X:%.1f, Y:%.1f, Z:%.1f]. ", myPos.x, myPos.y, myPos.z));
        if (entities.isEmpty()) { builder.append("Рядом никого нет."); } else {
            builder.append("Объекты рядом: ");
            for (LivingEntity entity : entities) {
                String name = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                if (entity instanceof net.minecraft.world.entity.player.Player) name = "player_" + entity.getName().getString();
                double dx = entity.getX() - myPos.x; double dy = entity.getY() - myPos.y; double dz = entity.getZ() - myPos.z;
                builder.append(String.format("%s(X:%.1f, Y:%.1f, Z:%.1f); ", name, dx, dy, dz));
            }
        }
        return builder.toString();
    }

    @Unique
    private void notAlone$triggerAutonomousThought(Villager villager) {
        this.notAlone$autonomousCooldown = 240;
        ServerLevel world = (ServerLevel) villager.level();
        String name = villager.getName().getString();
        String profession = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value()).getPath();

        long gameTime = world.getLevelData().getGameTime() % 24000;
        String timeText = (gameTime < 13000) ? "день" : "ночь";

        String weatherText = world.isRaining() ? "дождь" : "ясно";
        String snapshot = buildEnvironmentSnapshot(villager, world);

        StringBuilder genStr = new StringBuilder();
        notAlone$genome.forEach((g, v) -> genStr.append(g).append(":").append(v).append(", "));

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
                name, profession, timeText, weatherText, snapshot, genStr.toString(), this.notAlone$longTermMemory
        );

        DeepSeekClient.askAI(systemPrompt, "[Фоновый цикл]").thenAccept(response -> {
            try {
                if (!response.contains("{") || !response.contains("}")) return;
                String clean = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
                clean = clean.replace("\"navigation\"", " \"navigation\"").replace("\"target\"", " \"target\"");

                JsonObject json = JsonParser.parseString(clean).getAsJsonObject();

                String thought = json.has("thought") ? json.get("thought").getAsString() : "...";
                String sayToPlayer = json.has("say_to_player") ? json.get("say_to_player").getAsString() : "none";
                String nav = json.has("navigation") ? json.get("navigation").getAsString() : "none";
                String tgt = json.has("target") ? json.get("target").getAsString() : "none";
                String anim = json.has("animation") ? json.get("animation").getAsString() : "none";
                String emo = json.has("emotion") ? json.get("emotion").getAsString() : "neutral";

                if (json.has("new_fact")) this.notAlone$updateLongTermMemory(json.get("new_fact").getAsString());

                nav = nav.trim().toLowerCase().replace("\"", "").replace("'", "");
                if (nav.contains("wander")) nav = "wander";
                else if (nav.contains("work")) nav = "work";
                else if (nav.contains("home")) nav = "home";
                else if (nav.contains("flee")) nav = "flee";
                else if (nav.contains("follow")) nav = "follow";
                else if (nav.contains("go_to_target")) nav = "go_to_target";
                else nav = "none";

                notAlone$LOGGER.info("[ИИ ЛОГ] [{} МЫСЛЬ]: {}", name, thought);

                if (!sayToPlayer.equalsIgnoreCase("none") && !sayToPlayer.trim().isEmpty()) {
                    net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal("§e[" + name + " (" + profession + ")] §f" + sayToPlayer);
                    for (ServerPlayer p : world.players()) {
                        if (p.distanceToSqr(villager) < 144.0) {
                            p.sendSystemMessage(msg);
                            world.getServer().execute(() -> {
                                this.notAlone$startDialog(p.getUUID());
                                this.notAlone$addMessageToHistory("Ты (" + name + ")", sayToPlayer);
                            });
                        }
                    }
                } else if (Notaloneanymore.showThoughtsInChat) {
                    net.minecraft.network.chat.MutableComponent msg = net.minecraft.network.chat.Component.literal("§7[" + name + " думает]: §o" + thought);
                    for (ServerPlayer p : world.players()) {
                        if (p.distanceToSqr(villager) < 400.0) p.sendSystemMessage(msg);
                    }
                }

                String finalNav = nav;
                world.getServer().execute(() -> {
                    ServerPlayer targetPlayer = null; String finalTgt = tgt;
                    if (tgt.startsWith("player_")) {
                        targetPlayer = world.getServer().getPlayerList().getPlayerByName(tgt.replace("player_", ""));
                        finalTgt = "player";
                    }
                    this.notAlone$executeComplexAction(finalNav, finalTgt, anim, emo, targetPlayer);
                });
            } catch (Exception e) { notAlone$LOGGER.error("Ошибка парсинга мыслей: {}", e.getMessage()); }
        });
    }

    @Override @Unique
    public void notAlone$triggerReactiveEvent(String eventType, String description) {
        Villager villager = (Villager) (Object) this;
        ServerLevel world = (ServerLevel) villager.level();
        if (this.notAlone$reactiveCooldown > 0) return;
        this.notAlone$reactiveCooldown = 100;

        notAlone$LOGGER.warn("[ИИ ЛОГ] [РЕАКТИВНЫЙ ТРИГГЕР] {} заметил: {} - {}", villager.getName().getString(), eventType, description);
        this.notAlone$ramMemory = "[КРИТИЧЕСКОЕ СОБЫТИЕ]: " + description;

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
                eventType, description, villager.getName().getString(), this.notAlone$longTermMemory
        );

        DeepSeekClient.askAI(systemPrompt, "[Триггер " + eventType + "]").thenAccept(response -> {
            try {
                if (!response.contains("{") || !response.contains("}")) return;
                String clean = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
                clean = clean.replace("\"navigation\"", " \"navigation\"").replace("\"target\"", " \"target\"");

                JsonObject json = JsonParser.parseString(clean).getAsJsonObject();

                String thought = json.has("thought") ? json.get("thought").getAsString() : "...";
                String nav = json.has("navigation") ? json.get("navigation").getAsString() : "none";
                String anim = json.has("animation") ? json.get("animation").getAsString() : "none";
                String emo = json.has("emotion") ? json.get("emotion").getAsString() : "neutral";

                if (json.has("new_fact")) this.notAlone$updateLongTermMemory(json.get("new_fact").getAsString());

                nav = nav.trim().toLowerCase().replace("\"", "").replace("'", "");
                if (nav.contains("wander")) nav = "wander";
                else if (nav.contains("work")) nav = "work";
                else if (nav.contains("home")) nav = "home";
                else if (nav.contains("flee")) nav = "flee";
                else if (nav.contains("follow")) nav = "follow";
                else if (nav.contains("go_to_target")) nav = "go_to_target";
                else nav = "none";

                String professionKey = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().profession().value()).getPath();

                net.minecraft.network.chat.MutableComponent chatMessage;
                if (Notaloneanymore.showThoughtsInChat) {
                    String color = (eventType.startsWith("VANDALISM")) ? "§c" : "§e";
                    String tag = (eventType.startsWith("VANDALISM")) ? " РЕАГИРУЕТ]: §o" : " ЗАМЕТИЛ]: §o";
                    chatMessage = net.minecraft.network.chat.Component.literal(color + "[" + villager.getName().getString() + " (" + professionKey + ")" + tag + thought);
                } else {
                    chatMessage = net.minecraft.network.chat.Component.literal("§f" + villager.getName().getString() + ": " + thought);
                }

                for (ServerPlayer p : world.players()) {
                    if (p.distanceToSqr(villager) < 576.0) p.sendSystemMessage(chatMessage);
                }

                String finalNav = nav;
                world.getServer().execute(() -> {
                    this.notAlone$navState = finalNav; this.notAlone$animState = anim; this.notAlone$actionTimer = 160;
                    if (finalNav.equals("flee")) this.notAlone$targetEntity = world.getNearestPlayer(villager, 15.0);
                    this.notAlone$executeComplexAction(finalNav, "none", anim, emo, null);
                });
            } catch (Exception e) { notAlone$LOGGER.error("Ошибка реактивного парсинга JSON: {}", e.getMessage()); }
        });
    }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void onWriteNbt(net.minecraft.world.level.storage.ValueOutput output, CallbackInfo ci) {
        output.putString("RamMemory", this.notAlone$ramMemory);
        output.putString("LongTermMemory", this.notAlone$longTermMemory);
        output.putInt("CognitiveInertia", this.notAlone$cognitiveInertia);
        for (Map.Entry<String, Integer> entry : notAlone$genome.entrySet()) output.putInt("Gene_" + entry.getKey(), entry.getValue());
        for (Map.Entry<String, Integer> entry : notAlone$needs.entrySet()) output.putInt("Need_" + entry.getKey(), entry.getValue());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void onReadNbt(net.minecraft.world.level.storage.ValueInput input, CallbackInfo ci) {
        input.getString("RamMemory").ifPresent(val -> this.notAlone$ramMemory = val);
        input.getString("LongTermMemory").ifPresent(val -> this.notAlone$longTermMemory = val);
        input.getInt("CognitiveInertia").ifPresent(val -> this.notAlone$cognitiveInertia = val);
        String[] genes = {"moral", "empathy", "stubborn", "aggression", "industry", "greed", "extraversion", "bravery", "curiosity", "intellect", "paranoia", "machiavellianism", "narcissism", "sociability", "gossip"};
        for (String g : genes) input.getInt("Gene_" + g).ifPresent(val -> notAlone$genome.put(g, val));
        String[] needsList = {"hunger", "fatigue", "social", "finance"};
        for (String n : needsList) input.getInt("Need_" + n).ifPresent(val -> notAlone$needs.put(n, val));
    }

    @Override @Unique
    public void notAlone$executeComplexAction(String nav, String targetType, String anim, String emo, ServerPlayer player) {
        Villager villager = (Villager) (Object) this; ServerLevel level = (ServerLevel) villager.level();
        this.notAlone$navState = nav; this.notAlone$animState = anim; this.notAlone$actionTimer = 140;

        if (emo.equals("happy")) level.sendParticles(ParticleTypes.HAPPY_VILLAGER, villager.getX(), villager.getY() + 2, villager.getZ(), 5, 0.3, 0.3, 0.3, 0);
        else if (emo.equals("angry")) level.sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getY() + 2, villager.getZ(), 4, 0.2, 0.2, 0.2, 0);

        if (anim.equalsIgnoreCase("sleep")) villager.startSleeping(villager.blockPosition());
        else if (anim.equalsIgnoreCase("wake")) villager.stopSleeping();
        else if (anim.equalsIgnoreCase("jump")) { villager.getJumpControl().jump(); villager.setDeltaMovement(villager.getDeltaMovement().add(0, 0.5, 0)); }
        else if (anim.equalsIgnoreCase("work")) level.broadcastEntityEvent(villager, (byte) 15);

        if (!targetType.equals("none")) {
            if (targetType.equals("player") && player != null) { this.notAlone$targetEntity = player; }
            else {
                List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(12.0), e -> e != villager && (
                        net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equalsIgnoreCase(targetType) || (targetType.equals("monster") && e instanceof Enemy)
                ));
                if (!targets.isEmpty()) this.notAlone$targetEntity = targets.stream().min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(villager))).get();
            }
        }
        if ((nav.equals("go_to_target") || nav.equals("follow") || nav.equals("flee")) && notAlone$targetEntity != null) { villager.getNavigation().moveTo(notAlone$targetEntity, nav.equals("flee") ? 0.8 : 0.5); }
        else { villager.getNavigation().stop(); }
    }

    @Override @Unique public int notAlone$startDialog(UUID playerUuid) { this.notAlone$dialogTargetUuid = playerUuid; if (!this.notAlone$isInActiveConversation) { this.notAlone$currentMaxDialogSeconds = 25; this.notAlone$isInActiveConversation = true; } else { this.notAlone$currentMaxDialogSeconds += 1; } this.notAlone$dialogTicksLeft = this.notAlone$currentMaxDialogSeconds * 20; return this.notAlone$currentMaxDialogSeconds; }
    @Override @Unique public void notAlone$addMessageToHistory(String role, String content) { if (this.notAlone$chatHistory.size() > 8) this.notAlone$chatHistory.remove(0); this.notAlone$chatHistory.add(role + ": " + content); }
    @Override @Unique public String notAlone$getChatHistoryForPrompt() { return this.notAlone$chatHistory.isEmpty() ? "Разговор начат." : String.join("\n", this.notAlone$chatHistory); }
    @Override @Unique public void notAlone$updateLongTermMemory(String fact) { if (fact != null && !fact.equalsIgnoreCase("none")) this.notAlone$longTermMemory = fact; }
    @Override @Unique public String notAlone$getLongTermMemory() { return this.notAlone$longTermMemory; }
    @Override @Unique public String notAlone$getRamMemory() { return this.notAlone$ramMemory; }
    @Override @Unique public void notAlone$updateRamMemory(String newRam) { this.notAlone$ramMemory = newRam; }
    @Override @Unique public int notAlone$getInertia() { return this.notAlone$cognitiveInertia; }
    @Override @Unique public void notAlone$setInertia(int value) { this.notAlone$cognitiveInertia = value; }
    @Override @Unique public Map<String, Integer> notAlone$getGenome() { return this.notAlone$genome; }
    @Override @Unique public void notAlone$setGene(String geneName, int value) { this.notAlone$genome.put(geneName, value); }
    @Override @Unique public Map<String, Integer> notAlone$getNeeds() { return this.notAlone$needs; }
    @Override @Unique public void notAlone$setNeed(String needName, int value) { this.notAlone$needs.put(needName, value); }
    @Override public String notAlone$getNavState() { return this.notAlone$navState; }
    @Override public String notAlone$getAnimState() { return this.notAlone$animState; }
    @Override public String notAlone$getTargetName() { return this.notAlone$targetEntity != null ? net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(this.notAlone$targetEntity.getType()).getPath() : "none"; }
}