import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import type { BoardState, Prisoners } from './App';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

/**
 * Derive WebSocket URL. If API_BASE_URL is relative (e.g. /api),
 * use the current page's host. Otherwise parse the configured URL.
 */
function getWsBaseUrl(): string {
  if (API_BASE_URL.startsWith('/')) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}`;
  }
  const url = new URL(API_BASE_URL);
  const protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${url.host}`;
}

export type RoomState = {
  roomId: string;
  turn: 'BLACK' | 'WHITE';
  moveNumber: number;
  prisoners: Prisoners;
  board: BoardState;
  canUndo?: boolean;
  canRedo?: boolean;
  komi?: number;
  gameEnded?: boolean;
  scoreBlack?: number;
  scoreWhite?: number;
  resignedBy?: string | null;
  winner?: string | null;
  territoryMarks?: Record<string, string>;
  deadStones?: string[];
  isScoringPhase?: boolean;
};

export type ScoringMarks = { territoryMarks: Record<string, string>; deadStones: string[] } | null;

type Props = {
  roomId: string | null;          // null = "create room" flow, string = "join existing room"
  onBack: () => void;
  onBoardState: (board: BoardState | null) => void;
  onPrisoners: (prisoners: Prisoners) => void;
  onMoveHandler: (handler: ((x: number, y: number) => void) | null) => void;
  onRoomCreated?: (roomId: string) => void;  // Callback when room is created
  onScoringMarks?: (marks: ScoringMarks) => void;  // Territory/dead marks for board display (scoring phase)
};

export const OnlineGo: React.FC<Props> = ({ roomId: initialRoomId, onBack, onBoardState, onPrisoners, onMoveHandler, onRoomCreated, onScoringMarks }) => {
  const [phase, setPhase] = useState<'creating' | 'joining' | 'connected' | 'error'>(
    initialRoomId ? 'joining' : 'creating'
  );
  const [roomId, setRoomId] = useState<string | null>(initialRoomId);
  const [roomState, setRoomState] = useState<RoomState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [ruleTipIndex, setRuleTipIndex] = useState(0);
  /** When in scoring phase: 'territory' = mark empty as B/W territory, 'dead' = mark stone as dead. */
  const [markMode, setMarkMode] = useState<'territory' | 'dead' | null>(null);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<number | null>(null);
  const phaseRef = useRef(phase);
  phaseRef.current = phase;

  const shareUrl = useMemo(() => {
    if (!roomId) return null;
    return `${window.location.origin}/r/${roomId}`;
  }, [roomId]);

  const ruleTips = useMemo(() => ([
    {
      title: 'Ko rule',
      text: 'You cannot immediately recapture to recreate the exact previous board position. Play elsewhere first.',
    },
    {
      title: 'Komi',
      text: 'White gets extra points (komi) to balance Black moving first. Typical value is around 6.5 to 7.5.',
    },
    {
      title: 'Passing',
      text: 'Pass when no profitable move remains. The game usually ends after two consecutive passes.',
    },
    {
      title: 'Capturing',
      text: 'A group with no liberties is captured and removed from the board.',
    },
  ]), []);

  const toPrisoners = useCallback((raw: any): Prisoners => ({
    black: Number(raw?.black ?? 0),
    white: Number(raw?.white ?? 0),
  }), []);

  const toRoomState = useCallback((data: any, fallback?: RoomState | null): RoomState => ({
    roomId: data.roomId,
    turn: data.turn,
    moveNumber: data.moveNumber,
    prisoners: toPrisoners(data.prisoners),
    board: data.board,
    canUndo: data.canUndo ?? fallback?.canUndo ?? false,
    canRedo: data.canRedo ?? fallback?.canRedo ?? false,
    komi: data.komi ?? fallback?.komi,
    gameEnded: data.gameEnded ?? fallback?.gameEnded ?? false,
    scoreBlack: data.scoreBlack ?? fallback?.scoreBlack,
    scoreWhite: data.scoreWhite ?? fallback?.scoreWhite,
    resignedBy: data.resignedBy ?? fallback?.resignedBy ?? null,
    winner: data.winner ?? fallback?.winner ?? null,
    territoryMarks: data.territoryMarks ?? fallback?.territoryMarks ?? {},
    deadStones: Array.isArray(data.deadStones) ? data.deadStones : (fallback?.deadStones ?? []),
    isScoringPhase: data.isScoringPhase ?? fallback?.isScoringPhase ?? false,
  }), [toPrisoners]);

  // --- Create room ---
  useEffect(() => {
    if (phase !== 'creating') return;

    let cancelled = false;
    (async () => {
      try {
        console.log('Creating room...');
        const res = await fetch(`${API_BASE_URL}/rooms`, { method: 'POST' });
        if (!res.ok) {
          const errorText = await res.text();
          throw new Error(`Failed to create room: ${res.status} ${errorText}`);
        }
        const data = await res.json();
        if (cancelled) return;

        const newRoomId = data.roomId as string;
        console.log('Room created:', newRoomId);
        setRoomId(newRoomId);
        setPhase('joining');

        // Notify parent component
        onRoomCreated?.(newRoomId);

        // Update the URL
        window.history.replaceState({}, '', `/r/${newRoomId}`);
      } catch (err) {
        if (!cancelled) {
          console.error('Failed to create room:', err);
          setError((err as Error).message);
          setPhase('error');
        }
      }
    })();

    return () => { cancelled = true; };
  }, [phase]);

  // --- Fetch initial state via REST (fallback) ---
  useEffect(() => {
    if (phase !== 'joining' || !roomId || roomState) return;

    // Fetch initial state while WebSocket is connecting
    (async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/rooms/${roomId}`);
        if (res.ok) {
          const data = await res.json();
          const state: RoomState = toRoomState(data);
          console.log('Fetched initial state via REST:', state);
          setRoomState(state);
          onBoardState(state.board);
          onPrisoners(state.prisoners);
        }
      } catch (err) {
        console.error('Failed to fetch initial state:', err);
      }
    })();
  }, [phase, roomId, roomState, onBoardState, onPrisoners, toPrisoners, toRoomState]);

  // --- Poll for state updates when WebSocket is disconnected ---
  useEffect(() => {
    if (!roomId || !roomState) return;
    
    // Only poll if WebSocket is not connected
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      return; // WebSocket is connected, no need to poll
    }

    const interval = setInterval(async () => {
      try {
        const res = await fetch(`${API_BASE_URL}/rooms/${roomId}`);
        if (res.ok) {
          const data = await res.json();
          // Use functional update to avoid stale closure
          setRoomState(currentState => {
            if (!currentState) {
              const newState: RoomState = toRoomState(data);
              onBoardState(newState.board);
              onPrisoners(newState.prisoners);
              return newState;
            }
            // Only update if moveNumber changed (opponent made a move)
            if (data.moveNumber !== currentState.moveNumber) {
              const newState: RoomState = toRoomState(data, currentState);
              console.log('Polled state update:', newState, 'old:', currentState.moveNumber);
              onBoardState(newState.board);
              onPrisoners(newState.prisoners);
              return newState;
            }
            return currentState;
          });
        }
      } catch (err) {
        console.error('Failed to poll state:', err);
      }
    }, 3000); // Poll every 3 seconds (less aggressive to avoid race conditions)

    return () => clearInterval(interval);
  }, [roomId, roomState, onBoardState, onPrisoners, toPrisoners, toRoomState]);

  // --- Connect WebSocket ---
  useEffect(() => {
    if (phase !== 'joining' || !roomId) return;

    // Construct WebSocket URL
    // In dev, connect directly to backend (8080), in prod use same host as page
    let wsUrl: string;
    if (API_BASE_URL.startsWith('/')) {
      // Development: API_BASE_URL is relative, so connect directly to backend
      const isDev = window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
      if (isDev) {
        wsUrl = `ws://localhost:8080/ws/game/${roomId}`;
      } else {
        // Production: use same host as page
        wsUrl = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/game/${roomId}`;
      }
    } else {
      // API_BASE_URL is absolute, derive WebSocket URL from it
      wsUrl = `${getWsBaseUrl()}/ws/game/${roomId}`;
    }
    console.log('Connecting to WebSocket:', wsUrl, '(API_BASE_URL:', API_BASE_URL, ')');
    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      console.log('WebSocket connected to room:', roomId);
      setPhase('connected');
      setError(null);
    };

    ws.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data);
        console.log('WebSocket message received:', data.type, data);
        if (data.type === 'state') {
          const state: RoomState = toRoomState(data);
          console.log('Setting room state:', state);
          setRoomState(state);
          onBoardState(state.board);
          onPrisoners(state.prisoners);
        } else if (data.type === 'error') {
          setStatusMessage(data.message);
          // Error messages also include state — update if present
          if (data.board) {
            const state: RoomState = toRoomState(data);
            setRoomState(state);
            onBoardState(state.board);
            onPrisoners(state.prisoners);
          }
          setTimeout(() => setStatusMessage(null), 3000);
        }
      } catch (err) {
        console.error('Failed to parse WebSocket message:', err, event.data);
      }
    };

    ws.onclose = (event) => {
      console.log('WebSocket closed:', event.code, event.reason, 'wasClean:', event.wasClean);
      // Try to reconnect after a short delay
      const currentPhase = phaseRef.current;
      if (currentPhase === 'connected' || currentPhase === 'joining') {
        if (!event.wasClean && event.code !== 1000) {
          // Connection was closed unexpectedly
          setStatusMessage(`Connection lost (code: ${event.code}). Reconnecting...`);
          setTimeout(() => setStatusMessage(null), 3000);
        }
        reconnectTimeoutRef.current = window.setTimeout(() => {
          if (wsRef.current === ws && phaseRef.current !== 'error') {
            console.log('Attempting to reconnect...');
            setPhase('joining'); // triggers reconnect
          }
        }, 2000);
      }
    };

    ws.onerror = (error) => {
      console.error('WebSocket error:', error);
      const errorMsg = `Failed to connect to game server. Make sure the backend is running on port 8080.`;
      setError(errorMsg);
      setStatusMessage(errorMsg);
      // onclose will fire after this
    };

    return () => {
      ws.close();
      wsRef.current = null;
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
    };
  }, [phase, roomId, onBoardState, onPrisoners, toPrisoners, toRoomState]);

  // --- Move handler (passed up to App for Board clicks) ---
  const handlePlayMove = useCallback(async (x: number, y: number) => {
    console.log('handlePlayMove called:', { x, y, roomState, wsReady: wsRef.current?.readyState });
    
    if (!roomState) {
      console.log('No roomState');
      setStatusMessage('Game not ready yet. Please wait...');
      setTimeout(() => setStatusMessage(null), 2000);
      return;
    }
    
    const ws = wsRef.current;
    
    // Try WebSocket first
    if (ws && ws.readyState === WebSocket.OPEN) {
      console.log('Sending move via WebSocket:', { x, y });
      ws.send(JSON.stringify({
        type: 'move',
        x,
        y,
      }));
      return;
    }
    
    // Fallback: Use REST API if WebSocket not available
    console.log('WebSocket not available, using REST API');
    console.log('Sending move:', { x, y, currentTurn: roomState.turn });
    
    try {
      const requestBody = { x, y };
      console.log('Sending REST request:', { url: `${API_BASE_URL}/rooms/${roomState.roomId}/moves`, body: requestBody });
      
      const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/moves`, {
        method: 'POST',
        headers: { 
          'Content-Type': 'application/json',
          'Accept': 'application/json'
        },
        body: JSON.stringify(requestBody),
      });
      
      console.log('REST response status:', res.status, res.statusText);

      let data: any;
      try {
        data = await res.json();
      } catch (e) {
        const text = await res.text();
        throw new Error(`Failed to parse response (${res.status}): ${text}`);
      }

      console.log('REST API response:', { status: res.status, data });
      
      // Always update state from response (to get latest moveNumber and board)
      if (data.roomId && data.board) {
        const newState: RoomState = toRoomState(data, roomState);
        setRoomState(newState);
        onBoardState(newState.board);
        onPrisoners(newState.prisoners);
      }
      
      if (res.ok && data.success) {
        setStatusMessage('✓ Move played!');
        setTimeout(() => setStatusMessage(null), 2000);
      } else {
        // Handle error response
        const errorMsg = data.message || data.error || `Move failed (${res.status})`;
        console.error('Move failed:', errorMsg, 'Full response:', data);
        
        // Show user-friendly error
        if (errorMsg.includes('Illegal move')) {
          setStatusMessage('⚠ Illegal move (suicide/ko rule)');
        } else if (errorMsg.includes('out of bounds')) {
          setStatusMessage('⚠ Invalid position');
        } else {
          setStatusMessage(`⚠ ${errorMsg}`);
        }
        setTimeout(() => setStatusMessage(null), 3000);
      }
    } catch (err) {
      console.error('Failed to send move via REST:', err);
      const errorMsg = err instanceof Error ? err.message : 'Failed to send move. Is backend running?';
      setStatusMessage(errorMsg);
      setTimeout(() => setStatusMessage(null), 4000);
    }
  }, [roomState, onBoardState, onPrisoners, toPrisoners]);

  // Push scoring marks to parent for board display
  useEffect(() => {
    if (!onScoringMarks) return;
    if (roomState?.isScoringPhase && roomState.territoryMarks !== undefined && roomState.deadStones !== undefined) {
      onScoringMarks({
        territoryMarks: roomState.territoryMarks ?? {},
        deadStones: roomState.deadStones ?? [],
      });
    } else {
      onScoringMarks(null);
    }
  }, [roomState?.isScoringPhase, roomState?.territoryMarks, roomState?.deadStones, onScoringMarks]);

  // Update parent's move handler: scoring phase -> mark clicks; playing -> play moves; else null
  const handleMarkClick = useCallback(async (x: number, y: number) => {
    if (!roomState?.roomId || !roomState.isScoringPhase || !markMode) return;
    const size = roomState.board?.boardSize ?? 0;
    if (x < 0 || x >= size || y < 0 || y >= size) return;
    const key = `${x},${y}`;
    const hasStone = roomState.board?.stones?.some(s => s.x === x && s.y === y);

    if (markMode === 'territory') {
      if (hasStone) return;
      const current = roomState.territoryMarks?.[key];
      const next = !current ? 'BLACK' : current === 'BLACK' ? 'WHITE' : null;
      try {
        const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/marks/territory`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ x, y, color: next }),
        });
        const data = await res.json();
        if (data.roomId && data.board) {
          setRoomState(toRoomState(data, roomState));
          onBoardState(data.board);
          onPrisoners(toPrisoners(data.prisoners));
        }
        if (res.ok) setStatusMessage(next ? `Marked as ${next}` : 'Territory mark cleared');
        else setStatusMessage(data.message || 'Failed');
        setTimeout(() => setStatusMessage(null), 2000);
      } catch (e) {
        setStatusMessage('Request failed');
        setTimeout(() => setStatusMessage(null), 2000);
      }
      return;
    }
    if (markMode === 'dead') {
      if (!hasStone) return;
      try {
        const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/marks/dead`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ x, y }),
        });
        const data = await res.json();
        if (data.roomId && data.board) {
          setRoomState(toRoomState(data, roomState));
          onBoardState(data.board);
          onPrisoners(toPrisoners(data.prisoners));
        }
        if (res.ok) setStatusMessage('Dead stone toggled');
        else setStatusMessage(data.message || 'Failed');
        setTimeout(() => setStatusMessage(null), 2000);
      } catch (e) {
        setStatusMessage('Request failed');
        setTimeout(() => setStatusMessage(null), 2000);
      }
    }
  }, [roomState, markMode, toRoomState, toPrisoners, onBoardState, onPrisoners]);

  useEffect(() => {
    if (roomState?.isScoringPhase) {
      onMoveHandler(markMode ? handleMarkClick : null);
    } else if (roomState && !roomState.gameEnded) {
      onMoveHandler(handlePlayMove);
    } else {
      onMoveHandler(null);
    }
  }, [roomState, roomState?.isScoringPhase, roomState?.gameEnded, markMode, handleMarkClick, handlePlayMove, onMoveHandler]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      onBoardState(null);
      onPrisoners({ black: 0, white: 0 });
      onMoveHandler(null);
    };
  }, [onBoardState, onPrisoners, onMoveHandler]);

  // Rotate Go rule tips for lightweight in-app guidance.
  useEffect(() => {
    const timer = window.setInterval(() => {
      setRuleTipIndex((prev) => (prev + 1) % ruleTips.length);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [ruleTips.length]);

  const handleResign = async () => {
    if (!roomState?.roomId) return;
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'resign' }));
      setStatusMessage('Resigning…');
      setTimeout(() => setStatusMessage(null), 2000);
      return;
    }
    try {
      const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/resign`, { method: 'POST' });
      const data = await res.json();
      if (data.success && data.roomId && data.board != null) {
        const newState = toRoomState(data);
        setRoomState(newState);
        onBoardState(newState.board);
        onPrisoners(newState.prisoners);
        setStatusMessage(data.message ?? `${newState.resignedBy} resigned. ${newState.winner} wins.`);
      } else {
        setStatusMessage(data.message ?? 'Resign failed');
      }
      setTimeout(() => setStatusMessage(null), 3000);
    } catch (err) {
      setStatusMessage('Resign request failed');
      setTimeout(() => setStatusMessage(null), 3000);
    }
  };

  const handlePass = async () => {
    if (!roomState) return;
    
    const ws = wsRef.current;
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'pass',
      }));
      return;
    }
    
    // Fallback: Use REST API
    try {
      const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/pass`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
      });

      const data = await res.json();
      
      if (data.success) {
        const newState: RoomState = toRoomState(data);
        setRoomState(newState);
        onBoardState(newState.board);
        onPrisoners(newState.prisoners);
        setStatusMessage(newState.gameEnded ? 'Game over.' : 'Turn passed.');
        setTimeout(() => setStatusMessage(null), 2000);
      } else {
        setStatusMessage(data.message || 'Pass failed');
        setTimeout(() => setStatusMessage(null), 3000);
      }
    } catch (err) {
      console.error('Failed to pass via REST:', err);
      setStatusMessage('Failed to pass. Please check connection.');
      setTimeout(() => setStatusMessage(null), 3000);
    }
  };

  const handleUndo = async () => {
    if (!roomState?.roomId) return;
    try {
      const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/undo`, { method: 'POST' });
      const data = await res.json();
      if (data.success && data.board != null) {
        const newState: RoomState = toRoomState(data);
        setRoomState(newState);
        onBoardState(newState.board);
        onPrisoners(newState.prisoners);
        setStatusMessage(data.message ?? 'Move undone.');
      } else {
        setStatusMessage(data.message ?? 'Nothing to undo.');
      }
      setTimeout(() => setStatusMessage(null), 2500);
    } catch (err) {
      console.error('Undo failed:', err);
      setStatusMessage('Undo failed');
      setTimeout(() => setStatusMessage(null), 2500);
    }
  };

  const handleRedo = async () => {
    if (!roomState?.roomId) return;
    try {
      const res = await fetch(`${API_BASE_URL}/rooms/${roomState.roomId}/redo`, { method: 'POST' });
      const data = await res.json();
      if (data.success && data.board != null) {
        const newState: RoomState = toRoomState(data);
        setRoomState(newState);
        onBoardState(newState.board);
        onPrisoners(newState.prisoners);
        setStatusMessage(data.message ?? 'Move redone.');
      } else {
        setStatusMessage(data.message ?? 'Nothing to redo.');
      }
      setTimeout(() => setStatusMessage(null), 2500);
    } catch (err) {
      console.error('Redo failed:', err);
      setStatusMessage('Redo failed');
      setTimeout(() => setStatusMessage(null), 2500);
    }
  };

  const copyToClipboard = async (text: string) => {
    try {
      await navigator.clipboard.writeText(text);
      setStatusMessage('Copied to clipboard!');
      setTimeout(() => setStatusMessage(null), 2000);
    } catch {
      setStatusMessage('Failed to copy');
    }
  };

  const handleLeave = () => {
    if (wsRef.current) {
      wsRef.current.close();
      wsRef.current = null;
    }
    onBoardState(null);
    onPrisoners({ black: 0, white: 0 });
    onMoveHandler(null);
    // Reset URL
    window.history.replaceState({}, '', '/');
    onBack();
  };

  // --- Render ---
  return (
    <div className="online-go-container">
      {/* Loading / Connecting - only show if sidebar isn't visible */}
      {(phase === 'creating' || (phase === 'joining' && !roomId)) && (
        <div className="room-loading">
          <div className="loading-text">
            {phase === 'creating' ? 'Creating room...' : 'Connecting...'}
          </div>
        </div>
      )}

      {/* Error */}
      {phase === 'error' && (
        <div className="room-error">
          <p>{error}</p>
          <button className="btn-primary" onClick={onBack}>Back to Menu</button>
        </div>
      )}

      {/* Game sidebar - show when we have a roomId */}
      {roomId && (
        <div className="game-info-sidebar">
          <div className="game-info-card">
            {/* Back button */}
            <button className="back-button" onClick={handleLeave}>
              ← Back
            </button>

            {/* Turn indicator */}
            {roomState && !roomState.gameEnded && (
              <div className="turn-section">
                <div className="turn-label">Current turn</div>
                <div className={`turn-display ${roomState.turn.toLowerCase()}`}>
                  <span className={`turn-stone ${roomState.turn.toLowerCase()}`}>●</span>
                  <span className="turn-text">{roomState.turn === 'BLACK' ? 'Black' : 'White'}</span>
                </div>
                <div className="move-counter">Move #{roomState.moveNumber + 1}</div>
              </div>
            )}

            {/* Game Over - resign or Chinese scoring + Komi */}
            {roomState?.gameEnded && (
              <div className="game-over-section">
                <div className="game-over-title">Game Over</div>
                {roomState.resignedBy && roomState.winner ? (
                  <div className="game-over-resign">
                    <span className="resign-message">{roomState.resignedBy} resigned.</span>
                    <span className="winner-message">{roomState.winner} wins.</span>
                  </div>
                ) : (
                  <>
                    <div className="game-over-scores">
                      <div className="score-line black">
                        <span className="score-label">Black</span>
                        <span className="score-value">{typeof roomState.scoreBlack === 'number' ? roomState.scoreBlack.toFixed(1) : '—'}</span>
                      </div>
                      <div className="score-line white">
                        <span className="score-label">White <span className="komi-note">(+{roomState.komi ?? 6.5} komi)</span></span>
                        <span className="score-value">{typeof roomState.scoreWhite === 'number' ? roomState.scoreWhite.toFixed(1) : '—'}</span>
                      </div>
                    </div>
                    <div className="game-over-winner">
                      {typeof roomState.scoreBlack === 'number' && typeof roomState.scoreWhite === 'number' &&
                        (roomState.scoreBlack > roomState.scoreWhite
                          ? 'Black wins'
                          : roomState.scoreWhite > roomState.scoreBlack
                            ? 'White wins'
                            : 'Tie')}
                    </div>
                  </>
                )}
              </div>
            )}

            {!roomState && phase === 'joining' && (
              <div className="turn-section">
                <div className="turn-label">Connecting to game...</div>
                <div className="loading-text" style={{ fontSize: '0.85rem', color: '#999', marginTop: '0.5rem' }}>
                  Waiting for connection...
                </div>
              </div>
            )}

            {/* Share: room code + invite link in one block */}
            <div className="room-code-section share-section-merged">
              <div className="room-code-header">
                <div className="room-code-label">Share</div>
                <button
                  type="button"
                  className="btn-copy-text"
                  onClick={() => copyToClipboard(shareUrl || roomId || '')}
                  title="Copy invite link"
                  disabled={!roomId}
                >
                  Copy link
                </button>
              </div>
              <div className="invite-value invite-value-merged">
                {roomId && <span className="share-code">{roomId}</span>}
                {shareUrl && (
                  <>
                    {roomId && <span className="share-sep"> · </span>}
                    <span className="share-url">{shareUrl}</span>
                  </>
                )}
              </div>
              <div className="room-code-hint">Share this link or code with a friend to join instantly.</div>
            </div>

            {/* Undo / Redo - disabled when game over */}
            {roomState && !roomState.gameEnded && (
              <div className="undo-redo-section">
                <button
                  type="button"
                  className="btn-undo-redo"
                  onClick={handleUndo}
                  disabled={!roomState.canUndo}
                  title="Undo last move"
                >
                  Undo
                </button>
                <button
                  type="button"
                  className="btn-undo-redo"
                  onClick={handleRedo}
                  disabled={!roomState.canRedo}
                  title="Redo"
                >
                  Redo
                </button>
              </div>
            )}

            {/* Pass & Resign - hidden when game over */}
            {roomState && !roomState.gameEnded && (
              <div className="pass-resign-section">
                <button className="btn-pass" onClick={handlePass}>
                  Pass Turn
                </button>
                <button
                  type="button"
                  className="btn-resign"
                  onClick={handleResign}
                  title="Resign the game"
                >
                  Resign
                </button>
              </div>
            )}

            {/* Scoring phase: mark territory or dead stones (contest) */}
            {roomState?.isScoringPhase && (
              <div className="scoring-marks-section">
                <div className="scoring-marks-label">Mark for scoring</div>
                <div className="scoring-marks-buttons">
                  <button
                    type="button"
                    className={`btn-mark-mode ${markMode === 'territory' ? 'active' : ''}`}
                    onClick={() => setMarkMode(m => m === 'territory' ? null : 'territory')}
                  >
                    Mark territory
                  </button>
                  <button
                    type="button"
                    className={`btn-mark-mode ${markMode === 'dead' ? 'active' : ''}`}
                    onClick={() => setMarkMode(m => m === 'dead' ? null : 'dead')}
                  >
                    Mark dead / Contest
                  </button>
                </div>
                {markMode && (
                  <div className="scoring-marks-hint">
                    {markMode === 'territory' ? 'Click empty points to assign to Black or White (cycle).' : 'Click stones to mark as dead for scoring.'}
                  </div>
                )}
              </div>
            )}

            <div className="quick-rules">
              <div className="quick-rules-title">Game essentials</div>
              <div className="quick-rule-topic">{ruleTips[ruleTipIndex].title}</div>
              <div className="quick-rule-item">{ruleTips[ruleTipIndex].text}</div>
            </div>
          </div>
        </div>
      )}

      {/* Status toast */}
      {statusMessage && (
        <div className={`status-toast ${statusMessage.includes('Copied') ? 'success' : ''}`}>
          {statusMessage}
        </div>
      )}
    </div>
  );
};
