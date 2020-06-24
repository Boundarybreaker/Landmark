package dev.hephaestus.landmark.impl.world;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.collect.ConcurrentHashMultiset;
import dev.hephaestus.landmark.impl.LandmarkClient;
import dev.hephaestus.landmark.impl.LandmarkMod;
import dev.hephaestus.landmark.impl.landmarks.GeneratedLandmark;
import dev.hephaestus.landmark.impl.landmarks.Landmark;
import dev.hephaestus.landmark.impl.landmarks.PlayerLandmark;
import dev.hephaestus.landmark.impl.world.chunk.LandmarkChunkComponent;
import nerdhub.cardinal.components.api.util.sync.WorldSyncedComponent;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.network.PacketContext;

public class LandmarkTrackingComponent implements WorldSyncedComponent {
	public static final Identifier LANDMARK_SYNC_ID = LandmarkMod.id("packet", "sync");
	public static final Identifier LANDMARK_DELETE_ID = LandmarkMod.id("packet", "delete");

	private final World world;

	private ConcurrentHashMap<UUID, Landmark> landmarks = new ConcurrentHashMap<>();
	private ConcurrentHashMap<ChunkPos, ConcurrentHashMultiset<Landmark>> searcher = new ConcurrentHashMap<>();
	private ConcurrentHashMap<BlockPos, Landmark> generatedLandmarks = new ConcurrentHashMap<>();

	public LandmarkTrackingComponent(World world) {
		this.world = world;
	}

	public static void add(ServerWorld world, Landmark landmark) {
		of(world).add(landmark);
	}

	public void add(Landmark landmark) {
		if (landmark instanceof GeneratedLandmark) {
			this.add(landmark, ((GeneratedLandmark) landmark).getCenter());
		} else {
			this.landmarks.put(landmark.getId(), landmark);
			this.sync();
		}
	}

	public void add(Landmark landmark, BlockPos center) {
		this.landmarks.put(landmark.getId(), landmark);
		this.generatedLandmarks.put(center, landmark);

		this.sync();
	}

	public static LandmarkTrackingComponent of(World world) {
		return LandmarkMod.TRACKING_COMPONENT.get(world);
	}

	public Landmark get(UUID uuid) {
		return this.landmarks.get(uuid);
	}

	public Landmark get(BlockPos pos) {
		return this.generatedLandmarks.get(pos);
	}

	@Override
	public void fromTag(CompoundTag tag) {
		this.landmarks = new ConcurrentHashMap<>();
		this.searcher = new ConcurrentHashMap<>();

		CompoundTag landmarksTag = tag.getCompound("landmarks");

		for (String key : landmarksTag.getKeys()) {
			Landmark landmark = Landmark.from(this.world, landmarksTag.getCompound(key));
			this.landmarks.put(UUID.fromString(key), landmark);

			for (ChunkPos pos : landmark.getChunks()) {
				this.searcher.computeIfAbsent(pos, (p) -> ConcurrentHashMultiset.create()).add(landmark);
			}
		}

		CompoundTag generatedTag = tag.getCompound("generated");

		for (String key : generatedTag.getKeys()) {
			this.generatedLandmarks.put(
					BlockPos.fromLong(Long.parseLong(key)),
					Landmark.from(this.world, generatedTag.getCompound(key))
			);
		}
	}

	@Override
	public CompoundTag toTag(CompoundTag tag) {
		CompoundTag landmarksTag = new CompoundTag();

		for (Landmark landmark : this.landmarks.values()) {
			landmarksTag.put(landmark.getId().toString(), landmark.toTag(new CompoundTag()));
		}

		tag.put("landmarks", landmarksTag);

		CompoundTag generatedTag = new CompoundTag();

		for (Map.Entry<BlockPos, Landmark> entry : this.generatedLandmarks.entrySet()) {
			generatedTag.put(String.valueOf(entry.getKey().asLong()), entry.getValue().toTag(new CompoundTag()));
		}

		tag.put("generated", generatedTag);

		return tag;
	}

	public Text getName(UUID landmark) {
		return this.landmarks.get(landmark).getName();
	}

	@Environment(EnvType.CLIENT)
	public static void read(PacketContext context, PacketByteBuf buf) {
		CompoundTag tag = buf.readCompoundTag();

		context.getTaskQueue().execute(() -> {
			if (tag != null) {
				LandmarkClient.TRACKER.fromTag(tag);
			}
		});
	}

	public Collection<Landmark> get(ChunkPos pos) {
		return this.searcher.get(pos);
	}

	public void put(ChunkPos pos, PlayerLandmark landmark) {
		this.searcher.computeIfAbsent(pos, (p) -> ConcurrentHashMultiset.create()).add(landmark);
	}

	public boolean contains(BlockPos pos) {
		return this.generatedLandmarks.containsKey(pos);
	}

	@Override
	public World getWorld() {
		return this.world;
	}

	public static void delete(PacketContext context, PacketByteBuf buf) {
		UUID id = buf.readUuid();
		Hand hand = buf.readEnumConstant(Hand.class);

		context.getTaskQueue().execute(() -> {
			LandmarkTrackingComponent tracker = of(context.getPlayer().getEntityWorld());
			Landmark landmark = tracker.get(id);

			if (landmark instanceof PlayerLandmark) {
				if (((PlayerLandmark) landmark).ownedBy(context.getPlayer())) {
					for (ChunkPos pos : landmark.getChunks()) {
						LandmarkChunkComponent component = LandmarkMod.CHUNK_COMPONENT.get(landmark.getWorld().getChunk(pos.x, pos.z));
						component.remove((PlayerLandmark) landmark);
					}

					tracker.landmarks.remove(id);
					context.getPlayer().setStackInHand(hand, new ItemStack(context.getPlayer().getStackInHand(hand).getItem()));
					tracker.sync();
				} else {
					context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.delete.fail", new TranslatableText("deeds.landmark.delete.fail.owner")), false);
				}
			} else {
				context.getPlayer().sendMessage(new TranslatableText("deeds.landmark.delete.fail"), false);
			}
		});
	}
}
