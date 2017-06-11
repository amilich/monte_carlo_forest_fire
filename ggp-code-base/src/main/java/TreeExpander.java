import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.ggp.base.util.statemachine.StateMachine;

/**
 * TreeExpander
 * ------------
 * This class is currently unused.
 * It was initially used to allow multiple threads to perform the entire MCTS sequence -
 * selection, expansion, simulation, and backpropagation - at once; we decided this
 * was too complicated to be useful (it did not yield huge performance benefits).
 *
 * See ThreadedExpansionPlayer.java for usage.
 */
public class TreeExpander implements Runnable {
	private Semaphore canBackprop = new Semaphore(1);
	private MachineLessNode n = null;
	private long timeout = 0;
	private static int MAX_ITERATIONS = 5000000;
	StateMachine machine;
	//	List<StateMachine> machines; // Which state machines can be used by this expander
	public int numLoops = 0;

	public TreeExpander(MachineLessNode n, long timeout, StateMachine machine, int id) {
		this.n = n;
		this.timeout = timeout;
		this.machine = machine;
	}

	@Override
	public void run() {
		numLoops = 0;
		while (!MyHeuristics.checkTime(timeout)) {
			ArrayList<MachineLessNode> path = new ArrayList<MachineLessNode>();
			numLoops ++;
			try {
				canBackprop.acquire();
				MachineLessNode selected = n.select(path, machine);
				MachineLessNode expanded = selected.expand(machine);
				canBackprop.release();
				if (!expanded.equals(path.get(path.size() - 1))) path.add(expanded);
				double score = selected.simulate(machine);
				selected.backpropagate(path, score, machine);
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > MAX_ITERATIONS) break;
		}
		System.out.println(numLoops);
	}
}
