CREATE TABLE IF NOT EXISTS puzzles (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT,
    difficulty INTEGER CHECK (difficulty IN (1, 2, 3)),  --1 for easy, 2 for medium, 3 for hard
    initial_white TEXT NOT NULL,
    initial_black TEXT NOT NULL,
    solution TEXT NOT NULL, --move set, of player and opponent's moves 
    player_to_move BOOLEAN NOT NULL DEFAULT 1, -- 1 for white, 0 for black
    notes TEXT
);

CREATE TABLE IF NOT EXISTS board_state_stack (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    stack_type TEXT NOT NULL,
    state_json TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_board_state_stack_entity ON board_state_stack (entity_type, entity_id, stack_type);

-- Game rooms (persisted so they survive restarts).
-- SQLite: INTEGER for game_ended (0/1). For Postgres use BOOLEAN.
CREATE TABLE IF NOT EXISTS rooms (
    room_id TEXT PRIMARY KEY,
    board_size INTEGER NOT NULL,
    komi REAL NOT NULL,
    turn TEXT NOT NULL,
    move_number INTEGER NOT NULL,
    consecutive_passes INTEGER NOT NULL,
    game_ended INTEGER NOT NULL,
    score_black REAL NOT NULL,
    score_white REAL NOT NULL,
    resigned_by TEXT,
    winner TEXT,
    territory_marks_json TEXT,
    dead_stones_json TEXT,
    board_state_json TEXT NOT NULL,
    updated_at TEXT
);

