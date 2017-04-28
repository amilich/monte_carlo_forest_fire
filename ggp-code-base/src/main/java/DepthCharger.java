import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;

public class DepthCharger implements Runnable {

	private volatile double value = 0;
	private MachineState state;
	private StateMachine machine;
	private int numCharges = 0;
	private Role role;

	public DepthCharger(StateMachine machine, MachineState state, Role role, int numCharges) {
		this.machine = machine;
		this.role = role;
		this.state = state.clone();
		this.numCharges = numCharges;
	}

	@Override
	public void run() {
		value = 0;
		for (int ii = 0; ii < numCharges; ii ++) {
			int[] tempDepth = new int[1];
			try {
				MachineState depthCharge = machine.performDepthCharge(state, tempDepth);
				value += machine.getGoal(depthCharge, role);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		value /= numCharges;
	}

	public double getValue() {
		return value;
	}
}
