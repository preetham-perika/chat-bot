import '@testing-library/jest-dom';
import { vi } from 'vitest';

// Mock scrollIntoView which is missing in jsdom
window.HTMLElement.prototype.scrollIntoView = vi.fn();
