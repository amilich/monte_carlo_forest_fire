import java.util.ArrayList;
import java.util.concurrent.Semaphore;

import org.ggp.base.util.statemachine.StateMachine;

public class TreeExpander implements Runnable {
	private Semaphore canBackprop = null;
	private MachineLessNode n = null;
	private long timeout = 0;
	private static int MAX_ITERATIONS = 5000000;
	StateMachine machine;
//	List<StateMachine> machines;
//	int minInd, maxInd;

	public TreeExpander(MachineLessNode n, Semaphore s, long timeout, StateMachine machine) {
		this.n = n;
		this.timeout = timeout;
		canBackprop = s;
		this.machine = machine;
//		this.minInd = minInd;
//		this.maxInd = maxInd;
	}

	@Override
	public void run() {
		int numLoops = 0;
		while (!MyHeuristics.checkTime(timeout)) {
			ArrayList<MachineLessNode> path = new ArrayList<MachineLessNode>();
			numLoops ++;
			try {
				// MachineLessNode selected = n.selectAndExpand(path);
				canBackprop.acquire();
				MachineLessNode selected = n.select(path, machine);
				MachineLessNode expanded = selected.expand(machine);
				canBackprop.release();
				if (!expanded.equals(path.get(path.size() - 1))) path.add(expanded);

				double score = selected.simulate(machine);
				// canBackprop.acquire();
				selected.backpropagate(path, score, machine); // sqrt 2 for c
				// canBackprop.release();
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > MAX_ITERATIONS) break; // TODO
		}
		System.out.println(numLoops);
	}
}
