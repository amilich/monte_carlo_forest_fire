import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

public class GDepthCharger implements Runnable{

	int numDepthCharges;
	MachineState state;
	StateMachine machine;
	long decisionTime;
	Role role;

	double avgReward;

	public GDepthCharger(MachineState state, Role role, StateMachine machine, int numDepthCharges, long decisionTime) {
		this.numDepthCharges = numDepthCharges;
		this.state = state.clone();
		this.machine = machine;
		this.role = role;
		this.decisionTime = decisionTime;
		avgReward = 0.0;
	}

	@Override
	public void run() {
		// TODO Auto-generated method stub
		double total = 0.0;
		int curNumCharges = 0;
		for (int i = 0; i < numDepthCharges; i++) {
			curNumCharges += 1;
			if (MyHeuristics.checkTime(decisionTime)) break;
			int[] theDepth = new int[1];
			MachineState s = null;
			try {
				s = machine.performDepthCharge(state, theDepth);
			} catch (TransitionDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (MoveDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				total += machine.findReward(role, s);
			} catch (GoalDefinitionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		if (curNumCharges != 0) avgReward = total / curNumCharges;
	}

	public double getReward() {
		return avgReward;
	}

}
