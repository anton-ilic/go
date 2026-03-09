# Go puzzle generator

Go puzzle generator and simulator.

## Setup

1. Clone this repo.
2. Install dependencies.

## Dependencies

- Java 17+
- Maven
- Python 3.7+

## Database

See [Database](database/README.md) for details.

## Running (desktop Swing app)

To compile and run the original desktop project: `make run`

## Running the Spring Boot web API

From the project root:

1. Build and run the Spring Boot app:
   - `mvn spring-boot:run`
2. The API will be available at `http://localhost:8080/api`, including:
   - `GET /api/puzzles`
   - `GET /api/puzzles/{id}`
   - `POST /api/games`
   - `POST /api/games/{gameId}/moves`

## Running the React front-end

The web front-end lives in the `frontend/` directory.

1. Install Node.js (LTS) and npm.
2. From `frontend/`:
   - `npm install`
   - `npm run dev`
3. Open the URL shown in the terminal (typically `http://localhost:5173`).

The front-end expects the backend to be running at `http://localhost:8080/api`. You can override this by setting `VITE_API_BASE_URL` in a `.env` file in `frontend/`.

**Tip:** Start the backend first (`mvn spring-boot:run`), then run the frontend (`npm run dev`). The game connects to the backend on port 8080 directly, so you don't need Vite to proxy WebSockets. If you ever see a one-off `ws proxy socket error` or `EPIPE` in the Vite terminal, it's usually from the dev server's own connection and is harmless; a hard refresh (Cmd+Shift+R / Ctrl+Shift+R) can help.

## Tests

- Backend tests: from the project root run `mvn test`.
- Front-end tests: from `frontend/` run `npm test`.

