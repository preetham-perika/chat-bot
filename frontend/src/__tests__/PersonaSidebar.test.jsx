import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import PersonaSidebar from '../components/PersonaSidebar';

const mockPersonas = [
  { name: 'Hruday (Master Admin)', phoneNumber: '918790208445', role: 'ADMIN' },
  { name: 'Dad', phoneNumber: '919876543211', role: 'MEMBER' },
  { name: 'Mom', phoneNumber: '919876543212', role: 'MEMBER' }
];

describe('PersonaSidebar Component Unit Tests', () => {
  it('renders all family personas and stranger test caller', () => {
    render(
      <PersonaSidebar
        personas={mockPersonas}
        activePersona={mockPersonas[0]}
        onSelectPersona={() => {}}
        onQuickCommand={() => {}}
      />
    );

    expect(screen.getByText('Hruday (Master Admin)')).toBeInTheDocument();
    expect(screen.getByText('Dad')).toBeInTheDocument();
    expect(screen.getByText('Mom')).toBeInTheDocument();
    expect(screen.getByText('Stranger (Intruder)')).toBeInTheDocument();
  });

  it('calls onSelectPersona when a caller card is clicked', () => {
    const handleSelectPersona = vi.fn();
    render(
      <PersonaSidebar
        personas={mockPersonas}
        activePersona={mockPersonas[0]}
        onSelectPersona={handleSelectPersona}
        onQuickCommand={() => {}}
      />
    );

    fireEvent.click(screen.getByText('Dad'));
    expect(handleSelectPersona).toHaveBeenCalledWith(mockPersonas[1]);
  });

  it('dispatches quick commands when fast command buttons are clicked', () => {
    const handleQuickCommand = vi.fn();
    render(
      <PersonaSidebar
        personas={mockPersonas}
        activePersona={mockPersonas[0]}
        onSelectPersona={() => {}}
        onQuickCommand={handleQuickCommand}
      />
    );

    // Click Start ('hii') button
    fireEvent.click(screen.getByText(/Start \('hii'\)/));
    expect(handleQuickCommand).toHaveBeenCalledWith('hii');

    // Click Aadhaar service button
    fireEvent.click(screen.getByText(/1️⃣ Aadhaar/));
    expect(handleQuickCommand).toHaveBeenCalledWith('1');

    // Click Exit button
    fireEvent.click(screen.getByText(/Exit \('bye'\)/));
    expect(handleQuickCommand).toHaveBeenCalledWith('exit');
  });

  it('renders Admin Controls only when active caller has role ADMIN', () => {
    const { rerender } = render(
      <PersonaSidebar
        personas={mockPersonas}
        activePersona={mockPersonas[0]} // ADMIN
        onSelectPersona={() => {}}
        onQuickCommand={() => {}}
      />
    );
    expect(screen.getByText(/Admin Controls/)).toBeInTheDocument();

    // Re-render with MEMBER
    rerender(
      <PersonaSidebar
        personas={mockPersonas}
        activePersona={mockPersonas[1]} // Dad (MEMBER)
        onSelectPersona={() => {}}
        onQuickCommand={() => {}}
      />
    );
    expect(screen.queryByText(/Admin Controls/)).not.toBeInTheDocument();
  });
});
