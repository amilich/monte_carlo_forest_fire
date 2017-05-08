import java.util.List;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

// Perform random depth charge to terminal state.
public class DepthCharger implements Runnable, Charger {

	private volatile double value = 0;
	private MachineState state;
	private StateMachine machine;
	private int numCharges = 0;
	private Role role;
	private double scores[];
	private boolean multiPlayer;

	// Initialize depth charger with given state machine and state
	public DepthCharger(StateMachine machine, MachineState state, Role role, int numCharges, boolean multiPlayer) {
		this.machine = machine;
		this.role = role;
		this.state = state;
		this.numCharges = numCharges;
		this.multiPlayer = multiPlayer;
		scores = new double[machine.getRoles().size()];
	}

	// Perform depth charges
	@Override
	public void run() {
		int[] tempDepth = new int[1];
		if (!multiPlayer) {
			value = 0;
			for (int ii = 0; ii < numCharges; ii ++) {
				try {
					MachineState depthCharge = machine.performDepthCharge(state, tempDepth);
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
					MachineState depthCharge = machine.performDepthCharge(state, tempDepth);
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
	}

	// Return average scores for all players
	@Override
	public double[] getValues() {
		return scores;
	}

	// Return average score for one player
	@Override
	public double getValue() {
		return value;
	}
}
