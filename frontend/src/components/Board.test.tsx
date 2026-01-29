import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { Board } from './Board';
import type { BoardState } from '../App';

describe('Board', () => {
  const board: BoardState = {
    boardSize: 3,
    stones: [
      { x: 0, y: 0, color: 'BLACK' },
      { x: 1, y: 1, color: 'WHITE' }
    ]
  };

  it('renders stones at the expected positions', () => {
    render(<Board board={board} onPlayMove={() => {}} />);

    const stones = screen.getAllByRole('img');
    expect(stones.length).toBe(2);
  });

  it('invokes onPlayMove when a cell is clicked', () => {
    const handler = vi.fn();
    render(<Board board={board} onPlayMove={handler} />);

    const cells = screen.getAllByTestId('board-cell');
    fireEvent.click(cells[0]);

    expect(handler).toHaveBeenCalled();
  });
});

