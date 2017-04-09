//------------------------------------------------------------------------------
// Misére 5x5 Tic Tac Toe 
//------------------------------------------------------------------------------

/* 
 * English description: 
 * In Misere Tic Tac Toe, both players play the 'X' character. Unlike normal 
 * Tic Tac Toe, the loser of the game is the one to first make a row, column, 
 * or diagonal of X's. In this variant, the game is played on a 5x5 grid. 
 */

var init = 0; // Used to keep track of whether first move has been played 
var count = 0; // Counting number of moves 
var board_size = 5; // Size of baord 
var old_state = new Array(); // Old game state 
var changed_row = -1; // Last changed row
var changed_col = -1; // Last chaned column 

function renderstate (state) {
  var table = document.createElement('table');
  table.setAttribute('cellspacing', '0');
  table.setAttribute('border', '2');
  if (!init) { // not initialized yet
    old_state = state; 
    init = true; 
  } else {
    for (ii = 0; ii < board_size * board_size; ii ++) {
      if (state[ii].toString() === old_state[ii].toString()) {
        // noop 
      } else {
        changed_row = Math.floor(ii / board_size); 
        changed_col = ii % board_size; 
        console.log("Detected change at " + ii + ", or (" + changed_row + "," + changed_col + ")"); 
      }
    }
  }
  for (row = 0; row < board_size; row ++) makerow(table, row, state);
  count += 1; 
  old_state = state; 
  return table; 
}

function makerow (table, rownum, state) {
  var row = table.insertRow(rownum);
  for (col = 0; col < board_size; col ++) {
    makecell(row, rownum, col, state);
  }
  return row; 
}

function makecell (row, rownum, colnum, state) {
  var cell = row.insertCell(colnum);
  cell.setAttribute('width', '40');
  cell.setAttribute('height','40');
  cell.setAttribute('align', 'center');
  cell.setAttribute('valign', 'center');
  if (colnum % 2 == 0) {
    cell.setAttribute('bgcolor', rownum % 2 == 0 ? '0xccfffc' : '0xfff6cc');
  } else {
    cell.setAttribute('bgcolor', rownum % 2 == 1 ? '0xccfffc' : '0xfff6cc');
  } 
  cell.setAttribute('style', 'font-family:helvetica; font-size:18pt');

  rownum = (rownum + 1).toString();
  colnum = (colnum + 1).toString();
  var mark = compfindx('Z',seq('cell', rownum, colnum,'Z'), state, seq());
  if (mark && mark != 'b') {
    cell.innerHTML = "<font color=\"black\">X</font>"; 
  } else {
    cell.innerHTML = '&nbsp;'; 
  };
  if (rownum - 1 == changed_row && colnum - 1 == changed_col) {
    console.log("Found cell at (" + changed_row + " , " + changed_col + ")"); 
    cell.innerHTML = "<font color=\"red\">X</font>"; 
    console.log(state); 
  } 
  return cell; 
}

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------