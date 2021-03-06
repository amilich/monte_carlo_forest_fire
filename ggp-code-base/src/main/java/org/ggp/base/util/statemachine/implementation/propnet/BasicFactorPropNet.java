package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlConstant;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.propnet.architecture.Component;
import org.ggp.base.util.propnet.architecture.PropNet;
import org.ggp.base.util.propnet.architecture.components.And;
import org.ggp.base.util.propnet.architecture.components.Constant;
import org.ggp.base.util.propnet.architecture.components.Not;
import org.ggp.base.util.propnet.architecture.components.Or;
import org.ggp.base.util.propnet.architecture.components.Proposition;
import org.ggp.base.util.propnet.architecture.components.Transition;
import org.ggp.base.util.propnet.factory.OptimizingPropNetFactory;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.query.ProverQueryBuilder;


@SuppressWarnings("unused")
public class BasicFactorPropNet extends StateMachine {
	/** The underlying proposition network  */
	private PropNet propNet;
	/** The topological ordering of the propositions */
	private List<Proposition> ordering;
	/** The player roles */
	private List<Role> roles;

	private List<GdlSentence> gdlOrder = new ArrayList<GdlSentence>();

	public PropNet getPropnet() {
		return propNet;
	}

	public void dfs(Proposition p, Set<Proposition> basesFound) {
		Queue<Component> nodesToVisit = new LinkedList<Component>();
		Set<Component> visited = new HashSet<Component>();
		nodesToVisit.add(p);
		while (!nodesToVisit.isEmpty()) {
			Component currNode = nodesToVisit.poll();
			if (visited.contains(currNode)) continue;
			else visited.add(currNode);
			nodesToVisit.addAll(currNode.inputs);
			if (propNet.getAllBasePropositions().contains(currNode)) {
				basesFound.add((Proposition) currNode);
			}
		}
	}

	public void mergeddfs(Proposition p, Set<Proposition> basesFound, Map<Proposition, Set<Proposition>> allGames) {
		Queue<Component> nodesToVisit = new LinkedList<Component>();
		Set<Component> visited = new HashSet<Component>();
		nodesToVisit.add(p);
		List<Proposition> potentialKeys = new ArrayList<Proposition>();
		while (!nodesToVisit.isEmpty()) {
			Component currNode = nodesToVisit.poll();
			if (visited.contains(currNode)) continue;
			else visited.add(currNode);
			nodesToVisit.addAll(currNode.inputs);
			if (propNet.getAllBasePropositions().contains(currNode)) {
				basesFound.add((Proposition) currNode);
				for (Proposition key : allGames.keySet()) {
					if (allGames.get(key).contains((Proposition) currNode)) {
						potentialKeys.add(key);
					}
				}
			}
		}
		if (potentialKeys.size() == 0) {
			allGames.put(p, basesFound);
		} else {
			for (Proposition key : potentialKeys) {
				basesFound.addAll(allGames.get(key));
				if (propNet.getAllBasePropositions().contains(key)) {
					basesFound.add(key);
				}
			}
			for (Proposition key : potentialKeys) {
				allGames.remove(key);
			}
			allGames.put(p, basesFound);
		}
	}

	public double getNumSteps(Set<Proposition> propositions) {
		double maxStep = -1;
		for (Proposition p : propositions) {
			if (p.toString().contains("step")) {
				int stepNum = Integer.parseInt(p.getName().getBody().get(1).toString());
				if (stepNum > maxStep) maxStep = stepNum;
			}
		}
		return maxStep;
	}

	// ASSUMING ONE PLAYER
	public Proposition findBestGoal(Set<Proposition> goals) {
		int maxGoal = -1;
		Proposition f = null;
		Set<Proposition> rewards = propNet.getGoalPropositions().get(propNet.getRoles().get(0));
		for (Proposition p : rewards) {
			int val = Integer.parseInt(p.getName().get(1).toString());
			if (val > maxGoal) {
				maxGoal = val;
				f = p;
			}
		}
		return f;
	}

	public Set<Proposition> andOrDfs(Proposition p) {
		Queue<Component> nodesToVisit = new LinkedList<Component>();
		Set<Component> visited = new HashSet<Component>();
		Set<Proposition> basesFound = new HashSet<Proposition>();
		nodesToVisit.add(p);
		while (!nodesToVisit.isEmpty()) {
			Component currNode = nodesToVisit.poll();
			if (visited.contains(currNode)) continue;
			else visited.add(currNode);

			if (currNode instanceof Proposition && currNode != p) {
				basesFound.add((Proposition) currNode);
			} else {
				nodesToVisit.addAll(currNode.inputs);
			}
		}
		if (basesFound.size() == 0) {
			basesFound.add(p);
		}
		return basesFound;
	}

	public Set<Proposition> factorSubgames(Role r) {
		Proposition term = propNet.getTerminalProposition();
		Set<Proposition> gameRoots = new HashSet<Proposition>();
		gameRoots.addAll(andOrDfs(term));
		for (Proposition p : propNet.getAllGoalPropositions()) {
			gameRoots.addAll(andOrDfs(p));
		}

		System.out.println("Game Roots: " + gameRoots);
		Map<Proposition, Set<Proposition>> allGames = new HashMap<Proposition, Set<Proposition>>();
		for (Proposition p : gameRoots) {
			Set<Proposition> game = new HashSet<Proposition>();
			mergeddfs(p, game, allGames);
		}
		System.out.println("There are " + allGames.size() + " games. Roots = " + allGames.keySet() + ".");
		// Proposition bestGoal = findBestGoal(propNet.getAllGoalPropositions());
		// Set<Proposition> goalRoots = new HashSet<Proposition>();
		// andOrDfs(bestGoal, goalRoots);
		// System.out.println("Best goal: " + bestGoal);

		/* Proposition root = goalRoots.iterator().next();
		for (Proposition key : allGames.keySet()) {
			if (allGames.get(key).contains(root) || root == key) {
				System.out.println("Factored game to key " + key);
				return allGames.get(key);
			}
		}*/
		Set<Proposition> myGoals = propNet.getGoalPropositions().get(r);

		propNet.renderToFile("dual.dot");
		// return allGames.values().iterator().next();
		return null;
	}

	public Set<Proposition> terminalDFS(Proposition t, Set<Proposition> goals) {
		Set<Proposition> basesFound = new HashSet<Proposition>();
		dfs(t, basesFound);
		for (Proposition p : goals) {
			dfs(p, basesFound);
		}
		System.out.println(basesFound);
		Set<Proposition> ignoreBases = new HashSet<Proposition>();
		for (Proposition p : propNet.getAllBasePropositions()) {
			if (!basesFound.contains(p)) {
				ignoreBases.add(p);
			}
		}
		System.out.println(ignoreBases);
		return ignoreBases;
	}

	Set<Proposition> ignoreBases;

	@Override
	public void initialize(List<Gdl> description, Role r) {
		try {
			propNet = OptimizingPropNetFactory.create(description);
			roles = propNet.getRoles();
			ordering = getOrdering();

			Set<Proposition> factoredBases = factorSubgames(r);
			// allBaseArr = factoredBases.toArray(new Proposition[factoredBases.size()]);

			// ignoreBases = terminalDFS(propNet.getTerminalProposition(), propNet.getAllGoalPropositions());
			// allBaseArr = importantBases.toArray(new Proposition[importantBases.size()]);
			allBaseArr = propNet.getAllBasePropositions().toArray(new Proposition[propNet.getAllBasePropositions().size()]);
			allInputArr = propNet.getAllInputProps().toArray(new Proposition[propNet.getAllInputProps().size()]);
			for (Component c : propNet.getComponents()) {
				c.crystalize();
			}

			doInitWork();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		System.out.println("[PropNet] Initialization done");
	}

	public Proposition[] allBaseArr = null;
	public Proposition[] allInputArr = null;

	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		updatePropnetState(state);
		// return propNet.getTerminalProposition().getValue();
		return propNet.getTerminalProposition().curVal;
	}

	/**
	 * Computes the goal for a role in the current state.
	 * Should return the value of the goal proposition that
	 * is true for that role. If there is not exactly one goal
	 * proposition true for that role, then you should throw a
	 * GoalDefinitionException because the goal is ill-defined.
	 */
	@Override
	public int getGoal(MachineState state, Role role)
			throws GoalDefinitionException {
		updatePropnetState(state);

		List<Role> roles = propNet.getRoles();
		Set<Proposition> rewards = propNet.getGoalPropositions().get(role);
		for (Proposition p : rewards) {
			if (p.curVal) {
				return Integer.parseInt(p.getName().get(1).toString());
			}
		}
		return 0;
	}

	private MachineState doInitWork() {
		for (Component p : propNet.getComponents()) {
			p.curVal = false;
		}

		for (Component c : propNet.getComponents()) {
			if (c instanceof Constant) {
				forwardpropmark(c, c.getValue(), false);
			}
		}

		for (int ii = 0; ii < ordering.size(); ii ++) {
			forwardpropmark(ordering.get(ii), ordering.get(ii).curVal, false);
		}

		if (propNet.getInitProposition() != null) {
			forwardpropmark(propNet.getInitProposition(), true, true);
		}

		Set<Proposition> bases = propNet.getAllBasePropositions();
		Set<GdlSentence> sentences = new HashSet<GdlSentence>();
		for (Proposition base : bases) {
			if (base.getSingleInput().getSingleInput().curVal) {
				sentences.add(base.getName());
			}
		}
		trueProps.clear();
		trueProps.addAll(sentences);

		if (propNet.getInitProposition() != null) {
			forwardpropmark(propNet.getInitProposition(), false, false);
		}

		return new MachineState(sentences);
	}

	BitSet baseBits = new BitSet();
	Set<GdlSentence> trueProps = new HashSet<GdlSentence>();

	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
	@Override
	public MachineState getInitialState() {
		return doInitWork();
	}

	/**
	 * Computes all possible actions for role.
	 */
	@Override
	public List<Move> findActions(Role role)
			throws MoveDefinitionException {
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			legalMoves.add(new Move(p.getName().get(1)));
		}
		return legalMoves;
	}

	/**
	 * Computes the legal moves for role in state.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
			throws MoveDefinitionException {
		updatePropnetState(state);
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			if (p.curVal) {
				legalMoves.add(new Move(p.getName().get(1)));
			}
		}
		return legalMoves;
	}

	/**
	 * Computes the next state given state and the list of moves.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
			throws TransitionDefinitionException {
		updatePropnetState(state);
		updatePropnetMoves(moves);
		Set<GdlSentence> newState = new HashSet<GdlSentence>();
		newState.addAll(trueProps);
		return new MachineState(newState);
	}

	public void forwardpropmark(Component c, boolean newValue, boolean differential) {
		if (newValue == c.curVal && differential) {
			return; // stop forward propagating
		}
		c.curVal = newValue;
		if (c instanceof Transition) {
			Proposition o = (Proposition) c.getSingleOutput();
			if (newValue) trueProps.add(o.getName());
			else trueProps.remove(o.getName());
			return;
		}
		// List<Component> outputs = c.getOutputs();
		for (int jj = 0; jj < c.output_arr.length; jj ++) {
			Component out = c.output_arr[jj];
			if (out instanceof Proposition || out instanceof Transition) {
				forwardpropmark(out, newValue, differential);
			} else if (out instanceof And) {
				if (!newValue) {
					forwardpropmark(out, false, differential);
				} else {
					boolean result = true;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (!out.input_arr[ii].curVal) {
							result = false;
							break;
						}
					}
					forwardpropmark(out, result, differential);
				}
			} else if (out instanceof Or) {
				if (newValue) {
					forwardpropmark(out, true, differential);
				} else {
					boolean result = false;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (out.input_arr[ii].curVal) {
							result = true;
							break;
						}
					}
					forwardpropmark(out, result, differential);
				}
			} else if (out instanceof Not) {
				boolean result = !newValue;
				forwardpropmark(out, result, differential);
			}
		}
	}

	public void updatePropnetState(MachineState state) {
		Set<GdlSentence> stateGdl = state.getContents();
		for (int ii = 0; ii < allBaseArr.length; ii ++) {
			boolean contains = stateGdl.contains(allBaseArr[ii].getName());
			if (allBaseArr[ii].curVal != contains) {
				forwardpropmark(allBaseArr[ii], contains, true);
			}
		}
	}

	public void updatePropnetMoves(List<Move> moves) {
		Set<GdlSentence> moveGdl = toDoes(moves); // new HashSet<GdlSentence>(toDoes(moves));
		for (int ii = 0; ii < allInputArr.length; ii ++) {
			boolean contains = moveGdl.contains(allInputArr[ii].getName());
			if (allInputArr[ii].curVal != contains) {
				forwardpropmark(allInputArr[ii], contains, true);
			}
		}
	}

	/**
	 * This should compute the topological ordering of propositions.
	 * Each component is either a proposition, logical gate, or transition.
	 * Logical gates and transitions only have propositions as inputs.
	 *
	 * The base propositions and input propositions should always be exempt
	 * from this ordering.
	 *
	 * The base propositions values are set from the MachineState that
	 * operations are performed on and the input propositions are set from
	 * the Moves that operations are performed on as well (if any).
	 *
	 * @return The order in which the truth values of propositions need to be set.
	 */
	public List<Proposition> getOrdering() {
		// List to contain the topological ordering.
		List<Proposition> order = new ArrayList<Proposition>();

		// All of the components in the PropNet
		// List<Component> components = new ArrayList<Component>(propNet.getComponents());

		// All of the propositions in the PropNet.
		List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

		Queue<Proposition> allSources = new LinkedList<Proposition>();
		allSources.addAll(propNet.getAllInputProps());
		allSources.addAll(propNet.getAllBasePropositions());

		HashSet<Component> visitedNodes = new HashSet<Component>();

//		while (!allSources.isEmpty()){
//			Proposition front = allSources.poll();
//			order.add(front);
//			visitedNodes.add(front);
//			for (Component c : front.getOutputs()){
//				if (c instanceof Proposition){
//					Set<Component> otherInputs = new HashSet<Component>(c.getInputs());
//					otherInputs.removeAll(visitedNodes);
//					if (otherInputs.isEmpty()){
//						allSources.add((Proposition) c);
//					}
//				}
//			}
//		}
		// assert order.size() == propNet.getPropositions().size();
		order.addAll(allSources);
		return order;
	}

	// Test if topological ordering worked. Not supposed to be called at runtime
	/*public void testTopologicalOrdering(List<Proposition> ordering){
		for(int i=1; i < ordering.size(); i++){
			System.out.println(i);
			HashSet<Proposition> prev = new HashSet<Proposition>(ordering.subList(0, i));
			Set<Component> inputs_c = ordering.get(i).getInputs();
			HashSet<Proposition> inputs = new HashSet<Proposition>();
			for (Component c : inputs_c){
				if (c instanceof Proposition){
					inputs.add((Proposition) c);
				}
			}
			inputs.removeAll(prev);
			if (!inputs.isEmpty()){
				throw new Error("Ordering is not topological");
			}
		}
	}*/

	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return roles;
	}

	/* Helper methods */

	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 *
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlSentences that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.  This is a naive implementation when coupled with
	 * setting input values, feel free to change this for a more efficient implementation.
	 *
	 * @param moves
	 * @return
	 */
	private Set<GdlSentence> toDoes(List<Move> moves) {
		Set<GdlSentence> doeses = new HashSet<GdlSentence>(moves.size());
		Map<Role, Integer> roleIndices = getRoleIndices();

		for (int i = 0; i < roles.size(); i++) {
			int index = roleIndices.get(roles.get(i));
			doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)));
		}
		return doeses;
	}

	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */
	public static Move getMoveFromProposition(Proposition p) {
		return new Move(p.getName().get(1));
	}

	/**
	 * Helper method for parsing the value of a goal proposition
	 * @param goalProposition
	 * @return the integer value of the goal proposition
	 */
	private int getGoalValue(Proposition goalProposition) {
		GdlRelation relation = (GdlRelation) goalProposition.getName();
		GdlConstant constant = (GdlConstant) relation.get(1);
		return Integer.parseInt(constant.toString());
	}

	/**
	 * A Naive implementation that computes a PropNetMachineState
	 * from the true BasePropositions.  This is correct but slower than more advanced implementations
	 * You need not use this method!
	 * @return PropNetMachineState
	 */
	public MachineState getStateFromBase() {
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for (Proposition p : propNet.getBasePropositions().values()) {
			p.setValue(p.getSingleInput().getValue());
			if (p.getValue()) {
				contents.add(p.getName());
			}

		}
		return new MachineState(contents);
	}
}