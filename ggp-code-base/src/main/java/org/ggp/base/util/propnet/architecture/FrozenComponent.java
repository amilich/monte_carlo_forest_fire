package org.ggp.base.util.propnet.architecture;

import java.io.Serializable;

public abstract class FrozenComponent implements Serializable {

	private static final long serialVersionUID = 352524175700224448L;
    /** The inputs to the component. */
    protected final FrozenComponent[] inputs;
    /** The outputs of the component. */
    protected final FrozenComponent[] outputs;

    public FrozenComponent()
    {
    	inputs = null;
    	outputs = null;
    }

    /**
     * Creates a new Component with no inputs or outputs.
     */
    public FrozenComponent(FrozenComponent[] inputs, FrozenComponent[] outputs)
    {
        this.inputs = inputs;
        this.outputs = outputs;
    }

    /**
     * Getter method.
     *
     * @return The inputs to the component.
     */
    public FrozenComponent[] getInputs()
    {
        return inputs;
    }

    /**
     * A convenience method, to get a single input.
     * To be used only when the component is known to have
     * exactly one input.
     *
     * @return The single input to the component.
     */
    public FrozenComponent getSingleInput() {
        assert inputs.length == 1;
        return inputs[0];
    }

    /**
     * Getter method.
     *
     * @return The outputs of the component.
     */
    public FrozenComponent[] getOutputs()
    {
        return outputs;
    }

    /**
     * A convenience method, to get a single output.
     * To be used only when the component is known to have
     * exactly one output.
     *
     * @return The single output to the component.
     */
    public FrozenComponent getSingleOutput() {
        assert outputs.length == 1;
        return outputs[0];
    }

    /**
     * Returns the value of the Component.
     *
     * @return The value of the Component.
     */
    public abstract boolean getValue();

    /**
     * Returns a representation of the Component in .dot format.
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public abstract String toString();

    /**
     * Returns a configurable representation of the Component in .dot format.
     *
     * @param shape
     *            The value to use as the <tt>shape</tt> attribute.
     * @param fillcolor
     *            The value to use as the <tt>fillcolor</tt> attribute.
     * @param label
     *            The value to use as the <tt>label</tt> attribute.
     * @return A representation of the Component in .dot format.
     */
    protected String toDot(String shape, String fillcolor, String label)
    {
        StringBuilder sb = new StringBuilder();

        sb.append("\"@" + Integer.toHexString(hashCode()) + "\"[shape=" + shape + ", style= filled, fillcolor=" + fillcolor + ", label=\"" + label + "\"]; ");
        for ( FrozenComponent component : getOutputs() )
        {
            sb.append("\"@" + Integer.toHexString(hashCode()) + "\"->" + "\"@" + Integer.toHexString(component.hashCode()) + "\"; ");
        }

        return sb.toString();
    }

}