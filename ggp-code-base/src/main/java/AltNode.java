import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

public class AltNode {
	public static int numCharges = 0;

	public AltNode parent;
	private MachineState state;
	public AltNode[] children;
	private int numMoves;
	private int numEnemyMoves;
	public int visits;
	public double cumUtility;

	private int moveIndex;
	private int enemyMoveIndex;
	static StateMachine machine;
	static Role player;

	public AltNode(StateMachine sm, AltNode parent) {
		this.visits = 0;
		this.parent = parent;
	}
}