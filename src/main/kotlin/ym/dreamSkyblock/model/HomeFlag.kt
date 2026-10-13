package ym.dreamSkyblock.model

enum class HomeFlag(
    val category: Category,
    val defaultValue: Boolean
) {
    PVP(Category.WORLD, false),
    MONSTER_SPAWN(Category.WORLD, true),
    ANIMAL_SPAWN(Category.WORLD, true),
    FLIGHT(Category.WORLD, false),
    WEATHER(Category.WORLD, false),
    FIRE_SPREAD(Category.WORLD, false),
    TNT_EXPLOSION(Category.WORLD, false),
    ENTITY_GRIEF(Category.WORLD, false),
    LIQUID_FLOW(Category.WORLD, true),
    PORTAL_NETHER(Category.WORLD, false),
    PORTAL_END(Category.WORLD, false),
    STRANGER_PLACE(Category.STRANGER, false),
    STRANGER_BREAK(Category.STRANGER, false),
    STRANGER_CONTAINER(Category.STRANGER, false),
    STRANGER_DOOR(Category.STRANGER, false),
    STRANGER_BUTTON(Category.STRANGER, false),
    STRANGER_PRESSURE_PLATE(Category.STRANGER, false),
    STRANGER_BLOCK_INTERACT(Category.STRANGER, false),
    STRANGER_ENTITY_INTERACT(Category.STRANGER, false),
    STRANGER_ATTACK_ANIMAL(Category.STRANGER, false),
    STRANGER_ATTACK_MONSTER(Category.STRANGER, false),
    STRANGER_PICKUP(Category.STRANGER, false),
    STRANGER_DROP(Category.STRANGER, false),
    STRANGER_BUCKET(Category.STRANGER, false),
    STRANGER_VEHICLE(Category.STRANGER, false);

    enum class Category { WORLD, STRANGER }
}
