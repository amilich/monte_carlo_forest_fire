import java.util.List;
import java.util.Random;

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

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		StateMachine machine = getStateMachine();
		System.out.println("IT: " + machine.isTerminal(getCurrentState()));
		MachineState state = getCurrentState();
		Role role = getRole();
		System.out.println("HI");
		List<Move> moves = machine.getLegalMoves(state, role);
		System.out.println("Move # " + moveNum + " has moves: " + moves);
		// machineP.getPropnet().renderToFile("propnetfile0" + moveNum + ".dot");
		double total = 0;
		int count = 0;
		while (!MyHeuristics.checkTime(timeout)) {
			MachineState m = machine.internalDC(state);
			total += machine.getGoal(m, getRole());
			count ++;
		}
		total /= count;
		System.out.println("AVG: " + total);
		System.out.println("COUNT: " + count);
		System.out.println("IT: " + machine.isTerminal(getCurrentState()));
		// System.out.println(machine.getNextStates(getCurrentState()));
		// System.out.println(machine.getLegalJointMoves(getCurrentState()));
		// System.out.println(machine.getLegalMoves(getCurrentState(), getRole()));
		// StateMachine m = new CachedStateMachine(new ProverStateMachine());
		// getInitialStateMachine().
		// Random r = new Random();
		// return moves.get(r.nextInt(moves.size()));
		// System.out.println("Size: " + moves.size());
		return moves.get(0);
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
		return "LegalPlayer";
	}
}
