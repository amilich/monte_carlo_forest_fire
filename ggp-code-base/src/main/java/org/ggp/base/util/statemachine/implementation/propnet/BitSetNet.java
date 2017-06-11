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

public class BitSetNet extends StateMachine {
	/** The underlying proposition network  */
	private PropNet propNet;
	/** The topological ordering of the propositions */
	// private List<Proposition> ordering;
	/** The player roles */
	private Role roles[];

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
			if (currNode instanceof Proposition) {
				basesFound.add((Proposition) currNode);
			}
		}
	}

	public Set<Proposition> terminalDFS(Proposition t, Set<Proposition> goalLegals) {
		Set<Proposition> basesFound = new HashSet<Proposition>();
		dfs(t, basesFound);
		// propNet.renderToFile("ham.dot");
		for (Proposition p : goalLegals) {
			dfs(p, basesFound);
		}
		// System.out.println(basesFound);
		Set<Proposition> toIgnore = new HashSet<Proposition>();
		for (Proposition p : propNet.getAllBasePropositions()) {
			if (!basesFound.contains(p)) {
				// System.out.println("Ignore: " + p);
				toIgnore.add(p);
				propNet.removeComponent(p);
			}
		}
		System.out.println("Ignoring: " + toIgnore.size());
		return toIgnore;
	}

	Set<Proposition> ignoreBases;

	MachineState init;
	BitSet baseBits;
	BitSet inputBits;
	BitSet nextBaseBits;
	BitSet compBits;
	BitSet andBits;
	BitSet constBits;
	BitSet orBits;
	BitSet notBits;
	BitSet transBits;
	BitSet ignoreBits;
	public int counters[];

	public Component[] allCompArr = null;
	public Proposition[] allBaseArr = null;
	public Proposition[] allInputArr = null;

	/**
	 * Initializes the PropNetStateMachine. You should compute the topological
	 * ordering here. Additionally you may compute the initial state here, at
	 * your discretion.
	 */
	@Override
	public void initialize(List<Gdl> description, Role r) {
		// System.out.println("[PropNet] Initializing for role " + r);
		try {
			propNet = OptimizingPropNetFactory.create(description);
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);
			System.out.println("[PropNet] Initializing propnet with size " + propNet.getComponents().size());
			if (propNet.getComponents().size() < 5000) {
				Set<Proposition> startFrom = new HashSet<Proposition>();
				startFrom.addAll(propNet.getGoalPropositions().get(r));
				startFrom.addAll(propNet.getLegalPropositions().get(r));
				ignoreBases = terminalDFS(propNet.getTerminalProposition(), startFrom);
				if (ignoreBases.size() != 0) {
					propNet.roles.clear();
					propNet.roles.add(r);
					roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);
				}
			}

			allBaseArr = propNet.getAllBasePropositions().toArray(new Proposition[propNet.getAllBasePropositions().size()]);
			allInputArr = propNet.getAllInputProps().toArray(new Proposition[propNet.getAllInputProps().size()]);
			allCompArr = propNet.getComponents().toArray(new Component[propNet.getComponents().size()]);

			for (int ii = 0; ii < allBaseArr.length; ii ++) {
				allBaseArr[ii].intVal = ii;
				allBaseArr[ii].isBase = true;
			}
			for (int ii = 0; ii < allInputArr.length; ii ++) {
				allInputArr[ii].intVal = ii;
			}

			baseBits = new BitSet(allBaseArr.length);
			nextBaseBits = new BitSet(allBaseArr.length);
			inputBits = new BitSet(allInputArr.length);

			andBits = new BitSet(allCompArr.length);
			orBits = new BitSet(allCompArr.length);
			notBits = new BitSet(allCompArr.length);
			transBits = new BitSet(allCompArr.length);

			counters = new int[allCompArr.length];
			compBits = new BitSet(allCompArr.length);
			constBits = new BitSet(allCompArr.length);
			//			ignoreBits = new BitSet(allCompArr.length);

			for (int ii = 0; ii < allCompArr.length; ii ++) {
				allCompArr[ii].compIndex = ii;
				//				if (ignoreBases.contains(allCompArr[ii])) {
				//					ignoreBits.set(ii);
				//				}
				if (allCompArr[ii] instanceof And) {
					andBits.set(ii);
				} else if (allCompArr[ii] instanceof Or) {
					orBits.set(ii);
				} else if (allCompArr[ii] instanceof Not) {
					notBits.set(ii);
				} else if (allCompArr[ii] instanceof Transition) {
					transBits.set(ii);
				} else if (allCompArr[ii] instanceof Constant) {
					constBits.set(ii);
				}
			}

			for (Component c : propNet.getComponents()) {
				c.crystalize();
			}

			init = doInitWork();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		System.out.println("[PropNet] Initialization done [Role = " + r + "]");
	}

	private MachineState doInitWork() {
		for (int ii = constBits.nextSetBit(0); ii != -1; ii = constBits.nextSetBit(ii + 1)) {
			initforwardpropmark(allCompArr[ii], allCompArr[ii].getValue());
		}
		for (Proposition base : allBaseArr) {
			initforwardpropmark(base, false);
		}
		//		Random r = new Random();
		//		for (Proposition b : ignoreBases) {
		//			initforwardpropmark(b, r.nextBoolean());
		//		}
		//		for (Role r : roles) {
		//			for (Proposition p : propNet.getLegalPropositions().get(r)) {
		//				compBits.set(p.compIndex);
		//			}
		//		}

		if (propNet.getInitProposition() != null) {
			forwardpropmark(propNet.getInitProposition(), true);
		}
		Set<GdlSentence> sentences = new HashSet<GdlSentence>();
		for (Proposition base : allBaseArr) {
			if (compBits.get(base.getSingleInput().getSingleInput().compIndex)) {
				sentences.add(base.getName());
				nextBaseBits.set(base.intVal);
			}
			if (compBits.get(base.compIndex)) {
				baseBits.set(base.intVal);
			}
		}

		if (propNet.getInitProposition() != null) {
			initforwardpropmark(propNet.getInitProposition(), false);
		}

		for (Component c : propNet.getComponents()) {
			if (andBits.get(c.compIndex) || orBits.get(c.compIndex)) {
				counters[c.compIndex] = 0;
				for (int ii = 0; ii < c.input_arr.length; ii ++) {
					if (compBits.get(c.input_arr[ii].compIndex)) {
						counters[c.compIndex] ++;
					}
				}
			}
		}

		return new MachineState(sentences);
	}

	@Override
	public boolean isTerminal(MachineState state) {
		updatePropnetState(state);
		return compBits.get(propNet.getTerminalProposition().compIndex);
	}

	@Override
	public int getGoal(MachineState state, Role role)
			throws GoalDefinitionException {
		updatePropnetState(state);
		Set<Proposition> rewards = propNet.getGoalPropositions().get(role);

		for (Proposition p : rewards) {
			if (compBits.get(p.compIndex)) {
				if (p.goal == -1) {
					p.goal = Integer.parseInt(p.getName().get(1).toString());
				}
				return p.goal;
			}
		}
		return 0;
	}

	@Override
	public MachineState getInitialState() {
		return init;
	}

	/**
	 * Computes all possible actions for role.
	 */
	@Override
	public List<Move> findActions(Role role)
			throws MoveDefinitionException {
		List<Move> allMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			allMoves.add(new Move(p.getName().get(1)));
		}
		return allMoves;
	}

	/**
	 * Computes the legal moves for role in state.
	 */
	Map<Proposition, Move> propToMove = new HashMap<Proposition, Move>();
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
			throws MoveDefinitionException {
		updatePropnetState(state);
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		//		for (int ii = legalBits.nextSetBit(0); ii != -1; ii = legalBits.nextSetBit(ii + 1)) {
		//			Proposition p = allLegalArr[ii];
		//			if (legals.contains(p)) {
		//				Move m = propToMove.get(p);
		//				if (m == null) {
		//					m = new Move(p.getName().get(1));
		//					propToMove.put(p, m);
		//				}
		//				legalMoves.add(m);
		//			}
		//		}
		for (Proposition p : legals) {
			if (compBits.get(p.compIndex)) {
				Move m = propToMove.get(p);
				if (m == null) {
					m = new Move(p.getName().get(1));
					propToMove.put(p, m);
				}
				legalMoves.add(m);
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
		for (int ii = nextBaseBits.nextSetBit(0); ii != -1; ii = nextBaseBits.nextSetBit(ii + 1)) {
			newState.add(allBaseArr[ii].getName());
		}
		return new MachineState(newState);
	}

	public void initforwardpropmark(Component c, boolean newValue) {
		if (newValue) compBits.set(c.compIndex);
		else compBits.clear(c.compIndex);

		if (c.isBase) {
			if (newValue) baseBits.set(c.intVal);
			else baseBits.clear(c.intVal);
		} else if (transBits.get(c.compIndex)) { // if c is a transition
			// transitions always have exactly one output
			if (c.output_arr.length > 0) {
				if (newValue) nextBaseBits.set(c.output_arr[0].intVal);
				else nextBaseBits.clear(c.output_arr[0].intVal);
			}
			return;
		}
		for (int jj = 0; jj < c.output_arr.length; jj ++) {
			Component out = c.output_arr[jj];
			if (andBits.get(out.compIndex)) {
				if (!newValue) {
					initforwardpropmark(out, false);
				} else {
					boolean result = true;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (!compBits.get(out.input_arr[ii].compIndex)) {
							result = false;
							break;
						}
					}
					initforwardpropmark(out, result);
				}
			} else if (orBits.get(out.compIndex)) {
				if (newValue) {
					initforwardpropmark(out, true);
				} else {
					boolean result = false;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (compBits.get(out.input_arr[ii].compIndex)) {
							result = true;
							break;
						}
					}
					initforwardpropmark(out, result);
				}
			} else if (notBits.get(out.compIndex)) {
				initforwardpropmark(out, !newValue);
			} else {
				initforwardpropmark(out, newValue);
			}
		}
	}

	public void forwardpropmark(Component c, boolean newValue) {
		//		if (ignoreBases.contains(c)) {
		//			return;
		//		}
		if (newValue == compBits.get(c.compIndex)) {
			return; // stop forward propagating
		}
		if (newValue) compBits.set(c.compIndex);
		else compBits.clear(c.compIndex);

		if (c.isBase) {
			if (newValue) baseBits.set(c.intVal);
			else baseBits.clear(c.intVal);
		} else if (transBits.get(c.compIndex)) { // transitions always have exactly one output
			if (c.output_arr.length > 0) {
				if (newValue) nextBaseBits.set(c.output_arr[0].intVal);
				else nextBaseBits.clear(c.output_arr[0].intVal);
			}
			return;
		}
		for (int jj = 0; jj < c.output_arr.length; jj ++) {
			Component out = c.output_arr[jj];
			if (newValue) counters[out.compIndex] ++;
			else counters[out.compIndex] --;

			if (andBits.get(out.compIndex)) {
				forwardpropmark(out, newValue && counters[out.compIndex] == out.inputs.size()); // first newValue is just an optimization
			} else if (orBits.get(out.compIndex)) {
				forwardpropmark(out, newValue || counters[out.compIndex] > 0); // first newValue is just an optimization
			} else if (notBits.get(out.compIndex)) {
				forwardpropmark(out, !newValue);
			} else { // transition or proposition
				forwardpropmark(out, newValue);
			}
		}
	}

	// int counter = 0;
	public void updatePropnetState(MachineState state) {
		// counter ++;
		Set<GdlSentence> stateGdl = state.getContents();
		BitSet stateBits = new BitSet(allBaseArr.length);
		for (GdlSentence s : stateGdl) {
			Proposition p = propNet.getBasePropositions().get(s);
			if (p != null) {
				stateBits.set(p.intVal);
			}
		}
		stateBits.xor(baseBits);
		for (int ii = stateBits.nextSetBit(0); ii != -1; ii = stateBits.nextSetBit(ii + 1)) {
			/* if (counter > 100 && ignoreBits.get(ii)) {
				// baseBits.flip(ii);
				// compBits.flip(allBaseArr[ii].compIndex);
				// System.out.println("Skipping update of " + allBaseArr[ii]);
				continue;
			} // TODO bitset here */
			//			if (ignoreBits.get(ii)) {
			//				continue;
			//			}
			forwardpropmark(allBaseArr[ii], !baseBits.get(ii));
		}
	}

	public void updatePropnetMoves(List<Move> moves) {
		Set<GdlSentence> moveGdl = toDoes(moves);
		BitSet nowTrue = new BitSet(allInputArr.length);
		for (GdlSentence s : moveGdl) {
			nowTrue.set(propNet.getInputPropositions().get(s).intVal);
		}
		inputBits.xor(nowTrue);
		for (int ii = inputBits.nextSetBit(0); ii != -1; ii = inputBits.nextSetBit(ii + 1)) {
			forwardpropmark(allInputArr[ii], nowTrue.get(ii));
		}
		inputBits = nowTrue;
	}

	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return propNet.getRoles();
	}

	public List<Proposition> getOrdering() {
		// List to contain the topological ordering.
		List<Proposition> order = new ArrayList<Proposition>();

		// All of the components in the PropNet
		// List<Component> components = new ArrayList<Component>(propNet.getComponents());

		// All of the propositions in the PropNet.
		//		List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

		Queue<Proposition> allSources = new LinkedList<Proposition>();
		allSources.addAll(propNet.getAllInputProps());
		allSources.addAll(propNet.getAllBasePropositions());

		HashSet<Component> visitedNodes = new HashSet<Component>();

		while (!allSources.isEmpty()){
			Proposition front = allSources.poll();
			order.add(front);
			visitedNodes.add(front);
			for (Component c : front.getOutputs()){
				if (c instanceof Proposition){
					Set<Component> otherInputs = new HashSet<Component>(c.getInputs());
					otherInputs.removeAll(visitedNodes);
					if (otherInputs.isEmpty()){
						allSources.add((Proposition) c);
					}
				}
			}
		}
		// assert order.size() == propNet.getPropositions().size();
		order.addAll(allSources);
		return order;
	}

	/* Helper methods */

	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 *
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlSentences that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.
	 *
	 * @param moves
	 * @return
	 */
	Map<Role, Map<Move, GdlRelation>> moveToRelations = new HashMap<Role, Map<Move, GdlRelation>>();
	private Set<GdlSentence> toDoes(List<Move> moves) {
		Set<GdlSentence> doeses = new HashSet<GdlSentence>(moves.size());
		for (int ii = 0; ii < roles.length; ii ++) {
			Role r = roles[ii];
			Move m = moves.get(ii);
			if (moveToRelations.get(r) == null) {
				moveToRelations.put(r, new HashMap<Move, GdlRelation>());
			}
			GdlRelation relat = moveToRelations.get(r).get(m);
			if (relat != null) {
				doeses.add(relat);
			} else {
				GdlRelation n = ProverQueryBuilder.toDoes(r, m);
				doeses.add(n);
				moveToRelations.get(r).put(m, n);
			}
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
}