import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class Node {
	static final int NUM_DEPTH_CHARGES = 1; // TODO

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
	static Role player;

	public static void setStateMachine(StateMachine machine) {
		Node.machine = machine;
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
		System.out.println("Creating new node, parent = " + parent + ", movei = " + moveIndex + ", emove = " + enemyMoveIndex);
		this.state = state;
		this.parent = parent;
		this.moveIndex = moveIndex;
		this.enemyMoveIndex = enemyMoveIndex;
		List<Move> myMoves = machine.getLegalMoves(state, player);
		this.numMoves = myMoves.size();
		this.numEnemyMoves = machine.getLegalJointMoves(state, player, myMoves.get(0)).size();
		System.out.println("nm = " + numMoves + ", nem = " + numEnemyMoves);
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

	public double sumCounts(boolean opponent) {
		if (opponent) {
			return sumArray(oCounts);
		} else {
			return sumArray(pCounts);
		}
	}

	private double selectfn(int moveNum, boolean opponent) {
		if (opponent) {
			return -oVals[moveNum] + Math.sqrt(2 * Math.log(sumCounts(opponent)) / oCounts[moveNum]);
		} else {
			return pVals[moveNum] + Math.sqrt(2 * Math.log(sumCounts(opponent)) / pCounts[moveNum]);
		}
	}

	public Node getBestNode() {
		return null;
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
		return machine.getLegalMoves(state, player).get(maxMove);
	}

	public Node selectAndExpand() throws MoveDefinitionException, TransitionDefinitionException {
		Node selected = this.select();
		return selected.expand();
	}

	public Node select() {
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
		if (machine.findTerminalp(state)) return this;
		for (int ii = 0; ii < numMoves; ii ++) {
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (children[ii][jj] == null) {
					Move myMove = machine.getLegalMoves(state, player).get(ii);
					List<Move> jointMove = machine.getLegalJointMoves(state, player, myMove).get(jj);
					try {
						MachineState nextState = machine.getNextState(state, jointMove);
						children[ii][jj] = new Node(nextState, this, ii, jj);
						return children[ii][jj];
					} catch (Exception e) {
						System.out.println("expansion failed");
//						e.printStackTrace();
					}
					return this;
				}
			}
		}
		System.out.println("Failed to expand child node");
		return this; // TODO
	}

	public void backpropagate(double score) {
		if (parent == null) return;
		parent.pCounts[moveIndex] ++;
		parent.oCounts[enemyMoveIndex] ++;
		parent.pVals[moveIndex] += score;
		parent.oVals[enemyMoveIndex] += score;
		if (parent != null) {
			parent.backpropagate(score);
		}
	}

	public double simulate() // Check if immediate next state is terminal TODO
			throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {

		double avgResult = 0;
		for (int ii = 0; ii < NUM_DEPTH_CHARGES; ii ++) {
			int[] tempDepth = new int[1];
			avgResult += machine.getGoal(machine.performDepthCharge(state, tempDepth), player);
		}
		avgResult /= NUM_DEPTH_CHARGES;
		return avgResult;
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
		// TODO Auto-generated method stub
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
