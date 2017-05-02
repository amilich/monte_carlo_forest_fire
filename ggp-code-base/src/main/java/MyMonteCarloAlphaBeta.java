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

public class MyMonteCarloAlphaBeta extends StateMachineGamer{

	private static final double MIN_SCORE = 0;
	private static final double MAX_SCORE = 100;

	@Override
	public StateMachine getInitialStateMachine() {
		// TODO Auto-generated method stub
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub

	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub

		Role currRole = getRole();
		MachineState currState = getCurrentState();
		Move action = getStateMachine().findLegalx(currRole, currState);
		if (getStateMachine().getRoles().size() == 1) {
			action = MyDeliberationPlayer.bestmove(getRole(), currState, timeout, getStateMachine());
		} else {
			action = bestmove(getRole(), currState, timeout);
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
		// TODO Auto-generated method stub
		return "Monte Carlo Alpha Beta";
	}

	private Move bestmove(Role role, MachineState state, long decisionTime)
			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
		List<Move> actions = new ArrayList<Move>();
		actions.addAll(getStateMachine().getLegalMoves(state, role));
		Collections.shuffle(actions);
		double score = MIN_SCORE;
		Move finalMove = null;
		System.out.println("actions: " + actions);
		for (int ii = 0; ii < actions.size(); ii ++) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			double result = minscore(role, actions.get(ii), state, 0, 100, decisionTime);
			if (result > score) {
				score = result;
				finalMove = actions.get(ii);
			}
			if (score == MAX_SCORE) return actions.get(ii);
		}
		if (finalMove == null) {
			System.out.println("*** NULL move selected ***");
			return actions.get(0);
		}
		return finalMove;
	}

	private double maxscore(Role role, MachineState state, double alpha, double beta, long decisionTime) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
		if (getStateMachine().isTerminal(state)) {
			return getStateMachine().findReward(role, state);
		}
		List<Move> actions = getStateMachine().getLegalMoves(state, role);
		for (int i = 0; i < actions.size(); i++) {
			if (MyHeuristics.checkTime(decisionTime)) break;
			double result = minscore(role, actions.get(i), state, alpha, beta, decisionTime);
			alpha = Math.max(alpha, result);
			if (alpha >= beta) {
				return beta;
			}
		}
		return alpha;
	}

	private double minscore(Role role, Move action, MachineState state, double alpha, double beta, long decisionTime) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, action);

		for (List<Move> jointAction : jointActions) { // Opponent's move
			if (MyHeuristics.checkTime(decisionTime)) break;

//			if (role == roles[0]) {
//
//			}
			MachineState nextState = getStateMachine().getNextState(state, jointAction);
			double result = maxscore(role, nextState, alpha, beta, decisionTime);
			beta = Math.min(beta, result);
			if (beta <= alpha) {
				return alpha;
			}
		}
		return beta;
	}

}