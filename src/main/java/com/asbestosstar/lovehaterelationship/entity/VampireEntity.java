package com.asbestosstar.lovehaterelationship.entity;

import com.asbestosstar.lovehaterelationship.LoveHateRelationShip;
import com.asbestosstar.lovehaterelationship.entity.goal.SeekShelterGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireAvoidGarlicGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireDefendPlayerGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireFollowPlayerGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireFriendlyBiteGoal;
import com.asbestosstar.lovehaterelationship.entity.goal.VampireHuntZombiesAnimalsGoal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.IntTag;
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

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class VampireEntity extends Monster {
	private static final EntityDataAccessor<Integer> DATA_VARIANT = SynchedEntityData.defineId(VampireEntity.class,
			EntityDataSerializers.INT);

	private static final int MAX_RELATIONSHIP = 1000;
	private static final int MIN_RELATIONSHIP = -1000;
	private static final int NEUTRAL = 0;
	private static final int HOSTILE_THRESHOLD = -300; // Threshold for hostility

	// Map to store relationships with other entities (non-player), keyed by their
	// UUID
	// This map is not persisted in NBT
	private final Map<UUID, Integer> entityRelationships = new HashMap<>();
	// Map to store relationships with players, keyed by their UUID
	// This map *will* be persisted in NBT
	private final Map<UUID, Integer> playerRelationships = new HashMap<>();

	// Map to store the last known positions of targets, keyed by their UUID
	// This map is not persisted in NBT
	private final Map<UUID, BlockPos> targetPositions = new HashMap<>();

	private boolean nightForced = false;

	// Healing cooldown
	private static final int HEAL_COOLDOWN_TICKS = 600;
	private int healCooldown = 0;

	// Air-charge logic (server-authoritative)
	private static final int CHARGE_COOLDOWN_TICKS = 20; // 1.0s
	private static final int CHARGE_MIN_DIST_SQ = 9; // start charging if >3 blocks away
	private static final double CHARGE_REACH_GROW = 0.65; // how much to inflate AABB for air hit
	private static final double CHARGE_BASE_ACCEL = 0.20; // per tick acceleration during charge
	private static final double CHARGE_MAX_SPEED = 1.60; // hard cap while charging
	private static final double GLIDE_MAX_SPEED = 0.85; // normal glide speed cap
	private static final double GLIDE_ACCEL = 0.10; // per tick accel while gliding
	private static final float CHARGE_BONUS_DMG = 10.0f; // extra damage on ram
	private static final float CHARGE_KNOCK_UP = 0.35f;
	private static final double CHARGE_KNOCK_H = 0.9;

	private int chargeCooldown = 0;
	private boolean charging = false;

	// Relationship decay/increase
	private static final int RELATIONSHIP_CHANGE_INTERVAL_TICKS = 200; // e.g., 10 seconds (200 ticks)
	private static final int RELATIONSHIP_DECAY_AMOUNT = -1; // How much to decrease per interval (negative number)
	private static final int RELATIONSHIP_DRIFT_AMOUNT = 1; // How much to increase per interval (positive number)
	private int lastRelationshipChangeTick = 0; // Track the last tick when change occurred

	// Reflection cache for Sadako mod entities
	private static boolean sadakoModChecked = false;
	private static Class<?> sadakoModEntitiesClass = null;
	private static Field sadakoStalkField = null;
	private static Random random = new Random();

	// Reflection cache for Vampiric Llamas mod items
	private static boolean vampiricLlamasModChecked = false;
	private static Class<?> vampiricLlamasItemsClass = null;
	private static Field vampireLeatherField = null;

	public VampireEntity(EntityType<? extends Monster> entityType, Level level) {
		super(entityType, level);
		this.xpReward = 10;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH, 1000.0)
				.add(Attributes.MOVEMENT_SPEED, 0.275).add(Attributes.ATTACK_DAMAGE, 4.5)
				.add(Attributes.FOLLOW_RANGE, 32.0).add(Attributes.KNOCKBACK_RESISTANCE, 0.6);
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
		targetSelector.addGoal(3,
				new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, true, false, this::shouldAttackEntity));
	}

	private boolean shouldAttackEntity(LivingEntity target) {
		if (!(target instanceof Player)) {
			return getRelationshipWith(target) < HOSTILE_THRESHOLD;
		}
		return getRelationshipWith(target) < HOSTILE_THRESHOLD;
	}

	public int getRelationshipWith(Entity entity) {
		if (entity == null || entity == this)
			return NEUTRAL; // Prevent self-referencing
		UUID uuid = entity.getUUID();
		if (entity instanceof Player) {
			return playerRelationships.getOrDefault(uuid, NEUTRAL);
		} else {
			return entityRelationships.getOrDefault(uuid, NEUTRAL);
		}
	}

	public void setRelationshipWith(Entity entity, int value) {
		if (entity == null || entity == this)
			return; // Prevent self-referencing
		UUID uuid = entity.getUUID();
		if (entity instanceof Player) {
			playerRelationships.put(uuid, Mth.clamp(value, MIN_RELATIONSHIP, MAX_RELATIONSHIP));
		} else {
			entityRelationships.put(uuid, Mth.clamp(value, MIN_RELATIONSHIP, MAX_RELATIONSHIP));
		}
	}

	public void adjustRelationshipWith(Entity entity, int delta) {
		if (entity == null || entity == this)
			return; // Prevent self-referencing
		int currentRel = getRelationshipWith(entity);
		setRelationshipWith(entity, currentRel + delta);
		// *** UPDATE TARGET POSITION MAP ON RELATIONSHIP CHANGE ***
		// If the relationship becomes hostile, remember the target's position
		if (delta < 0 && getRelationshipWith(entity) <= HOSTILE_THRESHOLD) { // Hurt -> more hostile
			if (entity instanceof LivingEntity livingTarget) {
				targetPositions.put(entity.getUUID(), livingTarget.blockPosition());
			}
		}
		// If the relationship becomes friendly, forget the target's position
		if (delta > 0 && currentRel <= HOSTILE_THRESHOLD && getRelationshipWith(entity) > HOSTILE_THRESHOLD) { // Less
																												// hostile
																												// ->
																												// friendly
			targetPositions.remove(entity.getUUID());
		}
		// *** END UPDATE ***
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		// Immune to potion-magic damage
		if (source.is(DamageTypes.MAGIC))
			return false;

		// Wooden weapons hurt a lot
		Entity sourceEntity = source.getEntity();
		if (sourceEntity instanceof Player player) {
			ItemStack weapon = player.getMainHandItem();
			if (weapon.is(Items.WOODEN_SWORD) || weapon.is(Items.WOODEN_AXE)) {
				amount = 66.6f;
			}
		}

		boolean result = super.hurt(source, amount);
		if (result && sourceEntity instanceof LivingEntity attacker && attacker != this) { // Prevent self-hurting
																							// relationship change
			// Adjust relationship with the entity that hurt this vampire
			adjustRelationshipWith(attacker, -50);
			// *** UPDATE TARGET POSITION MAP ON HURT ***
			// Remember the attacker's position if relationship becomes hostile
			if (getRelationshipWith(attacker) <= HOSTILE_THRESHOLD) {
				targetPositions.put(attacker.getUUID(), attacker.blockPosition());
			}
			// *** END UPDATE ***
		}
		return result;
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		if (!(target instanceof LivingEntity living)) {
			return super.doHurtTarget(target);
		}

		int relToTarget = getRelationshipWith(living);
		float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);

		// Calculate bonus damage based on target's max health
		float targetMaxHealth = living.getMaxHealth();
		float bonusDamagePer100HP = 3.0f;
		float bonusDamage = (targetMaxHealth / 100.0f) * bonusDamagePer100HP;

		float totalDamage = baseDamage + bonusDamage;

		boolean result;
		if (relToTarget > 300) {
			float friendlyTotalDamage = baseDamage + 4.0f + bonusDamage;
			result = living.hurt(this.damageSources().mobAttack(this), friendlyTotalDamage);
			living.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
		} else {
			// Standard damage calculation with bonus
			result = living.hurt(this.damageSources().mobAttack(this), totalDamage);
			if (relToTarget < -800 && !level().isClientSide && level() instanceof ServerLevel sl) {
				for (int i = 0; i < 2; i++) {
					EntityType.BAT.spawn(sl, blockPosition(), MobSpawnType.TRIGGERED);
				}
			}
			if (relToTarget < -500) {
				living.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
				living.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 1));
				living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 1));

				// Apply bonus damage to the extra flat damage
				living.hurt(this.damageSources().mobAttack(this), 2.0f + bonusDamage);
			}
			if (relToTarget <= -500 && !level().isClientSide && level() instanceof ServerLevel serverLevel) {
				// Summon lightning if relationship is -500 or lower
				// Offset the lightning strike position slightly to avoid harming the vampire
				// directly
				double lightningX = target.getX() + (serverLevel.getRandom().nextDouble() - 0.5) * 2.0;
				double lightningY = target.getY();
				double lightningZ = target.getZ() + (serverLevel.getRandom().nextDouble() - 0.5) * 2.0;
				BlockPos lightningPos = new BlockPos((int) lightningX, (int) lightningY, (int) lightningZ);
				net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
				if (lightning != null) {
					lightning.moveTo(Vec3.atBottomCenterOf(lightningPos));
					// lightning.setCause(this); // or null if not caused by an entity
					serverLevel.addFreshEntity(lightning);
				}
			}
		}

		// *** UPDATE TARGET POSITION MAP ON HURT ***
		// Remember the target's position if relationship is hostile
		if (relToTarget <= HOSTILE_THRESHOLD) {
			targetPositions.put(living.getUUID(), living.blockPosition());
		}
		// *** END UPDATE ***

		return result;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_VARIANT, 0);
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
			MobSpawnType spawnType, SpawnGroupData spawnData) {

		entityData.set(DATA_VARIANT, random.nextInt(2));

		this.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
		this.setDropChance(EquipmentSlot.CHEST, 75.0f);

		return super.finalizeSpawn(level, difficulty, spawnType, spawnData);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		// Save player relationships
		ListTag playerRelList = new ListTag();
		for (Map.Entry<UUID, Integer> entry : playerRelationships.entrySet()) {
			CompoundTag entryTag = new CompoundTag();
			entryTag.putString("PlayerUUID", entry.getKey().toString());
			entryTag.putInt("Relationship", entry.getValue());
			playerRelList.add(entryTag);
		}
		tag.put("PlayerRelationships", playerRelList);

		tag.putInt("Variant", getVariant());
		tag.putBoolean("Charging", charging);
		tag.putInt("ChargeCooldown", chargeCooldown);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		// Load player relationships
		if (tag.contains("PlayerRelationships", Tag.TAG_LIST)) {
			ListTag playerRelList = tag.getList("PlayerRelationships", Tag.TAG_COMPOUND);
			for (int i = 0; i < playerRelList.size(); i++) {
				CompoundTag entryTag = playerRelList.getCompound(i);
				String uuidString = entryTag.getString("PlayerUUID");
				int relationship = entryTag.getInt("Relationship");
				try {
					UUID uuid = UUID.fromString(uuidString);
					playerRelationships.put(uuid, Mth.clamp(relationship, MIN_RELATIONSHIP, MAX_RELATIONSHIP));
				} catch (IllegalArgumentException e) {

				}
			}
		}
		// entityRelationships map is not loaded from NBT and remains empty
		// targetPositions map is not loaded from NBT and remains empty

		if (tag.contains("Variant"))
			entityData.set(DATA_VARIANT, tag.getInt("Variant"));
		if (tag.contains("Charging"))
			charging = tag.getBoolean("Charging");
		if (tag.contains("ChargeCooldown"))
			chargeCooldown = tag.getInt("ChargeCooldown");
	}

	@Override
	public void tick() {
		super.tick();

		if (!this.level().isClientSide) {
			LivingEntity currentTarget = this.getTarget();
			int relToTarget = (currentTarget != null) ? getRelationshipWith(currentTarget) : NEUTRAL;

			flightAndChargeTick(relToTarget);

			if (this.isFallFlying())
				this.getNavigation().stop();

			// Sun/water damage
			if (level() instanceof ServerLevel serverLevel) {
				boolean sun = serverLevel.isDay() && isOutside() && !isInWaterOrRain() && !isUnderWater();
				if (sun && !nightForced)
					hurt(serverLevel.damageSources().generic(), 10f);
			}
			if (isInWaterRainOrBubble())
				hurt(damageSources().generic(), 0.833f);

			// Heal when very angry with *anyone* and low HP - Uses relationship with
			// entity's target
			if (relToTarget < -500) {
				if (healCooldown > 0)
					healCooldown--;
				else {
					if (this.getHealth() / this.getMaxHealth() < 0.2f) {
						this.heal(this.getMaxHealth() * 0.5f);
						if (this.level() instanceof ServerLevel sl) {
							sl.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + 1.0, this.getZ(), 30, 0.6,
									0.6, 0.6, 0.15);
							this.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.6f);
						}
						healCooldown = HEAL_COOLDOWN_TICKS;
					}
				}
			}

			// Stalking logic: If no current target and hostile relationships exist, move
			// towards a remembered position
			if (currentTarget == null) {
				attemptStalking();
			}

			if (tickCount - lastRelationshipChangeTick >= RELATIONSHIP_CHANGE_INTERVAL_TICKS) {
				decayPlayerRelationships(); // Decay relationships > -300
				driftPlayerRelationships(currentTarget); // Increase relationships < -300 if not fighting them
				lastRelationshipChangeTick = tickCount;
			}

			if (tickCount % 20 == 0) {
				Player nearestPlayer = level().getNearestPlayer(this, 32.0);
				int relToNearestPlayer = (nearestPlayer != null) ? getRelationshipWith(nearestPlayer) : NEUTRAL;
				updateRelationshipDisplayName(nearestPlayer, relToNearestPlayer);
			}

			// Force night when furious
			if (relToTarget <= -900 && level() instanceof ServerLevel sl) {
				if (!nightForced && sl.isDay()) {
					sl.setDayTime(18000);
					nightForced = true;
					// *** PLAY CREEPY LAUGH WHEN FORCING NIGHT ***
					sl.playSound(null, this.blockPosition(), net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
							.wrapAsHolder(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
									.get(net.minecraft.resources.ResourceLocation
											.fromNamespaceAndPath("lovehaterelationship", "vampire_creepy_laugh")))
							.value(), SoundSource.HOSTILE, 1.0f, 1.0f);
					// *** END SOUND ***
					for (int i = 0; i < 20; i++) {
						sl.sendParticles(ParticleTypes.SMOKE, this.getX() + (random.nextDouble() - 0.5) * 16,
								this.getY() + 10 + random.nextDouble() * 8,
								this.getZ() + (random.nextDouble() - 0.5) * 16, 1, 0, 0, 0, 0);
					}
				}
			} else {
				nightForced = false;
			}
		}
	}

	/**
	 * Attempts to stalk a hostile target by moving towards a remembered position.
	 */
	private void attemptStalking() {
		// Find a hostile target whose position is remembered
		for (Map.Entry<UUID, BlockPos> entry : targetPositions.entrySet()) {
			UUID targetUUID = entry.getKey();
			BlockPos lastKnownPos = entry.getValue();

			// Try to find the entity by UUID (works for players, might work for other
			// living entities if they are loaded)
			// For players, use getPlayerByUUID which is more reliable for offline/online
			// status
			LivingEntity targetEntity = null;
			Player player = level().getPlayerByUUID(targetUUID);
			if (player != null) {
				targetEntity = player;
			} else {
				// For non-players (or if getPlayerByUUID failed), try getEntity(int) using the
				// entity's ID
				// This is less reliable as the entity might not be loaded.
				// First, we need to find an entity by UUID if it's loaded.
				// A common approach is to iterate through loaded entities, which can be
				// inefficient.
				// Let's try the player method first, and for non-players, we'll rely on the
				// position being set
				// when the entity was last seen/hurt and hope it's still relevant or the entity
				// reappears nearby.

				// Option 1: Iterate loaded entities (can be slow if many entities are loaded)
				// for (Entity e : level().getAllEntities()) {
				// if (e.getUUID().equals(targetUUID) && e instanceof LivingEntity le) {
				// targetEntity = le;
				// break;
				// }
				// }

				// Option 2: Use getEntity(int) if we stored the entity's ID along with UUID and
				// position
				// This requires changing the targetPositions map structure, which is more
				// complex.
				// For now, let's assume if it's not a player, the entity might not be loaded,
				// and we'll proceed with the position-based logic.

				// Since getPlayerByUUID failed and getEntity(int) requires the entity ID,
				// we'll proceed assuming the entity might not be loaded. The position is still
				// valid.
				// We'll check the relationship map to see if the UUID corresponds to a hostile
				// player or entity.
				// If it's a player, we can check if they are online.
				// If it's a non-player, we might need a different strategy or timeout for
				// positions.
			}

			// Check if the relationship is still hostile (for both players and other
			// entities)
			// If targetEntity is found, check its current relationship
			int currentRel = NEUTRAL; // Default if entity not found
			if (targetEntity != null) {
				currentRel = getRelationshipWith(targetEntity);
			} else {
				// If entity is not loaded, check if the UUID belongs to a player or an entity
				// in our relationship maps
				// and assume the relationship hasn't changed drastically if it was very
				// hostile.
				if (playerRelationships.containsKey(targetUUID)) {
					currentRel = playerRelationships.get(targetUUID);
				} else if (entityRelationships.containsKey(targetUUID)) {
					currentRel = entityRelationships.get(targetUUID);
				}
			}

			if (currentRel <= HOSTILE_THRESHOLD) {
				// If the entity is loaded and close, set it as target
				if (targetEntity != null && this.distanceToSqr(targetEntity) < 32.0 * 32.0) { // Adjust range as needed
					this.setTarget(targetEntity);
					return; // Found a target nearby, stop stalking for this tick
				}
				// If the entity is not loaded or far away, try to navigate to the last known
				// position
				// Only navigate if not already navigating elsewhere and the position is
				// reasonable
				if (!this.getNavigation().isInProgress() && Math.abs(this.getY() - lastKnownPos.getY()) < 10) { // Prevent
																												// chasing
																												// to
																												// different
																												// Y
																												// levels
					this.getNavigation().moveTo(lastKnownPos.getX() + 0.5, lastKnownPos.getY(),
							lastKnownPos.getZ() + 0.5, 1.0); // Adjust speed if needed
					return; // Started navigating, stop trying other positions for this tick
				}
			} else {
				// If relationship is no longer hostile, remove the position
				targetPositions.remove(targetUUID);
			}
			// If entity was not found (e.g., non-player entity that is now gone), check if
			// it was a player
			// and remove if necessary, or potentially add a timeout mechanism for
			// non-player positions.
			// For now, if relationship is not checked due to entity not being loaded, the
			// position might linger.
			// It will be overwritten if the entity is interacted with again or removed if
			// the relationship changes
			// when the entity is eventually loaded and interacted with.
		}
	}

	/**
	 * Decays the relationship with players stored in playerRelationships if rel >
	 * -300.
	 */
	private void decayPlayerRelationships() {
		for (Map.Entry<UUID, Integer> entry : playerRelationships.entrySet()) {
			UUID playerUUID = entry.getKey();
			int currentRel = entry.getValue();
			// Only decay if the relationship is greater than the hostile threshold
			if (currentRel > HOSTILE_THRESHOLD) {
				int newRel = Mth.clamp(currentRel + RELATIONSHIP_DECAY_AMOUNT, MIN_RELATIONSHIP, MAX_RELATIONSHIP);
				playerRelationships.put(playerUUID, newRel);
			}
		}
	}

	/**
	 * Increases the relationship with players stored in playerRelationships if rel
	 * < -300 and the vampire is not currently targeting them.
	 * 
	 * @param currentTarget The entity this vampire is currently targeting.
	 */
	private void driftPlayerRelationships(LivingEntity currentTarget) {
		for (Map.Entry<UUID, Integer> entry : playerRelationships.entrySet()) {
			UUID playerUUID = entry.getKey();
			int currentRel = entry.getValue();
			// Only drift if the relationship is less than the hostile threshold
			// and the vampire is not currently targeting this player
			if (currentRel < HOSTILE_THRESHOLD) {
				// Get the Player entity to check if it's the current target
				Player player = level().getPlayerByUUID(playerUUID);
				if (player != null && player != currentTarget) {
					int newRel = Mth.clamp(currentRel + RELATIONSHIP_DRIFT_AMOUNT, MIN_RELATIONSHIP, MAX_RELATIONSHIP);
					playerRelationships.put(playerUUID, newRel);
				}
			}
		}
	}

	/**
	 * Only uses Elytra-style flight. Locks onto target, glides or charges, and
	 * performs manual collision-based attack while airborne.
	 * 
	 * @param relToTarget The relationship with the currently targeted entity.
	 */
	private void flightAndChargeTick(int relToTarget) {
		LivingEntity target = this.getTarget();

		// Disable gliding if neutral/friendly or no target
		if (relToTarget > -200 || target == null || !target.isAlive()) {
			setFallFlying(false);
			charging = false;
			this.zza = 0.0f;
			return;
		}

		ItemStack chest = this.getItemBySlot(EquipmentSlot.CHEST);
		boolean hasElytra = !chest.isEmpty() && chest.is(Items.ELYTRA);
		if (!hasElytra || this.onGround() || this.isInWaterOrRain()) {
			setFallFlying(false);
			charging = false;
			this.zza = 0.0f;
			return;
		}

		if (!this.isFallFlying())
			setFallFlying(true);

		this.getLookControl().setLookAt(target, 45.0f, 45.0f);

		Level lvl = this.level();
		int groundY = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) this.getX(), (int) this.getZ());
		double altitude = this.getY() - groundY;

		Vec3 motion = this.getDeltaMovement();
		if (altitude < 2.0)
			motion = new Vec3(motion.x, Math.max(motion.y, 0.20), motion.z);
		else if (altitude > 4.0)
			motion = new Vec3(motion.x, Math.min(motion.y, -0.12), motion.z);

		Vec3 toTarget = new Vec3(target.getX() - this.getX(), target.getY(0.5) - this.getY(),
				target.getZ() - this.getZ());
		double distSq = toTarget.lengthSqr();
		if (distSq < 1e-6) {
			// Already overlapping
			tryAirHit(target, relToTarget);
			this.setDeltaMovement(motion);
			return;
		}

		Vec3 dir = toTarget.normalize();

		if (chargeCooldown > 0)
			chargeCooldown--;

		// Start charge if far enough and angry
		if (!charging && chargeCooldown == 0 && distSq >= CHARGE_MIN_DIST_SQ && relToTarget <= -500) {
			charging = true;
			chargeCooldown = CHARGE_COOLDOWN_TICKS;
			if (this.level() instanceof ServerLevel sl) {
				// *** PLAY STATIC ELECTRICITY WHEN STARTING CHARGE ***
				sl.playSound(null, this.blockPosition(), LoveHateRelationShip.vampire_static_electricity.value(), SoundSource.HOSTILE, 0.7f, 1.0f + this.random.nextFloat() * 0.2f);
				// *** END SOUND ***
			}
			this.setSprinting(true);
		}

		if (charging) {
			// Strong forward accel, slight lift to hold line
			motion = motion.add(dir.scale(CHARGE_BASE_ACCEL)).add(0.0, 0.03, 0.0);
			// Cap speed
			double spd = motion.length();
			if (spd > CHARGE_MAX_SPEED)
				motion = motion.scale(CHARGE_MAX_SPEED / spd);
		} else {
			// Normal glide chase
			motion = motion.add(dir.scale(GLIDE_ACCEL));
			double spd = motion.length();
			if (spd > GLIDE_MAX_SPEED)
				motion = motion.scale(GLIDE_MAX_SPEED / spd);
		}

		if (tryAirHit(target, relToTarget)) {
			charging = false;
			this.setSprinting(false);
		}

		this.setDeltaMovement(motion);
		this.zza = 1.0f;
	}

	/**
	 * Attempts a mid-air hit using AABB overlap/inflation. Returns true if an
	 * attack was landed.
	 * 
	 * @param target      The entity to hit.
	 * @param relToTarget The relationship with the target.
	 */
	private boolean tryAirHit(LivingEntity target, int relToTarget) {
		if (!this.isFallFlying())
			return false;

		AABB reach = this.getBoundingBox().inflate(CHARGE_REACH_GROW);
		boolean touching = reach.intersects(target.getBoundingBox());
		double linDist = this.distanceToSqr(target);

		if (!touching && linDist > 9.0)
			return false;

		// Calculate bonus damage based on target's max health
		float targetMaxHealth = target.getMaxHealth();
		float bonusDamagePer100HP = 3.0f;
		float bonusDamage = (targetMaxHealth / 100.0f) * bonusDamagePer100HP;

		float base = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
		float dmg = charging ? (base + CHARGE_BONUS_DMG) : base;
		float totalDamage = dmg + bonusDamage;

		boolean hurt = target.hurt(this.damageSources().mobAttack(this), totalDamage);
		if (hurt) {
			// Knockback on ram
			if (charging) {
				Vec3 v = new Vec3(target.getX() - this.getX(), 0.0, target.getZ() - this.getZ()).normalize();
				target.push(v.x * CHARGE_KNOCK_H, CHARGE_KNOCK_UP, v.z * CHARGE_KNOCK_H);
				if (this.level() instanceof ServerLevel sl) {
					// *** PLAY CREEPY LAUGH WHEN RAMMING (CHARGING HIT) ***
					sl.playSound(null, target.blockPosition(),
LoveHateRelationShip.vampire_creepy_laugh.value(),
							SoundSource.HOSTILE, 0.8f, 0.9f + this.random.nextFloat() * 0.3f);
					// *** END SOUND ***
					sl.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.2,
							0.2, 0.1);
				}
			}

			if (relToTarget > 300) {
				target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
			} else {
				if (relToTarget < -500) {
					target.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
					target.hurt(damageSources().mobAttack(this), 2.0f + bonusDamage);
				}
				if (relToTarget <= -500 && !level().isClientSide && level() instanceof ServerLevel serverLevel) {
					// Summon lightning if relationship is -500 or lower (during air hit)
					double lightningX = target.getX() + (serverLevel.getRandom().nextDouble() - 0.5) * 2.0;
					double lightningY = target.getY();
					double lightningZ = target.getZ() + (serverLevel.getRandom().nextDouble() - 0.5) * 2.0;
					BlockPos lightningPos = new BlockPos((int) lightningX, (int) lightningY, (int) lightningZ);
					net.minecraft.world.entity.LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(serverLevel);
					if (lightning != null) {
						lightning.moveTo(Vec3.atBottomCenterOf(lightningPos));
						// lightning.setCause(this); // null if not caused by an entity
						serverLevel.addFreshEntity(lightning);
					}

					// *** NEW REFLECTION LOGIC FOR SADAKO MOD ***
					// Only attempt to summon Sadako entities if relationship is very low (e.g.,
					// -900)
					if (relToTarget <= -900 && random.nextFloat() < 0.05f) { // 5% chance when very angry
						summonSadakoEntity(serverLevel, target.blockPosition());
					}
					// *** END NEW LOGIC ***
				}
			}

			// Swing for animation
			this.swing(InteractionHand.MAIN_HAND);

			return true;
		}
		return false;
	}

	// *** NEW METHOD FOR SUMMONING SADAKO ENTITY ***
	private void summonSadakoEntity(ServerLevel serverLevel, BlockPos targetPos) {
		// Check if we have already tried loading the classes/fields
		if (!sadakoModChecked) {
			try {
				sadakoModEntitiesClass = Class.forName("net.mcreator.sadako.init.SadakoModEntities");
				// Successfully found the class
				System.out.println("Sadako mod entities class found via reflection!");
			} catch (ClassNotFoundException e) {
				// Mod class not found, set flag to avoid future attempts
				System.out.println("Sadako mod entities class not found: " + e.getMessage());
				sadakoModChecked = true; // Mark as checked to prevent repeated attempts
				return; // Exit if not found
			}
			sadakoModChecked = true; // Mark as checked
		}

		// If class was found previously, proceed
		if (sadakoModEntitiesClass != null) {
			try {
				// Get all static fields declared in the class
				Field[] fields = sadakoModEntitiesClass.getDeclaredFields();
				java.util.List<Field> sadakoEntityFields = new java.util.ArrayList<>();

				// Filter fields that are likely DeferredHolders for EntityTypes
				for (Field field : fields) {
					// Check if the field type is DeferredHolder or a subtype
					// and if its generic type parameter involves EntityType
					if (java.util.regex.Pattern.matches("net\\.neoforged\\.neoforge\\.registries\\.DeferredHolder",
							field.getType().getName())) {
						// This is a heuristic check. A more robust way might involve checking
						// the generic type information, but that can be complex with reflection.
						// For MCreator-generated code, the pattern is usually consistent.
						// Let's assume any static field named like SADAKO_* or NEAR_* is a
						// DeferredHolder<EntityType<?>>
						if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
								&& (field.getName().startsWith("SADAKO") || field.getName().startsWith("NEAR"))) {
							field.setAccessible(true); // Allow access if needed
							sadakoEntityFields.add(field);
						}
					}
				}

				if (sadakoEntityFields.isEmpty()) {
					System.out.println("No suitable DeferredHolder<EntityType<?>> fields found in SadakoModEntities.");
					return; // Exit if no fields found
				}

				// Select a random field from the list
				Field randomField = sadakoEntityFields.get(random.nextInt(sadakoEntityFields.size()));
				System.out.println("Attempting to summon entity from field: " + randomField.getName());

				// Get the DeferredHolder<EntityType<?>, ?> from the selected field
				Object deferredHolder = randomField.get(null); // Static field, pass null

				// Assuming the DeferredHolder has a method like get() to retrieve the actual
				// EntityType
				Class<?> deferredHolderClass = deferredHolder.getClass();
				java.lang.reflect.Method getMethod = deferredHolderClass.getMethod("get");
				Object entityTypeObject = getMethod.invoke(deferredHolder);

				// Cast the retrieved object to EntityType<?>
				if (entityTypeObject instanceof EntityType) {
					EntityType<?> sadakoEntityType = (EntityType<?>) entityTypeObject;

					// Create the entity instance using the retrieved EntityType
					Entity sadakoEntity = sadakoEntityType.create(serverLevel);
					if (sadakoEntity != null) {
						// Position the entity near the target
						double x = targetPos.getX() + (serverLevel.random.nextDouble() - 0.5) * 4.0;
						double y = serverLevel.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) x,
								(int) targetPos.getZ());
						double z = targetPos.getZ() + (serverLevel.random.nextDouble() - 0.5) * 4.0;

						sadakoEntity.moveTo(x, y, z, serverLevel.random.nextFloat() * 360.0f, 0.0f);

						// Spawn the entity in the world
						serverLevel.addFreshEntity(sadakoEntity);
						System.out.println("Summoned " + sadakoEntityType + " entity via reflection!");
					} else {
						System.out.println("Failed to create " + randomField.getName() + " entity instance.");
					}
				} else {
					System.out.println("Retrieved object from " + randomField.getName() + " is not an EntityType.");
				}
			} catch (Exception e) {
				System.out.println("Error summoning Sadako entity via reflection: " + e.getMessage());
				e.printStackTrace(); // Print stack trace for debugging
			}
		}
	}
	// *** END NEW METHOD ***

	private void setFallFlying(boolean flying) {
		this.setSharedFlag(7, flying);
	}

	private void updateMovementSpeed() {
		LivingEntity target = this.getTarget();
		int relToTarget = (target != null) ? getRelationshipWith(target) : NEUTRAL;

		double speed;

		if (relToTarget < -700) {
			speed = 0.45;
		} else if (relToTarget < -400) {
			speed = 0.35;
		} else if (relToTarget > 300 && target != null) {
			speed = 0.38;
		} else if (relToTarget > 200) {
			speed = 0.25;
		} else {
			speed = 0.18;
		}

		if (this.isFallFlying() && target != null) {
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

	private void updateRelationshipDisplayName(Player nearestPlayer, int relToNearestPlayer) {
		String status;
		if (nearestPlayer == null) {
			status = "§7Neutral";
		} else {
			if (relToNearestPlayer > 500)
				status = "§aAlly";
			else if (relToNearestPlayer > 200)
				status = "§2Friend";
			else if (relToNearestPlayer >= HOSTILE_THRESHOLD)
				status = "§7Neutral";
			else if (relToNearestPlayer > -500)
				status = "§cRival";
			else
				status = "§4Enemy";
			status += "Vampire (" + relToNearestPlayer + ")"; // Just show the value with the player
		}

		this.setCustomName(Component.literal(status));
		this.setCustomNameVisible(true);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VEX_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource pDamageSource) {
		return SoundEvents.VEX_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VEX_DEATH;
	}

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

			// *** NEW REFLECTION LOGIC FOR VAMPIRIC LLAMAS MOD ***
			dropVampireLeather(sl);
			// *** END NEW LOGIC ***
		}
	}

	// *** NEW METHOD FOR DROPPING VAMPIRE LEATHER ***
	private void dropVampireLeather(ServerLevel serverLevel) {
		// Check if we have already tried loading the classes/fields
		if (!vampiricLlamasModChecked) {
			try {
				vampiricLlamasItemsClass = Class.forName("com.startraveler.vampiricllamas.VampiricLlamasItems");
				vampireLeatherField = vampiricLlamasItemsClass.getDeclaredField("VAMPIRE_LEATHER");
				vampireLeatherField.setAccessible(true); // Allow access to private field
				// Successfully found the class and field
				System.out.println("Vampiric Llamas mod items found via reflection!");
			} catch (ClassNotFoundException | NoSuchFieldException e) {
				// Mod or specific field not found, set flag to avoid future attempts
				System.out.println(
						"Vampiric Llamas mod items not found or VAMPIRE_LEATHER field missing: " + e.getMessage());
				vampiricLlamasModChecked = true; // Mark as checked to prevent repeated attempts
				return; // Exit if not found
			}
			vampiricLlamasModChecked = true; // Mark as checked
		}

		// If class/field were found previously, proceed
		if (vampiricLlamasItemsClass != null && vampireLeatherField != null) {
			try {
				// Get the DeferredHolder<Item, Item>
				Object deferredHolder = vampireLeatherField.get(null); // Static field, pass null

				// Get the actual Item from the DeferredHolder
				Class<?> deferredHolderClass = deferredHolder.getClass();
				java.lang.reflect.Method getMethod = deferredHolderClass.getMethod("get");
				Object itemObject = getMethod.invoke(deferredHolder);

				// Cast the retrieved object to Item
				if (itemObject instanceof net.minecraft.world.item.Item) {
					net.minecraft.world.item.Item vampireLeatherItem = (net.minecraft.world.item.Item) itemObject;

					// Create an ItemStack of vampire leather (5 items)
					ItemStack leatherStack = new ItemStack(vampireLeatherItem, 5);

					// Drop the item stack at the vampire's location
					net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
							serverLevel, this.getX(), this.getY(), this.getZ(), leatherStack);
					serverLevel.addFreshEntity(itemEntity);
					System.out.println("Dropped 5 Vampire Leather via reflection!");
				} else {
					System.out.println("Retrieved object from VAMPIRE_LEATHER is not an Item.");
				}
			} catch (Exception e) {
				System.out.println("Error dropping Vampire Leather via reflection: " + e.getMessage());
				e.printStackTrace(); // Print stack trace for debugging
			}
		}
	}
	// *** END NEW METHOD ***

	public boolean isInDanger() {
		if (this.level().isClientSide())
			return false;
		if (!(this.level() instanceof ServerLevel serverLevel))
			return false;
		boolean sun = serverLevel.isDay() && this.isOutside() && !this.isInWaterOrRain() && !this.isUnderWater();
		boolean wet = this.isInWaterRainOrBubble();
		return sun || wet;
	}

	@Override
	public boolean isCustomNameVisible() {
		return true;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (isAcceptableFood(stack)) {
			if (!this.level().isClientSide) { // Prevent self-feeding relationship change
				int boost = stack.is(Items.ROTTEN_FLESH) ? 30 : 100;
				adjustRelationshipWith(player, boost);
				stack.shrink(1);
				this.playSound(SoundEvents.GENERIC_EAT, 1.0f, 1.0f);
			}
			return InteractionResult.sidedSuccess(this.level().isClientSide);
		}
		return super.mobInteract(player, hand);
	}

	private boolean isAcceptableFood(ItemStack stack) {
		return stack.is(Items.PORKCHOP) || stack.is(Items.BEEF) || stack.is(Items.MUTTON) || stack.is(Items.SPIDER_EYE)
				|| stack.is(Items.ROTTEN_FLESH);
	}

	public int getVariant() {
		return Mth.clamp(entityData.get(DATA_VARIANT), 0, 3);
	}

	// Override setTarget to prevent the vampire from targeting itself
	@Override
	public void setTarget(LivingEntity target) {
		if (target == this) {
			return; // Do nothing, don't set the target to self
		}
		super.setTarget(target);
	}

}