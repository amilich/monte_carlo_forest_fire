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

public class ExpFactorPropNet extends StateMachine {
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
	BitSet nextBaseBits;
	BitSet compBits;

	public Proposition[] allBaseArr = null;
	public Proposition[] allInputArr = null;
	public int allLongs[] = null;
	public Component[] allCompArr = null;

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
		// propNet.renderToFile("optimized.dot");
	}

	@Override
	public void initialize(List<Gdl> description, Role r) {
		System.out.println("[PropNet] Initializing for role " + r);
		description = sanitizeDistinct(description);
		try {
			propNet = OptimizingPropNetFactory.create(description);
			// propNet.renderToFile("start.dot");
			roles = propNet.getRoles().toArray(new Role[propNet.getRoles().size()]);
			if (roles.length == 1) {
				 doOnePlayerOptimization();
			}

			allBaseArr = propNet.getAllBasePropositions().toArray(new Proposition[propNet.getAllBasePropositions().size()]);
			allInputArr = propNet.getAllInputProps().toArray(new Proposition[propNet.getAllInputProps().size()]);
			for (int ii = 0; ii < allBaseArr.length; ii ++) {
				allBaseArr[ii].bitIndex = ii;
				allBaseArr[ii].isBase = true;
			}
			for (int ii = 0; ii < allInputArr.length; ii ++) {
				allInputArr[ii].bitIndex = ii;
			}

			baseBits = new BitSet(allBaseArr.length);
			nextBaseBits = new BitSet(allBaseArr.length);
			inputBits = new BitSet(allInputArr.length);

			for (Component c : propNet.getComponents()) {
				c.crystalize();
			}
			allCompArr = propNet.getComponents().toArray(new Component[propNet.getComponents().size()]);
			allLongs = new int[allCompArr.length];
			for (int ii = 0; ii < allCompArr.length; ii ++) {
				allCompArr[ii].compIndex = ii;
				if (allCompArr[ii] instanceof And) {
					allLongs[ii] = 0x80000000 - allCompArr[ii].input_arr.length;
				} else if (allCompArr[ii] instanceof Or) {
					allLongs[ii] = 0x7FFFFFFF;
				} else if (allCompArr[ii] instanceof Not) {
					allLongs[ii] = -1 * allCompArr[ii].input_arr.length;
				} else if (allCompArr[ii] instanceof Transition) {
					// empty - no counter needed
				} else if (allCompArr[ii] instanceof Constant) {
					// empty - no counter needed
				}
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
				initforwardpropmark(c, c.getValue());
			}
		}
		Set<Proposition> bases = propNet.getAllBasePropositions();

		/* for (int ii = 0; ii < ordering.size(); ii ++) {
			forwardpropmark(ordering.get(ii), ordering.get(ii).curVal, false);
		}*/
		for (Proposition base : bases) {
			initforwardpropmark(base, false);
		}

		if (propNet.getInitProposition() != null) {
			initforwardpropmark(propNet.getInitProposition(), true);
		}

		Set<GdlSentence> sentences = new HashSet<GdlSentence>();
		for (Proposition base : bases) {
			System.out.println(base);
			if (base.getSingleInput().getSingleInput().curVal) {
				sentences.add(base.getName());
				nextBaseBits.set(base.bitIndex);
			}
			if (base.curVal) {
				baseBits.set(base.bitIndex);
			}
		}

		if (propNet.getInitProposition() != null) {
			initforwardpropmark(propNet.getInitProposition(), false);
		}

		for (Component c : propNet.getComponents()) {
			if (c instanceof And || c instanceof Or || c instanceof Not) {
				for (int ii = 0; ii < c.inputs.size(); ii ++) {
					if (c.input_arr[ii].curVal) {
						allLongs[c.compIndex] ++;
					}
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

	public void initforwardpropmark(Component c, boolean newValue) {
		c.curVal = newValue;

		if (c.isBase) {
			if (newValue) baseBits.set(c.bitIndex);
			else baseBits.clear(c.bitIndex);
		} else if (c instanceof Transition) { // if c is a transition
			// transitions always have exactly one output
			if (c.output_arr.length > 0) {
				if (newValue) nextBaseBits.set(c.output_arr[0].bitIndex);
				else nextBaseBits.clear(c.output_arr[0].bitIndex);
			}
			return;
		}
		for (int jj = 0; jj < c.output_arr.length; jj ++) {
			Component out = c.output_arr[jj];
			if (out instanceof And) {
				if (!newValue) {
					initforwardpropmark(out, false);
				} else {
					boolean result = true;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (!out.input_arr[ii].curVal) {
							result = false;
							break;
						}
					}
					initforwardpropmark(out, result);
				}
			} else if (out instanceof Or) {
				if (newValue) {
					initforwardpropmark(out, true);
				} else {
					boolean result = false;
					for (int ii = 0; ii < out.inputs.size(); ii ++) {
						if (out.input_arr[ii].curVal) {
							result = true;
							break;
						}
					}
					initforwardpropmark(out, result);
				}
			} else if (out instanceof Not) {
				initforwardpropmark(out, !newValue);
			} else {
				initforwardpropmark(out, newValue);
			}
		}
	}

	public void forwardpropmark(Component c, boolean newValue) {
		if (newValue == c.curVal) {
			return; // stop forward propagating
		}
		c.curVal = newValue;
		if (c.isBase) {
			baseBits.flip(c.bitIndex);
		}

		if (c instanceof Transition) {
			nextBaseBits.flip(c.output_arr[0].bitIndex);
			return;
		}
		for (int jj = 0; jj < c.outputs.size(); jj ++) {
			Component out = c.output_arr[jj];
			allLongs[out.compIndex] += newValue? 1 : -1;
			if (out instanceof Proposition || out instanceof Transition) {
				forwardpropmark(out, newValue);
			} else {
				boolean corrected = ((allLongs[out.compIndex] >> 31) & 1) == 1;
				forwardpropmark(out, corrected);
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
			forwardpropmark(allBaseArr[ii], !baseBits.get(ii));
		}
	}

	public void updatePropnetMoves(List<Move> moves) {
		Set<GdlSentence> moveGdl = toDoes(moves);
		BitSet nowTrue = new BitSet(allInputArr.length);
		for (GdlSentence s : moveGdl) {
			Proposition p = propNet.getInputPropositions().get(s);
			if (p != null) nowTrue.set(propNet.getInputPropositions().get(s).bitIndex);
		}
		inputBits.xor(nowTrue);
		for (int ii = inputBits.nextSetBit(0); ii != -1; ii = inputBits.nextSetBit(ii + 1)) {
			forwardpropmark(allInputArr[ii], nowTrue.get(ii));
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
		System.out.println("Sort start");
		// List to contain the topological ordering.
		List<Proposition> order = new ArrayList<Proposition>();

		// All of the components in the PropNet
		 List<Component> components = new ArrayList<Component>(propNet.getComponents());

		// All of the propositions in the PropNet.
		List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());

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
		System.out.println("Sort done");
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