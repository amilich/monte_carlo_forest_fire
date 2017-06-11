import java.util.ArrayList;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class MyHeuristics {
	final static int NUM_DEPTH_CHARGES = 10;

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

	@SuppressWarnings("unused")
	public static double numReachableStates(Role role, MachineState state, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		return machine.getNextStates(state).size(); // TODO division
	}


	@SuppressWarnings("unused")
	public static double nStepEnemyMobility(Role role, MachineState state, int n, StateMachine machine) {
		if (n == 0) {
			 return 0;
		} else if (n == 1) {
			return 0;
		} else {
			return 0;
		}
	}

	@SuppressWarnings("unused")
	public static double nStepFocus(Role role, MachineState state, int n, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		return 100 - nStepMobility(role, state, n, machine);
	}

	@SuppressWarnings("unused")
	public static double goalValue(Role role, MachineState state, StateMachine machine)
			throws GoalDefinitionException {
		return machine.getGoal(state, role);
	}

	// TODO enemy mobility
	public static double nStepMobility(Role role, MachineState state, int n, StateMachine machine)
			throws MoveDefinitionException, TransitionDefinitionException {
		if (n == 0) {
			 double numLegalActions = machine.getLegalMoves(state, role).size();
			 double numActions = machine.findActions(role);
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
