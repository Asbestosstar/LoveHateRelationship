package com.asbestosstar.lovehaterelationship.entity;

import com.asbestosstar.lovehaterelationship.entity.goal.SeekShelterGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireAvoidGarlicGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireDefendPlayerGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireFollowPlayerGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireFriendlyBiteGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireHuntZombiesAnimalsGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class VampireEntity extends Monster {
    private static final EntityDataAccessor<Integer> DATA_RELATIONSHIP =
            SynchedEntityData.defineId(VampireEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_VARIANT =
            SynchedEntityData.defineId(VampireEntity.class, EntityDataSerializers.INT);

    private static final int MAX_RELATIONSHIP = 1000;
    private static final int MIN_RELATIONSHIP = -1000;
    private static final int NEUTRAL = 0;

    private boolean nightForced = false;

    // Healing cooldown
    private static final int HEAL_COOLDOWN_TICKS = 600;
    private int healCooldown = 0;

    // Air-charge logic (server-authoritative)
    private static final int CHARGE_COOLDOWN_TICKS = 20;   // 1.0s
    private static final int CHARGE_MIN_DIST_SQ   = 9;     // start charging if >3 blocks away
    private static final double CHARGE_REACH_GROW = 0.65;  // how much to inflate AABB for air hit
    private static final double CHARGE_BASE_ACCEL = 0.20;  // per tick acceleration during charge
    private static final double CHARGE_MAX_SPEED  = 1.60;  // hard cap while charging
    private static final double GLIDE_MAX_SPEED   = 0.85;  // normal glide speed cap
    private static final double GLIDE_ACCEL       = 0.10;  // per tick accel while gliding
    private static final float  CHARGE_BONUS_DMG  = 10.0f; // extra damage on ram
    private static final float  CHARGE_KNOCK_UP   = 0.35f;
    private static final double CHARGE_KNOCK_H    = 0.9;

    private int chargeCooldown = 0;
    private boolean charging = false;

    public VampireEntity(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
        this.xpReward = 10;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 1000.0)
            .add(Attributes.MOVEMENT_SPEED, 0.275)
            .add(Attributes.ATTACK_DAMAGE, 8.0)
            .add(Attributes.FOLLOW_RANGE, 32.0)
            .add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new LookAtPlayerGoal(this, Player.class, 8.0f));
        goalSelector.addGoal(2, new RandomLookAroundGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false));
        goalSelector.addGoal(4, new VampireFollowPlayerGoal(this, 1.0, 12.0f));
        goalSelector.addGoal(5, new VampireFriendlyBiteGoal(this));
        goalSelector.addGoal(6, new VampireAvoidGarlicGoal(this, 1.0));
        goalSelector.addGoal(7, new SeekShelterGoal(this, 1.4));

        targetSelector.addGoal(0, new VampireHuntZombiesAnimalsGoal(this));
        targetSelector.addGoal(1, new VampireDefendPlayerGoal(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::shouldAttackPlayer));
    }

    private boolean shouldAttackPlayer(LivingEntity target) {
        return getRelationship() < -300;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_RELATIONSHIP, NEUTRAL);
        builder.define(DATA_VARIANT, 0);
    }

    public int getRelationship() {
        return entityData.get(DATA_RELATIONSHIP);
    }

    public void setRelationship(int value) {
        entityData.set(DATA_RELATIONSHIP, Mth.clamp(value, MIN_RELATIONSHIP, MAX_RELATIONSHIP));
    }

    public void adjustRelationship(int delta) {
        setRelationship(getRelationship() + delta);
    }

    public int getVariant() {
        return Mth.clamp(entityData.get(DATA_VARIANT), 0, 3);
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnData) {
        setRelationship(NEUTRAL);

        // Randomize 0..1 (matches your textures)
        entityData.set(DATA_VARIANT, random.nextInt(2));

        // Equip Elytra (no drop). We only use elytra-style flight.
        this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
        this.setDropChance(EquipmentSlot.CHEST, 0.0f);

        return super.finalizeSpawn(level, difficulty, spawnType, spawnData);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Relationship", getRelationship());
        tag.putInt("Variant", getVariant());
        tag.putBoolean("Charging", charging);
        tag.putInt("ChargeCooldown", chargeCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("Relationship")) setRelationship(tag.getInt("Relationship"));
        if (tag.contains("Variant")) entityData.set(DATA_VARIANT, tag.getInt("Variant"));
        if (tag.contains("Charging")) charging = tag.getBoolean("Charging");
        if (tag.contains("ChargeCooldown")) chargeCooldown = tag.getInt("ChargeCooldown");
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            int rel = getRelationship();

            // Server-side flight & charge control (includes manual hit detection)
            flightAndChargeTick();

            // Keep path AI from fighting air control while gliding
            if (this.isFallFlying()) this.getNavigation().stop();

            // Relationship drift
            if (tickCount % 200 == 0) {
                if (rel > -300) adjustRelationship(-1);
                else if (rel < -300) adjustRelationship(1);
            }

            // Sun/water damage
            if (level() instanceof ServerLevel serverLevel) {
                boolean sun = serverLevel.isDay() && isOutside() && !isInWaterOrRain() && !isUnderWater();
                if (sun && !nightForced) hurt(serverLevel.damageSources().generic(), 10f);
            }
            if (isInWaterRainOrBubble()) hurt(damageSources().generic(), 0.833f);

            // Heal when very angry and low HP
            if (rel < -500) {
                if (healCooldown > 0) healCooldown--;
                else {
                    if (this.getHealth() / this.getMaxHealth() < 0.2f) {
                        this.heal(this.getMaxHealth() * 0.5f);
                        if (this.level() instanceof ServerLevel sl) {
                            sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.6, 0.6, 0.6, 0.15);
                            this.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.6f);
                        }
                        healCooldown = HEAL_COOLDOWN_TICKS;
                    }
                }
            }

            // Name tag
            if (tickCount % 20 == 0) updateRelationshipDisplayName();

            // Force night when furious
            if (rel <= -900 && level() instanceof ServerLevel sl) {
                if (!nightForced && sl.isDay()) {
                    sl.setDayTime(18000);
                    nightForced = true;
                    sl.playSound(null, this.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.0f, 0.8f);
                    for (int i = 0; i < 20; i++) {
                        sl.sendParticles(ParticleTypes.SMOKE,
                            this.getX() + (random.nextDouble() - 0.5) * 16,
                            this.getY() + 10 + random.nextDouble() * 8,
                            this.getZ() + (random.nextDouble() - 0.5) * 16,
                            1, 0, 0, 0, 0);
                    }
                }
            } else {
                nightForced = false;
            }
        }
    }

    /**
     * Only uses Elytra-style flight. Locks onto target, glides or charges,
     * and performs manual collision-based attack while airborne.
     */
    private void flightAndChargeTick() {
        int rel = getRelationship();
        LivingEntity target = this.getTarget();

        // Disable gliding if neutral/friendly or no target
        if (rel > -200 || target == null || !target.isAlive()) {
            setFallFlying(false);
            charging = false;
            this.zza = 0.0f;
            return;
        }

        // Must have elytra, not be grounded, and not in water/rain
        ItemStack chest = this.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasElytra = !chest.isEmpty() && chest.is(Items.ELYTRA);
        if (!hasElytra || this.onGround() || this.isInWaterOrRain()) {
            setFallFlying(false);
            charging = false;
            this.zza = 0.0f;
            return;
        }

        // Enable fall-flying physics
        if (!this.isFallFlying()) setFallFlying(true);

        // Look at target to steer body yaw
        this.getLookControl().setLookAt(target, 45.0f, 45.0f);

        // Maintain modest altitude above ground
        Level lvl = this.level();
        int groundY = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) this.getX(), (int) this.getZ());
        double altitude = this.getY() - groundY;

        Vec3 motion = this.getDeltaMovement();
        if (altitude < 2.0) motion = new Vec3(motion.x, Math.max(motion.y, 0.20), motion.z);
        else if (altitude > 4.0) motion = new Vec3(motion.x, Math.min(motion.y, -0.12), motion.z);

        // Vector to target
        Vec3 toTarget = new Vec3(
            target.getX() - this.getX(),
            target.getY(0.5) - this.getY(),
            target.getZ() - this.getZ()
        );
        double distSq = toTarget.lengthSqr();
        if (distSq < 1e-6) {
            // Already overlapping
            tryAirHit(target);
            this.setDeltaMovement(motion);
            return;
        }

        Vec3 dir = toTarget.normalize();

        // Charge cadence
        if (chargeCooldown > 0) chargeCooldown--;

        // Start charge if far enough and angry
        if (!charging && chargeCooldown == 0 && distSq >= CHARGE_MIN_DIST_SQ && rel <= -500) {
            charging = true;
            chargeCooldown = CHARGE_COOLDOWN_TICKS;
            if (this.level() instanceof ServerLevel sl)
                sl.playSound(null, this.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.7f, 1.2f + this.random.nextFloat() * 0.2f);
            this.setSprinting(true);
        }

        // Movement model: glide or charge
        if (charging) {
            // Strong forward accel, slight lift to hold line
            motion = motion.add(dir.scale(CHARGE_BASE_ACCEL)).add(0.0, 0.03, 0.0);
            // Cap speed
            double spd = motion.length();
            if (spd > CHARGE_MAX_SPEED) motion = motion.scale(CHARGE_MAX_SPEED / spd);
        } else {
            // Normal glide chase
            motion = motion.add(dir.scale(GLIDE_ACCEL));
            double spd = motion.length();
            if (spd > GLIDE_MAX_SPEED) motion = motion.scale(GLIDE_MAX_SPEED / spd);
        }

        // Try air-hit when close / overlapping AABBs
        if (tryAirHit(target)) {
            // After a successful ram, briefly break charge so it can set up again
            charging = false;
            this.setSprinting(false);
        }

        // Push motion to physics & drive animation
        this.setDeltaMovement(motion);
        this.zza = 1.0f;
    }

    /**
     * Attempts a mid-air hit using AABB overlap/inflation.
     * Returns true if an attack was landed.
     */
    private boolean tryAirHit(LivingEntity target) {
        // If we're not fall-flying, skip (ground melee handles itself).
        if (!this.isFallFlying()) return false;

        // Use enlarged AABB for forgiving high-speed contact
        AABB reach = this.getBoundingBox().inflate(CHARGE_REACH_GROW);
        boolean touching = reach.intersects(target.getBoundingBox());
        double linDist = this.distanceToSqr(target);

        if (!touching && linDist > 9.0) return false; // too far and not touching

        // Land the hit now, with charge bonus if we are charging
        float base = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float dmg = charging ? (base + CHARGE_BONUS_DMG) : base;

        boolean hurt = target.hurt(this.damageSources().mobAttack(this), dmg);
        if (hurt) {
            // Knockback on ram
            if (charging) {
                Vec3 v = new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ()).normalize();
                target.push(v.x * CHARGE_KNOCK_H, CHARGE_KNOCK_UP, v.z * CHARGE_KNOCK_H);
                if (this.level() instanceof ServerLevel sl) {
                    sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.8f, 0.9f + this.random.nextFloat() * 0.3f);
                    sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.2, 0.2, 0.1);
                }
            }

            // Apply on-hit effects by hostility level (same logic as ground)
            int rel = getRelationship();
            if (rel > 300) {
                target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            } else {
                if (rel < -500) target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                if (rel < -300) target.hurt(damageSources().mobAttack(this), 2.0f);
            }

            // Swing for animation
            this.swing(InteractionHand.MAIN_HAND);

            return true;
        }
        return false;
    }

    private void setFallFlying(boolean flying) {
        // Shared flag 7 controls elytra fall-flying on most mappings
        this.setSharedFlag(7, flying);
    }

    private void updateMovementSpeed() {
        int rel = getRelationship();
        double speed;

        if (rel < -700) {
            speed = 0.45;
        } else if (rel < -400) {
            speed = 0.35;
        } else if (rel > 300 && this.getTarget() != null) {
            speed = 0.38;
        } else if (rel > 200) {
            speed = 0.25;
        } else {
            speed = 0.18;
        }

        // Keep it snappier while airborne & chasing
        if (this.isFallFlying() && this.getTarget() != null) {
            speed = Math.max(speed, 0.40);
        }

        double current = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (Math.abs(current - speed) > 0.01) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
        }
    }

    public boolean isOutside() {
        return level().canSeeSky(blockPosition());
    }

    private void updateRelationshipDisplayName() {
        int rel = this.getRelationship();
        String status;
        if (rel > 500)       status = "§aAlly";
        else if (rel > 200)  status = "§2Friend";
        else if (rel >= -200)status = "§7Neutral";
        else if (rel > -500) status = "§cRival";
        else                 status = "§4Enemy";

        this.setCustomName(Component.literal(status + " (" + rel + ")"));
        this.setCustomNameVisible(true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Immune to potion-magic damage
        if (source.is(DamageTypes.MAGIC)) return false;

        // Wooden weapons hurt a lot
        if (source.getDirectEntity() instanceof Player player) {
            ItemStack weapon = player.getMainHandItem();
            if (weapon.is(Items.WOODEN_SWORD) ||
                weapon.is(Items.WOODEN_AXE) ||
                weapon.is(Items.WOODEN_PICKAXE) ||
                weapon.is(Items.WOODEN_SHOVEL) ||
                weapon.is(Items.WOODEN_HOE)) {
                amount = 66.6f;
            }
        }

        boolean result = super.hurt(source, amount);
        if (result && source.getEntity() instanceof Player) adjustRelationship(-50);
        return result;
    }

    @Override
    protected SoundEvent getAmbientSound() { return SoundEvents.VEX_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource pDamageSource) { return SoundEvents.VEX_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.VEX_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState blockState) {
        playSound(SoundEvents.GENERIC_SWIM, 0.15f, 1.0f);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (!level().isClientSide && level() instanceof ServerLevel sl) {
            for (int i = 0; i < 8; i++) {
                EntityType.BAT.spawn(sl, blockPosition(), MobSpawnType.TRIGGERED);
            }
        }
    }

    public boolean isInDanger() {
        if (this.level().isClientSide()) return false;
        if (!(this.level() instanceof ServerLevel serverLevel)) return false;
        boolean sun = serverLevel.isDay() && this.isOutside() && !this.isInWaterOrRain() && !this.isUnderWater();
        boolean wet = this.isInWaterRainOrBubble();
        return sun || wet;
    }

    @Override
    public boolean isCustomNameVisible() { return true; }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isAcceptableFood(stack)) {
            if (!this.level().isClientSide) {
                int boost = stack.is(Items.ROTTEN_FLESH) ? 30 : 100;
                adjustRelationship(boost);
                stack.shrink(1);
                this.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide);
        }
        return super.mobInteract(player, hand);
        }

    private boolean isAcceptableFood(ItemStack stack) {
        return stack.is(Items.PORKCHOP) ||
               stack.is(Items.BEEF) ||
               stack.is(Items.MUTTON) ||
               stack.is(Items.SPIDER_EYE) ||
               stack.is(Items.ROTTEN_FLESH);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        // Ground/vanilla melee route; air hits are handled in tryAirHit()
        if (!(target instanceof LivingEntity living)) return super.doHurtTarget(target);

        int rel = getRelationship();
        boolean result;
        if (rel > 300) {
            float base = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
            living.hurt(this.damageSources().mobAttack(this), base + 4.0f);
            living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            result = true;
        } else {
            result = super.doHurtTarget(target);
            if (rel < -800 && !level().isClientSide && level() instanceof ServerLevel sl) {
                for (int i = 0; i < 2; i++) EntityType.BAT.spawn(sl, blockPosition(), MobSpawnType.TRIGGERED);
            }
            if (rel < -500) living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
            if (rel < -300) living.hurt(damageSources().mobAttack(this), 2.0f);
        }
        return result;
    }
}
