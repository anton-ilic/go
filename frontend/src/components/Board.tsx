import React from 'react';
import type { BoardState } from '../App';

type Props = {
  board: BoardState;
  onPlayMove: (x: number, y: number) => void;
};

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

  const rows = [];
  for (let row = 0; row < size; row++) {
    const cells = [];
    for (let col = 0; col < size; col++) {
      const x = col;
      const y = size - 1 - row;
      const stone = hasStoneAt(x, y);
      cells.push(
        <div
          key={`${row}-${col}`}
          className="board-cell"
          data-testid="board-cell"
          onClick={() => handleClick(row, col)}
        >
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

  return <div className="board">{rows}</div>;
};

