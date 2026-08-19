package com.nadaess.notaloneanymore.entity;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nadaess.notaloneanymore.DeepSeekClient;
import com.nadaess.notaloneanymore.Notaloneanymore;
import com.nadaess.notaloneanymore.ai.brain.tasks.AutonomousThoughtTask;
import com.nadaess.notaloneanymore.ai.brain.tasks.ExecuteAiOrderTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * РљР°СЃС‚РѕРјРЅР°СЏ СЃСѓС‰РЅРѕСЃС‚СЊ-РєРѕРјРїР°РЅСЊРѕРЅ. РџСЂСЏРјРѕСѓРіРѕР»СЊРЅС‹Р№ Р±Р»РѕРє СЃ С‚РµРєСЃС‚СѓСЂРѕР№ TNT (СЂРµРЅРґРµСЂ РІ client).
 * Р—Р°РјРµРЅСЏРµС‚ РІР°РЅРёР»СЊРЅС‹С… Р¶РёС‚РµР»РµР№ РїРѕР»РЅРѕСЃС‚СЊСЋ. Р’СЃСЏ РР-Р»РѕРіРёРєР° С‚РµРїРµСЂСЊ РІРЅСѓС‚СЂРё СЃСѓС‰РЅРѕСЃС‚Рё, Р° РЅРµ РІ РјРёРєСЃРёРЅРµ.
 * РЎРѕС…СЂР°РЅСЏРµС‚ РіР»Р°РІРЅС‹Рµ РёРґРµРё all-ideas: РіРµРЅРѕРј 15 СЃС‚Р°С‚РѕРІ, 4 РїРѕС‚СЂРµР±РЅРѕСЃС‚Рё, С‚СЂС‘С…СѓСЂРѕРІРЅРµРІР°СЏ РїР°РјСЏС‚СЊ, РѕС‡РµСЂРµРґСЊ AtomicAction.
 */
public class CompanionEntity extends PathfinderMob implements CompanionAgent {

    private final CompanionAiState aiState = new CompanionAiState();
    private final SimpleContainer inventory = new SimpleContainer(8);

    public CompanionEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        // Р§С‚РѕР±С‹ РЅРµ РґРµСЃРїР°РІРЅРёР»СЃСЏ
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.5)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ATTACK_DAMAGE, 2.0)
                .add(Attributes.STEP_HEIGHT, 1.0);
    }

    @Override
    protected void registerGoals() {
        // РџСѓСЃС‚РѕР№ goalSelector вЂ” РґРІРёР¶РµРЅРёРµ С‚РѕР»СЊРєРѕ С‡РµСЂРµР· AI РѕС‡РµСЂРµРґСЊ Рё navigation РЅР°РїСЂСЏРјСѓСЋ
        // РћСЃС‚Р°РІРёРј РїСѓСЃС‚С‹Рј РєР°Рє Р»РѕР±РѕС‚РѕРјРёСЏ РІ VillagerMixin
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) return;
        ServerLevel level = (ServerLevel) this.level();

        // РРЅРёС†РёР°Р»РёР·Р°С†РёСЏ РіРµРЅРѕРјР° РїСЂРё РїРµСЂРІРѕРј С‚РёРєРµ
        if (!aiState.isInitialized()) {
            aiState.initDefaultStats(this);
            // Р”Р°РµРј РёРјСЏ РµСЃР»Рё РЅРµС‚ РєР°СЃС‚РѕРјРЅРѕРіРѕ вЂ” РґР»СЏ СѓРґРѕР±СЃС‚РІР° РєРѕРјР°РЅРґ /mind
            if (!this.hasCustomName()) {
                // РРјСЏ РЅРµ СЃС‚Р°РІРёРј Р°РІС‚РѕРјР°С‚РёС‡РµСЃРєРё, РѕСЃС‚Р°РµС‚СЃСЏ РїСѓСЃС‚С‹Рј вЂ” РєРѕРјР°РЅРґР° /mind РёСЃРїРѕР»СЊР·СѓРµС‚ customName
                // РќРѕ РґР»СЏ Р»РѕРіРѕРІ РёСЃРїРѕР»СЊР·СѓРµРј entity id
            }
        }

        // РљРѕРЅРІРµР№РµСЂ С„РёР·РёС‡РµСЃРєРёС… РґРµР№СЃС‚РІРёР№
        ExecuteAiOrderTask.tickPhysicalMovement(level, this, aiState);
        // РђРІС‚РѕРЅРѕРјРЅРѕРµ РјС‹С€Р»РµРЅРёРµ, РєСѓР»РґР°СѓРЅС‹, РїРѕС‚СЂРµР±РЅРѕСЃС‚Рё, РґРёР°Р»РѕРіРё
        AutonomousThoughtTask.tickCustomAi(level, this, aiState);
    }

    // ===== NBT =====

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        aiState.saveToNbt(output);
        // РРЅРІРµРЅС‚Р°СЂСЊ РјРѕР¶РЅРѕ СЃРѕС…СЂР°РЅРёС‚СЊ РѕС‚РґРµР»СЊРЅРѕ РµСЃР»Рё РЅСѓР¶РЅРѕ, РЅРѕ РїРѕРєР° РЅРµ СЃРѕС…СЂР°РЅСЏРµРј
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        aiState.loadFromNbt(input);
    }

    // ===== РРЅРІРµРЅС‚Р°СЂСЊ =====

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    // ===== CompanionAgent delegation =====

    @Override
    public int companion$startDialog(UUID playerUuid) {
        return aiState.startDialog(playerUuid);
    }

    @Override
    public void companion$addMessageToHistory(String role, String content) {
        aiState.addMessageToHistory(role, content);
    }

    @Override
    public String companion$getChatHistoryForPrompt() {
        return aiState.getChatHistoryForPrompt();
    }

    @Override
    public void companion$updateLongTermMemory(String fact) {
        aiState.updateLongTermMemory(fact);
    }

    @Override
    public String companion$getLongTermMemory() {
        return aiState.getLongTermMemory();
    }

    @Override
    public String companion$getRamMemory() {
        return aiState.getRamMemory();
    }

    @Override
    public void companion$updateRamMemory(String newRam) {
        aiState.updateRamMemory(newRam);
    }

    @Override
    public int companion$getInertia() {
        return aiState.getInertia();
    }

    @Override
    public void companion$setInertia(int value) {
        aiState.setInertia(value);
    }

    @Override
    public Map<String, Integer> companion$getGenome() {
        return aiState.getGenome();
    }

    @Override
    public void companion$setGene(String geneName, int value) {
        aiState.setGene(geneName, value);
    }

    @Override
    public Map<String, Integer> companion$getNeeds() {
        return aiState.getNeeds();
    }

    @Override
    public void companion$setNeed(String needName, int value) {
        aiState.setNeed(needName, value);
    }

    @Override
    public String companion$getNavState() {
        return aiState.getNavState();
    }

    @Override
    public String companion$getAnimState() {
        return aiState.getAnimState();
    }

    @Override
    public String companion$getTargetName() {
        return aiState.getTargetName();
    }

    @Override
    public CompanionAiState companion$getAiState() {
        return aiState;
    }

    @Override
    public void companion$executeComplexAction(String nav, String targetType, String anim, String emo, ServerPlayer player) {
        ServerLevel level = (ServerLevel) this.level();

        aiState.setNavState(nav);
        aiState.setAnimState(anim);
        aiState.setActionTimer(140);

        // Р§Р°СЃС‚РёС†С‹ СЌРјРѕС†РёР№
        if (emo.equals("happy")) {
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(), 5, 0.3, 0.3, 0.3, 0);
        } else if (emo.equals("angry")) {
            level.sendParticles(ParticleTypes.ANGRY_VILLAGER, this.getX(), this.getY() + 1.0, this.getZ(), 4, 0.2, 0.2, 0.2, 0);
        }

        if (anim.equalsIgnoreCase("sleep")) {
            this.startSleeping(this.blockPosition());
        } else if (anim.equalsIgnoreCase("wake")) {
            this.stopSleeping();
        } else if (anim.equalsIgnoreCase("jump")) {
            this.getJumpControl().jump();
            this.setDeltaMovement(this.getDeltaMovement().add(0, 0.5, 0));
            this.hurtMarked = true;
        } else if (anim.equalsIgnoreCase("work")) {
            level.broadcastEntityEvent(this, (byte) 15);
        }

        if (!targetType.equals("none")) {
            if (targetType.equals("player") && player != null) {
                aiState.setTargetEntity(player);
            } else {
                List<LivingEntity> targets = level.getEntitiesOfClass(
                        LivingEntity.class, this.getBoundingBox().inflate(12.0),
                        e -> e != this && (
                                BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equalsIgnoreCase(targetType)
                                        || (targetType.equals("monster") && e instanceof Enemy)
                        ));
                if (!targets.isEmpty()) {
                    aiState.setTargetEntity(targets.stream()
                            .min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(this)))
                            .get());
                }
            }
        }

        if ((nav.equals("go_to_target") || nav.equals("follow") || nav.equals("flee")) && aiState.getTargetEntity() != null) {
            this.getNavigation().moveTo(aiState.getTargetEntity(), nav.equals("flee") ? 0.8 : 0.5);
        } else {
            // Р”Р»СЏ wander/home/work вЂ” РёСЃРїРѕР»СЊР·СѓРµРј СЂР°РЅРґРѕРјРЅСѓСЋ С‚РѕС‡РєСѓ РёР»Рё СЃС‚РѕРї; Р°РІС‚РѕРЅРѕРјРЅС‹Р№ thought РїРѕС‚РѕРј СЂРµС€РёС‚
            if (nav.equals("wander")) {
                BlockPos wanderPos = this.blockPosition().offset(
                        this.getRandom().nextInt(10) - 5,
                        0,
                        this.getRandom().nextInt(10) - 5);
                this.getNavigation().moveTo(wanderPos.getX() + 0.5, wanderPos.getY(), wanderPos.getZ() + 0.5, 0.5);
            } else if (nav.equals("none")) {
                this.getNavigation().stop();
            }
        }
    }

    @Override
    public void companion$triggerReactiveEvent(String eventType, String description) {
        ServerLevel world = (ServerLevel) this.level();

        if (aiState.getReactiveCooldown() > 0) return;
        aiState.setReactiveCooldown(100);

        Notaloneanymore.LOGGER.warn("[РР] [Р Р•РђРљРўРР’РќР«Р™ РўР РР“Р“Р•Р ] {} Р·Р°РјРµС‚РёР»: {} - {}",
                this.getName().getString(), eventType, description);

        aiState.updateRamMemory("[РљР РРўРР§Р•РЎРљРћР• РЎРћР‘Р«РўРР•]: " + description);

        String systemPrompt = String.format(
                "РљР РРўРР§Р•РЎРљРћР• РЎРћР‘Р«РўРР•! РџСЂРѕРёР·РѕС€Р»Рѕ: [%s] -> %s.\n" +
                        "РўС‹ РєРѕРјРїР°РЅСЊРѕРЅ %s. РџР°РјСЏС‚СЊ: \"%s\".\n" +
                        "Р’ РїРѕР»Рµ 'thought' РЅР°РїРёС€Рё СЃРІРѕСЋ РљР РР§РђР©РЈР® Р Р•Р§Р¬ РїРµСЂСЃРѕРЅР°Р¶РµРј.\n" +
                        "Р’РµСЂРЅРё СЃС‚СЂРѕРіРѕ JSON:\n" +
                        "{\n" +
                        "  \"thought\": \"What you scream out loud!\",\n" +
                        "  \"navigation\": \"none/wander/work/home/flee/go_to_target/follow\",\n" +
                        "  \"target\": \"none\",\n" +
                        "  \"animation\": \"none/panic/inspect/sleep/work/jump\",\n" +
                        "  \"emotion\": \"neutral/happy/sad/angry\",\n" +
                        "  \"new_fact\": \"none\"\n" +
                        "}",
                eventType, description, this.getName().getString(),
                aiState.getLongTermMemory()
        );

        DeepSeekClient.askAI(systemPrompt, "[РўСЂРёРіРіРµСЂ " + eventType + "]").thenAccept(response -> {
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

                    Component chatMessage;
                    if (Notaloneanymore.showThoughtsInChat) {
                        String color = (eventType.startsWith("VANDALISM")) ? "В§c" : "В§e";
                        String tag = (eventType.startsWith("VANDALISM"))
                                ? " Р Р•РђР“РР РЈР•Рў]: В§o"
                                : " Р—РђРњР•РўРР›]: В§o";
                        chatMessage = Component.literal(color + "["
                                + this.getName().getString() + tag + thought);
                    } else {
                        chatMessage = Component.literal("В§f"
                                + this.getName().getString() + ": " + thought);
                    }

                    for (ServerPlayer p : world.players()) {
                        if (p.distanceToSqr(this) < 576.0) {
                            p.sendSystemMessage(chatMessage);
                        }
                    }

                    aiState.setNavState(nav);
                    aiState.setAnimState(anim);
                    aiState.setActionTimer(160);
                    if (nav.equals("flee")) {
                        aiState.setTargetEntity(world.getNearestPlayer(this, 15.0));
                    }
                    this.companion$executeComplexAction(nav, "none", anim, emo, null);

                } catch (Exception e) {
                    Notaloneanymore.LOGGER.error("РћС€РёР±РєР° СЂРµР°РєС‚РёРІРЅРѕРіРѕ РїР°СЂСЃРёРЅРіР° JSON: {}", e.getMessage());
                }
            });
        });
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return true;
    }
}

