import React, { useEffect, useMemo, useState } from 'react';
import { Board } from './components/Board';
import { PuzzleList } from './components/PuzzleList';
import { OnlineGo } from './OnlineGo';

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

  const initialOnlineGameId = useMemo(() => {
    const url = new URL(window.location.href);
    // Check for ?gameId= query param (legacy UUID support)
    const gameId = url.searchParams.get('gameId');
    if (gameId) return gameId;
    
    // Check for /join/<roomCode> path
    const path = window.location.pathname;
    const joinMatch = path.match(/^\/join\/([A-Z0-9]+)$/i);
    if (joinMatch) {
      return joinMatch[1].toUpperCase();
    }
    
    return null;
  }, []);

  const [mode, setMode] = useState<'puzzles' | 'online'>(
    initialOnlineGameId ? 'online' : 'puzzles'
  );
  const [showMainMenu, setShowMainMenu] = useState(!initialOnlineGameId);
  const [showOnlineSubmenu, setShowOnlineSubmenu] = useState(false);
  const [onlineInitialPhase, setOnlineInitialPhase] = useState<'create' | 'join' | undefined>(undefined);

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
  const [onlineGameState, setOnlineGameState] = useState<any>(null);
  const [onlineMoveHandler, setOnlineMoveHandler] = useState<((x: number, y: number) => void) | null>(null);

  // Free-play sandbox: let users place stones when no game is active
  const [sandboxStones, setSandboxStones] = useState<Stone[]>([]);
  const [sandboxNextColor, setSandboxNextColor] = useState<'BLACK' | 'WHITE'>('BLACK');

  const handleSandboxMove = (x: number, y: number) => {
    // Toggle: if a stone already exists at this intersection, remove it
    const existingIdx = sandboxStones.findIndex(s => s.x === x && s.y === y);
    if (existingIdx >= 0) {
      setSandboxStones(prev => prev.filter((_, i) => i !== existingIdx));
      return;
    }
    // Place a new stone
    setSandboxStones(prev => [...prev, { x, y, color: sandboxNextColor }]);
    setSandboxNextColor(prev => prev === 'BLACK' ? 'WHITE' : 'BLACK');
  };

  // Empty board for sandbox mode
  const sandboxBoardState: BoardState = {
    boardSize: 19,
    stones: sandboxStones
  };

  const isInGame = !!(currentGame || onlineGameState);
  const displayBoardState = boardState || (onlineGameState?.board) || sandboxBoardState;

  const handleBoardMove = (x: number, y: number) => {
    if (mode === 'online' && onlineMoveHandler) {
      onlineMoveHandler(x, y);
    } else if (mode === 'puzzles' && currentGame) {
      handlePlayMove(x, y);
    } else if (!isInGame) {
      // Sandbox free-play mode
      handleSandboxMove(x, y);
    }
  };

  return (
    <div className="app chess-layout">
      <header className="app-header">
        <h1>Let's play GO!</h1>
      </header>
      <main className="app-main chess-main">
        {/* Left side: Board */}
        <div className="board-container-left">
          {(currentGame || onlineGameState) && (
            <div className="player-info-top">
              <div className="player-label">Opponent</div>
            </div>
          )}
          <div className="board-wrapper">
            <Board board={displayBoardState} onPlayMove={handleBoardMove} />
          </div>
          {(currentGame || onlineGameState) && (
            <div className="player-info-bottom">
              <div className="player-label">Player</div>
            </div>
          )}
        </div>

        {/* Right side: Menu/Options */}
        <div className="menu-container-right">
          {/* Main Menu */}
          {showMainMenu && !currentGame && !onlineGameState && (
            <div className="play-menu">
              <h2 className="play-menu-title">
                <span className="menu-icon-large">⚫</span>
                Play
              </h2>
              <div className="play-options">
                <div className="play-option-card" onClick={() => {
                  setShowMainMenu(false);
                  setMode('puzzles');
                }}>
                  <div className="play-option-icon">🧩</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Play Puzzle</div>
                    <div className="play-option-subtitle">Solve Go puzzles and improve your skills</div>
                  </div>
                </div>
                <div className="play-option-card" onClick={() => {
                  setShowMainMenu(false);
                  setShowOnlineSubmenu(true);
                  setMode('online');
                }}>
                  <div className="play-option-icon">⚡</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Play Online</div>
                    <div className="play-option-subtitle">Create or join a multiplayer game</div>
                  </div>
                </div>
              </div>

              {/* Sandbox controls */}
              <div className="sandbox-controls">
                <div className="sandbox-label">
                  Free play — click the board to place stones
                </div>
                <div className="sandbox-actions">
                  <span className="sandbox-next">
                    Next: <span className={`sandbox-color-dot ${sandboxNextColor.toLowerCase()}`}>●</span>
                  </span>
                  {sandboxStones.length > 0 && (
                    <button className="btn-clear-board" onClick={() => {
                      setSandboxStones([]);
                      setSandboxNextColor('BLACK');
                    }}>
                      Clear board
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Online Submenu */}
          {showOnlineSubmenu && !onlineGameState && (
            <div className="play-menu">
              <button className="back-button" onClick={() => {
                setShowOnlineSubmenu(false);
                setShowMainMenu(true);
                setMode('puzzles');
              }}>
                ← Back
              </button>
              <h2 className="play-menu-title">
                <span className="menu-icon-large">⚡</span>
                Play Online
              </h2>
              <div className="play-options">
                <div className="play-option-card" onClick={() => {
                  setShowOnlineSubmenu(false);
                  setOnlineInitialPhase('create');
                  setMode('online');
                }}>
                  <div className="play-option-icon">⚡</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Create Room</div>
                    <div className="play-option-subtitle">Start a new game and invite a friend</div>
                  </div>
                </div>
                <div className="play-option-card" onClick={() => {
                  setShowOnlineSubmenu(false);
                  setOnlineInitialPhase('join');
                  setMode('online');
                }}>
                  <div className="play-option-icon">🤝</div>
                  <div className="play-option-content">
                    <div className="play-option-title">Join Room</div>
                    <div className="play-option-subtitle">Enter a room code to join a game</div>
                  </div>
                </div>
              </div>
            </div>
          )}

          {/* Puzzle List */}
          {mode === 'puzzles' && !showMainMenu && !currentGame && (
            <div className="puzzle-sidebar">
              <button 
                className="back-button"
                onClick={() => {
                  setShowMainMenu(true);
                  setMode('puzzles');
                }}
              >
                ← Back
              </button>
              <h2>Puzzles</h2>
              <PuzzleList
                puzzles={puzzles}
                loading={loadingPuzzles}
                onSelectPuzzle={handleSelectPuzzle}
              />
              {statusMessage && <div className="status-message">{statusMessage}</div>}
            </div>
          )}

          {/* Puzzle Game Active */}
          {mode === 'puzzles' && currentGame && (
            <div className="puzzle-sidebar">
              <button 
                className="back-button"
                onClick={() => {
                  setCurrentGame(null);
                  setShowMainMenu(false);
                  setMode('puzzles');
                }}
              >
                ← Back
              </button>
              <h2>Puzzles</h2>
              <PuzzleList
                puzzles={puzzles}
                loading={loadingPuzzles}
                onSelectPuzzle={handleSelectPuzzle}
              />
              {currentGame?.state.solved && (
                <div className="status-message solved">Puzzle solved! 🎉</div>
              )}
              {statusMessage && <div className="status-message">{statusMessage}</div>}
            </div>
          )}

          {/* Online Game */}
          {mode === 'online' && !showOnlineSubmenu && (
            <OnlineGo 
              initialGameId={initialOnlineGameId}
              initialPhase={onlineInitialPhase}
              onBack={() => {
                setShowMainMenu(true);
                setShowOnlineSubmenu(false);
                setOnlineInitialPhase(undefined);
                setMode('puzzles');
                setCurrentGame(null);
                setOnlineGameState(null);
                setOnlineMoveHandler(null);
              }}
              onSwitchToPuzzles={() => {
                setShowMainMenu(true);
                setShowOnlineSubmenu(false);
                setMode('puzzles');
                setOnlineGameState(null);
                setOnlineMoveHandler(null);
              }}
              onGameStateChange={(state) => {
                setOnlineGameState(state);
                if (state) {
                  setShowMainMenu(false);
                  setShowOnlineSubmenu(false);
                }
              }}
              onMoveHandlerChange={setOnlineMoveHandler}
            />
          )}
        </div>
      </main>
    </div>
  );
};

