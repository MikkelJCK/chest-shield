package com.mikkeljck.chestshield.proteccion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mikkeljck.chestshield.CofresPersonales;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * La proteccion de un contenedor: quien es su dueno, mas los {@link Ajustes}
 * que dicen quien mas puede abrirlo.
 *
 * Vive fuera del BlockEntity a proposito. Cualquier bloque que quiera estar
 * blindado (el cofre hoy, el barril manana) compone una instancia de esta clase
 * e implementa {@link ContenedorBlindado}, en vez de copiar la logica entera.
 *
 * IMPORTANTE: las claves del NBT no se pueden renombrar. El mod ya esta
 * publicado y hay mundos con cofres colocados; si cambian, esos jugadores
 * pierden la propiedad de todo al actualizar.
 */
public class Proteccion {

	public static final int LONGITUD_MAXIMA_CLAVE = Ajustes.LONGITUD_MAXIMA_CLAVE;

	/** Ticks de espera tras un intento fallido, para frenar la fuerza bruta. */
	private static final long ENFRIAMIENTO_TICKS = 40L;

	/** Lo llama la proteccion para marcar el bloque como sucio y sincronizarlo. */
	private final Runnable alCambiar;

	private @Nullable UUID propietario;
	private String nombrePropietario = "";

	private final Ajustes ajustes = new Ajustes();

	/** Estado volatil, no se guarda en disco. */
	private @Nullable UUID invitadoTemporal;
	private final Map<UUID, Long> esperaHasta = new HashMap<>();

	public Proteccion(final Runnable alCambiar) {
		this.alCambiar = alCambiar;
	}

	/** Los ajustes que rigen este contenedor. De momento siempre los suyos propios. */
	public Ajustes getAjustes() {
		return this.ajustes;
	}

	/** Para uso desde fuera tras modificar los ajustes directamente. */
	public void marcarCambiado() {
		this.alCambiar.run();
	}

	// ---------- Propiedad ----------

	public @Nullable UUID getPropietario() {
		return this.propietario;
	}

	public String getNombrePropietario() {
		return this.nombrePropietario.isEmpty() ? "desconocido" : this.nombrePropietario;
	}

	public void asignarPropietario(final Player player) {
		this.propietario = player.getUUID();
		this.nombrePropietario = player.getName().getString();
		this.alCambiar.run();
	}

	/** Un contenedor sin dueno lo es de cualquiera: aun no lo ha estrenado nadie. */
	public boolean esPropietario(final Player player) {
		return this.propietario == null || this.propietario.equals(player.getUUID());
	}

	/** Solo mira el item en las manos; sirve igual en cliente y en servidor. */
	public static boolean sostieneLlaveMaestra(final Player player) {
		return player.getMainHandItem().is(CofresPersonales.LLAVE_MAESTRA)
				|| player.getOffhandItem().is(CofresPersonales.LLAVE_MAESTRA);
	}

	/**
	 * Acceso sin contrasena: el dueno, quien esta en la lista de permisos, o un
	 * admin con la Llave Maestra.
	 *
	 * El nivel de op solo se puede comprobar en el servidor. En el cliente somos
	 * optimistas para que la interfaz responda bien; el servidor tiene siempre la
	 * ultima palabra.
	 */
	public boolean puedeAcceder(final Player player) {
		if (this.esPropietario(player)) {
			return true;
		}
		if (this.ajustes.tienePermiso(player.getUUID())) {
			return true;
		}
		if (!sostieneLlaveMaestra(player)) {
			return false;
		}
		return !(player instanceof ServerPlayer serverPlayer)
				|| serverPlayer.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
	}

	public void avisarPropiedad(final Player player) {
		avisar(player, Component.translatable("message.chest_shield.protegido", this.getNombrePropietario())
				.withStyle(ChatFormatting.RED));
	}

	public static void avisar(final Player player, final Component mensaje) {
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.sendOverlayMessage(mensaje);
		}
	}

	// ---------- Permisos ----------

	public boolean tienePermiso(final UUID jugador) {
		return this.ajustes.tienePermiso(jugador);
	}

	public void agregarPermiso(final UUID jugador, final String nombre) {
		this.ajustes.agregarPermiso(jugador, nombre);
		this.alCambiar.run();
	}

	public boolean quitarPermiso(final UUID jugador) {
		boolean quitado = this.ajustes.quitarPermiso(jugador);
		if (quitado) {
			this.alCambiar.run();
		}
		return quitado;
	}

	public List<Ajustes.Permiso> getPermisos() {
		return this.ajustes.getPermisos();
	}

	// ---------- Contrasena ----------

	public boolean tieneClave() {
		return this.ajustes.tieneClave();
	}

	public void establecerClave(final String clave) {
		this.ajustes.establecerClave(clave);
		this.esperaHasta.clear();
		this.alCambiar.run();
	}

	public boolean verificarClave(final String clave) {
		return this.ajustes.verificarClave(clave);
	}

	public boolean enEspera(final Player player, final long tiempoActual) {
		Long hasta = this.esperaHasta.get(player.getUUID());
		return hasta != null && tiempoActual < hasta;
	}

	public void registrarFallo(final Player player, final long tiempoActual) {
		this.esperaHasta.put(player.getUUID(), tiempoActual + ENFRIAMIENTO_TICKS);
	}

	// ---------- Invitado de un solo uso ----------

	public void setInvitadoTemporal(final @Nullable UUID invitado) {
		this.invitadoTemporal = invitado;
	}

	public boolean esInvitado(final Player player) {
		return player.getUUID().equals(this.invitadoTemporal);
	}

	// ---------- Emparejado (cofres dobles) ----------

	/**
	 * Decide si dos contenedores pueden unirse.
	 *
	 * OJO CON EL ORDEN: al colocar un bloque, los vecinos reciben la
	 * actualizacion ANTES de que setPlacedBy le asigne dueno y clave al recien
	 * puesto. Por eso un cofre sin estrenar (dueno nulo, sin clave) cuenta como
	 * compatible: su dueno y sus ajustes se deciden un instante despues. La
	 * seguridad no se pierde porque getStateForPlacement ya comprobo que quien lo
	 * coloco era el dueno del vecino.
	 */
	public boolean esCompatibleCon(final Proteccion otra) {
		if (this.propietario != null && otra.propietario != null
				&& !this.propietario.equals(otra.propietario)) {
			return false;
		}
		return this.ajustes.claveCompatibleCon(otra.ajustes);
	}

	/**
	 * Copia los ajustes de la otra mitad tal cual.
	 *
	 * Un cofre doble es un solo mueble para el jugador, asi que las dos mitades
	 * comparten clave, permisos y tolvas. Se copian hash y salt en vez de volver
	 * a calcularlos, para que ambas queden identicas y sigan contando como
	 * compatibles.
	 */
	public void copiarAjustesDe(final Proteccion otra) {
		this.ajustes.copiarDe(otra.ajustes);
		this.esperaHasta.clear();
		this.alCambiar.run();
	}

	// ---------- Guardado ----------

	public void guardar(final ValueOutput output) {
		if (this.propietario != null) {
			output.store("Propietario", UUIDUtil.CODEC, this.propietario);
			output.putString("NombrePropietario", this.nombrePropietario);
		}
		this.ajustes.guardar(output);
	}

	public void cargar(final ValueInput input) {
		this.propietario = input.read("Propietario", UUIDUtil.CODEC).orElse(null);
		this.nombrePropietario = input.getStringOr("NombrePropietario", "");
		this.ajustes.cargar(input);
	}

	/**
	 * Lo que viaja al cliente: dueno, si hay clave, la lista de permisos y los
	 * flags de tolvas.
	 *
	 * El inventario, el hash y el salt JAMAS se envian: el cliente no debe poder
	 * leer ni el contenido de un cofre ajeno ni nada que sirva para romper la
	 * clave. La lista de permisos si va, porque el cliente la necesita para dos
	 * cosas: saber si debe pedir la contrasena al abrir, y pintarla en la
	 * pantalla de configuracion. No contiene ningun secreto, pero si es
	 * informacion publica: cualquiera cerca del cofre puede ver quien tiene
	 * acceso.
	 */
	public void escribirUpdateTag(final CompoundTag tag) {
		if (this.propietario != null) {
			tag.store("Propietario", UUIDUtil.CODEC, this.propietario);
			tag.putString("NombrePropietario", this.nombrePropietario);
		}
		tag.putBoolean("TieneClave", this.ajustes.tieneClave());
		List<Ajustes.Permiso> permisos = this.ajustes.getPermisos();
		if (!permisos.isEmpty()) {
			tag.store("Permisos", Ajustes.Permiso.LISTA, permisos);
		}
		tag.putBoolean("TolvasMeter", this.ajustes.tolvasPuedenMeter());
		tag.putBoolean("TolvasSacar", this.ajustes.tolvasPuedenSacar());
	}
}
