package com.mikkeljck.reinforcedchests.red;

import com.mikkeljck.reinforcedchests.CofresPersonales;
import com.mikkeljck.reinforcedchests.block.AperturaCofre;
import com.mikkeljck.reinforcedchests.block.CofrePersonalBlock;
import com.mikkeljck.reinforcedchests.block.CofrePersonalBlockEntity;

import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.network.ServerAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Canal de red del mod.
 *
 * Solo hay paquetes cliente -> servidor. La contrasena SIEMPRE se verifica en el
 * servidor: un cliente modificado no puede saltarse nada porque el servidor es
 * quien decide si abre el menu.
 *
 * El registro debe ocurrir en codigo comun (corre en cliente y en servidor), o
 * el saludo inicial de owo falla al conectarse.
 */
public final class RedCofres {

	public static final OwoNetChannel CANAL = OwoNetChannel.create(CofresPersonales.id("red"));

	/** Distancia maxima al cofre, al cuadrado (8 bloques). */
	private static final double ALCANCE_MAXIMO = 64.0;

	private RedCofres() {
	}

	/** Un jugador ajeno intenta abrir el cofre con una contrasena. */
	public record IntentoClave(BlockPos pos, String clave) {
	}

	/** El dueno pone, cambia o quita la contrasena. Cadena vacia = quitar. */
	public record EstablecerClave(BlockPos pos, String clave) {
	}

	public static void inicializar() {
		CANAL.registerServerbound(IntentoClave.class, RedCofres::alIntentarClave);
		CANAL.registerServerbound(EstablecerClave.class, RedCofres::alEstablecerClave);
	}

	private static void alIntentarClave(final IntentoClave mensaje, final ServerAccess acceso) {
		ServerPlayer jugador = acceso.player();
		CofrePersonalBlockEntity cofre = cofreValido(jugador, mensaje.pos(), mensaje.clave());
		if (cofre == null) {
			return;
		}

		if (cofre.puedeAcceder(jugador)) {
			AperturaCofre.abrir(jugador, jugador.level(), mensaje.pos(),
					jugador.level().getBlockState(mensaje.pos()), false);
			return;
		}

		if (!cofre.tieneClave()) {
			cofre.avisarPropiedad(jugador);
			return;
		}

		if (cofre.enEspera(jugador)) {
			cofre.avisar(jugador, Component.translatable("message.reinforced_chests.espera")
					.withStyle(ChatFormatting.RED));
			return;
		}

		if (cofre.verificarClave(mensaje.clave())) {
			cofre.abrirParaInvitado(jugador);
		} else {
			cofre.registrarFallo(jugador);
			cofre.avisar(jugador, Component.translatable("message.reinforced_chests.clave_incorrecta")
					.withStyle(ChatFormatting.RED));
		}
	}

	private static void alEstablecerClave(final EstablecerClave mensaje, final ServerAccess acceso) {
		ServerPlayer jugador = acceso.player();
		CofrePersonalBlockEntity cofre = cofreValido(jugador, mensaje.pos(), mensaje.clave());
		if (cofre == null) {
			return;
		}

		// Solo el dueno decide la clave. Ni siquiera un admin con Llave Maestra:
		// la llave sirve para entrar y limpiar, no para apropiarse del cofre.
		if (!cofre.esPropietario(jugador)) {
			cofre.avisarPropiedad(jugador);
			return;
		}

		cofre.establecerClave(mensaje.clave());

		// Un cofre doble es un solo mueble: la clave vale para las dos mitades.
		// Sin esto, la otra mitad quedaba sin clave y el jugador ajeno se topaba
		// con un bloqueo sin manera de introducirla.
		BlockState estado = jugador.level().getBlockState(mensaje.pos());
		if (estado.getBlock() instanceof CofrePersonalBlock
				&& estado.getValue(CofrePersonalBlock.TYPE) != ChestType.SINGLE) {
			BlockPos posPareja = mensaje.pos().relative(CofrePersonalBlock.direccionUnion(estado));
			if (jugador.level().getBlockEntity(posPareja) instanceof CofrePersonalBlockEntity pareja) {
				pareja.copiarClaveDe(cofre);
			}
		}
		cofre.avisar(jugador, Component.translatable(mensaje.clave().isBlank()
						? "message.reinforced_chests.clave_quitada"
						: "message.reinforced_chests.clave_guardada")
				.withStyle(ChatFormatting.GREEN));
	}

	/**
	 * Nunca confiar en la posicion que manda el cliente: hay que comprobar que el
	 * bloque existe, que es nuestro y que el jugador lo tiene al alcance.
	 */
	private static @Nullable CofrePersonalBlockEntity cofreValido(final ServerPlayer jugador, final BlockPos pos, final String clave) {
		if (clave.length() > CofrePersonalBlockEntity.LONGITUD_MAXIMA_CLAVE) {
			return null;
		}
		if (!jugador.level().isLoaded(pos)) {
			return null;
		}
		if (jugador.distanceToSqr(pos.getCenter()) > ALCANCE_MAXIMO) {
			return null;
		}
		return jugador.level().getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre ? cofre : null;
	}
}
