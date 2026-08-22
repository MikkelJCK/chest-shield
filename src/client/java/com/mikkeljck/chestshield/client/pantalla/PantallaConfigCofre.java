package com.mikkeljck.chestshield.client.pantalla;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.client.boton.CofreAbiertoActual;
import com.mikkeljck.chestshield.proteccion.Ajustes;
import com.mikkeljck.chestshield.proteccion.Proteccion;
import com.mikkeljck.chestshield.red.RedCofres;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Toda la configuracion de un cofre: proteccion, clave, tolvas y permisos.
 *
 * No guarda estado propio del cofre a proposito: lee del BlockEntity que el
 * cliente ya tiene sincronizado y manda cada cambio al servidor. Cuando el
 * servidor confirma, la pantalla se rehace sola. Si el servidor rechaza algo,
 * el control vuelve a su sitio sin que haya que programar nada.
 */
public class PantallaConfigCofre extends Screen {

	private static final int ANCHO = 244;
	private static final int MARGEN = 10;
	private static final int FILA_LISTA = 12;
	private static final int MAX_VISIBLES = 5;

	/** Cuantos permisos caben antes de que la lista pase a desplazarse. */
	private static final int PERMISOS_VISIBLES = 3;
	private static final int ALTO_PERMISO = 22;
	private static final int ANCHO_BARRA = 6;

	private static final Identifier BARRA = Identifier.withDefaultNamespace("widget/scroller");
	private static final Identifier BARRA_FONDO = Identifier.withDefaultNamespace("widget/scroller_background");

	private static final int COLOR_TEXTO = PanelVanilla.TEXTO;
	private static final int COLOR_SECCION = PanelVanilla.TEXTO_SUAVE;
	private static final int COLOR_APAGADO = PanelVanilla.TEXTO_APAGADO;
	private static final int COLOR_PENDIENTE = 0xFF8A5A1B;

	private final @Nullable Screen padre;
	private final BlockPos pos;

	private EditBox campoClave;
	private EditBox campoNombre;

	/** Huella del estado del cofre, para rehacer la pantalla solo si cambio. */
	private String huella = "";

	/** Coordenadas de la lista de sugerencias, calculadas en init(). */
	private int listaX;
	private int listaY;
	private int listaAncho;

	private boolean listaAbierta;
	private int desplazamiento;
	private List<String> sugerencias = List.of();

	/** Primera fila visible de la lista de permisos, y estado de su barra. */
	private int desplazamientoPermisos;
	private int permisosY;
	private int permisosX;
	private boolean arrastrandoBarra;

	/** Nombre pendiente de confirmar por estar desconectado. */
	private @Nullable String porConfirmar;

	public PantallaConfigCofre(final @Nullable Screen padre, final BlockPos pos) {
		super(Component.translatable("screen.chest_shield.titulo_config"));
		this.padre = padre;
		this.pos = pos;
	}

	private @Nullable CofrePersonalBlockEntity cofre() {
		if (this.minecraft == null || this.minecraft.level == null) {
			return null;
		}
		return this.minecraft.level.getBlockEntity(this.pos) instanceof CofrePersonalBlockEntity cofre
				? cofre
				: null;
	}

	// ---------- Construccion ----------

	@Override
	protected void init() {
		CofrePersonalBlockEntity cofre = this.cofre();
		if (cofre == null) {
			this.onClose();
			return;
		}
		if (this.porConfirmar != null) {
			this.construirConfirmacion();
			return;
		}

		Ajustes ajustes = cofre.getProteccion().getAjustes();
		boolean protegido = ajustes.estaProtegido();
		this.huella = this.calcularHuella(cofre);

		int izquierda = (this.width - ANCHO) / 2;
		int y = this.arriba(cofre) + 26;

		// ---- El interruptor principal ----
		this.addRenderableWidget(new CasillaVanilla(izquierda + MARGEN, y,
				Component.translatable("screen.chest_shield.protegido"), this.font, protegido,
				valor -> ClientPlayNetworking.send(new RedCofres.CambiarProtegido(this.pos, valor))));
		y += 34;

		// ---- Clave de invitado ----
		String clavePrevia = this.campoClave != null ? this.campoClave.getValue() : "";
		this.campoClave = new EditBox(this.font, izquierda + MARGEN, y, 130, 20,
				Component.translatable("screen.chest_shield.seccion_clave"));
		this.campoClave.setValue(clavePrevia);
		this.campoClave.setMaxLength(Proteccion.LONGITUD_MAXIMA_CLAVE);
		this.campoClave.setHint(Component.translatable(ajustes.tieneClave()
						? "screen.chest_shield.clave_puesta"
						: "screen.chest_shield.sin_clave_aun")
				.withStyle(ChatFormatting.DARK_GRAY));
		this.campoClave.setEditable(protegido);
		this.addRenderableWidget(this.campoClave);

		this.addRenderableWidget(this.activo(protegido, Button.builder(
						Component.translatable("screen.chest_shield.guardar"), boton -> this.guardarClave())
				.bounds(izquierda + MARGEN + 134, y, 66, 20)
				.build()));
		this.addRenderableWidget(this.activo(protegido && ajustes.tieneClave(), Button.builder(
						Component.literal("✖"), boton -> this.borrarClave())
				.bounds(izquierda + ANCHO - MARGEN - 20, y, 20, 20)
				.build()));
		y += 34;

		// ---- Tolvas: no dependen de la proteccion ----
		this.addRenderableWidget(new CasillaVanilla(izquierda + MARGEN, y,
				Component.translatable("screen.chest_shield.tolvas_meter"), this.font,
				ajustes.tolvasPuedenMeter(), valor -> this.enviarTolvas(valor, null)));
		y += 22;
		this.addRenderableWidget(new CasillaVanilla(izquierda + MARGEN, y,
				Component.translatable("screen.chest_shield.tolvas_sacar"), this.font,
				ajustes.tolvasPuedenSacar(), valor -> this.enviarTolvas(null, valor)));
		y += 32;

		// ---- Permisos ----
		this.listaX = izquierda + MARGEN;
		this.listaAncho = 118;
		this.listaY = y + 20;

		String nombrePrevio = this.campoNombre != null ? this.campoNombre.getValue() : "";
		this.campoNombre = new EditBox(this.font, izquierda + MARGEN, y, this.listaAncho, 20,
				Component.translatable("screen.chest_shield.nombre_jugador"));
		this.campoNombre.setMaxLength(16);
		// El valor se pone ANTES del responder: si no, restaurarlo abriria la
		// lista de sugerencias cada vez que se rehace la pantalla.
		this.campoNombre.setValue(nombrePrevio);
		this.campoNombre.setHint(Component.translatable("screen.chest_shield.nombre_jugador")
				.withStyle(ChatFormatting.DARK_GRAY));
		this.campoNombre.setEditable(protegido);
		// Escribir filtra la lista en vivo, como las sugerencias de comandos.
		this.campoNombre.setResponder(texto -> {
			this.recalcularSugerencias();
			this.listaAbierta = !texto.isEmpty() && !this.sugerencias.isEmpty();
		});
		this.addRenderableWidget(this.campoNombre);

		this.addRenderableWidget(this.activo(protegido, Button.builder(
						Component.literal("▼"), boton -> this.alternarLista())
				.bounds(izquierda + MARGEN + 122, y, 20, 20)
				.build()));
		this.addRenderableWidget(this.activo(protegido, Button.builder(
						Component.translatable("screen.chest_shield.anadir"),
						boton -> this.intentarAgregar(this.campoNombre.getValue()))
				.bounds(izquierda + ANCHO - MARGEN - 68, y, 68, 20)
				.build()));
		y += 24;

		// La lista de permisos no crece sin fin: a partir de PERMISOS_VISIBLES se
		// queda quieta y aparece una barra. Solo se crean los botones de las filas
		// que se ven, asi que scrollar obliga a rehacer los widgets.
		List<String> conPermiso = this.nombresConPermiso(cofre);
		int filas = this.filasPermisos(cofre);
		this.desplazamientoPermisos = Math.clamp(this.desplazamientoPermisos, 0,
				Math.max(0, conPermiso.size() - PERMISOS_VISIBLES));
		this.permisosX = izquierda + MARGEN;
		this.permisosY = y;

		boolean hayBarra = conPermiso.size() > PERMISOS_VISIBLES;
		int xBorrar = izquierda + ANCHO - MARGEN - 20 - (hayBarra ? ANCHO_BARRA + 4 : 0);

		for (int fila = 0; fila < filas; fila++) {
			String nombre = conPermiso.get(fila + this.desplazamientoPermisos);
			this.addRenderableWidget(this.activo(protegido, Button.builder(Component.literal("✖"),
							boton -> this.enviarPermiso(nombre, false, false))
					.bounds(xBorrar, y, 20, 20)
					.build()));
			y += ALTO_PERMISO;
		}

		this.addRenderableWidget(Button.builder(
						Component.translatable("screen.chest_shield.listo"), boton -> this.onClose())
				.bounds(izquierda + ANCHO - MARGEN - 80, y + 6, 80, 20)
				.build());

		this.recalcularSugerencias();
	}

	private void construirConfirmacion() {
		int centroX = this.width / 2;
		int centroY = this.height / 2;
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_YES, boton -> {
					String nombre = this.porConfirmar;
					this.porConfirmar = null;
					if (nombre != null) {
						this.enviarPermiso(nombre, true, true);
					}
					this.rebuildWidgets();
				})
				.bounds(centroX - 104, centroY + 20, 100, 20)
				.build());
		this.addRenderableWidget(Button.builder(CommonComponents.GUI_NO, boton -> {
					this.porConfirmar = null;
					this.rebuildWidgets();
				})
				.bounds(centroX + 4, centroY + 20, 100, 20)
				.build());
	}

	private <T extends AbstractWidget> T activo(final boolean activo, final T widget) {
		widget.active = activo;
		return widget;
	}

	/** Permisos ya resueltos primero, y despues los pendientes de que entren. */
	private List<String> nombresConPermiso(final CofrePersonalBlockEntity cofre) {
		List<String> nombres = new ArrayList<>();
		for (Ajustes.Permiso permiso : cofre.getPermisos()) {
			nombres.add(permiso.nombre());
		}
		nombres.addAll(cofre.getProteccion().getPendientes());
		return nombres;
	}

	private int filasPermisos(final CofrePersonalBlockEntity cofre) {
		return Math.min(PERMISOS_VISIBLES, this.nombresConPermiso(cofre).size());
	}

	private int altoPanel(final CofrePersonalBlockEntity cofre) {
		return 208 + this.filasPermisos(cofre) * ALTO_PERMISO;
	}

	private int arriba(final CofrePersonalBlockEntity cofre) {
		return (this.height - this.altoPanel(cofre)) / 2;
	}

	// ---------- Sugerencias ----------

	private void alternarLista() {
		this.listaAbierta = !this.listaAbierta;
		this.desplazamiento = 0;
		if (this.listaAbierta) {
			this.recalcularSugerencias();
		}
	}

	/**
	 * Los conectados que encajan con lo escrito.
	 *
	 * La lista de conectados ya la tiene el cliente: es la misma de la tecla Tab,
	 * asi que no hay que pedirle nada al servidor. Se descartan uno mismo y quien
	 * ya tiene permiso.
	 */
	private void recalcularSugerencias() {
		CofrePersonalBlockEntity cofre = this.cofre();
		if (this.minecraft == null || this.minecraft.getConnection() == null
				|| this.minecraft.player == null || cofre == null || this.campoNombre == null) {
			this.sugerencias = List.of();
			return;
		}
		String filtro = this.campoNombre.getValue().toLowerCase(Locale.ROOT);
		String propio = this.minecraft.player.getName().getString();
		List<String> yaEstan = this.nombresConPermiso(cofre);

		List<String> encontrados = new ArrayList<>();
		for (PlayerInfo info : this.minecraft.getConnection().getOnlinePlayers()) {
			String nombre = info.getProfile().name();
			if (nombre.equalsIgnoreCase(propio)) {
				continue;
			}
			if (yaEstan.stream().anyMatch(puesto -> puesto.equalsIgnoreCase(nombre))) {
				continue;
			}
			if (filtro.isEmpty() || nombre.toLowerCase(Locale.ROOT).contains(filtro)) {
				encontrados.add(nombre);
			}
		}
		encontrados.sort(String::compareToIgnoreCase);
		this.sugerencias = List.copyOf(encontrados);
		this.desplazamiento = Math.min(this.desplazamiento,
				Math.max(0, this.sugerencias.size() - MAX_VISIBLES));
	}

	private int filasVisibles() {
		return Math.min(MAX_VISIBLES, this.sugerencias.size());
	}

	private int altoLista() {
		return this.filasVisibles() * FILA_LISTA + 2;
	}

	@Override
	public boolean mouseClicked(final MouseButtonEvent evento, final boolean dobleClick) {
		if (this.listaAbierta && !this.sugerencias.isEmpty()) {
			int alto = this.altoLista();
			if (evento.x() >= this.listaX && evento.x() <= this.listaX + this.listaAncho
					&& evento.y() >= this.listaY && evento.y() <= this.listaY + alto) {
				int fila = (int) ((evento.y() - this.listaY - 1) / FILA_LISTA) + this.desplazamiento;
				if (fila >= 0 && fila < this.sugerencias.size()) {
					this.campoNombre.setValue(this.sugerencias.get(fila));
					this.listaAbierta = false;
				}
				return true;
			}
		}
		if (this.maximoDesplazamiento() > 0) {
			int xBarra = (this.width - ANCHO) / 2 + ANCHO - MARGEN - ANCHO_BARRA;
			if (evento.x() >= xBarra && evento.x() <= xBarra + ANCHO_BARRA
					&& evento.y() >= this.permisosY
					&& evento.y() <= this.permisosY + PERMISOS_VISIBLES * ALTO_PERMISO) {
				this.arrastrandoBarra = true;
				this.arrastrarBarra(evento.y());
				return true;
			}
		}
		return super.mouseClicked(evento, dobleClick);
	}

	@Override
	public boolean mouseDragged(final MouseButtonEvent evento, final double dx, final double dy) {
		if (this.arrastrandoBarra) {
			this.arrastrarBarra(evento.y());
			return true;
		}
		return super.mouseDragged(evento, dx, dy);
	}

	@Override
	public boolean mouseReleased(final MouseButtonEvent evento) {
		this.arrastrandoBarra = false;
		return super.mouseReleased(evento);
	}

	@Override
	public boolean mouseScrolled(final double x, final double y, final double dx, final double dy) {
		if (this.listaAbierta && this.sugerencias.size() > MAX_VISIBLES
				&& x >= this.listaX && x <= this.listaX + this.listaAncho
				&& y >= this.listaY && y <= this.listaY + this.altoLista()) {
			int maximo = this.sugerencias.size() - MAX_VISIBLES;
			this.desplazamiento = Math.clamp(this.desplazamiento - (int) Math.signum(dy), 0, maximo);
			return true;
		}
		int maximoPermisos = this.maximoDesplazamiento();
		if (maximoPermisos > 0 && this.sobreListaPermisos(x, y)) {
			int nuevo = Math.clamp(this.desplazamientoPermisos - (int) Math.signum(dy), 0, maximoPermisos);
			if (nuevo != this.desplazamientoPermisos) {
				this.desplazamientoPermisos = nuevo;
				this.rebuildWidgets();
			}
			return true;
		}
		return super.mouseScrolled(x, y, dx, dy);
	}

	// ---------- Envio de cambios ----------

	private void guardarClave() {
		String clave = this.campoClave.getValue();
		if (clave.isBlank()) {
			return;
		}
		ClientPlayNetworking.send(new RedCofres.EstablecerClave(this.pos, clave));
		this.campoClave.setValue("");
	}

	/** Cadena vacia = el servidor la borra. */
	private void borrarClave() {
		ClientPlayNetworking.send(new RedCofres.EstablecerClave(this.pos, ""));
		this.campoClave.setValue("");
	}

	private void enviarTolvas(final @Nullable Boolean meter, final @Nullable Boolean sacar) {
		CofrePersonalBlockEntity cofre = this.cofre();
		if (cofre == null) {
			return;
		}
		Ajustes ajustes = cofre.getProteccion().getAjustes();
		ClientPlayNetworking.send(new RedCofres.CambiarTolvas(this.pos,
				meter != null ? meter : ajustes.tolvasPuedenMeter(),
				sacar != null ? sacar : ajustes.tolvasPuedenSacar()));
	}

	/**
	 * Si el nombre no esta entre los conectados, se pregunta antes de mandarlo.
	 * El cliente ya tiene la lista, asi que puede avisar sin ir al servidor.
	 */
	private void intentarAgregar(final String nombre) {
		if (nombre.isBlank()) {
			return;
		}
		if (this.estaConectado(nombre)) {
			this.enviarPermiso(nombre, true, false);
			return;
		}
		this.porConfirmar = nombre;
		this.listaAbierta = false;
		this.rebuildWidgets();
	}

	private boolean estaConectado(final String nombre) {
		if (this.minecraft == null || this.minecraft.getConnection() == null) {
			return false;
		}
		for (PlayerInfo info : this.minecraft.getConnection().getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(nombre)) {
				return true;
			}
		}
		return false;
	}

	private void enviarPermiso(final String nombre, final boolean agregar, final boolean forzar) {
		ClientPlayNetworking.send(new RedCofres.CambiarPermiso(this.pos, nombre, agregar, forzar));
		if (agregar && this.campoNombre != null) {
			this.campoNombre.setValue("");
		}
	}

	// ---------- Refresco ----------

	@Override
	public void tick() {
		super.tick();
		CofrePersonalBlockEntity cofre = this.cofre();
		if (cofre == null) {
			this.onClose();
			return;
		}
		if (this.porConfirmar == null && !this.huella.equals(this.calcularHuella(cofre))) {
			this.rebuildWidgets();
		}
	}

	private String calcularHuella(final CofrePersonalBlockEntity cofre) {
		Ajustes ajustes = cofre.getProteccion().getAjustes();
		StringBuilder huella = new StringBuilder()
				.append(ajustes.estaProtegido()).append('|')
				.append(ajustes.tieneClave()).append('|')
				.append(ajustes.tolvasPuedenMeter()).append('|')
				.append(ajustes.tolvasPuedenSacar()).append('|');
		for (String nombre : this.nombresConPermiso(cofre)) {
			huella.append(nombre).append(',');
		}
		return huella.toString();
	}

	// ---------- Dibujo ----------

	@Override
	public void extractRenderState(final GuiGraphicsExtractor graficos, final int ratonX, final int ratonY,
			final float parcial) {
		CofrePersonalBlockEntity cofre = this.cofre();
		if (cofre == null) {
			super.extractRenderState(graficos, ratonX, ratonY, parcial);
			return;
		}

		// El panel va ANTES de super, para que quede debajo de los widgets.
		if (this.porConfirmar != null) {
			int anchoAviso = 240;
			PanelVanilla.dibujar(graficos, (this.width - anchoAviso) / 2, this.height / 2 - 48, anchoAviso, 100);
			super.extractRenderState(graficos, ratonX, ratonY, parcial);
			this.dibujarConfirmacion(graficos);
			return;
		}

		Ajustes ajustes = cofre.getProteccion().getAjustes();
		boolean protegido = ajustes.estaProtegido();
		int izquierda = (this.width - ANCHO) / 2;
		int arriba = this.arriba(cofre);

		PanelVanilla.dibujar(graficos, izquierda, arriba, ANCHO, this.altoPanel(cofre));
		super.extractRenderState(graficos, ratonX, ratonY, parcial);

		PanelVanilla.textoCentrado(graficos, this.font,
				Component.translatable("container.chest_shield.cofre_de", cofre.getNombreMostrado()),
				this.width / 2, arriba + 9, COLOR_TEXTO);

		int y = arriba + 26 + 34;
		graficos.text(this.font, Component.translatable("screen.chest_shield.seccion_clave"),
				izquierda + MARGEN, y - 12, protegido ? COLOR_SECCION : COLOR_APAGADO, false);
		y += 34;
		graficos.text(this.font, Component.translatable("screen.chest_shield.seccion_tolvas"),
				izquierda + MARGEN, y - 12, COLOR_SECCION, false);
		y += 22 + 32;
		graficos.text(this.font, Component.translatable("screen.chest_shield.seccion_permisos"),
				izquierda + MARGEN, y - 12, protegido ? COLOR_SECCION : COLOR_APAGADO, false);

		y += 24;
		List<String> conPermiso = this.nombresConPermiso(cofre);
		List<String> pendientes = cofre.getProteccion().getPendientes();
		int filas = this.filasPermisos(cofre);
		for (int fila = 0; fila < filas; fila++) {
			String nombre = conPermiso.get(fila + this.desplazamientoPermisos);
			boolean pendiente = pendientes.stream().anyMatch(p -> p.equalsIgnoreCase(nombre));
			Component texto = pendiente
					? Component.translatable("screen.chest_shield.pendiente", nombre)
					: Component.literal(nombre);
			graficos.text(this.font, texto, izquierda + MARGEN + 4, y + 6,
					pendiente ? COLOR_PENDIENTE : (protegido ? COLOR_TEXTO : COLOR_APAGADO), false);
			y += ALTO_PERMISO;
		}
		if (conPermiso.isEmpty()) {
			graficos.text(this.font, Component.translatable("screen.chest_shield.sin_permisos"),
					izquierda + MARGEN + 4, y + 6, COLOR_APAGADO, false);
		}
		if (conPermiso.size() > PERMISOS_VISIBLES) {
			this.dibujarBarra(graficos, izquierda, conPermiso.size());
		}

		// La lista va al final, para que quede por encima de todo lo demas.
		if (this.listaAbierta && !this.sugerencias.isEmpty()) {
			this.dibujarLista(graficos, ratonX, ratonY);
		}
	}

	private void dibujarLista(final GuiGraphicsExtractor graficos, final int ratonX, final int ratonY) {
		int alto = this.altoLista();
		graficos.fill(this.listaX, this.listaY, this.listaX + this.listaAncho, this.listaY + alto, 0xF0100010);
		graficos.fill(this.listaX, this.listaY, this.listaX + this.listaAncho, this.listaY + 1, 0xFFA0A0A0);
		graficos.fill(this.listaX, this.listaY + alto - 1, this.listaX + this.listaAncho, this.listaY + alto, 0xFFA0A0A0);

		for (int fila = 0; fila < this.filasVisibles(); fila++) {
			int indice = fila + this.desplazamiento;
			int filaY = this.listaY + 1 + fila * FILA_LISTA;
			boolean encima = ratonX >= this.listaX && ratonX <= this.listaX + this.listaAncho
					&& ratonY >= filaY && ratonY < filaY + FILA_LISTA;
			if (encima) {
				graficos.fill(this.listaX + 1, filaY, this.listaX + this.listaAncho - 1, filaY + FILA_LISTA, 0xFF5C5C5C);
			}
			// Esta lista va sobre fondo negro, como las sugerencias de comandos:
			// aqui el texto si es blanco y con sombra.
			graficos.text(this.font, Component.literal(this.sugerencias.get(indice)),
					this.listaX + 4, filaY + 2, 0xFFE8E8E8);
		}
	}

	/** Barra de desplazamiento de la lista de permisos, con los sprites de vanilla. */
	private void dibujarBarra(final GuiGraphicsExtractor graficos, final int izquierda, final int total) {
		int x = izquierda + ANCHO - MARGEN - ANCHO_BARRA;
		int alto = PERMISOS_VISIBLES * ALTO_PERMISO;
		graficos.blitSprite(RenderPipelines.GUI_TEXTURED, BARRA_FONDO, x, this.permisosY, ANCHO_BARRA, alto);

		int altoTirador = Math.max(12, alto * PERMISOS_VISIBLES / total);
		int recorrido = alto - altoTirador;
		int maximo = total - PERMISOS_VISIBLES;
		int y = this.permisosY + (maximo == 0 ? 0 : recorrido * this.desplazamientoPermisos / maximo);
		graficos.blitSprite(RenderPipelines.GUI_TEXTURED, BARRA, x, y, ANCHO_BARRA, altoTirador);
	}

	/** Cuantas filas se puede bajar como maximo. */
	private int maximoDesplazamiento() {
		CofrePersonalBlockEntity cofre = this.cofre();
		return cofre == null ? 0 : Math.max(0, this.nombresConPermiso(cofre).size() - PERMISOS_VISIBLES);
	}

	private boolean sobreListaPermisos(final double x, final double y) {
		return x >= this.permisosX && x <= this.permisosX + ANCHO - 2 * MARGEN
				&& y >= this.permisosY && y <= this.permisosY + PERMISOS_VISIBLES * ALTO_PERMISO;
	}

	/** Mapea una posicion del raton dentro de la barra a una fila. */
	private void arrastrarBarra(final double y) {
		int maximo = this.maximoDesplazamiento();
		if (maximo == 0) {
			return;
		}
		int alto = PERMISOS_VISIBLES * ALTO_PERMISO;
		double proporcion = (y - this.permisosY) / alto;
		int nuevo = Math.clamp((int) Math.round(proporcion * maximo), 0, maximo);
		if (nuevo != this.desplazamientoPermisos) {
			this.desplazamientoPermisos = nuevo;
			this.rebuildWidgets();
		}
	}

	private void dibujarConfirmacion(final GuiGraphicsExtractor graficos) {
		int centroX = this.width / 2;
		int centroY = this.height / 2;
		PanelVanilla.textoCentrado(graficos, this.font,
				Component.translatable("screen.chest_shield.no_conectado_titulo", this.porConfirmar),
				centroX, centroY - 30, COLOR_TEXTO);
		PanelVanilla.textoCentrado(graficos, this.font,
				Component.translatable("screen.chest_shield.no_conectado_aviso"),
				centroX, centroY - 12, COLOR_SECCION);
		PanelVanilla.textoCentrado(graficos, this.font,
				Component.translatable("screen.chest_shield.no_conectado_aviso2"),
				centroX, centroY, COLOR_SECCION);
	}

	/**
	 * Volver a la pantalla de la que venimos, si venimos de alguna.
	 *
	 * OJO: en 26.1.2 setScreen vive en Minecraft; en 26.2 se movio a Gui. Es una
	 * de las tres unicas diferencias de codigo entre las dos ramas.
	 */
	@Override
	public void onClose() {
		if (this.minecraft != null && this.padre != null) {
			// Al abrir esta pantalla, la del cofre se destruyo y con ella se
			// olvido su posicion. Hay que devolverla ANTES de volver, porque el
			// cofre se reconstruye al instante y es entonces cuando decide si
			// dibuja el candado. Sin esto el boton desaparece al volver.
			CofreAbiertoActual.establecer(this.pos);
			this.minecraft.setScreen(this.padre);
			return;
		}
		super.onClose();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/**
	 * Sin esto, Screen trata la pantalla como un menu: desenfoca el mundo y le
	 * pone encima la textura de fondo de los menus. Marcandola como interfaz de
	 * juego se comporta igual que la pantalla del cofre, con el mismo degradado
	 * oscuro y sin desenfoque.
	 */
	@Override
	public boolean isInGameUi() {
		return true;
	}
}
