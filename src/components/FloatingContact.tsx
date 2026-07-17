import { useState } from 'react';
import { Phone, MessageCircle } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA_MSG = "Hello MIROIR Studio, I'd like to book a session.";
const PHONE = '+971554511204';

export default function FloatingContact() {
  const [open, setOpen] = useState(false);

  return (
    <div className="fixed bottom-6 right-6 z-[9999] flex flex-col items-end gap-3">
      <AnimatePresence>
        {open && (
          <>
            <motion.a
              initial={{ opacity: 0, scale: 0.5, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.5, y: 10 }}
              href={`tel:${PHONE}`}
              className="w-14 h-14 flex items-center justify-center rounded-full bg-[#d4a84b] text-black shadow-xl shadow-black/40 hover:bg-[#d4b47a] transition-all duration-300 cursor-pointer"
              aria-label="Call us"
            >
              <Phone size={20} />
            </motion.a>
            <motion.a
              initial={{ opacity: 0, scale: 0.5, y: 10 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.5, y: 10 }}
              href={waLink(WA_MSG)}
              onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
              className="w-14 h-14 flex items-center justify-center rounded-full bg-[#d4a84b] text-black shadow-xl shadow-black/40 hover:bg-[#d4b47a] transition-all duration-300 cursor-pointer"
              aria-label="WhatsApp"
            >
              <MessageCircle size={20} />
            </motion.a>
          </>
        )}
      </AnimatePresence>

      <button
        onClick={() => setOpen(!open)}
        className="w-14 h-14 flex items-center justify-center rounded-full bg-[#d4a84b] text-black shadow-xl shadow-black/40 hover:bg-[#d4b47a] transition-all duration-300 cursor-pointer border-none"
        aria-label="Contact us"
      >
        <motion.div
          animate={{ rotate: open ? 45 : 0 }}
          transition={{ duration: 0.25 }}
          className="flex items-center justify-center"
        >
          <Phone size={20} />
        </motion.div>
      </button>
    </div>
  );
}
