package com.serverx.core;

import com.serverx.ServerX;

import java.util.PriorityQueue;

/** Tick-based scheduler. 20 ticks = 1 second. Runs on the server thread at the end of each tick. */
public final class Scheduler {

	private static final class Task implements Comparable<Task> {
		final long due;
		final long seq;
		final int period;
		final Runnable action;

		Task(long due, long seq, int period, Runnable action) {
			this.due = due;
			this.seq = seq;
			this.period = period;
			this.action = action;
		}

		@Override
		public int compareTo(Task other) {
			int byDue = Long.compare(due, other.due);
			return byDue != 0 ? byDue : Long.compare(seq, other.seq);
		}
	}

	private final PriorityQueue<Task> queue = new PriorityQueue<>();
	private long now;
	private long seq;

	/** Ticks elapsed since ServerX started. */
	public long now() {
		return now;
	}

	/** Runs once, {@code delayTicks} from now (0 = at the end of the current/next tick). */
	public void run(int delayTicks, Runnable action) {
		queue.add(new Task(now + Math.max(0, delayTicks), seq++, 0, action));
	}

	/** Runs every {@code periodTicks}, starting one period from now. */
	public void repeat(int periodTicks, Runnable action) {
		int period = Math.max(1, periodTicks);
		queue.add(new Task(now + period, seq++, period, action));
	}

	/** Called by ServerX once per server tick. */
	public void tick() {
		now++;
		Task task;
		while ((task = queue.peek()) != null && task.due <= now) {
			queue.poll();
			try {
				task.action.run();
			} catch (Throwable t) {
				ServerX.LOGGER.error("Scheduled task failed", t);
			}
			if (task.period > 0) {
				queue.add(new Task(now + task.period, seq++, task.period, task.action));
			}
		}
	}
}
