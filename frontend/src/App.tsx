import React, { useEffect, useState } from 'react';
import { Board } from './components/Board';
import { PuzzleList } from './components/PuzzleList';

export type Stone = { x: number; y: number; color: 'WHITE' | 'BLACK' };

export type BoardState = {
  boardSize: number;
  stones: Stone[];
};

type PuzzleSummary = {
  id: number;
  name: string | null;
  difficulty: number;
};

type CreateGameResponse = {
  gameId: string;
  playerColor: 'WHITE' | 'BLACK';
  state: {
    board: BoardState;
    solved: boolean;
  };
};

type MoveResponse = {
  gameId: string;
  status: 'IN_PROGRESS' | 'SOLVED' | 'INCORRECT_MOVE' | string;
  message: string;
  state: {
    board: BoardState;
    solved: boolean;
  } | null;
};

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

export const App: React.FC = () => {
  const [puzzles, setPuzzles] = useState<PuzzleSummary[]>([]);
  const [loadingPuzzles, setLoadingPuzzles] = useState(false);
  const [currentGame, setCurrentGame] = useState<CreateGameResponse | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    const loadPuzzles = async () => {
      setLoadingPuzzles(true);
      try {
        const res = await fetch(`${API_BASE_URL}/puzzles`);
        if (!res.ok) {
          throw new Error(`Failed to load puzzles: ${res.status}`);
        }
        const data: PuzzleSummary[] = await res.json();
        setPuzzles(data);
      } catch (err) {
        setStatusMessage((err as Error).message);
      } finally {
        setLoadingPuzzles(false);
      }
    };
    loadPuzzles();
  }, []);

  const handleSelectPuzzle = async (puzzleId: number) => {
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/games`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ puzzleId })
      });
      if (!res.ok) {
        throw new Error(`Failed to start game: ${res.status}`);
      }
      const data: CreateGameResponse = await res.json();
      setCurrentGame(data);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handlePlayMove = async (x: number, y: number) => {
    if (!currentGame) {
      return;
    }
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/games/${currentGame.gameId}/moves`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ x, y })
      });
      if (!res.ok) {
        throw new Error(`Failed to play move: ${res.status}`);
      }
      const data: MoveResponse = await res.json();
      if (data.state) {
        setCurrentGame(prev =>
          prev
            ? {
                ...prev,
                state: data.state!
              }
            : null
        );
      }
      setStatusMessage(data.message);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const boardState = currentGame?.state.board;

  return (
    <div className="app">
      <header className="app-header">
        <h1>Go Puzzles</h1>
      </header>
      <main className="app-main">
        <section className="sidebar">
          <h2>Puzzles</h2>
          <PuzzleList
            puzzles={puzzles}
            loading={loadingPuzzles}
            onSelectPuzzle={handleSelectPuzzle}
          />
        </section>
        <section className="board-section">
          {boardState ? (
            <Board board={boardState} onPlayMove={handlePlayMove} />
          ) : (
            <p>Select a puzzle to begin.</p>
          )}
          {currentGame?.state.solved && (
            <p className="status solved">Puzzle solved! 🎉</p>
          )}
          {statusMessage && <p className="status">{statusMessage}</p>}
        </section>
      </main>
    </div>
  );
};

