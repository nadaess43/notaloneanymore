package com.nadaess.notaloneanymore.ai.brain.tasks;

import com.nadaess.notaloneanymore.ai.state.VillagerAiState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Map;
import java.util.Queue;

/**
 * Конвейер выполнения атомарных примитивов (Voyager-лайк).
 * Каждый тик: peek() → executePrimitiveTick() → если done → poll().
 */
public class ExecuteAiOrderTask {

    public static void tickPhysicalMovement(ServerLevel level, Villager villager, VillagerAiState state) {
        Queue<AtomicAction> queue = state.getActionQueue();

        // Если пазлов нет — моб отдыхает
        if (queue.isEmpty()) {
            return;
        }

        // Смотрим на текущий первый кусочек пазла, не удаляя его из очереди
        AtomicAction currentAction = queue.peek();
        if (currentAction == null) return;

        // Выполняем тик этого примитива. Если он закончился -> возвращает true
        boolean isPrimitiveDone = executePrimitiveTick(level, villager, state, currentAction);

        if (isPrimitiveDone) {
            queue.poll(); // Кусочек выполнен, выкидываем его из очереди
            state.setActionInitialized(false); // Сбрасываем флаг для следующего кусочка
        }
    }

    // ========================================================================
    // ВСПОМОГАТЕЛЬНЫЙ МЕТОД для безопасного чтения String из params
    // ========================================================================

    private static String getStrParam(Map<String, Object> params, String key, String def) {
        if (params == null || !params.containsKey(key)) return def;
        Object val = params.get(key);
        return val != null ? val.toString() : def;
    }

    // ========================================================================
    // ЕЖЕТИКОВАЯ ЛОГИКА ПРИМИТИВОВ
    // ========================================================================

    private static boolean executePrimitiveTick(ServerLevel level, Villager villager, VillagerAiState state, AtomicAction action) {
        String name = action.action();
        Map<String, Object> p = action.params();

        // Проверяем первый запуск примитива (нужно для старта навигации или таймеров)
        if (!state.isActionInitialized()) {
            initPrimitive(villager, state, action);
            state.setActionInitialized(true);
        }

        // ====================================================================
        // 1. ЛОКОМОЦИЯ
        // ====================================================================
        switch (name) {

            // --- wait ---
            case "wait" -> {
                state.setActionTimer(state.getActionTimer() - 1);
                return state.getActionTimer() <= 0;
            }

            // --- walk_to ---
            case "walk_to" -> {
                int x = action.getIntParam("x", villager.getBlockX());
                int y = action.getIntParam("y", villager.getBlockY());
                int z = action.getIntParam("z", villager.getBlockZ());
                BlockPos target = new BlockPos(x, y, z);
                if (!villager.getNavigation().isInProgress() || villager.blockPosition().closerThan(target, 1.5)) {
                    villager.getNavigation().stop();
                    return true;
                }
                return false;
            }

            // --- step_forward ---
            case "step_forward" -> {
                int n = Math.max(1, action.getIntParam("n", 1));
                villager.setDeltaMovement(villager.getDeltaMovement().add(
                        villager.getForward().scale(0.1 * n)));
                return true;
            }

            // --- step_back ---
            case "step_back" -> {
                int n = Math.max(1, action.getIntParam("n", 1));
                villager.setDeltaMovement(villager.getDeltaMovement().add(
                        villager.getForward().scale(-0.1 * n)));
                return true;
            }

            // --- strafe ---
            case "strafe" -> {
                String dir = getStrParam(p, "dir", "left");
                int n = Math.max(1, action.getIntParam("n", 1));
                double side = dir.equalsIgnoreCase("right") ? 0.1 * n : -0.1 * n;
                villager.setDeltaMovement(villager.getDeltaMovement().add(
                        villager.getLookAngle().yRot(90).scale(side)));
                return true;
            }

            // --- stop_moving ---
            case "stop_moving" -> {
                villager.getNavigation().stop();
                villager.setDeltaMovement(0, villager.getDeltaMovement().y, 0);
                return true;
            }

            // --- jump ---
            case "jump" -> {
                if (villager.onGround()) {
                    villager.getJumpControl().jump();
                }
                return true;
            }

            // --- toggle_sprint ---
            case "toggle_sprint" -> {
                boolean sprint = action.getIntParam("value", 1) == 1;
                villager.setSprinting(sprint);
                return true;
            }

            // --- toggle_sneak ---
            case "toggle_sneak" -> {
                boolean sneak = action.getIntParam("value", 1) == 1;
                villager.setShiftKeyDown(sneak);
                return true;
            }

            // --- toggle_swim ---
            case "toggle_swim" -> {
                boolean swim = action.getIntParam("value", 1) == 1;
                villager.setSwimming(swim);
                return true;
            }

            // --- climb ---
            case "climb" -> {
                String dir = getStrParam(p, "dir", "up");
                double speed = dir.equalsIgnoreCase("down") ? -0.2 : 0.2;
                villager.setDeltaMovement(villager.getDeltaMovement().x, speed, villager.getDeltaMovement().z);
                return true;
            }

            // --- mount ---
            case "mount" -> {
                // mount требует entity target — пока заглушка
                return true;
            }

            // --- dismount ---
            case "dismount" -> {
                villager.stopRiding();
                return true;
            }

            // ====================================================================
            // 2. ОРИЕНТАЦИЯ И ПОЗА
            // ====================================================================

            // --- turn_body_to ---
            case "turn_body_to" -> {
                int x = action.getIntParam("x", villager.getBlockX());
                int z = action.getIntParam("z", villager.getBlockZ());
                double dx = x + 0.5 - villager.getX();
                double dz = z + 0.5 - villager.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                villager.setYRot(yaw);
                villager.setYHeadRot(yaw);
                return true;
            }

            // --- look_at ---
            case "look_at" -> {
                int x = action.getIntParam("x", villager.getBlockX());
                int y = action.getIntParam("y", villager.getBlockY());
                int z = action.getIntParam("z", villager.getBlockZ());
                villager.getLookControl().setLookAt(x + 0.5, y + 0.5, z + 0.5, 10.0F, 10.0F);
                return true;
            }

            // --- face_direction ---
            case "face_direction" -> {
                String compass = getStrParam(p, "compass", "north");
                float yaw = switch (compass.toLowerCase()) {
                    case "south" -> 180.0F;
                    case "east" -> 90.0F;
                    case "west" -> -90.0F;
                    default -> 0.0F; // north
                };
                villager.setYRot(yaw);
                villager.setYHeadRot(yaw);
                return true;
            }

            // --- sit / stand ---
            case "sit" -> {
                villager.setPose(net.minecraft.world.entity.Pose.SITTING);
                return true;
            }
            case "stand" -> {
                villager.setPose(net.minecraft.world.entity.Pose.STANDING);
                return true;
            }

            // --- lie_down / get_up ---
            case "lie_down" -> {
                villager.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                return true;
            }
            case "get_up" -> {
                villager.setPose(net.minecraft.world.entity.Pose.STANDING);
                return true;
            }

            // --- crouch / uncrouch ---
            case "crouch" -> {
                villager.setShiftKeyDown(true);
                return true;
            }
            case "uncrouch" -> {
                villager.setShiftKeyDown(false);
                return true;
            }

            // ====================================================================
            // 3. БЛОКИ
            // ====================================================================

            // --- break_block ---
            case "break_block" -> {
                int x = action.getIntParam("x", villager.getBlockX());
                int y = action.getIntParam("y", villager.getBlockY());
                int z = action.getIntParam("z", villager.getBlockZ());
                BlockPos bp = new BlockPos(x, y, z);
                if (level.getBlockState(bp).getDestroySpeed(level, bp) >= 0) {
                    level.destroyBlock(bp, true, villager);
                }
                return true;
            }

            // --- place_block ---
            case "place_block" -> {
                // place_block требует item в params — пока заглушка
                return true;
            }

            // --- use_block ---
            case "use_block" -> {
                // use_block требует Player — пока заглушка
                return true;
            }

            // --- toggle_door ---
            case "toggle_door" -> {
                // toggle_door требует Player — пока заглушка
                return true;
            }

            // --- pickup_item ---
            case "pickup_item" -> {
                // pickup_item требует Player — пока заглушка
                return true;
            }

            // ====================================================================
            // 4. СУЩНОСТИ
            // ====================================================================

            // --- hit_entity ---
            case "hit_entity" -> {
                if (state.getTargetEntity() != null) {
                    villager.doHurtTarget(level, state.getTargetEntity());
                }
                return true;
            }

            // --- give_item ---
            case "give_item" -> {
                // give_item требует item/count — пока заглушка
                return true;
            }

            // --- take_item ---
            case "take_item" -> {
                // take_item требует item/count — пока заглушка
                return true;
            }

            // --- pet ---
            case "pet" -> {
                if (state.getTargetEntity() != null) {
                    level.broadcastEntityEvent(villager, (byte) 15);
                }
                return true;
            }

            // --- hug / kiss ---
            case "hug" -> {
                if (state.getTargetEntity() != null) {
                    villager.lookAt(state.getTargetEntity(), 90.0F, 90.0F);
                    level.broadcastEntityEvent(villager, (byte) 15);
                }
                return true;
            }
            case "kiss" -> {
                if (state.getTargetEntity() != null) {
                    villager.lookAt(state.getTargetEntity(), 90.0F, 90.0F);
                    level.broadcastEntityEvent(villager, (byte) 15);
                }
                return true;
            }

            // --- push / pull ---
            case "push" -> {
                if (state.getTargetEntity() != null) {
                    state.getTargetEntity().push(villager);
                }
                return true;
            }
            case "pull" -> {
                if (state.getTargetEntity() != null) {
                    state.getTargetEntity().setDeltaMovement(
                            villager.position().subtract(state.getTargetEntity().position()).scale(0.1));
                }
                return true;
            }

            // --- heal_entity ---
            case "heal_entity" -> {
                if (state.getTargetEntity() != null) {
                    state.getTargetEntity().heal(4.0F);
                }
                return true;
            }

            // ====================================================================
            // 5. ИНВЕНТАРЬ
            // ====================================================================

            // --- equip ---
            case "equip" -> {
                // equip требует item/slot — пока заглушка
                return true;
            }

            // --- unequip ---
            case "unequip" -> {
                // unequip требует slot — пока заглушка
                return true;
            }

            // --- drop_item ---
            case "drop_item" -> {
                int count = Math.max(1, action.getIntParam("count", 1));
                for (int i = 0; i < count; i++) {
                    villager.spawnAtLocation(level, villager.getMainHandItem().copy());
                }
                villager.getMainHandItem().shrink(count);
                return true;
            }

            // --- store_item / retrieve_item ---
            case "store_item" -> {
                // store_item требует item/count/container — пока заглушка
                return true;
            }
            case "retrieve_item" -> {
                // retrieve_item требует item/count/container — пока заглушка
                return true;
            }

            // --- swap_hand ---
            case "swap_hand" -> {
                net.minecraft.world.item.ItemStack main = villager.getMainHandItem();
                net.minecraft.world.item.ItemStack off = villager.getOffhandItem();
                villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, off);
                villager.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, main);
                return true;
            }

            // ====================================================================
            // 6. РЕЧЬ
            // ====================================================================

            // --- say ---
            case "say" -> {
                String text = getStrParam(p, "text", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal(text), false);
                return true;
            }

            // --- speak_to ---
            case "speak_to" -> {
                String text = getStrParam(p, "text", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§e[говорит] §f" + text), false);
                return true;
            }

            // --- greet ---
            case "greet" -> {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§eПриветствую!"), false);
                return true;
            }

            // --- ask ---
            case "ask" -> {
                String topic = getStrParam(p, "topic", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§e[спрашивает] §f" + topic), false);
                return true;
            }

            // --- exclaim ---
            case "exclaim" -> {
                String text = getStrParam(p, "text", "Эй!");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§6" + text), false);
                return true;
            }

            // --- shout ---
            case "shout" -> {
                String text = getStrParam(p, "text", "АУ!");
                for (net.minecraft.server.level.ServerPlayer pl : level.players()) {
                    if (pl.distanceToSqr(villager) < 400.0) {
                        pl.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "§c[крик] " + villager.getName().getString() + ": " + text));
                    }
                }
                return true;
            }

            // --- whisper ---
            case "whisper" -> {
                String text = getStrParam(p, "text", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("§7[шёпот] §f" + text), false);
                return true;
            }

            // ====================================================================
            // 7. ВОСПРИЯТИЕ (сенсоры — заглушки)
            // ====================================================================

            case "glance" -> { return true; }
            case "request_screenshot" -> { return true; }
            case "listen" -> { return true; }
            case "scan_area" -> { return true; }

            // ====================================================================
            // 8. ВНУТРЕННИЕ/КОГНИТИВНЫЕ
            // ====================================================================

            case "recall" -> { return true; }
            case "adjust_opinion" -> { return true; }
            case "flag_trauma" -> { return true; }
            case "set_goal" -> { return true; }
            case "log_thought" -> {
                String text = getStrParam(p, "text", "...");
                state.addMessageToHistory("мысль", text);
                return true;
            }
            case "decide_lie" -> { return true; }
            case "forget" -> { return true; }

            // ====================================================================
            // 9. ЭМОЦИИ И АНИМАЦИИ ТЕЛА
            // ====================================================================

            case "nod" -> {
                villager.setYHeadRot(villager.getYHeadRot() + 15);
                return true;
            }
            case "shake_head" -> {
                villager.setYHeadRot(villager.getYHeadRot() - 15);
                return true;
            }
            case "shrug" -> {
                level.broadcastEntityEvent(villager, (byte) 15);
                return true;
            }
            case "flinch" -> {
                villager.hurt(level.damageSources().generic(), 0.01F);
                return true;
            }
            case "laugh" -> {
                level.broadcastEntityEvent(villager, (byte) 15);
                return true;
            }
            case "cry" -> {
                level.broadcastEntityEvent(villager, (byte) 15);
                return true;
            }
            case "tremble" -> {
                villager.setDeltaMovement(
                        (villager.getRandom().nextDouble() - 0.5) * 0.1,
                        villager.getDeltaMovement().y,
                        (villager.getRandom().nextDouble() - 0.5) * 0.1);
                return true;
            }
            case "facepalm" -> {
                level.broadcastEntityEvent(villager, (byte) 15);
                return true;
            }
            case "spawn_particles" -> {
                String type = getStrParam(p, "type", "happy");
                net.minecraft.core.particles.ParticleOptions particle = switch (type) {
                    case "angry" -> net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER;
                    case "heart" -> net.minecraft.core.particles.ParticleTypes.HEART;
                    case "question" -> net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER;
                    default -> net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER;
                };
                level.sendParticles(particle,
                        villager.getX(), villager.getY() + 2, villager.getZ(),
                        5, 0.3, 0.3, 0.3, 0);
                return true;
            }

            // ====================================================================
            // 10. МЕТА-ДЕЙСТВИЯ
            // ====================================================================

            case "idle" -> { return true; }
            case "cancel_current" -> {
                villager.getNavigation().stop();
                state.setActionTimer(0);
                return true;
            }
            case "repeat" -> { return true; }

            // ====================================================================
            // НЕИЗВЕСТНЫЙ ПРИМИТИВ
            // ====================================================================
            default -> { return true; }
        }
    }

    // ========================================================================
    // ОДНОРАЗОВАЯ ИНИЦИАЛИЗАЦИЯ ПРИМИТИВА
    // ========================================================================

    private static void initPrimitive(Villager villager, VillagerAiState state, AtomicAction action) {
        String name = action.action();

        switch (name) {
            case "wait" -> {
                int ticks = action.getIntParam("ticks", 20);
                state.setActionTimer(ticks);
            }
            case "walk_to" -> {
                int x = action.getIntParam("x", villager.getBlockX());
                int y = action.getIntParam("y", villager.getBlockY());
                int z = action.getIntParam("z", villager.getBlockZ());
                villager.getNavigation().moveTo(x, y, z, 0.5);
            }
            default -> {}
        }
    }
}
