package org.ggp.base.util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
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

	public PropNet getPropnet() {
		return propNet;
	}

	MachineState init;
	final int NUM_THREADS = 1;

	public Proposition[] allBaseArr; // TODO we should get rid of this eventually
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
	BitSet compBits;
	BitSet nextBaseBits;
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
    	return (compState[thread][compId] & TRUE_INT) == TRUE_INT;
    }

    int num = 0;
    private void convertAndRender(String filename) {
    	for (Component c : propNet.getComponents()) {
    		c.curVal = val(componentIds.get(c), 0);
    	}
    	propNet.renderToFile(filename + ++num + ".dot");
    }

	@Override
	public void initialize(List<Gdl> description, Role r) {
		System.out.println("[PropNet] Initializing for role " + r);
		description = sanitizeDistinct(description);
		try {
			propNet = OptimizingPropNetFactory.create(description);
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);
			if (roles.length == 1 && propNet.getComponents().size() < 10000) { // TODO
				System.out.println("Trying to optimize");
				doOnePlayerOptimization();
			}

			// initialize all component states to 0
			int nComps = propNet.getComponents().size();
			int[] initCompState = new int[nComps];

			compBits = new BitSet(nComps);
		    nextBaseBits = new BitSet(nComps);
		    isBase = new BitSet(nComps);
		    isInput = new BitSet(nComps);

			allBaseArr = propNet.getAllBasePropositions().toArray(new Proposition[propNet.getAllBasePropositions().size()]);
			allInputArr = propNet.getInputPropositions().values().toArray(new Proposition[propNet.getAllInputProps().size()]);

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
				int numInputs = cur.inputs.size();
				int numOutputs = cur.outputs.size();
				initCompState[i] = FALSE_INT; // by default, regardless of type, init to false

				// Component type
				long curInfo = 0;
				if (cur instanceof Proposition) {
					if (propNet.getAllInputProps().contains(cur)) {
						curInfo |= INPUT_TYPE_MASK;
						isInput.set(i);
					}
					else if (propNet.getAllBasePropositions().contains(cur)) {
						curInfo |= BASE_TYPE_MASK;
						isBase.set(i);
					}
					else {
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
				// System.out.println("After component " + i + ", compInfo=" + curInfo);
				cur.crystalize(); // necessary for doInitWork and initforwardpropmark
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
			for (int i = 0; i < NUM_THREADS; i++) {
				System.arraycopy(initCompState, 0, compState[i], 0, nComps);
			}

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
		updatePropnetState(state);
		return val(terminalCompId, 0);
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
			boolean val = val(componentIds.get(p), 0);
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
				nextBaseBits.set(componentIds.get(base));
			}
			if (base.curVal) {
				compBits.set(componentIds.get(base));
			}
		}

		BitSet initBits = (BitSet) nextBaseBits.clone();

		if (propNet.getInitProposition() != null) {
			Set<Component> visited = new HashSet<Component>();
			initforwardpropmark(propNet.getInitProposition(), false, visited, componentIds);
		}

		// Now, copy the object propnet state into our representation
		for (Component c : propNet.getComponents()) {
			if (c.equals(propNet.getInitProposition())) continue;
			if (c instanceof And || c instanceof Or || c instanceof Not) {
				for (int ii = 0; ii < c.inputs.size(); ii ++) {
					if (c.input_arr[ii].curVal) {
						initCompState[componentIds.get(c)]++;
					}
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
	public List<Move> getLegalMoves(MachineState state, Role role)
			throws MoveDefinitionException {
		updatePropnetState(state);
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			int id = componentIds.get(p);
			if (val(id, 0)) {
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
		updatePropnetState(state);
		updatePropnetMoves(moves);
		MachineState m = new MachineState(nextBaseBits);
		return m;
	}

	public void initforwardpropmark(Component c, boolean newValue, Set<Component> visited, Map<Component, Integer> componentIds) {
		if (visited.contains(c)) return;
		visited.add(c);
		c.curVal = newValue;

		if (c.isBase) {
			if (newValue) compBits.set(componentIds.get(c));
			else compBits.clear(componentIds.get(c));
		} else if (c instanceof Transition) {
			// transitions always have exactly one output, or zero if pruned during factoring
			if (c.output_arr.length > 0) {
				if (newValue) nextBaseBits.set(componentIds.get(c.output_arr[0]));
				else nextBaseBits.clear(componentIds.get(c.output_arr[0]));
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
		int numOutputs = numOutputs(compId);
		int offset = outputOffset(compId);
		long type = compInfo[compId] & TYPE_MASK;
		boolean newValue = val(compId, thread);
		if (type == BASE_TYPE_MASK) {
			compBits.flip(compId);
		} else if (type == TRANSITION_TYPE_MASK) {
			if (numOutputs > 0) {
				nextBaseBits.flip(compOutputs[offset]);
			}
			return;
		}

		for (int i = offset; i < offset + numOutputs; i++) {
			int comp = compOutputs[i];
			boolean orig = val(comp, thread);
			compState[thread][comp] += newValue ? 1 : -1;
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
	public void updatePropnetState(MachineState state) {
		BitSet newBits = (BitSet) state.props.clone();
		newBits.xor(compBits);
		newBits.and(isBase);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			forwardpropmark(ii, !val(ii, 0), 0);
		}
	}

	Random r = new Random();
	@Override
	public MachineState internalDC(MachineState start)
			throws MoveDefinitionException, TransitionDefinitionException {
		while (!isTerminal(start)) {
			List<List<Move>> jmoves = getLegalJointMoves(start);
			List<Move> selected = jmoves.get(r.nextInt(jmoves.size()));
//			List<Move> selected = randomJointMove(start);
			start = internalNextState(start, selected);
		}
		return start;
	}

	// TODO does not work
	public List<Move> randomJointMove(MachineState state) throws MoveDefinitionException {
		List<Move> moves = new ArrayList<Move>();
		for (Role role : getRoles()) {
			List<Move> lms = internalLegalMoves(state, role);
			moves.add(lms.get(r.nextInt(lms.size())));
        }
		return moves;
	}

	// TODO make internal legal joint moves
	public List<Move> internalLegalMoves(MachineState state, Role role)
			throws MoveDefinitionException {
		updatePropnetState(state);
		List<Move> legalMoves = new ArrayList<Move>();
		Set<Proposition> legals = propNet.getLegalPropositions().get(role);
		for (Proposition p : legals) {
			int id = componentIds.get(p);
			if (val(id, 0)) {
				legalMoves.add(new Move(id));
			}
		}
		return legalMoves;
	}

	public MachineState internalNextState(MachineState state, List<Move> moves)
			throws TransitionDefinitionException {
		updatePropnetState(state);
		internalMoveUpdate(moves);
		MachineState m = new MachineState(nextBaseBits);
		return m;
	}

	public void internalMoveUpdate(List<Move> moves) {
		BitSet newBits = new BitSet(compInfo.length);
		for (Move m : moves) {
			newBits.set(m.compId);
		}
		newBits.xor(compBits);
		newBits.and(isInput);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			forwardpropmark(ii, !val(ii, 0), 0);
		}
		for (int ii = isInput.nextSetBit(0); ii != -1; ii = isInput.nextSetBit(ii + 1)) {
			if (newBits.get(ii)) compBits.flip(ii);
		}
	}

	// TODO: need to handle multiple threads
	public void updatePropnetMoves(List<Move> moves) {
		Set<GdlSentence> moveGdl = toDoes(moves);
		BitSet newBits = new BitSet(compInfo.length);
		for (GdlSentence s : moveGdl) {
			newBits.set(componentIds.get(propNet.getInputPropositions().get(s)));
		}
		newBits.xor(compBits);
		newBits.and(isInput);

		for (int ii = newBits.nextSetBit(0); ii != -1; ii = newBits.nextSetBit(ii + 1)) {
			forwardpropmark(ii, !val(ii, 0), 0);
		}
		for (int ii = isInput.nextSetBit(0); ii != -1; ii = isInput.nextSetBit(ii + 1)) {
			if (newBits.get(ii)) compBits.flip(ii);
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


	public Set<Component> dfs(Proposition p) {
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

	public void doOnePlayerOptimization() {
		Set<Component> important = new HashSet<Component>();
		important.addAll(dfs(propNet.getTerminalProposition()));
		for (Proposition p : propNet.getAllGoalPropositions()) {
			important.addAll(dfs(p));
		}
		for (Proposition p : propNet.getAllLegalPropositions()) {
			important.addAll(dfs(p));
		}
		for (Proposition p : propNet.getAllInputProps()) {
			important.addAll(dfs(p));
		}
		Set<Component> toRemove = new HashSet<Component>();
		for (Component c : propNet.getComponents()) {
			if (!important.contains(c)) {
				toRemove.add(c);
			}
		}
		for (Component c : toRemove) {
			propNet.removeComponent(c);
		}
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
}
