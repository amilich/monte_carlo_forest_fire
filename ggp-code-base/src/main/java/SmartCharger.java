import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

import MCFFplayers.Charger;

public class SmartCharger implements Runnable, Charger {

	private volatile double value = 0;
	private MachineState state;
	private StateMachine machine;
	private int numCharges = 0;
	private Role role;
	private double scores[];
	private boolean multiPlayer;

	public SmartCharger(StateMachine machine, MachineState state, Role role, int numCharges, boolean multiPlayer) {
		this.machine = machine;
		this.role = role;
		this.state = state;
		this.numCharges = numCharges;
		this.multiPlayer = multiPlayer;
		scores = new double[machine.getRoles().size()];
	}

	private MachineState smartCharge(MachineState state)
			throws MoveDefinitionException, TransitionDefinitionException, GoalDefinitionException {
		Random r = new Random();
		while (!machine.isTerminal(state)) {
			List<MachineState> states = machine.getNextStates(state);
			boolean foundTerm = false;
			for (MachineState s : states) {
				if (machine.isTerminal(s)) {
					if (machine.getGoal(state, role) == 0) {
						state = s;
						foundTerm = true;
					}
				}
			}
			if (!foundTerm) {
				state = states.get(r.nextInt(states.size()));
			}
		}
		return state;
	}

	@Override
	public void run() {
		if (!multiPlayer) {
			value = 0;
			for (int ii = 0; ii < numCharges; ii ++) {
				try {
					MachineState depthCharge = smartCharge(state);
					value += machine.getGoal(depthCharge, role);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			value /= numCharges;
		} else { // n player case
			List<Role> roles;
			try {
				roles = machine.getRoles();
			} catch (Exception e) {
				e.printStackTrace();
				return;
			}
			for (int ii = 0; ii < numCharges; ii ++) {
				try {
					MachineState depthCharge = smartCharge(state);
					for (int jj = 0; jj < roles.size(); jj ++) {
						scores[jj] += machine.getGoal(depthCharge, roles.get(jj));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			for (int ii = 0; ii < machine.getRoles().size(); ii ++) {
				scores[ii] /= numCharges;
			}
		}
//		System.out.println("SMART CHARGER FINISHED: [" + System.currentTimeMillis() + "]");
	}

	@Override
	public double[] getValues() {
		return scores;
	}

	@Override
	public double getValue() {
		return value;
	}
}
