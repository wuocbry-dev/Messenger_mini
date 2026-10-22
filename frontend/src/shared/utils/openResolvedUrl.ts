/**
 * Opens a URL that is only known after an async call (such as a presigned
 * download link) in a new tab. The tab is opened synchronously, inside the
 * tap or click, so mobile Safari and popup blockers allow it; it is pointed at
 * the URL once that resolves, and closed again if resolving fails.
 */
export async function openResolvedUrl(resolve: () => Promise<string>): Promise<void> {
  const win = window.open('', '_blank');
  try {
    const url = await resolve();
    if (win && !win.closed) {
      win.opener = null;
      win.location.href = url;
    } else {
      window.open(url, '_blank', 'noopener,noreferrer');
    }
  } catch (err) {
    win?.close();
    throw err;
  }
}
