import java.util.ArrayList;
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

public class MyDeliberationPlayer extends StateMachineGamer {
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
	 * Function: stateMachineSelectMove
	 * ---------------------------------
	 * Recurses down game tree and selects move that leads to highest possible score outcome.
	 */
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		MachineState currState = getCurrentState();
		Move action = null;
		try {
			action = bestmove(getRole(), currState, machine);
		} catch(Exception e) {
			System.out.println("Error occurred while finding action: " + e);
			return machine.getLegalMoves(currState, getRole()).get(0);
		}
		return action;
	}

	/**
	 * Function: bestmove
	 * -------------------
	 * Iterates through moves at each step and calls function to determine max
	 * score achievable from move.
	 */
	private Move bestmove(Role role, MachineState state, StateMachine machine) throws MoveDefinitionException,
		GoalDefinitionException, TransitionDefinitionException {

		List<Move> actions = machine.getLegalMoves(state, role);
		double score = 0.0;
		Move finalMove = actions.get(0);
		for (int ii = 0; ii < actions.size(); ii ++) {
			List<Move> tempMoves = new ArrayList<Move>();
			tempMoves.add(actions.get(ii));
			double result = maxscore(role, machine.getNextState(state, tempMoves), machine);
			if (result == 100) return actions.get(ii);
			if (result > score) {
				score = result;
				finalMove = actions.get(ii);
			}
		}
		return finalMove;
	}

	/**
	 * Function: maxscore
	 * -------------------
	 * Recursively determine max score that can be achieved from choosing a given move
	 * in a given game state.
	 */
	private double maxscore(Role role, MachineState currState, StateMachine machine)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		if (machine.isTerminal(currState)) {
			return machine.getGoal(currState, role); // TODO correct value
		}
		List<Move> actions = machine.getLegalMoves(currState, role);
		double score = 0.0;
		for (int ii = 0; ii < actions.size(); ii ++) {
			List<Move> tempMoves = new ArrayList<Move>(); // TODO add other roles
			tempMoves.add(actions.get(ii));
			double result = maxscore(role, machine.getNextState(currState, tempMoves), machine);
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
		return "CompulsivePlayer";
	}
}
