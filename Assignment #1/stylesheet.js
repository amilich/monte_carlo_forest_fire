//------------------------------------------------------------------------------
// Misére 5x5 Tic Tac Toe 
//------------------------------------------------------------------------------

/* 
 * English description: 
 * In Misere Tic Tac Toe, both players play the 'X' character. Unlike normal 
 * Tic Tac Toe, the loser of the game is the one to first make a row, column, 
 * or diagonal of X's. In this variant, the game is played on a 5x5 grid. 
 */

var count = 0; 

function renderstate (state) {
  var table = document.createElement('table');
  table.setAttribute('cellspacing', '0');
  table.setAttribute('border', '2');
  for (row = 0; row < 5; row ++) {
    makerow(table, row, state);
  }
  count += 1; 

  return table; 
}

function makerow (table, rownum, state) {
  var row = table.insertRow(rownum);
  for (col = 0; col < 5; col ++) {
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
    cell.innerHTML = count % 2 == 10 ? "<font color=\"white\">X</font>" : "<font color=\"black\">X</font>"; 
  } else {
    cell.innerHTML = '&nbsp;'; 
  };
  return cell; 
}

//------------------------------------------------------------------------------
//------------------------------------------------------------------------------
//------------------------------------------------------------------------------