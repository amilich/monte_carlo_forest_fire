import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MyBasicMonteCarloPlayer extends StateMachineGamer {
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
	}


	/**
	 * Controls printing of debug statements.
	 */
	final boolean DEBUG_EN = false;
	final double MAX_SCORE = 100;
	final double MIN_SCORE = 0;
	double MAX_LEVEL = 3; // TODO level

	final static double MAX_DELIB_THRESHOLD = 1000;

	/**
	 * Function: stateMachineSelectMove
	 * ----------------------------------
	 * Returns move for the player at a given stage.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		long startTime = System.currentTimeMillis();
		long decisionTime = timeout;
		System.out.println("Curr time = " + System.currentTimeMillis() + " decision time = " + decisionTime);
		System.out.println("Timeout " + timeout);
		if (DEBUG_EN) System.out.println("Selecting move for " + getRole());
		MachineState currState = getCurrentState();
		Move action = null;
		MyBoundedMobilityPlayer m = new MyBoundedMobilityPlayer();
		if (getStateMachine().getRoles().size() == 1) {
			action = m.singlePlayerBestMove(getRole(), currState, decisionTime, getStateMachine(), 5); // TODO
		} else {
			action = null; // bestmove(getRole(), currState, decisionTime);
		}

		return action;
	}

	@Override
	public void stateMachineStop() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stateMachineAbort() {
		// TODO Auto-generated method stub

	}

	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	@Override
	public String getName() {
		return "BasicMonteCarlo";
	}
}
