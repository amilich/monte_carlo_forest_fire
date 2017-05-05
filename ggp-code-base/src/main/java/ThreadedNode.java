import java.util.ArrayList;
import java.util.Collection;
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

public class ThreadedNode {
	public static int numCharges = 0;

	private ThreadedNode parent;
	private MachineState state;
	private double[] pCounts;
	private double[] pVals;
	private double[] oCounts;
	private double[] oVals;
	private ThreadedNode[][] children;
	private int numMoves;
	private int numEnemyMoves;
	boolean explored = false;

	private int moveIndex;
	private int enemyMoveIndex;
	static StateMachine machine;
	static List<StateMachine> machines;
	static Role player;
	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	public static void setStateMachine(StateMachine machine) {
		ThreadedNode.machine = machine;
	}

	public static void setStateMachines(List<StateMachine> machines) {
		ThreadedNode.machines = machines;
	}

	public static void setRole(Role role) {
		ThreadedNode.player = role;
	}

	public ThreadedNode(MachineState state)
			throws MoveDefinitionException {
		this(state, null, -1, -1);
	}

	public ThreadedNode(MachineState state, ThreadedNode parent, int moveIndex, int enemyMoveIndex)
			throws MoveDefinitionException { // TODO try/catch
		// System.out.println("Creating new node, parent = " + parent + ", movei = " + moveIndex + ", emove = " + enemyMoveIndex);
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
		// System.out.println("nm = " + numMoves + ", nem = " + numEnemyMoves);
		pCounts = new double[numMoves];
		pVals = new double[numMoves];
		oCounts = new double[numEnemyMoves];
		oVals = new double[numEnemyMoves];
		children = new ThreadedNode[numMoves][numEnemyMoves];
	}

	private double sumArray(double array[]) {
		double total = 0;
		for (int ii = 0; ii < array.length; ii ++) {
			total += array[ii];
		}
		return total;
	}

	private double selectfn(int moveNum, boolean opponent) {
		if (opponent) {
			return oVals[moveNum] / oCounts[moveNum] + Math.sqrt(2 * Math.log(sumArray(oCounts)) / oCounts[moveNum]);
		} else {
			return pVals[moveNum] / pCounts[moveNum] + Math.sqrt(2 * Math.log(sumArray(pCounts)) / pCounts[moveNum]);
		}
	}

	public Move getBestMove() throws MoveDefinitionException {
		double avgUtility = 0;
		int maxMove = 0;
		for (int ii = 0; ii < pCounts.length; ii ++) {
			double tempAvg = pVals[ii] / pCounts[ii];
			if (tempAvg > avgUtility) {
				avgUtility = tempAvg;
				maxMove = ii;
			}
		}
		System.out.println("[THREADED] Avg utility of best move = " + avgUtility);
		return machine.getLegalMoves(state, player).get(maxMove);
	}

	public ThreadedNode selectAndExpand() throws MoveDefinitionException, TransitionDefinitionException {
		ThreadedNode selected = this.select();
		return selected.expand();
	}

	public ThreadedNode select() {
		if (machine.isTerminal(state)) return this; // TODO
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (this.children[ii][jj] == null) return this;
			}
		}
		double pMoveScore = 0;
		double oMoveScore = 0;
		int resultP = 0;
		int resultO = 0;
		for (int ii = 0; ii < numMoves; ii ++){
			double newscore = selectfn(ii, false);
			if (newscore > pMoveScore) {
				pMoveScore = newscore;
				resultP = ii;
			}
		}
		for (int jj = 0; jj < numEnemyMoves; jj ++) {
			double newscore = selectfn(jj, true);
			if (newscore > oMoveScore) {
				oMoveScore = newscore;
				resultO = jj;
			}
		}
		return children[resultP][resultO].select();
	}

	public ThreadedNode expand() throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) return this;
		for (int ii = 0; ii < numMoves; ii ++) {
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] == null) {
					Move myMove = machine.getLegalMoves(state, player).get(ii);
					try {
						List<Move> jointMove = machine.getLegalJointMoves(state, player, myMove).get(jj);
						MachineState nextState = machine.getNextState(state, jointMove);
						children[ii][jj] = new ThreadedNode(nextState, this, ii, jj);
						return children[ii][jj];
					} catch (Exception e) {
						System.out.println("expansion failed");
						e.printStackTrace(); // TODO what's going on
					}
					return this;
				}
			}
		}
		System.out.println("Failed to expand child node");
		return this; // TODO
	}

	public int getRoleIndex() {
		List<Role> roles = machine.getRoles();
		for (int ii = 0; ii < roles.size(); ii ++) {
			if (roles.get(ii).equals(player)) return ii;
		}
		return -1;
	}

	public void backpropagate(double[] scores) {
		if (parent == null) return;
		if (scores.length == 1) {
			parent.pCounts[moveIndex] ++;
			parent.pVals[moveIndex] += scores[0];
			// No opponent
		} else {
			double ourScore = scores[getRoleIndex()];
			double enemyAvgScore = 0;
			for (int ii = 0; ii < scores.length; ii ++) enemyAvgScore += scores[ii];
			enemyAvgScore -= ourScore;
			enemyAvgScore /= (machine.getRoles().size() - 1);
			parent.pCounts[moveIndex] ++;
			parent.pVals[moveIndex] += ourScore;
			parent.oCounts[enemyMoveIndex] ++;
			parent.oVals[enemyMoveIndex] += enemyAvgScore;

			if (parent != null) {
				if (explored) {
					boolean moveIndexExp = true;
					for (int ii = 0; ii < parent.numEnemyMoves; ii ++) {
						if (parent.children[moveIndex][ii] == null) {
							moveIndexExp = false;
							break;
						} else if (!parent.children[moveIndex][ii].explored) {
							moveIndexExp = false;
							break;
						}
					}
					// now, the parent has this move index fully explored: update array
					if (moveIndexExp) {
						parent.pVals[moveIndex] = ourScore * parent.pCounts[moveIndex];
					}

					boolean eMoveIndexExp = true;
					for (int ii = 0; ii < parent.numMoves; ii ++) {
						if (parent.children[ii][enemyMoveIndex] == null) {
							eMoveIndexExp = false;
							break;
						} else if (!parent.children[ii][enemyMoveIndex].explored) {
							eMoveIndexExp = false;
							break;
						}
					}
					// now, the parent has this enemy move index fully explored: update arrays
					if (eMoveIndexExp) {
						parent.oVals[enemyMoveIndex] = enemyAvgScore * parent.oCounts[enemyMoveIndex];
					}

					boolean allExp = true;
					for (int ii = 0; ii < parent.numMoves; ii ++) {
						for (int jj = 0; jj < parent.numEnemyMoves; jj ++) {
							if (parent.children[ii][jj] == null) {
								allExp = false;
							} else if (!parent.children[ii][jj].explored) {
								allExp = false;
							}
						}
					}

					if (allExp) {
						parent.explored = true;
						// System.out.println("[THREADED] Parent EXP!");
					}
				}
			}
		}

		if (parent != null) {
			parent.backpropagate(scores);
		}
	}

	public static final int NUM_THREADS = 4; // EVEN NUMBER!!
	static final int NUM_DEPTH_CHARGES = 3; // TODO
	static final boolean useHeuristic = false;
	public double[] simulate() // Check if immediate next state is terminal TODO
			throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		double[] avgScores = new double[machine.getRoles().size()];
		if (machine.isTerminal(state)) {
			explored = true;
			List<Integer> goals = machine.getGoals(state);
			for (int jj = 0; jj < goals.size(); jj ++) avgScores[jj] = goals.get(jj).doubleValue();
			return avgScores;
		}

		List<Charger> rs = new ArrayList<Charger>();
		Collection<Future<?>> futures = new LinkedList<Future<?>>();
		for (int ii = 0; ii < NUM_THREADS; ii ++) {
			DepthCharger d = new DepthCharger(machines.get(ii), state, player, NUM_DEPTH_CHARGES, true);
			rs.add(d);
			futures.add(executor.submit(d));
		}
		//		for (int ii = 0; ii < NUM_THREADS / 2; ii ++) {
		//			SmartCharger s = new SmartCharger(machines.get(ii), state, player, NUM_DEPTH_CHARGES, true);
		//			rs.add(s);
		//			futures.add(executor.submit(s));
		//		}

		for (Future<?> future:futures) {
			try {
				future.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (useHeuristic) {
			List<Role> roles = machine.findRoles();
			double heuristics[] = new double[roles.size()];
			for (int ii = 0; ii < roles.size(); ii ++) // TODO
				heuristics[ii] = MyHeuristics.weightedHeuristicFunction(roles.get(ii), state, machine);
			for (int ii = 0; ii < NUM_THREADS; ii ++)
				for (int jj = 0; jj < machine.getRoles().size(); jj ++) avgScores[jj] += rs.get(ii).getValues()[jj];
			for (int jj = 0; jj < machine.getRoles().size(); jj ++) avgScores[jj] += 2 * heuristics[jj];
			for (int ii = 0; ii < machine.getRoles().size(); ii ++) avgScores[ii] /= (NUM_THREADS + 2);
		} else {
			for (int ii = 0; ii < NUM_THREADS; ii ++)
				for (int jj = 0; jj < machine.getRoles().size(); jj ++) avgScores[jj] += rs.get(ii).getValues()[jj];
			for (int ii = 0; ii < machine.getRoles().size(); ii ++) avgScores[ii] /= NUM_THREADS;
		}
		numCharges += NUM_DEPTH_CHARGES * NUM_THREADS;
		return avgScores;
	}

	public ThreadedNode getParent() {
		return parent;
	}

	public void setParent(ThreadedNode parent) {
		this.parent = parent;
	}

	public MachineState getState() {
		return state;
	}

	public void setState(MachineState state) {
		this.state = state;
	}

	public ThreadedNode findMatchingState(MachineState currentState) {
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
