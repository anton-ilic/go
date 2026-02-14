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
