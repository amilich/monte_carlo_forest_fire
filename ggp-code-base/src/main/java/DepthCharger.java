import java.util.List;
import java.util.Random;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;

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

	public MachineState customdc(MachineState state) throws TransitionDefinitionException, MoveDefinitionException {
        Random r = new Random();
        // System.out.println(state);
        while(!machine.isTerminal(state)) {
        	List<List<Move>> jmoves = machine.getLegalJointMoves(state);
            state = machine.getNextState(state, jmoves.get(r.nextInt(jmoves.size())));
        }
        return state;
    }

	// Perform depth charges
	@Override
	public void run() {
		if (!multiPlayer) {
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
					MachineState depthCharge = machine.performDepthCharge(state, null);
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
