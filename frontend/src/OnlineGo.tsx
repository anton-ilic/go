import React, { useEffect, useMemo, useState } from 'react';
import { Board } from './components/Board';
import type { BoardState } from './App';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api';

type OnlineGameState = {
  board: BoardState;
  currentTurn: 'BLACK' | 'WHITE' | string;
  status: 'WAITING_FOR_OPPONENT' | 'IN_PROGRESS' | 'FINISHED' | string;
  blackPlayerName: string | null;
  whitePlayerName: string | null;
};

type CreateOnlineGameResponse = {
  gameId: string;
  playerId: string;
  color: 'BLACK' | 'WHITE' | string;
  state: OnlineGameState;
};

type JoinOnlineGameResponse = CreateOnlineGameResponse;

type OnlineMoveResponse = {
  status: string;
  message: string;
  state: OnlineGameState;
};

type Props = {
  initialGameId?: string | null;
};

export const OnlineGo: React.FC<Props> = ({ initialGameId }) => {
  const [mode, setMode] = useState<'idle' | 'creating' | 'joining' | 'playing'>('idle');
  const [playerName, setPlayerName] = useState('');
  const [gameId, setGameId] = useState<string | null>(initialGameId ?? null);
  const [playerId, setPlayerId] = useState<string | null>(null);
  const [color, setColor] = useState<'BLACK' | 'WHITE' | string | null>(null);
  const [state, setState] = useState<OnlineGameState | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const shareUrl = useMemo(() => {
    if (!gameId) return null;
    const url = new URL(window.location.href);
    url.searchParams.set('gameId', gameId);
    return url.toString();
  }, [gameId]);

  useEffect(() => {
    if (initialGameId && !gameId) {
      setGameId(initialGameId);
      setMode('joining');
    }
  }, [initialGameId, gameId]);

  // Polling
  useEffect(() => {
    if (!gameId || !playerId || !state || state.status === 'FINISHED') {
      return;
    }

    const interval = window.setInterval(async () => {
      try {
        const res = await fetch(
          `${API_BASE_URL}/online-games/${gameId}?playerId=${encodeURIComponent(playerId)}`
        );
        if (!res.ok) {
          return;
        }
        const data: OnlineGameState = await res.json();
        setState(data);
      } catch {
        // ignore transient errors
      }
    }, 1500);

    return () => window.clearInterval(interval);
  }, [gameId, playerId, state?.status]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/online-games`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerName: playerName || 'Player 1' })
      });
      if (!res.ok) {
        throw new Error(`Failed to create game: ${res.status}`);
      }
      const data: CreateOnlineGameResponse = await res.json();
      setGameId(data.gameId);
      setPlayerId(data.playerId);
      setColor(data.color);
      setState(data.state);
      setMode('playing');
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!gameId) return;
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/online-games/${gameId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerName: playerName || 'Player 2' })
      });
      if (!res.ok) {
        throw new Error(`Failed to join game: ${res.status}`);
      }
      const data: JoinOnlineGameResponse = await res.json();
      setPlayerId(data.playerId);
      setColor(data.color);
      setState(data.state);
      setMode('playing');
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handlePlayMove = async (x: number, y: number) => {
    if (!gameId || !playerId || !state) return;
    if (state.status !== 'IN_PROGRESS') return;
    if ((state.currentTurn === 'BLACK' && color !== 'BLACK') ||
        (state.currentTurn === 'WHITE' && color !== 'WHITE')) {
      return;
    }
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/online-games/${gameId}/moves`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, x, y })
      });
      if (!res.ok) {
        throw new Error(`Failed to play move: ${res.status}`);
      }
      const data: OnlineMoveResponse = await res.json();
      setState(data.state);
      setStatusMessage(data.message);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const boardState = state?.board;

  return (
    <div>
      <h2>Play Go Online</h2>
      {mode === 'idle' && (
        <div className="online-actions">
          <button type="button" onClick={() => setMode('creating')}>
            Create game
          </button>
          <button type="button" onClick={() => setMode('joining')}>
            Join game
          </button>
        </div>
      )}

      {mode === 'creating' && (
        <form onSubmit={handleCreate} className="online-form">
          <label>
            Your name
            <input
              value={playerName}
              onChange={e => setPlayerName(e.target.value)}
              placeholder="Player 1"
            />
          </label>
          <button type="submit">Create</button>
        </form>
      )}

      {mode === 'joining' && (
        <form onSubmit={handleJoin} className="online-form">
          {!gameId && (
            <label>
              Game ID
              <input
                value={gameId ?? ''}
                onChange={e => setGameId(e.target.value)}
                placeholder="Paste game ID"
              />
            </label>
          )}
          <label>
            Your name
            <input
              value={playerName}
              onChange={e => setPlayerName(e.target.value)}
              placeholder="Player 2"
            />
          </label>
          <button type="submit" disabled={!gameId}>
            Join
          </button>
        </form>
      )}

      {mode === 'playing' && boardState && (
        <div className="online-game">
          <div className="online-info">
            <div>Game ID: {gameId}</div>
            {shareUrl && (
              <div>
                Share this link with your opponent:
                <div className="share-url">{shareUrl}</div>
              </div>
            )}
            <div>You are: {color}</div>
            <div>Current turn: {state?.currentTurn}</div>
            <div>
              Black: {state?.blackPlayerName ?? '—'} | White: {state?.whitePlayerName ?? '—'}
            </div>
          </div>
          <Board board={boardState} onPlayMove={handlePlayMove} />
        </div>
      )}

      {statusMessage && <p className="status">{statusMessage}</p>}
    </div>
  );
}

