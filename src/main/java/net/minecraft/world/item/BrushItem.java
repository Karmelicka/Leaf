package net.minecraft.world.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class BrushItem extends Item {
    public static final int ANIMATION_DURATION = 10;
    private static final int USE_DURATION = 200;

    public BrushItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player != null && this.calculateHitResult(player).getType() == HitResult.Type.BLOCK) {
            player.startUsingItem(context.getHand());
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BRUSH;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 200;
    }

    @Override
    public void onUseTick(Level world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (remainingUseTicks >= 0 && user instanceof Player player) {
            HitResult hitResult = this.calculateHitResult(player);
            if (hitResult instanceof BlockHitResult blockHitResult) {
                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    int i = this.getUseDuration(stack) - remainingUseTicks + 1;
                    boolean bl = i % 10 == 5;
                    if (bl) {
                        BlockPos blockPos = blockHitResult.getBlockPos();
                        BlockState blockState = world.getBlockState(blockPos);
                        HumanoidArm humanoidArm = user.getUsedItemHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
                        if (blockState.shouldSpawnTerrainParticles() && blockState.getRenderShape() != RenderShape.INVISIBLE) {
                            this.spawnDustParticles(world, blockHitResult, blockState, user.getViewVector(0.0F), humanoidArm);
                        }

                        Block bl2 = blockState.getBlock();
                        SoundEvent soundEvent;
                        if (bl2 instanceof BrushableBlock) {
                            BrushableBlock brushableBlock = (BrushableBlock)bl2;
                            soundEvent = brushableBlock.getBrushSound();
                        } else {
                            soundEvent = SoundEvents.BRUSH_GENERIC;
                        }

                        world.playSound(player, blockPos, soundEvent, SoundSource.BLOCKS);
                        if (!world.isClientSide()) {
                            BlockEntity var18 = world.getBlockEntity(blockPos);
                            if (var18 instanceof BrushableBlockEntity) {
                                BrushableBlockEntity brushableBlockEntity = (BrushableBlockEntity)var18;
                                // Gale start - dev import deobfuscation fixes
                                boolean bl22 = brushableBlockEntity.brush(world.getGameTime(), player, blockHitResult.getDirection());
                                if (bl22) {
                                    // Gale end - dev import deobfuscation fixes
                                    EquipmentSlot equipmentSlot = stack.equals(player.getItemBySlot(EquipmentSlot.OFFHAND)) ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
                                    stack.hurtAndBreak(1, user, (userx) -> {
                                        userx.broadcastBreakEvent(equipmentSlot);
                                    });
                                }
                            }
                        }
                    }

                    return;
                }
            }

            user.releaseUsingItem();
        } else {
            user.releaseUsingItem();
        }
    }

    private HitResult calculateHitResult(Player user) {
        return ProjectileUtil.getHitResultOnViewVector(user, (entity) -> {
            return !entity.isSpectator() && entity.isPickable();
        }, (double)Player.getPickRange(user.isCreative()));
    }

    private void spawnDustParticles(Level world, BlockHitResult hitResult, BlockState state, Vec3 userRotation, HumanoidArm arm) {
        double d = 3.0D;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        int j = world.getRandom().nextInt(7, 12);
        BlockParticleOption blockParticleOption = new BlockParticleOption(ParticleTypes.BLOCK, state);
        Direction direction = hitResult.getDirection();
        BrushItem.DustParticlesDelta dustParticlesDelta = BrushItem.DustParticlesDelta.fromDirection(userRotation, direction);
        Vec3 vec3 = hitResult.getLocation();

        for(int k = 0; k < j; ++k) {
            world.addParticle(blockParticleOption, vec3.x - (double)(direction == Direction.WEST ? 1.0E-6F : 0.0F), vec3.y, vec3.z - (double)(direction == Direction.NORTH ? 1.0E-6F : 0.0F), dustParticlesDelta.xd() * (double)i * 3.0D * world.getRandom().nextDouble(), 0.0D, dustParticlesDelta.zd() * (double)i * 3.0D * world.getRandom().nextDouble());
        }

    }

    static record DustParticlesDelta(double xd, double yd, double zd) {
        private static final double ALONG_SIDE_DELTA = 1.0D;
        private static final double OUT_FROM_SIDE_DELTA = 0.1D;

        public static BrushItem.DustParticlesDelta fromDirection(Vec3 userRotation, Direction side) {
            double d = 0.0D;
            BrushItem.DustParticlesDelta var10000;
            switch (side) {
                case DOWN:
                case UP:
                    var10000 = new BrushItem.DustParticlesDelta(userRotation.z(), 0.0D, -userRotation.x());
                    break;
                case NORTH:
                    var10000 = new BrushItem.DustParticlesDelta(1.0D, 0.0D, -0.1D);
                    break;
                case SOUTH:
                    var10000 = new BrushItem.DustParticlesDelta(-1.0D, 0.0D, 0.1D);
                    break;
                case WEST:
                    var10000 = new BrushItem.DustParticlesDelta(-0.1D, 0.0D, -1.0D);
                    break;
                case EAST:
                    var10000 = new BrushItem.DustParticlesDelta(0.1D, 0.0D, 1.0D);
                    break;
                default:
                    throw new IncompatibleClassChangeError();
            }

            return var10000;
        }
    }
}
