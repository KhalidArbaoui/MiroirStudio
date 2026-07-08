import { useState, useEffect } from 'react';
import { Menu, X } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const links = [
  { label: 'Home',      href: '#home' },
  { label: 'Portfolio', href: '#portfolio' },
  { label: 'Services',  href: '#services' },
  { label: 'About',     href: '#about' },
  { label: 'Contact',   href: '#contact' },
];

const WA_MSG = "Hello MIROIR Studio, I'd like to book a session.";

export default function Navbar() {
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
        <div className="container-site flex items-center justify-between h-14">
          <a href="#home" className="flex flex-col leading-none group">
            <span className="font-monea text-[1.3rem] tracking-[0.28em] text-[#c9a96e] group-hover:opacity-80 transition-opacity duration-300">
              MIROIR
            </span>
            <span className="font-sans text-[0.45rem] tracking-[0.5em] text-[#555] uppercase mt-0.5">
              Studio
            </span>
          </a>

          <div className="hidden md:flex items-center gap-10">
            {links.map(l => (
              <a
                key={l.label}
                href={l.href}
                className="font-sans text-[0.62rem] tracking-[0.22em] uppercase text-[#aaaaaa] hover:text-[#c9a96e] transition-colors duration-300"
              >
                {l.label}
              </a>
            ))}
          </div>

          <a
            href={waLink(WA_MSG)}
            onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
            className="hidden md:inline-flex items-center border border-[#c9a96e50] text-[#c9a96e] px-5 py-2
              font-sans text-[0.58rem] tracking-[0.28em] uppercase
              hover:bg-[#c9a96e] hover:text-black hover:border-[#c9a96e]
              transition-all duration-300 cursor-pointer"
          >
            Book Now
          </a>

          <button
            className="md:hidden text-[#c9a96e] p-1"
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
            className="fixed inset-0 z-40 bg-[#050505] flex flex-col items-center justify-center gap-9"
          >
            {links.map((l, i) => (
              <motion.a
                key={l.label}
                href={l.href}
                initial={{ opacity: 0, y: 14 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.07 }}
                onClick={() => setMenuOpen(false)}
                className="font-serif text-4xl italic text-[#f0ebe0] hover:text-[#c9a96e] transition-colors tracking-wide"
              >
                {l.label}
              </motion.a>
            ))}
            <div className="w-8 h-px bg-[#c9a96e20] my-1" />
            <motion.a
              href={waLink(WA_MSG)}
              onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); setMenuOpen(false); }}
              initial={{ opacity: 0, y: 14 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: 0.4 }}
              className="border border-[#c9a96e50] text-[#c9a96e] px-10 py-3.5
                font-sans text-[0.6rem] tracking-[0.3em] uppercase cursor-pointer
                hover:bg-[#c9a96e] hover:text-black transition-all duration-300"
            >
              Book via WhatsApp
            </motion.a>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}