import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.IntPropNet;

/**
 * ThreadedGraphNode
 * -----------------
 * Node used for MCTS tree.
 *
 * @author monte_carlo_forest_fire
 */
public class ThreadedGraphNode {
	// Depth charging parameters/objects
	public static final int NUM_THREADS = IntPropNet.NUM_THREADS;
	public static final int NUM_DEPTH_CHARGES = 4;
	Charger rs[] = new Charger[NUM_THREADS];
	public static int num = 0;

	public static HashMap<MachineState, ThreadedGraphNode> stateMap = new HashMap<MachineState, ThreadedGraphNode>();
	public static int numCharges = 0;
	public double utility = -1;
	public static Role enemy;
	public static boolean usingProver = false;

	// Variables used to calculate standard deviation
	// http://stackoverflow.com/questions/5543651/computing-standard-deviation-in-a-stream
	protected double s0 = 0;
	protected double s1 = 0;
	protected double s2 = 0;

	// For "Solver"
	protected boolean explored = false;

	// Scores, counts, and children
	private double[] pCounts;
	private double[] pVals;
	private double[][] oCounts;
	private double[][] oVals;
	private ThreadedGraphNode[][] children;

	// Move/state information
	private int numMoves;
	private int numEnemyMoves;
	private MachineState state;
	private int pMobility;
	private int pGoal;

	// Store terminal value instead of recomputing
	private boolean isTerminal = false;

	// Static graph variables
	public static int roleIndex = -1;
	public static List<StateMachine> machines = null;
	static Role player;
	static StateMachine machine;
	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();
	private List<Move> myMoves; // "player's" available moves at this node
	private String myStr = "Parent"; // Used for exploring tree in debugger

	/**
	 * setStateMachine
	 *
	 * Set the static state machine used to create nodes.
	 */
	public static void setStateMachine(StateMachine machine) {
		ThreadedGraphNode.machine = machine;
	}

	/**
	 * setRole
	 *
	 * Set the static role information for the graph
	 */
	public static void setRole(Role role) {
		ThreadedGraphNode.player = role;
	}

	/**
	 * ThreadedGraphNode
	 *
	 * Node constructor given a string (representing move that led to the node).
	 */
	public ThreadedGraphNode(MachineState state, String toStr)
			throws MoveDefinitionException {
		this(state);
		myStr = toStr;
	}

	/**
	 * ThreadedGraphNode
	 *
	 * Node constructor given a machine state.
	 */
	public ThreadedGraphNode(MachineState state)
			throws MoveDefinitionException {
		this.state = state;
		num ++; // Number of nodes created
		isTerminal = machine.isTerminal(state);
		if (isTerminal) {
			this.numMoves = 0;
			this.numEnemyMoves = 0;
		} else {
			this.myMoves = machine.getLegalMoves(state, player);
			this.numMoves = myMoves.size();
			this.numEnemyMoves = machine.getLegalJointMoves(state, player, myMoves.get(0)).size();
		}
		pCounts = new double[numMoves];
		pVals = new double[numMoves];
		oCounts = new double[numMoves][numEnemyMoves];
		oVals = new double[numMoves][numEnemyMoves];
		children = new ThreadedGraphNode[numMoves][numEnemyMoves];
		pMobility = (int) (100.0 * numMoves / machine.findActions(player).size());
		try {
			pGoal = machine.getGoal(state, player);
		} catch (GoalDefinitionException e) {
			e.printStackTrace();
		}
	}

	/**
	 * sumArray
	 *
	 * Add the values of an array
	 */
	private double sumArray(double array[]) {
		double total = 0;
		for (int ii = 0; ii < array.length; ii ++) total += array[ii];
		return total;
	}

	/**
	 * getBestUtility
	 *
	 * Get the utility of the best move available from this node.
	 */
	public double getBestUtility() throws MoveDefinitionException {
		double maxUtility = 0;
		for (int ii = 0; ii < pVals.length; ii ++) {
			double tempUtility = pVals[ii] / pCounts[ii];
			if (tempUtility > maxUtility) {
				maxUtility = tempUtility;
			}
		}
		return maxUtility;
	}

	/**
	 * getBestMove
	 *
	 * Return the best move available from the current state.
	 */
	public Move getBestMove() throws MoveDefinitionException {
		if (explored) {
			double maxUtility = 0;
			int maxI = 0;
			for (int ii = 0; ii < this.numMoves; ii ++){
				for (int jj = 0; jj < this.numEnemyMoves; jj ++) {
					if (children[ii][jj].utility > maxUtility) {
						maxUtility = children[ii][jj].utility;
						maxI = ii;
					}
				}
			}
			System.out.println("[GRAPH] Solved utility of best move = " + maxUtility);
			return myMoves.get(maxI);
		} else {
			double maxUtility = 0;
			int maxMove = 0;
			for (int ii = 0; ii < pVals.length; ii ++) {
				double tempUtility = pVals[ii] / pCounts[ii];
				if (tempUtility > maxUtility) {
					maxUtility = tempUtility;
					maxMove = ii;
				}
			}
			System.out.println("[GRAPH] Utility of best move = " + maxUtility);
			if (maxUtility == 0) {
				Random r = new Random();
				return myMoves.get(r.nextInt(myMoves.size()));
			}
			return myMoves.get(maxMove);
		}
	}

	/**
	 * selectAndExpand
	 *
	 * Perform selection and expansion for a MCTS node.
	 */
	public ThreadedGraphNode selectAndExpand(ArrayList<ThreadedGraphNode> path)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		ThreadedGraphNode selected = this.select(path);
		ThreadedGraphNode expanded = selected.expand();
		if (!expanded.equals(path.get(path.size() - 1))) path.add(expanded); // May have expanded itself (and not added new node)
		return expanded;
	}

	// Constants used for select function
	public static double Csp = 300000;
	public static boolean heuristicEnable = false;
	public static double mobilityCorr = 0.0;
	public static double goalCorr = 0.0;
	static final int C = 50;
	static final double C1 = 0.85;
	static final double C2 = 0.2;
	// Two select functions are presented. One uses a generic constant, and the other uses the standard deviation
	// of the depth charges from a particular node.

	/**
	 * opponentSelectFn
	 *
	 * Used to select opponent move.
	 */
	protected double opponentSelectFn(int pMove, int oMove, ThreadedGraphNode n) throws MoveDefinitionException {
		double stddev = Math.sqrt((n.s0 * n.s2 - n.s1 * n.s1) / (n.s0 * (n.s0 - 1)));
		if (Double.isNaN(stddev)) {
			stddev = C;
		}
		if (heuristicEnable) {
			return -1 * oVals[pMove][oMove] / oCounts[pMove][oMove]
					+ Math.sqrt(C1 * stddev * Math.log(sumArray(oCounts[pMove]) / oCounts[pMove][oMove]))
					+ C2 * mobilityCorr * pMobility
					+ C2 * goalCorr * pGoal;
		} else {
			return -1 * oVals[pMove][oMove] / oCounts[pMove][oMove]
					+ Math.sqrt(C1 * stddev * Math.log(sumArray(oCounts[pMove]) / oCounts[pMove][oMove]));
		}
	}

	/**
	 * selectfn
	 *
	 * Used to select our move.
	 */
	protected double selectfn(int pMove, int oMove) throws GoalDefinitionException {
		return pVals[pMove] / pCounts[pMove] + Math.sqrt(C * Math.log(sumArray(pCounts)) / pCounts[pMove]);
	}

	/**
	 * singlePSelect
	 *
	 * Used to select our move in a single player game (uses different C value).
	 */
	protected double singlePSelect(int pMove) throws GoalDefinitionException {
		return pVals[pMove] / pCounts[pMove] + Math.sqrt(Csp * Math.log(sumArray(pCounts)) / pCounts[pMove]);
	}

	/**
	 * select
	 *
	 * MCTS select function, iteratively implemented.
	 */
	public ThreadedGraphNode select(ArrayList<ThreadedGraphNode> path) throws GoalDefinitionException, MoveDefinitionException {
		ThreadedGraphNode currNode = this;
		while (true) {
			path.add(currNode);
			if (currNode.isTerminal) return currNode;
			for (int ii = 0; ii < currNode.numMoves; ii ++){
				for (int jj = 0; jj < currNode.numEnemyMoves; jj ++) {
					if (currNode.children[ii][jj] == null) return currNode;
				}
			}
			if (machine.findRoles().size() == 1) {
				double pMoveScore = Double.NEGATIVE_INFINITY;
				int resultP = 0;
				for (int ii = 0; ii < currNode.numMoves; ii ++){
					double newscore = currNode.singlePSelect(ii);
					if (newscore > pMoveScore) {
						pMoveScore = newscore;
						resultP = ii;
					}
				}
				currNode = currNode.children[resultP][0];
			} else {
				double pMoveScore = Double.NEGATIVE_INFINITY;
				double oMoveScore = Double.NEGATIVE_INFINITY;
				int resultP = 0;
				int resultO = 0;
				for (int ii = 0; ii < currNode.numMoves; ii ++){
					double newscore = currNode.selectfn(ii, -1);
					if (newscore > pMoveScore) {
						pMoveScore = newscore;
						resultP = ii;
					}
				}
				for (int jj = 0; jj < currNode.numEnemyMoves; jj ++) {
					double newscore = currNode.opponentSelectFn(resultP, jj, currNode.children[resultP][jj]);
					if (newscore > oMoveScore) {
						oMoveScore = newscore;
						resultO = jj;
					}
				}
				currNode = currNode.children[resultP][resultO];
			}
		}
	}

	/**
	 * expand
	 *
	 * MCTS expansion function. Expands NULL child node.
	 */
	public ThreadedGraphNode expand() throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) return this;
		for (int ii = 0; ii < numMoves; ii ++) {
			Move myMove = myMoves.get(ii);
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] == null) {
					List<List<Move>> jMoves = machine.getLegalJointMoves(state, player, myMove);
					List<Move> jointMove = jMoves.get(jj);
					try {
						MachineState nextState = machine.getNextState(state, jointMove);
						if (stateMap.containsKey(nextState)) {
							children[ii][jj] = stateMap.get(nextState);
						} else {
							children[ii][jj] = new ThreadedGraphNode(nextState, nextState.toString());
							stateMap.put(nextState, children[ii][jj]);
						}
						return children[ii][jj];
					} catch (Exception e) {
						System.out.println("Expansion failed");
						e.printStackTrace();
					}
					return this;
				}
			}
		}
		System.out.println("Failed to expand child node");
		return this;
	}

	/**
	 * getRoleIndex
	 *
	 * Get the index for this particular role in the state machine.
	 */
	public static int getRoleIndex() {
		List<Role> roles = machine.getRoles();
		for (int ii = 0; ii < roles.size(); ii ++) if (roles.get(ii).equals(player)) return ii;
		return -1;
	}

	/**
	 * Class: Pair
	 *
	 * Pair of two integers (symbolizes joint move). Used for backpropagation.
	 */
	private class Pair {
		int first;
		int second;
		public Pair(int one, int two) { this.first = one; this.second = two; };
	}

	/**
	 * getMoveIndex
	 *
	 * Get the identifiers for a particular joint move. Used for backpropagation.
	 */
	private Pair getMoveIndex(ThreadedGraphNode parent, ThreadedGraphNode child) {
		for (int ii = 0; ii < parent.numMoves; ii ++) {
			for (int jj = 0; jj < parent.numEnemyMoves; jj ++) {
				if (parent.children[ii][jj] != null) {
					if (parent.children[ii][jj].equals(child)) return new Pair(ii, jj);
				}
			}
		}
		return null;
	}

	/**
	 * updateSolved
	 *
	 * Updates a node's status to solved if its children are.
	 */
	public boolean updateSolved(ThreadedGraphNode n) {
		if (n.isTerminal) return true;
		double maxUtility = 0;
		for (int ii = 0; ii < n.numMoves; ii ++) {
			for (int jj = 0; jj < n.numEnemyMoves; jj ++) {
				if (n.children[ii][jj] == null) {
					return false;
				}
				if (!n.children[ii][jj].explored) {
					return false;
				}
				if (n.children[ii][jj].utility > maxUtility) {
					maxUtility = n.children[ii][jj].utility;
				}
			}
		}
		n.explored = true;
		n.utility = maxUtility;
		return true;
	}

	/**
	 * backpropagate
	 *
	 * Iterative MCTS backpropagation.
	 * Backpropagates a score through the path taken in the graph.
	 */
	public static final int TOT_CHARG = NUM_THREADS * NUM_DEPTH_CHARGES;
	public void backpropagate(ArrayList<ThreadedGraphNode> path, double score) {
		boolean onePlayer = machine.getRoles().size() == 1;
		if (path.size() < 2) {
			System.out.println("GraphNode backprop error: path length < 2");
			return;
		}
		for (int ii = path.size() - 2; ii >= 0; ii --) {
			Pair pair = getMoveIndex(path.get(ii), path.get(ii + 1));
			if (onePlayer) { // No opponent
				path.get(ii).pCounts[pair.first] += TOT_CHARG;
				path.get(ii).pVals[pair.first] += score * TOT_CHARG;
			} else {
				path.get(ii).pCounts[pair.first] += TOT_CHARG;
				path.get(ii).pVals[pair.first] += score * TOT_CHARG;
				path.get(ii).oCounts[pair.first][pair.second] += TOT_CHARG;
				path.get(ii).oVals[pair.first][pair.second] += score * TOT_CHARG;
			}
		}
	}

	static final boolean MULTI_THREADED = false;
	static final int NUM_SIMP = 4; // Number of charges to perform if not multithreading
	/**
	 * simulate
	 *
	 * MCTS simulation function.
	 */
	public double simulate()
			throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException, InterruptedException, ExecutionException {
		if (machine.isTerminal(state)) {
			explored = true;
			if (utility < 0) utility = machine.getGoal(state, player);
			return utility;
		}
		if (roleIndex < 0) roleIndex = getRoleIndex();
		if (MULTI_THREADED) {
			double avgScore = 0;
			for (int ii = 0; ii < NUM_SIMP; ii ++) {
				MachineState m = machine.performDepthCharge(state, null);
				avgScore += machine.getGoal(m, player);
			}
			numCharges += NUM_SIMP;
			avgScore /= NUM_SIMP;
			return avgScore;
		} else {
			Set<Future<Double>> futures = new HashSet<Future<Double>>();
			for (int ii = 0; ii < NUM_THREADS; ii ++) {
				if (usingProver) {
					Future<Double> future = executor.submit(new DepthCharger(machines.get(ii), state, player, NUM_DEPTH_CHARGES, ii));
					futures.add(future);
				} else {
					Future<Double> future = executor.submit(new DepthCharger(machine, state, player, NUM_DEPTH_CHARGES, ii));
					futures.add(future);
				}
			}
			double avgScore = 0;
			for (Future<Double> future : futures) {
				double val = future.get().doubleValue();
				avgScore += val;
				s0 ++;
				s1 += val;
				s2 += val * val;
			}
			avgScore /= NUM_THREADS;
			numCharges += NUM_DEPTH_CHARGES * NUM_THREADS;
			return avgScore;
		}
	}

	/**
	 * findMatchingState
	 *
	 * Used to move the root onward after a move
	 */
	public ThreadedGraphNode findMatchingState(MachineState currentState) {
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] != null) if (children[ii][jj].state.equals(currentState)) return children[ii][jj];
			}
		}
		return null;
	}

	/**
	 * toString
	 *
	 * String representation of node.
	 */
	@Override
	public String toString() {
		return myStr;
	}
}
