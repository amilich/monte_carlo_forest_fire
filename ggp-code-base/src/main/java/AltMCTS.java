import java.util.List;

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

public class AltMCTS extends StateMachineGamer {

	@Override
	public StateMachine getInitialStateMachine() {
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

		return null;
	}

	private AltNode select(AltNode node) {
		if (node.visits == 0)
			return node;
		for (int i = 0; i < node.children.length; i++) {
			if (node.children[i].visits == 0) {
				return node.children[i];
			}
		}
		double score = 0;
		AltNode result = node;
		for (int i = 0; i < node.children.length; i++) {
			double newscore = selectfn(node.children[i]);
			if (newscore > score) {
				score = newscore;
				result = node.children[i];
			}
		}
		return select(result);
	}

	private double selectfn(AltNode node) {
		double c = 2.0;
		return node.cumUtility / node.visits +
				Math.sqrt(c * Math.log(node.parent.visits) / node.visits);
	}

	private void expand(AltNode node) throws MoveDefinitionException, TransitionDefinitionException {
		List<Move> myMoves = getStateMachine().getLegalMoves(node.state, getRole());
		for (int i = 0; i < myMoves.size(); i++) {
			List<List<Move>> actions = getStateMachine().getLegalJointMoves(node.state, getRole(), myMoves.get(i));
			for (int j = 0; j < actions.size(); j++) {
				MachineState newState = getStateMachine().getNextState(node.state, actions.get(j));
				AltNode newNode = new AltNode(newState, node);
				node.children[i][j] = newNode;
			}
		}
	}

	private double monteCarlo(MachineState state, int count) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		double total = 0.0;
		for (int i = 0; i < count; i++) {
			total += depthCharge(state);
		}
		return total / count;
	}

	private int depthCharge(MachineState state) throws GoalDefinitionException, TransitionDefinitionException, MoveDefinitionException {
		MachineState stateClone = state.clone(); // Because performDepthCharge may destroy the state
		MachineState end = getStateMachine().performDepthCharge(stateClone, /*theDepth=*/null);
		return getStateMachine().findReward(getRole(), end);
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
		return null;
	}

}
