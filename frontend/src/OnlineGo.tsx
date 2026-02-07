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
  const [phase, setPhase] = useState<'setup' | 'playing'>('setup');
  const [playerName, setPlayerName] = useState('');
  const [gameId, setGameId] = useState<string | null>(initialGameId ?? null);
  const [playerId, setPlayerId] = useState<string | null>(null);
  const [color, setColor] = useState<'BLACK' | 'WHITE' | string | null>(null);
  const [state, setState] = useState<OnlineGameState | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [autoJoinInProgress, setAutoJoinInProgress] = useState(false);

  const shareUrl = useMemo(() => {
    if (!gameId) return null;
    const url = new URL(window.location.href);
    url.searchParams.set('gameId', gameId);
    return url.toString();
  }, [gameId]);

  // If we land on a shared link with ?gameId=..., auto-join as the second player.
  useEffect(() => {
    if (!initialGameId || gameId || playerId || state || autoJoinInProgress) {
      return;
    }
    setGameId(initialGameId);
    setAutoJoinInProgress(true);
    setStatusMessage(null);

    (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/online-games/${initialGameId}/join`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ playerName: 'Player 2' })
        });
        if (!res.ok) {
          setStatusMessage(`Failed to auto-join game: ${res.status}`);
          setPhase('setup');
          return;
        }
        const data: JoinOnlineGameResponse = await res.json();
        setPlayerId(data.playerId);
        setColor(data.color);
        setState(data.state);
        setPhase('playing');
      } catch (err) {
        setStatusMessage((err as Error).message);
        setPhase('setup');
      } finally {
        setAutoJoinInProgress(false);
      }
    })();
  }, [initialGameId, gameId, playerId, state, autoJoinInProgress]);

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
      setPhase('playing');

      // Update the URL so the creator is on the shareable game link.
      try {
        const url = new URL(window.location.href);
        url.searchParams.set('gameId', data.gameId);
        window.history.replaceState({}, '', url.toString());
      } catch {
        // ignore URL errors
      }
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
      setPhase('playing');
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
      {phase === 'setup' && (
        <div className="online-setup">
          <div className="online-column">
            <h3>Start a new game</h3>
            <p>You will be Black. Share the game code with your friend so they can join as White.</p>
            <form onSubmit={handleCreate} className="online-form">
              <label>
                Your name
                <input
                  value={playerName}
                  onChange={e => setPlayerName(e.target.value)}
                  placeholder="Player 1"
                />
              </label>
              <button type="submit">Create game</button>
            </form>
          </div>
          <div className="online-column">
            <h3>Join a game</h3>
            <p>Paste the game code you received and choose your name.</p>
            <form onSubmit={handleJoin} className="online-form">
              <label>
                Game code
                <input
                  value={gameId ?? ''}
                  onChange={e => setGameId(e.target.value)}
                  placeholder="Paste game code"
                />
              </label>
              <label>
                Your name
                <input
                  value={playerName}
                  onChange={e => setPlayerName(e.target.value)}
                  placeholder="Player 2"
                />
              </label>
              <button type="submit" disabled={!gameId}>
                Join game
              </button>
            </form>
          </div>
        </div>
      )}

      {phase === 'playing' && boardState && (
        <div className="online-game">
          <div className="online-info">
            <div className="online-row">
              <label>
                Your display name
                <input
                  value={playerName}
                  onChange={e => {
                    const value = e.target.value;
                    setPlayerName(value);
                    setState(prev =>
                      prev
                        ? {
                            ...prev,
                            blackPlayerName:
                              color === 'BLACK' ? value : prev.blackPlayerName,
                            whitePlayerName:
                              color === 'WHITE' ? value : prev.whitePlayerName
                          }
                        : prev
                    );
                  }}
                  placeholder="Your name"
                />
              </label>
            </div>
            <div>Game code: {gameId}</div>
            {shareUrl && (
              <div>
                Share this link with your opponent:
                <div className="share-url">{shareUrl}</div>
              </div>
            )}
            <div>You are playing as: {color}</div>
            <div>
              {state?.status === 'WAITING_FOR_OPPONENT'
                ? 'Waiting for opponent to join...'
                : `Current turn: ${state?.currentTurn}`}
            </div>
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

