import java.util.ArrayList;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class MyHeuristics {
	final static int NUM_DEPTH_CHARGES = 10;
	final static double MAX_DELIB_THRESHOLD = 1000;

	public static double weightedHeuristicFunction(Role role, MachineState state, StateMachine machine) {
		double finalHeuristic = 0;

		double mobilityCoeff = 0.2;
		double enemyFocusCoeff = 0.5;
		double goalSimilarityCoeff = 0.2;
		double numReachableStatesCoeff = 0.1;

		try {
			double mobility = nStepMobility(role, state, 0, machine);
			double enemyFocus = nStepEnemyFocus(role, state, 0, machine);
			double goalSimilarity = goalStateSimilarity(role, state, machine);
			double reachableStates = numReachableStates(role, state, machine);
			System.out.println("Mob = [" + mobility + "]; Enemy mob = [" + enemyFocus + "]");
			System.out.println("Goal similarity = [" + goalSimilarity + "]; Reachable states = [" + reachableStates + "]");
			finalHeuristic += mobilityCoeff * mobility;
			finalHeuristic += enemyFocusCoeff * enemyFocus;
			finalHeuristic += goalSimilarityCoeff * goalSimilarity;
			finalHeuristic += numReachableStatesCoeff * reachableStates;
		} catch (Exception e) {
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

	public static double goalStateSimilarity(Role role, MachineState state, StateMachine machine)
			throws TransitionDefinitionException, MoveDefinitionException {
		ArrayList<MachineState> terminalStates = new ArrayList<MachineState>();
		for (int ii = 0; ii < NUM_DEPTH_CHARGES; ii ++) {
			int[] tempDepth = new int[1];
			terminalStates.add(machine.performDepthCharge(state, tempDepth));
		}
		double similarity = 0;
		for (int ii = 0; ii < NUM_DEPTH_CHARGES; ii ++) {
			similarity += compareStates(state, terminalStates.get(ii)) / NUM_DEPTH_CHARGES;
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
		return 100 - nStepMobility(role, state, n, machine);
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
