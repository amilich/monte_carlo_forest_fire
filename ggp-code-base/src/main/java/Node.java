import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class Node {
	static final int NUM_DEPTH_CHARGES = 10;

	private Node parent;
	private MachineState state;
//	private double[] pCounts;
//	private double[] pVals;
//	private double[] oCounts;
//	private double[] oVals;

	private double[][] vals;
	private double[][] counts;

	private Node[][] children;
	private int numMoves;
	private int numEnemyMoves;
	private int moveIndex;
	private int enemyMoveIndex;
	static StateMachine machine;
	static Role player;

	public Node(Role role, MachineState state, StateMachine machine)
			throws MoveDefinitionException {
		this(role, state, machine, null, -1, -1);
	}

	public Node(Role role, MachineState state, StateMachine machine, Node parent, int moveIndex, int enemyMoveIndex)
			throws MoveDefinitionException { // TODO try/catch
		this.state = state;
		this.parent = parent;
		this.moveIndex = moveIndex;
		this.enemyMoveIndex = enemyMoveIndex;
		Node.player = role;
		Node.machine = machine;
		List<Move> myMoves = machine.getLegalMoves(state, role);
		this.numMoves = myMoves.size();
		this.numEnemyMoves = machine.getLegalJointMoves(state, role, myMoves.get(0)).size();
//		pCounts = new double[numMoves];
//		pVals = new double[numMoves];
//		oCounts = new double[numEnemyMoves];
//		oVals = new double[numEnemyMoves];

		vals = new double[numMoves][numEnemyMoves];
		counts = new double[numMoves][numEnemyMoves];
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

	private double selectfn(int moveNum, int enemyMove, boolean opponent, Node childNode) {
		if (opponent) {
			return -vals[moveNum] + Math.sqrt(2 * Math.log(sumCounts(opponent)) / childNode.sumCounts(opponent));
		} else {
			return pVals[moveNum] + Math.sqrt(2 * Math.log(sumCounts(opponent)) / childNode.sumCounts(opponent));
		}
	}

	public void selectAndExpand() {
		Node selected = this.select();
		selected.expand();
	}

	public Node select() {
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				if (this.children[ii][jj] == null) return this;
			}
		}
		double score = 0;
		Node result = this;
		for (int ii = 0; ii < numMoves; ii ++){
			for (int jj = 0; jj < numEnemyMoves; jj ++) {
				double newscore = selectfn()
				if (newscore > score) {
					score = newscore;
					result = children[numMoves][numEnemyMoves];
				}
			}
		}
		return result.select();
	}

	int expandedMove;
	int expandedEnemyMove;
	public void expand() {

	}

	public void backpropagate(double score) {
		this.pCounts[expandedMove] ++;
		this.oCounts[expandedEnemyMove] ++;
		this.pVals[expandedMove] += score;
		this.oVals[expandedEnemyMove] += score;
		if (parent != null) {
			backpropagate(score);
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
}
