package org.ggp.base.util.statemachine;

import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlSentence;

public class MachineState {
    public MachineState() {
        this.contents = null;
    }

    /**
     * Starts with a simple implementation of a MachineState. StateMachines that
     * want to do more advanced things can subclass this implementation, but for
     * many cases this will do exactly what we want.
     */
    private Set<GdlSentence> contents;
    public MachineState(Set<GdlSentence> contents) {
        this.contents = contents;
    }

    public BitSet props;

    public MachineState(Set<GdlSentence> contents, BitSet bases) {
        this.contents = contents;
        this.props = new BitSet(bases.size());
        this.props.or(bases);
    }

    public MachineState(BitSet bases) {
        this.props = new BitSet(bases.size());
        this.props.or(bases);
    }

    /**
     * getContents returns the GDL sentences which determine the current state
     * of the game being played. Two given states with identical GDL sentences
     * should be identical states of the game.
     */
    public Set<GdlSentence> getContents(){
        return contents;
    }

    @Override
    public MachineState clone() {
        return new MachineState(new HashSet<GdlSentence>(contents));
    }

    /* Utility methods */
    @Override
    public int hashCode() {
    	if (contents == null) return props.hashCode();
        return getContents().hashCode();
    }

    @Override
    public String toString()
    {
        Set<GdlSentence> contents = getContents();
        if(contents == null)
            return "(MachineState with null contents)";
        else
            return contents.toString();
    }

    @Override
    public boolean equals(Object o) {
        if ((o != null) && (o instanceof MachineState)) {
        	MachineState state = (MachineState) o;
        	if (contents != null) {
        		return state.getContents().equals(getContents());
        	}
            return state.props.equals(this.props);
//            if (contents == null) {
//            	return state.props.equals(this.props);
//            } else {
//            	return state.getContents().equals(getContents());
//            }
            // return state.getContents().equals(getContents());
        }

        return false;
    }
}