import { useEffect, useState } from 'react';

/**
 * Phone layout: narrow screens, plus phones held in landscape (short touch
 * screens). Keep in sync with the CSS `@media` queries that use the same list.
 */
export const MOBILE_QUERY = '(max-width: 767px), (max-height: 500px) and (pointer: coarse)';

function matches(): boolean {
  return typeof window !== 'undefined' && window.matchMedia(MOBILE_QUERY).matches;
}

/** True while the viewport uses the phone (list/detail) layout. */
export function useIsMobile(): boolean {
  const [mobile, setMobile] = useState(matches);

  useEffect(() => {
    const mql = window.matchMedia(MOBILE_QUERY);
    const onChange = () => setMobile(mql.matches);
    onChange();
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return mobile;
}
