package dev.core.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.core.ability.SetBonus;

public class RPGItemSet {

	private final String id;
	private final String name;
	private final List<String> pieceIds;
	private final Map<Integer, SetBonus> bonuses;

	private RPGItemSet(Builder builder) {
		this.id = builder.id;
		this.name = builder.name;
		this.pieceIds = builder.pieceIds;
		this.bonuses = new HashMap<>(builder.bonuses);
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
		return new HashMap<>(bonuses);
	}

	public List<String> getPieceIds() {
		return pieceIds;
	}

	public static Builder builder(String id, String name) {
		return new Builder(id, name);
	}

	/**
	 * Builder for creating RPGItemSet instances with flexible composition.
	 */
	public static class Builder {
		private final String id;
		private final String name;
		private List<String> pieceIds;
		private Map<Integer, SetBonus> bonuses = new HashMap<>();

		public Builder(String id, String name) {
			this.id = id;
			this.name = name;
		}

		public Builder withPieceIds(List<String> pieceIds) {
			this.pieceIds = pieceIds;
			return this;
		}

		public Builder addBonus(int pieceCount, SetBonus bonus) {
			this.bonuses.put(pieceCount, bonus);
			return this;
		}

		public Builder withBonuses(Map<Integer, SetBonus> bonuses) {
			this.bonuses = new HashMap<>(bonuses);
			return this;
		}

		public RPGItemSet build() {
			if (pieceIds == null) {
				throw new IllegalStateException("pieceIds is required");
			}
			return new RPGItemSet(this);
		}
	}
}
