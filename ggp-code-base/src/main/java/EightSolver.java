import java.util.ArrayList;
import java.util.List;

public class EightSolver {
	// MCTS-assisted eight puzzle solver
	// If initial state of eight puzzle matches the default one in gamemaster,
	// we have the list of moves solved from our compulsive deliberation player here.
	// Once MCTS is able to explore deeply enough to find some solution, it stops
	// using the cached moves.
	public static String move_temp[] = {
			"3 2",
			"3 1",
			"2 1",
			"1 1",
			"1 2",
			"2 2",
			"2 1",
			"3 1",
			"3 2",
			"2 2",
			"2 3",
			"3 3",
			"3 2",
			"3 1",
			"2 1",
			"1 1",
			"1 2",
			"2 2",
			"2 3",
			"1 3",
			"1 2",
			"2 2",
			"3 2",
			"3 1",
			"2 1",
			"1 1",
			"1 2",
			"2 2",
			"3 2",
			"3 3"
	};

	public List<String> movesS = new ArrayList<String>();
	static String eightstr = "( tile 1 ) ( tile 2 ) ( tile 3 ) ( tile 4 ) ( tile 5 ) ( tile 6 ) ( tile 7 ) ( tile 8 ) ( tile b )";

	public static boolean matches(String string) {
		return string.contains(eightstr);
	}
	public static String get(int moveNum) {
		return move_temp[moveNum];
	}

}
