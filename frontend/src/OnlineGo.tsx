import React, { useEffect, useMemo, useState, useCallback } from 'react';
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
  initialPhase?: 'create' | 'join' | 'menu';
  onBack?: () => void;
  onSwitchToPuzzles?: () => void;
  onGameStateChange?: (state: OnlineGameState | null) => void;
  onMoveHandlerChange?: (handler: ((x: number, y: number) => void) | null) => void;
};

export const OnlineGo: React.FC<Props> = ({ initialGameId, initialPhase, onBack, onSwitchToPuzzles, onGameStateChange, onMoveHandlerChange }) => {
  // Determine initial phase
  const [phase, setPhase] = useState<'menu' | 'create' | 'join' | 'rejoin' | 'playing'>(() => {
    if (initialGameId) return 'rejoin';
    if (initialPhase) return initialPhase;
    return 'menu';
  });
  const [playerName, setPlayerName] = useState('');
  const [roomCode, setRoomCode] = useState<string | null>(null);
  const [gameId, setGameId] = useState<string | null>(initialGameId ?? null);
  const [playerId, setPlayerId] = useState<string | null>(null);
  const [color, setColor] = useState<'BLACK' | 'WHITE' | string | null>(null);
  const [state, setState] = useState<OnlineGameState | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [autoJoinInProgress, setAutoJoinInProgress] = useState(false);
  const [rejoinGameState, setRejoinGameState] = useState<OnlineGameState | null>(null);

  const shareUrl = useMemo(() => {
    if (!roomCode) return null;
    const baseUrl = window.location.origin;
    return `${baseUrl}/join/${roomCode}`;
  }, [roomCode]);

  // If we land on a shared link with room code, fetch game state for join
  useEffect(() => {
    if (!initialGameId || rejoinGameState || autoJoinInProgress || phase !== 'rejoin') {
      return;
    }
    const roomCodeToJoin = initialGameId;
    setAutoJoinInProgress(true);
    setStatusMessage(null);

    (async () => {
      try {
        // Check if we have stored player info for this game
        const storedInfo = localStorage.getItem(`game_${roomCodeToJoin}`);
        if (storedInfo) {
          const { playerId: storedPlayerId, color: storedColor, playerName: storedName } = JSON.parse(storedInfo);
          // Try to get game state with stored playerId
          const res = await fetch(`${API_BASE_URL}/online-games/${roomCodeToJoin}?playerId=${encodeURIComponent(storedPlayerId)}`);
          if (res.ok) {
            const data: OnlineGameState = await res.json();
            setRejoinGameState(data);
            setGameId(roomCodeToJoin);
            setRoomCode(roomCodeToJoin);
            setPlayerId(storedPlayerId);
            setColor(storedColor);
            setPlayerName(storedName);
            setState(data);
            setPhase('playing');
            setAutoJoinInProgress(false);
            onGameStateChange?.(data);
            return;
          }
        }

        // If no stored info or stored playerId doesn't work, fetch game state (no playerId needed for waiting games)
        const res = await fetch(`${API_BASE_URL}/online-games/${roomCodeToJoin}`);
        if (res.ok) {
          const data: OnlineGameState = await res.json();
          setRejoinGameState(data);
          setGameId(roomCodeToJoin);
          setRoomCode(roomCodeToJoin);
        } else if (res.status === 404) {
          setStatusMessage('Game not found. The room code may be invalid.');
          setPhase('menu');
        }
      } catch (err) {
        setStatusMessage((err as Error).message);
        setPhase('menu');
      } finally {
        setAutoJoinInProgress(false);
      }
    })();
  }, [initialGameId, rejoinGameState, autoJoinInProgress, phase, onGameStateChange]);

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
        onGameStateChange?.(data);
      } catch {
        // ignore transient errors
      }
    }, 1500);

    return () => window.clearInterval(interval);
  }, [gameId, roomCode, playerId, state?.status, onGameStateChange]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!playerName.trim()) {
      setStatusMessage('Please enter your name');
      return;
    }
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/online-games`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerName: playerName.trim() })
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
        setPlayerName(playerName.trim());
        setPhase('playing');
        onGameStateChange?.(data.state);

        // Store player info in localStorage for rejoin
        localStorage.setItem(`game_${data.roomCode}`, JSON.stringify({
          playerId: data.playerId,
          color: data.color,
          playerName: playerName.trim()
        }));

        // Update the URL to use /join/<roomCode> format
        window.history.replaceState({}, '', `/join/${data.roomCode}`);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handleJoin = async (e: React.FormEvent, selectedColor?: 'BLACK' | 'WHITE') => {
    e.preventDefault();
    if (!gameId || gameId.length !== 6) {
      setStatusMessage('Please enter a valid 6-character room code');
      return;
    }
    if (!playerName.trim()) {
      setStatusMessage('Please enter your name');
      return;
    }
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/online-games/${gameId}/join`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          playerName: playerName.trim(),
          preferredColor: selectedColor || null
        })
      });
      if (!res.ok) {
        if (res.status === 409) {
          setStatusMessage('Game is already full. Please create a new game.');
        } else if (res.status === 404) {
          setStatusMessage('Game not found. Please check the room code.');
        } else {
          throw new Error(`Failed to join game: ${res.status}`);
        }
        return;
      }
      const data: JoinOnlineGameResponse = await res.json();
      setGameId(data.gameId);
      setRoomCode(data.roomCode);
      setPlayerId(data.playerId);
      setColor(data.color);
        setState(data.state);
        setPlayerName(playerName.trim());
        setPhase('playing');
        onGameStateChange?.(data.state);

        // Store player info in localStorage for rejoin
        localStorage.setItem(`game_${data.roomCode}`, JSON.stringify({
          playerId: data.playerId,
          color: data.color,
          playerName: playerName.trim()
        }));

        // Update URL to use /join/<roomCode> format
        window.history.replaceState({}, '', `/join/${data.roomCode}`);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handleRejoin = async (selectedColor?: 'BLACK' | 'WHITE') => {
    if (!gameId || !playerName.trim()) {
      setStatusMessage('Please enter your name');
      return;
    }
    setStatusMessage(null);
    try {
      // If game is waiting, join as second player with color selection
      if (rejoinGameState?.status === 'WAITING_FOR_OPPONENT') {
        const res = await fetch(`${API_BASE_URL}/online-games/${gameId}/join`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ 
            playerName: playerName.trim(),
            preferredColor: selectedColor || null
          })
        });
        if (!res.ok) {
          if (res.status === 409) {
            setStatusMessage('Game is already full.');
          } else {
            throw new Error(`Failed to join game: ${res.status}`);
          }
          return;
        }
        const data: JoinOnlineGameResponse = await res.json();
        setPlayerId(data.playerId);
        setColor(data.color);
        setState(data.state);
        setPlayerName(playerName.trim());
        setPhase('playing');
        onGameStateChange?.(data.state);

        // Store player info
        localStorage.setItem(`game_${gameId}`, JSON.stringify({
          playerId: data.playerId,
          color: data.color,
          playerName: playerName.trim()
        }));
      } else if (rejoinGameState?.status === 'IN_PROGRESS') {
        // If game is in progress, check if we have stored player info
        const storedInfo = localStorage.getItem(`game_${gameId}`);
        if (storedInfo) {
          const { playerId: storedPlayerId, color: storedColor } = JSON.parse(storedInfo);
          // Try to get game state with stored playerId
          const res = await fetch(`${API_BASE_URL}/online-games/${gameId}?playerId=${encodeURIComponent(storedPlayerId)}`);
          if (res.ok) {
            const currentState: OnlineGameState = await res.json();
            setState(currentState);
            onGameStateChange?.(currentState);
            setPlayerId(storedPlayerId);
            setColor(storedColor);
            setPhase('playing');
          } else {
            setStatusMessage('Could not rejoin. You may need to join as a spectator or create a new game.');
          }
        } else {
          setStatusMessage('This game is in progress. If you were playing, please use the same browser/device where you started the game.');
        }
      }
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handleSwapColors = async () => {
    if (!gameId || !playerId) return;
    setStatusMessage(null);
    try {
      const identifier = roomCode || gameId;
      const res = await fetch(`${API_BASE_URL}/online-games/${identifier}/swap-colors`, {
        method: 'POST'
      });
      if (!res.ok) {
        throw new Error(`Failed to swap colors: ${res.status}`);
      }
      const data: OnlineGameState = await res.json();
      setState(data);
      onGameStateChange?.(data);
      
      // Update our color
      const newColor = color === 'BLACK' ? 'WHITE' : 'BLACK';
      setColor(newColor);
      
      // Update stored info
      if (roomCode) {
        const storedInfo = localStorage.getItem(`game_${roomCode}`);
        if (storedInfo) {
          const info = JSON.parse(storedInfo);
          info.color = newColor;
          localStorage.setItem(`game_${roomCode}`, JSON.stringify(info));
        }
      }
      
      setStatusMessage('Colors swapped!');
      setTimeout(() => setStatusMessage(null), 2000);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handlePlayMove = useCallback(async (x: number, y: number) => {
    if (!gameId || !playerId || !state) {
      setStatusMessage('Not ready to play. Please wait...');
      return;
    }
    if (state.status !== 'IN_PROGRESS') {
      setStatusMessage('Game is not in progress');
      return;
    }
    if ((state.currentTurn === 'BLACK' && color !== 'BLACK') ||
        (state.currentTurn === 'WHITE' && color !== 'WHITE')) {
      setStatusMessage("It's not your turn");
      return;
    }
    setStatusMessage(null);
    try {
      // Use room code if available, otherwise fall back to gameId (UUID)
      const identifier = (roomCode || gameId).toUpperCase();
      const res = await fetch(`${API_BASE_URL}/online-games/${identifier}/moves`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ playerId, x, y })
      });
      if (!res.ok) {
        const errorText = await res.text();
        let errorMessage = `Failed to play move: ${res.status}`;
        try {
          const errorData = JSON.parse(errorText);
          if (errorData.message) {
            errorMessage = errorData.message;
          }
        } catch {
          // Use default error message
        }
        throw new Error(errorMessage);
      }
      const data: OnlineMoveResponse = await res.json();
      setState(data.state);
      onGameStateChange?.(data.state);
      setStatusMessage(data.message);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  }, [gameId, playerId, state, color, roomCode, onGameStateChange]);

  // Update move handler when game state changes
  useEffect(() => {
    if (phase === 'playing' && state?.status === 'IN_PROGRESS') {
      onMoveHandlerChange?.(handlePlayMove);
    } else {
      onMoveHandlerChange?.(null);
    }
  }, [phase, state?.status, handlePlayMove, onMoveHandlerChange]);

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
      {phase === 'menu' && (
        <div className="play-menu">
          <button className="back-button" onClick={onBack}>
            ← Back
          </button>
          <h2 className="play-menu-title">
            <span className="menu-icon-large">⚫</span>
            Play Online
          </h2>
          <div className="play-options">
            <div className="play-option-card" onClick={() => {
              if (onSwitchToPuzzles) {
                onSwitchToPuzzles();
              }
            }}>
              <div className="play-option-icon">🧩</div>
              <div className="play-option-content">
                <div className="play-option-title">Play Puzzle</div>
                <div className="play-option-subtitle">Solve Go puzzles and improve your skills</div>
              </div>
            </div>
            <div className="play-option-card" onClick={() => setPhase('create')}>
              <div className="play-option-icon">⚡</div>
              <div className="play-option-content">
                <div className="play-option-title">Create Room</div>
                <div className="play-option-subtitle">Start a new game and invite a friend</div>
              </div>
            </div>
            <div className="play-option-card" onClick={() => setPhase('join')}>
              <div className="play-option-icon">🤝</div>
              <div className="play-option-content">
                <div className="play-option-title">Join Room</div>
                <div className="play-option-subtitle">Enter a room code to join a game</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {phase === 'create' && (
        <div className="online-form-container">
          <button className="back-button" onClick={() => setPhase('menu')}>
            ← Back
          </button>
          <div className="form-card">
            <h2>Create Room</h2>
            <p className="form-subtitle">Start a new game as Black</p>
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
        </div>
      )}

      {phase === 'join' && (
        <div className="online-form-container">
          <button className="back-button" onClick={() => setPhase('menu')}>
            ← Back
          </button>
          <div className="form-card">
            <h2>Join Room</h2>
            <p className="form-subtitle">Enter a room code to join</p>
            <form onSubmit={(e) => { e.preventDefault(); }} className="online-form">
              <label>
                Room code
                <input
                  value={gameId ?? ''}
                  onChange={e => setGameId(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
                  placeholder="e.g. AB3K9Q"
                  maxLength={6}
                  autoFocus
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
              <div className="color-selection">
                <div className="color-selection-label">Choose your color:</div>
                <div className="color-buttons">
                  <button
                    type="button"
                    className="color-button black"
                    onClick={() => handleJoin(new Event('submit') as any, 'BLACK')}
                    disabled={!gameId || gameId.length !== 6 || !playerName.trim()}
                  >
                    <span className="color-dot black">●</span>
                    Black
                  </button>
                  <button
                    type="button"
                    className="color-button white"
                    onClick={() => handleJoin(new Event('submit') as any, 'WHITE')}
                    disabled={!gameId || gameId.length !== 6 || !playerName.trim()}
                  >
                    <span className="color-dot white">●</span>
                    White
                  </button>
                </div>
              </div>
            </form>
          </div>
        </div>
      )}

      {phase === 'rejoin' && rejoinGameState && (
        <div className="online-form-container">
          <button className="back-button" onClick={onBack}>
            ← Back to Menu
          </button>
          <div className="form-card">
            <h2>Join Game</h2>
            <p className="form-subtitle">Room: {roomCode}</p>
            {rejoinGameState.status === 'WAITING_FOR_OPPONENT' && (
              <div className="rejoin-info">
                <p>This game is waiting for a second player. Choose your color:</p>
                <form onSubmit={(e) => { e.preventDefault(); }} className="online-form">
                  <label>
                    Your name
                    <input
                      value={playerName}
                      onChange={e => setPlayerName(e.target.value)}
                      placeholder="Enter your name"
                      autoFocus
                    />
                  </label>
                  <div className="color-selection">
                    <div className="color-selection-label">Choose your color:</div>
                    <div className="color-buttons">
                      <button
                        type="button"
                        className="color-button black"
                        onClick={() => handleRejoin('BLACK')}
                        disabled={!playerName.trim()}
                      >
                        <span className="color-dot black">●</span>
                        Black
                      </button>
                      <button
                        type="button"
                        className="color-button white"
                        onClick={() => handleRejoin('WHITE')}
                        disabled={!playerName.trim()}
                      >
                        <span className="color-dot white">●</span>
                        White
                      </button>
                    </div>
                  </div>
                </form>
              </div>
            )}
            {rejoinGameState.status === 'IN_PROGRESS' && (
              <div className="rejoin-info">
                <p>This game is in progress.</p>
                <div className="players-preview">
                  <div className="player-preview">
                    <span className="player-color black">●</span>
                    <span>{rejoinGameState.blackPlayerName || 'Black'}</span>
                  </div>
                  <div className="player-preview">
                    <span className="player-color white">●</span>
                    <span>{rejoinGameState.whitePlayerName || 'White'}</span>
                  </div>
                </div>
                <form onSubmit={(e) => { e.preventDefault(); handleRejoin(); }} className="online-form">
                  <label>
                    Your name
                    <input
                      value={playerName}
                      onChange={e => setPlayerName(e.target.value)}
                      placeholder="Enter your name to rejoin"
                      autoFocus
                    />
                  </label>
                  <button type="submit" className="btn-primary">Rejoin Game</button>
                </form>
              </div>
            )}
          </div>
        </div>
      )}

      {phase === 'playing' && boardState && (
        <div className="game-info-sidebar">
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

              {state?.status === 'IN_PROGRESS' && (
                <div className="swap-colors-section">
                  <button
                    type="button"
                    className="btn-swap-colors"
                    onClick={handleSwapColors}
                    title="Swap colors with your opponent"
                  >
                    🔄 Swap Colors
                  </button>
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

