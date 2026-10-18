package com.protx.core;

/** config/protx/config.json. New fields get written with their defaults on next start. */
public final class ProtXConfig {

	/** How far (in blocks) a prisoner may wander from the prison point before being pulled back. */
	public int prisonLeashRadius = 16;

	/** Decay time for blocks placed inside protected regions, for newly created regions. */
	public double defaultDecaySeconds = 2.0;

	/** Log every /cmd use to the server console. */
	public boolean auditCmd = true;
}
