package dev.hephaestus.landmark.impl.client;

import dev.hephaestus.landmark.impl.network.LandmarkNetworking;
import dev.hephaestus.landmark.impl.world.LandmarkTrackingComponent;
import dev.hephaestus.landmark.impl.world.chunk.LandmarkChunkComponent;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.network.PacketContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeleteLandmarkScreen extends Screen {
	protected DeleteLandmarkScreen() {
		super(LiteralText.EMPTY);
	}

	private List<ButtonWidget> landmarks;

	@Override
	public void init(MinecraftClient client, int width, int height) {
		super.init(client, width, height);

		this.client = client;

		landmarks = new ArrayList<>();
		LandmarkTrackingComponent tracker = LandmarkTrackingComponent.of(this.client.world);
		if (this.client.world != null && this.client.player != null) {
			List<UUID> sections = LandmarkChunkComponent.of(this.client.world.getChunk(this.client.player.chunkX, this.client.player.chunkZ)).getIds();
			for (int i = 0; i < sections.size(); ++i) {
				if (tracker.get(sections.get(i)).canModify(this.client.player)) {
					int finalI = i;
					ButtonWidget widget = new LandmarkButtonWidget(width / 2 - 100, width / 10 + 20 * i, 200, 20, tracker.get(sections.get(i)), (action) -> {
						PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
						buf.writeUuid(sections.get(finalI));
						buf.writeBoolean(false);
						ClientSidePacketRegistry.INSTANCE.sendToServer(LandmarkNetworking.TRACKER_DELETE_LANDMARK, buf);
						client.openScreen(null);
					});

					landmarks.add(widget);
					this.addButton(widget);
				}
			}
		}
	}

	@Override
	public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
		fill(matrices, 0, 0, this.client.getWindow().getFramebufferWidth(),  this.client.getWindow().getFramebufferHeight(), 0x88000000);
		int offset = this.width / 10;
		this.client.textRenderer.drawWithShadow(matrices, new TranslatableText("deeds.landmark.delete.message"), this.width / 2F - 100, offset - 20, 0xFFFFFF);
		for (ButtonWidget widget : this.landmarks) {
			widget.render(matrices, mouseX, mouseY, delta);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	public static void open(PacketContext context, PacketByteBuf buf) {
		context.getTaskQueue().execute(() -> {
			MinecraftClient.getInstance().openScreen(new DeleteLandmarkScreen());
		});
	}
}
