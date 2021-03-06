package minigamemanager.events.rotation;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import minigamemanager.api.rotation.Rotation;

/**
 * An event for rotations
 * 
 * @author DonkeyCore
 */
public abstract class RotationEvent extends Event {
	
	private static final HandlerList handlers = new HandlerList();
	
	private final Rotation rotation;
	
	public RotationEvent(Rotation rotation) {
		this.rotation = rotation;
	}
	
	public Rotation getRotation() {
		return rotation;
	}
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	public static HandlerList getHandlerList() {
		return handlers;
	}
	
}
