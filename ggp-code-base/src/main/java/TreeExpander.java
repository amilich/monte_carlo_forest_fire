import java.util.ArrayList;
import java.util.concurrent.Semaphore;

public class TreeExpander implements Runnable {
	private Semaphore canBackprop = null;
	private ThreadedGraphNode n = null;
	private long timeout = 0;
	private static int MAX_ITERATIONS = 5000000;

	public TreeExpander(ThreadedGraphNode n, Semaphore s, long timeout) {
		this.n = n;
		this.timeout = timeout;
		canBackprop = s;
	}

	@Override
	public void run() {
		int numLoops = 0;
		while (!MyHeuristics.checkTime(timeout)) {
			ArrayList<ThreadedGraphNode> path = new ArrayList<ThreadedGraphNode>();
			numLoops ++;
			try {
				// ThreadedGraphNode selected = n.selectAndExpand(path);
				canBackprop.acquire();
				ThreadedGraphNode selected = n.select(path);
				
				ThreadedGraphNode expanded = selected.expand();
				canBackprop.release();
				if (!expanded.equals(path.get(path.size() - 1))) path.add(expanded);

				double score = selected.simulate();
				// canBackprop.acquire();
				selected.backpropagate(path, score); // sqrt 2 for c
				// canBackprop.release();
			} catch(Exception e) {
				e.printStackTrace();
			}
			if (numLoops > MAX_ITERATIONS) break; // TODO
		}
		System.out.println(numLoops);
	}
}
