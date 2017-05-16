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
import org.ggp.base.util.statemachine.implementation.propnet.ConcurrentPropNetMachine;
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;

public class MyLegalPlayer extends StateMachineGamer {
	@Override
	public StateMachine getInitialStateMachine() {
		//return new SamplePropNetStateMachine(); //new CachedStateMachine(new ProverStateMachine());
		// return new CachedStateMachine(new ProverStateMachine());
		return new ConcurrentPropNetMachine();
	}

	SamplePropNetStateMachine machineP = null;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		moveNum = 0;
		// machineP = (SamplePropNetStateMachine) getStateMachine();
		// machineP.getPropnet().renderToFile("propnetfile0" + ".dot");

//		StateMachine m = new CachedStateMachine(new ProverStateMachine());
//		m.initialize(getMatch().getGame().getRules());
//		System.out.println("INIT STATE: " + m.getInitialState());
//		System.out.println("INIT STATE: " + getStateMachine().getInitialState());
	}

	int moveNum = 0;

	public MachineState customdc(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
		Random r = new Random();
		System.out.println(state);
		int num = 0;
		while(!getStateMachine().isTerminal(state)) {
			System.out.println("\t " + state);
			List<List<Move>> jmoves = getStateMachine().getLegalJointMoves(state);
			state = getStateMachine().getNextState(state, jmoves.get(r.nextInt(jmoves.size())));
			num ++;
			if (num == 2) {
				System.out.println("hi");
			}
		}
		System.out.println("NUM = " + num);
		return state;
	}

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		moveNum ++;
		StateMachine machine = getStateMachine();
		MachineState state = getCurrentState();
		Role role = getRole();
		List<Move> moves = machine.getLegalMoves(state, role);
		System.out.println(moves);
		// machineP.getPropnet().renderToFile("propnetfile0" + moveNum + ".dot");
		for (int ii = 0; ii < 1; ii ++) {
			MachineState m = customdc(state);
			// System.out.println("DC: " + m);
		}
		System.out.println("IT: " + machine.isTerminal(getCurrentState()));
		// System.out.println(machine.getNextStates(getCurrentState()));
		System.out.println(machine.getLegalJointMoves(getCurrentState()));
		// StateMachine m = new CachedStateMachine(new ProverStateMachine());
		// getInitialStateMachine().
		// Random r = new Random();
		// return moves.get(r.nextInt(moves.size()));
		System.out.println("Size: " + moves.size());
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
