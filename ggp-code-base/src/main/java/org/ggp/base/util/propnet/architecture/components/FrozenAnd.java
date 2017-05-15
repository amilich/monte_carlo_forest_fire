package org.ggp.base.util.propnet.architecture.components;

import org.ggp.base.util.propnet.architecture.FrozenComponent;

public class FrozenAnd extends FrozenComponent {
	/**
	 * Returns true if and only if every input to the and is true.
	 *
	 * @see org.ggp.base.util.propnet.architecture.Component#getValue()
	 */
	private int nTrue; // TODO initialize

	public FrozenAnd(FrozenComponent[] inputs, FrozenComponent[] outputs)
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
		return nTrue == inputs.length;
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
