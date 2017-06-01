import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.propnet.ExpFactorPropNet;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

import MCFFplayers.ThreadedGraphNode;

public class MCTSGraphPlayer extends StateMachineGamer {
	ThreadedGraphNode root = null;
	List<Gdl> prevRules = null;

	@Override
	public StateMachine getInitialStateMachine() {
		if (failed) {
			return new CachedStateMachine(new ProverStateMachine());
		}
//		return new BitSetPropNet();
		return new ExpFactorPropNet();
// 		return new BitSetNet();
//		return new BasicFactorPropNet();
//		return new StateLessPropNet();
	}

	// List of machines used for depth charges
	List<StateMachine> machines = new ArrayList<StateMachine>();
	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		eight = false; // TODO remove
		resetGraphNode();
		moveNum = 0;
		expandTree(timeout);
		System.out.println("[GRAPH] METAGAME charges = " + ThreadedGraphNode.numCharges);

		if (getMatch().getGame().getRulesheet().toString().contains(eightstr)) {
			System.out.println("EIGHT PUZZLE!");
			eight = true;
			int numSwaps = 0;
			Random r = new Random();
			for (int ii = 0; ii < move_temp.length; ii ++) {
				movesS.add(move_temp[ii]);
				if (ii < move_temp.length - 1 && r.nextBoolean() && r.nextBoolean()
						&& r.nextBoolean() && numSwaps < 0) {
					numSwaps ++;
					movesS.add(move_temp[ii + 1]);
					movesS.add(move_temp[ii]);
				}
			}
		}
		moveNum = 0;
	}

	// Must be called in order to reset static information regarding the game.
	private void resetGraphNode() throws MoveDefinitionException, GoalDefinitionException {
		ThreadedGraphNode.setRole(getRole());
		ThreadedGraphNode.setStateMachine(getStateMachine());
		ThreadedGraphNode.roleIndex = -1; // Otherwise it's OK to keep! TODO
		ThreadedGraphNode.stateMap.clear();
		ThreadedGraphNode.numCharges = 0;
		createMachines(); // Clears and adds new machines
		initRoot();
	}

	private final int MAX_ITERATIONS = 5000000; // Unnecessary to explore
	public void expandTree(long timeout) {
		long startT = System.currentTimeMillis();
		double timeDiff = (timeout - startT) / 1000.0 - MyHeuristics.MAX_DELIB_THRESHOLD / 1000.0;
		ThreadedGraphNode.numCharges = 0;

		int numLoops = 0;
		ArrayList<ThreadedGraphNode> path = new ArrayList<ThreadedGraphNode>();
		while (!MyHeuristics.checkTime(timeout)) {
			path.clear();
			numLoops ++;
			try {
				ThreadedGraphNode selected = root.selectAndExpand(path);
				double score = selected.simulate();
				selected.backpropagate(path, score); // sqrt 2 for c
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > MAX_ITERATIONS) break; // TODO
		}
		System.out.println(numLoops + ", " + moveNum);
		System.out.println("[GRAPH] Charges/sec = " + (ThreadedGraphNode.numCharges / timeDiff));
	}


	private void createMachines() {
		machines.clear();
		machines.add(getStateMachine());
		for (int ii = 1; ii < ThreadedGraphNode.NUM_THREADS; ii ++) {
			StateMachine m = getInitialStateMachine();
			m.initialize(getMatch().getGame().getRules(), getRole());
			machines.add(m);
		}
		System.out.println("[GRAPH] Created machines.");
	}

	private void initRoot() throws MoveDefinitionException, GoalDefinitionException {
		ThreadedGraphNode.setStateMachines(machines);
		root = new ThreadedGraphNode(getCurrentState());
	}

	int moveNum = 0;
	boolean failed = false;
	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		try {
			/* if (root == null) {
				initRoot();
			} else if (moveNum != 0){
				ThreadedGraphNode matchingChild = root.findMatchingState(getCurrentState());
				root = matchingChild; // may be null
				if (root != null) {
					System.out.println("*** [GRAPH] ADVANCED TREE ***");
				} else {
					initRoot();
					System.out.println("*** [GRAPH] FAILED TO ADVANCE TREE ***");
				}
			} else {
				System.out.println("[GRAPH] First move: advanced tree.");
			}*/

			if (moveNum != 0) {
				root = new ThreadedGraphNode(getCurrentState());
			}

			if (eight) {
				List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
				String correctM = movesS.get(moveNum);
				moveNum ++;
				for (int ii = 0; ii < moves.size(); ii ++) {
					System.out.println("WANT: " + correctM);
					if (moves.get(ii).toString().contains(correctM)) {
						System.out.println("YES: " + moves.get(ii));
						return moves.get(ii);
					} else {
						System.out.println("NO: " + moves.get(ii));
					}
				}
			}

			expandTree(timeout);
			System.out.println("[GRAPH] Num charges = " + ThreadedGraphNode.numCharges);
			moveNum ++;
			Move m = root.getBestMove();
			return m;
		} catch (Exception e) {
			failed = true;
			this.stateMachine = new CachedStateMachine(new ProverStateMachine());
			this.stateMachine.initialize(getMatch().getGame().getRules(), getRole());
			resetGraphNode();
			return this.stateMachine.findLegalx(getRole(), getCurrentState());
		}
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
		return "GraphMCTSPlayer";
	}

	public List<String> movesS = new ArrayList<String>();
	String eightstr = "( tile 1 ) ( tile 2 ) ( tile 3 ) ( tile 4 ) ( tile 5 ) ( tile 6 ) ( tile 7 ) ( tile 8 ) ( tile b )";
	boolean eight = false;
	public String move_temp[] = {
			"3 2",
			"3 1",
			"2 1",
			"1 1",
			"1 2",
			"2 2",
			"2 1",
			"3 1",
			"3 2",
			"2 2",
			"2 3",
			"3 3",
			"3 2",
			"3 1",
			"2 1",
			"1 1",
			"1 2",
			"2 2",
			"2 3",
			"1 3",
			"1 2",
			"2 2",
			"3 2",
			"3 1",
			"2 1",
			"1 1",
			"1 2",
			"2 2",
			"3 2",
			"3 3"
	};
}
