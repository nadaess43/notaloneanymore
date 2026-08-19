package com.nadaess.notaloneanymore.ai.brain.tasks;

import com.nadaess.notaloneanymore.entity.CompanionAiState;
import com.nadaess.notaloneanymore.entity.CompanionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.Queue;

/**
 * РљРѕРЅРІРµР№РµСЂ РІС‹РїРѕР»РЅРµРЅРёСЏ Р°С‚РѕРјР°СЂРЅС‹С… РїСЂРёРјРёС‚РёРІРѕРІ (Voyager-Р»Р°Р№Рє) РґР»СЏ CompanionEntity.
 * РљР°Р¶РґС‹Р№ С‚РёРє: peek() в†’ executePrimitiveTick() в†’ РµСЃР»Рё done в†’ poll().
 */
public class ExecuteAiOrderTask {

    public static void tickPhysicalMovement(ServerLevel level, CompanionEntity companion, CompanionAiState state) {
        Queue<AtomicAction> queue = state.getActionQueue();

        if (queue.isEmpty()) {
            return;
        }

        AtomicAction currentAction = queue.peek();
        if (currentAction == null) return;

        boolean isPrimitiveDone = executePrimitiveTick(level, companion, state, currentAction);

        if (isPrimitiveDone) {
            queue.poll();
            state.setActionInitialized(false);
        }
    }

    private static String getStrParam(Map<String, Object> params, String key, String def) {
        if (params == null || !params.containsKey(key)) return def;
        Object val = params.get(key);
        return val != null ? val.toString() : def;
    }

    private static boolean executePrimitiveTick(ServerLevel level, CompanionEntity companion, CompanionAiState state, AtomicAction action) {
        String name = action.action();
        Map<String, Object> p = action.params();

        if (!state.isActionInitialized()) {
            initPrimitive(companion, state, action);
            state.setActionInitialized(true);
        }

        switch (name) {

            case "wait" -> {
                state.setActionTimer(state.getActionTimer() - 1);
                return state.getActionTimer() <= 0;
            }

            case "walk_to" -> {
                int x = action.getIntParam("x", companion.getBlockX());
                int y = action.getIntParam("y", companion.getBlockY());
                int z = action.getIntParam("z", companion.getBlockZ());
                BlockPos target = new BlockPos(x, y, z);
                if (!companion.getNavigation().isInProgress() || companion.blockPosition().closerThan(target, 1.5)) {
                    companion.getNavigation().stop();
                    return true;
                }
                return false;
            }

            case "step_forward" -> {
                int n = Math.max(1, action.getIntParam("n", 1));
                companion.setDeltaMovement(companion.getDeltaMovement().add(
                        companion.getForward().scale(0.1 * n)));
                companion.hurtMarked = true;
                return true;
            }

            case "step_back" -> {
                int n = Math.max(1, action.getIntParam("n", 1));
                companion.setDeltaMovement(companion.getDeltaMovement().add(
                        companion.getForward().scale(-0.1 * n)));
                companion.hurtMarked = true;
                return true;
            }

            case "strafe" -> {
                String dir = getStrParam(p, "dir", "left");
                int n = Math.max(1, action.getIntParam("n", 1));
                double side = dir.equalsIgnoreCase("right") ? 0.1 * n : -0.1 * n;
                companion.setDeltaMovement(companion.getDeltaMovement().add(
                        companion.getLookAngle().yRot(90).scale(side)));
                companion.hurtMarked = true;
                return true;
            }

            case "stop_moving" -> {
                companion.getNavigation().stop();
                companion.setDeltaMovement(0, companion.getDeltaMovement().y, 0);
                return true;
            }

            case "jump" -> {
                if (companion.onGround()) {
                    companion.getJumpControl().jump();
                }
                return true;
            }

            case "toggle_sprint" -> {
                boolean sprint = action.getIntParam("value", 1) == 1;
                companion.setSprinting(sprint);
                return true;
            }

            case "toggle_sneak" -> {
                boolean sneak = action.getIntParam("value", 1) == 1;
                companion.setShiftKeyDown(sneak);
                return true;
            }

            case "toggle_swim" -> {
                boolean swim = action.getIntParam("value", 1) == 1;
                companion.setSwimming(swim);
                return true;
            }

            case "climb" -> {
                String dir = getStrParam(p, "dir", "up");
                double speed = dir.equalsIgnoreCase("down") ? -0.2 : 0.2;
                companion.setDeltaMovement(companion.getDeltaMovement().x, speed, companion.getDeltaMovement().z);
                companion.hurtMarked = true;
                return true;
            }

            case "mount" -> {
                return true;
            }

            case "dismount" -> {
                companion.stopRiding();
                return true;
            }

            case "turn_body_to" -> {
                int x = action.getIntParam("x", companion.getBlockX());
                int z = action.getIntParam("z", companion.getBlockZ());
                double dx = x + 0.5 - companion.getX();
                double dz = z + 0.5 - companion.getZ();
                float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
                companion.setYRot(yaw);
                companion.setYHeadRot(yaw);
                return true;
            }

            case "look_at" -> {
                int x = action.getIntParam("x", companion.getBlockX());
                int y = action.getIntParam("y", companion.getBlockY());
                int z = action.getIntParam("z", companion.getBlockZ());
                companion.getLookControl().setLookAt(x + 0.5, y + 0.5, z + 0.5, 10.0F, 10.0F);
                return true;
            }

            case "face_direction" -> {
                String compass = getStrParam(p, "compass", "north");
                float yaw = switch (compass.toLowerCase()) {
                    case "south" -> 180.0F;
                    case "east" -> 90.0F;
                    case "west" -> -90.0F;
                    default -> 0.0F;
                };
                companion.setYRot(yaw);
                companion.setYHeadRot(yaw);
                return true;
            }

            case "sit" -> {
                companion.setPose(net.minecraft.world.entity.Pose.SITTING);
                return true;
            }
            case "stand" -> {
                companion.setPose(net.minecraft.world.entity.Pose.STANDING);
                return true;
            }

            case "lie_down" -> {
                companion.setPose(net.minecraft.world.entity.Pose.SLEEPING);
                return true;
            }
            case "get_up" -> {
                companion.setPose(net.minecraft.world.entity.Pose.STANDING);
                return true;
            }

            case "crouch" -> {
                companion.setShiftKeyDown(true);
                return true;
            }
            case "uncrouch" -> {
                companion.setShiftKeyDown(false);
                return true;
            }

            case "break_block" -> {
                int x = action.getIntParam("x", companion.getBlockX());
                int y = action.getIntParam("y", companion.getBlockY());
                int z = action.getIntParam("z", companion.getBlockZ());
                BlockPos bp = new BlockPos(x, y, z);
                if (level.getBlockState(bp).getDestroySpeed(level, bp) >= 0) {
                    level.destroyBlock(bp, true, companion);
                }
                return true;
            }

            case "place_block" -> {
                return true;
            }

            case "use_block" -> {
                return true;
            }

            case "toggle_door" -> {
                int x = action.getIntParam("x", companion.getBlockX());
                int y = action.getIntParam("y", companion.getBlockY());
                int z = action.getIntParam("z", companion.getBlockZ());
                BlockPos pos = new BlockPos(x, y, z);
                net.minecraft.world.level.block.state.BlockState blockState = level.getBlockState(pos);
                if (blockState.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door) {
                    boolean isOpen = blockState.getValue(net.minecraft.world.level.block.DoorBlock.OPEN);
                    door.setOpen(companion, level, blockState, pos, !isOpen);
                    companion.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                }
                return true;
            }

            case "pickup_item" -> {
                java.util.List<net.minecraft.world.entity.item.ItemEntity> items = level.getEntitiesOfClass(
                    net.minecraft.world.entity.item.ItemEntity.class,
                    companion.getBoundingBox().inflate(2.0)
                );
                if (!items.isEmpty()) {
                    net.minecraft.world.entity.item.ItemEntity itemEntity = items.get(0);
                    net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
                    // Р”РѕР±Р°РІР»СЏРµРј РІ РёРЅРІРµРЅС‚Р°СЂСЊ РєРѕРјРїР°РЅСЊРѕРЅР° (8 СЃР»РѕС‚РѕРІ)
                    companion.getInventory().addItem(stack);
                    companion.take(itemEntity, stack.getCount());
                    itemEntity.discard();
                    companion.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
                }
                return true;
            }

            case "hit_entity" -> {
                if (state.getTargetEntity() != null) {
                    companion.doHurtTarget(level, state.getTargetEntity());
                }
                return true;
            }

            case "give_item" -> {
                return true;
            }

            case "take_item" -> {
                return true;
            }

            case "pet" -> {
                if (state.getTargetEntity() != null) {
                    level.broadcastEntityEvent(companion, (byte) 15);
                }
                return true;
            }

            case "hug" -> {
                if (state.getTargetEntity() != null) {
                    companion.lookAt(state.getTargetEntity(), 90.0F, 90.0F);
                    level.broadcastEntityEvent(companion, (byte) 15);
                }
                return true;
            }
            case "kiss" -> {
                if (state.getTargetEntity() != null) {
                    companion.lookAt(state.getTargetEntity(), 90.0F, 90.0F);
                    level.broadcastEntityEvent(companion, (byte) 15);
                }
                return true;
            }

            case "push" -> {
                if (state.getTargetEntity() != null) {
                    state.getTargetEntity().push(companion);
                }
                return true;
            }
            case "pull" -> {
                if (state.getTargetEntity() != null) {
                    state.getTargetEntity().setDeltaMovement(
                            companion.position().subtract(state.getTargetEntity().position()).scale(0.1));
                }
                return true;
            }

            case "heal_entity" -> {
                if (state.getTargetEntity() != null) {
                    state.getTargetEntity().heal(4.0F);
                }
                return true;
            }

            case "equip" -> {
                String itemId = getStrParam(p, "item", "minecraft:air");
                String slot = getStrParam(p, "slot", "main_hand");
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.Identifier.parse(itemId)).orElseThrow().value();
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
                if (slot.equals("main_hand")) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, stack);
                } else if (slot.equals("off_hand")) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, stack);
                } else if (slot.equals("head")) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, stack);
                } else if (slot.equals("chest")) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, stack);
                } else if (slot.equals("legs")) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, stack);
                } else if (slot.equals("feet")) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, stack);
                }
                return true;
            }

            case "unequip" -> {
                String slot = getStrParam(p, "slot", "main_hand");
                net.minecraft.world.entity.EquipmentSlot eqSlot = switch (slot) {
                    case "off_hand" -> net.minecraft.world.entity.EquipmentSlot.OFFHAND;
                    case "head" -> net.minecraft.world.entity.EquipmentSlot.HEAD;
                    case "chest" -> net.minecraft.world.entity.EquipmentSlot.CHEST;
                    case "legs" -> net.minecraft.world.entity.EquipmentSlot.LEGS;
                    case "feet" -> net.minecraft.world.entity.EquipmentSlot.FEET;
                    default -> net.minecraft.world.entity.EquipmentSlot.MAINHAND;
                };
                companion.setItemSlot(eqSlot, net.minecraft.world.item.ItemStack.EMPTY);
                return true;
            }

            case "drop_item" -> {
                String itemId = getStrParam(p, "item", "minecraft:wheat");
                int count = Math.max(1, action.getIntParam("count", 1));
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                    net.minecraft.resources.Identifier.parse(itemId)).orElseThrow().value();
                net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item, count);
                net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    level, companion.getX(), companion.getY() + 1.0, companion.getZ(), stack);
                drop.setDeltaMovement(companion.getLookAngle().scale(0.3));
                level.addFreshEntity(drop);
                if (companion.getMainHandItem().is(item)) {
                    companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, net.minecraft.world.item.ItemStack.EMPTY);
                }
                return true;
            }

            case "store_item" -> {
                return true;
            }
            case "retrieve_item" -> {
                return true;
            }

            case "swap_hand" -> {
                net.minecraft.world.item.ItemStack main = companion.getMainHandItem();
                net.minecraft.world.item.ItemStack off = companion.getOffhandItem();
                companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, off);
                companion.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, main);
                return true;
            }

            case "say" -> {
                String text = getStrParam(p, "text", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal(text), false);
                return true;
            }

            case "speak_to" -> {
                String text = getStrParam(p, "text", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("В§e[РіРѕРІРѕСЂРёС‚] В§f" + text), false);
                return true;
            }

            case "greet" -> {
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("В§eРџСЂРёРІРµС‚СЃС‚РІСѓСЋ!"), false);
                return true;
            }

            case "ask" -> {
                String topic = getStrParam(p, "topic", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("В§e[СЃРїСЂР°С€РёРІР°РµС‚] В§f" + topic), false);
                return true;
            }

            case "exclaim" -> {
                String text = getStrParam(p, "text", "Р­Р№!");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("В§6" + text), false);
                return true;
            }

            case "shout" -> {
                String text = getStrParam(p, "text", "РђРЈ!");
                for (net.minecraft.server.level.ServerPlayer pl : level.players()) {
                    if (pl.distanceToSqr(companion) < 400.0) {
                        pl.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                                "В§c[РєСЂРёРє] " + companion.getName().getString() + ": " + text));
                    }
                }
                return true;
            }

            case "whisper" -> {
                String text = getStrParam(p, "text", "...");
                level.getServer().getPlayerList().broadcastSystemMessage(
                        net.minecraft.network.chat.Component.literal("В§7[С€С‘РїРѕС‚] В§f" + text), false);
                return true;
            }

            case "glance" -> { return true; }
            case "request_screenshot" -> { return true; }
            case "listen" -> { return true; }
            case "scan_area" -> { return true; }

            case "recall" -> { return true; }
            case "adjust_opinion" -> { return true; }
            case "flag_trauma" -> { return true; }
            case "set_goal" -> { return true; }
            case "log_thought" -> {
                String text = getStrParam(p, "text", "...");
                state.addMessageToHistory("РјС‹СЃР»СЊ", text);
                return true;
            }
            case "decide_lie" -> { return true; }
            case "forget" -> { return true; }

            case "nod" -> {
                companion.setYHeadRot(companion.getYHeadRot() + 15);
                return true;
            }
            case "shake_head" -> {
                companion.setYHeadRot(companion.getYHeadRot() - 15);
                return true;
            }
            case "shrug" -> {
                level.broadcastEntityEvent(companion, (byte) 15);
                return true;
            }
            case "flinch" -> {
                companion.hurt(level.damageSources().generic(), 0.01F);
                return true;
            }
            case "laugh" -> {
                level.broadcastEntityEvent(companion, (byte) 15);
                return true;
            }
            case "cry" -> {
                level.broadcastEntityEvent(companion, (byte) 15);
                return true;
            }
            case "tremble" -> {
                companion.setDeltaMovement(
                        (companion.getRandom().nextDouble() - 0.5) * 0.1,
                        companion.getDeltaMovement().y,
                        (companion.getRandom().nextDouble() - 0.5) * 0.1);
                companion.hurtMarked = true;
                return true;
            }
            case "facepalm" -> {
                level.broadcastEntityEvent(companion, (byte) 15);
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
                        companion.getX(), companion.getY() + 1.0, companion.getZ(),
                        5, 0.3, 0.3, 0.3, 0);
                return true;
            }

            case "idle" -> { return true; }
            case "cancel_current" -> {
                companion.getNavigation().stop();
                state.setActionTimer(0);
                return true;
            }
            case "repeat" -> { return true; }

            default -> { return true; }
        }
    }

    private static void initPrimitive(CompanionEntity companion, CompanionAiState state, AtomicAction action) {
        String name = action.action();

        switch (name) {
            case "wait" -> {
                int ticks = action.getIntParam("ticks", 20);
                state.setActionTimer(ticks);
            }
            case "walk_to" -> {
                int x = action.getIntParam("x", companion.getBlockX());
                int y = action.getIntParam("y", companion.getBlockY());
                int z = action.getIntParam("z", companion.getBlockZ());
                companion.getNavigation().moveTo(x, y, z, 0.5);
            }
            default -> {}
        }
    }
}

