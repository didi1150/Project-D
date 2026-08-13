package dev.core.item.display;

import java.util.List;

import dev.core.item.RPGItem;

public interface RPGItemLoreRenderer {

	List<String> render(RPGItem item);
	
}
