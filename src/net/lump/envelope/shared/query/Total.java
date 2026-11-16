package net.lump.envelope.shared.query;

import net.lump.envelope.shared.entity.Identifiable;
import net.lump.lib.Money;

import java.io.Serializable;

/**
 * One account or category with the sum of its allocations: what the tree draws a
 * node from.  The structure comes from the entity and the money from a separate
 * sum keyed by its id, defaulting to zero, so an account or category with no
 * allocations yet still appears.
 *
 * @param item    the account or category
 * @param balance the sum of its allocations, zero when there are none
 */
public record Total<T extends Identifiable<Integer, ?>>(T item, Money balance) implements Serializable {
}
