package dev.core.item;

/**
 * Who may use an {@link RPGItem}.
 *
 * <ul>
 *   <li>{@link #BOTH} — usable by players (selectable in the item draft) and
 *       equippable on mobs.</li>
 *   <li>{@link #MOB_ONLY} — reserved for mobs; players can never select or equip
 *       it (the draft and inventory sync reject it).</li>
 * </ul>
 */
public enum ItemUsage {
    BOTH,
    MOB_ONLY
}
