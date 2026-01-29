import React from 'react';

type PuzzleSummary = {
  id: number;
  name: string | null;
  difficulty: number;
};

type Props = {
  puzzles: PuzzleSummary[];
  loading: boolean;
  onSelectPuzzle: (id: number) => void;
};

export const PuzzleList: React.FC<Props> = ({ puzzles, loading, onSelectPuzzle }) => {
  if (loading) {
    return <p>Loading puzzles...</p>;
  }

  if (!puzzles.length) {
    return <p>No puzzles available.</p>;
  }

  return (
    <ul className="puzzle-list">
      {puzzles.map(p => (
        <li key={p.id}>
          <button type="button" onClick={() => onSelectPuzzle(p.id)}>
            {p.name ?? `Puzzle #${p.id}`} (difficulty {p.difficulty})
          </button>
        </li>
      ))}
    </ul>
  );
};

