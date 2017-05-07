import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
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

public class ThreadedGraphNode {
	public static HashMap<MachineState, ThreadedGraphNode> stateMap = new HashMap<MachineState, ThreadedGraphNode>();
	public static int numCharges = 0;
	public double utility = 0;

	boolean explored = false;
	private ThreadedGraphNode parent;
	private MachineState state;

	// Scores, counts, and children
	private double[] pCounts;
	private double[] pVals;
	private double[][] oCounts;
	private double[][] oVals;
	private ThreadedGraphNode[][] children;

	// Move information
	private int numMoves;
	private int numEnemyMoves;
	private int moveIndex;
	private int enemyMoveIndex;

	// Static graph variables
	static Role player;
	static int roleIndex = -1;
	static StateMachine machine;
	static List<StateMachine> machines;
	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	public static void setStateMachine(StateMachine machine) {
		ThreadedGraphNode.machine = machine;
	}

	public static void setStateMachines(List<StateMachine> machines) {
		ThreadedGraphNode.machines = machines;
	}

	public static void setRole(Role role) {
		ThreadedGraphNode.player = role;
	}

	public ThreadedGraphNode(MachineState state)
			throws MoveDefinitionException {
		this(state, null, -1, -1);
	}

	public ThreadedGraphNode(MachineState state, ThreadedGraphNode parent, int moveIndex, int enemyMoveIndex)
			throws MoveDefinitionException {
		this.state = state;
		this.parent = parent;
		this.moveIndex = moveIndex;
		this.enemyMoveIndex = enemyMoveIndex;
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
		children = new ThreadedGraphNode[numMoves][numEnemyMoves];
	}

	private double sumArray(double array[]) {
		double total = 0;
		for (int ii = 0; ii < array.length; ii ++) {
			total += array[ii];
		}
		return total;
	}

	static final int C = 40;
	private double selectfn(int pMove, int oMove, boolean opponent) {
		if (opponent) {
			return -1 * oVals[pMove][oMove] / oCounts[pMove][oMove] + C * Math.sqrt(Math.log(sumArray(oCounts[pMove]) / oCounts[pMove][oMove]));
		} else {
			return pVals[pMove] / pCounts[pMove] + C * Math.sqrt(Math.log(sumArray(pCounts)) / pCounts[pMove]);
		}
	}

	public Move getBestMove() throws MoveDefinitionException {
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

	public ThreadedGraphNode selectAndExpand(ArrayList<ThreadedGraphNode> path)
			throws MoveDefinitionException, TransitionDefinitionException {
		ThreadedGraphNode selected = this.select(path);
		ThreadedGraphNode expanded = selected.expand();
		if (!expanded.equals(path.get(path.size() - 1))) path.add(expanded); // May have expanded itself (and not added new node)
		return expanded;
	}

	public ThreadedGraphNode select(ArrayList<ThreadedGraphNode> path) {
		path.add(this);
		if (machine.isTerminal(state)) return this;
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (this.children[ii][jj] == null) return this;
			}
		}
		double pMoveScore = Double.NEGATIVE_INFINITY;
		double oMoveScore = Double.NEGATIVE_INFINITY;
		int resultP = 0;
		int resultO = 0;
		for (int ii = 0; ii < numMoves; ii ++){
			double newscore = selectfn(ii, -1, false);
			if (newscore > pMoveScore) {
				pMoveScore = newscore;
				resultP = ii;
			}
		}
		for (int jj = 0; jj < numEnemyMoves; jj ++) {
			double newscore = selectfn(resultP, jj, true);
			 // if (children[resultP][jj].explored) newscore = -100; // TODO
			 // else newscore = selectfn(resultP, jj, true);
			if (newscore > oMoveScore) {
				oMoveScore = newscore;
				resultO = jj;
			}
		}
		return children[resultP][resultO].select(path);
	}

	public ThreadedGraphNode expand() throws MoveDefinitionException, TransitionDefinitionException {
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
							children[ii][jj] = new ThreadedGraphNode(nextState, this, ii, jj);
							stateMap.put(nextState, children[ii][jj]);
						}
						return children[ii][jj];
					} catch (Exception e) {
						System.out.println("Expansion failed");
						e.printStackTrace(); // TODO what's going on
					}
					return this;
				}
			}
		}
		System.out.println("Failed to expand child node");
		return this; // TODO
	}

	public static int getRoleIndex() {
		List<Role> roles = machine.getRoles();
		for (int ii = 0; ii < roles.size(); ii ++) {
			if (roles.get(ii).equals(player)) return ii;
		}
		return -1;
	}

	public void backpropagate(ArrayList<ThreadedGraphNode> path, double score) {
		boolean onePlayer = machine.getRoles().size() == 1;
		int tempMoveIndex = path.get(path.size() - 1).moveIndex; // Starting at end of path
		int tempEnemyMoveIndex = path.get(path.size() - 1).enemyMoveIndex;
		if (path.size() < 2) {
			System.out.println("GraphNode backprop error: path length < 2");
			return;
		}
		for (int ii = path.size() - 2; ii >= 0; ii --) {
			if (onePlayer) {
				path.get(ii).pCounts[tempMoveIndex] ++;
				path.get(ii).pVals[tempMoveIndex] += score;
				// No opponent
			} else {
				path.get(ii).pCounts[tempMoveIndex] ++;
				path.get(ii).pVals[tempMoveIndex] += score;
				path.get(ii).oCounts[tempMoveIndex][tempEnemyMoveIndex] ++;
				path.get(ii).oVals[tempMoveIndex][tempEnemyMoveIndex] += score;
			}
			tempMoveIndex = path.get(ii).moveIndex;
			tempEnemyMoveIndex = path.get(ii).enemyMoveIndex;
		}
	}

	public static final int NUM_THREADS = 4; // EVEN NUMBER!!
	static final int NUM_DEPTH_CHARGES = 4; // TODO
	public double simulate() // Check if immediate next state is terminal TODO
			throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		if (machine.isTerminal(state)) {
			explored = true;
			return machine.getGoal(state, player);
		}

		List<Charger> rs = new ArrayList<Charger>();
		Collection<Future<?>> futures = new LinkedList<Future<?>>();
		for (int ii = 0; ii < NUM_THREADS; ii ++) {
			DepthCharger d = new DepthCharger(machines.get(ii), state, player, NUM_DEPTH_CHARGES, true);
			rs.add(d);
			futures.add(executor.submit(d));
		}

		for (Future<?> future:futures) {
			try {
				future.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		double avgScore = 0;

		if (roleIndex < 0) {
			roleIndex = getRoleIndex();
		}
		int index = roleIndex;
		for (int ii = 0; ii < NUM_THREADS; ii ++)
			avgScore += rs.get(ii).getValues()[index];
		avgScore /= NUM_THREADS;
		numCharges += NUM_DEPTH_CHARGES * NUM_THREADS;
		return avgScore;
	}

	public ThreadedGraphNode getParent() {
		return parent;
	}

	public void setParent(ThreadedGraphNode parent) {
		this.parent = parent;
	}

	public MachineState getState() {
		return state;
	}

	public void setState(MachineState state) {
		this.state = state;
	}

	public ThreadedGraphNode findMatchingState(MachineState currentState) {
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] != null) {
					if (children[ii][jj].state.equals(currentState)) return children[ii][jj];
				}
			}
		}
		return null;
	}
}
