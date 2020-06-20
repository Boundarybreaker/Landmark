package dev.hephaestus.landmark.impl.client;

import dev.hephaestus.landmark.impl.LandmarkMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.network.PacketContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;

import static dev.hephaestus.landmark.impl.LandmarkClient.CONFIG;

@Environment(EnvType.CLIENT)
public class LandmarkNameHandler extends DrawableHelper {
	private static Text NAME;

	private static int NAME_DISPLAY_TOTAL_TICKS = 0;

	@Environment(EnvType.CLIENT)
	public static void init() {
		ClientSidePacketRegistry.INSTANCE.register(LandmarkMod.LANDMARK_DISCOVERED_PACKET, LandmarkNameHandler::accept);
		HudRenderCallback.EVENT.register(LandmarkNameHandler::draw);
	}

	private static int duration() {
		return (int) (CONFIG.namePopupFadeIn + CONFIG.namePopupDuration + CONFIG.namePopupFadeOut);
	}

	public static void draw(MatrixStack matrices, float tickDelta) {
		if (NAME != null && NAME_DISPLAY_TOTAL_TICKS > 0) {
			TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

			int alpha = 255;

			if (NAME_DISPLAY_TOTAL_TICKS >= (CONFIG.namePopupDuration + CONFIG.namePopupFadeOut) * 20) {
				float progress = (NAME_DISPLAY_TOTAL_TICKS - (CONFIG.namePopupDuration - CONFIG.namePopupFadeOut) * 20) / (CONFIG.namePopupFadeIn * 20);
				alpha = 255 - (int) (255 * progress);
			}

			if (NAME_DISPLAY_TOTAL_TICKS <= CONFIG.namePopupFadeOut * 20) {
				float progress = (NAME_DISPLAY_TOTAL_TICKS) / (CONFIG.namePopupFadeOut * 20);
				alpha = (int) (255 * progress);
			}

			matrices.push();
			matrices.translate(5, 5, 0);

			matrices.scale(2.F * CONFIG.namePopupScale, 2F * CONFIG.namePopupScale, 1);
			textRenderer.drawWithShadow(matrices, NAME, 0, 0, alpha << 24 | 0x00FFFFFF);

			matrices.pop();

		}

		--NAME_DISPLAY_TOTAL_TICKS;
	}

	@Environment(EnvType.CLIENT)
	private static void accept(PacketContext context, PacketByteBuf buf) {
		Text landmarkName = buf.readText();

		// It's possible using the task queue here is unnecessary, but I'm not gonna mess around and find out
		context.getTaskQueue().execute(() -> {
			if (NAME_DISPLAY_TOTAL_TICKS <= 0) {
				NAME_DISPLAY_TOTAL_TICKS = duration() * 20;
				NAME = landmarkName;
			}
		});
	}
}
