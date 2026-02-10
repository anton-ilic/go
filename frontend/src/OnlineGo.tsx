import React, { useEffect, useRef, useState, useCallback, useMemo } from 'react';
import type { BoardState } from './App';

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
  board: BoardState;
};

type Props = {
  roomId: string | null;          // null = "create room" flow, string = "join existing room"
  onBack: () => void;
  onBoardState: (board: BoardState | null) => void;
  onMoveHandler: (handler: ((x: number, y: number) => void) | null) => void;
  onRoomCreated?: (roomId: string) => void;  // Callback when room is created
};

export const OnlineGo: React.FC<Props> = ({ roomId: initialRoomId, onBack, onBoardState, onMoveHandler, onRoomCreated }) => {
  const [phase, setPhase] = useState<'creating' | 'joining' | 'connected' | 'error'>(
    initialRoomId ? 'joining' : 'creating'
  );
  const [roomId, setRoomId] = useState<string | null>(initialRoomId);
  const [roomState, setRoomState] = useState<RoomState | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [ruleTipIndex, setRuleTipIndex] = useState(0);

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
          const state: RoomState = {
            roomId: data.roomId,
            turn: data.turn,
            moveNumber: data.moveNumber,
            board: data.board,
          };
          console.log('Fetched initial state via REST:', state);
          setRoomState(state);
          onBoardState(state.board);
        }
      } catch (err) {
        console.error('Failed to fetch initial state:', err);
      }
    })();
  }, [phase, roomId, roomState, onBoardState]);

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
              const newState: RoomState = {
                roomId: data.roomId,
                turn: data.turn,
                moveNumber: data.moveNumber,
                board: data.board,
              };
              onBoardState(newState.board);
              return newState;
            }
            // Only update if moveNumber changed (opponent made a move)
            if (data.moveNumber !== currentState.moveNumber) {
              const newState: RoomState = {
                roomId: data.roomId,
                turn: data.turn,
                moveNumber: data.moveNumber,
                board: data.board,
              };
              console.log('Polled state update:', newState, 'old:', currentState.moveNumber);
              onBoardState(newState.board);
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
  }, [roomId, roomState, onBoardState]);

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
          const state: RoomState = {
            roomId: data.roomId,
            turn: data.turn,
            moveNumber: data.moveNumber,
            board: data.board,
          };
          console.log('Setting room state:', state);
          setRoomState(state);
          onBoardState(state.board);
        } else if (data.type === 'error') {
          setStatusMessage(data.message);
          // Error messages also include state — update if present
          if (data.board) {
            const state: RoomState = {
              roomId: data.roomId,
              turn: data.turn,
              moveNumber: data.moveNumber,
              board: data.board,
            };
            setRoomState(state);
            onBoardState(state.board);
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
  }, [phase, roomId, onBoardState]);

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
        const newState: RoomState = {
          roomId: data.roomId,
          turn: data.turn,
          moveNumber: data.moveNumber,
          board: data.board,
        };
        setRoomState(newState);
        onBoardState(newState.board);
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
  }, [roomState]);

  // Update parent's move handler - enable when we have roomState (works with REST fallback even if WebSocket disconnected)
  useEffect(() => {
    if (roomState) {
      onMoveHandler(handlePlayMove);
    } else {
      onMoveHandler(null);
    }
  }, [roomState, handlePlayMove, onMoveHandler]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      onBoardState(null);
      onMoveHandler(null);
    };
  }, [onBoardState, onMoveHandler]);

  // Rotate Go rule tips for lightweight in-app guidance.
  useEffect(() => {
    const timer = window.setInterval(() => {
      setRuleTipIndex((prev) => (prev + 1) % ruleTips.length);
    }, 5000);
    return () => window.clearInterval(timer);
  }, [ruleTips.length]);

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
        const newState: RoomState = {
          roomId: data.roomId,
          turn: data.turn,
          moveNumber: data.moveNumber,
          board: data.board,
        };
        setRoomState(newState);
        onBoardState(newState.board);
        setStatusMessage('Turn passed.');
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
            {roomState && (
              <div className="turn-section">
                <div className="turn-label">Current turn</div>
                <div className={`turn-display ${roomState.turn.toLowerCase()}`}>
                  <span className={`turn-stone ${roomState.turn.toLowerCase()}`}>●</span>
                  <span className="turn-text">{roomState.turn === 'BLACK' ? 'Black' : 'White'}</span>
                </div>
                <div className="move-counter">Move #{roomState.moveNumber + 1}</div>
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

            {/* Room code + share */}
            <div className="room-code-section">
              <div className="room-code-header">
                <div className="room-code-label">Room code</div>
                <button
                  type="button"
                  className="btn-copy-text"
                  onClick={() => roomId && copyToClipboard(roomId)}
                  title="Copy room code"
                >
                  Copy code
                </button>
              </div>
              <div className="invite-value invite-value-code">{roomId}</div>
              <div className="room-code-hint">Share this code or link with a friend to join instantly.</div>
            </div>

            {shareUrl && (
              <div className="share-section">
                <div className="share-header">
                  <div className="share-label">Invite link</div>
                  <button
                    type="button"
                    className="btn-copy-text"
                    onClick={() => copyToClipboard(shareUrl)}
                    title="Copy link"
                  >
                    Copy link
                  </button>
                </div>
                <div className="invite-value invite-value-link">{shareUrl}</div>
              </div>
            )}

            {/* Pass button */}
            {roomState && (
              <div className="pass-section">
                <button 
                  className="btn-pass" 
                  onClick={handlePass}
                >
                  Pass Turn
                </button>
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
