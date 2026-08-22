package com.mikkeljck.chestshield.proteccion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.mikkeljck.chestshield.util.HashClave;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Lo que se puede configurar de un contenedor blindado: la clave de invitado,
 * la lista de jugadores con permiso y si las tolvas pueden tocarlo.
 *
 * Esta separado de {@link Proteccion} porque el mismo bloque de ajustes va a
 * existir en dos sitios: dentro de cada cofre, y una vez por jugador como
 * perfil global. El cofre decidira de cual de los dos lee.
 *
 * El dueno NO esta aqui: el dueno es del cofre, no de la configuracion.
 */
public class Ajustes {

	public static final int LONGITUD_MAXIMA_CLAVE = 32;

	/** Una entrada de la lista de permisos. El nombre solo sirve para mostrarlo. */
	public record Permiso(UUID uuid, String nombre) {
		public static final Codec<Permiso> CODEC = RecordCodecBuilder.create(instancia -> instancia.group(
				UUIDUtil.CODEC.fieldOf("uuid").forGetter(Permiso::uuid),
				Codec.STRING.fieldOf("nombre").forGetter(Permiso::nombre)
		).apply(instancia, Permiso::new));

		public static final Codec<List<Permiso>> LISTA = CODEC.listOf();
	}

	/**
	 * El interruptor principal. Apagado, el cofre se abre como uno de vanilla:
	 * ni clave ni permisos tienen efecto. Encendido por defecto, porque proteger
	 * es la razon de ser del bloque.
	 *
	 * Apagarlo NO lo vuelve destruible: la resistencia a explosiones y a pistones
	 * esta en las propiedades del bloque, no aqui, y romperlo sigue siendo cosa
	 * exclusiva del dueno.
	 */
	private boolean protegido = true;

	/**
	 * "Hay una clave guardada". Es lo unico que viaja al cliente sobre la clave;
	 * el hash y el salt no salen del servidor.
	 */
	private boolean hayClave;
	private String hashClave = "";
	private String saltClave = "";

	/**
	 * Permisos dados a alguien que no estaba conectado, guardados por nombre en
	 * minusculas.
	 *
	 * Existen porque un permiso normal se guarda por UUID (para que sobreviva a
	 * un cambio de nick) y el UUID de un jugador desconectado no se puede
	 * averiguar sin preguntarle a Mojang. La primera vez que esa persona abre el
	 * cofre, el pendiente se convierte solo en un permiso por UUID.
	 */
	private final Set<String> pendientes = new LinkedHashSet<>();

	/** Orden de insercion, para que la lista no baile cada vez que se abre la pantalla. */
	private final Map<UUID, String> permisos = new LinkedHashMap<>();

	private boolean tolvasMeter;
	private boolean tolvasSacar;

	// ---------- Clave ----------

	public boolean estaProtegido() {
		return this.protegido;
	}

	public void setProtegido(final boolean protegido) {
		this.protegido = protegido;
	}

	/**
	 * OJO: tiene que responder bien EN EL CLIENTE, donde el hash nunca llega. Por
	 * eso mira el booleano y no el hash.
	 */
	public boolean tieneClave() {
		return this.hayClave;
	}

	/** Cadena vacia = borrar la clave del todo. Solo debe llamarse en el servidor. */
	public void establecerClave(final String clave) {
		if (clave.isBlank()) {
			this.hayClave = false;
			this.hashClave = "";
			this.saltClave = "";
		} else {
			this.saltClave = HashClave.nuevoSalt();
			this.hashClave = HashClave.calcular(clave, this.saltClave);
			this.hayClave = true;
		}
	}

	public boolean verificarClave(final String clave) {
		return HashClave.coincide(clave, this.saltClave, this.hashClave);
	}

	/** Dos cofres solo se unen si sus claves son identicas, o si a alguno le falta. */
	public boolean claveCompatibleCon(final Ajustes otros) {
		if (!this.tieneClave() || !otros.tieneClave()) {
			return true;
		}
		return this.hashClave.equals(otros.hashClave) && this.saltClave.equals(otros.saltClave);
	}

	// ---------- Permisos ----------

	public boolean tienePermiso(final UUID jugador) {
		return this.permisos.containsKey(jugador);
	}

	public boolean tienePendiente(final String nombre) {
		return this.pendientes.contains(nombre.toLowerCase(Locale.ROOT));
	}

	public void agregarPendiente(final String nombre) {
		this.pendientes.add(nombre.toLowerCase(Locale.ROOT));
	}

	public boolean quitarPendiente(final String nombre) {
		return this.pendientes.remove(nombre.toLowerCase(Locale.ROOT));
	}

	public List<String> getPendientes() {
		return List.copyOf(this.pendientes);
	}

	public void agregarPermiso(final UUID jugador, final String nombre) {
		this.permisos.put(jugador, nombre);
	}

	public boolean quitarPermiso(final UUID jugador) {
		return this.permisos.remove(jugador) != null;
	}

	/** Copia inmutable: nadie de fuera toca el mapa por su cuenta. */
	public List<Permiso> getPermisos() {
		List<Permiso> lista = new ArrayList<>(this.permisos.size());
		this.permisos.forEach((uuid, nombre) -> lista.add(new Permiso(uuid, nombre)));
		return List.copyOf(lista);
	}

	// ---------- Tolvas ----------

	public boolean tolvasPuedenMeter() {
		return this.tolvasMeter;
	}

	public boolean tolvasPuedenSacar() {
		return this.tolvasSacar;
	}

	public void setTolvasMeter(final boolean permitido) {
		this.tolvasMeter = permitido;
	}

	public void setTolvasSacar(final boolean permitido) {
		this.tolvasSacar = permitido;
	}

	// ---------- Copia ----------

	/** Se usa al emparejar dos cofres: las dos mitades son un solo mueble. */
	public void copiarDe(final Ajustes otros) {
		if (otros == this) {
			return;
		}
		this.protegido = otros.protegido;
		this.hayClave = otros.hayClave;
		this.hashClave = otros.hashClave;
		this.saltClave = otros.saltClave;
		this.permisos.clear();
		this.permisos.putAll(otros.permisos);
		this.pendientes.clear();
		this.pendientes.addAll(otros.pendientes);
		this.tolvasMeter = otros.tolvasMeter;
		this.tolvasSacar = otros.tolvasSacar;
	}

	// ---------- Guardado ----------
	// "TieneClave", "HashClave" y "SaltClave" son las claves NBT de la version
	// 1.0.0. NO se pueden renombrar: hay mundos publicados con cofres colocados,
	// y si cambian, esos jugadores pierden sus contrasenas al actualizar.

	public void guardar(final ValueOutput output) {
		output.putBoolean("Protegido", this.protegido);
		output.putBoolean("TieneClave", this.hayClave);
		if (!this.hashClave.isEmpty()) {
			output.putString("HashClave", this.hashClave);
			output.putString("SaltClave", this.saltClave);
		}
		List<Permiso> lista = this.getPermisos();
		if (!lista.isEmpty()) {
			output.store("Permisos", Permiso.LISTA, lista);
		}
		if (!this.pendientes.isEmpty()) {
			output.store("PermisosPendientes", Codec.STRING.listOf(), this.getPendientes());
		}
		output.putBoolean("TolvasMeter", this.tolvasMeter);
		output.putBoolean("TolvasSacar", this.tolvasSacar);
	}

	public void cargar(final ValueInput input) {
		// Por defecto protegido: es lo que eran todos los cofres de la 1.0.0.
		this.protegido = input.getBooleanOr("Protegido", true);
		this.hayClave = input.getBooleanOr("TieneClave", false);
		this.hashClave = input.getStringOr("HashClave", "");
		this.saltClave = input.getStringOr("SaltClave", "");
		this.pendientes.clear();
		input.read("PermisosPendientes", Codec.STRING.listOf())
				.ifPresent(lista -> lista.forEach(nombre -> this.pendientes.add(nombre.toLowerCase(Locale.ROOT))));
		this.permisos.clear();
		input.read("Permisos", Permiso.LISTA).ifPresent(lista -> {
			for (Permiso permiso : lista) {
				this.permisos.put(permiso.uuid(), permiso.nombre());
			}
		});
		this.tolvasMeter = input.getBooleanOr("TolvasMeter", false);
		this.tolvasSacar = input.getBooleanOr("TolvasSacar", false);
	}
}
