INSERT INTO puzzles (
    name, difficulty, initial_white, initial_black, solution
) VALUES (
    'Basic Capture',
    1,
    '[[0,1]]',
    '[[0,0]]',
    '[[1,0]]'
);

INSERT INTO puzzles (
    name, difficulty, initial_white, initial_black, solution
) VALUES (
    'Second Capture',
    1,
    '[[0,1]]',
    '[[0,0], [0, 3], [3, 0]]',
    '[[1,0]]'
);

-- Two black groups in atari (one liberty each): bottom-left and top-right. White to play; either capture is correct.
INSERT INTO puzzles (
    name, difficulty, initial_white, initial_black, solution
) VALUES (
    'Two Ways to Capture',
    1,
    '[[0,1],[1,1],[9,10],[10,8]]',
    '[[0,0],[10,10],[10,9]]',
    '[{"playerMoves":[[1,0],[9,9]],"opponentMove":null}]'
);
