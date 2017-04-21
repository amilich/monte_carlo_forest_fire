import java.util.ArrayList;
import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

// Andrew

public class MyHeuristics {
	final static int NUM_DEPTH_CHARGES = 10;
	final static double MAX_DELIB_THRESHOLD = 1500; // Timeout parameter

	public static boolean repeatedState(MachineState state, List<MachineState> prevStates) {
		for (MachineState s : prevStates) {
			if (s.equals(state)) return true;
		}
		return false;
	}

	private static boolean compareStateConvergence(Role role, MachineState state, MachineState prevState, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		double prevMob = nStepMobility(role, prevState, 0, machine);
		double currMob = nStepMobility(role, state, 0, machine);
		double prevReachable = numReachableStates(role, prevState, machine);
		double currReachable = numReachableStates(role, state, machine);
		double prevGoal = machine.findReward(role, prevState);
		double currGoal = machine.findReward(role, state);
		boolean conv = (prevGoal == currGoal) && (Math.abs(currReachable - prevReachable) < 3) && (Math.abs(prevMob - currMob) < 5);
//		System.out.println("\tState = " + state);
//		System.out.println("\tPrev = " + prevState);
//		System.out.println("\tMob old = " + prevMob + ", new = " + currMob);
//		System.out.println("\tReach old = " + prevReachable + ", new = " + currReachable);
//		System.out.println("\tGoal old = " + prevGoal + ", new = " + currGoal);
//		System.out.println("Conv = [" + conv + "]");
		//		if (prevGoal == currGoal && prevGoal != 0) return true;
		return conv;
	}


	public static boolean stateConverges(Role role, MachineState state, StateMachine machine, long timeout,
			List<MachineState> prevStates)
					throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		if (prevStates.size() < 2) return false;
		MachineState prevState = prevStates.get(prevStates.size() - 1);
		boolean compOne = compareStateConvergence(role, state, prevState, machine);

		MachineState backTwoStates = prevStates.get(prevStates.size() - 2);
		boolean compTwo = compareStateConvergence(role, state, backTwoStates, machine);

//		System.out.println("One = [" + compOne + "], Two = [" + compTwo + "]");
		return compTwo || compOne;
	}

	public static double weightedHeuristicFunction(Role role, MachineState state, StateMachine machine, long timeout)
			throws GoalDefinitionException {
		double finalHeuristic = 0;

		try {
			double intermedGoalCoeff = 0.4;
			double mobilityCoeff = 0.3;
			double enemyFocusCoeff = 0.1;
			double numReachableStatesCoeff = 0.2;

			double tempScore = machine.findReward(role, state);
			double mobility = nStepMobility(role, state, 0, machine);
			//			double enemyFocus = 0;
			if (machine.getRoles().size() <= 1) {
				intermedGoalCoeff += enemyFocusCoeff;
			} else {
				double enemyFocus = nStepEnemyFocus(role, state, 0, machine);
				finalHeuristic += enemyFocusCoeff * enemyFocus;
			}
			double reachableStates = numReachableStates(role, state, machine);
			//			System.out.println("Mob = [" + mobility + "]"); //; Enemy mob = [" + enemyFocus + "]");
			//			System.out.println("Mob = [" + mobility + "] Temp score = [" + tempScore + "]");
			finalHeuristic += intermedGoalCoeff * tempScore;
			finalHeuristic += mobilityCoeff * mobility;
			finalHeuristic += numReachableStatesCoeff * reachableStates;
		} catch (Exception e) {
			System.out.println("ERROR OCCURRED IN HEURISTIC FUNCTION");
			return 0.0;
		}
		return finalHeuristic;
	}

	/**
	 * Function: checkTime
	 * ---------------------
	 * Check if time is about to expire.
	 */
	public static boolean checkTime(long decisionTime) {
		long currTime = System.currentTimeMillis(); // TODOght way to do this
		if (decisionTime - currTime < MAX_DELIB_THRESHOLD) {
			System.out.println("** TIME EXPIRED ** Returning decision now.");
			return true;
		}
		return false;
	}

	public static double goalStateSimilarity(Role role, MachineState state, StateMachine machine, long timeout)
			throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException {
		ArrayList<MachineState> terminalStates = new ArrayList<MachineState>();
		int numCharges = NUM_DEPTH_CHARGES;
		for (int ii = 0; ii < NUM_DEPTH_CHARGES; ii ++) {
			if (timeout - System.currentTimeMillis() < 2500) { // custom threshold TODO
				System.out.println("Charging took too long");
				numCharges = ii;
				break;
			}
			int[] tempDepth = new int[1];
			System.out.println("Start depth charge");
			terminalStates.add(machine.performDepthCharge(state, tempDepth));
		}
		double similarity = 0;
		for (int ii = 0; ii < NUM_DEPTH_CHARGES; ii ++) {
			similarity += (machine.getGoal(terminalStates.get(ii), role) / 100) *
					compareStates(state, terminalStates.get(ii)) / numCharges;
		}
		return similarity;
	}

	private static double compareStates(MachineState first, MachineState second) {
		String stateOne = first.toString();
		String stateTwo = second.toString();
		double maxLength = Math.max(stateOne.length(), stateTwo.length());
		return 100 * levenshteinDist(stateOne, stateTwo) / maxLength;
	}

	/**
	 * Implementation of Levenshtein Distance Algorithm; credit to:
	 * https://rosettacode.org/wiki/Levenshtein_distance#Java
	 */
	private static double levenshteinDist(String a, String b) {
		int [] costs = new int [b.length() + 1];
		for (int j = 0; j < costs.length; j++)
			costs[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			costs[0] = i;
			int nw = i - 1;
			for (int j = 1; j <= b.length(); j++) {
				int cj = Math.min(1 + Math.min(costs[j], costs[j - 1]), a.charAt(i - 1) == b.charAt(j - 1) ? nw : nw + 1);
				nw = costs[j];
				costs[j] = cj;
			}
		}
		return costs[b.length()];
	}

	public static double numReachableStates(Role role, MachineState state, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		return machine.getNextStates(state).size(); // TODO division
	}

	public static double nStepEnemyMobility(Role role, MachineState state, int n, StateMachine machine)
			throws MoveDefinitionException {
		if (n == 0) {
			double numFriendlyMoves = machine.getLegalMoves(state, role).size();
			double numEnemyMoves = machine.getLegalJointMoves(state).size() / numFriendlyMoves;
			double numEnemyActions = Math.pow(machine.findActions(role).size(), machine.getRoles().size() - 1); // TODO
			return 100 * numEnemyMoves / numEnemyActions;
		} else if (n == 1) {
			return 0;
		} else {
			return 0;
		}
	}

	public static double nStepEnemyFocus(Role role, MachineState state, int n, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		return 100 - nStepEnemyMobility(role, state, n, machine);
	}

	public static double nStepFocus(Role role, MachineState state, int n, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		return 100 - nStepMobility(role, state, n, machine);
	}

	public static double goalValue(Role role, MachineState state, StateMachine machine)
			throws GoalDefinitionException {
		return machine.getGoal(state, role);
	}

	// TODO enemy mobility
	public static double nStepMobility(Role role, MachineState state, int n, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		if (n == 0) {
			double numLegalActions = machine.getLegalMoves(state, role).size();
			double numActions = machine.findActions(role).size(); // TODO TODO
			// double numStates = getStateMachine().getNextStates(state).size(); // TODO bad
			//	double numEnemyMoves = getStateMachine().getLegalJointMoves(state).size() / numActions;
			return 100 * numLegalActions / numActions;
		} else if (n == 1) {
			return 0;
		} else {
			return 0;
		}
	}
}
