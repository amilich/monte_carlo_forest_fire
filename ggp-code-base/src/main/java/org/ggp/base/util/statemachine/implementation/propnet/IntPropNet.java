package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlDistinct;
import org.ggp.base.util.gdl.grammar.GdlFunction;
import org.ggp.base.util.gdl.grammar.GdlLiteral;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.gdl.grammar.GdlRelation;
import org.ggp.base.util.gdl.grammar.GdlRule;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.gdl.grammar.GdlTerm;
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

public class IntPropNet extends StateMachine {
	/** The underlying proposition network  */
	private PropNet propNet;
	/** The player roles */
	private Role roles[];

	MachineState init;
	public static final int NUM_THREADS = 4;

	public Proposition[] allInputArr; // TODO we should get rid of this eventually
	Component[] origComps;
	private int terminalCompId;

	// TODO: maybe instead of using pointers in compInfo into compOutputs,
	// we could combine compInfo and compOutputs into one large array for extra cache locality
	private int[][] compState;

	// An entry in compInfo is a long of the form:
	// COMPONENT_TYPE (3 bits) + NUM_INPUTS (19 bits) + NUM_OUTPUTS (19 bits) + OUTPUT_OFFSET (23 bits)
	final long NUM_BITS_COMPONENT_TYPE = 3;
	final long NUM_BITS_INPUT = 19;
	final long NUM_BITS_OUTPUT = 19;
	final long NUM_BITS_OUTPUT_OFFSET = 23;
	final long NUM_INPUT_MASK = ((1l << NUM_BITS_INPUT) -1l) << (NUM_BITS_OUTPUT + NUM_BITS_OUTPUT_OFFSET); // TODO: we can get rid of this in compInfo b/c we do initialization based on objects
	final long NUM_OUTPUT_MASK = ((1l << NUM_BITS_OUTPUT) -1l) << NUM_BITS_OUTPUT_OFFSET;
	final long NUM_OUTPUT_OFFSET_MASK = ((1l << NUM_BITS_OUTPUT_OFFSET) -1l);
	private long[] compInfo;

	private int[] compOutputs;

	// All
	BitSet compBits[];
	BitSet nextBaseBits[];
	BitSet isBase;
	BitSet isInput;

	// The first three bits of the longs in compInfo define the proposition type
	private final long INPUT_TYPE_MASK = 0;
	private final long OTHER_PROP_TYPE_MASK = 1l << 61l;
	private final long BASE_TYPE_MASK = 2l << 61l;
	private final long AND_TYPE_MASK = 3l << 61l;
	private final long OR_TYPE_MASK = 4l << 61l;
	private final long NOT_TYPE_MASK = 5l << 61l;
	private final long TRANSITION_TYPE_MASK = 6l << 61l;
	private final long CONSTANT_TYPE_MASK = 7l << 61l;
	private final long TYPE_MASK = 7l << 61l; // call compInfo[i] & TYPE_MASK to get the type

	private final int TRUE_INT = 0x80000000;
	private final int FALSE_INT = 0x7FFFFFFF;

	private Map<Component, Integer> componentIds; // TODO THIS SHOULD NOT BE A MEMBER VARIABLE DAMNIT. UPDATE MACHINESTATE

	private int numInputs(int compId) {
		return (int)((compInfo[compId] & NUM_INPUT_MASK) >> (NUM_BITS_OUTPUT + NUM_BITS_OUTPUT_OFFSET));
	}

	private int numOutputs(int compId) {
		return (int)((compInfo[compId] & NUM_OUTPUT_MASK) >> NUM_BITS_OUTPUT_OFFSET);
	}

	private int outputOffset(int compId) {
		return (int)(compInfo[compId] & NUM_OUTPUT_OFFSET_MASK);
	}

	private boolean val(int compId, int thread) {
		int debugV = compState[thread][compId];
		int numIn = this.numInputs(compId);
		return (compState[thread][compId] & TRUE_INT) == TRUE_INT;
	}

	int num = 0;
	@Override
	public void convertAndRender(String filename) {
		for (Component c : propNet.getComponents()) {
			c.curVal = val(componentIds.get(c), 0);
			if (c instanceof And) {
				And a = (And) c;
				a.num = compState[0][componentIds.get(c)];
				a.bitIndex = componentIds.get(c);
			} else if (c instanceof Not) {
				Not a = (Not) c;
				a.num = compState[0][componentIds.get(c)];
				a.bitIndex = componentIds.get(c);
			}
		}
		propNet.renderToFile(filename + ++num + ".dot");
	}

	public void render(String filename) {
		propNet.renderToFile(filename + ++num + ".dot");
	}

	public void renderState(String filename, MachineState s) {
		updatePropnetState(s, 0);
		for (Component c : propNet.getComponents()) {
			c.curVal = val(componentIds.get(c), 0);
		}
		propNet.renderToFile(filename + ++num + ".dot");
	}

	@Override
	public int cheapMobility(MachineState s, Role r, int tid) throws MoveDefinitionException {
		double numActions = propNet.getLegalPropositions().get(r).size();
		double numMoves = getLegalMoves(s, r, tid).size();
		return (int) (100.0 * numMoves / numActions);
	}

	BitSet compBitsT;
	BitSet nextBaseBitsT;
	final int MAX_FACTOR_SIZE = 10000;
	@Override
	public void initialize(List<Gdl> description, Role r) {
		System.out.println("[PropNet] Initializing for role " + r);
		description = sanitizeDistinct(description);
		try {
			propNet = OptimizingPropNetFactory.create(description);
			if (propNet.getRoles().size() == 1 && propNet.getComponents().size() < MAX_FACTOR_SIZE) { // TODO
				System.out.println("Trying to optimize");
				doOnePlayerOptimization();
			} else if (propNet.getComponents().size() < MAX_FACTOR_SIZE) {
				factorSubgamesWCC(r);
			}
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);

			// initialize all component states to 0
			int nComps = propNet.getComponents().size();
			int[] initCompState = new int[nComps];

			compBitsT = new BitSet(nComps);
			nextBaseBitsT = new BitSet(nComps);
			isBase = new BitSet(nComps);
			isInput = new BitSet(nComps);

			origComps = propNet.getComponents().toArray(new Component[propNet.getComponents().size()]);
			componentIds = new HashMap<Component, Integer>();
			for (int i = 0; i < origComps.length; i++) {
				componentIds.put(origComps[i], i);
			}

			compInfo = new long[origComps.length];
			List<Integer> compOutputsTemp = new ArrayList<Integer>();
			int curOffset = 0;
			for (int i = 0; i < origComps.length; i++) {
				Component cur = origComps[i];
				cur.crystalize(); // necessary for doInitWork and initforwardpropmark
				int numInputs = cur.inputs.size();
				int numOutputs = cur.outputs.size();
				initCompState[i] = FALSE_INT; // by default, regardless of type, init to false

				// Component type
				long curInfo = 0;
				if (cur instanceof Proposition) {
					if (propNet.getAllInputProps().contains(cur)) {
						curInfo |= INPUT_TYPE_MASK;
						isInput.set(i);
					} else if (propNet.getAllBasePropositions().contains(cur)) {
						curInfo |= BASE_TYPE_MASK;
						isBase.set(i);
					} else {
						curInfo |= OTHER_PROP_TYPE_MASK;
					}
				} else if (cur instanceof And) {
					curInfo |= AND_TYPE_MASK;
					initCompState[i] = TRUE_INT - numInputs;
					if (cur.inputs.toString().contains("r 13") && cur.outputs.toString().contains("OR")) {
						System.out.println(); // TODO debug
					}
				} else if (cur instanceof Or) {
					curInfo |= OR_TYPE_MASK;
					initCompState[i] = FALSE_INT;
				} else if (cur instanceof Not) {
					curInfo |= NOT_TYPE_MASK;
					initCompState[i] = -numInputs;
				} else if (cur instanceof Transition) {
					curInfo |= TRANSITION_TYPE_MASK;
				} else if (cur instanceof Constant) {
					curInfo |= CONSTANT_TYPE_MASK;
					initCompState[i] = ((Constant)cur).getValue() ? TRUE_INT : FALSE_INT;
				}

				// Component inputs
				assert numInputs < (1l << NUM_BITS_INPUT); // if not, wont fit in representation
				long numInputsMask = ((long)numInputs) << (NUM_BITS_OUTPUT + NUM_BITS_OUTPUT_OFFSET);
				curInfo |= numInputsMask;

				// Component outputs
				assert numOutputs < (1l << NUM_BITS_OUTPUT); // if not, wont fit in representation
				long numOutputsMask = ((long)numOutputs) << NUM_BITS_OUTPUT_OFFSET;
				curInfo |= numOutputsMask;

				// Component offset
				long offsetMask = curOffset;
				assert offsetMask < (1l << NUM_BITS_OUTPUT_OFFSET); // if not, won't fit in representation
				curInfo |= offsetMask;

				compInfo[i] = curInfo;

				// Update the connectivity array while updating curOffset
				for (Component output : cur.getOutputs()) {
					compOutputsTemp.add(componentIds.get(output));
					curOffset++;
				}
				// System.out.println("After component " + i + ", compInfo=" + curInfo);
			}

			compOutputs = new int[compOutputsTemp.size()];
			for (int i = 0; i < compOutputs.length; i++) {
				compOutputs[i] = compOutputsTemp.get(i);
			}

			terminalCompId = componentIds.get(propNet.getTerminalProposition());

			// forward prop for initialization
			init = doInitWork(initCompState, componentIds);

			// copy initCompState into each thread's separate state
			compState = new int[NUM_THREADS][nComps];
			nextBaseBits = new BitSet[NUM_THREADS];
			compBits = new BitSet[NUM_THREADS];
			for (int i = 0; i < NUM_THREADS; i++) {
				System.arraycopy(initCompState, 0, compState[i], 0, nComps);
				nextBaseBits[i] = (BitSet) nextBaseBitsT.clone();
				compBits[i] = (BitSet) compBitsT.clone();
			}
			this.convertAndRender("theInitial");
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
	// TODO threads
	public boolean isTerminal(MachineState state) {
		return isTerminal(state, 0);
	}

	@Override
	public boolean isTerminal(MachineState state, int tid) {
		updatePropnetState(state, tid);
		return val(terminalCompId, tid);
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
		return getGoal(state, role, 0);
	}

	@Override
	public int getGoal(MachineState state, Role role, int tid) {
		updatePropnetState(state, tid);
		Set<Proposition> rewards = propNet.getGoalPropositions().get(role);
		for (Proposition p : rewards) {
			boolean val = val(componentIds.get(p), tid);
			if (val) {
				return Integer.parseInt(p.getName().get(1).toString());
			}
		}
		return 0;
	}

	private MachineState doInitWork(int[] initCompState, Map<Component, Integer> componentIds) {
		for (Component c : propNet.getComponents()) {
			if (c instanceof Constant) {
				Set<Component> visited = new HashSet<Component>();
				initforwardpropmark(c, c.getValue(), visited, componentIds);
			}
		}
		Set<Proposition> bases = propNet.getAllBasePropositions();

		System.out.println("There are " + bases.size() + " base propositions.");
		System.out.println("There are " + propNet.getComponents().size() + " components.");
		for (Proposition base : bases) {
			Set<Component> visited = new HashSet<Component>();
			initforwardpropmark(base, false, visited, componentIds);
		}
		System.out.println("Done with bases");

		if (propNet.getInitProposition() != null) {
			Set<Component> visited = new HashSet<Component>();
			initforwardpropmark(propNet.getInitProposition(), true, visited, componentIds);
		}

		Set<GdlSentence> sentences = new HashSet<GdlSentence>();
		for (Proposition base : bases) {
			if (base.getSingleInput().getSingleInput().curVal) {
				sentences.add(base.getName());
				nextBaseBitsT.set(componentIds.get(base));
			}
			if (base.curVal) {
				compBitsT.set(componentIds.get(base));
			}
		}

		BitSet initBits = (BitSet) nextBaseBitsT.clone();

		if (propNet.getInitProposition() != null) {
			Set<Component> visited = new HashSet<Component>();
			initforwardpropmark(propNet.getInitProposition(), false, visited, componentIds);
		}

		// Now, copy the object propnet state into our representation
		for (Component c : propNet.getComponents()) {
			if (c.equals(propNet.getInitProposition())) continue;
			if (c instanceof And || c instanceof Or) {
				for (int ii = 0; ii < c.inputs.size(); ii ++) {
					if (c.input_arr[ii].curVal || (!c.input_arr[ii].curVal && (c.input_arr[ii] instanceof Not))) {
						initCompState[componentIds.get(c)]++;
					}
				}
			} else if (c instanceof Not) {
				boolean inputVal = c.input_arr[0].curVal;
				if (!inputVal) {
					initCompState[componentIds.get(c)] = TRUE_INT;
				} else {
					initCompState[componentIds.get(c)] = FALSE_INT;
				}
			} else {
				if (c.curVal) {
					initCompState[componentIds.get(c)] = TRUE_INT;
				} else {
					initCompState[componentIds.get(c)] = FALSE_INT;
				}
			}
		}

		return new MachineState(initBits);
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
			allMoves.add(new Move(p.getName().get(1), componentIds.get(propNet.getLegalInputMap().get(p))));
		}
		return allMoves;
	}

	/**
	 * Computes the legal moves for role in state.
	 */
	Map<Proposition, Move> propToMove = new HashMap<Proposition, Move>();
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException {
		return getLegalMoves(state, role, 0);
	}

	@Override
	public List<Move> getLegalMoves(MachineState state, Role role, int tid) throws MoveDefinitionException {
		convertAndRender("unsure");
		updatePropnetState(state, tid);
		convertAndRender("unsure");
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			int id = componentIds.get(p);
			if (val(id, tid)) {
				Move m = propToMove.get(p);
				if (m == null) {
					m = new Move(p.getName().get(1), componentIds.get(propNet.getLegalInputMap().get(p)));
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
		return getNextState(state, moves, 0);
	}

	@Override
	public MachineState getNextState(MachineState state, List<Move> moves, int tid) throws TransitionDefinitionException {

		this.convertAndRender("important.dot");
		updatePropnetMoves(moves, tid);
		this.convertAndRender("important.dot");

		updatePropnetState(state, tid);
		Set<GdlSentence> newState = new HashSet<GdlSentence>();
		for (int ii = nextBaseBits[tid].nextSetBit(0); ii != -1; ii = nextBaseBits[tid].nextSetBit(ii + 1)) {
			newState.add(((Proposition) origComps[ii]).getName());
		}
		MachineState m = new MachineState(newState, nextBaseBits[tid]);
		return m;
	}

	public void initforwardpropmark(Component c, boolean newValue, Set<Component> visited, Map<Component, Integer> componentIds) {
		if (visited.contains(c)) return;
		visited.add(c);
		c.curVal = newValue;

		if (c.isBase) {
			if (newValue) compBitsT.set(componentIds.get(c));
			else compBitsT.clear(componentIds.get(c));
		} else if (c instanceof Transition) {
			// transitions always have exactly one output, or zero if pruned during factoring
			if (c.output_arr.length > 0) {
				if (newValue) nextBaseBitsT.set(componentIds.get(c.output_arr[0]));
				else nextBaseBitsT.clear(componentIds.get(c.output_arr[0]));
			}
			return;
		}
		for (int jj = 0; jj < c.output_arr.length; jj ++) {
			Component out = c.output_arr[jj];
			if (out instanceof Transition || out instanceof Proposition) {
				initforwardpropmark(out, newValue, visited, componentIds);
			} else if (out instanceof And) {
				if (!newValue) {
					initforwardpropmark(out, false, visited, componentIds);
				} else {
					boolean result = true;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (!out.input_arr[ii].curVal) {
							result = false;
							break;
						}
					}
					initforwardpropmark(out, result, visited, componentIds);
				}
			} else if (out instanceof Or) {
				if (newValue) {
					initforwardpropmark(out, true, visited, componentIds);
				} else {
					boolean result = false;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (out.input_arr[ii].curVal) {
							result = true;
							break;
						}
					}
					initforwardpropmark(out, result, visited, componentIds);
				}
			} else if (out instanceof Not) {
				initforwardpropmark(out, !newValue, visited, componentIds);
			}
		}
	}

	/**
	 * Precondition to being called: compId's value was changed (and the change was recorded in compState, but not in compBits!)
	 * in a previous recursive call.
	 * @param compId
	 * @param thread
	 */
	private void forwardpropmarkRec(int compId, int thread) {
		Component ci = origComps[compId];
		if (ci.toString().contains("r 13")) {
			System.out.println();
		} else if (ci.toString().contains("c 13")) {
			System.out.println();
		}

		int numOutputs = numOutputs(compId);
		int offset = outputOffset(compId);
		long type = compInfo[compId] & TYPE_MASK;
		boolean newValue = val(compId, thread);
		if (type == BASE_TYPE_MASK) {
			compBits[thread].flip(compId);
		} else if (type == TRANSITION_TYPE_MASK) {
			if (numOutputs > 0) {
				nextBaseBits[thread].flip(compOutputs[offset]);
			}
			return;
		}

		for (int i = offset; i < offset + numOutputs; i ++) {
			int comp = compOutputs[i];
			Component c = origComps[comp];// TODO rem
			c.bitIndex--; // TODO for debug

			long outType = compInfo[comp] & TYPE_MASK;
			boolean orig = val(comp, thread);
			compState[thread][comp] += newValue ? 1 : -1;
			if (c instanceof And && compState[thread][comp] < TRUE_INT - c.input_arr.length && compState[thread][comp] > 0) {
				System.out.println(compState[thread][comp]);
				this.convertAndRender("ALERT");
			}
			if (val(compOutputs[i], thread) != orig) {
				forwardpropmarkRec(comp, thread);
			}
		}
	}

	/**
	 * Precondition: only call this on base and input propositions whose truth values are
	 * different from the previous state.
	 * @param compId
	 * @param newValue
	 * @param thread
	 */
	public void forwardpropmark(int compId, boolean newValue, int thread) {
		compState[thread][compId] = newValue ? TRUE_INT : FALSE_INT;
		forwardpropmarkRec(compId, thread);
	}

	// TODO: need to handle multiple threads
	public void updatePropnetState(MachineState state, int tid) {
		BitSet newBits = (BitSet) state.props.clone();
		newBits.xor(compBits[tid]);
		newBits.and(isBase);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			Component c = origComps[ii];
			forwardpropmark(ii, state.props.get(ii), tid);
		}
	}

	public List<Move> getInternalMoves(MachineState state, int tid) throws MoveDefinitionException {
		List<Move> legals = new ArrayList<Move>();
		for (Role role : getRoles()) {
			List<Move> ms = getLegalMoves(state, role, tid);
			legals.add(ms.get(r.nextInt(ms.size())));
		}
		return legals;
	}

	Random r = new Random();
	@Override
	public MachineState internalDC(MachineState start, int tid)
			throws MoveDefinitionException, TransitionDefinitionException {
		// this.convertAndRender("btns25.dot");
		while (!isTerminal(start, tid)) {
			List<Move> selected = getInternalMoves(start, tid); //jmoves.get(r.nextInt(jmoves.size()));
			start = internalNextState(start, selected, tid);
		}
		return start;
	}

	// TODO does not work
	public List<Move> randomJointMove(MachineState state, int tid) throws MoveDefinitionException {
		List<Move> moves = new ArrayList<Move>();
		for (Role role : getRoles()) {
			List<Move> lms = internalLegalMoves(state, role, tid);
			moves.add(lms.get(r.nextInt(lms.size())));
		}
		return moves;
	}

	// TODO make internal legal joint moves
	public List<Move> internalLegalMoves(MachineState state, Role role, int tid)
			throws MoveDefinitionException {
		updatePropnetState(state, tid);
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			int id = componentIds.get(p);
			if (val(id, tid)) {
				legalMoves.add(new Move(id));
			}
		}
		return legalMoves;
	}

	public MachineState internalNextState(MachineState state, List<Move> moves, int tid)
			throws TransitionDefinitionException {
		updatePropnetState(state, tid);
		internalMoveUpdate(moves, tid);
		MachineState m = new MachineState(nextBaseBits[tid]);
		return m;
	}

	public void internalMoveUpdate(List<Move> moves, int tid) {
		BitSet newBits = new BitSet(compInfo.length);
		for (Move m : moves) {
			newBits.set(m.compId);
		}
		newBits.xor(compBits[tid]);
		newBits.and(isInput);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			forwardpropmark(ii, !val(ii, tid), tid);
		}
		for (int ii = isInput.nextSetBit(0); ii != -1; ii = isInput.nextSetBit(ii + 1)) {
			if (newBits.get(ii)) compBits[tid].flip(ii);
		}
	}

	// TODO: need to handle multiple threads
	public void updatePropnetMoves(List<Move> moves, int tid) {
		Set<GdlSentence> moveGdl = toDoes(moves);
		BitSet newBits = new BitSet(compInfo.length);
		for (GdlSentence s : moveGdl) {
			newBits.set(componentIds.get(propNet.getInputPropositions().get(s)));
		}
		newBits.xor(compBits[tid]);
		newBits.and(isInput);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			Component c = origComps[ii];
			forwardpropmark(ii, !val(ii, tid), tid);
		}
		for (int ii = isInput.nextSetBit(0); ii != -1; ii = isInput.nextSetBit(ii + 1)) {
			if (newBits.get(ii)) compBits[tid].flip(ii);
		}
	}

	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return propNet.getRoles();
	}

	/* Helper methods */


	private void sanitizeDistinctHelper(Gdl gdl, List<Gdl> in, List<Gdl> out) {
		if (!(gdl instanceof GdlRule)) {
			out.add(gdl);
			return;
		}
		GdlRule rule = (GdlRule) gdl;
		for (GdlLiteral lit : rule.getBody()) {
			if (lit instanceof GdlDistinct) {
				GdlDistinct d = (GdlDistinct) lit;
				GdlTerm a = d.getArg1();
				GdlTerm b = d.getArg2();
				if (!(a instanceof GdlFunction) && !(b instanceof GdlFunction)) continue;
				if (!(a instanceof GdlFunction && b instanceof GdlFunction)) return;
				GdlSentence af = ((GdlFunction) a).toSentence();
				GdlSentence bf = ((GdlFunction) b).toSentence();
				if (!af.getName().equals(bf.getName())) return;
				if (af.arity() != bf.arity()) return;
				for (int i = 0; i < af.arity(); i++) {
					List<GdlLiteral> ruleBody = new ArrayList<>();
					for (GdlLiteral newLit : rule.getBody()) {
						if (newLit != lit) ruleBody.add(newLit);
						else ruleBody.add(GdlPool.getDistinct(af.get(i), bf.get(i)));
					}
					GdlRule newRule = GdlPool.getRule(rule.getHead(), ruleBody);
					// Log.println("new rule: " + newRule);
					in.add(newRule);
				}
				return;
			}
		}
		for (GdlLiteral lit : rule.getBody()) {
			if (lit instanceof GdlDistinct) {
				System.out.println("distinct rule added: " + rule);
				break;
			}
		}
		out.add(rule);
	}

	private List<Gdl> sanitizeDistinct(List<Gdl> description) {
		List<Gdl> out = new ArrayList<>();
		for (int i = 0; i < description.size(); i++) {
			sanitizeDistinctHelper(description.get(i), description, out);
		}
		return out;
	}


	public Set<Component> dfs(Component p) {
		Queue<Component> nodesToVisit = new LinkedList<Component>();
		Set<Component> visited = new HashSet<Component>();
		nodesToVisit.add(p);
		while (!nodesToVisit.isEmpty()) {
			Component currNode = nodesToVisit.poll();
			if (visited.contains(currNode)) continue;
			else visited.add(currNode);
			nodesToVisit.addAll(currNode.inputs);
		}
		return visited;
	}

	Map<Proposition, Proposition> inputLegalMap = new HashMap<Proposition, Proposition>();

	public void doOnePlayerOptimization() {
		for (Proposition p : propNet.getLegalInputMap().keySet()) {
			inputLegalMap.put(propNet.getLegalInputMap().get(p), p);
		}

		Set<Component> important = new HashSet<Component>();
		Set<Component> toRemove = new HashSet<Component>();
		important.addAll(dfs(propNet.getTerminalProposition()));
		important.addAll(dfs(propNet.getInitProposition()));
		for (Proposition p : propNet.getAllGoalPropositions()) {
			important.addAll(dfs(p));
		}
		for (Proposition p : propNet.getAllLegalPropositions()) {
			important.addAll(dfs(p));
		}
		for (Component c : propNet.getComponents()) {
			if (!important.contains(c)) {
				toRemove.add(c);
			}
		}
		propNet.renderToFile("opBefore.dot");
		System.out.println("Removing " + toRemove.size() + " components - " + toRemove);
		for (Component c : toRemove) {
			propNet.removeComponent(c);
			if (inputLegalMap.containsKey(c)) {
				propNet.removeComponent(inputLegalMap.get(c));
			}
		}
		propNet.renderToFile("opDone.dot");
//		for (Component c : propNet.getComponents()) {
//			Proposition input = (Proposition) propNet.getLegalInputMap().get(c);
//			if (input == null) {
//				propNet.removeComponent(c);
//			}
//		}
		// System.out.println("Removed " + toRemove.size() + " components.");
		// propNet.renderToFile("optimized.dot");
	}

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
	public Set<Component> undirectedDfsFromNode(Proposition p, Set<Component> allVisited, List<Boolean> wccIsRelevant, Role r) {
		assert !allVisited.contains(p);
		Set<Component> result = new HashSet<Component>();
		Queue<Component> frontier = new LinkedList<Component>();
		frontier.add(p);
		boolean isRelevant = false;
		while (!frontier.isEmpty()) {
			Component cur = frontier.poll();
			if (allVisited.contains(cur))
				continue;
			if (cur.equals(propNet.getInitProposition())) // ignore the init proposition when determining WCCs
				continue;

			allVisited.add(cur);
			result.add(cur);
			if (propNet.getGoalPropositions().get(r).contains(cur) || propNet.getTerminalProposition().equals(cur))
				isRelevant = true;

			propNet.getInputPropositions().get(r);

			frontier.addAll(cur.inputs);
			frontier.addAll(cur.outputs);
		}
		wccIsRelevant.add(isRelevant);
		return result;
	}

	/**
	 * Factors game into weakly connected components, ignoring init.
	 * @param r
	 * @return
	 */
	public void factorSubgamesWCC(Role r) {
		propNet.renderToFile("start_w.dot");
		Proposition term = propNet.getTerminalProposition();
		Set<Component> allVisited = new HashSet<Component>();
		List<Set<Component>> wccs = new ArrayList<Set<Component>>();
		List<Boolean> wccIsRelevant = new ArrayList<Boolean>();

		for (Proposition p : propNet.getAllGoalPropositions()) {
			if (allVisited.contains(p))
				continue;
			Set<Component> curWcc = undirectedDfsFromNode(p, allVisited, wccIsRelevant, r);
			wccs.add(curWcc);
		}
		Set<Component> curWcc = undirectedDfsFromNode(term, allVisited, wccIsRelevant, r);
		wccs.add(curWcc);

		int numWccsRemoved = 0;
		int numR = 0;
		for (int i = 0; i < wccs.size(); i++) {
			if (!wccIsRelevant.get(i)) {
				for (Component c : wccs.get(i)) {
					propNet.removeComponent(c);
					numR ++;
				}
				numWccsRemoved++;
				// propNet.renderToFile("factor_removed_" + numWccsRemoved + "_wccs.dot");
			}
		}
		System.out.println("Removed " + numR + " components.");
		Set<Role> relevantRoles = new HashSet<Role>();
		for (int i = 0; i < wccs.size(); i++) {
			if (wccIsRelevant.get(i)) {
				for (Component c : wccs.get(i)) {
					for (Role role : propNet.roles) {
						if (propNet.getGoalPropositions().get(role).contains(c)) {
							relevantRoles.add(role);
							break;
						}
					}
					for (Role role : propNet.roles) {
						if (propNet.getLegalPropositions().get(role).contains(c)) {
							relevantRoles.add(role);
							break;
						}
					}
				}
			}
		}

		Iterator<Role> it = propNet.roles.iterator();
		while (it.hasNext()) {
			Role role = it.next();
			if (!relevantRoles.contains(role)) {
				it.remove();
			}
		}
		// System.out.println(relevantRoles);
		// System.out.println(propNet.roles);
	}
}
