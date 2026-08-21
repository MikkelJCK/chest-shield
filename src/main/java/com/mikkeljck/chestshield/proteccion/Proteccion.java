package com.mikkeljck.chestshield.proteccion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mikkeljck.chestshield.util.HashClave;

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
 * Todo el estado de proteccion de un contenedor: quien es el dueno, si tiene
 * clave, y quien puede abrirlo.
 *
 * Vive fuera del BlockEntity a proposito. Cualquier bloque que quiera ser
 * "blindado" (el cofre hoy, el barril manana) compone una instancia de esta
 * clase e implementa {@link ContenedorBlindado}, en vez de copiar doscientas
 * lineas de logica de permisos.
 *
 * IMPORTANTE: las claves del NBT no se pueden renombrar. El mod ya esta
 * publicado y hay mundos con cofres colocados; si cambian, esos jugadores
 * pierden la propiedad de todo al actualizar.
 */
public class Proteccion {

	public static final int LONGITUD_MAXIMA_CLAVE = 32;

	/** Ticks de espera tras un intento fallido, para frenar la fuerza bruta. */
	private static final long ENFRIAMIENTO_TICKS = 40L;

	/** Lo llama el dueno de esta proteccion para marcar el bloque como sucio y sincronizarlo. */
	private final Runnable alCambiar;

	private @Nullable UUID propietario;
	private String nombrePropietario = "";

	/** Solo "tieneClave" viaja al cliente. El hash y el salt JAMAS salen del servidor. */
	private boolean tieneClave;
	private String hashClave = "";
	private String saltClave = "";

	/** Estado volatil, no se guarda en disco. */
	private @Nullable UUID invitadoTemporal;
	private final Map<UUID, Long> esperaHasta = new HashMap<>();

	public Proteccion(final Runnable alCambiar) {
		this.alCambiar = alCambiar;
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
	 * Acceso sin contrasena: el dueno, o un admin con la Llave Maestra.
	 *
	 * El nivel de op solo se puede comprobar en el servidor. En el cliente somos
	 * optimistas para que la interfaz responda bien; el servidor tiene siempre la
	 * ultima palabra.
	 */
	public boolean puedeAcceder(final Player player) {
		if (this.esPropietario(player)) {
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

	// ---------- Contrasena ----------

	public boolean tieneClave() {
		return this.tieneClave;
	}

	/** Cadena vacia = quitar la contrasena. Solo debe llamarse en el servidor. */
	public void establecerClave(final String clave) {
		if (clave.isBlank()) {
			this.tieneClave = false;
			this.hashClave = "";
			this.saltClave = "";
		} else {
			this.saltClave = HashClave.nuevoSalt();
			this.hashClave = HashClave.calcular(clave, this.saltClave);
			this.tieneClave = true;
		}
		this.esperaHasta.clear();
		this.alCambiar.run();
	}

	public boolean verificarClave(final String clave) {
		return HashClave.coincide(clave, this.saltClave, this.hashClave);
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
	 * compatible: su dueno y su clave se deciden un instante despues. La
	 * seguridad no se pierde porque getStateForPlacement ya comprobo que quien lo
	 * coloco era el dueno del vecino.
	 */
	public boolean esCompatibleCon(final Proteccion otra) {
		if (this.propietario != null && otra.propietario != null
				&& !this.propietario.equals(otra.propietario)) {
			return false;
		}
		// Claves: o alguna esta vacia (esa heredara la de la otra), o son identicas.
		if (!this.tieneClave || !otra.tieneClave) {
			return true;
		}
		return this.hashClave.equals(otra.hashClave) && this.saltClave.equals(otra.saltClave);
	}

	/**
	 * Copia tal cual la clave de la otra mitad, incluido el caso de "sin clave".
	 *
	 * Un cofre doble es un solo mueble para el jugador, asi que las dos mitades
	 * deben compartir siempre la misma clave. Se copian hash y salt en vez de
	 * volver a calcularlos, para que ambas queden identicas y sigan contando como
	 * compatibles.
	 */
	public void copiarClaveDe(final Proteccion otra) {
		this.tieneClave = otra.tieneClave;
		this.hashClave = otra.hashClave;
		this.saltClave = otra.saltClave;
		this.esperaHasta.clear();
		this.alCambiar.run();
	}

	// ---------- Guardado ----------

	public void guardar(final ValueOutput output) {
		if (this.propietario != null) {
			output.store("Propietario", UUIDUtil.CODEC, this.propietario);
			output.putString("NombrePropietario", this.nombrePropietario);
		}
		output.putBoolean("TieneClave", this.tieneClave);
		if (this.tieneClave) {
			output.putString("HashClave", this.hashClave);
			output.putString("SaltClave", this.saltClave);
		}
	}

	public void cargar(final ValueInput input) {
		this.propietario = input.read("Propietario", UUIDUtil.CODEC).orElse(null);
		this.nombrePropietario = input.getStringOr("NombrePropietario", "");
		this.tieneClave = input.getBooleanOr("TieneClave", false);
		this.hashClave = input.getStringOr("HashClave", "");
		this.saltClave = input.getStringOr("SaltClave", "");
	}

	/**
	 * Lo unico que viaja al cliente: dueno y si hay clave o no. El inventario, el
	 * hash y el salt JAMAS se envian, porque el cliente no debe poder leer ni el
	 * contenido de un cofre ajeno ni nada que sirva para romper la clave.
	 */
	public void escribirUpdateTag(final CompoundTag tag) {
		if (this.propietario != null) {
			tag.store("Propietario", UUIDUtil.CODEC, this.propietario);
			tag.putString("NombrePropietario", this.nombrePropietario);
		}
		tag.putBoolean("TieneClave", this.tieneClave);
	}
}
