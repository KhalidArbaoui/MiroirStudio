import { motion } from 'framer-motion';
import { ChevronDown } from 'lucide-react';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA_MSG = "Hello MIROIR Studio, I'd like to book a session.";

export default function Hero() {
  return (
    <section id="home" className="relative h-screen min-h-[700px] flex items-center justify-center overflow-hidden bg-[#0a0806]">
      <div className="absolute inset-0" style={{
        maskImage: 'linear-gradient(to bottom, black 0%, black 55%, transparent 100%)',
        WebkitMaskImage: 'linear-gradient(to bottom, black 0%, black 55%, transparent 100%)',
      }}>
        <img
          src="./images/hero-dish.jpg"
          alt="Luxury plated dish - MIROIR Studio food photography"
          className="w-full h-full object-cover object-center"
          fetchPriority="high"
        />
        <div className="absolute inset-0 bg-[#0a0806]/40" />
        <div className="absolute inset-0 bg-gradient-to-t from-[#0a0806]/80 via-transparent to-[#0a0806]/20" />
      </div>

      <div className="relative z-10 text-center px-6 max-w-3xl mx-auto">
        <motion.div
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 0.3 }}
          className="flex items-center justify-center gap-4 mb-8"
        >
          <div className="w-10 h-px bg-[#d4a84b60]" />
          <span className="font-sans text-[0.52rem] tracking-[0.6em] text-[#d4a84b] uppercase font-medium">Abu Dhabi · Dubai, UAE</span>
          <div className="w-10 h-px bg-[#d4a84b60]" />
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 32 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 1.1, delay: 0.5, ease: [0.22, 1, 0.36, 1] }}
          className="font-monea leading-none mb-4"
        >
          <span className="block text-[5rem] sm:text-[7rem] md:text-[9rem] lg:text-[10rem] text-white tracking-[0.06em]">MIROIR</span>
          <span className="block text-[1.8rem] sm:text-[2.2rem] md:text-[2.8rem] text-[#d4a84b] tracking-[0.4em] mt-1">Studio</span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.9, delay: 0.9 }}
          className="font-sans text-[0.68rem] tracking-[0.26em] text-[#c8c0b7] uppercase mt-7 mb-12"
        >
          Premium Photography &amp; Video Production
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, delay: 1.1 }}
          className="flex flex-col sm:flex-row items-center justify-center gap-4"
        >
          <a
            href={waLink(WA_MSG)}
            onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
            className="wa-pulse min-w-[220px] bg-[#d4a84b] text-black font-sans text-[0.62rem] tracking-[0.24em] uppercase px-8 sm:px-10 py-4 hover:bg-[#d4b47a] transition-all duration-300 cursor-pointer font-medium"
          >
            Book via WhatsApp
          </a>
          <a
            href="#portfolio"
            className="min-w-[220px] border border-[#555] text-[#c8c0b7] font-sans text-[0.62rem] tracking-[0.24em] uppercase px-8 sm:px-10 py-4 hover:border-[#d4a84b] hover:text-[#d4a84b] transition-all duration-300"
          >
            View Portfolio
          </a>
        </motion.div>
      </div>

      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        transition={{ delay: 1.8 }}
        className="absolute bottom-8 left-1/2 -translate-x-1/2"
      >
        <motion.div animate={{ y: [0, 7, 0] }} transition={{ repeat: Infinity, duration: 2.4, ease: 'easeInOut' }}>
          <ChevronDown size={18} className="text-[#d4a84b40]" />
        </motion.div>
      </motion.div>
    </section>
  );
}
