package MCFFplayers;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

// Perform random depth charge to terminal state.
public class DepthCharger implements Callable<Double>, Charger {
	private volatile double value = 0;
	private MachineState state;
	private StateMachine machine;
	private int numCharges = 0;
	private Role role;
	int roleIndex;
    private Random r = new Random();

	// Initialize depth charger with given state machine and state
	public DepthCharger(StateMachine machine, MachineState state, Role role, int numCharges, int roleIndex) {
		this.machine = machine;
		this.role = role;
		this.state = state;
		this.numCharges = numCharges;
	}

    public MachineState customdc(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
        while (!machine.isTerminal(state)) {
        	List<List<Move>> jmoves = machine.getLegalJointMoves(state);
            state = machine.getNextState(state, jmoves.get(r.nextInt(jmoves.size())));
        }
        return state;
    }

	// Perform depth charges
	@Override
	public Double call() {
		value = 0;
		for (int ii = 0; ii < numCharges; ii ++) {
			try {
				MachineState depthCharge = customdc(state); // new
				value += machine.getGoal(depthCharge, role);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		value /= numCharges;
		return new Double(value);
	}

	@Override
	public double[] getValues() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public double getValue() {
		// TODO Auto-generated method stub
		return 0;
	}
}