import React, { useCallback, useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import './ImageLightbox.css';

export interface LightboxImage {
  fileId: number;
  name?: string;
}

interface ImageLightboxProps {
  images: LightboxImage[];
  /** Resolved presigned URLs by file id (they load asynchronously). */
  urls: Record<number, string | undefined>;
  startIndex: number;
  onClose: () => void;
}

/** Full-window image viewer rendered in place (no navigation, no new tab). */
export function ImageLightbox({ images, urls, startIndex, onClose }: ImageLightboxProps) {
  const [index, setIndex] = useState(startIndex);
  const count = images.length;
  const current = images[index];
  const url = current ? urls[current.fileId] : undefined;

  const go = useCallback(
    (delta: number) => setIndex((i) => (i + delta + count) % count),
    [count]
  );

  // Horizontal swipe on touch screens moves between images.
  const touchStartX = useRef<number | null>(null);
  const onTouchStart = (e: React.TouchEvent) => {
    touchStartX.current = e.touches.length === 1 ? e.touches[0].clientX : null;
  };
  const onTouchEnd = (e: React.TouchEvent) => {
    const start = touchStartX.current;
    touchStartX.current = null;
    if (start === null || count < 2) return;
    const dx = e.changedTouches[0].clientX - start;
    if (Math.abs(dx) > 50) go(dx < 0 ? 1 : -1);
  };

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
      else if (e.key === 'ArrowRight' && count > 1) go(1);
      else if (e.key === 'ArrowLeft' && count > 1) go(-1);
    };
    window.addEventListener('keydown', onKey);
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      window.removeEventListener('keydown', onKey);
      document.body.style.overflow = prevOverflow;
    };
  }, [count, go, onClose]);

  const download = async () => {
    if (!url || !current) return;
    const filename = current.name ?? `image-${current.fileId}`;
    try {
      // Fetch as a blob so the browser saves it instead of navigating.
      const res = await fetch(url);
      if (!res.ok) throw new Error(String(res.status));
      const blobUrl = URL.createObjectURL(await res.blob());
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(blobUrl), 1000);
    } catch {
      // Storage CORS may block the fetch; fall back to a plain link.
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      a.rel = 'noopener';
      document.body.appendChild(a);
      a.click();
      a.remove();
    }
  };

  if (!current) return null;

  return createPortal(
    <div className="lightbox" role="dialog" aria-modal="true" aria-label="Image viewer" onClick={onClose}>
      {url && <img className="lightbox-backdrop" src={url} alt="" aria-hidden="true" />}

      <div className="lightbox-actions" onClick={(e) => e.stopPropagation()}>
        <button className="lightbox-btn" onClick={() => void download()} disabled={!url} aria-label="Download image" title="Download">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
            <polyline points="7 10 12 15 17 10" />
            <line x1="12" y1="15" x2="12" y2="3" />
          </svg>
        </button>
        <button className="lightbox-btn" onClick={onClose} aria-label="Close" title="Close">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round">
            <line x1="18" y1="6" x2="6" y2="18" />
            <line x1="6" y1="6" x2="18" y2="18" />
          </svg>
        </button>
      </div>

      <div
        className="lightbox-stage"
        onClick={(e) => e.stopPropagation()}
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
      >
        {url ? (
          <img key={current.fileId} className="lightbox-image" src={url} alt={current.name ?? 'Image'} />
        ) : (
          <div className="lightbox-loading" aria-label="Loading" />
        )}
      </div>

      {count > 1 && (
        <>
          <button
            className="lightbox-nav lightbox-nav--prev"
            onClick={(e) => { e.stopPropagation(); go(-1); }}
            aria-label="Previous image"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><polyline points="15 18 9 12 15 6" /></svg>
          </button>
          <button
            className="lightbox-nav lightbox-nav--next"
            onClick={(e) => { e.stopPropagation(); go(1); }}
            aria-label="Next image"
          >
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round"><polyline points="9 18 15 12 9 6" /></svg>
          </button>

          <div className="lightbox-thumbs" onClick={(e) => e.stopPropagation()}>
            {images.map((img, i) => (
              <button
                key={img.fileId}
                className={`lightbox-thumb${i === index ? ' lightbox-thumb--active' : ''}`}
                onClick={() => setIndex(i)}
                aria-label={`Image ${i + 1} of ${count}`}
                aria-current={i === index}
              >
                {urls[img.fileId] ? <img src={urls[img.fileId]} alt="" /> : null}
              </button>
            ))}
          </div>
        </>
      )}
    </div>,
    document.body
  );
}
