package com.nadaess.notaloneanymore.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nadaess.notaloneanymore.DeepSeekClient;
import com.nadaess.notaloneanymore.Notaloneanymore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.nadaess.notaloneanymore.util.DialogAgent;


@Mixin(net.minecraft.server.players.PlayerList.class)
public abstract class ServerChatMixin {

    @Unique private static final Map<UUID, StringBuilder> messageBuffers = new ConcurrentHashMap<>();
    @Unique private static final Map<UUID, ScheduledFuture<?>> chatTasks = new ConcurrentHashMap<>();
    @Unique private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @Inject(method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V", at = @At("HEAD"))
    private void onPlayerChat(PlayerChatMessage message, ServerPlayer player, net.minecraft.network.chat.ChatType.Bound boundChatType, CallbackInfo ci) {
        String newText = message.unsignedContent() != null ? message.unsignedContent().getString() : message.signedContent();
        if (newText == null || newText.trim().isEmpty()) return;

        UUID playerUuid = player.getUUID();
        Level world = player.level();

        double radius = 12.0;
        AABB area = player.getBoundingBox().inflate(radius);
        List<Villager> villagers = world.getEntitiesOfClass(Villager.class, area);

        if (!villagers.isEmpty()) {
            Villager closestVillager = villagers.stream()
                    .min(Comparator.comparingDouble(v -> v.distanceToSqr(player)))
                    .get();

            if (closestVillager instanceof com.nadaess.notaloneanymore.util.DialogAgent agent) {
                agent.notAlone$startDialog(player.getUUID());
            }

            boolean isFirstMessage = !messageBuffers.containsKey(playerUuid) || messageBuffers.get(playerUuid).length() == 0;

            StringBuilder buffer = messageBuffers.computeIfAbsent(playerUuid, k -> new StringBuilder());
            if (buffer.length() > 0) buffer.append(" ");
            buffer.append(newText);

            if (chatTasks.containsKey(playerUuid)) {
                chatTasks.get(playerUuid).cancel(false);
            }

            long delayMs = isFirstMessage ? 1000 : 1500;

            ScheduledFuture<?> futureTask = scheduler.schedule(() -> {
                String fullUserText = buffer.toString();
                buffer.setLength(0);

                ((ServerLevel) world).getServer().execute(() ->
                        processAiTalk(player, closestVillager, world, fullUserText)
                );

            }, delayMs, TimeUnit.MILLISECONDS);

            chatTasks.put(playerUuid, futureTask);
        }
    }

    @Unique
    private void processAiTalk(ServerPlayer player, Villager closestVillager, Level world, String fullText) {
        String professionKey = net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getKey(closestVillager.getVillagerData().profession().value()).getPath();
        String villagerName = closestVillager.hasCustomName() ? closestVillager.getCustomName().getString() : "Житель";

        String biome = world.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME).getKey(world.getBiome(closestVillager.blockPosition()).value()).getPath();
        String weather = world.isThundering() ? "гроза" : (world.isRaining() ? "дождь" : "ясно");
        String timeString = ((world.getGameTime() % 24000) >= 13000) ? "ночь" : "день";
        String chatHistory = "Разговор начат.";
        String longTermMemory = "Ничего.";

        if (closestVillager instanceof com.nadaess.notaloneanymore.util.DialogAgent agent) {
            chatHistory = agent.notAlone$getChatHistoryForPrompt();
            longTermMemory = agent.notAlone$getLongTermMemory();
            agent.notAlone$addMessageToHistory("Игрок " + player.getName().getString(), fullText);
        }

        player.sendSystemMessage(Component.literal("§7[" + villagerName + " обдумывает твои слова... ]"));

        String systemPrompt = String.format(
                "Ты - разумный Житель в мире Minecraft по имени %s (%s).\n" +
                        "Окружение: биом %s, %s, погода %s.\n" +
                        "ТВОЯ ДОЛГОСРОЧНАЯ ПАМЯТЬ: \"%s\". Ты ОБЯЗАН строго помнить её и опираться на неё! Если там написано, что игрок мародёр, преступник или враг — веди себя агрессивно, испуганно или обиженно!\n\n" +
                        "Лог реплик текущей беседы:\n%s\n\n" +
                        "Игрок говорит тебе прямо сейчас: \"%s\".\n\n" +
                        "Ответь строго в формате JSON без markdown разметки:\n" +
                        "{\n" +
                        "  \"say_to_player\": \"Твой живой вербальный ответ игроку персонажем вслух\",\n" +
                        "  \"navigation\": \"none/go_to_target/follow/flee/work/home/wander\",\n" +
                        "  \"target\": \"player/none\",\n" +
                        "  \"animation\": \"none/jump_joy/panic/inspect\",\n" +
                        "  \"emotion\": \"neutral/happy/sad/angry\",\n" +
                        "  \"new_fact\": \"Дополни или перепиши память, ОБЯЗАТЕЛЬНО сохранив старые важные факты (особенно погромы)\"\n" +
                        "}",
                villagerName, professionKey, biome, timeString, weather, longTermMemory, chatHistory, fullText
        );

        DeepSeekClient.askAI(systemPrompt, fullText).thenAccept(response -> {
            try {
                if (!response.contains("{") || !response.contains("}")) return;
                String cleanResponse = response.substring(response.indexOf("{"), response.lastIndexOf("}") + 1);
                cleanResponse = cleanResponse.replace("\"navigation\"", " \"navigation\"").replace("\"target\"", " \"target\"");

                JsonObject json = JsonParser.parseString(cleanResponse).getAsJsonObject();

                if (json.has("say_to_player")) {
                    String cleanThought = json.get("say_to_player").getAsString();

                    if (closestVillager instanceof com.nadaess.notaloneanymore.util.DialogAgent agent) {
                        agent.notAlone$addMessageToHistory("Ты (" + villagerName + ")", cleanThought);
                    }

                    player.sendSystemMessage(Component.literal("§e[" + villagerName + " (" + professionKey + ")] §f" + cleanThought));

                    String nav = json.has("navigation") ? json.get("navigation").getAsString() : "none";
                    String tgt = json.has("target") ? json.get("target").getAsString() : "none";
                    String anim = json.has("animation") ? json.get("animation").getAsString() : "none";
                    String emo = json.has("emotion") ? json.get("emotion").getAsString() : "neutral";

                    if (json.has("new_fact") && closestVillager instanceof com.nadaess.notaloneanymore.util.DialogAgent agent) {
                        agent.notAlone$updateLongTermMemory(json.get("new_fact").getAsString());
                    }

                    nav = nav.trim().toLowerCase().replace("\"", "").replace("'", "");
                    if (nav.contains("wander")) nav = "wander";
                    else if (nav.contains("work")) nav = "work";
                    else if (nav.contains("home")) nav = "home";
                    else if (nav.contains("flee")) nav = "flee";
                    else if (nav.contains("follow")) nav = "follow";
                    else if (nav.contains("go_to_target")) nav = "go_to_target";
                    else nav = "none";

                    String finalNav = nav;
                    if (closestVillager instanceof DialogAgent agent) {
                        player.level().getServer().execute(() -> agent.notAlone$executeComplexAction(finalNav, tgt, anim, emo, player));
                    }
                }
            } catch (Exception e) {
                Notaloneanymore.LOGGER.error("Ошибка парсинга чата: " + e.getMessage());
            }
        });
    }
}