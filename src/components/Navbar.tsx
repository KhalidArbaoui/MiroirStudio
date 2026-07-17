import { useState, useEffect } from 'react';
import { Menu, X, ChevronLeft } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { openWhatsApp, waLink } from '../lib/whatsapp';
import { categories } from '../data/galleryData';

const WA_MSG = "Hello MIROIR Studio, I'd like to book a session.";

const links = [
  { label: 'Home',      href: '#home' },
  { label: 'Portfolio', href: '#portfolio' },
  { label: 'Services',  href: '#services' },
  { label: 'About',     href: '#about' },
  { label: 'Contact',   href: '#contact' },
];

interface NavbarProps {
  onNavClick: (target: string) => void;
  currentPage: 'home' | 'gallery';
  selectedCategory?: string | null;
}

export default function Navbar({ onNavClick, currentPage, selectedCategory }: NavbarProps) {
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    const fn = () => setScrolled(window.scrollY > 50);
    window.addEventListener('scroll', fn);
    return () => window.removeEventListener('scroll', fn);
  }, []);

  return (
    <>
      <motion.nav
        initial={{ y: -80, opacity: 0 }}
        animate={{ y: 0, opacity: 1 }}
        transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] }}
        className={`fixed top-0 inset-x-0 z-50 transition-all duration-500 nav-blur ${
          scrolled
            ? 'bg-[rgba(8,8,8,0.92)] border-b border-white/[0.05]'
            : 'bg-transparent'
        }`}
      >
        <div className="container-site flex items-center justify-between h-16">
          <button onClick={() => onNavClick('home')} className="flex flex-col leading-none group bg-transparent border-none cursor-pointer">
            <span className="font-monea text-[1.6rem] tracking-[0.28em] text-[#d4a84b] group-hover:opacity-80 transition-opacity duration-300">
              MIROIR
            </span>
            <span className="font-nav text-[0.52rem] tracking-[0.5em] text-[#555] uppercase mt-0.5 font-medium">
              Studio
            </span>
          </button>

          {currentPage === 'gallery' && (
            <button
              onClick={() => onNavClick('home')}
              className="hidden md:inline-flex items-center gap-1.5 font-nav text-[0.63rem] tracking-[0.3em] uppercase text-[#d4a84b]/60 hover:text-[#d4a84b] transition-colors duration-300 bg-transparent border-none cursor-pointer font-medium"
            >
              <ChevronLeft size={13} />
              All Categories
            </button>
          )}

          {currentPage === 'home' && (
            <div className="hidden md:flex items-center gap-12">
              {links.map(l => (
                <button
                  key={l.label}
                  onClick={() => onNavClick(l.label.toLowerCase() === 'home' ? 'home' : l.href.replace('#', ''))}
                  className="relative font-nav text-[0.72rem] tracking-[0.24em] uppercase text-[#aaaaaa] hover:text-[#d4a84b] transition-colors duration-300 bg-transparent border-none cursor-pointer font-medium py-1.5 group"
                >
                  {l.label}
                  <span className="absolute -bottom-px left-1/2 -translate-x-1/2 w-0 h-px bg-[#d4a84b] transition-all duration-300 group-hover:w-full" />
                </button>
              ))}
            </div>
          )}

          <a
            href={waLink(WA_MSG)}
            onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
            className="hidden md:inline-flex items-center border border-[#d4a84b50] text-[#d4a84b] px-8 py-2.5
              font-nav text-[0.65rem] tracking-[0.28em] uppercase font-medium
              hover:bg-[#d4a84b] hover:text-black hover:border-[#d4a84b]
              transition-all duration-300 cursor-pointer"
          >
            Book Now
          </a>

          <button
            className="md:hidden text-[#d4a84b] p-1"
            onClick={() => setMenuOpen(!menuOpen)}
            aria-label="Toggle navigation menu"
          >
            {menuOpen ? <X size={20} /> : <Menu size={20} />}
          </button>
        </div>
      </motion.nav>

      <AnimatePresence>
        {menuOpen && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.35 }}
            className="fixed inset-0 z-40 bg-[#070503] flex flex-col items-center justify-center gap-9"
          >
            {currentPage === 'gallery' && (
              <motion.button
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: 0 }}
                onClick={() => { onNavClick('home'); setMenuOpen(false); }}
                className="font-nav text-2xl text-[#d4a84b] hover:text-[#d4a84b]/80 transition-colors tracking-wide bg-transparent border-none cursor-pointer font-semibold"
              >
                &larr; All Categories
              </motion.button>
            )}
            {currentPage === 'home' && links.map((l, i) => (
              <motion.button
                key={l.label}
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.07 }}
                onClick={() => { onNavClick(l.label.toLowerCase() === 'home' ? 'home' : l.href.replace('#', '')); setMenuOpen(false); }}
                className="font-nav text-2xl text-[#f0ebe0] hover:text-[#d4a84b] transition-colors tracking-wide bg-transparent border-none cursor-pointer font-semibold"
              >
                {l.label}
              </motion.button>
            ))}
            {currentPage === 'home' && categories.map((cat, i) => (
              <motion.button
                key={cat.id}
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: (links.length + i) * 0.05 }}
                onClick={() => { onNavClick(cat.id); setMenuOpen(false); }}
                className="font-nav text-[0.68rem] tracking-[0.28em] uppercase text-white/40 hover:text-[#d4a84b] transition-colors bg-transparent border-none cursor-pointer font-medium"
              >
                {cat.title}
              </motion.button>
            ))}
            <div className="w-8 h-px bg-[#d4a84b20] my-1" />
            <motion.a
              href={waLink(WA_MSG)}
              onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); setMenuOpen(false); }}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4 }}
              className="border border-[#d4a84b50] text-[#d4a84b] px-10 py-3.5
                font-nav text-[0.68rem] tracking-[0.3em] uppercase cursor-pointer font-medium
                hover:bg-[#d4a84b] hover:text-black transition-all duration-300"
            >
              Book via WhatsApp
            </motion.a>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
