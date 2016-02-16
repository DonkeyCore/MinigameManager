package me.donkeycore.minigamemanager.api.winner;

import java.util.UUID;

import org.bukkit.Bukkit;

public final class SingleWinnerList implements WinnerList {
	
	private UUID firstPlace, secondPlace, thirdPlace;
	private String fName, sName, tName;
	
	public SingleWinnerList(UUID firstPlace) {
		this.firstPlace = firstPlace;
	}
	
	public SingleWinnerList(UUID firstPlace, UUID secondPlace) {
		this.firstPlace = firstPlace;
		this.secondPlace = secondPlace;
	}
	
	public SingleWinnerList(UUID firstPlace, UUID secondPlace, UUID thirdPlace) {
		this.firstPlace = firstPlace;
		this.secondPlace = secondPlace;
		this.thirdPlace = thirdPlace;
	}
	
	public UUID[] getFirstPlace() {
		return firstPlace == null ? null : new UUID[]{firstPlace};
	}
	
	public void setFirstPlace(UUID[] firstPlace) {
		this.firstPlace = firstPlace[0];
		setFirstPlaceName(Bukkit.getPlayer(this.firstPlace).getName());
	}

	@Override
	public String getFirstPlaceName() {
		return fName;
	}

	@Override
	public void setFirstPlaceName(String name) {
		this.fName = name;
	}
	
	public UUID[] getSecondPlace() {
		return secondPlace == null ? null : new UUID[]{secondPlace};
	}
	
	public void setSecondPlace(UUID[] secondPlace) {
		this.secondPlace = secondPlace[0];
		setSecondPlaceName(Bukkit.getPlayer(this.secondPlace).getName());
	}

	@Override
	public String getSecondPlaceName() {
		return sName;
	}

	@Override
	public void setSecondPlaceName(String name) {
		this.sName = name;
	}
	
	public UUID[] getThirdPlace() {
		return thirdPlace == null ? null : new UUID[]{thirdPlace};
	}
	
	public void setThirdPlace(UUID[] thirdPlace) {
		this.thirdPlace = thirdPlace[0];
		setThirdPlaceName(Bukkit.getPlayer(this.thirdPlace).getName());
	}

	@Override
	public String getThirdPlaceName() {
		return tName;
	}

	@Override
	public void setThirdPlaceName(String name) {
		this.tName = name;
	}
	
}
