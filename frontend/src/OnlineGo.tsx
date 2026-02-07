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
  roomCode: string;
  playerId: string;
  color: 'BLACK' | 'WHITE' | string;
  state: OnlineGameState;
};

type JoinOnlineGameResponse = {
  gameId: string;
  roomCode: string;
  playerId: string;
  color: 'BLACK' | 'WHITE' | string;
  state: OnlineGameState;
};

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
  const [roomCode, setRoomCode] = useState<string | null>(null);
  const [gameId, setGameId] = useState<string | null>(initialGameId ?? null);
  const [playerId, setPlayerId] = useState<string | null>(null);
  const [color, setColor] = useState<'BLACK' | 'WHITE' | string | null>(null);
  const [state, setState] = useState<OnlineGameState | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [autoJoinInProgress, setAutoJoinInProgress] = useState(false);

  const shareUrl = useMemo(() => {
    if (!roomCode) return null;
    const baseUrl = window.location.origin;
    return `${baseUrl}/join/${roomCode}`;
  }, [roomCode]);

  // If we land on a shared link with room code, auto-join as the second player.
  useEffect(() => {
    if (!initialGameId || playerId || state || autoJoinInProgress) {
      return;
    }
    // If we have an initialGameId (from URL), try to auto-join
    const roomCodeToJoin = initialGameId;
    setAutoJoinInProgress(true);
    setStatusMessage(null);

    (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/online-games/${roomCodeToJoin}/join`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ playerName: 'Player 2' })
        });
        if (!res.ok) {
          if (res.status === 409) {
            setStatusMessage('Game is already full. Please create a new game.');
          } else {
            setStatusMessage(`Failed to join game: ${res.status}`);
          }
          setPhase('setup');
          return;
        }
        const data: JoinOnlineGameResponse = await res.json();
        setGameId(data.gameId);
        setRoomCode(data.roomCode);
        setPlayerId(data.playerId);
        setColor(data.color);
        setState(data.state);
        setPlayerName(data.state.whitePlayerName || 'Player 2');
        setPhase('playing');
        // Update URL to use /join/<roomCode> format
        window.history.replaceState({}, '', `/join/${data.roomCode}`);
      } catch (err) {
        setStatusMessage((err as Error).message);
        setPhase('setup');
      } finally {
        setAutoJoinInProgress(false);
      }
    })();
  }, [initialGameId, playerId, state, autoJoinInProgress]);

  // Polling
  useEffect(() => {
    if (!gameId || !playerId || !state || state.status === 'FINISHED') {
      return;
    }

    const interval = window.setInterval(async () => {
      try {
        // Use room code if available, otherwise fall back to gameId (UUID)
        const identifier = roomCode || gameId;
        const res = await fetch(
          `${API_BASE_URL}/online-games/${identifier}?playerId=${encodeURIComponent(playerId)}`
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
  }, [gameId, roomCode, playerId, state?.status]);

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
      setRoomCode(data.roomCode);
      setPlayerId(data.playerId);
      setColor(data.color);
      setState(data.state);
      setPlayerName(playerName || data.state.blackPlayerName || 'Player 1');
      setPhase('playing');

      // Update the URL to use /join/<roomCode> format
      window.history.replaceState({}, '', `/join/${data.roomCode}`);
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
      setGameId(data.gameId);
      setRoomCode(data.roomCode);
      setPlayerId(data.playerId);
      setColor(data.color);
      setState(data.state);
      setPlayerName(playerName || data.state.whitePlayerName || 'Player 2');
      setPhase('playing');
      // Update URL to use /join/<roomCode> format
      window.history.replaceState({}, '', `/join/${data.roomCode}`);
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
      // Use room code if available, otherwise fall back to gameId (UUID)
      const identifier = roomCode || gameId;
      const res = await fetch(`${API_BASE_URL}/online-games/${identifier}/moves`, {
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

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setStatusMessage('Copied to clipboard!');
      setTimeout(() => setStatusMessage(null), 2000);
    } catch (err) {
      setStatusMessage('Failed to copy');
    }
  };

  const boardState = state?.board;
  const isMyTurn = state?.status === 'IN_PROGRESS' && 
    ((state.currentTurn === 'BLACK' && color === 'BLACK') ||
     (state.currentTurn === 'WHITE' && color === 'WHITE'));

  return (
    <div className="online-go-container">
      {phase === 'setup' && (
        <div className="online-setup">
          <div className="online-column">
            <div className="online-column-header">
              <h3>Create Room</h3>
              <p>Start a new game as Black</p>
            </div>
            <form onSubmit={handleCreate} className="online-form">
              <label>
                Your name
                <input
                  value={playerName}
                  onChange={e => setPlayerName(e.target.value)}
                  placeholder="Enter your name"
                  autoFocus
                />
              </label>
              <button type="submit" className="btn-primary">Create Game</button>
            </form>
          </div>
          <div className="online-column">
            <div className="online-column-header">
              <h3>Join Room</h3>
              <p>Enter a room code to join</p>
            </div>
            <form onSubmit={handleJoin} className="online-form">
              <label>
                Room code
                <input
                  value={gameId ?? ''}
                  onChange={e => setGameId(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
                  placeholder="e.g. AB3K9Q"
                  maxLength={6}
                  autoFocus={!!initialGameId}
                />
              </label>
              <label>
                Your name
                <input
                  value={playerName}
                  onChange={e => setPlayerName(e.target.value)}
                  placeholder="Enter your name"
                />
              </label>
              <button type="submit" disabled={!gameId || gameId.length !== 6} className="btn-primary">
                Join Game
              </button>
            </form>
          </div>
        </div>
      )}

      {phase === 'playing' && boardState && (
        <div className="online-game-layout">
          <div className="online-sidebar">
            <div className="game-info-card">
              <div className="player-section">
                <label className="player-name-label">
                  Your name
                  <input
                    className="player-name-input"
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

              {roomCode && (
                <div className="room-code-section">
                  <div className="room-code-label">Room Code</div>
                  <div className="room-code-container">
                    <span className="room-code-value">{roomCode}</span>
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(roomCode)}
                      title="Copy room code"
                    >
                      📋
                    </button>
                  </div>
                </div>
              )}

              {shareUrl && (
                <div className="share-section">
                  <div className="share-label">Share Link</div>
                  <div className="share-url-container">
                    <input
                      type="text"
                      readOnly
                      value={shareUrl}
                      className="share-url-input"
                    />
                    <button
                      type="button"
                      className="btn-copy"
                      onClick={() => copyToClipboard(shareUrl)}
                      title="Copy link"
                    >
                      📋
                    </button>
                  </div>
                </div>
              )}

              <div className="game-status-section">
                <div className="status-row">
                  <span className="status-label">You are:</span>
                  <span className={`player-badge ${color?.toLowerCase()}`}>
                    {color}
                  </span>
                </div>
                <div className="status-row">
                  <span className="status-label">Status:</span>
                  <span className={`turn-indicator ${isMyTurn ? 'your-turn' : 'waiting'}`}>
                    {state?.status === 'WAITING_FOR_OPPONENT'
                      ? 'Waiting for opponent...'
                      : isMyTurn
                      ? 'Your turn!'
                      : "Opponent's turn"}
                  </span>
                </div>
              </div>

              <div className="players-section">
                <div className="player-info">
                  <span className="player-color black">●</span>
                  <span className="player-name">{state?.blackPlayerName ?? '—'}</span>
                </div>
                <div className="player-info">
                  <span className="player-color white">●</span>
                  <span className="player-name">{state?.whitePlayerName ?? '—'}</span>
                </div>
              </div>
            </div>
          </div>

          <div className="board-container">
            <Board board={boardState} onPlayMove={handlePlayMove} />
            {!isMyTurn && state?.status === 'IN_PROGRESS' && (
              <div className="board-overlay">
                <div className="overlay-message">Waiting for opponent's move...</div>
              </div>
            )}
          </div>
        </div>
      )}

      {autoJoinInProgress && (
        <div className="loading-overlay">
          <div className="loading-spinner">Joining game...</div>
        </div>
      )}

      {statusMessage && (
        <div className={`status-toast ${statusMessage.includes('Copied') ? 'success' : ''}`}>
          {statusMessage}
        </div>
      )}
    </div>
  );
}

