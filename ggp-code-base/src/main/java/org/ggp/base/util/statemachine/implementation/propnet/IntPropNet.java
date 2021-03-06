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
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.ThreadLocalRandom;

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

/**
 * IntPropNet
 * -----------
 * Integer-based and thread-safe propositional net.
 *
 * @author monte_carlo_forest_fire
 */
public class IntPropNet extends StateMachine {
	/** The underlying proposition network  */
	private PropNet propNet;
	/** The player roles */
	private Role roles[];

	MachineState init; // Initial state (do not want to recompute)
	public static final int NUM_THREADS = 8;
	public Proposition[] allInputArr;
	Component[] origComps;
	private int terminalCompId;

	// 2D array of component state: row is thread_id, col is component state
	private int[][] compState;

	// An entry in compInfo is a long of the form:
	// COMPONENT_TYPE (3 bits) + NUM_INPUTS (19 bits) + NUM_OUTPUTS (19 bits) + OUTPUT_OFFSET (23 bits)
	final long NUM_BITS_COMPONENT_TYPE = 3;
	final long NUM_BITS_INPUT = 19;
	final long NUM_BITS_OUTPUT = 19;
	final long NUM_BITS_OUTPUT_OFFSET = 23;
	final long NUM_INPUT_MASK = ((1l << NUM_BITS_INPUT) -1l) << (NUM_BITS_OUTPUT + NUM_BITS_OUTPUT_OFFSET);
	final long NUM_OUTPUT_MASK = ((1l << NUM_BITS_OUTPUT) -1l) << NUM_BITS_OUTPUT_OFFSET;
	final long NUM_OUTPUT_OFFSET_MASK = ((1l << NUM_BITS_OUTPUT_OFFSET) -1l);
	private long[] compInfo;

	// Array of all outputs in the propositional net
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

	// Boolean value of a component is whether its top bit is true
	private final int TRUE_INT = 0x80000000;
	private final int FALSE_INT = 0x7FFFFFFF;
	private Map<Component, Integer> componentIds;

	/**
	 * numOutputs
	 *
	 * Looks into component info array to get number of outputs.
	 */
	private int numOutputs(int compId) {
		return (int)((compInfo[compId] & NUM_OUTPUT_MASK) >> NUM_BITS_OUTPUT_OFFSET);
	}

	/**
	 * outputOffset
	 *
	 * Looks into component info array to get output offset in large output array.
	 */
	private int outputOffset(int compId) {
		return (int)(compInfo[compId] & NUM_OUTPUT_OFFSET_MASK);
	}

	/**
	 * val
	 *
	 * Get component's value by checking its most significant bit.
	 */
	private boolean val(int compId, int thread) {
		return (compState[thread][compId] & TRUE_INT) == TRUE_INT;
	}

	/**
	 * ConvertAndRender
	 *
	 * Used to render the integer-based propnet into a graph file.
	 * Stores the current integer value into the component's object
	 * representation; colors every component according to their state.
	 */
	int num = 0;
	@Override
	public void convertAndRender(String filename) {
		for (Component c : propNet.getComponents()) {
			c.curVal = val(componentIds.get(c), 0);
			if (c instanceof And) {
				And a = (And) c;
				a.num = compState[0][componentIds.get(c)];
				a.intVal = componentIds.get(c);
			} else if (c instanceof Not) {
				Not a = (Not) c;
				a.num = compState[0][componentIds.get(c)];
				a.intVal = componentIds.get(c);
			} else if (c instanceof Or) {
				Or a = (Or) c;
				a.num = compState[0][componentIds.get(c)];
				a.intVal = componentIds.get(c);
			}
		}
		propNet.renderToFile(filename + ++num + ".dot");
	}

	BitSet compBitsT; // Temporary initialization BitSets shared bewteen initialize and correctPropNetState
	BitSet nextBaseBitsT;
	final int MAX_FACTOR_SIZE = 10000; // Maximum number of components in propnet to try and factor

	/**
	 * initialize
	 *
	 * Initializes a propnet for a given role. Will sort components, factor propnet,
	 * and initialize for NUM_THREADS threads.
	 */
	@Override
	public void initialize(List<Gdl> description, Role r) {
		System.out.println("[PropNet] Initializing for role " + r);
		description = sanitizeDistinct(description);
		try {
			propNet = OptimizingPropNetFactory.create(description);
			System.out.println("Built propnet");

			try {
				if (propNet.getRoles().size() == 1 && propNet.getComponents().size() < MAX_FACTOR_SIZE) {
					System.out.println("Trying to one-player factor propnet");
					doOnePlayerOptimization();
				} else if (propNet.getComponents().size() < MAX_FACTOR_SIZE) {
					System.out.println("Trying to two-player factor propnet");
					factorSubgamesWCC(r);
				}
			} catch (Exception e) {
				System.out.println("Factoring failed: ");
				System.out.println(e);
			}
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);

			// Initialize all component states to 0
			int nComps = propNet.getComponents().size();
			int[] initCompState = new int[nComps];

			compBitsT = new BitSet(nComps);
			nextBaseBitsT = new BitSet(nComps);
			isBase = new BitSet(nComps);
			isInput = new BitSet(nComps);

			origComps = propNet.getComponents().toArray(new Component[propNet.getComponents().size()]);
			boolean result = topsortComponents(origComps);
			System.out.println("[PropNet] Completed topological sort");
			// testTopologicalOrdering(Arrays.asList(origComps)); // Uncomment to test ordering
			System.out.println("[PropNet] Topsort result: " + result);

			componentIds = new HashMap<Component, Integer>();
			for (int i = 0; i < origComps.length; i++) {
				componentIds.put(origComps[i], i);
			}

			compInfo = new long[origComps.length];
			List<Integer> compOutputsTemp = new ArrayList<Integer>();
			int curOffset = 0;
			for (int i = 0; i < origComps.length; i++) {
				Component cur = origComps[i];
				cur.crystalize(); // Necessary for doInitWork and initforwardpropmark
				int numInputs = cur.inputs.size();
				int numOutputs = cur.outputs.size();
				initCompState[i] = FALSE_INT; // By default, regardless of type, init to false

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
			}

			compOutputs = new int[compOutputsTemp.size()];
			for (int i = 0; i < compOutputs.length; i++) {
				compOutputs[i] = compOutputsTemp.get(i);
			}
			terminalCompId = componentIds.get(propNet.getTerminalProposition());

			// Update our propnet's components to be correct
			init = correctPropNetState(initCompState, componentIds);

			// Copy initCompState into each thread's separate state
			compState = new int[NUM_THREADS][nComps];
			nextBaseBits = new BitSet[NUM_THREADS];
			compBits = new BitSet[NUM_THREADS];
			for (int i = 0; i < NUM_THREADS; i++) {
				System.arraycopy(initCompState, 0, compState[i], 0, nComps);
				nextBaseBits[i] = (BitSet) nextBaseBitsT.clone();
				compBits[i] = (BitSet) compBitsT.clone();
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		System.out.println("[PropNet] Initialization done");
	}

	/**
	 * isTerminal
	 *
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		return isTerminal(state, 0);
	}

	/**
	 * isTerminal
	 *
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state. This version
	 * accepts a thread ID.
	 */
	@Override
	public boolean isTerminal(MachineState state, int tid) {
		updatePropnetState(state, tid);
		return val(terminalCompId, tid);
	}

	/**
	 * getGoal
	 *
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

	/**
	 * getGoal
	 *
	 * Returns goal value from state and for role with given thread ID.
	 */
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

	/**
	 * topsortHelper
	 *
	 * Recursive topological sort helper function.
	 */
	private static boolean topsortHelper(Component comp, Set<Component> visited, Set<Component> tempMarks, Stack<Component> order) {
		if (tempMarks.contains(comp))
			return false; // Graph has a cycle

		if (!visited.contains(comp)) {
			if (!(comp instanceof Transition)) { // Pretend that transitions don't have outputs for the topsort
				tempMarks.add(comp);
				for (Component next : comp.getOutputs()) {
					if (!topsortHelper(next, visited, tempMarks, order))
						return false;
				}
				tempMarks.remove(comp);
			}
			visited.add(comp);
			order.push(comp);
		}
		return true;
	}

	/**
	 * topsortComponents
	 *
	 * This should compute the topological ordering of all components.
	 * Given an array of all components in the graph, modifies the array
	 * in-place to be topologically sorted. If the graph contains a cycle
	 * the ordering of the input array is unmodified.
	 *
	 * @return true iff the graph does not contain a cycle (ignoring transition outputs).
	 */
	public static boolean topsortComponents(Component[] comps) {
		Stack<Component> order = new Stack<Component>();
		Set<Component> visited = new HashSet<Component>();
		Set<Component> tempMarks = new HashSet<Component>();
		System.out.println("Starting topsort");
		int count = 0;
		for (Component comp : comps) {
			count ++;
			if (!visited.contains(comp)) {
				if (!topsortHelper(comp, visited, tempMarks, order))
					return false;
			}
			if (count % 5000 == 0) {
				System.out.println(count); // Just so we can see where we are in the ordering
			}
		}
		for (int i = 0; i < comps.length; i++) {
			comps[i] = order.pop();
		}
		return true;
	}

	/**
	 * testTopologicalOrdering
	 *
	 * Tests to see if our ordering is topological.
	 */
	public void testTopologicalOrdering(List<Component> ordering){
		for (int i = 1; i < ordering.size(); i++){
			HashSet<Component> prev = new HashSet<Component>(ordering.subList(0, i));
			Set<Component> inputs_c = ordering.get(i).getInputs();
			HashSet<Component> inputs = new HashSet<Component>();
			for (Component c : inputs_c){
				if (!(c instanceof Transition)){
					inputs.add(c);
				}
			}
			inputs.removeAll(prev);
			if (!inputs.isEmpty()){
				throw new Error("[PropNet] Ordering is not topological");
			}
		}
	}

	/**
	 * correctPropNetState
	 *
	 * Initializes our propNet to be consistent with the initial state.
	 */
	private MachineState correctPropNetState(int[] initCompState, Map<Component, Integer> componentIds) {
		Set<Proposition> bases = propNet.getAllBasePropositions();
		System.out.println("[PropNet] There are " + bases.size() + " base propositions.");
		System.out.println("[PropNet] There are " + propNet.getComponents().size() + " components.");
		if (propNet.getInitProposition() != null) {
			propNet.getInitProposition().curVal = true;
		}
		for (Component c : propNet.getComponents()) {
			if (c instanceof Constant) {
				Set<Component> visited = new HashSet<Component>();
				initforwardpropmark(c, c.getValue(), visited, componentIds);
			}
		}

		for (Component c : origComps) {
			if (c instanceof Constant) continue;
			else if (bases.contains(c)) continue;
			else if (propNet.getAllInputProps().contains(c)) continue;
			else if (c.equals(propNet.getInitProposition())) continue;
			else if (c instanceof And) {
				boolean result = true;
				for (int ii = 0; ii < c.inputs.size(); ii ++) {
					if (!c.input_arr[ii].curVal) {
						result = false;
						break;
					}
				}
				c.curVal = result;
			} else if (c instanceof Or) {
				boolean result = false;
				for (int ii = 0; ii < c.inputs.size(); ii ++) {
					if (c.input_arr[ii].curVal) {
						result = true;
						break;
					}
				}
				c.curVal = result;
			} else if (c instanceof Not) {
				c.curVal = !c.input_arr[0].curVal;
			} else if (c instanceof Transition) {
				c.curVal = c.input_arr[0].curVal;
			} else if (c instanceof Proposition) {
				if (c.input_arr.length == 0) {
					System.out.println(c);
				} else {
					c.curVal = c.input_arr[0].curVal;
				}
			} else {
				System.out.println("[IntPropNet] Unknown type: " + c);
			}
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
					Component in = c.input_arr[ii];
					if (in.curVal && !(in instanceof Not)) {
						initCompState[componentIds.get(c)]++;
					} else if (in instanceof Not) {
						boolean notVal = !in.input_arr[0].curVal;
						if (notVal) {
							initCompState[componentIds.get(c)]++;
						}
					}
				}
			} else if (c instanceof Not) {
				boolean inputVal = c.input_arr[0].curVal;
				if (!inputVal) {
					initCompState[componentIds.get(c)] = -1;
				} else {
					initCompState[componentIds.get(c)] = 0;
				}
			} else {
				if (c.curVal) {
					initCompState[componentIds.get(c)] = TRUE_INT;
				} else {
					initCompState[componentIds.get(c)] = FALSE_INT;
				}
			}
		}

		Set<GdlSentence> initSet = new HashSet<GdlSentence>();
		for (int ii = initBits.nextSetBit(0); ii != -1; ii = initBits.nextSetBit(ii + 1)) {
			Proposition p = (Proposition) origComps[ii];
			initSet.add(p.getName());
		}
		return new MachineState(initSet, initBits);
	}

	Set<Proposition> trueLegals = new HashSet<Proposition>();

	/**
	 * getInitialState
	 *
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
	@Override
	public MachineState getInitialState() {
		return init;
	}

	/**
	 * findActions
	 *
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
	 * getLegalMoves
	 *
	 * Computes the legal moves for role in state.
	 */
	Map<Proposition, Move> propToMove = new HashMap<Proposition, Move>();
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role) throws MoveDefinitionException {
		return getLegalMoves(state, role, 0);
	}

	/**
	 * getLegalMoves
	 *
	 * Get legal moves for a player given state, role, and thread ID.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role, int tid) throws MoveDefinitionException {
		updatePropnetState(state, tid);
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
	 * getNextState
	 *
	 * Computes the next state given state and the list of moves.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
			throws TransitionDefinitionException {
		return getNextState(state, moves, 0);
	}

	/**
	 * getNextState
	 *
	 * Computes the next state given state, list of moves, and thread ID.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves, int tid) throws TransitionDefinitionException {
		updatePropnetMoves(moves, tid);
		updatePropnetState(state, tid);
		Set<GdlSentence> newState = new HashSet<GdlSentence>();
		for (int ii = nextBaseBits[tid].nextSetBit(0); ii != -1; ii = nextBaseBits[tid].nextSetBit(ii + 1)) {
			newState.add(((Proposition) origComps[ii]).getName());
		}
		MachineState m = new MachineState(newState, nextBaseBits[tid]);
		return m;
	}

	/**
	 * initforwardpropmark
	 *
	 * Non-differential forward propagation performed on object based propnet.
	 */
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
	 * forwardpropmarkRec
	 *
	 * Precondition to being called: compId's value was changed (and the change was recorded in compState, but not in compBits!)
	 * in a previous recursive call.
	 */
	private void forwardpropmarkRec(int compId, int thread) {int numOutputs = numOutputs(compId);
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
			boolean orig = val(comp, thread);
			compState[thread][comp] += newValue ? 1 : -1;
			if (val(compOutputs[i], thread) != orig) {
				forwardpropmarkRec(comp, thread);
			}
		}
	}

	/**
	 * forwardpropmark
	 *
	 * Wrapper for recursive forward propagation method.
	 * Precondition: only call this on base and input propositions whose truth values are
	 * different from the previous state.
	 */
	public void forwardpropmark(int compId, boolean newValue, int thread) {
		compState[thread][compId] = newValue ? TRUE_INT : FALSE_INT;
		forwardpropmarkRec(compId, thread);
	}

	/**
	 * updatePropnetState
	 *
	 * Given a state, update the propnet's base propositions to be consistent with that state
	 * and then forward propagate changed ones.
	 */
	public void updatePropnetState(MachineState state, int tid) {
		if (state.props == null) {
			System.out.println("[PropNet] State contains null bitset");
			BitSet stateB = new BitSet(origComps.length);
			for (GdlSentence s : state.getContents()) {
				Component sc = propNet.getBasePropositions().get(s);
				if (sc != null) stateB.set(componentIds.get(sc));
			}
			state.props = stateB;
		}
		BitSet newBits = (BitSet) state.props.clone();
		newBits.xor(compBits[tid]);
		newBits.and(isBase);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			forwardpropmark(ii, state.props.get(ii), tid);
		}
	}

	/**
	 * getInternalMoves
	 *
	 * Get random joint move from all players using internal component representations
	 * (i.e. component IDs instead of GDL).
	 */
	public List<Move> getInternalMoves(MachineState state, int tid) throws MoveDefinitionException {
		List<Move> legals = new ArrayList<Move>();
		for (Role role : getRoles()) {
			List<Move> ms = getLegalMoves(state, role, tid);
			legals.add(ms.get(ThreadLocalRandom.current().nextInt(0, ms.size())));
		}
		return legals;
	}

	/**
	 * internalDC
	 *
	 * Internal depth charge for the propnet. Given a thread ID, uses internal component
	 * representations to speed up depth charges.
	 */
	@Override
	public MachineState internalDC(MachineState start, int tid)
			throws MoveDefinitionException, TransitionDefinitionException {
		while (!isTerminal(start, tid)) {
			List<Move> selected = getInternalMoves(start, tid);
			start = internalNextState(start, selected, tid);
		}
		return start;
	}

	/**
	 * preInternalDCGoal
	 *
	 * We use this method to return an average of goal value throughout the entire game instead of just at
	 * the end. See similar implementation for mobility heuristic below.
	 */
	@Override
	public MachineState preInternalDCGoal(MachineState start, MachineState finalS, int tid, double[] weightedGoal, Role player)
			throws MoveDefinitionException, TransitionDefinitionException {
		int playerIndex = -1;
		for(int i = 0; i <  roles.length ; i++){
			if (roles[i].equals(player)){
				playerIndex = i;
			}
		}
		MachineState next = null;
		List<Double> goalVals = new ArrayList<Double>();
		int numMovesToTerminal = 0;
		while (true) {
			List<Move> selected = getInternalMoves(start, tid);
			next = internalNextState(start, selected, tid);
			if (!isTerminal(next, tid)) {
				start = next;
				double goal = getGoal(start, roles[playerIndex], tid);
				goalVals.add(goal);
				numMovesToTerminal++;
			} else {
				break;
			}
		}
		List<Double> weights = new ArrayList<Double>();
		double weightsSum = 0.0;
		for(int i = goalVals.size(); i > 0; i--){
			weights.add((double) i);
			weightsSum += (double) i;
		}

		double weightedGoalSum = 0.0;
		for(int i = 0; i < weights.size(); i++){
			weightedGoalSum += goalVals.get(i) * weights.get(i) / weightsSum;
		}
		if (numMovesToTerminal > 0) {
			weightedGoal[0] = weightedGoalSum;
		} else {
			weightedGoal[0] = 0;
		}
		finalS.props = (BitSet) next.props.clone();
		return start;
	}

	/**
	 * cheapMobility
	 *
	 * Internal mobility heuristic inside propNet.
	 */
	@Override
	public double cheapMobility(MachineState s, Role r, int tid) throws MoveDefinitionException {
		double numActions = propNet.getLegalPropositions().get(r).size();
		double numMoves = getLegalMoves(s, r, tid).size();
		return (100.0 * numMoves / numActions);
	}

	/**
	 * preInternalDCMobility
	 *
	 * We use this method to return an average of the mobility throughout the entire game, not just at
	 * the end. This way, we get a sense of how good mobility is as a whole, not just at the end when
	 * the game is pretty much determined
	 */
	@Override
	public MachineState preInternalDCMobility(MachineState start, MachineState finalS, int tid, double[] weightedMobility, Role player)
			throws MoveDefinitionException, TransitionDefinitionException {
		int playerIndex = -1;
		for(int i = 0; i <  roles.length ; i++){
			if (roles[i].equals(player)){
				playerIndex = i;
			}
		}
		MachineState next = null;
		List<Double> mobilityVals = new ArrayList<Double>();
		int numMovesToTerminal = 0;
		while (true) {
			List<Move> selected = getInternalMoves(start, tid);
			next = internalNextState(start, selected, tid);
			if (!isTerminal(next, tid)) {
				start = next;
				double cm = cheapMobility(start, roles[playerIndex], tid);
				mobilityVals.add(cm);
				numMovesToTerminal++;
			} else {
				break;
			}
		}
		List<Double> weights = new ArrayList<Double>();
		double weightsSum = 0.0;
		for(int i = mobilityVals.size(); i > 0; i--){
			weights.add((double) i);
			weightsSum += (double) i;
		}

		double weightedMobilitySum = 0.0;
		for(int i = 0; i < weights.size(); i++){
			weightedMobilitySum += mobilityVals.get(i) * weights.get(i) / weightsSum;
		}
		if (numMovesToTerminal > 0) {
			weightedMobility[0] = weightedMobilitySum;
		} else {
			weightedMobility[0] = 0;
		}
		finalS.props = (BitSet) next.props.clone();
		return start;
	}

	/**
	 * preInternalDC
	 *
	 * Do a depth charge and return the state prior to the terminal state.
	 */
	@Override
	public MachineState preInternalDC(MachineState start, MachineState finalS, int tid)
			throws MoveDefinitionException, TransitionDefinitionException {
		MachineState next = null;
		while (true) {
			List<Move> selected = getInternalMoves(start, tid);
			next = internalNextState(start, selected, tid);
			if (!isTerminal(next, tid)) {
				start = next;
			} else {
				break;
			}
		}
		finalS.props = (BitSet) next.props.clone();
		return start;
	}

	/**
	 * internalNextState
	 *
	 * Get next state from internal component representations.
	 */
	public MachineState internalNextState(MachineState state, List<Move> moves, int tid)
			throws TransitionDefinitionException {
		internalStateUpdate((BitSet) state.props.clone(), tid);
		internalMoveUpdate(moves, tid);
		MachineState m = new MachineState(nextBaseBits[tid]);
		return m;
	}

	/**
	 * internalLegalMoves
	 *
	 * Get legal moves; returns moves without GDL in them (just component IDs).
	 * Useful for fast depth charges but not returning move to server.
	 */
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

	/**
	 * internalStateUpdate
	 *
	 * Update propnet's state based on a bitset of components.
	 */
	public void internalStateUpdate(BitSet state, int tid) {
		state.xor(compBits[tid]);
		state.and(isBase);
		for (int ii = state.nextSetBit(0); ii != -1; ii = state.nextSetBit(ii + 1)) {
			forwardpropmark(ii, state.get(ii), tid);
		}
	}

	/**
	 * internalMoveUpdate
	 *
	 * Internal move update used during a depth charge.
	 */
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

	/**
	 * updatePropnetMoves
	 *
	 * Given a list of moves, update our input propositions
	 * and forward propagate the ones that have changed.
	 */
	public void updatePropnetMoves(List<Move> moves, int tid) {
		Set<GdlSentence> moveGdl = toDoes(moves);
		BitSet newBits = new BitSet(compInfo.length);
		for (GdlSentence s : moveGdl) {
			Component c = propNet.getInputPropositions().get(s);
			if (c != null) {
				newBits.set(componentIds.get(c));
			}
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

	/**
	 * dfs
	 *
	 * Simply performs a DFS from a particular node and returns a set of visited components.
	 */
	public Set<Component> dfs(Component p) {
		Queue<Component> nodesToVisit = new LinkedList<Component>();
		Set<Component> visited = new HashSet<Component>();
		nodesToVisit.add(p);
		while (!nodesToVisit.isEmpty()) {
			Component currNode = nodesToVisit.poll();
			if (currNode == null) {
				System.out.println("[dfs] Null node found in dfs");
				continue;
			}
			if (visited.contains(currNode)) continue;
			else visited.add(currNode);
			if (currNode.inputs == null) {
				// Somehow, we found some nodes in Dual Rainbow would have
				// no inputs.
				System.out.println("[dfs] Null inputs: " + currNode);
			} else {
				nodesToVisit.addAll(currNode.inputs);
			}
		}
		return visited;
	}

	/**
	 * doOnePlayerOptimization
	 *
	 * This one-player optimization ignores unnecessary parts of the game.
	 * Useful for buttons and lights 25 and similar games.
	 */
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
		System.out.println("Removing " + toRemove.size() + " components - " + toRemove);
		for (Component c : toRemove) {
			propNet.removeComponent(c);
			if (inputLegalMap.containsKey(c)) {
				propNet.removeComponent(inputLegalMap.get(c));
			}
		}
	}

	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 *
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlSentences that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.  This is a naive implementation when coupled with
	 * setting input values, feel free to change this for a more efficient implementation.
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
	 * undirectedBfsFromNode
	 *
	 * Performs a breadth first search from a given proposition and returns a set of visited components.
	 * Unlike the one-player DFS, this keeps track of weather a connected component is "relevant" to our
	 * player.
	 */
	public Set<Component> undirectedBfsFromNode(Proposition p, Set<Component> allVisited, List<Boolean> wccIsRelevant, Role r) {
		assert !allVisited.contains(p);
		Set<Component> result = new HashSet<Component>();
		Queue<Component> frontier = new LinkedList<Component>();
		frontier.add(p);
		boolean isRelevant = false;
		while (!frontier.isEmpty()) {
			Component cur = frontier.poll();
			if (allVisited.contains(cur)) continue;
			// ignore the init proposition when determining WCCs
			if (cur.equals(propNet.getInitProposition())) continue;

			allVisited.add(cur);
			result.add(cur);
			if (propNet.getGoalPropositions().get(r).contains(cur) || propNet.getTerminalProposition().equals(cur)) {
				isRelevant = true;
			}

			propNet.getInputPropositions().get(r);
			frontier.addAll(cur.inputs);
			frontier.addAll(cur.outputs);
		}
		wccIsRelevant.add(isRelevant);
		return result;
	}

	/**
	 * factorSubgamesWcc
	 *
	 * Factors game into weakly connected components.
	 * This is useful for games like dual hamilton,
	 * dual hunter, dual rainbow, etc.
	 */
	public void factorSubgamesWCC(Role r) {
		Proposition term = propNet.getTerminalProposition();
		Set<Component> allVisited = new HashSet<Component>();
		List<Set<Component>> wccs = new ArrayList<Set<Component>>();
		List<Boolean> wccIsRelevant = new ArrayList<Boolean>();

		for (Proposition p : propNet.getAllGoalPropositions()) {
			if (allVisited.contains(p))
				continue;
			Set<Component> curWcc = undirectedBfsFromNode(p, allVisited, wccIsRelevant, r);
			wccs.add(curWcc);
		}
		Set<Component> curWcc = undirectedBfsFromNode(term, allVisited, wccIsRelevant, r);
		wccs.add(curWcc);

		int numR = 0;
		for (int i = 0; i < wccs.size(); i++) {
			if (!wccIsRelevant.get(i)) {
				for (Component c : wccs.get(i)) {
					propNet.removeComponent(c);
					numR ++;
				}
				// For debugging, render propnet to file here
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

		// If we removed information from an entire role, we must remove this
		// role from our game representation.
		Iterator<Role> it = propNet.roles.iterator();
		while (it.hasNext()) {
			Role role = it.next();
			if (!relevantRoles.contains(role)) {
				it.remove();
			}
		}
	}


	// TODO this is supposed to return a set of all step-related propositions
	// It is meant to go through all propositions and add them to a map based on keyword.
	// It then tries to figure out which set contains a set of propositions with consecutive numbers
	// using the formula 1 + 2 + ... + n = (n) * (n+1) / 2.
	//
	// Note: Andrew re-added this code from old propnet work that was not in our final player or propnet.
	public Set<Proposition> getStepCountProps(Set<Proposition> propositions) {
		Map<String, Set<Proposition>> keyWordPropMap = new HashMap<String, Set<Proposition>>();
		for (Proposition p : propositions) {
			String keyword = p.getName().getBody().get(0).toString();
			if (keyWordPropMap.containsKey(keyword)) {
				keyWordPropMap.get(keyword).add(p);
			} else {
				Set<Proposition> s = new HashSet<>();
				s.add(p);
				keyWordPropMap.put(keyword, s);
			}
		}
		for (String key : keyWordPropMap.keySet()) {
			Set<Proposition> s = keyWordPropMap.get(key);
			int size = s.size();
			int total = 0;
			for (Proposition p : s) {
				total += Integer.parseInt(p.getName().getBody().get(1).toString());
			}
			if (total == size * (size + 1) / 2) {
				// we have a set of consecutive propositions with the same keyword!
				return s;
			}
		}
		return null;
	}

	// This is a more naive implementation of a step count finder (compared to getStepCountProps)
	//
	// Also re-added by Andrew before final player submission
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

	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return propNet.getRoles();
	}

	/* Helper methods */
	// Brian's helper method
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

	// Brian's helper method
	private List<Gdl> sanitizeDistinct(List<Gdl> description) {
		List<Gdl> out = new ArrayList<>();
		for (int i = 0; i < description.size(); i++) {
			sanitizeDistinctHelper(description.get(i), description, out);
		}
		return out;
	}
}
