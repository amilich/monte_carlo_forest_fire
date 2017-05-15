package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.FrozenComponent;

public class FrozenOr extends FrozenComponent {
	/**
	 * Returns true if and only if every input to the and is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	private int nTrue;

	public FrozenOr(FrozenComponent[] inputs, FrozenComponent[] outputs)
	{
		super(inputs, outputs);

		nTrue = 0;
		for (FrozenComponent c : inputs) {
			if(c.getValue()) {
				nTrue ++;
			}
		}
	}

	@Override
	public boolean getValue()
	{
		return nTrue != 0;
	}

	/**
	 * @see org.ggp.base.util.propnet.architecture.Component#toString()
	 */
	@Override
	public String toString()
	{
		return toDot("invhouse", "grey", "AND");
	}
}
