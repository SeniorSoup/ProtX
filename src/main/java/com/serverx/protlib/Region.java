package com.serverx.protlib;

import net.minecraft.core.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** A cuboid protected region. Plain public fields so Gson can store it. */
public final class Region {

	public String name;
	public String dimension;
	public int minX;
	public int minY;
	public int minZ;
	public int maxX;
	public int maxY;
	public int maxZ;
	/** Only flags that differ from their default are stored. */
	public Map<String, Boolean> flags = new LinkedHashMap<>();
	public int decayTicks = 40;
	public String createdBy;
	public long createdAt;

	public Region() {
	}

	public void setBounds(BlockPos a, BlockPos b) {
		minX = Math.min(a.getX(), b.getX());
		minY = Math.min(a.getY(), b.getY());
		minZ = Math.min(a.getZ(), b.getZ());
		maxX = Math.max(a.getX(), b.getX());
		maxY = Math.max(a.getY(), b.getY());
		maxZ = Math.max(a.getZ(), b.getZ());
	}

	public boolean contains(String dim, int x, int y, int z) {
		return dimension.equals(dim)
			&& x >= minX && x <= maxX
			&& y >= minY && y <= maxY
			&& z >= minZ && z <= maxZ;
	}

	public boolean flag(ProtFlag flag) {
		Boolean value = flags.get(flag.id());
		return value != null ? value : flag.defaultValue();
	}

	public void setFlag(ProtFlag flag, boolean value) {
		if (value == flag.defaultValue()) {
			flags.remove(flag.id());
		} else {
			flags.put(flag.id(), value);
		}
	}

	public long volume() {
		return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
	}

	public String describeBounds() {
		return "(" + minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ")";
	}
}
