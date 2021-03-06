import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

// Graph based MCTS Node
public class MachineLessNode {
	// Depth charging parameters/objects
	public static final int NUM_THREADS = 3;
	public static final int NUM_DEPTH_CHARGES = 3; // TODO
	Charger rs[] = new Charger[NUM_THREADS];

	public static HashMap<MachineState, MachineLessNode> stateMap = new HashMap<MachineState, MachineLessNode>();
	public static int numCharges = 0;
	public double utility = 0;

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
	private MachineLessNode[][] children;

	// Move/state information
	private int numMoves;
	private int numEnemyMoves;
	private MachineState state;

	// Static graph variables
	static Role player;
	static int roleIndex = -1;
	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	// Set the static role information for the graph
	public static void setRole(Role role) {
		MachineLessNode.player = role;
	}

	// Node constructor
	public MachineLessNode(MachineState state, StateMachine machine)
			throws MoveDefinitionException {
		this.state = state;
		if (machine.isTerminal(state)) {
			this.numMoves = 0;
			this.numEnemyMoves = 0;
		} else {
			List<Move> myMoves = machine.getLegalMoves(state, player);
			this.numMoves = myMoves.size();
			this.numEnemyMoves = machine.getLegalJointMoves(state, player, myMoves.get(0)).size();
		}
		pCounts = new double[numMoves];
		pVals = new double[numMoves];
		oCounts = new double[numMoves][numEnemyMoves];
		oVals = new double[numMoves][numEnemyMoves];
		children = new MachineLessNode[numMoves][numEnemyMoves];
	}

	// Add the values of an array
	private double sumArray(double array[]) {
		double total = 0;
		for (int ii = 0; ii < array.length; ii ++) total += array[ii];
		return total;
	}

	// Return the best move available from the current state.
	public Move getBestMove(StateMachine machine) throws MoveDefinitionException {
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
		return machine.getLegalMoves(state, player).get(maxMove);
	}

	// Perform selection and expansion for a MCTS node.
	public MachineLessNode selectAndExpand(ArrayList<MachineLessNode> path, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		MachineLessNode selected = this.select(path, machine);
		MachineLessNode expanded = selected.expand(machine);
		if (!expanded.equals(path.get(path.size() - 1))) path.add(expanded); // May have expanded itself (and not added new node)
		return expanded;
	}

	// Two select functions are presented. One uses a generic constant, and the other uses the standard deviation
	// of the depth charges from a particular node.
	static final int C = 40;
	static final double C1 = 0.8;
	protected double opponentSelectFn(int pMove, int oMove, MachineLessNode n) {
//		double stddev = Math.sqrt((n.s0 * n.s2 - n.s1 * n.s1) / (n.s0 * (n.s0 - 1)));
//		if (Double.isNaN(stddev)) {
//			stddev = C;
//		}
//		return -1 * oVals[pMove][oMove] / oCounts[pMove][oMove] +
//				Math.sqrt(C1 * stddev * Math.log(sumArray(oCounts[pMove]) / oCounts[pMove][oMove]));
		return -1 * oVals[pMove][oMove] / oCounts[pMove][oMove] +
				Math.sqrt(C * Math.log(sumArray(oCounts[pMove]) / oCounts[pMove][oMove]));
	}
	protected double selectfn(int pMove, int oMove) {
		return pVals[pMove] / pCounts[pMove] + Math.sqrt(C * Math.log(sumArray(pCounts)) / pCounts[pMove]);
	}

	// MCTS selection
	public MachineLessNode select(ArrayList<MachineLessNode> path, StateMachine machine) {
		MachineLessNode currNode = this;
		while (true) {
			path.add(currNode);
			if (machine.isTerminal(currNode.state)) return currNode;
			for (int ii = 0; ii < currNode.numMoves; ii ++){
				for (int jj = 0; jj < currNode.numEnemyMoves; jj ++) {
					if (currNode.children[ii][jj] == null) return currNode;
				}
			}
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
				 // if (children[resultP][jj].explored) newscore = -100; // TODO, solver
				 // else newscore = selectfn(resultP, jj, true);
				if (newscore > oMoveScore) {
					oMoveScore = newscore;
					resultO = jj;
				}
			}
			currNode = currNode.children[resultP][resultO];
		}
	}

	// Perform MCTS expansion
	public MachineLessNode expand(StateMachine machine) throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) return this;
		for (int ii = 0; ii < numMoves; ii ++) {
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] == null) {
					Move myMove = machine.getLegalMoves(state, player).get(ii);
					List<Move> jointMove = machine.getLegalJointMoves(state, player, myMove).get(jj);
					try {
						MachineState nextState = machine.getNextState(state, jointMove);
						if (stateMap.containsKey(nextState)) children[ii][jj] = stateMap.get(nextState);
						else {
							children[ii][jj] = new MachineLessNode(nextState, machine);
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

	// Get the index for this particular role in the state machine
	public static int getRoleIndex(StateMachine machine) {
		List<Role> roles = machine.getRoles();
		for (int ii = 0; ii < roles.size(); ii ++) if (roles.get(ii).equals(player)) return ii;
		return -1;
	}

	// Pair of two integers (symbolizes joint move). Used for backpropagation.
	private class Pair {
		int first;
		int second;
		public Pair(int one, int two) { this.first = one; this.second = two; };
	}

	// Get the identifiers for a particular joint move. Used for backpropagation.
	private Pair getMoveIndex(MachineLessNode parent, MachineLessNode child) {
		for (int ii = 0; ii < parent.numMoves; ii ++) {
			for (int jj = 0; jj < parent.numEnemyMoves; jj ++) {
				if (parent.children[ii][jj] != null) {
					if (parent.children[ii][jj].equals(child)) return new Pair(ii, jj);
				}
			}
		}
		return null;
	}

	// Backpropagate a score through the path taken in the graph.
	public void backpropagate(ArrayList<MachineLessNode> path, double score, StateMachine machine) {
		boolean onePlayer = machine.getRoles().size() == 1;
		if (path.size() < 2) {
			System.out.println("GraphNode backprop error: path length < 2");
			return;
		}
		for (int ii = path.size() - 2; ii >= 0; ii --) {
			Pair pair = getMoveIndex(path.get(ii), path.get(ii + 1));
			if (onePlayer) {
				path.get(ii).pCounts[pair.first] ++;
				path.get(ii).pVals[pair.first] += score;
				// No opponent
			} else {
				path.get(ii).pCounts[pair.first] ++;
				path.get(ii).pVals[pair.first] += score;
				path.get(ii).oCounts[pair.first][pair.second] ++;
				path.get(ii).oVals[pair.first][pair.second] += score;
			}
		}
	}

	static final int NUM_SIMP = 4;
	public double simulate(StateMachine machine)
			throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if (machine.isTerminal(state)) {
			explored = true;
			return machine.getGoal(state, player);
		}
		if (roleIndex < 0) roleIndex = getRoleIndex(machine);
		double avgScore = 0;
		// long t1 = System.nanoTime();
		for (int ii = 0; ii < NUM_SIMP; ii ++) {
			MachineState m = machine.performDepthCharge(state, null);
			avgScore += machine.getGoal(m, player);
		}
		// long t2 = System.nanoTime();
		// System.out.println("Dc took " + (t2 - t1) + " nanoseconds");
		numCharges += NUM_SIMP;
		avgScore /= NUM_SIMP;
		return avgScore;
	}

	// Used to move the root onward after a move
	public MachineLessNode findMatchingState(MachineState currentState) {
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] != null) if (children[ii][jj].state.equals(currentState)) return children[ii][jj];
			}
		}
		return null;
	}
}
