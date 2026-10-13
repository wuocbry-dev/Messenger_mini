import { createContext } from 'react';

/**
 * DOM node inside the left card where the conversation list is rendered.
 * AppLayout owns the card (title + tabs); ChatPanel portals the list into it.
 */
export const SidebarSlotContext = createContext<HTMLElement | null>(null);
