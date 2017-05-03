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

	StateMachine machine2;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		machine2 = getInitialStateMachine();
		machine2.initialize(getMatch().getGame().getRules());
		Node.numCharges = 0;
		moveNum = 0;
		initRoot();
		while (!MyHeuristics.checkTime(timeout)) {
			Node selected = root.selectAndExpand();
			double[] scores = selected.simulate();
			selected.backpropagate(scores); // sqrt 2 for c
		}
		System.out.println("METAGAME charges = " + Node.numCharges);
	}


	private void initRoot() throws MoveDefinitionException {
		Node.setRole(getRole());
		Node.setStateMachine(getStateMachine());
		Node.setStateMachine2(machine2);
		root = new Node(getCurrentState());
	}

	int moveNum = 0;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		if (root == null) {
			initRoot();
		} else if (moveNum != 0){
			Node matchingChild = root.findMatchingState(getCurrentState());
			root = matchingChild; // may be null
			if (root != null) {
				System.out.println("*** ADVANCED TREE ***");
			} else {
				initRoot();
				System.out.println("*** FAILED TO ADVANCE TREE ***");
			}
		} else {
			System.out.println("First move: advanced tree.");
		}

		while (!MyHeuristics.checkTime(timeout)) {
			Node selected = root.selectAndExpand();
			double[] scores = selected.simulate();
			selected.backpropagate(scores); // sqrt 2 for c
		}
		System.out.println("Num charges = " + Node.numCharges);
		moveNum ++;
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
