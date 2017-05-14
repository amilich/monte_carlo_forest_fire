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
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

public class MyBoundedMobilityPlayer extends StateMachineGamer {
	@Override
	public StateMachine getInitialStateMachine() {
		// return new CachedStateMachine(new ProverStateMachine());
		return new SamplePropNetStateMachine();
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

	final static double MAX_DELIB_THRESHOLD = 1000;
	final static double DELIB_SAFETY_CONST = 1.5;

	double totalTimeTaken = 0;
	double numMeasurements = 0;

	/**
	 * Function: stateMachineSelectMove
	 * ----------------------------------
	 * Returns move for the player at a given stage.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		System.out.println("***** NEW MOVE ****");
		long decisionTime = timeout;
		if (DEBUG_EN) System.out.println("Selecting move for " + getRole());
		MachineState currState = getCurrentState();

		List<Move> levelMoves = new ArrayList<Move>();
		int maxLevel;
		for (maxLevel = 1; maxLevel < 15; maxLevel ++) {
			if (MyHeuristics.checkTime(decisionTime)) {
				System.out.println("Out of time");
				break; // This is critical
			}
			if (getStateMachine().getRoles().size() == 1) {
				System.out.println("***** SINGLE PLAYER MODE ****");
				levelMoves.add(singlePlayerBestMove(getRole(), currState, decisionTime, maxLevel, getStateMachine()));
			} else {
				levelMoves.add(bestmove(getRole(), currState, decisionTime, maxLevel));
			}
		}
		System.out.println("Completed [" + maxLevel + "] searches.");
		System.out.println("# Moves = [" + levelMoves.size() + "]");
		if (levelMoves.size() == 0) {
			return getStateMachine().findLegalx(getRole(), currState);
		} else {
			return levelMoves.get(maxLevel - 2);
		}
	}

	// TODO
	public static Move singlePlayerBestMove(Role role, MachineState state, long decisionTime, int maxLevel, StateMachine machine)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
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
				//	System.out.println("Result from min = " + result + " action = " + finalMove);
			}
			if (score == MAX_SCORE) return actions.get(ii);
		}
		System.out.println("Score = " + score);
		return finalMove;
	}

	public static double singlePlayerMaxscore(Role role, MachineState currState, long decisionTime, double level,
			List<MachineState> prevStates, int maxLevel, StateMachine machine)
					throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(currState)) {
			return machine.getGoal(currState, role); // TODO correct value
		} else if (level >= maxLevel) {
			return MyHeuristics.weightedHeuristicFunction(role, currState, machine);
		} else if (level > 2 && MyHeuristics.stateConverges(role, currState, machine, decisionTime, prevStates)) {
			return MyHeuristics.weightedHeuristicFunction(role, currState, machine);
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

	/**
	 * Function: bestmove
	 * -------------------
	 * Return best possible move using minimax strategy.
	 */
	private Move bestmove(Role role, MachineState state, long decisionTime, int maxLevel)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = new ArrayList<Move>();
		System.out.println("Starting bestmove with max level = " + maxLevel);
		actions.addAll(getStateMachine().getLegalMoves(state, role));
		Collections.shuffle(actions);
		double score = MIN_SCORE;
		Move finalMove = null;
		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			int level = 0;
			ArrayList<MachineState> emptyList = new ArrayList<MachineState>();
			double result = minscore(role, actions.get(ii), state, level, decisionTime, emptyList, maxLevel);
			if (result > score) {
				score = result;
				finalMove = actions.get(ii);
			}
			if (result == MAX_SCORE) return actions.get(ii);
		}
		if (finalMove == null) {
			System.out.println("*** NULL move selected ***");
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
	private double minscore(Role role, Move move, MachineState state, int level, long decisionTime, List<MachineState> prevStates,
			int maxLevel)
					throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		List<List<Move>> jointActions = new ArrayList<List<Move>>();
		jointActions.addAll(getStateMachine().getLegalJointMoves(state, role, move));
		Collections.shuffle(jointActions);
		double score = MAX_SCORE;
		for (List<Move> jointAction : jointActions) { // Opponent's move
			if (MyHeuristics.checkTime(decisionTime)) break;

			MachineState nextState = getStateMachine().getNextState(state, jointAction);
			prevStates.add(nextState);
			double result = maxscore(role, nextState, level + 1, decisionTime, prevStates, maxLevel);
			prevStates.remove(prevStates.size() - 1);
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
	private double maxscore(Role role, MachineState currState, int level, long decisionTime, List<MachineState> prevStates,
			int maxLevel)
					throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(currState)) {
			return getStateMachine().getGoal(currState, role);
		} else if (level >= maxLevel) {
			return MyHeuristics.weightedHeuristicFunction(role, currState, getStateMachine());
		} else if (level > 2 && MyHeuristics.stateConverges(role, currState, getStateMachine(), decisionTime, prevStates)) {
			 return MyHeuristics.weightedHeuristicFunction(role, currState, getStateMachine());
		}
		List<Move> actions = new ArrayList<Move>();
		actions.addAll(getStateMachine().getLegalMoves(currState, role));
		Collections.shuffle(actions);
		double score = MIN_SCORE;
		for (Move action : actions) {
			if (MyHeuristics.checkTime(decisionTime)) break;

			double result = minscore(role, action, currState, level, decisionTime, prevStates, maxLevel);
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
		return "MyBoundedDepthMobilityPlayer";
	}
}
