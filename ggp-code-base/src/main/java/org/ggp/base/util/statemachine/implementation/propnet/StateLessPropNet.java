package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

public class StateLessPropNet extends StateMachine {
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
	BitSet baseBits;
	BitSet inputBits;
	BitSet nextBaseBits;
	BitSet compBits;
	BitSet andBits;
	BitSet constBits;
	BitSet orBits;
	BitSet notBits;
	BitSet transBits;
	BitSet basePropBits; // true if index i is a base prop
	public int counters[];

	public Component[] allCompArr = null;
	public Proposition[] allBaseArr = null;
	public Proposition[] allInputArr = null;
	public int outputs[][];
	public int inputs[][];

	/**
	 * Initializes the PropNetStateMachine. You should compute the topological
	 * ordering here. Additionally you may compute the initial state here, at
	 * your discretion.
	 */
	@Override
	public void initialize(List<Gdl> description, Role r) {
		try {
			propNet = OptimizingPropNetFactory.create(description);
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);

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

			outputs = new int[allCompArr.length][];
			inputs = new int[allCompArr.length][];

			for (int ii = 0; ii < allCompArr.length; ii ++) {
				allCompArr[ii].compIndex = ii;
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
				outputs[ii] = new int[allCompArr[ii].outputs.size()];
				inputs[ii] = new int[allCompArr[ii].inputs.size()];
			}
			for (int ii = 0; ii < allCompArr.length; ii ++) {
				for (int jj = 0; jj < allCompArr[ii].outputs.size(); jj ++) {
					outputs[ii][jj] = allCompArr[ii].output_arr[jj].compIndex;
				}
				for (int jj = 0; jj < allCompArr[ii].inputs.size(); jj ++) {
					inputs[ii][jj] = allCompArr[ii].input_arr[jj].compIndex;
				}
			}
			basePropBits = new BitSet(allCompArr.length);
			for (Proposition p : allBaseArr) {
				basePropBits.set(p.compIndex);
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
		return compBits.get(propNet.getTerminalProposition().compIndex);
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
			if (compBits.get(p.compIndex)) {
				if (p.goal == -1) {
					p.goal = Integer.parseInt(p.getName().get(1).toString());
				}
				return p.goal;
			}
		}
		return 0;
	}

	private MachineState doInitWork() {
		for (int ii = constBits.nextSetBit(0); ii != -1; ii = constBits.nextSetBit(ii + 1)) {
			forwardpropmark(ii, allCompArr[ii].getValue(), false);
		}
		for (Proposition base : allBaseArr) {
			forwardpropmark(base.compIndex, false, false);
		}
		if (propNet.getInitProposition() != null) {
			forwardpropmark(propNet.getInitProposition().compIndex, true, true);
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
			forwardpropmark(propNet.getInitProposition().compIndex, false, false);
		}

		for (Component c : propNet.getComponents()) {
			if (andBits.get(c.compIndex) || orBits.get(c.compIndex)) {
				counters[c.compIndex] = 0;
				for (int ii = 0; ii < inputs[c.compIndex].length; ii ++) {
					if (compBits.get(inputs[c.compIndex][ii])) {
						counters[c.compIndex] ++;
					}
				}
			}
		}

		return new MachineState(sentences);
	}

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

	public void forwardpropmark(int c, boolean newValue, boolean differential) {
		if (newValue == compBits.get(c) && differential) {
			return; // stop forward propagating
		}
		if (newValue) compBits.set(c);
		else compBits.clear(c);

		if (basePropBits.get(c)) {
			if (newValue) baseBits.set(allCompArr[c].intVal);
			else baseBits.clear(allCompArr[c].intVal);
		} else if (transBits.get(c)) { // if c is a transition
			// transitions always have exactly one output
			if (newValue) nextBaseBits.set(allCompArr[outputs[c][0]].intVal);
			else nextBaseBits.clear(allCompArr[outputs[c][0]].intVal);
			return;
		}
		for (int jj = 0; jj < outputs[c].length; jj ++) {
			int out_ind = outputs[c][jj];
			if (differential) {
				if (newValue) counters[out_ind] ++;
				else counters[out_ind] --;
			}
			if (andBits.get(out_ind)) {
				if (!newValue) {
					forwardpropmark(out_ind, false, differential);
				} else if (differential) {
					forwardpropmark(out_ind, counters[out_ind] == inputs[out_ind].length, differential);
				} else {
					boolean result = true;
					for (int ii = 0; ii < inputs[out_ind].length; ii ++) {
						if (!compBits.get(inputs[out_ind][ii])) {
							result = false;
							break;
						}
					}
					forwardpropmark(out_ind, result, differential);
				}
			} else if (orBits.get(out_ind)) {
				if (newValue) {
					forwardpropmark(out_ind, true, differential);
				} else if (differential) {
					forwardpropmark(out_ind, counters[out_ind] > 0, differential);
				} else {
					boolean result = false;
					for (int ii = 0; ii < inputs[out_ind].length; ii ++) {
						if (compBits.get(inputs[out_ind][ii])) {
							result = true;
							break;
						}
					}
					forwardpropmark(out_ind, result, differential);
				}
			} else if (notBits.get(out_ind)) {
				forwardpropmark(out_ind, !newValue, differential);
			} else {
				forwardpropmark(out_ind, newValue, differential);
			}
		}
	}

	public void updatePropnetState(MachineState state) {
		Set<GdlSentence> stateGdl = state.getContents();
		BitSet stateBits = new BitSet(allBaseArr.length);
		for (GdlSentence s : stateGdl) {
			stateBits.set(propNet.getBasePropositions().get(s).intVal);
		}
		stateBits.xor(baseBits);
		for (int ii = stateBits.nextSetBit(0); ii != -1; ii = stateBits.nextSetBit(ii + 1)) {
			forwardpropmark(allBaseArr[ii].compIndex, !baseBits.get(ii), true);
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
			forwardpropmark(allInputArr[ii].compIndex, nowTrue.get(ii), true);
		}
		inputBits = nowTrue;
	}

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