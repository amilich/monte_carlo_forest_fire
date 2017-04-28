import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MyBasicMonteCarloPlayer extends StateMachineGamer {
	Node root = null;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
	}

	private void initRoot() throws MoveDefinitionException {
		Node.setRole(getRole());
		Node.setStateMachine(getStateMachine());
		root = new Node(getCurrentState());
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (root == null) {
			initRoot();
		} else {
			Node matchingChild = root.findMatchingState(getCurrentState());
			root = matchingChild; // may be null
			if (root != null) {
				System.out.println("*** ADVANCED TREE ***");
			} else {
				initRoot();
				System.out.println("*** FAILED TO ADVANCE TREE ***");
			}
		}

		while (!MyHeuristics.checkTime(timeout)) {
			Node selected = root.selectAndExpand();
			double[] scores = selected.simulate();
			selected.backpropagate(scores); // sqrt 2 for c
		}

		Move m = root.getBestMove();
		return m;
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
		return "BasicMCTS";
	}
}