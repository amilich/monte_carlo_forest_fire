import java.util.List;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.BitSetPropNet;

// Andrew

public class MyAlphaBetaPlayer extends StateMachineGamer {
	@Override
	public StateMachine getInitialStateMachine() {
		// return new CachedStateMachine(new ProverStateMachine());
		//		return new SamplePropNetStateMachine();
		return new BitSetPropNet();
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
	final static double MAX_SCORE = 100;
	final static double MIN_SCORE = 0;

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
		Move action = getStateMachine().findLegalx(getRole(), currState);
		int maxLevel = 4;
		try {
			if (getStateMachine().getRoles().size() == 1) {
				action = MyBoundedMobilityPlayer.singlePlayerBestMove(getRole(), currState, getStateMachine());
			} else {
				action = bestmove(getRole(), currState, decisionTime);
			}
		} catch(Exception e) {
			System.out.println("*** Failed to get best move ***");
		}
		// TODO try/catch
		if (DEBUG_EN) System.out.println("Selected action (role = " + getRole() + ") = " + action);
		return action;
	}

	public static boolean finished = false;
	static Move bestM = null;

	public static Move staticBest(StateMachine m, MachineState s, Role r)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<Move> actions = m.getLegalMoves(s, r);
		finished = false;
		double alpha = MIN_SCORE;
		double beta = MAX_SCORE;
		Move finalMove = null;
		// System.out.println("actions: " + actions);
		for (int ii = 0; ii < actions.size(); ii ++) {
			// if (MyHeuristics.checkTime(decisionTime)) break;
			double result = minscore(r, actions.get(ii), s, alpha, beta, m);
			double oldAlpha = alpha;
			alpha = Math.max(alpha, result);
			// System.out.println("result score = " + result);
			// System.out.println("curr alpha = " + alpha);
			if (alpha != oldAlpha) {
				// System.out.println("Updating alpha [" + oldAlpha + "] -> [" + alpha + "]");
				finalMove = actions.get(ii);
			}
		}
		finished = true;
		bestM = finalMove;
		return finalMove;
	}

	/**
	 * Function: bestmove
	 * -------------------
	 * Return best possible move using minimax strategy.
	 */
	private Move bestmove(Role role, MachineState state, long decisionTime) throws MoveDefinitionException,
	GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		finished = false;
		double alpha = MIN_SCORE;
		double beta = MAX_SCORE;
		Move finalMove = null;
		// System.out.println("actions: " + actions);
		for (int ii = 0; ii < actions.size(); ii ++) {
			// if (MyHeuristics.checkTime(decisionTime)) break;
			double result = minscore(role, actions.get(ii), state, alpha, beta, getStateMachine());
			double oldAlpha = alpha;
			alpha = Math.max(alpha, result);
			// System.out.println("result score = " + result);
			// System.out.println("curr alpha = " + alpha);
			if (alpha != oldAlpha) {
				// System.out.println("Updating alpha [" + oldAlpha + "] -> [" + alpha + "]");
				finalMove = actions.get(ii);
			}
		}
		finished = true;
		bestM = finalMove;
		return finalMove;
	}

	/**
	 * Function: minscore
	 * -------------------
	 * Recursively determine minimum score that can be achieved from choosing a given move
	 * in a given game state (assuming rational opponent).
	 */
	private static double minscore(Role role, Move move, MachineState state, double alpha, double beta,
			StateMachine m)
					throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<List<Move>> jointActions = m.getLegalJointMoves(state, role, move);
		for (List<Move> jointAction : jointActions) { // Opponent's move
			// if (MyHeuristics.checkTime(decisionTime)) break;
			MachineState nextState = m.getNextState(state, jointAction);
			double result = maxscore(role, nextState, alpha, beta, m);
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
	private static double maxscore(Role role, MachineState currState, double alpha, double beta,
			StateMachine m)
					throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {

		if (m.isTerminal(currState)) {
			return m.getGoal(currState, role);
		}
		List<Move> actions = m.getLegalMoves(currState, role);
		for (Move action : actions) {
			// if (MyHeuristics.checkTime(decisionTime)) break;
			double result = minscore(role, action, currState, alpha, beta, m);
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
