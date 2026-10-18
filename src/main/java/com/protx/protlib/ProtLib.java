package com.protx.protlib;

import com.protx.ProtX;
import com.protx.api.ProtXApi;
import com.protx.core.JsonStore;
import com.protx.core.Location;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * ProtLib: ProtX's protection library. Owns the regions, the flag registry and the
 * placed-block decay. The ProtectModule drives it with commands and player events, the
 * explosion mixin asks it which blocks to spare, and other modules can use it directly:
 * {@code api.service(ProtLib.class)}.
 */
public final class ProtLib {

	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{1,32}");

	public static final class Data {
		public List<Region> regions = new ArrayList<>();
	}

	private static final class Decay {
		final long id;
		final Level level;
		final BlockPos pos;
		final Block block;
		final UUID placer;

		Decay(long id, Level level, BlockPos pos, Block block, UUID placer) {
			this.id = id;
			this.level = level;
			this.pos = pos;
			this.block = block;
			this.placer = placer;
		}
	}

	private final ProtXApi api;
	private final Path file;
	private final Map<String, Region> regions = new LinkedHashMap<>();
	private final Map<String, ProtFlag> flags = new LinkedHashMap<>();
	private final Map<String, Decay> pending = new HashMap<>();
	private long decaySeq;

	public ProtLib(ProtXApi api) {
		this.api = api;
		this.file = api.dataDir().resolve("regions.json");
		registerFlag(ProtFlag.EXPLOSION_PROOF);
		registerFlag(ProtFlag.NO_BREAK);
		registerFlag(ProtFlag.NO_PLACE);
		registerFlag(ProtFlag.BLOCK_DECAY);
		registerFlag(ProtFlag.LOCK_SIGNS);
		registerFlag(ProtFlag.ALLOW_INTERACT);
	}

	// ---- persistence ---------------------------------------------------------------------

	public void load() {
		Data data = JsonStore.load(file, Data.class, Data::new);
		regions.clear();
		if (data.regions != null) {
			for (Region region : data.regions) {
				if (region.name == null || region.dimension == null) {
					continue;
				}
				if (region.flags == null) {
					region.flags = new LinkedHashMap<>();
				}
				regions.put(key(region.name), region);
			}
		}
		ProtX.LOGGER.info("ProtLib: loaded {} region(s)", regions.size());
	}

	public void save() {
		Data data = new Data();
		data.regions = new ArrayList<>(regions.values());
		JsonStore.save(file, data);
	}

	// ---- flags ---------------------------------------------------------------------------

	public void registerFlag(ProtFlag flag) {
		flags.put(flag.id(), flag);
	}

	public ProtFlag flag(String id) {
		return flags.get(id.toLowerCase(Locale.ROOT));
	}

	public Collection<ProtFlag> flags() {
		return flags.values();
	}

	// ---- regions -------------------------------------------------------------------------

	public static boolean validName(String name) {
		return NAME.matcher(name).matches();
	}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public Region get(String name) {
		return regions.get(key(name));
	}

	public Collection<Region> all() {
		return regions.values();
	}

	public Collection<String> names() {
		List<String> out = new ArrayList<>();
		for (Region region : regions.values()) {
			out.add(region.name);
		}
		return out;
	}

	public Region create(String name, String dimension, BlockPos a, BlockPos b, String createdBy) {
		Region region = new Region();
		region.name = name;
		region.dimension = dimension;
		region.setBounds(a, b);
		region.decayTicks = Math.max(1, (int) Math.round(api.config().defaultDecaySeconds * 20));
		region.createdBy = createdBy;
		region.createdAt = System.currentTimeMillis();
		regions.put(key(name), region);
		save();
		return region;
	}

	public boolean delete(String name) {
		boolean removed = regions.remove(key(name)) != null;
		if (removed) {
			save();
		}
		return removed;
	}

	public boolean rename(String oldName, String newName) {
		Region region = regions.get(key(oldName));
		if (region == null || (regions.containsKey(key(newName)) && !key(oldName).equals(key(newName)))) {
			return false;
		}
		regions.remove(key(oldName));
		region.name = newName;
		regions.put(key(newName), region);
		save();
		return true;
	}

	// ---- queries -------------------------------------------------------------------------

	/** Every region that contains the position (regions may overlap). */
	public List<Region> at(String dimension, BlockPos pos) {
		if (regions.isEmpty()) {
			return List.of();
		}
		List<Region> out = null;
		for (Region region : regions.values()) {
			if (region.contains(dimension, pos.getX(), pos.getY(), pos.getZ())) {
				if (out == null) {
					out = new ArrayList<>();
				}
				out.add(region);
			}
		}
		return out == null ? List.of() : out;
	}

	public List<Region> at(Level level, BlockPos pos) {
		return regions.isEmpty() ? List.of() : at(Location.dimId(level), pos);
	}

	/** True if at least one of the regions has the flag switched on. */
	public static boolean anyOn(List<Region> regions, ProtFlag flag) {
		for (Region region : regions) {
			if (region.flag(flag)) {
				return true;
			}
		}
		return false;
	}

	/** True if at least one of the regions has the flag switched off. */
	public static boolean anyOff(List<Region> regions, ProtFlag flag) {
		for (Region region : regions) {
			if (!region.flag(flag)) {
				return true;
			}
		}
		return false;
	}

	public static int shortestDecay(List<Region> regions) {
		int ticks = Integer.MAX_VALUE;
		for (Region region : regions) {
			if (region.flag(ProtFlag.BLOCK_DECAY)) {
				ticks = Math.min(ticks, region.decayTicks);
			}
		}
		return ticks;
	}

	// ---- explosions ----------------------------------------------------------------------

	/** Called by the explosion mixin: removes blocks inside explosion-proof regions from the blast. */
	public List<BlockPos> filterExplosion(Level level, List<BlockPos> blocks) {
		if (regions.isEmpty() || blocks.isEmpty()) {
			return blocks;
		}
		String dimension = Location.dimId(level);
		List<BlockPos> kept = new ArrayList<>(blocks.size());
		for (BlockPos pos : blocks) {
			if (!anyOn(at(dimension, pos), ProtFlag.EXPLOSION_PROOF)) {
				kept.add(pos);
			}
		}
		return kept.size() == blocks.size() ? blocks : kept;
	}

	// ---- placed-block decay --------------------------------------------------------------

	private static String decayKey(Level level, BlockPos pos) {
		return Location.dimId(level) + "@" + pos.asLong();
	}

	/**
	 * Call right before a player places a block at {@code pos}. If a block really appears there
	 * by the end of the tick, it is removed again after {@code decayTicks}.
	 */
	public void trackPlacement(Level level, BlockPos pos, int decayTicks) {
		trackPlacement(level, pos, decayTicks, null);
	}

	/**
	 * Same, but remembers who placed it: the block cracks over its last moments, breaks, and the
	 * item flies back into that player's inventory (survival players only).
	 */
	public void trackPlacement(Level level, BlockPos pos, int decayTicks, UUID placer) {
		BlockPos target = pos.immutable();
		BlockState before = level.getBlockState(target);
		api.scheduler().run(0, () -> {
			BlockState now = level.getBlockState(target);
			if (now.equals(before) || now.isAir()) {
				return; // the placement did not happen
			}
			String key = decayKey(level, target);
			if (pending.containsKey(key)) {
				return;
			}
			long id = ++decaySeq;
			Decay decay = new Decay(id, level, target, now.getBlock(), placer);
			pending.put(key, decay);
			scheduleCracks(key, decay, decayTicks);
			api.scheduler().run(decayTicks, () -> expire(key, id, true));
		});
	}

	public boolean isDecaying(Level level, BlockPos pos) {
		return !pending.isEmpty() && pending.containsKey(decayKey(level, pos));
	}

	/** A player broke the block themselves: stop its pending decay. */
	public void cancelDecay(Level level, BlockPos pos) {
		Decay decay = pending.remove(decayKey(level, pos));
		if (decay != null) {
			decay.level.destroyBlockProgress((int) decay.id, decay.pos, -1);
		}
	}

	/** Crack animation: ten stages spread over (at most) the last two seconds of the block's life. */
	private void scheduleCracks(String key, Decay decay, int decayTicks) {
		int window = Math.min(decayTicks, 40);
		int begin = decayTicks - window;
		for (int stage = 0; stage < 10; stage++) {
			int delay = begin + window * stage / 10;
			int progress = stage;
			api.scheduler().run(Math.max(1, delay), () -> {
				Decay current = pending.get(key);
				if (current != null && current.id == decay.id) {
					decay.level.destroyBlockProgress((int) decay.id, decay.pos, progress);
				}
			});
		}
	}

	private void expire(String key, long id, boolean animate) {
		Decay decay = pending.get(key);
		if (decay == null || decay.id != id) {
			return; // cancelled or replaced by a newer placement
		}
		pending.remove(key);
		decay.level.destroyBlockProgress((int) decay.id, decay.pos, -1);
		if (decay.level.getBlockState(decay.pos).is(decay.block)) {
			decay.level.destroyBlock(decay.pos, false); // plays the break particles and sound
			returnToPlacer(decay, animate);
		}
	}

	/** Gives the block's item back to whoever placed it, flying through the air if they are close. */
	private void returnToPlacer(Decay decay, boolean animate) {
		if (decay.placer == null || !api.hasServer()) {
			return;
		}
		ServerPlayer player = api.server().getPlayerList().getPlayer(decay.placer);
		ItemStack stack = new ItemStack(decay.block.asItem());
		if (player == null || player.isCreative() || stack.isEmpty()) {
			return;
		}
		boolean near = player.level() == decay.level && player.position().distanceToSqr(Vec3.atCenterOf(decay.pos)) <= 16 * 16;
		if (!animate || !near) {
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
			return;
		}
		Vec3 start = Vec3.atCenterOf(decay.pos);
		ItemEntity item = new ItemEntity(decay.level, start.x, start.y, start.z, stack, 0, 0.12, 0);
		item.setPickUpDelay(0);
		item.setNoGravity(true);
		decay.level.addFreshEntity(item);
		steerItem(item, decay.placer, 0);
	}

	/** Pulls the dropped item towards the player until they pick it up (about one second at most). */
	private void steerItem(ItemEntity item, UUID playerId, int tick) {
		if (item.isRemoved()) {
			return;
		}
		ServerPlayer player = api.hasServer() ? api.server().getPlayerList().getPlayer(playerId) : null;
		if (player == null || tick >= 30 || player.level() != item.level()) {
			item.setNoGravity(false);
			return;
		}
		Vec3 to = player.position().add(0, player.getBbHeight() * 0.5, 0).subtract(item.position());
		double dist = to.length();
		item.setDeltaMovement(dist < 1.0E-4 ? Vec3.ZERO : to.scale(Math.min(0.45, dist) / dist));
		api.scheduler().run(1, () -> steerItem(item, playerId, tick + 1));
	}

	/** Removes every still-decaying block right now (used on shutdown so none are left behind). */
	public void flushDecays() {
		for (Map.Entry<String, Decay> entry : new ArrayList<>(pending.entrySet())) {
			expire(entry.getKey(), entry.getValue().id, false);
		}
	}
}
