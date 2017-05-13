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
import org.ggp.base.util.statemachine.implementation.propnet.SamplePropNetStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

public class MyLegalPlayer extends StateMachineGamer {
	@Override
	public StateMachine getInitialStateMachine() {
		return new SamplePropNetStateMachine(); //new CachedStateMachine(new ProverStateMachine());
	}

	SamplePropNetStateMachine machineP = null;

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		machineP = (SamplePropNetStateMachine) getStateMachine();
		machineP.getPropnet().renderToFile("propnetfile0" + ".dot");

		StateMachine m = new CachedStateMachine(new ProverStateMachine());
		m.initialize(getMatch().getGame().getRules());
		System.out.println("INIT STATE: " + m.getInitialState());
		System.out.println("INIT STATE: " + getStateMachine().getInitialState());
	}

	int moveNum = 0;

	@Override
	public Move stateMachineSelectMove(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		moveNum ++;
		StateMachine machine = getStateMachine();
		MachineState state = getCurrentState();
		Role role = getRole();
		List<Move> moves = machine.getLegalMoves(state, role);
		System.out.println(moves);
		machineP.getPropnet().renderToFile("propnetfile0" + moveNum + ".dot");

		// StateMachine m = new CachedStateMachine(new ProverStateMachine());
		// getInitialStateMachine().
		// Random r = new Random();
		System.out.println(moves);
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
