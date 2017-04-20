import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

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

// Andrew

public class MyBoundedDepthPlayer extends StateMachineGamer {
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
	final double MAX_LEVEL = 4; // TODO level

	final static double MAX_DELIB_THRESHOLD = 1000;
	// TODO one player ??

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
		Move action = null; // bestmove(getRole(), currState, decisionTime); TODO
		if (getStateMachine().getRoles().size() == 1) {
			action = MyBoundedMobilityPlayer.singlePlayerBestMove(getRole(), currState, decisionTime, getStateMachine());
		} else {
			action = bestmove(getRole(), currState, decisionTime);
		}
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
		List<Move> actions = new ArrayList<Move>();
		actions.addAll(getStateMachine().getLegalMoves(state, role));
		Collections.shuffle(actions);
		double score = MIN_SCORE;
		Move finalMove = null;
		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;

			double level = 0;
			double result = minscore(role, actions.get(ii), state, level, decisionTime);
			if (result > score) {
				score = result;
				finalMove = actions.get(ii);
				System.out.println("result = " + result + " score = " + score);
				System.out.println("update to " + finalMove);
			}
			if (score == MAX_SCORE) return actions.get(ii);
		}
		if (finalMove == null) {
			System.out.println("NULL");
			Random random = new Random();
			return actions.get(random.nextInt(actions.size()));
		}
		return finalMove;
	}

	/**
	 * Function: minscore
	 * -------------------
	 * Recursively determine minimum score that can be achieved from choosing a given move
	 * in a given game state (assuming rational opponent).
	 */
	private double minscore(Role role, Move move, MachineState state, double level, long decisionTime)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {

		List<List<Move>> jointActions = new ArrayList<List<Move>>();
		jointActions.addAll(getStateMachine().getLegalJointMoves(state, role, move));
		Collections.shuffle(jointActions);
		double score = MAX_SCORE;
		for (List<Move> jointAction : jointActions) { // Opponent's move
			if (MyHeuristics.checkTime(decisionTime)) break;

			MachineState nextState = getStateMachine().getNextState(state, jointAction);
			double result = maxscore(role, nextState, level + 1, decisionTime);
			if (result == MIN_SCORE) return result;
			if (result < score) {
				score = result;
				if (DEBUG_EN) System.out.println("Low score of " + score + " if we play " + jointAction);
			}
		}
		return score;
	}

	/**
	 * Function: maxscore
	 * -------------------
	 * Determine max score that can be achieved from choosing a given move
	 * in a given game state by maximizing minimum score (minimax).
	 */
	private double maxscore(Role role, MachineState currState, double level, long decisionTime)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(currState)) {
			return getStateMachine().getGoal(currState, role);
		} else if (level >= MAX_LEVEL) {
			return 0;
		}
		List<Move> actions = getStateMachine().getLegalMoves(currState, role);
		double score = MIN_SCORE;
		for (Move action : actions) {
			if (MyHeuristics.checkTime(decisionTime)) break;

			double result = minscore(role, action, currState, level, decisionTime);
			if (result == MAX_SCORE) return result;
			if (result > score) score = result;
		}
		return score;
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
		return "BoundedDepthPlayer";
	}
}
