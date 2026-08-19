package com.nadaess.notaloneanymore.item;

import com.nadaess.notaloneanymore.entity.CompanionEntity;
import com.nadaess.notaloneanymore.entity.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;

import java.util.function.Consumer;

/**
 * Яйцо призыва компаньона — кастомная реализация без привязки к vanilla SpawnEggItem (API поменялся в 26.2).
 * Текстура 16x16: красный TNT-стиль с желтой эмблемой.
 */
public class CompanionSpawnEggItem extends Item {

    public CompanionSpawnEggItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        Direction dir = context.getClickedFace();
        BlockPos spawnPos = pos.relative(dir);

        // Если блок на котором кликнули — воздух? relative уже сместил
        CompanionEntity companion = new CompanionEntity(ModEntities.COMPANION, serverLevel);
        // Спавн чуть выше блока
        companion.setPos(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
        companion.setYRot(context.getPlayer() != null ? context.getPlayer().getYRot() : 0);

        // Даем рандомное имя если не задано NBT? Пока дефолт
        companion.setCustomName(Component.literal("Компаньон"));
        companion.setCustomNameVisible(true);
        companion.setPersistenceRequired();

        // Спавн
        serverLevel.addFreshEntityWithPassengers(companion);
        serverLevel.gameEvent(context.getPlayer(), GameEvent.ENTITY_PLACE, spawnPos);
        serverLevel.playSound(null, spawnPos, SoundEvents.EGG_THROW, SoundSource.BLOCKS, 1.0F, 1.0F);

        ItemStack stack = context.getItemInHand();
        if (context.getPlayer() != null && !context.getPlayer().hasInfiniteMaterials()) {
            stack.shrink(1);
        }

        // Finalize spawn (инициализация генома произойдёт в tick)
        companion.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(spawnPos), EntitySpawnReason.SPAWN_ITEM_USE, null);

        return InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.literal("§7Призывает ИИ-компаньона"));
        tooltip.accept(Component.literal("§7(TNT-куб, пока без модели)"));
        tooltip.accept(Component.literal("§eПКМ по блоку — спавн"));
        tooltip.accept(Component.literal("§8Геном и память уникальны"));
    }
}
