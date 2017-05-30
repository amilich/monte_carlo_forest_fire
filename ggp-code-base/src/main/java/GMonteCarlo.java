//<<<<<<< HEAD
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.Random;
//
//import org.ggp.base.player.gamer.exception.GamePreviewException;
//import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
//import org.ggp.base.util.game.Game;
//import org.ggp.base.util.statemachine.MachineState;
//import org.ggp.base.util.statemachine.Move;
//import org.ggp.base.util.statemachine.Role;
//import org.ggp.base.util.statemachine.StateMachine;
//import org.ggp.base.util.statemachine.cache.CachedStateMachine;
//import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
//import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
//import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
//import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
//
////Gili
//public class GMonteCarlo extends StateMachineGamer{
//
//	private static final double MAX_SCORE = 100;
//	private static final double MIN_SCORE = 0;
//	private static final int MAX_LEVEL = 4;
//
//
//	@Override
//	public StateMachine getInitialStateMachine() {
//		return new CachedStateMachine(new ProverStateMachine());
//	}
//
//	@Override
//	public void stateMachineMetaGame(long timeout)
//			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public Move stateMachineSelectMove(long timeout)
//			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
//		long decisionTime = timeout;
//		System.out.println("Curr time = " + System.currentTimeMillis() + " decision time = " + decisionTime);
//		System.out.println("Timeout " + timeout);
//		MachineState currState = getCurrentState();
//		Move action = getStateMachine().findLegalx(getRole(), currState);
//		try {
//			if (getStateMachine().getRoles().size() == 1) {
//				action = MyDeliberationPlayer.bestmove(getRole(), currState, decisionTime, getStateMachine());
//			} else {
//				action = bestmove(getRole(), currState, decisionTime);
//			}
//		} catch(Exception e) {
//			System.out.println("*** Failed to get best move ***");
//		}
//		// TODO try/catch
//		return action;
//
//	}
//
//	@Override
//	public void stateMachineStop() {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public void stateMachineAbort() {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public void preview(Game g, long timeout) throws GamePreviewException {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public String getName() {
//		// TODO Auto-generated method stub
//		return "G Monte Carlo";
//	}
//
//	private Move bestmove(Role role, MachineState state, long decisionTime)
//			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
//		List<Move> actions = new ArrayList<Move>();
//		actions.addAll(getStateMachine().getLegalMoves(state, role));
//		Collections.shuffle(actions);
//		double score = MIN_SCORE;
//		Move finalMove = null;
//		System.out.println("actions: " + actions);
//		for (int ii = 0; ii < actions.size(); ii ++) {
//			if (MyHeuristics.checkTime(decisionTime)) break;
//			double result = minscore(role, actions.get(ii), state, 0, decisionTime);
//			if (result > score) {
//				score = result;
//				finalMove = actions.get(ii);
//			}
//			if (score == MAX_SCORE) return actions.get(ii);
//		}
//		if (finalMove == null) {
//			System.out.println("*** NULL move selected ***");
//			return actions.get(0);
//		}
//		return finalMove;
//	}
//
//
//	private double maxscore(Role role, MachineState state, int level, long decisionTime) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
//		if (getStateMachine().isTerminal(state)) {
//			return getStateMachine().findReward(role, state);
//		}
//		if (level > MAX_LEVEL) {
//			System.out.println("Reaches here");
//			return montecarlo(role, state, 8, decisionTime);
//		}
//		List<Move> actions = getStateMachine().getLegalMoves(state, role);
//		double score = MIN_SCORE;
//		for (int i = 0; i < actions.size(); i++) {
//			if (MyHeuristics.checkTime(decisionTime)) break;
//
//			double result = minscore(role, actions.get(i), state, level +1, decisionTime);
//			if (result == MAX_SCORE) {
//				return MAX_SCORE;
//			}
//			if (result > score) {
//				score = result;
//			}
//		}
//		return score;
//	}
//
//
//	private double minscore(Role role, Move move, MachineState state, int level, long decisionTime) throws MoveDefinitionException,
//	TransitionDefinitionException, GoalDefinitionException {
//		List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, move);
//		double score = MAX_SCORE;
//
//		for (List<Move> jointAction : jointActions) { // Opponent's move
//			if (MyHeuristics.checkTime(decisionTime)) break;
//
//			MachineState nextState = getStateMachine().getNextState(state, jointAction);
//			double result = maxscore(role, nextState, level+1, decisionTime);
//			if (result == MIN_SCORE) return result;
//			if (result < score) {
//				score = result;
//			}
//		}
//		return score;
//	}
//
//	private double montecarlo(Role role, MachineState state, int count, long decisionTime) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
//		double total = 0;
//		StateMachine machine = getStateMachine();
//		for (int i = 0; i < count; i++) {
//			if (MyHeuristics.checkTime(decisionTime)) break;
//
//			MachineState s = depthcharge(state, decisionTime);
//			total += machine.findReward(role, s);
//		}
//		return total/count;
//	}
//
//
//	private MachineState depthcharge(MachineState state, long decisionTime) throws MoveDefinitionException, TransitionDefinitionException {
//
//		Random r = new Random();
//		StateMachine machine = getStateMachine();
//        while (!machine.findTerminalp(state)) {
//			if (MyHeuristics.checkTime(decisionTime)) break;
//
//            List<MachineState> nextStates = machine.getNextStates(state);
//            for (MachineState s : nextStates) {
//            	if (machine.findTerminalp(s)) return s;
//            }
//            state = nextStates.get(r.nextInt(nextStates.size()));
//        }
//        return state;
//	}
//}
//=======
////import java.util.ArrayList;
////import java.util.Collections;
////import java.util.List;
////import java.util.Random;
////
////import org.ggp.base.player.gamer.exception.GamePreviewException;
////import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
////import org.ggp.base.util.game.Game;
////import org.ggp.base.util.statemachine.MachineState;
////import org.ggp.base.util.statemachine.Move;
////import org.ggp.base.util.statemachine.Role;
////import org.ggp.base.util.statemachine.StateMachine;
////import org.ggp.base.util.statemachine.cache.CachedStateMachine;
////import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
////import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
////import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
////import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;
////
//////Gili
////public class GMonteCarlo extends StateMachineGamer{
////
////	private static final double MAX_SCORE = 100;
////	private static final double MIN_SCORE = 0;
////	private static final int MAX_LEVEL = 4;
////
////
////	@Override
////	public StateMachine getInitialStateMachine() {
////		return new CachedStateMachine(new ProverStateMachine());
////	}
////
////	@Override
////	public void stateMachineMetaGame(long timeout)
////			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
////		// TODO Auto-generated method stub
////
////	}
////
////	@Override
////	public Move stateMachineSelectMove(long timeout)
////			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
////		long decisionTime = timeout;
////		System.out.println("Curr time = " + System.currentTimeMillis() + " decision time = " + decisionTime);
////		System.out.println("Timeout " + timeout);
////		MachineState currState = getCurrentState();
////		Move action = getStateMachine().findLegalx(getRole(), currState);
////		try {
////			if (getStateMachine().getRoles().size() == 1) {
////				action = MyDeliberationPlayer.bestmove(getRole(), currState, decisionTime, getStateMachine());
////			} else {
////				action = bestmove(getRole(), currState, decisionTime);
////			}
////		} catch(Exception e) {
////			System.out.println("*** Failed to get best move ***");
////		}
////		// TODO try/catch
////		return action;
////
////	}
////
////	@Override
////	public void stateMachineStop() {
////		// TODO Auto-generated method stub
////
////	}
////
////	@Override
////	public void stateMachineAbort() {
////		// TODO Auto-generated method stub
////
////	}
////
////	@Override
////	public void preview(Game g, long timeout) throws GamePreviewException {
////		// TODO Auto-generated method stub
////
////	}
////
////	@Override
////	public String getName() {
////		// TODO Auto-generated method stub
////		return "G Monte Carlo";
////	}
////
////	private Move bestmove(Role role, MachineState state, long decisionTime)
////			throws MoveDefinitionException, GoalDefinitionException, TransitionDefinitionException {
////		List<Move> actions = new ArrayList<Move>();
////		actions.addAll(getStateMachine().getLegalMoves(state, role));
////		Collections.shuffle(actions);
////		double score = MIN_SCORE;
////		Move finalMove = null;
////		System.out.println("actions: " + actions);
////		for (int ii = 0; ii < actions.size(); ii ++) {
////			if (MyHeuristics.checkTime(decisionTime)) break;
////			double result = minscore(role, actions.get(ii), state, 0, decisionTime);
////			if (result > score) {
////				score = result;
////				finalMove = actions.get(ii);
////			}
////			if (score == MAX_SCORE) return actions.get(ii);
////		}
////		if (finalMove == null) {
////			System.out.println("*** NULL move selected ***");
////			return actions.get(0);
////		}
////		return finalMove;
////	}
////
////
////	private double maxscore(Role role, MachineState state, int level, long decisionTime) throws GoalDefinitionException, MoveDefinitionException, TransitionDefinitionException {
////		if (getStateMachine().isTerminal(state)) {
////			return getStateMachine().findReward(role, state);
////		}
////		if (level > MAX_LEVEL) {
////			return montecarlo(role, state, 4, decisionTime);
////		}
////		List<Move> actions = getStateMachine().getLegalMoves(state, role);
////		double score = MIN_SCORE;
////		for (int i = 0; i < actions.size(); i++) {
////			if (MyHeuristics.checkTime(decisionTime)) break;
////
////			double result = minscore(role, actions.get(i), state, level, decisionTime);
////			if (result == MAX_SCORE) {
////				return MAX_SCORE;
////			}
////			if (result > score) {
////				score = result;
////			}
////		}
////		return score;
////	}
////
////
////	private double minscore(Role role, Move move, MachineState state, int level, long decisionTime) throws MoveDefinitionException,
////	TransitionDefinitionException, GoalDefinitionException {
////		List<List<Move>> jointActions = getStateMachine().getLegalJointMoves(state, role, move);
////		double score = MAX_SCORE;
////		for (List<Move> jointAction : jointActions) { // Opponent's move
////			if (MyHeuristics.checkTime(decisionTime)) break;
////
////			MachineState nextState = getStateMachine().getNextState(state, jointAction);
////			double result = maxscore(role, nextState, level+1, decisionTime);
////			if (result == MIN_SCORE) return result;
////			if (result < score) {
////				score = result;
////			}
////		}
////		return score;
////	}
////
////	private double montecarlo(Role role, MachineState state, int count, long decisionTime) throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
////		double total = 0;
////		StateMachine machine = getStateMachine();
////		for (int i = 0; i < count; i++) {
////			if (MyHeuristics.checkTime(decisionTime)) break;
////
////			MachineState s = depthcharge(state, decisionTime);
////			total += machine.findReward(role, s);
////		}
////		return total/count;
////	}
////
////
////	private MachineState depthcharge(MachineState state, long decisionTime) throws MoveDefinitionException, TransitionDefinitionException {
////
////		Random r = new Random();
////		StateMachine machine = getStateMachine();
////        while (!machine.findTerminalp(state)) {
////			if (MyHeuristics.checkTime(decisionTime)) break;
////
////            List<MachineState> nextStates = machine.getNextStates(state);
////            for (MachineState s : nextStates) {
////            	if (machine.findTerminalp(s)) return s;
////            }
////            state = nextStates.get(r.nextInt(nextStates.size()));
////        }
////        return state;
////	}
////}
//>>>>>>> dfa69895207c99411123b990a399db90db305f71
