import React from 'react';
import { render, screen } from '@testing-library/react';
import { OnlineGo } from './OnlineGo';

describe('OnlineGo', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn());
  });

  afterEach(() => {
    (fetch as unknown as vi.Mock).mockReset();
    vi.unstubAllGlobals();
  });

  it('shows creating state when no roomId', () => {
    render(
      <OnlineGo
        roomId={null}
        onBack={() => {}}
        onBoardState={() => {}}
        onPrisoners={() => {}}
        onMoveHandler={() => {}}
      />
    );
    expect(screen.getByText(/Creating room/i)).toBeInTheDocument();
  });

  it('shows connecting state when roomId is provided', () => {
    render(
      <OnlineGo
        roomId="ABC12345"
        onBack={() => {}}
        onBoardState={() => {}}
        onPrisoners={() => {}}
        onMoveHandler={() => {}}
      />
    );
    expect(screen.getByText(/Connecting/i)).toBeInTheDocument();
  });
});
