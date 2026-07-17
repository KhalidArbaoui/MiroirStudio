import { useState, useCallback, useEffect } from 'react';
import type { MouseEvent } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { X, ArrowLeft, ArrowRight, ChevronLeft, Home } from 'lucide-react';
import type { Category } from '../data/galleryData';

type LightboxItem = { index: number };

export default function GalleryPage({ category, onBack }: { category: Category; onBack: () => void }) {
  useEffect(() => {
    const handler = (e: PopStateEvent) => { onBack(); };
    window.addEventListener('popstate', handler);
    return () => window.removeEventListener('popstate', handler);
  }, [onBack]);
  const [lightbox, setLightbox] = useState<LightboxItem | null>(null);

  const openLightbox = useCallback((index: number) => setLightbox({ index }), []);
  const closeLightbox = useCallback(() => setLightbox(null), []);

  const prev = useCallback((e: MouseEvent) => {
    e.stopPropagation();
    setLightbox(l => l ? { index: (l.index - 1 + category.items.length) % category.items.length } : null);
  }, [category.items.length]);

  const next = useCallback((e: MouseEvent) => {
    e.stopPropagation();
    setLightbox(l => l ? { index: (l.index + 1) % category.items.length } : null);
  }, [category.items.length]);

  const lightboxItem = lightbox !== null ? category.items[lightbox.index] : null;

  return (
    <div className="pt-20">
      <div className="relative h-[35vh] min-h-[250px] flex items-end overflow-hidden bg-[#0a0806]">
        <img
          src={category.cover}
          alt={category.title}
          className="absolute inset-0 w-full h-full object-cover"
          loading="eager"
        />
        {category.id === 'reels' && (
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="relative">
              <motion.div
                className="absolute -inset-6 rounded-full border border-[#d4a84b]/20"
                animate={{ opacity: [0.3, 0.05, 0.3], scale: [1, 1.2, 1] }}
                transition={{ duration: 3, repeat: Infinity, ease: 'easeInOut' }}
              />
              <div className="w-20 h-20 flex items-center justify-center rounded-full bg-[#d4a84b]/80 shadow-2xl shadow-[#d4a84b]/30">
                <svg viewBox="0 0 24 24" fill="white" className="w-9 h-9 ml-1.5">
                  <path d="M8 5v14l11-7z" />
                </svg>
              </div>
            </div>
          </div>
        )}
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0806] via-[#0a0806]/50 to-transparent" />
        <div className="relative z-10 w-full px-6 lg:px-10 pb-8 max-w-7xl mx-auto">
          <button
            onClick={onBack}
            className="inline-flex items-center gap-1.5 font-sans text-[0.68rem] tracking-[0.3em] uppercase text-[#d4a84b]/60 hover:text-[#d4a84b] transition-colors duration-300 mb-4 font-medium"
          >
            <ChevronLeft size={14} />
            All Categories
          </button>
          <h1 className="font-sans text-4xl sm:text-5xl md:text-6xl text-white leading-tight font-bold">
            {category.title}
          </h1>
        </div>
      </div>

      <div className="px-4 sm:px-6 lg:px-10 py-8 max-w-7xl mx-auto">
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 sm:gap-4">
          {category.items.map((item, i) => (
            <motion.div
              key={item.id}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: i * 0.06 }}
              className="group relative overflow-hidden cursor-pointer bg-[#0c0a08] aspect-[4/5]"
              onClick={() => openLightbox(i)}
            >
              {item.type === 'video' ? (
                <>
                  <video
                    src={item.src}
                    muted
                    playsInline
                    preload="metadata"
                    className="w-full h-full object-cover transition-all duration-700 group-hover:scale-105"
                  />
                  <div className="absolute inset-0 bg-black/10 group-hover:bg-black/40 transition-all duration-500" />
                  <div className="absolute inset-0 flex items-center justify-center">
                    <div className="relative">
                      <motion.div
                        className="absolute -inset-3 rounded-full border border-[#d4a84b]/30"
                        animate={{ opacity: [0.4, 0.1, 0.4], scale: [1, 1.15, 1] }}
                        transition={{ duration: 2.5, repeat: Infinity, ease: 'easeInOut' }}
                      />
                      <div className="relative w-16 h-16 flex items-center justify-center rounded-full bg-[#d4a84b]/90 shadow-lg shadow-[#d4a84b]/20 transition-transform duration-300 group-hover:scale-110">
                        <svg viewBox="0 0 24 24" fill="white" className="w-7 h-7 ml-1">
                          <path d="M8 5v14l11-7z" />
                        </svg>
                      </div>
                    </div>
                  </div>
                  {item.title && (
                    <div className="absolute bottom-0 left-0 right-0 bg-gradient-to-t from-black/80 via-black/30 to-transparent pt-16 pb-4 px-4">
                      <p className="text-white text-sm font-medium truncate">{item.title}</p>
                    </div>
                  )}
                  <div className="absolute top-3 right-3 flex items-center gap-1 px-2 py-1 rounded bg-black/50 backdrop-blur-sm text-white/80 text-[0.4rem] tracking-[0.15em] uppercase font-sans font-medium border border-white/10">
                    <svg viewBox="0 0 24 24" fill="currentColor" className="w-2.5 h-2.5">
                      <path d="M3 3h18v2H3V3zm0 4h18v2H3V7zm0 4h18v2H3v-2zm0 4h18v2H3v-2zm0 4h18v2H3v-2z" />
                    </svg>
                    Reel
                  </div>
                  <div className="absolute left-2 top-1/2 -translate-y-1/2 hidden sm:flex flex-col gap-1">
                    {[...Array(6)].map((_, j) => (
                      <div key={j} className="w-1 h-1 rounded-full bg-white/15" />
                    ))}
                  </div>
                </>
              ) : (
                <>
                  <img
                    src={item.src}
                    alt={item.title}
                    loading="lazy"
                    className="w-full h-full object-cover transition-all duration-700 group-hover:scale-105"
                  />
                  <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-all duration-500" />
                </>
              )}
              <div className="absolute inset-0 shadow-[inset_0_0_0_0_rgba(212,168,75,0)] group-hover:shadow-[inset_0_0_0_1px_rgba(212,168,75,0.3)] transition-all duration-500" />
            </motion.div>
          ))}
        </div>
      </div>

      <AnimatePresence>
        {lightboxItem && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.25 }}
            className="fixed inset-0 z-[9999] bg-[#070503]/98 flex items-center justify-center"
            onClick={closeLightbox}
          >
            <button
              onClick={closeLightbox}
              className="absolute top-6 right-6 w-10 h-10 flex items-center justify-center border border-[#1a1a1a] text-[#d4a84b60] hover:text-[#d4a84b] hover:border-[#d4a84b30] transition-all z-10"
              aria-label="Close"
            >
              <X size={14} />
            </button>
            <button
              onClick={prev}
              className="absolute left-4 md:left-8 w-10 h-10 flex items-center justify-center border border-[#1a1a1a] text-[#d4a84b60] hover:text-[#d4a84b] hover:border-[#d4a84b30] transition-all z-10"
              aria-label="Previous"
            >
              <ArrowLeft size={14} />
            </button>
            <button
              onClick={next}
              className="absolute right-4 md:right-8 w-10 h-10 flex items-center justify-center border border-[#1a1a1a] text-[#d4a84b60] hover:text-[#d4a84b] hover:border-[#d4a84b30] transition-all z-10"
              aria-label="Next"
            >
              <ArrowRight size={14} />
            </button>

            <motion.div
              key={lightboxItem.id}
              initial={{ opacity: 0, scale: 0.97 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0 }}
              transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
              className="flex flex-col items-center w-full px-16 md:px-28 max-w-5xl"
              onClick={e => e.stopPropagation()}
            >
              {lightboxItem.type === 'video' ? (
                <video
                  src={lightboxItem.src}
                  controls
                  autoPlay
                  className="max-h-[80vh] w-full object-contain"
                />
              ) : (
                <img
                  src={lightboxItem.src}
                  alt={lightboxItem.title}
                  className="max-h-[80vh] w-full object-contain"
                />
              )}
              <div className="flex items-center justify-between w-full mt-5">
                <div>
                  <p className="font-sans text-[0.48rem] tracking-[0.5em] text-[#d4a84b60] uppercase mb-1 font-medium">{category.title}</p>
                  <h3 className="font-sans text-xl text-[#f0ebe0] font-semibold">{lightboxItem.title}</h3>
                </div>
                <p className="font-sans text-[0.52rem] tracking-[0.3em] text-[#666666] uppercase font-medium">
                  {lightbox !== null ? lightbox.index + 1 : ''} / {category.items.length}
                </p>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
