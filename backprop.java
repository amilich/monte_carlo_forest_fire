			if (parent != null) {
				if (explored) {
					boolean moveIndexExp = true;
					for (int ii = 0; ii < parent.numEnemyMoves; ii ++) {
						if (parent.children[moveIndex][ii] == null) {
							moveIndexExp = false;
							break;
						} else if (!parent.children[moveIndex][ii].explored) {
							moveIndexExp = false;
							break;
						}
					}
					// now, the parent has this move index fully explored: update array
					if (moveIndexExp) {
						parent.pVals[moveIndex] = ourScore * parent.pCounts[moveIndex];
					}

					boolean eMoveIndexExp = true;
					for (int ii = 0; ii < parent.numMoves; ii ++) {
						if (parent.children[ii][enemyMoveIndex] == null) {
							eMoveIndexExp = false;
							break;
						} else if (!parent.children[ii][enemyMoveIndex].explored) {
							eMoveIndexExp = false;
							break;
						}
					}
					// now, the parent has this enemy move index fully explored: update arrays
					if (eMoveIndexExp) {
						parent.oVals[enemyMoveIndex] = enemyAvgScore * parent.oCounts[enemyMoveIndex];
					}

					boolean allExp = true;
					for (int ii = 0; ii < parent.numMoves; ii ++) {
						for (int jj = 0; jj < parent.numEnemyMoves; jj ++) {
							if (parent.children[ii][jj] == null) {
								allExp = false;
							} else if (!parent.children[ii][jj].explored) {
								allExp = false;
							}
						}
					}

					if (allExp) {
						parent.explored = true;
						// System.out.println("[THREADED] Parent EXP!");
					}
				}
			}