package dev.core.item;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.SetBonus;

public class RPGItemSet {

	private final String id;
	private final String name;
	private final List<String> pieceIds;
	private final Map<Integer, SetBonus> bonuses;

	public RPGItemSet(String id, String name, List<String> pieceIds, Map<Integer, SetBonus> bonuses) {
		this.id = id;
		this.name = name;
		this.pieceIds = pieceIds;
		this.bonuses = bonuses;
	}

	public Optional<SetBonus> getBonusForPieces(int count) {
		return Optional.ofNullable(bonuses.get(count));
	}

	public boolean containsPiece(String itemId) {
		return pieceIds.contains(itemId);
	}

	public String getName() {
		return name;
	}

	public String getId() {
		return id;
	}

	public Map<Integer, SetBonus> getBonuses() {
		return bonuses;
	}

}
