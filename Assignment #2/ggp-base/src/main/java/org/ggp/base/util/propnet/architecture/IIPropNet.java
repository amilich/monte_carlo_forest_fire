package org.ggp.base.util.propnet.architecture;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.statemachine.Role;

/**
 * The class IIPropNet is a generalization of the existing PropNet paradigm to
 * GGP with Incomplete Information. Each IIPropNet wraps around an existing
 * propnet and associates a double II-value to each Proposition in the net.
 * A value of 1 represents true, 0 represents false, and all numbers in between
 * represent an unknown truth value. For now, all unknown propositions have ii-value
 * of 0.5.
 *
 * The IIPropNet class also track "sees" propositions much like a traditional
 * propnet does for "legals," "base," etc. IIPropNets also assign each Transition
 * to be either "does" or "non-does." A "does" transition depends on an input proposition
 * whereas a "non-does" does not. Tracking these transitions is useful for determining
 * truth values of propositions that are not dependent on player moves, particularly for
 * calculating legal moves using an IIStateMachine.
 * @author hardiecate
 *
 */

public final class IIPropNet {

	private final PropNet propNet;

	private Map<Proposition, Double> iivalues;

	/** References to every SeesProposition, indexed by role. */
	private final Map<Role, Set<Proposition>> seesPropositionsByRole;

	/** References to every SeesProposition, indexed by name. */
	private final Map<GdlSentence, Proposition> seesPropositionsByName;

	private final Set<Transition> transitions;

	/** Set of all transitions not dependent on input propositions */
	private final Set<Transition> nonDoesTransitions;

	/** Map from roles to transitions that are dependent on the actions of that role. */
	private final Map<Role, Set<Transition>> doesTransitions;


	/**
	 * Creates a new IIPropNet from a list of Components, along with indices over
	 * those components.
	 *
	 * @param components
	 *            A list of Components.
	 */
	public IIPropNet(PropNet propNet) {
		this.propNet = propNet;

		this.seesPropositionsByRole = recordSeesPropositionsByRole();
		this.seesPropositionsByName = recordSeesPropositionsByName();
		this.iivalues = createIIValuesMap();
		transitions = recordTransitions();
		nonDoesTransitions = recordNonDoesTransitions();
		doesTransitions = recordDoesTransitions();
	}

	public Map<Role, Set<Proposition>> getSeesPropositionsByRole() {
		return seesPropositionsByRole;
	}

	public Map<GdlSentence, Proposition> getSeesPropositionsByName() {
		return seesPropositionsByName;
	}

	public Map<Proposition, Double> getIIValues() {
		return iivalues;
	}

	public Set<Transition> getTransitions() {
		return transitions;
	}

	public Set<Transition> getNonDoesTransitions() {
		return nonDoesTransitions;
	}

	public Map<Role, Set<Transition>> getDoesTransitions() {
		return doesTransitions;
	}

	public Map<Proposition, Proposition> getLegalInputMap() {
		return propNet.getLegalInputMap();
	}

	private Map<Proposition, Double> createIIValuesMap() {
		Map<Proposition, Double> iivalues = new HashMap<Proposition, Double>();
		for (Proposition prop : getPropositions()) {
			iivalues.put(prop, 0.5);
		}
		return iivalues;
	}

	public void setIIValue(Proposition prop, double value) {
		iivalues.put(prop, value);
		if (value == 0) {
			prop.setValue(false);
		} else if (value == 1) {
			prop.setValue(true);
		}
	}

	public double getIIValue(Proposition prop) {
		return iivalues.get(prop);
	}

	/**
	 * Builds an index over the SeesPropositions in the PropNet.
	 *
	 * @return An index over the SeesPropositions in the PropNet.
	 */
	private Map<Role, Set<Proposition>> recordSeesPropositionsByRole()
	{
		Map<Role, Set<Proposition>> seesPropositions = new HashMap<Role, Set<Proposition>>();
		for (Proposition proposition : propNet.getPropositions())
		{
			// Skip all propositions that aren't GdlRelations.
			if (!(proposition.getName() instanceof GdlRelation))
				continue;

			GdlRelation relation = (GdlRelation) proposition.getName();
			if (relation.getName().getValue().equals("sees")) {
				GdlConstant name = (GdlConstant) relation.get(0);
				Role r = new Role(name);
				if (!seesPropositions.containsKey(r)) {
					seesPropositions.put(r, new HashSet<Proposition>());
				}
				seesPropositions.get(r).add(proposition);
			}
		}

		return seesPropositions;
	}

	private Map<GdlSentence, Proposition> recordSeesPropositionsByName()
	{
		Map<GdlSentence, Proposition> seesPropositions = new HashMap<GdlSentence, Proposition>();
		for (Proposition proposition : propNet.getPropositions()) {
			// Skip all propositions that aren't GdlRelations.
			if (!(proposition.getName() instanceof GdlRelation))
				continue;

			if (proposition.getName().getName().getValue().equals("sees")) {
				seesPropositions.put(proposition.getName(), proposition);
			}
		}

		return seesPropositions;
	}

	private Set<Transition> recordTransitions() {
		Set<Transition> trans = new HashSet<Transition>();
		for (Component c : getComponents()) {
			if (c instanceof Transition) trans.add((Transition)c);
		}
		return trans;
	}

	private Set<Transition> recordNonDoesTransitions() {
		Set<Transition> nonDoes = new HashSet<Transition>();
		for (Transition t : transitions) {
			if (transitionsHelper(t.getSingleInput()).size() == 0) nonDoes.add(t);
		}
		return nonDoes;
	}

	// presumes that doesTransitions has already been set
	private Map<Role, Set<Transition>> recordDoesTransitions() {
		Map<Role, Set<Transition>> transMap = new HashMap<Role, Set<Transition>>();
		for (Role role : propNet.getRoles()) {
			transMap.put(role, new HashSet<Transition>());
		}
		for (Transition t : transitions) {
			if (!nonDoesTransitions.contains(t)) {
				Set<Role> roles = transitionsHelper(t.getSingleInput());
				for (Role r : roles) {
					transMap.get(r).add(t);
				}
			}
		}
		return transMap;
	}

	private Set<Role> transitionsHelper(Component c) {
		Set<Role> roles = new HashSet<Role>();
		if (c instanceof Transition) return roles;
		else if (c instanceof Proposition) {
			Proposition p = (Proposition) c;
			if (getInputPropositions().get(((Proposition)c).getName()) != null) {	// p is an input prop
				for (int i = 0; i < getRoles().size(); i++) {
					if (getRoles().get(i).getName().getValue().equals(p.getName().getBody().get(0).toString())) { // if roles match
						roles.add(getRoles().get(i));
					}
				}

			} else {	// not an inputProposition
				for (Component comp : c.getInputs()) {
					roles.addAll(transitionsHelper(comp));
				}
			}
			return roles;
		} else {	// not a Transition or Proposition
			for (Component comp : c.getInputs()) {
				roles.addAll(transitionsHelper(comp));
			}
			return roles;
		}
	}

	public List<Role> getRoles()
	{
		return propNet.getRoles();
	}

	/**
	 * Getter method.
	 *
	 * @return References to every BaseProposition in the PropNet, indexed by
	 *         name.
	 */
	public Map<GdlSentence, Proposition> getBasePropositions()
	{
		return propNet.getBasePropositions();
	}

	/**
	 * Getter method.
	 *
	 * @return References to every Component in the PropNet.
	 */
	public Set<Component> getComponents()
	{
		return propNet.getComponents();
	}

	/**
	 * Getter method.
	 *
	 * @return References to every GoalProposition in the PropNet, indexed by
	 *         player name.
	 */
	public Map<Role, Set<Proposition>> getGoalPropositions()
	{
		return propNet.getGoalPropositions();
	}

	/**
	 * Getter method. A reference to the single, unique, InitProposition.
	 *
	 * @return
	 */
	public Proposition getInitProposition()
	{
		return propNet.getInitProposition();
	}

	/**
	 * Getter method.
	 *
	 * @return References to every InputProposition in the PropNet, indexed by
	 *         name.
	 */
	public Map<GdlSentence, Proposition> getInputPropositions()
	{
		return propNet.getInputPropositions();
	}

	/**
	 * Getter method.
	 *
	 * @return References to every LegalProposition in the PropNet, indexed by
	 *         player name.
	 */
	public Map<Role, Set<Proposition>> getLegalPropositions()
	{
		return propNet.getLegalPropositions();
	}

	/**
	 * Getter method.
	 *
	 * @return References to every Proposition in the PropNet.
	 */
	public Set<Proposition> getPropositions()
	{
		return propNet.getPropositions();
	}

	/**
	 * Getter method.
	 *
	 * @return A reference to the single, unique, TerminalProposition.
	 */
	public Proposition getTerminalProposition()
	{
		return propNet.getTerminalProposition();
	}

	/**
	 * Returns a representation of the PropNet in .dot format.
	 *
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString()
	{
		StringBuilder sb = new StringBuilder();

		sb.append("digraph propNet\n{\n");
		for ( Component component : getComponents() )
		{
			sb.append("\t" + component.toString() + "\n");
		}
		sb.append("}");

		return sb.toString();
	}

	/**
	 * Outputs the propnet in .dot format to a particular file.
	 * This can be viewed with tools like Graphviz and ZGRViewer.
	 *
	 * @param filename the name of the file to output to
	 */
	public void renderToFile(String filename) {
		propNet.renderToFile(filename);
	}


	public int getSize() {
		return getComponents().size();
	}

	public int getNumAnds() {
		int andCount = 0;
		for(Component c : getComponents()) {
			if(c instanceof And)
				andCount++;
		}
		return andCount;
	}

	public int getNumOrs() {
		int orCount = 0;
		for(Component c : getComponents()) {
			if(c instanceof Or)
				orCount++;
		}
		return orCount;
	}

	public int getNumNots() {
		int notCount = 0;
		for(Component c : getComponents()) {
			if(c instanceof Not)
				notCount++;
		}
		return notCount;
	}

	public int getNumLinks() {
		int linkCount = 0;
		for(Component c : getComponents()) {
			linkCount += c.getOutputs().size();
		}
		return linkCount;
	}

	/**
	 * Removes a component from the propnet. Be very careful when using
	 * this method, as it is not thread-safe. It is highly recommended
	 * that this method only be used in an optimization period between
	 * the propnet's creation and its initial use, during which it
	 * should only be accessed by a single thread.
	 *
	 * The INIT and terminal components cannot be removed.
	 */
	public void removeComponent(Component c) {


		//Go through all the collections it could appear in
		if(c instanceof Proposition) {
			Proposition p = (Proposition) c;
			GdlSentence name = p.getName();
			if(getBasePropositions().containsKey(name)) {
				getBasePropositions().remove(name);
			} else if(getInputPropositions().containsKey(name)) {
				getInputPropositions().remove(name);
				//The map goes both ways...
				Proposition partner = propNet.getLegalInputMap().get(p);
				if(partner != null) {
					propNet.getLegalInputMap().remove(partner);
					propNet.getLegalInputMap().remove(p);
				}
			} else if(name == GdlPool.getProposition(GdlPool.getConstant("INIT"))) {
				throw new RuntimeException("The INIT component cannot be removed. Consider leaving it and ignoring it.");
			} else if(name == GdlPool.getProposition(GdlPool.getConstant("terminal"))) {
				throw new RuntimeException("The terminal component cannot be removed.");
			} else {
				for(Set<Proposition> propositions : propNet.getLegalPropositions().values()) {
					if(propositions.contains(p)) {
						propositions.remove(p);
						Proposition partner = propNet.getLegalInputMap().get(p);
						if(partner != null) {
							propNet.getLegalInputMap().remove(partner);
							propNet.getLegalInputMap().remove(p);
						}
					}
				}
				for(Set<Proposition> propositions : getGoalPropositions().values()) {
					propositions.remove(p);
				}
			}
			getPropositions().remove(p);
		}
		getComponents().remove(c);

		//Remove all the local links to the component
		for(Component parent : c.getInputs())
			parent.removeOutput(c);
		for(Component child : c.getOutputs())
			child.removeInput(c);
		//These are actually unnecessary...
		//c.removeAllInputs();
		//c.removeAllOutputs();
	}
}
