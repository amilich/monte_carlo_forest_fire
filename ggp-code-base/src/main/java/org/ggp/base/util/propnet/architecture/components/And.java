package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.Component;

/**
 * The And class is designed to represent logical AND gates.
 */
@SuppressWarnings("serial")
public final class And extends Component {
	/**
	 * Returns true if and only if every input to the and is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	@Override
	public boolean getValue() {
		for ( Component component : inputs ) {
			if ( !component.curVal ) {
				return false;
			}
		}
		return true;
	}

	public int num = 0;

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		if (num < 0) {
			return toDot("invhouse", "red", "AND=" + num +".id="+intVal);
		} else {
			return toDot("invhouse", "grey", "AND=" + num +".id="+intVal);
		}
	}

}
