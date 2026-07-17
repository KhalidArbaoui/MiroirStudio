import { Phone, MessageCircle } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';
import { openWhatsApp, waLink } from '../lib/whatsapp';

const WA_MSG = "Hello MIROIR Studio, I'd like to book a session.";
const PHONE = '+971554511204';

export default function FloatingContact() {
  return (
    <div className="fixed bottom-6 right-6 z-[9999] flex flex-col items-end gap-3">
      <motion.a
        initial={{ opacity: 0, scale: 0.5 }}
        animate={{ opacity: 1, scale: 1 }}
        href={`tel:${PHONE}`}
        className="w-14 h-14 flex items-center justify-center rounded-full bg-[#d4a84b] text-black shadow-xl shadow-black/40 hover:bg-[#d4b47a] transition-all duration-300 cursor-pointer"
        aria-label="Call us"
      >
        <Phone size={20} />
      </motion.a>
      <motion.a
        initial={{ opacity: 0, scale: 0.5 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ delay: 0.1 }}
        href={waLink(WA_MSG)}
        onClick={e => { e.preventDefault(); openWhatsApp(WA_MSG); }}
        className="w-14 h-14 flex items-center justify-center rounded-full bg-[#25D366] text-white shadow-xl shadow-black/40 hover:bg-[#20bd5a] transition-all duration-300 cursor-pointer"
        aria-label="WhatsApp"
      >
        <MessageCircle size={22} />
      </motion.a>
    </div>
  );
}
