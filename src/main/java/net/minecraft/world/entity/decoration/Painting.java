package net.minecraft.world.entity.decoration;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.PaintingVariantTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.VariantHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class Painting extends HangingEntity implements VariantHolder<Holder<PaintingVariant>> {
    private static final EntityDataAccessor<Holder<PaintingVariant>> DATA_PAINTING_VARIANT_ID = SynchedEntityData.defineId(Painting.class, EntityDataSerializers.PAINTING_VARIANT);
    private static final ResourceKey<PaintingVariant> DEFAULT_VARIANT = PaintingVariants.KEBAB;
    public static final String VARIANT_TAG = "variant";

    private static Holder<PaintingVariant> getDefaultVariant() {
        return BuiltInRegistries.PAINTING_VARIANT.getHolderOrThrow(DEFAULT_VARIANT);
    }

    public Painting(EntityType<? extends Painting> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_PAINTING_VARIANT_ID, getDefaultVariant());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data) {
        if (DATA_PAINTING_VARIANT_ID.equals(data)) {
            this.recalculateBoundingBox();
        }

    }

    @Override
    public void setVariant(Holder<PaintingVariant> variant) {
        this.entityData.set(DATA_PAINTING_VARIANT_ID, variant);
    }

    @Override
    public Holder<PaintingVariant> getVariant() {
        return this.entityData.get(DATA_PAINTING_VARIANT_ID);
    }

    public static Optional<Painting> create(Level world, BlockPos pos, Direction facing) {
        Painting painting = new Painting(world, pos);
        List<Holder<PaintingVariant>> list = new ArrayList<>();
        BuiltInRegistries.PAINTING_VARIANT.getTagOrEmpty(PaintingVariantTags.PLACEABLE).forEach(list::add);
        if (list.isEmpty()) {
            return Optional.empty();
        } else {
            painting.setDirection(facing);
            list.removeIf((variant) -> {
                painting.setVariant(variant);
                return !painting.survives();
            });
            if (list.isEmpty()) {
                return Optional.empty();
            } else {
                int i = list.stream().mapToInt(Painting::variantArea).max().orElse(0);
                list.removeIf((variant) -> {
                    return variantArea(variant) < i;
                });
                Optional<Holder<PaintingVariant>> optional = Util.getRandomSafe(list, painting.random);
                if (optional.isEmpty()) {
                    return Optional.empty();
                } else {
                    painting.setVariant(optional.get());
                    painting.setDirection(facing);
                    return Optional.of(painting);
                }
            }
        }
    }

    private static int variantArea(Holder<PaintingVariant> variant) {
        return variant.value().getWidth() * variant.value().getHeight();
    }

    private Painting(Level world, BlockPos pos) {
        super(EntityType.PAINTING, world, pos);
    }

    public Painting(Level world, BlockPos pos, Direction direction, Holder<PaintingVariant> variant) {
        this(world, pos);
        this.setVariant(variant);
        this.setDirection(direction);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag nbt) {
        storeVariant(nbt, this.getVariant());
        nbt.putByte("facing", (byte)this.direction.get2DDataValue());
        super.addAdditionalSaveData(nbt);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag nbt) {
        Holder<PaintingVariant> holder = loadVariant(nbt).orElseGet(Painting::getDefaultVariant);
        this.setVariant(holder);
        this.direction = Direction.from2DDataValue(nbt.getByte("facing"));
        super.readAdditionalSaveData(nbt);
        this.setDirection(this.direction);
    }

    public static void storeVariant(CompoundTag nbt, Holder<PaintingVariant> variant) {
        nbt.putString("variant", variant.unwrapKey().orElse(DEFAULT_VARIANT).location().toString());
    }

    public static Optional<Holder<PaintingVariant>> loadVariant(CompoundTag nbt) {
        return Optional.ofNullable(ResourceLocation.tryParse(nbt.getString("variant"))).map((id) -> {
            return ResourceKey.create(Registries.PAINTING_VARIANT, id);
        }).flatMap(BuiltInRegistries.PAINTING_VARIANT::getHolder);
    }

    @Override
    public int getWidth() {
        return this.getVariant().value().getWidth();
    }

    @Override
    public int getHeight() {
        return this.getVariant().value().getHeight();
    }

    @Override
    public void dropItem(@Nullable Entity entity) {
        if (this.level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            this.playSound(SoundEvents.PAINTING_BREAK, 1.0F, 1.0F);
            if (entity instanceof Player) {
                Player player = (Player)entity;
                if (player.getAbilities().instabuild) {
                    return;
                }
            }

            this.spawnAtLocation(Items.PAINTING);
        }
    }

    @Override
    public void playPlacementSound() {
        this.playSound(SoundEvents.PAINTING_PLACE, 1.0F, 1.0F);
    }

    @Override
    public void moveTo(double x, double y, double z, float yaw, float pitch) {
        this.setPos(x, y, z);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int interpolationSteps) {
        this.setPos(x, y, z);
    }

    @Override
    public Vec3 trackingPosition() {
        return Vec3.atLowerCornerOf(this.pos);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this, this.direction.get3DDataValue(), this.getPos());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        this.setDirection(Direction.from3DDataValue(packet.getData()));
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(Items.PAINTING);
    }
}
