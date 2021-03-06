import java.util.List;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MyAlphaBetaPlayer extends StateMachineGamer {
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

	/**
	 * Function: stateMachineSelectMove
	 * ----------------------------------
	 * Returns move for the player at a given stage.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		long decisionTime = timeout;
		if (DEBUG_EN) System.out.println("Selecting move for " + getRole());
		MachineState currState = getCurrentState();
		Move action = bestmove(getRole(), currState, decisionTime);
		// TODO try/catch
		if (DEBUG_EN) System.out.println("Selected action (role = " + getRole() + ") = " + action);
		return action;
	}

	/**
	 * Function: bestmove
	 * -------------------
	 * Return best possible move using minimax strategy.
	 */
	private Move bestmove(Role role, MachineState state, long decisionTime) throws MoveDefinitionException,
	GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = getStateMachine().getLegalMoves(state, role);

		double alpha = MIN_SCORE;
		double beta = MAX_SCORE;
		Move finalMove = null;
		System.out.println("actions: " + actions);
		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;

			double result = minscore(role, actions.get(ii), state, alpha, beta, decisionTime);
			double oldAlpha = alpha;
			alpha = Math.max(alpha, result);
			System.out.println("result score = " + result);
			System.out.println("curr alpha = " + alpha);
			if (alpha != oldAlpha) {
				System.out.println("Updating alpha [" + oldAlpha + "] -> [" + alpha + "]");
				finalMove = actions.get(ii);
			}
		}
		if (finalMove == null) {
			System.out.println("*** NULL final move selected ***");
			return actions.get(0);
		}
		return finalMove;
	}

	/**
	 * Function: opponent
	 * -------------------
	 * Find opponent player. Unused if getLegalJointMoves is called.
	 */
	@SuppressWarnings("unused")
	private Role opponent(Role role) {
		List<Role> roles = getStateMachine().getRoles();
		for (Role r : roles) {
			if (!r.equals(role)) return r;
		}
		return role; // TODO error
	}

	/**
	 * Function: minscore
	 * -------------------
	 * Recursively determine minimum score that can be achieved from choosing a given move
	 * in a given game state (assuming rational opponent).
	 */
	private double minscore(Role role, Move move, MachineState state, double alpha, double beta, long decisionTime)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, move);
		for (List<Move> jointAction : jointActions) { // Opponent's move
			if (MyHeuristics.checkTime(decisionTime)) break;

			MachineState nextState = getStateMachine().getNextState(state, jointAction);
			double result = maxscore(role, nextState, alpha, beta, decisionTime);
			beta = Math.min(beta, result);
			if (beta <= alpha) return alpha;
		}
		return beta;
	}

	/**
	 * Function: maxscore
	 * -------------------
	 * Determine max score that can be achieved from choosing a given move
	 * in a given game state by maximizing minimum score (minimax).
	 */
	private double maxscore(Role role, MachineState currState, double alpha, double beta, long decisionTime)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {

		if (getStateMachine().isTerminal(currState)) {
			return getStateMachine().getGoal(currState, role);
		}
		List<Move> actions = getStateMachine().getLegalMoves(currState, role);
		for (Move action : actions) {
			if (MyHeuristics.checkTime(decisionTime)) break;

			double result = minscore(role, action, currState, alpha, beta, decisionTime);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) return beta;
		}
		return alpha;
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
		return "AlphaBetaPlayer";
	}
}
