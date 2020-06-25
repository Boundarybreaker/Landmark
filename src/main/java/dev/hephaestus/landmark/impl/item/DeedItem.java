package dev.hephaestus.landmark.impl.item;

import java.util.List;
import java.util.UUID;

import dev.hephaestus.landmark.impl.landmarks.Landmark;
import dev.hephaestus.landmark.impl.landmarks.LandmarkSection;
import dev.hephaestus.landmark.impl.landmarks.PlayerLandmark;
import dev.hephaestus.landmark.impl.network.LandmarkNetworking;
import dev.hephaestus.landmark.impl.world.LandmarkTrackingComponent;
import io.netty.buffer.Unpooled;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;

public class DeedItem extends Item {
	private final double maxVolume;

	public DeedItem(Settings settings, double maxVolume) {
		super(settings);
		this.maxVolume = maxVolume;
	}

	@Override
	public boolean hasGlint(ItemStack stack) {
		return stack.getRarity() == Rarity.EPIC;
	}

	@Override
	public ActionResult useOnBlock(ItemUsageContext context) {
		if (!context.getWorld().isClient && context.getPlayer() != null) {
			ItemStack stack = context.getPlayer().getStackInHand(context.getHand());

			if (stack.hasTag()) {
				CompoundTag tag = stack.getOrCreateTag();
				Data data = new Data(context.getWorld(), context.getPlayer(), tag);

				if (data.isGenerated) {
					return ActionResult.PASS;
				}

				ServerWorld world = (ServerWorld) context.getWorld();

				if (data.marker != null) {
					if (!data.world.equals(world.getRegistryKey())) {
						context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.fail.world"), true);
						return ActionResult.FAIL;
					}

					tag.remove("marker");
					BlockPos marker = context.getBlockPos();

					BlockBox newBox = new BlockBox(data.marker, marker);

					LandmarkTrackingComponent trackingComponent = LandmarkTrackingComponent.of(world);
					PlayerLandmark landmark = (PlayerLandmark) trackingComponent.get(data.landmarkId);

					if (landmark.canModify(context.getPlayer())) {
						int result = landmark.add(new LandmarkSection(landmark.getId(), newBox), this.maxVolume, true);

						if (result == 0) {
							double volume = landmark.volume();
							landmark.makeSections();
							tag.putDouble("volume", volume);
							context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.success.add_box", volume), true);
							return ActionResult.SUCCESS;
						} else if (result == 1) {
							context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.fail.overlap", this.maxVolume), true);
							return ActionResult.FAIL;
						} else if (result == 2) {
							context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.fail.volume", this.maxVolume), true);
							return ActionResult.FAIL;
						} else {
							context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.fail.other", this.maxVolume), true);
							return ActionResult.FAIL;
						}
					} else {
						context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.fail.permissions"), true);
						return ActionResult.FAIL;
					}
				} else {
					tag.putLong("marker", context.getBlockPos().asLong());
				}
			} else {
				PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
				buf.writeEnumConstant(context.getHand());
				ServerSidePacketRegistry.INSTANCE.sendToPlayer(context.getPlayer(), LandmarkNetworking.OPEN_CLAIM_SCREEN, buf);
			}
		}

		return ActionResult.SUCCESS;
	}

	@Override
	@Environment(EnvType.CLIENT)
	public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
		CompoundTag tag = stack.getTag();
		int volume = 0;

		if (tag != null && tag.contains("volume", 6)) {
			volume = (int) tag.getDouble("volume");
		}

		if (tag != null && (!tag.contains("is_generated") || !tag.getBoolean("is_generated"))) {
			if (this.maxVolume < Double.MAX_VALUE) {
				tooltip.add(new TranslatableText("item.landmark.deed.volume", volume, (int) this.maxVolume).styled((style) -> style.withItalic(true).withColor(Formatting.DARK_GRAY)));
			} else {
				tooltip.add(new TranslatableText("item.landmark.deed.volume.creative", volume).styled((style) -> style.withItalic(true).withColor(Formatting.DARK_GRAY)));
			}
		}

		if (tag != null && tag.contains("landmark_name")) {
			tooltip.add(Text.Serializer.fromJson(tag.getString("landmark_name")));
		}

		super.appendTooltip(stack, world, tooltip, context);
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		if (!world.isClient) {
			if (user.getStackInHand(hand).hasTag()) {
				CompoundTag tag = user.getStackInHand(hand).getOrCreateTag();
				Data data = new Data(world, user, tag);

				PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
				buf.writeEnumConstant(hand);
				buf.writeUuid(data.landmarkId);
				ServerSidePacketRegistry.INSTANCE.sendToPlayer(user, LandmarkNetworking.OPEN_EDIT_SCREEN, buf);
			} else {
				PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
				buf.writeEnumConstant(hand);
				ServerSidePacketRegistry.INSTANCE.sendToPlayer(user, LandmarkNetworking.OPEN_CLAIM_SCREEN, buf);
			}
		}

		return super.use(world, user, hand);
	}

	public static class Data {
		public final UUID landmarkId;
		public final RegistryKey<World> world;
		public final MutableText landmarkName;
		public final boolean isGenerated;
		public final double volume;
		public final BlockPos marker;

		public Data(World world, PlayerEntity player, CompoundTag tag) {
			if (tag.contains("landmark_id")) {
				this.landmarkId = tag.getUuid("landmark_id");
				RegistryKey<World> tagWorld = RegistryKey.of(Registry.DIMENSION, new Identifier(tag.getString("world_key")));

				if (world.getServer() != null && LandmarkTrackingComponent.of(world.getServer().getWorld(tagWorld)).get(this.landmarkId) != null) {
					this.world = tagWorld;
					this.landmarkName = world.getServer() != null ? LandmarkTrackingComponent.of(world.getServer().getWorld(this.world)).get(this.landmarkId).getName() : null;
					this.isGenerated = tag.contains("is_generated") && tag.getBoolean("is_generated");
					this.volume = tag.contains("volume") ? tag.getDouble("volume") : 0;
					this.marker = tag.contains("marker") ? BlockPos.fromLong(tag.getLong("marker")) : null;
				} else {
					Landmark landmark = new PlayerLandmark(world, this.landmarkId);

					if (player != null) {
						landmark = landmark.withOwner(player);
					}

					LandmarkTrackingComponent.add((ServerWorld) world, landmark);
					tag.putUuid("landmark_id", this.landmarkId);

					tag.putString("world_key", world.getRegistryKey().getValue().toString());
					this.world = world.getRegistryKey();

					this.landmarkName = landmark.getName();
					tag.putString("landmark_name", Text.Serializer.toJson(this.landmarkName));

					tag.putBoolean("is_generated", false);
					this.isGenerated = false;

					tag.putDouble("volume", 0);
					this.volume = 0;

					this.marker = null;
				}
			} else {
				Landmark landmark = new PlayerLandmark(world);

				if (player != null) {
					landmark = landmark.withOwner(player);
				}

				LandmarkTrackingComponent.add((ServerWorld) world, landmark);
				tag.putUuid("landmark_id", landmark.getId());
				this.landmarkId = landmark.getId();

				tag.putString("world_key", world.getRegistryKey().getValue().toString());
				this.world = world.getRegistryKey();

				this.landmarkName = landmark.getName();
				tag.putString("landmark_name", Text.Serializer.toJson(this.landmarkName));

				tag.putBoolean("is_generated", false);
				this.isGenerated = false;

				tag.putDouble("volume", 0);
				this.volume = 0;

				this.marker = null;
			}
		}

		public Data(World world, CompoundTag tag) {
			this(world, null, tag);
		}
	}
}
