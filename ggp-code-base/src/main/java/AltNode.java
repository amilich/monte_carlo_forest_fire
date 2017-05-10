import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;

public class AltNode {
	public static int numCharges = 0;

	public AltNode parent;
	public MachineState state;
	public AltNode[][] children;
	private int numMoves;
	private int numEnemyMoves;
	public int visits;
	public double cumUtility;

	private int moveIndex;
	private int enemyMoveIndex;
	static StateMachine machine;
	static Role player;

	public static void setStateMachine(StateMachine machine) {
		AltNode.machine = machine;
	}

	public static StateMachine getStateMachine(StateMachine machine) {
		return AltNode.machine;
	}

	public AltNode(MachineState state, AltNode parent) {
		this.visits = 0;
		this.state = state;
		this.parent = parent;
		if (machine.isTerminal(state)) {
			this.numMoves = 0;
			this.numEnemyMoves = 0;
		} else {
			List<Move> myMoves;
			try {
				myMoves = machine.getLegalMoves(state, player);
				this.numMoves = myMoves.size();
				this.numEnemyMoves = machine.getLegalJointMoves(state, player, myMoves.get(0)).size();
			} catch (MoveDefinitionException e) {
				e.printStackTrace();
			}
		}
		children = new AltNode[numMoves][numEnemyMoves];
	}
}