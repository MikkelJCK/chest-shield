package com.mikkeljck.chestshield.proteccion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mikkeljck.chestshield.CofresPersonales;
import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
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
 * TODO ES LOCAL: cada cofre (o cofre doble) es dueno de su clave, su lista de
 * permisos y sus tolvas. No hay ningun perfil compartido entre cofres, y esa es
 * una decision de diseno, no una limitacion: sin estado global, lo que el
 * cliente tiene de un cofre nunca puede quedarse desfasado por un cambio hecho
 * en otro.
 *
 * IMPORTANTE: las claves del NBT no se pueden renombrar. El mod ya esta
 * publicado y hay mundos con cofres colocados; si cambian, esos jugadores
 * pierden la propiedad de todo al actualizar.
 */
public class Proteccion {

	public static final int LONGITUD_MAXIMA_CLAVE = Ajustes.LONGITUD_MAXIMA_CLAVE;

	/**
	 * Version del formato de datos guardado en el bloque.
	 *
	 * 0 = mod 1.0.0 (no escribia este campo)
	 * 1 = mod 1.1.0
	 *
	 * La migracion es PEREZOSA, cofre a cofre, cuando el chunk se carga. No se
	 * puede hacer "al arrancar el servidor" porque eso obligaria a cargar el
	 * mundo entero. Cada cofre se actualiza solo la primera vez que alguien pasa
	 * por su chunk, y se vuelve a guardar ya con la version nueva.
	 */
	public static final int VERSION_DATOS = 1;

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

	public Ajustes getAjustes() {
		return this.ajustes;
	}

	/** Para quien toca los ajustes directamente y necesita que se guarden. */
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
	 * Quien puede ABRIR el cofre sin escribir contrasena.
	 *
	 * Con la proteccion apagada, cualquiera. Si no: el dueno, quien tenga
	 * permiso, y un admin con la Llave Maestra.
	 */
	public boolean puedeAbrir(final Player player) {
		if (!this.ajustes.estaProtegido()) {
			return true;
		}
		if (this.esPropietario(player)) {
			return true;
		}
		if (this.ajustes.tienePermiso(player.getUUID())
				|| this.ajustes.tienePendiente(player.getName().getString())) {
			return true;
		}
		return this.esAdminConLlave(player);
	}

	/**
	 * Quien puede ROMPER el cofre y cambiar su configuracion.
	 *
	 * Solo el dueno y un admin con la Llave Maestra, aunque la proteccion este
	 * apagada. Apagarla abre el contenido, no regala el bloque: si no, compartir
	 * un cofre con el clan acabaria con alguien picandolo y llevandoselo entero.
	 * Los permisos tampoco dan derecho a romperlo: mandan sobre el contenido, no
	 * sobre el mueble.
	 */
	public boolean puedeGestionar(final Player player) {
		return this.esPropietario(player) || this.esAdminConLlave(player);
	}

	/** Tener la llave en la mano no basta: hace falta ser admin. Ver {@link Permisos}. */
	private boolean esAdminConLlave(final Player player) {
		return sostieneLlaveMaestra(player) && Permisos.esAdministrador(player);
	}

	/**
	 * Convierte en permiso normal el pendiente de quien acaba de entrar.
	 *
	 * Se llama al abrir, que es la primera vez que tenemos delante a la persona y
	 * por tanto su UUID. Solo tiene sentido en el servidor.
	 */
	public void resolverPendiente(final Player player) {
		String nombre = player.getName().getString();
		if (this.ajustes.quitarPendiente(nombre)) {
			this.ajustes.agregarPermiso(player.getUUID(), nombre);
			this.marcarCambiado();
		}
	}

	public boolean estaProtegido() {
		return this.ajustes.estaProtegido();
	}

	public void setProtegido(final boolean protegido) {
		this.ajustes.setProtegido(protegido);
		this.marcarCambiado();
	}

	public void agregarPendiente(final String nombre) {
		this.ajustes.agregarPendiente(nombre);
		this.marcarCambiado();
	}

	public boolean quitarPendiente(final String nombre) {
		boolean quitado = this.ajustes.quitarPendiente(nombre);
		if (quitado) {
			this.marcarCambiado();
		}
		return quitado;
	}

	public List<String> getPendientes() {
		return this.ajustes.getPendientes();
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
		output.putInt("Version", VERSION_DATOS);
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
		this.migrar(input.getIntOr("Version", 0));
	}

	/**
	 * Lleva los datos de una version anterior del mod a la actual.
	 *
	 * Se llama SIEMPRE al cargar, con la version que traia el bloque. Los pasos
	 * son acumulativos y sin "else": un cofre de la version 0 tiene que poder
	 * atravesar todos los pasos hasta la actual de una sola vez.
	 *
	 * De 0 a 1 no hay nada que transformar, y es a proposito: la 1.1 conserva tal
	 * cual las claves NBT de la 1.0.0 y solo anade campos nuevos, que al faltar
	 * toman su valor por defecto. Un cofre viejo mantiene dueno y contrasena sin
	 * tocar un byte, y su lista de permisos nace vacia. Una migracion que no hace
	 * nada es la unica que no puede fallar; el andamiaje esta aqui para cuando si
	 * haga falta.
	 */
	private void migrar(final int versionGuardada) {
		if (versionGuardada >= VERSION_DATOS) {
			return;
		}
		if (versionGuardada < 1) {
			CofresPersonales.LOGGER.debug("Cofre de la version {} leido como {}",
					versionGuardada, VERSION_DATOS);
		}
	}

	/**
	 * Lo que viaja al cliente: dueno, si hay clave, la lista de permisos y los
	 * flags de tolvas.
	 *
	 * El inventario, el hash y el salt JAMAS se envian: el cliente no debe poder
	 * leer ni el contenido de un cofre ajeno ni nada que sirva para romper la
	 * clave. La lista de permisos si va, porque la pantalla de configuracion
	 * tiene que pintarla. No contiene ningun secreto, pero si es informacion
	 * publica: cualquiera cerca del cofre puede ver quien tiene acceso.
	 */
	public void escribirUpdateTag(final CompoundTag tag) {
		tag.putInt("Version", VERSION_DATOS);
		if (this.propietario != null) {
			tag.store("Propietario", UUIDUtil.CODEC, this.propietario);
			tag.putString("NombrePropietario", this.nombrePropietario);
		}
		tag.putBoolean("Protegido", this.ajustes.estaProtegido());
		tag.putBoolean("TieneClave", this.ajustes.tieneClave());
		List<String> pendientes = this.ajustes.getPendientes();
		if (!pendientes.isEmpty()) {
			tag.store("PermisosPendientes", Codec.STRING.listOf(), pendientes);
		}
		List<Ajustes.Permiso> permisos = this.ajustes.getPermisos();
		if (!permisos.isEmpty()) {
			tag.store("Permisos", Ajustes.Permiso.LISTA, permisos);
		}
		tag.putBoolean("TolvasMeter", this.ajustes.tolvasPuedenMeter());
		tag.putBoolean("TolvasSacar", this.ajustes.tolvasPuedenSacar());
	}
}
