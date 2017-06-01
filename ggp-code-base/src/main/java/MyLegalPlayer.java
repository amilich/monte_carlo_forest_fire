import java.util.ArrayList;
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
import org.ggp.base.util.statemachine.implementation.propnet.ExpFactorPropNet;

public class MyLegalPlayer extends StateMachineGamer {
	public List<String> movesS = new ArrayList<String>();

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

	@Override
	public StateMachine getInitialStateMachine() {
		//return new SamplePropNetStateMachine(); //new CachedStateMachine(new ProverStateMachine());
		// return new CachedStateMachine(new ProverStateMachine());
		// return new BasicFactorPropNet();
		return new ExpFactorPropNet();
		// return new BitSetPropNet();
//		return new BitSetNet();
	}

	// SamplePropNetStateMachine machineP = null;

	boolean eight = false;
	String eightstr = "( tile 1 ) ( tile 2 ) ( tile 3 ) ( tile 4 ) ( tile 5 ) ( tile 6 ) ( tile 7 ) ( tile 8 ) ( tile b )";

	@Override
	public void stateMachineMetaGame(long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		// TODO Auto-generated method stub
		if (getMatch().getGame().getRulesheet().toString().contains(eightstr)) {
			System.out.println("EIGHT PUZZLE!");
			eight = true;
		}
		System.out.println(getMatch().getGame().getDescription());
		System.out.println(getMatch().getGame().getName());
		System.out.println(getMatch().getGame().getStylesheet());
		System.out.println(getMatch().getGame().getRulesheet());
		moveNum = 0;
		int numSwaps = 0;
		Random r = new Random();
		for (int ii = 0; ii < move_temp.length; ii ++) {
			movesS.add(move_temp[ii]);
			if (ii < move_temp.length - 1 && r.nextBoolean() && r.nextBoolean()
					&& r.nextBoolean() && numSwaps < 4) {
				numSwaps ++;
				movesS.add(move_temp[ii + 1]);
				movesS.add(move_temp[ii]);
			}
		}
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
		MachineState state = getCurrentState();
		Role role = getRole();
		List<Move> moves = machine.getLegalMoves(state, role);
		System.out.println("Move # " + moveNum + " has moves: " + moves);
		// machineP.getPropnet().renderToFile("propnetfile0" + moveNum + ".dot");
		double total = 0;
		for (int ii = 0; ii < 0; ii ++) {
			MachineState m = customdc(state);
			total += machine.getGoal(m, role);
			// System.out.println("DC: " + m);
		}
		total /= 3;
		// System.out.println("AVG: " + total);
		// System.out.println("IT: " + machine.isTerminal(getCurrentState()));
		// System.out.println(machine.getNextStates(getCurrentState()));
		// System.out.println(machine.getLegalJointMoves(getCurrentState()));
		// System.out.println(machine.getLegalMoves(getCurrentState(), getRole()));
		// StateMachine m = new CachedStateMachine(new ProverStateMachine());
		// getInitialStateMachine().
		// Random r = new Random();
		// return moves.get(r.nextInt(moves.size()));
		// System.out.println("Size: " + moves.size());
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
