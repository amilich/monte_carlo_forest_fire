//package org.ggp.base.util.statemachine.implementation.propnet;
//
//import java.util.ArrayList;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import org.ggp.base.util.gdl.grammar.Gdl;
//import org.ggp.base.util.gdl.grammar.GdlConstant;
//import org.ggp.base.util.gdl.grammar.GdlRelation;
//import org.ggp.base.util.gdl.grammar.GdlSentence;
//import org.ggp.base.util.propnet.architecture.Component;
//import org.ggp.base.util.propnet.architecture.PropNet;
//import org.ggp.base.util.propnet.architecture.components.And;
//import org.ggp.base.util.propnet.architecture.components.Constant;
//import org.ggp.base.util.propnet.architecture.components.Not;
//import org.ggp.base.util.propnet.architecture.components.Or;
//import org.ggp.base.util.propnet.architecture.components.Proposition;
//import org.ggp.base.util.propnet.architecture.components.Transition;
//import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
//import org.ggp.base.util.statemachine.MachineState;
//import org.ggp.base.util.statemachine.Move;
//import org.ggp.base.util.statemachine.Role;
//import org.ggp.base.util.statemachine.StateMachine;
//import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
//import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
//import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
//import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;
//
//
//@SuppressWarnings("unused")
//public class WorkingPropNet extends StateMachine {
//    /** The underlying proposition network  */
//    private PropNet propNet;
//    /** The topological ordering of the propositions */
//    private List<Proposition> ordering;
//    /** The player roles */
//    private List<Role> roles;
//
//    public PropNet getPropnet() {
//    	return propNet;
//    }
//
//    /**
//     * Initializes the PropNetStateMachine. You should compute the topological
//     * ordering here. Additionally you may compute the initial state here, at
//     * your discretion.
//     */
//    @Override
//    public void initialize(List<Gdl> description) {
//        try {
//            propNet = OptimizingPropNetFactory.create(description);
//            roles = propNet.getRoles();
//            ordering = getOrdering();
//        } catch (InterruptedException e) {
//            throw new RuntimeException(e);
//        }
//        System.out.println("[PropNet] Initialization done");
//    }
//
//    public boolean propmarkconjunction(And a) {
//    	List<Component> inputs = a.getInputs();
//    	for (Component c : inputs) {
//    		if (!propmarkp(c)) return false;
//    	}
//    	return true;
//    }
//
//    public boolean propmarkdisjunction(Or a) {
//    	List<Component> inputs = a.getInputs();
//    	for (Component c : inputs) {
//    		if (propmarkp(c)) return true;
//    	}
//    	return false;
//    }
//
//    public boolean propmarknegation(Not a) {
//    	return !propmarkp(a.getSingleInput());
//    }
//
//    public boolean propmarkp(Component c) {
//    	if (propNet.getBasePropositions().containsValue(c)) {
//    		return ((Proposition) c).getValue();
//    	} else if (propNet.getInputPropositions().containsValue(c)) {
//    		return ((Proposition) c).getValue();
//    	} else if (propNet.getAllLegalPropositions().contains(c)) { // TODO
//    		return propmarkp(((Proposition) c).getSingleInput());
//    	} else if (c instanceof And) {
//    		return propmarkconjunction((And) c);
//    	} else if (c instanceof Or) {
//    		return propmarkdisjunction((Or) c);
//    	} else if (c instanceof Not) {
//    		return propmarknegation((Not) c);
//    	} else if (c instanceof Constant) {
//    		System.out.println("[PropNet] CONSTANT - error");
//    	} else if (c instanceof Transition){
//    		return propmarkp(c.getSingleInput());
//    	} else if (c.equals(propNet.getInitProposition())) {
//    		return c.getValue();
//    	} else if (c instanceof Proposition) {
//    		return propmarkp(c.getSingleInput());
//    	}
//    	System.out.println("UNKNOWN PROPOSITION: " + c);
//    	return false;
//    }
//
//    private void markbases(MachineState state) {
//    	clearpropnet();
//    	Set<GdlSentence> stateGdl = state.getContents();
//    	for (GdlSentence sentence : stateGdl) {
//    		propNet.getBasePropositions().get(sentence).setValue(true); // (sentence, new Proposition(sentence));
//    	}
//    }
//
//    private void markactions(List<Move> moves) {
//    	for (GdlSentence s : propNet.getInputPropositions().keySet()) {
//    		propNet.getInputPropositions().get(s).setValue(false);
//    	}
//    	List<GdlSentence> gdlSentences = toDoes(moves);
//    	for (GdlSentence sentence : gdlSentences) {
//    		propNet.getInputPropositions().get(sentence).setValue(true);
//    	}
//    }
//
//    private void clearpropnet() {
//    	for (GdlSentence s : propNet.getBasePropositions().keySet()) {
//    		propNet.getBasePropositions().get(s).setValue(false);
//    	}
//    }
//
//    /**
//     * Computes if the state is terminal. Should return the value
//     * of the terminal proposition for the state.
//     */
//    @Override
//    public boolean isTerminal(MachineState state) {
//    	markbases(state);
//        return propmarkp(propNet.getTerminalProposition());
//    }
//
//    /**
//     * Computes the goal for a role in the current state.
//     * Should return the value of the goal proposition that
//     * is true for that role. If there is not exactly one goal
//     * proposition true for that role, then you should throw a
//     * GoalDefinitionException because the goal is ill-defined.
//     */
//    @Override
//    public int getGoal(MachineState state, Role role)
//            throws GoalDefinitionException {
//    	markbases(state);
//    	List<Role> roles = propNet.getRoles();
//    	Set<Proposition> rewards = null;
//    	for (Role r : roles) {
//    		if (r.equals(role)) {
//    			rewards = propNet.getGoalPropositions().get(r);
//    		}
//    	}
//    	for (Proposition p : rewards) {
//    		if (propmarkp(p)) {
//    			// System.out.println(p.getName().get(0));
//    			// System.out.println(p.getName().get(1));
//    			// System.out.println(p);
//    			return Integer.parseInt(p.getName().get(1).toString());
//    		}
//    	}
//        return 0;
//    }
//
//    /**
//     * Returns the initial state. The initial state can be computed
//     * by only setting the truth value of the INIT proposition to true,
//     * and then computing the resulting state.
//     */
//    @Override
//    public MachineState getInitialState() {
//    	propNet.getInitProposition().setValue(true);
//    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
//    	Set<GdlSentence> sentences = new HashSet<GdlSentence>();
//    	for (Proposition base : bases.values()) {
//    		if (propmarkp(base.getSingleInput())) sentences.add(base.getName());
//    	}
//    	propNet.getInitProposition().setValue(false);
//        return new MachineState(sentences);
//    }
//
//    /**
//     * Computes all possible actions for role.
//     */
//    @Override
//    public List<Move> findActions(Role role)
//            throws MoveDefinitionException {
//        // TODO: Compute legal moves.
//        return null;
//    }
//
//    /**
//     * Computes the legal moves for role in state.
//     */
//    @Override
//    public List<Move> getLegalMoves(MachineState state, Role role)
//            throws MoveDefinitionException {
//    	markbases(state);
//    	List<Role> roles = propNet.getRoles();
//    	List<Move> legalMoves = new ArrayList<Move>();
//    	Set<Proposition> legals = null;
//    	for (Role r : roles) {
//    		if (r.equals(role)) legals = propNet.getLegalPropositions().get(r);
//    	}
//    	for (Proposition p : legals) {
//    		if (propmarkp(p)) {
//    			// System.out.println(p.getName().get(0));
//    			// System.out.println(p.getName().get(1));
//    			// System.out.println(p);
//    			legalMoves.add(new Move(p.getName().get(1)));
//    		}
//    	}
//        return legalMoves;
//    }
//
//    public boolean forwardpropmark(Component c) {
//    	//    	if (propNet.getBasePropositions().containsValue(c)) {
//    	//    		return ((Proposition) c).getValue();
//    	//    	} else if (propNet.getInputPropositions().containsValue(c)) {
//    	//    		return ((Proposition) c).getValue();
//    	//    	} else if (propNet.getAllLegalPropositions().contains(c)) { // TODO
//    	//    		return propmarkp(((Proposition) c).getSingleInput());
//    	//    	} else if (c instanceof And) {
//    	//    		return propmarkconjunction((And) c);
//    	//    	} else if (c instanceof Or) {
//    	//    		return propmarkdisjunction((Or) c);
//    	//    	} else if (c instanceof Not) {
//    	//    		return propmarknegation((Not) c);
//    	//    	} else if (c instanceof Constant) {
//    	//    		System.out.println("[PropNet] CONSTANT - error");
//    	//    	} else if (c instanceof Transition){
//    	//    		return propmarkp(c.getSingleInput());
//    	//    	} else if (c.equals(propNet.getInitProposition())) {
//    	//    		return c.getValue();
//    	//    	} else if (c instanceof Proposition) {
//    	//    		return propmarkp(c.getSingleInput());
//    	//    	}
//    	//    	System.out.println("UNKNOWN PROPOSITION: " + c);
//    	//    	return false;
//    	if (c instanceof And) {
//    		return propmarkconjunction((And) c);
//    	} else if (c instanceof Or) {
//    		return propmarkdisjunction((Or) c);
//    	} else if (c instanceof Not) {
//    		return propmarknegation((Not) c);
//    	}
//    	return false;
//    }
//
//    /**
//     * Computes the next state given state and the list of moves.
//     */
//    @Override
//    public MachineState getNextState(MachineState state, List<Move> moves)
//            throws TransitionDefinitionException {
//    	markactions(moves);
//    	markbases(state);
//    	Map<GdlSentence, Proposition> bases = propNet.getBasePropositions();
//    	Set<GdlSentence> sentences = new HashSet<GdlSentence>();
//    	for (Proposition base : bases.values()) {
//    		if (propmarkp(base.getSingleInput().getSingleInput())) sentences.add(base.getName());
//    	}
//        return new MachineState(sentences);
//    }
//
//    /**
//     * This should compute the topological ordering of propositions.
//     * Each component is either a proposition, logical gate, or transition.
//     * Logical gates and transitions only have propositions as inputs.
//     *
//     * The base propositions and input propositions should always be exempt
//     * from this ordering.
//     *
//     * The base propositions values are set from the MachineState that
//     * operations are performed on and the input propositions are set from
//     * the Moves that operations are performed on as well (if any).
//     *
//     * @return The order in which the truth values of propositions need to be set.
//     */
//    public List<Proposition> getOrdering()
//    {
//        // List to contain the topological ordering.
//        List<Proposition> order = new LinkedList<Proposition>();
//
//        // All of the components in the PropNet
//        List<Component> components = new ArrayList<Component>(propNet.getComponents());
//
//        // All of the propositions in the PropNet.
//        List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());
//
//        // TODO: Compute the topological ordering.
//
//        return order;
//    }
//
//    /* Already implemented for you */
//    @Override
//    public List<Role> getRoles() {
//        return roles;
//    }
//
//    /* Helper methods */
//
//    /**
//     * The Input propositions are indexed by (does ?player ?action).
//     *
//     * This translates a list of Moves (backed by a sentence that is simply ?action)
//     * into GdlSentences that can be used to get Propositions from inputPropositions.
//     * and accordingly set their values etc.  This is a naive implementation when coupled with
//     * setting input values, feel free to change this for a more efficient implementation.
//     *
//     * @param moves
//     * @return
//     */
//    private List<GdlSentence> toDoes(List<Move> moves) {
//        List<GdlSentence> doeses = new ArrayList<GdlSentence>(moves.size());
//        Map<Role, Integer> roleIndices = getRoleIndices();
//
//        for (int i = 0; i < roles.size(); i++) {
//            int index = roleIndices.get(roles.get(i));
//            doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
//        }
//        return doeses;
//    }
//
//    /**
//     * Takes in a Legal Proposition and returns the appropriate corresponding Move
//     * @param p
//     * @return a PropNetMove
//     */
//    public static Move getMoveFromProposition(Proposition p) {
//        return new Move(p.getName().get(1));
//    }
//
//    /**
//     * Helper method for parsing the value of a goal proposition
//     * @param goalProposition
//     * @return the integer value of the goal proposition
//     */
//    private int getGoalValue(Proposition goalProposition) {
//        GdlRelation relation = (GdlRelation) goalProposition.getName();
//        GdlConstant constant = (GdlConstant) relation.get(1);
//        return Integer.parseInt(constant.toString());
//    }
//
//    /**
//     * A Naive implementation that computes a PropNetMachineState
//     * from the true BasePropositions.  This is correct but slower than more advanced implementations
//     * You need not use this method!
//     * @return PropNetMachineState
//     */
//    public MachineState getStateFromBase() {
//        Set<GdlSentence> contents = new HashSet<GdlSentence>();
//        for (Proposition p : propNet.getBasePropositions().values()) {
//            p.setValue(p.getSingleInput().getValue());
//            if (p.getValue()) {
//                contents.add(p.getName());
//            }
//
//        }
//        return new MachineState(contents);
//    }
//}