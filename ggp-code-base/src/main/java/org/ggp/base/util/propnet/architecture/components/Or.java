package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The Or class is designed to represent logical OR gates.
 */
@SuppressWarnings("serial")
public final class Or extends Component
{
	/**
	 * Returns true if and only if at least one of the inputs to the or is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	@Override
	public boolean getValue() {
		for ( Component component : inputs ) {
			if ( component.curVal ) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */

	public int num = 0;
	@Override
	public String toString()
	{
		if (num < 0) {
			return toDot("ellipse", "red", "OR=" + num +".id="+intVal);
		} else {
			return toDot("ellipse", "grey", "OR=" + num +".id="+intVal);
		}
	}
}