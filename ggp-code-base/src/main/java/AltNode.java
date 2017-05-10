import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

public class AltNode {
	public static int numCharges = 0;

	private AltNode parent;
	private MachineState state;
	private double[] pCounts;
	private double[] pVals;
	private double[] oCounts;
	private double[] oVals;
	public AltNode[] children;
	private int numMoves;
	private int numEnemyMoves;
	public int visits;

	private int moveIndex;
	private int enemyMoveIndex;
	static StateMachine machine;
	static Role player;

	public AltNode(StateMachine sm) {
		visits = 0;
	}
}