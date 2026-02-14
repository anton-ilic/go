import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Board } from './components/Board';
import { PuzzleList } from './components/PuzzleList';
import { OnlineGo } from './OnlineGo';

export type Stone = { x: number; y: number; color: 'WHITE' | 'BLACK' };

export type BoardState = {
  boardSize: number;
  stones: Stone[];
};

export type Prisoners = {
  black: number;
  white: number;
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

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

type Mode = 'menu' | 'puzzles' | 'puzzle-list' | 'online-menu' | 'online-create' | 'online-join' | 'online-room';

export const App: React.FC = () => {
  const [puzzles, setPuzzles] = useState<PuzzleSummary[]>([]);
  const [loadingPuzzles, setLoadingPuzzles] = useState(false);
  const [currentGame, setCurrentGame] = useState<CreateGameResponse | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // Check if we landed on a room URL: /r/{roomId}
  const initialRoomId = useMemo(() => {
    const path = window.location.pathname;
    const match = path.match(/^\/r\/([A-Z0-9]+)$/i);
    if (match) return match[1].toUpperCase();
    return null;
  }, []);

  const [mode, setMode] = useState<Mode>(initialRoomId ? 'online-room' : 'menu');
  const [onlineRoomId, setOnlineRoomId] = useState<string | null>(initialRoomId);

  // Listen for URL changes (e.g., when OnlineGo updates the URL after creating a room)
  useEffect(() => {
    const checkUrl = () => {
      const path = window.location.pathname;
      const match = path.match(/^\/r\/([A-Z0-9]+)$/i);
      if (match) {
        const roomId = match[1].toUpperCase();
        if (roomId !== onlineRoomId) {
          setOnlineRoomId(roomId);
          setMode('online-room');
        }
      }
    };

    // Check on mount and when popstate fires (back/forward navigation)
    checkUrl();
    window.addEventListener('popstate', checkUrl);
    return () => window.removeEventListener('popstate', checkUrl);
  }, [onlineRoomId]);
  const [joinRoomCode, setJoinRoomCode] = useState('');

  // Board state from online game (pushed up by OnlineGo)
  const [onlineBoardState, setOnlineBoardState] = useState<BoardState | null>(null);
  const [onlinePrisoners, setOnlinePrisoners] = useState<Prisoners>({ black: 0, white: 0 });
  // Use a ref for the move handler to avoid the React useState pitfall where
  // passing a function to a setter is interpreted as a functional update.
  const onlineMoveHandlerRef = useRef<((x: number, y: number) => void) | null>(null);
  const setOnlineMoveHandler = useCallback((handler: ((x: number, y: number) => void) | null) => {
    onlineMoveHandlerRef.current = handler;
  }, []);

  // Free-play sandbox
  const [sandboxStones, setSandboxStones] = useState<Stone[]>([]);
  const [sandboxNextColor, setSandboxNextColor] = useState<'BLACK' | 'WHITE'>('BLACK');

  useEffect(() => {
    const loadPuzzles = async () => {
      setLoadingPuzzles(true);
      try {
        const res = await fetch(`${API_BASE_URL}/puzzles`);
        if (!res.ok) throw new Error(`Failed to load puzzles: ${res.status}`);
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
        body: JSON.stringify({ puzzleId }),
      });
      if (!res.ok) throw new Error(`Failed to start game: ${res.status}`);
      const data: CreateGameResponse = await res.json();
      setCurrentGame(data);
      setMode('puzzles');
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handlePlayMove = async (x: number, y: number) => {
    if (!currentGame) return;
    setStatusMessage(null);
    try {
      const res = await fetch(`${API_BASE_URL}/games/${currentGame.gameId}/moves`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ x, y }),
      });
      if (!res.ok) throw new Error(`Failed to play move: ${res.status}`);
      const data: MoveResponse = await res.json();
      if (data.state) {
        setCurrentGame(prev => prev ? { ...prev, state: data.state! } : null);
      }
      setStatusMessage(data.message);
    } catch (err) {
      setStatusMessage((err as Error).message);
    }
  };

  const handleSandboxMove = (x: number, y: number) => {
    const existingIdx = sandboxStones.findIndex(s => s.x === x && s.y === y);
    if (existingIdx >= 0) {
      setSandboxStones(prev => prev.filter((_, i) => i !== existingIdx));
      return;
    }
    setSandboxStones(prev => [...prev, { x, y, color: sandboxNextColor }]);
    setSandboxNextColor(prev => (prev === 'BLACK' ? 'WHITE' : 'BLACK'));
  };

  const sandboxBoardState: BoardState = { boardSize: 11, stones: sandboxStones };

  // Determine what board to display
  const isOnlineRoom = mode === 'online-room' || mode === 'online-create';
  const displayBoardState = currentGame?.state.board || onlineBoardState || sandboxBoardState;
  const prisonerCount = onlinePrisoners;

  const handleBoardMove = (x: number, y: number) => {
    const handler = onlineMoveHandlerRef.current;
    console.log('handleBoardMove called:', { x, y, isOnlineRoom, hasHandler: !!handler, mode });
    if (isOnlineRoom && handler) {
      console.log('Calling onlineMoveHandler');
      handler(x, y);
    } else if (currentGame) {
      handlePlayMove(x, y);
    } else if (!currentGame && !isOnlineRoom) {
      handleSandboxMove(x, y);
    } else {
      console.log('No handler available for move');
    }
  };

  const goToMainMenu = () => {
    setMode('menu');
    setCurrentGame(null);
    setOnlineBoardState(null);
    setOnlinePrisoners({ black: 0, white: 0 });
    onlineMoveHandlerRef.current = null;
    setOnlineRoomId(null);
    window.history.replaceState({}, '', '/');
  };

  const handleJoinSubmit = () => {
    const code = joinRoomCode.trim().toUpperCase();
    if (code.length < 4) return;
    setOnlineRoomId(code);
    setMode('online-room');
    window.history.replaceState({}, '', `/r/${code}`);
  };

  return (
    <div className="app chess-layout">
      <header className="app-header">
        <h1 onClick={goToMainMenu} style={{ cursor: 'pointer' }}>Let's play GO!</h1>
        {isOnlineRoom && (
          <div className="prisoner-bar" aria-label="Prisoner count">
            <span className="prisoner-label">Prisoners</span>
            <span className="prisoner-pill">
              <span className="prisoner-stone black">●</span>
              <span className="prisoner-text">Black: {prisonerCount.black}</span>
            </span>
            <span className="prisoner-pill">
              <span className="prisoner-stone white">●</span>
              <span className="prisoner-text">White: {prisonerCount.white}</span>
            </span>
          </div>
        )}
      </header>
      <main className="app-main chess-main">
        {/* Left side: Board */}
        <div className="board-container-left">
          <div className="board-wrapper">
            <Board board={displayBoardState} onPlayMove={handleBoardMove} />
          </div>
        </div>

        {/* Right side: Menu/Options */}
        <div className="menu-container-right">
          {/* === Main Menu === */}
          {mode === 'menu' && (
            <div className="play-menu">
              <h2 className="play-menu-title">
                <span className="menu-icon-large">⚫</span>
                Play
              </h2>
              <div className="play-options">
                <div className="play-option-card" onClick={() => setMode('puzzle-list')}>
                  <div className="play-option-icon">🧩</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Play Puzzle</div>
                    <div className="play-option-subtitle">Solve Go puzzles and improve your skills</div>
                  </div>
                </div>
                <div className="play-option-card" onClick={() => setMode('online-menu')}>
                  <div className="play-option-icon">⚡</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Play Online</div>
                    <div className="play-option-subtitle">Hot-seat over the internet — share a link to play</div>
                  </div>
                </div>
              </div>

              {/* Sandbox controls */}
              <div className="sandbox-controls">
                <div className="sandbox-label">Free play — click the board to place stones</div>
                <div className="sandbox-actions">
                  <span className="sandbox-next">
                    Next: <span className={`sandbox-color-dot ${sandboxNextColor.toLowerCase()}`}>●</span>
                  </span>
                  {sandboxStones.length > 0 && (
                    <button
                      className="btn-clear-board"
                      onClick={() => {
                        setSandboxStones([]);
                        setSandboxNextColor('BLACK');
                      }}
                    >
                      Clear board
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* === Online Submenu === */}
          {mode === 'online-menu' && (
            <div className="play-menu">
              <button className="back-button" onClick={goToMainMenu}>← Back</button>
              <h2 className="play-menu-title">
                <span className="menu-icon-large">⚡</span>
                Play Online
              </h2>
              <div className="play-options">
                <div
                  className="play-option-card"
                  onClick={() => {
                    setOnlineRoomId(null);
                    setMode('online-create');
                  }}
                >
                  <div className="play-option-icon">⚡</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Create Room</div>
                    <div className="play-option-subtitle">Start a new game and share the link</div>
                  </div>
                </div>
                <div className="play-option-card" onClick={() => setMode('online-join')}>
                  <div className="play-option-icon">🤝</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Join Room</div>
                    <div className="play-option-subtitle">Enter a room code to join</div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* === Join Room Form === */}
          {mode === 'online-join' && (
            <div className="online-form-container">
              <button className="back-button" onClick={() => setMode('online-menu')}>← Back</button>
              <div className="form-card">
                <h2>Join Room</h2>
                <p className="form-subtitle">Enter a room code to join a game</p>
                <form
                  className="online-form"
                  onSubmit={(e) => {
                    e.preventDefault();
                    handleJoinSubmit();
                  }}
                >
                  <label>
                    Room code
                    <input
                      value={joinRoomCode}
                      onChange={(e) => setJoinRoomCode(e.target.value.toUpperCase().replace(/[^A-Z0-9]/g, ''))}
                      placeholder="e.g. AB3K9QXY"
                      maxLength={8}
                      autoFocus
                    />
                  </label>
                  <button
                    type="submit"
                    className="btn-primary"
                    disabled={joinRoomCode.trim().length < 4}
                  >
                    Join Game
                  </button>
                </form>
              </div>
            </div>
          )}

          {/* === Puzzle List === */}
          {mode === 'puzzle-list' && (
            <div className="puzzle-sidebar">
              <button className="back-button" onClick={goToMainMenu}>← Back</button>
              <h2>Puzzles</h2>
              <PuzzleList puzzles={puzzles} loading={loadingPuzzles} onSelectPuzzle={handleSelectPuzzle} />
              {statusMessage && <div className="status-message">{statusMessage}</div>}
            </div>
          )}

          {/* === Puzzle Active === */}
          {mode === 'puzzles' && currentGame && (
            <div className="puzzle-sidebar">
              <button className="back-button" onClick={() => { setCurrentGame(null); setMode('puzzle-list'); }}>
                ← Back
              </button>
              <h2>Puzzles</h2>
              <PuzzleList puzzles={puzzles} loading={loadingPuzzles} onSelectPuzzle={handleSelectPuzzle} />
              {currentGame.state.solved && (
                <div className="status-message solved">Puzzle solved! 🎉</div>
              )}
              {statusMessage && <div className="status-message">{statusMessage}</div>}
            </div>
          )}

          {/* === Online Room (Create or Join via link) === */}
          {(mode === 'online-create' || mode === 'online-room') && (
            <OnlineGo
              roomId={onlineRoomId}
              onBack={goToMainMenu}
              onBoardState={setOnlineBoardState}
              onPrisoners={setOnlinePrisoners}
              onMoveHandler={setOnlineMoveHandler}
              onRoomCreated={(roomId: string) => {
                setOnlineRoomId(roomId);
                setMode('online-room');
              }}
            />
          )}
        </div>
      </main>
    </div>
  );
};
