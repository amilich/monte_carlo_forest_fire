import java.util.ArrayList;
import java.util.List;

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

public class MyThreadedMonteCarlo extends StateMachineGamer {
	ThreadedNode root = null;

	@Override
	public StateMachine getInitialStateMachine() {
		return new CachedStateMachine(new ProverStateMachine());
	}

	List<StateMachine> machines = new ArrayList<StateMachine>();
	StateMachine machine2;
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		machine2 = getInitialStateMachine();
		machine2.initialize(getMatch().getGame().getRules());
		ThreadedNode.numCharges = 0;
		moveNum = 0;
		initRoot();
		expandTree(timeout);
		System.out.println("[THREADED] METAGAME charges = " + ThreadedNode.numCharges);
	}

	public void expandTree(long timeout) {
		while (!MyHeuristics.checkTime(timeout)) {
			try {
			ThreadedNode selected = root.selectAndExpand();
			double[] scores = selected.simulate();
			selected.backpropagate(scores); // sqrt 2 for c
			} catch(Exception e) { e.printStackTrace(); }
		}
	}


	private void createMachines() {
		for (int ii = 0; ii < ThreadedNode.NUM_THREADS; ii ++) {
			StateMachine m = getInitialStateMachine();
			m.initialize(getMatch().getGame().getRules());
			machines.add(m);
		}
	}

	private void initRoot() throws MoveDefinitionException {
		ThreadedNode.setRole(getRole());
		ThreadedNode.setRole(getRole());
		ThreadedNode.setStateMachine(getStateMachine());
		createMachines();
		ThreadedNode.setStateMachines(machines);
		root = new ThreadedNode(getCurrentState());
	}

	int moveNum = 0;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {

		if (root == null) {
			initRoot();
		} else if (moveNum != 0){
			ThreadedNode matchingChild = root.findMatchingState(getCurrentState());
			root = matchingChild; // may be null
			if (root != null) {
				System.out.println("*** [THREADED] ADVANCED TREE ***");
			} else {
				initRoot();
				System.out.println("*** [THREADED] FAILED TO ADVANCE TREE ***");
			}
		} else {
			System.out.println("[THREADED] First move: advanced tree.");
		}

		expandTree(timeout);
		System.out.println("[THREADED] Num charges = " + ThreadedNode.numCharges);
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
		return "Threaded MCTS";
	}
}
