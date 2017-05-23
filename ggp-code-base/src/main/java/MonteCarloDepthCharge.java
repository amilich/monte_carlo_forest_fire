import java.util.ArrayList;
import java.util.Collections;
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

// Andrew

public class MonteCarloDepthCharge extends StateMachineGamer {
	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	StateMachine machine2;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// initialize the 2nd state machine
		machine2 = getInitialStateMachine();
		machine2.initialize(getMatch().getGame().getRules(), getRole());
		MyHeuristics.numCharges = 0;
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
		Move action = getStateMachine().findLegalx(getRole(), currState);
		int maxLevel = 4;
		try {
			if (getStateMachine().getRoles().size() == 1) {
				List<Move> levelMoves = new ArrayList<Move>();
				for (maxLevel = 1; maxLevel < 15; maxLevel ++) {
					if (MyHeuristics.checkTime(decisionTime)) {
						System.out.println("Out of time");
						break; // This is critical
					}
					levelMoves.add(singlePlayerBestMove(getRole(), currState, decisionTime, maxLevel, getStateMachine()));
				}
				System.out.println("Completed [" + maxLevel + "] searches.");
				System.out.println("# Moves = [" + levelMoves.size() + "]");
				if (levelMoves.size() > 1) action = levelMoves.get(maxLevel - 2);
			} else {
				action = bestmove(getRole(), currState, decisionTime, maxLevel);
			}
			// TODO one player
		} catch(Exception e) {
			System.out.println("*** Failed to get best move ***");
		}
		System.out.println("*** NUM CHARGES *** " + MyHeuristics.numCharges);

		return action;
	}

	/**
	 * Function: bestmove
	 * -------------------
	 * Return best possible move using minimax strategy.
	 */
	private Move bestmove(Role role, MachineState state, long decisionTime, int maxLevel) throws MoveDefinitionException,
	GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = getStateMachine().getLegalMoves(state, role);

		double alpha = MIN_SCORE;
		double beta = MAX_SCORE;
		Move finalMove = null;

		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			int level = 0;
			ArrayList<MachineState> emptyList = new ArrayList<MachineState>();
			double result = minscore(role, actions.get(ii), state, alpha, beta, decisionTime, level, emptyList, maxLevel);
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
	 * Function: minscore
	 * -------------------
	 * Recursively determine minimum score that can be achieved from choosing a given move
	 * in a given game state (assuming rational opponent).
	 */
	private double minscore(Role role, Move move, MachineState state, double alpha, double beta, long decisionTime, double level,
			List<MachineState> prevStates, int maxLevel)
					throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, move);
		for (List<Move> jointAction : jointActions) { // Opponent's move
			if (MyHeuristics.checkTime(decisionTime)) break;
			MachineState nextState = getStateMachine().getNextState(state, jointAction);
			prevStates.add(nextState);
			double result = maxscore(role, nextState, alpha, beta, decisionTime, level + 1, prevStates, maxLevel);
			beta = Math.min(beta, result);
			prevStates.remove(prevStates.size() - 1);
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
	private double maxscore(Role role, MachineState currState, double alpha, double beta, long decisionTime, double level,
			List<MachineState> prevStates, int maxLevel)
					throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {

		if (getStateMachine().isTerminal(currState)) {
			return getStateMachine().getGoal(currState, role);
		} else if (level >= maxLevel) {
			try {
				double mc = MyHeuristics.monteCarloHeuristic(role, currState, getStateMachine(), machine2, decisionTime);
				double heu = MyHeuristics.weightedHeuristicFunction(role, currState, getStateMachine());
				return heu * 0.4 + mc * 0.6;
			} catch (Exception e) {
				e.printStackTrace();
				return MyHeuristics.weightedHeuristicFunction(role, currState, getStateMachine());
			}
		}

		List<Move> actions = getStateMachine().getLegalMoves(currState, role);
		for (Move action : actions) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			double result = minscore(role, action, currState, alpha, beta, decisionTime, level, prevStates, maxLevel);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) return beta;
		}
		return alpha;
	}

	public Move singlePlayerBestMove(Role role, MachineState state, long decisionTime, int maxLevel, StateMachine machine)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException, InterruptedException {
		List<Move> actions = new ArrayList<Move>();
		actions.addAll(machine.getLegalMoves(state, role));
		Collections.shuffle(actions);
		double score = MIN_SCORE;
		Move finalMove = actions.get(0);

		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			double level = 0;
			ArrayList<MachineState> emptyList = new ArrayList<MachineState>();
			double result = singlePlayerMaxscore(role, state, decisionTime, level, emptyList, maxLevel, machine);
			if (result > score) {
				score = result;
				finalMove = actions.get(ii);
			}
			if (score == MAX_SCORE) return actions.get(ii);
		}
		System.out.println("Score = " + score);
		return finalMove;
	}

	public double singlePlayerMaxscore(Role role, MachineState currState, long decisionTime, double level,
			List<MachineState> prevStates, int maxLevel, StateMachine machine)
					throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException, InterruptedException {
		if (machine.isTerminal(currState)) {
			return machine.getGoal(currState, role); // TODO correct value
		} else if (level >= maxLevel) {
			double mc = MyHeuristics.monteCarloHeuristic(role, currState, getStateMachine(), machine2, decisionTime);
			double heu = MyHeuristics.weightedHeuristicFunction(role, currState, getStateMachine());
			return heu * 0.4 + mc * 0.6;
		}
		List<Move> actions = machine.getLegalMoves(currState, role);
		double score = 0.0;
		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			List<Move> tempMoves = new ArrayList<Move>(); // TODO add other roles
			tempMoves.add(actions.get(ii));
			prevStates.add(currState);
			double result = singlePlayerMaxscore(role, machine.getNextState(currState, tempMoves), decisionTime, level + 1
					, prevStates, maxLevel, machine);
			prevStates.remove(prevStates.size() - 1);
			if (result == 100) return result;
			if (result > score) score = result; // TODO short circuit
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
		return "DepthCharger";
	}
}
