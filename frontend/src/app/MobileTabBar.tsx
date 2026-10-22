import React from 'react';
import { NavLink } from 'react-router-dom';
import { navItems } from './AppSidebar';
import './MobileTabBar.css';

/** Bottom navigation for phones; hidden by CSS on wider screens. */
export function MobileTabBar() {
  return (
    <nav className="mobile-tabbar" aria-label="Main navigation">
      {navItems.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end={item.to === '/'}
          className={({ isActive }) => `mobile-tab${isActive ? ' mobile-tab--active' : ''}`}
        >
          <span className="mobile-tab-icon" aria-hidden="true">{item.icon}</span>
          <span className="mobile-tab-label">{item.label}</span>
        </NavLink>
      ))}
    </nav>
  );
}
