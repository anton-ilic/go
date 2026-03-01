import React from 'react';
import type { BoardState } from '../App';

type Props = {
  board: BoardState;
  onPlayMove: (x: number, y: number) => void;
};

/**
 * Renders a Go board with stones placed on intersections (where grid lines cross).
 *
 * Each cell in the grid represents an intersection. Grid lines are drawn through
 * the center of each cell using CSS pseudo-elements. Edge cells only draw half-lines
 * so the grid terminates correctly at the board boundary.
 *
 * Star points (hoshi) are shown on standard positions for 19x19 and 9x9 boards.
 */
export const Board: React.FC<Props> = ({ board, onPlayMove }) => {
  const size = board.boardSize;

  const handleClick = (row: number, col: number) => {
    // Board coordinates use (x, y) with y from bottom; rows here are from top.
    const x = col;
    const y = size - 1 - row;
    onPlayMove(x, y);
  };

  const hasStoneAt = (x: number, y: number) =>
    board.stones.find(s => s.x === x && s.y === y);

  // Star point (hoshi) positions for common board sizes
  const starPoints = getStarPoints(size);
  const isStarPoint = (col: number, row: number) =>
    starPoints.some(([sx, sy]) => sx === col && sy === row);

  const rows = [];
  for (let row = 0; row < size; row++) {
    const cells = [];
    for (let col = 0; col < size; col++) {
      const x = col;
      const y = size - 1 - row;
      const stone = hasStoneAt(x, y);

      // Build edge classes so CSS can clip grid lines at borders
      const classes = ['board-cell'];
      if (row === 0) classes.push('edge-top');
      if (row === size - 1) classes.push('edge-bottom');
      if (col === 0) classes.push('edge-left');
      if (col === size - 1) classes.push('edge-right');

      cells.push(
        <div
          key={`${row}-${col}`}
          className={classes.join(' ')}
          data-testid="board-cell"
          onClick={() => handleClick(row, col)}
        >
          {/* Star point dot */}
          {!stone && isStarPoint(col, row) && (
            <div className="star-point" />
          )}
          {stone && (
            <div
              className={`stone ${stone.color === 'WHITE' ? 'white' : 'black'}`}
              role="img"
            />
          )}
        </div>
      );
    }
    rows.push(
      <div className="board-row" key={row}>
        {cells}
      </div>
    );
  }

  return <div className={`board board-size-${size}`}>{rows}</div>;
};

/** Returns star point positions as [col, row] pairs (visual coordinates, 0-indexed from top-left). */
function getStarPoints(size: number): [number, number][] {
  if (size === 19) {
    // Standard 19x19 star points
    return [
      [3, 3], [9, 3], [15, 3],
      [3, 9], [9, 9], [15, 9],
      [3, 15], [9, 15], [15, 15],
    ];
  }
  if (size === 13) {
    return [
      [3, 3], [9, 3],
      [6, 6],
      [3, 9], [9, 9],
    ];
  }
  if (size === 9) {
    return [
      [2, 2], [6, 2],
      [4, 4],
      [2, 6], [6, 6],
    ];
  }
  if (size === 11) {
    return [
      [2, 2], [5, 2], [8, 2],
      [2, 5], [5, 5], [8, 5],
      [2, 8], [5, 8], [8, 8],
    ];
  }
  return [];
}