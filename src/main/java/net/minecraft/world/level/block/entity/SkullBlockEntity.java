package net.minecraft.world.level.block.entity;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Services;
import net.minecraft.util.StringUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.state.BlockState;

public class SkullBlockEntity extends BlockEntity {

    public static final String TAG_SKULL_OWNER = "SkullOwner";
    public static final String TAG_NOTE_BLOCK_SOUND = "note_block_sound";
    @Nullable
    private static Executor mainThreadExecutor;
    @Nullable
    private static LoadingCache<String, CompletableFuture<Optional<GameProfile>>> profileCache;
    private static final Executor CHECKED_MAIN_THREAD_EXECUTOR = (runnable) -> {
        Executor executor = SkullBlockEntity.mainThreadExecutor;

        if (executor != null) {
            executor.execute(runnable);
        }

    };
    @Nullable
    public GameProfile owner;
    @Nullable
    public ResourceLocation noteBlockSound;
    private int animationTickCount;
    private boolean isAnimating;

    public SkullBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityType.SKULL, pos, state);
    }

    public static void setup(final Services apiServices, Executor executor) {
        SkullBlockEntity.mainThreadExecutor = executor;
        final BooleanSupplier booleansupplier = () -> {
            return SkullBlockEntity.profileCache == null;
        };

        SkullBlockEntity.profileCache = CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(10L)).maximumSize(256L).build(new CacheLoader<String, CompletableFuture<Optional<GameProfile>>>() {
            public CompletableFuture<Optional<GameProfile>> load(String s) {
                return booleansupplier.getAsBoolean() ? CompletableFuture.completedFuture(Optional.empty()) : SkullBlockEntity.loadProfile(s, apiServices, booleansupplier);
            }
        });
    }

    public static void clear() {
        SkullBlockEntity.mainThreadExecutor = null;
        SkullBlockEntity.profileCache = null;
    }

    static CompletableFuture<Optional<GameProfile>> loadProfile(String name, Services apiServices, BooleanSupplier booleansupplier) {
        return apiServices.profileCache().getAsync(name).thenApplyAsync((optional) -> {
            if (optional.isPresent() && !booleansupplier.getAsBoolean()) {
                UUID uuid = ((GameProfile) optional.get()).getId();
                ProfileResult profileresult = apiServices.sessionService().fetchProfile(uuid, true);

                return profileresult != null ? Optional.ofNullable(profileresult.profile()) : optional;
            } else {
                return Optional.empty();
            }
        }, Util.backgroundExecutor());
    }

    @Override
    protected void saveAdditional(CompoundTag nbt) {
        super.saveAdditional(nbt);
        if (this.owner != null) {
            CompoundTag nbttagcompound1 = new CompoundTag();

            NbtUtils.writeGameProfile(nbttagcompound1, this.owner);
            nbt.put("SkullOwner", nbttagcompound1);
        }

        if (this.noteBlockSound != null) {
            nbt.putString("note_block_sound", this.noteBlockSound.toString());
        }

    }

    @Override
    public void load(CompoundTag nbt) {
        super.load(nbt);
        if (nbt.contains("SkullOwner", 10)) {
            this.setOwner(NbtUtils.readGameProfile(nbt.getCompound("SkullOwner")));
        } else if (nbt.contains("ExtraType", 8)) {
            String s = nbt.getString("ExtraType");

            if (!StringUtil.isNullOrEmpty(s)) {
                this.setOwner(new GameProfile(Util.NIL_UUID, s));
            }
        }

        if (nbt.contains("note_block_sound", 8)) {
            this.noteBlockSound = ResourceLocation.tryParse(nbt.getString("note_block_sound"));
        }

    }

    public static void animation(Level world, BlockPos pos, BlockState state, SkullBlockEntity blockEntity) {
        if (state.hasProperty(SkullBlock.POWERED) && (Boolean) state.getValue(SkullBlock.POWERED)) {
            blockEntity.isAnimating = true;
            ++blockEntity.animationTickCount;
        } else {
            blockEntity.isAnimating = false;
        }

    }

    public float getAnimation(float tickDelta) {
        return this.isAnimating ? (float) this.animationTickCount + tickDelta : (float) this.animationTickCount;
    }

    @Nullable
    public GameProfile getOwnerProfile() {
        return this.owner;
    }

    @Nullable
    public ResourceLocation getNoteBlockSound() {
        return this.noteBlockSound;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return this.saveWithoutMetadata();
    }

    public void setOwner(@Nullable GameProfile owner) {
        synchronized (this) {
            this.owner = owner;
        }

        this.updateOwnerProfile();
    }

    private void updateOwnerProfile() {
        if (this.owner != null && !Util.isBlank(this.owner.getName()) && !SkullBlockEntity.hasTextures(this.owner)) {
            SkullBlockEntity.fetchGameProfile(this.owner.getName()).thenAcceptAsync((optional) -> {
                this.owner = (GameProfile) optional.orElse(this.owner);
                this.setChanged();
            }, SkullBlockEntity.CHECKED_MAIN_THREAD_EXECUTOR);
        } else {
            this.setChanged();
        }
    }

    @Nullable
    public static GameProfile getOrResolveGameProfile(CompoundTag nbt) {
        if (nbt.contains("SkullOwner", 10)) {
            return NbtUtils.readGameProfile(nbt.getCompound("SkullOwner"));
        } else {
            if (nbt.contains("SkullOwner", 8)) {
                String s = nbt.getString("SkullOwner");

                if (!Util.isBlank(s)) {
                    nbt.remove("SkullOwner");
                    SkullBlockEntity.resolveGameProfile(nbt, s);
                }
            }

            return null;
        }
    }

    public static void resolveGameProfile(CompoundTag nbt) {
        String s = nbt.getString("SkullOwner");

        if (!Util.isBlank(s)) {
            SkullBlockEntity.resolveGameProfile(nbt, s);
            // CraftBukkit start
        } else {
            net.minecraft.nbt.ListTag textures = nbt.getCompound("SkullOwner").getCompound("Properties").getList("textures", 10); // Safe due to method contracts
            for (int i = 0; i < textures.size(); i++) {
                if (textures.get(i) instanceof CompoundTag && !((CompoundTag) textures.get(i)).contains("Signature", 8) && ((CompoundTag) textures.get(i)).getString("Value").trim().isEmpty()) {
                    nbt.remove("SkullOwner");
                    break;
                }
            }
            // CraftBukkit end
        }

    }

    private static void resolveGameProfile(CompoundTag nbt, String name) {
        SkullBlockEntity.fetchGameProfile(name).thenAccept((optional) -> {
            nbt.put("SkullOwner", NbtUtils.writeGameProfile(new CompoundTag(), (GameProfile) optional.orElse(new GameProfile(Util.NIL_UUID, name))));
        });
    }

    private static CompletableFuture<Optional<GameProfile>> fetchGameProfile(String name) {
        LoadingCache<String, CompletableFuture<Optional<GameProfile>>> loadingcache = SkullBlockEntity.profileCache;

        return loadingcache != null && Player.isValidUsername(name) ? (CompletableFuture) loadingcache.getUnchecked(name) : CompletableFuture.completedFuture(Optional.empty());
    }

    private static boolean hasTextures(GameProfile profile) {
        return profile.getProperties().containsKey("textures");
    }
}
