package com.mikkeljck.chestshield.comando;

import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.proteccion.Ajustes;
import com.mikkeljck.chestshield.proteccion.Permisos;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * /chestshield — informacion del cofre al que estas MIRANDO.
 *
 * Sirve para moderar sin romper bloques: saber de quien es un cofre y como esta
 * configurado sin tener que picarlo ni pedirle la clave a nadie.
 *
 * Todo lo demas se configura desde la propia pantalla del cofre. Este comando
 * llego a tener subcomandos para permisos y tolvas, pero eran andamios para
 * poder probar antes de que existiera la interfaz, y se quitaron al llegar esta.
 */
public final class ComandoCofre {

	/** Alcance del rayo, en bloques. Un poco mas que el alcance normal de uso. */
	private static final double ALCANCE = 6.0;

	private static final SimpleCommandExceptionType NO_MIRAS_UN_COFRE =
			new SimpleCommandExceptionType(Component.translatable("command.chest_shield.no_miras_cofre"));

	private static final SimpleCommandExceptionType NO_ES_TUYO =
			new SimpleCommandExceptionType(Component.translatable("command.chest_shield.no_es_tuyo"));

	private ComandoCofre() {
	}

	public static void registrar(final CommandDispatcher<CommandSourceStack> despachador) {
		despachador.register(Commands.literal("chestshield")
				.executes(ComandoCofre::info)
				.then(Commands.literal("info").executes(ComandoCofre::info)));
	}

	private static int info(final CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		CofrePersonalBlockEntity cofre = cofreMirado(jugador);

		// El dueno y un administrador. Para el resto, el cofre ya dice de quien es
		// cuando intentan abrirlo; no hace falta un comando que lo detalle.
		if (!cofre.esPropietario(jugador) && !Permisos.esAdministrador(contexto.getSource())) {
			throw NO_ES_TUYO.create();
		}

		Ajustes ajustes = cofre.getProteccion().getAjustes();
		linea(contexto, Component.translatable("command.chest_shield.info_dueno",
				cofre.getNombreMostrado()));
		linea(contexto, Component.translatable("command.chest_shield.info_protegido",
				si(ajustes.estaProtegido())));
		linea(contexto, Component.translatable("command.chest_shield.info_clave",
				si(ajustes.tieneClave())));
		linea(contexto, Component.translatable("command.chest_shield.info_tolvas",
				si(ajustes.tolvasPuedenMeter()), si(ajustes.tolvasPuedenSacar())));

		var permisos = cofre.getPermisos();
		var pendientes = cofre.getProteccion().getPendientes();
		if (permisos.isEmpty() && pendientes.isEmpty()) {
			linea(contexto, Component.translatable("command.chest_shield.info_sin_permisos"));
			return 1;
		}

		linea(contexto, Component.translatable("command.chest_shield.info_permisos",
				permisos.size() + pendientes.size()));
		for (Ajustes.Permiso permiso : permisos) {
			linea(contexto, Component.literal("  - " + permiso.nombre()));
		}
		for (String pendiente : pendientes) {
			linea(contexto, Component.literal("  - ")
					.append(Component.translatable("command.chest_shield.info_pendiente", pendiente)));
		}
		return 1;
	}

	// ---------- Ayudas ----------

	private static Component si(final boolean valor) {
		return valor ? CommonComponents.GUI_YES : CommonComponents.GUI_NO;
	}

	private static void linea(final CommandContext<CommandSourceStack> contexto, final Component texto) {
		contexto.getSource().sendSuccess(() -> texto.copy().withStyle(ChatFormatting.GRAY), false);
	}

	private static CofrePersonalBlockEntity cofreMirado(final ServerPlayer jugador) throws CommandSyntaxException {
		CofrePersonalBlockEntity cofre = buscarCofre(jugador);
		if (cofre == null) {
			throw NO_MIRAS_UN_COFRE.create();
		}
		return cofre;
	}

	private static @Nullable CofrePersonalBlockEntity buscarCofre(final ServerPlayer jugador) {
		HitResult golpe = jugador.pick(ALCANCE, 0.0F, false);
		if (!(golpe instanceof BlockHitResult bloque)) {
			return null;
		}
		return jugador.level().getBlockEntity(bloque.getBlockPos()) instanceof CofrePersonalBlockEntity cofre
				? cofre
				: null;
	}
}
