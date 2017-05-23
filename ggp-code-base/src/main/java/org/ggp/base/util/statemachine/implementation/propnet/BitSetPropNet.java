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

public class BitSetPropNet extends StateMachine {
	/** The underlying proposition network  */
	private PropNet propNet;
	/** The topological ordering of the propositions */
	// private List<Proposition> ordering;
	/** The player roles */
	private Role roles[];

	public PropNet getPropnet() {
		return propNet;
	}

	MachineState init;
	BitSet baseBits; // = new BitSet();
	BitSet inputBits;
	BitSet legalBits;
	BitSet nextBaseBits;

	// BitSet propBits;
	BitSet compBits;

	public Proposition[] allBaseArr = null;
	public Proposition[] allInputArr = null;
	public Proposition[] allLegalArr = null;
	// Map<Integer, Proposition> baseBitMap = new HashMap<Integer, Proposition>();

	/**
	 * Initializes the PropNetStateMachine. You should compute the topological
	 * ordering here. Additionally you may compute the initial state here, at
	 * your discretion.
	 */
	@Override
	public void initialize(List<Gdl> description, Role r) {
		System.out.println("[PropNet] Initializing for role " + r);
		try {
			propNet = OptimizingPropNetFactory.create(description);
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);

			allBaseArr = propNet.getAllBasePropositions().toArray(new Proposition[propNet.getAllBasePropositions().size()]);
			allInputArr = propNet.getAllInputProps().toArray(new Proposition[propNet.getAllInputProps().size()]);
			allLegalArr = propNet.getAllLegalPropositions().toArray(
					new Proposition[propNet.getAllLegalPropositions().size()]);
			for (int ii = 0; ii < allBaseArr.length; ii ++) {
				allBaseArr[ii].bitIndex = ii;
				allBaseArr[ii].isBase = true;
			}
			for (int ii = 0; ii < allLegalArr.length; ii ++) {
				allLegalArr[ii].bitIndex = ii;
				allLegalArr[ii].isLegal = true;
			}
			for (int ii = 0; ii < allInputArr.length; ii ++) {
				allInputArr[ii].bitIndex = ii;
			}

			baseBits = new BitSet(allBaseArr.length);
			nextBaseBits = new BitSet(allBaseArr.length);
			inputBits = new BitSet(allInputArr.length);
			legalBits = new BitSet(allLegalArr.length);

			for (Component c : propNet.getComponents()) {
				c.crystalize();
			}

			init = doInitWork();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		System.out.println("[PropNet] Initialization done");
	}

	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		updatePropnetState(state);
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
		Set<Proposition> rewards = propNet.getGoalPropositions().get(role);

		for (Proposition p : rewards) {
			if (p.curVal) {
				if (p.goal == -1) {
					p.goal = Integer.parseInt(p.getName().get(1).toString());
				}
				return p.goal;
			}
		}
		return 0;
	}

	private MachineState doInitWork() {
		for (Component c : propNet.getComponents()) {
			if (c instanceof Constant) {
				forwardpropmark(c, c.getValue(), false);
			}
		}
		Set<Proposition> bases = propNet.getAllBasePropositions();

		/* for (int ii = 0; ii < ordering.size(); ii ++) {
			forwardpropmark(ordering.get(ii), ordering.get(ii).curVal, false);
		}*/
		for (Proposition base : bases) {
			forwardpropmark(base, false, false);
		}

		if (propNet.getInitProposition() != null) {
			forwardpropmark(propNet.getInitProposition(), true, true);
		}

		Set<GdlSentence> sentences = new HashSet<GdlSentence>();
		for (Proposition base : bases) {
			if (base.getSingleInput().getSingleInput().curVal) {
				sentences.add(base.getName());
				nextBaseBits.set(base.bitIndex);
			}
			if (base.curVal) {
				baseBits.set(base.bitIndex);
			}
		}

		for (Proposition p : propNet.getAllLegalPropositions()) {
			if (p.curVal) {
				legalBits.set(p.bitIndex);
			}
		}

		if (propNet.getInitProposition() != null) {
			forwardpropmark(propNet.getInitProposition(), false, false);
		}

		for (Component c : propNet.getComponents()) {
			if (c instanceof And || c instanceof Or) {
				c.numTrue = 0;
				for (int ii = 0; ii < c.inputs.size(); ii ++) {
					if (c.input_arr[ii].curVal) c.numTrue ++;
				}
			}
		}

		return new MachineState(sentences);
	}

	Set<Proposition> trueLegals = new HashSet<Proposition>();

	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
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
			if (p.curVal) {
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

	public void forwardpropmark(Component c, boolean newValue, boolean differential) {
		if (newValue == c.curVal && differential) {
			return; // stop forward propagating
		}
		c.curVal = newValue;

		if (c.isBase) {
			if (newValue) baseBits.set(c.bitIndex);
			else baseBits.clear(c.bitIndex);
		}
		/* else if (c.isLegal) {
			if (newValue) legalBits.set(c.bitIndex);
			else legalBits.clear(c.bitIndex);
		} */
		if (c instanceof Transition) {
			if (newValue) nextBaseBits.set(c.output_arr[0].bitIndex);
			else nextBaseBits.clear(c.output_arr[0].bitIndex);
			return;
		}
		for (int jj = 0; jj < c.outputs.size(); jj ++) {
			Component out = c.output_arr[jj];
			if (differential) {
				if (newValue) out.numTrue ++;
				else out.numTrue --;
			}
			if (out instanceof Proposition || out instanceof Transition) {
				forwardpropmark(out, newValue, differential);
			} else if (out instanceof And) {
				if (!newValue) {
					forwardpropmark(out, false, differential);
				} else if (differential) {
					forwardpropmark(out, out.numTrue == out.inputs.size(), differential);
				} else {
					boolean result = ((And) out).getValue();
					forwardpropmark(out, result, differential);
				}
			} else if (out instanceof Or) {
				if (newValue) {
					forwardpropmark(out, true, differential);
				} else if (differential) {
					forwardpropmark(out, out.numTrue > 0, differential);
				} else {
					boolean result = ((Or) out).getValue();
					forwardpropmark(out, result, differential);
				}
			} else if (out instanceof Not) {
				forwardpropmark(out, !newValue, differential);
			}
		}
	}

	public void updatePropnetState(MachineState state) {
		Set<GdlSentence> stateGdl = state.getContents();
		BitSet stateBits = new BitSet(allBaseArr.length);
		for (GdlSentence s : stateGdl) {
			stateBits.set(propNet.getBasePropositions().get(s).bitIndex);
		}
		stateBits.xor(baseBits);
		for (int ii = stateBits.nextSetBit(0); ii != -1; ii = stateBits.nextSetBit(ii + 1)) {
			forwardpropmark(allBaseArr[ii], !baseBits.get(ii), true);
		}
	}

	public void updatePropnetMoves(List<Move> moves) {
		Set<GdlSentence> moveGdl = toDoes(moves);
		BitSet nowTrue = new BitSet(allInputArr.length);
		for (GdlSentence s : moveGdl) {
			nowTrue.set(propNet.getInputPropositions().get(s).bitIndex);
		}
		inputBits.xor(nowTrue);
		for (int ii = inputBits.nextSetBit(0); ii != -1; ii = inputBits.nextSetBit(ii + 1)) {
			forwardpropmark(allInputArr[ii], nowTrue.get(ii), true);
		}
		inputBits = nowTrue;
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
//		List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

		Queue<Proposition> allSources = new LinkedList<Proposition>();
		allSources.addAll(propNet.getAllInputProps());
		allSources.addAll(propNet.getAllBasePropositions());

//		HashSet<Component> visitedNodes = new HashSet<Component>();

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
		return propNet.getRoles();
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