import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

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
import org.ggp.base.util.statemachine.implementation.propnet.IntPropNet;

public class MyLegalPlayer extends StateMachineGamer {


	// IntPropNet i = new IntPropNet();
	@Override
	public StateMachine getInitialStateMachine() {
		//return new SamplePropNetStateMachine(); //new CachedStateMachine(new ProverStateMachine());
		// return new CachedStateMachine(new ProverStateMachine());
		//		return new BasicFactorPropNet();
		// return new BasicFactorPropNet();
		//		return new ExpFactorPropNet();
		// return new BitSetPropNet();
		//		return new BitSetNet();
		return new IntPropNet();
		// return new ProverStateMachine();
	}
	// SamplePropNetStateMachine machineP = null;


	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		System.out.println(getMatch().getGame().getDescription());
		System.out.println(getMatch().getGame().getName());
		System.out.println(getMatch().getGame().getStylesheet());
		System.out.println(getMatch().getGame().getRulesheet());
		moveNum = 0;

		// StateMachine m = new ProverStateMachine();
		// m.initialize(getMatch().getGame().getRules(), getRole());
		// StateMachineVerifier.checkMachineConsistency(m, getStateMachine(), 5000);
		// machineP = (SamplePropNetStateMachine) getStateMachine();
		// machineP.getPropnet().renderToFile("propnetfile0" + ".dot");

		//		StateMachine m = new CachedStateMachine(new ProverStateMachine());
		//		m.initialize(getMatch().getGame().getRules());
		//		System.out.println("INIT STATE: " + m.getInitialState());
		//		System.out.println("INIT STATE: " + getStateMachine().getInitialState());
	}

	int moveNum = 0;

	public MachineState customdc(MachineState state) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		Random r = new Random();
		// System.out.println(state);
		int num = 0;
		while(!getStateMachine().isTerminal(state)) {
			System.out.println("\t " + state);
			List<List<Move>> jmoves = getStateMachine().getLegalJointMoves(state);
			state = getStateMachine().getNextState(state, jmoves.get(r.nextInt(jmoves.size())));
			num ++;
		}
		// System.out.println("NUM = " + num);
		// System.out.println("GOAL = " + getStateMachine().getGoal(state, this.getRole()));
		return state;
	}

	static ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		// System.out.println("IT: " + machine.isTerminal(getCurrentState()));
		MachineState state = getCurrentState();
		Role role = getRole();
		System.out.println("HI");
		List<Move> moves = machine.getLegalMoves(state, role);
		System.out.println("Move # " + moveNum + " has moves: " + moves);
		// machineP.getPropnet().renderToFile("propnetfile0" + moveNum + ".dot");
		double total = 0;
		int count = 0;
//		while (!MyHeuristics.checkTime(timeout) || count < 10) {
//			MachineState m = machine.internalDC(state, 1);
//			total += machine.getGoal(m, getRole());
//			count ++;
//		}
		/* int numT = 3;
		int numD = 3;
		double avgScore = 0;
		while (!MyHeuristics.checkTime(timeout)) {
			Set<Future<Double>> futures = new HashSet<Future<Double>>();
			for (int ii = 0; ii < numT; ii ++) {
				Future<Double> future = executor.submit(new DepthCharger(machine, state, getRole(), numD, ii));
				futures.add(future);
			}
			for (Future<Double> future : futures) {
				try {
					double val = future.get().doubleValue();
					avgScore += val;
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			count += numT * numD;
		}
		avgScore /= count;*/
		total /= count;
		System.out.println("AVG: " + total);
		System.out.println("COUNT: " + count);
		System.out.println("IT: " + machine.isTerminal(getCurrentState()));
		if (moveNum == 3) {
			System.out.println();
		}
		for (Move m : moves) {
			if (m.toString().contains(theMoves[moveNum])) {
				System.out.println(m);
				moveNum ++;
				machine.convertAndRender("machinestate");
				List<Move> temp = new ArrayList<Move>();
				temp.add(m);
				if (m.toString().contains("a") && moveNum > 1) {
					System.out.println();
				}
				MachineState next = machine.getNextState(getCurrentState(), temp);
				machine.convertAndRender("machinestate");
//				System.out.println("Curr goal = " + machine.getGoal(getCurrentState(), getRole()) + "; next goal = "
//						+ machine.getGoal(next, getRole()));
				System.out.println(getCurrentState());
				System.out.println(next);
				return m;
			}
		}

		return moves.get(0);
	}
	public String theMoves[] = { "a 13", "b 13", "c 13", "a 13", "b 13", "a 13" };

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
		return "LegalPlayer";
	}
}
