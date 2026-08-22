package com.mikkeljck.chestshield.comando;

import com.mikkeljck.chestshield.block.CofrePersonalBlock;
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
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * Comando de administracion y pruebas: /chestshield
 *
 * Todos los subcomandos actuan sobre el cofre al que estas MIRANDO, para no
 * tener que teclear coordenadas.
 *
 * Los subcomandos de permisos son provisionales: existen para poder probar la
 * logica antes de que exista la interfaz, y la interfaz los sustituira. El
 * subcomando "info" si se queda: es util para moderar sin romper bloques.
 */
public final class ComandoCofre {

	/** Alcance del rayo, en bloques. Un poco mas que el alcance normal de uso. */
	private static final double ALCANCE = 6.0;

	private static final SimpleCommandExceptionType NO_MIRAS_UN_COFRE =
			new SimpleCommandExceptionType(Component.literal("No estas mirando un cofre blindado"));

	private static final SimpleCommandExceptionType NO_ERES_EL_DUENO =
			new SimpleCommandExceptionType(Component.literal("Ese cofre no es tuyo"));

	private ComandoCofre() {
	}

	public static void registrar(final CommandDispatcher<CommandSourceStack> despachador) {
		despachador.register(Commands.literal("chestshield")
				.then(Commands.literal("info")
						.executes(ComandoCofre::info))
				.then(Commands.literal("permiso")
						.then(Commands.literal("add")
								.then(Commands.argument("jugador", EntityArgument.player())
										.executes(contexto -> agregar(contexto,
												EntityArgument.getPlayer(contexto, "jugador")))))
						.then(Commands.literal("remove")
								.then(Commands.argument("jugador", EntityArgument.player())
										.executes(contexto -> quitar(contexto,
												EntityArgument.getPlayer(contexto, "jugador"))))))
				.then(Commands.literal("tolvas")
						.then(Commands.literal("meter")
								.then(Commands.literal("on").executes(c -> tolvas(c, true, true)))
								.then(Commands.literal("off").executes(c -> tolvas(c, true, false))))
						.then(Commands.literal("sacar")
								.then(Commands.literal("on").executes(c -> tolvas(c, false, true)))
								.then(Commands.literal("off").executes(c -> tolvas(c, false, false))))));
	}

	// ---------- Subcomandos ----------

	private static int info(final CommandContext<CommandSourceStack> contexto) throws CommandSyntaxException {
		CofrePersonalBlockEntity cofre = cofreMirado(contexto);
		StringBuilder texto = new StringBuilder();
		texto.append("Dueno: ").append(cofre.getNombrePropietario());
		texto.append("\nProtegido: ").append(cofre.estaProtegido() ? "si" : "NO (abierto a todos)");
		texto.append("\nClave: ").append(cofre.tieneClave() ? "si" : "no");
		Ajustes ajustes = cofre.getProteccion().getAjustes();
		texto.append("\nTolvas: meter=").append(ajustes.tolvasPuedenMeter())
				.append(" sacar=").append(ajustes.tolvasPuedenSacar());
		texto.append("\nPermisos (").append(cofre.getPermisos().size()).append("):");
		if (cofre.getPermisos().isEmpty()) {
			texto.append(" ninguno");
		} else {
			for (Ajustes.Permiso permiso : cofre.getPermisos()) {
				texto.append("\n  - ").append(permiso.nombre());
			}
		}
		for (String pendiente : cofre.getProteccion().getPendientes()) {
			texto.append("\n  - ").append(pendiente).append(" (pendiente)");
		}
		responder(contexto, texto.toString(), ChatFormatting.GRAY);
		return 1;
	}

	private static int agregar(final CommandContext<CommandSourceStack> contexto, final ServerPlayer objetivo)
			throws CommandSyntaxException {
		CofrePersonalBlockEntity cofre = cofreMiradoSiendoDueno(contexto);
		if (cofre.esPropietario(objetivo)) {
			responder(contexto, objetivo.getName().getString() + " ya es el dueno", ChatFormatting.YELLOW);
			return 0;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre,
				mitad -> mitad.agregarPermiso(objetivo.getUUID(), objetivo.getName().getString()));
		responder(contexto, "Permiso concedido a " + objetivo.getName().getString(), ChatFormatting.GREEN);
		return 1;
	}

	private static int quitar(final CommandContext<CommandSourceStack> contexto, final ServerPlayer objetivo)
			throws CommandSyntaxException {
		CofrePersonalBlockEntity cofre = cofreMiradoSiendoDueno(contexto);
		if (!cofre.tienePermiso(objetivo)) {
			responder(contexto, objetivo.getName().getString() + " no tenia permiso", ChatFormatting.YELLOW);
			return 0;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre, mitad -> mitad.quitarPermiso(objetivo.getUUID()));
		responder(contexto, "Permiso retirado a " + objetivo.getName().getString(), ChatFormatting.GREEN);
		return 1;
	}

	private static int tolvas(final CommandContext<CommandSourceStack> contexto, final boolean meter,
			final boolean permitido) throws CommandSyntaxException {
		CofrePersonalBlockEntity cofre = cofreMiradoSiendoDueno(contexto);
		CofrePersonalBlock.paraAmbasMitades(cofre, mitad -> {
			if (meter) {
				mitad.getProteccion().getAjustes().setTolvasMeter(permitido);
			} else {
				mitad.getProteccion().getAjustes().setTolvasSacar(permitido);
			}
			mitad.getProteccion().marcarCambiado();
		});
		responder(contexto, "Las tolvas " + (permitido ? "ya pueden " : "ya no pueden ")
				+ (meter ? "meter objetos" : "sacar objetos"), ChatFormatting.GREEN);
		return 1;
	}

	// ---------- Ayudas ----------

	private static CofrePersonalBlockEntity cofreMiradoSiendoDueno(final CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		CofrePersonalBlockEntity cofre = cofreMirado(contexto);
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
		// Mismo permiso que la Llave Maestra: quien puede abrir cofres ajenos
		// tambien puede administrarlos por comando.
		boolean admin = contexto.getSource().checkPermission(Permisos.LLAVE_MAESTRA.key(),
				PermissionLevel.GAMEMASTERS);
		if (!cofre.puedeGestionar(jugador) && !admin) {
			throw NO_ERES_EL_DUENO.create();
		}
		return cofre;
	}

	private static CofrePersonalBlockEntity cofreMirado(final CommandContext<CommandSourceStack> contexto)
			throws CommandSyntaxException {
		ServerPlayer jugador = contexto.getSource().getPlayerOrException();
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

	private static void responder(final CommandContext<CommandSourceStack> contexto, final String texto,
			final ChatFormatting color) {
		contexto.getSource().sendSuccess(() -> Component.literal(texto).withStyle(color), false);
	}
}
