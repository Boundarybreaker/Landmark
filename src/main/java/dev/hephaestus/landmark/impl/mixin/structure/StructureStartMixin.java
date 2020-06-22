package dev.hephaestus.landmark.impl.mixin.structure;

import java.util.List;
import java.util.Random;

import com.google.common.collect.ImmutableList;
import dev.hephaestus.landmark.api.LandmarkType;
import dev.hephaestus.landmark.api.LandmarkTypeRegistry;
import dev.hephaestus.landmark.impl.LandmarkMod;
import dev.hephaestus.landmark.impl.landmarks.GeneratedLandmark;
import dev.hephaestus.landmark.impl.landmarks.LandmarkSection;
import dev.hephaestus.landmark.impl.names.NameGenerator;
import dev.hephaestus.landmark.impl.world.LandmarkTrackingComponent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructurePiece;
import net.minecraft.structure.StructureStart;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.StructureFeature;

@Mixin(StructureStart.class)
public abstract class StructureStartMixin {
	@Final
	@Shadow protected List<StructurePiece> children;

	@Shadow public abstract BlockPos getPos();

	@Shadow public abstract StructureFeature<?> getFeature();

	@Shadow public abstract boolean hasChildren();

	@Unique private static final int EXPAND = 5;

	@Inject(method = "generateStructure", at = @At(value = "INVOKE", target = "Lnet/minecraft/structure/StructureStart;setBoundingBoxFromChildren()V", shift = At.Shift.AFTER))
	private void makeLandmark(ServerWorldAccess serverWorldAccess, StructureAccessor structureAccessor, ChunkGenerator chunkGenerator, Random random, BlockBox blockBox, ChunkPos chunkPos, CallbackInfo ci) {
		ServerWorld world;

		if (serverWorldAccess instanceof ServerWorld) {
			world = (ServerWorld) serverWorldAccess;
		} else if (serverWorldAccess instanceof ChunkRegion) {
			world = ((ChunkRegion) serverWorldAccess).getWorld();
		} else {
			world = null;
		}

		ImmutableList<StructurePiece> list = ImmutableList.copyOf(this.children);
		LandmarkMod.EXECUTOR.execute(() -> {
			LandmarkType type = LandmarkTypeRegistry.get((StructureStart<?>) (Object) this, world);

			if (world != null && type != null && this.hasChildren()) {
				GeneratedLandmark landmark;
				LandmarkTrackingComponent tracker = LandmarkTrackingComponent.of(world);

				if (tracker.contains(this.getPos())) {
					landmark = (GeneratedLandmark) tracker.get(this.getPos());
				} else {
					landmark = new GeneratedLandmark(world, this.getPos(), LiteralText.EMPTY);
					landmark.setName(NameGenerator.generate(type.getNameGeneratorId()));
				}

				LandmarkTrackingComponent.add(world, landmark);

				for (StructurePiece structurePiece : list) {
					BlockBox box = structurePiece.getBoundingBox();
					LandmarkSection section = new LandmarkSection(
							landmark.getId(),
							box.minX - EXPAND,
							box.minY,
							box.minZ - EXPAND,
							box.maxX + EXPAND,
							box.maxY + EXPAND,
							box.maxZ + EXPAND
					);

					landmark.add(section);
				}

				world.getServer().submit(() -> {
					GeneratedLandmark.resolve(landmark);
					tracker.sync();
				});
			}
		});
	}
}
