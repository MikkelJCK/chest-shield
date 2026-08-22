package com.mikkeljck.chestshield.red;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.block.AperturaCofre;
import com.mikkeljck.chestshield.block.CofrePersonalBlock;
import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;

import io.wispforest.owo.network.OwoNetChannel;
import io.wispforest.owo.network.ServerAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
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

	/**
	 * Servidor -> cliente: "abre la pantalla de contrasena para este cofre".
	 *
	 * Quien decide si hay que pedir la clave es el SERVIDOR, no el cliente. El
	 * cliente tiene una copia del cofre que puede estar desfasada (sobre todo con
	 * el perfil global, donde un cambio afecta a muchos cofres pero solo se
	 * resincroniza uno), asi que si decidiera el, un cofre podria quedarse sin
	 * responder al click. Aqui el cliente solo obedece.
	 */
	public record PedirClave(BlockPos pos, String nombreDueno) {
	}

	/**
	 * Servidor -> cliente: "el cofre que acabas de abrir esta en esta posicion".
	 *
	 * La pantalla de cofre de vanilla no sabe de que bloque viene, solo tiene un
	 * menu. El boton de configuracion necesita la posicion, y adivinarla en el
	 * cliente (por ejemplo recordando el ultimo click) se rompe en cuanto algo se
	 * desincroniza. Que lo diga el servidor al abrir es exacto y barato.
	 */
	public record CofreAbierto(BlockPos pos) {
	}

	/** El dueno enciende o apaga la proteccion entera del cofre. */
	public record CambiarProtegido(BlockPos pos, boolean protegido) {
	}

	/** El dueno cambia las dos casillas de tolvas a la vez. */
	public record CambiarTolvas(BlockPos pos, boolean meter, boolean sacar) {
	}

	/**
	 * El dueno da o retira permiso. Se manda el nombre, y el servidor lo resuelve.
	 *
	 * forzar = el jugador confirmo que quiere anadir a alguien desconectado. Sin
	 * eso, un nombre que no esta conectado se rechaza con un aviso.
	 */
	public record CambiarPermiso(BlockPos pos, String nombre, boolean agregar, boolean forzar) {
	}

	public static void inicializar() {
		CANAL.registerServerbound(IntentoClave.class, RedCofres::alIntentarClave);
		CANAL.registerServerbound(EstablecerClave.class, RedCofres::alEstablecerClave);
		CANAL.registerServerbound(CambiarProtegido.class, RedCofres::alCambiarProtegido);
		CANAL.registerServerbound(CambiarTolvas.class, RedCofres::alCambiarTolvas);
		CANAL.registerServerbound(CambiarPermiso.class, RedCofres::alCambiarPermiso);
		// El manejador real se registra en el cliente. En el servidor dedicado
		// solo se declara el paquete, para que el saludo de owo cuadre en ambos
		// lados sin cargar ninguna clase de cliente.
		CANAL.registerClientboundDeferred(PedirClave.class);
		CANAL.registerClientboundDeferred(CofreAbierto.class);
	}

	/** Le pide al cliente de este jugador que abra la pantalla de contrasena. */
	public static void pedirClave(final ServerPlayer jugador, final BlockPos pos, final String nombreDueno) {
		CANAL.serverHandle(jugador).send(new PedirClave(pos, nombreDueno));
	}

	/** Le dice al cliente que cofre esta abriendo, para el boton de configuracion. */
	public static void avisarCofreAbierto(final ServerPlayer jugador, final BlockPos pos) {
		CANAL.serverHandle(jugador).send(new CofreAbierto(pos));
	}

	private static void alIntentarClave(final IntentoClave mensaje, final ServerAccess acceso) {
		ServerPlayer jugador = acceso.player();
		CofrePersonalBlockEntity cofre = cofreValido(jugador, mensaje.pos(), mensaje.clave());
		if (cofre == null) {
			return;
		}

		if (cofre.puedeAbrir(jugador)) {
			AperturaCofre.abrir(jugador, jugador.level(), mensaje.pos(),
					jugador.level().getBlockState(mensaje.pos()), false);
			return;
		}

		if (!cofre.tieneClave()) {
			cofre.avisarPropiedad(jugador);
			return;
		}

		if (cofre.enEspera(jugador)) {
			cofre.avisar(jugador, Component.translatable("message.chest_shield.espera")
					.withStyle(ChatFormatting.RED));
			return;
		}

		if (cofre.verificarClave(mensaje.clave())) {
			cofre.abrirParaInvitado(jugador);
		} else {
			cofre.registrarFallo(jugador);
			cofre.avisar(jugador, Component.translatable("message.chest_shield.clave_incorrecta")
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
				pareja.copiarAjustesDe(cofre);
			}
		}
		cofre.avisar(jugador, Component.translatable(mensaje.clave().isBlank()
						? "message.chest_shield.clave_quitada"
						: "message.chest_shield.clave_guardada")
				.withStyle(ChatFormatting.GREEN));
	}

	// ---------- Ajustes desde la pantalla de configuracion ----------
	// Los tres comparten la misma comprobacion: el cliente solo dibuja estos
	// controles al dueno, pero eso no vale de nada — un cliente modificado puede
	// mandar el paquete igual, asi que el servidor revalida siempre.

	private static void alCambiarProtegido(final CambiarProtegido mensaje, final ServerAccess acceso) {
		CofrePersonalBlockEntity cofre = cofreDelDueno(acceso.player(), mensaje.pos());
		if (cofre == null) {
			return;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre,
				mitad -> mitad.getProteccion().setProtegido(mensaje.protegido()));
	}

	private static void alCambiarTolvas(final CambiarTolvas mensaje, final ServerAccess acceso) {
		CofrePersonalBlockEntity cofre = cofreDelDueno(acceso.player(), mensaje.pos());
		if (cofre == null) {
			return;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre, mitad -> {
			mitad.getProteccion().getAjustes().setTolvasMeter(mensaje.meter());
			mitad.getProteccion().getAjustes().setTolvasSacar(mensaje.sacar());
			mitad.getProteccion().marcarCambiado();
		});
	}

	private static void alCambiarPermiso(final CambiarPermiso mensaje, final ServerAccess acceso) {
		ServerPlayer jugador = acceso.player();
		CofrePersonalBlockEntity cofre = cofreDelDueno(jugador, mensaje.pos());
		if (cofre == null || mensaje.nombre().length() > 16) {
			return;
		}

		if (!mensaje.agregar()) {
			// Para quitar basta el nombre: el UUID ya esta en la lista, y si era un
			// pendiente ni siquiera hay UUID todavia.
			CofrePersonalBlock.paraAmbasMitades(cofre, mitad -> {
				mitad.getProteccion().quitarPendiente(mensaje.nombre());
				mitad.getPermisos().stream()
						.filter(permiso -> permiso.nombre().equalsIgnoreCase(mensaje.nombre()))
						.findFirst()
						.ifPresent(permiso -> mitad.quitarPermiso(permiso.uuid()));
			});
			return;
		}

		// ServerPlayer no expone el servidor; se llega por su nivel.
		MinecraftServer servidor = jugador.level().getServer();
		ServerPlayer objetivo = servidor == null
				? null
				: servidor.getPlayerList().getPlayerByName(mensaje.nombre());

		if (objetivo != null) {
			if (cofre.esPropietario(objetivo)) {
				cofre.avisar(jugador, Component.translatable("message.chest_shield.ya_es_dueno")
						.withStyle(ChatFormatting.YELLOW));
				return;
			}
			CofrePersonalBlock.paraAmbasMitades(cofre,
					mitad -> mitad.agregarPermiso(objetivo.getUUID(), objetivo.getName().getString()));
			return;
		}

		// Desconectado: solo se guarda si el jugador confirmo en la pantalla.
		if (!mensaje.forzar()) {
			cofre.avisar(jugador, Component.translatable("message.chest_shield.jugador_no_conectado", mensaje.nombre())
					.withStyle(ChatFormatting.RED));
			return;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre,
				mitad -> mitad.getProteccion().agregarPendiente(mensaje.nombre()));
		cofre.avisar(jugador, Component.translatable("message.chest_shield.permiso_pendiente", mensaje.nombre())
				.withStyle(ChatFormatting.YELLOW));
	}

	/** El cofre al que apunta el paquete, solo si quien lo manda es su dueno. */
	private static @Nullable CofrePersonalBlockEntity cofreDelDueno(final ServerPlayer jugador, final BlockPos pos) {
		CofrePersonalBlockEntity cofre = cofreValido(jugador, pos, "");
		if (cofre == null) {
			return null;
		}
		if (!cofre.esPropietario(jugador)) {
			cofre.avisarPropiedad(jugador);
			return null;
		}
		return cofre;
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
		if (pos.distToCenterSqr(jugador.position()) > ALCANCE_MAXIMO) {
			return null;
		}
		return jugador.level().getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre ? cofre : null;
	}
}
