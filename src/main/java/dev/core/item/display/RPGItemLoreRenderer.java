package dev.core.item.display;

import java.util.List;

import dev.core.entity.RPGEntity;
import dev.core.item.RPGItem;

public interface RPGItemLoreRenderer {

	List<String> render(RPGItem item);

	/**
	 * Renders the item's lore from the perspective of a specific holder (may be
	 * {@code null}). Implementations can use the holder to reflect holder-specific
	 * modifiers, e.g. a set-passive mana cost discount on ability costs; the base
	 * implementation simply falls back to the holder-less render.
	 */
	default List<String> render(RPGItem item, RPGEntity holder) {
		return render(item);
	}
	
}
