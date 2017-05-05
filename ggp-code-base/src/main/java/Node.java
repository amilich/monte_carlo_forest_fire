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

public class Node {
	public static int numCharges = 0;

	private Node parent;
	private MachineState state;
	private double[] pCounts;
	private double[] pVals;
	private double[] oCounts;
	private double[] oVals;
	private Node[][] children;
	private int numMoves;
	private int numEnemyMoves;

	private int moveIndex;
	private int enemyMoveIndex;
	static StateMachine machine;
	static StateMachine machine2;
	static Role player;
	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	public static void setStateMachine(StateMachine machine) {
		Node.machine = machine;
	}

	public static void setStateMachine2(StateMachine machine2) {
		Node.machine2 = machine2;
	}

	public static void setRole(Role role) {
		Node.player = role;
	}

	public Node(MachineState state)
			throws MoveDefinitionException {
		this(state, null, -1, -1);
	}

	public Node(MachineState state, Node parent, int moveIndex, int enemyMoveIndex)
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
		children = new Node[numMoves][numEnemyMoves];
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
		System.out.println("Avg utility of best move = " + avgUtility);
		return machine.getLegalMoves(state, player).get(maxMove);
	}

	public Node selectAndExpand() throws MoveDefinitionException, TransitionDefinitionException {
		Node selected = this.select();
		return selected.expand();
	}

	public Node select() {
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

	public Node expand() throws MoveDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(state)) return this;
		for (int ii = 0; ii < numMoves; ii ++) {
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] == null) {
					Move myMove = machine.getLegalMoves(state, player).get(ii);
					try {
						List<Move> jointMove = machine.getLegalJointMoves(state, player, myMove).get(jj);
						MachineState nextState = machine.getNextState(state, jointMove);
						children[ii][jj] = new Node(nextState, this, ii, jj);
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
			// parent.oCounts[enemyMoveIndex] ++; // no opponent
			// parent.oVals[enemyMoveIndex] += score;
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
		}

		if (parent != null) {
			parent.backpropagate(scores);
		}
	}

	static final int NUM_DEPTH_CHARGES = 3; // TODO
	public double[] simulate() // Check if immediate next state is terminal TODO
			throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		double[] avgScores = new double[machine.getRoles().size()];
		if (machine.isTerminal(state)) {
			List<Integer> goals = machine.getGoals(state);
			for (int jj = 0; jj < goals.size(); jj ++) {
				avgScores[jj] = goals.get(jj).doubleValue();
			}
			return avgScores;
		}

		DepthCharger d1 = new DepthCharger(machine, state, player, NUM_DEPTH_CHARGES, true);
		DepthCharger d2 = new DepthCharger(machine2, state, player, NUM_DEPTH_CHARGES, true);
//		SmartCharger d2 = new SmartCharger(machine2, state, player, NUM_DEPTH_CHARGES, true);
		Collection<Future<?>> futures = new LinkedList<Future<?>>();
		futures.add(executor.submit(d1));
		futures.add(executor.submit(d2));
		for (Future<?> future:futures) {
			try {
				future.get();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		double value1[] = d1.getValues();
		double value2[] = d2.getValues();
		for (int ii = 0; ii < machine.getRoles().size(); ii ++) {
			avgScores[ii] = (value1[ii] + value2[ii]) / 2;
			// System.out.print(avgScores[ii] + ",");
		}
		numCharges += NUM_DEPTH_CHARGES;
		numCharges += NUM_DEPTH_CHARGES;
//		System.out.println("NEW TURN");
		return avgScores;
	}

	public Node getParent() {
		return parent;
	}

	public void setParent(Node parent) {
		this.parent = parent;
	}

	public MachineState getState() {
		return state;
	}

	public void setState(MachineState state) {
		this.state = state;
	}

	public Node findMatchingState(MachineState currentState) {
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
