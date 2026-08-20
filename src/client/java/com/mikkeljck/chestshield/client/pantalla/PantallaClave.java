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

/** Pantalla que se le muestra a un jugador ajeno cuando el cofre tiene clave. */
public class PantallaClave extends BaseOwoScreen<FlowLayout> {

	private final BlockPos pos;
	private final String duenno;
	private TextBoxComponent campo;

	public PantallaClave(final BlockPos pos, final String duenno) {
		super(Component.translatable("screen.chest_shield.titulo_clave"));
		this.pos = pos;
		this.duenno = duenno;
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

		raiz.child(UIContainers.verticalFlow(Sizing.content(), Sizing.content())
				.child(UIComponents.label(Component.translatable("screen.chest_shield.cofre_de", this.duenno))
						.shadow(true))
				.child(UIComponents.label(Component.translatable("screen.chest_shield.escribe_clave")))
				.child(this.campo)
				.child(UIComponents.button(
						Component.translatable("screen.chest_shield.abrir"),
						boton -> this.enviar()))
				.gap(8)
				.horizontalAlignment(HorizontalAlignment.CENTER)
				.padding(Insets.of(12))
				.surface(Surface.flat(0x99000000).and(Surface.outline(0x77000000))));
	}

	private void enviar() {
		RedCofres.CANAL.clientHandle().send(new RedCofres.IntentoClave(this.pos, this.campo.getValue()));
		this.onClose();
	}
}
