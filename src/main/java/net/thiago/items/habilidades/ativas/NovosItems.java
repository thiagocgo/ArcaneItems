package net.thiago.items.habilidades.ativas;

import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.thiago.items.ClasseGeral;
import net.thiago.items.armaduras.ModArmorMaterials;
import net.thiago.items.guild.GuildContractItem;

public class NovosItems {
    // Cria o registro usando o mod id definido em ClasseGeral
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ClasseGeral.MOD_ID);

    // Registra os itens usando a API do NeoForge (DeferredItem)

    public static final DeferredItem<DashWandItem> DASHITEM = ITEMS.register("dash_wand",
            () -> new DashWandItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ResistanceShieldItem> SHIELDITEM = ITEMS.register("resistance_shield",
            () -> new ResistanceShieldItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> TRUEINVISIBILITY = ITEMS.register("true_invisibility",
            () -> new TrueInvisibilityItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<LifeSigilItem> LIFESIGIL = ITEMS.register("life_sigil",
            () -> new LifeSigilItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<SpeedRuneItem> SPEEDRUNE = ITEMS.register("speed_rune",
            () -> new SpeedRuneItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ArcaneQuiverItem> ARCANE_QUIVER = ITEMS.register("arcane_quiver",
            () -> new ArcaneQuiverItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<RageOrbItem> RAGE_ORB = ITEMS.register("rage_orb",
            () -> new RageOrbItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ArcaneGuiItem> ARCANE_GUI = ITEMS.register("arcane_gui",
            () -> new ArcaneGuiItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> DRAGON_ORB = ITEMS.register("dragon_orb",
            () -> new DragonOrbItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> ELYTRIAN_POUCH = ITEMS.register("elytrian_pouch",
            () -> new ElytrianPouchItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> BAT_ESSENCE = ITEMS.register("bat_essence",
            () -> new ThunderHammerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> NECROMANCER_ORB = ITEMS.register("necromancer_orb",
            () -> new NecromancerOrbItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<Item> VIP_INGOT = ITEMS.register("vip_ingot",
            () -> new Item(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<GuildContractItem> GUILD_CONTRACT_IRON = ITEMS.register("guild_contract_iron",
            () -> new GuildContractItem(new Item.Properties().stacksTo(1), 1, 1, 5, -1)); // -1 para todos os blocos
    public static final DeferredItem<GuildContractItem> GUILD_CONTRACT_GOLD = ITEMS.register("guild_contract_gold",
            () -> new GuildContractItem(new Item.Properties().stacksTo(1), 3, 3, 10, 342));
    public static final DeferredItem<GuildContractItem> GUILD_CONTRACT_DIAMOND = ITEMS.register("guild_contract_diamond",
            () -> new GuildContractItem(new Item.Properties().stacksTo(1), 5, 5, 20, 506));


    public static final DeferredItem<ArmorItem> VIP_HELMET = ITEMS.register("vip_helmet",
            () -> new ArmorItem(ModArmorMaterials.VIP_ARMOR_MATERIAL, ArmorItem.Type.HELMET,
                    new Item.Properties().durability(ArmorItem.Type.HELMET.getDurability(99))));
    public static final DeferredItem<ArmorItem> VIP_CHESTPLATE = ITEMS.register("vip_chestplate",
            () -> new ArmorItem(ModArmorMaterials.VIP_ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE,
                    new Item.Properties().durability(ArmorItem.Type.CHESTPLATE.getDurability(99))));
    public static final DeferredItem<ArmorItem> VIP_LEGGINGS = ITEMS.register("vip_leggings",
            () -> new ArmorItem(ModArmorMaterials.VIP_ARMOR_MATERIAL, ArmorItem.Type.LEGGINGS,
                    new Item.Properties().durability(ArmorItem.Type.LEGGINGS.getDurability(99))));
    public static final DeferredItem<ArmorItem> VIP_BOOTS = ITEMS.register("vip_boots",
            () -> new ArmorItem(ModArmorMaterials.VIP_ARMOR_MATERIAL, ArmorItem.Type.BOOTS,
                    new Item.Properties().durability(ArmorItem.Type.BOOTS.getDurability(99))));



    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}


