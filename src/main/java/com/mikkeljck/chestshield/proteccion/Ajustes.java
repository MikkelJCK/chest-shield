package com.mikkeljck.chestshield.proteccion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	 * "Activada" y "hay una clave guardada" son cosas distintas: apagar la clave
	 * no la borra, para poder volver a encenderla sin escribirla otra vez.
	 */
	private boolean claveActiva;
	private String hashClave = "";
	private String saltClave = "";

	/** Orden de insercion, para que la lista no baile cada vez que se abre la pantalla. */
	private final Map<UUID, String> permisos = new LinkedHashMap<>();

	private boolean tolvasMeter;
	private boolean tolvasSacar;

	// ---------- Clave ----------

	/**
	 * OJO: esto tiene que poder responder bien EN EL CLIENTE, donde el hash nunca
	 * llega. Por eso mira solo el booleano, y el invariante "activa implica que
	 * hay hash" se mantiene en setClaveActiva y establecerClave.
	 */
	public boolean tieneClave() {
		return this.claveActiva;
	}

	public boolean hayClaveGuardada() {
		return !this.hashClave.isEmpty();
	}

	/** No se puede activar una clave que no existe. */
	public void setClaveActiva(final boolean activa) {
		this.claveActiva = activa && !this.hashClave.isEmpty();
	}

	/** Cadena vacia = borrar la clave del todo. Solo debe llamarse en el servidor. */
	public void establecerClave(final String clave) {
		if (clave.isBlank()) {
			this.claveActiva = false;
			this.hashClave = "";
			this.saltClave = "";
		} else {
			this.saltClave = HashClave.nuevoSalt();
			this.hashClave = HashClave.calcular(clave, this.saltClave);
			this.claveActiva = true;
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
		this.claveActiva = otros.claveActiva;
		this.hashClave = otros.hashClave;
		this.saltClave = otros.saltClave;
		this.permisos.clear();
		this.permisos.putAll(otros.permisos);
		this.tolvasMeter = otros.tolvasMeter;
		this.tolvasSacar = otros.tolvasSacar;
	}

	// ---------- Guardado ----------
	// "TieneClave", "HashClave" y "SaltClave" son las claves NBT de la version
	// 1.0.0. NO se pueden renombrar: hay mundos publicados con cofres colocados,
	// y si cambian, esos jugadores pierden sus contrasenas al actualizar.

	public void guardar(final ValueOutput output) {
		output.putBoolean("TieneClave", this.claveActiva);
		if (!this.hashClave.isEmpty()) {
			output.putString("HashClave", this.hashClave);
			output.putString("SaltClave", this.saltClave);
		}
		List<Permiso> lista = this.getPermisos();
		if (!lista.isEmpty()) {
			output.store("Permisos", Permiso.LISTA, lista);
		}
		output.putBoolean("TolvasMeter", this.tolvasMeter);
		output.putBoolean("TolvasSacar", this.tolvasSacar);
	}

	public void cargar(final ValueInput input) {
		this.claveActiva = input.getBooleanOr("TieneClave", false);
		this.hashClave = input.getStringOr("HashClave", "");
		this.saltClave = input.getStringOr("SaltClave", "");
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
