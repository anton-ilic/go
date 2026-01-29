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

  it('renders mode buttons', () => {
    render(<OnlineGo />);
    expect(screen.getByText(/Create game/i)).toBeInTheDocument();
    expect(screen.getByText(/Join game/i)).toBeInTheDocument();
  });

  it('renders without polling or board before game starts', () => {
    render(<OnlineGo />);
    // no board is rendered yet, so nothing to poll/click
    expect(screen.queryByTestId('board-cell')).toBeNull();
  });
});

