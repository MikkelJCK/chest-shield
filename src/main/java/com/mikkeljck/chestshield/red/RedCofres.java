package com.mikkeljck.chestshield.red;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.block.AperturaCofre;
import com.mikkeljck.chestshield.block.CofrePersonalBlock;
import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.proteccion.Proteccion;

import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * Los paquetes del mod, sobre la API de red de Fabric.
 *
 * Antes esto usaba owo-lib. Se paso a la API de Fabric para que el mod no tenga
 * ninguna dependencia externa: una menos que declarar en Modrinth, y una menos
 * que se pueda romper cuando actualice.
 *
 * La contrasena SIEMPRE se verifica en el servidor, y todos los paquetes que
 * cambian algo revalidan la propiedad. El cliente solo dibuja los controles al
 * dueno, pero eso no vale de nada: un cliente modificado puede mandar el paquete
 * igual.
 */
public final class RedCofres {

	/** Distancia maxima al cofre, al cuadrado (8 bloques). */
	private static final double ALCANCE_MAXIMO = 64.0;

	private RedCofres() {
	}

	// ---------- Cliente -> servidor ----------

	/** Un jugador ajeno intenta abrir el cofre con una contrasena. */
	public record IntentoClave(BlockPos pos, String clave) implements CustomPacketPayload {
		public static final Type<IntentoClave> TIPO = new Type<>(CofresPersonales.id("intento_clave"));
		public static final StreamCodec<ByteBuf, IntentoClave> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, IntentoClave::pos,
				ByteBufCodecs.STRING_UTF8, IntentoClave::clave,
				IntentoClave::new);

		@Override
		public Type<IntentoClave> type() {
			return TIPO;
		}
	}

	/** El dueno pone o cambia la contrasena. Cadena vacia = borrarla. */
	public record EstablecerClave(BlockPos pos, String clave) implements CustomPacketPayload {
		public static final Type<EstablecerClave> TIPO = new Type<>(CofresPersonales.id("establecer_clave"));
		public static final StreamCodec<ByteBuf, EstablecerClave> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, EstablecerClave::pos,
				ByteBufCodecs.STRING_UTF8, EstablecerClave::clave,
				EstablecerClave::new);

		@Override
		public Type<EstablecerClave> type() {
			return TIPO;
		}
	}

	/** El dueno enciende o apaga la proteccion entera del cofre. */
	public record CambiarProtegido(BlockPos pos, boolean protegido) implements CustomPacketPayload {
		public static final Type<CambiarProtegido> TIPO = new Type<>(CofresPersonales.id("cambiar_protegido"));
		public static final StreamCodec<ByteBuf, CambiarProtegido> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, CambiarProtegido::pos,
				ByteBufCodecs.BOOL, CambiarProtegido::protegido,
				CambiarProtegido::new);

		@Override
		public Type<CambiarProtegido> type() {
			return TIPO;
		}
	}

	/** El dueno cambia las dos casillas de tolvas a la vez. */
	public record CambiarTolvas(BlockPos pos, boolean meter, boolean sacar) implements CustomPacketPayload {
		public static final Type<CambiarTolvas> TIPO = new Type<>(CofresPersonales.id("cambiar_tolvas"));
		public static final StreamCodec<ByteBuf, CambiarTolvas> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, CambiarTolvas::pos,
				ByteBufCodecs.BOOL, CambiarTolvas::meter,
				ByteBufCodecs.BOOL, CambiarTolvas::sacar,
				CambiarTolvas::new);

		@Override
		public Type<CambiarTolvas> type() {
			return TIPO;
		}
	}

	/**
	 * El dueno da o retira permiso. Se manda el nombre y el servidor lo resuelve.
	 *
	 * forzar = el jugador confirmo en la pantalla que quiere anadir a alguien
	 * desconectado. Sin eso, un nombre que no esta conectado se rechaza.
	 */
	public record CambiarPermiso(BlockPos pos, String nombre, boolean agregar, boolean forzar)
			implements CustomPacketPayload {
		public static final Type<CambiarPermiso> TIPO = new Type<>(CofresPersonales.id("cambiar_permiso"));
		public static final StreamCodec<ByteBuf, CambiarPermiso> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, CambiarPermiso::pos,
				ByteBufCodecs.STRING_UTF8, CambiarPermiso::nombre,
				ByteBufCodecs.BOOL, CambiarPermiso::agregar,
				ByteBufCodecs.BOOL, CambiarPermiso::forzar,
				CambiarPermiso::new);

		@Override
		public Type<CambiarPermiso> type() {
			return TIPO;
		}
	}

	// ---------- Servidor -> cliente ----------

	/**
	 * "Abre la pantalla de contrasena para este cofre".
	 *
	 * Quien decide si hay que pedir la clave es el SERVIDOR, no el cliente: la
	 * copia que el cliente tiene del cofre puede estar desfasada, y si decidiera
	 * el, un cofre podria quedarse sin responder al click.
	 */
	public record PedirClave(BlockPos pos, String nombreDueno) implements CustomPacketPayload {
		public static final Type<PedirClave> TIPO = new Type<>(CofresPersonales.id("pedir_clave"));
		public static final StreamCodec<ByteBuf, PedirClave> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, PedirClave::pos,
				ByteBufCodecs.STRING_UTF8, PedirClave::nombreDueno,
				PedirClave::new);

		@Override
		public Type<PedirClave> type() {
			return TIPO;
		}
	}

	/**
	 * "El cofre que acabas de abrir esta en esta posicion".
	 *
	 * La pantalla de cofre de vanilla no sabe de que bloque viene, solo tiene un
	 * menu, y el boton de configuracion necesita la posicion.
	 */
	public record CofreAbierto(BlockPos pos) implements CustomPacketPayload {
		public static final Type<CofreAbierto> TIPO = new Type<>(CofresPersonales.id("cofre_abierto"));
		public static final StreamCodec<ByteBuf, CofreAbierto> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, CofreAbierto::pos,
				CofreAbierto::new);

		@Override
		public Type<CofreAbierto> type() {
			return TIPO;
		}
	}

	// ---------- Registro ----------

	/**
	 * Los tipos de paquete se declaran en codigo COMUN, en los dos sentidos, o el
	 * saludo inicial no cuadra. Los manejadores de servidor tambien van aqui; los
	 * de cliente estan en CofresPersonalesClient, porque tocan clases de cliente.
	 */
	public static void inicializar() {
		PayloadTypeRegistry.serverboundPlay().register(IntentoClave.TIPO, IntentoClave.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(EstablecerClave.TIPO, EstablecerClave.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CambiarProtegido.TIPO, CambiarProtegido.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CambiarTolvas.TIPO, CambiarTolvas.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CambiarPermiso.TIPO, CambiarPermiso.CODEC);

		PayloadTypeRegistry.clientboundPlay().register(PedirClave.TIPO, PedirClave.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(CofreAbierto.TIPO, CofreAbierto.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(IntentoClave.TIPO,
				(mensaje, contexto) -> alIntentarClave(mensaje, contexto.player()));
		ServerPlayNetworking.registerGlobalReceiver(EstablecerClave.TIPO,
				(mensaje, contexto) -> alEstablecerClave(mensaje, contexto.player()));
		ServerPlayNetworking.registerGlobalReceiver(CambiarProtegido.TIPO,
				(mensaje, contexto) -> alCambiarProtegido(mensaje, contexto.player()));
		ServerPlayNetworking.registerGlobalReceiver(CambiarTolvas.TIPO,
				(mensaje, contexto) -> alCambiarTolvas(mensaje, contexto.player()));
		ServerPlayNetworking.registerGlobalReceiver(CambiarPermiso.TIPO,
				(mensaje, contexto) -> alCambiarPermiso(mensaje, contexto.player()));
	}

	/** Le pide al cliente de este jugador que abra la pantalla de contrasena. */
	public static void pedirClave(final ServerPlayer jugador, final BlockPos pos, final String nombreDueno) {
		ServerPlayNetworking.send(jugador, new PedirClave(pos, nombreDueno));
	}

	/** Le dice al cliente que cofre esta abriendo, para el boton de configuracion. */
	public static void avisarCofreAbierto(final ServerPlayer jugador, final BlockPos pos) {
		ServerPlayNetworking.send(jugador, new CofreAbierto(pos));
	}

	// ---------- Apertura con contrasena ----------

	private static void alIntentarClave(final IntentoClave mensaje, final ServerPlayer jugador) {
		if (mensaje.clave().length() > Proteccion.LONGITUD_MAXIMA_CLAVE) {
			return;
		}
		CofrePersonalBlockEntity cofre = cofreValido(jugador, mensaje.pos());
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

	// ---------- Ajustes desde la pantalla de configuracion ----------

	private static void alEstablecerClave(final EstablecerClave mensaje, final ServerPlayer jugador) {
		if (mensaje.clave().length() > Proteccion.LONGITUD_MAXIMA_CLAVE) {
			return;
		}
		CofrePersonalBlockEntity cofre = cofreDelDueno(jugador, mensaje.pos());
		if (cofre == null) {
			return;
		}
		cofre.establecerClave(mensaje.clave());

		// Un cofre doble es un solo mueble: la clave vale para las dos mitades.
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

	private static void alCambiarProtegido(final CambiarProtegido mensaje, final ServerPlayer jugador) {
		CofrePersonalBlockEntity cofre = cofreDelDueno(jugador, mensaje.pos());
		if (cofre == null) {
			return;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre,
				mitad -> mitad.getProteccion().setProtegido(mensaje.protegido()));
	}

	private static void alCambiarTolvas(final CambiarTolvas mensaje, final ServerPlayer jugador) {
		CofrePersonalBlockEntity cofre = cofreDelDueno(jugador, mensaje.pos());
		if (cofre == null) {
			return;
		}
		CofrePersonalBlock.paraAmbasMitades(cofre, mitad -> {
			mitad.getProteccion().getAjustes().setTolvasMeter(mensaje.meter());
			mitad.getProteccion().getAjustes().setTolvasSacar(mensaje.sacar());
			mitad.getProteccion().marcarCambiado();
		});
	}

	private static void alCambiarPermiso(final CambiarPermiso mensaje, final ServerPlayer jugador) {
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

	// ---------- Comprobaciones ----------

	/** El cofre al que apunta el paquete, solo si quien lo manda es su dueno. */
	private static @Nullable CofrePersonalBlockEntity cofreDelDueno(final ServerPlayer jugador, final BlockPos pos) {
		CofrePersonalBlockEntity cofre = cofreValido(jugador, pos);
		if (cofre == null) {
			return null;
		}
		if (!cofre.puedeGestionar(jugador)) {
			cofre.avisarPropiedad(jugador);
			return null;
		}
		return cofre;
	}

	/**
	 * Nunca confiar en la posicion que manda el cliente: hay que comprobar que el
	 * bloque existe, que es nuestro y que el jugador lo tiene al alcance.
	 */
	private static @Nullable CofrePersonalBlockEntity cofreValido(final ServerPlayer jugador, final BlockPos pos) {
		if (!jugador.level().isLoaded(pos)) {
			return null;
		}
		if (pos.distToCenterSqr(jugador.position()) > ALCANCE_MAXIMO) {
			return null;
		}
		return jugador.level().getBlockEntity(pos) instanceof CofrePersonalBlockEntity cofre ? cofre : null;
	}
}
