package com.mikkeljck.chestshield;

import com.mikkeljck.chestshield.block.CofrePersonalBlock;
import com.mikkeljck.chestshield.comando.ComandoCofre;
import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.red.RedCofres;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CofresPersonales implements ModInitializer {
	public static final String MOD_ID = "chest_shield";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	// --- Claves de registro (obligatorias desde 1.21.2+) ---
	public static final ResourceKey<Block> COFRE_PERSONAL_BLOCK_KEY =
			ResourceKey.create(Registries.BLOCK, id("shielded_chest"));
	public static final ResourceKey<Item> COFRE_PERSONAL_ITEM_KEY =
			ResourceKey.create(Registries.ITEM, id("shielded_chest"));
	public static final ResourceKey<Item> LLAVE_MAESTRA_KEY =
			ResourceKey.create(Registries.ITEM, id("master_key"));

	// --- Bloque: Cofre Personal ---
	// Propiedades explicitas: NO se copian las del cofre vanilla porque ese
	// arrastra ignitedByLava (la lava lo puede prender) y otras cosas que no
	// queremos en un bloque que debe ser indestructible.
	public static final Block COFRE_PERSONAL = new CofrePersonalBlock(
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.METAL)
					.instrument(NoteBlockInstrument.BASS)
					.sound(SoundType.METAL)
					.strength(2.5F, 3600000.0F) // dureza 2.5, inmune a explosiones
					.pushReaction(PushReaction.BLOCK) // los pistones no lo mueven ni lo destruyen
					.setId(COFRE_PERSONAL_BLOCK_KEY)
	);

	// --- Item: Llave Maestra ---
	public static final Item LLAVE_MAESTRA = new Item(
			new Item.Properties()
					.stacksTo(1)
					.setId(LLAVE_MAESTRA_KEY)
	);

	// --- BlockEntity ---
	public static final BlockEntityType<CofrePersonalBlockEntity> COFRE_PERSONAL_BE = Registry.register(
			BuiltInRegistries.BLOCK_ENTITY_TYPE,
			id("shielded_chest"),
			FabricBlockEntityTypeBuilder.create(CofrePersonalBlockEntity::new, COFRE_PERSONAL).build()
	);

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.BLOCK, COFRE_PERSONAL_BLOCK_KEY, COFRE_PERSONAL);

		Registry.register(BuiltInRegistries.ITEM, COFRE_PERSONAL_ITEM_KEY,
				new BlockItem(COFRE_PERSONAL, new Item.Properties().useBlockDescriptionPrefix().setId(COFRE_PERSONAL_ITEM_KEY)));

		Registry.register(BuiltInRegistries.ITEM, LLAVE_MAESTRA_KEY, LLAVE_MAESTRA);

		// Pestanas del modo creativo
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS)
				.register(output -> output.accept(COFRE_PERSONAL));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES)
				.register(output -> output.accept(LLAVE_MAESTRA));

		// Bloqueo de rotura: cubre tambien modo creativo, donde getDestroyProgress no aplica
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (blockEntity instanceof CofrePersonalBlockEntity cofre && !cofre.puedeAcceder(player)) {
				cofre.avisarPropiedad(player);
				return false;
			}
			return true;
		});

		// El canal de red se registra en codigo comun a proposito: owo exige que
		// cliente y servidor declaren los mismos paquetes, y ademas SOLO permite
		// registrarlos durante la inicializacion del mod. Si esta llamada falta,
		// la clase RedCofres se inicializa tarde (al enviar el primer paquete) y
		// owo lanza ServicesFrozenException.
		RedCofres.inicializar();

		// Comando de administracion y pruebas. Ver ComandoCofre.
		CommandRegistrationCallback.EVENT.register(
				(despachador, acceso, entorno) -> ComandoCofre.registrar(despachador));

		LOGGER.info("Mod Cofres Personales inicializado correctamente");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
