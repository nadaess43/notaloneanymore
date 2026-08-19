package com.nadaess.notaloneanymore.item;

import com.nadaess.notaloneanymore.Notaloneanymore;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public class ModItems {

    public static final Identifier SPAWN_EGG_ID = Identifier.fromNamespaceAndPath(Notaloneanymore.MOD_ID, "companion_spawn_egg");

    public static final Item COMPANION_SPAWN_EGG = Registry.register(
            BuiltInRegistries.ITEM,
            SPAWN_EGG_ID,
            new CompanionSpawnEggItem(new Item.Properties()
                    .stacksTo(64)
                    .rarity(Rarity.UNCOMMON)
            )
    );

    public static void register() {
        Notaloneanymore.LOGGER.info("Registering item {}", SPAWN_EGG_ID);
        // Креатив-таб добавим позже (fabric-item-group-api-v1), пока доступен через /give
    }
}
