package com.mikkeljck.chestshield.client.pantalla;

import com.mikkeljck.chestshield.block.CofrePersonalBlockEntity;
import com.mikkeljck.chestshield.red.RedCofres;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Pantalla del dueno para poner, cambiar o quitar la contrasena. */
public class PantallaConfigClave extends BaseOwoScreen<FlowLayout> {

	private final BlockPos pos;
	private final boolean teniaClave;
	private TextBoxComponent campo;

	public PantallaConfigClave(final BlockPos pos, final boolean teniaClave) {
		super(Component.translatable("screen.chest_shield.titulo_config"));
		this.pos = pos;
		this.teniaClave = teniaClave;
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	protected void build(final FlowLayout raiz) {
		this.campo = UIComponents.textBox(Sizing.fixed(170));
		this.campo.setMaxLength(CofrePersonalBlockEntity.LONGITUD_MAXIMA_CLAVE);

		raiz.surface(Surface.VANILLA_TRANSLUCENT)
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.verticalAlignment(VerticalAlignment.CENTER);

		// Sin titulo aparte: la propia frase de estado ya dice de que va la pantalla,
		// y el boton cambia de "Guardar" a "Cambiar" segun si ya habia contrasena.
		FlowLayout panel = UIContainers.verticalFlow(Sizing.content(), Sizing.content())
				.child(UIComponents.label(Component.translatable(this.teniaClave
						? "screen.chest_shield.tiene_clave"
						: "screen.chest_shield.sin_clave"))
						.shadow(true))
				.child(this.campo)
				.child(UIComponents.button(
						Component.translatable(this.teniaClave
								? "screen.chest_shield.cambiar"
								: "screen.chest_shield.guardar"),
						boton -> this.enviar(this.campo.getValue())));

		if (this.teniaClave) {
			panel.child(UIComponents.button(
					Component.translatable("screen.chest_shield.quitar"),
					boton -> this.enviar("")));
		}

		raiz.child(panel
				.gap(8)
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.padding(Insets.of(12))
				.surface(Surface.flat(0x99000000).and(Surface.outline(0x77000000))));
	}

	private void enviar(final String clave) {
		RedCofres.CANAL.clientHandle().send(new RedCofres.EstablecerClave(this.pos, clave));
		this.onClose();
	}
}
